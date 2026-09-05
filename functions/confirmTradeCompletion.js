/* eslint-disable max-len, require-jsdoc */

const {onCall, HttpsError} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.database();
const SAFE_KEY_PATTERN = /^[^.#$/[\]]+$/;

function isSafeRtdbKey(value, maxLength) {
  return (
    typeof value === "string" &&
    value.length > 0 &&
    value.length <= maxLength &&
    SAFE_KEY_PATTERN.test(value)
  );
}

exports.confirmTradeCompletion = onCall(
    {
      region: "asia-southeast1",
      cpu: 0.083,
      memory: "256MiB",
      minInstances: 0,
      maxInstances: 1,
    },
    async (request) => {
      const uid = request.auth && request.auth.uid;

      if (!uid) {
        throw new HttpsError("unauthenticated", "Login required.");
      }

      const data = request.data || {};
      const tradeId = String(data.tradeId || "").trim();
      const clientChatId = String(data.chatId || "").trim();
      const messageId = String(data.messageId || "").trim();

      if (!isSafeRtdbKey(tradeId, 256)) {
        throw new HttpsError("invalid-argument", "Missing tradeId.");
      }

      if (clientChatId && !isSafeRtdbKey(clientChatId, 300)) {
        throw new HttpsError("invalid-argument", "Invalid chatId.");
      }

      if (messageId && !isSafeRtdbKey(messageId, 256)) {
        throw new HttpsError("invalid-argument", "Invalid messageId.");
      }

      const tradeSnap = await db.ref("trade_requests/" + tradeId).get();

      if (!tradeSnap.exists()) {
        throw new HttpsError("not-found", "Trade not found.");
      }

      const trade = tradeSnap.val() || {};
      const chatId = trade.chatId || trade.conversationId || trade.threadId || clientChatId;
      const fromUid = getFromUid(trade);
      const toUid = getToUid(trade);

      if (!chatId) {
        throw new HttpsError("failed-precondition", "Missing chatId.");
      }

      if (!isSafeRtdbKey(chatId, 300)) {
        throw new HttpsError("failed-precondition", "Invalid chatId.");
      }

      if (!fromUid || !toUid) {
        throw new HttpsError("failed-precondition", "Trade participants are missing.");
      }

      if (uid !== fromUid && uid !== toUid) {
        throw new HttpsError("permission-denied", "You are not part of this trade.");
      }

      const now = Date.now();

      await db.ref().update({
        ["user_actions/" + tradeId + "/" + uid + "/clicked_completed"]: true,
        ["user_actions/" + tradeId + "/" + uid + "/timestamp"]: now,
        ["user_actions/" + tradeId + "/" + uid + "/messageId"]: messageId,
        ["trade_requests/" + tradeId + "/updatedAt"]: now,
      });

      const actionsSnap = await db.ref("user_actions/" + tradeId).get();
      const fromClicked = actionsSnap.child(fromUid).child("clicked_completed").val() === true;
      const toClicked = actionsSnap.child(toUid).child("clicked_completed").val() === true;

      if (!fromClicked || !toClicked) {
        return {
          success: true,
          completed: false,
          waiting: true,
          message: "Waiting for partner confirmation.",
        };
      }

      const lockRef = db.ref("trade_completion_locks/" + tradeId);
      const lockResult = await lockRef.transaction((current) => {
        if (current === true) return;
        return true;
      });

      await saveCompletionStatus({
        tradeId,
        chatId,
        messageId,
        now,
      });

      const receiptId = await ensureCompletionArtifacts({
        tradeId,
        chatId,
        trade,
        messageId,
        now,
      });

      if (lockResult.committed) {
        await saveTradeHistoryIfMissing({
          tradeId,
          chatId,
          trade,
          completedByUid: uid,
          receiptId,
          now,
        });
      }

      return {
        success: true,
        completed: true,
        waiting: false,
        receiptId,
        message: "Trade completed successfully.",
      };
    },
);

async function saveCompletionStatus(params) {
  const tradeId = params.tradeId;
  const chatId = params.chatId;
  const messageId = params.messageId;
  const now = params.now || Date.now();

  const updates = {};
  updates["trade_requests/" + tradeId + "/status"] = "Completed";
  updates["trade_requests/" + tradeId + "/completedAt"] = now;
  updates["trade_requests/" + tradeId + "/updatedAt"] = now;

  if (messageId) {
    updates["chats/" + chatId + "/messages/" + messageId + "/messageType"] = "system_trade_completed";
    updates["chats/" + chatId + "/messages/" + messageId + "/tradeDetails/status"] = "Completed";
  }

  updates["chats/" + chatId + "/lastMessage"] = "Transaction Completed";
  updates["chats/" + chatId + "/lastMessageTime"] = now;
  updates["chats/" + chatId + "/updatedAt"] = now;

  await db.ref().update(updates);
}

async function ensureCompletionArtifacts(params) {
  const tradeId = params.tradeId;
  const chatId = params.chatId;
  const trade = params.trade || {};
  const now = params.now || Date.now();

  const receiptId = await ensureReceipt({tradeId, chatId, trade, now});
  await createRatingMessageIfMissing({tradeId, chatId, trade, now});
  await ensureCompletionNotifications({tradeId, chatId, trade, receiptId, now});

  return receiptId;
}

async function ensureReceipt(params) {
  const tradeId = params.tradeId;
  const chatId = params.chatId;
  const trade = params.trade || {};
  const now = params.now || Date.now();
  const receiptIndexRef = db.ref("receipts_by_trade/" + tradeId);
  const receiptIndexSnap = await receiptIndexRef.get();

  if (receiptIndexSnap.exists()) {
    const existingReceiptId = String(receiptIndexSnap.val() || "");
    if (existingReceiptId) return existingReceiptId;
  }

  const receiptId = db.ref("receipts").push().key;
  const updates = {};
  updates["receipts/" + receiptId] = buildReceiptData({receiptId, tradeId, chatId, trade, now});
  updates["receipts_by_trade/" + tradeId] = receiptId;
  await db.ref().update(updates);
  return receiptId;
}

async function createRatingMessageIfMissing(params) {
  const tradeId = params.tradeId;
  const chatId = params.chatId;
  const trade = params.trade || {};
  const now = params.now || Date.now();
  const lockRef = db.ref("trade_rating_messages_created/" + tradeId);

  const lockResult = await lockRef.transaction((current) => {
    if (current === true) return;
    return true;
  });

  if (!lockResult.committed) return;

  const messageRef = db.ref("chats/" + chatId + "/messages").push();
  const messageId = messageRef.key;
  const fromUser = trade.fromUser || {};
  const toUser = trade.toUser || {};
  const offeredItem = trade.offeredItem || {};
  const targetItem = trade.targetItem || {};

  const ratingMessage = {
    messageId,
    messageType: "system_trade_rating",
    senderId: "system",
    senderName: "BarterHub",
    text: "Rate your barter partner",
    timestamp: now,
    isSystemMessage: true,
    tradeDetails: {
      tradeRequestId: tradeId,
      chatId,
      fromUserId: getFromUid(trade),
      toUserId: getToUid(trade),
      offeredBy: fromUser.username || trade.fromUserName || trade.requesterName || "User",
      acceptedBy: toUser.username || trade.toUserName || trade.ownerName || "User",
      fromUserProfileImage: fromUser.profileImage || fromUser.profileImageUrl || "",
      toUserProfileImage: toUser.profileImage || toUser.profileImageUrl || "",
      fromUserLocation: fromUser.location || "",
      toUserLocation: toUser.location || "",
      offeredItemId: offeredItem.itemId || "",
      offeredItemName: offeredItem.title || "Item",
      offeredItemDescription: offeredItem.description || "",
      offeredItemImage: offeredItem.image || "",
      offeredItemCategory: offeredItem.category || "",
      offeredItemCondition: offeredItem.condition || "",
      targetItemId: targetItem.itemId || "",
      targetItemName: targetItem.title || "Item",
      targetItemDescription: targetItem.description || "",
      targetItemImage: targetItem.image || "",
      targetItemCategory: targetItem.category || "",
      targetItemCondition: targetItem.condition || "",
      status: "Completed",
    },
  };

  await db.ref().update({
    ["chats/" + chatId + "/messages/" + messageId]: ratingMessage,
    ["chats/" + chatId + "/lastMessage"]: "Rate your barter partner",
    ["chats/" + chatId + "/lastMessageTime"]: now,
    ["chats/" + chatId + "/updatedAt"]: now,
  });
}

async function ensureCompletionNotifications(params) {
  const tradeId = params.tradeId;
  const chatId = params.chatId;
  const trade = params.trade || {};
  const receiptId = params.receiptId || "";
  const now = params.now || Date.now();
  const fromUid = getFromUid(trade);
  const toUid = getToUid(trade);

  await ensureUserCompletionNotification({
    uid: fromUid,
    partnerUid: toUid,
    partnerName: getToName(trade),
    tradeId,
    chatId,
    receiptId,
    now,
  });

  await ensureUserCompletionNotification({
    uid: toUid,
    partnerUid: fromUid,
    partnerName: getFromName(trade),
    tradeId,
    chatId,
    receiptId,
    now,
  });
}

async function ensureUserCompletionNotification(params) {
  const uid = params.uid;
  const partnerUid = params.partnerUid;
  const partnerName = params.partnerName || "Trade partner";
  const tradeId = params.tradeId;
  const chatId = params.chatId;
  const receiptId = params.receiptId;
  const now = params.now || Date.now();
  const completedNotifId = "trade_completed_" + tradeId;
  const receiptNotifId = "receipt_" + tradeId;
  const completedNotifRef = db.ref("notifications/" + uid + "/" + completedNotifId);
  const completedNotifSnap = await completedNotifRef.get();
  const shouldIncrementUnread = !completedNotifSnap.exists();
  const updates = {};

  updates["notifications/" + uid + "/" + completedNotifId] = {
    id: completedNotifId,
    type: "trade_completed",
    requestId: tradeId,
    tradeId,
    chatId,
    partnerId: partnerUid,
    partnerName,
    message: "Transaction completed.",
    timestamp: now,
    read: false,
  };
  updates["notifications/" + uid + "/" + receiptNotifId] = {
    id: receiptNotifId,
    type: "receipt_created",
    receiptId,
    requestId: tradeId,
    tradeId,
    chatId,
    partnerId: partnerUid,
    partnerName,
    message: "Receipt is ready.",
    timestamp: now,
    read: false,
  };
  updates["user_inbox/" + uid + "/" + chatId + "/chatId"] = chatId;
  updates["user_inbox/" + uid + "/" + chatId + "/partnerId"] = partnerUid;
  updates["user_inbox/" + uid + "/" + chatId + "/partnerName"] = partnerName;
  updates["user_inbox/" + uid + "/" + chatId + "/lastMessage"] = "Rate your barter partner";
  updates["user_inbox/" + uid + "/" + chatId + "/lastMessageTime"] = now;
  updates["user_inbox/" + uid + "/" + chatId + "/updatedAt"] = now;

  await db.ref().update(updates);

  if (shouldIncrementUnread) {
    await db.ref("chats/" + chatId + "/unreadCount/" + uid).transaction((current) => (current || 0) + 1);
    await db.ref("user_inbox/" + uid + "/" + chatId + "/unreadCount").transaction((current) => (current || 0) + 1);
  }
}

function buildReceiptData(params) {
  const receiptId = params.receiptId;
  const tradeId = params.tradeId;
  const chatId = params.chatId;
  const trade = params.trade || {};
  const now = params.now || Date.now();
  const fromUser = trade.fromUser || {};
  const toUser = trade.toUser || {};
  const offeredItem = trade.offeredItem || {};
  const targetItem = trade.targetItem || {};

  return {
    receiptId,
    receiptNo: generateReceiptNumber(),
    chatDisplayId: generateChatDisplayId(),
    requestDisplayId: generateRequestDisplayId(),
    chatId,
    tradeRequestId: tradeId,
    timestamp: now,
    completedAt: now,
    status: "completed",
    fromUserId: getFromUid(trade),
    offeredBy: getFromName(trade),
    fromUserProfileImage: fromUser.profileImage || fromUser.profileImageUrl || "",
    fromUserLocation: fromUser.location || "",
    toUserId: getToUid(trade),
    acceptedBy: getToName(trade),
    toUserProfileImage: toUser.profileImage || toUser.profileImageUrl || "",
    toUserLocation: toUser.location || "",
    offeredItemId: offeredItem.itemId || "",
    offeredItemName: offeredItem.title || "Item",
    offeredItemDescription: offeredItem.description || "",
    offeredItemImage: offeredItem.image || "",
    offeredItemCategory: offeredItem.category || "",
    offeredItemCondition: offeredItem.condition || "",
    targetItemId: targetItem.itemId || "",
    targetItemName: targetItem.title || "Item",
    targetItemDescription: targetItem.description || "",
    targetItemImage: targetItem.image || "",
    targetItemCategory: targetItem.category || "",
    targetItemCondition: targetItem.condition || "",
  };
}

async function saveTradeHistoryIfMissing(params) {
  const tradeId = params.tradeId;
  const chatId = params.chatId;
  const trade = params.trade || {};
  const completedByUid = params.completedByUid;
  const receiptId = params.receiptId;
  const now = params.now || Date.now();
  const historyRef = db.ref("completed_trades/" + tradeId);
  const historySnap = await historyRef.get();

  if (historySnap.exists()) return;

  await historyRef.set({
    tradeId,
    chatId,
    receiptId,
    completedByUid,
    completedAt: now,
    createdAt: now,
    fromUserId: getFromUid(trade),
    toUserId: getToUid(trade),
    status: "Completed",
  });
}

function getFromUid(trade) {
  const fromUser = trade.fromUser || {};
  return fromUser.userId || trade.fromUserId || trade.requesterId || trade.buyerId || "";
}

function getToUid(trade) {
  const toUser = trade.toUser || {};
  return toUser.userId || trade.toUserId || trade.receiverId || trade.ownerId || trade.sellerId || "";
}

function getFromName(trade) {
  const fromUser = trade.fromUser || {};
  return fromUser.username || trade.fromUserName || trade.requesterName || "User";
}

function getToName(trade) {
  const toUser = trade.toUser || {};
  return toUser.username || trade.toUserName || trade.ownerName || "User";
}

function generateReceiptNumber() {
  const year = new Date().getFullYear();
  const random = Math.floor(100000 + Math.random() * 900000);
  return "RCPT-" + year + "-" + random;
}

function generateChatDisplayId() {
  const random = Math.floor(10000 + Math.random() * 90000);
  return "CHT-" + random;
}

function generateRequestDisplayId() {
  const random = Math.floor(10000 + Math.random() * 90000);
  return "REQ-" + random;
}
