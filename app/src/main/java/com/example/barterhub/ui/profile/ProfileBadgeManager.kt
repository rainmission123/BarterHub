package com.example.barterhub.ui.profile

import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.barterhub.R
import com.example.barterhub.data.models.Badge
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ProfileBadgeManager(private val fragment: Fragment) {

    private val auth = FirebaseAuth.getInstance()
    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference

    fun loadUserBadges(badgesContainer: LinearLayout) {
        val userId = auth.currentUser?.uid ?: return
        Log.d("ProfileDebug", "🔄 Setting up real-time badges listener for user: $userId")

        database.child("users").child(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!fragment.isAdded) return
                    Log.d("ProfileDebug", "📊 User data updated, recalculating badges...")
                    recreateBadgesFromUserData(userId, snapshot, badgesContainer)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileDebug", "Error getting user data: ${error.message}")
                }
            })
    }

    private fun recreateBadgesFromUserData(
        userId: String,
        userSnapshot: DataSnapshot,
        badgesContainer: LinearLayout
    ) {
        Log.d("ProfileDebug", "🔄 Recreating badges from user data")

        val verificationStatus = userSnapshot.child("isIDVerified").getValue(String::class.java)
        val rating = userSnapshot.child("rating").getValue(Float::class.java) ?: 0f
        val reviewsCount = userSnapshot.child("reviewsCount").getValue(Int::class.java) ?: 0

        // FIXED: Safer cast for badges
        val currentBadgesMap = userSnapshot.child("badges").value
        val currentBadges = if (currentBadgesMap is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            currentBadgesMap as? Map<String, Boolean>
        } else {
            null
        }

        val badges = currentBadges?.toMutableMap() ?: mutableMapOf()

        // Update badges
        badges["verified"] = verificationStatus == "verified"
        badges["top_trader"] = rating >= 4.5 && reviewsCount >= 5

        checkFirstTradeBadge(userId) { hasFirstTrade ->
            badges["first_trade"] = hasFirstTrade

            // Initialize other badges
            if (!badges.containsKey("community")) badges["community"] = false
            if (!badges.containsKey("friendly")) badges["friendly"] = false
            if (!badges.containsKey("reliable")) badges["reliable"] = false

            Log.d("ProfileDebug", "🎯 Final badges: $badges")

            // Save if changed
            val needsUpdate = currentBadges == null ||
                    currentBadges["verified"] != badges["verified"] ||
                    currentBadges["top_trader"] != badges["top_trader"] ||
                    currentBadges?.get("first_trade") != hasFirstTrade

            if (needsUpdate) {
                database.child("users").child(userId).child("badges")
                    .setValue(badges)
                    .addOnSuccessListener {
                        Log.d("ProfileDebug", "✅ Updated badges")
                        displayBadgesFromMap(badges, badgesContainer)
                    }
                    .addOnFailureListener { e ->
                        Log.e("ProfileDebug", "❌ Failed to update badges: ${e.message}")
                        displayBadgesFromMap(badges, badgesContainer)
                    }
            } else {
                Log.d("ProfileDebug", "   No changes needed")
                displayBadgesFromMap(badges, badgesContainer)
            }
        }
    }

    private fun checkFirstTradeBadge(userId: String, callback: (Boolean) -> Unit) {
        database.child("reviews")
            .orderByChild("reviewedUserId")
            .equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val reviewCount = snapshot.childrenCount.toInt()
                    database.child("users").child(userId).child("rating")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(ratingSnapshot: DataSnapshot) {
                                val rating = ratingSnapshot.getValue(Float::class.java) ?: 0f
                                val hasFirstTrade = reviewCount > 0 || rating > 0
                                callback(hasFirstTrade)
                            }

                            override fun onCancelled(error: DatabaseError) {
                                callback(reviewCount > 0)
                            }
                        })
                }

                override fun onCancelled(error: DatabaseError) {
                    callback(false)
                }
            })
    }

    private fun displayBadgesFromMap(badges: Map<String, Boolean>, container: LinearLayout) {
        if (!fragment.isAdded) return

        val badgeList = mutableListOf<Badge>()
        badges.forEach { (key, value) ->
            if (value) {
                val badgeInfo = getBadgeInfo(key)
                badgeList.add(Badge(key, badgeInfo.first, badgeInfo.second, true))
            }
        }

        if (badgeList.isNotEmpty()) {
            displayBadges(badgeList, container)
        } else {
            showNoBadgesMessage(container)
        }
    }

    private fun displayBadges(badges: List<Badge>, container: LinearLayout) {
        container.removeAllViews()

        badges.forEach { badge ->
            val badgeView = LayoutInflater.from(fragment.requireContext())
                .inflate(R.layout.item_badge, container, false)
            val badgeIcon = badgeView.findViewById<ImageView>(R.id.badgeIcon)
            val badgeText = badgeView.findViewById<TextView>(R.id.badgeText)

            badgeIcon.setImageResource(badge.iconResId)
            badgeText.text = badge.name

            badgeIcon.alpha = if (badge.achieved) 1.0f else 0.4f
            badgeText.alpha = if (badge.achieved) 1.0f else 0.4f

            badgeView.setOnClickListener {
                showBadgeInfo(badge)
            }

            container.addView(badgeView)
        }
    }

    private fun showNoBadgesMessage(container: LinearLayout) {
        container.removeAllViews()
        val noBadgesView = TextView(fragment.requireContext()).apply {
            text = fragment.getString(R.string.no_badges_message)
            textSize = 12f
            setTextColor(ContextCompat.getColor(fragment.requireContext(), R.color.gray))
        }
        container.addView(noBadgesView)
    }

    private fun getBadgeInfo(key: String): Pair<String, Int> {
        // FIXED: Proper when statement structure
        val (name, defaultIconRes) = when (key) {
            "first_trade" -> Pair("First Trade", R.drawable.ic_badge_first_trade)
            "verified" -> Pair("Verified", R.drawable.ic_badge_verified)
            "top_trader" -> Pair("Top Trader", R.drawable.ic_badge_top_trader)
            "community" -> Pair("Community", R.drawable.ic_badge_community)
            "friendly" -> Pair("Friendly", R.drawable.ic_badge_friendly)
            "reliable" -> Pair("Reliable", R.drawable.ic_badge_reliable)
            else -> Pair(
                key.replace("_", " ").replaceFirstChar { it.uppercase() },
                R.drawable.ic_badge_generic
            )
        }

        // FIXED: Use direct resource IDs instead of getIdentifier
        val iconRes = when (key) {
            "first_trade" -> R.drawable.ic_badge_first_trade
            "verified" -> R.drawable.ic_badge_verified
            "top_trader" -> R.drawable.ic_badge_top_trader
            "community" -> R.drawable.ic_badge_community
            "friendly" -> R.drawable.ic_badge_friendly
            "reliable" -> R.drawable.ic_badge_reliable
            else -> defaultIconRes
        }

        return Pair(name, iconRes)
    }

    private fun showBadgeInfo(badge: Badge) {
        val message = if (badge.achieved) {
            fragment.getString(R.string.badge_earned_message, badge.name)
        } else {
            fragment.getString(R.string.badge_unlock_message, badge.name)
        }
        Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}