package com.example.barterhub.managers

import android.util.Log
import com.example.barterhub.data.models.TradeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TradeReceiptManager {

    companion object {
        private const val TAG = "TradeReceiptManager"
    }

    private val db = FirebaseDatabase.getInstance().reference
    private val notificationManager = TradeNotificationManager()

    fun ensureReceiptExists(
        currentUserId: String,
        chatId: String,
        request: TradeRequest
    ) {
        db.child("receipts")
            .orderByChild("tradeRequestId")
            .equalTo(request.requestId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val receiptId = snapshot.children.firstOrNull()?.key.orEmpty()

                        if (receiptId.isNotEmpty()) {
                            notificationManager.checkAndSendMissingReceiptNotifications(
                                currentUserId = currentUserId,
                                chatId = chatId,
                                request = request,
                                receiptId = receiptId
                            )
                        }
                    } else {
                        createTradeReceiptIfMissing(
                            currentUserId = currentUserId,
                            chatId = chatId,
                            request = request
                        )
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "❌ ensureReceiptExists cancelled: ${error.message}")
                }
            })
    }

    fun createTradeReceiptIfMissing(
        currentUserId: String,
        chatId: String,
        request: TradeRequest
    ) {
        db.child("receipts_by_trade")
            .child(request.requestId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val existingReceiptId = snapshot.value.toString()

                    Log.d(TAG, "⚠️ Receipt already exists: $existingReceiptId")

                    notificationManager.checkAndSendMissingReceiptNotifications(
                        currentUserId = currentUserId,
                        chatId = chatId,
                        request = request,
                        receiptId = existingReceiptId
                    )

                    return@addOnSuccessListener
                }

                val receiptId = db.child("receipts").push().key ?: return@addOnSuccessListener
                createNewReceipt(
                    currentUserId = currentUserId,
                    chatId = chatId,
                    request = request,
                    receiptId = receiptId
                )
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed checking receipts_by_trade: ${e.message}")
            }
    }

    private fun createNewReceipt(
        currentUserId: String,
        chatId: String,
        request: TradeRequest,
        receiptId: String
    ) {
        val timestamp = System.currentTimeMillis()

        val receiptData = hashMapOf<String, Any>(
            "receiptId" to receiptId,
            "receiptNo" to generateReceiptNumber(),
            "chatDisplayId" to generateChatDisplayId(),
            "requestDisplayId" to generateRequestDisplayId(),
            "chatId" to chatId,
            "tradeRequestId" to request.requestId,
            "timestamp" to timestamp,
            "completedAt" to timestamp,
            "status" to "completed",

            "fromUserId" to request.fromUser.userId,
            "offeredBy" to request.fromUser.username,
            "fromUserProfileImage" to request.fromUser.profileImage,
            "fromUserLocation" to request.fromUser.location,

            "toUserId" to request.toUser.userId,
            "acceptedBy" to request.toUser.username,
            "toUserProfileImage" to request.toUser.profileImage,
            "toUserLocation" to request.toUser.location,

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
            "targetItemCondition" to request.targetItem.condition
        )

        val updates = hashMapOf<String, Any>(
            "receipts/$receiptId" to receiptData,
            "receipts_by_trade/${request.requestId}" to receiptId
        )

        db.updateChildren(updates)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Receipt created: $receiptId")

                notificationManager.notifyReceiptReadyForBothUsers(
                    currentUserId = currentUserId,
                    chatId = chatId,
                    request = request,
                    receiptId = receiptId
                )
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to create receipt: ${e.message}")
            }
    }

    private fun generateReceiptNumber(): String {
        val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
        val random = (100000..999999).random()
        return "RCPT-$year-$random"
    }

    private fun generateChatDisplayId(): String {
        val random = (10000..99999).random()
        return "CHT-$random"
    }

    private fun generateRequestDisplayId(): String {
        val random = (10000..99999).random()
        return "REQ-$random"
    }
}