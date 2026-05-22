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
        request: TradeRequest
    ) {
        val partnerId = getPartnerId(currentUserId, request)
        val currentUserName = getCurrentUserName(currentUserId, request)

        sendPushNotificationRecord(
            targetUserId = partnerId,
            fromUserId = currentUserId,
            type = "trade_rated",
            chatId = chatId,
            request = request,
            message = "$currentUserName submitted a rating for your barter."
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
        message: String
    ) {
        val notifRef = db.child("notifications").child(targetUserId).push()
        val notifId = notifRef.key ?: return

        val notification = hashMapOf<String, Any>(
            "id" to notifId,
            "type" to type,
            "fromUserId" to fromUserId,
            "requestId" to request.requestId,
            "chatId" to chatId,
            "message" to message,
            "timestamp" to System.currentTimeMillis(),
            "read" to false
        )

        notifRef.setValue(notification)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Notification sent: $type to $targetUserId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed notification: ${e.message}")
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