import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barterhub.databinding.OfferDialogBinding
import com.example.barterhub.adapters.SelectedPhotosAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.navigation.fragment.findNavController
import okio.IOException
import org.json.JSONObject

data class UserData(
    val username: String,
    val profileImage: String,
    val location: String,
    val rating: Double
)

@Suppress("DEPRECATION")
class OfferDialogFragment : DialogFragment() {

    private var _binding: OfferDialogBinding? = null
    private val binding get() = _binding!!

    private val selectedPhotos = mutableListOf<Uri>()
    private lateinit var photosAdapter: SelectedPhotosAdapter
    private var currentPhotoUri: Uri? = null

    companion object {
        private const val REQUEST_CAMERA = 1001
        private const val REQUEST_GALLERY = 1002
        private const val CLOUDINARY_URL = "https://api.cloudinary.com/v1_1/dtccox0s0/image/upload"
        private const val UPLOAD_PRESET = "barterhub_ids"
    }

    private fun openCamera() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val photoFile = createImageFile()
            currentPhotoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                photoFile
            )
            intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(intent, REQUEST_CAMERA)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Camera not available: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("OfferDialog", "Camera error: ${e.message}")
        }
    }

    private fun openGallery() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(Intent.createChooser(intent, "Select Photos"), REQUEST_GALLERY)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Gallery not available: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("OfferDialog", "Gallery error: ${e.message}")
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == android.app.Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CAMERA -> {
                    currentPhotoUri?.let { uri ->
                        selectedPhotos.add(uri)
                        updatePhotosVisibility()
                        Log.d("OfferDialog", "📸 Camera photo added: $uri")
                    } ?: run {
                        Log.e("OfferDialog", "❌ Camera photo URI is null")
                        Toast.makeText(requireContext(), "Failed to capture photo", Toast.LENGTH_SHORT).show()
                    }
                }
                REQUEST_GALLERY -> {
                    if (data?.clipData != null) {
                        val count = data.clipData!!.itemCount
                        for (i in 0 until count) {
                            val imageUri = data.clipData!!.getItemAt(i).uri
                            selectedPhotos.add(imageUri)
                            Log.d("OfferDialog", "🖼️ Gallery photo added: $imageUri")
                        }
                    } else if (data?.data != null) {
                        selectedPhotos.add(data.data!!)
                        Log.d("OfferDialog", "🖼️ Single gallery photo added: ${data.data}")
                    }
                    updatePhotosVisibility()
                }
            }
        } else {
            Log.d("OfferDialog", "❌ Activity result not OK: $resultCode")
        }
    }

    private fun setupRecyclerView() {
        photosAdapter = SelectedPhotosAdapter(selectedPhotos) { uri ->
            selectedPhotos.remove(uri)
            updatePhotosVisibility()
        }

        binding.rvSelectedPhotos.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = photosAdapter
        }
    }

    private fun setupClickListeners() {
        binding.cancelButton.setOnClickListener { dismiss() }
        binding.sendOfferButton.setOnClickListener { sendOffer() }
        binding.btnTakePhoto.setOnClickListener { openCamera() }
        binding.btnChooseFromGallery.setOnClickListener { openGallery() }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = OfferDialogBinding.inflate(LayoutInflater.from(context))
        setupRecyclerView()
        setupClickListeners()
        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
    }

    @SuppressLint("SetTextI18n")
    private fun sendOffer() {
        val message = binding.messageEditText.text.toString().trim()

        if (message.isBlank()) {
            Toast.makeText(requireContext(), "Please enter a message for your offer", Toast.LENGTH_SHORT).show()
            binding.messageEditText.requestFocus()
            return
        }

        if (selectedPhotos.isEmpty()) {
            Toast.makeText(requireContext(), "Please upload at least one photo of the item", Toast.LENGTH_SHORT).show()
            binding.photoPreviewContainer.visibility = View.VISIBLE
            binding.tvSelectedPhotos.text = "📸 Please add photos first"
            binding.tvSelectedPhotos.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            return
        }

        checkIfUserIsVerified { isVerified ->
            if (!isVerified) {
                showVerificationRequiredDialog()
                binding.sendOfferButton.isEnabled = true
                binding.sendOfferButton.text = "Send Offer"
                return@checkIfUserIsVerified
            }

            binding.sendOfferButton.isEnabled = false
            binding.sendOfferButton.text = "Uploading..."

            // ✅ UPLOAD IMAGES
            uploadImagesToCloudinary(selectedPhotos) { uploadedUrls ->
                if (uploadedUrls.isNotEmpty()) {
                    Log.d("OfferDialog", "✅ ${uploadedUrls.size} images uploaded successfully")
                    sendTradeRequestWithImages("Trade Item", message, uploadedUrls)
                } else {
                    Log.e("OfferDialog", "❌ No images were uploaded")
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Failed to upload photos", Toast.LENGTH_SHORT).show()
                        binding.sendOfferButton.isEnabled = true
                        binding.sendOfferButton.text = "Send Offer"
                    }
                }
            }
        }
    }

    private fun uploadImagesToCloudinary(uris: List<Uri>, callback: (List<String>) -> Unit) {
        val uploadedUrls = mutableListOf<String>()
        var completedCount = 0

        Log.d("CloudinaryUpload", "📤 Starting upload of ${uris.size} photos")

        if (uris.isEmpty()) {
            callback(emptyList())
            return
        }

        uris.forEachIndexed { index, uri ->
            uploadImage(uri, index) { imageUrl ->
                if (imageUrl.isNotEmpty()) {
                    uploadedUrls.add(imageUrl)
                    Log.d("CloudinaryUpload", "✅ Image $index uploaded: $imageUrl")
                } else {
                    Log.e("CloudinaryUpload", "❌ Failed to upload image $index")
                }

                completedCount++

                requireActivity().runOnUiThread {
                    binding.sendOfferButton.text = "Uploading $completedCount/${uris.size}"
                }

                if (completedCount == uris.size) {
                    requireActivity().runOnUiThread {
                        callback(uploadedUrls)
                    }
                }

            }
        }
    }

    private fun uploadImage(uri: Uri, index: Int, callback: (String) -> Unit) {
        try {
            val file = uriToFile(uri)
            if (file == null || !file.exists()) {
                Log.e("CloudinaryUpload", "❌ File does not exist: $uri")
                callback("")
                return
            }

            val mimeType = requireContext().contentResolver.getType(uri) ?: "image/jpeg"

            val client = OkHttpClient()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody(mimeType.toMediaType())
                )
                .addFormDataPart("upload_preset", UPLOAD_PRESET)
                .build()

            val request = Request.Builder()
                .url(CLOUDINARY_URL)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("CloudinaryUpload", "❌ Upload failed: ${e.message}")
                    requireActivity().runOnUiThread {
                        callback("")
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val body = it.body?.string()
                        val imageUrl = if (!body.isNullOrEmpty()) {
                            JSONObject(body).getString("secure_url")
                        } else ""
                        requireActivity().runOnUiThread {
                            callback(imageUrl)
                        }
                    }
                }
            })


        } catch (e: Exception) {
            Log.e("CloudinaryUpload", "❌ Exception: ${e.message}")
            callback("")
        }
    }


    private fun uriToFile(uri: Uri): File? {
        return try {
            when {
                uri.scheme == ContentResolver.SCHEME_FILE -> {
                    File(uri.path!!)
                }
                uri.scheme == ContentResolver.SCHEME_CONTENT -> {
                    val contentResolver = requireContext().contentResolver
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    var fileName = "upload_${System.currentTimeMillis()}.jpg"

                    cursor?.use {
                        if (it.moveToFirst()) {
                            val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (displayNameIndex != -1) {
                                fileName = it.getString(displayNameIndex) ?: fileName
                            }
                        }
                    }
                    cursor?.close()

                    val inputStream = contentResolver.openInputStream(uri) ?: return null
                    val tempFile = File(requireContext().cacheDir, fileName)

                    FileOutputStream(tempFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }

                    tempFile
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e("OfferDialog", "❌ Error converting URI to file: ${e.message}")
            null
        }
    }

    @SuppressLint("SetTextI18n")
    private fun sendTradeRequestWithImages(
        offeredItem: String,
        message: String,
        additionalImageUrls: List<String>
    ) {
        val itemId = arguments?.getString("itemId") ?: ""
        val ownerId = arguments?.getString("ownerId") ?: ""
        val itemTitle = arguments?.getString("itemTitle") ?: "Unknown Item"

        if (itemId.isEmpty() || ownerId.isEmpty()) {
            Toast.makeText(requireContext(), "Error: Item information missing", Toast.LENGTH_SHORT).show()
            resetSendButton()
            return
        }

        Log.d("OfferDialog", "📤 Sending trade request with ${additionalImageUrls.size} images")

        // Show final uploading status
        binding.sendOfferButton.text = "Sending request..."

        sendTradeRequestWithOffer(itemId, ownerId, itemTitle, offeredItem, message, additionalImageUrls) {
            dismiss()
        }
    }

    private fun sendTradeRequestWithOffer(
        itemId: String,
        ownerId: String,
        itemTitle: String,
        offeredItem: String,
        message: String,
        additionalPhotos: List<String> = emptyList(),
        onComplete: () -> Unit = {}
    ) {
        val requesterId = FirebaseAuth.getInstance().currentUser?.uid
        if (requesterId == null) {
            Toast.makeText(requireContext(), "Please login first", Toast.LENGTH_SHORT).show()
            resetSendButton()
            return
        }

        val requestId = FirebaseDatabase.getInstance().reference.push().key ?: return

        loadUserData(requesterId, ownerId) { currentUserData, targetUserData ->
            loadActualItemImage(itemId) { targetItemImageUrl ->
                loadOfferedItemImage(offeredItem) { offeredItemImageUrl ->
                    val finalOfferedImageUrl = offeredItemImageUrl.ifEmpty { "" }
                    val additionalPhotosString = additionalPhotos.joinToString(",")

                    Log.d("OfferDialog", "🎯 Saving to Firebase...")
                    Log.d("OfferDialog", "   Additional Photos: $additionalPhotosString")

                    val request = mapOf(
                        "requestId" to requestId,
                        "targetItem" to mapOf(
                            "itemId" to itemId,
                            "title" to itemTitle,
                            "category" to "Unknown",
                            "condition" to "Unknown",
                            "description" to "Item for trade",
                            "image" to targetItemImageUrl
                        ),
                        "offeredItem" to mapOf(
                            "title" to offeredItem,
                            "category" to "Unknown",
                            "condition" to "Good",
                            "description" to offeredItem,
                            "image" to finalOfferedImageUrl
                        ),
                        "fromUser" to mapOf(
                            "userId" to requesterId,
                            "username" to currentUserData.username,
                            "profileImage" to currentUserData.profileImage,
                            "location" to currentUserData.location,
                            "rating" to currentUserData.rating
                        ),
                        "toUser" to mapOf(
                            "userId" to ownerId,
                            "username" to targetUserData.username,
                            "profileImage" to targetUserData.profileImage,
                            "location" to targetUserData.location,
                            "rating" to targetUserData.rating
                        ),
                        "message" to message,
                        "status" to "Pending",
                        "preferredMeetup" to "Public Place",
                        "createdAt" to System.currentTimeMillis(),
                        "additionalPhotos" to additionalPhotosString,
                        "hasAdditionalPhotos" to additionalPhotos.isNotEmpty()
                    )

                    FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
                        .getReference("trade_requests")
                        .child(requestId)
                        .setValue(request)
                        .addOnSuccessListener {
                            Log.d("OfferDialog", "✅ Trade request sent successfully!")
                            Toast.makeText(requireContext(), "Trade offer sent!", Toast.LENGTH_SHORT).show()
                            resetSendButton()
                            onComplete()
                        }
                        .addOnFailureListener { e ->
                            Log.e("OfferDialog", "❌ Error: ${e.message}")
                            Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                            resetSendButton()
                            onComplete()
                        }
                }
            }
        }
    }

    private fun resetSendButton() {
        binding.sendOfferButton.isEnabled = true
        binding.sendOfferButton.text = "Send Offer"
    }

    private fun showVerificationRequiredDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Account Verification Required")
            .setMessage("You need to verify your account before sending trade offers. Please complete ID verification in your profile.")
            .setPositiveButton("Go to Profile") { dialog, which ->
                redirectToProfileFragment()
            }
            .setNegativeButton("Cancel") { dialog, which ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun redirectToProfileFragment() {
        dismiss()
        try {
            findNavController().navigate(com.example.barterhub.R.id.nav_profile)
            Toast.makeText(requireContext(), "Please complete ID verification", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("OfferDialog", "Navigation error: ${e.message}")
            AlertDialog.Builder(requireContext())
                .setTitle("🔐 Verification Required")
                .setMessage("To send trade offers, please complete ID verification in your Profile section.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun checkIfUserIsVerified(onComplete: (Boolean) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.d("OfferDialog", "❌ No current user")
            onComplete(false)
            return
        }

        val database = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("users")

        database.child(currentUser.uid).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val isIDVerified = snapshot.child("isIDVerified").getValue(String::class.java)
                val isVerified = isIDVerified == "verified"
                Log.d("OfferDialog", "🔍 Verification check: $isVerified")
                onComplete(isVerified)
            } else {
                Log.d("OfferDialog", "❌ User data not found")
                onComplete(false)
            }
        }.addOnFailureListener { error ->
            Log.e("OfferDialog", "❌ Database error: ${error.message}")
            onComplete(false)
        }
    }

    private fun loadActualItemImage(itemId: String, onComplete: (String) -> Unit) {
        val database = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/").reference

        database.child("items").child(itemId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val imageUrl = snapshot.child("imageUrl").getValue(String::class.java)
                        ?: snapshot.child("imageUrls").getValue(String::class.java)?.split(",")?.firstOrNull()
                        ?: snapshot.child("image").getValue(String::class.java)
                        ?: ""
                    Log.d("OfferDialog", "📷 Target item image: $imageUrl")
                    onComplete(imageUrl)
                } else {
                    Log.w("OfferDialog", "❌ Target item not found: $itemId")
                    onComplete("")
                }
            }
            .addOnFailureListener { e ->
                Log.e("OfferDialog", "❌ Failed to load target item image: ${e.message}")
                onComplete("")
            }
    }

    private fun loadOfferedItemImage(offeredItemTitle: String, onComplete: (String) -> Unit) {
        val database = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/").reference
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        if (currentUserId.isEmpty()) {
            Log.w("OfferDialog", "❌ No current user ID")
            onComplete("")
            return
        }

        Log.d("OfferDialog", "🔍 Searching for: '$offeredItemTitle'")

        database.child("items")
            .orderByChild("ownerId")
            .equalTo(currentUserId)
            .get()
            .addOnSuccessListener { snapshot ->
                var foundImageUrl = ""
                Log.d("OfferDialog", "📦 Found ${snapshot.childrenCount} items for user")

                for (itemSnap in snapshot.children) {
                    val itemTitle = itemSnap.child("title").getValue(String::class.java) ?: ""
                    if (itemTitle.equals(offeredItemTitle, ignoreCase = true)) {
                        foundImageUrl = extractImageUrl(itemSnap)
                        Log.d("OfferDialog", "✅ Exact match: '$itemTitle' -> '$foundImageUrl'")
                        break
                    }
                }

                if (foundImageUrl.isEmpty()) {
                    Log.w("OfferDialog", "❌ No match found for: '$offeredItemTitle'")
                }

                onComplete(foundImageUrl)
            }
            .addOnFailureListener { e ->
                Log.e("OfferDialog", "❌ Failed to search items: ${e.message}")
                onComplete("")
            }
    }

    private fun extractImageUrl(itemSnap: DataSnapshot): String {
        return itemSnap.child("imageUrl").getValue(String::class.java)
            ?: itemSnap.child("imageUrls").getValue(String::class.java)?.split(",")?.firstOrNull()
            ?: itemSnap.child("image").getValue(String::class.java)
            ?: ""
    }

    private fun loadUserData(
        currentUserId: String,
        targetUserId: String,
        onComplete: (UserData, UserData) -> Unit
    ) {
        val database = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("users")

        database.child(currentUserId).get().addOnSuccessListener { currentUserSnap ->
            val currentUserData = if (currentUserSnap.exists()) {
                val username = currentUserSnap.child("username").getValue(String::class.java)
                    ?: currentUserSnap.child("fullName").getValue(String::class.java)
                    ?: "User_${currentUserId.takeLast(4)}"

                val profileImage = currentUserSnap.child("profileImageUrl").getValue(String::class.java) ?: ""

                val location = currentUserSnap.child("completeAddress").getValue(String::class.java)
                    ?: currentUserSnap.child("address").getValue(String::class.java)
                    ?: currentUserSnap.child("userLocation").getValue(String::class.java)
                    ?: currentUserSnap.child("location").getValue(String::class.java)
                    ?: buildAddressFromParts(
                        currentUserSnap.child("barangay").getValue(String::class.java),
                        currentUserSnap.child("city").getValue(String::class.java),
                        currentUserSnap.child("province").getValue(String::class.java)
                    )
                    ?: "Address not set"

                val rating = currentUserSnap.child("rating").getValue(Double::class.java) ?: 0.0

                UserData(username, profileImage, location, rating)
            } else {
                UserData(
                    username = "User_${currentUserId.takeLast(4)}",
                    profileImage = "",
                    location = "Address not set",
                    rating = 0.0
                )
            }

            database.child(targetUserId).get().addOnSuccessListener { targetUserSnap ->
                val targetUserData = if (targetUserSnap.exists()) {
                    val username = targetUserSnap.child("username").getValue(String::class.java)
                        ?: targetUserSnap.child("fullName").getValue(String::class.java)
                        ?: "User_${targetUserId.takeLast(4)}"

                    val profileImage = targetUserSnap.child("profileImageUrl").getValue(String::class.java) ?: ""

                    val location = targetUserSnap.child("completeAddress").getValue(String::class.java)
                        ?: targetUserSnap.child("address").getValue(String::class.java)
                        ?: targetUserSnap.child("userLocation").getValue(String::class.java)
                        ?: targetUserSnap.child("location").getValue(String::class.java)
                        ?: buildAddressFromParts(
                            targetUserSnap.child("barangay").getValue(String::class.java),
                            targetUserSnap.child("city").getValue(String::class.java),
                            targetUserSnap.child("province").getValue(String::class.java)
                        )
                        ?: "Address not set"

                    val rating = targetUserSnap.child("rating").getValue(Double::class.java) ?: 0.0

                    UserData(username, profileImage, location, rating)
                } else {
                    UserData(
                        username = "User_${targetUserId.takeLast(4)}",
                        profileImage = "",
                        location = "Address not set",
                        rating = 0.0
                    )
                }

                onComplete(currentUserData, targetUserData)
            }
        }.addOnFailureListener {
            Log.e("OfferDialog", "❌ Failed to load user data: ${it.message}")
            val currentUserData = UserData(
                username = "User_${currentUserId.takeLast(4)}",
                profileImage = "",
                location = "Address not set",
                rating = 0.0
            )
            val targetUserData = UserData(
                username = "User_${targetUserId.takeLast(4)}",
                profileImage = "",
                location = "Address not set",
                rating = 0.0
            )
            onComplete(currentUserData, targetUserData)
        }
    }

    private fun buildAddressFromParts(barangay: String?, city: String?, province: String?): String? {
        val parts = listOf(barangay, city, province).filter { !it.isNullOrEmpty() }
        return if (parts.isNotEmpty()) parts.joinToString(", ") else null
    }

    private fun updatePhotosVisibility() {
        val hasPhotos = selectedPhotos.isNotEmpty()

        if (hasPhotos) {
            if (binding.photoPreviewContainer.visibility != View.VISIBLE) {
                binding.photoPreviewContainer.visibility = View.VISIBLE
                binding.photoPreviewContainer.alpha = 0f
                binding.photoPreviewContainer.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            }
            photosAdapter.notifyDataSetChanged()
            binding.tvSelectedPhotos.text = "Selected Photos (${selectedPhotos.size})"
        } else {
            if (binding.photoPreviewContainer.visibility != View.GONE) {
                binding.photoPreviewContainer.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        binding.photoPreviewContainer.visibility = View.GONE
                    }
                    .start()
            }
        }

        Log.d("OfferDialog", "📸 Photo preview: ${if (hasPhotos) "SHOWING" else "HIDDEN"}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}