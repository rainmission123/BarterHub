package com.example.barterhub.ui

import OfferDialogFragment
import android.annotation.SuppressLint
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
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.databinding.FragmentItemDetailBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.BitmapFactory
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import androidx.core.widget.ImageViewCompat
import android.content.res.ColorStateList
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.widget.Button
import com.google.firebase.database.getValue
import kotlin.math.max

class ItemDetailFragment : Fragment() {

    private var isLiked = false
    private var likeCount = 0
    private var _binding: FragmentItemDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var itemDatabase: DatabaseReference
    private lateinit var userDatabase: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var pointAnnotationManager: PointAnnotationManager

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
        _binding = FragmentItemDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("ItemDetailFragment", "🎬 onViewCreated started")

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""

        itemId = arguments?.getString("itemId") ?: ""
        ownerId = arguments?.getString("ownerId") ?: ""

        Log.d("ItemDetailFragment", "📱 Starting with itemId: $itemId, ownerId: $ownerId")

        itemDatabase = FirebaseDatabase
            .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("items")
        userDatabase = FirebaseDatabase
            .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("users")

        pointAnnotationManager = binding.mapViewItemDetail.annotations.createPointAnnotationManager()

        setupMapClickListener()
        setupOwnerClickListener()
        setupViewMoreButton()
        checkIfLiked()
        getLikeCount()
        val imageCount = 10
        setupSnapScroll(imageCount)

        Log.d("ItemDetailFragment", "🔄 About to call loadItemDetails()")
        loadItemDetails()
        Log.d("ItemDetailFragment", "✅ loadItemDetails() called")

        setupButtonListeners()

        binding.btnRequestBarter.setOnClickListener {
            println("DEBUG: Request Trade button clicked!")

            if (currentUserId.isEmpty()) {
                Toast.makeText(requireContext(), "Please login to send requests", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (currentUserId == ownerId) {
                Toast.makeText(requireContext(), "You cannot send a Barter request to your own item", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ✅ SHOW THE OFFER DIALOG INSTEAD OF DIRECTLY SENDING
            if (itemId.isNotEmpty() && ownerId.isNotEmpty()) {
                showOfferDialog()
            } else {
                Toast.makeText(requireContext(), "Item not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupOwnerClickListener() {

        val ownerImage = binding.ownerImage
        val ownerName = binding.itemOwner
        val ownerRating = binding.ownerRating
        val starIcon = binding.starIcon

        val clickableViews = arrayOf(
            ownerImage,
            ownerName,
            ownerRating,
            starIcon
        )

        clickableViews.forEach { view ->
            view.setOnClickListener {
                navigateToOwnerProfile()
            }
        }

        val root = binding.root
        val postedDate = binding.postedDate


        val ownerImageParent = ownerImage.parent
        if (ownerImageParent is LinearLayout) {

            var foundPostedDate = false
            for (i in 0 until ownerImageParent.childCount) {
                if (ownerImageParent.getChildAt(i) == postedDate) {
                    foundPostedDate = true
                    break
                }
            }

            if (foundPostedDate) {
                ownerImageParent.setOnClickListener {
                    navigateToOwnerProfile()
                }
                ownerImageParent.isClickable = true
                ownerImageParent.isFocusable = true

                // Add ripple effect programmatically
                val attrs = intArrayOf(android.R.attr.selectableItemBackground)
                val typedArray = requireContext().obtainStyledAttributes(attrs)
                val background = typedArray.getDrawable(0)
                ownerImageParent.background = background
                typedArray.recycle()
            }
        }
    }

    private fun navigateToOwnerProfile() {
        if (ownerId.isEmpty()) {
            Toast.makeText(requireContext(), "Owner information not available", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("ItemDetailFragment", "👤 Navigating to OwnerProfileFragment with ownerId: $ownerId")

        // Create bundle with ownerId
        val bundle = Bundle().apply {
            putString("ownerId", ownerId)
        }

        // Navigate to OwnerProfileFragment using navigation component
        // Make sure you have this action in your navigation graph
        findNavController().navigate(R.id.action_itemDetailFragment_to_ownerProfileFragment, bundle)
    }

    private fun showOfferDialog() {
        try {
            println("DEBUG: Showing offer dialog...")

            val dialog = OfferDialogFragment()

            val bundle = Bundle().apply {
                putString("itemId", itemId)
                putString("ownerId", ownerId)
                putString("itemTitle", itemTitle)
            }
            dialog.arguments = bundle

            dialog.show(parentFragmentManager, "OfferDialogFragment")
            println("DEBUG: Dialog show method called successfully")

        } catch (e: Exception) {
            println("DEBUG: Error showing dialog: ${e.message}")
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error opening offer dialog", Toast.LENGTH_SHORT).show()
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun redirectToProfileForVerification() {
        try {
            findNavController().navigate(R.id.nav_profile)
            Toast.makeText(requireContext(), "Please complete ID verification to view locations", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("ItemDetailFragment", "Navigation error: ${e.message}")
            AlertDialog.Builder(requireContext())
                .setTitle("Verification Required")
                .setMessage("Please go to your Profile to complete ID verification and unlock location features.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun checkIfUserIsVerified(onComplete: (Boolean) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.d("ItemDetailFragment", "❌ No user logged in")
            onComplete(false)
            return
        }

        val database = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("users")

        database.child(currentUser.uid).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val isIDVerified = snapshot.child("isIDVerified").getValue(String::class.java)
                val isVerified = isIDVerified == "verified"

                Log.d("ItemDetailFragment", "🔍 Map Access Check: isIDVerified='$isIDVerified' -> Access=$isVerified")
                onComplete(isVerified)
            } else {
                Log.d("ItemDetailFragment", "❌ User data not found")
                onComplete(false)
            }
        }.addOnFailureListener { error ->
            Log.e("ItemDetailFragment", "❌ Database error: ${error.message}")
            onComplete(false)
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
    }

    private fun generateChatId(): String {
        return listOf(currentUserId, ownerId, itemId).sorted().joinToString("_")
    }

    private fun toggleLike() {
        if (currentUserId.isEmpty()) {
            Toast.makeText(requireContext(), "Please login to like items", Toast.LENGTH_SHORT).show()
            return
        }

        if (itemId.isEmpty()) {
            Toast.makeText(requireContext(), "Item not found", Toast.LENGTH_SHORT).show()
            return
        }

        val itemsRef = FirebaseDatabase
            .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("items")
            .child(itemId)

        // ✅ DAGDAG: Use transaction para sa like count
        itemsRef.runTransaction(object : com.google.firebase.database.Transaction.Handler {
            override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                val itemMap = currentData.getValue<MutableMap<String, Any>>() ?: return com.google.firebase.database.Transaction.success(currentData)

                // Initialize likeCount if not exists
                if (!itemMap.containsKey("likeCount")) {
                    itemMap["likeCount"] = 0
                }
                if (!itemMap.containsKey("likedBy")) {
                    itemMap["likedBy"] = mutableMapOf<String, Boolean>()
                }

                val likedBy = itemMap["likedBy"] as? MutableMap<String, Boolean> ?: mutableMapOf()
                val currentLikeCount = (itemMap["likeCount"] as? Long)?.toInt() ?: 0

                val isCurrentlyLiked = likedBy[currentUserId] == true

                if (isCurrentlyLiked) {
                    // Unlike: remove user from likedBy and decrement count
                    likedBy.remove(currentUserId)
                    itemMap["likeCount"] = max(0, currentLikeCount - 1)
                    Log.d("LikeDebug", "👎 Unliked - New count: ${currentLikeCount - 1}")
                } else {
                    // Like: add user to likedBy and increment count
                    likedBy[currentUserId] = true
                    itemMap["likeCount"] = currentLikeCount + 1
                    Log.d("LikeDebug", "👍 Liked - New count: ${currentLikeCount + 1}")
                }

                itemMap["likedBy"] = likedBy
                currentData.value = itemMap
                return com.google.firebase.database.Transaction.success(currentData)
            }

            override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, currentData: com.google.firebase.database.DataSnapshot?) {
                if (error != null) {
                    Log.e("LikeFunction", "Like transaction failed: ${error.message}")
                    Toast.makeText(requireContext(), "Failed to like item", Toast.LENGTH_SHORT).show()
                } else if (committed) {
                    // Update favorites reference
                    val favoritesRef = FirebaseDatabase
                        .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
                        .getReference("favorites")
                        .child(currentUserId)
                        .child(itemId)

                    val isLiked = currentData?.child("likedBy")?.child(currentUserId)?.getValue(Boolean::class.java) ?: false

                    if (isLiked) {
                        favoritesRef.setValue(true)
                        Toast.makeText(requireContext(), "Item liked!", Toast.LENGTH_SHORT).show()

                        // ✅ SEND NOTIFICATION TO ITEM OWNER
                        if (currentUserId != ownerId) {
                            Log.d("NotificationDebug", "🚀 SENDING NOTIFICATION...")
                            sendLikeNotification()
                        }
                    } else {
                        favoritesRef.removeValue()
                        Toast.makeText(requireContext(), "Item unliked", Toast.LENGTH_SHORT).show()
                    }

                    // Update UI
                    this@ItemDetailFragment.isLiked = isLiked
                    updateLikeButton()

                    // ✅ DAGDAG: Log the updated like count
                    val updatedLikeCount = currentData?.child("likeCount")?.getValue(Int::class.java) ?: 0
                    Log.d("LikeDebug", "🎉 Like count updated to: $updatedLikeCount")
                }
            }
        })
    }

    private fun sendLikeNotification() {
        if (currentUserId == ownerId) {
            Log.d("NotificationDebug", "❌ Skipping notification - user liked their own item")
            return
        }

        val database = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
        val notificationRef = database.getReference("notifications").child(ownerId).push()

        val notificationData = mapOf(
            "type" to "like",
            "fromUserId" to currentUserId,
            "itemId" to itemId,
            "read" to false,
            "timestamp" to System.currentTimeMillis()
        )

        Log.d("NotificationDebug", "📤 Sending notification data:")
        Log.d("NotificationDebug", "   - To owner: $ownerId")
        Log.d("NotificationDebug", "   - From user: $currentUserId")
        Log.d("NotificationDebug", "   - Item: $itemId")
        Log.d("NotificationDebug", "   - Data: $notificationData")

        notificationRef.setValue(notificationData)
            .addOnSuccessListener {
                Log.d("NotificationDebug", "✅ Like notification SENT SUCCESSFULLY to owner: $ownerId")
                Log.d("NotificationDebug", "   - Notification ID: ${notificationRef.key}")
            }
            .addOnFailureListener { e ->
                Log.e("NotificationDebug", "❌ FAILED to send notification: ${e.message}")
                Log.e("NotificationDebug", "   - Error details: ${e.stackTraceToString()}")
            }
    }


    private fun updateLikeButton() {
        if (isLiked) {
            // Liked state - filled heart
            binding.btnLike.apply {
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite_filled)
                setIconTintResource(R.color.red_500)
                strokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.red_500))
                setTextColor(ContextCompat.getColor(requireContext(), R.color.red_500))
            }
        } else {
            // Not liked state - outline heart
            binding.btnLike.apply {
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite_border)
                setIconTintResource(R.color.teal_700)
                strokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.teal_700))
                setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_700))
            }
        }
    }

    // ✅ CHECK IF ITEM IS LIKED
    private fun checkIfLiked() {
        if (currentUserId.isEmpty() || itemId.isEmpty()) return

        val favoritesRef = FirebaseDatabase
            .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("favorites")
            .child(currentUserId)
            .child(itemId)

        favoritesRef.get().addOnSuccessListener { snapshot ->
            isLiked = snapshot.exists()
            updateLikeButton()
            Log.d("Like", "📊 Like status: $isLiked")
        }.addOnFailureListener { e ->
            Log.e("Like", "❌ Error checking like status: ${e.message}")
        }
    }

    // ✅ GET LIKE COUNT (Optional)
    private fun getLikeCount() {
        if (itemId.isEmpty()) return

        val favoritesRef = FirebaseDatabase
            .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("favorites")

        // Count how many users liked this item
        favoritesRef.get().addOnSuccessListener { snapshot ->
            var count = 0
            for (userSnapshot in snapshot.children) {
                if (userSnapshot.hasChild(itemId)) {
                    count++
                }
            }
            likeCount = count
            Log.d("Like", "❤️ Like count: $likeCount")
            // You can display this count if you want
        }.addOnFailureListener { e ->
            Log.e("Like", "❌ Error getting like count: ${e.message}")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadItemImages(imageUrls: List<String?>) {
        val imageContainer = binding.imageContainer
        val indicatorContainer = binding.imageIndicator

        // Clear existing views
        imageContainer.removeAllViews()
        indicatorContainer.removeAllViews()

        val displayMetrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels

        // Limit to maximum 5 images
        val validImageUrls = imageUrls.take(10)

        // Load actual images with EXACT screen width
        for (i in validImageUrls.indices) {
            val imageUrl = validImageUrls[i]
            if (imageUrl.isNullOrEmpty()) continue

            val imageView = ImageView(requireContext())

            val params = LinearLayout.LayoutParams(
                screenWidth, // Exact screen width
                ViewGroup.LayoutParams.MATCH_PARENT
            )

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

        // Create indicators - SMALLER VERSION
        for (i in validImageUrls.indices) {
            val dot = ImageView(requireContext())
            val dotParams = LinearLayout.LayoutParams(6.dpToPx(), 6.dpToPx())
            dotParams.setMargins(3.dpToPx(), 0, 3.dpToPx(), 0)
            dot.layoutParams = dotParams

            val dotDrawable = if (i == 0) {
                ContextCompat.getDrawable(requireContext(), R.drawable.indicator_dot_active)
            } else {
                ContextCompat.getDrawable(requireContext(), R.drawable.indicator_dot_inactive)
            }

            dot.setImageDrawable(dotDrawable)
            indicatorContainer.addView(dot)
        }

        // Update image counter
        binding.tvImageCount.text = "1/${validImageUrls.size}"

        // ✅ SETUP PERFECT SNAP SCROLL
        setupPerfectSnapScroll(validImageUrls.size, screenWidth)
    }

    // ✅ PERFECT SNAP SCROLL FUNCTION
    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun setupPerfectSnapScroll(totalImages: Int, screenWidth: Int) {
        if (totalImages <= 1) return

        val scrollView = binding.horizontalScrollView
        val indicatorContainer = binding.imageIndicator

        scrollView.setOnScrollChangeListener { _: View, scrollX: Int, _: Int, _: Int, _: Int ->
            val position = (scrollX + screenWidth / 2) / screenWidth
            val currentIndex = position.coerceIn(0, totalImages - 1)

            // Update indicators
            for (i in 0 until indicatorContainer.childCount) {
                val dot = indicatorContainer.getChildAt(i) as ImageView
                val drawable = if (i == currentIndex) {
                    ContextCompat.getDrawable(requireContext(), R.drawable.indicator_dot_active)
                } else {
                    ContextCompat.getDrawable(requireContext(), R.drawable.indicator_dot_inactive)
                }
                dot.setImageDrawable(drawable)
            }

            // Update image counter
            binding.tvImageCount.text = "${currentIndex + 1}/$totalImages"
        }

        // ✅ PERFECT TOUCH HANDLING - AUTO SNAP TO IMAGES
        scrollView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val scrollX = scrollView.scrollX
                    val currentIndex = (scrollX + screenWidth / 2) / screenWidth
                    val targetScroll = currentIndex * screenWidth

                    // Smooth scroll to exact image position
                    scrollView.smoothScrollTo(targetScroll, 0)
                    return@setOnTouchListener true
                }
            }
            false
        }
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
                375.dpToPx() // ✅ FIXED
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
                val priceText = when (priceValue) {
                    is Long -> if (priceValue == 0L) "Barter Only" else "₱$priceValue"
                    is Double -> if (priceValue == 0.0) "Barter Only" else "₱${priceValue.toInt()}"
                    is String -> if (priceValue == "0" || priceValue == "0.0") "Barter Only" else "₱$priceValue"
                    else -> "Barter Only"
                }

                val category = snapshot.child("category").getValue(String::class.java) ?: ""
                val location = snapshot.child("location").getValue(String::class.java) ?: ""
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                val itemOwnerId = snapshot.child("ownerId").getValue(String::class.java) ?: ""


                val latitudeRaw = snapshot.child("latitude").value
                val longitudeRaw = snapshot.child("longitude").value
                Log.d("ItemDetailFragment", "📍 Latitude raw: $latitudeRaw (type: ${latitudeRaw?.javaClass?.simpleName})")
                Log.d("ItemDetailFragment", "📍 Longitude raw: $longitudeRaw (type: ${longitudeRaw?.javaClass?.simpleName})")

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

                val imageUrls = mutableListOf<String?>()
                val imagesValue = snapshot.child("imageUrls").getValue(String::class.java)
                if (!imagesValue.isNullOrEmpty()) {
                    imageUrls.addAll(imagesValue.split(","))
                } else {
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
                checkDescriptionLength(description)
                binding.itemPrice.text = priceText
                binding.itemCategory.text = category
                binding.itemLocation.text = location
                binding.postedDate.text = DateFormat.format("MMM dd, yyyy", timestamp)

                if (imageUrls.isNotEmpty()) {
                    loadItemImages(imageUrls)
                } else {
                    loadDefaultImage()
                }

                if (itemLatitude != 0.0 && itemLongitude != 0.0) {
                    Log.d("MapClick", "📍 Initializing map with coordinates: $itemLatitude, $itemLongitude")
                    initializeMapView()
                } else {
                    Log.d("MapClick", "📍 No coordinates available for map")
                    binding.mapViewItemDetail.visibility = View.GONE
                }

                loadOwnerInfo(ownerId)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Failed to load item: ${error.message}", Toast.LENGTH_SHORT).show()
                Log.e("ItemDetailFragment", "Failed to load item: ${error.message}")
            }
        })
    }

    private fun initializeMapView() {
        val mapView: MapView = binding.mapViewItemDetail

        mapView.getMapboxMap().loadStyleUri(Style.SATELLITE_STREETS) {
            val itemPoint = Point.fromLngLat(itemLongitude, itemLatitude)

            // Center camera using flyTo
            mapView.getMapboxMap().flyTo(
                cameraOptions = CameraOptions.Builder()
                    .center(itemPoint)
                    .zoom(14.0)
                    .build(),
                animationOptions = null
            )

            // Add marker
            addMarker(mapView, itemPoint)

            Log.d("MapClick", "📍 MapView initialized and ready for clicks")
        }
    }

    private fun loadDefaultImage() {
        val imageContainer = binding.imageContainer
        imageContainer.removeAllViews()

        val displayMetrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels

        val imageView = ImageView(requireContext())
        val params = LinearLayout.LayoutParams(
            screenWidth,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        imageView.layoutParams = params
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_light))

        Glide.with(this)
            .load(R.drawable.login_background)
            .into(imageView)

        imageContainer.addView(imageView)

        binding.imageIndicator.visibility = View.GONE
        binding.tvImageCount.visibility = View.GONE
    }

    private fun addMarker(mapView: MapView, point: Point) {
        try {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_map_marker)

            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(bitmap)

            pointAnnotationManager.create(pointAnnotationOptions)
            Log.d("MapDebug", "📍 Marker added at: ${point.latitude()}, ${point.longitude()}")

        } catch (e: Exception) {
            Log.e("MapDebug", "❌ Error adding marker: ${e.message}")

            // Fallback method: Add a simple circle annotation if marker fails
            addFallbackMarker(point)
        }
    }

    private fun setupMapClickListener() {
        val mapCardView = binding.root.findViewById<com.google.android.material.card.MaterialCardView>(R.id.mapCardView)

        mapCardView?.setOnClickListener {
            Log.d("MapClick", "📍 CardView clicked via direct binding!")
            openFullScreenMap()
        }

    }

    // Function para mag-open ng full screen map
    private fun openFullScreenMap() {
        Log.d("MapClick", "📍 openFullScreenMap called")
        if (itemLatitude == 0.0 || itemLongitude == 0.0) {
            Log.d("MapClick", "📍 No coordinates available")
            Toast.makeText(requireContext(), "Location not available", Toast.LENGTH_SHORT).show()
            return
        }

        // Check verification muna
        checkIfUserIsVerified { isVerified ->
            if (!isVerified) {
                Toast.makeText(requireContext(), "Verify your account to view full map", Toast.LENGTH_SHORT).show()
                redirectToProfileForVerification()
                return@checkIfUserIsVerified
            }

            // Show full screen map dialog
            showFullScreenMapDialog()
        }
    }

    // Function para mag-show ng full screen map
    private fun showFullScreenMapDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_fullscreen_map)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val mapView = dialog.findViewById<MapView>(R.id.mapViewFullScreen)
        val btnClose = dialog.findViewById<Button>(R.id.btnCloseMap)

        // Initialize the full screen map
        mapView.getMapboxMap().loadStyleUri(Style.SATELLITE_STREETS) {
            val itemPoint = Point.fromLngLat(itemLongitude, itemLatitude)

            mapView.getMapboxMap().setCamera(
                CameraOptions.Builder()
                    .center(itemPoint)
                    .zoom(15.0)
                    .build()
            )

            // Add marker
            addMarkerToFullScreenMap(mapView, itemPoint)
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // Function para mag-add ng marker sa full screen map
    private fun addMarkerToFullScreenMap(mapView: MapView, point: Point) {
        try {
            val pointAnnotationManager = mapView.annotations.createPointAnnotationManager()
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_map_marker)

            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(bitmap)

            pointAnnotationManager.create(pointAnnotationOptions)
        } catch (e: Exception) {
            Log.e("FullScreenMap", "Error adding marker: ${e.message}")
        }
    }

    // Fallback method using circle annotation
    private fun addFallbackMarker(point: Point) {
        try {
            val circleAnnotationManager = binding.mapViewItemDetail.annotations.createCircleAnnotationManager()
            val circleAnnotationOptions = com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions()
                .withPoint(point)
                .withCircleColor("#FF0000") // Red color
                .withCircleRadius(8.0)
                .withCircleStrokeWidth(2.0)
                .withCircleStrokeColor("#FFFFFF")

            circleAnnotationManager.create(circleAnnotationOptions)
            Log.d("MapDebug", "📍 Fallback circle marker added")
        } catch (e: Exception) {
            Log.e("MapDebug", "❌ Error adding fallback marker: ${e.message}")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadOwnerInfo(ownerId: String) {
        if (ownerId.isEmpty()) {
            binding.itemOwner.text = "Posted by: Unknown"
            Log.e("ItemDetailFragment", "❌ Empty ownerId")
            return
        }

        userDatabase.child(ownerId).addListenerForSingleValueEvent(object : ValueEventListener {
            @SuppressLint("SetTextI18n")
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

                // ✅ DAGDAG: Load rating data
                val ownerRating = snapshot.child("rating").getValue(Float::class.java) ?: 0f
                val reviewsCount = snapshot.child("reviewsCount").getValue(Int::class.java) ?: 0

                Log.d("ItemDetailFragment", "✅ Owner loaded: $ownerName - Rating: $ownerRating ($reviewsCount reviews)")

                binding.itemOwner.text = "Posted by: $ownerName"
                this@ItemDetailFragment.ownerName = ownerName

                // ✅ DAGDAG: Update rating display
                updateOwnerRatingUI(ownerRating, reviewsCount)

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
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ItemDetailFragment", "❌ Failed to load owner: ${error.message}")
                Toast.makeText(requireContext(), "Failed to load owner info", Toast.LENGTH_SHORT).show()
                binding.itemOwner.text = "Posted by: Unknown"
                binding.ownerImage.setImageResource(R.drawable.ic_profile)
            }
        })
    }

    private fun setupViewMoreButton() {
        binding.btnViewMore.setOnClickListener {
            toggleDescription()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun toggleDescription() {
        val description = binding.itemDescription
        val viewMoreBtn = binding.btnViewMore

        if (description.maxLines == 3) {
            // Expand
            description.maxLines = Integer.MAX_VALUE
            viewMoreBtn.text = "View Less"
        } else {
            // Collapse
            description.maxLines = 3
            viewMoreBtn.text = "View More"
        }
    }

    private fun checkDescriptionLength(description: String) {
        val textView = binding.itemDescription
        val viewMoreBtn = binding.btnViewMore

        // Set the text first
        textView.text = description

        // Use ViewTreeObserver to check if text is ellipsized
        textView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                textView.viewTreeObserver.removeOnPreDrawListener(this)

                // Check if text is being ellipsized (has ... at the end)
                val layout = textView.layout
                if (layout != null) {
                    val lines = layout.lineCount
                    val isEllipsized = lines > 0 && layout.getEllipsisCount(lines - 1) > 0

                    if (lines > 3 || isEllipsized) {
                        viewMoreBtn.visibility = View.VISIBLE
                        Log.d("ViewMore", "📝 Show View More - Lines: $lines, Ellipsized: $isEllipsized")
                    } else {
                        viewMoreBtn.visibility = View.GONE
                        Log.d("ViewMore", "📝 Hide View More - Lines: $lines")
                    }
                }
                return true
            }
        })
    }


    @SuppressLint("SetTextI18n")
    private fun updateOwnerRatingUI(rating: Float, reviewsCount: Int) {
        // Format rating text - show 1 decimal place
        val ratingText = String.format("%.1f", rating)

        // Show reviews count only if there are reviews
        val ratingDisplay = if (reviewsCount > 0) {
            "$ratingText ($reviewsCount reviews)"
        } else {
            "$ratingText (No reviews yet)"
        }
        binding.ownerRating.text = ratingDisplay

        // Change text color based on rating
        val ratingColor = when {
            rating >= 4.5 -> ContextCompat.getColor(requireContext(), R.color.success_green)    // Excellent - Green
            rating >= 4.0 -> ContextCompat.getColor(requireContext(), R.color.premium_gold)     // Very Good - Gold
            rating >= 3.0 -> ContextCompat.getColor(requireContext(), R.color.amber_200)        // Good - Orange
            rating >= 2.0 -> ContextCompat.getColor(requireContext(), R.color.orange_500)       // Fair - Dark Orange
            else -> ContextCompat.getColor(requireContext(), R.color.red_500)                   // Poor - Red
        }
        binding.ownerRating.setTextColor(ratingColor)

        // ✅ FIXED: Use the existing starIcon ImageView instead of adding compound drawable
        val starIcon = binding.root.findViewById<ImageView>(R.id.starIcon)

        // Update star color based on rating
        val starColor = when {
            rating >= 4.5 -> ContextCompat.getColor(requireContext(), R.color.success_green)    // 🌟🌟🌟🌟🌟 Green
            rating >= 4.0 -> ContextCompat.getColor(requireContext(), R.color.premium_gold)     // 🌟🌟🌟🌟⭐ Gold
            rating >= 3.0 -> ContextCompat.getColor(requireContext(), R.color.amber_200)        // 🌟🌟🌟⭐⭐ Orange
            rating >= 2.0 -> ContextCompat.getColor(requireContext(), R.color.orange_500)       // 🌟🌟⭐⭐⭐ Dark Orange
            else -> ContextCompat.getColor(requireContext(), R.color.red_500)                   // 🌟⭐⭐⭐⭐ Red
        }

        // Set the tint color for the star icon
        ImageViewCompat.setImageTintList(starIcon, ColorStateList.valueOf(starColor))

        // Make sure the star is always visible
        starIcon.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}