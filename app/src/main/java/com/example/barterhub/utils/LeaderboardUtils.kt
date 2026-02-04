package com.example.barterhub.utils

import com.google.firebase.database.FirebaseDatabase
import java.util.Calendar

object LeaderboardUtils {

    fun isNewWeek(lastReset: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = lastReset

        val lastWeek = calendar.get(Calendar.WEEK_OF_YEAR)
        val currentWeek = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)

        return lastWeek != currentWeek
    }

    fun resetWeeklyTradesIfNeeded(userId: String, lastReset: Long) {
        if (!isNewWeek(lastReset)) return

        val userRef = FirebaseDatabase.getInstance()
            .reference.child("users/$userId")

        val updates = mapOf(
            "weeklyTrades" to 0,
            "weeklyReviews" to 0,
            "lastWeeklyReset" to System.currentTimeMillis()
        )

        userRef.updateChildren(updates)
    }
}
