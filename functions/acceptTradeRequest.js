const {onCall, HttpsError} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

const ACCEPT_TRADE_DB_URL =
  "https://barterhub-3c947-default-rtdb.firebaseio.com";

const acceptTradeApp =
  admin.apps.find((app) => app.name === "acceptTradeRequestDb") ||
  admin.initializeApp(
      {databaseURL: ACCEPT_TRADE_DB_URL},
      "acceptTradeRequestDb",
  );

const db = acceptTradeApp.database();
const SAFE_KEY_PATTERN = /^[^.#$/[\]]+$/;

function isSafeRtdbKey(value, maxLength) {
  return (
    typeof value === "string" &&
    value.length > 0 &&
    value.length <= maxLength &&
    SAFE_KEY_PATTERN.test(value)
  );
}

exports.acceptTradeRequest = onCall(
    {
      region: "us-central1",
      cpu: 0.083,
      memory: "256MiB",
      minInstances: 0,
      maxInstances: 1,
      invoker: "public",
    },
    async (request) => {
      const uid = request.auth && request.auth.uid;

      if (!uid) {
        throw new HttpsError(
            "unauthenticated",
            "Please sign in to accept a trade.",
        );
      }

      const tradeId = String(
          request.data && request.data.tradeId || "",
      ).trim();

      if (!isSafeRtdbKey(tradeId, 256)) {
        throw new HttpsError(
            "invalid-argument",
            "tradeId is required.",
        );
      }

      const tradeRef = db.ref("trade_requests/" + tradeId);
      const initialSnap = await tradeRef.get();

      if (!initialSnap.exists()) {
        throw new HttpsError(
            "not-found",
            "Trade request was not found.",
        );
      }

      const initialTrade = initialSnap.val() || {};

      const fromUid = getFromUid(initialTrade);
      const toUid = getToUid(initialTrade);

      if (!fromUid || !toUid) {
        throw new HttpsError(
            "failed-precondition",
            "Trade participants are incomplete.",
        );
      }

      if (uid !== toUid) {
        throw new HttpsError(
            "permission-denied",
            "Only the receiving user can accept this trade.",
        );
      }

      let failure = null;
      let alreadyAccepted = false;

      const transactionResult = await tradeRef.transaction(
          (currentTrade) => {
            if (currentTrade === null) {
              return currentTrade;
            }

            if (typeof currentTrade !== "object") {
              failure = "missing";
              return;
            }

            const currentFromUid = getFromUid(currentTrade);
            const currentToUid = getToUid(currentTrade);

            if (!currentFromUid || !currentToUid) {
              failure = "participants";
              return;
            }

            if (currentToUid !== uid) {
              failure = "permission";
              return;
            }

            const status = stringValue(currentTrade.status).toLowerCase();

            if (status === "accepted") {
              alreadyAccepted = true;
              return currentTrade;
            }

            if (status !== "pending") {
              failure = "status";
              return;
            }

            currentTrade.status = "Accepted";
            currentTrade.updatedAt = Date.now();

            if (!stringValue(currentTrade.chatId)) {
              currentTrade.chatId = buildChatId(
                  currentFromUid,
                  currentToUid,
              );
            }

            return currentTrade;
          },
          undefined,
          false,
      );

      if (!transactionResult.committed) {
        if (failure === "missing") {
          throw new HttpsError(
              "not-found",
              "Trade request was not found.",
          );
        }

        if (failure === "participants") {
          throw new HttpsError(
              "failed-precondition",
              "Trade participants are incomplete.",
          );
        }

        if (failure === "permission") {
          throw new HttpsError(
              "permission-denied",
              "Only the receiving user can accept this trade.",
          );
        }

        if (failure === "status") {
          throw new HttpsError(
              "failed-precondition",
              "Only pending trades can be accepted.",
          );
        }

        throw new HttpsError(
            "aborted",
            "Trade acceptance could not be completed.",
        );
      }

      if (!transactionResult.snapshot.exists()) {
        throw new HttpsError(
            "not-found",
            "Trade request was not found.",
        );
      }

      const acceptedTrade = transactionResult.snapshot.val() || {};

      const acceptedFromUid = getFromUid(acceptedTrade);
      const acceptedToUid = getToUid(acceptedTrade);

      const chatId =
        stringValue(acceptedTrade.chatId) ||
        buildChatId(acceptedFromUid, acceptedToUid);

      const fromUser =
        acceptedTrade.fromUser &&
        typeof acceptedTrade.fromUser === "object" ?
          acceptedTrade.fromUser :
          {};

      const toUser =
        acceptedTrade.toUser &&
        typeof acceptedTrade.toUser === "object" ?
          acceptedTrade.toUser :
          {};

      const requesterName =
        stringValue(fromUser.username) ||
        stringValue(acceptedTrade.fromUserName) ||
        "User";

      const acceptorName =
        stringValue(toUser.username) ||
        stringValue(acceptedTrade.toUserName) ||
        "Someone";

      const targetItem =
        acceptedTrade.targetItem &&
        typeof acceptedTrade.targetItem === "object" ?
          acceptedTrade.targetItem :
          {};

      const targetItemTitle =
        stringValue(targetItem.title) ||
        "Item";

      const now = Date.now();

      /*
       * IMPORTANT:
       * Use update(), not set(), so existing messages under chats/{chatId}
       * are never deleted.
       */
      await db.ref("chats/" + chatId).update({
        chatId,
        user1Id: acceptedFromUid,
        user1Name: requesterName,
        user2Id: acceptedToUid,
        user2Name: acceptorName,
        itemId: stringValue(targetItem.itemId),
        itemTitle: targetItemTitle,
        tradeRequestId: tradeId,
        lastMessage: "Trade accepted! Discuss transaction details.",
        lastMessageTime: now,
        updatedAt: now,
      });

      await db
          .ref("chats/" + chatId + "/participants/" + acceptedFromUid)
          .set(true);

      await db
          .ref("chats/" + chatId + "/participants/" + acceptedToUid)
          .set(true);

      /*
       * Deterministic ID prevents duplicate accepted events.
       */
      const eventId =
        tradeId + "_trade_accepted_" + acceptedToUid;

      const eventRef =
        db.ref("trade_events/" + eventId);

      const eventResult = await eventRef.transaction(
          (existingEvent) => {
            if (existingEvent) {
              return;
            }

            return {
              type: "trade_accepted",
              toUserId: acceptedFromUid,
              fromUserId: acceptedToUid,
              fromUserName: acceptorName,
              chatId,
              partnerId: acceptedToUid,
              partnerName: acceptorName,
              requestId: tradeId,
              tradeId,
              message:
                acceptorName + " accepted your trade request.",
              timestamp: now,
            };
          },
          undefined,
          false,
      );

      return {
        success: true,
        alreadyAccepted,
        eventCreated: eventResult.committed,
        tradeId,
        chatId,
      };
    },
);

function getFromUid(trade) {
  return (
    stringValue(trade && trade.fromUserId) ||
    stringValue(trade && trade.requesterId) ||
    stringValue(
        trade &&
        trade.fromUser &&
        trade.fromUser.userId,
    )
  );
}

function getToUid(trade) {
  return (
    stringValue(trade && trade.toUserId) ||
    stringValue(trade && trade.receiverId) ||
    stringValue(
        trade &&
        trade.toUser &&
        trade.toUser.userId,
    )
  );
}

function stringValue(value) {
  return typeof value === "string" ? value.trim() : "";
}

function buildChatId(uidA, uidB) {
  return [uidA, uidB].sort().join("_");
}
