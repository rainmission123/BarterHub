package com.example.barterhub.data.repository

import android.util.Log
import com.example.barterhub.data.models.Message
import com.example.barterhub.utils.ChatUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
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

    override suspend fun chatExists(chatId: String): Boolean {
        return try {
            Log.d("CHAT_SEND_FIREBASE_WRITE", "Checking if chats/$chatId exists before listener attach")
            messagesRef.child(chatId).get().await().exists()
        } catch (e: Exception) {
            Log.e("CHAT_SEND_EXCEPTION", "Failed checking chats/$chatId existence: ${e.message}", e)
            false
        }
    }

    override suspend fun canCurrentUserAccessChat(chatId: String): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (chatId.isBlank() || uid.isBlank()) {
            Log.d("CHAT_ACCESS_CHECK", "chatId=$chatId uid=$uid exists=false result=false")
            Log.e("CHAT_ACCESS_DENIED", "Missing chatId or auth uid for chat access check")
            return false
        }

        return try {
            val chatSnap = messagesRef.child(chatId).get().await()
            val exists = chatSnap.exists()
            val canAccess = exists && isParticipantSnapshot(chatSnap, uid)

            Log.d("CHAT_ACCESS_CHECK", "chatId=$chatId uid=$uid exists=$exists result=$canAccess")

            if (exists && !canAccess) {
                Log.e("CHAT_ACCESS_DENIED", "chat exists but current user is not participant: chatId=$chatId uid=$uid")
            }

            canAccess
        } catch (e: Exception) {
            Log.e("CHAT_ACCESS_DENIED", "Failed access check for chatId=$chatId uid=$uid: ${e.message}", e)
            false
        }
    }

    override suspend fun sendMessage(chatId: String, message: Message): String {
        Log.d(
            "CHAT_SEND_START",
            "sendMessage chatId=$chatId senderId=${message.senderId} receiverId=${message.receiverId} " +
                    "itemId=${message.itemId} type=${message.messageType}"
        )
        Log.d("CHAT_SEND_CHAT_ID", chatId)
        Log.d("CHAT_SEND_RECEIVER", message.receiverId)

        if (chatId.isBlank() || message.senderId.isBlank() || message.receiverId.isBlank()) {
            Log.e(
                "CHAT_SEND_FAILURE",
                "Invalid send args chatId=$chatId senderId=${message.senderId} receiverId=${message.receiverId}"
            )
            return ""
        }

        val messageId = messagesRef.child(chatId).child("messages").push().key ?: return ""
        message.messageId = messageId

        try {
            val chatRef = messagesRef.child(chatId)
            Log.d("CHAT_SEND_FIREBASE_WRITE", "Checking chats/$chatId")
            val chatSnap = chatRef.get().await()

            if (!chatSnap.exists()) {
                Log.d(
                    "CHAT_SEND_FIREBASE_WRITE",
                    "chats/$chatId missing; creating conversation before first message"
                )
                createChatAtId(chatId, message)
                Log.d("CHAT_SEND_SUCCESS", "Created chat and first message: chats/$chatId/messages/$messageId")
                return messageId
            }

            if (!isParticipantSnapshot(chatSnap, message.senderId)) {
                Log.e(
                    "CHAT_SEND_FAILURE",
                    "Blocked message write: ${message.senderId} is not a participant of $chatId"
                )
                return ""
            }

            Log.d("CHAT_SEND_FIREBASE_WRITE", "Writing chats/$chatId/messages/$messageId")
            chatRef.child("messages")
                .child(messageId)
                .setValue(message)
                .await()

            Log.d("CHAT_SEND_FIREBASE_WRITE", "Updating inbox metadata for $chatId")
            try {
                updateInbox(chatId, message)
            } catch (metadataError: Exception) {
                Log.e(
                    "CHAT_SEND_DATABASE_ERROR",
                    "Message was written, but inbox metadata failed for $chatId: ${metadataError.message}",
                    metadataError
                )
            }
            Log.d("CHAT_SEND_SUCCESS", "Message sent: chats/$chatId/messages/$messageId")
        } catch (e: Exception) {
            Log.e(
                "CHAT_SEND_EXCEPTION",
                "Failed sendMessage chatId=$chatId senderId=${message.senderId} " +
                        "receiverId=${message.receiverId}: ${e.message}",
                e
            )
            throw e
        }

        return messageId
    }

    private suspend fun createChatAtId(chatId: String, firstMessage: Message) {
        val timestamp = firstMessage.timestamp.takeIf { it > 0L } ?: System.currentTimeMillis()
        val messageId = firstMessage.messageId.ifBlank {
            messagesRef.child(chatId).child("messages").push().key.orEmpty()
        }

        if (messageId.isBlank()) {
            Log.e("CHAT_SEND_FAILURE", "Could not generate first message id for $chatId")
            return
        }

        firstMessage.messageId = messageId
        firstMessage.timestamp = timestamp

        val chatMap = mapOf(
            "chatId" to chatId,
            "participants" to mapOf(firstMessage.senderId to true, firstMessage.receiverId to true),
            "participantIds" to mapOf(firstMessage.senderId to true, firstMessage.receiverId to true),
            "user1Id" to firstMessage.senderId,
            "user2Id" to firstMessage.receiverId,
            "itemId" to firstMessage.itemId,
            "lastMessage" to firstMessage.text.ifBlank { "New message" },
            "lastMessageTime" to timestamp,
            "createdAt" to timestamp,
            "unreadCount" to mapOf(
                firstMessage.senderId to 0,
                firstMessage.receiverId to 0
            ),
            "messages" to mapOf(messageId to firstMessage)
        )

        try {
            Log.d("CHAT_SEND_FIREBASE_WRITE", "Creating chats/$chatId")
            messagesRef.child(chatId).setValue(chatMap).await()
            Log.d("CHAT_SEND_FIREBASE_WRITE", "Creating user_inbox entries for $chatId")
            try {
                updateInbox(chatId, firstMessage)
            } catch (metadataError: Exception) {
                Log.e(
                    "CHAT_SEND_DATABASE_ERROR",
                    "First message chat was created, but inbox metadata failed for $chatId: ${metadataError.message}",
                    metadataError
                )
            }
        } catch (e: Exception) {
            Log.e(
                "CHAT_SEND_EXCEPTION",
                "Failed creating first-message chat at chats/$chatId: ${e.message}",
                e
            )
            throw e
        }
    }

    override suspend fun getOrCreateDirectChat(
        currentUserId: String,
        partnerId: String,
        itemId: String,
        itemTitle: String,
        partnerName: String
    ): String {
        if (currentUserId.isBlank() || partnerId.isBlank() || currentUserId == partnerId) {
            Log.e(
                "CHAT_SEND_FAILURE",
                "Invalid getOrCreateDirectChat args currentUserId=$currentUserId partnerId=$partnerId"
            )
            return ""
        }

        val chatId = ChatUtils.generateChatId(currentUserId, partnerId)
        val now = System.currentTimeMillis()
        val chatRef = messagesRef.child(chatId)

        return try {
            Log.d("CHAT_SEND_FIREBASE_WRITE", "getOrCreateDirectChat checking chats/$chatId")
            val existingChat = chatRef.get().await()

            if (existingChat.exists()) {
                if (!isParticipantSnapshot(existingChat, currentUserId)) {
                    Log.e(
                        "CHAT_ACCESS_DENIED",
                        "Existing direct chat is not accessible: chatId=$chatId uid=$currentUserId"
                    )
                    return ""
                }

                ensureDirectInboxEntries(
                    chatId = chatId,
                    currentUserId = currentUserId,
                    partnerId = partnerId,
                    partnerName = partnerName,
                    lastMessage = existingChat.child("lastMessage").getValue(String::class.java).orEmpty(),
                    lastMessageTime = existingChat.child("lastMessageTime").getValue(Long::class.java) ?: now
                )
                Log.d("CHAT_SEND_SUCCESS", "Reusing direct chat: $chatId")
                return chatId
            }

            val chatData = mapOf(
                "chatId" to chatId,
                "participants" to mapOf(currentUserId to true, partnerId to true),
                "participantIds" to mapOf(currentUserId to true, partnerId to true),
                "user1Id" to currentUserId,
                "user2Id" to partnerId,
                "itemId" to itemId,
                "itemTitle" to itemTitle,
                "lastMessage" to "",
                "lastMessageTime" to now,
                "createdAt" to now,
                "unreadCount" to mapOf(
                    currentUserId to 0,
                    partnerId to 0
                )
            )

            Log.d("CHAT_SEND_FIREBASE_WRITE", "Creating direct chat chats/$chatId before navigation")
            chatRef.setValue(chatData).await()

            ensureDirectInboxEntries(
                chatId = chatId,
                currentUserId = currentUserId,
                partnerId = partnerId,
                partnerName = partnerName,
                lastMessage = "",
                lastMessageTime = now
            )

            Log.d("CHAT_SEND_SUCCESS", "Created direct chat before navigation: $chatId")
            chatId
        } catch (e: Exception) {
            Log.e(
                "CHAT_SEND_EXCEPTION",
                "Failed getOrCreateDirectChat currentUserId=$currentUserId partnerId=$partnerId chatId=$chatId: ${e.message}",
                e
            )
            ""
        }
    }

    private suspend fun ensureDirectInboxEntries(
        chatId: String,
        currentUserId: String,
        partnerId: String,
        partnerName: String,
        lastMessage: String,
        lastMessageTime: Long
    ) {
        try {
            writeDirectInboxEntry(
                userId = currentUserId,
                chatId = chatId,
                partnerId = partnerId,
                partnerName = partnerName.ifBlank { "Chat Partner" },
                lastMessage = lastMessage,
                lastMessageTime = lastMessageTime
            )
            writeDirectInboxEntry(
                userId = partnerId,
                chatId = chatId,
                partnerId = currentUserId,
                partnerName = "",
                lastMessage = lastMessage,
                lastMessageTime = lastMessageTime
            )
            Log.d("CHAT_SEND_SUCCESS", "Direct inbox entries ready for $chatId")
        } catch (e: Exception) {
            Log.e(
                "CHAT_SEND_DATABASE_ERROR",
                "Direct chat exists but inbox repair failed for $chatId: ${e.message}",
                e
            )
        }
    }

    private suspend fun writeDirectInboxEntry(
        userId: String,
        chatId: String,
        partnerId: String,
        partnerName: String,
        lastMessage: String,
        lastMessageTime: Long
    ) {
        val inboxEntryRef = inboxRef.child(userId).child(chatId)

        suspendCoroutine<Unit> { continuation ->
            inboxEntryRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    if (currentData.child("deleted").getValue(Boolean::class.java) == true) {
                        return Transaction.abort()
                    }

                    val currentUnread = currentData.child("unreadCount").getValue(Int::class.java)
                        ?: currentData.child("unreadCount").getValue(Long::class.java)?.toInt()
                        ?: 0
                    val currentPartnerName =
                        currentData.child("partnerName").getValue(String::class.java).orEmpty()

                    currentData.value = mapOf(
                        "chatId" to chatId,
                        "partnerId" to partnerId,
                        "partnerName" to currentPartnerName.ifBlank { partnerName },
                        "lastMessage" to lastMessage,
                        "lastMessageTime" to lastMessageTime,
                        "unreadCount" to currentUnread
                    )

                    return Transaction.success(currentData)
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    when {
                        error != null -> continuation.resumeWithException(error.toException())
                        !committed -> continuation.resume(Unit)
                        else -> continuation.resume(Unit)
                    }
                }
            })
        }
    }

    override suspend fun createChat(
        userId1: String,
        userId2: String,
        itemId: String,
        itemTitle: String,
        firstMessage: Message
    ): String {
        val chatId = ChatUtils.generateChatId(userId1, userId2)

        val messageId = firstMessage.messageId.takeIf { it.isNotBlank() }
            ?: messagesRef.child(chatId).child("messages").push().key
            ?: return ""
        firstMessage.messageId = messageId

        val existingChat = messagesRef.child(chatId).get().await()
        if (existingChat.exists()) {
            if (!isParticipantSnapshot(existingChat, userId1)) {
                Log.e("CHAT_SEND_FAILURE", "Cannot reuse existing chat because $userId1 is not a participant of $chatId")
                return ""
            }

            sendMessage(chatId, firstMessage)
            return chatId
        }

        val chatMap = mapOf(
            "chatId" to chatId,
            "participants" to mapOf(userId1 to true, userId2 to true),
            "participantIds" to mapOf(userId1 to true, userId2 to true),
            "user1Id" to userId1,
            "user2Id" to userId2,
            "itemId" to itemId,
            "itemTitle" to itemTitle,
            "lastMessage" to (firstMessage.text ?: ""),
            "lastMessageTime" to (firstMessage.timestamp ?: System.currentTimeMillis()),
            "createdAt" to System.currentTimeMillis(),
            "unreadCount" to mapOf(
                userId1 to 0,
                userId2 to 1
            ),
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
        try {
            Log.d("CHAT_SEND_FIREBASE_WRITE", "Writing chats/$chatId/lastMessage")
            messagesRef.child(chatId).child("lastMessage").setValue(messageText).await()
            Log.d("CHAT_SEND_FIREBASE_WRITE", "Writing chats/$chatId/lastMessageTime")
            messagesRef.child(chatId).child("lastMessageTime").setValue(timestamp).await()
        } catch (e: Exception) {
            Log.e("CHAT_SEND_EXCEPTION", "Failed updateLastMessage for $chatId: ${e.message}", e)
            throw e
        }
    }

    override suspend fun markMessagesAsRead(chatId: String, userId: String, messages: List<Message>) {
        try {
            inboxRef.child(userId)
                .child(chatId)
                .child("unreadCount")
                .setValue(0)
                .await()
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to clear inbox unread count: ${e.message}", e)
        }

        try {
            messagesRef.child(chatId)
                .child("unreadCount")
                .child(userId)
                .setValue(0)
                .await()
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to clear chat unread count: ${e.message}", e)
        }
    }

    override suspend fun clearChatForUser(chatId: String, userId: String) {
        inboxRef.child(userId).child(chatId).removeValue().await()
    }

    override fun observePartnerStatus(userId: String, onStatusChange: (String) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.child("state").getValue(String::class.java)
                    ?: snapshot.child("isOnline").getValue(Boolean::class.java)?.let { isOnline ->
                        if (isOnline) "online" else "offline"
                    }
                    ?: "offline"
                onStatusChange(state)
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        statusRef.child(userId).addValueEventListener(listener)
        return listener
    }

    override fun setupUserPresence(userId: String) {
        Log.d(
            "PRESENCE_CHAT_REPOSITORY_IGNORED",
            "setupUserPresence(userId=$userId) ignored. UserPresenceManager is the single presence writer.\n" +
                    Log.getStackTraceString(Throwable("PRESENCE_CHAT_REPOSITORY_CALL_STACK"))
        )
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
        ChatInboxUpdater.updateForMessage(database, chatId, message)
    }

    private suspend fun isExistingParticipant(chatId: String, userId: String): Boolean {
        return try {
            val chatSnap = messagesRef.child(chatId).get().await()
            isParticipantSnapshot(chatSnap, userId)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to verify chat participant: ${e.message}")
            false
        }
    }

    private fun isParticipantSnapshot(chatSnap: DataSnapshot, userId: String): Boolean {
        if (!chatSnap.exists()) return false
        if (chatSnap.child("participants").child(userId).exists()) return true
        if (chatSnap.child("participantIds").child(userId).exists()) return true

        return listOf(
            "user1Id",
            "user2Id",
            "user1",
            "user2",
            "buyerId",
            "sellerId",
            "senderId",
            "receiverId",
            "ownerId",
            "requesterId"
        ).any { field ->
            chatSnap.child(field).getValue(String::class.java) == userId
        }
    }

    private fun incrementUnreadCounter(counterRef: DatabaseReference) {
        counterRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentCount = currentData.getValue(Int::class.java) ?: 0
                currentData.value = currentCount + 1
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null) {
                    Log.e("ChatRepository", "Failed to increment unread count: ${error.message}")
                }
            }
        })
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
