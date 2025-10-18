package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.barterhub.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import de.hdodenhof.circleimageview.CircleImageView

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var tvUserPhone: TextView
    private lateinit var tvUserBio: TextView
    private lateinit var tvUserLocation: TextView
    private lateinit var ivProfileImage: CircleImageView
    private lateinit var bgOnlineStatus: ImageView
    private lateinit var tvHeaderUserName: TextView

    // ✅ FIXED: Changed to TextView since your XML uses TextView for idVerificationStatus
    private lateinit var idVerificationStatus: MaterialTextView

    private lateinit var verificationStatusListener: ValueEventListener

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        // Initialize ALL UI elements
        tvUserName = view.findViewById(R.id.nameTextView)
        tvUserEmail = view.findViewById(R.id.emailTextView)
        tvUserPhone = view.findViewById(R.id.phoneTextView)
        tvUserBio = view.findViewById(R.id.bioTextView)
        tvUserLocation = view.findViewById(R.id.locationTextView)
        ivProfileImage = view.findViewById(R.id.profileImage)
        bgOnlineStatus = view.findViewById(R.id.bgOnlineStatus)
        tvHeaderUserName = view.findViewById(R.id.userNameText)

        // ✅ FIXED: Correct initialization as TextView
        idVerificationStatus = view.findViewById(R.id.idVerificationStatus)

        loadUserData()
        setOnlineStatus(true)
        setupClickListeners(view)
        setupVerificationStatusListener()
    }

    // Real-time verification status listener
    private fun setupVerificationStatusListener() {
        val currentUser = auth.currentUser
        if (currentUser == null) return

        val userRef = database.child("users").child(currentUser.uid)

        verificationStatusListener = object : ValueEventListener {
            @SuppressLint("SetTextI18n")
            override fun onDataChange(snapshot: DataSnapshot) {
                val verificationStatus = snapshot.child("isIDVerified").getValue(String::class.java)
                updateVerificationUI(verificationStatus)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileFragment", "Verification status listener cancelled: ${error.message}")
            }
        }

        userRef.addValueEventListener(verificationStatusListener)
    }

    // ✅ FIXED: Updated verification UI function for TextView
    @SuppressLint("SetTextI18n")
    private fun updateVerificationUI(status: String?) {
        val successColor = ContextCompat.getColor(requireContext(), R.color.success)
        val grayColor = ContextCompat.getColor(requireContext(), R.color.gray)
        val orangeColor = ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)
        val redColor = ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)

        when (status) {
            "verified" -> {
                idVerificationStatus.text = "Verified"
                idVerificationStatus.setTextColor(successColor)
                idVerificationStatus.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_check_circle, 0, 0, 0
                )
                // Make it non-clickable when verified
                idVerificationStatus.isClickable = false
            }
            "pending" -> {
                idVerificationStatus.text = "Under Review"
                idVerificationStatus.setTextColor(orangeColor)
                idVerificationStatus.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_info, 0, 0, 0
                )
                // Make it non-clickable when pending
                idVerificationStatus.isClickable = false
            }
            "rejected" -> {
                idVerificationStatus.text = "Not Verified"
                idVerificationStatus.setTextColor(redColor)
                idVerificationStatus.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_info, 0, 0, 0
                )
                // Make it clickable when rejected so user can re-verify
                idVerificationStatus.isClickable = true
            }
            else -> {
                idVerificationStatus.text = "Not Verified"
                idVerificationStatus.setTextColor(grayColor)
                idVerificationStatus.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_info, 0, 0, 0
                )
                // Make it clickable when not verified
                idVerificationStatus.isClickable = true
            }
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid

            // Load from Realtime Database
            database.child("users").child(userId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    @SuppressLint("SetTextI18n")
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val username = snapshot.child("username").getValue(String::class.java) ?: "No Name"
                            val email = currentUser.email ?: ""
                            val phone = snapshot.child("phoneNumber").getValue(String::class.java) ?: "No phone number"
                            val bio = snapshot.child("bio").getValue(String::class.java) ?: "No bio yet"
                            val address = snapshot.child("address").getValue(String::class.java) ?: "No address set"
                            val profileImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)

                            tvHeaderUserName.text = username
                            tvUserName.text = username
                            tvUserEmail.text = email
                            tvUserPhone.text = phone
                            tvUserBio.text = bio
                            tvUserLocation.text = address

                            if (!profileImageUrl.isNullOrEmpty()) {
                                Glide.with(requireContext())
                                    .load(profileImageUrl)
                                    .placeholder(R.drawable.ic_profile_placeholder)
                                    .skipMemoryCache(true)
                                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                                    .into(ivProfileImage)

                            } else {
                                ivProfileImage.setImageResource(R.drawable.ic_profile_placeholder)
                            }
                        } else {
                            // No data
                            val displayName = currentUser.displayName ?: "User"
                            tvHeaderUserName.text = displayName
                            tvUserName.text = displayName
                            tvUserEmail.text = currentUser.email ?: ""
                            tvUserPhone.text = "No phone number"
                            tvUserBio.text = "No bio yet"
                            tvUserLocation.text = "No address set"
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(requireContext(), "Failed to load user data", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    private fun setOnlineStatus(isOnline: Boolean) {
        val backgroundResource = if (isOnline) R.drawable.bg_online else R.drawable.bg_offline
        bgOnlineStatus.setBackgroundResource(backgroundResource)
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<MaterialButton>(R.id.tradeHistoryButton).setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_tradeHistoryFragment)
        }

        view.findViewById<MaterialButton>(R.id.favoritesButton).setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_favoritesFragment)
        }

        view.findViewById<MaterialButton>(R.id.myListingsButton).setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_myListingsFragment)
        }

        view.findViewById<MaterialButton>(R.id.editProfileButton).setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        // ✅ FIXED: Use the TextView for click listener instead of MaterialButton
        idVerificationStatus.setOnClickListener {
            // Only navigate if status is "rejected" or null (not verified)
            val currentUser = auth.currentUser
            if (currentUser != null) {
                database.child("users").child(currentUser.uid).child("isIDVerified")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val status = snapshot.getValue(String::class.java)
                            if (status == "rejected" || status == null) {
                                findNavController().navigate(R.id.action_profileFragment_to_uploadIdFragment)
                            }
                            // If status is "pending" or "verified", do nothing (not clickable)
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("ProfileFragment", "Error checking verification status: ${error.message}")
                        }
                    })
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove listener to prevent memory leaks
        val currentUser = auth.currentUser
        if (currentUser != null && ::verificationStatusListener.isInitialized) {
            database.child("users").child(currentUser.uid)
                .removeEventListener(verificationStatusListener)
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }
}