package com.example.barterhub.ui

import android.app.Dialog
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import com.example.barterhub.R
import com.google.android.material.button.MaterialButton
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.addListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class CoinFlipDialog(context: Context) : Dialog(context) {

    private var userChoice: String? = null
    private var isFlipping = false
    private lateinit var soundThrow: MediaPlayer

    // Fixed win rate
    private val WIN_RATE = 0.20f
    private val BET_AMOUNT = 5
    private val WIN_AMOUNT = 10

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_coin_flip)

        val btnClose = findViewById<ImageView>(R.id.btnCloseCoinFlip)
        val btnFlip = findViewById<MaterialButton>(R.id.btnFlipCoin)
        val btnHeads = findViewById<MaterialButton>(R.id.btnHeads)
        val btnTails = findViewById<MaterialButton>(R.id.btnTails)
        val tvResult = findViewById<TextView>(R.id.tvCoinResult)
        val coinImage = findViewById<ImageView>(R.id.imgCoin)

        soundThrow = MediaPlayer.create(context, R.raw.coin_throw)

        btnClose.setOnClickListener { dismiss() }

        // -----------------------
        // HEADS BUTTON
        // -----------------------
        btnHeads.setOnClickListener {
            userChoice = "HEADS"
            btnHeads.setBackgroundColor(context.getColor(R.color.red_500))
            btnHeads.setTextColor(context.getColor(android.R.color.white))

            btnTails.setBackgroundColor(context.getColor(android.R.color.transparent))
            btnTails.setTextColor(context.getColor(R.color.blue_500))

            tvResult.text = "You chose: HEADS\nTap FLIP COIN to play!"
        }

        // -----------------------
        // TAILS BUTTON
        // -----------------------
        btnTails.setOnClickListener {
            userChoice = "TAILS"
            btnTails.setBackgroundColor(context.getColor(R.color.blue_500))
            btnTails.setTextColor(context.getColor(android.R.color.white))

            btnHeads.setBackgroundColor(context.getColor(android.R.color.transparent))
            btnHeads.setTextColor(context.getColor(R.color.red_500))

            tvResult.text = "You chose: TAILS\nTap FLIP COIN to play!"
        }

        // Firebase references
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val userRef = db.collection("users").document(userId!!)

        // ===========================
        // FLIP COIN BUTTON
        // ===========================
        btnFlip.setOnClickListener {
            if (isFlipping) return@setOnClickListener
            if (userChoice == null) {
                tvResult.text = "Please choose Heads or Tails first!"
                return@setOnClickListener
            }

            isFlipping = true
            btnFlip.isEnabled = false

            // 1️⃣ Check user coins first (Realtime Database)
            val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)
            userRef.get().addOnSuccessListener { snapshot ->
                val currentCoins = snapshot.child("coins").getValue(Int::class.java) ?: 0
                if (currentCoins < BET_AMOUNT) {
                    tvResult.text = "😔 Not enough coins!"
                    isFlipping = false
                    btnFlip.isEnabled = true
                    return@addOnSuccessListener
                }

                // 2️⃣ Fixed Win Rate Logic
                val userWins = Math.random().toFloat() < WIN_RATE
                val result = if (userWins) userChoice!! else if (userChoice == "HEADS") "TAILS" else "HEADS"
                val isHeads = (result == "HEADS")

                // ========== ANIMATION ==========
                val jumpUp = ObjectAnimator.ofFloat(coinImage, "translationY", 0f, -250f).apply {
                    duration = 300
                    interpolator = DecelerateInterpolator()
                    addListener(onStart = { soundThrow.start() })
                }

                val spinX = ObjectAnimator.ofFloat(coinImage, "rotationX", 0f, 720f)
                val spinY = ObjectAnimator.ofFloat(coinImage, "rotationY", 0f, 360f)
                val spin = AnimatorSet().apply {
                    duration = 600
                    interpolator = AccelerateInterpolator()
                    playTogether(spinX, spinY)
                }

                val fallDown = ObjectAnimator.ofFloat(coinImage, "translationY", -250f, 0f).apply {
                    duration = 300
                    interpolator = AccelerateInterpolator()
                    addListener(onEnd = {

                        // FINAL SIDE IMAGE
                        coinImage.setImageResource(if (isHeads) R.drawable.coin_heads else R.drawable.coin_tails1)

                        // 3️⃣ Update Wallet (Realtime Database)
                        val netChange = if (userWins) WIN_AMOUNT else -BET_AMOUNT
                        val newBalance = currentCoins + netChange

                        userRef.child("coins").setValue(newBalance)
                            .addOnSuccessListener {
                                val message = if (userWins) {
                                    "🎉 You won! It's $result!\nWallet: $newBalance coins"
                                } else {
                                    "😔 You lost! It's $result!\nWallet: $newBalance coins"
                                }
                                tvResult.text = message
                            }
                            .addOnFailureListener {
                                tvResult.text = "Error updating wallet!"
                            }

                        // RESET
                        isFlipping = false
                        btnFlip.isEnabled = true

                        Handler(Looper.getMainLooper()).postDelayed({
                            userChoice = null
                            btnHeads.setBackgroundColor(context.getColor(android.R.color.transparent))
                            btnHeads.setTextColor(context.getColor(R.color.red_500))
                            btnTails.setBackgroundColor(context.getColor(android.R.color.transparent))
                            btnTails.setTextColor(context.getColor(R.color.blue_500))
                            tvResult.text = "Choose Heads or Tails!"
                        }, 2000)
                    })
                }

                AnimatorSet().apply {
                    playSequentially(jumpUp, spin, fallDown)
                    start()
                }

            }.addOnFailureListener {
                tvResult.text = "Failed to access wallet!"
                isFlipping = false
                btnFlip.isEnabled = true
            }
        }

    }

    override fun dismiss() {
        super.dismiss()
        if (::soundThrow.isInitialized) soundThrow.release()
    }
}
