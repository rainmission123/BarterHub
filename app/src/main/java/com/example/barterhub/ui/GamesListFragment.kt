package com.example.barterhub.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.barterhub.databinding.FragmentGamesListBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class GamesListFragment : Fragment() {

    private var _binding: FragmentGamesListBinding? = null
    private val binding get() = _binding!!
    private lateinit var luckySpinManager: LuckySpinManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGamesListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("GamesListFragment", "onViewCreated called")

        // Initialize LuckySpinManager
        luckySpinManager = LuckySpinManager()

        setupGameClickListeners()
        setupButtonClickListeners()

        // Test if views are properly bound
        testViewBinding()
    }

    private fun testViewBinding() {
        try {
            Log.d("GamesListFragment", "cardLuckySpin is null: ${binding.cardLuckySpin == null}")
            Log.d("GamesListFragment", "cardCoinFlip is null: ${binding.cardCoinFlip == null}")
            Log.d("GamesListFragment", "cardScratchCard is null: ${binding.cardScratchCard == null}")
        } catch (e: Exception) {
            Log.e("GamesListFragment", "Error testing view binding: ${e.message}")
        }
    }

    private fun setupGameClickListeners() {
        // Card clicks
        binding.cardLuckySpin.setOnClickListener {
            Log.d("GamesListFragment", "Lucky Spin card CLICKED!")
            showLuckySpinDialog()
        }

        binding.cardCoinFlip.setOnClickListener {
            Log.d("GamesListFragment", "Coin Flip card CLICKED!")
            showCoinFlipDialog()
        }

        binding.cardScratchCard.setOnClickListener {
            Log.d("GamesListFragment", "Scratch Card card CLICKED!")
            showScratchCardDialog()
        }

        Log.d("GamesListFragment", "All card click listeners set up")
    }

    private fun setupButtonClickListeners() {
        // Play buttons
        binding.btnPlayLuckySpin.setOnClickListener {
            Log.d("GamesListFragment", "Lucky Spin PLAY button clicked")
            showLuckySpinDialog()
        }

        binding.btnPlayCoinFlip.setOnClickListener {
            Log.d("GamesListFragment", "Coin Flip PLAY button clicked")
            showCoinFlipDialog()
        }

        binding.btnPlayScratchCard.setOnClickListener {
            Log.d("GamesListFragment", "Scratch Card PLAY button clicked")
            showScratchCardDialog()
        }

        Log.d("GamesListFragment", "All button click listeners set up")
    }

    private fun showLuckySpinDialog() {
        Log.d("GamesListFragment", "showLuckySpinDialog called")

        val auth = FirebaseAuth.getInstance()
        val database = FirebaseDatabase.getInstance()
        val currentUser = auth.currentUser

        if (currentUser != null) {
            // ✅ UPDATE: Load current coins from Firebase
            database.getReference("users").child(currentUser.uid).child("coins")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val coins = snapshot.getValue(Int::class.java) ?: 0

                        val dialog = LuckySpinDialog(
                            context = requireContext(),
                            userCoins = coins.toDouble()
                        )

                        // ✅ UPDATE: Set listener for real-time sync
                        dialog.setOnCoinsUpdateListener { updatedCoins: Double ->
                            // Auto-save to Firebase (handled by LuckySpinDialog)
                            Log.d("GamesListFragment", "Coins updated to: $updatedCoins")
                        }

                        dialog.show()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        showErrorMessage("Failed to load coins from wallet")
                    }
                })
        } else {
            showErrorMessage("Please login first")
        }
    }

    private fun showCoinFlipDialog() {
        Log.d("GamesListFragment", "showCoinFlipDialog called")
        try {
            val coinFlipDialog = CoinFlipDialog(requireContext())
            coinFlipDialog.show()
            Log.d("GamesListFragment", "CoinFlipDialog shown successfully")
        } catch (e: Exception) {
            Log.e("GamesListFragment", "Failed to open Coin Flip: ${e.message}", e)
            showErrorMessage("Failed to open Coin Flip: ${e.message}")
        }
    }

    private fun showScratchCardDialog() {
        Log.d("GamesListFragment", "showScratchCardDialog called")
        try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                val scratchCardDialog = ScratchCardDialog(requireContext())
                scratchCardDialog.show()
                Log.d("GamesListFragment", "ScratchCardDialog shown successfully")
            } else {
                showErrorMessage("Please login first")
            }
        } catch (e: Exception) {
            Log.e("GamesListFragment", "Failed to open Scratch Card: ${e.message}", e)
            showErrorMessage("Failed to open Scratch Card: ${e.message}")
        }
    }


    private fun getUserCoins(onResult: (Int) -> Unit) {
        onResult(50) // placeholder for Firebase coins
    }

    private fun showRewardMessage(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_LONG).show()
    }

    private fun showErrorMessage(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
