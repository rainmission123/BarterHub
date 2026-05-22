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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider

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
        private const val EMAIL_HINT = "delacruz@gmail.com"
        private const val PASSWORD_HINT = "password"
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null && currentUser.isEmailVerified) {
            handlePostLoginNavigation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

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

        setupHintBehavior(emailEditText, EMAIL_HINT)
        setupHintBehavior(passwordEditText, PASSWORD_HINT)

        LoginManager.getInstance().registerCallback(
            callbackManager,
            object : FacebookCallback<LoginResult> {
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
            }
        )

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        loginButton.setOnClickListener { loginWithEmailPassword() }

        signupTextView.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        forgotPasswordText.setOnClickListener { resetPassword() }

        googleLoginCard.setOnClickListener {
            showProgress(true)
            startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
        }

        facebookLoginCard.setOnClickListener {
            showProgress(true)
            LoginManager.getInstance().logInWithReadPermissions(
                this,
                listOf("public_profile", "email")
            )
        }

        tvResendVerification.setOnClickListener { resendVerificationEmail() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        callbackManager.onActivityResult(requestCode, resultCode, data)

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

    private fun setupHintBehavior(editText: TextInputEditText, defaultHint: String) {
        editText.hint = defaultHint

        editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                editText.hint = ""
            } else {
                editText.hint = if (editText.text.isNullOrEmpty()) {
                    defaultHint
                } else {
                    ""
                }
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
                        handlePostLoginNavigation()
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

        val data = hashMapOf(
            "email" to email
        )

        com.google.firebase.functions.FirebaseFunctions.getInstance()
            .getHttpsCallable("sendPasswordResetEmail")
            .call(data)
            .addOnSuccessListener {
                showProgress(false)
                showSnack("Password reset email sent to $email")
            }
            .addOnFailureListener { e ->
                showProgress(false)
                showSnack("Reset failed: ${e.message}")
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
                showProgress(false)
                if (task.isSuccessful) {
                    handlePostLoginNavigation()
                } else {
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
                    handlePostLoginNavigation()
                } else {
                    val e = task.exception
                    Log.e(TAG, "FB->Firebase FAILED", e)

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

    private fun handlePostLoginNavigation() {
        val openAfterLogin = intent?.getStringExtra("open_after_login")

        if (openAfterLogin == "chat_message") {
            val chatId = intent.getStringExtra("chatId")
            val partnerId = intent.getStringExtra("partnerId")
            val partnerName = intent.getStringExtra("partnerName")
            val partnerProfilePic = intent.getStringExtra("partnerProfilePic")

            val homeIntent = Intent(this, HomeActivity::class.java).apply {
                putExtra("notification_type", "chat_message")
                putExtra("chatId", chatId)
                putExtra("partnerId", partnerId)
                putExtra("partnerName", partnerName)
                putExtra("partnerProfilePic", partnerProfilePic)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            startActivity(homeIntent)
            finish()
            return
        }

        if (openAfterLogin == "friend_request") {
            val fromUserId = intent.getStringExtra("fromUserId")
            val fromUserName = intent.getStringExtra("fromUserName")
            val fromUserProfilePic = intent.getStringExtra("fromUserProfilePic")

            val homeIntent = Intent(this, HomeActivity::class.java).apply {
                putExtra("notification_type", "friend_request")
                putExtra("fromUserId", fromUserId)
                putExtra("fromUserName", fromUserName)
                putExtra("fromUserProfilePic", fromUserProfilePic)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            startActivity(homeIntent)
            finish()
            return
        }

        startActivity(Intent(this, HomeActivity::class.java))
        finish()
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