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
import com.google.firebase.database.*

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

        val btnClose = findViewById<ImageView>(R.id.btnCloseScratchCard)
        btnScratch = findViewById(R.id.btnScratch)
        tvResult = findViewById(R.id.tvScratchResult)
        scratchView = findViewById(R.id.scratchView)
        prizeLayout = findViewById(R.id.prizeLayout)
        tvPrizeAmount = findViewById(R.id.tvPrizeAmount)
        tvPrizeDescription = findViewById(R.id.tvPrizeDescription)
        tvScratchProgress = findViewById(R.id.tvScratchProgress)

        prizeLayout.visibility = View.VISIBLE
        prizeLayout.setBackgroundColor(Color.parseColor("#FFD700"))

        btnClose.setOnClickListener { dismiss() }

        setupScratchCard()

        btnScratch.setOnClickListener {
            if (!isCardBought) {
                startScratchCard()
            } else {
                resetScratchCard()
            }
        }

        resetToInitialState()
    }

    private fun coinsRef(userId: String): DatabaseReference {
        return database.getReference("users")
            .child(userId)
            .child("wallet")
            .child("coins")
    }

    private fun resetToInitialState() {
        tvPrizeAmount.text = "?"
        tvPrizeDescription.text = "Buy scratch card to start!"
        tvScratchProgress.text = "Scratch 0% to reveal prize"
        tvResult.text = "Buy scratch card to start!"
        btnScratch.text = "Buy Scratch Card - 15 coins"
        btnScratch.isEnabled = true
        prizeLayout.visibility = View.VISIBLE
    }

    @SuppressLint("SetTextI18n")
    private fun setupScratchCard() {
        scratchView.onScratchListener = object : ScratchView.OnScratchListener {
            override fun onScratchStarted() {
                tvPrizeDescription.text = "Keep scratching..."
            }

            override fun onScratchProgress(progress: Float) {
                val progressPercent = (progress * 100).toInt()
                tvScratchProgress.text = "Scratch $progressPercent% to reveal prize"

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
                        tvPrizeAmount.text = "$prizeCoins"
                        tvPrizeDescription.text = "You won!"
                        tvPrizeAmount.setTextColor(Color.RED)
                    }

                    progressPercent >= 70 -> {
                        tvPrizeAmount.text = "$prizeCoins"
                        tvPrizeDescription.text = "🎉 CONGRATULATIONS! 🎉"
                        tvPrizeAmount.setTextColor(Color.GREEN)

                        if (!isPrizeRevealed) {
                            isPrizeRevealed = true
                            onPrizeRevealed()
                        }
                    }
                }
            }

            override fun onScratchComplete() {
                if (!isPrizeRevealed) {
                    tvPrizeAmount.text = "$prizeCoins"
                    tvPrizeDescription.text = "🎉 PRIZE REVEALED! 🎉"
                    isPrizeRevealed = true
                    onPrizeRevealed()
                }
            }
        }

        scratchView.setScratchEnabled(false)
    }

    private fun onPrizeRevealed() {
        tvResult.text = "🎉 You won $prizeCoins coins! Adding to your wallet..."
        btnScratch.text = "Play Again"
        btnScratch.isEnabled = true

        updateUserCoins(prizeCoins)
    }

    @SuppressLint("SetTextI18n")
    private fun startScratchCard() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            tvResult.text = "Please login first!"
            return
        }

        btnScratch.isEnabled = false
        btnScratch.text = "Processing..."

        coinsRef(userId).runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentCoins = when (val value = currentData.value) {
                    is Long -> value.toInt()
                    is Int -> value
                    is Double -> value.toInt()
                    else -> 0
                }

                if (currentCoins < SCRATCH_COST) {
                    return Transaction.abort()
                }

                currentData.value = currentCoins - SCRATCH_COST
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                snapshot: DataSnapshot?
            ) {
                if (error != null) {
                    Log.e(TAG, "Error processing payment", error.toException())
                    tvResult.text = "❌ Error processing payment"
                    resetScratchCard()
                    return
                }

                if (!committed) {
                    tvResult.text = "❌ Not enough coins! Need $SCRATCH_COST coins"
                    btnScratch.isEnabled = true
                    btnScratch.text = "Buy Scratch Card - 15 coins"
                    return
                }

                prizeCoins = generatePrize()

                scratchView.setScratchEnabled(true)
                scratchView.resetScratch()

                tvPrizeAmount.text = "?"
                tvPrizeAmount.setTextColor(Color.BLACK)
                tvPrizeDescription.text = "Scratch to reveal your prize!"
                tvScratchProgress.text = "Scratch 0% - Prize: ??? coins"

                prizeLayout.visibility = View.VISIBLE
                prizeLayout.invalidate()

                tvResult.text = "✅ Paid! Scratch to reveal your prize!"
                btnScratch.text = "Scratch Now!"
                isCardBought = true
                isPrizeRevealed = false

                recordTransaction(-SCRATCH_COST, "Scratch card cost")
            }
        })
    }

    private fun generatePrize(): Int {
        val random = Math.random()
        return when {
            random < 0.6 -> (5..8).random()
            random < 0.85 -> (9..15).random()
            random < 0.97 -> (16..25).random()
            else -> (26..35).random()
        }
    }

    private fun updateUserCoins(prize: Int) {
        val userId = auth.currentUser?.uid ?: return

        coinsRef(userId).runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentCoins = when (val value = currentData.value) {
                    is Long -> value.toInt()
                    is Int -> value
                    is Double -> value.toInt()
                    else -> 0
                }

                currentData.value = currentCoins + prize
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                snapshot: DataSnapshot?
            ) {
                if (error != null || !committed) {
                    Log.e(TAG, "Failed to update wallet coins: ${error?.message}")
                    tvResult.text = "🎉 You won $prizeCoins coins! (Wallet update failed)"
                    return
                }

                val newBalance = when (val value = snapshot?.value) {
                    is Long -> value.toInt()
                    is Int -> value
                    is Double -> value.toInt()
                    else -> 0
                }

                recordTransaction(prize, "Scratch card win: $prize coins")
                tvResult.text = "🎉 You won $prizeCoins coins! Total: $newBalance coins"
            }
        })
    }

    private fun recordTransaction(coins: Int, description: String) {
        val userId = auth.currentUser?.uid ?: return

        val transactionData = hashMapOf<String, Any>(
            "userId" to userId,
            "type" to "scratch_card",
            "coins" to coins,
            "description" to description,
            "amount" to (coins * 0.50),
            "status" to "completed",
            "createdAt" to System.currentTimeMillis()
        )

        database.getReference("transactions").push().setValue(transactionData)
    }

    @SuppressLint("SetTextI18n")
    private fun resetScratchCard() {
        isCardBought = false
        isPrizeRevealed = false
        prizeCoins = 0

        scratchView.setScratchEnabled(false)
        scratchView.resetScratch()

        tvPrizeAmount.text = "?"
        tvPrizeAmount.setTextColor(Color.BLACK)
        tvPrizeDescription.text = "Buy scratch card to start!"
        tvScratchProgress.text = "Scratch 0% to reveal prize"
        tvResult.text = "Ready to play!"
        btnScratch.text = "Buy Scratch Card - 15 coins"
        btnScratch.isEnabled = true

        prizeLayout.visibility = View.VISIBLE
    }
}