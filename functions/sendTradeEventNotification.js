const {onValueCreated} = require("firebase-functions/v2/database");
const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");

exports.sendTradeEventNotification = onValueCreated(
    {
      ref: "/trade_events/{eventId}",
      region: "us-central1",
      instance: "barterhub-3c947-default-rtdb",
      cpu: 0.083,
      memory: "256MiB",
      minInstances: 0,
      maxInstances: 1,
    },
    async (event) => {
      const eventId = event.params.eventId;
      const snapshot = event.data;

      if (!snapshot) return null;

      const data = snapshot.val();
      if (!data) return null;

      const toUserId = data.toUserId || "";
      const fromUserId = data.fromUserId || "";
      const type = data.type || "";
      const chatId = data.chatId || "";
      const partnerId = data.partnerId || fromUserId;
      const requestId = data.requestId || "";
      const tradeId = data.tradeId || requestId || "";
      const receiptId = data.receiptId || "";

      if (!toUserId || !fromUserId || !type) {
        logger.error("Missing trade event fields", {eventId, data});
        return null;
      }

      const allowedTypes = [
        "trade_accepted",
        "trade_completed",
        "rating_submitted",
      ];

      if (!allowedTypes.includes(type)) return null;

      const db = admin.database();

      try {
        const tokenSnap = await db
            .ref("users/" + toUserId + "/fcmToken")
            .get();

        const fromUserSnap = await db
            .ref("users/" + fromUserId)
            .get();

        const token = tokenSnap.val();
        const fromUser = fromUserSnap.val() || {};

        const fromUserName =
                fromUser.username ||
                fromUser.fullName ||
                data.fromUserName ||
                "Someone";

        const fromUserProfilePic =
                fromUser.profileImageUrl ||
                fromUser.profileImage ||
                data.fromUserProfilePic ||
                "";

        const content = buildNotificationContent(
            type,
            fromUserName,
            data,
        );

        if (chatId && type === "trade_accepted") {
          await createTradeChatMessage({
            db,
            eventId,
            data,
            type,
            chatId,
            fromUserId,
            toUserId,
            fromUserName,
            fromUserProfilePic,
            partnerId,
            requestId,
            tradeId,
            receiptId,
            message: content.message,
          });
        }

        if (!token) {
          logger.warn("No FCM token for trade event", {
            eventId,
            toUserId,
            type,
          });
          return null;
        }

        await admin.messaging().send({
          token: token,
          notification: {
            title: content.title,
            body: content.message,
          },
          data: {
            title: String(content.title),
            body: String(content.message),
            type: String(type),
            chatId: String(chatId),
            partnerId: String(partnerId),
            partnerName: String(fromUserName),
            partnerProfilePic: String(fromUserProfilePic),
            requestId: String(requestId),
            tradeId: String(tradeId),
            receiptId: String(receiptId),
            target: "chat",
          },
          android: {
            priority: "high",
            notification: {
              channelId: "barterhub_general_notifications",
              title: content.title,
              body: content.message,
              sound: "default",
              defaultSound: true,
              notificationPriority: "PRIORITY_HIGH",
            },
          },
        });

        logger.info("Trade event notification sent", {
          eventId,
          type,
          toUserId,
          fromUserId,
          chatId,
        });

        return null;
      } catch (error) {
        logger.error("Trade event notification error", {
          eventId,
          type,
          message: error.message,
          stack: error.stack,
        });
        return null;
      }
    },
);

/**
 * Builds push/chat text for a trade event.
 *
 * @param {string} type Trade event type.
 * @param {string} fromUserName Sender display name.
 * @param {object} data Trade event payload.
 * @return {{title: string, message: string}} Notification content.
 */
function buildNotificationContent(type, fromUserName, data) {
  if (type === "trade_accepted") {
    return {
      title: "Trade request accepted",
      message: fromUserName + " accepted your trade request.",
    };
  }

  if (type === "trade_completed") {
    return {
      title: "Transaction completed",
      message: fromUserName +
                " marked this transaction as completed.",
    };
  }

  if (type === "rating_submitted") {
    const ratingValue = Number(data.rating || 0);
    const safeRating = Math.max(1, Math.min(ratingValue, 5));
    const stars = "⭐".repeat(safeRating);

    return {
      title: stars + " New Rating",
      message: fromUserName + " rated you " + safeRating +
                " star" + (safeRating > 1 ? "s." : "."),
    };
  }

  return {
    title: "Trade update",
    message: fromUserName + " updated your barter.",
  };
}

/**
 * Creates the actual chat message for a trade event.
 *
 * @param {object} params Message creation params.
 * @return {Promise<void>} Resolves when write is complete.
 */
async function createTradeChatMessage(params) {
  const db = params.db;
  const eventId = params.eventId;
  const data = params.data;
  const type = params.type;
  const chatId = params.chatId;
  const fromUserId = params.fromUserId;
  const toUserId = params.toUserId;
  const fromUserName = params.fromUserName;
  const fromUserProfilePic = params.fromUserProfilePic;
  const partnerId = params.partnerId;
  const requestId = params.requestId;
  const tradeId = params.tradeId;
  const receiptId = params.receiptId;
  const message = params.message;

  const now = Date.now();
  const baseId = tradeId || requestId || eventId;
  const messageId = baseId + "_" + type + "_" + fromUserId;
  const messagePath = "chats/" + chatId + "/messages/" + messageId;
  const messageRef = db.ref(messagePath);
  const existingMessage = await messageRef.get();

  if (existingMessage.exists()) return;

  const chatMessage = {
    messageId: messageId,
    senderId: fromUserId,
    senderName: fromUserName,
    receiverId: toUserId,
    text: message,
    message: message,
    messageType: type,
    type: type,
    read: false,
    isRead: false,
    systemMessage: false,
    isSystemMessage: false,
    timestamp: now,
    tradeId: tradeId,
    requestId: requestId,
    receiptId: receiptId,
    partnerId: partnerId,
    fromUserProfilePic: fromUserProfilePic,
  };

  if (type === "trade_accepted") {
    chatMessage.senderId = "system";
    chatMessage.senderName = "System";
    chatMessage.systemMessage = true;
    chatMessage.isSystemMessage = true;
    chatMessage.tradeDetails = await buildTradeDetails(
        db,
        requestId || tradeId,
        data,
    );
  }

  await messageRef.set(chatMessage);

  await db.ref("chats/" + chatId + "/unreadCount/" + toUserId)
      .transaction((current) => {
        return (current || 0) + 1;
      });

  await db.ref("chats/" + chatId).update({
    lastMessage: message,
    lastMessageTime: now,
  });

  logger.info("Trade chat message created", {
    eventId,
    type,
    chatId,
    messageId,
  });
}

/**
 * Builds trade details required by Android Barter Accepted card.
 *
 * @param {object} db Firebase Realtime Database instance.
 * @param {string} requestId Trade request id.
 * @param {object} eventData Trade event payload.
 * @return {Promise<object>} Android tradeDetails map.
 */
async function buildTradeDetails(db, requestId, eventData) {
  let request = {};

  if (requestId) {
    const requestSnap = await db
        .ref("trade_requests/" + requestId)
        .get();
    request = requestSnap.val() || {};
  }

  const fromUser = request.fromUser || {};
  const toUser = request.toUser || {};
  const offeredItem = request.offeredItem || {};
  const targetItem = request.targetItem || {};

  return {
    tradeRequestId: requestId ||
            eventData.requestId ||
            eventData.tradeId ||
            "",
    status: "Accepted",

    fromUserId: fromUser.userId || eventData.fromUserId || "",
    offeredBy: fromUser.username || eventData.fromUserName || "User",
    fromUserLocation: fromUser.location || "Unknown Location",
    fromUserRating: Number(fromUser.rating || 0),
    fromUserProfileImage:
            fromUser.profileImage || fromUser.profileImageUrl || "",

    toUserId: toUser.userId || eventData.toUserId || "",
    acceptedBy: toUser.username || eventData.partnerName || "User",
    toUserLocation: toUser.location || "Unknown Location",
    toUserRating: Number(toUser.rating || 0),
    toUserProfileImage:
            toUser.profileImage || toUser.profileImageUrl || "",

    offeredItemId: offeredItem.itemId || "",
    offeredItemName: offeredItem.title || "Item",
    offeredItemDescription: offeredItem.description || "No description",
    offeredItemImage: offeredItem.image || "",
    offeredItemCategory: offeredItem.category || "Unknown",
    offeredItemCondition: offeredItem.condition || "Unknown",

    targetItemId: targetItem.itemId || "",
    targetItemName: targetItem.title || "Item",
    targetItemDescription: targetItem.description || "No description",
    targetItemImage: targetItem.image || "",
    targetItemCategory: targetItem.category || "Unknown",
    targetItemCondition: targetItem.condition || "Unknown",

    message: request.message || "No message",
    additionalPhotos: Array.isArray(request.additionalPhotos) ?
            request.additionalPhotos.join(",") :
            request.additionalPhotos || "",
    preferredMeetup: request.preferredMeetup || "",
  };
}
