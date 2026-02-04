package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.ui.viewmodel.ListingViewModel
import com.google.android.material.snackbar.Snackbar
import android.widget.TextView
import android.widget.ImageView
import androidx.navigation.fragment.findNavController
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class AddPreviewFragment : Fragment() {

    private lateinit var photoRecycler: RecyclerView
    private lateinit var listNowButton: AppCompatButton
    private lateinit var titleView: TextView
    private lateinit var descView: TextView
    private lateinit var categoryConditionView: TextView
    private lateinit var priceView: TextView
    private lateinit var originalPriceView: TextView
    private lateinit var locationView: TextView

    private val listingViewModel: ListingViewModel by activityViewModels()
    private val cloudinaryUploadUrl = "https://api.cloudinary.com/v1_1/dtccox0s0/image/upload"
    private val cloudinaryPreset = "barterhub_unsigned"

    // 🟢 Interstitial Ad
    private var interstitialAd: InterstitialAd? = null
    private val AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add_preview, container, false)

        photoRecycler = view.findViewById(R.id.photoPreviewRecycler)
        listNowButton = view.findViewById(R.id.listNowButton)
        titleView = view.findViewById(R.id.previewTitle)
        descView = view.findViewById(R.id.previewDescription)
        categoryConditionView = view.findViewById(R.id.previewCategoryCondition)
        priceView = view.findViewById(R.id.previewPrice)
        originalPriceView = view.findViewById(R.id.previewOriginalPrice)
        locationView = view.findViewById(R.id.previewLocation)

        // Load Interstitial Ad
        loadInterstitialAd()

        // Show Ad on button click before uploading
        listNowButton.setOnClickListener {
            if (interstitialAd != null) {
                interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        // Ad dismissed, proceed to upload
                        uploadToCloudinaryAndSaveToFirebase(view)
                        loadInterstitialAd() // preload next ad
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                        // Failed to show, fallback
                        uploadToCloudinaryAndSaveToFirebase(view)
                    }

                    override fun onAdShowedFullScreenContent() {
                        interstitialAd = null
                    }
                }
                interstitialAd?.show(requireActivity())
            } else {
                // If ad not ready, just upload normally
                uploadToCloudinaryAndSaveToFirebase(view)
            }
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadDataFromViewModel(view)
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            requireContext(),
            AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d("AddPreviewFragment", "Interstitial loaded")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e("AddPreviewFragment", "Interstitial failed: ${loadAdError.message}")
                    interstitialAd = null
                }
            }
        )
    }

    @SuppressLint("SetTextI18n")
    private fun loadDataFromViewModel(view: View) {
        val photoUrls = listingViewModel.selectedImageUrls
        val photoUris = listingViewModel.selectedImages

        if (photoUrls.isNotEmpty()) {
            photoRecycler.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            photoRecycler.adapter = PhotoPreviewAdapter(photoUrls)
        } else if (photoUris.isNotEmpty()) {
            photoRecycler.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            photoRecycler.adapter = LocalPhotoPreviewAdapter(photoUris)
        } else {
            Snackbar.make(view, "No photos available", Snackbar.LENGTH_SHORT).show()
        }

        titleView.text = listingViewModel.title.ifEmpty { "No title provided" }
        descView.text = listingViewModel.description.ifEmpty { "No description provided" }
        categoryConditionView.text =
            "${listingViewModel.category} • ${listingViewModel.condition}"

        displayPriceWithOriginal()

        locationView.text = listingViewModel.location.ifEmpty { "No location provided" }
    }

    @SuppressLint("SetTextI18n")
    private fun displayPriceWithOriginal() {
        val currentPrice = listingViewModel.price
        val originalPrice = listingViewModel.originalPrice

        val formattedCurrentPrice = if (currentPrice.isNotEmpty()) {
            if (currentPrice.startsWith("₱")) currentPrice else "₱$currentPrice"
        } else {
            "Barter Only"
        }

        val formattedOriginalPrice = if (originalPrice.isNotEmpty()) {
            if (originalPrice.startsWith("₱")) originalPrice else "₱$originalPrice"
        } else {
            ""
        }

        val hasOriginalPrice = formattedOriginalPrice.isNotEmpty() &&
                formattedOriginalPrice != "₱0" &&
                formattedOriginalPrice != formattedCurrentPrice &&
                formattedOriginalPrice != "₱" &&
                formattedOriginalPrice != "₱0.0" &&
                formattedOriginalPrice != "₱0.00"

        if (hasOriginalPrice) {
            originalPriceView.text = formattedOriginalPrice
            originalPriceView.visibility = View.VISIBLE
            priceView.text = formattedCurrentPrice
            originalPriceView.paintFlags = originalPriceView.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            priceView.setTextColor(requireContext().getColor(R.color.red_500))
        } else {
            priceView.text = formattedCurrentPrice
            originalPriceView.visibility = View.GONE
            priceView.setTextColor(if (currentPrice.isEmpty()) requireContext().getColor(R.color.gray_600)
            else requireContext().getColor(R.color.colorAccent))
        }

        Log.d("PreviewPrice", "💰 Current: $formattedCurrentPrice, Original: $formattedOriginalPrice, HasOriginal: $hasOriginalPrice")
    }

    private fun uploadToCloudinaryAndSaveToFirebase(view: View) {
        val dbRef = Firebase.database.reference.child("items")
        val itemId = dbRef.push().key ?: return
        val photoUrls = listingViewModel.selectedImageUrls

        if (photoUrls.isEmpty()) {
            Snackbar.make(view, "Please wait until all photos are uploaded", Snackbar.LENGTH_SHORT).show()
            return
        }

        if (listingViewModel.description.isBlank()) {
            Snackbar.make(view, "Description cannot be empty!", Snackbar.LENGTH_LONG).show()
            return
        }

        val snackbar = Snackbar.make(view, "Listing item...", Snackbar.LENGTH_INDEFINITE)
        snackbar.show()

        val itemData = hashMapOf<String, Any>(
            "itemId" to itemId,
            "title" to listingViewModel.title,
            "description" to listingViewModel.description,
            "category" to listingViewModel.category,
            "condition" to listingViewModel.condition,
            "price" to (listingViewModel.price.toDoubleOrNull() ?: 0.0),
            "displayPrice" to listingViewModel.price,
            "imageUrls" to photoUrls.joinToString(","),
            "location" to listingViewModel.location,
            "latitude" to getLatitudeFromLocation(),
            "longitude" to getLongitudeFromLocation(),
            "ownerId" to (FirebaseAuth.getInstance().currentUser?.uid ?: ""),
            "timestamp" to System.currentTimeMillis(),
            "likeCount" to 0
        )

        dbRef.child(itemId).setValue(itemData)
            .addOnSuccessListener {
                snackbar.dismiss()
                Snackbar.make(view, "Item listed successfully!", Snackbar.LENGTH_LONG).show()
                listingViewModel.clearData()
                findNavController().popBackStack(R.id.nav_home, false)
            }
            .addOnFailureListener { e ->
                snackbar.dismiss()
                Snackbar.make(view, "Failed to list item: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }

    private fun getLatitudeFromLocation(): Double {
        return try {
            when {
                listingViewModel.location.contains("Talisay") -> 14.0824478
                listingViewModel.location.contains("Batangas") -> 13.7565
                listingViewModel.location.contains("Manila") -> 14.5995
                listingViewModel.location.contains("Quezon") -> 14.6760
                else -> 14.5995
            }
        } catch (e: Exception) {
            14.5995
        }
    }

    private fun getLongitudeFromLocation(): Double {
        return try {
            when {
                listingViewModel.location.contains("Talisay") -> 120.9618969
                listingViewModel.location.contains("Batangas") -> 121.0583
                listingViewModel.location.contains("Manila") -> 120.9842
                listingViewModel.location.contains("Quezon") -> 121.0437
                else -> 120.9842
            }
        } catch (_: Exception) {
            120.9842
        }
    }

    class PhotoPreviewAdapter(private val photos: List<String>) :
        RecyclerView.Adapter<PhotoPreviewAdapter.PhotoViewHolder>() {

        inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.photoPreview)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_photo_preview, parent, false)
            return PhotoViewHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            val uri = photos[position]
            Glide.with(holder.imageView.context)
                .load(uri)
                .centerCrop()
                .into(holder.imageView)
        }

        override fun getItemCount() = photos.size
    }
}
