package com.example.barterhub.ui.earn

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DailyChallengesManager {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    private fun getUid(): String? = auth.currentUser?.uid

    private fun getTodayKey(): String {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return now.format(java.util.Date())
    }

    private fun userChallengesRef(): DatabaseReference? {
        val uid = getUid() ?: return null
        return db.child("users").child(uid).child("daily_challenges").child(getTodayKey())
    }

    private fun walletCoinsRef(): DatabaseReference? {
        val uid = getUid() ?: return null
        return db.child("users").child(uid)
            .child("wallet")
            .child("coins")
    }

    fun ensureTodayChallenges(onDone: (() -> Unit)? = null) {
        val ref = userChallengesRef() ?: return

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    onDone?.invoke()
                    return
                }

                val defaults = mapOf(
                    "post_item" to DailyChallengeEntity(
                        title = "Post 1 item",
                        action = "post_item",
                        progress = 0,
                        target = 1,
                        reward = 5,
                        completed = false,
                        rewarded = false
                    ),
                    "complete_transactions" to DailyChallengeEntity(
                        title = "Complete 2 transactions",
                        action = "complete_transactions",
                        progress = 0,
                        target = 2,
                        reward = 10,
                        completed = false,
                        rewarded = false
                    ),
                    "daily_login" to DailyChallengeEntity(
                        title = "Daily login",
                        action = "daily_login",
                        progress = 1,
                        target = 1,
                        reward = 2,
                        completed = true,
                        rewarded = false
                    ),
                    "rate_partner" to DailyChallengeEntity(
                        title = "Rate a trade partner",
                        action = "rate_partner",
                        progress = 0,
                        target = 1,
                        reward = 1,
                        completed = false,
                        rewarded = false
                    ),
                    "share_app" to DailyChallengeEntity(
                        title = "Share app with friends",
                        action = "share_app",
                        progress = 0,
                        target = 1,
                        reward = 3,
                        completed = false,
                        rewarded = false
                    )
                )

                ref.setValue(defaults).addOnCompleteListener {
                    onDone?.invoke()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onDone?.invoke()
            }
        })
    }

    fun loadChallenges(onResult: (List<Challenge>) -> Unit) {
        val ref = userChallengesRef() ?: return

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val challenges = mutableListOf<Challenge>()

                snapshot.children.forEach { child ->
                    val entity = child.getValue(DailyChallengeEntity::class.java) ?: return@forEach
                    challenges.add(
                        Challenge(
                            title = entity.title,
                            reward = "+${entity.reward} coin" + if (entity.reward > 1) "s" else "",
                            action = entity.action,
                            isCompleted = entity.completed,
                            progress = entity.progress,
                            target = entity.target,
                            rewardCoins = entity.reward,
                            rewarded = entity.rewarded
                        )
                    )
                }

                val ordered = challenges.sortedBy {
                    when (it.action) {
                        "post_item" -> 0
                        "complete_transactions" -> 1
                        "daily_login" -> 2
                        "rate_partner" -> 3
                        "share_app" -> 4
                        else -> 99
                    }
                }

                onResult(ordered)
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(emptyList())
            }
        })
    }

    fun incrementChallengeProgress(
        action: String,
        step: Int = 1,
        onRewardEarned: ((Int) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val ref = userChallengesRef()?.child(action) ?: return

        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val map = currentData.value as? MutableMap<String, Any?> ?: return Transaction.success(currentData)

                val currentProgress = (map["progress"] as? Long ?: 0L).toInt()
                val target = (map["target"] as? Long ?: 1L).toInt()

                val newProgress = (currentProgress + step).coerceAtMost(target)
                map["progress"] = newProgress
                map["completed"] = newProgress >= target

                currentData.value = map
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                onComplete?.invoke()
            }
        })
    }

    fun awardChallengeIfNeeded(
        action: String,
        onRewardEarned: ((Int) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val challengeRef = userChallengesRef()?.child(action) ?: return
        val coinsRef = walletCoinsRef() ?: return

        val awardState = BooleanArray(1)
        val rewardState = IntArray(1)

        challengeRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                awardState[0] = false
                rewardState[0] = 0

                val map = currentData.value as? MutableMap<String, Any?>
                    ?: return Transaction.success(currentData)

                val completed = map["completed"] as? Boolean ?: false
                val rewarded = map["rewarded"] as? Boolean ?: false

                val reward = when (val value = map["reward"]) {
                    is Long -> value.toInt()
                    is Int -> value
                    is Double -> value.toInt()
                    else -> 0
                }

                if (completed && !rewarded && reward > 0) {
                    map["rewarded"] = true
                    currentData.value = map

                    awardState[0] = true
                    rewardState[0] = reward
                }

                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null || !committed || !awardState[0] || rewardState[0] <= 0) {
                    onComplete?.invoke()
                    return
                }

                coinsRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val currentCoins = when (val value = currentData.value) {
                            is Long -> value.toInt()
                            is Int -> value
                            is Double -> value.toInt()
                            else -> 0
                        }

                        currentData.value = currentCoins + rewardState[0]
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        currentData: DataSnapshot?
                    ) {
                        if (committed) {
                            onRewardEarned?.invoke(rewardState[0])
                        }
                        onComplete?.invoke()
                    }
                })
            }
        })
    }
}