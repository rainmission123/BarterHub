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

        database.getReference("items")
            .orderByChild("timestamp")
            .limitToLast(100)
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    val allItems = mutableListOf<FeaturedItem>()

                    Log.d("AllTrendingItems", "Found ${snapshot.childrenCount} items")

                    for (itemSnapshot in snapshot.children) {
                        val item = itemSnapshot.getValue(FeaturedItem::class.java)

                        if (item != null) {
                            item.itemId = itemSnapshot.key ?: ""

                            // FORCE READ important fields
                            item.ownerId = itemSnapshot.child("ownerId").getValue(String::class.java) ?: ""
                            item.likeCount = itemSnapshot.child("likeCount").asInt() ?: 0
                            item.timestamp = itemSnapshot.child("timestamp").asLong() ?: 0L
                            val isActive = itemSnapshot.child("isActive").asBoolean(defaultValue = true)
                            val isArchived = itemSnapshot.child("isArchived").asBoolean(defaultValue = false)

                            Log.d("TRENDING_TEST", "itemId=${item.itemId}")
                            Log.d("TRENDING_TEST", "title=${item.title}")
                            Log.d("TRENDING_TEST", "ownerId=${item.ownerId}")
                            Log.d("TRENDING_TEST", "likeCount=${item.likeCount}")

                            if (item.ownerId.isNotBlank() && isActive && !isArchived) {
                                allItems.add(item)
                            }
                        }
                    }

                    if (allItems.isEmpty()) {
                        showLoading(false)
                        showEmptyState(true)
                        return
                    }

                    loadPremiumOwnersAndFilterItems(allItems)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("AllTrendingItems", "Failed to load items: ${error.message}")
                    showLoading(false)
                    showEmptyState(true)
                }
            })
    }

    private fun loadPremiumOwnersAndFilterItems(items: MutableList<FeaturedItem>) {
        val now = System.currentTimeMillis()

        database.getReference("public_users")
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    val premiumOwnerIds = mutableSetOf<String>()

                    for (userSnapshot in snapshot.children) {
                        val uid = userSnapshot.key ?: continue

                        val isPremium = userSnapshot.child("isPremium")
                            .asBoolean(defaultValue = false)

                        val premiumExpiry = userSnapshot.child("premiumExpiry")
                            .asLong()
                            ?.let { normalizeExpiryMillis(it) }
                            ?: 0L

                        val premiumActive = isPremium && premiumExpiry > now

                        if (premiumActive) {
                            premiumOwnerIds.add(uid)
                        }

                        Log.d(
                            "TrendingPremium",
                            "uid=$uid, isPremium=$isPremium, premiumExpiry=$premiumExpiry, premiumActive=$premiumActive"
                        )
                    }

                    val premiumTrendingItems = items
                        .filter { item ->
                            val matched = item.ownerId.isNotBlank() &&
                                    premiumOwnerIds.contains(item.ownerId)

                            Log.d(
                                "TRENDING_MATCH",
                                "title=${item.title}, ownerId=${item.ownerId}, matched=$matched"
                            )

                            matched
                        }
                        .sortedWith(
                            compareByDescending<FeaturedItem> { it.likeCount }
                                .thenByDescending { it.timestamp }
                        )
                        .take(20)
                        .toMutableList()

                    val trendingItems = if (premiumTrendingItems.isNotEmpty()) {
                        premiumTrendingItems
                    } else {
                        items.sortedWith(
                            compareByDescending<FeaturedItem> { it.likeCount }
                                .thenByDescending { it.timestamp }
                        )
                            .take(20)
                            .toMutableList()
                    }

                    Log.d("TrendingPremium", "Premium owners count = ${premiumOwnerIds.size}")
                    Log.d("TrendingPremium", "Trending premium items count = ${trendingItems.size}")

                    if (trendingItems.isEmpty()) {
                        showLoading(false)
                        showEmptyState(true)
                        return
                    }

                    enrichItemsWithOwnerInfo(trendingItems, snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("TrendingPremium", "Failed to load users: ${error.message}")
                    showLoading(false)
                    showEmptyState(true)
                }
            })
    }

    private fun enrichItemsWithOwnerInfo(
        items: MutableList<FeaturedItem>,
        usersSnapshot: DataSnapshot
    ) {
        for (item in items) {
            if (item.ownerId.isBlank()) continue

            val userSnapshot = usersSnapshot.child(item.ownerId)
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
        }

        showLoading(false)
        showEmptyState(false)
        trendingAdapter.submitList(items)

        Log.d("AllTrendingItems", "Displaying ${items.size} trending premium items")
    }

    private fun showLoading(show: Boolean) {
        if (_binding == null) return
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmptyState(show: Boolean) {
        if (_binding == null) return
        binding.emptyStateLayout.visibility = if (show) View.VISIBLE else View.GONE
        binding.trendingItemsRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun DataSnapshot.asBoolean(defaultValue: Boolean): Boolean {
        return when (val value = getValue()) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            is Number -> value.toInt() != 0
            else -> defaultValue
        }
    }

    private fun DataSnapshot.asLong(): Long? {
        return when (val value = getValue()) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun DataSnapshot.asInt(): Int? {
        return when (val value = getValue()) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun normalizeExpiryMillis(expiry: Long): Long {
        return if (expiry in 1 until 1_000_000_000_000L) expiry * 1000L else expiry
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
