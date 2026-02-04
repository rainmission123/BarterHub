package com.example.barterhub.data.models

data class TraderStats(
    val userId: String = "",
    val username: String = "",
    val profileImageUrl: String = "",
    val rating: Float = 0f,
    val reviewsCount: Int = 0,
    val tradesCompleted: Int = 0,
    val isVerified: Boolean = false,
    val rank: Int = 0
) {
    constructor() : this("", "", "", 0f, 0, 0, false, 0)

    // Calculate score for sorting
    fun getScore(): Float {
        return rating * 0.5f + reviewsCount * 0.3f + tradesCompleted * 0.2f
    }
}