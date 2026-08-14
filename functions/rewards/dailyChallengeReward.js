const {onCall, HttpsError} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

const ALLOWED_REWARDS = {
  post_item: 5,
  complete_transactions: 10,
  daily_login: 2,
  rate_partner: 1,
  share_app: 3,
};

const getTodayKey = () => {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Manila",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
};

const claimDailyChallengeReward = onCall(
    {region: "us-central1"},
    async (request) => {
      if (!request.auth || !request.auth.uid) {
        throw new HttpsError("unauthenticated", "You must be signed in.");
      }

      const uid = request.auth.uid;
      const action = request.data && request.data.action;

      if (
        !action ||
        !Object.prototype.hasOwnProperty.call(
            ALLOWED_REWARDS,
            action,
        )
      ) {
        throw new HttpsError("invalid-argument", "Invalid challenge action.");
      }

      const db = admin.database();
      const today = getTodayKey();
      const rewardCoins = ALLOWED_REWARDS[action];

      const challengeRef = db.ref(
          "users/" + uid + "/daily_challenges/" + today + "/" + action,
      );
      const walletCoinsRef = db.ref("users/" + uid + "/wallet/coins");
      const coinTransactionRef = db.ref("coin_transactions/" + uid).push();
      const notificationRef = db.ref("notifications/" + uid).push();

      const transactionId = coinTransactionRef.key;
      const notificationId = notificationRef.key;

      const challengeResult = await challengeRef.transaction((current) => {
        if (!current) return current;
        if (current.rewarded === true) return;
        if (current.completed !== true) return current;

        const currentReward =
          typeof current.reward === "number" ? current.reward : rewardCoins;

        if (currentReward !== rewardCoins) return current;

        return {
          ...current,
          rewarded: true,
          rewardTransactionId: transactionId,
          rewardNotificationId: notificationId,
          rewardedAt: admin.database.ServerValue.TIMESTAMP,
        };
      });

      if (!challengeResult.committed) {
        return {rewarded: false};
      }

      const walletResult = await walletCoinsRef.transaction((currentCoins) => {
        const safeCurrentCoins =
          typeof currentCoins === "number" ? currentCoins : 0;

        return safeCurrentCoins + rewardCoins;
      });

      if (!walletResult.committed) {
        await challengeRef.update({
          rewarded: false,
          rewardStatus: "wallet_failed",
          updatedAt: admin.database.ServerValue.TIMESTAMP,
        });

        throw new HttpsError(
            "aborted",
            "Could not credit daily challenge reward.",
        );
      }

      const updates = {};

      updates["coin_transactions/" + uid + "/" + transactionId] = {
        title: "Daily Challenge Reward",
        type: "daily_challenge_reward",
        action: action,
        amount: 0,
        coins: rewardCoins,
        date: today,
        status: "completed",
        transactionId: transactionId,
        referenceNo: "DAILY-" + today + "-" + transactionId,
        timestamp: admin.database.ServerValue.TIMESTAMP,
      };

      updates["notifications/" + uid + "/" + notificationId] = {
        type: "coins",
        coins: rewardCoins,
        message:
          "ðŸŽ‰ You earned +" +
          rewardCoins +
          " coins from a daily challenge!",
        read: false,
        timestamp: admin.database.ServerValue.TIMESTAMP,
      };

      await db.ref().update(updates);

      return {
        rewarded: true,
        coins: rewardCoins,
      };
    },
);

module.exports = {
  claimDailyChallengeReward,
};
