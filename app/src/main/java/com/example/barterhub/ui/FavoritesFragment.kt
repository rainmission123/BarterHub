package com.example.barterhub.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
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
    private val favoriteItems = mutableListOf<FeaturedItem>()
    private lateinit var adapter: FavoritesAdapter // ✅ Class-level para ma-access sa loadFavorites

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Initialize RecyclerView
        favoritesRecyclerView = view.findViewById(R.id.rvFavorites)

        // ✅ Set LayoutManager
        favoritesRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // ✅ Initialize adapter at i-assign sa RecyclerView
        adapter = FavoritesAdapter(favoriteItems)
        favoritesRecyclerView.adapter = adapter

        // ✅ Load favorites mula Firebase
        loadFavorites()
    }

    private fun loadFavorites() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val favRef = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("favorites")
            .child(userId)

        favRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return // ✅ Check kung naka-attach ang fragment

                favoriteItems.clear()
                for (child in snapshot.children) {
                    val item = child.getValue(FeaturedItem::class.java)
                    item?.let { favoriteItems.add(it) }
                }
                adapter.notifyDataSetChanged() // ✅ Refresh adapter
            }

            override fun onCancelled(error: DatabaseError) {
                context?.let { ctx ->
                    Toast.makeText(ctx, "Failed to load favorites", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

}