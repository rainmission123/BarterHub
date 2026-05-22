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
import com.example.barterhub.ui.helpers.BannerAnimationHelper
import com.example.barterhub.ui.profile.ProfilePremiumManager

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
        setupStatusBarInsets()
        setupClickListeners()
        setupTradeRequestBadgeListener()
        setupWishlistBadgeListener()
        observeViewModel()
        viewModel.loadAllItems()
        setupScrollListener()
        setupTrendingSlider()

        BannerAnimationHelper.startPulse(
            binding.dailyChallengeBanner
        ) { _binding != null }

        BannerAnimationHelper.startShimmerEffect(
            binding.dailyChallengeBanner,
            binding.shimmerView
        ) { _binding != null }

        startCoinGlow()
        setupDailyChallengeBannerAccess()
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

    private fun navigateToAllTrendingItems() {
        try {
            findNavController().navigate(R.id.action_homeFragment_to_allTrendingItemsFragment)
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error navigating to AllTrendingItems: ${e.message}")
            Toast.makeText(requireContext(), "Cannot open trending items", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupWishlistBadgeListener() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val ref = FirebaseDatabase.getInstance()
            .getReference("favorites")
            .child(currentUserId)

        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return

                val count = snapshot.childrenCount.toInt()

                if (count > 0) {
                    binding.wishlistBadge.visibility = View.VISIBLE
                    binding.wishlistBadge.text = count.toString()
                } else {
                    binding.wishlistBadge.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("WishlistBadge", "Error: ${error.message}")
            }
        })
    }

    private fun loadTrendingItemsFromFirebase() {
        val db = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")

        db.getReference("items")
            .orderByChild("timestamp")
            .limitToLast(50)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    if (_binding == null || !isAdded) return

                    val allItems = mutableListOf<FeaturedItem>()

                    for (itemSnapshot in snapshot.children) {
                        val item = itemSnapshot.getValue(FeaturedItem::class.java)
                        if (item != null) {
                            item.itemId = itemSnapshot.key ?: item.itemId

                            val isActive = itemSnapshot.child("isActive")
                                .getValue(Boolean::class.java) ?: true

                            val isArchived = itemSnapshot.child("isArchived")
                                .getValue(Boolean::class.java) ?: false

                            if (item.ownerId.isNotBlank() && isActive && !isArchived) {
                                allItems.add(item)
                            }
                        }
                    }

                    if (allItems.isEmpty()) {
                        binding.trendingViewPager.visibility = View.GONE
                        return
                    }

                    loadPremiumUsersForHomeTrending(allItems)
                }

                override fun onCancelled(error: DatabaseError) {

                    if (_binding == null || !isAdded) return

                    Log.e("TRENDING_DEBUG", "Failed to load items: ${error.message}")
                    binding.trendingViewPager.visibility = View.GONE
                }
            })
    }

    private fun loadPremiumUsersForHomeTrending(allItems: List<FeaturedItem>) {
        val db = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
        val now = System.currentTimeMillis()

        db.getReference("public_users")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    if (_binding == null || !isAdded) return

                    val premiumUserIds = mutableSetOf<String>()

                    for (userSnapshot in snapshot.children) {
                        val uid = userSnapshot.key ?: continue
                        val isPremium = userSnapshot.child("isPremium").getValue(Boolean::class.java) ?: false
                        val premiumExpiry = userSnapshot.child("premiumExpiry").getValue(Long::class.java) ?: 0L

                        if (isPremium && premiumExpiry > now) {
                            premiumUserIds.add(uid)
                        }
                    }

                    val trendingItems = allItems
                        .filter { it.ownerId in premiumUserIds }
                        .sortedWith(
                            compareByDescending<FeaturedItem> { it.likeCount }
                                .thenByDescending { it.timestamp }
                        )
                        .take(5)

                    trendingAdapter.submitList(trendingItems)

                    if (trendingItems.isNotEmpty()) {
                        binding.trendingSliderContainer.visibility = View.VISIBLE
                        binding.trendingViewPager.visibility = View.VISIBLE
                        trendingSliderManager?.startAutoSlide()
                    } else {
                        binding.trendingViewPager.visibility = View.GONE
                        Log.d("TRENDING_DEBUG", "No premium trending items found")
                    }
                }

                override fun onCancelled(error: DatabaseError) {

                    if (_binding == null || !isAdded) return

                    Log.e("TRENDING_DEBUG", "Failed to load public users: ${error.message}")
                    binding.trendingViewPager.visibility = View.GONE
                }
            })
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
        featuredAdapter.setOnThreeDotsClickListener { anchorView, item ->
            showItemOptionsMenu(anchorView, item)
        }

        binding.featuredItems.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = featuredAdapter

            addItemDecoration(GridItemDecoration(4.dpToPx(this)))
        }
    }

    private fun showItemOptionsMenu(anchorView: View, item: FeaturedItem) {
        val title = item.title
        val itemId = item.itemId

        val popup = androidx.appcompat.widget.PopupMenu(
            requireContext(),
            anchorView,
            android.view.Gravity.END
        )

        popup.menu.add(0, 1, 0, "Share")
        popup.menu.add(0, 2, 1, "Report")

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    shareItem(title)
                    true
                }
                2 -> {
                    reportItem(itemId, title)
                    true
                }
                else -> false
            }
        }

        popup.show()
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
        binding.btnMenu.setOnClickListener {
            (activity as? HomeActivity)?.toggleDrawer()
        }

        binding.dailyChallengeBanner.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_dailyChallengesFragment)
        }

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
        val topBar = binding.topBar

        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top

            view.setPadding(
                view.paddingLeft,
                statusBarHeight + 5.dpToPx(view),
                view.paddingRight,
                5.dpToPx(view)
            )

            insets
        }

        ViewCompat.requestApplyInsets(topBar)
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

        if (_binding == null || !isAdded) return

        viewModel.loadAllItems()

        view?.postDelayed({
            if (_binding != null && isAdded) {
                binding.scrollContent.scrollTo(0, 0)
            }
        }, 100)
    }

    override fun onDestroyView() {
        trendingSliderManager?.stopAutoSlide()
        trendingSliderManager = null

        _binding?.coinGlow?.animate()?.cancel()

        _binding = null
        super.onDestroyView()
    }

    private fun startCoinGlow() {
        binding.coinGlow.alpha = 0.45f
        binding.coinGlow.scaleX = 0.85f
        binding.coinGlow.scaleY = 0.85f

        binding.coinGlow.animate()
            .alpha(1f)
            .scaleX(1.35f)
            .scaleY(1.35f)
            .setDuration(850)
            .withEndAction {
                if (_binding == null) return@withEndAction

                binding.coinGlow.animate()
                    .alpha(0.45f)
                    .scaleX(0.85f)
                    .scaleY(0.85f)
                    .setDuration(850)
                    .withEndAction {
                        if (_binding != null) startCoinGlow()
                    }
                    .start()
            }
            .start()
    }

    private fun setupDailyChallengeBannerAccess() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid == null) {
            if (_binding != null && isAdded) {
                showLockedDailyBanner()
            }
            return
        }

        FirebaseDatabase.getInstance()
            .reference
            .child("users")
            .child(uid)
            .get()
            .addOnSuccessListener { snap ->

                if (_binding == null || !isAdded) return@addOnSuccessListener

                val isPremium = snap.child("isPremium").getValue(Boolean::class.java) ?: false
                val expiry = snap.child("premiumExpiry").getValue(Long::class.java) ?: 0L
                val premiumActive = isPremium && expiry > System.currentTimeMillis()

                if (premiumActive) {
                    showUnlockedDailyBanner()
                } else {
                    showLockedDailyBanner()
                }
            }
            .addOnFailureListener {

                if (_binding == null || !isAdded) return@addOnFailureListener

                showLockedDailyBanner()
            }
    }

    private fun showUnlockedDailyBanner() {
        if (_binding == null || !isAdded) return

        binding.tvDailyBannerTitle.text = "Daily Challenges"
        binding.tvDailyBannerSubtitle.text = "Claim rewards and earn coins daily"
        binding.dailyChallengeBanner.alpha = 1f

        binding.dailyChallengeBanner.setOnClickListener {
            if (_binding != null && isAdded) {
                findNavController().navigate(R.id.action_home_to_dailyChallengesFragment)
            }
        }
    }

    private fun showLockedDailyBanner() {
        if (_binding == null || !isAdded) return

        binding.tvDailyBannerTitle.text = "Premium Challenges 🔒"
        binding.tvDailyBannerSubtitle.text = "Upgrade to Premium to earn daily coins 💰"
        binding.dailyChallengeBanner.alpha = 0.85f

        binding.dailyChallengeBanner.setOnClickListener {
            if (_binding != null && isAdded) {
                ProfilePremiumManager(this).showPremiumDirect()
            }
        }
    }
}