package com.example.barterhub.ui.earn

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ReferralManager(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) {

    fun loadOrCreateReferralCode(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            onError("User not logged in.")
            return
        }

        val userRef = database.getReference("users").child(uid)

        userRef.child("referralCode").get()
            .addOnSuccessListener { snapshot ->
                val existingCode = snapshot.getValue(String::class.java)

                if (!existingCode.isNullOrBlank()) {
                    onSuccess(existingCode)
                } else {
                    val generatedCode = generateReferralCode(uid)

                    userRef.child("referralCode").setValue(generatedCode)
                        .addOnSuccessListener {
                            onSuccess(generatedCode)
                        }
                        .addOnFailureListener {
                            onError("Failed to create referral code.")
                        }
                }
            }
            .addOnFailureListener {
                onError("Failed to load referral code.")
            }
    }

    private fun generateReferralCode(uid: String): String {
        return "BH-" + uid.takeLast(6).uppercase()
    }

    fun buildReferralMessage(referralCode: String): String {
        return """
            Join me on BarterHub! 🎯
            
            Trade items, earn coins, and get amazing deals!
            
            Use my referral code: $referralCode
            
            Download now: https://play.google.com/store/apps/details?id=com.example.barterhub
        """.trimIndent()
    }

    fun performShare(activity: Activity, message: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra(Intent.EXTRA_SUBJECT, "Join BarterHub and Earn Coins!")
        }

        activity.startActivity(Intent.createChooser(shareIntent, "Invite Friends to BarterHub"))
    }
}