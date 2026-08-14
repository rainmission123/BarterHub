/* eslint-disable require-jsdoc, max-len, indent */
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

function toCoinsNumber(value) {
  const coins = Number(value || 0);
  return Number.isFinite(coins) ? coins : 0;
}

function cleanUsername(value) {
  return String(value || "").trim().toLowerCase();
}

exports.sendCoins = onCall(
  {region: "us-central1"},
  async (request) => {
    if (!request.auth || !request.auth.uid) {
      throw new HttpsError("unauthenticated", "Authentication required.");
    }

    const senderUid = request.auth.uid;
    const username = cleanUsername(request.data && request.data.username);
    const amount = Number(request.data && request.data.amount);

    if (!username) {
      throw new HttpsError("invalid-argument", "Recipient username is required.");
    }

    if (!Number.isInteger(amount) || amount <= 0) {
      throw new HttpsError("invalid-argument", "Enter a valid coin amount.");
    }

    const db = admin.database();

    const recipientUidSnap = await db.ref("usernames").child(username).get();
    const recipientUid = recipientUidSnap.val();

    if (!recipientUid) {
      throw new HttpsError("not-found", "Recipient username was not found.");
    }

    if (recipientUid === senderUid) {
      throw new HttpsError("invalid-argument", "You cannot send coins to yourself.");
    }

    const senderCoinsRef = db.ref(`users/${senderUid}/wallet/coins`);
    const recipientCoinsRef = db.ref(`users/${recipientUid}/wallet/coins`);

    let senderFinalCoins = 0;

    const senderResult = await senderCoinsRef.transaction((currentCoins) => {
      const coins = toCoinsNumber(currentCoins);

      if (coins < amount) {
        return;
      }

      senderFinalCoins = coins - amount;
      return senderFinalCoins;
    });

    if (!senderResult.committed) {
      throw new HttpsError("failed-precondition", "Not enough coins.");
    }

    let recipientFinalCoins = 0;

    const recipientResult = await recipientCoinsRef.transaction((currentCoins) => {
      const coins = toCoinsNumber(currentCoins);
      recipientFinalCoins = coins + amount;
      return recipientFinalCoins;
    });

    if (!recipientResult.committed) {
      await senderCoinsRef.transaction((currentCoins) => {
        return toCoinsNumber(currentCoins) + amount;
      });

      throw new HttpsError("aborted", "Could not credit recipient. Coins were returned.");
    }

    const now = admin.database.ServerValue.TIMESTAMP;
    const transferId = db.ref("coin_transfers").push().key;
    const senderTxnId = `send_${transferId}`;
    const recipientTxnId = `receive_${transferId}`;

    const [senderSnap, recipientSnap] = await Promise.all([
      db.ref(`users/${senderUid}`).get(),
      db.ref(`users/${recipientUid}`).get(),
    ]);

    const sender = senderSnap.val() || {};
    const recipient = recipientSnap.val() || {};
    const senderName = sender.fullName || sender.username || "Someone";
    const recipientName = recipient.fullName || recipient.username || username;

    const updates = {};

    updates[`coin_transfers/${transferId}`] = {
      transferId,
      fromUserId: senderUid,
      toUserId: recipientUid,
      fromName: senderName,
      toName: recipientName,
      coins: amount,
      status: "completed",
      timestamp: now,
    };

    updates[`coin_transactions/${senderUid}/${senderTxnId}`] = {
      transactionId: senderTxnId,
      transferId,
      title: "Sent Coins",
      type: "send",
      coins: -amount,
      amount: 0,
      status: "completed",
      fromUserId: senderUid,
      toUserId: recipientUid,
      fromName: senderName,
      toName: recipientName,
      finalCoins: senderFinalCoins,
      timestamp: now,
    };

    updates[`coin_transactions/${recipientUid}/${recipientTxnId}`] = {
      transactionId: recipientTxnId,
      transferId,
      title: "Received Coins",
      type: "receive",
      coins: amount,
      amount: 0,
      status: "completed",
      fromUserId: senderUid,
      toUserId: recipientUid,
      fromName: senderName,
      toName: recipientName,
      finalCoins: recipientFinalCoins,
      timestamp: now,
    };

    updates[`transactions/${senderTxnId}`] =
      Object.assign({userId: senderUid}, updates[`coin_transactions/${senderUid}/${senderTxnId}`]);

    updates[`transactions/${recipientTxnId}`] =
      Object.assign({userId: recipientUid}, updates[`coin_transactions/${recipientUid}/${recipientTxnId}`]);

    updates[`notifications/${recipientUid}/${recipientTxnId}`] = {
      type: "coins",
      coins: amount,
      fromUserId: senderUid,
      senderName,
      message: `${senderName} sent you ${amount} coins!`,
      read: false,
      timestamp: now,
    };

    await db.ref().update(updates);

    return {
      success: true,
      message: `Sent ${amount} coins to ${recipientName}`,
      newBalance: senderFinalCoins,
      transferId,
    };
  },
);
