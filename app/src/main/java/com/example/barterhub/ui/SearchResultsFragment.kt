package com.example.barterhub.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.FeaturedItemsAdapter
import com.example.barterhub.R
import com.example.barterhub.data.models.FeaturedItem
import com.google.android.material.button.MaterialButton
import com.google.firebase.database.*

class SearchResultsFragment : Fragment(R.layout.fragment_search_results) {

    companion object {
        private const val ITEMS_NODE = "items"
        private const val MAX_RESULTS = 200
    }

    private lateinit var database: DatabaseReference
    private lateinit var recyclerView: RecyclerView
    private lateinit var txtQuery: TextView

    private lateinit var emptyState: LinearLayout
    private lateinit var btnBrowseAll: MaterialButton

    private val resultsList = ArrayList<FeaturedItem>()
    private lateinit var adapter: FeaturedItemsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val queryArg = try {
            SearchResultsFragmentArgs.fromBundle(requireArguments()).query.trim()
        } catch (e: Exception) {
            arguments?.getString("query")?.trim().orEmpty()
        }

        txtQuery = view.findViewById(R.id.txtQuery)
        recyclerView = view.findViewById(R.id.searchResultsRecycler)
        emptyState = view.findViewById(R.id.emptyStateResults)
        btnBrowseAll = view.findViewById(R.id.btnBrowseAllResults)

        txtQuery.text =
            if (queryArg.isBlank()) "Browse all items"
            else "Results for: $queryArg"

        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        recyclerView.setHasFixedSize(true)

        // ✅ CLICKABLE adapter
        adapter = FeaturedItemsAdapter(resultsList) { item ->
            if (item.itemId.isBlank() || item.ownerId.isBlank()) {
                Toast.makeText(requireContext(), "Item unavailable", Toast.LENGTH_SHORT).show()
                return@FeaturedItemsAdapter
            }
            openItemDetail(item.itemId, item.ownerId)
        }

        recyclerView.adapter = adapter

        database = FirebaseDatabase.getInstance().reference.child(ITEMS_NODE)

        btnBrowseAll.setOnClickListener {
            txtQuery.text = "Browse all items"
            fetchAndFilter("")
        }

        fetchAndFilter(queryArg)
    }

    private fun fetchAndFilter(queryArg: String) {
        val q = queryArg.lowercase()

        database.orderByChild("timestamp")
            .limitToLast(MAX_RESULTS)
            .addListenerForSingleValueEvent(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    resultsList.clear()

                    for (itemSnap in snapshot.children) {
                        val item = itemSnap.getValue(FeaturedItem::class.java) ?: continue
                        if (!item.isActive || item.isArchived) continue

                        if (q.isBlank()) {
                            resultsList.add(item)
                        } else {
                            val title = item.title.lowercase()
                            val category = item.category.lowercase()
                            val desc = item.description.lowercase()

                            if (title.contains(q) || category.contains(q) || desc.contains(q)) {
                                resultsList.add(item)
                            }
                        }
                    }

                    resultsList.sortByDescending { it.timestamp }
                    adapter.notifyDataSetChanged()

                    updateEmptyState(queryArg)
                }

                override fun onCancelled(error: DatabaseError) {
                    resultsList.clear()
                    adapter.notifyDataSetChanged()
                    showEmpty("Error", error.message)
                }
            })
    }

    private fun updateEmptyState(queryArg: String) {
        if (resultsList.isEmpty()) {
            if (queryArg.isBlank()) {
                showEmpty("No items yet", "Try again later.")
            } else {
                showEmpty("No results found", "Try different keywords.")
            }
        } else {
            hideEmpty()
        }
    }

    private fun showEmpty(title: String, subtitle: String) {
        emptyState.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        emptyState.findViewById<TextView>(R.id.tvEmptyTitle).text = title
        emptyState.findViewById<TextView>(R.id.tvEmptySubtitle).text = subtitle
    }

    private fun hideEmpty() {
        emptyState.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    private fun openItemDetail(itemId: String, ownerId: String) {
        val bundle = Bundle().apply {
            putString("itemId", itemId)
            putString("ownerId", ownerId)
        }
        findNavController().navigate(R.id.nav_item_detail, bundle)
    }

}
