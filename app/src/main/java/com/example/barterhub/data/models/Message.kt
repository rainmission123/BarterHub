package com.example.barterhub.data.models

data class Message(
    val messageId: String? = null,
    val senderId: String = "",
    val senderName: String? = null,
    val text: String? = null,
    val imageUrl: String? = null,
    val timestamp: Long = 0L,
    val senderProfilePic: String? = null,
    val isRead: Boolean = false,
    val messageType: String = "text"
) {
    constructor() : this(null, "", null, null, null, 0L, null, false, "text")
}