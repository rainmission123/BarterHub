package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.barterhub.R
import com.example.barterhub.adapters.FeaturedAdapter
import com.example.barterhub.data.models.FeaturedItem
import com.example.barterhub.databinding.FragmentHomeBinding
import com.example.barterhub.ui.helpers.TrendingSliderManager
import com.example.barterhub.ui.viewmodel.HomeViewModel
import com.example.barterhub.utils.Categories
import com.example.barterhub.utils.GridItemDecoration
import com.example.barterhub.utils.dpToPx
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.example.barterhub.adapters.TrendingAdapter

class HomeFragment : Fragment(R.layout.fragment_home) {
    private lateinit var trendingAdapter: TrendingAdapter
    private lateinit var prefs: SharedPreferences
    private lateinit var auth: FirebaseAuth
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var progressBar: ProgressBar
    private lateinit var mainContent: LinearLayout

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var featuredAdapter: FeaturedAdapter
    private var isCategoriesExpanded = false
    private var trendingSliderManager: TrendingSliderManager? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)
        auth = FirebaseAuth.getInstance()
        prefs = requireActivity().getSharedPreferences("AppPrefs", AppCompatActivity.MODE_PRIVATE)

        trendingSliderManager = TrendingSliderManager(binding.trendingViewPager)

        initializeLoadingViews(view)
        showLoading(true)
        setupRecyclerView()
        setupUI()
        view.postDelayed({
            setupStatusBarInsets()
            setupClickListeners()
            setupTradeRequestBadgeListener()
            observeViewModel()
            viewModel.loadAllItems()
            setupScrollListener()
            setupTrendingSlider()
        }, 1)
    }

    private fun setupTrendingSlider() {
        trendingAdapter = TrendingAdapter { item ->
            Toast.makeText(requireContext(), item.title, Toast.LENGTH_SHORT).show()
        }

        binding.trendingViewPager.adapter = trendingAdapter
        binding.trendingSliderContainer.visibility = View.VISIBLE

        // Set click listener para sa "View All"
        binding.trendingViewAll.setOnClickListener {
            navigateToAllTrendingItems()
        }

        loadTrendingItemsFromFirebase()
    }

    // Sa HomeFragment.kt, hanapin ang navigateToAllTrendingItems()
    private fun navigateToAllTrendingItems() {
        try {
            // Gamitin ang action ID na nasa nav_graph niyo
            findNavController().navigate(R.id.action_homeFragment_to_allTrendingItemsFragment)
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error navigating to AllTrendingItems: ${e.message}")
            Toast.makeText(requireContext(), "Cannot open trending items", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadTrendingItemsFromFirebase() {
        val ref = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("items")

        // Kunin ang latest items (pwede ring orderByChild("timestamp").limitToLast(20) para marami)
        ref.orderByChild("timestamp").limitToLast(20)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val trendingItems = mutableListOf<FeaturedItem>()
                    val premiumUserIds = mutableSetOf<String>()

                    Log.d("TRENDING_DEBUG", "📦 Loading items for trending slider...")

                    // Una, kunin muna natin ang lahat ng items
                    val allItems = mutableListOf<FeaturedItem>()
                    for (itemSnapshot in snapshot.children) {
                        val item = itemSnapshot.getValue(FeaturedItem::class.java)
                        item?.let {
                            it.itemId = itemSnapshot.key ?: ""
                            allItems.add(it)
                        }
                    }

                    val ownerIds = allItems.map { it.ownerId }.distinct()

                    if (ownerIds.isEmpty()) {
                        binding.trendingSliderContainer.visibility = View.GONE
                        return
                    }

                    val usersRef = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
                        .getReference("users")

                    var processedCount = 0

                    ownerIds.forEach { ownerId ->
                        usersRef.child(ownerId).child("isPremium").addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(userSnapshot: DataSnapshot) {
                                val isPremium = userSnapshot.getValue(Boolean::class.java) ?: false

                                if (isPremium) {
                                    premiumUserIds.add(ownerId)
                                }

                                processedCount++

                                if (processedCount == ownerIds.size) {
                                    filterItemsByPremiumUsers(allItems, premiumUserIds)
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Log.e("TRENDING_DEBUG", "Failed to check user premium status: ${error.message}")
                                processedCount++

                                if (processedCount == ownerIds.size) {
                                    filterItemsByPremiumUsers(allItems, premiumUserIds)
                                }
                            }
                        })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("TRENDING_DEBUG", "❌ Failed to load trending items: ${error.message}")
                    binding.trendingSliderContainer.visibility = View.GONE
                }
            })
    }

    private fun filterItemsByPremiumUsers(allItems: List<FeaturedItem>, premiumUserIds: Set<String>) {
        val trendingItems = allItems.filter { it.ownerId in premiumUserIds }
            .sortedByDescending { it.timestamp } // Pababang ayos (latest first)
            .take(5)

        Log.d("TRENDING_DEBUG", "📊 Total items: ${allItems.size}")
        Log.d("TRENDING_DEBUG", "⭐ Premium users: ${premiumUserIds.size}")
        Log.d("TRENDING_DEBUG", "🔥 Trending items (premium only): ${trendingItems.size}")

        trendingItems.forEachIndexed { index, item ->
            Log.d("TRENDING_DEBUG", "  $index: ${item.title} - Owner: ${item.ownerId} (Premium)")
        }

        trendingAdapter.submitList(trendingItems)

        if (trendingItems.isNotEmpty()) {
            binding.trendingSliderContainer.visibility = View.VISIBLE
            trendingSliderManager?.startAutoSlide()
        } else {
            binding.trendingSliderContainer.visibility = View.GONE
            Log.d("TRENDING_DEBUG", "😢 No premium items found")
        }
    }

    override fun onPause() {
        super.onPause()
        trendingSliderManager?.stopAutoSlide()
    }

    private fun initializeLoadingViews(view: View) {
        progressBar = view.findViewById(R.id.progressBar)
        mainContent = view.findViewById(R.id.mainContent)
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        mainContent.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun setupUI() {
        Log.d("ThemeDebug", "🎨 Using default theme from XML")
    }
    private fun setupRecyclerView() {
        featuredAdapter = FeaturedAdapter()
        featuredAdapter.setOnThreeDotsClickListener { item ->
            showItemOptionsMenu(item)
        }

        binding.featuredItems.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = featuredAdapter

            addItemDecoration(GridItemDecoration(4.dpToPx(this)))
        }
    }

    private fun showItemOptionsMenu(item: FeaturedItem) {
        val title = item.title
        val itemId = item.itemId

        val options = arrayOf("Share", "Report", "Cancel")

        AlertDialog.Builder(requireContext())
            .setTitle("Item Options")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> shareItem(title)
                    1 -> reportItem(itemId, title)
                    // 2 is Cancel - do nothing
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun shareItem(title: String) {
        val shareText = "Check out this item on BarterHub:\n\n" +
                "📱 $title\n\n" +
                "Download BarterHub to see more!"

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share Item"))
    }

    private fun reportItem(itemId: String, title: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Report Item")
            .setMessage("Report '$title'? This will notify our moderators.")
            .setPositiveButton("Report") { dialog, _ ->
                reportItemToModerators(itemId, title)
                Toast.makeText(requireContext(), "Item reported successfully", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun reportItemToModerators(itemId: String, title: String) {
        Log.d("Report", "Reporting item: $itemId - $title")
    }

    private fun observeViewModel() {
        viewModel.items.observe(viewLifecycleOwner, Observer { itemList ->
            // ✅ ADDITIONAL DEBUG: Check each item
            itemList.forEachIndexed { index, item ->
                Log.d("FragmentDebug", "Item $index received:")
                Log.d("FragmentDebug", "  Title: '${item.title}'")
                Log.d("FragmentDebug", "  Description: '${item.description}'")
                Log.d("FragmentDebug", "  Desc length: ${item.description.length}")
            }

            featuredAdapter.updateData(itemList)
            showLoading(false)
        })

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.featuredProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun setupClickListeners() {
        binding.tradeRequestIcon.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_tradeRequestsFragment)
        }
        binding.itemWishlistIcon.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_favoritesFragment)
        }
        binding.searchIcon.setOnClickListener {
            binding.searchInput.requestFocus()
        }

        binding.Toolcamera.setOnClickListener {
            navigateToAddPhotos()
        }

        setupCategoryClicks()
        setupFilterButton()
    }

    private fun navigateToAddPhotos() {
        try {
            findNavController().navigate(R.id.action_homeFragment_to_addPhotosFragment)
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error navigating to Add Photos: ${e.message}")
            Toast.makeText(requireContext(), "Cannot open camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCategoryClicks() {
        val categories = mapOf(
            binding.categoryElectronics to "Electronics",
            binding.categoryClothing to "Clothing",
            binding.categoryHomeKitchen to "Kitchen",
            binding.categoryBooks to "Books",
            binding.categorySports to "Sports & Outdoors"
        )

        categories.forEach { (view, category) ->
            view.setOnClickListener {
                showLoading(true)
                viewModel.loadItemsByCategory(category)
            }
        }

        binding.viewAllCategories.setOnClickListener {
            showLoading(true)
            viewModel.loadAllItems()
        }
    }

    private fun setupFilterButton() {
        binding.filterButton.setOnClickListener {
            toggleCategoriesExpansion()
        }
        setupExpandedCategories()
    }

    @SuppressLint("SetTextI18n")
    private fun setupExpandedCategories() {
        val container = binding.expandedCategoriesContainer
        Categories.CATEGORIES_WITH_ICONS.forEach { (name, icon) ->
            container.addView(createCategoryChip(name, icon))
        }
    }

    private fun createCategoryChip(name: String, icon: Int): Chip {
        return Chip(requireContext()).apply {
            text = name
            isCheckable = false
            isClickable = true
            setChipIconResource(icon)
            isChipIconVisible = true
            chipIconSize = 45f
            setChipBackgroundColorResource(R.color.gray_200)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_700))
            setPadding(20, 12, 20, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 8, 8, 8) }

            setOnClickListener {
                showLoading(true)
                viewModel.loadItemsByCategory(name)
                if (isCategoriesExpanded) toggleCategoriesExpansion()
            }
        }
    }

    private fun toggleCategoriesExpansion() {
        isCategoriesExpanded = !isCategoriesExpanded
        binding.expandedCategoriesScroll.visibility = if (isCategoriesExpanded) View.VISIBLE else View.GONE
    }

    private fun setupTradeRequestBadgeListener() {
        val currentUserId = auth.currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("trade_requests")

        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return
                var pendingCount = 0
                for (tradeSnap in snapshot.children) {
                    val tradeMap = tradeSnap.value as? Map<*, *> ?: continue
                    val toUserId = (tradeMap["toUser"] as? Map<*, *>)?.get("userId") as? String ?: ""
                    val fromUserId = (tradeMap["fromUser"] as? Map<*, *>)?.get("userId") as? String ?: ""
                    val status = tradeMap["status"] as? String ?: ""
                    if ((toUserId == currentUserId || fromUserId == currentUserId) && status == "Pending") pendingCount++
                }
                updateTradeRequestBadge(pendingCount)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeFragment", "Failed to load trade requests: ${error.message}")
            }
        })
    }

    private fun updateTradeRequestBadge(count: Int) {
        if (!isAdded || _binding == null) return
        binding.tradeRequestBadge.visibility = if (count > 0) {
            binding.tradeRequestBadge.text = count.toString()
            View.VISIBLE
        } else View.GONE
    }

    private fun setupStatusBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }
    }

    private fun setupScrollListener() {
        val scrollView = binding.scrollContent
        val filterButtonContainer = binding.filterButton
        val categoriesView = binding.categoriesScroll

        scrollView.viewTreeObserver.addOnScrollChangedListener {
            val scrollY = scrollView.scrollY
            val categoryBottom = categoriesView.bottom
            if (scrollY >= categoryBottom) {
                filterButtonContainer.translationY = scrollY - categoryBottom.toFloat()
                filterButtonContainer.elevation = 10f
            } else {
                filterButtonContainer.translationY = 0f
                filterButtonContainer.elevation = 0f
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAllItems()
        view?.postDelayed({ binding.scrollContent.scrollTo(0, 0) }, 100)
    }

    override fun onDestroyView() {
        trendingSliderManager?.stopAutoSlide()
        trendingSliderManager = null
        _binding = null
        super.onDestroyView()
    }
}