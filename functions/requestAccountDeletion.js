const {onCall, HttpsError} = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

exports.requestAccountDeletion = onCall(
    {region: "us-central1"},
    async (request) => {
      if (!request.auth || !request.auth.uid) {
        throw new HttpsError(
            "unauthenticated",
            "You must be signed in to request account deletion.",
        );
      }

      const uid = request.auth.uid;
      const now = Date.now();

      const userSnap = await admin.database()
          .ref("users/" + uid)
          .get();

      if (!userSnap.exists()) {
        throw new HttpsError(
            "not-found",
            "User profile was not found.",
        );
      }

      const updates = {};

      updates["users/" + uid + "/accountStatus"] = "pending_deletion";
      updates["users/" + uid + "/accountDeletionRequested"] = true;
      updates["users/" + uid + "/accountDeletionRequestedAt"] = now;
      updates["users/" + uid + "/updatedAt"] = now;

      updates["public_users/" + uid + "/accountStatus"] =
        "pending_deletion";
      updates["public_users/" + uid + "/accountDeletionRequested"] = true;
      updates["public_users/" + uid + "/updatedAt"] = now;

      updates["account_deletion_requests/" + uid] = {
        uid: uid,
        status: "pending",
        requestedAt: now,
        source: "android_app",
      };

      await admin.database().ref().update(updates);

      logger.info("Account deletion requested", {
        uid: uid,
      });

      return {
        success: true,
        status: "pending",
      };
    },
);
