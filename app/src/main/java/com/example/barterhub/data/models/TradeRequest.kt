package com.example.barterhub.data.models

data class TradeRequest(
    val requestId: String = "",
    val fromUser: TradeUser = TradeUser(),
    val toUser: TradeUser = TradeUser(),
    val targetItem: TradeItem = TradeItem(),
    val offeredItem: TradeItem = TradeItem(),
    var status: String = "Pending",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val additionalPhotos: List<String> = emptyList(),
    val preferredMeetup: String = ""
)

data class TradeItem(
    val itemId: String = "",
    val title: String = "",
    val description: String = "",
    val image: String = "",
    val category: String = "",
    val condition: String = ""
)

data class TradeUser(
    val userId: String = "",
    val username: String = "",
    val profileImage: String = "",
    val location: String = "",
    val address: String = "",
    val rating: Double = 0.0
)