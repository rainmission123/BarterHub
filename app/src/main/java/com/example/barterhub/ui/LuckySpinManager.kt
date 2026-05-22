package com.example.barterhub.ui

import android.widget.ImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

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

        val randomIndex = getWeightedRandomIndex()
        val landingAngle = randomIndex * sliceAngle
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
        val weights = listOf(20, 10, 5, 20, 25, 10, 15, 5)
        val total = weights.sum()
        val random = (0 until total).random()

        var current = 0
        for (i in weights.indices) {
            current += weights[i]
            if (random < current) return i
        }

        return weights.size - 1
    }

    fun applyReward(
        reward: Reward,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            onError("User not logged in")
            return
        }

        val userRef = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)

        val coinsRef = userRef.child("wallet").child("coins")

        when (reward.type) {
            RewardType.COINS -> {
                coinsRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val currentCoins = when (val value = currentData.value) {
                            is Long -> value.toInt()
                            is Int -> value
                            is Double -> value.toInt()
                            else -> 0
                        }

                        currentData.value = currentCoins + reward.value
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        snapshot: DataSnapshot?
                    ) {
                        if (error != null || !committed) {
                            onError("Failed to update coins")
                            return
                        }

                        onSuccess("You won ${reward.value} coins!")
                    }
                })
            }

            RewardType.MYSTERY_BOX -> {
                val mysteryRef = userRef.child("mysteryBoxes")

                mysteryRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val currentBoxes = when (val value = currentData.value) {
                            is Long -> value.toInt()
                            is Int -> value
                            is Double -> value.toInt()
                            else -> 0
                        }

                        currentData.value = currentBoxes + 1
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        snapshot: DataSnapshot?
                    ) {
                        if (error != null || !committed) {
                            onError("Failed to add mystery box")
                            return
                        }

                        onSuccess("You won a Mystery Box!")
                    }
                })
            }

            RewardType.BADGE -> {
                userRef.child("badges").child("special").setValue(true)
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
    }

    fun deductSpinCost(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            onError("User not logged in")
            return
        }

        val coinsRef = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)
            .child("wallet")
            .child("coins")

        coinsRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentCoins = when (val value = currentData.value) {
                    is Long -> value.toInt()
                    is Int -> value
                    is Double -> value.toInt()
                    else -> 0
                }

                if (currentCoins < 10) {
                    return Transaction.abort()
                }

                currentData.value = currentCoins - 10
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                snapshot: DataSnapshot?
            ) {
                if (error != null) {
                    onError("Failed to deduct coins")
                    return
                }

                if (!committed) {
                    onError("Not enough coins")
                    return
                }

                onSuccess()
            }
        })
    }
}

data class Reward(
    val name: String,
    val value: Int,
    val type: RewardType
)

enum class RewardType {
    COINS,
    MYSTERY_BOX,
    BADGE,
    NOTHING
}