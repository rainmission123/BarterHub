const {onCall, HttpsError} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

const REFERRAL_REWARD_COINS = 20;

const findInviterUidByReferralCode = async (db, referralCode) => {
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
};

const claimReferralReward = onCall(
    {region: "us-central1"},
    async (request) => {
      if (!request.auth || !request.auth.uid) {
        throw new HttpsError("unauthenticated", "You must be signed in.");
      }

      const authUid = request.auth.uid;
      const invitedUserId = request.data && request.data.invitedUserId;

      if (!invitedUserId || invitedUserId !== authUid) {
        throw new HttpsError(
            "permission-denied",
            "You can only claim your own referral completion.",
        );
      }

      const db = admin.database();
      const invitedUserRef = db.ref("users/" + invitedUserId);
      const invitedSnap = await invitedUserRef.get();

      if (!invitedSnap.exists()) {
        throw new HttpsError("not-found", "Invited user not found.");
      }

      const invitedUser = invitedSnap.val() || {};
      const referredBy = invitedUser.referredBy || "";
      const rewardGranted = invitedUser.referralRewardGranted === true;
      const tradesCompleted = Number(invitedUser.tradesCompleted || 0);

      if (!referredBy || rewardGranted || tradesCompleted !== 1) {
        return {rewarded: false};
      }

      const inviterUid = await findInviterUidByReferralCode(db, referredBy);

      if (!inviterUid || inviterUid === invitedUserId) {
        return {rewarded: false};
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
        return {rewarded: false};
      }

      const inviterCoinsRef = db.ref(
          "users/" + inviterUid + "/wallet/coins",
      );

      const walletResult = await inviterCoinsRef.transaction((currentCoins) => {
        const safeCurrentCoins =
          typeof currentCoins === "number" ? currentCoins : 0;

        return safeCurrentCoins + REFERRAL_REWARD_COINS;
      });

      if (!walletResult.committed) {
        await rewardFlagRef.set(false);

        throw new HttpsError(
            "aborted",
            "Could not credit referral reward.",
        );
      }

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

      return {
        rewarded: true,
        coins: REFERRAL_REWARD_COINS,
      };
    },
);

module.exports = {
  claimReferralReward,
};
