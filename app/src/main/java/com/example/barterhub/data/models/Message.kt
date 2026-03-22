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

    // REACTIONS
    var reactions: Map<String, Map<String, Boolean>> = emptyMap(),

    // 👇 BAGONG FIELD: Track kung sino ang nag-delete/hide ng message
    var hiddenForUsers: Map<String, Boolean> = emptyMap(),

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

    // 👇 HELPER FUNCTION: Check if message is hidden for current user
    fun isHiddenForUser(userId: String): Boolean {
        return hiddenForUsers[userId] == true
    }
}