package com.example.barterhub.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.barterhub.R
import com.example.barterhub.adapters.AllTrendingItemsAdapter
import com.example.barterhub.data.models.FeaturedItem
import com.example.barterhub.databinding.FragmentAllTrendingItemsBinding
import com.google.firebase.database.*

class AllTrendingItemsFragment : Fragment(R.layout.fragment_all_trending_items) {

    private var _binding: FragmentAllTrendingItemsBinding? = null
    private val binding get() = _binding!!

    private lateinit var trendingAdapter: AllTrendingItemsAdapter

    private val database by lazy {
        FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAllTrendingItemsBinding.bind(view)

        setupToolbar()
        setupRecyclerView()
        loadAllTrendingItems()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        trendingAdapter = AllTrendingItemsAdapter(
            onItemClick = { item ->
                if (item.itemId.isBlank()) {
                    Toast.makeText(requireContext(), "Item not found", Toast.LENGTH_SHORT).show()
                } else {
                    val bundle = Bundle().apply {
                        putString("itemId", item.itemId)
                        putString("ownerId", item.ownerId)
                    }

                    findNavController().navigate(R.id.nav_item_detail, bundle)
                }
            },
            onWishlistClick = { item ->
                Toast.makeText(requireContext(), "Added to wishlist: ${item.title}", Toast.LENGTH_SHORT).show()
            },
            onLikeClick = { item ->
                Toast.makeText(requireContext(), "Liked: ${item.title}", Toast.LENGTH_SHORT).show()
            }
        )

        binding.trendingItemsRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = trendingAdapter
        }
    }

    private fun loadAllTrendingItems() {
        showLoading(true)
        showEmptyState(false)

        val itemsRef = database.getReference("items")

        Log.d("AllTrendingItems", "🔍 Loading items from Firebase...")

        itemsRef.orderByChild("timestamp").limitToLast(50)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val allItems = mutableListOf<FeaturedItem>()

                    Log.d("AllTrendingItems", "📦 Found ${snapshot.childrenCount} items in database")

                    for (itemSnapshot in snapshot.children) {
                        val item = itemSnapshot.getValue(FeaturedItem::class.java)
                        item?.let {
                            it.itemId = itemSnapshot.key ?: ""

                            Log.d("AllTrendingItems", "Loaded item: ${it.title}")
                            Log.d("AllTrendingItems", "ownerId=${it.ownerId}")
                            Log.d("AllTrendingItems", "ownerName=${it.ownerName}")
                            Log.d("AllTrendingItems", "ownerProfileImage=${it.ownerProfileImage}")

                            allItems.add(it)
                        }
                    }

                    if (allItems.isEmpty()) {
                        Log.d("AllTrendingItems", "❌ No items found")
                        showLoading(false)
                        showEmptyState(true)
                        return
                    }

                    filterPremiumItems(allItems)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("AllTrendingItems", "❌ Failed to load items: ${error.message}")
                    showLoading(false)
                    showEmptyState(true)
                }
            })
    }

    private fun filterPremiumItems(items: MutableList<FeaturedItem>) {
        val usersRef = database.getReference("users")
        val now = System.currentTimeMillis()

        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val premiumOwnerIds = mutableSetOf<String>()

                Log.d("TrendingPremium", "users childrenCount = ${snapshot.childrenCount}")

                for (userSnapshot in snapshot.children) {
                    val uid = userSnapshot.key ?: continue

                    val premiumExpiry = userSnapshot.child("premiumExpiry").getValue(Long::class.java) ?: 0L
                    val premiumUntil = userSnapshot.child("premiumUntil").getValue(String::class.java).orEmpty()

                    val isPremium = premiumExpiry > now

                    Log.d(
                        "TrendingPremium",
                        "uid=$uid, premiumExpiry=$premiumExpiry, premiumUntil=$premiumUntil, isPremium=$isPremium"
                    )

                    if (isPremium) {
                        premiumOwnerIds.add(uid)
                    }
                }

                Log.d("TrendingPremium", "premiumOwnerIds = $premiumOwnerIds")

                val premiumItems = items.filter { item ->
                    item.ownerId.isNotBlank() &&
                            premiumOwnerIds.contains(item.ownerId) &&
                            item.isActive &&
                            !item.isArchived
                }.toMutableList()

                Log.d("TrendingPremium", "premiumItems count = ${premiumItems.size}")

                if (premiumItems.isEmpty()) {
                    showLoading(false)
                    showEmptyState(true)
                    return
                }

                enrichItemsWithOwnerInfo(premiumItems)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TrendingPremium", "❌ Failed to load users: ${error.message}")
                showLoading(false)
                showEmptyState(true)
            }
        })
    }

    private fun enrichItemsWithOwnerInfo(items: MutableList<FeaturedItem>) {
        val usersRef = database.getReference("users")

        val ownerIds = items.map { it.ownerId }
            .filter { it.isNotBlank() }
            .distinct()

        if (ownerIds.isEmpty()) {
            Log.d("AllTrendingItems", "⚠️ No ownerIds found, showing items without user enrichment")
            showLoading(false)
            showEmptyState(false)
            trendingAdapter.submitList(items.takeLast(10).reversed())
            return
        }

        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (item in items) {
                    if (item.ownerId.isBlank()) continue

                    val userSnapshot = snapshot.child(item.ownerId)
                    if (!userSnapshot.exists()) continue

                    val fetchedName =
                        userSnapshot.child("fullName").getValue(String::class.java)
                            ?.takeIf { it.isNotBlank() }
                            ?: userSnapshot.child("name").getValue(String::class.java)
                                ?.takeIf { it.isNotBlank() }
                            ?: userSnapshot.child("username").getValue(String::class.java)
                                ?.takeIf { it.isNotBlank() }
                            ?: userSnapshot.child("displayName").getValue(String::class.java)
                                ?.takeIf { it.isNotBlank() }

                    val fetchedProfile =
                        userSnapshot.child("profileImage").getValue(String::class.java)
                            ?.takeIf { it.isNotBlank() }
                            ?: userSnapshot.child("profileImageUrl").getValue(String::class.java)
                                ?.takeIf { it.isNotBlank() }
                            ?: userSnapshot.child("imageUrl").getValue(String::class.java)
                                ?.takeIf { it.isNotBlank() }
                            ?: userSnapshot.child("avatar").getValue(String::class.java)
                                ?.takeIf { it.isNotBlank() }

                    if (item.ownerName.isBlank() && !fetchedName.isNullOrBlank()) {
                        item.ownerName = fetchedName
                    }

                    if (item.ownerProfileImage.isBlank() && !fetchedProfile.isNullOrBlank()) {
                        item.ownerProfileImage = fetchedProfile
                    }

                    Log.d(
                        "AllTrendingItems",
                        "✅ Enriched item '${item.title}' -> ownerName='${item.ownerName}', ownerProfile='${item.ownerProfileImage}'"
                    )
                }

                showLoading(false)
                showEmptyState(false)
                trendingAdapter.submitList(items.takeLast(10).reversed())
                Log.d("AllTrendingItems", "✅ Displaying ${items.takeLast(10).reversed().size} enriched items")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AllTrendingItems", "❌ Failed to load users: ${error.message}")
                showLoading(false)
                showEmptyState(false)
                trendingAdapter.submitList(items.takeLast(10).reversed())
            }
        })
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmptyState(show: Boolean) {
        binding.emptyStateLayout.visibility = if (show) View.VISIBLE else View.GONE
        binding.trendingItemsRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}