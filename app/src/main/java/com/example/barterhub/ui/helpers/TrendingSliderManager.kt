package com.example.barterhub.ui.helpers

import android.os.Handler
import android.os.Looper
import androidx.viewpager2.widget.ViewPager2

class TrendingSliderManager(
    private val viewPager: ViewPager2
) {
    private val autoSliderHandler = Handler(Looper.getMainLooper())
    private var autoSliderRunnable: Runnable? = null

    fun startAutoSlide() {
        autoSliderRunnable?.let { autoSliderHandler.removeCallbacks(it) }

        autoSliderRunnable = object : Runnable {
            override fun run() {
                val adapter = viewPager.adapter ?: return
                if (adapter.itemCount <= 1) return

                val currentItem = viewPager.currentItem
                val nextItem = if (currentItem < adapter.itemCount - 1) {
                    currentItem + 1
                } else {
                    0
                }

                viewPager.setCurrentItem(nextItem, true)
                autoSliderHandler.postDelayed(this, 3000)
            }
        }

        autoSliderHandler.postDelayed(autoSliderRunnable!!, 3000)
    }

    fun stopAutoSlide() {
        autoSliderRunnable?.let { autoSliderHandler.removeCallbacks(it) }
    }
}