package com.example.barterhub.data.models

// data/models/Review.kt
data class Review(
    val reviewId: String = "",
    val tradeId: String = "", // ID ng trade/request
    val reviewerId: String = "", // ID ng nag-rate
    val reviewedUserId: String = "", // ID ng na-rate
    val rating: Float = 0f, // 1-5 stars
    val comment: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val reviewerName: String = "",
    val reviewedUserName: String = ""
)