package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.example.barterhub.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlin.random.Random

data class MysteryReward(val type: String, val amount: Double, val message: String)

class LuckySpinDialog(
    private val context: Context,
    private var userCoins: Double = 0.0
) {

    private var specialSound: MediaPlayer? = null

    private lateinit var dialog: Dialog
    private lateinit var rewardViews: List<FrameLayout>

    private lateinit var spinWheelContainer: RelativeLayout
    private lateinit var spinWheel: ImageView
    private lateinit var btnSpin: MaterialButton
    private lateinit var tvSpinCost: TextView

    private lateinit var tvCoinBalance: TextView

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    // ✅ FIXED: Reward map matches actual rewards
    private val rewardMap = mapOf(
        0 to "10 Coins",
        1 to "15 Coins",
        2 to "Mystery Gift",
        3 to "1 Extra Spin",
        4 to "1 Coin",
        5 to "25 Coins",  // ✅ Fixed: was "0.6 Coins"
        6 to "5 Coins",
        7 to "2 Coins"    // ✅ Fixed: was "0.5 Coins"
    )

    private var onCoinsUpdateListener: ((Double) -> Unit)? = null

    fun setOnCoinsUpdateListener(listener: (Double) -> Unit) {
        this.onCoinsUpdateListener = listener
    }

    fun show() {
        dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_lucky_spin)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseLuckySpin)
        btnSpin = dialog.findViewById(R.id.btnSpinNow)
        tvSpinCost = dialog.findViewById(R.id.tvSpinCost)

        spinWheelContainer = dialog.findViewById(R.id.wheelContainer)
        spinWheel = dialog.findViewById(R.id.imgSpinWheel)

        rewardViews = listOf(
            dialog.findViewById(R.id.slice1Container),
            dialog.findViewById(R.id.slice2Container),
            dialog.findViewById(R.id.slice3Container),
            dialog.findViewById(R.id.slice4Container),
            dialog.findViewById(R.id.slice5Container),
            dialog.findViewById(R.id.slice6Container),
            dialog.findViewById(R.id.slice7Container),
            dialog.findViewById(R.id.slice8Container)
        )

        tvCoinBalance = dialog.findViewById(R.id.tvCoinBalance)
        tvCoinBalance.text = formatCoins(userCoins)

        updateSpinButton()
        tvSpinCost.text = context.getString(R.string._1_coins_per_spin)

        btnClose.setOnClickListener { dialog.dismiss() }

        btnSpin.setOnClickListener {
            if (userCoins >= 10) startSpin()
            else Toast.makeText(context, "Not enough coins!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun formatCoins(coins: Double): String {
        return if (coins % 1 == 0.0) {
            coins.toInt().toString()
        } else {
            "%.1f".format(coins)
        }
    }

    private fun refreshCoins() {
        tvCoinBalance.text = formatCoins(userCoins)
        saveCoinsToFirebase(userCoins)
        onCoinsUpdateListener?.invoke(userCoins)
    }

    private fun saveCoinsToFirebase(coins: Double) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            database.getReference("users").child(currentUser.uid).child("coins")
                .setValue(coins.toInt())
                .addOnSuccessListener {
                    Log.d("LuckySpinDialog", "Coins saved to Firebase: $coins")
                }
                .addOnFailureListener { e ->
                    Log.e("LuckySpinDialog", "Failed to save coins to Firebase: ${e.message}")
                }
        }
    }

    private fun loadCoinsFromFirebase(callback: (Double) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            database.getReference("users").child(currentUser.uid).child("coins")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val coins = snapshot.getValue(Int::class.java) ?: 0
                        callback(coins.toDouble())
                    }

                    override fun onCancelled(error: DatabaseError) {
                        callback(0.0)
                    }
                })
        } else {
            callback(0.0)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateSpinButton() {
        val coinsText = if (userCoins % 1 == 0.0) userCoins.toInt().toString() else "%.1f".format(userCoins)
        btnSpin.text = "SPIN NOW ($coinsText/10)"
        btnSpin.alpha = if (userCoins >= 10) 1f else 0.6f
        btnSpin.isEnabled = userCoins >= 10
    }

    private fun startSpin() {
        loadCoinsFromFirebase { currentCoins ->
            if (currentCoins >= 10) {
                userCoins = currentCoins
                proceedWithSpin()
            } else {
                Toast.makeText(context, "Not enough coins!", Toast.LENGTH_SHORT).show()
                userCoins = currentCoins
                updateSpinButton()
            }
        }
    }

    private fun proceedWithSpin() {
        val spinSound = MediaPlayer.create(context, R.raw.spin_sound)

        if (spinSound == null) {
            Log.e("LuckySpinDialog", "❌ MediaPlayer failed to create! File may be missing or invalid format.")
        } else {
            Log.d("LuckySpinDialog", "✅ MediaPlayer created successfully.")
            spinSound.setVolume(1f, 1f)
            spinSound.start()
        }

        userCoins -= 10.0
        updateSpinButton()

        spinWheelContainer.clearAnimation()
        rewardViews.forEach { it.alpha = 1f }

        val selectedReward = getControlledReward()
        val targetAngle = calculateTargetAngle(selectedReward)

        val wheelAnimation = RotateAnimation(
            0f, targetAngle,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 3000
            fillAfter = true
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    highlightReward(selectedReward)
                    val rewardText = getRewardText(selectedReward)
                    Toast.makeText(context, "🎉 You won: $rewardText!", Toast.LENGTH_LONG).show()

                    // 🎵 SPECIAL SOUND FOR MYSTERY GIFT
                    if (selectedReward == 2) {
                        specialSound?.release()
                        specialSound = MediaPlayer.create(context, R.raw.mystery_sound)
                        specialSound?.start()
                    }

                    applyReward(selectedReward)

                    val rewardSound = MediaPlayer.create(context, R.raw.reward_sound)
                    rewardSound?.start()
                    rewardSound?.setOnCompletionListener {
                        it.release()
                    }

                    spinSound?.stop()
                    spinSound?.release()
                }

                override fun onAnimationRepeat(animation: Animation?) {}
            })
        }

        spinWheelContainer.startAnimation(wheelAnimation)
    }

    // ✅ OPTIMIZED: Better profit margin
    private fun getControlledReward(): Int {
        val random = Random.nextDouble(100.0)
        return when {
            random < 25 -> 4  // 1 Coin - 25%
            random < 45 -> 7  // 2 Coins - 20%
            random < 60 -> 6  // 5 Coins - 15%
            random < 72 -> 0  // 10 Coins - 12%
            random < 82 -> 1  // 15 Coins - 10%
            random < 90 -> 2  // Mystery Gift - 8%
            random < 96 -> 3  // Free Spin - 6%
            else -> 5         // 25 Coins - 4% (jackpot)
        }
    }

    private fun calculateTargetAngle(selectedReward: Int): Float {
        val totalSlices = rewardViews.size
        val sliceAngle = 360f / totalSlices
        val fullRotations = 5

        return fullRotations * 360f + (totalSlices - selectedReward + 1) * sliceAngle - (sliceAngle / 2)
    }

    private fun highlightReward(index: Int) {
        rewardViews.forEachIndexed { i, view ->
            view.alpha = if (i == index) 1f else 0.3f
        }
    }

    private fun getRewardText(index: Int): String = rewardMap[index] ?: "Unknown Reward"

    private fun applyReward(index: Int) {
        when (index) {
            0 -> {
                userCoins += 10.0
                refreshCoins()
            }
            1 -> {
                userCoins += 15.0
                refreshCoins()
            }
            2 -> showMysteryGift() // Mystery Gift
            3 -> {
                // 🎵 FREE SPIN SOUND - play immediately when wheel stops
                playFreeSpinSound()
                addExtraSpin()   // 1 Extra Spin - AUTO SPIN!
            }
            4 -> {
                userCoins += 1.0
                refreshCoins()
            }
            5 -> {
                userCoins += 25.0
                refreshCoins()
            }
            6 -> {
                userCoins += 5.0
                refreshCoins()
            }
            7 -> {
                userCoins += 2.0
                refreshCoins()
            }
        }
        // ✅ Only update button if NOT free spin
        if (index != 3) {
            updateSpinButton()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun addExtraSpin() {
        // ✅ DISABLE SPIN BUTTON during free spin
        btnSpin.isEnabled = false
        btnSpin.alpha = 0.5f
        btnSpin.text = "FREE SPIN ACTIVE..."

        Toast.makeText(context, "🎁 FREE SPIN! Auto-spinning...", Toast.LENGTH_LONG).show()

        // Auto spin after 2 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startFreeSpin()
        }, 2000)
    }

    private fun startFreeSpin() {
        val spinSound = MediaPlayer.create(context, R.raw.spin_sound)

        if (spinSound == null) {
            Log.e("LuckySpinDialog", "❌ MediaPlayer failed to create!")
        } else {
            Log.d("LuckySpinDialog", "✅ FREE SPIN: MediaPlayer created successfully.")
            spinSound.setVolume(1f, 1f)
            spinSound.start()
        }

        // ✅ NO COIN DEDUCTION for free spin
        spinWheelContainer.clearAnimation()
        rewardViews.forEach { it.alpha = 1f }

        val selectedReward = getControlledReward()
        val targetAngle = calculateTargetAngle(selectedReward)

        val wheelAnimation = RotateAnimation(
            0f, targetAngle,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 2500 // Slightly faster for free spin
            fillAfter = true
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    highlightReward(selectedReward)
                    val rewardText = getRewardText(selectedReward)

                    // Special message for free spin
                    Toast.makeText(context, "✨ FREE SPIN WIN: $rewardText!", Toast.LENGTH_LONG).show()

                    // Special sound for mystery gift
                    if (selectedReward == 2) {
                        specialSound?.release()
                        specialSound = MediaPlayer.create(context, R.raw.mystery_sound)
                        specialSound?.start()
                    }

                    // Apply the reward from free spin
                    applyFreeSpinReward(selectedReward)

                    val rewardSound = MediaPlayer.create(context, R.raw.reward_sound)
                    rewardSound?.start()
                    rewardSound?.setOnCompletionListener {
                        it.release()
                    }

                    spinSound?.stop()
                    spinSound?.release()

                    // ✅ RE-ENABLE BUTTON after free spin completes (unless it's another free spin)
                    if (selectedReward != 3) {
                        updateSpinButton()
                    }
                }

                override fun onAnimationRepeat(animation: Animation?) {}
            })
        }

        spinWheelContainer.startAnimation(wheelAnimation)
    }

    private fun applyFreeSpinReward(index: Int) {
        when (index) {
            0 -> {
                userCoins += 10.0
                refreshCoins()
            }
            1 -> {
                userCoins += 15.0
                refreshCoins()
            }
            2 -> showMysteryGift() // Mystery Gift
            3 -> {
                // ✅ CHAIN FREE SPINS!
                Toast.makeText(context, "🎊 CHAIN BONUS! Another FREE SPIN!", Toast.LENGTH_LONG).show()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startFreeSpin()
                }, 3000)
                // ✅ Button stays disabled during chain spins
            }
            4 -> {
                userCoins += 1.0
                refreshCoins()
            }
            5 -> {
                userCoins += 25.0
                refreshCoins()
            }
            6 -> {
                userCoins += 5.0
                refreshCoins()
            }
            7 -> {
                userCoins += 2.0
                refreshCoins()
            }
        }

        // ✅ Only update button if NOT another free spin
        if (index != 3) {
            updateSpinButton()
        }
    }

    private fun playFreeSpinSound() {
        try {
            val freeSpinSound = MediaPlayer.create(context, R.raw.mystery_sound)
            freeSpinSound?.setVolume(1.0f, 1.0f)
            freeSpinSound?.start()
            freeSpinSound?.setOnCompletionListener {
                it.release()
            }
        } catch (e: Exception) {
            Log.e("LuckySpinDialog", "Failed to play free spin sound: ${e.message}")
        }
    }

    // ✅ OPTIMIZED: Better balanced mystery rewards
    private fun getRandomMysteryReward(): MysteryReward {
        val rewards = listOf(
            MysteryReward("coins", 30.0, "🎉 You found 30 Coins!"),
            MysteryReward("coins", 25.0, "🎉 You found 25 Coins!"),
            MysteryReward("coins", 40.0, "🎊 NICE! 40 Coins!"),
            MysteryReward("coins", 15.0, "🎁 You got 15 Coins!"),
            MysteryReward("coins", 20.0, "🎁 You found 20 Coins!")
        )
        return rewards.random()
    }

    private fun applyMysteryReward(reward: MysteryReward) {
        when (reward.type) {
            "coins" -> {
                userCoins += reward.amount
                refreshCoins()
                Toast.makeText(context, "➕ ${reward.amount.toInt()} Coins added!", Toast.LENGTH_SHORT).show()
            }
            "item" -> {
                Toast.makeText(context, "📦 ${reward.message}", Toast.LENGTH_SHORT).show()
            }
            "spins" -> {
                userCoins += 20.0
                refreshCoins()
                Toast.makeText(context, "🔄 2 Free Spins added!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showMysteryGift() {
        val giftDialog = Dialog(context)
        giftDialog.setContentView(R.layout.dialog_mystery_gift)
        giftDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        giftDialog.setCancelable(true)

        val btnClaimGift = giftDialog.findViewById<MaterialButton>(R.id.btnClaimGift)
        val imgGiftBox = giftDialog.findViewById<ImageView>(R.id.imgGiftBox)
        val tvGiftMessage = giftDialog.findViewById<TextView>(R.id.tvGiftMessage)

        tvGiftMessage.visibility = View.GONE

        imgGiftBox.scaleX = 0f
        imgGiftBox.scaleY = 0f
        imgGiftBox.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(500)
            .withEndAction {
                imgGiftBox.animate()
                    .scaleX(1.1f).scaleY(1.1f)
                    .setDuration(300)
                    .withEndAction {
                        imgGiftBox.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(300)
                            .start()
                    }
                    .start()
            }
            .start()

        btnClaimGift.setOnClickListener {
            btnClaimGift.isEnabled = false
            btnClaimGift.text = "OPENING..."

            imgGiftBox.animate()
                .scaleX(1.3f).scaleY(1.3f)
                .rotation(15f)
                .setDuration(300)
                .withEndAction {
                    imgGiftBox.animate()
                        .rotation(-15f)
                        .setDuration(150)
                        .withEndAction {
                            imgGiftBox.animate()
                                .rotation(0f)
                                .setDuration(150)
                                .withEndAction {
                                    openGiftBox(imgGiftBox, tvGiftMessage, btnClaimGift, giftDialog)
                                }
                                .start()
                        }
                        .start()
                }
                .start()
        }

        giftDialog.show()
    }

    @SuppressLint("SetTextI18n")
    private fun openGiftBox(
        imgGiftBox: ImageView,
        tvGiftMessage: TextView,
        btnClaimGift: MaterialButton,
        giftDialog: Dialog
    ) {
        val reward = getRandomMysteryReward()

        imgGiftBox.animate()
            .scaleX(1.5f).scaleY(1.5f)
            .alpha(0.7f)
            .setDuration(400)
            .withEndAction {
                tvGiftMessage.text = reward.message
                tvGiftMessage.visibility = View.VISIBLE
                tvGiftMessage.scaleX = 0f
                tvGiftMessage.scaleY = 0f
                tvGiftMessage.alpha = 0f

                tvGiftMessage.animate()
                    .scaleX(1f).scaleY(1f)
                    .alpha(1f)
                    .setDuration(500)
                    .start()

                applyMysteryReward(reward)

                imgGiftBox.animate()
                    .scaleX(1f).scaleY(1f)
                    .alpha(1f)
                    .setDuration(300)
                    .withEndAction {
                        btnClaimGift.text = "CLOSE"
                        btnClaimGift.isEnabled = true

                        btnClaimGift.setOnClickListener {
                            giftDialog.dismiss()
                        }
                    }
                    .start()
            }
            .start()
    }

    fun dismiss() {
        if (::dialog.isInitialized) dialog.dismiss()
    }
}