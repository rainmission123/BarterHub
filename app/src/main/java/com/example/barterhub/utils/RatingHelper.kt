package com.example.barterhub.utils

import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.example.barterhub.R

object RatingHelper {

    fun updateOwnerRatingUI(ratingText: TextView, starIcon: ImageView, rating: Float, reviewsCount: Int) {
        val context = ratingText.context

        // Format rating text
        val ratingFormatted = String.format("%.1f", rating)
        val ratingDisplay = if (reviewsCount > 0) {
            "$ratingFormatted ($reviewsCount reviews)"
        } else {
            "$ratingFormatted (No reviews yet)"
        }
        ratingText.text = ratingDisplay

        // Set rating color based on rating value
        val ratingColor = when {
            rating >= 4.5 -> ContextCompat.getColor(context, R.color.success_green)
            rating >= 4.0 -> ContextCompat.getColor(context, R.color.premium_gold)
            rating >= 3.0 -> ContextCompat.getColor(context, R.color.amber_200)
            rating >= 2.0 -> ContextCompat.getColor(context, R.color.orange_500)
            else -> ContextCompat.getColor(context, R.color.red_500)
        }
        ratingText.setTextColor(ratingColor)

        // Set star icon color
        val starColor = when {
            rating >= 4.5 -> ContextCompat.getColor(context, R.color.success_green)
            rating >= 4.0 -> ContextCompat.getColor(context, R.color.premium_gold)
            rating >= 3.0 -> ContextCompat.getColor(context, R.color.amber_200)
            rating >= 2.0 -> ContextCompat.getColor(context, R.color.orange_500)
            else -> ContextCompat.getColor(context, R.color.red_500)
        }
        ImageViewCompat.setImageTintList(starIcon, ColorStateList.valueOf(starColor))
        starIcon.visibility = android.view.View.VISIBLE
    }
}