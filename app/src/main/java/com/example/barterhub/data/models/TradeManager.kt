package com.example.barterhub.data.models

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

object TradeManager {

    private val db = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/").reference

    // 📌 Step 1: Send trade request
    fun sendTradeRequest(context: Context, itemId: String, ownerId: String) {
        val requesterId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val requestId = db.child("trade_requests").push().key ?: return

        val request = mapOf(
            "itemId" to itemId,
            "owner" to ownerId,
            "requester" to requesterId,
            "date" to System.currentTimeMillis(),
            "status" to "Pending"
        )

        db.child("trade_requests").child(requestId).setValue(request)
            .addOnSuccessListener {
                Toast.makeText(context, "Trade request sent!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("ItemDetail", "❌ Failed to send request: ${e.message}", e)
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // 📌 Step 2: Accept request (by Owner)
    fun acceptTradeRequest(requestId: String) {
        db.child("trade_requests").child(requestId).child("status").setValue("Accepted")
    }

    // 📌 Step 3: Complete trade (both users get history)
    fun completeTrade(itemName: String, ownerId: String, requesterId: String) {
        val tradeDataOwner = mapOf(
            "date" to System.currentTimeMillis(),
            "itemName" to itemName,
            "status" to "Completed",
            "tradedWith" to requesterId
        )

        val tradeDataRequester = mapOf(
            "date" to System.currentTimeMillis(),
            "itemName" to itemName,
            "status" to "Completed",
            "tradedWith" to ownerId
        )

        val tradesRef = db.child("trades")
        val newTradeIdOwner = tradesRef.child(ownerId).push().key ?: return
        val newTradeIdRequester = tradesRef.child(requesterId).push().key ?: return

        val updates = hashMapOf<String, Any>(
            "/$ownerId/$newTradeIdOwner" to tradeDataOwner,
            "/$requesterId/$newTradeIdRequester" to tradeDataRequester
        )

        tradesRef.updateChildren(updates)
    }
}