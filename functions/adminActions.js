const {onCall, HttpsError} = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

const ID_STORAGE_BUCKET = "barterhub-3c947.firebasestorage.app";
const SAFE_KEY_PATTERN = /^[^.#$/[\]]+$/;

function isSafeRtdbKey(value, maxLength) {
  return (
    typeof value === "string" &&
    value.length > 0 &&
    value.length <= maxLength &&
    SAFE_KEY_PATTERN.test(value)
  );
}

/**
 * Verifies that the callable request is from an admin user.
 *
 * @param {object} request Callable function request.
 * @return {Promise<string>} Authenticated admin uid.
 */
async function requireAdmin(request) {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError(
        "unauthenticated",
        "You must be signed in as an admin.",
    );
  }

  const uid = request.auth.uid;
  const adminSnap = await admin.database()
      .ref("admin_users/" + uid)
      .get();

  if (adminSnap.val() !== true) {
    throw new HttpsError(
        "permission-denied",
        "This account is not allowed to perform admin actions.",
    );
  }

  return uid;
}

/**
 * Reads and validates a target uid from callable request data.
 *
 * @param {object} data Callable function data.
 * @return {string} Target user uid.
 */
function requireUid(data) {
  const uid = typeof (data && data.uid) === "string" ?
    data.uid.trim() :
    "";

  if (!isSafeRtdbKey(uid, 128)) {
    throw new HttpsError("invalid-argument", "A valid uid is required.");
  }

  return uid;
}

exports.adminSetIdVerification = onCall(
    {region: "us-central1"},
    async (request) => {
      const adminUid = await requireAdmin(request);
      const uid = requireUid(request.data || {});
      const status = (request.data && request.data.status) || "";
      const allowed = ["pending", "verified", "rejected"];

      if (!allowed.includes(status)) {
        throw new HttpsError(
            "invalid-argument",
            "Invalid ID verification status.",
        );
      }

      const now = Date.now();
      const updates = {};

      updates["users/" + uid + "/isIDVerified"] = status;
      updates["users/" + uid + "/verifiedAt"] = now;
      updates["users/" + uid + "/verifiedBy"] = adminUid;
      updates["users/" + uid + "/updatedAt"] = now;

      updates["public_users/" + uid + "/isIDVerified"] = status;
      updates["public_users/" + uid + "/updatedAt"] = now;

      await admin.database().ref().update(updates);

      logger.info("Admin updated ID verification", {
        adminUid: adminUid,
        uid: uid,
        status: status,
      });

      return {success: true, status: status};
    },
);

exports.adminRejectAccountDeletion = onCall(
    {region: "us-central1"},
    async (request) => {
      const adminUid = await requireAdmin(request);
      const data = request.data || {};
      const uid = requireUid(data);
      const reason = String(data.reason || "Rejected by admin.")
          .trim()
          .slice(0, 500);
      const now = Date.now();

      const updates = {};
      updates["account_deletion_requests/" + uid + "/status"] = "rejected";
      updates["account_deletion_requests/" + uid + "/reviewedAt"] = now;
      updates["account_deletion_requests/" + uid + "/reviewedBy"] = adminUid;
      updates["account_deletion_requests/" + uid + "/reason"] = reason;

      updates["users/" + uid + "/accountStatus"] = null;
      updates["users/" + uid + "/accountDeletionRequested"] = false;
      updates["users/" + uid + "/updatedAt"] = now;

      updates["public_users/" + uid + "/accountStatus"] = null;
      updates["public_users/" + uid + "/accountDeletionRequested"] = false;
      updates["public_users/" + uid + "/updatedAt"] = now;

      await admin.database().ref().update(updates);

      logger.info("Admin rejected account deletion", {
        adminUid: adminUid,
        uid: uid,
      });

      return {success: true};
    },
);

exports.adminCompleteAccountDeletion = onCall(
    {region: "us-central1"},
    async (request) => {
      const adminUid = await requireAdmin(request);
      const data = request.data || {};
      const uid = requireUid(data);
      const note = String(data.note || "Processed by admin.")
          .trim()
          .slice(0, 500);
      const now = Date.now();

      const requestSnap = await admin.database()
          .ref("account_deletion_requests/" + uid)
          .get();

      if (!requestSnap.exists()) {
        throw new HttpsError(
            "not-found",
            "No deletion request exists for this user.",
        );
      }

      const deletedName = "Deleted User";
      const updates = {};

      updates["users/" + uid + "/accountStatus"] = "deleted";
      updates["users/" + uid + "/accountDeletionCompleted"] = true;
      updates["users/" + uid + "/accountDeletionCompletedAt"] = now;
      updates["users/" + uid + "/fullName"] = deletedName;
      updates["users/" + uid + "/username"] = "deleted_user";
      updates["users/" + uid + "/email"] = null;
      updates["users/" + uid + "/phoneNumber"] = null;
      updates["users/" + uid + "/address"] = null;
      updates["users/" + uid + "/bio"] = null;
      updates["users/" + uid + "/profileImageUrl"] = null;
      updates["users/" + uid + "/idFrontUrl"] = null;
      updates["users/" + uid + "/idBackUrl"] = null;
      updates["users/" + uid + "/idFrontPath"] = null;
      updates["users/" + uid + "/idBackPath"] = null;
      updates["users/" + uid + "/fcmToken"] = null;
      updates["users/" + uid + "/updatedAt"] = now;

      updates["public_users/" + uid + "/accountStatus"] = "deleted";
      updates["public_users/" + uid + "/fullName"] = deletedName;
      updates["public_users/" + uid + "/username"] = "deleted_user";
      updates["public_users/" + uid + "/profileImageUrl"] = null;
      updates["public_users/" + uid + "/bio"] = null;
      updates["public_users/" + uid + "/updatedAt"] = now;

      updates["fcm_tokens/" + uid] = null;
      updates["notifications/" + uid] = null;
      updates["favorites/" + uid] = null;

      updates["account_deletion_requests/" + uid + "/status"] = "completed";
      updates["account_deletion_requests/" + uid + "/completedAt"] = now;
      updates["account_deletion_requests/" + uid + "/completedBy"] = adminUid;
      updates["account_deletion_requests/" + uid + "/note"] = note;

      await admin.database().ref().update(updates);

      try {
        await admin.auth().deleteUser(uid);
      } catch (error) {
        logger.warn("Auth user delete failed or user already missing", {
          uid: uid,
          message: error.message,
        });
      }

      logger.info("Admin completed account deletion", {
        adminUid: adminUid,
        uid: uid,
      });

      return {success: true};
    },
);

exports.getIdVerificationImageUrls = onCall(
    {region: "us-central1"},
    async (request) => {
      await requireAdmin(request);

      const uid = requireUid(request.data || {});

      const userSnap = await admin.database()
          .ref("users/" + uid)
          .get();

      if (!userSnap.exists()) {
        throw new HttpsError(
            "not-found",
            "User not found.",
        );
      }

      const user = userSnap.val() || {};
      const frontPath = user.idFrontPath || "";
      const backPath = user.idBackPath || "";

      if (!frontPath && !backPath) {
        return {
          frontUrl: "",
          backUrl: "",
        };
      }

      const bucket = admin.storage().bucket(ID_STORAGE_BUCKET);
      const expiresAt = Date.now() + 15 * 60 * 1000;

      /**
       * Creates a short-lived signed URL for a verified ID image path.
       *
       * @param {string} path Firebase Storage object path.
       * @return {Promise<string>} Signed image URL, or empty string.
       */
      async function createSignedUrl(path) {
        if (!path || typeof path !== "string") {
          return "";
        }

        if (!path.startsWith("id_verifications/" + uid + "/")) {
          throw new HttpsError(
              "permission-denied",
              "Invalid ID verification image path.",
          );
        }

        const file = bucket.file(path);
        const [exists] = await file.exists();

        if (!exists) {
          logger.warn("ID verification image file missing", {
            uid: uid,
            path: path,
          });
          return "";
        }

        const [url] = await file.getSignedUrl({
          action: "read",
          expires: expiresAt,
        });

        return url;
      }

      const frontUrl = await createSignedUrl(frontPath);
      const backUrl = await createSignedUrl(backPath);

      logger.info("Admin requested ID verification image URLs", {
        adminUid: request.auth.uid,
        uid: uid,
        hasFront: Boolean(frontUrl),
        hasBack: Boolean(backUrl),
      });

      return {
        frontUrl: frontUrl,
        backUrl: backUrl,
        expiresAt: expiresAt,
      };
    },
);
