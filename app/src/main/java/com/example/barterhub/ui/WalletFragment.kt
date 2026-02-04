package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.adapters.TransactionAdapter
import com.example.barterhub.data.models.TransactionModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class WalletFragment : Fragment() {
    private lateinit var tvUserUID: TextView
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var coinPrice: TextView
    private lateinit var coinBalance: TextView
    private lateinit var btnBuyCoins: MaterialButton
    private lateinit var btnSendCoins: MaterialButton
    private lateinit var rvTransactions: RecyclerView
    private lateinit var btnHowToEarn: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_wallet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        initializeViews(view)

        val currentUser = auth.currentUser
        val fullUID = currentUser?.uid ?: "N/A"

        // 1️⃣ Shortened UID for display
        val displayUID = if (fullUID.length > 10) {
            "${fullUID.take(6)}...${fullUID.takeLast(4)}"
        } else {
            fullUID
        }
        tvUserUID.text = "UID: $displayUID"

        // 2️⃣ Copy full UID on click
        val ivCopyUID: ImageView = view.findViewById(R.id.ivCopyUID)
        ivCopyUID.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("UID", fullUID)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "UID copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        setupClickListeners()
        loadCoinBalance()
        setupRealTimeBalanceListener()
        loadTransactionHistory()
    }

    private fun initializeViews(view: View) {
        coinBalance = view.findViewById(R.id.tvCoinsBalance)
        coinPrice = view.findViewById(R.id.tvCoinsPrice)
        btnBuyCoins = view.findViewById(R.id.btnBuyCoins)
        btnSendCoins = view.findViewById(R.id.btnSendCoins)
        rvTransactions = view.findViewById(R.id.rvTransactions)
        btnHowToEarn = view.findViewById(R.id.btnHowToEarn)
        rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        tvUserUID = view.findViewById(R.id.tvUserUID)
    }

    private fun setupClickListeners() {
        btnBuyCoins.setOnClickListener { showBuyCoinsDialog() }

        btnHowToEarn.setOnClickListener {
            findNavController().navigate(R.id.action_walletFragment_to_howToEarnFragment)
        }

        btnSendCoins.setOnClickListener { navigateToSendCoinsFragment() }
    }

    private fun navigateToSendCoinsFragment() {
        val currentUser = auth.currentUser ?: return

        database.getReference("users").child(currentUser.uid).child("coins")
            .get()
            .addOnSuccessListener { snapshot ->
                val coins = snapshot.getValue(Int::class.java) ?: 0
                if (coins > 0) {
                    try {
                        findNavController().navigate(R.id.action_walletFragment_to_sendCoinsFragment)
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Cannot open Send Coins", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    showNoCoinsDialog()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to check balance", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showNoCoinsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("No Coins Available")
            .setMessage("You don't have any coins to send. Earn coins by playing games or buying coins.")
            .setPositiveButton("Buy Coins") { dialog, _ ->
                dialog.dismiss()
                showBuyCoinsDialog()
            }
            .setNegativeButton("Earn Coins") { dialog, _ ->
                dialog.dismiss()
                findNavController().navigate(R.id.action_walletFragment_to_howToEarnFragment)
            }
            .setNeutralButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showBuyCoinsDialog() {
        val buyCoinsDialog = BuyCoinsDialog {
            loadCoinBalance()
            loadTransactionHistory()
        }
        buyCoinsDialog.show(parentFragmentManager, "BuyCoinsDialog")
    }

    private fun loadCoinBalance() {
        val uid = auth.currentUser?.uid ?: return

        database.getReference("users").child(uid).child("coins")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                @SuppressLint("SetTextI18n")
                override fun onDataChange(snapshot: DataSnapshot) {
                    val coins = snapshot.getValue(Int::class.java) ?: 0
                    coinBalance.text = coins.toString()

                    val totalPhp = coins * 0.50
                    coinPrice.text = "₱${String.format("%.2f", totalPhp)}"
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Failed to load coin balance", Toast.LENGTH_SHORT).show()
                    coinBalance.text = "0"
                    coinPrice.text = "₱0.00"
                }
            })
    }

    private fun setupRealTimeBalanceListener() {
        val uid = auth.currentUser?.uid ?: return

        database.getReference("users").child(uid).child("coins")
            .addValueEventListener(object : ValueEventListener {
                @SuppressLint("SetTextI18n")
                override fun onDataChange(snapshot: DataSnapshot) {
                    val coins = snapshot.getValue(Int::class.java) ?: 0
                    coinBalance.text = coins.toString()
                    val totalPhp = coins * 0.50
                    coinPrice.text = "₱${String.format("%.2f", totalPhp)}"

                    // Enable/Disable Send Coins button
                    btnSendCoins.isEnabled = coins > 0
                    btnSendCoins.alpha = if (coins > 0) 1.0f else 0.5f

                    // Reload transactions automatically
                    loadTransactionHistory()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadTransactionHistory() {
        val uid = auth.currentUser?.uid ?: return
        val ref = database.getReference("transactions")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val transactionList = snapshot.children.mapNotNull { ts ->
                    val type = ts.child("type").getValue(String::class.java) ?: "purchase"
                    val coins = ts.child("coins").getValue(Int::class.java) ?: 0
                    val timestamp = ts.child("timestamp").getValue(Long::class.java) ?: 0L
                    val status = ts.child("status").getValue(String::class.java) ?: "completed"
                    val from = ts.child("from").getValue(String::class.java)
                    val to = ts.child("to").getValue(String::class.java)
                    val userId = ts.child("userId").getValue(String::class.java)

                    if (userId == uid || from == uid || to == uid) {
                        val date = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm")
                            .format(java.util.Date(timestamp))

                        TransactionModel(
                            title = when(type) {
                                "transfer" -> if (from == uid) "Sent Coins" else "Received Coins"
                                else -> "Purchase Coins"
                            },
                            amount = if (type == "transfer" && from == uid) -coins.toDouble() else coins.toDouble(),
                            date = date,
                            status = status
                        )
                    } else null
                }.sortedByDescending { it.date }

                rvTransactions.adapter = TransactionAdapter(transactionList)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error loading transactions: ${error.message}")
            }
        })
    }

    private fun transferCoins(senderUid: String, receiverUid: String, amount: Int) {
        if (amount <= 0) return

        val senderRef = database.getReference("users").child(senderUid).child("coins")
        val receiverRef = database.getReference("users").child(receiverUid).child("coins")

        senderRef.get().addOnSuccessListener { snapshot ->
            val senderBalance = snapshot.getValue(Int::class.java) ?: 0
            if (senderBalance >= amount) {
                // Update sender balance
                senderRef.setValue(senderBalance - amount)

                // Update receiver balance
                receiverRef.get().addOnSuccessListener { rSnap ->
                    val receiverBalance = rSnap.getValue(Int::class.java) ?: 0
                    receiverRef.setValue(receiverBalance + amount)

                    // Save transaction for sender
                    saveTransaction("transfer", amount, senderUid, receiverUid)

                    // Send notification
                    sendCoinNotification(senderUid, receiverUid, amount)

                    Toast.makeText(requireContext(), "Coins sent successfully", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Insufficient balance", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveTransaction(type: String, coins: Int, fromUid: String, toUid: String) {
        val ref = database.getReference("transactions").push()

        val data = mapOf(
            "type" to type,
            "coins" to coins,
            "timestamp" to System.currentTimeMillis(),
            "from" to fromUid,
            "to" to toUid,
            "userId" to fromUid,
            "status" to "completed"
        )

        ref.setValue(data)
    }

    private fun sendCoinNotification(senderUid: String, receiverUid: String, coins: Int) {
        val ref = database.getReference("notifications").child(receiverUid).push()

        val data = mapOf(
            "fromUserId" to senderUid,
            "type" to "coins_received",
            "coins" to coins,
            "timestamp" to System.currentTimeMillis(),
            "read" to false
        )

        ref.setValue(data).addOnSuccessListener {
            Log.d(TAG, "Notification sent to $receiverUid")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to send notification", e)
        }
    }

    override fun onResume() {
        super.onResume()
        loadCoinBalance()
        loadTransactionHistory()
    }

    companion object {
        private const val TAG = "WalletFragment"
    }
}
