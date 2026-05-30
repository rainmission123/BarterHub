package com.example.barterhub.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.managers.UsernameManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import kotlin.math.max

class EditProfileActivity : AppCompatActivity() {

    private lateinit var fabChangePhoto: FloatingActionButton
    private lateinit var ivProfileImage: ImageView
    private lateinit var etFullName: EditText
    private lateinit var etBio: EditText
    private lateinit var etPhone: EditText
    private lateinit var etLocation: EditText
    private lateinit var btnSave: MaterialButton
    private var imageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 1001
    private val client = OkHttpClient()
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var usernameManager: UsernameManager
    private var oldUsername: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        val scroll = requireView<NestedScrollView>(R.id.scrollRoot, "scrollRoot")

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, max(ime, nav))
            insets
        }

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        usernameManager = UsernameManager()

        ivProfileImage = requireView(R.id.ivProfileImage, "ivProfileImage")
        fabChangePhoto = requireView(R.id.fabChangePhoto, "fabChangePhoto")
        etFullName = requireView(R.id.editFullName, "editFullName")
        etBio = requireView(R.id.editBio, "editBio")
        etPhone = requireView(R.id.editPhone, "editPhone")
        etLocation = requireView(R.id.editLocation, "editLocation")
        btnSave = requireView(R.id.btnSave, "btnSave")
        ivProfileImage.setOnClickListener { openFileChooser() }
        fabChangePhoto.setOnClickListener { openFileChooser() }
        btnSave.setOnClickListener { saveProfile() }

        loadUserData()
    }

    private fun <T : android.view.View> requireView(id: Int, name: String): T {
        return findViewById<T>(id)
            ?: throw IllegalStateException("Missing view in activity_edit_profile.xml: $name")
    }

    private fun openFileChooser() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            imageUri = data.data
            Glide.with(this).load(imageUri).into(ivProfileImage)
        }
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return

        database.child("users").child(uid).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    oldUsername = snapshot.child("username").getValue(String::class.java) ?: ""

                    etFullName.setText(snapshot.child("fullName").getValue(String::class.java) ?: "")
                    etBio.setText(snapshot.child("bio").getValue(String::class.java) ?: "")
                    etPhone.setText(snapshot.child("phoneNumber").getValue(String::class.java) ?: "")
                    etLocation.setText(snapshot.child("address").getValue(String::class.java) ?: "")

                    val profileImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                    if (!profileImageUrl.isNullOrEmpty()) {
                        Glide.with(this).load(profileImageUrl).into(ivProfileImage)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveProfile() {
        val uid = auth.currentUser?.uid ?: return

        val fullName = etFullName.text.toString().trim()
        val newUsername = oldUsername.trim().lowercase()
        val bio = etBio.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val location = etLocation.text.toString().trim()

        if (fullName.isEmpty()) {
            Toast.makeText(this, "Full name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (newUsername.isEmpty()) {
            Toast.makeText(this, "Username cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        btnSave.isEnabled = false

        if (imageUri != null) {
            uploadToCloudinary(
                imageUri = imageUri!!,
                onSuccess = { imageUrl ->
                    saveProfileToDatabase(uid, fullName, newUsername, bio, phone, location, imageUrl)
                },
                onError = { errorMsg ->
                    btnSave.isEnabled = true
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                }
            )
        } else {
            saveProfileToDatabase(uid, fullName, newUsername, bio, phone, location, null)
        }
    }

    private fun uploadToCloudinary(
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                onError("Cannot open image")
                return
            }

            val imageBytes = inputStream.readBytes()
            inputStream.close()

            if (imageBytes.size > 10 * 1024 * 1024) {
                onError("Image too large. Max 10MB")
                return
            }

            val fileName = "profile_${System.currentTimeMillis()}.jpg"
            val requestBody = imageBytes.toRequestBody("image/*".toMediaTypeOrNull())

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, requestBody)
                .addFormDataPart("upload_preset", "barterhub_ids")
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/dtccox0s0/image/upload")
                .post(multipartBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Log.e("Cloudinary", "Upload failed: ${e.message}")
                        onError("Upload failed: ${e.message}")
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val responseBody = response.body?.string() ?: ""

                        if (!response.isSuccessful) {
                            Log.e("Cloudinary", "Upload failed: ${response.code} - $responseBody")
                            runOnUiThread {
                                onError("Upload failed: ${response.code}")
                            }
                            return
                        }

                        try {
                            val json = JSONObject(responseBody)
                            if (json.has("error")) {
                                val errorMsg = json.getJSONObject("error").getString("message")
                                runOnUiThread { onError("Cloudinary error: $errorMsg") }
                                return
                            }

                            val url = json.getString("secure_url")
                            runOnUiThread { onSuccess(url) }
                        } catch (e: Exception) {
                            Log.e("Cloudinary", "Parse error: ${e.message}")
                            runOnUiThread { onError("Failed to parse response") }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("Cloudinary", "Upload error: ${e.message}")
            onError("Upload error: ${e.message}")
        }
    }

    private fun saveProfileToDatabase(
        uid: String,
        fullName: String,
        newUsername: String,
        bio: String,
        phone: String,
        location: String,
        imageUrl: String?
    ) {
        val userUpdates = hashMapOf<String, Any>(
            "fullName" to fullName,
            "bio" to bio,
            "address" to location,
            "phoneNumber" to phone,
            "updatedAt" to System.currentTimeMillis()
        )

        if (imageUrl != null) {
            userUpdates["profileImageUrl"] = imageUrl
            userUpdates["profileImage"] = imageUrl
        }

        val publicUpdates = hashMapOf<String, Any>(
            "fullName" to fullName,
            "updatedAt" to System.currentTimeMillis()
        )

        if (imageUrl != null) {
            publicUpdates["profileImageUrl"] = imageUrl
            publicUpdates["profileImage"] = imageUrl
        }

        database.child("users").child(uid).updateChildren(userUpdates)
            .addOnSuccessListener {
                database.child("public_users").child(uid).updateChildren(publicUpdates)
                    .addOnSuccessListener {
                        updateUsernameIfNeeded(uid, newUsername)
                    }
                    .addOnFailureListener { e ->
                        btnSave.isEnabled = true
                        Toast.makeText(this, e.message ?: "Failed to update public profile", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                btnSave.isEnabled = true
                Toast.makeText(this, e.message ?: "Failed to update profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUsernameIfNeeded(uid: String, newUsername: String) {
        if (newUsername == oldUsername.trim().lowercase()) {
            btnSave.isEnabled = true
            Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        usernameManager.updateUsername(
            uid = uid,
            oldUsername = oldUsername,
            newUsernameInput = newUsername,
            onSuccess = {
                btnSave.isEnabled = true
                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                finish()
            },
            onError = { error ->
                btnSave.isEnabled = true
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        )
    }
}