package com.example.barterhub.utils

import android.annotation.SuppressLint
import android.util.Log
import java.util.Calendar

object DateFormatter {

    @SuppressLint("DefaultLocale")
    fun getCurrentYearMonth(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        return "$year-${String.format("%02d", month)}"
    }

    fun formatMemberSinceWithMonth(dateValue: String?): String {
        Log.d("ProfileDebug", "📅 Formatting memberSince: '$dateValue'")

        if (dateValue.isNullOrEmpty()) {
            val current = getCurrentYearMonth()
            return formatYearMonth(current)
        }

        try {
            if (dateValue.length == 4 && dateValue.all { it.isDigit() }) {
                val year = dateValue.toInt()
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)

                return if (year == currentYear) {
                    val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
                    "Member since ${getMonthName(currentMonth)} $year"
                } else {
                    "Member since December $year"
                }
            }

            if (dateValue.contains("-")) {
                val parts = dateValue.split("-")
                if (parts.size >= 2) {
                    val year = parts[0].toIntOrNull() ?: 2024
                    val month = parts[1].toIntOrNull() ?: 12
                    return "Member since ${getMonthName(month)} $year"
                }
            }
        } catch (e: Exception) {
            Log.e("ProfileDebug", "Error formatting date: $dateValue", e)
        }

        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        return "Member since ${getMonthName(month)} $year"
    }

    fun formatYearMonth(yearMonth: String): String {
        try {
            val parts = yearMonth.split("-")
            if (parts.size >= 2) {
                val year = parts[0].toIntOrNull() ?: 2025
                val month = parts[1].toIntOrNull() ?: 1
                return "Member since ${getMonthName(month)} $year"
            }
        } catch (e: Exception) {
            Log.e("ProfileDebug", "Error in formatYearMonth: $yearMonth", e)
        }
        return "Member since 2025"
    }

    private fun getMonthName(month: Int): String {
        val monthNames = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        return if (month in 1..12) monthNames[month - 1] else "Month $month"
    }
}