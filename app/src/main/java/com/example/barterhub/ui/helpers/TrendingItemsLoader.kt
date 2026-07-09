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
                        item.ownerId = itemSnapshot.firstString(
                            "ownerId",
                            "userId",
                            "sellerId",
                            "ownerUserId",
                            "postedBy"
                        )
                        item.ownerName = item.ownerName.takeUnless { it.isGenericDisplayName() }
                            ?: itemSnapshot.firstString(
                                "ownerName",
                                "username",
                                "ownerUsername",
                                "sellerName",
                                "sellerUsername",
                                "postedByName"
                            )
                        item.ownerProfileImage = item.ownerProfileImage.ifBlank {
                            itemSnapshot.firstString(
                                "ownerProfileImage",
                                "ownerProfileImageUrl",
                                "profileImage",
                                "profileImageUrl",
                                "sellerProfileImage",
                                "sellerProfileImageUrl"
                            )
                        }
                        item.location = itemSnapshot.resolveItemLocation(item.location)
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
        val ownerIds = activeItems.map { it.ownerId }.filter { it.isNotBlank() }.distinct()

        loadOwnerProfiles(
            db = db,
            ownerIds = ownerIds,
            onComplete = { ownerProfiles ->
                val now = System.currentTimeMillis()
                val premiumOwnerIds = ownerProfiles
                    .filterValues { it.isPremium && it.premiumExpiry > now }
                    .keys

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
                    return@loadOwnerProfiles
                }

                enrichItemsWithOwnerInfo(trendingItems, ownerProfiles)
                onSuccess(trendingItems)
            },
            onError = { error ->
                Log.e("TrendingItemsLoader", "Failed to load owner profiles: ${error.message}")
                val fallbackItems = activeItems.sortedByLikesThenTime().take(limit)
                if (fallbackItems.isNotEmpty()) {
                    onSuccess(fallbackItems)
                } else {
                    onError(error)
                }
            }
        )
    }

    private fun loadOwnerProfiles(
        db: FirebaseDatabase,
        ownerIds: List<String>,
        onComplete: (Map<String, OwnerPublicInfo>) -> Unit,
        onError: (DatabaseError) -> Unit
    ) {
        if (ownerIds.isEmpty()) {
            onComplete(emptyMap())
            return
        }

        val profiles = mutableMapOf<String, OwnerPublicInfo>()
        var pendingCount = ownerIds.size
        var firstError: DatabaseError? = null

        fun finishOne() {
            pendingCount -= 1
            if (pendingCount == 0) {
                if (profiles.isEmpty() && firstError != null) {
                    onError(firstError!!)
                } else {
                    onComplete(profiles)
                }
            }
        }

        ownerIds.forEach { ownerId ->
            db.getReference("public_users")
                .child(ownerId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            profiles[ownerId] = snapshot.toOwnerPublicInfo()
                        }
                        finishOne()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (firstError == null) firstError = error
                        Log.e("TrendingItemsLoader", "Failed to load owner $ownerId: ${error.message}")
                        finishOne()
                    }
                })
        }
    }

    private fun enrichItemsWithOwnerInfo(items: List<FeaturedItem>, ownerProfiles: Map<String, OwnerPublicInfo>) {
        for (item in items) {
            val ownerProfile = ownerProfiles[item.ownerId] ?: continue

            if (item.ownerName.isGenericDisplayName() && ownerProfile.name.isNotBlank()) {
                item.ownerName = ownerProfile.name
            }
            if (item.ownerProfileImage.isBlank() && ownerProfile.profileImage.isNotBlank()) {
                item.ownerProfileImage = ownerProfile.profileImage
            }
            if (item.location.isGenericLocation() && ownerProfile.location.isNotBlank()) {
                item.location = ownerProfile.location
            }
        }
    }

    private fun DataSnapshot.toOwnerPublicInfo(): OwnerPublicInfo {
        return OwnerPublicInfo(
            name = firstString("fullName", "name", "username", "displayName"),
            profileImage = firstString("profileImage", "profileImageUrl", "imageUrl", "avatar"),
            location = resolveUserLocation(),
            isPremium = child("isPremium").asBoolean(defaultValue = false),
            premiumExpiry = normalizeExpiryMillis(child("premiumExpiry").asLong() ?: 0L)
        )
    }

    private fun DataSnapshot.resolveItemLocation(existingLocation: String): String {
        val directLocation = existingLocation.takeUnless { it.isGenericLocation() }
            ?: firstLocationString(
                "location",
                "itemLocation",
                "pickupLocation",
                "addressText",
                "address"
            )
        if (directLocation.isNotBlank()) return directLocation

        return joinLocationParts(
            firstString("cityMunicipality", "city"),
            firstString("province")
        )
    }

    private fun DataSnapshot.resolveUserLocation(): String {
        val directLocation = firstLocationString("location", "addressText", "address")
        if (directLocation.isNotBlank()) return directLocation

        return joinLocationParts(
            firstString("cityMunicipality", "city"),
            firstString("province")
        )
    }

    private fun DataSnapshot.firstString(vararg keys: String): String {
        for (key in keys) {
            val value = child(key).getValue(String::class.java)?.trim().orEmpty()
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun DataSnapshot.firstLocationString(vararg keys: String): String {
        for (key in keys) {
            val value = child(key).getValue(String::class.java)?.trim().orEmpty()
            if (!value.isGenericLocation()) return value
        }
        return ""
    }

    private fun joinLocationParts(city: String, province: String): String {
        return listOf(city, province)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }

    private fun String.isGenericDisplayName(): Boolean {
        val normalized = trim().lowercase()
        return normalized.isBlank() ||
            normalized == "user" ||
            normalized == "unknown" ||
            normalized == "unknown user" ||
            normalized == "barterhub user"
    }

    private fun String.isGenericLocation(): Boolean {
        val normalized = trim().lowercase()
        return normalized.isBlank() ||
            normalized == "location not specified" ||
            normalized == "not specified" ||
            normalized == "not provided" ||
            normalized == "unknown" ||
            normalized == "manila, philippines"
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

    private data class OwnerPublicInfo(
        val name: String,
        val profileImage: String,
        val location: String,
        val isPremium: Boolean,
        val premiumExpiry: Long
    )
}
