package com.example.barterhub.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.example.barterhub.R
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment() {

    private lateinit var preferences: SharedPreferences
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        auth = FirebaseAuth.getInstance()
        preferences = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)


        val switchNotifications = view.findViewById<SwitchMaterial>(R.id.switchNotifications)
        switchNotifications.isChecked = preferences.getBoolean("notifications", true)
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("notifications", isChecked).apply()
        }

        return view
    }
}
