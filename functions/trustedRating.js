const {onValueWritten} = require("firebase-functions/v2/database");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

const PRIOR_RATING = 4.0;
const PRIOR_COUNT = 5;

/**
 * Recalculates trusted rating for a user.
 *
 * @param {string} uid User ID.
 * @return {Promise<object|null>} Rating result.
 */
async function recalculateTrustedRating(uid) {
  if (!uid || typeof uid !== "string") {
    return null;
  }

  const db = admin.database();

  const reviewsSnap = await db
      .ref("reviews")
      .orderByChild("reviewedUserId")
      .equalTo(uid)
      .get();

  let sum = 0;
  let count = 0;

  reviewsSnap.forEach((reviewSnap) => {
    const rawRating = reviewSnap.child("rating").val();
    const rating = Number(rawRating);

    if (!Number.isFinite(rating)) {
      return;
    }

    if (rating <= 0 || rating > 5) {
      return;
    }

    sum += rating;
    count += 1;
  });

  const finalRating = count > 0 ?
    ((PRIOR_RATING * PRIOR_COUNT) + sum) / (PRIOR_COUNT + count) :
    0;

  const roundedRating = Math.round(finalRating * 10) / 10;

  const updates = {};
  updates[`users/${uid}/rating`] = roundedRating;
  updates[`users/${uid}/reviewsCount`] = count;
  updates[`public_users/${uid}/rating`] = roundedRating;
  updates[`public_users/${uid}/reviewsCount`] = count;
  updates[`public_users/${uid}/updatedAt`] =
    admin.database.ServerValue.TIMESTAMP;

  await db.ref().update(updates);

  return {
    uid,
    rating: roundedRating,
    reviewsCount: count,
  };
}

exports.syncTrustedRatingOnReviewWrite = onValueWritten(
    "/reviews/{reviewId}",
    async (event) => {
      const before = event.data.before;
      const after = event.data.after;

      const beforeUid = before.exists() ?
        before.child("reviewedUserId").val() :
        null;

      const afterUid = after.exists() ?
        after.child("reviewedUserId").val() :
        null;

      const affectedUids = new Set();

      if (beforeUid) {
        affectedUids.add(beforeUid);
      }

      if (afterUid) {
        affectedUids.add(afterUid);
      }

      await Promise.all(
          Array.from(affectedUids).map((uid) =>
            recalculateTrustedRating(uid),
          ),
      );

      return null;
    },
);

exports.backfillTrustedRating = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }

  const uid = request.data && request.data.uid;

  if (!uid || typeof uid !== "string") {
    throw new HttpsError("invalid-argument", "uid is required.");
  }

  const isAdminSnap = await admin.database()
      .ref(`admin_users/${request.auth.uid}`)
      .get();

  if (isAdminSnap.val() !== true) {
    throw new HttpsError("permission-denied", "Admin only.");
  }

  return recalculateTrustedRating(uid);
});

exports.backfillAllTrustedRatings = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }

  const adminSnap = await admin.database()
      .ref(`admin_users/${request.auth.uid}`)
      .get();

  if (adminSnap.val() !== true) {
    throw new HttpsError("permission-denied", "Admin only.");
  }

  const db = admin.database();

  const [usersSnap, publicUsersSnap, reviewsSnap] = await Promise.all([
    db.ref("users").get(),
    db.ref("public_users").get(),
    db.ref("reviews").get(),
  ]);

  const userIds = new Set();

  usersSnap.forEach((snap) => {
    if (snap.key) {
      userIds.add(snap.key);
    }
  });

  publicUsersSnap.forEach((snap) => {
    if (snap.key) {
      userIds.add(snap.key);
    }
  });

  const ratingsByUser = new Map();

  reviewsSnap.forEach((reviewSnap) => {
    const uid = reviewSnap.child("reviewedUserId").val();
    const rating = Number(reviewSnap.child("rating").val());

    if (!uid || typeof uid !== "string") {
      return;
    }

    if (!Number.isFinite(rating) || rating <= 0 || rating > 5) {
      return;
    }

    if (!ratingsByUser.has(uid)) {
      ratingsByUser.set(uid, {
        sum: 0,
        count: 0,
      });
    }

    const aggregate = ratingsByUser.get(uid);
    aggregate.sum += rating;
    aggregate.count += 1;
    userIds.add(uid);
  });

  const updates = {};
  let processedUsers = 0;

  userIds.forEach((uid) => {
    const aggregate = ratingsByUser.get(uid) || {
      sum: 0,
      count: 0,
    };

    const finalRating = aggregate.count > 0 ?
      ((PRIOR_RATING * PRIOR_COUNT) + aggregate.sum) /
        (PRIOR_COUNT + aggregate.count) :
      0;

    const roundedRating = Math.round(finalRating * 10) / 10;

    updates[`users/${uid}/rating`] = roundedRating;
    updates[`users/${uid}/reviewsCount`] = aggregate.count;
    updates[`public_users/${uid}/rating`] = roundedRating;
    updates[`public_users/${uid}/reviewsCount`] = aggregate.count;
    updates[`public_users/${uid}/updatedAt`] =
      admin.database.ServerValue.TIMESTAMP;

    processedUsers += 1;
  });

  if (Object.keys(updates).length > 0) {
    await db.ref().update(updates);
  }

  return {
    success: true,
    processedUsers,
    reviewedUsers: ratingsByUser.size,
  };
});
