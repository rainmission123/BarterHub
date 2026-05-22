package com.example.barterhub.ui.profile

import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.barterhub.R
import com.example.barterhub.utils.DateFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ProfileDataLoader(private val fragment: Fragment) {

    private val auth = FirebaseAuth.getInstance()
    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference

    fun loadUserData(
        tvUsernameHandle: TextView,
        tvHeaderUserName: TextView,
        tvUserName: TextView,
        tvUserEmail: TextView,
        tvUserPhone: TextView,
        tvUserBio: TextView,
        tvUserLocation: TextView,
        memberSinceText: TextView,
        ivProfileImage: ImageView,
        tradesCountText: TextView,
        onLoadingComplete: () -> Unit
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onLoadingComplete()
            return
        }

        val userId = currentUser.uid

        Handler(Looper.getMainLooper()).postDelayed({
            database.child("users").child(userId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!fragment.isAdded) {
                            onLoadingComplete()
                            return
                        }

                        if (snapshot.exists()) {
                            val fullName = snapshot.child("fullName")
                                .getValue(String::class.java)
                                ?.trim()
                                .orEmpty()

                            val username = snapshot.child("username")
                                .getValue(String::class.java)
                                ?.trim()
                                .orEmpty()

                            val emailFromDb = snapshot.child("email")
                                .getValue(String::class.java)
                                ?.trim()
                                .orEmpty()

                            val phone = snapshot.child("phoneNumber")
                                .getValue(String::class.java)
                                ?.trim()
                                .orEmpty()

                            val bio = snapshot.child("bio")
                                .getValue(String::class.java)
                                ?.trim()
                                .orEmpty()

                            val address = snapshot.child("address")
                                .getValue(String::class.java)
                                ?.trim()
                                .orEmpty()

                            val addressText = snapshot.child("addressText")
                                .getValue(String::class.java)
                                ?.trim()
                                .orEmpty()

                            val cityMunicipality = snapshot.child("cityMunicipality")
                                .getValue(String::class.java)
                                ?.trim()
                                .orEmpty()

                            val province = snapshot.child("province")
                                .getValue(String::class.java)
                                ?.trim()
                                .orEmpty()

                            val profileImageUrl = snapshot.child("profileImageUrl")
                                .getValue(String::class.java)
                                ?.trim()
                                .orEmpty()

                            val memberSince = snapshot.child("memberSince")
                                .getValue(String::class.java)
                                ?.trim()
                                .orEmpty()

                            val createdAt = snapshot.child("createdAt").getValue(Long::class.java)
                            val tradesCount = snapshot.child("tradesCompleted")
                                .getValue(Int::class.java) ?: 0

                            val displayName = when {
                                fullName.isNotBlank() -> fullName
                                username.isNotBlank() -> username
                                currentUser.displayName?.isNotBlank() == true -> currentUser.displayName!!
                                else -> "No Name"
                            }

                            val displayEmail = when {
                                emailFromDb.isNotBlank() -> emailFromDb
                                currentUser.email?.isNotBlank() == true -> currentUser.email!!
                                else -> "No email set"
                            }

                            val displayPhone = phone.ifBlank {
                                fragment.getString(R.string.no_phone_number)
                            }

                            val displayBio: String = bio.ifBlank {
                                fragment.getString(R.string.no_bio_yet)
                            }

                            val displayLocation = when {
                                addressText.isNotBlank() -> addressText
                                address.isNotBlank() -> address
                                cityMunicipality.isNotBlank() && province.isNotBlank() ->
                                    "$cityMunicipality, $province"
                                province.isNotBlank() -> province
                                else -> fragment.getString(R.string.no_address_set)
                            }

                            tvHeaderUserName.text = displayName
                            tvUsernameHandle.text = if (username.isNotBlank()) "@$username" else "@unknown"
                            tvUserName.text = displayName
                            tvUserEmail.text = displayEmail
                            tvUserPhone.text = displayPhone
                            tvUserBio.text = displayBio
                            tvUserLocation.text = displayLocation

                            val memberSinceValue = when {
                                memberSince.isNotBlank() -> {
                                    DateFormatter.formatMemberSinceWithMonth(memberSince)
                                }
                                createdAt != null && createdAt > 0 -> {
                                    DateFormatter.formatYearMonth(DateFormatter.getCurrentYearMonth())
                                }
                                else -> {
                                    DateFormatter.formatYearMonth(DateFormatter.getCurrentYearMonth())
                                }
                            }

                            memberSinceText.text = memberSinceValue

                            tradesCountText.text = tradesCount.toString()
                            updateTradesColor(tradesCount, tradesCountText)

                            if (profileImageUrl.isNotBlank()) {
                                Glide.with(fragment.requireContext())
                                    .load(profileImageUrl)
                                    .placeholder(R.drawable.ic_profile_placeholder)
                                    .error(R.drawable.ic_profile_placeholder)
                                    .skipMemoryCache(true)
                                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                                    .into(ivProfileImage)
                            } else {
                                ivProfileImage.setImageResource(R.drawable.ic_profile_placeholder)
                            }
                        } else {
                            val displayName = currentUser.displayName ?: "User"
                            val current = DateFormatter.getCurrentYearMonth()

                            tvUsernameHandle.text = "@unknown"
                            tvHeaderUserName.text = displayName
                            tvUserName.text = displayName
                            tvUserEmail.text = currentUser.email ?: ""
                            tvUserPhone.text = fragment.getString(R.string.no_phone_number)
                            tvUserBio.text = fragment.getString(R.string.no_bio_yet)
                            tvUserLocation.text = fragment.getString(R.string.no_address_set)
                            memberSinceText.text = DateFormatter.formatYearMonth(current)

                            tradesCountText.text = "0"
                            updateTradesColor(0, tradesCountText)
                            ivProfileImage.setImageResource(R.drawable.ic_profile_placeholder)
                        }

                        onLoadingComplete()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (fragment.isAdded) {
                            Toast.makeText(
                                fragment.requireContext(),
                                fragment.getString(R.string.failed_to_load_user_data),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        onLoadingComplete()
                    }
                })
        }, 10)
    }

    private fun updateTradesColor(
        tradesCount: Int,
        tradesCountText: TextView
    ) {
        if (!fragment.isAdded || fragment.context == null) return

        val context = fragment.requireContext()

        val tradesColor = if (tradesCount > 0) {
            ContextCompat.getColor(context, R.color.success_green)
        } else {
            ContextCompat.getColor(context, R.color.gray)
        }

        tradesCountText.setTextColor(tradesColor)
    }
}