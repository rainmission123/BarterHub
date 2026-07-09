package com.example.barterhub.ui.helpers

import android.util.Log
import com.example.barterhub.data.models.FeaturedItem
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object TrendingItemsLoader {
    private const val DB_URL = "https://barterhub-3c947-default-rtdb.firebaseio.com/"
    private const val ITEM_SCAN_LIMIT = 100

    fun load(
        limit: Int,
        onSuccess: (List<FeaturedItem>) -> Unit,
        onEmpty: () -> Unit,
        onError: (DatabaseError) -> Unit
    ) {
        val db = FirebaseDatabase.getInstance(DB_URL)

        db.getReference("items")
            .orderByChild("timestamp")
            .limitToLast(ITEM_SCAN_LIMIT)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val activeItems = snapshot.children.mapNotNull { itemSnapshot ->
                        val item = itemSnapshot.getValue(FeaturedItem::class.java) ?: return@mapNotNull null
                        item.itemId = itemSnapshot.key ?: item.itemId
                        item.ownerId = itemSnapshot.child("ownerId").getValue(String::class.java).orEmpty()
                        item.likeCount = itemSnapshot.child("likeCount").asInt() ?: 0
                        item.timestamp = itemSnapshot.child("timestamp").asLong() ?: 0L

                        val isActive = itemSnapshot.child("isActive").asBoolean(defaultValue = true)
                        val isArchived = itemSnapshot.child("isArchived").asBoolean(defaultValue = false)

                        item.takeIf { it.ownerId.isNotBlank() && isActive && !isArchived }
                    }

                    if (activeItems.isEmpty()) {
                        onEmpty()
                        return
                    }

                    loadPremiumOwners(
                        db = db,
                        activeItems = activeItems,
                        limit = limit,
                        onSuccess = onSuccess,
                        onEmpty = onEmpty,
                        onError = onError
                    )
                }

                override fun onCancelled(error: DatabaseError) {
                    onError(error)
                }
            })
    }

    private fun loadPremiumOwners(
        db: FirebaseDatabase,
        activeItems: List<FeaturedItem>,
        limit: Int,
        onSuccess: (List<FeaturedItem>) -> Unit,
        onEmpty: () -> Unit,
        onError: (DatabaseError) -> Unit
    ) {
        db.getReference("public_users")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val now = System.currentTimeMillis()
                    val premiumOwnerIds = snapshot.children.mapNotNull { userSnapshot ->
                        val uid = userSnapshot.key ?: return@mapNotNull null
                        val isPremium = userSnapshot.child("isPremium").asBoolean(defaultValue = false)
                        val premiumExpiry = normalizeExpiryMillis(
                            userSnapshot.child("premiumExpiry").asLong() ?: 0L
                        )
                        uid.takeIf { isPremium && premiumExpiry > now }
                    }.toSet()

                    val premiumItems = activeItems
                        .filter { it.ownerId in premiumOwnerIds }
                        .sortedByLikesThenTime()

                    val trendingItems = if (premiumItems.isNotEmpty()) {
                        premiumItems.take(limit)
                    } else {
                        activeItems.sortedByLikesThenTime().take(limit)
                    }

                    if (trendingItems.isEmpty()) {
                        onEmpty()
                        return
                    }

                    enrichItemsWithOwnerInfo(trendingItems, snapshot)
                    onSuccess(trendingItems)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("TrendingItemsLoader", "Failed to load premium owners: ${error.message}")
                    val fallbackItems = activeItems.sortedByLikesThenTime().take(limit)
                    if (fallbackItems.isNotEmpty()) {
                        onSuccess(fallbackItems)
                    } else {
                        onError(error)
                    }
                }
            })
    }

    private fun enrichItemsWithOwnerInfo(items: List<FeaturedItem>, usersSnapshot: DataSnapshot) {
        for (item in items) {
            val userSnapshot = usersSnapshot.child(item.ownerId)
            if (!userSnapshot.exists()) continue

            val fetchedName =
                userSnapshot.child("fullName").getValue(String::class.java)?.takeIf { it.isNotBlank() }
                    ?: userSnapshot.child("name").getValue(String::class.java)?.takeIf { it.isNotBlank() }
                    ?: userSnapshot.child("username").getValue(String::class.java)?.takeIf { it.isNotBlank() }
                    ?: userSnapshot.child("displayName").getValue(String::class.java)?.takeIf { it.isNotBlank() }

            val fetchedProfile =
                userSnapshot.child("profileImage").getValue(String::class.java)?.takeIf { it.isNotBlank() }
                    ?: userSnapshot.child("profileImageUrl").getValue(String::class.java)?.takeIf { it.isNotBlank() }
                    ?: userSnapshot.child("imageUrl").getValue(String::class.java)?.takeIf { it.isNotBlank() }
                    ?: userSnapshot.child("avatar").getValue(String::class.java)?.takeIf { it.isNotBlank() }

            if (item.ownerName.isBlank() && !fetchedName.isNullOrBlank()) {
                item.ownerName = fetchedName
            }
            if (item.ownerProfileImage.isBlank() && !fetchedProfile.isNullOrBlank()) {
                item.ownerProfileImage = fetchedProfile
            }
        }
    }

    private fun List<FeaturedItem>.sortedByLikesThenTime(): List<FeaturedItem> {
        return sortedWith(
            compareByDescending<FeaturedItem> { it.likeCount }
                .thenByDescending { it.timestamp }
        )
    }

    private fun DataSnapshot.asBoolean(defaultValue: Boolean): Boolean {
        return when (val value = getValue()) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            is Number -> value.toInt() != 0
            else -> defaultValue
        }
    }

    private fun DataSnapshot.asLong(): Long? {
        return when (val value = getValue()) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun DataSnapshot.asInt(): Int? {
        return when (val value = getValue()) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun normalizeExpiryMillis(expiry: Long): Long {
        return if (expiry in 1 until 1_000_000_000_000L) expiry * 1000L else expiry
    }
}
