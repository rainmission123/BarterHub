const crypto = require("crypto");
const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.database();
const MANILA_TIME_ZONE = "Asia/Manila";
const POST_ITEM_REWARD_COINS = 2;
const PAYMONGO_CHECKOUT_URL = "https://api.paymongo.com/v1/checkout_sessions";
const CHECKOUT_SUCCESS_URL = "https://barterhub-3c947.web.app/payment-success";
const CHECKOUT_CANCEL_URL = "https://barterhub-3c947.web.app/payment-cancel";
const COIN_PACKAGES = {
  coin_100: {coins: 100, amount: 50},
  coin_200: {coins: 200, amount: 100},
  coin_500: {coins: 500, amount: 250},
};
const PREMIUM_PLANS = {
  "1_month": {cost: 50, durationDays: 30, label: "1 month"},
  "5_months": {cost: 100, durationDays: 150, label: "5 months"},
  "1_year": {cost: 200, durationDays: 365, label: "1 year"},
};
const DAILY_CHALLENGES = {
  post_item: {title: "Post 1 item", target: 1, reward: 5},
  complete_transactions: {title: "Complete 2 transactions", target: 2, reward: 10},
  daily_login: {title: "Daily login", target: 1, reward: 2},
  rate_partner: {title: "Rate a trade partner", target: 1, reward: 1},
  share_app: {title: "Share app with friends", target: 1, reward: 3},
};
const LUCKY_SPIN_COST = 10;
const LUCKY_SPIN_REWARDS = [
  {label: "+5 Coins", type: "coins", coins: 5, weight: 20},
  {label: "+20 Coins", type: "coins", coins: 20, weight: 10},
  {label: "Mystery Gift", type: "mystery", coins: 25, weight: 5},
  {label: "+5 Coins", type: "coins", coins: 5, weight: 20},
  {label: "Better luck next time", type: "nothing", coins: 0, weight: 25},
  {label: "+20 Coins", type: "coins", coins: 20, weight: 10},
  {label: "+10 Coins", type: "coins", coins: 10, weight: 15},
  {label: "Free Spin", type: "free_spin", coins: 0, weight: 5},
];
const COIN_FLIP_COST = 5;
const COIN_FLIP_WIN_REWARD = 10;
const SCRATCH_CARD_COST = 15;
const SCRATCH_CARD_PRIZES = [
  {coins: 5, weight: 30},
  {coins: 10, weight: 28},
  {coins: 15, weight: 22},
  {coins: 25, weight: 14},
  {coins: 50, weight: 6},
];
const SEND_COINS_MIN = 1;
const SEND_COINS_MAX = 10000;

function requireAuth(context) {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Please sign in to continue."
    );
  }
  return context.auth.uid;
}

function getTodayDateKey() {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: MANILA_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

function asNumber(value, fallback = 0) {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function pushKey(path) {
  return db.ref(path).push().key;
}

function randomRewardIndex() {
  const total = LUCKY_SPIN_REWARDS.reduce((sum, reward) => sum + reward.weight, 0);
  let roll = Math.floor(Math.random() * total);
  for (let i = 0; i < LUCKY_SPIN_REWARDS.length; i += 1) {
    roll -= LUCKY_SPIN_REWARDS[i].weight;
    if (roll < 0) return i;
  }
  return LUCKY_SPIN_REWARDS.length - 1;
}

function randomScratchPrize() {
  const total = SCRATCH_CARD_PRIZES.reduce((sum, prize) => sum + prize.weight, 0);
  let roll = Math.floor(Math.random() * total);
  for (let i = 0; i < SCRATCH_CARD_PRIZES.length; i += 1) {
    roll -= SCRATCH_CARD_PRIZES[i].weight;
    if (roll < 0) return SCRATCH_CARD_PRIZES[i].coins;
  }
  return SCRATCH_CARD_PRIZES[0].coins;
}

function requirePositiveInteger(value, fieldName) {
  const number = Number(value);
  if (!Number.isInteger(number) || number <= 0) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      `${fieldName} must be a positive whole number.`
    );
  }
  return number;
}

function requireWallet(user, message = "Wallet is not available.") {
  if (!user || typeof user !== "object") {
    throw new functions.https.HttpsError("not-found", "User account was not found.");
  }
  const wallet = Object.assign({}, user.wallet || {});
  const coins = asNumber(wallet.coins);
  if (coins < 0) {
    throw new functions.https.HttpsError("failed-precondition", message);
  }
  return {wallet, coins};
}

function setLedger(root, uid, transactionId, payload) {
  if (!root.coin_transactions) root.coin_transactions = {};
  if (!root.coin_transactions[uid]) root.coin_transactions[uid] = {};
  if (!root.transactions) root.transactions = {};

  root.coin_transactions[uid][transactionId] = payload;
  root.transactions[transactionId] = Object.assign({userId: uid}, payload);
}

function applyCoinDelta(user, delta) {
  const {wallet, coins} = requireWallet(user);
  const nextCoins = coins + delta;
  if (nextCoins < 0) return null;
  wallet.coins = nextCoins;
  user.wallet = wallet;
  return nextCoins;
}

function verifyPaymongoSignature(req) {
  const secret = functions.config().paymongo &&
    functions.config().paymongo.webhook_secret;
  if (!secret) return false;

  const header = req.get("paymongo-signature") || req.get("Paymongo-Signature");
  if (!header) return false;

  const parts = Object.fromEntries(
    header.split(",").map((part) => {
      const [key, ...rest] = part.trim().split("=");
      return [key, rest.join("=")];
    })
  );
  const timestamp = parts.t;
  const candidates = [parts.v1, parts.te, parts.li].filter(Boolean);
  if (!timestamp || candidates.length === 0) return false;

  const rawBody = req.rawBody ? req.rawBody.toString("utf8") :
    JSON.stringify(req.body || {});
  const expected = crypto
    .createHmac("sha256", secret)
    .update(`${timestamp}.${rawBody}`)
    .digest("hex");

  return candidates.some((candidate) => {
    try {
      return crypto.timingSafeEqual(
        Buffer.from(candidate, "hex"),
        Buffer.from(expected, "hex")
      );
    } catch (_error) {
      return false;
    }
  });
}

function findPaymongoMetadata(event) {
  const attrs = event && event.data && event.data.attributes;
  const nestedAttrs = attrs && attrs.data && attrs.data.attributes;
  return {
    eventType: attrs && attrs.type,
    eventId: event && event.data && event.data.id,
    checkoutSessionId: attrs && attrs.data && attrs.data.id,
    paymentProviderId: nestedAttrs && nestedAttrs.payment_intent_id,
    metadata: (nestedAttrs && nestedAttrs.metadata) || (attrs && attrs.metadata) || {},
    referenceNo: (nestedAttrs && nestedAttrs.reference_number) ||
      (attrs && attrs.reference_number),
  };
}

async function writeCoinLedger(uid, transactionId, payload) {
  const updates = {};
  updates[`coin_transactions/${uid}/${transactionId}`] = payload;
  updates[`transactions/${transactionId}`] = Object.assign({userId: uid}, payload);
  await db.ref().update(updates);
}

exports.claimPostItemReward = functions.https.onCall(async (_data, context) => {
  const uid = requireAuth(context);
  const today = getTodayDateKey();
  const rewardRef = db.ref(`post_rewards/${uid}/${today}`);
  const transactionId = pushKey(`coin_transactions/${uid}`);
  const notificationId = pushKey(`notifications/${uid}`);

  const rewardResult = await rewardRef.transaction((currentReward) => {
    if (currentReward && currentReward.rewarded === true) return;
    return {
      rewarded: true,
      coins: POST_ITEM_REWARD_COINS,
      date: today,
      transactionId,
      notificationId,
      timestamp: admin.database.ServerValue.TIMESTAMP,
    };
  }, undefined, false);

  if (!rewardResult.committed) return {rewarded: false};

  const updates = {};
  updates[`users/${uid}/wallet/coins`] =
    admin.database.ServerValue.increment(POST_ITEM_REWARD_COINS);
  updates[`coin_transactions/${uid}/${transactionId}`] = {
    title: "Post Item Reward",
    type: "post_item_reward",
    amount: 0,
    coins: POST_ITEM_REWARD_COINS,
    date: today,
    status: "completed",
    transactionId,
    referenceNo: `POST-${today}-${transactionId}`,
    timestamp: admin.database.ServerValue.TIMESTAMP,
  };
  updates[`transactions/${transactionId}`] = {
    userId: uid,
    title: "Post Item Reward",
    type: "post_item_reward",
    amount: 0,
    coins: POST_ITEM_REWARD_COINS,
    date: today,
    status: "completed",
    transactionId,
    referenceNo: `POST-${today}-${transactionId}`,
    timestamp: admin.database.ServerValue.TIMESTAMP,
  };
  updates[`notifications/${uid}/${notificationId}`] = {
    type: "coins",
    coins: POST_ITEM_REWARD_COINS,
    message: "You earned +2 coins from posting an item!",
    timestamp: admin.database.ServerValue.TIMESTAMP,
    read: false,
  };
  await db.ref().update(updates);
  return {rewarded: true, coins: POST_ITEM_REWARD_COINS};
});

exports.claimDailyChallengeReward = functions.https.onCall(async (data, context) => {
  const uid = requireAuth(context);
  const action = String(data && data.action || "");
  const challengeConfig = DAILY_CHALLENGES[action];
  if (!challengeConfig) {
    throw new functions.https.HttpsError("invalid-argument", "Invalid challenge.");
  }

  const today = getTodayDateKey();
  if (action === "post_item") {
    const postRewardSnap = await db.ref(`post_rewards/${uid}/${today}/rewarded`)
      .once("value");
    if (postRewardSnap.val() !== true) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Post an item before claiming this daily challenge."
      );
    }
  } else if (action !== "daily_login") {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "This challenge needs a server-verified action before it can be claimed."
    );
  }

  const transactionId = pushKey(`coin_transactions/${uid}`);
  const rewardCoins = challengeConfig.reward;
  const now = Date.now();
  let finalCoins = 0;
  let alreadyRewarded = false;
  let failure = null;

  const rewardResult = await db.ref().transaction((root) => {
    root = root || {};
    if (!root.users || !root.users[uid]) {
      failure = "account_missing";
      return;
    }

    const user = root.users[uid];
    const existingChallenge = user.daily_challenges &&
      user.daily_challenges[today] &&
      user.daily_challenges[today][action];
    if (existingChallenge && existingChallenge.rewarded === true) {
      alreadyRewarded = true;
      return;
    }

    finalCoins = applyCoinDelta(user, rewardCoins);
    if (finalCoins === null) {
      failure = "wallet_error";
      return;
    }

    if (!user.daily_challenges) user.daily_challenges = {};
    if (!user.daily_challenges[today]) user.daily_challenges[today] = {};
    user.daily_challenges[today][action] = Object.assign({}, existingChallenge || {}, {
      title: challengeConfig.title,
      action,
      progress: challengeConfig.target,
      target: challengeConfig.target,
      completed: true,
      rewarded: true,
      reward: challengeConfig.reward,
      rewardedAt: now,
      transactionId,
    });
    root.users[uid] = user;

    setLedger(root, uid, transactionId, {
      title: "Daily Challenge Reward",
      type: "daily_challenge_reward",
      action,
      coins: rewardCoins,
      amount: 0,
      status: "completed",
      transactionId,
      referenceNo: `DAILY-${today}-${transactionId}`,
      walletBalanceAfter: finalCoins,
      timestamp: now,
    });
    return root;
  }, undefined, false);

  if (!rewardResult.committed) {
    if (alreadyRewarded) return {rewarded: false, coins: 0};
    if (failure === "account_missing") {
      throw new functions.https.HttpsError("not-found", "User account was not found.");
    }
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Daily challenge reward could not be claimed."
    );
  }

  return {rewarded: true, coins: rewardCoins, finalCoins};
});

exports.activatePremium = functions.https.onCall(async (data, context) => {
  const uid = requireAuth(context);
  const planId = String(data && data.planId || "");
  const plan = PREMIUM_PLANS[planId];
  if (!plan) {
    throw new functions.https.HttpsError("invalid-argument", "Invalid premium plan.");
  }

  const now = Date.now();
  const transactionId = pushKey(`coin_transactions/${uid}`);
  let finalCoins = 0;
  let premiumExpiry = 0;

  const result = await db.ref(`users/${uid}`).transaction((user) => {
    if (!user) return;
    const currentCoins = asNumber(user.wallet && user.wallet.coins);
    if (currentCoins < plan.cost) return;

    const baseExpiry = Math.max(asNumber(user.premiumExpiry), now);
    premiumExpiry = baseExpiry + plan.durationDays * 24 * 60 * 60 * 1000;
    finalCoins = currentCoins - plan.cost;

    return Object.assign({}, user, {
      isPremium: true,
      premiumExpiry,
      wallet: Object.assign({}, user.wallet || {}, {coins: finalCoins}),
    });
  }, undefined, false);

  if (!result.committed) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Not enough coins for this premium plan."
    );
  }

  const premiumTxnId = pushKey("premium_transactions");
  const updates = {};
  updates[`public_users/${uid}/isPremium`] = true;
  updates[`public_users/${uid}/premiumExpiry`] = premiumExpiry;
  updates[`coin_transactions/${uid}/${transactionId}`] = {
    title: "Premium Activation",
    type: "premium_activation",
    planId,
    coins: -plan.cost,
    amount: 0,
    status: "completed",
    transactionId,
    referenceNo: `PREM-${transactionId}`,
    timestamp: admin.database.ServerValue.TIMESTAMP,
  };
  updates[`premium_transactions/${premiumTxnId}`] = {
    userId: uid,
    planId,
    duration: plan.label,
    coins: plan.cost,
    premiumExpiry,
    status: "completed",
    transactionId,
    timestamp: admin.database.ServerValue.TIMESTAMP,
  };
  await db.ref().update(updates);
  return {success: true, coins: finalCoins, premiumExpiry};
});

exports.playLuckySpin = functions.https.onCall(async (data, context) => {
  const uid = requireAuth(context);
  const useFreeSpin = data && data.useFreeSpin === true;
  const rewardIndex = randomRewardIndex();
  const reward = LUCKY_SPIN_REWARDS[rewardIndex];
  const transactionId = pushKey(`coin_transactions/${uid}`);
  let finalCoins = 0;
  let freeSpinGranted = false;

  const result = await db.ref(`users/${uid}`).transaction((user) => {
    if (!user) return;
    const wallet = Object.assign({}, user.wallet || {});
    const currentCoins = asNumber(wallet.coins);
    const currentFreeSpins = asNumber(user.freeLuckySpins);

    if (useFreeSpin) {
      if (currentFreeSpins < 1) return;
      user.freeLuckySpins = currentFreeSpins - 1;
      finalCoins = currentCoins;
    } else {
      if (currentCoins < LUCKY_SPIN_COST) return;
      finalCoins = currentCoins - LUCKY_SPIN_COST;
    }

    if (reward.type === "coins" || reward.type === "mystery") {
      finalCoins += reward.coins;
    }
    if (reward.type === "free_spin") {
      user.freeLuckySpins = asNumber(user.freeLuckySpins) + 1;
      freeSpinGranted = true;
    }

    wallet.coins = finalCoins;
    user.wallet = wallet;
    return user;
  }, undefined, false);

  if (!result.committed) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      useFreeSpin ? "No free spin available." : "Not enough coins."
    );
  }

  const cost = useFreeSpin ? 0 : LUCKY_SPIN_COST;
  const coinsDelta = reward.coins - cost;
  await writeCoinLedger(uid, transactionId, {
    title: "Lucky Spin",
    type: "lucky_spin",
    rewardType: reward.type,
    rewardLabel: reward.label,
    coins: coinsDelta,
    amount: 0,
    status: "completed",
    transactionId,
    referenceNo: `SPIN-${transactionId}`,
    timestamp: admin.database.ServerValue.TIMESTAMP,
  });

  return {
    rewardIndex,
    rewardLabel: reward.label,
    rewardType: reward.type,
    rewardCoins: reward.coins,
    coinsDelta,
    finalCoins,
    freeSpinGranted,
    mysteryReward: reward.type === "mystery" ? {
      message: "Mystery reward unlocked!",
      coins: reward.coins,
    } : null,
  };
});

exports.sendCoins = functions.https.onCall(async (data, context) => {
  const senderUid = requireAuth(context);
  const username = String(data && data.username || "").trim().toLowerCase();
  const amount = requirePositiveInteger(data && data.amount, "Amount");

  if (!/^[a-z0-9_]{4,20}$/.test(username)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Enter a valid recipient username."
    );
  }
  if (amount < SEND_COINS_MIN || amount > SEND_COINS_MAX) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      `Amount must be between ${SEND_COINS_MIN} and ${SEND_COINS_MAX} coins.`
    );
  }

  const recipientSnap = await db.ref(`usernames/${username}`).once("value");
  const recipientUid = recipientSnap.val();
  if (!recipientUid || typeof recipientUid !== "string") {
    throw new functions.https.HttpsError("not-found", "Recipient username was not found.");
  }
  if (recipientUid === senderUid) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "You cannot send coins to yourself."
    );
  }

  const senderTxnId = pushKey(`coin_transactions/${senderUid}`);
  const recipientTxnId = pushKey(`coin_transactions/${recipientUid}`);
  const now = Date.now();
  let newBalance = 0;
  let failure = null;

  const result = await db.ref().transaction((root) => {
    root = root || {};
    const users = root.users || {};
    const sender = users[senderUid];
    const recipient = users[recipientUid];
    if (!sender || !recipient) {
      failure = "account_missing";
      return;
    }

    const senderWallet = requireWallet(sender, "Sender wallet is not available.");
    const recipientWallet = requireWallet(recipient, "Recipient wallet is not available.");
    if (senderWallet.coins < amount) {
      failure = "insufficient_funds";
      return;
    }

    senderWallet.wallet.coins = senderWallet.coins - amount;
    recipientWallet.wallet.coins = recipientWallet.coins + amount;
    sender.wallet = senderWallet.wallet;
    recipient.wallet = recipientWallet.wallet;
    users[senderUid] = sender;
    users[recipientUid] = recipient;
    root.users = users;
    newBalance = senderWallet.wallet.coins;

    setLedger(root, senderUid, senderTxnId, {
      title: "Coins Sent",
      type: "coin_sent",
      toUserId: recipientUid,
      toUsername: username,
      coins: -amount,
      amount: 0,
      status: "completed",
      transactionId: senderTxnId,
      referenceNo: `SEND-${senderTxnId}`,
      walletBalanceAfter: senderWallet.wallet.coins,
      timestamp: now,
    });
    setLedger(root, recipientUid, recipientTxnId, {
      title: "Coins Received",
      type: "coin_received",
      fromUserId: senderUid,
      coins: amount,
      amount: 0,
      status: "completed",
      transactionId: recipientTxnId,
      referenceNo: `RECV-${recipientTxnId}`,
      walletBalanceAfter: recipientWallet.wallet.coins,
      timestamp: now,
    });
    return root;
  }, undefined, false);

  if (!result.committed) {
    if (failure === "insufficient_funds") {
      throw new functions.https.HttpsError("failed-precondition", "Not enough coins.");
    }
    throw new functions.https.HttpsError("not-found", "Sender or recipient account was not found.");
  }

  return {
    success: true,
    message: `Sent ${amount} coins to ${username}.`,
    newBalance,
    transactionId: senderTxnId,
  };
});

exports.playCoinFlip = functions.https.onCall(async (data, context) => {
  const uid = requireAuth(context);
  const choice = String(data && data.choice || "").trim().toLowerCase();
  if (!["heads", "tails"].includes(choice)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Choose either heads or tails."
    );
  }

  const coinSide = Math.random() < 0.5 ? "heads" : "tails";
  const won = choice === coinSide;
  const coinsDelta = won ? COIN_FLIP_WIN_REWARD - COIN_FLIP_COST : -COIN_FLIP_COST;
  const transactionId = pushKey(`coin_transactions/${uid}`);
  const now = Date.now();
  let finalCoins = 0;
  let failure = null;

  const result = await db.ref().transaction((root) => {
    root = root || {};
    const user = root.users && root.users[uid];
    if (!user) {
      failure = "account_missing";
      return;
    }
    const {wallet, coins} = requireWallet(user);
    if (coins < COIN_FLIP_COST) {
      failure = "insufficient_funds";
      return;
    }

    finalCoins = coins + coinsDelta;
    if (finalCoins < 0) {
      failure = "insufficient_funds";
      return;
    }
    wallet.coins = finalCoins;
    user.wallet = wallet;
    root.users[uid] = user;

    setLedger(root, uid, transactionId, {
      title: "Coin Flip",
      type: "coin_flip",
      choice,
      coinSide,
      won,
      cost: COIN_FLIP_COST,
      rewardCoins: won ? COIN_FLIP_WIN_REWARD : 0,
      coins: coinsDelta,
      amount: 0,
      status: "completed",
      transactionId,
      referenceNo: `FLIP-${transactionId}`,
      walletBalanceAfter: finalCoins,
      timestamp: now,
    });
    return root;
  }, undefined, false);

  if (!result.committed) {
    if (failure === "insufficient_funds") {
      throw new functions.https.HttpsError("failed-precondition", "Not enough coins.");
    }
    throw new functions.https.HttpsError("not-found", "User account was not found.");
  }

  return {coinSide, won, finalCoins, coinsDelta};
});

exports.buyScratchCard = functions.https.onCall(async (_data, context) => {
  const uid = requireAuth(context);
  const cardId = pushKey(`scratch_cards/${uid}`);
  const transactionId = pushKey(`coin_transactions/${uid}`);
  const prizeCoins = randomScratchPrize();
  const now = Date.now();
  let finalCoins = 0;
  let failure = null;

  const result = await db.ref().transaction((root) => {
    root = root || {};
    const user = root.users && root.users[uid];
    if (!user) {
      failure = "account_missing";
      return;
    }
    const {wallet, coins} = requireWallet(user);
    if (coins < SCRATCH_CARD_COST) {
      failure = "insufficient_funds";
      return;
    }

    finalCoins = coins - SCRATCH_CARD_COST;
    wallet.coins = finalCoins;
    user.wallet = wallet;
    root.users[uid] = user;

    if (!root.scratch_cards) root.scratch_cards = {};
    if (!root.scratch_cards[uid]) root.scratch_cards[uid] = {};
    root.scratch_cards[uid][cardId] = {
      cardId,
      uid,
      prizeCoins,
      cost: SCRATCH_CARD_COST,
      status: "purchased",
      claimed: false,
      purchaseTransactionId: transactionId,
      createdAt: now,
    };

    setLedger(root, uid, transactionId, {
      title: "Scratch Card Purchase",
      type: "scratch_card_purchase",
      cardId,
      coins: -SCRATCH_CARD_COST,
      amount: 0,
      status: "completed",
      transactionId,
      referenceNo: `SCRATCH-BUY-${transactionId}`,
      walletBalanceAfter: finalCoins,
      timestamp: now,
    });
    return root;
  }, undefined, false);

  if (!result.committed) {
    if (failure === "insufficient_funds") {
      throw new functions.https.HttpsError("failed-precondition", "Not enough coins.");
    }
    throw new functions.https.HttpsError("not-found", "User account was not found.");
  }

  return {cardId, prizeCoins, finalCoins, cost: SCRATCH_CARD_COST};
});

exports.claimScratchCardPrize = functions.https.onCall(async (data, context) => {
  const uid = requireAuth(context);
  const cardId = String(data && data.cardId || "").trim();
  if (!cardId) {
    throw new functions.https.HttpsError("invalid-argument", "Scratch card ID is required.");
  }

  const transactionId = pushKey(`coin_transactions/${uid}`);
  const now = Date.now();
  let prizeCoins = 0;
  let finalCoins = 0;
  let failure = null;

  const result = await db.ref().transaction((root) => {
    root = root || {};
    const user = root.users && root.users[uid];
    const card = root.scratch_cards &&
      root.scratch_cards[uid] &&
      root.scratch_cards[uid][cardId];
    if (!user || !card) {
      failure = "not_found";
      return;
    }
    if (card.claimed === true || card.status === "claimed") {
      failure = "already_claimed";
      return;
    }

    prizeCoins = requirePositiveInteger(card.prizeCoins, "Prize");
    const allowedPrize = SCRATCH_CARD_PRIZES.some((prize) => prize.coins === prizeCoins);
    if (!allowedPrize) {
      failure = "invalid_prize";
      return;
    }

    finalCoins = applyCoinDelta(user, prizeCoins);
    if (finalCoins === null) {
      failure = "wallet_error";
      return;
    }
    root.users[uid] = user;
    card.claimed = true;
    card.status = "claimed";
    card.claimTransactionId = transactionId;
    card.claimedAt = now;
    root.scratch_cards[uid][cardId] = card;

    setLedger(root, uid, transactionId, {
      title: "Scratch Card Prize",
      type: "scratch_card_prize",
      cardId,
      coins: prizeCoins,
      amount: 0,
      status: "completed",
      transactionId,
      referenceNo: `SCRATCH-CLAIM-${transactionId}`,
      walletBalanceAfter: finalCoins,
      timestamp: now,
    });
    return root;
  }, undefined, false);

  if (!result.committed) {
    if (failure === "already_claimed") {
      throw new functions.https.HttpsError(
        "already-exists",
        "This scratch card prize has already been claimed."
      );
    }
    if (failure === "invalid_prize") {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Scratch card prize is invalid."
      );
    }
    throw new functions.https.HttpsError("not-found", "Scratch card was not found.");
  }

  return {success: true, cardId, prizeCoins, finalCoins, transactionId};
});

exports.claimReferralReward = functions.https.onCall(async (data, context) => {
  const inviterUid = requireAuth(context);
  const invitedUserId = String(data && data.invitedUserId || "");
  if (!invitedUserId || invitedUserId === inviterUid) {
    throw new functions.https.HttpsError("invalid-argument", "Invalid invited user.");
  }

  const [inviterSnap, invitedSnap] = await Promise.all([
    db.ref(`users/${inviterUid}`).once("value"),
    db.ref(`users/${invitedUserId}`).once("value"),
  ]);
  const inviter = inviterSnap.val() || {};
  const invited = invitedSnap.val() || {};
  const inviterCode = String(inviter.referralCode || "");
  const referredBy = String(invited.referredBy || "");
  if (!referredBy || (referredBy !== inviterCode && referredBy !== inviterUid)) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "This referral is not eligible."
    );
  }

  const rewardCoins = 10;
  const transactionId = pushKey(`coin_transactions/${inviterUid}`);
  const rewardRef = db.ref(`referrals/${inviterUid}/${invitedUserId}`);
  const result = await rewardRef.transaction((current) => {
    if (current && current.rewarded === true) return;
    return {
      rewarded: true,
      invitedUserId,
      coins: rewardCoins,
      transactionId,
      timestamp: admin.database.ServerValue.TIMESTAMP,
    };
  }, undefined, false);

  if (!result.committed) return {rewarded: false, coins: 0};

  const updates = {};
  updates[`users/${inviterUid}/wallet/coins`] =
    admin.database.ServerValue.increment(rewardCoins);
  updates[`coin_transactions/${inviterUid}/${transactionId}`] = {
    title: "Referral Reward",
    type: "referral_reward",
    invitedUserId,
    coins: rewardCoins,
    amount: 0,
    status: "completed",
    transactionId,
    referenceNo: `REFERRAL-${transactionId}`,
    timestamp: admin.database.ServerValue.TIMESTAMP,
  };
  updates[`transactions/${transactionId}`] = {
    userId: inviterUid,
    title: "Referral Reward",
    type: "referral_reward",
    invitedUserId,
    coins: rewardCoins,
    amount: 0,
    status: "completed",
    transactionId,
    referenceNo: `REFERRAL-${transactionId}`,
    timestamp: admin.database.ServerValue.TIMESTAMP,
  };
  await db.ref().update(updates);
  return {rewarded: true, coins: rewardCoins};
});

exports.createCoinCheckout = functions.https.onCall(async (data, context) => {
  const uid = requireAuth(context);
  const packageId = String(data.packageId || "");
  const paymentMethod = String(data.paymentMethod || "");
  const currency = String(data.currency || "PHP").toUpperCase();
  const coins = Number(data.coins || 0);
  const amount = Number(data.amount || 0);
  const selectedPackage = COIN_PACKAGES[packageId];
  const allowedPaymentMethods = ["gcash", "grab_pay", "card"];

  if (!selectedPackage) {
    throw new functions.https.HttpsError("invalid-argument", "Invalid coin package.");
  }
  if (
    selectedPackage.coins !== coins ||
    selectedPackage.amount !== amount ||
    currency !== "PHP" ||
    !allowedPaymentMethods.includes(paymentMethod)
  ) {
    throw new functions.https.HttpsError("invalid-argument", "Invalid payment request.");
  }

  const secretKey = functions.config().paymongo &&
    functions.config().paymongo.secret_key;
  if (!secretKey) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Payment service is not configured."
    );
  }

  const paymentRef = db.ref("coin_payments").push();
  const paymentId = paymentRef.key;
  const referenceNo = `COIN-${Date.now()}-${paymentId.slice(-6).toUpperCase()}`;
  const amountInCentavos = Math.round(selectedPackage.amount * 100);
  const checkoutPayload = {
    data: {
      attributes: {
        description: `${selectedPackage.coins} Barter Coins`,
        reference_number: referenceNo,
        payment_method_types: [paymentMethod],
        line_items: [{
          name: `${selectedPackage.coins} Barter Coins`,
          quantity: 1,
          amount: amountInCentavos,
          currency,
          description: `BarterHub ${selectedPackage.coins} coin package`,
        }],
        metadata: {
          uid,
          paymentId,
          referenceNo,
          packageId,
          coins: String(selectedPackage.coins),
          amount: String(selectedPackage.amount),
          currency,
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
    console.error("PayMongo checkout failed", response.status, responseBody);
    throw new functions.https.HttpsError(
      "internal",
      "Unable to create checkout session. Please try again."
    );
  }

  const checkoutSession = responseBody.data || {};
  const attributes = checkoutSession.attributes || {};
  const checkoutUrl = attributes.checkout_url;
  const checkoutSessionId = checkoutSession.id || "";
  if (!checkoutUrl) {
    console.error("PayMongo checkout URL missing", responseBody);
    throw new functions.https.HttpsError(
      "internal",
      "Checkout URL was not returned. Please try again."
    );
  }

  const pendingPayment = {
    paymentId,
    checkoutSessionId,
    referenceNo,
    uid,
    userId: uid,
    packageId,
    coins: selectedPackage.coins,
    amount: selectedPackage.amount,
    amountInCentavos,
    currency,
    paymentMethod,
    provider: "paymongo",
    type: "coin_purchase",
    status: "pending",
    checkoutUrl,
    createdAt: admin.database.ServerValue.TIMESTAMP,
    updatedAt: admin.database.ServerValue.TIMESTAMP,
  };

  const updates = {};
  updates[`coin_payments/${paymentId}`] = pendingPayment;
  if (checkoutSessionId) {
    updates[`paymongo_checkout_sessions/${checkoutSessionId}`] = pendingPayment;
  }
  await db.ref().update(updates);
  return {checkoutUrl, paymentId, checkoutSessionId, referenceNo};
});

exports.paymongoWebhook = functions.https.onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("Method not allowed");
    return;
  }
  if (!verifyPaymongoSignature(req)) {
    res.status(401).send("Invalid signature");
    return;
  }

  const event = req.body || {};
  const {eventType, eventId, checkoutSessionId, metadata, referenceNo} =
    findPaymongoMetadata(event);
  if (!String(eventType || "").includes("paid")) {
    res.status(200).send("Ignored");
    return;
  }

  let paymentId = metadata.paymentId;
  if (!paymentId && checkoutSessionId) {
    const sessionSnap = await db
      .ref(`paymongo_checkout_sessions/${checkoutSessionId}`)
      .once("value");
    paymentId = sessionSnap.child("paymentId").val();
  }
  if (!paymentId) {
    res.status(400).send("Missing paymentId");
    return;
  }

  const paymentSnap = await db.ref(`coin_payments/${paymentId}`).once("value");
  const paymentData = paymentSnap.val();
  if (!paymentData) {
    res.status(400).send("Payment record not found");
    return;
  }

  const uid = paymentData.uid || paymentData.userId || metadata.uid;
  const coins = asNumber(paymentData.coins || metadata.coins);
  const amount = asNumber(paymentData.amount || metadata.amount);
  const currency = paymentData.currency || metadata.currency || "PHP";
  const transactionId = `paymongo_${paymentId}`;
  const paymentReferenceNo =
    paymentData.referenceNo || referenceNo || metadata.referenceNo || "";

  if (!uid || coins <= 0) {
    res.status(400).send("Invalid payment metadata");
    return;
  }

  const processingTimeoutMs = 5 * 60 * 1000;
  const lockStartedAt = Date.now();
  const processedRef = db.ref(`processed_paymongo_payments/${paymentId}`);
  const lockResult = await processedRef.transaction((current) => {
    if (current && current.status === "completed") return;

    const currentStartedAt = asNumber(current && current.processingStartedAt);
    const isFreshProcessing = current &&
      current.status === "processing" &&
      lockStartedAt - currentStartedAt < processingTimeoutMs;
    if (isFreshProcessing) return;

    return {
      paymentId,
      uid,
      userId: uid,
      coins,
      amount,
      currency,
      provider: "paymongo",
      status: "processing",
      webhookEventId: eventId || "",
      checkoutSessionId: checkoutSessionId || paymentData.checkoutSessionId || "",
      referenceNo: paymentReferenceNo,
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
    .once("value");
  const existingLegacyTxn = await db
    .ref(`transactions/${transactionId}`)
    .once("value");
  if (existingCoinTxn.exists() || existingLegacyTxn.exists()) {
    await processedRef.update({
      status: "completed",
      updatedAt: admin.database.ServerValue.TIMESTAMP,
    });
    res.status(200).send("Already credited");
    return;
  }

  const updates = {};
  updates[`users/${uid}/wallet/coins`] =
    admin.database.ServerValue.increment(coins);
  updates[`coin_payments/${paymentId}/status`] = "completed";
  updates[`coin_payments/${paymentId}/webhookEventId`] = eventId || "";
  updates[`coin_payments/${paymentId}/paidAt`] =
    admin.database.ServerValue.TIMESTAMP;
  updates[`coin_payments/${paymentId}/updatedAt`] =
    admin.database.ServerValue.TIMESTAMP;

  const sessionId = checkoutSessionId || paymentData.checkoutSessionId || "";
  if (sessionId) {
    updates[`paymongo_checkout_sessions/${sessionId}/status`] = "completed";
    updates[`paymongo_checkout_sessions/${sessionId}/paidAt`] =
      admin.database.ServerValue.TIMESTAMP;
    updates[`paymongo_checkout_sessions/${sessionId}/updatedAt`] =
      admin.database.ServerValue.TIMESTAMP;
  }

  updates[`processed_paymongo_payments/${paymentId}`] = {
    paymentId,
    uid,
    userId: uid,
    coins,
    amount,
    currency,
    provider: "paymongo",
    status: "completed",
    webhookEventId: eventId || "",
    checkoutSessionId: sessionId,
    referenceNo: paymentReferenceNo,
    transactionId,
    processedAt: admin.database.ServerValue.TIMESTAMP,
    updatedAt: admin.database.ServerValue.TIMESTAMP,
  };

  const transaction = {
    title: "Coin Purchase",
    type: "purchase",
    provider: "paymongo",
    paymentId,
    coins,
    amount,
    currency,
    status: "completed",
    transactionId,
    referenceNo: paymentReferenceNo,
    timestamp: admin.database.ServerValue.TIMESTAMP,
  };
  updates[`coin_transactions/${uid}/${transactionId}`] = transaction;
  updates[`transactions/${transactionId}`] = Object.assign(
    {userId: uid},
    transaction
  );

  try {
    await db.ref().update(updates);
    console.log("PayMongo payment credited", {paymentId, uid, transactionId});
    res.status(200).send("OK");
  } catch (error) {
    console.error("PayMongo multi-location update failed", {
      paymentId,
      uid,
      transactionId,
      message: error.message,
    });
    await processedRef.transaction((current) => {
      if (!current || current.status === "completed") return current;
      return Object.assign({}, current, {
        status: "failed_retryable",
        reason: "multi_location_update_failed",
        updatedAt: Date.now(),
      });
    }, undefined, false);
    res.status(500).send("Webhook processing failed");
  }
});

exports.confirmTradeCompletion =
  require("./confirmTradeCompletion").confirmTradeCompletion;

exports.createTradeRatingMessage =
  require("./createTradeRatingMessage").createTradeRatingMessage;

exports.sendTradeEventNotification =
  require("./sendTradeEventNotification").sendTradeEventNotification;

exports.syncItemLikeCount =
  require("./syncItemLikeCount").syncItemLikeCount;

const publicBadges = require("./syncPublicUserBadges");
exports.syncPublicUserBadgesFromUser =
  publicBadges.syncPublicUserBadgesFromUser;
exports.syncPublicUserBadgesFromReview =
  publicBadges.syncPublicUserBadgesFromReview;

exports.submitTradeReview =
  require("./reviews/submitTradeReview").submitTradeReview;

exports.requestAccountDeletion =
  require("./requestAccountDeletion").requestAccountDeletion;

exports.sendPasswordResetEmail =
  require("./passwordResetEmail").sendPasswordResetEmail;

exports.verifyGooglePlayCoinPurchase =
  require("./verifyGooglePlayCoinPurchase")
    .verifyGooglePlayCoinPurchase;

exports.getCloudinarySignature =
  require("./cloudinaryUpload").getCloudinarySignature;

const adminActions = require("./adminActions");

exports.adminSetIdVerification =
  adminActions.adminSetIdVerification;

exports.adminRejectAccountDeletion =
  adminActions.adminRejectAccountDeletion;

exports.adminCompleteAccountDeletion =
  adminActions.adminCompleteAccountDeletion;

exports.getIdVerificationImageUrls =
  adminActions.getIdVerificationImageUrls;


const adminFeed = require("./adminFeed");

exports.mirrorWalletTransactionToAdminFeed =
  adminFeed.mirrorWalletTransactionToAdminFeed;

exports.mirrorPremiumTransactionToAdminFeed =
  adminFeed.mirrorPremiumTransactionToAdminFeed;

exports.mirrorPayMongoPaymentToAdminFeed =
  adminFeed.mirrorPayMongoPaymentToAdminFeed;

exports.mirrorProcessedPayMongoPaymentToAdminFeed =
  adminFeed.mirrorProcessedPayMongoPaymentToAdminFeed;

exports.rebuildAdminStatsOnSchedule =
  adminFeed.rebuildAdminStatsOnSchedule;


const trustedRating = require("./trustedRating");

exports.syncTrustedRatingOnReviewWrite =
  trustedRating.syncTrustedRatingOnReviewWrite;

exports.backfillTrustedRating =
  trustedRating.backfillTrustedRating;

exports.backfillAllTrustedRatings =
  trustedRating.backfillAllTrustedRatings;


const tradeCompletionStats = require("./rewards/tradeCompletionStats");

exports.processCompletedTradeStats =
  tradeCompletionStats.processCompletedTradeStats;
