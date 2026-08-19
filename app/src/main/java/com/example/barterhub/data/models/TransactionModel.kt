package com.example.barterhub.data.models

import java.io.Serial
import java.io.Serializable

data class TransactionModel(
    val title: String = "",
    val type: String = "",
    val amount: Double = 0.0,
    val coins: Int = 0,
    val date: String = "",
    val status: String = "",
    val transactionId: String = "",
    val referenceNo: String = "",
    val paymentMethod: String = "",
    val paymentId: String = "",
    val provider: String = "",
    val checkoutSessionId: String = "",
    val walletBalanceAfter: Int? = null,
    val timestamp: Long = 0L,
    val fromName: String = "",
    val toName: String = ""
) : Serializable {
    companion object {
        @Serial
        private const val serialVersionUID: Long = -6458563323891300634L
    }
}
