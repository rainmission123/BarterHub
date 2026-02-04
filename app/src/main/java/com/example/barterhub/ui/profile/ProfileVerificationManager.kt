package com.example.barterhub.ui.profile

import android.util.Log
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.barterhub.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.android.material.textview.MaterialTextView

class ProfileVerificationManager(private val fragment: Fragment) {

    private val auth = FirebaseAuth.getInstance()
    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference
    private lateinit var verificationStatusListener: ValueEventListener

    fun setupVerificationStatusListener(idVerificationStatus: MaterialTextView) {
        val currentUser = auth.currentUser ?: return

        val userRef = database.child("users").child(currentUser.uid)

        verificationStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("ProfileDebug", "Verification snapshot exists: ${snapshot.exists()}")

                val verificationStatus = when {
                    !snapshot.exists() -> null
                    else -> {
                        val statusValue = snapshot.child("isIDVerified").value
                        Log.d("ProfileDebug", "Raw verification status: $statusValue (type: ${statusValue?.javaClass?.simpleName})")

                        when (statusValue) {
                            is String -> statusValue
                            is Boolean -> if (statusValue) "verified" else "not_verified"
                            is Int -> when (statusValue) {
                                1 -> "verified"
                                0 -> "not_verified"
                                else -> statusValue.toString()
                            }
                            else -> null
                        }
                    }
                }

                Log.d("ProfileDebug", "Processed verification status: $verificationStatus")
                updateVerificationUI(verificationStatus, idVerificationStatus)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileFragment", "Verification status listener cancelled: ${error.message}")
            }
        }

        userRef.addValueEventListener(verificationStatusListener)
    }

    private fun updateVerificationUI(status: String?, idVerificationStatus: MaterialTextView) {
        if (!fragment.isAdded || fragment.context == null) {
            Log.w("ProfileFragment", "⚠️ Fragment not attached - skipping UI update")
            return
        }

        val successColor = ContextCompat.getColor(fragment.requireContext(), R.color.success)
        val grayColor = ContextCompat.getColor(fragment.requireContext(), R.color.gray)
        val orangeColor = ContextCompat.getColor(fragment.requireContext(), android.R.color.holo_orange_dark)
        val redColor = ContextCompat.getColor(fragment.requireContext(), android.R.color.holo_red_dark)

        when (status) {
            "verified" -> {
                idVerificationStatus.text = "Verified"
                idVerificationStatus.setTextColor(successColor)
                idVerificationStatus.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_check_circle, 0, 0, 0
                )
                idVerificationStatus.isClickable = false
            }
            "pending" -> {
                idVerificationStatus.text = "Under Review"
                idVerificationStatus.setTextColor(orangeColor)
                idVerificationStatus.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_info, 0, 0, 0
                )
                idVerificationStatus.isClickable = false
            }
            "rejected" -> {
                idVerificationStatus.text = "Not Verified"
                idVerificationStatus.setTextColor(redColor)
                idVerificationStatus.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_info, 0, 0, 0
                )
                idVerificationStatus.isClickable = true
            }
            else -> {
                idVerificationStatus.text = "Not Verified"
                idVerificationStatus.setTextColor(grayColor)
                idVerificationStatus.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_info, 0, 0, 0
                )
                idVerificationStatus.isClickable = true
            }
        }
    }

    fun removeListener() {
        auth.currentUser?.uid?.let { userId ->
            database.child("users").child(userId)
                .removeEventListener(verificationStatusListener)
        }
    }
}