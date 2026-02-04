package com.example.barterhub.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.adapters.UserListingsAdapter
import com.example.barterhub.data.models.FeaturedItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MyListingsFragment : Fragment(R.layout.fragment_my_listings) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noListingsText: TextView
    private lateinit var adapter: UserListingsAdapter

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private val itemList = mutableListOf<FeaturedItem>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rvMyListings)
        noListingsText = view.findViewById(R.id.noListingsText)

        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)

        adapter = UserListingsAdapter(
            itemList,
            onEditClick = { item ->
                Log.d("MyListingsFragment", "✅ EDIT CLICKED: ${item.title} (ID: ${item.itemId})")

                // ✅ Create Bundle with correct types
                val bundle = Bundle().apply {
                    putString("itemId", item.itemId)
                    putString("title", item.title)
                    putString("description", item.description)
                    putFloat("price", item.price.toFloat()) // Float matches nav argument
                    putString("category", item.category)
                    putString("condition", item.condition)
                    putString("location", item.location)
                    putString("imageUrls", item.imageUrls)
                }

                try {
                    findNavController().navigate(
                        R.id.action_nav_my_listings_to_editItemFragment,
                        bundle
                    )
                } catch (e: Exception) {
                    Log.e("MyListingsFragment", "Navigation failed: ${e.message}")
                    showEditDialog(item) // fallback dialog
                }
            },

            onDeleteClick = { item ->
                Log.d("MyListingsFragment", "✅ DELETE CLICKED: ${item.title}")
                // Remove from Firebase
                database.child(item.itemId).removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Item deleted", Toast.LENGTH_SHORT).show()
                        itemList.remove(item)
                        adapter.updateData(itemList)
                        updateEmptyState()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to delete item", Toast.LENGTH_SHORT).show()
                    }
            }
        )


        recyclerView.adapter = adapter

        database = FirebaseDatabase.getInstance().getReference("items")
        auth = FirebaseAuth.getInstance()

        loadUserListings()
    }

    private fun showEditDialog(item: FeaturedItem) {
        // ✅ Simple Edit Dialog as fallback
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Edit Item")
            .setMessage("Edit functionality for: ${item.title}\n\nItem ID: ${item.itemId}")
            .setPositiveButton("Edit in New Screen") { dialog, _ ->
                // Open Edit Activity or Fragment
                Toast.makeText(requireContext(), "Opening edit screen for ${item.title}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadUserListings() {
        val userId = auth.currentUser?.uid ?: return

        Log.d("MyListingsFragment", "📌 Current UID = $userId")

        database.orderByChild("ownerId").equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d("MyListingsFragment", "📌 Firebase returned ${snapshot.childrenCount} items")

                    itemList.clear()

                    for (data in snapshot.children) {
                        Log.d("MyListingsFragment", "🔥 Item found: ${data.child("title").value}")

                        val item = data.getValue(FeaturedItem::class.java)
                        if (item != null) {
                            // ✅ Ensure itemId is set from Firebase key
                            if (item.itemId.isNullOrEmpty()) {
                                item.itemId = data.key ?: ""
                            }
                            itemList.add(item)
                        }
                    }

                    adapter.updateData(itemList)
                    updateEmptyState()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Failed to load listings", Toast.LENGTH_SHORT).show()
                    updateEmptyState()
                }
            })
    }

    private fun updateEmptyState() {
        if (itemList.isEmpty()) {
            noListingsText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            noListingsText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload data when returning to fragment
        loadUserListings()
    }
}