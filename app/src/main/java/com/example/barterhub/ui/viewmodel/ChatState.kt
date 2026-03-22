package com.example.barterhub.ui.viewmodel

import com.example.barterhub.data.models.Message

data class ChatState(
    val messages: List<Message> = emptyList(),
    val partnerName: String = "",
    val partnerProfilePic: String? = null,
    val currentUserProfilePic: String? = null,
    val currentUserName: String = "",
    val partnerStatus: String = "Offline",
    val uploadProgress: Map<String, Int> = emptyMap(),
    val isTradeAccepted: Boolean = false,
    val tradeData: TradeData? = null
)

data class TradeData(
    val targetItemTitle: String = "",
    val offeredItemTitle: String = "",
    val offeredBy: String = "",
    val acceptedBy: String = "",
    val requestId: String = ""
)

sealed class ChatEvent {
    data class ShowError(val message: String) : ChatEvent()
    data class ShowMessage(val message: String) : ChatEvent()
    data class NavigateToProfile(val userId: String) : ChatEvent()
    data class NavigateToPartnerProfile(val partnerId: String) : ChatEvent()
    object NavigateBack : ChatEvent()
}