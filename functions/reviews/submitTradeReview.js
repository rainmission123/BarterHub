const {onCall, HttpsError} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.database();
const SAFE_KEY_PATTERN = /^[^.#$/[\]]+$/;

function isSafeRtdbKey(value, maxLength) {
  return (
    typeof value === "string" &&
    value.length > 0 &&
    value.length <= maxLength &&
    SAFE_KEY_PATTERN.test(value)
  );
}

exports.submitTradeReview = onCall(
    {
      region: "asia-southeast1",
      cpu: 0.083,
      memory: "256MiB",
      minInstances: 0,
      maxInstances: 1,
    },
    async (request) => {
      const uid = request.auth && request.auth.uid;

      if (!uid) {
        throw new HttpsError("unauthenticated", "Login required.");
      }

      const data = request.data || {};
      const tradeId = data.tradeId;
      const chatId = typeof data.chatId === "string" ? data.chatId.trim() : "";
      const messageId = typeof data.messageId === "string" ? data.messageId.trim() : "";
      const reviewedUserId = data.reviewedUserId;
      const rating = data.rating;
      const comment = data.comment;

      if (!isSafeRtdbKey(tradeId, 256)) {
        throw new HttpsError("invalid-argument", "Missing tradeId.");
      }

      if (!isSafeRtdbKey(reviewedUserId, 128)) {
        throw new HttpsError("invalid-argument", "Missing reviewedUserId.");
      }

      if (chatId && !isSafeRtdbKey(chatId, 300)) {
        throw new HttpsError("invalid-argument", "Invalid chatId.");
      }

      if (messageId && !isSafeRtdbKey(messageId, 256)) {
        throw new HttpsError("invalid-argument", "Invalid messageId.");
      }

      if (reviewedUserId === uid) {
        throw new HttpsError(
            "invalid-argument",
            "You cannot review yourself.",
        );
      }

      const safeRating = Number(rating);

      if (!Number.isInteger(safeRating) || safeRating < 1 || safeRating > 5) {
        throw new HttpsError("invalid-argument", "Rating must be 1 to 5.");
      }

      const safeComment = typeof comment === "string" ?
        comment.trim().substring(0, 500) :
        "";

      const tradeSnap = await db.ref("trade_requests/" + tradeId).get();

      if (!tradeSnap.exists()) {
        throw new HttpsError("not-found", "Trade not found.");
      }

      const trade = tradeSnap.val() || {};

      const fromUid =
        (trade.fromUser && trade.fromUser.userId) ||
        trade.fromUserId ||
        trade.requesterId ||
        trade.buyerId ||
        "";

      const toUid =
        (trade.toUser && trade.toUser.userId) ||
        trade.toUserId ||
        trade.ownerId ||
        trade.sellerId ||
        "";

      const isParticipant =
        uid === fromUid ||
        uid === toUid ||
        uid === trade.fromUserId ||
        uid === trade.toUserId ||
        uid === trade.ownerId ||
        uid === trade.requesterId;

      if (!isParticipant) {
        throw new HttpsError(
            "permission-denied",
            "You are not part of this trade.",
        );
      }

      const reviewedIsParticipant =
        reviewedUserId === fromUid ||
        reviewedUserId === toUid ||
        reviewedUserId === trade.fromUserId ||
        reviewedUserId === trade.toUserId ||
        reviewedUserId === trade.ownerId ||
        reviewedUserId === trade.requesterId;

      if (!reviewedIsParticipant) {
        throw new HttpsError(
            "permission-denied",
            "Reviewed user is not part of this trade.",
        );
      }

      const status = String(trade.status || "").toLowerCase();

      if (
        status !== "completed" &&
        status !== "complete" &&
        status !== "trade_completed"
      ) {
        throw new HttpsError(
            "failed-precondition",
            "Trade must be completed before rating.",
        );
      }

      const reviewId = tradeId + "_" + uid;
      const reviewRef = db.ref("reviews/" + reviewId);
      const existingSnap = await reviewRef.get();

      if (existingSnap.exists()) {
        throw new HttpsError(
            "already-exists",
            "You already reviewed this trade.",
        );
      }

      const now = Date.now();

      const reviewData = {
        reviewId: reviewId,
        tradeId: tradeId,
        reviewerId: uid,
        reviewedUserId: reviewedUserId,
        rating: safeRating,
        comment: safeComment,
        timestamp: now,
        createdAt: now,
      };

      const reviewedUserReviewsSnap = await db
          .ref("reviews")
          .orderByChild("reviewedUserId")
          .equalTo(reviewedUserId)
          .get();

      let totalRating = safeRating;
      let totalCount = 1;

      reviewedUserReviewsSnap.forEach((child) => {
        const childRating = Number(child.child("rating").val());

        if (
          Number.isFinite(childRating) &&
          childRating >= 1 &&
          childRating <= 5
        ) {
          totalRating += childRating;
          totalCount += 1;
        }
      });

      const averageRating = Math.round((totalRating / totalCount) * 10) / 10;

      const updates = {};

      updates["reviews/" + reviewId] = reviewData;
      updates["users/" + reviewedUserId + "/rating"] = averageRating;
      updates["users/" + reviewedUserId + "/reviewsCount"] = totalCount;
      updates["public_users/" + reviewedUserId + "/rating"] = averageRating;
      updates["public_users/" + reviewedUserId + "/reviewsCount"] = totalCount;
      updates["trade_requests/" + tradeId + "/ratings/" + uid] = true;
      updates["trade_requests/" + tradeId + "/updatedAt"] = now;

      if (chatId && messageId) {
        updates["chats/" + chatId + "/messages/" + messageId + "/tradeDetails/ratingStatus/" + uid] = "rated";
        updates["chats/" + chatId + "/messages/" + messageId + "/tradeDetails/ratedAt/" + uid] = now;
      }

      await db.ref().update(updates);

      return {
        success: true,
        reviewId: reviewId,
        rating: averageRating,
        reviewsCount: totalCount,
      };
    },
);
