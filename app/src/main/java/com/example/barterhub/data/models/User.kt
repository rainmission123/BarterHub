package com.example.barterhub.data.models

data class User(
    val userId: String = "",
    val email: String? = "",
    val username: String? = "",
    val profilePicture: String? = "",
    val phoneNumber: String? = "",
    val location: String? = "",
    val createdAt: Long = 0L,
    val lastSeen: Long = 0L,
    val isOnline: Boolean = false
) {
    // Default constructor for Firebase
    constructor() : this("", "", "", "", "", "", 0L, 0L, false)
}