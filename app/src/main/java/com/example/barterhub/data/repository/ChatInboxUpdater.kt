package com.example.barterhub.data.repository

import android.util.Log
import com.example.barterhub.data.models.Message
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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

        writeInboxEntry(
            inboxRef = inboxRef,
            userId = message.receiverId,
            chatId = chatId,
            partnerId = message.senderId,
            partnerName = message.senderName,
            lastMessage = preview,
            lastMessageTime = timestamp,
            incrementUnread = message.senderId != message.receiverId
        )

        if (message.senderId != message.receiverId) {
            incrementUnreadCounter(
                messagesRef.child(chatId).child("unreadCount").child(message.receiverId)
            )
        }
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
        val inboxEntryRef = inboxRef.child(userId).child(chatId)

        suspendCoroutine<Unit> { continuation ->
            inboxEntryRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val currentUnread = currentData.child("unreadCount").asInt() ?: 0
                    val currentDeletedAt = currentData.child("deletedAt").asLong()
                    val currentPartnerName =
                        currentData.child("partnerName").getValue(String::class.java).orEmpty()

                    val nextValue = mutableMapOf<String, Any>(
                        "chatId" to chatId,
                        "partnerId" to partnerId,
                        "partnerName" to currentPartnerName.ifBlank { partnerName },
                        "lastMessage" to lastMessage,
                        "lastMessageTime" to lastMessageTime,
                        "unreadCount" to if (incrementUnread) currentUnread + 1 else currentUnread,
                        "deleted" to false
                    )

                    if (currentDeletedAt != null) {
                        nextValue["deletedAt"] = currentDeletedAt
                    }

                    currentData.value = nextValue

                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    when {
                        error != null -> continuation.resumeWithException(error.toException())
                        !committed -> continuation.resumeWithException(
                            IllegalStateException("Inbox transaction was not committed")
                        )
                        else -> continuation.resume(Unit)
                    }
                }
            })
        }
    }

    private fun MutableData.asInt(): Int? {
        return getValue(Int::class.java) ?: getValue(Long::class.java)?.toInt()
    }

    private fun MutableData.asLong(): Long? {
        return getValue(Long::class.java)
            ?: getValue(Int::class.java)?.toLong()
            ?: getValue(Double::class.java)?.toLong()
            ?: getValue(String::class.java)?.toLongOrNull()
    }

    private fun incrementUnreadCounter(counterRef: DatabaseReference) {
        counterRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentCount = currentData.asInt() ?: 0
                currentData.value = currentCount + 1
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null) {
                    Log.e(TAG, "Failed to increment unread count: ${error.message}")
                }
            }
        })
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
