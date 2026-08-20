package com.example.barterhub.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.auth.FirebaseAuth
import java.security.MessageDigest

class PlayBillingManager(
    context: Context,
    private var listener: Listener?
) : PurchasesUpdatedListener {

    interface Listener {
        fun onProductsLoaded(products: List<CoinProduct>)
        fun onBillingUnavailable(message: String)
        fun onPurchaseReady(productId: String, purchaseToken: String)
        fun onPurchasePending()
        fun onPurchaseCancelled()
        fun onPurchaseError(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    private val handledPurchaseTokens = mutableSetOf<String>()
    private var connecting = false
    private var stopped = false

    fun start() {
        stopped = false
        Log.d(TAG, "Billing start isReady=${billingClient.isReady} connecting=$connecting")

        if (billingClient.isReady) {
            queryProductDetails()
            reconcilePurchases()
            return
        }

        if (connecting) return
        connecting = true

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                Log.d(TAG, "Billing setup responseCode=${result.responseCode} message=${result.debugMessage}")

                if (stopped) return

                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails()
                    reconcilePurchases()
                } else {
                    notifyUnavailable("Google Play Billing is unavailable. Please try again later.")
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
                Log.w(TAG, "Billing service disconnected")
                if (!stopped) {
                    mainHandler.postDelayed({ start() }, RECONNECT_DELAY_MS)
                }
            }
        })
    }

    fun stop() {
        stopped = true
        connecting = false
        listener = null
        handledPurchaseTokens.clear()
        mainHandler.removeCallbacksAndMessages(null)
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    fun launchPurchase(activity: Activity, product: CoinProduct): Boolean {
        val details = product.details ?: run {
            notifyPurchaseError("This coin package is currently unavailable.")
            return false
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) {
            notifyPurchaseError("Please log in to continue.")
            return false
        }

        if (!billingClient.isReady) {
            notifyPurchaseError("Google Play Billing is still connecting. Please try again.")
            start()
            return false
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .setObfuscatedAccountId(sha256Lowercase(uid))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        Log.d(TAG, "launchBillingFlow productId=${product.productId} responseCode=${result.responseCode} message=${result.debugMessage}")

        return when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> true
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                notifyCancelled()
                false
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                reconcilePurchases()
                true
            }
            else -> {
                notifyPurchaseError("Unable to open Google Play Billing. Please try again.")
                false
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        Log.d(TAG, "purchasesUpdated responseCode=${result.responseCode} message=${result.debugMessage}")

        if (stopped) return

        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handlePurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> notifyCancelled()
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> reconcilePurchases()
            else -> notifyPurchaseError("Google Play purchase was not completed. Please try again.")
        }
    }

    fun reconcilePurchases() {
        if (!billingClient.isReady || stopped) return

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            Log.d(TAG, "queryPurchasesAsync responseCode=${result.responseCode} message=${result.debugMessage} count=${purchases.size}")

            if (stopped) return@queryPurchasesAsync

            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            }
        }
    }

    private fun queryProductDetails() {
        if (!billingClient.isReady || stopped) return

        val requestedProducts = CoinProduct.supported.map { it.productId }
        Log.d(TAG, "Querying ProductDetails type=${BillingClient.ProductType.INAPP} ids=$requestedProducts")

        val queryProducts = CoinProduct.supported.map { product ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(product.productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(queryProducts)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsResult ->
            val productDetailsList = productDetailsResult.productDetailsList
            val returnedIds = productDetailsList.map { it.productId }
            Log.d(TAG, "ProductDetails responseCode=${result.responseCode} message=${result.debugMessage} count=${productDetailsList.size} ids=$returnedIds")

            if (stopped) return@queryProductDetailsAsync

            if (result.responseCode != BillingClient.BillingResponseCode.OK || productDetailsList.isEmpty()) {
                notifyUnavailable("Coin packages are currently unavailable from Google Play. Please try again later.")
                return@queryProductDetailsAsync
            }

            val detailsById = productDetailsList.associateBy { it.productId }
            val products = CoinProduct.supported.map { product ->
                product.copy(details = detailsById[product.productId])
            }.filter { it.details != null }

            if (products.isEmpty()) {
                notifyUnavailable("Coin packages are currently unavailable from Google Play. Please try again later.")
            } else {
                mainHandler.post { listener?.onProductsLoaded(products) }
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        purchases.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> handlePurchased(purchase)
                Purchase.PurchaseState.PENDING -> notifyPending()
                else -> Unit
            }
        }
    }

    private fun handlePurchased(purchase: Purchase) {
        val productId = purchase.products.firstOrNull { productId ->
            CoinProduct.coinsForProduct(productId) != null
        } ?: return

        val token = purchase.purchaseToken
        if (token.isBlank() || !handledPurchaseTokens.add(token)) return

        mainHandler.post {
            listener?.onPurchaseReady(productId, token)
        }
    }

    private fun notifyUnavailable(message: String) {
        mainHandler.post { listener?.onBillingUnavailable(message) }
    }

    private fun notifyPurchaseError(message: String) {
        mainHandler.post { listener?.onPurchaseError(message) }
    }

    private fun notifyPending() {
        mainHandler.post { listener?.onPurchasePending() }
    }

    private fun notifyCancelled() {
        mainHandler.post { listener?.onPurchaseCancelled() }
    }

    private fun sha256Lowercase(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val TAG = "PlayBillingManager"
        private const val RECONNECT_DELAY_MS = 1_500L
    }
}
