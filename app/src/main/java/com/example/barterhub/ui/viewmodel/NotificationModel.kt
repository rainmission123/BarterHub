package com.example.barterhub.ui.viewmodel

data class NotificationModel(
    val id: String? = null,
    val type: String? = null,
    val fromUserId: String? = null,
    val itemId: String? = null,
    val read: Boolean? = false,
    val timestamp: Long = 0L,
    val coins: Int? = null
)
