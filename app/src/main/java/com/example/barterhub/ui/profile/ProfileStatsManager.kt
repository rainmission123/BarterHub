package com.example.barterhub.ui.profile

import android.util.Log
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.barterhub.R
import com.google.firebase.database.*

class ProfileStatsManager(
    private val fragment: Fragment
) {

    private val database: DatabaseReference by lazy {
        FirebaseDatabase.getInstance().reference
    }

    private var userStatsListener: ValueEventListener? = null
    private var referralListener: ValueEventListener? = null
    private var itemsListedListener: ValueEventListener? = null

    fun setupStats(
        userId: String,
        tradesCountText: TextView,
        itemsListedText: TextView,
        likesCountText: TextView,
        referralCountText: TextView,
        onLikesLoaded: ((Int) -> Unit)? = null
    ) {
        setupRealTimeTradeStats(userId, tradesCountText)
        setupItemsListedListener(userId, itemsListedText)
        setupReferralCountListener(userId, referralCountText)
        loadUserLikes(userId, likesCountText, onLikesLoaded)
    }

    private fun setupRealTimeTradeStats(
        userId: String,
        tradesCountText: TextView
    ) {
        clearUserStatsListener(userId)

        userStatsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!fragment.isAdded) return

                val tradesCompleted =
                    snapshot.child("tradesCompleted").getValue(Int::class.java) ?: 0

                tradesCountText.text = tradesCompleted.toString()
                updateStatColor(tradesCountText, tradesCompleted)

                Log.d("ProfileStatsManager", "Trades updated: $tradesCompleted")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileStatsManager", "Trade stats failed: ${error.message}")
            }
        }

        database.child("users").child(userId)
            .addValueEventListener(userStatsListener!!)
    }

    private fun setupItemsListedListener(
        userId: String,
        itemsListedText: TextView
    ) {
        clearItemsListedListener(userId)

        itemsListedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!fragment.isAdded) return

                val count = snapshot.childrenCount.toInt()
                itemsListedText.text = count.toString()
                updateStatColor(itemsListedText, count)

                Log.d("ProfileStatsManager", "Items listed updated: $count")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileStatsManager", "Items listed failed: ${error.message}")
            }
        }

        database.child("items")
            .orderByChild("ownerId")
            .equalTo(userId)
            .addValueEventListener(itemsListedListener!!)
    }

    private fun setupReferralCountListener(
        userId: String,
        referralCountText: TextView
    ) {
        clearReferralListener(userId)

        referralListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!fragment.isAdded) return

                val referralCount = snapshot.childrenCount.toInt()
                referralCountText.text = referralCount.toString()
                updateStatColor(referralCountText, referralCount)

                Log.d("ProfileStatsManager", "Referral count updated: $referralCount")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileStatsManager", "Referral count failed: ${error.message}")
            }
        }

        database.child("users")
            .orderByChild("referredBy")
            .equalTo(userId)
            .addValueEventListener(referralListener!!)
    }

    private fun loadUserLikes(
        userId: String,
        likesCountText: TextView,
        onLikesLoaded: ((Int) -> Unit)?
    ) {
        database.child("items")
            .orderByChild("ownerId")
            .equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(itemsSnapshot: DataSnapshot) {
                    if (!fragment.isAdded) return

                    if (!itemsSnapshot.exists()) {
                        likesCountText.text = "0"
                        updateLikesColor(likesCountText, 0)
                        onLikesLoaded?.invoke(0)
                        Log.d("ProfileStatsManager", "No items found for user: $userId")
                        return
                    }

                    var totalLikes = 0

                    for (itemSnapshot in itemsSnapshot.children) {
                        val itemId = itemSnapshot.key ?: continue

                        val likesSnapshot = itemSnapshot.child("likeCount")
                        val likeCount = when (val value = likesSnapshot.getValue()) {
                            is Long -> value.toInt()
                            is Int -> value
                            is Double -> value.toInt()
                            is String -> value.toIntOrNull() ?: 0
                            else -> 0
                        }

                        if (likeCount > 0) {
                            totalLikes += likeCount
                        } else {
                            // fallback: bilangin sa itemLikes node kung walang likeCount sa item
                            val itemLikesNode = itemSnapshot.ref.root
                                .child("itemLikes")
                                .child(itemId)

                            // note: async fallback per item
                            itemLikesNode.addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (!fragment.isAdded) return

                                    val fallbackLikes = snapshot.childrenCount.toInt()
                                    val currentDisplayed = likesCountText.text.toString().toIntOrNull() ?: 0
                                    val updatedTotal = currentDisplayed + fallbackLikes

                                    likesCountText.text = updatedTotal.toString()
                                    updateLikesColor(likesCountText, updatedTotal)
                                    onLikesLoaded?.invoke(updatedTotal)

                                    Log.d(
                                        "ProfileStatsManager",
                                        "Fallback likes for item $itemId: $fallbackLikes | running total: $updatedTotal"
                                    )
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Log.e(
                                        "ProfileStatsManager",
                                        "Fallback itemLikes load failed for $itemId: ${error.message}"
                                    )
                                }
                            })
                        }
                    }

                    // initial total from items.likeCount
                    likesCountText.text = totalLikes.toString()
                    updateLikesColor(likesCountText, totalLikes)
                    onLikesLoaded?.invoke(totalLikes)

                    Log.d("ProfileStatsManager", "Likes updated from items.likeCount: $totalLikes")
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileStatsManager", "Likes load failed: ${error.message}")
                }
            })
    }

    private fun updateStatColor(textView: TextView, value: Int) {
        if (!fragment.isAdded) return

        val color = when {
            value >= 10 -> ContextCompat.getColor(fragment.requireContext(), R.color.success_green)
            value >= 5 -> ContextCompat.getColor(fragment.requireContext(), R.color.success_green)
            value > 0 -> ContextCompat.getColor(fragment.requireContext(), R.color.success_green)
            else -> ContextCompat.getColor(fragment.requireContext(), R.color.gray)
        }

        textView.setTextColor(color)
    }

    private fun updateLikesColor(textView: TextView, totalLikes: Int) {
        if (!fragment.isAdded) return

        val color = if (totalLikes > 0) {
            ContextCompat.getColor(fragment.requireContext(), R.color.green_500)
        } else {
            ContextCompat.getColor(fragment.requireContext(), R.color.gray)
        }

        textView.setTextColor(color)
    }

    private fun clearUserStatsListener(userId: String) {
        userStatsListener?.let {
            database.child("users").child(userId).removeEventListener(it)
        }
        userStatsListener = null
    }

    private fun clearReferralListener(userId: String) {
        referralListener?.let {
            database.child("users")
                .orderByChild("referredBy")
                .equalTo(userId)
                .removeEventListener(it)
        }
        referralListener = null
    }

    private fun clearItemsListedListener(userId: String) {
        itemsListedListener?.let {
            database.child("items")
                .orderByChild("ownerId")
                .equalTo(userId)
                .removeEventListener(it)
        }
        itemsListedListener = null
    }

    fun clear(userId: String) {
        clearUserStatsListener(userId)
        clearReferralListener(userId)
        clearItemsListedListener(userId)
    }
}