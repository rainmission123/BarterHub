package com.example.barterhub.utils

import android.util.Log
import com.google.firebase.database.DatabaseReference

class ChatSystemMessageSender(
    private val chatsRef: DatabaseReference,
    private val inboxRef: DatabaseReference
) {

    fun sendTradeAccepted(
        chatId: String,
        currentUserId: String,
        partnerId: String,
        offeredBy: String,
        acceptedBy: String,
        offeredItemTitle: String,
        targetItemTitle: String,
        requestId: String,
        offeredItemPoints: Int = 0,
        targetItemPoints: Int = 0
    ) {
        if (chatId.isEmpty() || requestId.isEmpty() || currentUserId.isEmpty() || partnerId.isEmpty()) return

        val messageId = chatsRef.child(chatId).child("messages").push().key ?: return

        val difference = offeredItemPoints - targetItemPoints
        val differenceText = when {
            difference > 0 -> "Difference: ${difference}BP (in ${offeredBy}'s favor)"
            difference < 0 -> "Difference: ${-difference}BP (in ${acceptedBy}'s favor)"
            else -> "Equal trade! ✅"
        }

        val now = System.currentTimeMillis()

        // ✅ IMPORTANT: include tradeStatus + completedAt fields para walang flash
        val systemMessage = hashMapOf<String, Any?>(
            "messageId" to messageId,
            "receiverId" to partnerId,
            "tradeId" to requestId,
            "senderId" to "system",
            "senderName" to "System",
            "text" to "Trade Accepted!",
            "timestamp" to now,
            "read" to false,
            "messageType" to "system_trade",   // ✅ generic type
            "tradeStatus" to "accepted",       // ✅ accepted | completed
            "completedAt" to 0L,               // ✅ 0 means not completed
            "completedBy" to "",
            "tradeDetails" to mapOf(
                "offeredBy" to offeredBy,
                "acceptedBy" to acceptedBy,
                "offeredItemName" to offeredItemTitle,
                "targetItemName" to targetItemTitle,
                "offeredItemPoints" to offeredItemPoints,
                "targetItemPoints" to targetItemPoints,
                "pointsDifference" to difference,
                "differenceText" to differenceText
            )
        )

        chatsRef.child(chatId).child("messages").child(messageId).setValue(systemMessage)
            .addOnSuccessListener {
                val lastMessageText = "Trade accepted: $offeredItemTitle ↔ $targetItemTitle"
                updateChatAndInbox(
                    chatId = chatId,
                    currentUserId = currentUserId,
                    partnerId = partnerId,
                    lastMessage = lastMessageText,
                    time = now
                )
                Log.d("DEBUG_TRADE", "✅ Trade accepted system message sent")
            }
            .addOnFailureListener { e ->
                Log.e("DEBUG_TRADE", "❌ Failed sendTradeAccepted: ${e.message}")
            }
    }

    /**
     * ✅ Call this when user taps "Completed"
     * This updates the SAME system message → so next open, completed na agad (no flash).
     */
    fun markTradeCompleted(
        chatId: String,
        systemMessageId: String,
        currentUserId: String,
        partnerId: String,
        offeredItemTitle: String,
        targetItemTitle: String
    ) {
        if (chatId.isEmpty() || systemMessageId.isEmpty() || currentUserId.isEmpty() || partnerId.isEmpty()) return

        val now = System.currentTimeMillis()

        val updates = mapOf<String, Any>(
            "tradeStatus" to "completed",
            "completedAt" to now,
            "completedBy" to currentUserId
        )

        chatsRef.child(chatId).child("messages").child(systemMessageId).updateChildren(updates)
            .addOnSuccessListener {
                val lastMessageText = "✅ Transaction Completed: $offeredItemTitle ↔ $targetItemTitle"
                updateChatAndInbox(
                    chatId = chatId,
                    currentUserId = currentUserId,
                    partnerId = partnerId,
                    lastMessage = lastMessageText,
                    time = now
                )
                Log.d("DEBUG_TRADE", "✅ Trade marked completed")
            }
            .addOnFailureListener { e ->
                Log.e("DEBUG_TRADE", "❌ Failed markTradeCompleted: ${e.message}")
            }
    }

    private fun updateChatAndInbox(
        chatId: String,
        currentUserId: String,
        partnerId: String,
        lastMessage: String,
        time: Long
    ) {
        chatsRef.child(chatId).updateChildren(
            mapOf("lastMessage" to lastMessage, "lastMessageTime" to time)
        )

        val inboxUpdate = mapOf("lastMessage" to lastMessage, "lastMessageTime" to time)
        inboxRef.child(currentUserId).child(chatId).updateChildren(inboxUpdate)
        inboxRef.child(partnerId).child(chatId).updateChildren(inboxUpdate)
    }
}
