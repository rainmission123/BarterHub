package com.example.barterhub.network

data class PayMongoRequest(
    val amount: Int,
    val paymentMethod: String,
    val userId: String,
    val coins: Int,
    val currency: String = "PHP"
)