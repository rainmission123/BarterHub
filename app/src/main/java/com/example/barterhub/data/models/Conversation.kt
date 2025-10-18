package com.example.barterhub.data.models

data class Conversation(
    val chatId: String = "",
    val participants: Map<String, Boolean> = emptyMap(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantProfilePics: Map<String, String?> = emptyMap(),
    val messages: Map<String, Message> = emptyMap(),
    val lastMessage: String? = "",
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0,
    val createdAt: Long = 0L
) {

    constructor() : this("", emptyMap(), emptyMap(), emptyMap(), emptyMap(), "", 0L, 0, 0L)
}