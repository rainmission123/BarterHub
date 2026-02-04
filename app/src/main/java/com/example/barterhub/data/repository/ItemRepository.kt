package com.example.barterhub.data.repository

import android.util.Log
import com.example.barterhub.data.models.FeaturedItem
import com.google.firebase.database.*


class ItemRepository {

    private val database = FirebaseDatabase
        .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
        .getReference("items")
    fun fetchAllItems(callback: (List<FeaturedItem>) -> Unit) {
        Log.d("ItemRepository", "🔥 Fetching items sorted by timestamp (newest first)...")

        // Firebase query: order by timestamp descending
        // Note: Firebase doesn't have native DESC order, need to reverse in code
        database.orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val items = mutableListOf<FeaturedItem>()

                    // Convert to list and reverse for newest first
                    snapshot.children.reversed().forEach { child ->
                        if (!child.child("title").exists()) return@forEach

                        val item = mapChildToFeaturedItem(child)
                        item?.let { items.add(it) }
                    }

                    Log.d("ItemRepository", "✅ Loaded ${items.size} items (newest first)")
                    callback(items)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ItemRepository", "Error: ${error.message}")
                    callback(emptyList())
                }
            })
    }
    fun fetchItemsByCategory(category: String, callback: (List<FeaturedItem>) -> Unit) {
        database.orderByChild("category").equalTo(category)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val items = mutableListOf<FeaturedItem>()

                    // ✅ REVERSE FOR NEWEST FIRST
                    snapshot.children.reversed().forEach { child ->
                        if (!child.child("title").exists()) return@forEach
                        mapChildToFeaturedItem(child)?.let { items.add(it) }
                    }

                    Log.d("ItemRepository", "✅ Category '$category': ${items.size} items (newest first)")
                    callback(items)
                }

                override fun onCancelled(error: DatabaseError) {
                    callback(emptyList())
                }
            })
    }
    private fun mapChildToFeaturedItem(child: DataSnapshot): FeaturedItem? {
        return try {
            // ✅ DEBUG RAW DATA
            Log.d("MapDebug", "=== Mapping item ${child.key} ===")

            // Get title
            val title = child.child("title").getValue(String::class.java) ?: ""
            Log.d("MapDebug", "Title: '$title'")

            // Get description - FIXED VERSION
            val rawDescription = child.child("description").value
            Log.d("MapDebug", "Raw description value: '$rawDescription'")
            Log.d("MapDebug", "Raw description type: ${rawDescription?.javaClass?.simpleName}")

            val description = when (rawDescription) {
                null -> {
                    Log.d("MapDebug", "Description is NULL")
                    ""
                }
                is String -> {
                    // Clean the string
                    val cleaned = rawDescription.trim()
                    Log.d("MapDebug", "Description cleaned: '$cleaned' (length: ${cleaned.length})")
                    cleaned
                }
                else -> {
                    // Convert any other type to string
                    val asString = rawDescription.toString().trim()
                    Log.d("MapDebug", "Description converted: '$asString'")
                    asString
                }
            }

            // Get price
            val priceValue = child.child("price").value?.let {
                when (it) {
                    is Number -> it.toDouble()
                    is String -> it.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }
            } ?: 0.0
            Log.d("MapDebug", "Price: $priceValue")

            // Get displayPrice (if exists)
            val displayPrice = if (child.child("displayPrice").exists()) {
                child.child("displayPrice").getValue(String::class.java) ?: ""
            } else {
                ""
            }

            // Get image URLs
            val imageUrls = getImageUrls(child)
            Log.d("MapDebug", "Image URLs: '$imageUrls'")

            // Get timestamp
            val timestampValue = child.child("timestamp").value?.let {
                when (it) {
                    is Number -> it.toLong()
                    is String -> it.toLongOrNull() ?: 0L
                    else -> System.currentTimeMillis()
                }
            } ?: 0L

            // Get like count
            val likeCountValue = child.child("likeCount").value?.let {
                when (it) {
                    is Number -> it.toInt()
                    is String -> it.toIntOrNull() ?: 0
                    else -> countLikesFromLikedBy(child)
                }
            } ?: 0

            // Get owner ID
            val ownerId = child.child("ownerId").getValue(String::class.java) ?: ""
            Log.d("MapDebug", "Owner ID: '$ownerId'")

            // Create item WITHOUT owner profile image from items
            FeaturedItem(
                itemId = child.child("itemId").getValue(String::class.java) ?: child.key ?: "",
                title = title,
                description = description,
                category = child.child("category").getValue(String::class.java) ?: "",
                condition = child.child("condition").getValue(String::class.java) ?: "",
                price = priceValue,
                displayPrice = displayPrice,
                imageUrls = imageUrls,
                location = child.child("location").getValue(String::class.java) ?: "",
                latitude = child.child("latitude").getValue(Double::class.java) ?: 0.0,
                longitude = child.child("longitude").getValue(Double::class.java) ?: 0.0,
                ownerId = ownerId,
                ownerName = "", // Leave empty - ViewModel will fill
                ownerProfileImage = "", // Leave empty - ViewModel will fill from users collection
                timestamp = timestampValue,
                likeCount = likeCountValue
            )

        } catch (e: Exception) {
            Log.e("ItemRepository", "❌ mapChildToFeaturedItem failed for ${child.key}: ${e.message}")
            null
        }
    }

    // 🔹 Helper to get image URLs
    private fun getImageUrls(child: DataSnapshot): String {
        return try {
            when {
                child.child("imageUrls").exists() -> {
                    child.child("imageUrls").getValue(String::class.java) ?: ""
                }
                child.child("photoUrls").exists() -> {
                    val urlList = child.child("photoUrls").getValue(object : GenericTypeIndicator<List<String>>() {})
                    urlList?.joinToString(",") ?: ""
                }
                else -> ""
            }
        } catch (e: Exception) {
            Log.w("ItemRepository", "Image URL error: ${e.message}")
            ""
        }
    }

    // 🔹 Count likes from 'likedBy' map
    private fun countLikesFromLikedBy(child: DataSnapshot): Int {
        return try {
            val likedBy = child.child("likedBy").value as? Map<*, *>
            likedBy?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }
}