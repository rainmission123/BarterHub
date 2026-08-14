"use strict";

const crypto = require("crypto");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const {google} = require("googleapis");

const db = admin.database();

const PACKAGE_NAME = "com.jorian.barterhub";
const ANDROID_PUBLISHER_SCOPE =
  "https://www.googleapis.com/auth/androidpublisher";
const PROCESSING_TIMEOUT_MS = 5 * 60 * 1000;

const GOOGLE_PLAY_PRODUCTS = {
  barter_coins_100: {
    coins: 100,
    amount: 50,
    currency: "PHP",
  },
  barter_coins_200: {
    coins: 200,
    amount: 100,
    currency: "PHP",
  },
  barter_coins_500: {
    coins: 500,
    amount: 250,
    currency: "PHP",
  },
};

function sha256Lowercase(value) {
  return crypto
    .createHash("sha256")
    .update(String(value))
    .digest("hex")
    .toLowerCase();
}

function asNumber(value, fallback = 0) {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function requireString(value, fieldName, maxLength) {
  const result = typeof value === "string" ? value.trim() : "";

  if (!result || result.length > maxLength) {
    throw new HttpsError(
      "invalid-argument",
      `${fieldName} is invalid.`
    );
  }

  return result;
}

function apiStatus(error) {
  return Number(
    error &&
    error.response &&
    error.response.status
  ) || 0;
}

function normalizeState(value) {
  return String(value || "")
    .trim()
    .toUpperCase()
    .replace(/^PURCHASE_STATE_/, "");
}

function normalizeConsumptionState(value) {
  return String(value || "")
    .trim()
    .toUpperCase()
    .replace(/^CONSUMPTION_STATE_/, "");
}

function safeExistingResponse(record) {
  if (!record || typeof record !== "object") {
    return null;
  }

  if (record.status === "completed") {
    return {
      status: "already_processed",
      transactionId: record.transactionId || null,
      coins: asNumber(record.coins),
      finalCoins: asNumber(record.finalCoins),
    };
  }

  if (
    record.status === "credited_pending_consume" ||
    record.status === "credited_consume_failed_retryable"
  ) {
    return {
      status: record.status,
      transactionId: record.transactionId || null,
      coins: asNumber(record.coins),
      finalCoins: asNumber(record.finalCoins),
    };
  }

  return null;
}

async function getAuthorizedClient() {
  const auth = new google.auth.GoogleAuth({
    scopes: [ANDROID_PUBLISHER_SCOPE],
  });

  return auth.getClient();
}

function productV2Url(purchaseToken) {
  return (
    "https://androidpublisher.googleapis.com/androidpublisher/v3/" +
    `applications/${encodeURIComponent(PACKAGE_NAME)}/` +
    "purchases/productsv2/tokens/" +
    encodeURIComponent(purchaseToken)
  );
}

function consumeUrl(productId, purchaseToken) {
  return (
    "https://androidpublisher.googleapis.com/androidpublisher/v3/" +
    `applications/${encodeURIComponent(PACKAGE_NAME)}/` +
    `purchases/products/${encodeURIComponent(productId)}/tokens/` +
    `${encodeURIComponent(purchaseToken)}:consume`
  );
}

async function getGooglePlayPurchase(client, purchaseToken) {
  const response = await client.request({
    method: "GET",
    url: productV2Url(purchaseToken),
  });

  return response.data || {};
}

function isPurchaseConsumed(purchase) {
  const lineItems = Array.isArray(purchase.productLineItem) ?
    purchase.productLineItem :
    [];

  return (
    lineItems.length > 0 &&
    lineItems.every((lineItem) => {
      return normalizeConsumptionState(
        lineItem && lineItem.consumptionState
      ) === "CONSUMED";
    })
  );
}

async function consumeGooglePlayPurchase(
  client,
  productId,
  purchaseToken
) {
  try {
    await client.request({
      method: "POST",
      url: consumeUrl(productId, purchaseToken),
    });

    return true;
  } catch (consumeError) {
    try {
      const purchase = await getGooglePlayPurchase(
        client,
        purchaseToken
      );

      if (isPurchaseConsumed(purchase)) {
        return true;
      }
    } catch (verificationError) {
      console.error("Unable to confirm consumption state", {
        consumeStatus: apiStatus(consumeError),
        verificationStatus: apiStatus(verificationError),
      });
    }

    throw consumeError;
  }
}

function validateVerifiedPurchase(
  purchase,
  uid,
  expectedProductId
) {
  const state = normalizeState(
    purchase &&
    purchase.purchaseStateContext &&
    purchase.purchaseStateContext.purchaseState
  );

  if (state === "PENDING") {
    return {
      status: "pending",
    };
  }

  if (state !== "PURCHASED") {
    return {
      status: "invalid",
    };
  }

const expectedAccountId = sha256Lowercase(uid);
const actualAccountId = String(
  purchase.obfuscatedExternalAccountId || ""
).toLowerCase();

console.log("Google Play account validation", {
  uidHashPrefix: expectedAccountId.substring(0, 10),
  actualAccountIdPrefix: actualAccountId.substring(0, 10),
  hasActualAccountId: Boolean(actualAccountId),
  accountIdMatches: actualAccountId === expectedAccountId,
  expectedProductId,
});

if (
  !actualAccountId ||
  actualAccountId !== expectedAccountId
) {
  return {
    status: "permission-denied",
  };
}

  const lineItems = Array.isArray(purchase.productLineItem) ?
    purchase.productLineItem :
    [];

  const matchingItems = lineItems.filter((lineItem) => {
    return (
      lineItem &&
      String(lineItem.productId || "") === expectedProductId
    );
  });

  if (matchingItems.length !== 1 || lineItems.length !== 1) {
    return {
      status: "invalid",
    };
  }

  const lineItem = matchingItems[0];
  const quantity = asNumber(lineItem.quantity, 1);

  if (quantity !== 1) {
    return {
      status: "invalid",
    };
  }

  const purchaseOptionId = String(
    lineItem.productOfferDetails &&
    lineItem.productOfferDetails.purchaseOptionId ||
    ""
  );

  if (purchaseOptionId && purchaseOptionId !== "standard") {
    return {
      status: "invalid",
    };
  }

  return {
    status: "verified",
    orderId: String(purchase.orderId || ""),
    consumed: isPurchaseConsumed(purchase),
  };
}

async function readFinalCoins(uid) {
  const snapshot = await db
    .ref(`users/${uid}/wallet/coins`)
    .once("value");

  return asNumber(snapshot.val());
}

async function finishConsumption({
  client,
  processedRef,
  productId,
  purchaseToken,
  transactionId,
  coins,
  uid,
}) {
  try {
    await consumeGooglePlayPurchase(
      client,
      productId,
      purchaseToken
    );

    const finalCoins = await readFinalCoins(uid);

    await processedRef.update({
      status: "completed",
      finalCoins,
      consumedAt: admin.database.ServerValue.TIMESTAMP,
      updatedAt: admin.database.ServerValue.TIMESTAMP,
    });

    return {
      status: "completed",
      transactionId,
      coins,
      finalCoins,
    };
  } catch (error) {
    console.error("Google Play consume failed", {
      uid,
      productId,
      transactionId,
      status: apiStatus(error),
      message: error.message,
    });

    const finalCoins = await readFinalCoins(uid);

    await processedRef.update({
      status: "credited_consume_failed_retryable",
      finalCoins,
      consumeErrorStatus: apiStatus(error),
      updatedAt: admin.database.ServerValue.TIMESTAMP,
    });

    return {
      status: "credited_consume_failed_retryable",
      transactionId,
      coins,
      finalCoins,
    };
  }
}

exports.verifyGooglePlayCoinPurchase = onCall(
  {
    region: "us-central1",
  },
  async (request) => {
    const data = request.data || {};

    if (!request.auth || !request.auth.uid) {
      throw new HttpsError(
        "unauthenticated",
        "Please sign in to continue."
      );
    }

    const uid = request.auth.uid;

    console.log("Google Play callable input received", {
      hasData: Boolean(data),
      dataType: typeof data,
      dataKeys:
        data && typeof data === "object"
          ? Object.keys(data)
          : [],
      hasProductId: Boolean(
        data &&
        typeof data.productId === "string" &&
        data.productId.trim()
      ),
      productId:
        data && typeof data.productId === "string"
          ? data.productId
          : "",
      hasPurchaseToken: Boolean(
        data &&
        typeof data.purchaseToken === "string" &&
        data.purchaseToken.trim()
      ),
      purchaseTokenLength:
        data && typeof data.purchaseToken === "string"
          ? data.purchaseToken.length
          : 0,
    });
    const productId = requireString(
      data && data.productId,
      "productId",
      100
    );
    const purchaseToken = requireString(
      data && data.purchaseToken,
      "purchaseToken",
      4096
    );
    const selectedProduct = GOOGLE_PLAY_PRODUCTS[productId];

    if (!selectedProduct) {
      return {
        status: "invalid",
      };
    }

    const purchaseTokenHash = sha256Lowercase(purchaseToken);
    const processedRef = db.ref(
      `processed_google_play_purchases/${purchaseTokenHash}`
    );

const initialSnapshot = await processedRef.once("value");
const initialRecord = initialSnapshot.val();

console.log("Google Play initial purchase record", {
  purchaseTokenHashPrefix: purchaseTokenHash.substring(0, 10),
  hasInitialRecord: Boolean(initialRecord),
  currentUidHashPrefix: sha256Lowercase(uid).substring(0, 10),
  recordUidHashPrefix:
    initialRecord && initialRecord.uid
      ? sha256Lowercase(initialRecord.uid).substring(0, 10)
      : "",
  requestedProductId: productId,
  recordProductId:
    initialRecord
      ? String(initialRecord.productId || "")
      : "",
  recordStatus:
    initialRecord
      ? String(initialRecord.status || "")
      : "",
  uidMatches: Boolean(
    initialRecord && initialRecord.uid === uid
  ),
  productMatches: Boolean(
    initialRecord && initialRecord.productId === productId
  ),
});

if (
  initialRecord &&
  (
    initialRecord.uid !== uid ||
    initialRecord.productId !== productId
  )
) {
  return {
    status: "permission-denied",
  };
}

    const existingResponse = safeExistingResponse(initialRecord);

    if (
      existingResponse &&
      existingResponse.status === "already_processed"
    ) {
      return existingResponse;
    }

    let client;

    try {
      client = await getAuthorizedClient();
    } catch (error) {
      console.error("Google authorization failed", {
        message: error.message,
      });

      throw new HttpsError(
        "internal",
        "Purchase verification is temporarily unavailable."
      );
    }

    if (
      existingResponse &&
      (
        existingResponse.status === "credited_pending_consume" ||
        existingResponse.status ===
          "credited_consume_failed_retryable"
      )
    ) {
      return finishConsumption({
        client,
        processedRef,
        productId,
        purchaseToken,
        transactionId: existingResponse.transactionId,
        coins: existingResponse.coins,
        uid,
      });
    }

    let purchase;

    try {
      purchase = await getGooglePlayPurchase(
        client,
        purchaseToken
      );
    } catch (error) {
      const status = apiStatus(error);

      console.error("Google Play verification request failed", {
        uid,
        productId,
        status,
        message: error.message,
      });

      if (status === 401 || status === 403) {
        return {
          status: "permission-denied",
        };
      }

      if (status === 400 || status === 404) {
        return {
          status: "invalid",
        };
      }

      throw new HttpsError(
        "unavailable",
        "Purchase verification is temporarily unavailable."
      );
    }

    const validation = validateVerifiedPurchase(
      purchase,
      uid,
      productId
    );

    if (validation.status !== "verified") {
      return {
        status: validation.status,
      };
    }

    if (validation.consumed) {
      return {
        status: "invalid",
      };
    }

    const transactionId =
      `googleplay_${purchaseTokenHash.substring(0, 32)}`;
    const now = Date.now();

    const lockResult = await processedRef.transaction(
      (current) => {
        if (
          current &&
          (
            current.uid !== uid ||
            current.productId !== productId
          )
        ) {
          return;
        }

        if (
          current &&
          (
            current.status === "completed" ||
            current.status === "credited_pending_consume" ||
            current.status ===
              "credited_consume_failed_retryable"
          )
        ) {
          return;
        }

        const processingStartedAt = asNumber(
          current && current.processingStartedAt
        );
        const isFreshProcessing =
          current &&
          current.status === "processing" &&
          now - processingStartedAt < PROCESSING_TIMEOUT_MS;

        if (isFreshProcessing) {
          return;
        }

        return {
          purchaseTokenHash,
          uid,
          userId: uid,
          productId,
          purchaseOptionId: "standard",
          coins: selectedProduct.coins,
          amount: selectedProduct.amount,
          currency: selectedProduct.currency,
          provider: "google_play",
          packageName: PACKAGE_NAME,
          orderId: validation.orderId,
          transactionId,
          status: "processing",
          processingStartedAt: now,
          updatedAt: now,
        };
      },
      undefined,
      false
    );

    if (!lockResult.committed) {
      const latestSnapshot = await processedRef.once("value");
      const latest = latestSnapshot.val();

      if (
        latest &&
        (
          latest.uid !== uid ||
          latest.productId !== productId
        )
      ) {
        return {
          status: "permission-denied",
        };
      }

      const latestResponse = safeExistingResponse(latest);

      if (latestResponse) {
        return latestResponse;
      }

      return {
        status: "processing",
      };
    }

    const existingTransaction = await db
      .ref(`coin_transactions/${uid}/${transactionId}`)
      .once("value");

    if (!existingTransaction.exists()) {
      const referenceNo =
        validation.orderId ||
        `GP-${purchaseTokenHash.substring(0, 16).toUpperCase()}`;

      const transaction = {
        title: "Coin Purchase",
        type: "purchase",
        provider: "google_play",
        productId,
        purchaseOptionId: "standard",
        purchaseTokenHash,
        orderId: validation.orderId,
        coins: selectedProduct.coins,
        amount: selectedProduct.amount,
        currency: selectedProduct.currency,
        status: "completed",
        transactionId,
        referenceNo,
        timestamp: admin.database.ServerValue.TIMESTAMP,
      };

      const updates = {};

      updates[`users/${uid}/wallet/coins`] =
        admin.database.ServerValue.increment(
          selectedProduct.coins
        );
      updates[`coin_transactions/${uid}/${transactionId}`] =
        transaction;
      updates[`transactions/${transactionId}`] =
        Object.assign(
          {
            userId: uid,
          },
          transaction
        );
      updates[
        `processed_google_play_purchases/${purchaseTokenHash}`
      ] = {
        purchaseTokenHash,
        uid,
        userId: uid,
        productId,
        purchaseOptionId: "standard",
        coins: selectedProduct.coins,
        amount: selectedProduct.amount,
        currency: selectedProduct.currency,
        provider: "google_play",
        packageName: PACKAGE_NAME,
        orderId: validation.orderId,
        transactionId,
        status: "credited_pending_consume",
        creditedAt: admin.database.ServerValue.TIMESTAMP,
        updatedAt: admin.database.ServerValue.TIMESTAMP,
      };

      try {
        await db.ref().update(updates);
      } catch (error) {
        console.error("Google Play credit update failed", {
          uid,
          productId,
          transactionId,
          message: error.message,
        });

        await processedRef.transaction(
          (current) => {
            if (
              !current ||
              current.status !== "processing"
            ) {
              return current;
            }

            return Object.assign({}, current, {
              status: "failed_retryable",
              reason: "multi_location_update_failed",
              updatedAt: Date.now(),
            });
          },
          undefined,
          false
        );

        throw new HttpsError(
          "internal",
          "Unable to credit the purchase."
        );
      }
    } else {
      await processedRef.update({
        status: "credited_pending_consume",
        creditedAt: admin.database.ServerValue.TIMESTAMP,
        updatedAt: admin.database.ServerValue.TIMESTAMP,
      });
    }

    return finishConsumption({
      client,
      processedRef,
      productId,
      purchaseToken,
      transactionId,
      coins: selectedProduct.coins,
      uid,
    });
  });
