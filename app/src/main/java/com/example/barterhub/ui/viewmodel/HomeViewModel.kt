package com.example.barterhub.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.barterhub.data.models.FeaturedItem
import com.example.barterhub.data.repository.ItemRepository
import com.google.firebase.database.FirebaseDatabase

class HomeViewModel : ViewModel() {

    private var rotateIndex = 0
    private val repository = ItemRepository()

    private val _items = MutableLiveData<List<FeaturedItem>>()
    val items: LiveData<List<FeaturedItem>> get() = _items

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    // ---------------------------------------------------------
    // 🔥 LOAD ALL ITEMS WITH SAFE CLEANING + PUBLIC USER MERGE
    // ---------------------------------------------------------
    fun loadAllItems() {

        _isLoading.value = true
        Log.d("HomeViewModel", "🔄 loadAllItems() called")

        repository.fetchAllItems { rawItems ->

            if (rawItems.isEmpty()) {
                _items.value = emptyList()
                _isLoading.value = false
                return@fetchAllItems
            }

            val sortedItems = rawItems.sortedByDescending { it.timestamp }

            // ✅ FIXED → USE PUBLIC_USERS INSTEAD OF USERS
            val db = FirebaseDatabase.getInstance(
                "https://barterhub-3c947-default-rtdb.firebaseio.com/"
            ).getReference("public_users")

            val processed = MutableList(sortedItems.size) { sortedItems[0] }

            var done = 0

            sortedItems.forEachIndexed { index, item ->

                val cleaned = cleanItemDescription(item)

                // ✅ ownerId check
                if (item.ownerId.isNotBlank()) {

                    db.child(item.ownerId)
                        .get()
                        .addOnSuccessListener { snap ->

                            // ✅ SAFE OWNER NAME FALLBACKS
                            val ownerName = when {

                                snap.child("fullName").exists() ->
                                    snap.child("fullName")
                                        .getValue(String::class.java)
                                        ?: "Unknown"

                                snap.child("username").exists() ->
                                    snap.child("username")
                                        .getValue(String::class.java)
                                        ?: "Unknown"

                                snap.child("name").exists() ->
                                    snap.child("name")
                                        .getValue(String::class.java)
                                        ?: "Unknown"

                                snap.child("displayName").exists() ->
                                    snap.child("displayName")
                                        .getValue(String::class.java)
                                        ?: "Unknown"

                                else -> "Unknown"
                            }

                            // ✅ SAFE PROFILE IMAGE FALLBACKS
                            val profileImage = snap.child("profileImageUrl")
                                .getValue(String::class.java)
                                ?: snap.child("profileImage")
                                    .getValue(String::class.java)
                                ?: ""

                            processed[index] = cleaned.copy(
                                ownerName = ownerName,
                                ownerProfileImage = profileImage
                            )

                            done++

                            if (done == sortedItems.size) {
                                finishProcessing(processed)
                            }
                        }

                        .addOnFailureListener { error ->

                            Log.e(
                                "HomeViewModel",
                                "Failed loading public user: ${error.message}"
                            )

                            processed[index] = cleaned.copy(
                                ownerName = "Unknown",
                                ownerProfileImage = ""
                            )

                            done++

                            if (done == sortedItems.size) {
                                finishProcessing(processed)
                            }
                        }

                } else {

                    processed[index] = cleaned.copy(
                        ownerName = "Unknown",
                        ownerProfileImage = ""
                    )

                    done++

                    if (done == sortedItems.size) {
                        finishProcessing(processed)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------
    // 🔥 CATEGORY FILTER WITH ROTATION
    // ---------------------------------------------------------
    fun loadItemsByCategory(category: String) {

        _isLoading.value = true

        repository.fetchItemsByCategory(category) { result ->

            val cleaned = result.map { cleanItemDescription(it) }

            if (cleaned.size > 1) {

                rotateIndex = (rotateIndex + 1) % cleaned.size

                val rotated =
                    cleaned.drop(rotateIndex) + cleaned.take(rotateIndex)

                _items.value = rotated

            } else {

                _items.value = cleaned
            }

            _isLoading.value = false
        }
    }

    // ---------------------------------------------------------
    // 🔥 CLEAN DESCRIPTION / PREVENT BLANKS
    // ---------------------------------------------------------
    private fun cleanItemDescription(item: FeaturedItem): FeaturedItem {

        var desc = item.description ?: ""

        // Remove invisible chars
        desc = desc.replace(Regex("[\\p{C}]"), "").trim()

        // Remove weird unicode
        desc = desc.replace(
            Regex("[^\\x20-\\x7E\\p{L}\\p{N}\\s.,!?-]"),
            ""
        ).trim()

        // Debug
        if (desc.isBlank()) {

            Log.e(
                "DATA_CHECK",
                "🚨 Empty/Corrupt Description → itemId=${item.itemId}, title=${item.title}"
            )
        }

        if (desc.isBlank()) {
            desc = "No description available"
        }

        return item.copy(description = desc)
    }

    // ---------------------------------------------------------
    // 🔥 FINAL APPLY
    // ---------------------------------------------------------
    private fun finishProcessing(list: List<FeaturedItem>) {

        if (list.isEmpty()) {

            _items.value = emptyList()
            _isLoading.value = false
            return
        }

        if (list.size == 1) {

            _items.value = list
            _isLoading.value = false
            return
        }

        rotateIndex = (rotateIndex + 1) % list.size

        val rotated =
            list.drop(rotateIndex) + list.take(rotateIndex)

        rotated.forEachIndexed { i, it ->

            Log.d(
                "FinalDebug",
                "Item $i → Title='${it.title}', Owner='${it.ownerName}'"
            )
        }

        _items.value = rotated
        _isLoading.value = false
    }
}