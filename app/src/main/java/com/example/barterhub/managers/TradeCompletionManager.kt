package com.example.barterhub.managers

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctions

class TradeCompletionManager {

    companion object {
        private const val TAG = "TradeCompletionManager"
    }

    private val db = FirebaseDatabase.getInstance().reference
    private val functions = FirebaseFunctions.getInstance("asia-southeast1")

    data class CompletionResult(
        val completed: Boolean,
        val waiting: Boolean,
        val receiptId: String?
    )

    fun confirmTradeCompletion(
        tradeId: String,
        chatId: String,
        messageId: String,
        onSuccess: ((CompletionResult) -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null
    ) {
        val data = hashMapOf(
            "tradeId" to tradeId,
            "chatId" to chatId,
            "messageId" to messageId
        )

        functions
            .getHttpsCallable("confirmTradeCompletion")
            .call(data)
            .addOnSuccessListener { result ->
                val response = result.data as? Map<*, *>
                val completionResult = CompletionResult(
                    completed = response?.get("completed") as? Boolean ?: false,
                    waiting = response?.get("waiting") as? Boolean ?: false,
                    receiptId = response?.get("receiptId") as? String
                )

                Log.d(
                    TAG,
                    "Completion confirmed by Cloud: completed=${completionResult.completed}, waiting=${completionResult.waiting}"
                )
                onSuccess?.invoke(completionResult)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Cloud completion failed: ${e.message}", e)
                onFailure?.invoke(e.message ?: "Failed to confirm completion")
            }
    }

    fun checkUserActionStatus(
        currentUserId: String,
        tradeId: String,
        callback: (Boolean, Boolean, Boolean) -> Unit
    ) {
        db.child("user_actions")
            .child(tradeId)
            .child(currentUserId)
            .child("clicked_completed")
            .get()
            .addOnSuccessListener { clickedSnapshot ->
                val userClickedCompleted =
                    clickedSnapshot.getValue(Boolean::class.java) ?: false

                db.child("reviews")
                    .orderByChild("tradeId")
                    .equalTo(tradeId)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            var currentUserRated = false
                            var partnerRated = false

                            for (reviewSnap in snapshot.children) {
                                val reviewerId =
                                    reviewSnap.child("reviewerId").getValue(String::class.java)
                                val reviewedUserId =
                                    reviewSnap.child("reviewedUserId").getValue(String::class.java)

                                if (reviewerId == currentUserId) {
                                    currentUserRated = true
                                }

                                if (
                                    reviewerId != null &&
                                    reviewerId != currentUserId &&
                                    reviewedUserId == currentUserId
                                ) {
                                    partnerRated = true
                                }
                            }

                            callback(userClickedCompleted, currentUserRated, partnerRated)
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e(TAG, "Reviews check cancelled: ${error.message}")
                            callback(false, false, false)
                        }
                    })
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "User action check failed: ${e.message}")
                callback(false, false, false)
            }
    }
}
