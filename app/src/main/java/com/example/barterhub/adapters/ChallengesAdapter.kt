package com.example.barterhub.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.ui.HowToEarnFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ChallengesAdapter(
    private val challenges: List<HowToEarnFragment.Challenge>,
    private val fragment: HowToEarnFragment
) : RecyclerView.Adapter<ChallengesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivStatus: ImageView = view.findViewById(R.id.ivStatus)
        val tvChallengeTitle: TextView = view.findViewById(R.id.tvChallengeTitle)
        val tvChallengeReward: TextView = view.findViewById(R.id.tvChallengeReward)
        val btnAction: MaterialButton = view.findViewById(R.id.btnAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_challenge, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val challenge = challenges[position]

        holder.tvChallengeTitle.text = challenge.title
        holder.tvChallengeReward.text = challenge.reward

        if (challenge.isCompleted) {
            // Use text or color instead of missing icon
            holder.ivStatus.visibility = View.GONE
            holder.btnAction.text = "Completed"
            holder.btnAction.isEnabled = false
            holder.btnAction.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.gray_400))
        } else {
            // Use text or color instead of missing icon
            holder.ivStatus.visibility = View.GONE
            holder.btnAction.text = "Start"
            holder.btnAction.isEnabled = true
            holder.btnAction.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.premium_gold))
        }

        holder.btnAction.setOnClickListener {
            // Handle challenge start
            onChallengeStart(challenge, holder.itemView.context)
        }
    }

    override fun getItemCount() = challenges.size

    private fun onChallengeStart(challenge: HowToEarnFragment.Challenge, context: Context) {
        // Handle challenge start based on action type
        when (challenge.action) {
            "post_item" -> {
                // Show post item message
                MaterialAlertDialogBuilder(context)
                    .setTitle("Post Item")
                    .setMessage("You can start by posting your first item for trade!")
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
            "share_app" -> {
                // Direct share implementation here instead of calling fragment method
                shareReferralLinkDirectly(context)
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

    private fun shareReferralLinkDirectly(context: Context) {
        val referralCode = "BH${System.currentTimeMillis().toString().takeLast(6)}"
        val referralMessage = "Join me on BarterHub! 🎯\n\n" +
                "Trade items, earn coins, and get amazing deals!\n\n" +
                "Use my referral code: $referralCode\n\n" +
                "Download now: https://play.google.com/store/apps/details?id=com.example.barterhub"

        MaterialAlertDialogBuilder(context)
            .setTitle("Invite Friends & Earn Coins!")
            .setMessage("Share this referral code with your friends:\n\n" +
                    "🔑 $referralCode\n\n" +
                    "You'll earn coins when they join and complete their first trade!")
            .setPositiveButton("Share Now") { dialog, _ ->
                performShareDirectly(referralMessage, context)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performShareDirectly(message: String, context: Context) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra(Intent.EXTRA_SUBJECT, "Join BarterHub and Earn Coins!")
        }

        context.startActivity(Intent.createChooser(shareIntent, "Invite Friends to BarterHub"))
    }
}