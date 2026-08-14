const {onValueWritten} = require("firebase-functions/v2/database");
const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");

exports.syncItemLikeCount = onValueWritten(
    {
      ref: "/itemLikes/{itemId}/{userId}",
      region: "us-central1",
      instance: "barterhub-3c947-default-rtdb",
      cpu: 0.083,
      memory: "256MiB",
      minInstances: 0,
      maxInstances: 1,
    },
    async (event) => {
      const itemId = event.params.itemId;
      const userId = event.params.userId;

      if (!itemId) {
        logger.warn("syncItemLikeCount skipped: missing itemId", {userId});
        return null;
      }

      const db = admin.database();
      const itemRef = db.ref("items/" + itemId);
      const itemSnap = await itemRef.get();

      if (!itemSnap.exists()) {
        logger.info("syncItemLikeCount skipped: item no longer exists", {
          itemId,
          userId,
        });
        return null;
      }

      const likesSnap = await db.ref("itemLikes/" + itemId).get();
      let likeCount = 0;

      likesSnap.forEach((child) => {
        if (child.val() === true) {
          likeCount += 1;
        }
      });

      likeCount = Math.max(0, likeCount);

      await itemRef.child("likeCount").set(likeCount);
      logger.info("syncItemLikeCount updated item likeCount", {
        itemId,
        userId,
        likeCount,
      });

      return null;
    },
);
