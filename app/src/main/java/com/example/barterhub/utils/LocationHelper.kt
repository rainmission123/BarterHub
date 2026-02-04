package com.example.barterhub.utils

import android.content.Context
import android.location.Geocoder
import java.util.Locale

object LocationHelper {

    fun getCityFromLatLng(
        context: Context,
        lat: Double,
        lng: Double
    ): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            addresses?.firstOrNull()?.locality
        } catch (e: Exception) {
            null
        }
    }
}
