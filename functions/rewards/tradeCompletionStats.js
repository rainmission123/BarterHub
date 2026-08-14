const {onValueWritten} = require("firebase-functions/database");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

const REFERRAL_REWARD_COINS = 20;

/**
 * Gets participant uids from a trade request.
 *
 * @param {object} trade Trade request data.
 * @return {string[]} Participant uids.
 */
function getParticipantUids(trade) {
  const fromUid =
    trade.fromUser && trade.fromUser.userId ?
      trade.fromUser.userId :
      trade.fromUserId || "";

  const toUid =
    trade.toUser && trade.toUser.userId ?
      trade.toUser.userId :
      trade.toUserId || "";

  return [fromUid, toUid].filter((uid, index, list) => {
    return uid && list.indexOf(uid) === index;
  });
}

/**
 * Finds the inviter uid by referral code.
 *
 * @param {admin.database.Database} db Realtime Database instance.
 * @param {string} referralCode Referral code.
 * @return {Promise<string>} Inviter uid.
 */
async function findInviterUidByReferralCode(db, referralCode) {
  const usersSnap = await db.ref("users")
      .orderByChild("referralCode")
      .equalTo(referralCode)
      .limitToFirst(1)
      .get();

  if (!usersSnap.exists()) {
    return "";
  }

  const users = usersSnap.val() || {};
  return Object.keys(users)[0] || "";
}

/**
 * Recalculates and saves success rate for a user.
 *
 * @param {admin.database.Database} db Realtime Database instance.
 * @param {string} uid User uid.
 * @return {Promise<void>}
 */
async function recalculateSuccessRate(db, uid) {
  const userRef = db.ref("users/" + uid);
  const userSnap = await userRef.get();
  const user = userSnap.val() || {};

  const tradesCompleted = Number(user.tradesCompleted || 0);
  const totalTrades = Number(user.totalTrades || tradesCompleted);
  const failedTrades = Number(user.failedTrades || 0);
  const denominator = totalTrades > 0 ?
    totalTrades :
    tradesCompleted + failedTrades;

  const successRate = denominator > 0 ?
    Math.round((tradesCompleted / denominator) * 100) :
    100;

  await userRef.child("successRate").set(successRate);
}

/**
 * Increments completed trade stats once per trade/user pair.
 *
 * @param {admin.database.Database} db Realtime Database instance.
 * @param {string} tradeId Trade request id.
 * @param {string} uid User uid.
 * @return {Promise<boolean>} True when stats were updated.
 */
async function incrementTradeStatsOnce(db, tradeId, uid) {
  const markerRef = db.ref(
      "trade_completion_stats/" + tradeId + "/" + uid,
  );

  const markerResult = await markerRef.transaction((current) => {
    if (current && current.processed === true) {
      return;
    }

    return {
      processed: true,
      processedAt: Date.now(),
    };
  });

  if (!markerResult.committed) {
    return false;
  }

  try {
    const tradesRef = db.ref("users/" + uid + "/tradesCompleted");

    await tradesRef.transaction((current) => {
      const safeCurrent = typeof current === "number" ? current : 0;
      return safeCurrent + 1;
    });

    await db.ref("users/" + uid + "/tradeHistory/" + tradeId).set(true);
    await recalculateSuccessRate(db, uid);

    return true;
  } catch (error) {
    await markerRef.remove();
    throw error;
  }
}

/**
 * Grants referral reward when an invited user finishes first trade.
 *
 * @param {admin.database.Database} db Realtime Database instance.
 * @param {string} invitedUserId Invited user uid.
 * @return {Promise<boolean>} True when reward was granted.
 */
async function grantReferralRewardIfEligible(db, invitedUserId) {
  const invitedUserRef = db.ref("users/" + invitedUserId);
  const invitedSnap = await invitedUserRef.get();

  if (!invitedSnap.exists()) {
    return false;
  }

  const invitedUser = invitedSnap.val() || {};
  const referredBy = invitedUser.referredBy || "";
  const rewardGranted = invitedUser.referralRewardGranted === true;
  const tradesCompleted = Number(invitedUser.tradesCompleted || 0);

  if (!referredBy || rewardGranted || tradesCompleted !== 1) {
    return false;
  }

  const inviterUid = await findInviterUidByReferralCode(db, referredBy);

  if (!inviterUid || inviterUid === invitedUserId) {
    return false;
  }

  const rewardFlagRef = db.ref(
      "referrals/" +
      inviterUid +
      "/" +
      invitedUserId +
      "/rewardGranted",
  );

  const lockResult = await rewardFlagRef.transaction((current) => {
    if (current === true) {
      return;
    }

    return true;
  });

  if (!lockResult.committed) {
    return false;
  }

  try {
    const inviterCoinsRef = db.ref(
        "users/" + inviterUid + "/wallet/coins",
    );

    await inviterCoinsRef.transaction((currentCoins) => {
      const safeCurrentCoins =
        typeof currentCoins === "number" ? currentCoins : 0;

      return safeCurrentCoins + REFERRAL_REWARD_COINS;
    });

    const transactionId = "referral_reward_" + invitedUserId;
    const notifId = "referral_reward_" + invitedUserId;
    const now = admin.database.ServerValue.TIMESTAMP;
    const updates = {};

    updates["users/" + invitedUserId + "/referralRewardGranted"] = true;
    updates["users/" + invitedUserId + "/referredByUid"] = inviterUid;

    updates[
        "referrals/" +
        inviterUid +
        "/" +
        invitedUserId +
        "/firstTransactionCompleted"
    ] = true;

    updates[
        "referrals/" +
        inviterUid +
        "/" +
        invitedUserId +
        "/status"
    ] = "completed";

    updates[
        "referrals/" +
        inviterUid +
        "/" +
        invitedUserId +
        "/rewardedAt"
    ] = now;

    updates["coin_transactions/" + inviterUid + "/" + transactionId] = {
      title: "Referral Reward",
      type: "referral_reward",
      amount: 0,
      coins: REFERRAL_REWARD_COINS,
      invitedUserId: invitedUserId,
      referralCode: referredBy,
      status: "completed",
      transactionId: transactionId,
      referenceNo: "REF-" + invitedUserId,
      timestamp: now,
    };

    updates["notifications/" + inviterUid + "/" + notifId] = {
      id: notifId,
      type: "referral_reward",
      title: "Referral Reward",
      message:
        "You earned " +
        REFERRAL_REWARD_COINS +
        " coins because your invited user completed their first trade!",
      invitedUserId: invitedUserId,
      read: false,
      targetType: "referral_reward",
      targetUserId: invitedUserId,
      timestamp: now,
    };

    await db.ref().update(updates);

    return true;
  } catch (error) {
    await rewardFlagRef.set(false);
    throw error;
  }
}

const processCompletedTradeStats = onValueWritten(
    {
      ref: "/trade_requests/{tradeId}/status",
      region: "us-central1",
    },
    async (event) => {
      const beforeStatus = event.data.before.val();
      const afterStatus = event.data.after.val();

      if (beforeStatus === "Completed" || afterStatus !== "Completed") {
        return null;
      }

      const tradeId = event.params.tradeId;
      const db = admin.database();
      const tradeSnap = await db.ref("trade_requests/" + tradeId).get();

      if (!tradeSnap.exists()) {
        logger.warn("Completed trade request missing", {tradeId});
        return null;
      }

      const trade = tradeSnap.val() || {};
      const participantUids = getParticipantUids(trade);

      if (participantUids.length === 0) {
        logger.warn("Completed trade has no participant uids", {tradeId});
        return null;
      }

      const updatedUsers = [];

      for (const uid of participantUids) {
        const updated = await incrementTradeStatsOnce(db, tradeId, uid);

        if (updated) {
          updatedUsers.push(uid);
          await grantReferralRewardIfEligible(db, uid);
        }
      }

      logger.info("Processed completed trade stats", {
        tradeId: tradeId,
        updatedUsers: updatedUsers,
      });

      return null;
    },
);

module.exports = {
  processCompletedTradeStats,
};
