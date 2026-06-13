package com.example.barterhub.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.adapters.RecentSearchesAdapter
import com.example.barterhub.adapters.TrendingItemsAdapter
import com.example.barterhub.data.models.Category
import com.example.barterhub.data.models.FeaturedItem
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase


class SearchFragment : Fragment() {
    private var scrollView: androidx.core.widget.NestedScrollView? = null
    private var suppressNextTextWatcher = false
    private var lastNavigatedQuery: String? = null
    private lateinit var searchEditText: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var recentRecyclerView: RecyclerView
    private lateinit var trendingRecyclerView: RecyclerView
    private lateinit var chipGroup: ChipGroup
    private lateinit var tvClearRecent: TextView
    private lateinit var tvNoRecent: TextView

    private lateinit var recentAdapter: RecentSearchesAdapter
    private lateinit var trendingAdapter: TrendingItemsAdapter

    private val recentList = mutableListOf<String>()
    private val trendingItems = mutableListOf<FeaturedItem>()
    private val categories = mutableListOf<Category>()

    private lateinit var database: DatabaseReference

    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null

    companion object {
        private const val SEARCH_DELAY_MS = 450L
        private const val MAX_RECENT_SEARCHES = 10
        private const val PREFS_NAME = "BarterHubSearch"
        private const val PREF_KEY_RECENT = "recent_list"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_search, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupFirebase()
        setupAdapters()
        setupSearchListeners()
        setupClickListeners()
        loadInitialData()

        showKeyboard()
    }

    private fun initializeViews(view: View) {
        searchEditText = view.findViewById(R.id.searchEditText)
        btnClearSearch = view.findViewById(R.id.btnClearSearch)
        progressBar = view.findViewById(R.id.searchProgressBar)
        recentRecyclerView = view.findViewById(R.id.recentSearchesRecyclerView)
        trendingRecyclerView = view.findViewById(R.id.trendingRecyclerView)
        chipGroup = view.findViewById(R.id.categoryChipGroup)
        tvClearRecent = view.findViewById(R.id.tvClearRecent)
        tvNoRecent = view.findViewById(R.id.tvNoRecent)
        recentRecyclerView.isNestedScrollingEnabled = false
        trendingRecyclerView.isNestedScrollingEnabled = false
        scrollView = view.findViewById(R.id.searchScrollView)
    }

    private fun setupFirebase() {
        database = Firebase.database.reference
    }

    private fun setupAdapters() {
        recentAdapter = RecentSearchesAdapter(recentList) { selected ->
            searchEditText.setText(selected)
            searchEditText.setSelection(selected.length)
            saveRecentSearch(selected)
            navigateToSearchResults(selected)
        }
        //trendingRecyclerView.setHasFixedSize(true)
        trendingRecyclerView.isNestedScrollingEnabled = false

        recentRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = recentAdapter
        }

        trendingAdapter = TrendingItemsAdapter(trendingItems) { item ->
            navigateToItemDetail(item)
        }

        trendingRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = trendingAdapter
        }
    }

    private fun setupSearchListeners() {

        searchEditText.imeOptions = EditorInfo.IME_ACTION_SEARCH
        searchEditText.setRawInputType(EditorInfo.TYPE_CLASS_TEXT)

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (suppressNextTextWatcher) {
                    suppressNextTextWatcher = false
                    return
                }

                val query = s?.toString()?.trim().orEmpty()
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE

                pendingSearch?.let { searchHandler.removeCallbacks(it) }
                pendingSearch = null

                progressBar.visibility = View.GONE
                lastNavigatedQuery = null
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        searchEditText.setOnEditorActionListener { _, actionId, event ->
            val isImeSearch = actionId == EditorInfo.IME_ACTION_SEARCH
            val isEnter =
                event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
                        event.action == android.view.KeyEvent.ACTION_DOWN

            if (isImeSearch || isEnter) {
                val query = searchEditText.text?.toString()?.trim().orEmpty()
                if (query.isNotBlank()) {
                    if (lastNavigatedQuery == query) return@setOnEditorActionListener true
                    lastNavigatedQuery = query

                    hideKeyboard()
                    saveRecentSearch(query)
                    navigateToSearchResults(query)
                }
                true
            } else {
                false
            }
        }
    }

    private fun setupClickListeners() {
        btnClearSearch.setOnClickListener {
            pendingSearch?.let { searchHandler.removeCallbacks(it) }
            pendingSearch = null
            lastNavigatedQuery = null

            searchEditText.text?.clear()
            btnClearSearch.visibility = View.GONE
            progressBar.visibility = View.GONE
            showKeyboard()
        }

        tvClearRecent.setOnClickListener { clearRecentSearches() }

    }

    private fun loadInitialData() {
        loadRecentSearches()
        loadCategories()
        loadTrendingItems()
    }

    private fun loadCategories() {
        database.child("categories").get()
            .addOnSuccessListener { snapshot ->
                categories.clear()
                chipGroup.removeAllViews()

                for (categorySnap in snapshot.children) {
                    val category = categorySnap.getValue(Category::class.java)
                    if (category != null && category.name.isNotBlank()) {
                        categories.add(category)
                        addCategoryChip(category)
                    }
                }

                if (categories.isEmpty()) loadDefaultCategories()
            }
            .addOnFailureListener {
                loadDefaultCategories()
            }
    }

    private fun loadDefaultCategories() {
        val defaultCategories = listOf(
            Category("1", "Electronics", "ic_electronics", "#4CAF50"),
            Category("2", "Clothing", "ic_clothing", "#2196F3"),
            Category("3", "Books", "ic_books", "#FF9800"),
            Category("4", "Home Items", "ic_home", "#9C27B0"),
            Category("5", "Sports", "ic_sports", "#F44336"),
            Category("6", "Toys", "ic_toys", "#00BCD4"),
            Category("7", "Tools", "ic_tools", "#795548"),
            Category("8", "Art", "ic_art", "#E91E63")
        )

        categories.clear()
        chipGroup.removeAllViews()
        defaultCategories.forEach {
            categories.add(it)
            addCategoryChip(it)
        }
    }

    private fun addCategoryChip(category: Category) {
        val chip = Chip(requireContext()).apply {
            text = category.name
            chipBackgroundColor =
                ContextCompat.getColorStateList(requireContext(), R.color.chip_background)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_800))
            chipStrokeColor =
                ContextCompat.getColorStateList(requireContext(), R.color.outline)
            chipStrokeWidth = 1f
            isCheckable = false
            isClickable = true
            isFocusable = true

            try {
                val iconId = resources.getIdentifier(
                    category.icon,
                    "drawable",
                    requireContext().packageName
                )
                if (iconId != 0) {
                    chipIcon = ContextCompat.getDrawable(requireContext(), iconId)
                    iconStartPadding = 8f
                    iconEndPadding = 8f
                }
            } catch (_: Exception) { }

            elevation = 2f
        }

        chip.setOnClickListener {
            val q = category.name.trim()
            searchEditText.setText(q)
            searchEditText.setSelection(q.length)
            saveRecentSearch(q)
            hideKeyboard()
            navigateToSearchResults(q)
        }

        chipGroup.addView(chip)
    }

    private fun loadTrendingItems() {
        progressBar.visibility = View.VISIBLE

        database.child("items")
            .orderByChild("timestamp")
            .limitToLast(100)
            .get()
            .addOnSuccessListener { snapshot ->
                trendingItems.clear()

                for (itemSnap in snapshot.children) {
                    val item = itemSnap.getValue(FeaturedItem::class.java) ?: continue
                    if (!item.isActive || item.isArchived) continue
                    trendingItems.add(item)
                }

                trendingItems.sortWith(
                    compareByDescending<FeaturedItem> { it.likeCount }
                        .thenByDescending { it.timestamp }
                )

                val top = trendingItems.take(8)
                trendingItems.clear()
                trendingItems.addAll(top)

                trendingAdapter.notifyDataSetChanged()

                progressBar.visibility = View.GONE

                trendingRecyclerView.visibility =
                    if (trendingItems.isEmpty()) View.GONE else View.VISIBLE
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                trendingRecyclerView.visibility = View.GONE
                Toast.makeText(requireContext(), "Trending error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun navigateToSearchResults(query: String) {
        try {
            val action = SearchFragmentDirections.actionSearchFragmentToSearchResultsFragment(query)
            findNavController().navigate(action)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Navigation failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveRecentSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) return

        recentList.remove(q)
        recentList.add(0, q)
        while (recentList.size > MAX_RECENT_SEARCHES) recentList.removeAt(recentList.lastIndex)

        val prefs = requireContext().getSharedPreferences(PREFS_NAME, 0)
        prefs.edit {
            putString(PREF_KEY_RECENT, recentList.joinToString("|"))
        }

        updateRecentUI()
    }

    private fun loadRecentSearches() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, 0)
        val raw = prefs.getString(PREF_KEY_RECENT, "") ?: ""

        recentList.clear()
        if (raw.isNotBlank()) {
            recentList.addAll(raw.split("|").map { it.trim() }.filter { it.isNotBlank() })
        }

        updateRecentUI()
    }

    private fun clearRecentSearches() {
        recentList.clear()
        requireContext().getSharedPreferences(PREFS_NAME, 0)
            .edit { remove(PREF_KEY_RECENT) }
        updateRecentUI()
    }

    private fun updateRecentUI() {
        if (recentList.isNotEmpty()) {
            recentRecyclerView.visibility = View.VISIBLE
            tvNoRecent.visibility = View.GONE
            recentAdapter.notifyDataSetChanged()
        } else {
            recentRecyclerView.visibility = View.GONE
            tvNoRecent.visibility = View.VISIBLE
        }
    }

    private fun navigateToItemDetail(item: FeaturedItem) {
        try {
            val action = SearchFragmentDirections.actionSearchFragmentToItemDetailFragment(
                itemId = item.itemId,
                ownerId = item.ownerId
            )
            findNavController().navigate(action)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Opening item: ${item.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showKeyboard() {
        searchEditText.requestFocus()
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pendingSearch?.let { searchHandler.removeCallbacks(it) }
        pendingSearch = null
    }

    override fun onResume() {
        super.onResume()
        suppressNextTextWatcher = true
    }

}
