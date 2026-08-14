const {onCall, HttpsError} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

const claimPostItemReward = onCall(
    {
      region: "us-central1",
    },
    async (request) => {
      if (!request.auth || !request.auth.uid) {
        throw new HttpsError(
            "unauthenticated",
            "You must be signed in to claim the post item reward.",
        );
      }

      const uid = request.auth.uid;
      const rewardCoins = 2;
      const db = admin.database();

      const today = new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Manila",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      }).format(new Date());

      const rewardRef = db.ref("post_rewards/" + uid + "/" + today);
      const walletCoinsRef = db.ref("users/" + uid + "/wallet/coins");
      const coinTransactionRef = db.ref("coin_transactions/" + uid).push();
      const notificationRef = db.ref("notifications/" + uid).push();

      const transactionId = coinTransactionRef.key;
      const notificationId = notificationRef.key;

      const rewardResult = await rewardRef.transaction((currentReward) => {
        if (currentReward && currentReward.rewarded === true) {
          return;
        }

        return {
          rewarded: true,
          coins: rewardCoins,
          date: today,
          status: "wallet_pending",
          transactionId: transactionId,
          notificationId: notificationId,
          timestamp: admin.database.ServerValue.TIMESTAMP,
        };
      });

      if (!rewardResult.committed) {
        return {
          rewarded: false,
        };
      }

      const walletResult = await walletCoinsRef.transaction((currentCoins) => {
        const safeCurrentCoins =
          typeof currentCoins === "number" ? currentCoins : 0;

        return safeCurrentCoins + rewardCoins;
      });

      if (!walletResult.committed) {
        await rewardRef.update({
          status: "wallet_failed",
          error: "wallet_transaction_not_committed",
          updatedAt: admin.database.ServerValue.TIMESTAMP,
        });

        throw new HttpsError(
            "aborted",
            "Could not credit post item reward coins.",
        );
      }

      const updates = {};

      updates["post_rewards/" + uid + "/" + today + "/status"] = "completed";
      updates["post_rewards/" + uid + "/" + today + "/walletCreditedAt"] =
        admin.database.ServerValue.TIMESTAMP;

      updates["coin_transactions/" + uid + "/" + transactionId] = {
        title: "Post Item Reward",
        type: "post_item_reward",
        amount: 0,
        coins: rewardCoins,
        date: today,
        status: "completed",
        transactionId: transactionId,
        referenceNo: "POST-" + today + "-" + transactionId,
        timestamp: admin.database.ServerValue.TIMESTAMP,
      };

      updates["notifications/" + uid + "/" + notificationId] = {
        type: "coins",
        coins: rewardCoins,
        message: "🎉 You earned +2 coins from posting an item!",
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
  claimPostItemReward,
};
