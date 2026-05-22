package com.example.barterhub.ui.profile

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.barterhub.R
import com.example.barterhub.ads.AppOpenAdManager
import com.example.barterhub.utils.PremiumHelper
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ProfilePremiumManager(private val fragment: Fragment) {

    private var premiumListener: ValueEventListener? = null
    private var premiumRef: DatabaseReference? = null
    private val auth = FirebaseAuth.getInstance()
    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference

    // ===========================
    // PREMIUM STATUS CHECK
    // ===========================
    fun checkPremiumStatus(tvPremiumStatus: TextView, btnGetPremium: Button) {
        val userId = auth.currentUser?.uid ?: return

        premiumListener?.let { premiumRef?.removeEventListener(it) }
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
                    btnGetPremium.text = "Manage Premium"

                } else {
                    tvPremiumStatus.visibility = View.GONE
                    btnGetPremium.text = "Get Premium"
                }

                btnGetPremium.visibility = View.VISIBLE
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        premiumRef?.addValueEventListener(premiumListener!!)
    }

    // ===========================
    // SHOW BOTTOM SHEET (FIXED FULL HEIGHT)
    // ===========================
    fun showPremiumBottomSheet(btnGetPremium: Button) {
        btnGetPremium.setOnClickListener {

            val dialog = BottomSheetDialog(fragment.requireContext())
            val view = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.bottom_sheet_premium, null)

            setupPremiumBottomSheet(view, dialog)

            dialog.setContentView(view)

            // 🔥 FULL HEIGHT FIX
            dialog.setOnShowListener {
                val bottomSheet = dialog.findViewById<View>(
                    com.google.android.material.R.id.design_bottom_sheet
                )

                bottomSheet?.let {
                    it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT

                    val behavior = BottomSheetBehavior.from(it)
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    behavior.skipCollapsed = true
                }
            }

            dialog.show()
        }
    }

    // ===========================
    // SETUP UI
    // ===========================
    private fun setupPremiumBottomSheet(view: View, dialog: BottomSheetDialog) {

        val tvCurrentBalance = view.findViewById<TextView>(R.id.tvCurrentBalance)
        val rgPremiumOptions = view.findViewById<RadioGroup>(R.id.rgPremiumOptions)
        val rb50 = view.findViewById<RadioButton>(R.id.rb50Coins)
        val rb100 = view.findViewById<RadioButton>(R.id.rb100Coins)
        val rb200 = view.findViewById<RadioButton>(R.id.rb200Coins)
        val btnConfirm = view.findViewById<Button>(R.id.btnConfirmPremium)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancelPremium)

        val userId = auth.currentUser?.uid ?: return

        val selector = PremiumOptionSelector(
            view,
            rgPremiumOptions,
            rb50,
            rb100,
            rb200
        )

        selector.setup()

        // ===========================
        // LOAD COINS
        // ===========================
        database.child("users").child(userId).child("coins")
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    val coins = snapshot.getValue(Int::class.java) ?: 0
                    tvCurrentBalance.text = coins.toString()

                    when {
                        coins >= 200 -> selector.select200()
                        coins >= 100 -> selector.select100()
                        coins >= 50 -> selector.select50()
                        else -> {
                            rb50.isEnabled = false
                            rb100.isEnabled = false
                            rb200.isEnabled = false

                            Toast.makeText(
                                fragment.requireContext(),
                                fragment.getString(R.string.not_enough_coins_for_premium),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    // ===========================
                    // CONFIRM BUTTON
                    // ===========================
                    btnConfirm.setOnClickListener {

                        val selectedId = rgPremiumOptions.checkedRadioButtonId

                        val (cost, planId, durationText) = when (selectedId) {
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

                        if (coins < cost) {
                            Toast.makeText(
                                fragment.requireContext(),
                                fragment.getString(R.string.not_enough_coins),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }

                        activatePremium(cost, planId, durationText)
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

        btnCancel.setOnClickListener { dialog.dismiss() }
    }

    // ===========================
    // ACTIVATE PREMIUM
    // ===========================
    private fun activatePremium(cost: Int, planId: String, duration: String) {

        val userId = auth.currentUser?.uid ?: return
        val userRef = database.child("users").child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                val coins = snapshot.child("coins").getValue(Int::class.java) ?: 0
                val isPremium = snapshot.child("isPremium").getValue(Boolean::class.java) ?: false
                val expiry = snapshot.child("premiumExpiry").getValue(Long::class.java) ?: 0L

                if (coins < cost) return

                val durationMillis = PremiumHelper.getPlanExpiry(planId) - System.currentTimeMillis()

                val newExpiry = if (PremiumHelper.isPremiumActive(isPremium, expiry)) {
                    expiry + durationMillis
                } else {
                    System.currentTimeMillis() + durationMillis
                }

                val updates = mapOf(
                    "coins" to (coins - cost),
                    "isPremium" to true,
                    "premiumExpiry" to newExpiry
                )

                userRef.updateChildren(updates)
                    .addOnSuccessListener {
                        AppOpenAdManager.onPremiumStateChanged()
                        Toast.makeText(
                            fragment.requireContext(),
                            fragment.getString(R.string.premium_activated_for, duration),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(
                            fragment.requireContext(),
                            fragment.getString(R.string.failed_to_activate_premium),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun clear() {
        premiumListener?.let { premiumRef?.removeEventListener(it) }
    }

    fun showPremiumDirect() {
        val dialog = BottomSheetDialog(fragment.requireContext())
        val view = LayoutInflater.from(fragment.requireContext())
            .inflate(R.layout.bottom_sheet_premium, null)

        setupPremiumBottomSheet(view, dialog)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )

            bottomSheet?.let {
                it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT

                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        dialog.show()
    }
}