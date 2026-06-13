package com.example.barterhub.utils

object ActiveChatTracker {
    @Volatile
    var currentChatId: String? = null
}
