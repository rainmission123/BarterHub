package com.example.barterhub.ui.earn

data class DailyChallengeEntity(
    val title: String = "",
    val action: String = "",
    val progress: Int = 0,
    val target: Int = 1,
    val reward: Int = 0,
    val completed: Boolean = false,
    val rewarded: Boolean = false
)