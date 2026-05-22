package com.example.barterhub.ui.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.barterhub.R
import com.example.barterhub.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var preferences: SharedPreferences
    private lateinit var auth: FirebaseAuth
    private lateinit var settingsManager: SettingsManager
    private lateinit var privacyManager: PrivacyManager
    private lateinit var storageManager: StorageManager
    private lateinit var securityManager: SecurityManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        preferences = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        settingsManager = SettingsManager(preferences)
        privacyManager = PrivacyManager(requireContext(), settingsManager)
        storageManager = StorageManager(requireContext())
        securityManager = SecurityManager(requireContext(), auth)

        setupDarkModeSwitch()
        setupPrivacySettings()
        setupClickListeners()
    }

    private fun setupDarkModeSwitch() {
        val savedDarkMode = settingsManager.isDarkModeEnabled()

        binding.switchDarkMode.setOnCheckedChangeListener(null)
        binding.switchDarkMode.isChecked = savedDarkMode

        binding.switchDarkMode.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) return@setOnCheckedChangeListener

            settingsManager.saveDarkMode(isChecked)

            val mode = if (isChecked) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }

            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    private fun setupPrivacySettings() {
        binding.privacySettings.setOnClickListener {
            privacyManager.showPrivacyBottomSheet()
        }

        val savedLocationState = settingsManager.isLocationEnabled()

        binding.locationSwitch.setOnCheckedChangeListener(null)
        binding.locationSwitch.isChecked = savedLocationState

        binding.locationSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) return@setOnCheckedChangeListener

            settingsManager.saveLocationEnabled(isChecked)

            if (isChecked) {
                showToast("Location sharing enabled")
            } else {
                showToast("Location sharing disabled")
            }
        }

        binding.locationSettings.setOnClickListener {
            binding.locationSwitch.isChecked = !binding.locationSwitch.isChecked
        }

        binding.dataStorage.setOnClickListener {
            storageManager.showDataStorageBottomSheet {
                clearAppCache()
            }
        }
    }

    private fun setupClickListeners() {
        binding.changePassword.setOnClickListener {
            securityManager.showChangePassword()
        }

        binding.securitySettings.setOnClickListener {
            securityManager.showSecurityBottomSheet {
                openAppSettings()
            }
        }

        binding.helpSupportSection.setOnClickListener {
            navigateSafely(R.id.nav_help_support, "Help & Support page is not available yet.")
        }

        binding.aboutAppSection.setOnClickListener {
            navigateSafely(R.id.nav_about, "About page is not available yet.")
        }
    }

    private fun clearAppCache() {
        try {
            val deleted = requireContext().cacheDir.deleteRecursively()

            if (deleted) {
                showToast("Cache cleared successfully.")
            } else {
                showToast("Cache already clean.")
            }
        } catch (e: Exception) {
            showError("Failed to clear cache: ${e.message}")
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${requireContext().packageName}")
            )
            startActivity(intent)
        } catch (e: Exception) {
            showError("Unable to open app settings.")
        }
    }

    private fun navigateSafely(destinationId: Int, fallbackMessage: String) {
        try {
            findNavController().navigate(destinationId)
        } catch (e: Exception) {
            showToast(fallbackMessage)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}