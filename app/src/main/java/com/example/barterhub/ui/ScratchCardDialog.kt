package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.barterhub.R
import com.example.barterhub.views.ScratchView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ScratchCardDialog(context: Context) : Dialog(context) {

    companion object {
        private const val SCRATCH_COST = 15
        private const val TAG = "ScratchCardDialog"
    }

    private lateinit var scratchView: ScratchView
    private lateinit var prizeLayout: LinearLayout
    private lateinit var tvPrizeAmount: TextView
    private lateinit var tvPrizeDescription: TextView
    private lateinit var tvResult: TextView
    private lateinit var tvScratchProgress: TextView
    private lateinit var btnScratch: MaterialButton

    private var prizeCoins = 0
    private var isCardBought = false
    private var isPrizeRevealed = false

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_scratch_card)

        Log.d(TAG, "ScratchCardDialog created")

        val btnClose = findViewById<ImageView>(R.id.btnCloseScratchCard)
        btnScratch = findViewById(R.id.btnScratch)
        tvResult = findViewById(R.id.tvScratchResult)
        scratchView = findViewById(R.id.scratchView)
        prizeLayout = findViewById(R.id.prizeLayout)
        tvPrizeAmount = findViewById(R.id.tvPrizeAmount)
        tvPrizeDescription = findViewById(R.id.tvPrizeDescription)
        tvScratchProgress = findViewById(R.id.tvScratchProgress)

        // ✅ CRITICAL FIX: Ensure prize layout is VISIBLE from start
        prizeLayout.visibility = View.VISIBLE
        prizeLayout.setBackgroundColor(Color.parseColor("#FFD700")) // premium_gold color

        Log.d(TAG, "Prize layout visibility: ${prizeLayout.visibility}")

        btnClose.setOnClickListener {
            Log.d(TAG, "Close button clicked")
            dismiss()
        }

        setupScratchCard()

        btnScratch.setOnClickListener {
            Log.d(TAG, "Scratch button clicked, isCardBought: $isCardBought")
            if (!isCardBought) {
                startScratchCard()
            } else {
                resetScratchCard()
            }
        }

        // Set initial state
        resetToInitialState()
    }

    private fun resetToInitialState() {
        tvPrizeAmount.text = "?"
        tvPrizeDescription.text = "Buy scratch card to start!"
        tvScratchProgress.text = "Scratch 0% to reveal prize"
        tvResult.text = "Buy scratch card to start!"
        btnScratch.text = "Buy Scratch Card - 15 coins"
        btnScratch.isEnabled = true

        // ✅ CRITICAL: Make sure prize layout is always visible
        prizeLayout.visibility = View.VISIBLE
    }

    @SuppressLint("SetTextI18n")
    private fun setupScratchCard() {
        Log.d(TAG, "Setting up scratch card listener")

        scratchView.onScratchListener = object : ScratchView.OnScratchListener {
            override fun onScratchStarted() {
                Log.d(TAG, "Scratch started")
                tvPrizeDescription.text = "Keep scratching..."
            }

            override fun onScratchProgress(progress: Float) {
                val progressPercent = (progress * 100).toInt()
                Log.d(TAG, "Scratch progress: $progressPercent%")
                tvScratchProgress.text = "Scratch $progressPercent% to reveal prize"

                // ✅ SIMPLIFIED PROGRESS - MAS MADALING MAKITA
                when {
                    progressPercent < 15 -> {
                        tvPrizeAmount.text = "?"
                        tvPrizeDescription.text = "Scratch more..."
                    }
                    progressPercent in 15..39 -> {
                        tvPrizeAmount.text = "🎯"
                        tvPrizeDescription.text = "Getting warmer..."
                    }
                    progressPercent in 40..69 -> {
                        // Show actual partial amount
                        val partialAmount = prizeCoins
                        tvPrizeAmount.text = "$partialAmount"
                        tvPrizeDescription.text = "You won!"
                        tvPrizeAmount.setTextColor(Color.RED) // Highlight
                    }
                    progressPercent >= 70 -> {
                        // Show full prize with celebration
                        tvPrizeAmount.text = "$prizeCoins"
                        tvPrizeDescription.text = "🎉 CONGRATULATIONS! 🎉"
                        tvPrizeAmount.setTextColor(Color.GREEN) // Celebration color

                        if (!isPrizeRevealed) {
                            isPrizeRevealed = true
                            onPrizeRevealed()
                        }
                    }
                }
            }

            override fun onScratchComplete() {
                Log.d(TAG, "Scratch complete triggered")
                if (!isPrizeRevealed) {
                    tvPrizeAmount.text = "$prizeCoins"
                    tvPrizeDescription.text = "🎉 PRIZE REVEALED! 🎉"
                    onPrizeRevealed()
                }
            }
        }

        // Initially disable scratching until payment
        scratchView.setScratchEnabled(false)
    }

    private fun onPrizeRevealed() {
        Log.d(TAG, "Prize revealed: $prizeCoins coins")
        isPrizeRevealed = true
        tvResult.text = "🎉 You won $prizeCoins coins! Added to your wallet."
        btnScratch.text = "Play Again"
        btnScratch.isEnabled = true

        // Update user coins
        updateUserCoins(prizeCoins)
    }

    @SuppressLint("SetTextI18n")
    private fun startScratchCard() {
        Log.d(TAG, "Starting scratch card purchase")
        val userId = auth.currentUser?.uid
        if (userId == null) {
            tvResult.text = "Please login first!"
            return
        }

        btnScratch.isEnabled = false
        btnScratch.text = "Processing..."

        val userRef = database.getReference("users").child(userId)

        userRef.get().addOnSuccessListener { snapshot ->
            val currentCoins = snapshot.child("coins").getValue(Int::class.java) ?: 0
            Log.d(TAG, "Current coins: $currentCoins, required: $SCRATCH_COST")

            if (currentCoins < SCRATCH_COST) {
                tvResult.text = "❌ Not enough coins! Need $SCRATCH_COST coins"
                btnScratch.isEnabled = true
                btnScratch.text = "Buy Scratch Card - 15 coins"
                return@addOnSuccessListener
            }

            // Deduct coins
            val coinsAfterCost = currentCoins - SCRATCH_COST
            userRef.child("coins").setValue(coinsAfterCost)
                .addOnSuccessListener {
                    // Generate random prize
                    prizeCoins = generatePrize()
                    Log.d(TAG, "🎯 PRIZE GENERATED: $prizeCoins coins")

                    // ✅ ENABLE SCRATCHING AND SETUP PRIZE DISPLAY
                    scratchView.setScratchEnabled(true)
                    scratchView.resetScratch()

                    // ✅ CRITICAL: UPDATE PRIZE DISPLAY IMMEDIATELY
                    tvPrizeAmount.text = "?"
                    tvPrizeAmount.setTextColor(Color.BLACK) // Reset color
                    tvPrizeDescription.text = "Scratch to reveal $prizeCoins coins!"
                    tvScratchProgress.text = "Scratch 0% - Prize: ??? coins"

                    // ✅ MAKE SURE PRIZE LAYOUT IS VISIBLE
                    prizeLayout.visibility = View.VISIBLE
                    prizeLayout.invalidate() // Force redraw

                    tvResult.text = "✅ Paid! Scratch to reveal your prize!"
                    btnScratch.text = "Scratch Now!"
                    isCardBought = true
                    isPrizeRevealed = false

                    // Record transaction for cost
                    recordTransaction(-SCRATCH_COST, "Scratch card cost")

                    Log.d(TAG, "🎯 Scratch card READY! Prize: $prizeCoins coins")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error processing payment", e)
                    tvResult.text = "❌ Error processing payment"
                    resetScratchCard()
                }

        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to access wallet", e)
            tvResult.text = "❌ Failed to access wallet"
            resetScratchCard()
        }
    }

    private fun generatePrize(): Int {
        // CONTROLLED prize probability
        val random = Math.random()
        val prize = when {
            random < 0.6 -> (5..8).random()        // 60% chance: 5-8 coins (small)
            random < 0.85 -> (9..15).random()      // 25% chance: 9-15 coins (medium)
            random < 0.97 -> (16..25).random()     // 12% chance: 16-25 coins (big)
            else -> (26..35).random()              // 3% chance: 26-35 coins (jackpot)
        }
        Log.d(TAG, "🎲 Controlled prize: $prize coins (random: $random)")
        return prize
    }


    private fun updateUserCoins(prize: Int) {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.getReference("users").child(userId)

        userRef.child("coins").get().addOnSuccessListener { snapshot ->
            val currentCoins = snapshot.getValue(Int::class.java) ?: 0
            val newBalance = currentCoins + prize
            Log.d(TAG, "💰 Updating coins: $currentCoins + $prize = $newBalance")

            userRef.child("coins").setValue(newBalance)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Coins updated successfully")
                    recordTransaction(prize, "Scratch card win: $prize coins")

                    // ✅ UPDATE RESULT TEXT WITH NEW BALANCE
                    tvResult.text = "🎉 You won $prizeCoins coins! Total: $newBalance coins"
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to update coins", e)
                    tvResult.text = "🎉 You won $prizeCoins coins! (Wallet update failed)"
                }
        }
    }

    private fun recordTransaction(coins: Int, description: String) {
        val userId = auth.currentUser?.uid ?: return

        val transactionData = hashMapOf(
            "userId" to userId,
            "type" to "scratch_card",
            "coins" to coins,
            "description" to description,
            "amount" to (coins * 0.50),
            "status" to "completed",
            "createdAt" to System.currentTimeMillis()
        )

        database.getReference("transactions").push().setValue(transactionData)
            .addOnSuccessListener {
                Log.d(TAG, "📝 Transaction recorded: $description")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to record transaction", e)
            }
    }

    @SuppressLint("SetTextI18n")
    private fun resetScratchCard() {
        Log.d(TAG, "Resetting scratch card")
        isCardBought = false
        isPrizeRevealed = false
        prizeCoins = 0

        // Disable scratching
        scratchView.setScratchEnabled(false)
        scratchView.resetScratch()

        // Reset UI
        tvPrizeAmount.text = "?"
        tvPrizeAmount.setTextColor(Color.BLACK)
        tvPrizeDescription.text = "Buy scratch card to start!"
        tvScratchProgress.text = "Scratch 0% to reveal prize"
        tvResult.text = "Ready to play!"
        btnScratch.text = "Buy Scratch Card - 15 coins"
        btnScratch.isEnabled = true

        // ✅ CRITICAL: Keep prize layout visible
        prizeLayout.visibility = View.VISIBLE

        Log.d(TAG, "Scratch card reset complete")
    }

    override fun dismiss() {
        Log.d(TAG, "Dialog dismissed")
        super.dismiss()
    }
}