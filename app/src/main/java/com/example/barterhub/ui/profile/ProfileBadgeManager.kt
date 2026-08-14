package com.example.barterhub.ui.profile

import android.util.Log
import android.view.LayoutInflater
import android.view.View
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
    private var userBadgesRef: DatabaseReference? = null
    private var userBadgesListener: ValueEventListener? = null

    companion object {
        private val ACTIVE_BADGE_IDS = setOf("verified", "first_trade")
    }

    fun loadUserBadges(badgesContainer: LinearLayout) {
        clear()
        val userId = auth.currentUser?.uid ?: return
        Log.d("ProfileDebug", "Setting up active badge listener for user: $userId")

        val ref = database.child("users").child(userId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!fragment.isAdded) return
                recreateBadgesFromUserData(userId, snapshot, badgesContainer)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileDebug", "Error getting user data: ${error.message}")
            }
        }

        userBadgesRef = ref
        userBadgesListener = listener
        ref.addValueEventListener(listener)
    }

    fun clear() {
        userBadgesListener?.let { listener ->
            userBadgesRef?.removeEventListener(listener)
        }
        userBadgesListener = null
        userBadgesRef = null
    }

    private fun recreateBadgesFromUserData(
        userId: String,
        userSnapshot: DataSnapshot,
        badgesContainer: LinearLayout
    ) {
        val verificationStatus = userSnapshot.child("isIDVerified").getValue(String::class.java)
        val badges = mutableMapOf(
            "verified" to (verificationStatus == "verified")
        )

        checkFirstTradeBadge(userId, userSnapshot) { hasFirstTrade ->
            badges["first_trade"] = hasFirstTrade
            displayBadgesFromMap(badges, badgesContainer, hideWhenEmpty = false)
        }
    }

    private fun checkFirstTradeBadge(
        userId: String,
        userSnapshot: DataSnapshot,
        callback: (Boolean) -> Unit
    ) {
        database.child("reviews")
            .orderByChild("reviewedUserId")
            .equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val reviewCount = snapshot.childrenCount.toInt()
                    val rating = userSnapshot.child("rating").getValue(Float::class.java) ?: 0f
                    callback(reviewCount > 0 || rating > 0)
                }

                override fun onCancelled(error: DatabaseError) {
                    val rating = userSnapshot.child("rating").getValue(Float::class.java) ?: 0f
                    callback(rating > 0)
                }
            })
    }

    private fun displayBadgesFromMap(
        badges: Map<String, Boolean>,
        container: LinearLayout,
        hideWhenEmpty: Boolean,
        section: View? = null
    ) {
        if (!fragment.isAdded) return

        val badgeList = filterActiveBadges(badges)
            .filterValues { it }
            .map { (key, _) ->
                val badgeInfo = getBadgeInfo(key)
                Badge(key, badgeInfo.first, badgeInfo.second, true)
            }

        if (badgeList.isNotEmpty()) {
            section?.visibility = View.VISIBLE
            displayBadges(badgeList, container)
        } else if (hideWhenEmpty) {
            container.removeAllViews()
            section?.visibility = View.GONE
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
            badgeIcon.scaleX = 1.0f
            badgeIcon.scaleY = 1.0f
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
        loadPublicUserBadgesForUserId(userId, badgesContainer)
    }

    fun loadPublicUserBadgesForUserId(
        userId: String,
        badgesContainer: LinearLayout,
        badgesSection: View? = null
    ) {
        if (userId.isBlank()) {
            badgesSection?.visibility = View.GONE
            return
        }

        database.child("public_users").child(userId).child("badges")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!fragment.isAdded) return

                    val badges = mutableMapOf<String, Boolean>()
                    snapshot.children.forEach { child ->
                        val key = child.key ?: return@forEach
                        badges[key] = child.getValue(Boolean::class.java) ?: false
                    }

                    displayBadgesFromMap(
                        badges = badges,
                        container = badgesContainer,
                        hideWhenEmpty = badgesSection != null,
                        section = badgesSection
                    )
                }

                override fun onCancelled(error: DatabaseError) {
                    badgesContainer.removeAllViews()
                    badgesSection?.visibility = View.GONE
                }
            })
    }

    private fun filterActiveBadges(badges: Map<String, Boolean>): Map<String, Boolean> {
        return badges.filterKeys { it in ACTIVE_BADGE_IDS }
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
