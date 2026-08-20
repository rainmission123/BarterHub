package com.example.barterhub.billing

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException

class PlayPurchaseVerifier(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("us-central1")
) {

    data class VerificationResult(
        val status: String,
        val transactionId: String? = null,
        val coins: Int = 0,
        val finalCoins: Int = 0,
        val retryable: Boolean = false,
        val message: String? = null
    ) {
        val isSuccessful: Boolean
            get() = status == STATUS_COMPLETED ||
                    status == STATUS_CREDITED ||
                    status == STATUS_ALREADY_PROCESSED ||
                    status == STATUS_CREDITED_PENDING_CONSUME ||
                    status == STATUS_CONSUME_RETRYABLE
    }

    fun verify(
        productId: String,
        purchaseToken: String,
        onResult: (VerificationResult) -> Unit
    ) {

        Log.d(
            TAG,
            "Sending Google Play verification request " +
                    "productId=$productId " +
                    "purchaseTokenLength=${purchaseToken.length}"
        )

        val request = hashMapOf(
            "productId" to productId,
            "purchaseToken" to purchaseToken
        )

        functions
            .getHttpsCallable("verifyGooglePlayCoinPurchase")
            .call(request)
            .addOnSuccessListener { result ->

                Log.d(TAG, "Cloud Function SUCCESS")
                Log.d(TAG, "Raw result = ${result.data}")

                val data = result.data as? Map<*, *>
                val status = data?.get("status")?.toString().orEmpty()

                Log.d(
                    TAG,
                    "Parsed result -> " +
                            "status=$status " +
                            "transactionId=${data?.get("transactionId")} " +
                            "coins=${data?.get("coins")} " +
                            "finalCoins=${data?.get("finalCoins")}"
                )

                onResult(
                    VerificationResult(
                        status = status.ifBlank { STATUS_INVALID },
                        transactionId = data?.get("transactionId")?.toString(),
                        coins = data?.get("coins").asInt(),
                        finalCoins = data?.get("finalCoins").asInt(),
                        retryable = status == STATUS_PROCESSING ||
                                status == STATUS_CREDITED_PENDING_CONSUME ||
                                status == STATUS_CONSUME_RETRYABLE
                    )
                )
            }
            .addOnFailureListener { error ->

                val exception = error as? FirebaseFunctionsException

                val status = when (exception?.code) {
                    FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                        STATUS_PERMISSION_DENIED

                    FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                        STATUS_PERMISSION_DENIED

                    FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                        STATUS_INVALID

                    FirebaseFunctionsException.Code.UNAVAILABLE ->
                        STATUS_PROCESSING

                    else ->
                        STATUS_ERROR
                }

                Log.e(TAG, "Cloud Function FAILED")
                Log.e(TAG, "Functions Code = ${exception?.code}")
                Log.e(TAG, "Functions Details = ${exception?.details}")
                Log.e(TAG, "Functions Message = ${exception?.message}")
                Log.e(TAG, "Mapped Status = $status", error)

                onResult(
                    VerificationResult(
                        status = status,
                        retryable = status == STATUS_PROCESSING,
                        message = error.message
                    )
                )
            }
    }

    private fun Any?.asInt(): Int {
        return when (this) {
            is Number -> toInt()
            is String -> toIntOrNull() ?: 0
            else -> 0
        }
    }

    companion object {
        private const val TAG = "PlayPurchaseVerifier"

        const val STATUS_COMPLETED = "completed"
        const val STATUS_CREDITED = "credited"
        const val STATUS_ALREADY_PROCESSED = "already_processed"
        const val STATUS_CREDITED_PENDING_CONSUME =
            "credited_pending_consume"
        const val STATUS_CONSUME_RETRYABLE =
            "credited_consume_failed_retryable"
        const val STATUS_PROCESSING = "processing"
        const val STATUS_PENDING = "pending"
        const val STATUS_PERMISSION_DENIED = "permission-denied"
        const val STATUS_INVALID = "invalid"
        const val STATUS_ERROR = "error"
    }
}