package com.example.barterhub.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.barterhub.R
import com.example.barterhub.databinding.FragmentHowToEarnBinding
import com.example.barterhub.ui.earn.DailyChallengesManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.example.barterhub.ui.profile.ProfilePremiumManager

class HowToEarnFragment : Fragment() {

    private var _binding: FragmentHowToEarnBinding? = null
    private val binding get() = _binding!!

    private val challengesManager = DailyChallengesManager()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHowToEarnBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        challengesManager.ensureTodayChallenges()
        setupClickListeners()
    }

    private fun setupClickListeners() = with(binding) {
        btnInviteFriends.setOnClickListener {
            findNavController().navigate(
                R.id.action_howToEarnFragment_to_referralShareFragment
            )
        }

        btnPostItem.setOnClickListener {
            findNavController().navigate(R.id.action_howToEarnFragment_to_addPhotosFragment)
        }

        btnViewChallenges.setOnClickListener {
            checkPremiumThenOpenChallenges()
        }

        btnOpenGames.setOnClickListener {
            navigateToGamesList()
        }

        btnGoBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun navigateToGamesList() {
        try {
            findNavController().navigate(R.id.action_howToEarnFragment_to_gamesListFragment)
        } catch (e: Exception) {
            toast("Games feature coming soon!")
            e.printStackTrace()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun checkPremiumThenOpenChallenges() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseDatabase.getInstance().reference
            .child("users")
            .child(uid)
            .get()
            .addOnSuccessListener { snap ->

                val isPremium = snap.child("isPremium").getValue(Boolean::class.java) ?: false
                val expiry = snap.child("premiumExpiry").getValue(Long::class.java) ?: 0L
                val premiumActive = isPremium && expiry > System.currentTimeMillis()

                if (premiumActive) {
                    // ✅ Premium → open challenges
                    findNavController().navigate(R.id.dailyChallengesFragment)
                } else {
                    // 🔒 Non-premium → open premium bottom sheet
                    ProfilePremiumManager(this).showPremiumDirect()
                }
            }
    }
}