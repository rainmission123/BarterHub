package com.example.barterhub.data.models

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    // ============ FIREBASE FIELDS (EXACT MATCH) ============
    var userId: String = "",  // We'll set this manually

    // ✅ EXACTLY AS IN DATABASE:
    var username: String? = null,         // ALWAYS exists: "username": "Rain Mission"
    var fullName: String? = null,         // Sometimes exists: "fullName": "Rian Llanos Mission"
    var email: String? = null,            // Sometimes exists: "email": "tigcutdog@gmail.com"

    // Profile Images - FROM YOUR DATABASE:
    var profileImageUrl: String? = null,  // ✅ THIS HAS THE ACTUAL URL: "https://res.cloudinary.com/..."

    // Contact Info
    var address: String? = null,          // ✅ "address": "Talisay, Batangas"
    var phoneNumber: String? = null,      // ✅ "phoneNumber": "9513645480"

    // Bio
    var bio: String? = null,              // ✅ "bio": "Hello"

    // Trading Stats
    var rating: Double? = null,           // ✅ "rating": 5 (Int in Firebase, Double in Kotlin)
    var recentRating: Double? = null,     // Some users have this
    var reviewsCount: Int = 0,            // Some users have this
    var tradesCompleted: Int = 0,         // Some users have this
    var successRate: Int = 0,             // Some users have this

    // Coins & Items
    var coins: Int = 0,                   // ✅ "coins": 546
    var itemsListed: Int = 0,             // ✅ "itemsListed": 0

    // Verification
    var isIDVerified: String? = null,     // ✅ "isIDVerified": "verified"
    var isPremium: Boolean = false,       // ✅ "isPremium": true
    var verifiedAt: Long = 0L,            // Some users have this
    var verifiedBy: String? = null,       // Some users have this

    // Badges
    var badges: Map<String, Boolean>? = null, // ✅ "badges": {community: false, ...}

    // Dates & Timestamps
    var memberSince: String? = null,      // Some users have: "memberSince": "2025-12"
    var updatedAt: Long = 0L,             // Some users have: "updatedAt": 1760515959433
    var createdAt: Long = 0L,
    var lastSeen: Long = 0L,

    // Premium Info
    var premiumUntil: String? = null,     // Some users have: "premiumUntil": "Jan 02, 2027"
    var premiumExpiry: Long = 0L,         // Some users have: "premiumExpiry": 1771134797084

    // ID Verification URLs
    var idFrontUrl: String? = null,       // ✅ "idFrontUrl": "https://..."
    var idBackUrl: String? = null,        // ✅ "idBackUrl": "https://..."

    // Other Firebase fields
    var fcmToken: String? = null,         // ✅ "fcmToken": "dXI3hIBKQ3WteHJkGKXL_r..."
    var lastWeeklyReset: Long = 0L,
    var weeklyTrades: Int = 0,
    var weeklyReviews: Int = 0,
    var lastRated: Long = 0L,
    var verificationSubmittedAt: Long = 0L,
    var mysteryBoxes: Int = 0,

    // Online Status (might need separate listener)
    var isOnline: Boolean = false,
    var lastActive: Long = 0L,

    // ============ LOCAL/UI FIELDS ============
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
            // Add other location fields if you have them
            // !location.isNullOrEmpty() -> location!!
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

    fun isVerified(): Boolean {
        return isIDVerified == "verified"
    }

    fun getTradeStats(): String {
        return if (tradesCompleted > 0) "$tradesCompleted trades" else "No trades yet"
    }

    fun getCoinCount(): String {
        return if (coins > 0) "$coins coins" else ""
    }

    fun formatMemberSince(): String {
        return if (!memberSince.isNullOrEmpty()) "Member since $memberSince" else ""
    }

    // For debugging
    fun printDebugInfo() {
        println("=== USER DEBUG: $userId ===")
        println("Username: $username")
        println("Full Name: $fullName")
        println("Email: $email")
        println("Address: $address")
        println("Phone: $phoneNumber")
        println("Rating: $rating")
        println("Profile Image: ${getProfileImage()}")
        println("Bio: ${bio?.take(50)}")
        println("Is Verified: ${isVerified()}")
        println("Is Premium: $isPremium")
        println("=========================")
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