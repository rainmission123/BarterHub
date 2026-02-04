package com.example.barterhub.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.barterhub.R
import com.example.barterhub.databinding.FragmentHelpSupportBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.net.toUri

class HelpSupportFragment : Fragment() {

    private var _binding: FragmentHelpSupportBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHelpSupportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Email Support
        binding.contactEmail.setOnClickListener {
            openEmailSupport()
        }

        // FAQs
        binding.openFaqs.setOnClickListener {
            openFaqs()
        }

        // Community Guidelines
        binding.communityGuidelines.setOnClickListener {
            openCommunityGuidelines()
        }

        // Quick Help items - NOW USING XML LAYOUTS
        binding.quickHelpBarter.setOnClickListener {
            showCustomHelpDialog(R.layout.dialog_help_barter)
        }

        binding.quickHelpPods.setOnClickListener {
            showCustomHelpDialog(R.layout.dialog_help_pods)
        }

        binding.quickHelpSafety.setOnClickListener {
            showCustomHelpDialog(R.layout.dialog_help_safety)
        }

        binding.quickHelpTrade.setOnClickListener {
            showCustomHelpDialog(R.layout.dialog_help_trade)
        }

        binding.quickHelpAccount.setOnClickListener {
            showCustomHelpDialog(R.layout.dialog_help_account)
        }
    }

    // NEW METHOD FOR CUSTOM DIALOGS
    private fun showCustomHelpDialog(layoutRes: Int) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(layoutRes, null)

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun openEmailSupport() {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:barterhubph.team@gmail.com".toUri()
            putExtra(Intent.EXTRA_SUBJECT, "BarterHub Ph Support")
            putExtra(Intent.EXTRA_TEXT, """
                Hello BarterHub Team,

                I need help with:

                [Please describe your issue here]

                Thank you!
            """.trimIndent())
        }

        try {
            startActivity(Intent.createChooser(emailIntent, "Send email using..."))
        } catch (e: Exception) {
            showErrorDialog("No email app found. Please email us at: barterhubph.team@gmail.com")
        }
    }

    private fun openFaqs() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.faqs))
            .setMessage(getString(R.string.faqs_coming_soon_message))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openCommunityGuidelines() {
        val guidelines = """
            • Be honest about your items' condition
            • Communicate clearly and respectfully
            • Meet in safe, public locations
            • Only trade items you legally own
            • No prohibited items (weapons, illegal substances, etc.)
            • Respect other users' time and preferences
            • Rate your trading partners fairly

            Violations may result in account suspension.
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.community_guidelines))
            .setMessage(guidelines)
            .setPositiveButton("I Understand", null)
            .show()
    }

    private fun showErrorDialog(message: String) {
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