const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {defineSecret} = require("firebase-functions/params");
const admin = require("firebase-admin");

const paymongoSecretKey = defineSecret("PAYMONGO_SECRET_KEY");

const PAYMONGO_CHECKOUT_URL = "https://api.paymongo.com/v1/checkout_sessions";
const CHECKOUT_SUCCESS_URL = "https://barterhub-3c947.web.app/payment-success";
const CHECKOUT_CANCEL_URL = "https://barterhub-3c947.web.app/payment-cancel";

const COIN_PACKAGES = {
  coin_100: {coins: 100, amount: 50},
  coin_200: {coins: 200, amount: 100},
  coin_500: {coins: 500, amount: 250},
};

/**
 * Creates a secure PayMongo checkout session for coin purchases.
 *
 * @param {object} request Gen 2 callable request.
 * @return {Promise<object>} Checkout session result.
 */
exports.createCoinCheckout = onCall(
    {
      region: "us-central1",
      secrets: [paymongoSecretKey],
    },
    async (request) => {
      const data = request.data || {};
      const uid = request.auth && request.auth.uid;

      if (!uid) {
        throw new HttpsError(
            "unauthenticated",
            "Please log in to continue.",
        );
      }

      const packageId = String(data.packageId || "");
      const paymentMethod = String(data.paymentMethod || "");
      const currency = String(data.currency || "PHP").toUpperCase();
      const coins = Number(data.coins || 0);
      const amount = Number(data.amount || 0);

      const selectedPackage = COIN_PACKAGES[packageId];
      const allowedPaymentMethods = ["gcash", "grab_pay", "card"];

      if (!selectedPackage) {
        throw new HttpsError(
            "invalid-argument",
            "Invalid coin package.",
        );
      }

      if (
        selectedPackage.coins !== coins ||
        selectedPackage.amount !== amount ||
        currency !== "PHP" ||
        !allowedPaymentMethods.includes(paymentMethod)
      ) {
        throw new HttpsError(
            "invalid-argument",
            "Invalid payment request.",
        );
      }

      const secretKey = paymongoSecretKey.value();

      if (!secretKey) {
        throw new HttpsError(
            "failed-precondition",
            "Payment service is not configured.",
        );
      }

      const db = admin.database();
      const paymentRef = db.ref("coin_payments").push();
      const paymentId = paymentRef.key;
      const referenceNo =
        `COIN-${Date.now()}-${paymentId.slice(-6).toUpperCase()}`;
      const amountInCentavos = Math.round(selectedPackage.amount * 100);

      const checkoutPayload = {
        data: {
          attributes: {
            description: `${selectedPackage.coins} Barter Coins`,
            reference_number: referenceNo,
            payment_method_types: [paymentMethod],
            line_items: [
              {
                name: `${selectedPackage.coins} Barter Coins`,
                quantity: 1,
                amount: amountInCentavos,
                currency: currency,
                description:
                  `BarterHub ${selectedPackage.coins} coin package`,
              },
            ],
            metadata: {
              uid: uid,
              userId: uid,
              paymentId: paymentId,
              referenceNo: referenceNo,
              packageId: packageId,
              coins: String(selectedPackage.coins),
              amount: String(selectedPackage.amount),
              currency: currency,
              type: "coin_purchase",
            },
            success_url: CHECKOUT_SUCCESS_URL,
            cancel_url: CHECKOUT_CANCEL_URL,
          },
        },
      };

      const authHeader = Buffer.from(`${secretKey}:`).toString("base64");

      const response = await fetch(PAYMONGO_CHECKOUT_URL, {
        method: "POST",
        headers: {
          "Authorization": `Basic ${authHeader}`,
          "Content-Type": "application/json",
          "Accept": "application/json",
        },
        body: JSON.stringify(checkoutPayload),
      });

      const responseBody = await response.json().catch(() => ({}));

      if (!response.ok) {
        console.error(
            "PayMongo checkout failed",
            response.status,
            responseBody,
        );
        throw new HttpsError(
            "internal",
            "Unable to create checkout session. Please try again.",
        );
      }

      const checkoutSession = responseBody.data || {};
      const attributes = checkoutSession.attributes || {};
      const checkoutUrl = attributes.checkout_url;
      const checkoutSessionId = checkoutSession.id || "";

      if (!checkoutUrl) {
        console.error(
            "PayMongo checkout URL missing",
            responseBody,
        );
        throw new HttpsError(
            "internal",
            "Checkout URL was not returned. Please try again.",
        );
      }

      const pendingPayment = {
        paymentId: paymentId,
        checkoutSessionId: checkoutSessionId,
        referenceNo: referenceNo,
        uid: uid,
        userId: uid,
        packageId: packageId,
        coins: selectedPackage.coins,
        amount: selectedPackage.amount,
        amountInCentavos: amountInCentavos,
        currency: currency,
        paymentMethod: paymentMethod,
        provider: "paymongo",
        type: "coin_purchase",
        status: "pending",
        checkoutUrl: checkoutUrl,
        createdAt: admin.database.ServerValue.TIMESTAMP,
        updatedAt: admin.database.ServerValue.TIMESTAMP,
      };

      const updates = {};
      updates[`coin_payments/${paymentId}`] = pendingPayment;

      if (checkoutSessionId) {
        updates[`paymongo_checkout_sessions/${checkoutSessionId}`] =
          pendingPayment;
      }

      await db.ref().update(updates);

      return {
        checkoutUrl: checkoutUrl,
        paymentId: paymentId,
        checkoutSessionId: checkoutSessionId,
        referenceNo: referenceNo,
      };
    },
);
