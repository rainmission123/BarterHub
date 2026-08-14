package com.example.barterhub.data.repository

import android.util.Log
import com.example.barterhub.data.models.Message
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.tasks.await

object ChatInboxUpdater {
    private const val TAG = "ChatInboxUpdater"

    suspend fun updateForMessage(
        database: FirebaseDatabase,
        chatId: String,
        message: Message,
        updateChatLastMessage: Boolean = false
    ) {
        val messagesRef = database.getReference("chats")
        val inboxRef = database.getReference("user_inbox")
        val preview = getMessagePreview(message)
        val timestamp = message.timestamp.takeIf { it > 0L } ?: System.currentTimeMillis()

        if (updateChatLastMessage) {
            messagesRef.child(chatId).child("lastMessage").setValue(preview).await()
            messagesRef.child(chatId).child("lastMessageTime").setValue(timestamp).await()
        }

        writeInboxEntry(
            inboxRef = inboxRef,
            userId = message.senderId,
            chatId = chatId,
            partnerId = message.receiverId,
            partnerName = "",
            lastMessage = preview,
            lastMessageTime = timestamp,
            incrementUnread = false
        )

        Log.d(TAG, "Client updated sender inbox only; receiver inbox sync is backend-owned")
    }

    private suspend fun writeInboxEntry(
        inboxRef: DatabaseReference,
        userId: String,
        chatId: String,
        partnerId: String,
        partnerName: String,
        lastMessage: String,
        lastMessageTime: Long,
        incrementUnread: Boolean
    ) {
        val updates = mutableMapOf<String, Any>(
            "chatId" to chatId,
            "partnerId" to partnerId,
            "partnerName" to partnerName,
            "lastMessage" to lastMessage,
            "lastMessageTime" to lastMessageTime,
            "unreadCount" to if (incrementUnread) ServerValue.increment(1) else 0,
            "deleted" to false
        )

        inboxRef.child(userId).child(chatId).updateChildren(updates).await()
    }

    private fun getMessagePreview(message: Message): String {
        return when (message.messageType) {
            "image" -> "📷 Image"
            "video" -> "🎬 Video"
            "system_trade_accepted" -> "Trade accepted"
            "system_trade_completed" -> "Trade completed"
            "system_trade_rated", "rating_submitted" -> "Rating submitted"
            else -> message.text.ifBlank { "New message" }
        }
    }
}
