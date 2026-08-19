/* eslint-disable require-jsdoc, max-len, indent */

const {onCall, HttpsError} = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

const SEND_COINS_DB_URL =
  "https://barterhub-3c947-default-rtdb.firebaseio.com";

const sendCoinsApp =
  admin.apps.find((app) => app.name === "sendCoinsDb") ||
  admin.initializeApp(
      {databaseURL: SEND_COINS_DB_URL},
      "sendCoinsDb",
  );

function toCoinsNumber(value) {
  const coins = Number(value || 0);
  return Number.isFinite(coins) ? coins : 0;
}

function cleanUsername(value) {
  return String(value || "").trim().toLowerCase();
}

function cleanRequestId(value) {
  return String(value || "").trim();
}

function isValidRequestId(value) {
  return /^[A-Za-z0-9_-]{16,128}$/.test(value);
}

function buildCompletedResponse(savedRequest) {
  const result =
    savedRequest &&
    typeof savedRequest.result === "object" &&
    savedRequest.result !== null ?
      savedRequest.result :
      {};

  return {
    success: true,

    message:
      String(
          result.message ||
          "Coins already sent successfully",
      ),

    newBalance:
      toCoinsNumber(
          result.newBalance !== undefined ?
            result.newBalance :
            savedRequest.senderFinalCoins,
      ),

    transferId:
      String(
          result.transferId ||
          savedRequest.transferId ||
          savedRequest.requestId ||
          "",
      ),

    duplicate: true,
  };
}

async function safelyRemoveRequest(requestRef) {
  try {
    await requestRef.remove();
  } catch (error) {
    console.error(
        "sendCoins request cleanup failed",
        {
          message:
            error && error.message,
        },
    );
  }
}

async function safelyUpdateRequest(
    requestRef,
    updates,
) {
  try {
    await requestRef.update(updates);
  } catch (error) {
    console.error(
        "sendCoins request status update failed",
        {
          message:
            error && error.message,
        },
    );
  }
}

exports.sendCoins = onCall(
    {
      region: "us-central1",
    },

    async (request) => {
      // =========================================================
      // AUTHENTICATION
      // =========================================================

      if (
        !request.auth ||
        !request.auth.uid
      ) {
        throw new HttpsError(
            "unauthenticated",
            "Authentication required.",
        );
      }

      const senderUid =
        request.auth.uid;

      const username =
        cleanUsername(
            request.data &&
            request.data.username,
        );

      const amount =
        Number(
            request.data &&
            request.data.amount,
        );

      const requestId =
        cleanRequestId(
            request.data &&
            request.data.requestId,
        );

      // =========================================================
      // VALIDATION
      // =========================================================

      if (!username) {
        throw new HttpsError(
            "invalid-argument",
            "Recipient username is required.",
        );
      }

      if (
        !Number.isInteger(amount) ||
        amount <= 0
      ) {
        throw new HttpsError(
            "invalid-argument",
            "Enter a valid coin amount.",
        );
      }

      if (!requestId) {
        throw new HttpsError(
            "invalid-argument",
            "Missing transfer request ID.",
        );
      }

      if (!isValidRequestId(requestId)) {
        throw new HttpsError(
            "invalid-argument",
            "Invalid transfer request ID.",
        );
      }

      const db =
        admin.database(sendCoinsApp);

      // =========================================================
      // FIND RECIPIENT
      // =========================================================

      const recipientUidSnap =
        await db
            .ref("usernames")
            .child(username)
            .get();

      const recipientUid =
        recipientUidSnap.val();

      if (!recipientUid) {
        throw new HttpsError(
            "not-found",
            "Recipient username was not found.",
        );
      }

      if (recipientUid === senderUid) {
        throw new HttpsError(
            "invalid-argument",
            "You cannot send coins to yourself.",
        );
      }

      // =========================================================
      // IDEMPOTENCY CLAIM
      // =========================================================

      const requestRef =
        db.ref(
            `send_coin_requests/${senderUid}/${requestId}`,
        );

      /*
       * First invocation:
       * creates the processing request.
       *
       * Duplicate invocation:
       * transaction will not overwrite the existing request.
       */
      const claimResult =
        await requestRef.transaction(
            (current) => {
              if (current !== null) {
                return;
              }

              return {
                requestId,
                senderUid,
                recipientUid,
                username,
                amount,
                status: "processing",
                createdAt: Date.now(),
                updatedAt: Date.now(),
              };
            },
            undefined,
            false,
        );

      // =========================================================
      // DUPLICATE / RETRY HANDLING
      // =========================================================

      if (!claimResult.committed) {
        const existingSnap =
          await requestRef.get();

        const existing =
          existingSnap.val() || {};

        /*
         * A request ID cannot be reused for a different
         * recipient or amount.
         */
        const sameRequest =
          existing.senderUid === senderUid &&
          existing.recipientUid === recipientUid &&
          cleanUsername(existing.username) === username &&
          Number(existing.amount) === amount;

        if (!sameRequest) {
          throw new HttpsError(
              "already-exists",
              "This transfer request ID is already used by another transfer.",
          );
        }

        /*
         * Successful retry:
         *
         * Same request was already completed.
         * Return previous result without touching balances.
         */
        if (existing.status === "completed") {
          console.log(
              "sendCoins duplicate completed request",
              {
                senderUid,
                recipientUid,
                requestId,
              },
          );

          return buildCompletedResponse(
              existing,
          );
        }

        /*
         * An uncertain transfer must never automatically
         * perform another debit.
         */
        if (existing.status === "needs_review") {
          throw new HttpsError(
              "aborted",
              "This transfer needs review and will not be sent again automatically.",
          );
        }

        if (existing.status === "refunded") {
          throw new HttpsError(
              "aborted",
              "This transfer was refunded. Please start a new transfer.",
          );
        }

        throw new HttpsError(
            "aborted",
            "This transfer is already being processed. Please wait before retrying.",
        );
      }

      // =========================================================
      // WALLET REFERENCES
      // =========================================================

      const senderCoinsRef =
        db.ref(
            `users/${senderUid}/wallet/coins`,
        );

      const recipientCoinsRef =
        db.ref(
            `users/${recipientUid}/wallet/coins`,
        );

      // =========================================================
      // LOAD CURRENT BALANCES
      // =========================================================

      let senderCoinsSnap;
      let recipientCoinsSnap;

      try {
        [
          senderCoinsSnap,
          recipientCoinsSnap,
        ] =
          await Promise.all([
            senderCoinsRef.once("value"),
            recipientCoinsRef.once("value"),
          ]);
      } catch (balanceLoadError) {
        /*
         * No wallet mutation happened yet,
         * therefore the claim can safely be released.
         */
        await safelyRemoveRequest(
            requestRef,
        );

        throw new HttpsError(
            "unavailable",
            "Could not load wallet balances. Please try again.",
        );
      }

      if (!senderCoinsSnap.exists()) {
        await safelyRemoveRequest(
            requestRef,
        );

        throw new HttpsError(
            "not-found",
            "Sender wallet was not found.",
        );
      }

      const directSenderCoins =
        toCoinsNumber(
            senderCoinsSnap.val(),
        );

      const directRecipientCoins =
        toCoinsNumber(
            recipientCoinsSnap.val(),
        );

      if (directSenderCoins < amount) {
        /*
         * No balance mutation occurred.
         * Remove claim so user can try again later.
         */
        await safelyRemoveRequest(
            requestRef,
        );

        throw new HttpsError(
            "failed-precondition",
            "Not enough coins.",
        );
      }

      // =========================================================
      // DEBIT SENDER
      // =========================================================

      let senderFinalCoins = 0;
      let senderHadInsufficientCoins = false;
      let senderResult;

      try {
        senderResult =
          await senderCoinsRef.transaction(
              (currentCoins) => {
                const coins =
                  currentCoins === null ||
                  currentCoins === undefined ?
                    directSenderCoins :
                    toCoinsNumber(
                        currentCoins,
                    );

                if (coins < amount) {
                  senderHadInsufficientCoins = true;
                  return;
                }

                senderFinalCoins =
                  coins - amount;

                return senderFinalCoins;
              },
              undefined,
              false,
          );
      } catch (senderError) {
        /*
         * Debit was not confirmed.
         * Safe to remove claim and allow retry.
         */
        await safelyRemoveRequest(
            requestRef,
        );

        throw new HttpsError(
            "aborted",
            "Wallet transaction could not be completed. Please try again.",
        );
      }

      if (!senderResult.committed) {
        await safelyRemoveRequest(
            requestRef,
        );

        if (senderHadInsufficientCoins) {
          throw new HttpsError(
              "failed-precondition",
              "Not enough coins.",
          );
        }

        throw new HttpsError(
            "aborted",
            "Wallet transaction could not be completed. Please try again.",
        );
      }

      senderFinalCoins =
        toCoinsNumber(
            senderResult.snapshot.val(),
        );

      /*
       * Sender debit succeeded.
       *
       * From here onward, NEVER remove the request blindly.
       * Duplicate retry must stay blocked.
       */
      await safelyUpdateRequest(
          requestRef,
          {
            status: "sender_debited",
            senderFinalCoins,
            updatedAt: Date.now(),
          },
      );

      // =========================================================
      // CREDIT RECIPIENT
      // =========================================================

      let recipientFinalCoins = 0;

      try {
        const recipientResult =
          await recipientCoinsRef.transaction(
              (currentCoins) => {
                const coins =
                  currentCoins === null ||
                  currentCoins === undefined ?
                    directRecipientCoins :
                    toCoinsNumber(
                        currentCoins,
                    );

                recipientFinalCoins =
                  coins + amount;

                return recipientFinalCoins;
              },
              undefined,
              false,
          );

        if (!recipientResult.committed) {
          throw new Error(
              "Recipient wallet transaction was not committed.",
          );
        }

        recipientFinalCoins =
          toCoinsNumber(
              recipientResult.snapshot.val(),
          );
      } catch (creditError) {
        console.error(
            "sendCoins recipient credit failed",
            {
              senderUid,
              recipientUid,
              amount,
              requestId,
              message:
                creditError &&
                creditError.message,
            },
        );

        // =======================================================
        // REFUND SENDER
        // =======================================================

        try {
          const senderRefundSnap =
            await senderCoinsRef.once(
                "value",
            );

          const directRefundBalance =
            toCoinsNumber(
                senderRefundSnap.val(),
            );

          const refundResult =
            await senderCoinsRef.transaction(
                (currentCoins) => {
                  const coins =
                    currentCoins === null ||
                    currentCoins === undefined ?
                      directRefundBalance :
                      toCoinsNumber(
                          currentCoins,
                      );

                  return coins + amount;
                },
                undefined,
                false,
            );

          if (!refundResult.committed) {
            throw new Error(
                "Sender refund transaction was not committed.",
            );
          }

          await safelyUpdateRequest(
              requestRef,
              {
                status: "refunded",
                failure:
                  "recipient_credit_failed",
                updatedAt: Date.now(),
              },
          );

          /*
           * Keep the request record.
           *
           * Same request ID must not accidentally become
           * another transfer.
           */
          throw new HttpsError(
              "aborted",
              "Could not credit recipient. Coins were returned. Please start a new transfer.",
          );
        } catch (refundError) {
          /*
           * The HttpsError above means refund succeeded.
           * Pass it through instead of treating it
           * as a refund failure.
           */
          if (refundError instanceof HttpsError) {
            throw refundError;
          }

          console.error(
              "CRITICAL: sendCoins sender refund failed",
              {
                senderUid,
                recipientUid,
                amount,
                requestId,
                message:
                  refundError &&
                  refundError.message,
              },
          );

          /*
           * Unknown balance state.
           *
           * Lock this request permanently until reviewed.
           * Do NOT allow another debit.
           */
          await safelyUpdateRequest(
              requestRef,
              {
                status: "needs_review",
                failure:
                  "recipient_credit_and_refund_failed",
                senderFinalCoins,
                updatedAt: Date.now(),
              },
          );

          throw new HttpsError(
              "internal",
              "Recipient credit failed and automatic refund needs review.",
          );
        }
      }

      /*
       * Both wallet balance mutations succeeded.
       */
      await safelyUpdateRequest(
          requestRef,
          {
            status: "recipient_credited",
            senderFinalCoins,
            recipientFinalCoins,
            updatedAt: Date.now(),
          },
      );

      // =========================================================
      // CREATE TRANSFER RECORDS
      // =========================================================

      /*
       * requestId is also the transferId.
       *
       * This makes the transfer and transaction IDs
       * deterministic for one logical Send Coins request.
       */
      const transferId =
        requestId;

      const senderTxnId =
        `send_${transferId}`;

      const recipientTxnId =
        `receive_${transferId}`;

      const now =
        admin.database.ServerValue.TIMESTAMP;

      // =========================================================
      // LOAD USER DETAILS
      // =========================================================

      let sender;
      let recipient;

      try {
        const [
          senderSnap,
          recipientSnap,
        ] =
          await Promise.all([
            db.ref(
                `users/${senderUid}`,
            ).get(),

            db.ref(
                `users/${recipientUid}`,
            ).get(),
          ]);

        sender =
          senderSnap.val() || {};

        recipient =
          recipientSnap.val() || {};
      } catch (profileLoadError) {
        /*
         * Money already moved.
         *
         * Do not retry the transfer automatically.
         */
        await safelyUpdateRequest(
            requestRef,
            {
              status: "needs_review",
              failure:
                "post_transfer_profile_load_failed",
              transferId,
              senderFinalCoins,
              recipientFinalCoins,
              updatedAt: Date.now(),
            },
        );

        throw new HttpsError(
            "internal",
            "Coins were transferred, but the transaction record needs review.",
        );
      }

      const senderName =
        sender.fullName ||
        sender.username ||
        "Someone";

      const recipientName =
        recipient.fullName ||
        recipient.username ||
        username;

      const successMessage =
        `Sent ${amount} coins to ${recipientName}`;

      // =========================================================
      // TRANSACTION RECORDS
      // =========================================================

      const senderTransaction = {
        transactionId: senderTxnId,
        transferId,
        requestId,
        title: "Sent Coins",
        type: "send",
        coins: -amount,
        amount: 0,
        status: "completed",
        fromUserId: senderUid,
        toUserId: recipientUid,
        fromName: senderName,
        toName: recipientName,
        finalCoins: senderFinalCoins,
        timestamp: now,
      };

      const recipientTransaction = {
        transactionId: recipientTxnId,
        transferId,
        requestId,
        title: "Received Coins",
        type: "receive",
        coins: amount,
        amount: 0,
        status: "completed",
        fromUserId: senderUid,
        toUserId: recipientUid,
        fromName: senderName,
        toName: recipientName,
        finalCoins: recipientFinalCoins,
        timestamp: now,
      };

      const updates = {};

      // =========================================================
      // TRANSFER AUDIT
      // =========================================================

      updates[
          `coin_transfers/${transferId}`
      ] = {
        transferId,
        requestId,
        fromUserId: senderUid,
        toUserId: recipientUid,
        fromName: senderName,
        toName: recipientName,
        coins: amount,
        status: "completed",
        timestamp: now,
      };

      // =========================================================
      // USER LEDGERS
      // =========================================================

      updates[
          `coin_transactions/${senderUid}/${senderTxnId}`
      ] = senderTransaction;

      updates[
          `coin_transactions/${recipientUid}/${recipientTxnId}`
      ] = recipientTransaction;

      // =========================================================
      // GLOBAL AUDIT
      // =========================================================

      updates[
          `transactions/${senderTxnId}`
      ] = {
        userId: senderUid,
        ...senderTransaction,
      };

      updates[
          `transactions/${recipientTxnId}`
      ] = {
        userId: recipientUid,
        ...recipientTransaction,
      };

      // =========================================================
      // RECIPIENT NOTIFICATION
      // =========================================================

      updates[
          `notifications/${recipientUid}/${recipientTxnId}`
      ] = {
        type: "coins",
        coins: amount,
        fromUserId: senderUid,
        senderName,
        message:
          `${senderName} sent you ${amount} coins!`,
        read: false,
        dismissed: false,
        timestamp: now,
      };

      // =========================================================
      // COMPLETE IDEMPOTENCY REQUEST
      // =========================================================

      /*
       * This is intentionally included in the same
       * multi-location update as ledgers and notification.
       */
      updates[
          `send_coin_requests/${senderUid}/${requestId}`
      ] = {
        requestId,
        senderUid,
        recipientUid,
        username,
        amount,
        transferId,
        status: "completed",
        senderFinalCoins,
        recipientFinalCoins,

        result: {
          success: true,
          message: successMessage,
          newBalance: senderFinalCoins,
          transferId,
        },

        completedAt: now,
        updatedAt: now,
      };

      // =========================================================
      // SAVE RECORDS
      // =========================================================

      try {
        await db.ref().update(
            updates,
        );
      } catch (recordError) {
        console.error(
            "sendCoins record update failed after balance transfer",
            {
              transferId,
              requestId,
              senderUid,
              recipientUid,
              amount,
              senderFinalCoins,
              recipientFinalCoins,
              message:
                recordError &&
                recordError.message,
            },
        );

        /*
         * Balances were already transferred.
         *
         * Keep request locked to prevent another debit.
         */
        await safelyUpdateRequest(
            requestRef,
            {
              status: "needs_review",
              failure:
                "post_transfer_recording_failed",
              transferId,
              senderFinalCoins,
              recipientFinalCoins,
              updatedAt: Date.now(),
            },
        );

        throw new HttpsError(
            "internal",
            "Coins were transferred, but the transaction record needs review.",
        );
      }

      console.log(
          "sendCoins completed successfully",
          {
            transferId,
            requestId,
            senderUid,
            recipientUid,
            amount,
          },
      );

      // =========================================================
      // RESPONSE
      // =========================================================

      return {
        success: true,
        message: successMessage,
        newBalance: senderFinalCoins,
        transferId,
        duplicate: false,
      };
    },
);
