package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
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
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.adapters.FeaturedAdapter
import com.example.barterhub.data.models.FeaturedItem
import com.example.barterhub.databinding.FragmentHomeBinding
import com.example.barterhub.ui.viewmodel.HomeViewModel
import com.example.barterhub.utils.Categories
import com.google.android.gms.ads.AdRequest
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*


class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var prefs: SharedPreferences
    private lateinit var auth: FirebaseAuth
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var progressBar: ProgressBar
    private lateinit var mainContent: LinearLayout

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var featuredAdapter: FeaturedAdapter

    private var isCategoriesExpanded = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)
        auth = FirebaseAuth.getInstance()
        prefs = requireActivity().getSharedPreferences("AppPrefs", AppCompatActivity.MODE_PRIVATE)

        initializeLoadingViews(view)
        showLoading(true)
        setupRecyclerView()
        setupUI()
        diagnoseFirebaseData()
        cleanupDatabaseFields()

        view.postDelayed({
            setupStatusBarInsets()
            setupAd()
            setupClickListeners()
            setupTradeRequestBadgeListener()
            observeViewModel()
            viewModel.loadAllItems()
            setupScrollListener()
        }, 1)
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

            // ✅ 2dp spacing sa gitna lang
            addItemDecoration(GridItemDecoration(4.dpToPx(this)))
        }
    }
    class GridItemDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            super.getItemOffsets(outRect, view, parent, state)

            val position = parent.getChildAdapterPosition(view)
            val column = position % 2

            // ✅ SPACING SA GITNA LANG (2dp)
            if (column == 0) {
                // Left item: right spacing only
                outRect.right = spacing / 2
            } else {
                // Right item: left spacing only
                outRect.left = spacing / 2
            }

            // ✅ Bottom spacing (2dp din)
            outRect.bottom = spacing

            // ✅ Top spacing for first row only
            if (position < 2) {
                outRect.top = spacing
            }
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
                    0 -> shareItem(item, title)
                    1 -> reportItem(itemId, title)
                    // 2 is Cancel - do nothing
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun shareItem(item: FeaturedItem, title: String) {
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

    private fun diagnoseFirebaseData() {
        val ref = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("items")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("DIAGNOSE", "=== FIREBASE DATA DIAGNOSIS ===")
                Log.d("DIAGNOSE", "Total items in DB: ${snapshot.childrenCount}")

                var index = 0
                snapshot.children.forEach { child ->
                    index++
                    Log.d("DIAGNOSE", "--- Item $index (${child.key}) ---")

                    // Check ALL fields
                    child.children.forEach { field ->
                        val key = field.key
                        val value = field.value
                        val type = value?.javaClass?.simpleName ?: "NULL"

                        Log.d("DIAGNOSE", "  $key: '$value' (type: $type)")
                    }

                    // Special check for description
                    val desc = child.child("description").value
                    Log.d("DIAGNOSE", "  DESCRIPTION ANALYSIS:")
                    Log.d("DIAGNOSE", "    Raw value: '$desc'")
                    Log.d("DIAGNOSE", "    Type: ${desc?.javaClass?.simpleName ?: "NULL"}")
                    Log.d("DIAGNOSE", "    Is null: ${desc == null}")
                    Log.d("DIAGNOSE", "    Is empty string: ${desc == ""}")
                    Log.d("DIAGNOSE", "    Is blank string: ${(desc as? String)?.isBlank()}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DIAGNOSE", "Error: ${error.message}")
            }
        })
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

    private fun setupAd() {
        binding.adViewTop.loadAd(AdRequest.Builder().build())
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

    private fun cleanupDatabaseFields() {
        val ref = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("items")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { child ->
                    val updates = HashMap<String, Any>()

                    // Remove originalPrice if exists
                    if (child.child("originalPrice").exists()) {
                        updates["originalPrice"] = ""
                    }

                    // Remove ownerProfileImage from items (keep in users only)
                    if (child.child("ownerProfileImage").exists()) {
                        updates["ownerProfileImage"] = ""
                    }

                    // Apply updates if needed
                    if (updates.isNotEmpty()) {
                        child.ref.updateChildren(updates)
                            .addOnSuccessListener {
                                Log.d("Cleanup", "✅ Cleaned item ${child.key}")
                            }
                            .addOnFailureListener { e ->
                                Log.e("Cleanup", "❌ Failed to clean item ${child.key}: ${e.message}")
                            }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Cleanup", "Error: ${error.message}")
            }
        })
    }

    override fun onResume() {
        super.onResume()
        setupTradeRequestBadgeListener()
        viewModel.loadAllItems() // ✅ Refresh data when coming back
        view?.postDelayed({ binding.scrollContent.scrollTo(0, 0) }, 100)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

fun Int.dpToPx(view: View): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        view.resources.displayMetrics
    ).toInt()
}