package com.example.barterhub.data.models

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    // ============ FIREBASE FIELDS (EXACT MATCH) ============
    var userId: String = "",
    var username: String? = null,
    var fullName: String? = null,
    var email: String? = null,

    // Profile Images - FROM YOUR DATABASE:
    var profileImageUrl: String? = null,

    // Contact Info
    var address: String? = null,
    var phoneNumber: String? = null,

    // Bio
    var bio: String? = null,

    // Trading Stats
    var rating: Double? = null,
    var recentRating: Double? = null,
    var reviewsCount: Int = 0,
    var tradesCompleted: Int = 0,
    var successRate: Int = 0,

    // Coins & Items
    var coins: Int = 0,
    var itemsListed: Int = 0,

    // Verification
    var isIDVerified: String? = null,
    var isPremium: Boolean = false,
    var verifiedAt: Long = 0L,
    var verifiedBy: String? = null,

    // Badges
    var badges: Map<String, Boolean>? = null,

    // Dates & Timestamps
    var memberSince: String? = null,
    var updatedAt: Long = 0L,
    var createdAt: Long = 0L,
    var lastSeen: Long = 0L,

    // Premium Info
    var premiumUntil: String? = null,
    var premiumExpiry: Long = 0L,

    // ID Verification URLs
    var idFrontUrl: String? = null,
    var idBackUrl: String? = null,

    // Other Firebase fields
    var fcmToken: String? = null,
    var lastWeeklyReset: Long = 0L,
    var weeklyTrades: Int = 0,
    var weeklyReviews: Int = 0,
    var lastRated: Long = 0L,
    var verificationSubmittedAt: Long = 0L,
    var mysteryBoxes: Int = 0,

    // Online Status (might need separate listener)
    var isOnline: Boolean = false,
    var lastActive: Long = 0L,

    var friendStatus: FriendStatus = FriendStatus.NOT_FRIEND

) {
    // ============ HELPER FUNCTIONS ============

    fun getDisplayName(): String {
        return when {
            // Most users have username
            !username.isNullOrEmpty() -> username!!
            // Some have fullName
            !fullName.isNullOrEmpty() -> fullName!!
            // Fallback to email
            !email.isNullOrEmpty() -> email!!.split("@").first()
            else -> "Unknown User"
        }
    }

    fun getLocation(): String {
        return when {
            !address.isNullOrEmpty() -> address!!
            else -> "No location specified"
        }
    }

    fun getProfileImage(): String? {
        android.util.Log.d("UserModel",
            "Getting profile image for ${username}: " +
                    "profileImageUrl = '${profileImageUrl?.take(30)}...'"
        )

        return if (!profileImageUrl.isNullOrEmpty()) {
            android.util.Log.d("UserModel", "✅ Found profile image URL: ${profileImageUrl?.take(30)}...")
            profileImageUrl
        } else {
            android.util.Log.d("UserModel", "❌ No profile image URL")
            null
        }
    }

    fun getRatingText(): String {
        return when {
            rating != null && rating!! > 0 -> "⭐ ${String.format("%.1f", rating!!)}"
            recentRating != null && recentRating!! > 0 -> "⭐ ${String.format("%.1f", recentRating!!)}"
            else -> "No ratings"
        }
    }

    constructor() : this(
        userId = "",
        username = null,
        fullName = null,
        email = null,
        profileImageUrl = null,
        address = null,
        phoneNumber = null,
        bio = null,
        rating = null,
        recentRating = null,
        reviewsCount = 0,
        tradesCompleted = 0,
        successRate = 0,
        coins = 0,
        itemsListed = 0,
        isIDVerified = null,
        isPremium = false,
        verifiedAt = 0L,
        verifiedBy = null,
        badges = null,
        memberSince = null,
        updatedAt = 0L,
        createdAt = 0L,
        lastSeen = 0L,
        premiumUntil = null,
        premiumExpiry = 0L,
        idFrontUrl = null,
        idBackUrl = null,
        fcmToken = null,
        lastWeeklyReset = 0L,
        weeklyTrades = 0,
        weeklyReviews = 0,
        lastRated = 0L,
        verificationSubmittedAt = 0L,
        mysteryBoxes = 0,
        isOnline = false,
        lastActive = 0L,
        friendStatus = FriendStatus.NOT_FRIEND
    )
}