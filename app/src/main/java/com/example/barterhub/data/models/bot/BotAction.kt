package com.example.barterhub.data.models.bot

@Suppress("ClassName")
sealed class BotAction {
    object OPEN_ADD_ITEM : BotAction()
    object OPEN_WALLET : BotAction()
    object OPEN_PROFILE : BotAction()
    object OPEN_SUPPORT : BotAction()
    object OPEN_SEARCH : BotAction()
    object OPEN_CATEGORIES : BotAction()
    object OPEN_SAFETY_GUIDE : BotAction()
    object OPEN_CHAT_SUPPORT : BotAction()
    data class OPEN_URL(val url: String) : BotAction()
    data class OPEN_SCREEN(val screenName: String) : BotAction()
}