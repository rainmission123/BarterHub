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

    private val favoriteItems = mutableListOf<FeaturedItem>()
    private lateinit var adapter: FavoritesAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        favoritesRecyclerView = view.findViewById(R.id.rvFavorites)
        progressBar = view.findViewById(R.id.progressBar)
        btnBack = view.findViewById(R.id.btnBack)

        favoritesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = FavoritesAdapter(favoriteItems)
        favoritesRecyclerView.adapter = adapter

        setupBackButton()
        showLoading(true)
        loadFavorites()
    }

    private fun setupBackButton() {
        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        favoritesRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun loadFavorites() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val favRef = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("favorites")
            .child(userId)

        favRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return

                favoriteItems.clear()
                for (child in snapshot.children) {
                    try {
                        val item = parseFeaturedItem(child)
                        item?.let { favoriteItems.add(it) }
                    } catch (e: Exception) {
                        android.util.Log.e("FavoritesFragment", "Error parsing favorite: ${e.message}")
                    }
                }
                adapter.notifyDataSetChanged()
                showLoading(false)
            }

            override fun onCancelled(error: DatabaseError) {
                context?.let { ctx ->
                    Toast.makeText(ctx, "Failed to load favorites", Toast.LENGTH_SHORT).show()
                }
                showLoading(false)
            }
        })
    }

    private fun parseFeaturedItem(snapshot: DataSnapshot): FeaturedItem? {
        return try {
            FeaturedItem(
                itemId = snapshot.child("itemId").getValue(String::class.java) ?: snapshot.key ?: "",
                title = snapshot.child("title").getValue(String::class.java) ?: "",
                description = snapshot.child("description").getValue(String::class.java) ?: "",
                category = snapshot.child("category").getValue(String::class.java) ?: "",
                condition = snapshot.child("condition").getValue(String::class.java) ?: "",
                price = snapshot.child("price").value?.let {
                    when (it) {
                        is Number -> it.toDouble()
                        is String -> it.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                } ?: 0.0,
                displayPrice = snapshot.child("displayPrice").value?.toString() ?: "",
                imageUrls = snapshot.child("imageUrls").getValue(String::class.java)
                    ?: snapshot.child("photoUrls").getValue(String::class.java)
                    ?: "",
                location = snapshot.child("location").getValue(String::class.java) ?: "",
                latitude = (snapshot.child("latitude").value as? Number)?.toDouble() ?: 0.0,
                longitude = (snapshot.child("longitude").value as? Number)?.toDouble() ?: 0.0,
                ownerId = snapshot.child("ownerId").getValue(String::class.java) ?: "",
                ownerName = snapshot.child("ownerName").getValue(String::class.java) ?: "",
                ownerProfileImage = snapshot.child("ownerProfileImage").getValue(String::class.java) ?: "",
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
