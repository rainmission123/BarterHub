package com.example.barterhub.managers

import android.util.Log
import com.example.barterhub.data.models.TradeRequest
import com.example.barterhub.data.models.TradeUser
import com.google.firebase.database.FirebaseDatabase

class TradeNotificationManager {

    companion object {
        private const val TAG = "TradeNotificationManager"
    }

    private val db = FirebaseDatabase.getInstance().reference

    fun notifyCompletedClicked(
        currentUserId: String,
        chatId: String,
        request: TradeRequest
    ) {
        val partnerId = getPartnerId(currentUserId, request)
        val currentUserName = getCurrentUserName(currentUserId, request)

        sendPushNotificationRecord(
            targetUserId = partnerId,
            fromUserId = currentUserId,
            type = "trade_completed_clicked",
            chatId = chatId,
            request = request,
            message = "$currentUserName marked the barter as completed."
        )
    }

    fun notifyRated(
        currentUserId: String,
        chatId: String,
        request: TradeRequest,
        rating: Int
    ) {
        val partnerId = getPartnerId(currentUserId, request)
        val currentUserName = getCurrentUserName(currentUserId, request)

        sendPushNotificationRecord(
            targetUserId = partnerId,
            fromUserId = currentUserId,
            type = "trade_rated",
            chatId = chatId,
            request = request,
            message = "$currentUserName submitted a rating for your barter.",
            rating = rating
        )
    }

    fun notifyReceiptReadyForBothUsers(
        currentUserId: String,
        chatId: String,
        request: TradeRequest,
        receiptId: String
    ) {
        notifyReceiptReady(
            targetUserId = request.fromUser.userId,
            fromUserId = currentUserId,
            partner = request.toUser,
            chatId = chatId,
            request = request,
            receiptId = receiptId
        )

        notifyReceiptReady(
            targetUserId = request.toUser.userId,
            fromUserId = currentUserId,
            partner = request.fromUser,
            chatId = chatId,
            request = request,
            receiptId = receiptId
        )
    }

    fun checkAndSendMissingReceiptNotifications(
        currentUserId: String,
        chatId: String,
        request: TradeRequest,
        receiptId: String
    ) {
        val notifId = "receipt_$receiptId"

        db.child("notifications")
            .child(request.fromUser.userId)
            .child(notifId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    notifyReceiptReady(
                        targetUserId = request.fromUser.userId,
                        fromUserId = currentUserId,
                        partner = request.toUser,
                        chatId = chatId,
                        request = request,
                        receiptId = receiptId
                    )
                }
            }

        db.child("notifications")
            .child(request.toUser.userId)
            .child(notifId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    notifyReceiptReady(
                        targetUserId = request.toUser.userId,
                        fromUserId = currentUserId,
                        partner = request.fromUser,
                        chatId = chatId,
                        request = request,
                        receiptId = receiptId
                    )
                }
            }
    }

    private fun notifyReceiptReady(
        targetUserId: String,
        fromUserId: String,
        partner: TradeUser,
        chatId: String,
        request: TradeRequest,
        receiptId: String
    ) {
        val notifId = "receipt_$receiptId"

        val notification = hashMapOf<String, Any>(
            "id" to notifId,
            "type" to "receipt_created",
            "fromUserId" to fromUserId,
            "receiptId" to receiptId,
            "requestId" to request.requestId,
            "chatId" to chatId,
            "partnerId" to partner.userId,
            "partnerName" to partner.username,
            "message" to "✅ Transaction completed! Receipt is ready.",
            "timestamp" to System.currentTimeMillis(),
            "read" to false
        )

        db.child("notifications")
            .child(targetUserId)
            .child(notifId)
            .setValue(notification)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Receipt notification sent to $targetUserId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed receipt notification: ${e.message}")
            }
    }

    private fun sendPushNotificationRecord(
        targetUserId: String,
        fromUserId: String,
        type: String,
        chatId: String,
        request: TradeRequest,
        message: String,
        rating: Int? = null
    ) {
        val cloudType = when (type) {
            "trade_completed_clicked" -> "trade_completed"
            "trade_rated" -> "rating_submitted"
            else -> type
        }

        val fromUserName = getCurrentUserName(fromUserId, request)

        val event = hashMapOf<String, Any>(
            "type" to cloudType,
            "toUserId" to targetUserId,
            "fromUserId" to fromUserId,
            "fromUserName" to fromUserName,
            "chatId" to chatId,
            "partnerId" to fromUserId,
            "partnerName" to fromUserName,
            "requestId" to request.requestId,
            "tradeId" to request.requestId,
            "message" to message,
            "timestamp" to System.currentTimeMillis()
        )

        rating?.let {
            event["rating"] = it
        }

        db.child("trade_events")
            .push()
            .setValue(event)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Trade event sent to cloud: $cloudType to $targetUserId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed trade event: ${e.message}")
            }
    }

    private fun getPartnerId(currentUserId: String, request: TradeRequest): String {
        return if (currentUserId == request.fromUser.userId) {
            request.toUser.userId
        } else {
            request.fromUser.userId
        }
    }

    private fun getCurrentUserName(currentUserId: String, request: TradeRequest): String {
        return if (currentUserId == request.fromUser.userId) {
            request.fromUser.username
        } else {
            request.toUser.username
        }
    }
}