const admin = require("firebase-admin");
const sgMail = require("@sendgrid/mail");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {defineSecret} = require("firebase-functions/params");
const crypto = require("crypto");

const SENDGRID_API_KEY = defineSecret("SENDGRID_API_KEY");
const GENERIC_RESET_MESSAGE =
  "If an account exists for that email, a password reset email will be sent.";

function isValidEmail(email) {
  return (
    typeof email === "string" &&
    email.length <= 254 &&
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
  );
}

function emailHash(email) {
  return crypto
      .createHash("sha256")
      .update(email)
      .digest("hex")
      .slice(0, 16);
}

exports.sendPasswordResetEmail = onCall(
    {
      region: "us-central1",
      secrets: [SENDGRID_API_KEY],
    },
    async (request) => {
      const email = String(request.data.email || "").trim().toLowerCase();

      if (!isValidEmail(email)) {
        throw new HttpsError(
            "invalid-argument",
            "A valid email is required.",
        );
      }

      try {
        sgMail.setApiKey(SENDGRID_API_KEY.value());

        const resetLink = await admin
            .auth()
            .generatePasswordResetLink(email);

        const html = `
          <div style="font-family:Arial,sans-serif;padding:24px;">
            <div style="max-width:560px;margin:auto;padding:28px;">
              <h1 style="color:#0B8F3A;">Reset Your Password</h1>
              <p>Hello,</p>
              <p>Someone requested to reset your BarterHub PH password.</p>
              <p>
                <a href="${resetLink}">
                  Reset Password
                </a>
              </p>
              <p>If you did not request this, you can ignore this email.</p>
              <p>BarterHub PH Team</p>
            </div>
          </div>
        `;

        await sgMail.send({
          to: email,
          from: {
            email: "barterhubph.team@gmail.com",
            name: "BarterHub PH",
          },
          subject: "Reset your BarterHub PH password",
          html: html,
        });

        console.log("Password reset email sent:", {
          emailHash: emailHash(email),
        });

        return {
          success: true,
          message: GENERIC_RESET_MESSAGE,
        };
      } catch (error) {
        const code = String(error.code || "");

        if (code === "auth/user-not-found") {
          console.warn("Password reset requested for unknown account:", {
            emailHash: emailHash(email),
            code: code,
          });

          return {
            success: true,
            message: GENERIC_RESET_MESSAGE,
          };
        }

        console.error("Password reset error:", {
          code: error.code,
          statusCode: error.statusCode,
          emailHash: emailHash(email),
        });

        throw new HttpsError(
            "internal",
            "Failed to send reset email.",
        );
      }
    },
);
