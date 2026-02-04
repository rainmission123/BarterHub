package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.databinding.DialogDailyChallengesBinding
import com.example.barterhub.databinding.DialogReferralShareBinding
import com.example.barterhub.databinding.DialogSellingTransactionBinding
import com.example.barterhub.databinding.ItemChallengeBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HowToEarnFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_how_to_earn, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Kunin ang lahat ng ACTION BUTTONS
        val btnInviteFriends = view.findViewById<MaterialButton>(R.id.btnInviteFriends)
        val btnPostItem = view.findViewById<MaterialButton>(R.id.btnPostItem)
        val btnViewChallenges = view.findViewById<MaterialButton>(R.id.btnViewChallenges)
        val btnGoBack = view.findViewById<MaterialButton>(R.id.btnGoBack)

        // ✅ IDAGDAG ITO - Games Button
        val btnOpenGames = view.findViewById<MaterialButton>(R.id.btnOpenGames)

        // Invite Friends Button - mag-share ng referral
        btnInviteFriends.setOnClickListener {
            shareReferralLink()
        }

        // Post Item Button - show message or navigate
        btnPostItem.setOnClickListener {
            showPostItemMessage()
        }

        // View Challenges Button - show daily challenges
        btnViewChallenges.setOnClickListener {
            showDailyChallengesDialog()
        }

        // ✅ IDAGDAG ITO - Games Button Click Listener
        btnOpenGames.setOnClickListener {
            navigateToGamesList()
        }

        // Go Back Button
        btnGoBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    // ✅ IDAGDAG ANG METHOD NA ITO
    private fun navigateToGamesList() {
        try {
            findNavController().navigate(R.id.action_howToEarnFragment_to_gamesListFragment)
        } catch (e: Exception) {
            // Fallback kung may error sa navigation
            showToast("Games feature coming soon!")
            e.printStackTrace()
        }
    }

    private fun shareReferralLink() {
        // Use ViewBinding for the dialog
        val binding = DialogReferralShareBinding.inflate(LayoutInflater.from(requireContext()))

        val referralCode = getUserReferralCode()
        val referralMessage = "Join me on BarterHub! 🎯\n\n" +
                "Trade items, earn coins, and get amazing deals!\n\n" +
                "Use my referral code: $referralCode\n\n" +
                "Download now: https://play.google.com/store/apps/details?id=com.example.barterhub"

        // Set data
        binding.tvReferralCode.text = referralCode
        binding.tvMessagePreview.text = referralMessage

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setCancelable(false)
            .create()

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnShareNow.setOnClickListener {
            dialog.dismiss()
            performShare(referralMessage)
        }

        dialog.show()
    }

    private fun performShare(message: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra(Intent.EXTRA_SUBJECT, "Join BarterHub and Earn Coins!")
        }

        startActivity(Intent.createChooser(shareIntent, "Invite Friends to BarterHub"))
    }

    private fun getUserReferralCode(): String {
        return "BH${System.currentTimeMillis().toString().takeLast(6)}"
    }

    private fun showPostItemMessage() {
        // Use ViewBinding for the dialog
        val binding = DialogSellingTransactionBinding.inflate(LayoutInflater.from(requireContext()))

        // Animate progress indicator
        binding.progressIndicator.progress = 70 // 70% complete

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setCancelable(false)
            .create()

        binding.btnNotifyMe.setOnClickListener {
            // Optional: Add notification subscription logic
            showNotificationSubscription()
            dialog.dismiss()
        }

        binding.btnGotIt.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showNotificationSubscription() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Get Notified")
            .setMessage("We'll notify you when the Post Item feature is ready!")
            .setPositiveButton("Subscribe") { dialog, _ ->
                // Add subscription logic here
                showToast("You'll be notified when the feature launches!")
                dialog.dismiss()
            }
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("SetTextI18n")
    private fun showDailyChallengesDialog() {
        val binding = DialogDailyChallengesBinding.inflate(LayoutInflater.from(requireContext()))

        // Setup challenges data
        val challenges = listOf(
            Challenge("Post 1 item", "+5 coins", "post_item", false),
            Challenge("Complete 2 transactions", "+10 coins", "complete_transactions", false),
            Challenge("Daily login", "+2 coins", "daily_login", true),
            Challenge("Rate a trade partner", "+1 coin", "rate_partner", false),
            Challenge("Share app with friends", "+3 coins", "share_app", false)
        )

        // Calculate total coins
        val totalCoins = challenges.sumOf {
            it.reward.replace("+", "").replace(" coins", "").trim().toIntOrNull() ?: 0
        }

        // Setup UI
        binding.rvChallenges.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChallenges.adapter = ChallengesAdapter(challenges, this)

        // Setup progress
        val completedCount = challenges.count { it.isCompleted }
        val totalCount = challenges.size
        binding.progressDaily.progress = (completedCount * 100) / totalCount
        binding.tvProgressText.text = "$completedCount/$totalCount completed"

        // CREATE CUSTOM DIALOG (not MaterialAlertDialog)
        val dialog = Dialog(requireContext())
        dialog.setContentView(binding.root)
        dialog.setCancelable(true)

        // Set dialog window properties
        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.95).toInt()
        val height = (displayMetrics.heightPixels * 0.90).toInt()

        dialog.window?.apply {
            setLayout(width, height)
            setBackgroundDrawableResource(android.R.color.transparent) // Important!
        }

        // Close button functionality - NOW IT WILL WORK!
        binding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnViewAllChallenges.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("All Challenges")
                .setMessage("All daily challenges will be available here soon!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .show()
            dialog.dismiss()
        }

        binding.btnStartChallenge.setOnClickListener {
            startFirstChallenge(challenges)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun startFirstChallenge(challenges: List<Challenge>) {
        val firstIncomplete = challenges.firstOrNull { !it.isCompleted }
        firstIncomplete?.let { challenge ->
            when (challenge.action) {
                "post_item" -> showPostItemMessage()
                "share_app" -> shareReferralLink()
                "daily_login" -> showToast("Daily login completed!")
                else -> showToast("Starting: ${challenge.title}")
            }
        } ?: showToast("All challenges completed! 🎉")
    }

    // Data class for challenges
    data class Challenge(
        val title: String,
        val reward: String,
        val action: String,
        val isCompleted: Boolean
    )

    // Adapter for challenges list
    class ChallengesAdapter(
        private val challenges: List<Challenge>,
        private val fragment: HowToEarnFragment
    ) : RecyclerView.Adapter<ChallengesAdapter.ViewHolder>() {

        class ViewHolder(private val binding: ItemChallengeBinding) : RecyclerView.ViewHolder(binding.root) {
            @SuppressLint("SetTextI18n")
            fun bind(challenge: Challenge) {
                binding.tvChallengeTitle.text = challenge.title
                binding.tvChallengeReward.text = challenge.reward

                if (challenge.isCompleted) {
                    // Hide status icon and show completed state
                    binding.ivStatus.visibility = View.GONE
                    binding.btnAction.text = "Completed"
                    binding.btnAction.isEnabled = false
                    binding.btnAction.setBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.gray_400))
                } else {
                    // Hide status icon and show start state
                    binding.ivStatus.visibility = View.GONE
                    binding.btnAction.text = "Start"
                    binding.btnAction.isEnabled = true
                    binding.btnAction.setBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.premium_gold))
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemChallengeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val challenge = challenges[position]
            holder.bind(challenge)

            holder.itemView.findViewById<MaterialButton>(R.id.btnAction).setOnClickListener {
                onChallengeStart(challenge, holder.itemView.context)
            }
        }

        override fun getItemCount() = challenges.size

        private fun onChallengeStart(challenge: Challenge, context: android.content.Context) {
            when (challenge.action) {
                "post_item" -> {
                    MaterialAlertDialogBuilder(context)
                        .setTitle("Post Item")
                        .setMessage("You can start by posting your first item for trade!")
                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                        .show()
                }
                "share_app" -> {
                    fragment.shareReferralLink()
                }
                "daily_login" -> {
                    Toast.makeText(context, "Daily login completed!", Toast.LENGTH_SHORT).show()
                }
                "complete_transactions" -> {
                    Toast.makeText(context, "Complete transactions to earn coins!", Toast.LENGTH_SHORT).show()
                }
                "rate_partner" -> {
                    Toast.makeText(context, "Rate your trade partners after successful trades!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}