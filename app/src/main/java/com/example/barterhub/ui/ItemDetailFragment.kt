package com.example.barterhub.ui

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.data.models.TradeManager
import com.example.barterhub.databinding.ActivityItemDetailBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ItemDetailFragment : Fragment(), OnMapReadyCallback {

    private var _binding: ActivityItemDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var itemDatabase: DatabaseReference
    private lateinit var userDatabase: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var googleMap: GoogleMap

    private var itemId: String = ""
    private var ownerId: String = ""
    private var currentUserId: String = ""
    private var itemTitle: String = ""
    private var ownerName: String = ""
    private var itemLatitude: Double = 0.0
    private var itemLongitude: Double = 0.0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityItemDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("ItemDetailFragment", "🎬 onViewCreated started")

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""

        // Get arguments from navigation
        itemId = arguments?.getString("itemId") ?: ""
        ownerId = arguments?.getString("ownerId") ?: ""

        Log.d("ItemDetailFragment", "📱 Starting with itemId: $itemId, ownerId: $ownerId")

        // Initialize Firebase references
        itemDatabase = FirebaseDatabase
            .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("items")
        userDatabase = FirebaseDatabase
            .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("users")

        // ✅ Setup Google Map
        setupMap()

        Log.d("ItemDetailFragment", "🔄 About to call loadItemDetails()")
        loadItemDetails()
        Log.d("ItemDetailFragment", "✅ loadItemDetails() called")
        Log.d("ItemDetailFragment", "🏁 onViewCreated completed") // ✅ FIXED TYPO

        // Setup button click listeners
        setupButtonListeners()

        // ✅ REQUEST TRADE BUTTON
        binding.btnRequestBarter.setOnClickListener {
            if (currentUserId.isEmpty()) {
                Toast.makeText(requireContext(), "Please login to send requests", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🚫 Prevent sending request to own item
            if (currentUserId == ownerId) {
                Toast.makeText(requireContext(), "You cannot send a Barter request to your own item", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ✅ Proceed to send trade request
            if (itemId.isNotEmpty() && ownerId.isNotEmpty()) {
                TradeManager.sendTradeRequest(requireContext(), itemId, ownerId)
            } else {
                Toast.makeText(requireContext(), "Item not found", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun sendTradeRequest(itemId: String, ownerId: String) {
        val requesterId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val requestId = FirebaseDatabase.getInstance().reference.push().key ?: return

        val request = mapOf(
            "itemId" to itemId,
            "owner" to ownerId,
            "requester" to requesterId,
            "date" to System.currentTimeMillis(),
            "status" to "Pending"
        )

        FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("trade_requests")
            .child(requestId)
            .setValue(request)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Trade request sent!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to send request", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager
            .findFragmentById(R.id.mapView) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        Log.d("MapDebug", "✅ onMapReady called")
        googleMap = map

        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isScrollGesturesEnabled = true

        binding.mapLoading.visibility = View.GONE
        binding.btnOpenMaps.visibility = View.VISIBLE

        if (::googleMap.isInitialized && itemLatitude != 0.0 && itemLongitude != 0.0) {
            updateMapLocation()
        }
    }

    private fun updateMapLocation() {
        val itemLocation = LatLng(itemLatitude, itemLongitude)
        googleMap.addMarker(
            MarkerOptions()
                .position(itemLocation)
                .title("Item Location")
                .snippet(itemTitle)
        )
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(itemLocation, 15f))

        // Setup open in maps button
        binding.btnOpenMaps.setOnClickListener {
            openInGoogleMaps(itemLocation)
        }
    }

    private fun openInGoogleMaps(location: LatLng) {
        try {
            val gmmIntentUri =
                "geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}($itemTitle)".toUri()
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Google Maps app not installed", Toast.LENGTH_SHORT).show()
            // Fallback to browser
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}".toUri()
            )
            startActivity(browserIntent)
        }
    }

    private fun setupButtonListeners() {
        // Chat Button
        binding.btnChat.setOnClickListener {
            if (currentUserId.isEmpty()) {
                Toast.makeText(requireContext(), "Please login to start chatting", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if user is trying to chat with themselves
            if (ownerId == currentUserId) {
                Toast.makeText(requireContext(), "You cannot chat with yourself", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Generate chat ID
            val chatId = generateChatId()

            // Navigate to ChatFragment using Navigation Component
            val bundle = Bundle().apply {
                putString("chatId", chatId)
                putString("partnerId", ownerId)
                putString("partnerName", ownerName)
                putString("itemId", itemId)
                putString("itemTitle", itemTitle)
            }

            findNavController().navigate(R.id.action_itemDetailFragment_to_chatFragment, bundle)
        }

        // Like Button
        binding.btnLike.setOnClickListener {
            if (currentUserId.isEmpty()) {
                Toast.makeText(requireContext(), "Please login to like items", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            toggleLike()
        }

        // Make Offer Button
        binding.btnMakeOffer.setOnClickListener {
            if (currentUserId.isEmpty()) {
                Toast.makeText(requireContext(), "Please login to make offers", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (ownerId == currentUserId) {
                Toast.makeText(requireContext(), "You cannot make offers on your own item", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showMakeOfferDialog()
        }
    }

    private fun generateChatId(): String {
        return listOf(currentUserId, ownerId, itemId).sorted().joinToString("_")
    }

    private fun toggleLike() {
        Toast.makeText(requireContext(), "Like functionality coming soon!", Toast.LENGTH_SHORT).show()
    }

    private fun showMakeOfferDialog() {
        Toast.makeText(requireContext(), "Make offer functionality coming soon!", Toast.LENGTH_SHORT).show()
    }

    private fun loadItemImages(imageUrls: List<String?>) {
        val imageContainer = binding.imageContainer
        val indicatorContainer = binding.imageIndicator

        // Clear existing views
        imageContainer.removeAllViews()
        indicatorContainer.removeAllViews()

        // Limit to maximum 5 images
        val validImageUrls = imageUrls.take(5)

        // Load actual images
        for (i in validImageUrls.indices) {
            val imageUrl = validImageUrls[i]
            if (imageUrl.isNullOrEmpty()) continue

            val imageView = ImageView(requireContext())
            val params = LinearLayout.LayoutParams(
                dpToPx(375),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            params.setMargins(0, 0, 6, 0) // walang space para dikit-dikit
            imageView.layoutParams = params
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_light))

            Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.login_background)
                .error(R.drawable.login_background)
                .into(imageView)

            imageContainer.addView(imageView)
        }

        // Create indicators equal to image count
        for (i in validImageUrls.indices) {
            val dot = ImageView(requireContext())
            val dotParams = LinearLayout.LayoutParams(dpToPx(5), dpToPx(5))
            dotParams.setMargins(dpToPx(0), 0, dpToPx(5), 0)
            dot.layoutParams = dotParams

            val isActive = i == 0
            val dotDrawable = if (isActive)
                R.drawable.indicator_dot_active
            else
                R.drawable.indicator_dot_inactive

            dot.setBackgroundResource(dotDrawable)

            indicatorContainer.addView(dot)
        }

        // Snap to photo per scroll
        setupSnapScroll(validImageUrls.size)
    }

    private fun setupSnapScroll(totalImages: Int) {
        if (totalImages <= 0) return

        val scrollView = binding.horizontalScrollView
        val indicatorContainer = binding.imageIndicator

        var scrollRunnable: Runnable? = null

        scrollView.setOnScrollChangeListener { _: View, scrollX: Int, _: Int, _: Int, _: Int ->
            val firstChild = binding.imageContainer.getChildAt(0)
            val itemWidth = if (firstChild != null && firstChild.width > 0) {
                firstChild.width
            } else {
                dpToPx(375)
            }

            val position = (scrollX + itemWidth / 2) / itemWidth
            val currentIndex = position.coerceIn(0, totalImages - 1)

            // Update indicators
            for (i in 0 until indicatorContainer.childCount) {
                val dot = indicatorContainer.getChildAt(i) as ImageView
                val drawable = if (i == currentIndex) R.drawable.indicator_dot_active else R.drawable.indicator_dot_inactive
                dot.setBackgroundResource(drawable)
            }

            // Cancel previous pending snap
            scrollRunnable?.let { scrollView.removeCallbacks(it) }

            // Schedule snap after user stops scrolling (200ms delay)
            scrollRunnable = Runnable {
                scrollView.smoothScrollTo(currentIndex * itemWidth, 0)
            }
            scrollView.postDelayed(scrollRunnable, 10)
        }
    }

    private fun setupScrollListener(totalImages: Int) {
        binding.horizontalScrollView.viewTreeObserver.addOnScrollChangedListener {
            updateActiveIndicator(totalImages)
        }
    }

    private fun updateActiveIndicator(totalImages: Int) {
        if (totalImages == 0) return

        val scrollX = binding.horizontalScrollView.scrollX
        val imageView = binding.imageContainer.getChildAt(0) ?: return
        val imageWidth = imageView.width // margin 0 na, dikit-dikit

        val currentPosition = ((scrollX + imageWidth / 2) / imageWidth).toInt()
        val activePosition = currentPosition.coerceIn(0, totalImages - 1)

        for (i in 0 until binding.imageIndicator.childCount) {
            val dot = binding.imageIndicator.getChildAt(i) as ImageView
            val size = if (i == activePosition) dpToPx(12) else dpToPx(6)
            dot.layoutParams.width = size
            dot.layoutParams.height = size
            dot.requestLayout()

            val drawable = if (i == activePosition) R.drawable.indicator_dot_active else R.drawable.indicator_dot_inactive
            dot.setBackgroundResource(drawable)
        }
    }

    private fun scrollToImage(position: Int) {
        val imageWidth = dpToPx(320 + 8) // image width + margin
        val scrollX = position * imageWidth
        binding.horizontalScrollView.smoothScrollTo(scrollX, 0)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun loadItemDetails() {
        if (itemId.isEmpty()) {
            Toast.makeText(requireContext(), "Item not found", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        itemDatabase.child(itemId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(requireContext(), "Item not found", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                    return
                }

                val title = snapshot.child("title").getValue(String::class.java) ?: "No title"
                val description = snapshot.child("description").getValue(String::class.java) ?: ""
                val priceValue = snapshot.child("price").getValue(Any::class.java)
                val price = when (priceValue) {
                    is Long -> "₱${priceValue}"
                    is Double -> "₱${priceValue.toInt()}"
                    is String -> "₱$priceValue"
                    else -> "₱0"
                }
                val category = snapshot.child("category").getValue(String::class.java) ?: ""
                val location = snapshot.child("location").getValue(String::class.java) ?: ""
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                val itemOwnerId = snapshot.child("ownerId").getValue(String::class.java) ?: ""

                // ✅ DEBUG: CHECK ACTUAL VALUE TYPE
                val latitudeRaw = snapshot.child("latitude").value
                val longitudeRaw = snapshot.child("longitude").value
                Log.d("ItemDetailFragment", "📍 Latitude raw: $latitudeRaw (type: ${latitudeRaw?.javaClass?.simpleName})")
                Log.d("ItemDetailFragment", "📍 Longitude raw: $longitudeRaw (type: ${longitudeRaw?.javaClass?.simpleName})")

                // ✅ FIXED: SAFE COORDINATES HANDLING
                val latitudeValue = snapshot.child("latitude").getValue(Any::class.java)
                val longitudeValue = snapshot.child("longitude").getValue(Any::class.java)

                itemLatitude = when (latitudeValue) {
                    is Double -> latitudeValue
                    is Long -> latitudeValue.toDouble()
                    is String -> latitudeValue.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }

                itemLongitude = when (longitudeValue) {
                    is Double -> longitudeValue
                    is Long -> longitudeValue.toDouble()
                    is String -> longitudeValue.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }

                Log.d("ItemDetailFragment", "📍 Coordinates: $itemLatitude, $itemLongitude")

                // GET MULTIPLE IMAGES
                val imageUrls = mutableListOf<String?>()
                val imagesValue = snapshot.child("imageUrls").getValue(String::class.java)
                if (!imagesValue.isNullOrEmpty()) {
                    // Split string by comma
                    imageUrls.addAll(imagesValue.split(","))
                } else {
                    // Fallback sa single imageUrl
                    val singleImageUrl = snapshot.child("imageUrl").getValue(String::class.java)
                    if (!singleImageUrl.isNullOrEmpty()) {
                        imageUrls.add(singleImageUrl)
                    }
                }

                ownerId = itemOwnerId
                itemTitle = title

                Log.d("ItemDetailFragment", "📦 Loaded item: $title by owner: $ownerId")
                Log.d("ItemDetailFragment", "🖼️ Image URLs: ${imageUrls.size}")

                binding.itemTitle.text = title
                binding.itemDescription.text = description
                binding.itemPrice.text = price
                binding.itemCategory.text = category
                binding.itemLocation.text = location
                binding.postedDate.text = DateFormat.format("MMM dd, yyyy", timestamp)

                // ✅ LOAD MULTIPLE IMAGES
                if (imageUrls.isNotEmpty()) {
                    loadItemImages(imageUrls)
                } else {
                    // Fallback to single image if no images found
                    Glide.with(requireContext())
                        .load(R.drawable.login_background)
                        .into(binding.itemImage)
                }

                // ✅ UPDATE MAP IF COORDINATES ARE AVAILABLE
                if (itemLatitude != 0.0 && itemLongitude != 0.0 && ::googleMap.isInitialized) {
                    updateMapLocation()
                } else if (itemLatitude == 0.0 || itemLongitude == 0.0) {
                    // Hide map section if no coordinates
                    binding.mapLoading.visibility = View.GONE
                    binding.btnOpenMaps.visibility = View.GONE
                }

                loadOwnerInfo(ownerId)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Failed to load item: ${error.message}", Toast.LENGTH_SHORT).show()
                Log.e("ItemDetailFragment", "Failed to load item: ${error.message}")
            }
        })
    }

    private fun loadOwnerInfo(ownerId: String) {
        if (ownerId.isEmpty()) {
            binding.itemOwner.text = "Posted by: Unknown"
            Log.e("ItemDetailFragment", "❌ Empty ownerId")
            return
        }

        userDatabase.child(ownerId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.e("ItemDetailFragment", "❌ User not found: $ownerId")
                    binding.itemOwner.text = "Posted by: Unknown"
                    return
                }

                val ownerName = snapshot.child("fullName").getValue(String::class.java)
                    ?: snapshot.child("username").getValue(String::class.java)
                    ?: "Unknown"
                val ownerImage = snapshot.child("profileImageUrl").getValue(String::class.java) ?: ""
                val ownerRating = snapshot.child("rating").getValue(Double::class.java) ?: 0.0

                Log.d("ItemDetailFragment", "✅ Owner loaded: $ownerName")

                binding.itemOwner.text = "Posted by: $ownerName"
                this@ItemDetailFragment.ownerName = ownerName

                if (ownerImage.isNotEmpty()) {
                    Glide.with(requireContext())
                        .load(ownerImage)
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .circleCrop()
                        .into(binding.ownerImage)
                } else {
                    binding.ownerImage.setImageResource(R.drawable.ic_profile)
                }

                binding.ownerRating.text = "%.1f".format(ownerRating)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ItemDetailFragment", "❌ Failed to load owner: ${error.message}")
                Toast.makeText(requireContext(), "Failed to load owner info", Toast.LENGTH_SHORT).show()
                binding.itemOwner.text = "Posted by: Unknown"
                binding.ownerImage.setImageResource(R.drawable.ic_profile)
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}