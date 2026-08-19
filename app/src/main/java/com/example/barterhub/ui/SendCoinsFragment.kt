package com.example.barterhub.ui

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.barterhub.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.functions.functions
import java.util.UUID

class SendCoinsFragment : Fragment(R.layout.fragment_send_coins) {

    private lateinit var etUsername: TextInputEditText
    private lateinit var etCoins: TextInputEditText
    private lateinit var btnSend: MaterialButton
    private lateinit var tvCurrentBalance: TextView
    private lateinit var progressBar: ProgressBar

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    /*
     * Idempotency retry state.
     *
     * Kapag hindi malinaw kung successful ba ang request
     * dahil halimbawa nag-timeout ang connection,
     * gagamitin ulit natin ang parehong requestId kapag
     * parehong username at amount ang ni-retry ng user.
     */
    private var pendingRequestId: String? = null
    private var pendingUsername: String? = null
    private var pendingAmount: Int? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        etUsername = view.findViewById(R.id.etUsername)
        etCoins = view.findViewById(R.id.etCoins)
        btnSend = view.findViewById(R.id.btnSend)
        tvCurrentBalance = view.findViewById(R.id.tvCurrentBalance)
        progressBar = view.findViewById(R.id.progressBar)

        btnSend.setOnClickListener {
            sendCoins()
        }

        loadCurrentBalance()
    }

    private fun loadCurrentBalance() {
        val userId = auth.uid ?: return

        db.child("users")
            .child(userId)
            .child("wallet")
            .child("coins")
            .get()
            .addOnSuccessListener { snapshot ->
                val balance =
                    snapshot.getValue(Int::class.java) ?: 0

                tvCurrentBalance.text = "$balance coins"
            }
            .addOnFailureListener {
                tvCurrentBalance.text = "0 coins"

                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "Error loading balance",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun sendCoins() {
        val username =
            etUsername.text
                ?.toString()
                ?.trim()
                ?.lowercase()
                .orEmpty()

        val coinsToSend =
            etCoins.text
                ?.toString()
                ?.trim()
                ?.toIntOrNull()
                ?: 0

        if (username.isEmpty()) {
            etUsername.error =
                "Please enter recipient username"
            return
        }

        if (coinsToSend <= 0) {
            etCoins.error =
                "Enter a valid amount"
            return
        }

        /*
         * Reuse requestId only when retrying
         * exactly the same transfer.
         *
         * Different username or amount =
         * brand-new requestId.
         */
        val requestId =
            if (
                pendingRequestId != null &&
                pendingUsername == username &&
                pendingAmount == coinsToSend
            ) {
                pendingRequestId!!
            } else {
                UUID.randomUUID()
                    .toString()
                    .also { newRequestId ->
                        pendingRequestId = newRequestId
                        pendingUsername = username
                        pendingAmount = coinsToSend
                    }
            }

        val data = hashMapOf<String, Any>(
            "username" to username,
            "amount" to coinsToSend,
            "requestId" to requestId
        )

        showLoading(true)

        Firebase.functions("us-central1")
            .getHttpsCallable("sendCoins")
            .call(data)
            .addOnSuccessListener { result ->
                showLoading(false)

                val response =
                    result.data as? Map<*, *>

                val message =
                    response
                        ?.get("message")
                        ?.toString()
                        ?: "Coins sent successfully"

                val newBalance =
                    response
                        ?.get("newBalance")
                        ?.toString()
                        ?: "0"

                /*
                 * Confirmed success:
                 * this transfer is finished.
                 *
                 * The next Send action must receive
                 * a brand-new requestId.
                 */
                clearPendingRequest()

                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "✅ $message",
                        Toast.LENGTH_LONG
                    ).show()
                }

                etUsername.text?.clear()
                etCoins.text?.clear()

                tvCurrentBalance.text =
                    "$newBalance coins"

                loadCurrentBalance()
            }
            .addOnFailureListener { error ->
                showLoading(false)

                /*
                 * IMPORTANT:
                 *
                 * Do NOT clear pendingRequestId here.
                 *
                 * The failure might only mean that
                 * the response was lost/timed out even
                 * though the backend already processed
                 * the transfer.
                 *
                 * If the user retries the same
                 * username + amount, the same requestId
                 * will therefore be sent again.
                 */
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        error.message
                            ?: "Failed to send coins",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun clearPendingRequest() {
        pendingRequestId = null
        pendingUsername = null
        pendingAmount = null
    }

    private fun showLoading(
        isLoading: Boolean
    ) {
        progressBar.visibility =
            if (isLoading) {
                View.VISIBLE
            } else {
                View.GONE
            }

        btnSend.isEnabled = !isLoading

        btnSend.text =
            if (isLoading) {
                "Processing..."
            } else {
                "Send"
            }
    }

    override fun onResume() {
        super.onResume()
        loadCurrentBalance()
    }
}