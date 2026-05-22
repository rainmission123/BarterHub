package com.example.barterhub.ui.earn

data class Challenge(
    val title: String = "",
    val reward: String = "",
    val action: String = "",
    val isCompleted: Boolean = false,
    val progress: Int = 0,
    val target: Int = 1,
    val rewardCoins: Int = 0,
    val rewarded: Boolean = false
)