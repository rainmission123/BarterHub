package com.example.barterhub.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.barterhub.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.Calendar

class ProfilePremiumManager(private val fragment: Fragment) {

    private val auth = FirebaseAuth.getInstance()
    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference

    fun checkPremiumStatus(
        tvPremiumStatus: TextView,
        btnGetPremium: Button
    ) {
        val userId = auth.currentUser?.uid ?: return

        database.child("users").child(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isPremium = snapshot.child("isPremium")
                        .getValue(Boolean::class.java) ?: false
                    val expiry = snapshot.child("premiumExpiry")
                        .getValue(Long::class.java) ?: 0L
                    val now = System.currentTimeMillis()

                    if (isPremium && expiry > now) {
                        tvPremiumStatus.visibility = View.VISIBLE
                        btnGetPremium.visibility = View.GONE
                    } else {
                        tvPremiumStatus.visibility = View.GONE
                        btnGetPremium.visibility = View.VISIBLE
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun showPremiumBottomSheet(btnGetPremium: Button) {
        btnGetPremium.setOnClickListener {
            val bottomSheetDialog = BottomSheetDialog(fragment.requireContext())
            val view = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.bottom_sheet_premium, null)

            setupPremiumBottomSheet(view, bottomSheetDialog)
            bottomSheetDialog.setContentView(view)
            bottomSheetDialog.show()
        }
    }

    private fun setupPremiumBottomSheet(view: View, dialog: BottomSheetDialog) {
        val tvCurrentBalance = view.findViewById<TextView>(R.id.tvCurrentBalance)
        val rgPremiumOptions = view.findViewById<RadioGroup>(R.id.rgPremiumOptions)
        val rb50Coins = view.findViewById<RadioButton>(R.id.rb50Coins)
        val rb100Coins = view.findViewById<RadioButton>(R.id.rb100Coins)
        val rb200Coins = view.findViewById<RadioButton>(R.id.rb200Coins)
        val btnConfirmPremium = view.findViewById<Button>(R.id.btnConfirmPremium)
        val btnCancelPremium = view.findViewById<TextView>(R.id.btnCancelPremium)

        val userId = auth.currentUser?.uid ?: return

        // Set up click listeners for the card views (not just radio buttons)
        setupCardViewClicks(view, rb50Coins, rb100Coins, rb200Coins)

        // Load user coins
        database.child("users").child(userId).child("coins")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val currentCoins = snapshot.getValue(Int::class.java) ?: 0
                    tvCurrentBalance.text = currentCoins.toString()

                    // Auto-select based on coins
                    when {
                        currentCoins >= 200 -> {
                            rb200Coins.isChecked = true
                            rgPremiumOptions.check(R.id.rb200Coins)
                        }
                        currentCoins >= 100 -> {
                            rb100Coins.isChecked = true
                            rgPremiumOptions.check(R.id.rb100Coins)
                        }
                        currentCoins >= 50 -> {
                            rb50Coins.isChecked = true
                            rgPremiumOptions.check(R.id.rb50Coins)
                        }
                        else -> {
                            rb50Coins.isEnabled = false
                            rb100Coins.isEnabled = false
                            rb200Coins.isEnabled = false
                            Toast.makeText(
                                fragment.requireContext(),
                                fragment.getString(R.string.not_enough_coins_for_premium),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    btnConfirmPremium.setOnClickListener {
                        val selectedId = rgPremiumOptions.checkedRadioButtonId
                        val (coinsRequired, duration) = when (selectedId) {
                            R.id.rb50Coins -> 50 to "1 month"
                            R.id.rb100Coins -> 100 to "5 months"
                            R.id.rb200Coins -> 200 to "1 year"
                            else -> {
                                Toast.makeText(
                                    fragment.requireContext(),
                                    fragment.getString(R.string.select_a_plan),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@setOnClickListener
                            }
                        }

                        if (currentCoins < coinsRequired) {
                            Toast.makeText(
                                fragment.requireContext(),
                                fragment.getString(R.string.not_enough_coins),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }

                        activatePremium(coinsRequired, duration)
                        dialog.dismiss()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        fragment.requireContext(),
                        fragment.getString(R.string.failed_to_load_coins),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })

        btnCancelPremium.setOnClickListener { dialog.dismiss() }
    }

    private fun setupCardViewClicks(
        view: View,
        rb50Coins: RadioButton,
        rb100Coins: RadioButton,
        rb200Coins: RadioButton
    ) {

        // Find the CardView containers for each option
        val cardView50 = rb50Coins.parent?.parent as? View
        val cardView100 = rb100Coins.parent?.parent as? View
        val cardView200 = rb200Coins.parent?.parent as? View

        // Set click listeners on the CardViews
        cardView50?.setOnClickListener {
            rb50Coins.isChecked = true
            (view.findViewById<RadioGroup>(R.id.rgPremiumOptions))?.check(R.id.rb50Coins)
        }

        cardView100?.setOnClickListener {
            rb100Coins.isChecked = true
            (view.findViewById<RadioGroup>(R.id.rgPremiumOptions))?.check(R.id.rb100Coins)
        }

        cardView200?.setOnClickListener {
            rb200Coins.isChecked = true
            (view.findViewById<RadioGroup>(R.id.rgPremiumOptions))?.check(R.id.rb200Coins)
        }
    }

    private fun activatePremium(coinsRequired: Int, duration: String) {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.child("users").child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentCoins = snapshot.child("coins").getValue(Int::class.java) ?: 0

                if (currentCoins >= coinsRequired) {
                    val newCoins = currentCoins - coinsRequired
                    val calendar = Calendar.getInstance()

                    when (duration) {
                        "1 month" -> calendar.add(Calendar.MONTH, 1)
                        "5 months" -> calendar.add(Calendar.MONTH, 5)
                        "1 year" -> calendar.add(Calendar.YEAR, 1)
                    }

                    val premiumExpiry = calendar.timeInMillis
                    val updates = mapOf(
                        "coins" to newCoins,
                        "isPremium" to true,
                        "premiumExpiry" to premiumExpiry
                    )

                    userRef.updateChildren(updates)
                        .addOnSuccessListener {
                            Toast.makeText(
                                fragment.requireContext(),
                                fragment.getString(R.string.premium_activated_for, duration),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                fragment.requireContext(),
                                fragment.getString(R.string.failed_to_activate_premium, e.message),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    Toast.makeText(
                        fragment.requireContext(),
                        fragment.getString(R.string.insufficient_coins),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    fragment.requireContext(),
                    fragment.getString(R.string.error, error.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
}