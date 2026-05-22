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
    private lateinit var locationTextView: TextView

    private lateinit var badgesContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var mainContent: LinearLayout

    private lateinit var database: DatabaseReference
    private var ownerId: String = ""

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
        progressBar = view.findViewById(R.id.progressBar)
        mainContent = view.findViewById(R.id.mainContent)

        rvOwnerItems = view.findViewById(R.id.rvOwnerItems)
        tvItemsCount = view.findViewById(R.id.tvItemsCount)
        tvNoItems = view.findViewById(R.id.tvNoItems)

        // Adapter
        itemsAdapter = OwnerProfileItemsAdapter(mutableListOf()) { clicked ->
            val bundle = Bundle().apply {
                putString("itemId", clicked.itemId)
                putString("ownerId", clicked.ownerId)
            }
            findNavController().navigate(R.id.nav_item_detail, bundle)
        }

        rvOwnerItems.layoutManager = GridLayoutManager(requireContext(), 2)
        rvOwnerItems.adapter = itemsAdapter

        // Owner ID
        ownerId = arguments?.getString("ownerId") ?: ""

        if (ownerId.isEmpty()) {
            Toast.makeText(requireContext(), "Owner ID not found!", Toast.LENGTH_SHORT).show()
            return
        }

        // Badges
        val badgeManager = ProfileBadgeManager(this)
        badgeManager.loadUserBadgesForUserId(ownerId, badgesContainer)

        // Firebase
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
                    val profileUrl = snapshot.child("profileImageUrl").value?.toString() ?: ""
                    val raw = snapshot.child("memberSince").value

                    userNameText.text = username
                    memberSinceText.text = formatMemberSince(raw)

                    // SAFE LOCATION ONLY
                    val fullAddress = snapshot.child("address").value?.toString() ?: ""
                    val safeLocation = fullAddress.split(",").takeLast(2).joinToString(", ")
                    locationTextView.text = if (safeLocation.isNotBlank()) safeLocation else "Not Provided"

                    if (profileUrl.isNotEmpty()) {
                        Glide.with(requireContext())
                            .load(profileUrl)
                            .placeholder(R.drawable.ic_profile)
                            .into(profileImage)
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
}