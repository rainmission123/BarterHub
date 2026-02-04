@file:Suppress("DEPRECATION")

package com.example.barterhub.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.barterhub.R
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.util.Arrays
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.util.Base64
import android.widget.Toast



class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var callbackManager: CallbackManager
    private val RC_SIGN_IN = 100
    private lateinit var progressBar: ProgressBar

    private fun printKeyHash() {
        try {
            val info: PackageInfo = packageManager.getPackageInfo(
                "com.example.barterhub",
                PackageManager.GET_SIGNATURES
            )

            // ✅ FIXED: Safe handling of signatures array
            val signatures: Array<Signature> = info.signatures ?: return

            for (signature in signatures) {
                val md = java.security.MessageDigest.getInstance("SHA")
                md.update(signature.toByteArray())
                val keyHash = Base64.encodeToString(md.digest(), Base64.DEFAULT)

                Log.d("KEY_HASH", "==========================================")
                Log.d("KEY_HASH", "YOUR KEY HASH: $keyHash")
                Log.d("KEY_HASH", "==========================================")

                // Show in Toast din para sure
                Toast.makeText(this, "Key Hash: $keyHash", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("KEY_HASH", "Error: ${e.message}")
            Toast.makeText(this, "Error getting Key Hash", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null && currentUser.isEmailVerified) {
            // ✅ May naka-login na user at verified na
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_login)

        auth = FirebaseAuth.getInstance()
        progressBar = findViewById(R.id.progressBar)

        val emailEditText: TextInputEditText = findViewById(R.id.emailEditText)
        val passwordEditText: TextInputEditText = findViewById(R.id.passwordEditText)
        val loginButton: Button = findViewById(R.id.btnLogin)
        val signupTextView: TextView = findViewById(R.id.signUpText)
        val tvResendVerification: TextView = findViewById(R.id.tvResendVerification)
        val googleLoginCard: MaterialCardView = findViewById(R.id.googleLoginCard)
        val facebookLoginCard: MaterialCardView = findViewById(R.id.facebookLoginCard)
        val forgotPasswordText: TextView = findViewById(R.id.forgotPasswordText)

        // Initialize Facebook callback manager
        callbackManager = CallbackManager.Factory.create()

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // ✅ FORGOT PASSWORD FUNCTIONALITY
        forgotPasswordText.setOnClickListener {
            val email = emailEditText.text.toString().trim()

            if (email.isEmpty()) {
                Snackbar.make(
                    loginButton,
                    "Please enter your email address first",
                    Snackbar.LENGTH_LONG
                )
                    .setBackgroundTint(getColor(android.R.color.holo_orange_dark))
                    .setTextColor(getColor(android.R.color.white))
                    .show()
                return@setOnClickListener
            }

            showProgressBar(true)

            // Send password reset email
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    showProgressBar(false)

                    if (task.isSuccessful) {
                        Snackbar.make(
                            loginButton,
                            "Password reset email sent to $email",
                            Snackbar.LENGTH_LONG
                        )
                            .setBackgroundTint(getColor(android.R.color.holo_green_dark))
                            .setTextColor(getColor(android.R.color.white))
                            .show()
                        Log.d("LoginActivity", "Password reset email sent to: $email")
                    } else {
                        val errorMessage = task.exception?.message ?: "Failed to send reset email"
                        Snackbar.make(
                            loginButton,
                            "Error: $errorMessage",
                            Snackbar.LENGTH_LONG
                        )
                            .setBackgroundTint(getColor(android.R.color.holo_red_dark))
                            .setTextColor(getColor(android.R.color.white))
                            .show()
                        Log.e("LoginActivity", "Password reset error: $errorMessage")
                    }
                }
        }

        // Google login button click
        googleLoginCard.setOnClickListener {
            showProgressBar(true)
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        // Facebook login button click
        facebookLoginCard.setOnClickListener {
            showProgressBar(true)
            LoginManager.Companion.getInstance().logInWithReadPermissions(this, Arrays.asList("email", "public_profile"))
            LoginManager.Companion.getInstance().registerCallback(callbackManager, object :
                FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    handleFacebookAccessToken(result.accessToken)
                }

                override fun onCancel() {
                    showProgressBar(false)
                    Snackbar.make(
                        findViewById(R.id.btnLogin),
                        "Facebook login cancelled",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }

                override fun onError(error: FacebookException) {
                    showProgressBar(false)
                    Snackbar.make(
                        findViewById(R.id.btnLogin),
                        "Facebook login failed: ${error.message}",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            })
        }

        // Email/password login
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                showProgressBar(true)
                loginButton.isEnabled = false

                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        showProgressBar(false)
                        loginButton.isEnabled = true

                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            if (user != null && user.isEmailVerified) {
                                val intent = Intent(this, HomeActivity::class.java)
                                startActivity(intent)
                                finish()
                            } else {
                                auth.signOut()
                                Snackbar.make(
                                    loginButton,
                                    "Please verify your email before login.",
                                    Snackbar.LENGTH_LONG
                                )
                                    .setBackgroundTint(getColor(android.R.color.holo_red_dark))
                                    .setTextColor(getColor(android.R.color.white))
                                    .show()

                                tvResendVerification.visibility = View.VISIBLE
                            }
                        } else {
                            Snackbar.make(
                                loginButton,
                                "Invalid email or password. Please try again.",
                                Snackbar.LENGTH_LONG
                            )
                                .setBackgroundTint(getColor(android.R.color.holo_red_dark))
                                .setTextColor(getColor(android.R.color.white))
                                .show()
                        }
                    }
            } else {
                Snackbar.make(
                    loginButton,
                    "Please enter email and password",
                    Snackbar.LENGTH_LONG
                )
                    .setBackgroundTint(getColor(android.R.color.holo_orange_dark))
                    .setTextColor(getColor(android.R.color.white))
                    .show()
            }
        }

        // Navigate to Signup Activity
        signupTextView.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }

        // Resend Verification Email
        tvResendVerification.setOnClickListener {
            val user = auth.currentUser
            if (user != null) {
                showProgressBar(true)
                user.sendEmailVerification()
                    .addOnCompleteListener { task ->
                        showProgressBar(false)
                        if (task.isSuccessful) {
                            Snackbar.make(
                                loginButton,
                                "Verification email sent again. Please check your inbox.",
                                Snackbar.LENGTH_LONG
                            )
                                .setBackgroundTint(getColor(android.R.color.holo_green_dark))
                                .setTextColor(getColor(android.R.color.white))
                                .show()
                        } else {
                            Snackbar.make(
                                loginButton,
                                "Failed to resend verification email.",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
            } else {
                Snackbar.make(
                    loginButton,
                    "No user found. Please login first.",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Pass the activity result to the Facebook callback manager
        callbackManager.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(Exception::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: Exception) {
                showProgressBar(false)
                Snackbar.make(
                    findViewById(R.id.btnLogin),
                    "Google sign in failed: ${e.message}",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && !user.isEmailVerified) {
                        user.sendEmailVerification()
                            .addOnCompleteListener { verificationTask ->
                                showProgressBar(false)
                                if (verificationTask.isSuccessful) {
                                    Snackbar.make(
                                        findViewById(R.id.btnLogin),
                                        "Verification email sent to ${user.email}",
                                        Snackbar.LENGTH_LONG
                                    ).show()
                                    // Optional: sign out until verified
                                    auth.signOut()
                                } else {
                                    Snackbar.make(
                                        findViewById(R.id.btnLogin),
                                        "Failed to send verification email.",
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    } else {
                        showProgressBar(false)
                        Snackbar.make(
                            findViewById(R.id.btnLogin),
                            "Welcome ${user?.displayName}",
                            Snackbar.LENGTH_SHORT
                        ).show()
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    }
                } else {
                    showProgressBar(false)
                    Snackbar.make(
                        findViewById(R.id.btnLogin),
                        "Google Authentication Failed",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun handleFacebookAccessToken(token: AccessToken) {
        val credential = FacebookAuthProvider.getCredential(token.token)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    showProgressBar(false)
                    Snackbar.make(
                        findViewById(R.id.btnLogin),
                        "Welcome ${user?.displayName}",
                        Snackbar.LENGTH_SHORT
                    ).show()
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                } else {
                    showProgressBar(false)
                    Snackbar.make(
                        findViewById(R.id.btnLogin),
                        "Facebook Authentication Failed",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun showProgressBar(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE

        // Disable interactive elements during loading
        val loginButton: Button = findViewById(R.id.btnLogin)
        val signupTextView: TextView = findViewById(R.id.signUpText)
        val forgotPasswordText: TextView = findViewById(R.id.forgotPasswordText)
        val googleLoginCard: MaterialCardView = findViewById(R.id.googleLoginCard)
        val facebookLoginCard: MaterialCardView = findViewById(R.id.facebookLoginCard)

        loginButton.isEnabled = !show
        signupTextView.isEnabled = !show
        forgotPasswordText.isEnabled = !show
        googleLoginCard.isEnabled = !show
        facebookLoginCard.isEnabled = !show
    }
}