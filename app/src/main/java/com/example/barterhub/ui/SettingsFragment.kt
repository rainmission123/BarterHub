package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        preferences = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        setupClickListeners()
        setupPrivacySettings()
        setupDarkModeSwitch()
    }

    private fun setupDarkModeSwitch() {
        // Set initial state
        val isDarkMode = preferences.getBoolean("dark_mode", false)
        binding.switchDarkMode.isChecked = isDarkMode

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            // Save preference
            preferences.edit().putBoolean("dark_mode", isChecked).apply()

            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )

            showMessage(if (isChecked) "Dark mode enabled 🌙" else "Light mode enabled ☀️")

            requireActivity().recreate()
        }
    }

    private fun setupClickListeners() {
        binding.helpSupportSection.setOnClickListener {
            findNavController().navigate(R.id.nav_help_support)
        }

        binding.aboutAppSection.setOnClickListener {
            findNavController().navigate(R.id.nav_about)
        }

        // ✅ Change Password
        binding.changePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        // ✅ Security Settings
        binding.securitySettings.setOnClickListener {
            showSecuritySettingsDialog()
        }
    }

    private fun setupPrivacySettings() {
        // ✅ Privacy Settings
        binding.privacySettings.setOnClickListener {
            showPrivacySettingsDialog()
        }

        // ✅ Location Settings Switch
        binding.locationSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Request location permission or enable location
                enableLocationServices()
            } else {
                // Disable location services
                disableLocationServices()
            }
            // Save preference
            preferences.edit().putBoolean("location_enabled", isChecked).apply()
        }

        // ✅ Data & Storage
        binding.dataStorage.setOnClickListener {
            showDataStorageDialog()
        }

        // Set initial switch state
        binding.locationSwitch.isChecked = preferences.getBoolean("location_enabled", true)
    }

    private fun showChangePasswordDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Password")
            .setMessage("Password change feature will be available soon. For now, you can reset your password via email.")
            .setPositiveButton("OK", null)
            .setNeutralButton("Reset via Email") { dialog, which ->
                // TODO: Implement password reset via email
                showMessage("Password reset email feature coming soon!")
            }
            .show()
    }

    private fun showSecuritySettingsDialog() {
        val securityTips = """
            🔒 **Security Settings**
            
            **Two-Factor Authentication:**
            • Coming soon for enhanced security
            
            **Login Notifications:**
            • Get alerts for new logins
            • Monitor account activity
            
            **Session Management:**
            • View active sessions
            • Log out from other devices
            
            **Security Recommendations:**
            • Use strong, unique password
            • Enable biometric authentication
            • Regularly review account activity
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Security Settings")
            .setMessage(securityTips)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPrivacySettingsDialog() {
        val privacyOptions = """
            🛡️ **Privacy Settings**
            
            **Profile Visibility:**
            • Control who sees your profile
            • Choose what information is public
            
            **Trade History:**
            • Hide specific trades
            • Control trade history visibility
            
            **Communication:**
            • Manage message preferences
            • Block unwanted users
            
            **Data Sharing:**
            • Control analytics data
            • Manage third-party sharing
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Privacy Settings")
            .setMessage(privacyOptions)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showDataStorageDialog() {
        val storageInfo = """
            💾 **Data & Storage**
            
            **Cache Management:**
            • Clear app cache: 15.2 MB
            • Clear temporary files
            
            **Storage Usage:**
            • Photos: 8.3 MB
            • Messages: 2.1 MB
            • App Data: 4.8 MB
            
            **Data Saving:**
            • Compress images
            • Limit background data
            • Auto-delete old messages
            
            **Backup:**
            • Cloud backup coming soon
            • Export your data
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Data & Storage")
            .setMessage(storageInfo)
            .setPositiveButton("Clear Cache") { dialog, which ->
                clearAppCache()
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun enableLocationServices() {
        showMessage("Location services enabled")
        // TODO: Implement location permission request
        // requestLocationPermission()
    }

    private fun disableLocationServices() {
        showMessage("Location services disabled")
        // TODO: Implement location services disable
    }

    private fun clearAppCache() {
        // Simple cache clearing simulation
        showMessage("Cache cleared successfully")
        // TODO: Implement actual cache clearing
    }

    private fun showMessage(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}