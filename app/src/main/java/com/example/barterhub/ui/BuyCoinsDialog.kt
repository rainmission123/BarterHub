package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.barterhub.network.PayMongoApiService
import com.example.barterhub.network.PayMongoRequest
import com.example.barterhub.network.PayMongoResponse
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

@SuppressLint("SetTextI18n")
class BuyCoinsDialog(private val onCoinsUpdated: (() -> Unit)? = null) : DialogFragment() {

    private lateinit var functions: FirebaseFunctions
    private var selectedCoins = 0
    private var selectedPrice = 0.0
    private var paymentListener: ValueEventListener? = null
    private var isTransactionSaved = false
    private lateinit var packageOptions: RadioGroup
    private lateinit var btnConfirmBuy: MaterialButton
    private lateinit var btnCancel: MaterialButton

    companion object {
        private const val TAG = "BuyCoinsDialog"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        // Root Layout
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        // Title
        val title = TextView(context).apply {
            text = "Buy Barter Coins"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(title)

        // RadioGroup
        val radioGroup = RadioGroup(context)
        val rb100 = RadioButton(context).apply {
            text = "100 Coins - ₱50"
            setPadding(24, 24, 24, 24)
        }

        val rb200 = RadioButton(context).apply {
            text = "200 Coins - ₱100"
            setPadding(24, 24, 24, 24)
        }

        val rb500 = RadioButton(context).apply {
            text = "500 Coins - ₱250 (Best Value 🔥)"
            setPadding(24, 24, 24, 24)
        }

        radioGroup.addView(rb100)
        radioGroup.addView(rb200)
        radioGroup.addView(rb500)
        rootLayout.addView(radioGroup)

        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val paramsCancel = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = 16 } // spacing between buttons
        val btnCancel = MaterialButton(context).apply { text = "Cancel" }
        val btnBuy = MaterialButton(context).apply { text = "Buy Now" }

        buttonLayout.addView(btnCancel, paramsCancel)
        buttonLayout.addView(btnBuy)
        rootLayout.addView(buttonLayout)


        // Assign to class variables
        packageOptions = radioGroup
        this.btnConfirmBuy = btnBuy
        this.btnCancel = btnCancel

        // Setup listeners
        setupClickListeners()
        setupInitialSelection()

        functions = Firebase.functions
        listenForCoinUpdates()

        return AlertDialog.Builder(context)
            .setView(rootLayout)
            .setCancelable(false)
            .create()
    }

    override fun onDestroy() {
        super.onDestroy()
        paymentListener?.let {
            FirebaseDatabase.getInstance().getReference("coin_payments")
                .removeEventListener(it)
        }
    }

    private fun setupInitialSelection() {
        packageOptions.check(packageOptions.getChildAt(0).id)
        updateSelectedPackage(packageOptions.getChildAt(0) as RadioButton)
    }

    private fun setupClickListeners() {
        packageOptions.setOnCheckedChangeListener { group, checkedId ->
            val rb = group.findViewById<RadioButton>(checkedId)
            updateSelectedPackage(rb)
        }

        btnCancel.setOnClickListener { dismiss() }

        btnConfirmBuy.setOnClickListener {
            if (selectedCoins > 0) {
                showPaymentOptionsDialog()
            } else {
                showToast("Please select a coin package")
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun updateSelectedPackage(rb: RadioButton) {
        when (rb.text.toString()) {

            "100 Coins - ₱50" -> {
                selectedCoins = 100
                selectedPrice = 50.0
            }

            "200 Coins - ₱100" -> {
                selectedCoins = 200
                selectedPrice = 100.0
            }

            "500 Coins - ₱250 (Best Value 🔥)" -> {
                selectedCoins = 500
                selectedPrice = 250.0
            }

            else -> {
                selectedCoins = 0
                selectedPrice = 0.0
            }
        }

        btnConfirmBuy.text = "Buy Now - ₱${String.format("%.2f", selectedPrice)}"
    }

    private fun showPaymentOptionsDialog() {
        val paymentMethods = listOf(
            "GCash" to "gcash",
            "GrabPay" to "grab_pay",
            "Credit / Debit Card" to "card"
        )
        AlertDialog.Builder(requireContext())
            .setTitle("Choose Payment Method")
            .setItems(paymentMethods.map { it.first }.toTypedArray()) { _, which ->
                createPayMongoPayment(paymentMethods[which].second)
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun createPayMongoPayment(paymentMethod: String) {
        if (!isAdded) return
        btnConfirmBuy.isEnabled = false
        btnConfirmBuy.text = "Processing..."

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            showToast("Please log in to continue")
            resetButtonState()
            return
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://barterhub-server.onrender.com")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(PayMongoApiService::class.java)
        val request = PayMongoRequest(
            packageId = getSelectedPackageId(),
            amount = (selectedPrice * 100).toInt(),
            paymentMethod = paymentMethod,
            userId = currentUser.uid,
            coins = selectedCoins,
            currency = "PHP"
        )

        currentUser.getIdToken(false)
            .addOnSuccessListener { tokenResult ->
                val authHeader = "Bearer ${tokenResult.token.orEmpty()}"
                service.createCheckoutSession(authHeader, request).enqueue(object : Callback<PayMongoResponse> {
                    override fun onResponse(call: Call<PayMongoResponse>, response: Response<PayMongoResponse>) {
                        if (!isAdded) return
                        val checkoutUrl = response.body()?.checkout_url
                        if (!checkoutUrl.isNullOrEmpty()) startPaymentFlow(checkoutUrl, "payment_${System.currentTimeMillis()}")
                        else { showToast("Payment setup failed"); resetButtonState() }
                    }

                    override fun onFailure(call: Call<PayMongoResponse>, t: Throwable) {
                        if (!isAdded) return
                        showToast("Network error: ${t.message}")
                        resetButtonState()
                    }
                })
            }
            .addOnFailureListener { error ->
                if (!isAdded) return@addOnFailureListener
                showToast("Authentication error: ${error.message}")
                resetButtonState()
            }
    }

    private fun getSelectedPackageId(): String {
        return when (selectedCoins) {
            100 -> "coin_100"
            200 -> "coin_200"
            500 -> "coin_500"
            else -> ""
        }
    }

    private fun startPaymentFlow(redirectUrl: String, paymentIntentId: String) {
        listenForCoinBalance()
        savePendingTransaction(paymentIntentId, "paymongo")

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(redirectUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(requireContext().packageManager) != null) startActivity(intent)
            else showUrlFallbackDialog(redirectUrl)
        } catch (e: Exception) {
            showToast("Error starting payment. Please try again.")
            resetButtonState()
        }
    }

    private fun listenForCoinUpdates() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(uid)
            .child("wallet")
            .child("coins")

        db.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onCoinsUpdated?.invoke()
            }

            override fun onCancelled(error: DatabaseError) { }
        })
    }

    private fun showUrlFallbackDialog(redirectUrl: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Payment URL")
            .setMessage("Open this URL in browser:\n\n$redirectUrl")
            .setPositiveButton("Copy URL") { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Payment URL", redirectUrl))
                showToast("URL copied")
                resetButtonState()
            }
            .setNegativeButton("Cancel") { _, _ -> resetButtonState() }
            .show()
    }

    private fun savePendingTransaction(paymentIntentId: String, method: String) { /*... same as before ...*/ }

    private fun listenForCoinBalance() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val db = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(uid)
            .child("wallet")
            .child("coins")

        db.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val coins = snapshot.getValue(Int::class.java) ?: 0

                if (coins >= selectedCoins && isAdded && !isTransactionSaved) {
                    isTransactionSaved = true

                    recordTransactionHistory(uid, selectedCoins, selectedPrice)

                    Toast.makeText(
                        requireContext(),
                        "Coins received: $selectedCoins",
                        Toast.LENGTH_SHORT
                    ).show()

                    dismissAllowingStateLoss()
                }

                onCoinsUpdated?.invoke()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Coin listener error: ${error.message}")
            }
        })
    }

    private fun recordTransactionHistory(uid: String, coins: Int, amount: Double) {
        val transactionRef = FirebaseDatabase.getInstance()
            .getReference("transactions")
            .push()

        val transactionId = "TXN${System.currentTimeMillis()}"
        val referenceNo = "REF${UUID.randomUUID().toString().take(8).uppercase()}"

        val data = hashMapOf<String, Any>(
            "userId" to uid,
            "type" to "purchase",
            "coins" to coins,
            "amount" to amount,
            "status" to "completed",
            "timestamp" to System.currentTimeMillis(),
            "currency" to "PHP",

            // ✅ FIX: IDs
            "transactionId" to transactionId,
            "referenceNo" to referenceNo,

            // optional display
            "fromName" to "System",
            "toName" to "You"
        )

        transactionRef.setValue(data).addOnSuccessListener {
            Log.d(TAG, "Transaction saved with ID: $transactionId")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to record transaction: ${e.message}")
        }
    }

    private fun resetButtonState() {
        btnConfirmBuy.isEnabled = true
        btnConfirmBuy.text = "Buy Now - ₱${String.format("%.2f", selectedPrice)}"
    }

    private fun showToast(message: String) {
        if (isAdded) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
