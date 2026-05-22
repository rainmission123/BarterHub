package com.example.barterhub.ui.settings

import android.content.Context
import android.view.LayoutInflater
import android.widget.Switch
import android.widget.Toast
import com.example.barterhub.R
import com.google.android.material.bottomsheet.BottomSheetDialog

class PrivacyManager(
    private val context: Context,
    private val settingsManager: SettingsManager
) {

    fun showPrivacyBottomSheet() {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.bottom_sheet_privacy_settings, null)

        val switchProfile = view.findViewById<Switch>(R.id.switchProfileVisibility)
        val switchLocation = view.findViewById<Switch>(R.id.switchLocation)
        val switchAnalytics = view.findViewById<Switch>(R.id.switchAnalytics)

        switchProfile.setOnCheckedChangeListener(null)
        switchLocation.setOnCheckedChangeListener(null)
        switchAnalytics.setOnCheckedChangeListener(null)

        switchProfile.isChecked = settingsManager.isProfilePublic()
        switchLocation.isChecked = settingsManager.isLocationEnabled()
        switchAnalytics.isChecked = settingsManager.isAnalyticsEnabled()

        switchProfile.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) return@setOnCheckedChangeListener

            settingsManager.saveProfilePublic(isChecked)
            showToast("Profile visibility updated")
        }

        switchLocation.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) return@setOnCheckedChangeListener

            settingsManager.saveLocationEnabled(isChecked)
            showToast("Location privacy updated")
        }

        switchAnalytics.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) return@setOnCheckedChangeListener

            settingsManager.saveAnalyticsEnabled(isChecked)
            showToast("Analytics preference updated")
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}