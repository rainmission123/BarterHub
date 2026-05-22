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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase

class SendCoinsFragment : Fragment(R.layout.fragment_send_coins) {

    private lateinit var etUsername: TextInputEditText
    private lateinit var etCoins: TextInputEditText
    private lateinit var btnSend: MaterialButton
    private lateinit var tvCurrentBalance: TextView
    private lateinit var progressBar: ProgressBar

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
                val balance = snapshot.getValue(Int::class.java) ?: 0
                tvCurrentBalance.text = "$balance coins"
            }
            .addOnFailureListener {
                tvCurrentBalance.text = "0 coins"
                Toast.makeText(requireContext(), "Error loading balance", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendCoins() {
        val username = etUsername.text.toString().trim().lowercase()
        val coinsToSend = etCoins.text.toString().trim().toIntOrNull() ?: 0

        if (username.isEmpty()) {
            etUsername.error = "Please enter recipient username"
            return
        }

        if (coinsToSend <= 0) {
            etCoins.error = "Enter a valid amount"
            return
        }

        showLoading(true)

        val data = hashMapOf(
            "username" to username,
            "amount" to coinsToSend
        )

        Firebase.functions("us-central1")
            .getHttpsCallable("sendCoins")
            .call(data)
            .addOnSuccessListener { result ->
                showLoading(false)

                val response = result.data as? Map<*, *>
                val message = response?.get("message")?.toString()
                    ?: "Coins sent successfully"
                val newBalance = response?.get("newBalance")?.toString() ?: "0"

                Toast.makeText(
                    requireContext(),
                    "✅ $message",
                    Toast.LENGTH_LONG
                ).show()

                etUsername.text?.clear()
                etCoins.text?.clear()
                tvCurrentBalance.text = "$newBalance coins"

                loadCurrentBalance()
            }
            .addOnFailureListener { error ->
                showLoading(false)

                Toast.makeText(
                    requireContext(),
                    error.message ?: "Failed to send coins",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSend.isEnabled = !isLoading
        btnSend.text = if (isLoading) "Processing..." else "Send"
    }

    override fun onResume() {
        super.onResume()
        loadCurrentBalance()
    }
}