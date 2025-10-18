package com.example.barterhub.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.example.barterhub.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class SignupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSignUp: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup) // Tiyakin na ito ang tamang layout

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        val etFullName = findViewById<TextInputEditText>(R.id.etFullName)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val cbTerms = findViewById<CheckBox>(R.id.cbTerms)
        btnSignUp = findViewById<MaterialButton>(R.id.btnSignUp)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)
        progressBar = findViewById<ProgressBar>(R.id.progressBar)

        btnSignUp.setOnClickListener {
            Log.d("SignupActivity", "Sign Up button clicked")
            val fullName = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            // Validation checks
            when {
                fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() -> {
                    showToast("Please fill all fields")
                    return@setOnClickListener
                }
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    showToast("Invalid email format")
                    return@setOnClickListener
                }
                password.length < 6 -> {
                    showToast("Password must be at least 6 characters")
                    return@setOnClickListener
                }
                password != confirmPassword -> {
                    showToast("Passwords do not match")
                    return@setOnClickListener
                }
                !cbTerms.isChecked -> {
                    showToast("You must agree to Terms and Conditions")
                    return@setOnClickListener
                }
                else -> {
                    signUpUser(fullName, email, password)
                }
            }
        }

        tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun signUpUser(fullName: String, email: String, password: String) {
        showLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val userId = user?.uid
                    val initials = generateInitials(fullName)

                    user?.sendEmailVerification()
                        ?.addOnSuccessListener {
                            if (userId != null) {
                                saveUserToFirestore(userId, fullName, email, initials)
                                saveUserToRealtimeDatabase(userId, fullName, email, "") // empty string for profileImage for now
                            }

                            showToast("Please check your email to verify your account.")
                            auth.signOut()
                            showLoading(false)
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        }
                        ?.addOnFailureListener { e ->
                            showLoading(false)
                            Log.e("SignupActivity", "Failed to send email verification", e)
                            showToast("Failed to send verification email.")
                        }
                } else {
                    showLoading(false)
                    Log.e("SignupActivity", "Signup failed", task.exception)
                    showToast("Signup failed: ${task.exception?.message}")
                }
            }
    }

    private fun saveUserToFirestore(userId: String, fullName: String, email: String, initials: String) {
        val userMap = hashMapOf(
            "fullName" to fullName,
            "email" to email,
            "profileInitials" to initials,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("users").document(userId)
            .set(userMap)
            .addOnSuccessListener {
                generateAndUploadProfileImage(userId, initials)
            }
            .addOnFailureListener { e ->
                Log.e("SignupActivity", "Firestore save failed", e)
                showToast("Account created but failed to save user data")
            }
    }

    private fun saveUserToRealtimeDatabase(userId: String, fullName: String, email: String, profileImageUrl: String) {
        val userMap = hashMapOf(
            "fullName" to fullName,
            "username" to fullName, // optional: pwede iba kung may username field
            "email" to email,
            "profileImage" to profileImageUrl,
            "rating" to 0.0, // default rating
            "phoneNumber" to "",
            "bio" to "",
            "address" to "",
            "updatedAt" to System.currentTimeMillis()
        )

        val database = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/").getReference("users")
        database.child(userId).setValue(userMap)
            .addOnSuccessListener { Log.d("SignupActivity", "User saved to Realtime DB") }
            .addOnFailureListener { e -> Log.e("SignupActivity", "Failed to save user to Realtime DB", e) }
    }


    private fun generateAndUploadProfileImage(userId: String, initials: String) {
        try {
            val bitmap = generateProfileImage(initials)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val data = baos.toByteArray()

            val storageRef = storage.reference
            val profileImageRef = storageRef.child("profile_images/$userId.png")

            profileImageRef.putBytes(data)
                .addOnSuccessListener {
                    Log.d("SignupActivity", "Profile image uploaded successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("SignupActivity", "Profile image upload failed", e)
                }
        } catch (e: Exception) {
            Log.e("SignupActivity", "Profile image generation failed", e)
        }
    }

    private fun generateInitials(fullName: String): String {
        val names = fullName.split(" ")
        return when {
            names.size >= 2 -> "${names[0].first().uppercase()}${names[1].first().uppercase()}"
            names.size == 1 -> names[0].take(2).uppercase()
            else -> "US"
        }
    }

    private fun generateProfileImage(initials: String): Bitmap {
        val size = 200
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background circle
        val paint = Paint().apply {
            color = "#4A148C".toColorInt() // Fixed color parsing
            isAntiAlias = true
        }

        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        // Draw text (initials)
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = size * 0.4f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val yPos = radius - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(initials, radius, yPos, textPaint)

        return bitmap
    }


    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSignUp.isEnabled = !show
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}