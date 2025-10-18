package com.example.barterhub.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.barterhub.data.models.FeaturedItem
import com.example.barterhub.data.repository.ItemRepository
import com.google.firebase.database.FirebaseDatabase

class HomeViewModel : ViewModel() {

    private val repository = ItemRepository()

    private val _items = MutableLiveData<List<FeaturedItem>>()
    val items: LiveData<List<FeaturedItem>> get() = _items

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    fun loadAllItems() {
        _isLoading.value = true
        repository.fetchAllItems { items ->
            val itemsWithUsername = mutableListOf<FeaturedItem>()
            val db = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/").getReference("users")

            var remaining = items.size

            items.forEach { item ->
                db.child(item.ownerId).get().addOnSuccessListener { userSnap ->
                    item.ownerName = userSnap.child("username").getValue(String::class.java) ?: "Unknown"
                    item.ownerProfileImage = userSnap.child("profileImageUrl").getValue(String::class.java) ?: ""
                    itemsWithUsername.add(item)

                    remaining--
                    if (remaining == 0) {
                        _items.value = itemsWithUsername
                        _isLoading.value = false
                    }
                }.addOnFailureListener {
                    remaining--
                    if (remaining == 0) {
                        _items.value = itemsWithUsername
                        _isLoading.value = false
                    }
                }
            }
        }
    }

    fun loadItemsByCategory(category: String) {
        _isLoading.value = true
        repository.fetchItemsByCategory(category) { result ->
            _items.value = result
            _isLoading.value = false
        }
    }
}
