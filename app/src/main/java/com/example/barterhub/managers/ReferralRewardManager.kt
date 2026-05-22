package com.example.barterhub.managers

import android.util.Log
import com.google.firebase.database.*

class ReferralRewardManager {

    companion object {
        private const val TAG = "ReferralRewardManager"
        private const val REFERRAL_REWARD_COINS = 20
        private const val DB_URL = "https://barterhub-3c947-default-rtdb.firebaseio.com/"
    }

    private val db = FirebaseDatabase.getInstance(DB_URL).reference

    fun grantReferralRewardIfEligible(invitedUserId: String) {
        val userRef = db.child("users").child(invitedUserId)

        userRef.get()
            .addOnSuccessListener { userSnapshot ->
                if (!userSnapshot.exists()) {
                    Log.d(TAG, "User $invitedUserId not found")
                    return@addOnSuccessListener
                }

                val inviterUid = userSnapshot.child("referredBy")
                    .getValue(String::class.java)
                    .orEmpty()

                val rewardGranted = userSnapshot.child("referralRewardGranted")
                    .getValue(Boolean::class.java) ?: false

                val tradesCompleted = userSnapshot.child("tradesCompleted")
                    .getValue(Int::class.java) ?: 0

                Log.d(
                    TAG,
                    "Checking referral reward | invited=$invitedUserId inviter=$inviterUid rewardGranted=$rewardGranted tradesCompleted=$tradesCompleted"
                )

                if (inviterUid.isBlank()) return@addOnSuccessListener
                if (rewardGranted) return@addOnSuccessListener
                if (tradesCompleted != 1) return@addOnSuccessListener

                lockAndGrantReward(invitedUserId, inviterUid)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check referral eligibility: ${e.message}")
            }
    }

    private fun lockAndGrantReward(invitedUserId: String, inviterUid: String) {
        val rewardFlagRef = db.child("referrals")
            .child(inviterUid)
            .child(invitedUserId)
            .child("rewardGranted")

        rewardFlagRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val alreadyGranted = currentData.getValue(Boolean::class.java) ?: false
                if (alreadyGranted) {
                    return Transaction.abort()
                }
                currentData.value = true
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                snapshot: DataSnapshot?
            ) {
                if (error != null) {
                    Log.e(TAG, "Reward transaction failed: ${error.message}")
                    return
                }

                if (!committed) {
                    Log.d(TAG, "Reward already granted for invited user: $invitedUserId")
                    return
                }

                grantInviterReward(invitedUserId, inviterUid)
            }
        })
    }

    private fun grantInviterReward(invitedUserId: String, inviterUid: String) {
        val inviterCoinsRef = db.child("users")
            .child(inviterUid)
            .child("wallet")
            .child("coins")

        inviterCoinsRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentCoins = currentData.getValue(Int::class.java) ?: 0
                currentData.value = currentCoins + REFERRAL_REWARD_COINS
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                snapshot: DataSnapshot?
            ) {
                if (error != null) {
                    Log.e(TAG, "Failed to update inviter coins: ${error.message}")
                    return
                }

                if (!committed) {
                    Log.e(TAG, "Inviter coins transaction not committed")
                    return
                }

                val updates = hashMapOf<String, Any>(
                    "/users/$invitedUserId/referralRewardGranted" to true,
                    "/referrals/$inviterUid/$invitedUserId/firstTransactionCompleted" to true,
                    "/referrals/$inviterUid/$invitedUserId/status" to "completed",
                    "/referrals/$inviterUid/$invitedUserId/rewardedAt" to System.currentTimeMillis()
                )

                db.updateChildren(updates)
                    .addOnSuccessListener {
                        Log.d(TAG, "Referral reward granted successfully")
                        createReferralRewardNotification(
                            inviterUid = inviterUid,
                            invitedUserId = invitedUserId
                        )
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to finalize reward updates: ${e.message}")
                    }
            }
        })
    }

    private fun createReferralRewardNotification(
        inviterUid: String,
        invitedUserId: String
    ) {
        // FIXED ID para hindi madoble
        val notifId = "referral_reward_$invitedUserId"
        val notifRef = db.child("notifications").child(inviterUid).child(notifId)

        notifRef.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    Log.d(TAG, "Referral reward notification already exists: $notifId")
                    return@addOnSuccessListener
                }

                val notification = hashMapOf<String, Any>(
                    "id" to notifId,
                    "type" to "referral_reward",
                    "title" to "Referral Reward",
                    "message" to "You earned $REFERRAL_REWARD_COINS coins because your invited user completed their first trade!",
                    "invitedUserId" to invitedUserId,
                    "timestamp" to System.currentTimeMillis(),
                    "read" to false,

                    // for navigation
                    "targetType" to "referral_reward",
                    "targetUserId" to invitedUserId
                )

                notifRef.setValue(notification)
                    .addOnSuccessListener {
                        Log.d(TAG, "Referral reward notification sent: $notifId")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send referral notification: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check notification existence: ${e.message}")
            }
    }
}