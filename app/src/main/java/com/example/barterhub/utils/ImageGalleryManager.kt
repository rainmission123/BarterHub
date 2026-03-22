package com.example.barterhub.utils

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.adapters.ImageViewPagerAdapter
import com.example.barterhub.databinding.FragmentItemDetailBinding

object ImageGalleryManager {

    fun loadImages(
        context: Context,
        binding: FragmentItemDetailBinding,
        imageUrls: List<String?>,
        onOpenFullscreen: (urls: List<String>, startIndex: Int) -> Unit
    ) {
        val validImageUrls = imageUrls.filterNotNull().take(10)

        if (validImageUrls.isEmpty()) {
            loadDefaultImage(context, binding)
            return
        }

        val adapter = ImageViewPagerAdapter(validImageUrls) { index ->
            onOpenFullscreen(validImageUrls, index)
        }
        binding.viewPagerImages.adapter = adapter

        setupCustomDots(binding, validImageUrls.size)
        updateImageCounter(binding, 1, validImageUrls.size)

        binding.viewPagerImages.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateImageCounter(binding, position + 1, validImageUrls.size)
                    updateDotsSelection(binding, position)
                }
            }
        )
    }


// ======== ADD THESE FUNCTIONS ========

    private fun setupCustomDots(binding: FragmentItemDetailBinding, count: Int) {
        // Clear existing dots
        binding.imageIndicator.removeAllViews()

        if (count <= 1) {
            binding.imageIndicator.visibility = View.GONE
            return
        }

        binding.imageIndicator.visibility = View.VISIBLE

        // Create dots
        for (i in 0 until count) {
            val dot = createDot(binding.root.context, i == 0)
            binding.imageIndicator.addView(dot)
        }
    }

    private fun createDot(context: Context, isSelected: Boolean): View {
        val dotSize = 8.dpToPx(context) // Helper function below

        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                marginStart = 4.dpToPx(context)
                marginEnd = 4.dpToPx(context)
            }

            background = if (isSelected) {
                ContextCompat.getDrawable(context, R.drawable.tab_dot_selected)
            } else {
                ContextCompat.getDrawable(context, R.drawable.tab_dot_default)
            }
        }
    }

    private fun updateDotsSelection(binding: FragmentItemDetailBinding, selectedPosition: Int) {
        for (i in 0 until binding.imageIndicator.childCount) {
            val dot = binding.imageIndicator.getChildAt(i)
            dot.background = if (i == selectedPosition) {
                ContextCompat.getDrawable(binding.root.context, R.drawable.tab_dot_selected)
            } else {
                ContextCompat.getDrawable(binding.root.context, R.drawable.tab_dot_default)
            }
        }
    }

    // Helper function to convert dp to pixels
    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    fun loadOwnerImage(context: Context, imageView: ImageView, imageUrl: String?) {
        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(context)
                .load(imageUrl)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .circleCrop()
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.ic_profile)
        }
    }

    fun loadMapPreview(context: Context, imageView: ImageView, latitude: Double, longitude: Double) {
        val mapUrl = generateStaticMapUrl(context, latitude, longitude)

        Glide.with(context)
            .load(mapUrl)
            .placeholder(R.drawable.backgroundlogin)
            .error(R.drawable.backgroundlogin)
            .centerCrop()
            .into(imageView)
    }

    private fun generateStaticMapUrl(context: Context, latitude: Double, longitude: Double): String {
        val token = context.getString(R.string.mapbox_access_token).trim()

        if (token.isEmpty()) {
            return "https://via.placeholder.com/900x450/cccccc/333333?text=Missing+Mapbox+Token"
        }

        // Mapbox wants lng,lat
        val lngLat = String.format(java.util.Locale.US, "%.6f,%.6f", longitude, latitude)

        val style = "mapbox/satellite-streets-v12"
        val size = "900x450"
        val zoom = "15"
        val bearing = "0"
        val pitch = "0"

        return "https://api.mapbox.com/styles/v1/$style/static/" +
                "pin-s+ff0000($lngLat)/" +
                "$lngLat,$zoom,$bearing,$pitch/" +
                "$size" +
                "?access_token=$token&logo=false&attribution=false"
    }

    fun loadDefaultImage(context: Context, binding: FragmentItemDetailBinding) {
        binding.tvImageCount.visibility = View.GONE

        val adapter = ImageViewPagerAdapter(listOf("default")) { _ ->
        }
        binding.viewPagerImages.adapter = adapter
    }


    private fun updateImageCounter(binding: FragmentItemDetailBinding, current: Int, total: Int) {
        binding.tvImageCount.text = "$current/$total"
    }

}