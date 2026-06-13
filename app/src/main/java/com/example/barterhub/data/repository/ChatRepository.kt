package com.example.barterhub.data.repository

import android.util.Log
import com.example.barterhub.data.models.Message
import com.google.firebase.database.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val database: FirebaseDatabase
) : IChatRepository {

    private val messagesRef = database.getReference("chats")
    private val inboxRef = database.getReference("user_inbox")
    private val statusRef = database.getReference("status")

    // 👇 IMPLEMENTATION: Hide message for specific user
    override suspend fun hideMessageForUser(chatId: String, messageId: String, userId: String) {
        try {
            val hiddenRef = messagesRef
                .child(chatId)
                .child("messages")
                .child(messageId)
                .child("hiddenForUsers")
                .child(userId)

            hiddenRef.setValue(true).await()

            android.util.Log.d("ChatRepository", "Message $messageId hidden for user $userId")
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Error hiding message: ${e.message}")
        }
    }

    override fun observeMessages(
        chatId: String,
        onMessageAdded: (Message) -> Unit,
        onMessageChanged: (Message) -> Unit,
        onMessageRemoved: (String) -> Unit
    ): ChildEventListener {
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(Message::class.java)
                message?.let {
                    it.messageId = snapshot.key ?: ""
                    onMessageAdded(it)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(Message::class.java)
                message?.let {
                    it.messageId = snapshot.key ?: ""
                    onMessageChanged(it)
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                onMessageRemoved(snapshot.key ?: "")
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        messagesRef.child(chatId).child("messages")
            .orderByChild("timestamp")
            .addChildEventListener(listener)

        return listener
    }

    override suspend fun sendMessage(chatId: String, message: Message): String {
        val messageId = messagesRef.child(chatId).child("messages").push().key ?: return ""
        message.messageId = messageId

        messagesRef.child(chatId)
            .child("messages")
            .child(messageId)
            .setValue(message)
            .await()

        updateInbox(chatId, message)
        return messageId
    }

    override suspend fun createChat(
        userId1: String,
        userId2: String,
        itemId: String,
        itemTitle: String,
        firstMessage: Message
    ): String {
        val chatId = if (userId1 < userId2)
            "chat_${userId1}_$userId2"
        else
            "chat_${userId2}_$userId1"

        val messageId = firstMessage.messageId ?: ""

        val chatMap = mapOf(
            "participants" to mapOf(userId1 to true, userId2 to true),
            "itemId" to itemId,
            "itemTitle" to itemTitle,
            "lastMessage" to (firstMessage.text ?: ""),
            "lastMessageTime" to (firstMessage.timestamp ?: System.currentTimeMillis()),
            "createdAt" to System.currentTimeMillis(),
            "messages" to mapOf(messageId to firstMessage)
        )

        messagesRef.child(chatId).setValue(chatMap).await()

        // Create inbox entries
        val currentUserInbox = mapOf(
            "chatId" to chatId,
            "partnerId" to userId2,
            "partnerName" to "", // Will be filled later
            "lastMessage" to (firstMessage.text ?: ""),
            "lastMessageTime" to (firstMessage.timestamp ?: System.currentTimeMillis()),
            "unreadCount" to 0
        )

        val partnerInbox = mapOf(
            "chatId" to chatId,
            "partnerId" to userId1,
            "partnerName" to "", // Will be filled later
            "lastMessage" to (firstMessage.text ?: ""),
            "lastMessageTime" to (firstMessage.timestamp ?: System.currentTimeMillis()),
            "unreadCount" to 1
        )

        inboxRef.child(userId1).child(chatId).setValue(currentUserInbox).await()
        inboxRef.child(userId2).child(chatId).setValue(partnerInbox).await()

        return chatId
    }

    override suspend fun updateLastMessage(chatId: String, messageText: String, timestamp: Long) {
        messagesRef.child(chatId).updateChildren(
            mapOf(
                "lastMessage" to messageText,
                "lastMessageTime" to timestamp
            )
        ).await()
    }

    override suspend fun markMessagesAsRead(chatId: String, userId: String, messages: List<Message>) {
        messages.forEach { message ->
            if (message.senderId != userId && !message.read) {
                message.messageId?.let { messageId ->
                    messagesRef.child(chatId)
                        .child("messages")
                        .child(messageId)
                        .child("read")
                        .setValue(true)
                        .await()
                }
            }
        }

        inboxRef.child(userId).child(chatId).child("unreadCount").setValue(0).await()
    }

    override suspend fun clearChatForUser(chatId: String, userId: String) {
        inboxRef.child(userId).child(chatId).removeValue().await()
    }

    override fun observePartnerStatus(userId: String, onStatusChange: (String) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.child("state").getValue(String::class.java) ?: "offline"
                onStatusChange(state)
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        statusRef.child(userId).addValueEventListener(listener)
        return listener
    }

    override fun removeMessagesListener(chatId: String, listener: ChildEventListener) {
        messagesRef.child(chatId).child("messages").removeEventListener(listener)
    }

    override fun removeStatusListener(userId: String, listener: ValueEventListener) {
        statusRef.child(userId).removeEventListener(listener)
    }

    override fun observeNewMessagesForNotification(chatId: String, onNewMessage: (Message) -> Unit): ChildEventListener {
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(Message::class.java)
                message?.let {
                    it.messageId = snapshot.key ?: ""
                    onNewMessage(it)
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        messagesRef.child(chatId).child("messages").addChildEventListener(listener)
        return listener
    }

    private suspend fun updateInbox(chatId: String, message: Message) {
        val updates = mapOf(
            "lastMessage" to getMessagePreview(message),
            "lastMessageTime" to (message.timestamp ?: System.currentTimeMillis())
        )

        inboxRef.child(message.senderId).child(chatId).updateChildren(updates).await()
        inboxRef.child(message.receiverId).child(chatId).updateChildren(updates).await()

        if (message.senderId != message.receiverId) {
            try {
                val unreadSnapshot = inboxRef.child(message.receiverId).child(chatId).child("unreadCount").get().await()
                val currentCount = unreadSnapshot.getValue(Int::class.java) ?: 0
                inboxRef.child(message.receiverId).child(chatId).child("unreadCount").setValue(currentCount + 1).await()
            } catch (e: Exception) {
                inboxRef.child(message.receiverId).child(chatId).child("unreadCount").setValue(1).await()
            }
        }
    }

    override suspend fun getLastMessageAfterDeletion(chatId: String, userId: String): Pair<String, Long>? {
        return try {
            val messagesSnapshot = messagesRef.child(chatId)
                .child("messages")
                .orderByChild("timestamp")
                .get()
                .await()

            val visibleMessages = messagesSnapshot.children
                .mapNotNull { it.getValue(Message::class.java) }
                .filter { !it.isHiddenForUser(userId) }
                .sortedByDescending { it.timestamp ?: 0L }

            val lastMessage = visibleMessages.firstOrNull()
            if (lastMessage != null) {
                val messageText = when (lastMessage.messageType) {
                    "image" -> "📷 Image"
                    "video" -> "🎬 Video"
                    else -> lastMessage.text ?: "New message"
                }
                Pair(messageText, lastMessage.timestamp ?: System.currentTimeMillis())
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error getting last message: ${e.message}")
            null
        }
    }

    private fun getMessagePreview(message: Message): String {
        return when (message.messageType) {
            "image" -> "📷 Image"
            "video" -> "🎬 Video"
            "system_trade_accepted" -> "Trade accepted"
            else -> message.text ?: "New message"
        }
    }
}
