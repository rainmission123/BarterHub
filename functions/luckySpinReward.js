const {onCall, HttpsError} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const {getDatabase, ServerValue} = require("firebase-admin/database");

const DATABASE_URL = "https://barterhub-3c947-default-rtdb.firebaseio.com";
const SPIN_COST = 10;

const LOW_RESOURCE_OPTIONS = {
  cpu: 0.083,
  memory: "256MiB",
  minInstances: 0,
  maxInstances: 1,
};

const REWARDS = [
  {index: 0, label: "10 Coins", type: "coins", coins: 10, weight: 12},
  {index: 1, label: "15 Coins", type: "coins", coins: 15, weight: 10},
  {index: 2, label: "Mystery Gift", type: "mystery", coins: 0, weight: 8},
  {index: 3, label: "1 Extra Spin", type: "free_spin", coins: 0, weight: 6},
  {index: 4, label: "1 Coin", type: "coins", coins: 1, weight: 25},
  {index: 5, label: "25 Coins", type: "coins", coins: 25, weight: 4},
  {index: 6, label: "5 Coins", type: "coins", coins: 5, weight: 15},
  {index: 7, label: "2 Coins", type: "coins", coins: 2, weight: 20},
];

const MYSTERY_REWARDS = [
  {coins: 30, message: "You found 30 Coins!"},
  {coins: 25, message: "You found 25 Coins!"},
  {coins: 40, message: "NICE! 40 Coins!"},
  {coins: 15, message: "You got 15 Coins!"},
  {coins: 20, message: "You found 20 Coins!"},
];

/**
 * Converts wallet value to a safe number.
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
 * Picks a weighted lucky spin reward.
 *
 * @return {object} Selected reward.
 */
function pickReward() {
  const totalWeight = REWARDS.reduce((sum, reward) => {
    return sum + reward.weight;
  }, 0);

  let random = Math.random() * totalWeight;

  for (const reward of REWARDS) {
    random -= reward.weight;

    if (random < 0) {
      return reward;
    }
  }

  return REWARDS[REWARDS.length - 1];
}

/**
 * Picks a mystery gift reward.
 *
 * @return {object} Mystery reward.
 */
function pickMysteryReward() {
  const index = Math.floor(Math.random() * MYSTERY_REWARDS.length);
  return MYSTERY_REWARDS[index];
}

/**
 * Consumes one stored free spin.
 *
 * @param {object} freeSpinRef Realtime Database ref.
 * @return {Promise<void>}
 */
async function consumeFreeSpin(freeSpinRef) {
  const result = await freeSpinRef.transaction((currentValue) => {
    const currentSpins = toCoinsNumber(currentValue);

    if (currentSpins < 1) {
      return;
    }

    return currentSpins - 1;
  });

  if (!result.committed) {
    throw new HttpsError(
        "failed-precondition",
        "No free spin available.",
    );
  }
}

/**
 * Applies wallet delta safely.
 *
 * @param {object} walletRef Realtime Database ref.
 * @param {number} coinsDelta Coins to add or deduct.
 * @param {number} minCoins Minimum required coins before update.
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
        "Not enough coins to spin.",
    );
  }

  return toCoinsNumber(result.snapshot.val());
}

/**
 * Plays Lucky Spin safely on the server.
 *
 * @param {object} request Callable request.
 * @return {Promise<object>} Lucky Spin result.
 */
exports.playLuckySpin = onCall(LOW_RESOURCE_OPTIONS, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }

  const data = request.data || {};
  const useFreeSpin = data.useFreeSpin === true;
  const uid = request.auth.uid;
  const db = getDatabase(admin.app(), DATABASE_URL);

  const walletRef = db.ref(`users/${uid}/wallet/coins`);
  const freeSpinRef = db.ref(`lucky_spin_state/${uid}/freeSpins`);
  const txRef = db.ref(`coin_transactions/${uid}`).push();

  console.log("LUCKY_SPIN_START", {
    uid,
    useFreeSpin,
  });

  try {
    if (useFreeSpin) {
      await consumeFreeSpin(freeSpinRef);
    }

    const reward = pickReward();
    let rewardCoins = reward.coins;
    let mysteryReward = null;
    let freeSpinGranted = false;

    if (reward.type === "mystery") {
      mysteryReward = pickMysteryReward();
      rewardCoins = mysteryReward.coins;
    }

    if (reward.type === "free_spin") {
      freeSpinGranted = true;
    }

    const cost = useFreeSpin ? 0 : SPIN_COST;
    const coinsDelta = rewardCoins - cost;
    const minCoins = useFreeSpin ? 0 : SPIN_COST;
    const finalCoins = await updateWallet(walletRef, coinsDelta, minCoins);

    if (freeSpinGranted) {
      await freeSpinRef.transaction((currentValue) => {
        return toCoinsNumber(currentValue) + 1;
      });
    }

    const transactionId = txRef.key;
    const transactionData = {
      transactionId,
      userId: uid,
      title: "Lucky Spin",
      type: "game_lucky_spin",
      status: "completed",
      rewardIndex: reward.index,
      rewardLabel: reward.label,
      rewardType: reward.type,
      rewardCoins,
      cost,
      coins: coinsDelta,
      finalCoins,
      useFreeSpin,
      freeSpinGranted,
      mysteryReward,
      description: `Lucky Spin result: ${reward.label}`,
      timestamp: ServerValue.TIMESTAMP,
    };

    await Promise.all([
      txRef.set(transactionData),
      db.ref(`transactions/${transactionId}`).set(transactionData),
    ]);

    console.log("LUCKY_SPIN_SUCCESS", {
      uid,
      transactionId,
      rewardIndex: reward.index,
      rewardType: reward.type,
      rewardCoins,
      coinsDelta,
      finalCoins,
      useFreeSpin,
      freeSpinGranted,
    });

    return {
      rewarded: true,
      rewardIndex: reward.index,
      rewardLabel: reward.label,
      rewardType: reward.type,
      rewardCoins,
      coinsDelta,
      finalCoins,
      useFreeSpin,
      freeSpinGranted,
      mysteryReward,
    };
  } catch (error) {
    if (error instanceof HttpsError) {
      console.error("LUCKY_SPIN_EXPECTED_ERROR", {
        uid,
        useFreeSpin,
        code: error.code,
        message: error.message,
      });

      throw error;
    }

    console.error("LUCKY_SPIN_ERROR", {
      uid,
      useFreeSpin,
      message: error && error.message,
      stack: error && error.stack,
    });

    throw new HttpsError(
        "internal",
        "Could not play Lucky Spin.",
    );
  }
});
