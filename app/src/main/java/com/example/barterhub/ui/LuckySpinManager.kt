package com.example.barterhub.ui

import android.widget.ImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LuckySpinManager {

    private val rewards = listOf(
        Reward("+5 Coins", 5, RewardType.COINS),
        Reward("+20 Coins", 20, RewardType.COINS),
        Reward("Mystery Box", 1, RewardType.MYSTERY_BOX),
        Reward("+5 Coins", 5, RewardType.COINS),
        Reward("Better luck next time", 0, RewardType.NOTHING),
        Reward("+20 Coins", 20, RewardType.COINS),
        Reward("+10 Coins", 10, RewardType.COINS),
        Reward("Special Badge", 1, RewardType.BADGE)
    )

    fun startSpin(
        wheel: ImageView,
        onFinish: (Reward) -> Unit
    ) {
        val slices = rewards.size
        val sliceAngle = 360 / slices

        // Random index with probability weights
        val randomIndex = getWeightedRandomIndex()
        val landingAngle = randomIndex * sliceAngle

        // Multiple rotations for realistic effect
        val rotation = (360 * 5 + landingAngle).toFloat()

        wheel.animate()
            .rotation(rotation)
            .setDuration(3000)
            .withEndAction {
                onFinish(rewards[randomIndex])
            }
            .start()
    }

    private fun getWeightedRandomIndex(): Int {
        val weights = listOf(20, 10, 5, 20, 25, 10, 15, 5) // Probability weights
        val total = weights.sum()
        val random = (0 until total).random()

        var current = 0
        for (i in weights.indices) {
            current += weights[i]
            if (random < current) return i
        }
        return weights.size - 1
    }

    fun applyReward(reward: Reward, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            onError("User not logged in")
            return
        }

        val ref = FirebaseDatabase.getInstance().getReference("users/$userId")

        ref.child("coins").get().addOnSuccessListener { snapshot ->
            val currentCoins = snapshot.getValue(Int::class.java) ?: 0

            when (reward.type) {
                RewardType.COINS -> {
                    val newCoins = currentCoins + reward.value
                    ref.child("coins").setValue(newCoins)
                        .addOnSuccessListener {
                            onSuccess("You won ${reward.value} coins!")
                        }
                        .addOnFailureListener {
                            onError("Failed to update coins")
                        }
                }
                RewardType.MYSTERY_BOX -> {
                    ref.child("mysteryBoxes").get().addOnSuccessListener { mysterySnapshot ->
                        val currentBoxes = mysterySnapshot.getValue(Int::class.java) ?: 0
                        ref.child("mysteryBoxes").setValue(currentBoxes + 1)
                            .addOnSuccessListener {
                                onSuccess("You won a Mystery Box!")
                            }
                            .addOnFailureListener {
                                onError("Failed to add mystery box")
                            }
                    }
                }
                RewardType.BADGE -> {
                    ref.child("badges").child("special").setValue(true)
                        .addOnSuccessListener {
                            onSuccess("You won a Special Badge!")
                        }
                        .addOnFailureListener {
                            onError("Failed to add badge")
                        }
                }
                RewardType.NOTHING -> {
                    onSuccess("Better luck next time!")
                }
            }
        }.addOnFailureListener {
            onError("Failed to get user data")
        }
    }

    fun deductSpinCost(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            onError("User not logged in")
            return
        }

        val ref = FirebaseDatabase.getInstance().getReference("users/$userId")

        ref.child("coins").get().addOnSuccessListener { snapshot ->
            val currentCoins = snapshot.getValue(Int::class.java) ?: 0

            if (currentCoins >= 10) {
                ref.child("coins").setValue(currentCoins - 10)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError("Failed to deduct coins") }
            } else {
                onError("Not enough coins")
            }
        }.addOnFailureListener {
            onError("Failed to get coins")
        }
    }
}

data class Reward(
    val name: String,
    val value: Int,
    val type: RewardType
)

enum class RewardType {
    COINS, MYSTERY_BOX, BADGE, NOTHING
}