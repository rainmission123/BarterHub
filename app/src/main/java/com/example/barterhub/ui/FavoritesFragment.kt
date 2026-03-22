package com.example.barterhub.ui

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.adapters.FavoritesAdapter
import com.example.barterhub.data.models.FeaturedItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FavoritesFragment : Fragment(R.layout.fragment_favorites) {

    private lateinit var favoritesRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: View

    private val adapter = FavoritesAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        favoritesRecyclerView = view.findViewById(R.id.rvFavorites)
        progressBar = view.findViewById(R.id.progressBar)
        btnBack = view.findViewById(R.id.btnBack)

        favoritesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        favoritesRecyclerView.adapter = adapter

        btnBack.setOnClickListener { findNavController().navigateUp() }

        showLoading(true)
        loadFavorites()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        favoritesRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun loadFavorites() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            showLoading(false)
            return
        }

        val db = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/").reference
        val favRef = db.child("favorites").child(userId)

        favRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return

                val tempList = mutableListOf<FeaturedItem>()

                for (child in snapshot.children) {
                    val item = parseFeaturedItem(child) ?: continue

                    if (item.itemId.isBlank()) {
                        child.ref.removeValue()
                        continue
                    }

                    tempList.add(item)
                }

                resolveOwnerNamesIfNeeded(db, tempList) { resolved ->
                    adapter.submitList(resolved)
                    showLoading(false)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isAdded) return
                Toast.makeText(requireContext(), "Failed to load favorites", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
        })
    }

    private fun resolveOwnerNamesIfNeeded(
        db: com.google.firebase.database.DatabaseReference,
        list: List<FeaturedItem>,
        done: (List<FeaturedItem>) -> Unit
    ) {
        val unresolved = list.filter { it.ownerName.isBlank() && it.ownerId.isNotBlank() }

        if (unresolved.isEmpty()) {
            done(list)
            return
        }

        val result = list.toMutableList()
        var remaining = unresolved.size

        for (item in unresolved) {
            db.child("users").child(item.ownerId).child("username")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        val username = s.getValue(String::class.java).orEmpty().trim()
                        val fixed = if (username.isNotEmpty()) {
                            item.copy(ownerName = username)
                        } else {
                            item
                        }

                        val idx = result.indexOfFirst { it.itemId == item.itemId }
                        if (idx != -1) result[idx] = fixed

                        remaining--
                        if (remaining == 0) done(result)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        remaining--
                        if (remaining == 0) done(result)
                    }
                })
        }
    }

    private fun parseFeaturedItem(snapshot: DataSnapshot): FeaturedItem? {
        return try {
            FeaturedItem(
                itemId = snapshot.child("itemId").getValue(String::class.java) ?: snapshot.key.orEmpty(),
                title = snapshot.child("title").getValue(String::class.java).orEmpty(),
                description = snapshot.child("description").getValue(String::class.java).orEmpty(),
                category = snapshot.child("category").getValue(String::class.java).orEmpty(),
                condition = snapshot.child("condition").getValue(String::class.java).orEmpty(),
                price = snapshot.child("price").value?.let {
                    when (it) {
                        is Number -> it.toDouble()
                        is String -> it.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                } ?: 0.0,
                displayPrice = snapshot.child("displayPrice").value?.toString().orEmpty(),
                imageUrls = snapshot.child("imageUrls").getValue(String::class.java)
                    ?: snapshot.child("photoUrls").getValue(String::class.java)
                    ?: "",
                location = snapshot.child("location").getValue(String::class.java).orEmpty(),
                latitude = (snapshot.child("latitude").value as? Number)?.toDouble() ?: 0.0,
                longitude = (snapshot.child("longitude").value as? Number)?.toDouble() ?: 0.0,
                ownerId = snapshot.child("ownerId").getValue(String::class.java).orEmpty(),
                ownerName = snapshot.child("ownerName").getValue(String::class.java).orEmpty(),
                ownerProfileImage = snapshot.child("ownerProfileImage").getValue(String::class.java).orEmpty(),
                timestamp = snapshot.child("timestamp").value?.let {
                    when (it) {
                        is Number -> it.toLong()
                        is String -> it.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                } ?: 0L,
                likeCount = snapshot.child("likeCount").value?.let {
                    when (it) {
                        is Number -> it.toInt()
                        is String -> it.toIntOrNull() ?: 0
                        else -> 0
                    }
                } ?: 0
            )
        } catch (e: Exception) {
            android.util.Log.e("FavoritesFragment", "parseFeaturedItem failed: ${e.message}")
            null
        }
    }
}