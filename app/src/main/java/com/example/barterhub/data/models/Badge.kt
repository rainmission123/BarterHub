package com.example.barterhub.data.models

import com.example.barterhub.R

data class Badge(
    val id: String = "",
    val name: String = "",
    val iconResId: Int = R.drawable.ic_badge_first_trade,
    val achieved: Boolean = false
)
