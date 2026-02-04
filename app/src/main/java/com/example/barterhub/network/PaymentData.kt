package com.example.barterhub.network

data class PaymentData(
    val userId: String = "",
    val userEmail: String = "",
    val paymentIntentId: String = "",
    val coins: Int = 0,
    val amount: Double = 0.0,
    val status: String = "pending",
    val method: String = "",
    val timestamp: Long = 0,
    val currency: String = "PHP"
)