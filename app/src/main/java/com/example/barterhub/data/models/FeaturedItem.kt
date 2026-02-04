package com.example.barterhub.data.models

import com.google.firebase.database.IgnoreExtraProperties


data class FeaturedItem(
    var itemId: String = "",
    var title: String = "",
    var description: String = "",
    var category: String = "",
    var condition: String = "",
    var price: Double = 0.0,
    var displayPrice: String = "",
    var imageUrls: String = "",
    var location: String = "",
    val isActive: Boolean = true,
    val isArchived: Boolean = false,
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var ownerId: String = "",
    var ownerName: String = "",
    var ownerProfileImage: String = "",
    var timestamp: Long = 0,
    var likeCount: Int = 0,
    var likedBy: Map<String, Boolean> = emptyMap()
)
