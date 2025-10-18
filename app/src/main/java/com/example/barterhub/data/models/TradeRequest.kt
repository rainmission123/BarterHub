package com.example.barterhub.data.models

data class TradeRequest(
    val requestId: String = "",
    val itemId: String = "",
    val owner: String = "",
    val ownerName: String = "",
    val ownerPhoto: String = "",
    val requester: String = "",
    val requesterName: String = "",
    val requesterPhoto: String = "",
    val itemTitle: String = "",
    val itemImage: String = "",
    val status: String = "Pending",
    val date: String = ""
)
