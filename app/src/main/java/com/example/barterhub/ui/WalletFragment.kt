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

    private var balanceListener: ValueEventListener? = null
    private var balanceRef: DatabaseReference? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_wallet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        initializeViews(view)
        setupUIDDisplay(view)
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
        tvUserUID = view.findViewById(R.id.tvUserUID)

        rvTransactions.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupUIDDisplay(view: View) {
        val currentUser = auth.currentUser ?: return

        database.getReference("users")
            .child(currentUser.uid)
            .child("username")
            .get()
            .addOnSuccessListener { snapshot ->
                val username = snapshot.getValue(String::class.java) ?: "unknown_user"

                tvUserUID.text = "@$username"

                val ivCopyUID: ImageView = view.findViewById(R.id.ivCopyUID)
                ivCopyUID.setOnClickListener {
                    val clipboard =
                        requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Username", username)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "Username copied", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                tvUserUID.text = "Username: error"
            }
    }

    private fun setupClickListeners() {
        btnBuyCoins.setOnClickListener {
            showBuyCoinsDialog()
        }

        btnSendCoins.setOnClickListener {
            navigateToSendCoinsFragment()
        }
    }

    private fun getWalletCoinsRef(uid: String): DatabaseReference {
        return database.getReference("users")
            .child(uid)
            .child("wallet")
            .child("coins")
    }

    private fun navigateToSendCoinsFragment() {
        val currentUser = auth.currentUser ?: return

        getWalletCoinsRef(currentUser.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val coins = snapshot.getValue(Int::class.java) ?: 0

                if (coins > 0) {
                    try {
                        findNavController().navigate(R.id.action_walletFragment_to_sendCoinsFragment)
                    } catch (_: Exception) {
                        Toast.makeText(
                            requireContext(),
                            "Cannot open Send Coins",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    showNoCoinsDialog()
                }
            }
            .addOnFailureListener {
                Toast.makeText(
                    requireContext(),
                    "Failed to check balance",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showNoCoinsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("No Coins Available")
            .setMessage("You don't have any coins to send. Earn coins by posting items, playing games, or buying coins.")
            .setPositiveButton("Buy Coins") { dialog, _ ->
                dialog.dismiss()
                showBuyCoinsDialog()
            }
            .setNeutralButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
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

        getWalletCoinsRef(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                @SuppressLint("SetTextI18n")
                override fun onDataChange(snapshot: DataSnapshot) {
                    val coins = snapshot.getValue(Int::class.java) ?: 0
                    updateCoinUI(coins)
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to load coin balance",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateCoinUI(0)
                }
            })
    }

    private fun setupRealTimeBalanceListener() {
        val uid = auth.currentUser?.uid ?: return

        balanceRef = getWalletCoinsRef(uid)

        balanceListener = object : ValueEventListener {
            @SuppressLint("SetTextI18n")
            override fun onDataChange(snapshot: DataSnapshot) {
                val coins = snapshot.getValue(Int::class.java) ?: 0
                updateCoinUI(coins)
                loadTransactionHistory()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Balance listener cancelled: ${error.message}")
            }
        }

        balanceRef?.addValueEventListener(balanceListener!!)
    }

    @SuppressLint("SetTextI18n")
    private fun updateCoinUI(coins: Int) {
        coinBalance.text = coins.toString()

        val totalPhp = coins * 0.50
        coinPrice.text = "₱${String.format("%.2f", totalPhp)}"

        btnSendCoins.isEnabled = coins > 0
        btnSendCoins.alpha = if (coins > 0) 1.0f else 0.5f
    }

    private fun loadTransactionHistory() {
        val uid = auth.currentUser?.uid ?: return

        val ref = database.getReference("coin_transactions")
            .child(uid)

        ref.orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val transactionList = parseTransactionSnapshot(snapshot)

                    if (transactionList.isEmpty()) {
                        loadLegacyTransactionHistory(uid)
                    } else {
                        bindTransactionHistoryWithPaymentMethods(transactionList)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error loading transactions: ${error.message}")
                }
            })
    }

    private fun loadLegacyTransactionHistory(uid: String) {
        database.getReference("transactions")
            .child(uid)
            .orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    bindTransactionHistoryWithPaymentMethods(parseTransactionSnapshot(snapshot))
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error loading legacy transactions: ${error.message}")
                }
            })
    }

    private fun parseTransactionSnapshot(snapshot: DataSnapshot): List<TransactionModel> {
        return snapshot.children.mapNotNull { ts ->
            val type = ts.child("type").getValue(String::class.java) ?: "buy_coins"
            if (type.startsWith("game_", ignoreCase = true)) {
                return@mapNotNull null
            }
            val coins = ts.child("coins").asInt()
            val amount = ts.child("amount").asDouble()
            val timestamp = ts.child("timestamp").asLong()
            val status = ts.child("status").getValue(String::class.java) ?: "completed"
            val paymentId = ts.child("paymentId").getValue(String::class.java).orEmpty()
            val provider = ts.child("provider").getValue(String::class.java).orEmpty()
            val checkoutSessionId = ts.child("checkoutSessionId").getValue(String::class.java).orEmpty()
            val transactionId = ts.child("transactionId").getValue(String::class.java)
                ?.takeIf { it.isNotBlank() }
                ?: paymentId.takeIf { it.isNotBlank() }
                ?: ts.key.orEmpty()
            val rawReferenceNo = ts.child("referenceNo").getValue(String::class.java) ?: ""
            val paymentMethod = ts.child("paymentMethod").getValue(String::class.java)
                ?: ts.child("method").getValue(String::class.java)
                ?: ""
            val walletBalanceAfter =
                ts.child("walletBalanceAfter").asIntOrNull()
                    ?: ts.child("balanceAfter").asIntOrNull()
                    ?: ts.child("finalCoins").asIntOrNull()
            val fromName = ts.child("fromName").getValue(String::class.java) ?: ""
            val toName = ts.child("toName").getValue(String::class.java) ?: ""
            val titleFromDb = ts.child("title").getValue(String::class.java)

            val date = java.text.SimpleDateFormat(
                "MMM dd, yyyy HH:mm",
                java.util.Locale.getDefault()
            ).format(java.util.Date(timestamp))

            val title = when (type) {
                "buy_coins", "purchase" -> "Buy Coins"
                "cash-in" -> "Cash In"
                "send" -> "Sent Coins"
                "receive" -> "Received Coins"
                "post_reward" -> "Post Reward"
                else -> titleFromDb ?: "Transaction"
            }

            val coinAmount = when {
                type == "send" && coins > 0 -> -coins
                else -> coins
            }

            TransactionModel(
                title = title,
                type = type,
                amount = amount,
                coins = coinAmount,
                date = date,
                status = status,
                transactionId = formatDisplayTransactionId(
                    transactionId = transactionId,
                    referenceNo = rawReferenceNo,
                    timestamp = timestamp
                ),
                referenceNo = normalizeReferenceNo(rawReferenceNo, transactionId),
                paymentMethod = paymentMethod,
                paymentId = paymentId,
                provider = provider,
                checkoutSessionId = checkoutSessionId,
                walletBalanceAfter = walletBalanceAfter,
                timestamp = timestamp,
                fromName = fromName,
                toName = toName
            )
        }.sortedByDescending { it.timestamp }
    }

    private fun formatDisplayTransactionId(
        transactionId: String,
        referenceNo: String,
        timestamp: Long
    ): String {
        val trimmedTransactionId = transactionId.trim()
        if (
            trimmedTransactionId.startsWith("BH-TXN-", ignoreCase = true) ||
            !trimmedTransactionId.startsWith("-")
        ) {
            return trimmedTransactionId
        }

        val datePart = if (timestamp > 0L) {
            java.text.SimpleDateFormat(
                "yyyyMMdd",
                java.util.Locale.getDefault()
            ).format(java.util.Date(timestamp))
        } else {
            java.text.SimpleDateFormat(
                "yyyyMMdd",
                java.util.Locale.getDefault()
            ).format(java.util.Date())
        }

        val suffix = referenceNo
            .substringAfterLast("-", missingDelimiterValue = "")
            .filter { it.isLetterOrDigit() }
            .takeIf { it.isNotBlank() }
            ?: trimmedTransactionId
                .filter { it.isLetterOrDigit() }
                .takeLast(6)
                .uppercase(java.util.Locale.getDefault())

        return "BH-TXN-$datePart-${suffix.uppercase(java.util.Locale.getDefault())}"
    }

    private fun bindTransactionHistoryWithPaymentMethods(
        transactionList: List<TransactionModel>
    ) {
        val missingPaymentMethods = transactionList.withIndex().filter { (_, transaction) ->
            transaction.paymentMethod.isBlank() && transaction.paymentId.isNotBlank()
        }

        if (missingPaymentMethods.isEmpty()) {
            bindTransactionHistory(
                transactionList.map { transaction ->
                    transaction.withPayMongoMethodFallback()
                }
            )
            return
        }

        val enrichedTransactions = transactionList.toMutableList()
        var pendingLookups = missingPaymentMethods.size

        fun finishLookupIfReady() {
            pendingLookups -= 1
            if (pendingLookups == 0) {
                bindTransactionHistory(
                    enrichedTransactions.map { transaction ->
                        transaction.withPayMongoMethodFallback()
                    }
                )
            }
        }

        missingPaymentMethods.forEach { (index, transaction) ->
            database.getReference("coin_payments")
                .child(transaction.paymentId)
                .child("paymentMethod")
                .get()
                .addOnSuccessListener { snapshot ->
                    val paymentMethod = snapshot.getValue(String::class.java).orEmpty()
                    if (paymentMethod.isNotBlank()) {
                        enrichedTransactions[index] = transaction.copy(
                            paymentMethod = paymentMethod
                        )
                    }
                    finishLookupIfReady()
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Error loading payment method: ${error.message}")
                    finishLookupIfReady()
                }
        }
    }

    private fun TransactionModel.withPayMongoMethodFallback(): TransactionModel {
        if (paymentMethod.isNotBlank()) return this

        if (provider.equals("google_play", ignoreCase = true)) {
            return copy(paymentMethod = "google_play")
        }

        val isPayMongoTransaction = provider.equals("paymongo", ignoreCase = true) ||
            paymentId.isNotBlank() ||
            checkoutSessionId.isNotBlank()

        return if (isPayMongoTransaction) {
            copy(paymentMethod = "paymongo_checkout")
        } else {
            this
        }
    }

    private fun bindTransactionHistory(transactionList: List<TransactionModel>) {
        rvTransactions.adapter = TransactionAdapter(transactionList) { transaction ->
            val bundle = Bundle().apply {
                putSerializable("transaction", transaction)
            }

            findNavController().navigate(
                R.id.action_walletFragment_to_transactionReceiptFragment,
                bundle
            )
        }
    }

    private fun DataSnapshot.asInt(): Int = asIntOrNull() ?: 0

    private fun DataSnapshot.asIntOrNull(): Int? {
        return when (val rawValue = value) {
            is Number -> rawValue.toInt()
            is String -> rawValue.toIntOrNull()
            else -> null
        }
    }

    private fun DataSnapshot.asLong(): Long {
        return when (val rawValue = value) {
            is Number -> rawValue.toLong()
            is String -> rawValue.toLongOrNull()
            else -> null
        } ?: 0L
    }

    private fun DataSnapshot.asDouble(): Double {
        return when (val rawValue = value) {
            is Number -> rawValue.toDouble()
            is String -> rawValue.toDoubleOrNull()
            else -> null
        } ?: 0.0
    }

    private fun normalizeReferenceNo(referenceNo: String, transactionId: String): String {
        val trimmedReferenceNo = referenceNo.trim()
        val trimmedTransactionId = transactionId.trim()

        if (trimmedReferenceNo.isBlank()) return ""

        val isPayMongoPaymentId = trimmedReferenceNo.startsWith(
            prefix = "pay_",
            ignoreCase = true
        )
        val isSameAsTransactionId = trimmedReferenceNo.equals(
            trimmedTransactionId,
            ignoreCase = true
        )

        return if (isPayMongoPaymentId || isSameAsTransactionId) {
            ""
        } else {
            trimmedReferenceNo
        }
    }

    override fun onResume() {
        super.onResume()
        loadCoinBalance()
        loadTransactionHistory()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        if (balanceListener != null && balanceRef != null) {
            balanceRef?.removeEventListener(balanceListener!!)
        }

        balanceListener = null
        balanceRef = null
    }

    companion object {
        private const val TAG = "WalletFragment"
    }
}
