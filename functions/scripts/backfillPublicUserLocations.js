/* eslint-disable no-console */
"use strict";

const admin = require("firebase-admin");

const DATABASE_URL =
  process.env.FIREBASE_DATABASE_URL ||
  "https://barterhub-3c947-default-rtdb.firebaseio.com";

const APPLY = process.argv.includes("--apply");
const BATCH_SIZE = 400;

if (!admin.apps.length) {
  admin.initializeApp({databaseURL: DATABASE_URL});
}

const db = admin.database();

function asString(value) {
  return typeof value === "string" ? value.trim() : "";
}

function firstNonBlank(...values) {
  return values.find((value) => asString(value).length > 0)?.trim() || "";
}

function buildLocation(city, province) {
  const safeCity = asString(city);
  const safeProvince = asString(province);

  if (safeCity && safeProvince) return `${safeCity}, ${safeProvince}`;
  if (safeProvince) return safeProvince;
  if (safeCity) return safeCity;
  return "";
}

function isBlank(value) {
  return value === undefined || value === null || asString(value) === "";
}

function buildSafePublicFields(user) {
  const cityMunicipality = asString(user.cityMunicipality);
  const city = firstNonBlank(user.city, user.cityMunicipality);
  const province = asString(user.province);
  const locationSource = firstNonBlank(cityMunicipality, city);
  const displayLocation = buildLocation(locationSource, province);

  if (!displayLocation) return null;

  const fields = {};
  if (cityMunicipality) fields.cityMunicipality = cityMunicipality;
  if (city) fields.city = city;
  if (province) fields.province = province;
  fields.location = displayLocation;
  fields.addressText = displayLocation;
  return fields;
}

function buildMissingFieldUpdates(uid, user, publicUser) {
  const safeFields = buildSafePublicFields(user);
  if (!safeFields) return null;

  const updates = {};
  for (const [key, value] of Object.entries(safeFields)) {
    if (isBlank(publicUser[key]) && !isBlank(value)) {
      updates[`public_users/${uid}/${key}`] = value;
    }
  }

  return Object.keys(updates).length > 0 ? updates : null;
}

async function applyInBatches(updatesByPath) {
  const entries = Object.entries(updatesByPath);
  let updatedUsers = 0;
  let failedBatches = 0;

  for (let index = 0; index < entries.length; index += BATCH_SIZE) {
    const batchEntries = entries.slice(index, index + BATCH_SIZE);
    const batch = Object.fromEntries(batchEntries);
    try {
      await db.ref().update(batch);
      updatedUsers += new Set(
        batchEntries.map(([path]) => path.split("/")[1]),
      ).size;
    } catch (error) {
      failedBatches++;
      console.error("Batch update failed", {
        start: index,
        size: batchEntries.length,
        message: error.message,
      });
    }
  }

  return {updatedUsers, failedBatches};
}

async function main() {
  console.log(
    APPLY ?
      "Running public user location backfill in APPLY mode." :
      "Running public user location backfill in DRY-RUN mode.",
  );

  const [usersSnap, publicUsersSnap] = await Promise.all([
    db.ref("users").get(),
    db.ref("public_users").get(),
  ]);

  const users = usersSnap.val() || {};
  const publicUsers = publicUsersSnap.val() || {};
  const updates = {};
  const counts = {
    scanned: 0,
    skipped: 0,
    planned: 0,
    updated: 0,
    failed: 0,
  };

  for (const [uid, user] of Object.entries(users)) {
    counts.scanned++;
    const publicUser = publicUsers[uid] || {};
    const missingUpdates = buildMissingFieldUpdates(uid, user || {}, publicUser);

    if (!missingUpdates) {
      counts.skipped++;
      continue;
    }

    counts.planned++;
    Object.assign(updates, missingUpdates);
  }

  console.log("Backfill plan", {
    ...counts,
    fieldUpdates: Object.keys(updates).length,
  });

  if (!APPLY) {
    console.log("Dry run only. Re-run with --apply to write these updates.");
    return;
  }

  const result = await applyInBatches(updates);
  counts.updated = result.updatedUsers;
  counts.failed = result.failedBatches;

  console.log("Backfill complete", {
    ...counts,
    fieldUpdates: Object.keys(updates).length,
  });
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("Backfill failed", error);
    process.exit(1);
  });
