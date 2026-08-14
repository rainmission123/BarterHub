const {onValueWritten} = require("firebase-functions/v2/database");
const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");

const ACTIVE_BADGES = ["verified", "first_trade"];
const DISABLED_BADGES = [
  "top_trader",
  "top_1",
  "top_2",
  "top_3",
  "top_4",
  "top_5",
  "top_6",
  "top_7",
  "top_8",
  "top_9",
  "top_10",
  "community",
  "friendly",
  "reliable",
];

const triggerOptions = {
  region: "us-central1",
  instance: "barterhub-3c947-default-rtdb",
  cpu: 0.083,
  memory: "256MiB",
  minInstances: 0,
  maxInstances: 1,
};

function asNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function stringValue(value) {
  return typeof value === "string" ? value.trim() : "";
}

function normalizedStatus(value) {
  return stringValue(value).toLowerCase();
}

function isTrue(value) {
  return value === true;
}

function getBadgeIneligibilityReason(user, publicUser) {
  const publicUsername = stringValue(publicUser.username);
  const blockedStatuses = new Set([
    "deleted",
    "pending_deletion",
    "deactivated",
    "disabled",
  ]);

  if (!publicUsername) return "missing_public_username";
  if (publicUsername.toLowerCase() === "deleted_user") {
    return "deleted_public_username";
  }
  if (
    blockedStatuses.has(normalizedStatus(user.accountStatus)) ||
    blockedStatuses.has(normalizedStatus(publicUser.accountStatus))
  ) {
    return "blocked_account_status";
  }
  if (
    isTrue(user.accountDeletionRequested) ||
    isTrue(publicUser.accountDeletionRequested)
  ) {
    return "account_deletion_requested";
  }
  if (
    isTrue(user.accountDeletionCompleted) ||
    isTrue(publicUser.accountDeletionCompleted)
  ) {
    return "account_deletion_completed";
  }

  return null;
}

function collectReviewedUserIds(beforeValue, afterValue) {
  const ids = new Set();
  const beforeReviewedUserId = beforeValue && beforeValue.reviewedUserId;
  const afterReviewedUserId = afterValue && afterValue.reviewedUserId;

  if (beforeReviewedUserId) ids.add(String(beforeReviewedUserId));
  if (afterReviewedUserId) ids.add(String(afterReviewedUserId));

  return Array.from(ids);
}

async function syncPublicUserBadges(uid) {
  if (!uid) return null;

  const db = admin.database();
  const userSnap = await db.ref(`users/${uid}`).get();
  const publicUserSnap = await db.ref(`public_users/${uid}`).get();

  if (!publicUserSnap.exists()) {
    logger.info("syncPublicUserBadges skipped: public user missing", {uid});
    return null;
  }

  const publicBadgesRef = db.ref(`public_users/${uid}/badges`);

  if (!userSnap.exists()) {
    await publicBadgesRef.remove();
    logger.info("syncPublicUserBadges removed badges for missing user", {uid});
    return null;
  }

  const user = userSnap.val() || {};
  const publicUser = publicUserSnap.val() || {};
  const ineligibilityReason = getBadgeIneligibilityReason(user, publicUser);
  if (ineligibilityReason) {
    await publicBadgesRef.remove();
    logger.info("syncPublicUserBadges removed badges for ineligible user", {
      uid,
      reason: ineligibilityReason,
    });
    return null;
  }

  const verified = user.isIDVerified === "verified";
  const rating = asNumber(user.rating);
  const reviewSnap = await db
      .ref("reviews")
      .orderByChild("reviewedUserId")
      .equalTo(uid)
      .limitToFirst(1)
      .get();
  const firstTrade = rating > 0 || reviewSnap.exists();

  const updates = {};
  ACTIVE_BADGES.forEach((badgeId) => {
    updates[badgeId] = null;
  });
  DISABLED_BADGES.forEach((badgeId) => {
    updates[badgeId] = null;
  });

  if (verified) updates.verified = true;
  if (firstTrade) updates.first_trade = true;

  await publicBadgesRef.update(updates);
  logger.info("syncPublicUserBadges updated public badges", {
    uid,
    verified,
    firstTrade,
  });

  return null;
}

exports.syncPublicUserBadgesFromUser = onValueWritten(
    {
      ref: "/users/{uid}",
      ...triggerOptions,
    },
    async (event) => syncPublicUserBadges(event.params.uid),
);

exports.syncPublicUserBadgesFromReview = onValueWritten(
    {
      ref: "/reviews/{reviewId}",
      ...triggerOptions,
    },
    async (event) => {
      const beforeValue = event.data.before.val();
      const afterValue = event.data.after.val();
      const reviewedUserIds = collectReviewedUserIds(beforeValue, afterValue);

      await Promise.all(reviewedUserIds.map((uid) => syncPublicUserBadges(uid)));
      return null;
    },
);
