package com.example.barterhub.managers

import android.util.Log
import com.example.barterhub.data.models.TradeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class TradeCompletionManager {

    companion object {
        private const val TAG = "TradeCompletionManager"
    }

    private val db = FirebaseDatabase.getInstance().reference
    private val receiptManager = TradeReceiptManager()
    private val statsManager = TradeStatsManager()

    fun saveUserClickedCompleted(
        currentUserId: String,
        tradeId: String,
        messageId: String,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null
    ) {
        val actionData = hashMapOf<String, Any>(
            "clicked_completed" to true,
            "timestamp" to System.currentTimeMillis(),
            "messageId" to messageId
        )

        db.child("user_actions")
            .child(tradeId)
            .child(currentUserId)
            .setValue(actionData)
            .addOnSuccessListener {
                Log.d(TAG, "✅ User clicked completed saved")
                onSuccess?.invoke()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed clicked completed: ${e.message}")
                onFailure?.invoke(e.message ?: "Failed to save action")
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
                                val reviewerId = reviewSnap.child("reviewerId").getValue(String::class.java)
                                val reviewedUserId = reviewSnap.child("reviewedUserId").getValue(String::class.java)

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
                            Log.e(TAG, "❌ Reviews check cancelled: ${error.message}")
                            callback(false, false, false)
                        }
                    })
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ User action check failed: ${e.message}")
                callback(false, false, false)
            }
    }

    fun updateTradeStatusToCompleted(
        currentUserId: String,
        chatId: String,
        tradeId: String,
        messageId: String,
        request: TradeRequest,
        onCompleted: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null
    ) {
        db.child("trade_requests")
            .child(tradeId)
            .child("status")
            .get()
            .addOnSuccessListener { snapshot ->
                val currentStatus = snapshot.getValue(String::class.java)

                if (currentStatus == "Completed") {
                    Log.d(TAG, "⚠️ Trade already completed")

                    receiptManager.ensureReceiptExists(
                        currentUserId = currentUserId,
                        chatId = chatId,
                        request = request
                    )

                    onCompleted?.invoke()
                    return@addOnSuccessListener
                }

                proceedWithTradeCompletion(
                    currentUserId = currentUserId,
                    chatId = chatId,
                    tradeId = tradeId,
                    messageId = messageId,
                    request = request,
                    onCompleted = onCompleted,
                    onFailure = onFailure
                )
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed checking trade status: ${e.message}")
                onFailure?.invoke(e.message ?: "Failed checking trade status")
            }
    }

    private fun proceedWithTradeCompletion(
        currentUserId: String,
        chatId: String,
        tradeId: String,
        messageId: String,
        request: TradeRequest,
        onCompleted: (() -> Unit)?,
        onFailure: ((String) -> Unit)?
    ) {
        db.child("trade_requests")
            .child(tradeId)
            .child("status")
            .setValue("Completed")
            .addOnSuccessListener {
                Log.d(TAG, "✅ Trade status updated to Completed")

                statsManager.saveTradeHistory(request)
                statsManager.updateUserTradeStats(request)

                receiptManager.createTradeReceiptIfMissing(
                    currentUserId = currentUserId,
                    chatId = chatId,
                    request = request
                )

                updateSystemMessageToCompleted(
                    chatId = chatId,
                    messageId = messageId,
                    tradeId = tradeId,
                    request = request,
                    onCompleted = onCompleted,
                    onFailure = onFailure
                )
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to update trade status: ${e.message}")
                onFailure?.invoke(e.message ?: "Failed to complete trade")
            }
    }

    private fun updateSystemMessageToCompleted(
        chatId: String,
        messageId: String,
        tradeId: String,
        request: TradeRequest,
        onCompleted: (() -> Unit)?,
        onFailure: ((String) -> Unit)?
    ) {
        val updatedTradeDetails = hashMapOf<String, Any>(
            "tradeRequestId" to tradeId,
            "fromUserId" to request.fromUser.userId,
            "toUserId" to request.toUser.userId,
            "offeredBy" to request.fromUser.username,
            "acceptedBy" to request.toUser.username,
            "offeredItemId" to request.offeredItem.itemId,
            "offeredItemName" to request.offeredItem.title,
            "offeredItemDescription" to request.offeredItem.description,
            "offeredItemImage" to request.offeredItem.image,
            "offeredItemCategory" to request.offeredItem.category,
            "offeredItemCondition" to request.offeredItem.condition,
            "targetItemId" to request.targetItem.itemId,
            "targetItemName" to request.targetItem.title,
            "targetItemDescription" to request.targetItem.description,
            "targetItemImage" to request.targetItem.image,
            "targetItemCategory" to request.targetItem.category,
            "targetItemCondition" to request.targetItem.condition,
            "status" to "Completed"
        )
        Log.d(TAG, "✅ Trade completed. Chat message will be created by Cloud Function.")
        onCompleted?.invoke()
    }
}