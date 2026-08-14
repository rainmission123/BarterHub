/* eslint-disable max-len, no-console */

const admin = require("firebase-admin");

const REQUIRED_PROJECT_ID = "barterhub-3c947";
const DEFAULT_DATABASE_URL =
  "https://barterhub-3c947-default-rtdb.firebaseio.com";
const SAMPLE_LIMIT = 50;

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

const args = parseArgs(process.argv.slice(2));
const applyMode = args.apply === true;
const dryRun = !applyMode;
const projectId = args.project || process.env.GOOGLE_CLOUD_PROJECT ||
  process.env.GCLOUD_PROJECT || process.env.FIREBASE_PROJECT_ID ||
  REQUIRED_PROJECT_ID;
const databaseURL = args.databaseUrl || process.env.FIREBASE_DATABASE_URL ||
  DEFAULT_DATABASE_URL;

if (!admin.apps.length) {
  admin.initializeApp({
    projectId,
    databaseURL,
  });
}

const db = admin.database();

async function main() {
  assertProjectSafety();

  const [publicUsersSnap, usersSnap, reviewsSnap] = await Promise.all([
    db.ref("public_users").get(),
    db.ref("users").get(),
    db.ref("reviews").get(),
  ]);

  const publicUsers = publicUsersSnap.val() || {};
  const users = usersSnap.val() || {};
  const reviewedUserIds = collectReviewedUserIds(reviewsSnap);
  const report = emptyReport();
  const updates = {};

  for (const [uid, publicUser] of Object.entries(publicUsers)) {
    report.totalUsersScanned += 1;
    inspectUser({
      uid,
      publicUser: publicUser || {},
      user: users[uid] || {},
      reviewedUserIds,
      report,
      updates,
    });
  }

  report.plannedWritePaths = Object.keys(updates).length;

  if (applyMode && report.plannedWritePaths > 0) {
    await db.ref().update(updates);
    report.executedWritePaths = report.plannedWritePaths;
  }

  printReport(report);
}

function inspectUser(params) {
  const uid = params.uid;
  const publicUser = objectOrEmpty(params.publicUser);
  const user = objectOrEmpty(params.user);
  const badges = objectOrEmpty(publicUser.badges);
  const username = stringValue(publicUser.username);
  const ineligibilityReason = getBadgeIneligibilityReason(user, publicUser);

  if (ineligibilityReason) {
    params.report.skippedUsers += 1;
    sample(params.report.skippedUserSamples, {
      uid,
      username: username || null,
      reason: ineligibilityReason,
    });

    const changes = [];
    const badgeIds = Object.keys(badges);
    if (badgeIds.length > 0) {
      params.updates[`public_users/${uid}/badges`] = null;
      badgeIds.forEach((badgeId) => {
        changes.push({badgeId, action: "remove_ineligible"});
      });
      params.report.usersRequiringBadgeChanges += 1;
      sample(params.report.perUserChanges, {
        uid,
        username: username || null,
        reason: ineligibilityReason,
        changes,
      });
    }
    return;
  }

  const verified = user.isIDVerified === "verified";
  const rating = numberValue(user.rating);
  const firstTrade = rating > 0 || params.reviewedUserIds.has(uid);
  const changes = [];

  if (verified) params.report.qualifiedVerifiedCount += 1;
  if (firstTrade) params.report.qualifiedFirstTradeCount += 1;

  planBadge({
    uid,
    badgeId: "verified",
    shouldExist: verified,
    currentBadges: badges,
    updates: params.updates,
    changes,
  });

  planBadge({
    uid,
    badgeId: "first_trade",
    shouldExist: firstTrade,
    currentBadges: badges,
    updates: params.updates,
    changes,
  });

  const disabledRemoved = [];
  for (const badgeId of DISABLED_BADGES) {
    if (Object.prototype.hasOwnProperty.call(badges, badgeId)) {
      params.updates[`public_users/${uid}/badges/${badgeId}`] = null;
      disabledRemoved.push(badgeId);
      changes.push({badgeId, action: "remove_disabled"});
    }
  }

  if (disabledRemoved.length > 0) {
    params.report.disabledLegacyBadgeRemovals += disabledRemoved.length;
  }

  if (changes.length > 0) {
    params.report.usersRequiringBadgeChanges += 1;
    sample(params.report.perUserChanges, {
      uid,
      username: username || null,
      changes,
    });
  } else {
    params.report.usersAlreadyCorrect += 1;
  }
}

function planBadge(params) {
  const path = `public_users/${params.uid}/badges/${params.badgeId}`;
  const hasCurrentValue = Object.prototype.hasOwnProperty.call(
      params.currentBadges,
      params.badgeId,
  );
  const currentValue = params.currentBadges[params.badgeId];

  if (params.shouldExist) {
    if (currentValue !== true) {
      params.updates[path] = true;
      params.changes.push({badgeId: params.badgeId, action: "set_true"});
    }
    return;
  }

  if (hasCurrentValue) {
    params.updates[path] = null;
    params.changes.push({badgeId: params.badgeId, action: "remove_not_qualified"});
  }
}

function collectReviewedUserIds(reviewsSnap) {
  const ids = new Set();
  reviewsSnap.forEach((reviewSnap) => {
    const review = reviewSnap.val();
    if (!review || typeof review !== "object") return;

    const reviewedUserId = stringValue(review.reviewedUserId);
    if (reviewedUserId) ids.add(reviewedUserId);
  });
  return ids;
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

function assertProjectSafety() {
  if (projectId !== REQUIRED_PROJECT_ID) {
    throw new Error(
        "Refusing to run: project ID must be exactly " + REQUIRED_PROJECT_ID +
        ", got " + projectId,
    );
  }

  if (!databaseURL.includes(REQUIRED_PROJECT_ID)) {
    throw new Error(
        "Refusing to run: databaseURL does not match " + REQUIRED_PROJECT_ID,
    );
  }
}

function emptyReport() {
  return {
    mode: dryRun ? "DRY_RUN" : "APPLY",
    projectId,
    databaseURL,
    writeEnabled: applyMode,
    writableRoot: "public_users/{uid}/badges",
    activeBadges: ACTIVE_BADGES,
    disabledBadges: DISABLED_BADGES,
    totalUsersScanned: 0,
    qualifiedVerifiedCount: 0,
    qualifiedFirstTradeCount: 0,
    usersRequiringBadgeChanges: 0,
    usersAlreadyCorrect: 0,
    disabledLegacyBadgeRemovals: 0,
    plannedWritePaths: 0,
    executedWritePaths: 0,
    errors: 0,
    skippedUsers: 0,
    skippedUserSamples: [],
    perUserChanges: [],
  };
}

function printReport(report) {
  console.log("Public badge backfill " + report.mode);
  console.log("Dry-run mode: " + String(dryRun));
  console.log(JSON.stringify(report, null, 2));

  if (dryRun) {
    console.log("No writes were performed. Re-run with --apply to write changes.");
  }
}

function objectOrEmpty(value) {
  return value && typeof value === "object" && !Array.isArray(value) ? value : {};
}

function numberValue(value) {
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

function sample(target, value) {
  if (target.length < SAMPLE_LIMIT) target.push(value);
}

function parseArgs(argv) {
  const parsed = {};

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--apply") {
      parsed.apply = true;
    } else if (arg === "--project") {
      parsed.project = argv[i + 1];
      i += 1;
    } else if (arg.startsWith("--project=")) {
      parsed.project = arg.slice("--project=".length);
    } else if (arg === "--database-url") {
      parsed.databaseUrl = argv[i + 1];
      i += 1;
    } else if (arg.startsWith("--database-url=")) {
      parsed.databaseUrl = arg.slice("--database-url=".length);
    } else {
      throw new Error("Unknown argument: " + arg);
    }
  }

  return parsed;
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
