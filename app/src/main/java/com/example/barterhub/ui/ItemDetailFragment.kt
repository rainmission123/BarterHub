package com.example.barterhub.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.barterhub.R
import com.example.barterhub.data.repository.ChatRepository
import com.example.barterhub.databinding.FragmentItemDetailBinding
import com.example.barterhub.utils.ImageGalleryManager
import com.example.barterhub.utils.MapManager
import com.example.barterhub.utils.RatingHelper
import com.example.barterhub.utils.TextHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch

class ItemDetailFragment : Fragment() {
    private var _binding: FragmentItemDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var itemId: String
    private lateinit var ownerId: String
    private lateinit var currentUserId: String
    private lateinit var itemTitle: String
    private lateinit var ownerName: String

    private lateinit var itemDatabase: com.google.firebase.database.DatabaseReference
    private lateinit var userDatabase: com.google.firebase.database.DatabaseReference
    private lateinit var auth: FirebaseAuth

    private var itemLatitude: Double = 0.0
    private var itemLongitude: Double = 0.0
    private var isLiked = false
    private var likeWriteInProgress = false

    private fun isUiActive(): Boolean {
        return isAdded && _binding != null && context != null
    }

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

        // Get arguments
        itemId = arguments?.getString("itemId") ?: ""
        ownerId = arguments?.getString("ownerId") ?: ""

        ownerName = "Seller"
        itemTitle = "Item"

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""

        itemDatabase = FirebaseDatabase
            .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("items")
        userDatabase = FirebaseDatabase
            .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("public_users")

        setupClickListeners()
        loadItemDetails()
        checkIfLiked()
    }

    private fun setupClickListeners() {
        // Owner click
        binding.ownerInfoSection.setOnClickListener {
            navigateToOwnerProfile()
        }

        // Chat button
        binding.btnChat.setOnClickListener {
            handleChatClick()
        }

        // Like button
        binding.btnLike.setOnClickListener {
            handleLikeClick()
        }

        // Request barter button
        binding.btnRequestBarter.setOnClickListener {
            handleBarterRequest()
        }

        // Map click
        binding.mapCardView.setOnClickListener {
            handleMapClick()
        }

        // View more description
        binding.btnViewMore.setOnClickListener {
            toggleDescription()
        }
    }

    private fun handleChatClick() {
        if (currentUserId.isEmpty()) {
            Toast.makeText(requireContext(), "Please login to start chatting", Toast.LENGTH_SHORT).show()
            return
        }

        if (ownerId == currentUserId) {
            Toast.makeText(requireContext(), "You cannot chat with yourself", Toast.LENGTH_SHORT).show()
            return
        }

        navigateToChat()
    }

    private fun handleLikeClick() {
        if (currentUserId.isEmpty()) {
            Toast.makeText(requireContext(), "Please login to like items", Toast.LENGTH_SHORT).show()
            return
        }

        toggleLike()
    }

    private fun handleBarterRequest() {
        if (currentUserId.isEmpty()) {
            Toast.makeText(requireContext(), "Please login to send requests", Toast.LENGTH_SHORT).show()
            return
        }

        if (ownerId == currentUserId) {
            Toast.makeText(requireContext(), "You cannot send a Barter request to your own item", Toast.LENGTH_SHORT).show()
            return
        }

        showOfferDialog()
    }

    private fun handleMapClick() {
        if (itemLatitude == 0.0 || itemLongitude == 0.0) {
            Toast.makeText(requireContext(), "Location not available", Toast.LENGTH_SHORT).show()
            return
        }

        checkIfUserIsVerified { isVerified ->
            if (!isVerified) {
                Toast.makeText(requireContext(), "Verify your account to view full map", Toast.LENGTH_SHORT).show()
                redirectToProfileForVerification()
            } else {
                MapManager.openFullScreenMap(this, itemLatitude, itemLongitude)
            }
        }
    }

    private fun loadItemDetails() {
        if (itemId.isEmpty()) {
            Toast.makeText(requireContext(), "Item not found", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        itemDatabase.child(itemId).addListenerForSingleValueEvent(
            object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (!isAdded || _binding == null || context == null) return

                    if (!snapshot.exists()) {
                        Toast.makeText(requireContext(), "Item not found", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                        return
                    }

                    // ----------------------------
                    // 1) Read basic fields
                    // ----------------------------
                    val title = snapshot.child("title").getValue(String::class.java)?.trim().orEmpty().ifEmpty { "No title" }
                    val description = snapshot.child("description").getValue(String::class.java)?.trim().orEmpty()
                    val category = snapshot.child("category").getValue(String::class.java)?.trim().orEmpty()
                    val location = snapshot.child("location").getValue(String::class.java)?.trim().orEmpty()
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                    // price can be Long/Double/String -> use your helper
                    val priceValue = snapshot.child("price").getValue(Any::class.java)
                    val priceText = formatPrice(priceValue)

                    // ownerId (required)
                    val itemOwnerId = snapshot.child("ownerId").getValue(String::class.java)?.trim().orEmpty()

                    // ----------------------------
                    // 2) Parse coordinates safely
                    // ----------------------------
                    fun toDoubleSafe(v: Any?): Double {
                        return when (v) {
                            is Double -> v
                            is Long -> v.toDouble()
                            is Int -> v.toDouble()
                            is String -> v.toDoubleOrNull() ?: 0.0
                            else -> 0.0
                        }
                    }

                    itemLatitude = toDoubleSafe(snapshot.child("latitude").getValue(Any::class.java))
                    itemLongitude = toDoubleSafe(snapshot.child("longitude").getValue(Any::class.java))

                    val imageUrls = mutableListOf<String>()

                    val imagesNode = snapshot.child("imageUrls")

                    if (imagesNode.exists()) {
                        when (val raw = imagesNode.value) {

                            is List<*> -> {
                                raw.forEach { v ->
                                    val url = v?.toString()?.trim()
                                    if (!url.isNullOrBlank()) imageUrls.add(url)
                                }
                            }

                            is String -> {
                                if (raw.isNotBlank()) {
                                    imageUrls.addAll(
                                        raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                    )
                                }
                            }
                        }
                    }

// fallback single imageUrl
                    if (imageUrls.isEmpty()) {
                        val single = snapshot.child("imageUrl").getValue(String::class.java)
                        if (!single.isNullOrBlank()) imageUrls.add(single.trim())
                    }


                    // ----------------------------
                    // 4) Save IDs for other actions
                    // ----------------------------
                    ownerId = itemOwnerId
                    itemTitle = title

                    // ----------------------------
                    // 5) Update UI
                    // ----------------------------
                    binding.itemTitle.text = title
                    binding.itemDescription.text = description
                    TextHelper.checkDescriptionLength(binding.itemDescription, binding.btnViewMore, description)

                    binding.itemPrice.text = priceText
                    binding.itemCategory.text = if (category.isNotEmpty()) category else "Uncategorized"
                    binding.itemLocation.text = if (location.isNotEmpty()) location else "Location not set"
                    binding.postedDate.text = if (timestamp > 0L) formatDate(timestamp) else "—"

                    // ----------------------------
                    // 6) Images
                    // ----------------------------
                    if (imageUrls.isNotEmpty()) {
                        ImageGalleryManager.loadImages(requireContext(), binding, imageUrls) { urls, startIndex ->
                            val bundle = Bundle().apply {
                                putStringArray("urls", urls.toTypedArray())
                                putInt("index", startIndex)
                            }
                            findNavController().navigate(R.id.fullscreenImageViewerFragment, bundle)
                        }
                        // Optional: count
                        binding.tvImageCount.text = "${imageUrls.size}"
                    } else {
                        ImageGalleryManager.loadDefaultImage(requireContext(), binding)
                        binding.tvImageCount.text = "0"
                    }

                    // ----------------------------
                    // 7) Owner info (this is where verified badge will be set inside loadOwnerInfo)
                    // ----------------------------
                    if (ownerId.isNotBlank()) {
                        loadOwnerInfo(ownerId)
                    } else {
                        binding.itemOwner.text = "Unknown"
                        binding.ownerRating.text = ""
                        binding.chipVerified.visibility = View.GONE
                    }

                    // ----------------------------
                    // 8) Map preview
                    // ----------------------------
                    if (itemLatitude != 0.0 && itemLongitude != 0.0) {
                        binding.mapCardView.visibility = View.VISIBLE
                        ImageGalleryManager.loadMapPreview(
                            requireContext(),
                            binding.mapPreviewImage,
                            itemLatitude,
                            itemLongitude
                        )
                    } else {
                        binding.mapCardView.visibility = View.GONE
                    }
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    if (!isAdded || _binding == null || context == null) return
                    Toast.makeText(requireContext(), "Failed to load item: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun loadOwnerInfo(ownerId: String) {
        if (ownerId.isBlank()) {
            binding.itemOwner.text = "Unknown"
            binding.ownerRating.text = ""
            binding.chipVerified.apply {
                visibility = View.VISIBLE
                text = "Not Verified"
                setChipIconResource(R.drawable.ic_not_verified)
                setChipBackgroundColorResource(R.color.gray_600)
            }
            return
        }

        userDatabase.child(ownerId).addListenerForSingleValueEvent(
            object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (!isAdded || _binding == null || context == null) return

                    if (!snapshot.exists()) {
                        binding.itemOwner.text = "Unknown"
                        binding.ownerRating.text = ""
                        binding.chipVerified.apply {
                            visibility = View.VISIBLE
                            text = "Not Verified"
                            setChipIconResource(R.drawable.ic_not_verified)
                            setChipBackgroundColorResource(R.color.gray_600)
                        }
                        return
                    }

                    val name = snapshot.child("fullName").getValue(String::class.java)
                        ?: snapshot.child("username").getValue(String::class.java)
                        ?: "Unknown"

                    val profileImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java).orEmpty()

                    // ✅ rating safe (Float/Double/Long/String)
                    fun toFloatSafe(v: Any?): Float {
                        return when (v) {
                            is Float -> v
                            is Double -> v.toFloat()
                            is Long -> v.toFloat()
                            is Int -> v.toFloat()
                            is String -> v.toFloatOrNull() ?: 0f
                            else -> 0f
                        }
                    }

                    val rating = toFloatSafe(snapshot.child("rating").getValue(Any::class.java))
                    val reviewsCount = (snapshot.child("reviewsCount").getValue(Long::class.java) ?: 0L).toInt()

                    // ✅ verification status
                    val verificationStatus = snapshot.child("isIDVerified").getValue(String::class.java).orEmpty()
                    val isVerified = verificationStatus == "verified"

                    // ✅ chip always visible (Verified / Not Verified)
                    binding.chipVerified.apply {
                        visibility = View.VISIBLE
                        if (isVerified) {
                            text = "Verified"
                            setChipIconResource(R.drawable.ic_verified)
                            setChipBackgroundColorResource(R.color.green_dark)
                        } else {
                            text = "Not Verified"
                            setChipIconResource(R.drawable.ic_not_verified)
                            setChipBackgroundColorResource(R.color.gray_600)
                        }
                    }

                    ownerName = name
                    binding.itemOwner.text = name

                    ImageGalleryManager.loadOwnerImage(requireContext(), binding.ownerImage, profileImageUrl)
                    RatingHelper.updateOwnerRatingUI(binding.ownerRating, binding.starIcon, rating, reviewsCount)
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    if (!isAdded || _binding == null || context == null) return

                    binding.itemOwner.text = "Unknown"
                    binding.ownerRating.text = ""

                    binding.chipVerified.apply {
                        visibility = View.VISIBLE
                        text = "Not Verified"
                        setChipIconResource(R.drawable.ic_not_verified)
                        setChipBackgroundColorResource(R.color.gray_600)
                    }

                    binding.ownerImage.setImageResource(R.drawable.ic_profile)
                }
            }
        )
    }

    private fun checkIfLiked() {
        if (currentUserId.isEmpty() || itemId.isEmpty()) return

        val likeRef = FirebaseDatabase
            .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("itemLikes")
            .child(itemId)
            .child(currentUserId)

        likeRef.get().addOnSuccessListener { snapshot ->
            if (!isAdded || _binding == null || context == null) return@addOnSuccessListener
            isLiked = snapshot.exists()
            updateLikeButton(isLiked)
        }.addOnFailureListener {
            if (!isAdded || _binding == null || context == null) return@addOnFailureListener
        }
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
        if (likeWriteInProgress) return

        val db = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
        val likeRef = db.getReference("itemLikes").child(itemId).child(currentUserId)
        val favoritesRef = db.getReference("favorites").child(currentUserId).child(itemId)
        val previousLiked = isLiked

        fun finishLikeWrite() {
            likeWriteInProgress = false
            if (isUiActive()) {
                binding.btnLike.isEnabled = true
            }
        }

        fun restorePreviousState(message: String) {
            if (!isUiActive()) {
                finishLikeWrite()
                return
            }

            isLiked = previousLiked
            updateLikeButton(previousLiked)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            finishLikeWrite()
        }

        likeWriteInProgress = true
        binding.btnLike.isEnabled = false

        likeRef.get().addOnSuccessListener { snap ->
            if (!isUiActive()) {
                finishLikeWrite()
                return@addOnSuccessListener
            }

            val alreadyLiked = snap.exists()

            if (alreadyLiked) {
                likeRef.removeValue()
                    .addOnSuccessListener {
                        if (!isUiActive()) {
                            finishLikeWrite()
                            return@addOnSuccessListener
                        }

                        favoritesRef.removeValue()
                        isLiked = false
                        updateLikeButton(false)
                        Toast.makeText(requireContext(), "Item unliked", Toast.LENGTH_SHORT).show()
                        finishLikeWrite()
                    }
                    .addOnFailureListener {
                        restorePreviousState("Failed to unlike")
                    }
            } else {
                likeRef.setValue(true)
                    .addOnSuccessListener {
                        if (!isUiActive()) {
                            finishLikeWrite()
                            return@addOnSuccessListener
                        }

                        favoritesRef.setValue(true)
                        isLiked = true
                        updateLikeButton(true)
                        Toast.makeText(requireContext(), "Item liked!", Toast.LENGTH_SHORT).show()
                        finishLikeWrite()
                    }
                    .addOnFailureListener {
                        restorePreviousState("Failed to like")
                    }
            }
        }.addOnFailureListener {
            restorePreviousState(if (previousLiked) "Failed to unlike" else "Failed to like")
        }
    }

    private fun updateLikeButton(isLiked: Boolean) {
        val ctx = context ?: return
        val binding = _binding ?: return
        if (isLiked) {
            binding.btnLike.apply {
                icon = ContextCompat.getDrawable(ctx, R.drawable.ic_like)
                setIconTintResource(R.color.red_500)
                strokeColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.red_500))
                setTextColor(ContextCompat.getColor(ctx, R.color.red_500))
            }
        } else {
            binding.btnLike.apply {
                icon = ContextCompat.getDrawable(ctx, R.drawable.ic_like_border)
                setIconTintResource(R.color.teal_700)
                strokeColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.teal_700))
                setTextColor(ContextCompat.getColor(ctx, R.color.teal_700))
            }
        }
    }

    private fun navigateToOwnerProfile() {
        if (ownerId.isEmpty()) {
            Toast.makeText(requireContext(), "Owner information not available", Toast.LENGTH_SHORT).show()
            return
        }

        val bundle = Bundle().apply {
            putString("ownerId", ownerId)
        }
        findNavController().navigate(R.id.action_itemDetailFragment_to_ownerProfileFragment, bundle)
    }

    private fun navigateToChat() {
        val ctx = context ?: return
        binding.btnChat.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val database = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            val chatRepository = ChatRepository(database)
            val chatId = chatRepository.getOrCreateDirectChat(
                currentUserId = currentUserId,
                partnerId = ownerId,
                itemId = itemId,
                itemTitle = itemTitle,
                partnerName = ownerName
            )

            if (!isUiActive()) return@launch
            binding.btnChat.isEnabled = true

            if (chatId.isBlank()) {
                Toast.makeText(ctx, "Unable to open chat. Please try again.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val bundle = Bundle().apply {
                putString("chatId", chatId)
                putString("partnerId", ownerId)
                putString("partnerName", ownerName)
                putString("itemId", itemId)
                putString("itemTitle", itemTitle)
            }
            findNavController().navigate(R.id.action_itemDetailFragment_to_chatFragment, bundle)
        }
    }

    private fun showOfferDialog() {
        val bundle = Bundle().apply {
            putString("itemId", itemId)
            putString("ownerId", ownerId)
            putString("itemTitle", itemTitle)
        }
        findNavController().navigate(R.id.action_itemDetailFragment_to_offerFragment, bundle)
    }

    private fun redirectToProfileForVerification() {
        try {
            findNavController().navigate(R.id.nav_profile)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Please complete ID verification in your profile", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkIfUserIsVerified(onComplete: (Boolean) -> Unit) {
        if (currentUserId.isEmpty()) {
            onComplete(false)
            return
        }

        FirebaseDatabase
            .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("users")
            .child(currentUserId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded || _binding == null || context == null) return@addOnSuccessListener
                if (snapshot.exists()) {
                    val isIDVerified = snapshot.child("isIDVerified").value
                    onComplete(isVerifiedStatus(isIDVerified))
                } else {
                    onComplete(false)
                }
            }.addOnFailureListener {
                if (!isAdded || _binding == null || context == null) return@addOnFailureListener
                onComplete(false)
            }
    }

    private fun isVerifiedStatus(value: Any?): Boolean {
        return when (value) {
            is String -> value.equals("verified", ignoreCase = true)
            is Boolean -> value
            is Int -> value == 1
            is Long -> value == 1L
            is Double -> value == 1.0
            else -> false
        }
    }

    private fun toggleDescription() {
        val description = binding.itemDescription
        val viewMoreBtn = binding.btnViewMore

        if (description.maxLines == 3) {
            description.maxLines = Integer.MAX_VALUE
            viewMoreBtn.text = "View Less"
        } else {
            description.maxLines = 3
            viewMoreBtn.text = "View More"
        }
    }

    private fun formatPrice(price: Any?): String {
        return when (price) {
            is Long -> if (price == 0L) "Barter Only" else "₱$price"
            is Double -> if (price == 0.0) "Barter Only" else "₱${price.toInt()}"
            is String -> if (price == "0" || price == "0.0") "Barter Only" else "₱$price"
            else -> "Barter Only"
        }
    }

    private fun formatDate(timestamp: Long): String {
        return android.text.format.DateFormat.format("MMM dd, yyyy", timestamp).toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
