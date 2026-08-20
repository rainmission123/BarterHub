package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.barterhub.R
import com.example.barterhub.billing.CoinProduct
import com.example.barterhub.billing.PlayBillingManager
import com.example.barterhub.billing.PlayPurchaseVerifier
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.Firebase
import com.google.firebase.functions.functions

@SuppressLint("SetTextI18n")
class BuyCoinsDialog(private val onCoinsUpdated: (() -> Unit)? = null) : DialogFragment() {

    private lateinit var functions: FirebaseFunctions
    private lateinit var purchaseVerifier: PlayPurchaseVerifier
    private var billingManager: PlayBillingManager? = null

    private var selectedCoins = 0
    private var selectedProductId = ""
    private var paymentListener: ValueEventListener? = null
    private var paymentBalanceRef: DatabaseReference? = null
    private var walletCoinsBeforePayment: Int? = null
    private var latestWalletBalance: Int? = null
    private var paymentSuccessHandled = false
    private var isPurchaseInProgress = false
    private var billingReady = false

    private val productsById = mutableMapOf<String, CoinProduct>()
    private val verifyingPurchaseTokens = mutableSetOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var btnConfirmBuy: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var cardCoins100: LinearLayout
    private lateinit var cardCoins200: LinearLayout
    private lateinit var cardCoins500: LinearLayout
    private lateinit var rbCoins100: RadioButton
    private lateinit var rbCoins200: RadioButton
    private lateinit var rbCoins500: RadioButton
    private lateinit var tvCoins100Price: TextView
    private lateinit var tvCoins200Price: TextView
    private lateinit var tvCoins500Price: TextView

    companion object {
        private const val TAG = "BuyCoinsDialog"
        private const val MAX_VERIFICATION_RETRIES = 3
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val rootView = layoutInflater.inflate(R.layout.dialog_buy_coins_package, null)

        cardCoins100 = rootView.findViewById(R.id.cardCoins100)
        cardCoins200 = rootView.findViewById(R.id.cardCoins200)
        cardCoins500 = rootView.findViewById(R.id.cardCoins500)
        rbCoins100 = rootView.findViewById(R.id.rbCoins100)
        rbCoins200 = rootView.findViewById(R.id.rbCoins200)
        rbCoins500 = rootView.findViewById(R.id.rbCoins500)
        tvCoins100Price = rootView.findViewById(R.id.tvCoins100Price)
        tvCoins200Price = rootView.findViewById(R.id.tvCoins200Price)
        tvCoins500Price = rootView.findViewById(R.id.tvCoins500Price)
        btnConfirmBuy = rootView.findViewById(R.id.btnConfirmBuyCoins)
        btnCancel = rootView.findViewById(R.id.btnCancelBuyCoins)

        functions = Firebase.functions("us-central1")
        purchaseVerifier = PlayPurchaseVerifier(functions)
        billingManager = createBillingManager()

        setupClickListeners()
        setupInitialSelection()
        listenForCoinUpdates()
        setPricesLoading()

        return AlertDialog.Builder(requireContext())
            .setView(rootView)
            .setCancelable(false)
            .create()
            .apply {
                setOnShowListener {
                    polishDialogWindow(this)
                    Log.d(TAG, "Buy Coins dialog shown")
                    billingManager?.start()
                }
            }
    }

    override fun onDestroy() {
        removePaymentBalanceListener()
        billingManager?.stop()
        billingManager = null
        verifyingPurchaseTokens.clear()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun createBillingManager(): PlayBillingManager {
        return PlayBillingManager(requireContext(), object : PlayBillingManager.Listener {
            override fun onProductsLoaded(products: List<CoinProduct>) {
                if (!isDialogActive()) return
                Log.d(TAG, "onBillingReady productCount=${products.size} ids=${products.map { it.productId }}")
                billingReady = true
                productsById.clear()
                products.forEach { product -> productsById[product.productId] = product }
                bindProductPrices()
                resetButtonState()
            }

            override fun onBillingUnavailable(message: String) {
                if (!isDialogActive()) return
                Log.w(TAG, "onBillingUnavailable message=$message")
                billingReady = false
                isPurchaseInProgress = false
                btnConfirmBuy.isEnabled = true
                btnConfirmBuy.text = "Retry Google Play"
                showToast(message)
            }

            override fun onPurchaseReady(productId: String, purchaseToken: String) {
                if (!isDialogActive()) return
                val coins = CoinProduct.coinsForProduct(productId) ?: selectedCoins
                selectedCoins = coins
                selectedProductId = productId
                verifyPurchase(productId, purchaseToken)
            }

            override fun onPurchasePending() {
                if (!isDialogActive()) return
                isPurchaseInProgress = false
                showToast("Payment is pending in Google Play. Coins will be added once payment completes.")
                resetButtonState()
            }

            override fun onPurchaseCancelled() {
                if (!isDialogActive()) return
                isPurchaseInProgress = false
                resetButtonState()
            }

            override fun onPurchaseError(message: String) {
                if (!isDialogActive()) return
                isPurchaseInProgress = false
                showToast(message)
                resetButtonState()
            }
        })
    }

    private fun setupInitialSelection() {
        selectCoinPackage(coins = 100)
    }

    private fun setupClickListeners() {
        cardCoins100.setOnClickListener { selectCoinPackage(coins = 100) }
        cardCoins200.setOnClickListener { selectCoinPackage(coins = 200) }
        cardCoins500.setOnClickListener { selectCoinPackage(coins = 500) }

        btnCancel.setOnClickListener { dismiss() }

        btnConfirmBuy.setOnClickListener {
            if (!billingReady) {
                btnConfirmBuy.isEnabled = false
                btnConfirmBuy.text = "Loading prices..."
                billingManager?.start()
                return@setOnClickListener
            }

            launchSelectedPurchase()
        }
    }

    private fun selectCoinPackage(coins: Int) {
        selectedCoins = coins
        selectedProductId = CoinProduct.forCoins(coins)?.productId.orEmpty()

        rbCoins100.isChecked = coins == 100
        rbCoins200.isChecked = coins == 200
        rbCoins500.isChecked = coins == 500

        cardCoins100.setBackgroundResource(
            if (coins == 100) R.drawable.bg_coin_dialog_option_selected else R.drawable.bg_coin_dialog_option
        )
        cardCoins200.setBackgroundResource(
            if (coins == 200) R.drawable.bg_coin_dialog_option_selected else R.drawable.bg_coin_dialog_option
        )
        cardCoins500.setBackgroundResource(
            if (coins == 500) R.drawable.bg_coin_dialog_option_selected else R.drawable.bg_coin_dialog_option
        )

        resetButtonState()
    }

    private fun launchSelectedPurchase() {
        if (isPurchaseInProgress) return

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            showToast("Please log in to continue")
            return
        }

        val product = productsById[selectedProductId]
        if (product?.details == null) {
            showToast("This coin package is currently unavailable from Google Play.")
            resetButtonState()
            return
        }

        if (!isDialogActive()) return

        isPurchaseInProgress = true
        walletCoinsBeforePayment = null
        latestWalletBalance = null
        paymentSuccessHandled = false
        listenForCoinBalance()

        btnConfirmBuy.isEnabled = false
        btnConfirmBuy.text = "Opening Google Play..."

        val launched = billingManager?.launchPurchase(requireActivity(), product) == true
        if (!launched) {
            isPurchaseInProgress = false
            resetButtonState()
        }
    }

    private fun verifyPurchase(
        productId: String,
        purchaseToken: String,
        retryCount: Int = 0
    ) {
        if (!verifyingPurchaseTokens.add(purchaseToken)) return
        if (!isDialogActive()) return

        btnConfirmBuy.isEnabled = false
        btnConfirmBuy.text = "Verifying purchase..."

        purchaseVerifier.verify(productId, purchaseToken) { result ->
            verifyingPurchaseTokens.remove(purchaseToken)
            if (!isDialogActive()) return@verify

            when {
                result.isSuccessful -> handleVerifiedPurchaseSuccess(result)
                result.status == PlayPurchaseVerifier.STATUS_PROCESSING && retryCount < MAX_VERIFICATION_RETRIES -> {
                    btnConfirmBuy.text = "Still verifying..."
                    mainHandler.postDelayed({
                        if (isDialogActive()) verifyPurchase(productId, purchaseToken, retryCount + 1)
                    }, 2_000L)
                }
                result.status == PlayPurchaseVerifier.STATUS_PENDING -> {
                    isPurchaseInProgress = false
                    showToast("Payment is pending in Google Play. Coins will be added once payment completes.")
                    resetButtonState()
                }
                result.status == PlayPurchaseVerifier.STATUS_PERMISSION_DENIED -> {
                    isPurchaseInProgress = false
                    showToast("Purchase could not be verified for this account.")
                    resetButtonState()
                }
                result.status == PlayPurchaseVerifier.STATUS_INVALID -> {
                    isPurchaseInProgress = false
                    showToast("Purchase could not be verified. Please try again.")
                    resetButtonState()
                }
                else -> {
                    isPurchaseInProgress = false
                    showToast("Purchase verification is temporarily unavailable. Please try again.")
                    resetButtonState()
                }
            }
        }
    }

    private fun handleVerifiedPurchaseSuccess(result: PlayPurchaseVerifier.VerificationResult) {
        isPurchaseInProgress = false
        latestWalletBalance = result.finalCoins.takeIf { it > 0 } ?: latestWalletBalance
        onCoinsUpdated?.invoke()

        if (!paymentSuccessHandled) {
            paymentSuccessHandled = true
            showPaymentSuccessDialog(result.transactionId)
            dismissAllowingStateLoss()
        }
    }

    private fun setPricesLoading() {
        tvCoins100Price.text = "Loading..."
        tvCoins200Price.text = "Loading..."
        tvCoins500Price.text = "Loading..."
        btnConfirmBuy.isEnabled = false
        btnConfirmBuy.text = "Loading prices..."
    }

    private fun bindProductPrices() {
        tvCoins100Price.text = productsById[CoinProduct.PRODUCT_100]?.formattedPrice ?: "Unavailable"
        tvCoins200Price.text = productsById[CoinProduct.PRODUCT_200]?.formattedPrice ?: "Unavailable"
        tvCoins500Price.text = productsById[CoinProduct.PRODUCT_500]?.formattedPrice ?: "Unavailable"
    }

    private fun selectedPriceLabel(): String {
        return productsById[selectedProductId]?.formattedPrice ?: "Google Play"
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

            override fun onCancelled(error: DatabaseError) = Unit
        })
    }

    private fun listenForCoinBalance() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        removePaymentBalanceListener()

        paymentBalanceRef = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(uid)
            .child("wallet")
            .child("coins")

        paymentListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val coins = snapshot.getValue(Int::class.java) ?: 0
                latestWalletBalance = coins
                val baselineCoins = walletCoinsBeforePayment

                if (baselineCoins == null) {
                    walletCoinsBeforePayment = coins
                    onCoinsUpdated?.invoke()
                    return
                }

                val expectedCoinsAfterPayment = baselineCoins + selectedCoins
                if (
                    coins >= expectedCoinsAfterPayment &&
                    isDialogActive() &&
                    !paymentSuccessHandled
                ) {
                    paymentSuccessHandled = true
                    showPaymentSuccessDialog()
                    dismissAllowingStateLoss()
                }

                onCoinsUpdated?.invoke()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Coin listener error: ${error.message}")
            }
        }

        paymentBalanceRef?.addValueEventListener(paymentListener!!)
    }

    private fun removePaymentBalanceListener() {
        val listener = paymentListener
        val ref = paymentBalanceRef
        if (listener != null && ref != null) {
            ref.removeEventListener(listener)
        }
        paymentListener = null
        paymentBalanceRef = null
    }

    private fun resetButtonState() {
        if (!::btnConfirmBuy.isInitialized) return

        val productAvailable = productsById[selectedProductId]?.details != null
        btnConfirmBuy.isEnabled = billingReady && productAvailable && !isPurchaseInProgress
        btnConfirmBuy.text = when {
            !billingReady -> "Loading prices..."
            !productAvailable -> "Unavailable"
            else -> "Buy Now - ${selectedPriceLabel()}"
        }
    }

    private fun showPaymentSuccessDialog(transactionId: String? = null) {
        if (!isDialogActive()) return

        val rootView = layoutInflater.inflate(R.layout.dialog_payment_success, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(rootView)
            .create()

        rootView.findViewById<TextView>(R.id.tvPaymentSuccessMessage).text =
            "$selectedCoins Coins added to your wallet"
        rootView.findViewById<TextView>(R.id.tvSuccessNewBalance).text =
            "${latestWalletBalance ?: selectedCoins} Coins"
        rootView.findViewById<TextView>(R.id.tvSuccessTransactionId).text =
            transactionId ?: "GOOGLE-PLAY"
        rootView.findViewById<MaterialButton>(R.id.btnPaymentSuccessOk).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnShowListener { polishDialogWindow(dialog) }
        dialog.show()
    }

    private fun polishDialogWindow(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.72f)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showToast(message: String) {
        if (isDialogActive()) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun isDialogActive(): Boolean {
        return isAdded && dialog != null
    }
}
