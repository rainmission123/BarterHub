package com.example.barterhub.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.barterhub.databinding.FragmentUploadIdBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import java.io.File

class UploadIdFragment : Fragment() {

    private lateinit var binding: FragmentUploadIdBinding
    private var frontImageUri: Uri? = null
    private var backImageUri: Uri? = null
    private var isFront: Boolean = true
    private var currentPhotoPath: String? = null
    private var isFragmentActive = false
    private lateinit var verificationStatusListener: ValueEventListener

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted && isFragmentActive) openCameraAfterPermission()
        else if (isFragmentActive) Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_LONG).show()
    }

    // Gallery picker
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (!isFragmentActive) return@registerForActivityResult

        uri?.let {
            if (isFront) {
                frontImageUri = it
                loadImage(it, binding.ivFrontID, binding.ivFrontPlaceholder)
            } else {
                backImageUri = it
                loadImage(it, binding.ivBackID, binding.ivBackPlaceholder)
            }
            updateSubmitButton()
        }
    }

    // Camera launcher
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (!isFragmentActive) return@registerForActivityResult

        if (result.resultCode == Activity.RESULT_OK && currentPhotoPath != null) {
            val imagePath = currentPhotoPath!!
            val fixedBitmap = fixImageOrientation(imagePath)

            if (isFront) {
                frontImageUri = Uri.fromFile(File(imagePath))
                binding.ivFrontID.setImageBitmap(fixedBitmap)
                binding.ivFrontPlaceholder.visibility = View.GONE
            } else {
                backImageUri = Uri.fromFile(File(imagePath))
                binding.ivBackID.setImageBitmap(fixedBitmap)
                binding.ivBackPlaceholder.visibility = View.GONE
            }

            updateSubmitButton()
        }
    }

    // ID Camera launcher
    private val idCameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (!isFragmentActive) return@registerForActivityResult

        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("imageUri")?.let { uriString ->
                val uri = Uri.parse(uriString)
                val isFrontFromCamera = result.data?.getBooleanExtra("isFront", true) ?: true

                if (isFrontFromCamera) {
                    frontImageUri = uri
                    loadImage(uri, binding.ivFrontID, binding.ivFrontPlaceholder)
                } else {
                    backImageUri = uri
                    loadImage(uri, binding.ivBackID, binding.ivBackPlaceholder)
                }
                updateSubmitButton()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentUploadIdBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.layoutUploadFront.setOnClickListener {
            if (isFragmentActive) {
                isFront = true
                showImageSourceDialog()
            }
        }

        binding.layoutUploadBack.setOnClickListener {
            if (isFragmentActive) {
                isFront = false
                showImageSourceDialog()
            }
        }

        binding.btnSubmitVerification.setOnClickListener {
            if (isFragmentActive) submitVerification()
        }

        checkExistingVerification()
        setupRealTimeStatusListener()
    }

    override fun onStart() {
        super.onStart()
        isFragmentActive = true
    }

    override fun onStop() {
        super.onStop()
        isFragmentActive = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentActive = false

        // 🔥 IMPORTANTE: Safely remove listener
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && ::verificationStatusListener.isInitialized) {
            try {
                FirebaseDatabase.getInstance().getReference("users/${currentUser.uid}")
                    .removeEventListener(verificationStatusListener)
            } catch (e: Exception) {
                Log.e("UploadIdFragment", "Error removing listener: ${e.message}")
            }
        }
    }

    private fun setupRealTimeStatusListener() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        verificationStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // 🔥 SAFETY CHECK: Skip if fragment is not active
                if (!isFragmentActive || !isAdded || context == null) {
                    Log.d("UploadIdFragment", "Fragment not active, skipping update")
                    return
                }

                try {
                    val status = snapshot.child("isIDVerified").getValue(String::class.java)
                    updateVerificationStatusUI(status)
                    Log.d("REAL_TIME_STATUS", "Status updated to: $status")
                } catch (e: Exception) {
                    Log.e("UploadIdFragment", "Error in onDataChange: ${e.message}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (isFragmentActive) {
                    Log.e("UploadIdFragment", "Real-time listener cancelled: ${error.message}")
                }
            }
        }

        FirebaseDatabase.getInstance().getReference("users/$userId")
            .addValueEventListener(verificationStatusListener)
    }

    @SuppressLint("SetTextI18n")
    private fun updateVerificationStatusUI(status: String?) {
        // 🔥 SAFETY CHECK: Double-check if fragment is still attached
        if (!isFragmentActive || !isAdded || context == null) {
            Log.w("UploadIdFragment", "Fragment not attached, skipping UI update")
            return
        }

        try {
            val successColor = ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
            val redColor = ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
            val orangeColor = ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)
            val grayColor = ContextCompat.getColor(requireContext(), android.R.color.darker_gray)

            when (status) {
                "verified" -> {
                    binding.tvVerificationStatus.text = "🎉 VERIFIED! Your ID has been approved!"
                    binding.tvVerificationStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
                    binding.tvVerificationStatus.setTextColor(successColor)
                    binding.btnSubmitVerification.visibility = View.GONE
                    if (isFragmentActive) {
                        Toast.makeText(requireContext(), "🎉 Verification Approved!", Toast.LENGTH_SHORT).show()
                    }
                }
                "rejected" -> {
                    binding.tvVerificationStatus.text = "❌ REJECTED! Please upload valid ID photos."
                    binding.tvVerificationStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                    binding.tvVerificationStatus.setTextColor(redColor)
                    binding.btnSubmitVerification.visibility = View.VISIBLE
                    binding.btnSubmitVerification.text = "Resubmit Verification"
                    if (isFragmentActive) {
                        Toast.makeText(requireContext(), "❌ Verification Rejected", Toast.LENGTH_SHORT).show()
                    }
                }
                "pending" -> {
                    binding.tvVerificationStatus.text = "⏳ PENDING: Under review by admin"
                    binding.tvVerificationStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light))
                    binding.tvVerificationStatus.setTextColor(orangeColor)
                    binding.btnSubmitVerification.visibility = View.GONE
                }
                else -> {
                    binding.tvVerificationStatus.text = "📸 Please upload ID photos for verification"
                    binding.tvVerificationStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                    binding.tvVerificationStatus.setTextColor(grayColor)
                    binding.btnSubmitVerification.visibility = View.VISIBLE
                    binding.btnSubmitVerification.text = "Submit for Verification"
                }
            }
            binding.tvVerificationStatus.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e("UploadIdFragment", "Error updating UI: ${e.message}")
        }
    }

    private fun showImageSourceDialog() {
        if (!isFragmentActive) return

        AlertDialog.Builder(requireContext())
            .setTitle("Select Image Source")
            .setItems(arrayOf("Take Photo with ID Guide", "Choose from Gallery")) { _, which ->
                when (which) {
                    0 -> openIdCamera()
                    1 -> openGallery()
                }
            }.show()
    }

    private fun openIdCamera() {
        if (!isFragmentActive) return

        val intent = Intent(requireContext(), IdCameraActivity::class.java).apply {
            putExtra("isFront", isFront)
        }
        idCameraLauncher.launch(intent)
    }

    private fun checkCameraPermission() {
        if (!isFragmentActive) return

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCameraAfterPermission()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openGallery() {
        if (!isFragmentActive) return
        galleryLauncher.launch("image/*")
    }

    private fun openCameraAfterPermission() {
        if (!isFragmentActive) return

        try {
            val imageFile = createImageFile()
            val photoURI = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                imageFile
            )
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            cameraLauncher.launch(intent)
        } catch (e: Exception) {
            if (isFragmentActive) {
                Toast.makeText(requireContext(), "Failed to open camera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createImageFile(): File {
        return File.createTempFile("photo_${System.currentTimeMillis()}", ".jpg", requireContext().cacheDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun loadImage(uri: Uri?, imageView: ImageView, placeholder: ImageView) {
        if (uri == null || !isFragmentActive) return

        try {
            val imagePath = uri.path
            if (imagePath != null) {
                val file = File(imagePath)
                if (file.exists()) {
                    val fixedBitmap = fixImageOrientation(file.absolutePath)
                    imageView.setImageBitmap(fixedBitmap)
                } else {
                    Glide.with(this).load(uri).into(imageView)
                }
            } else {
                Glide.with(this).load(uri).into(imageView)
            }

            imageView.visibility = View.VISIBLE
            placeholder.visibility = View.GONE
        } catch (e: Exception) {
            Log.e("UploadIdFragment", "Error loading image: ${e.message}")
        }
    }

    private fun updateSubmitButton() {
        if (!isFragmentActive) return
        binding.btnSubmitVerification.isEnabled = (frontImageUri != null && backImageUri != null)
    }

    @SuppressLint("SetTextI18n")
    private fun submitVerification() {
        if (!isFragmentActive) return

        if (frontImageUri != null && backImageUri != null) {
            binding.btnSubmitVerification.isEnabled = false
            binding.btnSubmitVerification.text = "Uploading..."

            uploadVerificationImage(frontImageUri!!, "front",
                onSuccess = { frontPath ->
                    if (!isFragmentActive) return@uploadVerificationImage
                    uploadVerificationImage(backImageUri!!, "back",
                        onSuccess = { backPath ->
                            if (!isFragmentActive) return@uploadVerificationImage
                            saveVerificationData(frontPath, backPath)
                        },
                        onError = { errorMsg ->
                            if (isFragmentActive) showErrorAndReset("Back image: $errorMsg")
                        }
                    )
                },
                onError = { errorMsg ->
                    if (isFragmentActive) showErrorAndReset("Front image: $errorMsg")
                }
            )
        } else {
            Toast.makeText(requireContext(), "Please upload both ID photos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadVerificationImage(
        imageUri: Uri,
        side: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            onError("User not logged in")
            return
        }

        val storagePath = "id_verifications/${currentUser.uid}/$side/${System.currentTimeMillis()}.jpg"
        val storageRef = FirebaseStorage.getInstance().reference.child(storagePath)

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                onSuccess(storagePath)
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Upload failed")
            }
    }

    @SuppressLint("SetTextI18n")
    private fun showErrorAndReset(errorMessage: String) {
        if (!isFragmentActive) return
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
        resetUploadButton()
    }

    @SuppressLint("SetTextI18n")
    private fun resetUploadButton() {
        if (!isFragmentActive) return
        binding.btnSubmitVerification.isEnabled = true
        binding.btnSubmitVerification.text = "Submit for Verification"
    }

    @SuppressLint("SetTextI18n")
    private fun saveVerificationData(frontPath: String, backPath: String) {
        if (!isFragmentActive) return

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val verificationData = mapOf(
            "idFrontPath" to frontPath,
            "idBackPath" to backPath,
            "isIDVerified" to "pending",
            "verificationSubmittedAt" to System.currentTimeMillis()
        )

        FirebaseDatabase.getInstance().getReference("users/${currentUser.uid}")
            .updateChildren(verificationData)
            .addOnSuccessListener {
                if (!isFragmentActive) return@addOnSuccessListener

                Toast.makeText(requireContext(), "✅ ID submitted for verification!", Toast.LENGTH_SHORT).show()
                updateVerificationStatusUI("pending")
                binding.btnSubmitVerification.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                if (!isFragmentActive) return@addOnFailureListener
                Toast.makeText(requireContext(), "Failed to submit: ${e.message}", Toast.LENGTH_SHORT).show()
                resetUploadButton()
            }
    }

    private fun checkExistingVerification() {
        if (!isFragmentActive) return

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().getReference("users/$userId").addListenerForSingleValueEvent(object : ValueEventListener {
            @SuppressLint("SetTextI18n")
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isFragmentActive) return

                val status = snapshot.child("isIDVerified").getValue(String::class.java)
                val frontPath = snapshot.child("idFrontPath").getValue(String::class.java)
                val backPath = snapshot.child("idBackPath").getValue(String::class.java)
                val frontUrl = snapshot.child("idFrontUrl").getValue(String::class.java)
                val backUrl = snapshot.child("idBackUrl").getValue(String::class.java)

                updateVerificationStatusUI(status)

                if (!frontPath.isNullOrEmpty()) {
                    loadStoredIdImage(frontPath, binding.ivFrontID)
                    binding.ivFrontID.visibility = View.VISIBLE
                    binding.ivFrontPlaceholder.visibility = View.GONE
                } else if (!frontUrl.isNullOrEmpty()) {
                    Glide.with(requireContext()).load(frontUrl).into(binding.ivFrontID)
                    binding.ivFrontID.visibility = View.VISIBLE
                    binding.ivFrontPlaceholder.visibility = View.GONE
                }
                if (!backPath.isNullOrEmpty()) {
                    loadStoredIdImage(backPath, binding.ivBackID)
                    binding.ivBackID.visibility = View.VISIBLE
                    binding.ivBackPlaceholder.visibility = View.GONE
                } else if (!backUrl.isNullOrEmpty()) {
                    Glide.with(requireContext()).load(backUrl).into(binding.ivBackID)
                    binding.ivBackID.visibility = View.VISIBLE
                    binding.ivBackPlaceholder.visibility = View.GONE
                }
                updateSubmitButton()
            }

            override fun onCancelled(error: DatabaseError) {
                if (isFragmentActive) {
                    Toast.makeText(requireContext(), "Error loading verification data", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun loadStoredIdImage(storagePath: String, imageView: ImageView) {
        FirebaseStorage.getInstance().reference.child(storagePath).downloadUrl
            .addOnSuccessListener { uri ->
                if (isFragmentActive && isAdded) {
                    Glide.with(requireContext()).load(uri).into(imageView)
                }
            }
            .addOnFailureListener { e ->
                Log.e("UploadIdFragment", "Failed to load ID image: ${e.message}")
            }
    }

    private fun fixImageOrientation(imagePath: String): Bitmap {
        val bitmap = BitmapFactory.decodeFile(imagePath)
        val ei = ExifInterface(imagePath)
        val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
            else -> bitmap
        }
    }

    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
