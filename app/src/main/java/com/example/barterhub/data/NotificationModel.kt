package com.example.barterhub.data

data class NotificationModel(
    val id: String? = null,
    val type: String? = null,
    val fromUserId: String? = null,
    val fromUserName: String? = null,
    val fromUserProfile: String? = null,
    val itemId: String? = null,
    val coins: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val read: Boolean = false,
    var message: String? = null,
    var status: String? = null
)
