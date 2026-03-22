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
import com.facebook.*
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var callbackManager: CallbackManager

    private val RC_SIGN_IN = 100

    private lateinit var progressBar: ProgressBar
    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var loginButton: Button
    private lateinit var signupTextView: TextView
    private lateinit var tvResendVerification: TextView
    private lateinit var googleLoginCard: MaterialCardView
    private lateinit var facebookLoginCard: MaterialCardView
    private lateinit var forgotPasswordText: TextView

    companion object {
        private const val TAG = "FB_AUTH"
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null && currentUser.isEmailVerified) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Views
        progressBar = findViewById(R.id.progressBar)
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.btnLogin)
        signupTextView = findViewById(R.id.signUpText)
        tvResendVerification = findViewById(R.id.tvResendVerification)
        googleLoginCard = findViewById(R.id.googleLoginCard)
        facebookLoginCard = findViewById(R.id.facebookLoginCard)
        forgotPasswordText = findViewById(R.id.forgotPasswordText)

        callbackManager = CallbackManager.Factory.create()

        LoginManager.getInstance().registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                Log.d(TAG, "FB onSuccess userId=${result.accessToken.userId}")
                handleFacebookAccessToken(result.accessToken)
            }

            override fun onCancel() {
                showProgress(false)
                showSnack("Facebook login cancelled")
            }

            override fun onError(error: FacebookException) {
                showProgress(false)
                Log.e(TAG, "Facebook SDK error", error)
                showSnack("Facebook error: ${error.message}")
            }
        })

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Email/password login
        loginButton.setOnClickListener { loginWithEmailPassword() }

        // Signup
        signupTextView.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        // Forgot password
        forgotPasswordText.setOnClickListener { resetPassword() }

        // Google login
        googleLoginCard.setOnClickListener {
            showProgress(true)
            startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
        }

        // ✅ Facebook login (CLICK ONLY starts login)
        facebookLoginCard.setOnClickListener {
            showProgress(true)
            LoginManager.getInstance().logInWithReadPermissions(this, listOf("public_profile", "email"))
        }

        // Resend verification
        tvResendVerification.setOnClickListener { resendVerificationEmail() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // ✅ Facebook
        callbackManager.onActivityResult(requestCode, resultCode, data)

        // ✅ Google
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(Exception::class.java)
                if (account?.idToken.isNullOrBlank()) {
                    showProgress(false)
                    showSnack("Google sign-in token missing.")
                    return
                }
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: Exception) {
                showProgress(false)
                showSnack("Google sign in failed: ${e.message}")
            }
        }
    }

    private fun loginWithEmailPassword() {
        val email = emailEditText.text?.toString()?.trim().orEmpty()
        val password = passwordEditText.text?.toString()?.trim().orEmpty()

        if (email.isBlank() || password.isBlank()) {
            showSnack("Please enter email and password")
            return
        }

        showProgress(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                showProgress(false)
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && user.isEmailVerified) {
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    } else {
                        auth.signOut()
                        tvResendVerification.visibility = View.VISIBLE
                        showSnack("Please verify your email before login.")
                    }
                } else {
                    showSnack("Invalid email or password. ${task.exception?.message ?: ""}")
                }
            }
    }

    private fun resetPassword() {
        val email = emailEditText.text?.toString()?.trim().orEmpty()
        if (email.isBlank()) {
            showSnack("Please enter your email address first")
            return
        }

        showProgress(true)
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                showProgress(false)
                if (task.isSuccessful) {
                    showSnack("Password reset email sent to $email")
                } else {
                    showSnack("Reset failed: ${task.exception?.message}")
                }
            }
    }

    private fun resendVerificationEmail() {
        val user = auth.currentUser
        if (user == null) {
            showSnack("No user found. Please login first.")
            return
        }

        showProgress(true)
        user.sendEmailVerification()
            .addOnCompleteListener { task ->
                showProgress(false)
                if (task.isSuccessful) {
                    showSnack("Verification email sent again. Please check your inbox.")
                } else {
                    showSnack("Failed to resend: ${task.exception?.message}")
                }
            }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showProgress(false)
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                } else {
                    showProgress(false)
                    showSnack("Google Authentication Failed: ${task.exception?.message}")
                }
            }
    }

    private fun handleFacebookAccessToken(token: AccessToken) {
        val credential = FacebookAuthProvider.getCredential(token.token)

        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                showProgress(false)

                if (task.isSuccessful) {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                } else {
                    val e = task.exception
                    Log.e(TAG, "FB->Firebase FAILED", e)

                    // ✅ Helpful message for the most common case
                    if (e is FirebaseAuthUserCollisionException) {
                        showSnack(
                            "Account exists with another sign-in method. " +
                                    "Login using Google/Email first, then we will link Facebook."
                        )
                    } else {
                        showSnack("Facebook Authentication Failed: ${e?.javaClass?.simpleName}: ${e?.message}")
                    }
                }
            }
    }

    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        loginButton.isEnabled = !show
        signupTextView.isEnabled = !show
        forgotPasswordText.isEnabled = !show
        googleLoginCard.isEnabled = !show
        facebookLoginCard.isEnabled = !show
    }

    private fun showSnack(message: String) {
        Snackbar.make(findViewById(R.id.btnLogin), message, Snackbar.LENGTH_LONG).show()
    }
}

