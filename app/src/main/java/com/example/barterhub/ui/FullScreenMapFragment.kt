package com.example.barterhub.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.barterhub.R
import com.example.barterhub.databinding.FragmentFullscreenMapBinding
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.scalebar.scalebar

class FullScreenMapFragment : Fragment() {

    private var _binding: FragmentFullscreenMapBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFullscreenMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCloseMap.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // SAFE: Get arguments
        val lat = arguments?.getFloat("lat", 0f)?.toDouble() ?: 0.0
        val lng = arguments?.getFloat("lng", 0f)?.toDouble() ?: 0.0

        if (lat == 0.0 || lng == 0.0) {
            requireActivity().onBackPressedDispatcher.onBackPressed()
            return
        }

        setupMapControls()
        initializeMap(lat, lng)
    }

    private fun setupMapControls() {
        val mapView = binding.mapViewFullScreen

        // ✅ Convert 40dp → px (correct for all screen densities)
        val dp60 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            60f,
            resources.displayMetrics
        )

        val dp24 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            24f,
            resources.displayMetrics
        )

        // 🧭 COMPASS — TOP LEFT, mas may breathing room
        mapView.compass.apply {
            enabled = true
            position = Gravity.TOP or Gravity.START
            marginTop = dp60
            marginLeft = dp24
        }

        // 📏 SCALEBAR — BOTTOM LEFT
        mapView.scalebar.apply {
            enabled = true
            marginLeft = dp24
            marginBottom = dp24
        }

        // 📍 Location component (optional)
        mapView.location.apply {
            enabled = false
        }
    }


    private fun initializeMap(latitude: Double, longitude: Double) {
        val mapView = binding.mapViewFullScreen
        val point = Point.fromLngLat(longitude, latitude)

        mapView.getMapboxMap().loadStyleUri(Style.SATELLITE_STREETS) { style ->
            if (!isAdded || activity == null) return@loadStyleUri

            // Set camera position
            mapView.getMapboxMap().setCamera(
                CameraOptions.Builder()
                    .center(point)
                    .zoom(15.0)
                    .build()
            )

            // Add marker
            addMarker(mapView, point)
        }
    }

    private fun addMarker(mapView: com.mapbox.maps.MapView, point: Point) {
        try {
            val pointAnnotationManager = mapView.annotations.createPointAnnotationManager()

            val bitmap = BitmapFactory.decodeResource(
                requireContext().resources,
                R.drawable.ic_map_marker
            )

            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(bitmap)
                .withIconSize(1.5) // Slightly larger

            pointAnnotationManager.create(pointAnnotationOptions)

        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to simple annotation
            addFallbackMarker(mapView, point)
        }
    }

    private fun addFallbackMarker(mapView: com.mapbox.maps.MapView, point: Point) {
        try {
            val circleAnnotationManager = mapView.annotations.createCircleAnnotationManager()
            val circleAnnotationOptions = com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions()
                .withPoint(point)
                .withCircleColor("#FF0000")
                .withCircleRadius(10.0)
                .withCircleStrokeWidth(2.0)
                .withCircleStrokeColor("#FFFFFF")

            circleAnnotationManager.create(circleAnnotationOptions)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}