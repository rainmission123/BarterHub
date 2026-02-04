package com.example.barterhub.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.google.firebase.database.*
import de.hdodenhof.circleimageview.CircleImageView

class OwnerProfileFragment : Fragment(R.layout.fragment_owner_profile) {

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

        ownerId = arguments?.getString("ownerId") ?: ""

        if (ownerId.isEmpty()) {
            Toast.makeText(requireContext(), "Owner ID not found!", Toast.LENGTH_SHORT).show()
            return
        }

        database = FirebaseDatabase
            .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .reference

        showLoading(true)
        loadOwnerInfo()
        loadOwnerStats()
        loadOwnerBadges()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        mainContent.visibility = if (show) View.GONE else View.VISIBLE
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
