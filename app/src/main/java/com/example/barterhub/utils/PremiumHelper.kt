package com.example.barterhub.utils

object PremiumHelper {

    fun isPremiumActive(isPremium: Boolean?, premiumExpiry: Long?): Boolean {
        return isPremium == true &&
                premiumExpiry != null &&
                premiumExpiry > System.currentTimeMillis()
    }

    fun getPlanExpiry(planId: String): Long {
        val now = System.currentTimeMillis()
        return when (planId) {
            "1_month" -> now + (30L * 24 * 60 * 60 * 1000)
            "5_months" -> now + (150L * 24 * 60 * 60 * 1000)
            "1_year" -> now + (365L * 24 * 60 * 60 * 1000)
            else -> now
        }
    }
}