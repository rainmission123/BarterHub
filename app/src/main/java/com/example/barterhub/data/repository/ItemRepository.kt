package com.example.barterhub.data.repository

import android.util.Log
import com.example.barterhub.data.models.FeaturedItem
import com.google.firebase.database.*

class ItemRepository {

    private val database = FirebaseDatabase
        .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
        .getReference("items")

    private val userRef = FirebaseDatabase
        .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
        .getReference("users")


    fun fetchAllItems(callback: (List<FeaturedItem>) -> Unit) {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val items = mutableListOf<FeaturedItem>()

                for (child in snapshot.children) {
                    if (!child.child("title").exists()) continue
                    val item = mapChildToFeaturedItem(child)
                    items.add(item)
                }

                if (items.isEmpty()) {
                    callback(emptyList())
                    return
                }

                // Fetch owner data
                var completed = 0
                val updatedItems = mutableListOf<FeaturedItem>()

                for (item in items) {
                    if (item.ownerId.isNotEmpty()) {
                        userRef.child(item.ownerId)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(userSnap: DataSnapshot) {
                                    val name = userSnap.child("name").getValue(String::class.java) ?: "Unknown"
                                    val profile = userSnap.child("profileImageUrl").getValue(String::class.java) ?: ""

                                    updatedItems.add(
                                        item.copy(
                                            ownerName = name,
                                            ownerProfileImage = profile
                                        )
                                    )

                                    completed++
                                    if (completed == items.size) callback(updatedItems)
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    updatedItems.add(item)
                                    completed++
                                    if (completed == items.size) callback(updatedItems)
                                }
                            })
                    } else {
                        updatedItems.add(item)
                        completed++
                        if (completed == items.size) callback(updatedItems)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ItemRepository", "Failed to fetch items", error.toException())
                callback(emptyList())
            }
        })
    }


    // 🔹 Fetch items by category safely
    fun fetchItemsByCategory(category: String, callback: (List<FeaturedItem>) -> Unit) {
        database.orderByChild("category").equalTo(category)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val items = mutableListOf<FeaturedItem>()
                    for (child in snapshot.children) {
                        if (!child.child("title").exists()) continue
                        val item = mapChildToFeaturedItem(child)
                        items.add(item)
                    }
                    callback(items)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ItemRepository", "Failed to fetch items by category", error.toException())
                    callback(emptyList())
                }
            })
    }


    // 🔹 Helper function
    private fun mapChildToFeaturedItem(child: DataSnapshot): FeaturedItem {
        val priceValue = (child.child("price").value as? Number)?.toDouble()
            ?: (child.child("price").getValue(String::class.java)?.toDoubleOrNull() ?: 0.0)

        val latitudeValue = (child.child("latitude").value as? Number)?.toDouble()
            ?: (child.child("latitude").getValue(String::class.java)?.toDoubleOrNull() ?: 0.0)

        val longitudeValue = (child.child("longitude").value as? Number)?.toDouble()
            ?: (child.child("longitude").getValue(String::class.java)?.toDoubleOrNull() ?: 0.0)

        val ownerId = child.child("ownerId").getValue(String::class.java) ?: ""

        return FeaturedItem(
            title = child.child("title").getValue(String::class.java) ?: "",
            description = child.child("description").getValue(String::class.java) ?: "",
            imageUrls = child.child("imageUrls").getValue(String::class.java) ?: "",
            price = priceValue,
            originalPrice = child.child("originalPrice").getValue(String::class.java) ?: "",
            itemId = child.child("itemId").getValue(String::class.java) ?: "",
            ownerId = ownerId,
            ownerName = "", // temporary — kukunin sa "users"
            ownerProfileImage = "",
            location = child.child("location").getValue(String::class.java) ?: "",
            category = child.child("category").getValue(String::class.java) ?: "",
            condition = child.child("condition").getValue(String::class.java) ?: "",
            latitude = latitudeValue,
            longitude = longitudeValue
        )
    }
}
