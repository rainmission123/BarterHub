const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const DAY_MS = 24 * 60 * 60 * 1000;

const PREMIUM_PLANS = Object.freeze({
  "1_month": {
    cost: 50,
    durationMs: 30 * DAY_MS
  },
  "5_months": {
    cost: 100,
    durationMs: 150 * DAY_MS
  },
  "1_year": {
    cost: 200,
    durationMs: 365 * DAY_MS
  }
});

exports.activatePremium = functions
  .region("us-central1")
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "You must be signed in to activate premium."
      );
    }

    const uid = context.auth.uid;
    const planId = typeof data?.planId === "string" ? data.planId : "";
    const plan = PREMIUM_PLANS[planId];

    if (!plan) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Invalid premium plan."
      );
    }

    const userRef = admin.database().ref(`users/${uid}`);
    const now = Date.now();
    let newBalance = 0;
    let newExpiry = 0;

    const result = await userRef.transaction((currentUser) => {
      if (currentUser === null) {
        return;
      }

      const wallet = currentUser.wallet || {};
      const currentCoins = Number(wallet.coins || 0);

      if (!Number.isFinite(currentCoins) || currentCoins < plan.cost) {
        return;
      }

      const currentExpiry = Number(currentUser.premiumExpiry || 0);
      const hasActivePremium = currentUser.isPremium === true && currentExpiry > now;
      newExpiry = (hasActivePremium ? currentExpiry : now) + plan.durationMs;
      newBalance = currentCoins - plan.cost;

      return {
        ...currentUser,
        wallet: {
          ...wallet,
          coins: newBalance
        },
        isPremium: true,
        premiumExpiry: newExpiry,
        updatedAt: now
      };
    });

    if (!result.committed) {
      const snapshot = await userRef.once("value");
      if (!snapshot.exists()) {
        throw new functions.https.HttpsError(
          "not-found",
          "User profile was not found."
        );
      }

      const currentCoins = Number(snapshot.child("wallet/coins").val() || 0);

      if (currentCoins < plan.cost) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "Insufficient coins for this premium plan."
        );
      }

      throw new functions.https.HttpsError(
        "aborted",
        "Could not activate premium. Please try again."
      );
    }

    await admin.database().ref(`public_users/${uid}`).update({
      isPremium: true,
      premiumExpiry: newExpiry,
      updatedAt: now
    });

    await admin.database().ref(`premium_transactions/${uid}`).push({
      planId,
      cost: plan.cost,
      premiumExpiry: newExpiry,
      type: "premium_activation",
      status: "completed",
      timestamp: now
    });

    return {
      success: true,
      planId,
      coinsDeducted: plan.cost,
      newBalance,
      premiumExpiry: newExpiry
    };
  });
