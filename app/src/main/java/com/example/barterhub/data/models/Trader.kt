package com.example.barterhub.data.models

import java.util.Locale

data class Trader(
    val userId: String = "",
    val username: String = "",
    val profileImageUrl: String = "",
    val rating: Double = 0.0,
    val reviewsCount: Int = 0,
    val tradesCompleted: Int = 0,
    val isVerified: Boolean = false,
    val rank: Int = 0,
    val isPremium: Boolean = false,
    val premiumExpiry: Long = 0L,
    val score: Double = 0.0,
    val address: String? = null,
    val lastWeeklyReset: Long? = null,
    val city: String? = null,
    val badges: Map<String, Boolean> = emptyMap()
) {
    fun getDisplayRating(): String = String.format(Locale.US, "%.1f", rating)
    fun getReviewsText(): String = "$reviewsCount review${if (reviewsCount != 1) "s" else ""}"
    fun getTradesText(): String = "$tradesCompleted trade${if (tradesCompleted != 1) "s" else ""}"
}