const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {defineSecret} = require("firebase-functions/params");
const cloudinary = require("cloudinary").v2;

const CLOUDINARY_CLOUD_NAME = defineSecret("CLOUDINARY_CLOUD_NAME");
const CLOUDINARY_API_KEY = defineSecret("CLOUDINARY_API_KEY");
const CLOUDINARY_API_SECRET = defineSecret("CLOUDINARY_API_SECRET");

exports.getCloudinarySignature = onCall(
    {
      secrets: [
        CLOUDINARY_CLOUD_NAME,
        CLOUDINARY_API_KEY,
        CLOUDINARY_API_SECRET,
      ],
    },
    async (request) => {
      if (!request.auth) {
        throw new HttpsError(
            "unauthenticated",
            "You must be logged in to upload images.",
        );
      }

      cloudinary.config({
        cloud_name: CLOUDINARY_CLOUD_NAME.value(),
        api_key: CLOUDINARY_API_KEY.value(),
        api_secret: CLOUDINARY_API_SECRET.value(),
      });

      const timestamp = Math.round(Date.now() / 1000);
      const folder = `barterhub/${request.auth.uid}`;

      const signature = cloudinary.utils.api_sign_request(
          {
            timestamp,
            folder,
          },
          CLOUDINARY_API_SECRET.value(),
      );

      return {
        cloudName: CLOUDINARY_CLOUD_NAME.value(),
        apiKey: CLOUDINARY_API_KEY.value(),
        timestamp,
        folder,
        signature,
      };
    },
);
