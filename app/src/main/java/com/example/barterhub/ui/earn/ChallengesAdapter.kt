package com.example.barterhub.ui.earn

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.databinding.ItemChallengeBinding

class ChallengesAdapter(
    private val challenges: List<Challenge>,
    private val onChallengeClick: (Challenge) -> Unit
) : RecyclerView.Adapter<ChallengesAdapter.ViewHolder>() {

    class ViewHolder(private val binding: ItemChallengeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(challenge: Challenge, onChallengeClick: (Challenge) -> Unit) {
            binding.tvChallengeTitle.text = challenge.title
            binding.tvChallengeReward.text = challenge.reward

            binding.ivStatus.visibility = View.GONE

            if (challenge.isCompleted && !challenge.rewarded) {
                binding.btnAction.text = "Claim"
                binding.btnAction.isEnabled = true
                binding.btnAction.setBackgroundColor(
                    ContextCompat.getColor(binding.root.context, R.color.premium_gold)
                )
            } else if (challenge.rewarded) {
                binding.btnAction.text = "Claimed"
                binding.btnAction.isEnabled = false
                binding.btnAction.setBackgroundColor(
                    ContextCompat.getColor(binding.root.context, R.color.gray_400)
                )
            } else {
                binding.btnAction.text = "Start"
                binding.btnAction.isEnabled = true
                binding.btnAction.setBackgroundColor(
                    ContextCompat.getColor(binding.root.context, R.color.premium_gold)
                )
            }

            binding.btnAction.setOnClickListener {
                onChallengeClick(challenge)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChallengeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(challenges[position], onChallengeClick)
    }

    override fun getItemCount(): Int = challenges.size
}