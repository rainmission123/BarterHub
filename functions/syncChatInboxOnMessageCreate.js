const {onValueCreated} = require("firebase-functions/v2/database");
const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");

const triggerOptions = {
  region: "us-central1",
  instance: "barterhub-3c947-default-rtdb",
  cpu: 0.083,
  memory: "256MiB",
  minInstances: 0,
  maxInstances: 1,
};

function stringValue(value) {
  return typeof value === "string" ? value.trim() : "";
}

function numberValue(value, fallback) {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function collectParticipants(chat) {
  const ids = new Set();

  ["participants", "participantIds"].forEach((field) => {
    const map = chat[field] || {};
    Object.keys(map).forEach((uid) => {
      if (map[uid] === true || map[uid] === "true") ids.add(uid);
    });
  });

  [
    "user1Id",
    "user2Id",
    "buyerId",
    "sellerId",
    "senderId",
    "receiverId",
  ].forEach((field) => {
    const uid = stringValue(chat[field]);
    if (uid) ids.add(uid);
  });

  return Array.from(ids).filter(Boolean);
}

function getPreview(message) {
  const messageType = stringValue(message.messageType || message.type);
  if (messageType === "image") return "Image";
  if (messageType === "video") return "Video";
  if (messageType === "system_trade_accepted" || messageType === "trade_accepted") {
    return stringValue(message.text || message.message) || "Trade accepted";
  }
  if (messageType === "system_trade_completed" || messageType === "trade_completed") {
    return stringValue(message.text || message.message) || "Trade completed";
  }
  if (messageType === "system_trade_rated" || messageType === "rating_submitted") {
    return stringValue(message.text || message.message) || "Rating submitted";
  }

  return stringValue(message.text || message.message) || "New message";
}

function getEffectiveSenderId(message, participants) {
  const senderId = stringValue(message.senderId);
  if (participants.includes(senderId)) return senderId;

  const partnerId = stringValue(message.partnerId);
  if (participants.includes(partnerId)) return partnerId;

  const fromUserId = stringValue(message.fromUserId);
  if (participants.includes(fromUserId)) return fromUserId;

  return senderId;
}

async function getPublicName(db, uid, fallback) {
  if (!uid) return fallback || "Chat Partner";

  try {
    const snapshot = await db.ref("public_users/" + uid).get();
    const publicUser = snapshot.val() || {};
    return stringValue(publicUser.fullName) ||
      stringValue(publicUser.username) ||
      fallback ||
      "Chat Partner";
  } catch (error) {
    logger.warn("Could not load public user name for inbox sync", {
      uid,
      message: error.message,
    });
    return fallback || "Chat Partner";
  }
}

async function syncParticipantInbox(params) {
  const db = params.db;
  const chatId = params.chatId;
  const messageId = params.messageId;
  const uid = params.uid;
  const partnerId = params.partnerId;
  const partnerName = params.partnerName;
  const preview = params.preview;
  const timestamp = params.timestamp;
  const incrementUnread = params.incrementUnread;

  const inboxRef = db.ref("user_inbox/" + uid + "/" + chatId);
  const result = await inboxRef.transaction((current) => {
    const next = current && typeof current === "object" ? current : {};
    if (next.lastInboxMessageId === messageId) return next;

    const currentUnread = numberValue(next.unreadCount, 0);
    next.chatId = chatId;
    next.partnerId = partnerId;
    next.partnerName = partnerName || next.partnerName || "Chat Partner";
    next.lastMessage = preview;
    next.lastMessageTime = timestamp;
    next.updatedAt = admin.database.ServerValue.TIMESTAMP;
    next.lastInboxMessageId = messageId;

    if (next.deleted === true) {
      next.deleted = false;
    }

    next.unreadCount = incrementUnread ? currentUnread + 1 : 0;

    return next;
  }, undefined, false);

  if (!result.committed) {
    logger.warn("Inbox sync transaction did not commit", {chatId, messageId, uid});
  }
}

exports.syncChatInboxOnMessageCreate = onValueCreated(
    {
      ref: "/chats/{chatId}/messages/{messageId}",
      ...triggerOptions,
    },
    async (event) => {
      const chatId = event.params.chatId;
      const messageId = event.params.messageId;
      const message = event.data.val() || {};

      if (!chatId || !messageId) return null;

      const db = admin.database();
      const messageType = stringValue(message.messageType || message.type);
      if (messageType === "system_trade_rating") {
        logger.info(
            "syncChatInboxOnMessageCreate skipped: rating inbox is owned by confirmTradeCompletion",
            {chatId, messageId},
        );
        return null;
      }

      const chatSnap = await db.ref("chats/" + chatId).get();
      if (!chatSnap.exists()) {
        logger.info("syncChatInboxOnMessageCreate skipped: chat missing", {
          chatId,
          messageId,
        });
        return null;
      }

      const chat = chatSnap.val() || {};
      const participants = collectParticipants(chat);
      if (participants.length === 0) {
        logger.warn("syncChatInboxOnMessageCreate skipped: no participants", {
          chatId,
          messageId,
        });
        return null;
      }

      const effectiveSenderId = getEffectiveSenderId(message, participants);
      const receiverId = stringValue(message.receiverId);
      const preview = getPreview(message);
      const timestamp = numberValue(message.timestamp, Date.now());

      await db.ref("chats/" + chatId).update({
        lastMessage: preview,
        lastMessageTime: timestamp,
      });

      await Promise.all(participants.map(async (uid) => {
        const fallbackPartnerId = participants.find((id) => id !== uid) || effectiveSenderId;
        const partnerId = uid === effectiveSenderId ?
          fallbackPartnerId :
          (effectiveSenderId || fallbackPartnerId);
        const fallbackName = uid === effectiveSenderId ?
          "Chat Partner" :
          stringValue(message.senderName);
        const partnerName = await getPublicName(db, partnerId, fallbackName);
        const incrementUnread = uid !== effectiveSenderId &&
          (!receiverId || uid === receiverId);

        await syncParticipantInbox({
          db,
          chatId,
          messageId,
          uid,
          partnerId,
          partnerName,
          preview,
          timestamp,
          incrementUnread,
        });
      }));

      logger.info("syncChatInboxOnMessageCreate completed", {
        chatId,
        messageId,
        participants: participants.length,
        effectiveSenderId,
        receiverId,
      });

      return null;
    },
);
