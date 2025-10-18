package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
import com.example.barterhub.adapters.UserItemAdapter
import com.example.barterhub.data.models.UserItem
import com.example.barterhub.databinding.FragmentHomeBinding
import com.example.barterhub.ui.viewmodel.HomeViewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*


class HomeFragment : Fragment(R.layout.fragment_home) {

    private var isDarkMode = false

    private lateinit var auth: FirebaseAuth
    private lateinit var userItemAdapter: UserItemAdapter
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // ✅ Connect to our ViewModel
    private val viewModel: HomeViewModel by viewModels()

    private lateinit var featuredAdapter: FeaturedAdapter

    private fun setupUserItemsRecyclerView(items: List<UserItem>, isDarkMode: Boolean) {
        userItemAdapter = UserItemAdapter(items)
        userItemAdapter.setDarkMode(isDarkMode)
        binding.featuredItems.adapter = userItemAdapter
        binding.featuredItems.layoutManager = GridLayoutManager(requireContext(), 2)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)
        auth = FirebaseAuth.getInstance()

        // 🌗 Dark mode adaptation
        val sharedPrefs = requireActivity().getSharedPreferences("AppPrefs", AppCompatActivity.MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean("dark_mode", false)
        val rootLayout = binding.rootLayout

        val categoryTextViews = listOf(
            binding.tvCategoryElectronics,
            binding.tvCategoryClothing,
            binding.tvCategoryKitchen,
            binding.tvCategoryBooks,
            binding.tvCategorySportsOutdoor
        )

        val filterText = binding.filterButtonText
        val filterIcon = binding.filterButtonIcon
        val tradeRequestIcon = binding.tradeRequestIcon
        val wishlistIcon = binding.itemWishlistIcon

        if (isDarkMode) {
            rootLayout.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.soft_dark_background))
            categoryTextViews.forEach { it.setTextColor(Color.WHITE) }
            filterText.setTextColor(Color.WHITE)
            filterIcon.setColorFilter(Color.WHITE)
            tradeRequestIcon.setColorFilter(Color.WHITE)
        } else {
            rootLayout.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
            categoryTextViews.forEach { it.setTextColor(Color.BLACK) }
            filterText.setTextColor(Color.BLACK)
            filterIcon.setColorFilter(Color.BLACK)
            tradeRequestIcon.setColorFilter(Color.BLACK)
        }

        setupStatusBarInsets()
        setupAd()
        setupRecyclerView()
        setupClickListeners()
        setupTradeRequestBadgeListener()
        observeViewModel()
        viewModel.loadAllItems()
    }



    private fun observeViewModel() {
            // 🧩 Observe LiveData for items
            viewModel.items.observe(viewLifecycleOwner, Observer { itemList ->
                Log.d("HomeFragment", "✅ Loaded ${itemList.size} items")

                // Optional: log bawat item para makita kung tama ang data
                itemList.forEach { item ->
                    Log.d("HomeFragment", "Item: ${item.title}, ID: ${item.itemId}")
                }

                featuredAdapter.updateData(itemList)
            })


            // 🌀 Optional: show loading
            viewModel.isLoading.observe(viewLifecycleOwner, Observer { isLoading ->
                binding.featuredProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            })
        }


    private fun setupRecyclerView() {
        featuredAdapter = FeaturedAdapter(mutableListOf())
        featuredAdapter.setDarkMode(isDarkMode)
        binding.featuredItems.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = featuredAdapter
        }
    }


    private fun setupTradeRequestBadgeListener() {
        val currentUserId = auth.currentUser?.uid ?: return

        FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("trade_requests")
            .orderByChild("owner")
            .equalTo(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    if (!isAdded || _binding == null) {
                        return
                    }
                    
                    val pendingCount = snapshot.children.count { reqSnap ->
                        val status = reqSnap.child("status").getValue(String::class.java)
                        status == "Pending"
                    }

                    updateTradeRequestBadge(pendingCount)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("HomeFragment", "❌ Failed: ${error.message}")
                }
            })
    }

    private fun updateTradeRequestBadge(count: Int) {

        if (!isAdded || _binding == null) {
            return
        }

        val badgeTextView = binding.tradeRequestBadge
        if (count > 0) {
            badgeTextView.text = count.toString()
            badgeTextView.visibility = View.VISIBLE
        } else {
            badgeTextView.visibility = View.GONE
        }
    }

    private fun setupStatusBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = -statusBarHeight
            insets
        }
    }

    private fun setupAd() {
        val adRequest = AdRequest.Builder().build()
        binding.adViewTop.loadAd(adRequest)
    }

    private fun setupClickListeners() {
        binding.tradeRequestIcon.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_tradeRequestsFragment)
        }

        binding.itemWishlistIcon.setOnClickListener {
            Toast.makeText(requireContext(), "Wishlist feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        binding.searchIcon.setOnClickListener { binding.searchInput.requestFocus() }

        setupCategoryClicks()
        setupFilterButton()
    }

    private fun setupFilterButton() {
        binding.filterButton.setOnClickListener {
            toggleCategoriesExpansion()
        }

        setupExpandedCategories()
    }

    private var isCategoriesExpanded = false

    @SuppressLint("SetTextI18n")
    private fun toggleCategoriesExpansion() {
        isCategoriesExpanded = !isCategoriesExpanded
        if (isCategoriesExpanded) {
            binding.expandedCategoriesScroll.visibility = View.VISIBLE
            binding.filterButtonText.text = "Hide All Categories"
        } else {
            binding.expandedCategoriesScroll.visibility = View.GONE
            binding.filterButtonText.text = "Show All Categories"
        }
    }

    private fun setupExpandedCategories() {
        val categories = mapOf(
            "Electronics" to R.drawable.ic_electronics,
            "Kitchen" to R.drawable.ic_kitchen,
            "Clothing" to R.drawable.ic_clothings,
            "Books" to R.drawable.ic_books,
            "Sports & Outdoors" to R.drawable.ic_sports,
            "Food & Beverages" to R.drawable.food,
            "Vehicles" to R.drawable.car,
            "Baby & Kids" to R.drawable.baby,
            "Pet Supplies" to R.drawable.pet,
            "Rice" to R.drawable.rice,
            "Fish & Seafood" to R.drawable.fish,
            "Meat & Poultry" to R.drawable.meat,
            "Fruits & Vegetables" to R.drawable.vegetable,
            "Groceries" to R.drawable.grocery,
            "Home Appliances" to R.drawable.furniture,
            "Handmade & Crafts" to R.drawable.craft,
            "Livestock" to R.drawable.livestock,
            "Services" to R.drawable.service,
            "Others" to R.drawable.ic_others,
        )

        val container = binding.expandedCategoriesContainer

        categories.forEach { (categoryName, iconRes) ->
            val chip = createCategoryChip(categoryName, iconRes)
            container.addView(chip)
        }
    }

    private fun createCategoryChip(categoryName: String, iconRes: Int): Chip {
        return Chip(requireContext()).apply {
            text = categoryName
            isCheckable = false
            isClickable = true

            setChipIconResource(iconRes)
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
                Toast.makeText(requireContext(), "Showing: $categoryName", Toast.LENGTH_SHORT).show()
                viewModel.loadItemsByCategory(categoryName)
                if (isCategoriesExpanded) toggleCategoriesExpansion()
            }
        }
    }

    private fun setupCategoryClicks() {
        binding.categoryElectronics.setOnClickListener {
            viewModel.loadItemsByCategory("Electronics")
        }
        binding.categoryClothing.setOnClickListener {
            viewModel.loadItemsByCategory("Clothing")
        }
        binding.categoryHomeKitchen.setOnClickListener {
            viewModel.loadItemsByCategory("Kitchen")
        }
        binding.categoryBooks.setOnClickListener {
            viewModel.loadItemsByCategory("Books")
        }
        binding.categorySports.setOnClickListener {
            viewModel.loadItemsByCategory("Sports & Outdoors")
        }
        binding.viewAllCategories.setOnClickListener {
            Toast.makeText(requireContext(), "Showing all items", Toast.LENGTH_SHORT).show()
            viewModel.loadAllItems()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// --- Extension function for dp → px ---
fun Int.dpToPx(view: View): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this.toFloat(),
        view.resources.displayMetrics
    ).toInt()
}
