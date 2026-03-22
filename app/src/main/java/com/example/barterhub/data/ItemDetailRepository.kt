package com.example.barterhub.data

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class ItemDetailRepository {

    private val database: FirebaseDatabase = Firebase.database("https://barterhub-3c947-default-rtdb.firebaseio.com/")
    private val itemsRef = database.getReference("items")
    private val usersRef = database.getReference("users")
    private val favoritesRef = database.getReference("favorites")
    private val notificationsRef = database.getReference("notifications")

    fun fetchItemDetails(itemId: String, callback: (Item?) -> Unit) {
        itemsRef.child(itemId).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                callback(null)
                return@addOnSuccessListener
            }

            val item = Item(
                title = snapshot.child("title").getValue(String::class.java) ?: "",
                description = snapshot.child("description").getValue(String::class.java) ?: "",
                price = snapshot.child("price").value,
                category = snapshot.child("category").getValue(String::class.java) ?: "",
                location = snapshot.child("location").getValue(String::class.java) ?: "",
                timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L,
                imageUrls = parseImageUrls(snapshot),
                latitude = parseCoordinate(snapshot.child("latitude").value),
                longitude = parseCoordinate(snapshot.child("longitude").value),
                ownerId = snapshot.child("ownerId").getValue(String::class.java) ?: ""
            )
            callback(item)
        }.addOnFailureListener {
            callback(null)
        }
    }

    fun fetchOwnerInfo(ownerId: String, callback: (Owner?) -> Unit) {
        usersRef.child(ownerId).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                callback(null)
                return@addOnSuccessListener
            }

            val owner = Owner(
                name = snapshot.child("fullName").getValue(String::class.java)
                    ?: snapshot.child("username").getValue(String::class.java)
                    ?: "Unknown",
                profileImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java) ?: "",
                rating = snapshot.child("rating").getValue(Float::class.java) ?: 0f,
                reviewsCount = snapshot.child("reviewsCount").getValue(Int::class.java) ?: 0
            )
            callback(owner)
        }.addOnFailureListener {
            callback(null)
        }
    }

    fun toggleLike(itemId: String, ownerId: String, currentUserId: String, callback: (RepositoryResult<Boolean>) -> Unit) {
        itemsRef.child(itemId).runTransaction(object : com.google.firebase.database.Transaction.Handler {
            override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                val itemMap = currentData.getValue<MutableMap<String, Any>>() ?: return com.google.firebase.database.Transaction.success(currentData)

                if (!itemMap.containsKey("likeCount")) itemMap["likeCount"] = 0
                if (!itemMap.containsKey("likedBy")) itemMap["likedBy"] = mutableMapOf<String, Boolean>()

                val likedBy = itemMap["likedBy"] as? MutableMap<String, Boolean> ?: mutableMapOf()
                val currentLikeCount = (itemMap["likeCount"] as? Long)?.toInt() ?: 0
                val isCurrentlyLiked = likedBy[currentUserId] == true

                if (isCurrentlyLiked) {
                    likedBy.remove(currentUserId)
                    itemMap["likeCount"] = kotlin.math.max(0, currentLikeCount - 1)
                } else {
                    likedBy[currentUserId] = true
                    itemMap["likeCount"] = currentLikeCount + 1
                }

                itemMap["likedBy"] = likedBy
                currentData.value = itemMap
                return com.google.firebase.database.Transaction.success(currentData)
            }

            override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, currentData: com.google.firebase.database.DataSnapshot?) {
                if (error != null) {
                    callback(RepositoryResult.Error(error.message ?: "Failed to toggle like"))
                } else if (committed) {
                    val isLiked = currentData?.child("likedBy")?.child(currentUserId)?.getValue(Boolean::class.java) ?: false

                    // Update favorites
                    val userFavoritesRef = favoritesRef.child(currentUserId).child(itemId)
                    if (isLiked) {
                        userFavoritesRef.setValue(true)
                        // Send notification
                        if (currentUserId != ownerId) {
                            sendLikeNotification(ownerId, currentUserId, itemId)
                        }
                    } else {
                        userFavoritesRef.removeValue()
                    }

                    callback(RepositoryResult.Success(isLiked))
                }
            }
        })
    }

    fun checkIfLiked(itemId: String, currentUserId: String, callback: (Boolean) -> Unit) {
        favoritesRef.child(currentUserId).child(itemId).get().addOnSuccessListener { snapshot ->
            callback(snapshot.exists())
        }.addOnFailureListener {
            callback(false)
        }
    }

    fun checkIfUserIsVerified(userId: String, callback: (Boolean) -> Unit) {
        usersRef.child(userId).child("isIDVerified").get().addOnSuccessListener { snapshot ->
            val verificationStatus = snapshot.getValue(String::class.java)
            callback(verificationStatus == "verified")
        }.addOnFailureListener {
            callback(false)
        }
    }

    private fun sendLikeNotification(ownerId: String, fromUserId: String, itemId: String) {
        val notificationRef = notificationsRef.child(ownerId).push()
        val notificationData = mapOf(
            "type" to "like",
            "fromUserId" to fromUserId,
            "itemId" to itemId,
            "read" to false,
            "timestamp" to System.currentTimeMillis()
        )
        notificationRef.setValue(notificationData)
    }

    private fun parseImageUrls(snapshot: com.google.firebase.database.DataSnapshot): List<String?> {
        val imagesValue = snapshot.child("imageUrls").getValue(String::class.java)
        if (!imagesValue.isNullOrEmpty()) {
            return imagesValue.split(",")
        }
        val singleImageUrl = snapshot.child("imageUrl").getValue(String::class.java)
        return if (!singleImageUrl.isNullOrEmpty()) listOf(singleImageUrl) else emptyList()
    }

    private fun parseCoordinate(value: Any?): Double {
        return when (value) {
            is Double -> value
            is Long -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    data class Item(
        val title: String,
        val description: String,
        val price: Any?,
        val category: String,
        val location: String,
        val timestamp: Long,
        val imageUrls: List<String?>,
        val latitude: Double,
        val longitude: Double,
        val ownerId: String
    )

    data class Owner(
        val name: String,
        val profileImageUrl: String,
        val rating: Float,
        val reviewsCount: Int
    )

    sealed class RepositoryResult<T> {
        data class Success<T>(val data: T) : RepositoryResult<T>()
        data class Error<T>(val message: String) : RepositoryResult<T>()
    }
}