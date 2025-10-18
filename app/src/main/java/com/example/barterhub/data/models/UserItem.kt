package com.example.barterhub.data.models

data class UserItem(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val condition: String = "",
    val price: String = "",
    val originalPrice: String,
    val ownerName: String,
    val location: String = "",
    val imageUrl: String = "",
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
