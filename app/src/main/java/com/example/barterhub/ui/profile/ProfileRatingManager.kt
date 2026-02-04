package com.example.barterhub.ui.profile

import android.util.Log
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRatingBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.barterhub.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ProfileRatingManager(private val fragment: Fragment) {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    fun setupRatingSystem(
        ratingBar: AppCompatRatingBar,
        ratingText: TextView,
        reviewsCountText: TextView
    ) {
        ratingBar.rating = 0f
        ratingText.text = "0.0"
        reviewsCountText.text = "0 reviews"

        ratingBar.setOnClickListener {
            showRatingDetails(ratingBar.rating, reviewsCountText.text.toString())
        }
    }

    fun setupRealTimeRatingListeners(
        userId: String,
        ratingBar: AppCompatRatingBar,
        ratingText: TextView,
        reviewsCountText: TextView,
        memberSinceText: TextView
    ) {
        // Real-time rating from profile
        database.child("users").child(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!fragment.isAdded || fragment.context == null) return

                    val userRating = snapshot.child("rating").getValue(Float::class.java) ?: 0f
                    val reviewsCount = snapshot.child("reviewsCount").getValue(Int::class.java) ?: 0
                    val memberSince = snapshot.child("memberSince").getValue(String::class.java) ?: "2024"

                    updateRatingUI(userRating, reviewsCount, memberSince,
                        ratingBar, ratingText, reviewsCountText, memberSinceText)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileFragment", "Rating listener cancelled: ${error.message}")
                }
            })

        // Listen for new reviews
        database.child("reviews")
            .orderByChild("reviewedUserId")
            .equalTo(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!fragment.isAdded) return
                    Log.d("ProfileFragment", "New review detected for user $userId")
                    recalculateUserRating(userId)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileFragment", "Reviews listener cancelled: ${error.message}")
                }
            })
    }

    private fun recalculateUserRating(userId: String) {
        database.child("reviews")
            .orderByChild("reviewedUserId")
            .equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var totalRating = 0f
                    var reviewCount = 0

                    for (reviewSnapshot in snapshot.children) {
                        val rating = reviewSnapshot.child("rating").getValue(Float::class.java) ?: 0f
                        totalRating += rating
                        reviewCount++
                    }

                    val averageRating = if (reviewCount > 0) totalRating / reviewCount else 0f
                    val updates = mapOf(
                        "rating" to averageRating,
                        "reviewsCount" to reviewCount
                    )

                    database.child("users").child(userId).updateChildren(updates)
                        .addOnSuccessListener {
                            Log.d("ProfileFragment", "✅ Recalculated rating: $averageRating ($reviewCount reviews)")
                        }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileFragment", "Error recalculating rating: ${error.message}")
                }
            })
    }

    private fun updateRatingUI(
        rating: Float,
        reviewsCount: Int,
        memberSince: String,
        ratingBar: AppCompatRatingBar,
        ratingText: TextView,
        reviewsCountText: TextView,
        memberSinceText: TextView
    ) {
        if (!fragment.isAdded || fragment.context == null) return

        ratingBar.rating = rating
        ratingText.text = String.format("%.1f", rating)
        reviewsCountText.text = "$reviewsCount reviews"
        memberSinceText.text = formatMemberSince(memberSince)

        // Rating color
        val ratingColor = when {
            rating >= 4.5 -> ContextCompat.getColor(fragment.requireContext(), R.color.success_green)
            rating >= 3.5 -> ContextCompat.getColor(fragment.requireContext(), R.color.premium_gold)
            rating >= 2.5 -> ContextCompat.getColor(fragment.requireContext(), R.color.amber_200)
            else -> ContextCompat.getColor(fragment.requireContext(), R.color.red_500)
        }
        ratingText.setTextColor(ratingColor)
    }

    private fun formatMemberSince(yearMonth: String): String {
        try {
            val parts = yearMonth.split("-")
            if (parts.size == 2) {
                val year = parts[0].toInt()
                val month = parts[1].toInt()
                val monthNames = arrayOf(
                    "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"
                )
                val monthName = if (month in 1..12) monthNames[month - 1] else "Month $month"
                return "Member since $monthName $year"
            }
        } catch (e: Exception) {
            Log.e("ProfileDebug", "Error formatting date: $yearMonth", e)
        }
        return "Member since 2025"
    }

    private fun showRatingDetails(rating: Float, reviewsText: String) {
        androidx.appcompat.app.AlertDialog.Builder(fragment.requireContext())
            .setTitle("Rating Details")
            .setMessage("Average Rating: $rating\n$reviewsText")
            .setPositiveButton("OK", null)
            .show()
    }
}