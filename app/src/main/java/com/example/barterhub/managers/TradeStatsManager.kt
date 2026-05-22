package com.example.barterhub.managers

import android.util.Log
import com.example.barterhub.data.models.TradeHistoryItem
import com.example.barterhub.data.models.TradeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class TradeStatsManager {

    companion object {
        private const val TAG = "TradeStatsManager"
    }

    private val db = FirebaseDatabase.getInstance().reference
    private val referralRewardManager = ReferralRewardManager()

    fun saveTradeHistory(request: TradeRequest) {
        val tradeId = request.requestId
        val date = System.currentTimeMillis().toString()

        val fromUserHistory = TradeHistoryItem(
            itemName = request.targetItem.title,
            tradedWith = request.toUser.username,
            date = date,
            status = "Completed"
        )

        val toUserHistory = TradeHistoryItem(
            itemName = request.offeredItem.title,
            tradedWith = request.fromUser.username,
            date = date,
            status = "Completed"
        )

        val updates = hashMapOf<String, Any>(
            "trades/${request.fromUser.userId}/$tradeId" to fromUserHistory,
            "trades/${request.toUser.userId}/$tradeId" to toUserHistory
        )

        db.updateChildren(updates)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Trade history saved for both users")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save trade history: ${e.message}")
            }
    }

    fun updateUserTradeStats(request: TradeRequest) {
        updateSingleUserTradeStats(request.fromUser.userId, request.requestId)
        updateSingleUserTradeStats(request.toUser.userId, request.requestId)
    }

    private fun updateSingleUserTradeStats(userId: String, tradeId: String) {
        val userRef = db.child("users").child(userId)

        userRef.child("tradesCompleted")
            .runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    val currentTrades = currentData.getValue(Int::class.java) ?: 0
                    currentData.value = currentTrades + 1
                    return com.google.firebase.database.Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    snapshot: DataSnapshot?
                ) {
                    if (error != null) {
                        Log.e(TAG, "❌ Failed to increment tradesCompleted: ${error.message}")
                        return
                    }

                    recalculateSuccessRate(userId)
                    saveTradeToUserHistory(userId, tradeId)
                    referralRewardManager.grantReferralRewardIfEligible(userId)
                }
            })
    }

    private fun recalculateSuccessRate(userId: String) {
        val userRef = db.child("users").child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tradesCompleted = snapshot.child("tradesCompleted").getValue(Int::class.java) ?: 0
                val totalTrades = snapshot.child("totalTrades").getValue(Int::class.java) ?: tradesCompleted
                val failedTrades = snapshot.child("failedTrades").getValue(Int::class.java) ?: 0

                val denominator = if (totalTrades > 0) {
                    totalTrades
                } else {
                    tradesCompleted + failedTrades
                }

                val successRate = if (denominator > 0) {
                    ((tradesCompleted.toDouble() / denominator.toDouble()) * 100).toInt()
                } else {
                    100
                }

                userRef.child("successRate").setValue(successRate)
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ Success rate updated for $userId: $successRate%")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Failed success rate update: ${e.message}")
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "❌ Failed to recalculate success rate: ${error.message}")
            }
        })
    }

    private fun saveTradeToUserHistory(userId: String, tradeId: String) {
        db.child("users")
            .child(userId)
            .child("tradeHistory")
            .child(tradeId)
            .setValue(true)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Trade $tradeId saved to user $userId history")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save user trade history: ${e.message}")
            }
    }
}