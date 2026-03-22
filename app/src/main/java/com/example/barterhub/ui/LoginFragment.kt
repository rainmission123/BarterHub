package com.example.barterhub.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.barterhub.R
import com.facebook.*
import com.facebook.login.LoginBehavior
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {

    private lateinit var callbackManager: CallbackManager
    private lateinit var auth: FirebaseAuth

    companion object {
        private const val TAG = "FB_AUTH"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Firebase
        auth = FirebaseAuth.getInstance()

        // Facebook
        callbackManager = CallbackManager.Factory.create()

        // Optional but recommended
        LoginManager.getInstance().setLoginBehavior(LoginBehavior.NATIVE_WITH_FALLBACK)

        // Register callback ONCE
        LoginManager.getInstance().registerCallback(
            callbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    val token = result.accessToken?.token
                    if (token.isNullOrBlank()) {
                        Toast.makeText(requireContext(), "Facebook token missing", Toast.LENGTH_LONG).show()
                        return
                    }

                    val credential = FacebookAuthProvider.getCredential(token)

                    auth.signInWithCredential(credential)
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), "Facebook login success!", Toast.LENGTH_SHORT).show()

                            // TODO: navigate to your home screen
                            // findNavController().navigate(R.id.action_loginFragment_to_homeFragment)

                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Firebase signInWithCredential failed", e)
                            Toast.makeText(
                                requireContext(),
                                "Firebase login failed: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }

                override fun onCancel() {
                    Toast.makeText(requireContext(), "Facebook login cancelled", Toast.LENGTH_SHORT).show()
                }

                override fun onError(error: FacebookException) {
                    Log.e(TAG, "Facebook SDK error", error)
                    Toast.makeText(
                        requireContext(),
                        "Facebook error: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Click FB card to login
        val fbCard = view.findViewById<View>(R.id.facebookLoginCard)
        fbCard.setOnClickListener {
            // Request minimal permissions
            LoginManager.getInstance().logInWithReadPermissions(
                this, // IMPORTANT: Fragment (not activity)
                listOf("public_profile", "email")
            )
        }
    }

    // IMPORTANT: forward result to Facebook SDK
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Optional cleanup (not required)
        // LoginManager.getInstance().unregisterCallback(callbackManager)
    }
}