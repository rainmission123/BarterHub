package com.example.barterhub.ui.profile

import android.os.Handler
import android.os.Looper
import android.util.Log
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
        tvHeaderUserName: TextView,
        tvUserName: TextView,
        tvUserEmail: TextView,
        tvUserPhone: TextView,
        tvUserBio: TextView,
        tvUserLocation: TextView,
        memberSinceText: TextView,
        ivProfileImage: ImageView,
        tradesCountText: TextView,
        successRateText: TextView,
        onLoadingComplete: () -> Unit
    ) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid

            Handler(Looper.getMainLooper()).postDelayed({
                database.child("users").child(userId)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                // Basic user info
                                val username = snapshot.child("username").getValue(String::class.java) ?: "No Name"
                                val email = currentUser.email ?: ""
                                val phone = snapshot.child("phoneNumber").getValue(String::class.java) ?: "No phone number"
                                val bio = snapshot.child("bio").getValue(String::class.java) ?: "No bio yet"
                                val address = snapshot.child("address").getValue(String::class.java) ?: "No address set"
                                val profileImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                                val memberSince = snapshot.child("memberSince").getValue(String::class.java)

                                // Stats (get from Firebase or use defaults)
                                val tradesCount = snapshot.child("tradesCompleted").getValue(Int::class.java) ?: 0
                                val successRate = snapshot.child("successRate").getValue(Int::class.java) ?: 0

                                // Update UI
                                tvHeaderUserName.text = username
                                tvUserName.text = username
                                tvUserEmail.text = email
                                tvUserPhone.text = phone
                                tvUserBio.text = bio
                                tvUserLocation.text = address
                                memberSinceText.text = DateFormatter.formatMemberSinceWithMonth(memberSince)

                                // Update stats
                                tradesCountText.text = tradesCount.toString()
                                successRateText.text = fragment.getString(R.string.percent_format, successRate)

                                // Update colors based on values
                                updateStatsColors(tradesCount, successRate, tradesCountText, successRateText)

                                // Load profile image
                                if (!profileImageUrl.isNullOrEmpty()) {
                                    Glide.with(fragment.requireContext())
                                        .load(profileImageUrl)
                                        .placeholder(R.drawable.ic_profile_placeholder)
                                        .skipMemoryCache(true)
                                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                                        .into(ivProfileImage)
                                } else {
                                    ivProfileImage.setImageResource(R.drawable.ic_profile_placeholder)
                                }
                            } else {
                                // No data - set default values
                                val displayName = currentUser.displayName ?: "User"
                                val current = DateFormatter.getCurrentYearMonth()

                                tvHeaderUserName.text = displayName
                                tvUserName.text = displayName
                                tvUserEmail.text = currentUser.email ?: ""
                                tvUserPhone.text = fragment.getString(R.string.no_phone_number)
                                tvUserBio.text = fragment.getString(R.string.no_bio_yet)
                                tvUserLocation.text = fragment.getString(R.string.no_address_set)
                                memberSinceText.text = DateFormatter.formatYearMonth(current)

                                // Default stats
                                tradesCountText.text = "0"
                                successRateText.text = fragment.getString(R.string.zero_percent)
                                updateStatsColors(0, 0, tradesCountText, successRateText)
                            }
                            onLoadingComplete()
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Toast.makeText(fragment.requireContext(),
                                fragment.getString(R.string.failed_to_load_user_data), Toast.LENGTH_SHORT).show()
                            onLoadingComplete()
                        }
                    })
            }, 10)
        } else {
            onLoadingComplete()
        }
    }

    private fun updateStatsColors(
        tradesCount: Int,
        successRate: Int,
        tradesCountText: TextView,
        successRateText: TextView
    ) {
        if (!fragment.isAdded || fragment.context == null) return

        val context = fragment.requireContext()

        // ✅ Trades - always green if may value
        val tradesColor = if (tradesCount > 0) {
            ContextCompat.getColor(context, R.color.success_green)
        } else {
            ContextCompat.getColor(context, R.color.gray)
        }
        tradesCountText.setTextColor(tradesColor)

        // ✅ Success rate - keep smart coloring (optional)
        val successColor = when {
            successRate >= 90 -> ContextCompat.getColor(context, R.color.success_green)
            successRate >= 70 -> ContextCompat.getColor(context, R.color.success_green)
            successRate > 0 -> ContextCompat.getColor(context, R.color.success_green)
            else -> ContextCompat.getColor(context, R.color.gray)
        }
        successRateText.setTextColor(successColor)
    }

    fun loadUserLikes(userId: String?, onLikesLoaded: (Int) -> Unit) {
        if (userId == null) {
            onLikesLoaded(0)
            return
        }

        database.child("items")
            .orderByChild("ownerId")
            .equalTo(userId)
            .addListenerForSingleValueEvent(object: ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var totalLikes = 0
                    for (itemSnapshot in snapshot.children) {
                        val likeCount = itemSnapshot.child("likeCount").getValue(Int::class.java) ?: 0
                        totalLikes += likeCount
                    }
                    Log.d("ProfileDebug", "📱 Total likes from items: $totalLikes")
                    onLikesLoaded(totalLikes)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileDebug", "Failed to fetch items likes: ${error.message}")
                    onLikesLoaded(0)
                }
            })
    }

    fun setupItemsListedListener(
        userId: String,
        itemsListedText: TextView
    ) {
        Log.d("ProfileDebug", "🔄 Setting up ITEMS LISTED listener for user: $userId")

        database.child("items")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!fragment.isAdded || fragment.context == null) return

                    var itemsListedCount = 0
                    if (snapshot.exists()) {
                        for (itemSnapshot in snapshot.children) {
                            val ownerId = itemSnapshot.child("ownerId").getValue(String::class.java)
                            if (ownerId == userId) {
                                itemsListedCount++
                            }
                        }
                    }

                    Log.d("ProfileDebug", "📦 Items listed: $itemsListedCount")
                    itemsListedText.text = itemsListedCount.toString()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileFragment", "❌ Items listed listener cancelled: ${error.message}")
                    itemsListedText.text = "0"
                }
            })
    }

    // NEW METHOD: Load trades count from Firebase
    fun loadTradesCount(userId: String, onTradesLoaded: (Int) -> Unit) {
        // Check in user's profile first
        database.child("users").child(userId).child("tradesCompleted")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val tradesFromProfile = snapshot.getValue(Int::class.java)

                    if (tradesFromProfile != null && tradesFromProfile > 0) {
                        onTradesLoaded(tradesFromProfile)
                    } else {
                        // If not in profile, calculate from trade history
                        calculateTradesFromHistory(userId, onTradesLoaded)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    calculateTradesFromHistory(userId, onTradesLoaded)
                }
            })
    }

    private fun calculateTradesFromHistory(userId: String, callback: (Int) -> Unit) {
        // Count from trade history where user is involved
        database.child("trades")
            .orderByChild("participants/$userId")
            .equalTo(true)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val tradesCount = snapshot.childrenCount.toInt()
                    callback(tradesCount)
                }

                override fun onCancelled(error: DatabaseError) {
                    callback(0)
                }
            })
    }

    // NEW METHOD: Calculate success rate
    fun calculateSuccessRate(userId: String, onSuccessRateCalculated: (Int) -> Unit) {
        // For now, use a simple calculation or get from user profile
        database.child("users").child(userId).child("successRate")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val successRate = snapshot.getValue(Int::class.java) ?: 0
                    onSuccessRateCalculated(successRate)
                }

                override fun onCancelled(error: DatabaseError) {
                    onSuccessRateCalculated(0)
                }
            })
    }
}