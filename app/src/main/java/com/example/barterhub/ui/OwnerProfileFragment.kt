package com.example.barterhub.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.google.firebase.database.*
import de.hdodenhof.circleimageview.CircleImageView
import com.example.barterhub.ui.profile.ProfileBadgeManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.navigation.fragment.findNavController
import com.example.barterhub.adapters.OwnerItemUi
import com.example.barterhub.adapters.OwnerProfileItemsAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OwnerProfileFragment : Fragment(R.layout.fragment_owner_profile) {

    private lateinit var rvOwnerItems: RecyclerView
    private lateinit var tvItemsCount: TextView
    private lateinit var tvNoItems: TextView
    private lateinit var tvViewMoreItems: TextView
    private lateinit var itemsAdapter: OwnerProfileItemsAdapter
    private lateinit var profileImage: CircleImageView
    private lateinit var userNameText: TextView
    private lateinit var ratingBar: RatingBar
    private lateinit var ratingText: TextView
    private lateinit var reviewsCountText: TextView
    private lateinit var memberSinceText: TextView
    private lateinit var locationTextView: TextView
    private lateinit var badgesContainer: LinearLayout
    private lateinit var reviewsContainer: LinearLayout
    private lateinit var tvNoReviews: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var mainContent: LinearLayout
    private lateinit var database: DatabaseReference
    private var ownerId: String = ""
    private var allOwnerItems: List<OwnerItemUi> = emptyList()
    private var itemsExpanded = false
    private var itemLocationFallback = ""
    private var reviewsExpanded = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Views
        profileImage = view.findViewById(R.id.profileImage)
        userNameText = view.findViewById(R.id.userNameText)
        ratingBar = view.findViewById(R.id.ratingBar)
        ratingText = view.findViewById(R.id.ratingText)
        reviewsCountText = view.findViewById(R.id.reviewsCountText)
        memberSinceText = view.findViewById(R.id.memberSinceText)
        locationTextView = view.findViewById(R.id.locationTextView)
        badgesContainer = view.findViewById(R.id.badgesContainer)
        reviewsContainer = view.findViewById(R.id.reviewsContainer)
        tvNoReviews = view.findViewById(R.id.tvNoReviews)
        progressBar = view.findViewById(R.id.progressBar)
        mainContent = view.findViewById(R.id.mainContent)
        rvOwnerItems = view.findViewById(R.id.rvOwnerItems)
        tvItemsCount = view.findViewById(R.id.tvItemsCount)
        tvNoItems = view.findViewById(R.id.tvNoItems)
        tvViewMoreItems = view.findViewById(R.id.tvViewMoreItems)
        itemsAdapter = OwnerProfileItemsAdapter(mutableListOf()) { clicked ->
            val bundle = Bundle().apply {
                putString("itemId", clicked.itemId)
                putString("ownerId", clicked.ownerId)
            }
            findNavController().navigate(R.id.nav_item_detail, bundle)
        }

        rvOwnerItems.layoutManager = GridLayoutManager(requireContext(), 2)
        rvOwnerItems.adapter = itemsAdapter

        ownerId = arguments?.getString("ownerId") ?: ""

        if (ownerId.isEmpty()) {
            Toast.makeText(requireContext(), "Owner ID not found!", Toast.LENGTH_SHORT).show()
            return
        }

        val badgeManager = ProfileBadgeManager(this)
        badgeManager.loadUserBadgesForUserId(ownerId, badgesContainer)

        database = FirebaseDatabase
            .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .reference

        showLoading(true)
        loadOwnerInfo()
        loadOwnerStats()
        loadOwnerItems()
        loadOwnerReviews()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        mainContent.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun loadOwnerItems() {
        database.child("items")
            .orderByChild("ownerId")
            .equalTo(ownerId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return

                    val list = mutableListOf<OwnerItemUi>()
                    var firstItemLocation = ""

                    for (itemSnap in snapshot.children) {
                        val itemId = itemSnap.key ?: continue
                        val title = itemSnap.child("title").getValue(String::class.java) ?: "Untitled"

                        if (firstItemLocation.isBlank()) {
                            firstItemLocation = extractItemLocation(itemSnap)
                        }

                        val priceAny = itemSnap.child("price").value
                        val priceText = when (priceAny) {
                            is Long -> if (priceAny == 0L) "Barter Only" else "₱$priceAny"
                            is Double -> if (priceAny == 0.0) "Barter Only" else "₱${priceAny.toInt()}"
                            is String -> if (priceAny == "0" || priceAny == "0.0") "Barter Only" else "₱$priceAny"
                            else -> "Barter Only"
                        }

                        val imageUrlsCsv = itemSnap.child("imageUrls").getValue(String::class.java)
                        val firstFromCsv = imageUrlsCsv?.split(",")?.firstOrNull()?.trim()

                        val singleImageUrl = itemSnap.child("imageUrl").getValue(String::class.java)
                        val imageUrl = when {
                            !firstFromCsv.isNullOrBlank() -> firstFromCsv
                            !singleImageUrl.isNullOrBlank() -> singleImageUrl
                            else -> null
                        }

                        list.add(
                            OwnerItemUi(
                                itemId = itemId,
                                ownerId = ownerId,
                                title = title,
                                priceText = priceText,
                                imageUrl = imageUrl
                            )
                        )
                    }

                    tvItemsCount.text = list.size.toString()
                    tvNoItems.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    itemLocationFallback = firstItemLocation
                    applyItemLocationFallback()

                    allOwnerItems = list
                    itemsExpanded = false
                    renderOwnerItems()
                }

                override fun onCancelled(error: DatabaseError) {
                    if (!isAdded) return
                    tvItemsCount.text = "0"
                    tvNoItems.visibility = View.VISIBLE
                }
            })
    }

    private fun renderOwnerItems() {
        val visibleItems = if (itemsExpanded) allOwnerItems else allOwnerItems.take(2)

        itemsAdapter.setItems(visibleItems)

        tvViewMoreItems.visibility =
            if (allOwnerItems.size > 2) View.VISIBLE else View.GONE
        tvViewMoreItems.text = if (itemsExpanded) "Show less" else "View more"
        tvViewMoreItems.setOnClickListener {
            itemsExpanded = !itemsExpanded
            renderOwnerItems()
        }
    }

    private fun extractItemLocation(itemSnap: DataSnapshot): String {
        val addressText = itemSnap.child("addressText")
            .getValue(String::class.java)
            ?.takeIf { it.isNotBlank() }
        if (!addressText.isNullOrBlank()) return addressText

        val city = itemSnap.child("cityMunicipality")
            .getValue(String::class.java)
            .orEmpty()
        val province = itemSnap.child("province")
            .getValue(String::class.java)
            .orEmpty()
        val cityProvince = listOf(city, province)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        if (cityProvince.isNotBlank()) return cityProvince

        return itemSnap.child("location")
            .getValue(String::class.java)
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
    }

    private fun applyItemLocationFallback() {
        if (itemLocationFallback.isBlank()) return
        if (locationTextView.text.toString().equals("Not Provided", ignoreCase = true)) {
            locationTextView.text = itemLocationFallback
        }
    }

    private fun loadOwnerInfo() {
        database.child("public_users").child(ownerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    val displayName = snapshot.child("fullName").value?.toString()
                        ?.takeIf { it.isNotBlank() }
                        ?: snapshot.child("username").value?.toString()
                            ?.takeIf { it.isNotBlank() }
                        ?: "Unknown User"

                    val profileUrl = snapshot.child("profileImageUrl").value?.toString()
                        ?.takeIf { it.isNotBlank() }
                        ?: snapshot.child("profileImage").value?.toString()
                            ?.takeIf { it.isNotBlank() }
                        ?: ""

                    userNameText.text = displayName
                    memberSinceText.text = formatMemberSince(
                        snapshot.child("createdAt").value
                            ?: snapshot.child("memberSince").value
                    )

                    val city = snapshot.child("cityMunicipality").value?.toString()
                        ?: snapshot.child("city").value?.toString()
                        ?: ""
                    val province = snapshot.child("province").value?.toString() ?: ""
                    val publicLocation =
                        listOf(city, province).filter { it.isNotBlank() }.joinToString(", ")
                    locationTextView.text = publicLocation.ifBlank { "Not Provided" }
                    applyItemLocationFallback()

                    Glide.with(this@OwnerProfileFragment)
                        .load(profileUrl.ifBlank { R.drawable.ic_profile_placeholder })
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .into(profileImage)

                    loadOwnerPrivateFallback(profileUrl, publicLocation)

                    showLoading(false)
                }

                override fun onCancelled(error: DatabaseError) {
                    showLoading(false)
                }
            })
    }

    private fun loadOwnerPrivateFallback(publicProfileUrl: String, publicLocation: String) {
        database.child("users").child(ownerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return

                    if (publicProfileUrl.isBlank()) {
                        val fallbackProfileUrl =
                            snapshot.child("profileImageUrl").getValue(String::class.java)
                                ?.takeIf { it.isNotBlank() }
                                ?: snapshot.child("profileImage").getValue(String::class.java)
                                    ?.takeIf { it.isNotBlank() }
                                ?: ""

                        if (fallbackProfileUrl.isNotBlank()) {
                            Glide.with(this@OwnerProfileFragment)
                                .load(fallbackProfileUrl)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .error(R.drawable.ic_profile_placeholder)
                                .into(profileImage)
                        }
                    }

                    if (publicLocation.isBlank()) {
                        val fallbackLocation =
                            snapshot.child("addressText").getValue(String::class.java)
                                ?.takeIf { it.isNotBlank() }
                                ?: listOf(
                                    snapshot.child("cityMunicipality")
                                        .getValue(String::class.java)
                                        .orEmpty(),
                                    snapshot.child("province")
                                        .getValue(String::class.java)
                                        .orEmpty()
                                ).filter { it.isNotBlank() }.joinToString(", ")

                        locationTextView.text =
                            fallbackLocation.ifBlank { "Not Provided" }
                        applyItemLocationFallback()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    applyItemLocationFallback()
                }
            })
    }

    private fun formatMemberSince(value: Any?): String {
        if (value == null) return "Member since Unknown"

        return when (value) {
            is Long -> {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = value }
                val month = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(cal.time)
                val year = cal.get(java.util.Calendar.YEAR)
                "Member since $month $year"
            }

            is Double -> formatMemberSince(value.toLong())

            is String -> {
                val s = value.trim()
                if (s.isBlank()) return "Member since Unknown"
                if (s.contains(" ")) return "Member since $s"
                "Member since $s"
            }

            else -> "Member since Unknown"
        }
    }

    private fun loadOwnerStats() {
        database.child("public_users").child(ownerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val rating = snapshot.child("rating").getValue(Float::class.java) ?: 0f
                    val reviews = snapshot.child("reviewsCount").getValue(Int::class.java) ?: 0

                    ratingBar.rating = rating
                    ratingText.text = String.format("%.1f", rating)
                    reviewsCountText.text = "$reviews reviews"
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadOwnerReviews() {
        database.child("reviews")
            .orderByChild("reviewedUserId")
            .equalTo(ownerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return

                    val reviews = snapshot.children
                        .mapNotNull { reviewSnap ->
                            val rating = toFloatSafe(reviewSnap.child("rating").value)
                            if (rating <= 0f) return@mapNotNull null

                            OwnerReviewUi(
                                rating = rating,
                                comment = reviewSnap.child("comment")
                                    .getValue(String::class.java)
                                    ?.trim()
                                    .orEmpty(),
                                reviewerId = reviewSnap.child("reviewerId")
                                    .getValue(String::class.java)
                                    ?.trim()
                                    .orEmpty(),
                                reviewerName = reviewSnap.child("reviewerName")
                                    .getValue(String::class.java)
                                    ?.trim()
                                    .takeUnless { it.isNullOrBlank() }
                                    ?: "BarterHub user",
                                reviewerProfileImage = reviewSnap.child("reviewerProfileImage")
                                    .getValue(String::class.java)
                                    ?.trim()
                                    .takeUnless { it.isNullOrBlank() }
                                    ?: reviewSnap.child("reviewerProfileImageUrl")
                                        .getValue(String::class.java)
                                        ?.trim()
                                        .takeUnless { it.isNullOrBlank() }
                                    ?: reviewSnap.child("profileImage")
                                        .getValue(String::class.java)
                                        ?.trim()
                                        .takeUnless { it.isNullOrBlank() }
                                    ?: reviewSnap.child("profileImageUrl")
                                        .getValue(String::class.java)
                                        ?.trim()
                                        .takeUnless { it.isNullOrBlank() }
                                    .orEmpty(),
                                timestamp = reviewSnap.child("timestamp")
                                    .getValue(Long::class.java)
                                    ?: 0L
                            )
                        }
                        .sortedByDescending { it.timestamp }
                        .take(50)

                    reviewsExpanded = false
                    renderReviews(reviews)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (!isAdded) return
                    reviewsContainer.removeAllViews()
                    tvNoReviews.visibility = View.VISIBLE
                }
            })
    }

    private fun renderReviews(reviews: List<OwnerReviewUi>) {
        reviewsContainer.removeAllViews()
        tvNoReviews.visibility = if (reviews.isEmpty()) View.VISIBLE else View.GONE

        val visibleReviews = if (reviewsExpanded) reviews else reviews.take(5)

        visibleReviews.forEach { review ->
            reviewsContainer.addView(createReviewCard(review))
        }

        if (reviews.size > 5) {
            reviewsContainer.addView(createViewMoreReviewsButton(reviews))
        }
    }

    private fun createViewMoreReviewsButton(reviews: List<OwnerReviewUi>): View {
        val context = requireContext()
        return TextView(context).apply {
            text = if (reviewsExpanded) "Show less" else "View more"
            setTextColor(ContextCompat.getColor(context, R.color.bh_accent))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(Color.argb(45, 0, 188, 212))
                setStroke(dp(1), Color.argb(150, 0, 188, 212))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                topMargin = dp(2)
            }
            setOnClickListener {
                reviewsExpanded = !reviewsExpanded
                renderReviews(reviews)
            }
        }
    }

    private fun createReviewCard(review: OwnerReviewUi): View {
        val context = requireContext()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.argb(72, 18, 18, 24))
                setStroke(dp(1), Color.argb(110, 255, 255, 255))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }

        val avatar = CircleImageView(context).apply {
            setImageResource(R.drawable.ic_profile_placeholder)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                marginEnd = dp(10)
                topMargin = dp(2)
            }
        }

        loadReviewAvatar(avatar, review)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val ratingTextView = TextView(context).apply {
            text = buildReviewStars(review.rating)
            setTextColor(ContextCompat.getColor(context, R.color.premium_gold))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val dateTextView = TextView(context).apply {
            text = formatReviewDate(review.timestamp)
            setTextColor(ContextCompat.getColor(context, R.color.gray_400))
            textSize = 11f
        }

        header.addView(ratingTextView)
        header.addView(dateTextView)
        content.addView(header)

        if (review.comment.isNotBlank()) {
            val commentTextView = TextView(context).apply {
                text = "“${review.comment}”"
                setTextColor(ContextCompat.getColor(context, R.color.white))
                textSize = 13f
                setPadding(0, dp(8), 0, 0)
            }
            content.addView(commentTextView)
        }

        val reviewerTextView = TextView(context).apply {
            text = "— ${review.reviewerName}"
            setTextColor(ContextCompat.getColor(context, R.color.gray_300))
            textSize = 12f
            setPadding(0, dp(6), 0, 0)
        }
        content.addView(reviewerTextView)

        card.addView(avatar)
        card.addView(content)

        return card
    }

    private fun loadReviewAvatar(imageView: CircleImageView, review: OwnerReviewUi) {
        if (review.reviewerProfileImage.isNotBlank()) {
            Glide.with(this)
                .load(review.reviewerProfileImage)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .into(imageView)
            return
        }

        if (review.reviewerId.isBlank()) return

        database.child("public_users")
            .child(review.reviewerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return

                    val fallbackImageUrl =
                        snapshot.child("profileImageUrl").getValue(String::class.java)
                            ?: snapshot.child("profileImage").getValue(String::class.java)
                            ?: ""

                    if (fallbackImageUrl.isBlank()) {
                        loadReviewAvatarFromPrivateUser(imageView, review.reviewerId)
                        return
                    }

                    Glide.with(this@OwnerProfileFragment)
                        .load(fallbackImageUrl)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .into(imageView)
                }

                override fun onCancelled(error: DatabaseError) = Unit
            })
    }

    private fun loadReviewAvatarFromPrivateUser(imageView: CircleImageView, reviewerId: String) {
        database.child("users")
            .child(reviewerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return

                    val fallbackImageUrl =
                        snapshot.child("profileImageUrl").getValue(String::class.java)
                            ?: snapshot.child("profileImage").getValue(String::class.java)
                            ?: ""

                    if (fallbackImageUrl.isBlank()) return

                    Glide.with(this@OwnerProfileFragment)
                        .load(fallbackImageUrl)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .into(imageView)
                }

                override fun onCancelled(error: DatabaseError) = Unit
            })
    }

    private fun formatReviewDate(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            .format(Date(timestamp))
    }

    private fun buildReviewStars(rating: Float): String {
        val starCount = rating.toInt().coerceIn(1, 5)
        val emptyCount = 5 - starCount
        return "★".repeat(starCount) + "☆".repeat(emptyCount)
    }

    private fun toFloatSafe(value: Any?): Float {
        return when (value) {
            is Float -> value
            is Double -> value.toFloat()
            is Long -> value.toFloat()
            is Int -> value.toFloat()
            is String -> value.toFloatOrNull() ?: 0f
            else -> 0f
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class OwnerReviewUi(
        val rating: Float,
        val comment: String,
        val reviewerId: String,
        val reviewerName: String,
        val reviewerProfileImage: String,
        val timestamp: Long
    )
}
