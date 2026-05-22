package com.example.barterhub.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.barterhub.R
import com.example.barterhub.databinding.FragmentAddLocationBinding
import com.example.barterhub.ui.viewmodel.ListingViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import java.util.Locale

class AddLocationFragment : Fragment() {

    private var _binding: FragmentAddLocationBinding? = null
    private val binding get() = _binding!!

    private val listingViewModel: ListingViewModel by activityViewModels()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var selectedPoint: Point? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        setupMapboxMap()
        restoreExistingLocationData()

        binding.detectLocationButton.setOnClickListener {
            detectCurrentLocation()
        }

        binding.nextButton.setOnClickListener {
            val fullLocation = binding.addressInput.text?.toString()?.trim().orEmpty()

            if (fullLocation.isBlank()) {
                Snackbar.make(requireView(), "Please enter/set location", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val parsed = extractLocationParts(fullLocation)

            if (parsed.province.isBlank()) {
                Snackbar.make(
                    requireView(),
                    "Unable to detect province. Please use a more complete address.",
                    Snackbar.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            listingViewModel.location = fullLocation
            listingViewModel.addressText = parsed.addressText
            listingViewModel.cityMunicipality = parsed.cityMunicipality
            listingViewModel.province = parsed.province

            selectedPoint?.let {
                listingViewModel.latitude = it.latitude()
                listingViewModel.longitude = it.longitude()
            }

            findNavController().navigate(R.id.action_addLocation_to_preview)
        }
    }

    private fun restoreExistingLocationData() {
        if (listingViewModel.location.isNotBlank()) {
            binding.addressInput.setText(listingViewModel.location)
        }
    }

    private fun setupMapboxMap() {
        val mapView: MapView = binding.mapView

        mapView.getMapboxMap().loadStyleUri(Style.SATELLITE_STREETS) {
            mapView.gestures.addOnMapClickListener { point ->
                selectedPoint = point
                addMarker(point)
                reverseGeocode(point)
                true
            }

            val locationPlugin = mapView.location
            locationPlugin.updateSettings {
                enabled = true
                pulsingEnabled = true
                pulsingColor = resources.getColor(R.color.colorAccent, null)
                locationPuck = LocationPuck2D(
                    topImage = null,
                    bearingImage = null
                )
            }

            binding.detectLocationButton.setOnClickListener {
                detectCurrentLocationAndZoom()
            }
        }
    }

    private fun detectCurrentLocationAndZoom() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val point = Point.fromLngLat(it.longitude, it.latitude)
                selectedPoint = point
                addMarker(point)
                reverseGeocode(point)

                binding.mapView.getMapboxMap().setCamera(
                    com.mapbox.maps.CameraOptions.Builder()
                        .center(point)
                        .zoom(15.0)
                        .build()
                )
            } ?: Snackbar.make(requireView(), "Unable to detect location", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun addMarker(point: Point) {
        val annotationApi = binding.mapView.annotations
        val markerManager = annotationApi.createPointAnnotationManager()

        markerManager.deleteAll()

        val markerOptions = PointAnnotationOptions()
            .withPoint(point)

        markerManager.create(markerOptions)
    }

    private fun detectCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            binding.progressBar.visibility = View.GONE

            location?.let {
                val point = Point.fromLngLat(location.longitude, location.latitude)
                selectedPoint = point
                addMarker(point)
                reverseGeocode(point)
            } ?: Snackbar.make(requireView(), "Unable to detect location", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun reverseGeocode(point: Point) {
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(point.latitude(), point.longitude(), 1)

            if (!addresses.isNullOrEmpty()) {
                val addressLine = addresses[0].getAddressLine(0).orEmpty()
                binding.addressInput.setText(addressLine)

                val parsed = extractLocationParts(addressLine)
                listingViewModel.addressText = parsed.addressText
                listingViewModel.cityMunicipality = parsed.cityMunicipality
                listingViewModel.province = parsed.province
            }
        } catch (_: Exception) {
        }
    }

    private fun extractLocationParts(fullLocation: String): ParsedLocation {
        val parts = fullLocation.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        var cityMunicipality = ""
        var province = ""

        // 🔥 FIXED LOGIC
        when {
            parts.size >= 5 -> {
                // ex: [code, brgy, city, province, country]
                cityMunicipality = parts[2]
                province = parts[3]
            }
            parts.size == 4 -> {
                // ex: [brgy, city, province, country]
                cityMunicipality = parts[1]
                province = parts[2]
            }
            parts.size == 3 -> {
                // ex: [city, province, country]
                cityMunicipality = parts[0]
                province = parts[1]
            }
            parts.size == 2 -> {
                cityMunicipality = parts[0]
                province = parts[1]
            }
            else -> {
                province = parts.lastOrNull() ?: ""
            }
        }

        cityMunicipality = normalizeLocationValue(cityMunicipality)
        province = normalizeLocationValue(province)

        val addressText = listOf(cityMunicipality, province)
            .filter { it.isNotBlank() }
            .joinToString(", ")

        return ParsedLocation(
            fullLocation = fullLocation,
            addressText = addressText,
            cityMunicipality = cityMunicipality,
            province = province
        )
    }

    private fun normalizeLocationValue(value: String): String {
        return value.lowercase(Locale.getDefault())
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { char ->
                    if (char.isLowerCase()) {
                        char.titlecase(Locale.getDefault())
                    } else {
                        char.toString()
                    }
                }
            }
    }

    data class ParsedLocation(
        val fullLocation: String,
        val addressText: String,
        val cityMunicipality: String,
        val province: String
    )

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onDestroyView() {
        binding.mapView.onDestroy()
        _binding = null
        super.onDestroyView()
    }
}