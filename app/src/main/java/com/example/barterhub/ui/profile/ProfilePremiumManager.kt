package com.example.barterhub.ui.profile

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.barterhub.R
import com.example.barterhub.ads.AppOpenAdManager
import com.example.barterhub.utils.PremiumHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ProfilePremiumManager(private val fragment: Fragment) {
    private var premiumListener: ValueEventListener? = null
    private var premiumRef: DatabaseReference? = null
    private val auth = FirebaseAuth.getInstance()
    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference

    fun checkPremiumStatus(tvPremiumStatus: TextView, btnGetPremium: Button) {
        val userId = auth.currentUser?.uid ?: return

        premiumListener?.let { l ->
            premiumRef?.removeEventListener(l)
        }

        premiumRef = database.child("users").child(userId)

        premiumListener = object : ValueEventListener {
            @SuppressLint("SetTextI18n")
            override fun onDataChange(snapshot: DataSnapshot) {
                val isPremium = snapshot.child("isPremium").getValue(Boolean::class.java) ?: false
                val expiry = snapshot.child("premiumExpiry").getValue(Long::class.java) ?: 0L

                val isActive = PremiumHelper.isPremiumActive(isPremium, expiry)

                if (isActive) {
                    tvPremiumStatus.visibility = View.VISIBLE

                    val date = java.text.SimpleDateFormat(
                        "MMM dd, yyyy",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date(expiry))

                    tvPremiumStatus.text = "Premium Active • Until $date"

                    btnGetPremium.visibility = View.VISIBLE
                    btnGetPremium.text = "Manage Premium"
                } else {
                    tvPremiumStatus.visibility = View.GONE
                    btnGetPremium.visibility = View.VISIBLE
                    btnGetPremium.text = "Get Premium"
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        premiumRef?.addValueEventListener(premiumListener!!)
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

        setupCardViewClicks(view, rb50Coins, rb100Coins, rb200Coins)

        database.child("users").child(userId).child("coins")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val currentCoins = snapshot.getValue(Int::class.java) ?: 0
                    tvCurrentBalance.text = currentCoins.toString()

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
                        val (coinsRequired, planId, durationText) = when (selectedId) {
                            R.id.rb50Coins -> Triple(50, "1_month", "1 month")
                            R.id.rb100Coins -> Triple(100, "5_months", "5 months")
                            R.id.rb200Coins -> Triple(200, "1_year", "1 year")
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

                        activatePremium(coinsRequired, planId, durationText)
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

    fun clear() {
        premiumListener?.let { l ->
            premiumRef?.removeEventListener(l)
        }
        premiumListener = null
        premiumRef = null
    }

    private fun setupCardViewClicks(
        view: View,
        rb50Coins: RadioButton,
        rb100Coins: RadioButton,
        rb200Coins: RadioButton
    ) {
        val cardView50 = rb50Coins.parent?.parent as? View
        val cardView100 = rb100Coins.parent?.parent as? View
        val cardView200 = rb200Coins.parent?.parent as? View

        cardView50?.setOnClickListener {
            rb50Coins.isChecked = true
            view.findViewById<RadioGroup>(R.id.rgPremiumOptions)?.check(R.id.rb50Coins)
        }

        cardView100?.setOnClickListener {
            rb100Coins.isChecked = true
            view.findViewById<RadioGroup>(R.id.rgPremiumOptions)?.check(R.id.rb100Coins)
        }

        cardView200?.setOnClickListener {
            rb200Coins.isChecked = true
            view.findViewById<RadioGroup>(R.id.rgPremiumOptions)?.check(R.id.rb200Coins)
        }
    }

    private fun activatePremium(coinsRequired: Int, planId: String, durationText: String) {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.child("users").child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentCoins = snapshot.child("coins").getValue(Int::class.java) ?: 0
                val currentIsPremium = snapshot.child("isPremium").getValue(Boolean::class.java) ?: false
                val currentExpiry = snapshot.child("premiumExpiry").getValue(Long::class.java) ?: 0L

                if (currentCoins < coinsRequired) {
                    Toast.makeText(
                        fragment.requireContext(),
                        fragment.getString(R.string.insufficient_coins),
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                val durationMillis = PremiumHelper.getPlanExpiry(planId) - System.currentTimeMillis()
                val newExpiry = if (
                    PremiumHelper.isPremiumActive(currentIsPremium, currentExpiry)
                ) {
                    currentExpiry + durationMillis
                } else {
                    System.currentTimeMillis() + durationMillis
                }

                val updates = mapOf<String, Any>(
                    "coins" to (currentCoins - coinsRequired),
                    "isPremium" to true,
                    "premiumExpiry" to newExpiry
                )

                userRef.updateChildren(updates)
                    .addOnSuccessListener {
                        AppOpenAdManager.onPremiumStateChanged()
                        Toast.makeText(
                            fragment.requireContext(),
                            fragment.getString(R.string.premium_activated_for, durationText),
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