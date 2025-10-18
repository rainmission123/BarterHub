package com.example.barterhub.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.barterhub.data.models.Item
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.NumberFormat
import java.util.*

data class Condition(val name: String, val iconRes: Int) {
    override fun toString(): String = name
}

@Suppress("DEPRECATION")
class AddFragment : androidx.fragment.app.Fragment(com.example.barterhub.R.layout.fragment_add) {

    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var usersDatabase: DatabaseReference

    private lateinit var imageContainer: LinearLayout
    private lateinit var addImageButton: LinearLayout
    private val selectedImageUris = mutableListOf<Uri>()
    private lateinit var pickImageLauncher: ActivityResultLauncher<String>

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>

    private val uploadedImageUrls = mutableMapOf<Uri, String>()
    private val imageUploadProgress = mutableMapOf<Uri, Int>()
    private val imageViewMap = mutableMapOf<Uri, View>()

    private var selectedCategory: String? = null
    private var selectedCondition: String? = null

    // 🔥 DAGDAG: Verification status
    private var isUserVerified = false
    private lateinit var verificationStatusListener: ValueEventListener

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("items")
        usersDatabase = FirebaseDatabase.getInstance().getReference("users")

        imageContainer = view.findViewById(com.example.barterhub.R.id.imageContainer)
        addImageButton = view.findViewById(com.example.barterhub.R.id.addImageButton)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        setupLocationPermission()
        setupImagePicker()
        setupCategoryDropdown()
        setupConditionDropdown()
        setupPriceFormatting()
        setupClickListeners()

        // 🔥 DAGDAG: Check user verification status
        checkUserVerificationStatus()
    }

    // 🔥 DAGDAG: Function to check user verification status
    private fun checkUserVerificationStatus() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showVerificationRequiredMessage()
            return
        }

        verificationStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val verificationStatus = snapshot.child("isIDVerified").getValue(String::class.java)
                    isUserVerified = verificationStatus == "verified"

                    if (!isUserVerified) {
                        showVerificationRequiredMessage()
                    } else {
                        hideVerificationMessage()
                    }
                } else {
                    // User data not found, assume not verified
                    isUserVerified = false
                    showVerificationRequiredMessage()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // If there's an error, assume not verified for safety
                isUserVerified = false
                showVerificationRequiredMessage()
            }
        }

        usersDatabase.child(currentUser.uid).addValueEventListener(verificationStatusListener)
    }

    // 🔥 DAGDAG: Show verification required message
    @SuppressLint("SetTextI18n", "CutPasteId")
    private fun showVerificationRequiredMessage() {
        view?.findViewById<MaterialButton>(com.example.barterhub.R.id.submitButton)?.isEnabled = false
        view?.findViewById<MaterialButton>(com.example.barterhub.R.id.submitButton)?.text = "Verify to Post"

        // Show a message to the user
        val verificationMessage = view?.findViewById<TextView>(com.example.barterhub.R.id.verificationMessage)
        if (verificationMessage != null) {
            verificationMessage.visibility = View.VISIBLE
            verificationMessage.text = "You need to verify your ID to post items. Please go to your profile to verify."
        } else {
            // Create and show Toast if no TextView exists
            Toast.makeText(requireContext(), "You need to verify your ID to post items", Toast.LENGTH_LONG).show()
        }

        // Disable all input fields
        disableAllInputs()
    }

    // 🔥 DAGDAG: Hide verification message when verified
    @SuppressLint("CutPasteId")
    private fun hideVerificationMessage() {
        view?.findViewById<MaterialButton>(com.example.barterhub.R.id.submitButton)?.isEnabled = true
        view?.findViewById<MaterialButton>(com.example.barterhub.R.id.submitButton)?.text = "List Item"

        val verificationMessage = view?.findViewById<TextView>(com.example.barterhub.R.id.verificationMessage)
        verificationMessage?.visibility = View.GONE

        // Enable all input fields
        enableAllInputs()
    }

    // 🔥 DAGDAG: Disable all input fields
    private fun disableAllInputs() {
        view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.itemTitleEditText)?.isEnabled = false
        view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.descriptionEditText)?.isEnabled = false
        view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.priceEditText)?.isEnabled = false
        view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.locationEditText)?.isEnabled = false
        view?.findViewById<AutoCompleteTextView>(com.example.barterhub.R.id.categorySpinner)?.isEnabled = false
        view?.findViewById<AutoCompleteTextView>(com.example.barterhub.R.id.conditionSpinner)?.isEnabled = false
        addImageButton.isEnabled = false
        view?.findViewById<MaterialButton>(com.example.barterhub.R.id.getLocationButton)?.isEnabled = false
    }

    // 🔥 DAGDAG: Enable all input fields
    private fun enableAllInputs() {
        view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.itemTitleEditText)?.isEnabled = true
        view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.descriptionEditText)?.isEnabled = true
        view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.priceEditText)?.isEnabled = true
        view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.locationEditText)?.isEnabled = true
        view?.findViewById<AutoCompleteTextView>(com.example.barterhub.R.id.categorySpinner)?.isEnabled = true
        view?.findViewById<AutoCompleteTextView>(com.example.barterhub.R.id.conditionSpinner)?.isEnabled = true
        addImageButton.isEnabled = true
        view?.findViewById<MaterialButton>(com.example.barterhub.R.id.getLocationButton)?.isEnabled = true
    }

    private fun setupLocationPermission() {
        locationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                if (fineGranted || coarseGranted) getUserLocation()
                else Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupImagePicker() {
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            // 🔥 DAGDAG: Check if user is verified before allowing image selection
            if (!isUserVerified) {
                Toast.makeText(requireContext(), "Please verify your ID first to post items", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }

            uri?.let { selectedUri ->
                if (selectedImageUris.size >= 5) {
                    Toast.makeText(requireContext(), "Max 5 images only", Toast.LENGTH_SHORT).show()
                    return@let
                }
                selectedImageUris.add(selectedUri)

                val imageItemView = LayoutInflater.from(requireContext())
                    .inflate(com.example.barterhub.R.layout.item_upload_image, imageContainer, false)

                val imageView = imageItemView.findViewById<ImageView>(com.example.barterhub.R.id.uploadImageView)
                val progressBar = imageItemView.findViewById<ProgressBar>(com.example.barterhub.R.id.uploadProgress)
                val deleteButton = imageItemView.findViewById<ImageView>(com.example.barterhub.R.id.btnRemoveImage)

                Glide.with(requireContext()).load(selectedUri).into(imageView)

                val addIndex = imageContainer.indexOfChild(addImageButton)
                imageContainer.addView(imageItemView, addIndex)

                deleteButton.setOnClickListener {
                    removeImageFromContainer(imageItemView, selectedUri)
                }

                imageUploadProgress[selectedUri] = 0
                uploadImageToCloudinary(selectedUri, progressBar, deleteButton)
            }
        }
    }

    private fun uploadImageToCloudinary(uri: Uri, progressBar: ProgressBar, deleteButton: ImageView) {
        progressBar.visibility = View.VISIBLE
        deleteButton.visibility = View.GONE

        MediaManager.get().upload(uri)
            .option("folder", "barterhub/items")
            .option("resource_type", "image")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) { progressBar.progress = 0 }

                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                    val progress = ((bytes.toDouble() / totalBytes.toDouble()) * 100).toInt()
                    progressBar.progress = progress
                    imageUploadProgress[uri] = progress
                }

                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    progressBar.progress = 100
                    progressBar.visibility = View.GONE
                    deleteButton.visibility = View.VISIBLE
                    val imageUrl = resultData["secure_url"].toString()
                    uploadedImageUrls[uri] = imageUrl
                    imageUploadProgress[uri] = 100
                }

                override fun onError(requestId: String, error: ErrorInfo) {
                    progressBar.visibility = View.GONE
                    deleteButton.visibility = View.VISIBLE
                    uploadedImageUrls.remove(uri)
                    imageUploadProgress.remove(uri)
                    Toast.makeText(requireContext(), "Upload failed: ${error.description}", Toast.LENGTH_SHORT).show()
                }

                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            })
            .dispatch()
    }

    private fun removeImageFromContainer(imageItemView: View, uri: Uri) {
        imageContainer.removeView(imageItemView)
        selectedImageUris.remove(uri)
        uploadedImageUrls.remove(uri)
        imageUploadProgress.remove(uri)
        imageViewMap.remove(uri)
        Toast.makeText(requireContext(), "Image removed", Toast.LENGTH_SHORT).show()
    }

    private fun setupCategoryDropdown() {
        val categoryEt = view?.findViewById<AutoCompleteTextView>(com.example.barterhub.R.id.categorySpinner) ?: return
        val categories = listOf(
            Condition("Electronics", com.example.barterhub.R.drawable.ic_electronics),
            Condition("Kitchen", com.example.barterhub.R.drawable.ic_kitchen),
            Condition("Clothing", com.example.barterhub.R.drawable.ic_clothings),
            Condition("Books", com.example.barterhub.R.drawable.ic_books),
            Condition("Sports & Outdoors", com.example.barterhub.R.drawable.ic_sports),
            Condition("Food & Beverages", com.example.barterhub.R.drawable.food),
            Condition("Vehicles", com.example.barterhub.R.drawable.car),
            Condition("Baby & Kids", com.example.barterhub.R.drawable.baby),
            Condition("Pet Supplies", com.example.barterhub.R.drawable.pet),
            Condition("Rice", com.example.barterhub.R.drawable.rice),
            Condition("Fish & Seafood", com.example.barterhub.R.drawable.fish),
            Condition("Meat & Poultry", com.example.barterhub.R.drawable.meat),
            Condition("Fruits & Vegetables", com.example.barterhub.R.drawable.vegetable),
            Condition("Groceries", com.example.barterhub.R.drawable.grocery),
            Condition("Home Appliances", com.example.barterhub.R.drawable.furniture),
            Condition("Handmade & Crafts", com.example.barterhub.R.drawable.craft),
            Condition("Livestock", com.example.barterhub.R.drawable.livestock),
            Condition("Services", com.example.barterhub.R.drawable.service),
            Condition("Others", com.example.barterhub.R.drawable.ic_others)
        )

        val adapter = object : ArrayAdapter<Condition>(requireContext(), com.example.barterhub.R.layout.category_item_with_icon, categories) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context)
                    .inflate(com.example.barterhub.R.layout.category_item_with_icon, parent, false)
                val category = getItem(position)
                view.findViewById<TextView>(com.example.barterhub.R.id.item_text).text = category?.name
                view.findViewById<ImageView>(com.example.barterhub.R.id.item_icon).setImageResource(category?.iconRes ?: 0)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup) = getView(position, convertView, parent)
        }

        categoryEt.setAdapter(adapter)
        categoryEt.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position) as Condition
            selectedCategory = selected.name
            categoryEt.setText(selected.name)
            categoryEt.error = null
        }
        categoryEt.inputType = 0 // non-editable
    }

    private fun setupConditionDropdown() {
        val conditionEt = view?.findViewById<AutoCompleteTextView>(com.example.barterhub.R.id.conditionSpinner) ?: return
        val conditions = listOf(
            Condition("New", com.example.barterhub.R.drawable.ic_new),
            Condition("Like New", com.example.barterhub.R.drawable.ic_like_new),
            Condition("Used", com.example.barterhub.R.drawable.ic_used),
            Condition("For Parts", com.example.barterhub.R.drawable.ic_parts)
        )

        val adapter = object : ArrayAdapter<Condition>(requireContext(), com.example.barterhub.R.layout.dropdown_item_with_icon, conditions) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(com.example.barterhub.R.layout.dropdown_item_with_icon, parent, false)
                val condition = getItem(position)
                view.findViewById<TextView>(com.example.barterhub.R.id.item_text).text = condition?.name
                view.findViewById<ImageView>(com.example.barterhub.R.id.item_icon).setImageResource(condition?.iconRes ?: 0)
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup) = getView(position, convertView, parent)
        }

        conditionEt.setAdapter(adapter)
        conditionEt.dropDownWidth = 150.dp
        conditionEt.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position) as Condition
            selectedCondition = selected.name
            conditionEt.setText(selected.name)
            conditionEt.error = null
        }
    }

    private fun setupPriceFormatting() {
        val priceEt = view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.priceEditText) ?: return
        priceEt.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    priceEt.removeTextChangedListener(this)
                    val clean = s.toString().replace(",", "")
                    current = if (clean.isNotEmpty()) {
                        try {
                            val parsed = clean.toDouble()
                            val formatted = NumberFormat.getNumberInstance(Locale.US).format(parsed)
                            priceEt.setText(formatted)
                            priceEt.setSelection(formatted.length)
                            formatted
                        } catch (_: Exception) { "" }
                    } else ""
                    priceEt.addTextChangedListener(this)
                }
            }
        })
    }

    private fun setupClickListeners() {
        addImageButton.setOnClickListener {
            // 🔥 DAGDAG: Check verification before allowing image selection
            if (!isUserVerified) {
                Toast.makeText(requireContext(), "Please verify your ID first to post items", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            pickImageLauncher.launch("image/*")
        }

        view?.findViewById<MaterialButton>(com.example.barterhub.R.id.getLocationButton)?.setOnClickListener {
            // 🔥 DAGDAG: Check verification before allowing location access
            if (!isUserVerified) {
                Toast.makeText(requireContext(), "Please verify your ID first to post items", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            requestLocationPermission()
        }

        view?.findViewById<MaterialButton>(com.example.barterhub.R.id.submitButton)?.setOnClickListener {
            // 🔥 DAGDAG: Final verification check before submission
            if (!isUserVerified) {
                Toast.makeText(requireContext(), "Please verify your ID first to post items", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            submitItem()
        }
    }

    private fun requestLocationPermission() {
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        val hasFine = ContextCompat.checkSelfPermission(requireContext(), fine) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(requireContext(), coarse) == PackageManager.PERMISSION_GRANTED
        if (hasFine && hasCoarse) getUserLocation() else locationPermissionLauncher.launch(arrayOf(fine, coarse))
    }

    @SuppressLint("MissingPermission")
    private fun getUserLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    currentLatitude = location.latitude
                    currentLongitude = location.longitude
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    val address = addresses?.get(0)?.getAddressLine(0) ?: "Unknown location"
                    view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.locationEditText)?.setText(address)
                } else Toast.makeText(requireContext(), "Unable to get location", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Location error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } catch (_: SecurityException) {
            Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun submitItem() {
        // 🔥 DAGDAG: Final verification check
        if (!isUserVerified) {
            Toast.makeText(requireContext(), "Please verify your ID first to post items", Toast.LENGTH_LONG).show()
            return
        }

        val titleEt = view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.itemTitleEditText)
        val descEt = view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.descriptionEditText)
        val priceEt = view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.priceEditText)
        val locationEt = view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.locationEditText)
        val submitBtn = view?.findViewById<MaterialButton>(com.example.barterhub.R.id.submitButton)

        val title = titleEt?.text?.toString()?.trim().orEmpty()
        val desc = descEt?.text?.toString()?.trim().orEmpty()
        val priceText = priceEt?.text?.toString()?.trim().orEmpty()
        val price = priceText.replace(",", "").toDoubleOrNull()
        val location = locationEt?.text?.toString()?.trim().orEmpty()
        val ownerId = auth.currentUser?.uid ?: return

        // 🔹 CHECK ALL VALIDATIONS
        if (title.isEmpty()) { titleEt?.error = "Title required"; return }
        if (desc.isEmpty()) { descEt?.error = "Description required"; return }
        if (selectedCategory.isNullOrEmpty()) { Toast.makeText(requireContext(), "Select category", Toast.LENGTH_SHORT).show(); return }
        if (selectedCondition.isNullOrEmpty()) { Toast.makeText(requireContext(), "Select condition", Toast.LENGTH_SHORT).show(); return }
        if (price != null && price < 0) { priceEt?.error = "Price cannot be negative"; return }
        if (location.isEmpty()) { locationEt?.error = "Location required"; return }
        if (selectedImageUris.isEmpty()) { Toast.makeText(requireContext(), "Select at least 1 image", Toast.LENGTH_SHORT).show(); return }
        if (selectedImageUris.any { imageUploadProgress[it] != 100 }) { Toast.makeText(requireContext(), "Wait for all images to upload", Toast.LENGTH_SHORT).show(); return }

        submitBtn?.isEnabled = false
        submitBtn?.text = "Uploading..."

        val itemId = database.push().key ?: return
        val uploadedUrls = selectedImageUris.mapNotNull { uploadedImageUrls[it] }
        if (uploadedUrls.size != selectedImageUris.size) {
            Toast.makeText(requireContext(), "Some images failed. Retry", Toast.LENGTH_SHORT).show()
            submitBtn?.isEnabled = true
            submitBtn?.text = "List Item"
            return
        }

        val item = Item(
            itemId = itemId,
            ownerId = ownerId,
            title = title,
            description = desc,
            category = selectedCategory ?: "",
            condition = selectedCondition ?: "",
            price = price,
            displayPrice = priceText.ifEmpty { "Barter Only" },
            location = location,
            imageUrls = uploadedUrls.joinToString(","),
            latitude = currentLatitude,
            longitude = currentLongitude,
            timestamp = System.currentTimeMillis()
        )

        database.child(itemId).setValue(item).addOnSuccessListener {
            Toast.makeText(requireContext(), "Item posted successfully!", Toast.LENGTH_SHORT).show()
            clearForm()
            submitBtn?.isEnabled = true
            submitBtn?.text = "List Item"
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Failed to post item", Toast.LENGTH_SHORT).show()
            submitBtn?.isEnabled = true
            submitBtn?.text = "List Item"
        }
    }

    private fun clearForm() {
        view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.itemTitleEditText)?.text?.clear()
        view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.descriptionEditText)?.text?.clear()
        view?.findViewById<AutoCompleteTextView>(com.example.barterhub.R.id.categorySpinner)?.text?.clear()
        view?.findViewById<AutoCompleteTextView>(com.example.barterhub.R.id.conditionSpinner)?.text?.clear()
        view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.priceEditText)?.text?.clear()
        view?.findViewById<TextInputEditText>(com.example.barterhub.R.id.locationEditText)?.text?.clear()

        selectedCategory = null
        selectedCondition = null
        uploadedImageUrls.clear()
        imageUploadProgress.clear()
        imageViewMap.clear()

        for (i in imageContainer.childCount - 1 downTo 0) {
            val child = imageContainer.getChildAt(i)
            if (child != addImageButton) imageContainer.removeViewAt(i)
        }
        selectedImageUris.clear()
    }

    // 🔥 DAGDAG: Clean up listener
    override fun onDestroyView() {
        super.onDestroyView()
        val currentUser = auth.currentUser
        if (currentUser != null && ::verificationStatusListener.isInitialized) {
            usersDatabase.child(currentUser.uid).removeEventListener(verificationStatusListener)
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}