package com.example.barterhub.ui.settings

import android.content.SharedPreferences

class SettingsManager(
    private val preferences: SharedPreferences
) {

    fun saveDarkMode(enabled: Boolean) {
        preferences.edit().putBoolean("dark_mode", enabled).apply()
    }

    fun isDarkModeEnabled(): Boolean {
        return preferences.getBoolean("dark_mode", false)
    }

    fun saveLocationEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("location_enabled", enabled).apply()
    }

    fun isLocationEnabled(): Boolean {
        return preferences.getBoolean("location_enabled", true)
    }

    fun saveProfilePublic(enabled: Boolean) {
        preferences.edit().putBoolean("profile_public", enabled).apply()
    }

    fun isProfilePublic(): Boolean {
        return preferences.getBoolean("profile_public", true)
    }

    fun saveAnalyticsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("analytics_enabled", enabled).apply()
    }

    fun isAnalyticsEnabled(): Boolean {
        return preferences.getBoolean("analytics_enabled", true)
    }
}