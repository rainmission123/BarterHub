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
import com.example.barterhub.databinding.FragmentAllTrendingItemsBinding
import com.example.barterhub.ui.helpers.TrendingItemsLoader
import com.google.firebase.database.*

class AllTrendingItemsFragment : Fragment(R.layout.fragment_all_trending_items) {

    private var _binding: FragmentAllTrendingItemsBinding? = null
    private val binding get() = _binding!!

    private lateinit var trendingAdapter: AllTrendingItemsAdapter

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

        TrendingItemsLoader.load(
            limit = 20,
            onSuccess = { items ->
                if (_binding == null) return@load
                showLoading(false)
                showEmptyState(false)
                trendingAdapter.submitList(items)
                Log.d("AllTrendingItems", "Displaying ${items.size} trending items")
            },
            onEmpty = {
                if (_binding == null) return@load
                showLoading(false)
                showEmptyState(true)
            },
            onError = { error ->
                if (_binding == null) return@load
                Log.e("AllTrendingItems", "Failed to load trending items: ${error.message}")
                showLoading(false)
                showEmptyState(true)
            }
        )
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
