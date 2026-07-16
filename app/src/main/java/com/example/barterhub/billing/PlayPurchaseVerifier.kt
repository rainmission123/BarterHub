package com.example.barterhub.billing

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase

class PlayPurchaseVerifier(
    private val functions: FirebaseFunctions = Firebase.functions("us-central1")
) {
    sealed class Result {
        data class Success(
            val status: String,
            val transactionId: String?,
            val coins: Int?,
            val finalCoins: Int?,
            val needsReconciliation: Boolean
        ) : Result()

        data object Pending : Result()
        data object Processing : Result()
        data class SafeError(val status: String, val retryable: Boolean) : Result()
    }

    fun verify(
        productId: String,
        purchaseToken: String,
        callback: (Result) -> Unit
    ) {
        val request = hashMapOf(
            "productId" to productId,
            "purchaseToken" to purchaseToken
        )

        functions
            .getHttpsCallable("verifyGooglePlayCoinPurchase")
            .call(request)
            .addOnSuccessListener { task ->
                val data = task.data as? Map<*, *> ?: emptyMap<Any, Any>()
                val status = data["status"]?.toString().orEmpty()

                callback(
                    when (status) {
                        "completed", "credited" -> data.toSuccess(
                            status = status,
                            needsReconciliation = false
                        )

                        "already_processed" -> data.toSuccess(
                            status = status,
                            needsReconciliation = false
                        )

                        "credited_consume_failed_retryable" -> data.toSuccess(
                            status = status,
                            needsReconciliation = true
                        )

                        "processing" -> Result.Processing
                        "pending" -> Result.Pending
                        "permission-denied", "invalid" -> Result.SafeError(
                            status = status,
                            retryable = false
                        )

                        else -> Result.SafeError(
                            status = status.ifBlank { "unknown" },
                            retryable = false
                        )
                    }
                )
            }
            .addOnFailureListener { error ->
                val functionsError = error as? FirebaseFunctionsException
                val code = functionsError?.code
                val retryable = code == FirebaseFunctionsException.Code.UNAVAILABLE ||
                    code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ||
                    code == FirebaseFunctionsException.Code.INTERNAL

                callback(
                    Result.SafeError(
                        status = code?.name?.lowercase() ?: "verification_failed",
                        retryable = retryable
                    )
                )
            }
    }

    private fun Map<*, *>.toSuccess(
        status: String,
        needsReconciliation: Boolean
    ): Result.Success {
        return Result.Success(
            status = status,
            transactionId = this["transactionId"]?.toString(),
            coins = (this["coins"] as? Number)?.toInt(),
            finalCoins = (this["finalCoins"] as? Number)?.toInt(),
            needsReconciliation = needsReconciliation
        )
    }
}
