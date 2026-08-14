const {onCall, HttpsError} = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

const DAY_MS = 24 * 60 * 60 * 1000;

const PLANS = {
  "1_month": {cost: 50, durationMs: 30 * DAY_MS},
  "5_months": {cost: 100, durationMs: 150 * DAY_MS},
  "1_year": {cost: 200, durationMs: 365 * DAY_MS},
};

exports.activatePremium = onCall(
    {region: "us-central1"},
    async (request) => {
      try {
        if (!request.auth || !request.auth.uid) {
          throw new HttpsError(
              "unauthenticated",
              "You must be signed in to activate premium.",
          );
        }

        const uid = request.auth.uid;
        const data = request.data || {};
        const planId = data.planId;
        const plan = PLANS[planId];

        logger.info("activatePremium request", {
          uid: uid,
          planId: planId,
        });

        if (!plan) {
          throw new HttpsError("invalid-argument", "Invalid premium plan.");
        }

        const userRef = admin.database().ref("users/" + uid);
        const userSnap = await userRef.once("value");

        if (!userSnap.exists()) {
          throw new HttpsError("not-found", "User profile was not found.");
        }

        const startingUser = userSnap.val() || {};
        const startingWallet = startingUser.wallet || {};
        const startingCoins = Number(startingWallet.coins || 0);

        logger.info("Direct wallet read", {
          uid: uid,
          walletCoins: startingWallet.coins,
          type: typeof startingWallet.coins,
        });

        if (!Number.isFinite(startingCoins) || startingCoins < plan.cost) {
          throw new HttpsError(
              "failed-precondition",
              "Insufficient coins for this premium plan.",
          );
        }

        let newBalance = 0;
        let newExpiry = 0;
        const now = Date.now();

        const premiumResult = await userRef.transaction((currentUser) => {
          const baseUser = currentUser === null ? startingUser : currentUser;
          const wallet = baseUser.wallet || {};
          const coins = Number(wallet.coins || 0);

          logger.info("Premium transaction read", {
            uid: uid,
            usedFallback: currentUser === null,
            coins: coins,
            cost: plan.cost,
          });

          if (!Number.isFinite(coins) || coins < plan.cost) {
            return;
          }

          const currentExpiry = Number(baseUser.premiumExpiry || 0);
          const isPremium = baseUser.isPremium === true;
          const hasActivePremium = isPremium && currentExpiry > now;
          const expiryBase = hasActivePremium ? currentExpiry : now;

          newBalance = coins - plan.cost;
          newExpiry = expiryBase + plan.durationMs;

          return Object.assign({}, baseUser, {
            wallet: Object.assign({}, wallet, {
              coins: newBalance,
            }),
            isPremium: true,
            premiumExpiry: newExpiry,
            updatedAt: now,
          });
        });

        logger.info("Premium transaction result", {
          uid: uid,
          committed: premiumResult.committed,
          newBalance: newBalance,
          premiumExpiry: newExpiry,
        });

        if (!premiumResult.committed) {
          throw new HttpsError(
              "failed-precondition",
              "Insufficient coins for this premium plan.",
          );
        }

        const transactionRef = admin.database()
            .ref("premium_transactions/" + uid)
            .push();

        const updates = {};
        updates["public_users/" + uid + "/isPremium"] = true;
        updates["public_users/" + uid + "/premiumExpiry"] = newExpiry;
        updates["public_users/" + uid + "/updatedAt"] = now;

        updates[
            "premium_transactions/" + uid + "/" + transactionRef.key
        ] = {
          planId: planId,
          cost: plan.cost,
          coinsDeducted: plan.cost,
          newBalance: newBalance,
          premiumExpiry: newExpiry,
          type: "premium_activation",
          status: "completed",
          timestamp: now,
        };

        await admin.database().ref().update(updates);

        logger.info("Premium activated successfully", {
          uid: uid,
          planId: planId,
          newBalance: newBalance,
          premiumExpiry: newExpiry,
        });

        return {
          success: true,
          planId: planId,
          coinsDeducted: plan.cost,
          newBalance: newBalance,
          premiumExpiry: newExpiry,
        };
      } catch (error) {
        logger.error("activatePremium failed", error);

        if (error instanceof HttpsError) {
          throw error;
        }

        throw new HttpsError(
            "internal",
            error.message || "Could not activate premium. Please try again.",
        );
      }
    },
);
