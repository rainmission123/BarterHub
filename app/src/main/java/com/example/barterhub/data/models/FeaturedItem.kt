package com.example.barterhub.data.models

data class FeaturedItem(
    val title: String = "",
    val description: String = "",
    val imageUrls: String = "",
    val price: Any? = null,
    val originalPrice: String = "",
    val itemId: String = "",
    val ownerId: String = "",
    var ownerName: String = "",
    var ownerProfileImage: String = "",
    val location: String = "",
    val category: String = "",
    val condition: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)
