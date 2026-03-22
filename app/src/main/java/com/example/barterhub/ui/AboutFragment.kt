package com.example.barterhub.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.barterhub.databinding.FragmentAboutBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.net.toUri

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Contact Email
        binding.contactEmail.setOnClickListener {
            openEmailSupport()
        }

        // Privacy Policy - USING DIALOG FRAGMENT
        binding.privacyPolicy.setOnClickListener {
            val dialog = PrivacyPolicyFragment()
            dialog.show(parentFragmentManager, PrivacyPolicyFragment.TAG)
        }

        // Terms of Service - USING DIALOG FRAGMENT
        binding.termsOfService.setOnClickListener {
            val dialog = TermsFragment()
            dialog.show(parentFragmentManager, TermsFragment.TAG)
        }
    }

    private fun openEmailSupport() {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:barterhubph.team@gmail.com".toUri()
            putExtra(Intent.EXTRA_SUBJECT, "BarterHub Ph Feedback")
        }

        try {
            startActivity(Intent.createChooser(emailIntent, "Send email using..."))
        } catch (e: Exception) {
            showMessage("No email app found. Please email us at: barterhubph.team@gmail.com")
        }
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