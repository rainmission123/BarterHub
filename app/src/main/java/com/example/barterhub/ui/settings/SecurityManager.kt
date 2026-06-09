package com.example.barterhub.ui.settings

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.example.barterhub.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions

class SecurityManager(
    private val context: Context,
    private val auth: FirebaseAuth
) {

    private val functions: FirebaseFunctions =
        FirebaseFunctions.getInstance("us-central1")

    fun showChangePassword() {
        val email = auth.currentUser?.email

        if (email.isNullOrEmpty()) {
            MaterialAlertDialogBuilder(context)
                .setTitle("Change Password")
                .setMessage("No email account found. Please login again or check your account.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.bottom_sheet_change_password, null)

        val tvEmail = view.findViewById<TextView>(R.id.tvPasswordEmail)
        val btnSend = view.findViewById<View>(R.id.btnSendResetEmail)

        tvEmail.text = "Send password reset link to:\n\n$email"

        btnSend.setOnClickListener {
            btnSend.isEnabled = false

            val data = hashMapOf(
                "email" to email
            )

            functions.getHttpsCallable("sendPasswordResetEmail")
                .call(data)
                .addOnSuccessListener {
                    showToast("Password reset email sent.")
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    btnSend.isEnabled = true
                    showError("Failed to send reset email: ${e.message}")
                }
        }

        dialog.setContentView(view)
        dialog.show()
    }

    fun showSecurityBottomSheet(onOpenAppSettings: () -> Unit) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.bottom_sheet_security_settings, null)

        val btnOpenAppSettings = view.findViewById<View>(R.id.btnOpenAppSettings)
        val btnResetPassword = view.findViewById<View>(R.id.btnResetPassword)
        val btnDeleteAccount = view.findViewById<View>(
            R.id.btnRequestAccountDeletion
        )

        btnOpenAppSettings.setOnClickListener {
            dialog.dismiss()
            onOpenAppSettings()
        }

        btnResetPassword.setOnClickListener {
            dialog.dismiss()
            showChangePassword()
        }

        btnDeleteAccount.setOnClickListener {
            showDeleteAccountConfirmation(dialog)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showDeleteAccountConfirmation(dialog: BottomSheetDialog) {
        val user = auth.currentUser

        if (user == null) {
            showError("Please sign in again before requesting account deletion.")
            return
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Request Account Deletion")
            .setMessage(
                "This will request deletion of your BarterHub account and " +
                        "personal data. Some transaction, safety, or legal records " +
                        "may be retained if required."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Request Deletion") { _, _ ->
                requestAccountDeletion(dialog)
            }
            .show()
    }

    private fun requestAccountDeletion(dialog: BottomSheetDialog) {
        functions.getHttpsCallable("requestAccountDeletion")
            .call()
            .addOnSuccessListener {
                showToast("Account deletion request submitted.")
                dialog.dismiss()
            }
            .addOnFailureListener { e ->
                showError(
                    e.message ?: "Could not submit deletion request. Please try again."
                )
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}