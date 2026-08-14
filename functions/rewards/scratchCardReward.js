const {onCall, HttpsError} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const {getDatabase, ServerValue} = require("firebase-admin/database");

const DATABASE_URL = "https://barterhub-3c947-default-rtdb.firebaseio.com";
const SCRATCH_COST = 15;

const LOW_RESOURCE_OPTIONS = {
  cpu: 0.083,
  memory: "256MiB",
  minInstances: 0,
  maxInstances: 1,
};

/**
 * Converts wallet value to safe number.
 *
 * @param {*} value Wallet value.
 * @return {number} Safe coin number.
 */
function toCoinsNumber(value) {
  const coins = Number(value || 0);

  if (!Number.isFinite(coins)) {
    return 0;
  }

  return coins;
}

/**
 * Generates scratch card prize on server.
 *
 * @return {number} Prize coins.
 */
function generatePrize() {
  const random = Math.random();

  if (random < 0.6) {
    return randomInt(5, 8);
  }

  if (random < 0.85) {
    return randomInt(9, 15);
  }

  if (random < 0.97) {
    return randomInt(16, 25);
  }

  return randomInt(26, 35);
}

/**
 * Generates random int inclusive.
 *
 * @param {number} min Minimum.
 * @param {number} max Maximum.
 * @return {number} Random int.
 */
function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

/**
 * Safely updates wallet coins using RTDB transaction.
 *
 * @param {object} walletRef Wallet ref.
 * @param {number} coinsDelta Coins delta.
 * @param {number} minCoins Minimum required coins.
 * @return {Promise<number>} Final coins.
 */
async function updateWallet(walletRef, coinsDelta, minCoins) {
  const beforeSnapshot = await walletRef.get();
  const beforeCoins = beforeSnapshot.exists() ?
    toCoinsNumber(beforeSnapshot.val()) :
    0;

  const result = await walletRef.transaction((currentValue) => {
    const safeValue =
      currentValue === null && beforeSnapshot.exists() ?
        beforeCoins :
        currentValue;

    const currentCoins = toCoinsNumber(safeValue);

    if (currentCoins < minCoins) {
      return;
    }

    return currentCoins + coinsDelta;
  });

  if (!result.committed) {
    throw new HttpsError(
        "failed-precondition",
        "Not enough coins.",
    );
  }

  return toCoinsNumber(result.snapshot.val());
}

/**
 * Buys scratch card safely on server.
 *
 * @param {object} request Callable request.
 * @return {Promise<object>} Scratch card purchase result.
 */
exports.buyScratchCard = onCall(LOW_RESOURCE_OPTIONS, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }

  const uid = request.auth.uid;
  const db = getDatabase(admin.app(), DATABASE_URL);

  const walletRef = db.ref(`users/${uid}/wallet/coins`);
  const scratchCardRef = db.ref(`scratch_cards/${uid}`).push();

  const prizeCoins = generatePrize();
  const finalCoins = await updateWallet(
      walletRef,
      -SCRATCH_COST,
      SCRATCH_COST,
  );

  const cardId = scratchCardRef.key;
  const transactionId = `scratch_buy_${cardId}`;

  const historyData = {
    transactionId,
    userId: uid,
    title: "Scratch Card Purchase",
    type: "game_scratch_card_cost",
    game: "scratch_card",
    status: "completed",
    coins: -SCRATCH_COST,
    cost: SCRATCH_COST,
    reward: 0,
    scratchCardId: cardId,
    description: "Scratch card cost",
    timestamp: ServerValue.TIMESTAMP,
  };

  const updates = {};
  updates[`scratch_cards/${uid}/${cardId}`] = {
    cardId,
    uid,
    status: "pending",
    cost: SCRATCH_COST,
    prizeCoins,
    claimed: false,
    createdAt: ServerValue.TIMESTAMP,
  };
  updates[`game_transactions/${uid}/${transactionId}`] = historyData;
  updates[`transactions/${transactionId}`] = historyData;

  await db.ref().update(updates);

  return {
    cardId,
    prizeCoins,
    cost: SCRATCH_COST,
    finalCoins,
  };
});

/**
 * Claims scratch card prize once.
 *
 * @param {object} request Callable request.
 * @return {Promise<object>} Scratch card claim result.
 */
exports.claimScratchCardPrize = onCall(
    LOW_RESOURCE_OPTIONS,
    async (request) => {
      if (!request.auth) {
        throw new HttpsError("unauthenticated", "Authentication required.");
      }

      const uid = request.auth.uid;
      const data = request.data || {};
      const cardId = String(data.cardId || "").trim();

      if (!cardId) {
        throw new HttpsError(
            "invalid-argument",
            "cardId is required.",
        );
      }

      const db = getDatabase(admin.app(), DATABASE_URL);
      const cardRef = db.ref(`scratch_cards/${uid}/${cardId}`);
      const walletRef = db.ref(`users/${uid}/wallet/coins`);
      const transactionId = `scratch_claim_${cardId}`;
      const gameTxRef = db.ref(
          `game_transactions/${uid}/${transactionId}`,
      );

      const cardSnapshot = await cardRef.get();

      if (!cardSnapshot.exists()) {
        throw new HttpsError(
            "failed-precondition",
            "Scratch card already claimed or not found.",
        );
      }

      const card = cardSnapshot.val() || {};

      if (card.claimed === true || card.status === "claimed") {
        const gameTxSnapshot = await gameTxRef.get();
        const walletSnapshot = await walletRef.get();

        if (gameTxSnapshot.exists()) {
          const tx = gameTxSnapshot.val() || {};
          return {
            cardId,
            prizeCoins: toCoinsNumber(tx.reward || tx.coins),
            finalCoins: toCoinsNumber(walletSnapshot.val()),
            claimed: true,
          };
        }

        throw new HttpsError(
            "failed-precondition",
            "Scratch card already claimed or not found.",
        );
      }

      const prizeCoins = toCoinsNumber(card.prizeCoins);

      if (prizeCoins <= 0) {
        throw new HttpsError(
            "failed-precondition",
            "Scratch card prize is invalid.",
        );
      }

      const historyData = {
        transactionId,
        userId: uid,
        title: "Scratch Card Win",
        type: "game_scratch_card_win",
        game: "scratch_card",
        status: "completed",
        coins: prizeCoins,
        cost: 0,
        reward: prizeCoins,
        scratchCardId: cardId,
        description: `Scratch card win: ${prizeCoins} coins`,
        timestamp: ServerValue.TIMESTAMP,
      };

      const updates = {};
      updates[`users/${uid}/wallet/coins`] =
        ServerValue.increment(prizeCoins);
      updates[`scratch_cards/${uid}/${cardId}/status`] = "claimed";
      updates[`scratch_cards/${uid}/${cardId}/claimed`] = true;
      updates[`scratch_cards/${uid}/${cardId}/claimedAt`] =
        ServerValue.TIMESTAMP;
      updates[`scratch_cards/${uid}/${cardId}/claimTransactionId`] =
        transactionId;
      updates[`game_transactions/${uid}/${transactionId}`] = historyData;
      updates[`transactions/${transactionId}`] = historyData;

      await db.ref().update(updates);

      const walletSnapshot = await walletRef.get();

      return {
        cardId,
        prizeCoins,
        finalCoins: toCoinsNumber(walletSnapshot.val()),
        claimed: true,
      };
    },
);
