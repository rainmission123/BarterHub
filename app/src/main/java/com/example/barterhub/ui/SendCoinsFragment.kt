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


        // Load current user's balance
        loadCurrentBalance()
    }

    private fun loadCurrentBalance() {
        val userId = auth.uid ?: return

        db.child("users").child(userId).child("coins")
            .get()
            .addOnSuccessListener { snapshot ->
                val balance = snapshot.getValue(Int::class.java) ?: 0
                tvCurrentBalance.text = "$balance coins"
            }
            .addOnFailureListener {
                tvCurrentBalance.text = "Error loading balance"
            }
    }
    private fun sendCoinNotification(senderUid: String, receiverUid: String, coins: Int) {
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(senderUid)

        userRef.get().addOnSuccessListener { snapshot ->

            // Auto-detect ANY available name field
            val senderName =
                snapshot.child("name").getValue(String::class.java)
                    ?: snapshot.child("username").getValue(String::class.java)
                    ?: snapshot.child("fullName").getValue(String::class.java)
                    ?: snapshot.child("displayName").getValue(String::class.java)
                    ?: snapshot.child("userName").getValue(String::class.java)
                    ?: "Unknown User"

            val notifRef = FirebaseDatabase.getInstance()
                .getReference("notifications")
                .child(receiverUid)
                .push()

            val data = mapOf(
                "fromUserId" to senderUid,
                "fromUserName" to senderName,
                "type" to "coins_received",
                "coins" to coins,
                "timestamp" to System.currentTimeMillis(),
                "read" to false
            )

            notifRef.setValue(data)
        }
    }

    private fun sendCoins() {
        val senderId = auth.uid ?: return
        val recipientUid = etUsername.text.toString().trim()
        val coinsToSend = etCoins.text.toString().trim().toIntOrNull() ?: 0

        if (recipientUid.isEmpty()) {
            etUsername.error = "Please enter recipient UID"
            return
        }

        if (coinsToSend <= 0) {
            etCoins.error = "Enter a valid amount"
            return
        }

        if (recipientUid == senderId) {
            Toast.makeText(requireContext(), "You cannot send coins to yourself", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        // Step 1: Load sender coins
        db.child("users").child(senderId).child("coins").get()
            .addOnSuccessListener { snap ->
                val senderCoins = snap.getValue(Int::class.java) ?: 0
                if (senderCoins < coinsToSend) {
                    showLoading(false)
                    Toast.makeText(requireContext(), "Not enough coins", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Step 2: Load recipient coins
                db.child("users").child(recipientUid).child("coins").get()
                    .addOnSuccessListener { recvSnap ->
                        if (!recvSnap.exists()) {
                            showLoading(false)
                            etUsername.error = "Recipient not found"
                            return@addOnSuccessListener
                        }

                        val receiverCoins = recvSnap.getValue(Int::class.java) ?: 0

                        val updates = hashMapOf<String, Any>(
                            "users/$senderId/coins" to (senderCoins - coinsToSend),
                            "users/$recipientUid/coins" to (receiverCoins + coinsToSend)
                        )

                        val txId = db.child("transactions").push().key!!
                        val timestamp = System.currentTimeMillis()
                        updates["transactions/$txId"] = mapOf(
                            "from" to senderId,
                            "to" to recipientUid,
                            "coins" to coinsToSend,
                            "timestamp" to timestamp,
                            "type" to "transfer",
                            "status" to "completed"
                        )
                        db.updateChildren(updates)
                            .addOnSuccessListener {
                                // 🔔 Send notification to receiver
                                sendCoinNotification(senderId, recipientUid, coinsToSend)

                                showLoading(false)
                                Toast.makeText(requireContext(), "Coins sent!", Toast.LENGTH_SHORT).show()
                                etUsername.text?.clear()
                                etCoins.text?.clear()
                            }

                    }
                    .addOnFailureListener {
                        showLoading(false)
                        Toast.makeText(requireContext(), "Recipient not found", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(requireContext(), "Failed to get balance", Toast.LENGTH_SHORT).show()
            }
    }

    private fun processTransfer(senderId: String, receiverId: String, coins: Int) {
        // Step 2: Load sender coins
        db.child("users").child(senderId).child("coins").get()
            .addOnSuccessListener { snap ->
                val senderCoins = snap.getValue(Int::class.java) ?: 0

                if (senderCoins < coins) {
                    showLoading(false)
                    Toast.makeText(requireContext(), "Not enough coins. You have $senderCoins coins", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val updates = HashMap<String, Any>()
                val newSenderCoins = senderCoins - coins

                // Step 3: Load receiver coins
                db.child("users").child(receiverId).child("coins").get()
                    .addOnSuccessListener { recvSnap ->
                        val receiverCoins = recvSnap.getValue(Int::class.java) ?: 0
                        val newReceiverCoins = receiverCoins + coins

                        // Update both
                        updates["users/$senderId/coins"] = newSenderCoins
                        updates["users/$receiverId/coins"] = newReceiverCoins

                        // Add transaction history
                        val transactionId = db.child("transactions").push().key!!
                        updates["transactions/$transactionId"] = mapOf(
                            "from" to senderId,
                            "to" to receiverId,
                            "coins" to coins,
                            "type" to "transfer",
                            "timestamp" to System.currentTimeMillis(),
                            "status" to "completed"
                        )

                        // Step 4: atomic update
                        db.updateChildren(updates)
                            .addOnSuccessListener {
                                showLoading(false)
                                Toast.makeText(requireContext(), "✅ Coins sent successfully!", Toast.LENGTH_SHORT).show()

                                // Clear fields
                                etUsername.text?.clear()
                                etCoins.text?.clear()

                                // Update balance display
                                tvCurrentBalance.text = "$newSenderCoins coins"

                                // Show confirmation message
                                showSuccessMessage("$coins coins transferred successfully!")
                            }
                            .addOnFailureListener { error ->
                                showLoading(false)
                                Toast.makeText(requireContext(), "Transfer failed: ${error.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener {
                        showLoading(false)
                        Toast.makeText(requireContext(), "Failed to get receiver info", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(requireContext(), "Failed to get your balance", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSend.isEnabled = !isLoading
        btnSend.text = if (isLoading) "Processing..." else "Send Coins"
    }

    private fun showSuccessMessage(message: String) {
        // You can implement a more beautiful success dialog here
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        loadCurrentBalance() // Refresh balance when fragment resumes
    }
}