package com.example.barterhub.utils

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.barterhub.R

object MapManager {

    fun openFullScreenMap(fragment: Fragment, latitude: Double, longitude: Double) {
        if (latitude == 0.0 || longitude == 0.0) return

        val bundle = Bundle().apply {
            putFloat("lat", latitude.toFloat())
            putFloat("lng", longitude.toFloat())
        }

        try {
            fragment.findNavController().navigate(
                R.id.action_itemDetailFragment_to_fullScreenMapFragment,
                bundle
            )
        } catch (e: Exception) {
            // Fallback: Just use back press if navigation fails
            fragment.requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }
}