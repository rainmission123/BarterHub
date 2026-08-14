const {onCall, HttpsError} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const {getDatabase, ServerValue} = require("firebase-admin/database");

const DATABASE_URL = "https://barterhub-3c947-default-rtdb.firebaseio.com";
const COIN_FLIP_COST = 5;
const COIN_FLIP_WIN_REWARD = 10;

const LOW_RESOURCE_OPTIONS = {
  cpu: 0.083,
  memory: "256MiB",
  minInstances: 0,
  maxInstances: 1,
};

/**
 * Converts wallet coin value to a safe number.
 *
 * @param {*} value Current wallet value.
 * @return {number} Safe coin number.
 */
function toCoinsNumber(value) {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }

  if (typeof value === "string") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  return 0;
}

/**
 * Plays coin flip safely on the server.
 *
 * @param {object} request Callable request.
 * @return {Promise<object>} Coin flip result.
 */
exports.playCoinFlip = onCall(LOW_RESOURCE_OPTIONS, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }

  const data = request.data || {};
  const rawChoice = String(data.choice || "").toLowerCase();

  if (rawChoice !== "heads" && rawChoice !== "tails") {
    throw new HttpsError(
        "invalid-argument",
        "Choice must be heads or tails.",
    );
  }

  const uid = request.auth.uid;
  const db = getDatabase(admin.app(), DATABASE_URL);

  const walletPath = `users/${uid}/wallet/coins`;
  const walletRef = db.ref(walletPath);
  const txRef = db.ref(`coin_transactions/${uid}`).push();

  const beforeSnapshot = await walletRef.get();
  const beforeCoins = beforeSnapshot.val();

  const coinSide = Math.random() < 0.5 ? "heads" : "tails";
  const won = rawChoice === coinSide;
  const coinsDelta = won ?
    COIN_FLIP_WIN_REWARD - COIN_FLIP_COST :
    -COIN_FLIP_COST;

  console.log("COIN_FLIP_START", {
    uid,
    walletPath,
    choice: rawChoice,
    coinSide,
    won,
    beforeCoins,
  });

  let startingCoins = 0;

  const result = await walletRef.transaction((currentCoins) => {
    const safeValue =
      currentCoins === null && beforeSnapshot.exists() ?
        beforeCoins :
        currentCoins;

    const coins = toCoinsNumber(safeValue);
    startingCoins = coins;

    if (coins < COIN_FLIP_COST) {
      return;
    }

    return coins + coinsDelta;
  });

  if (!result.committed) {
    console.error("COIN_FLIP_NOT_ENOUGH_COINS", {
      uid,
      walletPath,
      startingCoins,
      beforeCoins,
    });

    throw new HttpsError(
        "failed-precondition",
        "Not enough coins to play.",
    );
  }

  const finalCoins = toCoinsNumber(result.snapshot.val());
  const transactionId = txRef.key;

  const transactionData = {
    transactionId,
    userId: uid,
    title: won ? "Coin Flip Win" : "Coin Flip Lose",
    type: "game_coin_flip",
    status: "completed",
    choice: rawChoice,
    coinSide,
    result: won ? "win" : "lose",
    cost: COIN_FLIP_COST,
    reward: won ? COIN_FLIP_WIN_REWARD : 0,
    coins: coinsDelta,
    amount: 0,
    finalCoins,
    description: won ?
      "Coin Flip reward" :
      "Coin Flip play cost",
    timestamp: ServerValue.TIMESTAMP,
  };

  await Promise.all([
    txRef.set(transactionData),
    db.ref(`transactions/${transactionId}`).set(transactionData),
  ]);

  console.log("COIN_FLIP_SUCCESS", {
    uid,
    walletPath,
    transactionId,
    choice: rawChoice,
    coinSide,
    won,
    coinsDelta,
    finalCoins,
  });

  return {
    choice: rawChoice,
    coinSide,
    result: won ? "win" : "lose",
    won,
    coinsDelta,
    finalCoins,
  };
});
