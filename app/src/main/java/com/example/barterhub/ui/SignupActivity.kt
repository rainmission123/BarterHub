package com.example.barterhub.ui

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.barterhub.R
import com.example.barterhub.data.UserRepository
import com.example.barterhub.data.UsernameRepository
import com.example.barterhub.domain.SignupManager
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

        findViewById<View>(R.id.btnSignUp).setOnClickListener {

            val fullName = etFullName.text.toString().trim()
            val username = etUsername.text.toString().trim().lowercase()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            when {
                fullName.isEmpty() ||
                        username.isEmpty() ||
                        email.isEmpty() ||
                        password.isEmpty() -> {
                    toast("Fill all fields")
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

                password != confirmPassword -> {
                    toast("Passwords do not match")
                }

                else -> {
                    manager.signup(
                        fullName = fullName,
                        username = username,
                        email = email,
                        password = password,
                        address = "",
                        province = "",
                        cityMunicipality = "",
                        referralCode = null,
                        onSuccess = {

                            FirebaseAuth.getInstance().signOut()

                            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                                .setTitle("Verify your email")
                                .setMessage(
                                    "Account created successfully.\n\n" +
                                            "Please check your email (Inbox or Spam) to verify your account before logging in."
                                )
                                .setPositiveButton("Open Email") { dialog, _ ->
                                    dialog.dismiss()

                                    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                                        addCategory(android.content.Intent.CATEGORY_APP_EMAIL)
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    }

                                    try {
                                        startActivity(intent)
                                    } catch (e: Exception) {
                                        toast("No email app found")
                                    }
                                }
                                .setNegativeButton("Go to Login") { dialog, _ ->
                                    dialog.dismiss()

                                    val intent = android.content.Intent(this, LoginActivity::class.java)
                                    intent.flags =
                                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK

                                    startActivity(intent)
                                    finish()
                                }
                                .setCancelable(false)
                                .show()
                        },
                        onError = {
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