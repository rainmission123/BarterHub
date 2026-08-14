/* eslint-disable require-jsdoc, max-len, indent */

const crypto = require("crypto");
const {onRequest} = require("firebase-functions/v2/https");
const {defineSecret} = require("firebase-functions/params");
const admin = require("firebase-admin");

const PAYMONGO_WEBHOOK_SECRET = defineSecret("PAYMONGO_WEBHOOK_SECRET");
const PROCESSING_TIMEOUT_MS = 5 * 60 * 1000;
const SIGNATURE_TOLERANCE_SECONDS = 5 * 60;

function toNumber(value, fallback = 0) {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function getRawBody(req) {
  if (req.rawBody) {
    return req.rawBody.toString("utf8");
  }
  return JSON.stringify(req.body || {});
}

function parseSignatureHeader(header) {
  const parsed = {};

  String(header || "")
      .split(",")
      .map((part) => part.trim())
      .filter(Boolean)
      .forEach((part) => {
        const index = part.indexOf("=");
        if (index <= 0) return;

        const key = part.slice(0, index);
        const value = part.slice(index + 1);
        parsed[key] = value;
      });

  return parsed;
}

function safeTimingEqual(left, right) {
  try {
    const leftBuffer = Buffer.from(String(left), "hex");
    const rightBuffer = Buffer.from(String(right), "hex");

    if (leftBuffer.length !== rightBuffer.length) {
      return false;
    }

    return crypto.timingSafeEqual(leftBuffer, rightBuffer);
  } catch (error) {
    return false;
  }
}

function verifyPaymongoSignature(req) {
  const secret = PAYMONGO_WEBHOOK_SECRET.value();

  if (!secret) {
    console.error("PAYMONGO_WEBHOOK_SECRET is not configured.");
    return false;
  }

  const header =
    req.get("paymongo-signature") ||
    req.get("Paymongo-Signature") ||
    "";

  const parsed = parseSignatureHeader(header);
  const timestamp = parsed.t;
  const signatures = [parsed.v1, parsed.te, parsed.li].filter(Boolean);

  if (!timestamp || signatures.length === 0) {
    console.error("Missing PayMongo signature timestamp/signature.");
    return false;
  }

  const timestampNumber = Number(timestamp);
  const nowSeconds = Math.floor(Date.now() / 1000);
  if (
    !Number.isFinite(timestampNumber) ||
    Math.abs(nowSeconds - timestampNumber) > SIGNATURE_TOLERANCE_SECONDS
  ) {
    console.error("PayMongo signature timestamp is stale or invalid.");
    return false;
  }

  const rawBody = getRawBody(req);
  const expectedSignature = crypto
      .createHmac("sha256", secret)
      .update(`${timestamp}.${rawBody}`)
      .digest("hex");

  return signatures.some((signature) => {
    return safeTimingEqual(signature, expectedSignature);
  });
}

function getEventPayload(event) {
  const attributes =
    event &&
    event.data &&
    event.data.attributes ?
      event.data.attributes :
      {};

  const paymentObject =
    attributes &&
    attributes.data ?
      attributes.data :
      {};

  const paymentAttributes =
    paymentObject &&
    paymentObject.attributes ?
      paymentObject.attributes :
      {};

  return {
    eventId: event && event.data && event.data.id || "",
    eventType: attributes.type || "",
    checkoutSessionId: paymentObject.id || "",
    paymentIntentId:
      paymentAttributes.payment_intent &&
      paymentAttributes.payment_intent.id ||
      paymentAttributes.payment_intent_id ||
      "",
    referenceNo:
      paymentAttributes.reference_number ||
      attributes.reference_number ||
      "",
    metadata:
      paymentAttributes.metadata ||
      attributes.metadata ||
      {},
  };
}

async function findPaymentByPayload(db, payload) {
  const metadata = payload.metadata || {};
  let paymentId = metadata.paymentId || "";

  if (paymentId) {
    const snap = await db.ref(`coin_payments/${paymentId}`).get();

    if (snap.exists()) {
      return {
        paymentId,
        payment: snap.val() || {},
      };
    }
  }

  if (payload.checkoutSessionId) {
    const sessionSnap = await db
        .ref(`paymongo_checkout_sessions/${payload.checkoutSessionId}`)
        .get();

    if (sessionSnap.exists()) {
      const payment = sessionSnap.val() || {};
      paymentId = payment.paymentId || paymentId;

      if (paymentId) {
        return {
          paymentId,
          payment,
        };
      }
    }
  }

  if (payload.referenceNo) {
    const querySnap = await db
        .ref("coin_payments")
        .orderByChild("referenceNo")
        .equalTo(payload.referenceNo)
        .limitToFirst(1)
        .get();

    if (querySnap.exists()) {
      const payments = querySnap.val() || {};
      paymentId = Object.keys(payments)[0];

      return {
        paymentId,
        payment: payments[paymentId] || {},
      };
    }
  }

  return {
    paymentId: "",
    payment: null,
  };
}

exports.paymongoWebhook = onRequest(
    {
      secrets: [PAYMONGO_WEBHOOK_SECRET],
    },
    async (req, res) => {
      if (req.method !== "POST") {
        res.status(405).send("Method not allowed");
        return;
      }

      if (!verifyPaymongoSignature(req)) {
        res.status(401).send("Invalid signature");
        return;
      }

      const db = admin.database();
      const payload = getEventPayload(req.body || {});

      if (!String(payload.eventType || "").includes("paid")) {
        res.status(200).send("Ignored");
        return;
      }

      const found = await findPaymentByPayload(db, payload);
      const paymentId = found.paymentId;
      const payment = found.payment;

      if (!paymentId || !payment) {
        res.status(400).send("Payment record not found");
        return;
      }

      const uid = payment.uid || payment.userId || "";
      const coins = toNumber(payment.coins);
      const amount = toNumber(payment.amount);
      const currency = payment.currency || "PHP";
      const transactionId = `paymongo_${paymentId}`;
      const referenceNo =
        payment.referenceNo ||
        payload.referenceNo ||
        payload.metadata.referenceNo ||
        paymentId;

      if (!uid || coins <= 0 || amount <= 0) {
        res.status(400).send("Invalid payment record");
        return;
      }

      const lockStartedAt = Date.now();
      const processedRef = db.ref(`processed_paymongo_payments/${paymentId}`);

      const lockResult = await processedRef.transaction((current) => {
        if (current && current.status === "completed") {
          return;
        }

        const currentStartedAt = toNumber(
            current && current.processingStartedAt,
        );

        const isFreshProcessing = current &&
          current.status === "processing" &&
          lockStartedAt - currentStartedAt < PROCESSING_TIMEOUT_MS;

        if (isFreshProcessing) {
          return;
        }

        return {
          paymentId,
          uid,
          userId: uid,
          coins,
          amount,
          currency,
          provider: "paymongo",
          status: "processing",
          webhookEventId: payload.eventId || "",
          checkoutSessionId:
            payload.checkoutSessionId ||
            payment.checkoutSessionId ||
            "",
          paymentIntentId: payload.paymentIntentId || "",
          referenceNo,
          transactionId,
          processingStartedAt: lockStartedAt,
          updatedAt: lockStartedAt,
        };
      }, undefined, false);

      if (!lockResult.committed) {
        res.status(200).send("Already processing or processed");
        return;
      }

      const existingCoinTxn = await db
          .ref(`coin_transactions/${uid}/${transactionId}`)
          .get();
      const existingLegacyTxn = await db
          .ref(`transactions/${transactionId}`)
          .get();

      if (existingCoinTxn.exists() || existingLegacyTxn.exists()) {
        await processedRef.update({
          status: "completed",
          updatedAt: admin.database.ServerValue.TIMESTAMP,
        });

        res.status(200).send("Already credited");
        return;
      }

      const sessionId =
        payload.checkoutSessionId ||
        payment.checkoutSessionId ||
        "";

      const updates = {};
      updates[`users/${uid}/wallet/coins`] =
        admin.database.ServerValue.increment(coins);

      updates[`coin_payments/${paymentId}/status`] = "completed";
      updates[`coin_payments/${paymentId}/webhookEventId`] =
        payload.eventId || "";
      updates[`coin_payments/${paymentId}/paymentIntentId`] =
        payload.paymentIntentId || "";
      updates[`coin_payments/${paymentId}/paidAt`] =
        admin.database.ServerValue.TIMESTAMP;
      updates[`coin_payments/${paymentId}/updatedAt`] =
        admin.database.ServerValue.TIMESTAMP;

      if (sessionId) {
        updates[`paymongo_checkout_sessions/${sessionId}/status`] =
          "completed";
        updates[`paymongo_checkout_sessions/${sessionId}/paidAt`] =
          admin.database.ServerValue.TIMESTAMP;
        updates[`paymongo_checkout_sessions/${sessionId}/updatedAt`] =
          admin.database.ServerValue.TIMESTAMP;
      }

      const transaction = {
        title: "Coin Purchase",
        type: "purchase",
        provider: "paymongo",
        paymentId,
        checkoutSessionId: sessionId,
        paymentIntentId: payload.paymentIntentId || "",
        coins,
        amount,
        currency,
        status: "completed",
        transactionId,
        referenceNo,
        timestamp: admin.database.ServerValue.TIMESTAMP,
      };

      updates[`coin_transactions/${uid}/${transactionId}`] = transaction;
      updates[`transactions/${transactionId}`] = Object.assign(
          {userId: uid},
          transaction,
      );

      updates[`processed_paymongo_payments/${paymentId}`] = {
        paymentId,
        uid,
        userId: uid,
        coins,
        amount,
        currency,
        provider: "paymongo",
        status: "completed",
        webhookEventId: payload.eventId || "",
        checkoutSessionId: sessionId,
        paymentIntentId: payload.paymentIntentId || "",
        referenceNo,
        transactionId,
        processedAt: admin.database.ServerValue.TIMESTAMP,
        updatedAt: admin.database.ServerValue.TIMESTAMP,
      };

      try {
        await db.ref().update(updates);

        console.log("PayMongo payment credited", {
          paymentId,
          uid,
          transactionId,
        });

        res.status(200).send("OK");
      } catch (error) {
        console.error("PayMongo multi-location update failed", {
          paymentId,
          uid,
          transactionId,
          message: error.message,
        });

        await processedRef.transaction((current) => {
          if (!current || current.status === "completed") {
            return current;
          }

          return Object.assign({}, current, {
            status: "failed_retryable",
            reason: "multi_location_update_failed",
            updatedAt: Date.now(),
          });
        }, undefined, false);

        res.status(500).send("Webhook processing failed");
      }
    },
);
