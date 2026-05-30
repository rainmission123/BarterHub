package com.example.barterhub.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.barterhub.R
import com.example.barterhub.data.UserRepository
import com.example.barterhub.data.UsernameRepository
import com.example.barterhub.domain.SignupManager
import com.example.barterhub.managers.LocationDropdownManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class SignupActivity : AppCompatActivity() {

    private lateinit var manager: SignupManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        manager = SignupManager(
            FirebaseAuth.getInstance(),
            UsernameRepository(),
            UserRepository()
        )

        val etFullName = findViewById<TextInputEditText>(R.id.etFullName)
        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)

        val etProvince = findViewById<AutoCompleteTextView>(R.id.etProvince)
        val etCityMunicipality =
            findViewById<AutoCompleteTextView>(R.id.etCityMunicipality)

        val etAddress = findViewById<TextInputEditText>(R.id.etAddress)
        val etReferralCode = findViewById<TextInputEditText>(R.id.etReferralCode)

        // Location Dropdown Setup
        LocationDropdownManager.setup(
            context = this,
            provinceView = etProvince,
            cityView = etCityMunicipality
        )

        findViewById<View>(R.id.btnSignUp).setOnClickListener {

            val fullName = etFullName.text.toString().trim()
            val username = etUsername.text.toString().trim().lowercase()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            val province = etProvince.text.toString().trim()
            val cityMunicipality = etCityMunicipality.text.toString().trim()
            val address = etAddress.text.toString().trim()

            val referralCode =
                etReferralCode.text?.toString()?.trim()?.ifEmpty { null }

            when {

                fullName.isEmpty() ||
                        username.isEmpty() ||
                        email.isEmpty() ||
                        password.isEmpty() -> {

                    toast("Fill all required fields")
                }

                province.isEmpty() -> {
                    toast("Select province")
                }

                cityMunicipality.isEmpty() -> {
                    toast("Select city")
                }

                username.length < 3 -> {
                    toast("Username too short")
                }

                !username.matches(Regex("^[a-z0-9_]+$")) -> {
                    toast("Invalid username format")
                }

                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    toast("Invalid email")
                }

                password.length < 6 -> {
                    toast("Password must be at least 6 characters")
                }

                password != confirmPassword -> {
                    toast("Passwords do not match")
                }

                else -> {

                    findViewById<View>(R.id.progressBar).visibility = View.VISIBLE
                    findViewById<View>(R.id.btnSignUp).isEnabled = false

                    manager.signup(
                        fullName = fullName,
                        username = username,
                        email = email,
                        password = password,
                        address = address,
                        province = province,
                        cityMunicipality = cityMunicipality,
                        referralCode = referralCode,

                        onSuccess = {

                            findViewById<View>(R.id.progressBar).visibility = View.GONE
                            findViewById<View>(R.id.btnSignUp).isEnabled = true

                            FirebaseAuth.getInstance().signOut()

                            MaterialAlertDialogBuilder(this)
                                .setTitle("Verify your email")
                                .setMessage(
                                    "Account created successfully.\n\n" +
                                            "Please check your email (Inbox or Spam) " +
                                            "to verify your account before logging in."
                                )

                                .setPositiveButton("Open Email") { dialog, _ ->

                                    dialog.dismiss()

                                    val intent = Intent(Intent.ACTION_MAIN).apply {
                                        addCategory(Intent.CATEGORY_APP_EMAIL)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }

                                    try {
                                        startActivity(intent)
                                    } catch (e: Exception) {
                                        toast("No email app found")
                                    }
                                }

                                .setNegativeButton("Go to Login") { dialog, _ ->

                                    dialog.dismiss()

                                    val intent =
                                        Intent(this, LoginActivity::class.java)

                                    intent.flags =
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TASK

                                    startActivity(intent)
                                    finish()
                                }

                                .setCancelable(false)
                                .show()
                        },

                        onError = {

                            findViewById<View>(R.id.progressBar).visibility = View.GONE
                            findViewById<View>(R.id.btnSignUp).isEnabled = true

                            toast(it)
                        }
                    )
                }
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}