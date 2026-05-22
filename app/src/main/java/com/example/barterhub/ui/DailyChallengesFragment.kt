package com.example.barterhub.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barterhub.R
import com.example.barterhub.databinding.FragmentDailyChallengesBinding
import com.example.barterhub.ui.earn.Challenge
import com.example.barterhub.ui.earn.ChallengesAdapter
import com.example.barterhub.ui.earn.DailyChallengesManager
import com.example.barterhub.ui.profile.ProfilePremiumManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class DailyChallengesFragment : Fragment() {

    private var _binding: FragmentDailyChallengesBinding? = null
    private val binding get() = _binding!!

    private val manager = DailyChallengesManager()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDailyChallengesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide UI first while checking premium status
        binding.root.visibility = View.INVISIBLE

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        checkPremiumThenLoad()
    }

    private fun checkPremiumThenLoad() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid == null) {
            findNavController().popBackStack()
            return
        }

        FirebaseDatabase.getInstance().reference
            .child("users")
            .child(uid)
            .get()
            .addOnSuccessListener { snap ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                val isPremium = snap.child("isPremium").getValue(Boolean::class.java) ?: false
                val expiry = snap.child("premiumExpiry").getValue(Long::class.java) ?: 0L
                val premiumActive = isPremium && expiry > System.currentTimeMillis()

                if (premiumActive) {
                    binding.root.visibility = View.VISIBLE
                    loadChallenges()
                } else {
                    findNavController().popBackStack()
                    ProfilePremiumManager(this).showPremiumDirect()
                }
            }
            .addOnFailureListener {
                if (!isAdded || _binding == null) return@addOnFailureListener

                findNavController().popBackStack()
                ProfilePremiumManager(this).showPremiumDirect()
            }
    }

    private fun loadChallenges() {
        if (!isAdded || _binding == null) return

        binding.rvChallenges.layoutManager = LinearLayoutManager(requireContext())

        manager.ensureTodayChallenges {
            manager.loadChallenges { challenges ->
                if (!isAdded || _binding == null) return@loadChallenges

                val completedCount = challenges.count { it.isCompleted }
                val totalCount = challenges.size
                val progressPercent = if (totalCount > 0) {
                    (completedCount * 100) / totalCount
                } else {
                    0
                }

                binding.progressDaily.max = 100
                binding.progressDaily.progress = progressPercent
                binding.tvProgressText.text = "$completedCount/$totalCount completed"

                binding.rvChallenges.adapter = ChallengesAdapter(challenges) { challenge ->
                    when {
                        challenge.isCompleted && !challenge.rewarded -> {
                            claimReward(challenge)
                        }

                        !challenge.isCompleted -> {
                            handleStartChallenge(challenge)
                        }
                    }
                }
            }
        }
    }

    private fun claimReward(challenge: Challenge) {
        if (!isAdded || _binding == null) return

        binding.rvChallenges.isEnabled = false

        manager.awardChallengeIfNeeded(
            action = challenge.action,
            onRewardEarned = { coins ->
                if (!isAdded || _binding == null) return@awardChallengeIfNeeded

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Reward Claimed! 🎉")
                    .setMessage("You earned +$coins coins!")
                    .setPositiveButton("Awesome") { d, _ -> d.dismiss() }
                    .show()
            },
            onComplete = {
                if (!isAdded || _binding == null) return@awardChallengeIfNeeded

                binding.rvChallenges.isEnabled = true
                loadChallenges()
            }
        )
    }

    private fun handleStartChallenge(challenge: Challenge) {
        if (!isAdded || _binding == null) return

        when (challenge.action) {
            "post_item" -> {
                findNavController().navigate(R.id.addPhotosFragment)
            }

            else -> {
                Toast.makeText(
                    requireContext(),
                    "Challenge not available yet.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}