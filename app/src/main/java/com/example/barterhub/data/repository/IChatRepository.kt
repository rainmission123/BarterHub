package com.example.barterhub.data.repository

import com.example.barterhub.data.models.Message
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.ValueEventListener

interface IChatRepository {
    suspend fun getLastMessageAfterDeletion(chatId: String, userId: String): Pair<String, Long>?
    suspend fun hideMessageForUser(chatId: String, messageId: String, userId: String)
    fun observeMessages(chatId: String, onMessageAdded: (Message) -> Unit, onMessageChanged: (Message) -> Unit, onMessageRemoved: (String) -> Unit): ChildEventListener

    // Message sending
    suspend fun sendMessage(chatId: String, message: Message): String

    // Chat creation
    suspend fun createChat(userId1: String, userId2: String, itemId: String, itemTitle: String, firstMessage: Message): String

    // Chat updates
    suspend fun updateLastMessage(chatId: String, messageText: String, timestamp: Long)
    suspend fun markMessagesAsRead(chatId: String, userId: String, messages: List<Message>)
    suspend fun clearChatForUser(chatId: String, userId: String)

    // Status
    fun observePartnerStatus(userId: String, onStatusChange: (String) -> Unit): ValueEventListener
    fun setupUserPresence(userId: String)

    // Listeners cleanup
    fun removeMessagesListener(chatId: String, listener: ChildEventListener)
    fun removeStatusListener(userId: String, listener: ValueEventListener)

    // Notifications listener
    fun observeNewMessagesForNotification(chatId: String, onNewMessage: (Message) -> Unit): ChildEventListener
}