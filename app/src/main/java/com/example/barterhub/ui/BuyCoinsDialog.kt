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
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

@SuppressLint("SetTextI18n")
class BuyCoinsDialog(private val onCoinsUpdated: (() -> Unit)? = null) : DialogFragment() {

    private lateinit var functions: FirebaseFunctions
    private var selectedCoins = 0
    private var selectedPrice = 0.0
    private var paymentListener: ValueEventListener? = null

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
        val rb20 = RadioButton(context).apply {
            text = "20 Coins - ₱10"
            setPadding(24, 24, 24, 24)
        }
        val rb50 = RadioButton(context).apply {
            text = "50 Coins - ₱25"
            setPadding(24, 24, 24, 24)
        }
        val rb120 = RadioButton(context).apply {
            text = "120 Coins - ₱60 (Best Value!)"
            setPadding(24, 24, 24, 24)
        }
        radioGroup.addView(rb20)
        radioGroup.addView(rb50)
        radioGroup.addView(rb120)
        rootLayout.addView(radioGroup)

// Buttons
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
            "20 Coins - ₱10" -> { selectedCoins = 20; selectedPrice = 10.0 }
            "50 Coins - ₱25" -> { selectedCoins = 50; selectedPrice = 25.0 }
            "120 Coins - ₱60 (Best Value!)" -> { selectedCoins = 120; selectedPrice = 60.0 }
            else -> { selectedCoins = 0; selectedPrice = 0.0 }
        }
        btnConfirmBuy.text = "Buy Now - ₱${String.format("%.2f", selectedPrice)}"
    }

    private fun showPaymentOptionsDialog() {
        val paymentMethods = listOf("GCash" to "gcash")
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
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://barterhub-server.onrender.com")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(PayMongoApiService::class.java)
        val request = PayMongoRequest(
            amount = (selectedPrice * 100).toInt(),
            paymentMethod = paymentMethod,
            userId = currentUser.uid,
            coins = selectedCoins,
            currency = "PHP"
        )

        service.createCheckoutSession(request).enqueue(object : Callback<PayMongoResponse> {
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
        val db = FirebaseDatabase.getInstance().getReference("users").child(uid).child("coins")
        db.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { onCoinsUpdated?.invoke() }
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
        val db = FirebaseDatabase.getInstance().getReference("users").child(uid).child("coins")

        db.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val coins = snapshot.getValue(Int::class.java) ?: 0

                // Automatic dismiss kapag nadagdagan ang coins
                if (coins >= selectedCoins && isAdded) {
                    Toast.makeText(requireContext(), "Coins received: $coins", Toast.LENGTH_SHORT).show()
                    dismissAllowingStateLoss()
                }

                // Optional: i-update ang UI kung may callback
                onCoinsUpdated?.invoke()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Coin listener error: ${error.message}")
            }
        })
    }

    private fun addCoinsToUser(amount: Int) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance().getReference("users").child(uid)

        db.child("coins").get().addOnSuccessListener { snapshot ->
            val current = snapshot.getValue(Int::class.java) ?: 0
            val newBalance = current + amount

            db.child("coins").setValue(newBalance).addOnSuccessListener {
                recordTransactionHistory(uid, amount, selectedPrice)

                onCoinsUpdated?.invoke()
                showToast("Successfully added $amount coins!")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to update coins: ${e.message}")
                showToast("Error updating coins. Contact support.")
            }
        }
    }

    private fun recordTransactionHistory(uid: String, coins: Int, amount: Double) {
        val transactionRef = FirebaseDatabase.getInstance()
            .getReference("transactions")
            .push()

        val data = hashMapOf<String, Any>(
            "userId" to uid,
            "type" to "cashin",
            "coins" to coins,
            "amount" to amount,
            "status" to "completed",
            "createdAt" to System.currentTimeMillis(),
            "currency" to "PHP"
        )

        transactionRef.setValue(data).addOnSuccessListener {
            Log.d(TAG, "Cashin transaction recorded for user $uid: $coins coins")
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
