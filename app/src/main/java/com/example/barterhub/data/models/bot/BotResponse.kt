package com.example.barterhub.data.models.bot

data class BotResponse(
    val message: String,
    val quickReplies: List<String> = emptyList(),
    val action: BotAction? = null,
    val intent: BotIntent = BotIntent.UNKNOWN
)