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
        val leaderboardRank = userSnapshot.child("leaderboardRank").getValue(Int::class.java) ?: 0

        val currentBadgesMap = userSnapshot.child("badges").value
        val currentBadges = if (currentBadgesMap is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            currentBadgesMap as? Map<String, Boolean>
        } else {
            null
        }

        val badges = currentBadges?.toMutableMap() ?: mutableMapOf()
        badges.remove("top_trader")

        badges["verified"] = verificationStatus == "verified"
        badges["top_1"] = leaderboardRank == 1
        badges["top_2"] = leaderboardRank == 2
        badges["top_3"] = leaderboardRank == 3
        badges["top_4"] = leaderboardRank == 4
        badges["top_5"] = leaderboardRank == 5
        badges["top_6"] = leaderboardRank == 6
        badges["top_7"] = leaderboardRank == 7
        badges["top_8"] = leaderboardRank == 8
        badges["top_9"] = leaderboardRank == 9
        badges["top_10"] = leaderboardRank == 10

        checkFirstTradeBadge(userId) { hasFirstTrade ->
            badges["first_trade"] = hasFirstTrade

            if (!badges.containsKey("community")) badges["community"] = false
            if (!badges.containsKey("friendly")) badges["friendly"] = false
            if (!badges.containsKey("reliable")) badges["reliable"] = false

            Log.d("ProfileDebug", "🎯 Final badges: $badges")

            val needsUpdate = currentBadges == null ||
                    currentBadges["verified"] != badges["verified"] ||
                    currentBadges["top_1"] != badges["top_1"] ||
                    currentBadges["top_2"] != badges["top_2"] ||
                    currentBadges["top_3"] != badges["top_3"] ||
                    currentBadges["top_4"] != badges["top_4"] ||
                    currentBadges["top_5"] != badges["top_5"] ||
                    currentBadges["top_6"] != badges["top_6"] ||
                    currentBadges["top_7"] != badges["top_7"] ||
                    currentBadges["top_8"] != badges["top_8"] ||
                    currentBadges["top_9"] != badges["top_9"] ||
                    currentBadges["top_10"] != badges["top_10"] ||
                    currentBadges["first_trade"] != hasFirstTrade

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
                Log.d("ProfileDebug", "No changes needed")
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

            // 🔥 FIX SIZE ISSUE
            if (badge.id.startsWith("top_")) {
                badgeIcon.scaleX = 1.5f
                badgeIcon.scaleY = 1.5f
            } else {
                badgeIcon.scaleX = 1.0f
                badgeIcon.scaleY = 1.0f
            }

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
        val (name, defaultIconRes) = when (key) {
            "first_trade" -> Pair("First Trade", R.drawable.ic_badge_first_trade)
            "verified" -> Pair("Verified", R.drawable.ic_badge_verified)
            "top_1" -> Pair("Top 1", R.drawable.ic_badge_top1)
            "top_2" -> Pair("Top 2", R.drawable.ic_badge_top2)
            "top_3" -> Pair("Top 3", R.drawable.ic_badge_top3)
            "top_4" -> Pair("Top 4", R.drawable.ic_badge_top4)
            "top_5" -> Pair("Top 5", R.drawable.ic_badge_top5)
            "top_6" -> Pair("Top 6", R.drawable.ic_badge_top6)
            "top_7" -> Pair("Top 7", R.drawable.ic_badge_top7)
            "top_8" -> Pair("Top 8", R.drawable.ic_badge_top8)
            "top_9" -> Pair("Top 9", R.drawable.ic_badge_top9)
            "top_10" -> Pair("Top 10", R.drawable.ic_badge_top10)
            "community" -> Pair("Community", R.drawable.ic_badge_community)
            "friendly" -> Pair("Friendly", R.drawable.ic_badge_friendly)
            "reliable" -> Pair("Reliable", R.drawable.ic_badge_reliable)
            else -> Pair(
                key.replace("_", " ").replaceFirstChar { it.uppercase() },
                R.drawable.ic_badge_generic
            )
        }

        val iconRes = when (key) {
            "first_trade" -> R.drawable.ic_badge_first_trade
            "verified" -> R.drawable.ic_badge_verified
            "top_1" -> R.drawable.ic_badge_top1
            "top_2" -> R.drawable.ic_badge_top2
            "top_3" -> R.drawable.ic_badge_top3
            "top_4" -> R.drawable.ic_badge_top4
            "top_5" -> R.drawable.ic_badge_top5
            "top_6" -> R.drawable.ic_badge_top6
            "top_7" -> R.drawable.ic_badge_top7
            "top_8" -> R.drawable.ic_badge_top8
            "top_9" -> R.drawable.ic_badge_top9
            "top_10" -> R.drawable.ic_badge_top10
            "community" -> R.drawable.ic_badge_community
            "friendly" -> R.drawable.ic_badge_friendly
            "reliable" -> R.drawable.ic_badge_reliable
            else -> defaultIconRes
        }

        return Pair(name, iconRes)
    }

    fun loadUserBadgesForUserId(userId: String, badgesContainer: LinearLayout) {
        if (userId.isBlank()) return

        database.child("users").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!fragment.isAdded) return
                    displayBadgesFromUserSnapshot(userId, snapshot, badgesContainer)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun displayBadgesFromUserSnapshot(
        userId: String,
        userSnapshot: DataSnapshot,
        badgesContainer: LinearLayout
    ) {
        val verificationStatus = userSnapshot.child("isIDVerified").getValue(String::class.java)
        val leaderboardRank = userSnapshot.child("leaderboardRank").getValue(Int::class.java) ?: 0

        val currentBadgesMap = userSnapshot.child("badges").value
        val currentBadges = if (currentBadgesMap is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            currentBadgesMap as? Map<String, Boolean>
        } else {
            null
        }

        val badges = currentBadges?.toMutableMap() ?: mutableMapOf()
        badges.remove("top_trader")

        badges["verified"] = verificationStatus == "verified"
        badges["top_1"] = leaderboardRank == 1
        badges["top_2"] = leaderboardRank == 2
        badges["top_3"] = leaderboardRank == 3
        badges["top_4"] = leaderboardRank == 4
        badges["top_5"] = leaderboardRank == 5
        badges["top_6"] = leaderboardRank == 6
        badges["top_7"] = leaderboardRank == 7
        badges["top_8"] = leaderboardRank == 8
        badges["top_9"] = leaderboardRank == 9
        badges["top_10"] = leaderboardRank == 10

        checkFirstTradeBadge(userId) { hasFirstTrade ->
            badges["first_trade"] = hasFirstTrade

            if (!badges.containsKey("community")) badges["community"] = false
            if (!badges.containsKey("friendly")) badges["friendly"] = false
            if (!badges.containsKey("reliable")) badges["reliable"] = false

            displayBadgesFromMap(badges, badgesContainer)
        }
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