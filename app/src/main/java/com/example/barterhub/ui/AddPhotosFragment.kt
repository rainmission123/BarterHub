package com.example.barterhub.ui

import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.navigation.fragment.findNavController
import com.example.barterhub.ui.viewmodel.ListingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

@Suppress("DEPRECATION")
class AddPhotosFragment : Fragment() {

    private val listingViewModel: ListingViewModel by activityViewModels()

    private lateinit var imageContainer: LinearLayout
    private lateinit var addImageButton: LinearLayout
    private lateinit var cameraButton: LinearLayout
    private lateinit var photoOptionsCard: MaterialCardView
    private lateinit var nextButton: MaterialButton
    private lateinit var photoCounter: TextView
    private val imageUris = mutableListOf<Uri>()
    private val uploadedUrls = mutableListOf<String>()

    private val client = OkHttpClient()
    private var uploadProgressDialog: ProgressDialog? = null
    private var totalImagesToUpload = 0
    private var completedUploads = 0

    private var currentPhotoPath: String? = null

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, open camera
            openCamera()
        } else {
            // Permission denied
            showErrorDialog("Camera permission is required to take photos")
        }
    }

    // For gallery selection
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newUris = uris.take(MAX_PHOTOS - imageUris.size)
            newUris.forEach { uri ->
                addImageToContainer(uri)
            }
            startBatchUpload(newUris)
            updatePhotoCounter()
            updateNextButtonState()
        }
    }

    // For camera capture
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoPath != null) {
            val photoFile = File(currentPhotoPath)
            val photoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )
            addImageToContainer(photoUri)
            startBatchUpload(listOf(photoUri))
            updatePhotoCounter()
            updateNextButtonState()
        } else {
            // Camera was closed or failed
            currentPhotoPath = null
        }
    }

    // For single image selection (fallback)
    private val pickSingleImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            addImageToContainer(it)
            startBatchUpload(listOf(it))
            updatePhotoCounter()
            updateNextButtonState()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add_photos, container, false)

        imageContainer = view.findViewById(R.id.imageContainer)
        addImageButton = view.findViewById(R.id.addImageButton)
        cameraButton = view.findViewById(R.id.cameraButton)
        photoOptionsCard = view.findViewById(R.id.photoOptionsCard)
        nextButton = view.findViewById(R.id.nextButton)
        photoCounter = view.findViewById(R.id.photoCounter)

        setupClickListeners()
        updatePhotoCounter()
        updateNextButtonState()
        return view
    }

    private fun setupClickListeners() {
        addImageButton.setOnClickListener {
            openGallery()
        }

        cameraButton.setOnClickListener {
            checkCameraPermission()
        }

        photoOptionsCard.setOnClickListener {
            showPhotoSourceDialog()
        }

        nextButton.setOnClickListener {
            if (uploadedUrls.isNotEmpty()) {
                listingViewModel.selectedImageUrls = uploadedUrls.toList()
                findNavController().navigate(R.id.action_addPhotos_to_addDetails)
            } else {
                showErrorDialog("Please wait until all photos are uploaded")
            }
        }
    }

    private fun showPhotoSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choose Photo Source")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermission()
                    1 -> openGallery()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                openCamera()
            }
            else -> {
                // Request permission
                requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }
    }

    private fun openGallery() {
        try {
            pickImageLauncher.launch("image/*")
        } catch (e: Exception) {
            Log.e("AddPhotosFragment", "Error opening gallery: ${e.message}")
            // Fallback to single image picker
            pickSingleImageLauncher.launch("image/*")
        }
    }

    private fun openCamera() {
        try {
            // Create a temporary file to store the camera image
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = requireContext().cacheDir
            val photoFile = File.createTempFile(
                "JPEG_${timeStamp}_",
                ".jpg",
                storageDir
            )

            currentPhotoPath = photoFile.absolutePath

            val photoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )

            takePictureLauncher.launch(photoUri)
        } catch (e: Exception) {
            Log.e("AddPhotosFragment", "Error opening camera: ${e.message}")
            showErrorDialog("Cannot open camera: ${e.message}")
        }
    }

    // ... REST OF YOUR UPLOAD AND IMAGE HANDLING CODE REMAINS THE SAME ...

    private fun startBatchUpload(uris: List<Uri>) {
        totalImagesToUpload = uris.size
        completedUploads = 0

        uploadProgressDialog = ProgressDialog(requireContext()).apply {
            setMessage("Uploading ${uris.size} images...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = totalImagesToUpload
            progress = 0
            setCancelable(false)
            show()
        }

        uris.forEach { uri ->
            uploadToCloudinary(uri)
        }
    }

    private fun uploadToCloudinary(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
                val tempFile = File.createTempFile("upload_", ".jpg", requireContext().cacheDir)
                tempFile.outputStream().use { output -> inputStream?.copyTo(output) }

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", tempFile.name, tempFile.asRequestBody("image/*".toMediaTypeOrNull()))
                    .addFormDataPart("upload_preset", "barterhub_ids")
                    .build()

                val request = Request.Builder()
                    .url("https://api.cloudinary.com/v1_1/dtccox0s0/image/upload")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val bodyString = response.body?.string()

                if (response.isSuccessful && bodyString != null) {
                    val json = JSONObject(bodyString)
                    val imageUrl = json.getString("secure_url")
                    uploadedUrls.add(imageUrl)
                    Log.d("Cloudinary", "✅ Uploaded: $imageUrl")

                    withContext(Dispatchers.Main) {
                        completedUploads++
                        updateUploadProgress()

                        if (completedUploads == totalImagesToUpload) {
                            showUploadCompleteMessage()
                        }
                        updateNextButtonState()
                    }
                } else {
                    Log.e("Cloudinary", "❌ Upload failed: $bodyString")
                    withContext(Dispatchers.Main) {
                        completedUploads++
                        updateUploadProgress()
                        if (completedUploads == totalImagesToUpload) {
                            showUploadCompleteMessage()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Cloudinary", "❌ Exception: ${e.message}")
                withContext(Dispatchers.Main) {
                    completedUploads++
                    updateUploadProgress()
                    if (completedUploads == totalImagesToUpload) {
                        showUploadCompleteMessage()
                    }
                }
            }
        }
    }

    private fun updateUploadProgress() {
        uploadProgressDialog?.progress = completedUploads
        uploadProgressDialog?.setMessage("Uploading images... ($completedUploads/$totalImagesToUpload)")
    }

    private fun showUploadCompleteMessage() {
        uploadProgressDialog?.dismiss()

        val successCount = uploadedUrls.size
        val failedCount = totalImagesToUpload - successCount

        if (failedCount == 0) {
            if (successCount > 1) {
                android.widget.Toast.makeText(requireContext(), "$successCount images uploaded successfully!", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(requireContext(), "Image uploaded successfully!", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            val message = if (successCount > 0) {
                "$successCount images uploaded, $failedCount failed"
            } else {
                "All images failed to upload"
            }
            android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun addImageToContainer(uri: Uri) {
        if (imageUris.size >= MAX_PHOTOS) {
            showMaxPhotosDialog()
            return
        }

        imageUris.add(uri)

        val imageView = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(400, 400).apply {
                setMargins(8, 0, 8, 0)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setOnClickListener { showDeleteDialog(this, uri) }
        }

        Glide.with(this).load(uri).centerCrop().into(imageView)

        val cameraButtonIndex = imageContainer.indexOfChild(cameraButton)
        val addButtonIndex = imageContainer.indexOfChild(addImageButton)
        val insertIndex = minOf(cameraButtonIndex, addButtonIndex)

        imageContainer.addView(imageView, insertIndex)

        updateAddButtonVisibility()
    }

    private fun showDeleteDialog(imageView: ImageView, uri: Uri) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove photo?")
            .setMessage("Do you want to remove this photo?")
            .setPositiveButton("Remove") { _, _ ->
                imageContainer.removeView(imageView)
                imageUris.remove(uri)
                uploadedUrls.removeAll { url -> url.contains(uri.lastPathSegment ?: "") }
                updatePhotoCounter()
                updateNextButtonState()
                updateAddButtonVisibility()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updatePhotoCounter() {
        photoCounter.text = "${imageUris.size}/$MAX_PHOTOS photos"
    }

    private fun updateNextButtonState() {
        val allUploaded = uploadedUrls.size == imageUris.size && imageUris.isNotEmpty()
        nextButton.isEnabled = allUploaded
        nextButton.alpha = if (allUploaded) 1f else 0.5f
    }

    private fun updateAddButtonVisibility() {
        val canAddMore = imageUris.size < MAX_PHOTOS
        addImageButton.visibility = if (canAddMore) View.VISIBLE else View.GONE
        cameraButton.visibility = if (canAddMore) View.VISIBLE else View.GONE
        photoOptionsCard.visibility = if (canAddMore) View.VISIBLE else View.GONE
    }

    private fun showMaxPhotosDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Maximum Photos Reached")
            .setMessage("You can only add up to $MAX_PHOTOS photos.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        uploadProgressDialog?.dismiss()
    }

    companion object {
        private const val MAX_PHOTOS = 10
    }
}