const admin = require("firebase-admin");
const sgMail = require("@sendgrid/mail");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {defineSecret} = require("firebase-functions/params");

const SENDGRID_API_KEY = defineSecret("SENDGRID_API_KEY");

exports.sendPasswordResetEmail = onCall(
    {
      region: "us-central1",
      secrets: [SENDGRID_API_KEY],
    },
    async (request) => {
      const email = String(request.data.email || "").trim().toLowerCase();

      if (!email) {
        throw new HttpsError(
            "invalid-argument",
            "Email is required.",
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
          to: email,
        });

        return {
          success: true,
          message: "Password reset email sent.",
        };
      } catch (error) {
        console.error("Password reset error:", {
          email: email,
          code: error.code,
          message: error.message,
          stack: error.stack,
          response: error.response && error.response.body,
        });

        throw new HttpsError(
            "internal",
            error.message || "Failed to send reset email.",
        );
      }
    },
);
