/* eslint-disable max-len, no-console */

const admin = require("firebase-admin");

const REQUIRED_PROJECT_ID = "barterhub-3c947";
const DEFAULT_DATABASE_URL =
  "https://barterhub-3c947-default-rtdb.firebaseio.com";
const DEFAULT_PAGE_SIZE = 500;
const DEFAULT_WRITE_BATCH_SIZE = 400;
const SAMPLE_LIMIT = 20;

const args = new Set(process.argv.slice(2));
const executeMode =
  args.has("--execute") || process.env.BACKFILL_EXECUTE === "true";
const dryRun = !executeMode;
const projectId =
  process.env.GCLOUD_PROJECT ||
  process.env.GOOGLE_CLOUD_PROJECT ||
  process.env.FIREBASE_PROJECT_ID ||
  REQUIRED_PROJECT_ID;
const databaseURL = process.env.FIREBASE_DATABASE_URL || DEFAULT_DATABASE_URL;
const pageSize = positiveInteger(process.env.PAGE_SIZE, DEFAULT_PAGE_SIZE);
const writeBatchSize = positiveInteger(
    process.env.WRITE_BATCH_SIZE,
    DEFAULT_WRITE_BATCH_SIZE,
);

if (!admin.apps.length) {
  admin.initializeApp({
    projectId,
    databaseURL,
  });
}

const db = admin.database();

async function main() {
  assertProjectSafety();

  const report = emptyReport();
  const allExecutableUpdates = {};
  let lastKey = "";

  while (true) { // eslint-disable-line no-constant-condition
    let query = db.ref("completed_trades").orderByKey().limitToFirst(pageSize);
    if (lastKey) query = query.startAfter(lastKey);

    const pageSnap = await query.get();
    if (!pageSnap.exists()) break;

    const updates = {};
    const existingChecks = [];
    let pageCount = 0;

    pageSnap.forEach((child) => {
      pageCount += 1;
      lastKey = child.key;
      inspectTradeRecord({
        tradeId: child.key,
        trade: child.val() || {},
        updates,
        existingChecks,
        report,
      });
    });

    const executableUpdates = await resolveExistingIndexes(
        report,
        existingChecks,
        updates,
    );

    for (const path of Object.keys(executableUpdates)) {
      allExecutableUpdates[path] = executableUpdates[path];
    }

    if (pageCount < pageSize) break;
  }

  report.estimatedBatches = Math.ceil(report.plannedWrites / writeBatchSize);
  enforceExecuteReadiness(report);

  if (!dryRun && Object.keys(allExecutableUpdates).length > 0) {
    await writeUpdatesInBatches(allExecutableUpdates, report);
  }

  printReport(report);
}

function inspectTradeRecord(params) {
  const tradeId = params.tradeId;
  const trade = params.trade;
  const report = params.report;
  const updates = params.updates;
  const existingChecks = params.existingChecks;

  report.scannedRecords += 1;
  recordStatus(report, trade.status);
  recordFieldPresence(report, trade);

  if (!trade || typeof trade !== "object") {
    skipMalformed(report, tradeId, "not_object");
    return;
  }

  if (trade.status !== "Completed") {
    report.skippedNonCompleted += 1;
    sample(report.skippedSamples, {tradeId, status: trade.status || null});
    return;
  }

  const fromUserId = stringField(trade.fromUserId);
  const toUserId = stringField(trade.toUserId);

  if (!fromUserId || !toUserId) {
    skipMalformed(report, tradeId, "missing_participants");
    return;
  }

  if (fromUserId === toUserId) {
    skipMalformed(report, tradeId, "same_participants");
    return;
  }

  report.validCompletedTrades += 1;

  const indexValue = {
    tradeId,
    completedAt: trade.completedAt || null,
    status: "Completed",
  };

  planIndexWrite({
    report,
    updates,
    existingChecks,
    uid: fromUserId,
    tradeId,
    indexValue,
  });
  planIndexWrite({
    report,
    updates,
    existingChecks,
    uid: toUserId,
    tradeId,
    indexValue,
  });
}

function planIndexWrite(params) {
  const path =
    "completed_trades_by_user/" + params.uid + "/" + params.tradeId;
  params.report.expectedIndexEntries += 1;
  params.updates[path] = params.indexValue;
  params.existingChecks.push({
    path,
    uid: params.uid,
    tradeId: params.tradeId,
    indexValue: params.indexValue,
  });
}

async function resolveExistingIndexes(report, existingChecks, updates) {
  const executableUpdates = {};

  for (const entry of existingChecks) {
    const existingSnap = await db.ref(entry.path).get();
    if (!existingSnap.exists()) {
      report.plannedWrites += 1;
      executableUpdates[entry.path] = updates[entry.path];
      sample(report.plannedUpdateSamples, {
        path: redactIndexPath(entry.path),
        value: entry.indexValue,
      });
      continue;
    }

    const existing = existingSnap.val();
    if (stableJson(existing) === stableJson(entry.indexValue)) {
      report.alreadyExistingIdentical += 1;
      continue;
    }

    report.conflictingEntries += 1;
    sample(report.conflictSamples, {
      path: redactIndexPath(entry.path),
      existing,
      planned: entry.indexValue,
    });
  }

  return executableUpdates;
}

async function writeUpdatesInBatches(updates, report) {
  const paths = Object.keys(updates);
  for (let i = 0; i < paths.length; i += writeBatchSize) {
    const batchPaths = paths.slice(i, i + writeBatchSize);
    const batch = {};
    for (const path of batchPaths) batch[path] = updates[path];
    await db.ref().update(batch);
    report.executedBatches += 1;
    report.executedWrites += batchPaths.length;
  }
}

function enforceExecuteReadiness(report) {
  if (dryRun) return;

  const reviewedMalformed = process.env.MALFORMED_RECORDS_REVIEWED === "true";
  const backupConfirmed = process.env.FIREBASE_BACKUP_CONFIRMED === "true";

  if (report.conflictingEntries > 0) {
    throw new Error("Refusing execute mode: unresolved conflicts exist.");
  }
  if (report.malformedRecords > 0 && !reviewedMalformed) {
    throw new Error(
        "Refusing execute mode: malformed records require review. " +
        "Set MALFORMED_RECORDS_REVIEWED=true after review.",
    );
  }
  if (!backupConfirmed) {
    throw new Error(
        "Refusing execute mode: export/backup confirmation required. " +
        "Set FIREBASE_BACKUP_CONFIRMED=true after backup.",
    );
  }
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

  if (executeMode && process.env.BACKFILL_EXECUTE !== "true") {
    throw new Error(
        "Refusing execute mode: set BACKFILL_EXECUTE=true and pass --execute.",
    );
  }
}

function emptyReport() {
  return {
    mode: dryRun ? "DRY_RUN" : "EXECUTE",
    projectId,
    databaseURL,
    scannedRecords: 0,
    validCompletedTrades: 0,
    skippedMalformedRecords: 0,
    skippedNonCompleted: 0,
    expectedIndexEntries: 0,
    alreadyExistingIdentical: 0,
    conflictingEntries: 0,
    plannedWrites: 0,
    estimatedBatches: 0,
    executedBatches: 0,
    executedWrites: 0,
    statusCounts: {},
    fieldPresence: {},
    malformedRecords: 0,
    malformedSamples: [],
    skippedSamples: [],
    conflictSamples: [],
    plannedUpdateSamples: [],
  };
}

function printReport(report) {
  console.log(JSON.stringify(report, null, 2));
}

function skipMalformed(report, tradeId, reason) {
  report.malformedRecords += 1;
  report.skippedMalformedRecords += 1;
  sample(report.malformedSamples, {tradeId, reason});
}

function recordStatus(report, status) {
  const key = status === undefined || status === null ? "__missing__" : String(status);
  report.statusCounts[key] = (report.statusCounts[key] || 0) + 1;
}

function recordFieldPresence(report, trade) {
  if (!trade || typeof trade !== "object") return;
  for (const key of Object.keys(trade)) {
    report.fieldPresence[key] = (report.fieldPresence[key] || 0) + 1;
  }
}

function sample(target, value) {
  if (target.length < SAMPLE_LIMIT) target.push(value);
}

function stringField(value) {
  return typeof value === "string" ? value.trim() : "";
}

function stableJson(value) {
  return JSON.stringify(sortObject(value));
}

function sortObject(value) {
  if (Array.isArray(value)) return value.map(sortObject);
  if (!value || typeof value !== "object") return value;
  return Object.keys(value).sort().reduce((acc, key) => {
    acc[key] = sortObject(value[key]);
    return acc;
  }, {});
}

function redactIndexPath(path) {
  const parts = path.split("/");
  if (parts.length >= 3) parts[1] = redactUid(parts[1]);
  return parts.join("/");
}

function redactUid(uid) {
  if (!uid || uid.length <= 8) return "***";
  return uid.slice(0, 4) + "..." + uid.slice(-4);
}

function positiveInteger(value, fallback) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
