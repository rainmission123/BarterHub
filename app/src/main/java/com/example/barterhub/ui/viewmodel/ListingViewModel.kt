package com.example.barterhub.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel

class ListingViewModel : ViewModel() {

    // Photos
    var selectedImages: List<Uri> = emptyList()
    var selectedImageUrls: List<String> = emptyList()

    // Details
    var title: String = ""
    var description: String = ""
    var category: String = ""
    var condition: String = ""
    var price: String = ""
    var originalPrice: String = ""

    // Location
    var location: String = ""
    var addressText: String = ""
    var cityMunicipality: String = ""
    var province: String = ""

    var latitude: Double = 0.0
    var longitude: Double = 0.0

    fun clearData() {
        selectedImages = emptyList()
        selectedImageUrls = emptyList()

        title = ""
        description = ""
        category = ""
        condition = ""
        price = ""
        originalPrice = ""

        location = ""
        addressText = ""
        cityMunicipality = ""
        province = ""

        latitude = 0.0
        longitude = 0.0
    }
}