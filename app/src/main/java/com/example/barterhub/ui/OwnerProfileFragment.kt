package com.example.barterhub.ui

import android.os.Bundle
import android.view.View
import android.widget.*
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



class OwnerProfileFragment : Fragment(R.layout.fragment_owner_profile) {
    private lateinit var rvOwnerItems: RecyclerView
    private lateinit var tvItemsCount: TextView
    private lateinit var tvNoItems: TextView
    private lateinit var itemsAdapter: OwnerProfileItemsAdapter
    private lateinit var profileImage: CircleImageView
    private lateinit var userNameText: TextView
    private lateinit var ratingBar: RatingBar
    private lateinit var ratingText: TextView
    private lateinit var reviewsCountText: TextView
    private lateinit var memberSinceText: TextView
    private lateinit var nameTextView: TextView
    private lateinit var phoneTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var locationTextView: TextView
    private lateinit var bioTextView: TextView
    private lateinit var viewAllTextView: TextView
    private lateinit var badgesContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var mainContent: LinearLayout

    private lateinit var database: DatabaseReference
    private var ownerId: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views based on XML IDs
        profileImage = view.findViewById(R.id.profileImage)
        userNameText = view.findViewById(R.id.userNameText)
        ratingBar = view.findViewById(R.id.ratingBar)
        ratingText = view.findViewById(R.id.ratingText)
        reviewsCountText = view.findViewById(R.id.reviewsCountText)
        memberSinceText = view.findViewById(R.id.memberSinceText)
        nameTextView = view.findViewById(R.id.nameTextView)
        phoneTextView = view.findViewById(R.id.phoneTextView)
        emailTextView = view.findViewById(R.id.emailTextView)
        locationTextView = view.findViewById(R.id.locationTextView)
        bioTextView = view.findViewById(R.id.bioTextView)
        viewAllTextView = view.findViewById(R.id.viewAllTextView)
        badgesContainer = view.findViewById(R.id.badgesContainer)
        progressBar = view.findViewById(R.id.progressBar)
        mainContent = view.findViewById(R.id.mainContent)
        rvOwnerItems = view.findViewById(R.id.rvOwnerItems)
        tvItemsCount = view.findViewById(R.id.tvItemsCount)
        tvNoItems = view.findViewById(R.id.tvNoItems)

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

                    for (itemSnap in snapshot.children) {
                        val itemId = itemSnap.key ?: continue
                        val title = itemSnap.child("title").getValue(String::class.java) ?: "Untitled"

                        val priceAny = itemSnap.child("price").value
                        val priceText = when (priceAny) {
                            is Long -> if (priceAny == 0L) "Barter Only" else "₱$priceAny"
                            is Double -> if (priceAny == 0.0) "Barter Only" else "₱${priceAny.toInt()}"
                            is String -> if (priceAny == "0" || priceAny == "0.0") "Barter Only" else "₱$priceAny"
                            else -> "Barter Only"
                        }

                        // imageUrl fallback (imageUrls or imageUrl)
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
                    itemsAdapter.setItems(list)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (!isAdded) return
                    tvItemsCount.text = "0"
                    tvNoItems.visibility = View.VISIBLE
                }
            })
    }


    private fun loadOwnerInfo() {
        database.child("users").child(ownerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    val username = snapshot.child("username").value?.toString() ?: "Unknown User"
                    val phone = snapshot.child("phoneNumber").value?.toString() ?: "No phone"
                    val profileUrl = snapshot.child("profileImageUrl").value?.toString() ?: ""
                    val memberSince = snapshot.child("memberSince").value?.toString() ?: "Unknown"
                    val bio = snapshot.child("bio").value?.toString() ?: "No bio yet"

                    val raw = snapshot.child("memberSince").value
                    android.util.Log.d("OwnerProfile", "memberSince raw = $raw (${raw?.javaClass})")
                    memberSinceText.text = formatMemberSince(raw)

                    // Set UI values
                    userNameText.text = username
                    nameTextView.text = username
                    phoneTextView.text = phone
                    emailTextView.text = snapshot.child("email").value?.toString() ?: "Not Provided"
                    locationTextView.text = snapshot.child("address").value?.toString() ?: "Not Provided"
                    bioTextView.text = bio
                    memberSinceText.text = "Member since $memberSince"

                    // Load profile image
                    if (profileUrl.isNotEmpty()) {
                        Glide.with(requireContext())
                            .load(profileUrl)
                            .placeholder(R.drawable.ic_profile)
                            .into(profileImage)
                    }

                    // Optional: View All click for bio
                    viewAllTextView.setOnClickListener {
                        bioTextView.maxLines = Int.MAX_VALUE
                        viewAllTextView.visibility = View.GONE
                    }

                    showLoading(false)
                }

                override fun onCancelled(error: DatabaseError) {
                    showLoading(false)
                }
            })
    }

    private fun formatMemberSince(value: Any?): String {
        if (value == null) return "Member since Unknown"

        return when (value) {
            is Long -> {
                // timestamp millis -> "Member since Feb 2026"
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = value }
                val month = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(cal.time)
                val year = cal.get(java.util.Calendar.YEAR)
                "Member since $month $year"
            }

            is Double -> {
                // sometimes firebase stores number as Double
                formatMemberSince(value.toLong())
            }

            is String -> {
                val s = value.trim()
                if (s.isBlank()) return "Member since Unknown"

                // If already looks like "Jan 2023" or "February 2023"
                if (s.contains(" ")) return "Member since $s"

                // If looks like "2026-02"
                if (Regex("""\d{4}-\d{2}""").matches(s)) {
                    val parts = s.split("-")
                    val y = parts[0].toIntOrNull()
                    val m = parts[1].toIntOrNull()
                    if (y != null && m != null && m in 1..12) {
                        val cal = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.YEAR, y)
                            set(java.util.Calendar.MONTH, m - 1)
                        }
                        val month = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(cal.time)
                        return "Member since $month $y"
                    }
                }

                // fallback
                "Member since $s"
            }

            else -> "Member since Unknown"
        }
    }

    private fun loadOwnerStats() {
        database.child("users").child(ownerId)
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

    private fun loadOwnerBadges() {
        badgesContainer.removeAllViews()
        addEmptyBadge()
    }

    private fun addEmptyBadge() {
        val empty = TextView(requireContext()).apply {
            text = "No badges yet"
            textSize = 14f
            setTextColor(requireContext().getColor(R.color.gray))
            setPadding(16, 8, 16, 8)
        }
        badgesContainer.addView(empty)
    }
}
