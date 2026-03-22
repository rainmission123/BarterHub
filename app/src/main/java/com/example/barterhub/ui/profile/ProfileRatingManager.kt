package com.example.barterhub.ui.profile

import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatRatingBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.barterhub.R
import com.google.firebase.database.*

class ProfileRatingManager(private val fragment: Fragment) {

    companion object {
        private const val TAG = "ProfileRatingManager"
        private const val PRIOR_RATING = 4.0
        private const val PRIOR_COUNT = 5
    }

    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference

    private var userListener: ValueEventListener? = null
    private var reviewsListener: ValueEventListener? = null

    /**
     * Call in Fragment.onDestroyView() to avoid leaks:
     * ratingManager.clear(userId)
     */
    fun clear(userId: String? = null) {
        try {
            userListener?.let { listener ->
                if (userId != null) {
                    database.child("users").child(userId).removeEventListener(listener)
                }
            }
            reviewsListener?.let { listener ->
                if (userId != null) {
                    database.child("reviews")
                        .orderByChild("reviewedUserId")
                        .equalTo(userId)
                        .removeEventListener(listener)
                }
            }
        } catch (_: Exception) {
        } finally {
            userListener = null
            reviewsListener = null
        }
    }

    fun setupRatingSystem(
        ratingBar: AppCompatRatingBar,
        ratingText: TextView,
        reviewsCountText: TextView
    ) {
        ratingBar.rating = 0f
        ratingText.text = "0.0"
        reviewsCountText.text = "No reviews yet"

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
        clear(userId)

        // 1) Real-time user profile listener
        userListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!fragment.isAdded) return

                val userRating = snapshot.child("rating").value.toFloatSafe(default = 0f)
                val reviewsCount = snapshot.child("reviewsCount").value.toIntSafe(default = 0)
                val memberSince = snapshot.child("memberSince").getValue(String::class.java) ?: "2025-01"

                updateRatingUI(
                    rating = userRating,
                    reviewsCount = reviewsCount,
                    memberSince = memberSince,
                    ratingBar = ratingBar,
                    ratingText = ratingText,
                    reviewsCountText = reviewsCountText,
                    memberSinceText = memberSinceText
                )
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "User rating listener cancelled: ${error.message}")
            }
        }

        database.child("users").child(userId)
            .addValueEventListener(userListener as ValueEventListener)

        // 2) Reviews listener -> recalculates rating when reviews change
        reviewsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!fragment.isAdded) return
                Log.d(TAG, "Reviews changed for user $userId. Recalculating...")
                recalculateUserRating(userId, snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Reviews listener cancelled: ${error.message}")
            }
        }

        database.child("reviews")
            .orderByChild("reviewedUserId")
            .equalTo(userId)
            .addValueEventListener(reviewsListener as ValueEventListener)
    }

    /**
     * ✅ Production rating computation
     * - Skips rating == 0 (rating skipped)
     * - Uses Bayesian smoothing to avoid instant 5.0 on first rating
     */
    private fun recalculateUserRating(userId: String, reviewsSnapshot: DataSnapshot) {

        var sum = 0.0
        var count = 0

        for (reviewSnapshot in reviewsSnapshot.children) {
            val rating = reviewSnapshot.child("rating").value.toDoubleSafe(default = 0.0)

            // ✅ skip "Rating skipped" or invalid ratings
            if (rating <= 0.0) continue
            if (rating > 5.0) continue

            sum += rating
            count++
        }

        val finalRating = if (count > 0) {
            ((PRIOR_RATING * PRIOR_COUNT) + sum) / (PRIOR_COUNT + count)
        } else {
            0.0
        }

        val updates: Map<String, Any> = mapOf(
            "rating" to finalRating,
            "reviewsCount" to count
        )

        database.child("users").child(userId).updateChildren(updates)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Rating updated for $userId: $finalRating ($count reviews)")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to update rating: ${e.message}")
            }
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
        if (!fragment.isAdded) return

        val safeRating = rating.coerceIn(0f, 5f)

        ratingBar.rating = safeRating
        ratingText.text = String.format("%.1f", safeRating)

        reviewsCountText.text = if (reviewsCount > 0) {
            "$reviewsCount review${if (reviewsCount == 1) "" else "s"}"
        } else {
            "No reviews yet"
        }

        memberSinceText.text = formatMemberSince(memberSince)

        val ctx = fragment.requireContext()
        val ratingColor = when {
            reviewsCount == 0 -> ContextCompat.getColor(ctx, R.color.gray_500)
            safeRating >= 4.5f -> ContextCompat.getColor(ctx, R.color.success_green)
            safeRating >= 3.5f -> ContextCompat.getColor(ctx, R.color.premium_gold)
            safeRating >= 2.5f -> ContextCompat.getColor(ctx, R.color.amber_200)
            else -> ContextCompat.getColor(ctx, R.color.red_500)
        }
        ratingText.setTextColor(ratingColor)
    }

    private fun formatMemberSince(value: String): String {
        return try {
            val parts = value.trim().split("-")
            val year = parts.getOrNull(0)?.toIntOrNull() ?: 2025
            val month = parts.getOrNull(1)?.toIntOrNull()

            val monthName = if (month != null && month in 1..12) {
                arrayOf(
                    "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"
                )[month - 1]
            } else null

            if (monthName != null) "Member since $monthName $year" else "Member since $year"
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting memberSince: $value", e)
            "Member since 2025"
        }
    }

    private fun showRatingDetails(rating: Float, reviewsText: String) {
        if (!fragment.isAdded) return

        AlertDialog.Builder(fragment.requireContext())
            .setTitle("Rating Details")
            .setMessage("Average Rating: ${String.format("%.1f", rating)}\n$reviewsText")
            .setPositiveButton("OK", null)
            .show()
    }

    // -------------------------
    // ✅ SAFE CONVERTERS (NO Number::class.java)
    // -------------------------
    private fun Any?.toIntSafe(default: Int = 0): Int {
        return when (this) {
            is Int -> this
            is Long -> this.toInt()
            is Double -> this.toInt()
            is Float -> this.toInt()
            is String -> this.toIntOrNull() ?: default
            else -> default
        }
    }

    private fun Any?.toFloatSafe(default: Float = 0f): Float {
        return when (this) {
            is Float -> this
            is Double -> this.toFloat()
            is Long -> this.toFloat()
            is Int -> this.toFloat()
            is String -> this.toFloatOrNull() ?: default
            else -> default
        }
    }

    private fun Any?.toDoubleSafe(default: Double = 0.0): Double {
        return when (this) {
            is Double -> this
            is Float -> this.toDouble()
            is Long -> this.toDouble()
            is Int -> this.toDouble()
            is String -> this.toDoubleOrNull() ?: default
            else -> default
        }
    }
}