package com.example.barterhub.data.models

data class Item(
    val itemId: String = "",
    val ownerId: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val condition: String = "",
    val price: Double?,
    val displayPrice: String = "",
    val location: String = "",
    val imageUrls: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val ownerProfileImage: String = ""
)
