package com.example.barterhub.data.models

import com.google.firebase.database.Exclude

data class Message(
    var messageId: String = "",
    var senderId: String = "",
    var receiverId: String = "",
    var senderName: String = "",
    var text: String = "",

    // IMAGES
    var imageUrl: String? = null,
    var imageUri: String? = null,
    var imageUrls: List<String>? = null,

    // VIDEOS
    var videoUrl: String? = null,
    var fileName: String? = null,
    var fileSize: Long? = null,
    var videoDuration: Long? = null,

    // META
    var timestamp: Long = 0L,
    var senderProfilePic: String? = null,
    var read: Boolean = false,
    var readTimestamp: Long? = null,
    var messageType: String = "text",
    var isSystemMessage: Boolean = false,
    var itemId: String? = null,
    var tradeDetails: Map<String, Any>? = null,

    // 🔥 FIX: Change reactions type to match Firebase structure
    var reactions: Map<String, Map<String, Boolean>> = emptyMap(), // emoji -> userId -> true

    // UI ONLY - Excluded from Firebase
    @get:Exclude
    @set:Exclude
    var isUploading: Boolean = false,

    @get:Exclude
    @set:Exclude
    var uploadProgress: Int = 0

) {
    // Secondary constructor for Firebase deserialization
    constructor() : this(
        messageId = "",
        senderId = "",
        receiverId = "",
        senderName = "",
        text = "",
        messageType = "text",
        timestamp = 0L,
        read = false,
        isSystemMessage = false,
        reactions = emptyMap()
    )

    // 🔥 UPDATED: Helper function to check if message has reactions
    @Exclude
    fun hasReactions(): Boolean {
        return reactions.isNotEmpty()
    }

    // 🔥 UPDATED: Helper function to get current user's reaction
    @Exclude
    fun getUserReaction(userId: String): String? {
        return reactions.entries.firstOrNull { entry ->
            entry.value.containsKey(userId)
        }?.key
    }

    // 🔥 UPDATED: Helper function to get reaction count
    @Exclude
    fun getReactionCount(): Int {
        return reactions.values.sumOf { it.size }
    }

    // 🔥 ADDED: Get reactions as list of user IDs (for UI display)
    @Exclude
    fun getReactionsAsUserIds(): Map<String, List<String>> {
        return reactions.mapValues { entry ->
            entry.value.keys.toList()
        }
    }

    // 🔥 ADDED: Check if specific user reacted with emoji
    @Exclude
    fun hasUserReacted(userId: String, emoji: String): Boolean {
        return reactions[emoji]?.containsKey(userId) ?: false
    }
}