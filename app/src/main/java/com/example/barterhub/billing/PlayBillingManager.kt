package com.example.barterhub.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.auth.FirebaseAuth
import java.security.MessageDigest

class PlayBillingManager(
    context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val verifier: PlayPurchaseVerifier = PlayPurchaseVerifier(),
    private val listener: Listener
) : PurchasesUpdatedListener {

    interface Listener {
        fun onBillingReady(products: List<CoinProductDetails>) = Unit
        fun onBillingUnavailable() = Unit
        fun onPurchasePending(productId: String) = Unit
        fun onPurchaseProcessing(productId: String) = Unit
        fun onPurchaseVerified(result: PlayPurchaseVerifier.Result.Success) = Unit
        fun onPurchaseNeedsReconciliation(productId: String) = Unit
        fun onPurchaseCanceled() = Unit
        fun onPurchaseError() = Unit
    }

    data class CoinProductDetails(
        val product: CoinProduct,
        val productDetails: ProductDetails,
        val formattedPrice: String
    )

    private val appContext = context.applicationContext
    private val productDetailsById = mutableMapOf<String, ProductDetails>()
    private val submittedPurchaseTokens = mutableSetOf<String>()
    private val verificationAttempts = mutableMapOf<String, Int>()
    private val retryHandler = Handler(Looper.getMainLooper())

    private var connecting = false

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (billingClient.isReady || connecting) return

        connecting = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                connecting = false

                if (billingResult.responseCode == BillingResponseCode.OK) {
                    queryProductDetails()
                    reconcilePurchases()
                } else {
                    listener.onBillingUnavailable()
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
                listener.onBillingUnavailable()
            }
        })
    }

    fun stop() {
        retryHandler.removeCallbacksAndMessages(null)
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    fun queryProductDetails() {
        runWhenReady {
            val products = CoinProduct.all.map { product ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(product.productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(products)
                .build()

            billingClient.queryProductDetailsAsync(params) { billingResult, result ->
                if (billingResult.responseCode != BillingResponseCode.OK) {
                    listener.onBillingUnavailable()
                    return@queryProductDetailsAsync
                }

                productDetailsById.clear()
                result.productDetailsList.forEach { details ->
                    productDetailsById[details.productId] = details
                }

                listener.onBillingReady(
                    CoinProduct.all.mapNotNull { product ->
                        val details = productDetailsById[product.productId]
                            ?: return@mapNotNull null
                        val formattedPrice = details.oneTimePurchaseOfferDetailsList
                            ?.firstOrNull()
                            ?.formattedPrice
                            .orEmpty()

                        CoinProductDetails(
                            product = product,
                            productDetails = details,
                            formattedPrice = formattedPrice
                        )
                    }
                )
            }
        }
    }

    fun launchPurchase(activity: Activity, productId: String) {
        val user = auth.currentUser
        val productDetails = productDetailsById[productId]

        if (user == null || productDetails == null) {
            listener.onPurchaseError()
            return
        }

        val offerDetails = productDetails.oneTimePurchaseOfferDetailsList?.firstOrNull()
        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams
            .newBuilder()
            .setProductDetails(productDetails)

        val offerToken = offerDetails?.offerToken
        if (!offerToken.isNullOrBlank()) {
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .setObfuscatedAccountId(sha256Lowercase(user.uid))
            .build()

        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (billingResult.responseCode == BillingResponseCode.ITEM_ALREADY_OWNED) {
            reconcilePurchases()
        } else if (billingResult.responseCode != BillingResponseCode.OK) {
            listener.onPurchaseError()
        }
    }

    fun reconcilePurchases() {
        runWhenReady {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode != BillingResponseCode.OK) return@queryPurchasesAsync
                purchases.forEach(::handlePurchase)
            }
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> purchases.orEmpty().forEach(::handlePurchase)
            BillingResponseCode.USER_CANCELED -> listener.onPurchaseCanceled()
            BillingResponseCode.ITEM_ALREADY_OWNED -> reconcilePurchases()
            BillingResponseCode.SERVICE_DISCONNECTED -> start()
            else -> listener.onPurchaseError()
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        val productId = purchase.products.firstOrNull { product ->
            CoinProduct.fromProductId(product) != null
        } ?: return

        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> listener.onPurchasePending(productId)
            Purchase.PurchaseState.PURCHASED -> submitPurchasedToken(
                productId = productId,
                purchaseToken = purchase.purchaseToken
            )
        }
    }

    private fun submitPurchasedToken(productId: String, purchaseToken: String) {
        if (!submittedPurchaseTokens.add(purchaseToken)) return

        verifier.verify(productId, purchaseToken) { result ->
            when (result) {
                is PlayPurchaseVerifier.Result.Success -> {
                    listener.onPurchaseVerified(result)
                    if (result.needsReconciliation) {
                        listener.onPurchaseNeedsReconciliation(productId)
                    }
                }

                PlayPurchaseVerifier.Result.Processing -> {
                    listener.onPurchaseProcessing(productId)
                    retryVerification(productId, purchaseToken)
                }

                PlayPurchaseVerifier.Result.Pending -> listener.onPurchasePending(productId)
                is PlayPurchaseVerifier.Result.SafeError -> {
                    if (result.retryable) {
                        retryVerification(productId, purchaseToken)
                    } else {
                        listener.onPurchaseError()
                    }
                }
            }
        }
    }

    private fun retryVerification(productId: String, purchaseToken: String) {
        val attempts = verificationAttempts[purchaseToken] ?: 0
        if (attempts >= MAX_VERIFICATION_RETRIES) {
            listener.onPurchaseNeedsReconciliation(productId)
            return
        }

        val nextAttempt = attempts + 1
        verificationAttempts[purchaseToken] = nextAttempt

        retryHandler.postDelayed(
            {
                verifier.verify(productId, purchaseToken) { result ->
                    when (result) {
                        is PlayPurchaseVerifier.Result.Success -> {
                            listener.onPurchaseVerified(result)
                            if (result.needsReconciliation) {
                                listener.onPurchaseNeedsReconciliation(productId)
                            }
                        }

                        PlayPurchaseVerifier.Result.Processing -> {
                            retryVerification(productId, purchaseToken)
                        }

                        PlayPurchaseVerifier.Result.Pending -> {
                            listener.onPurchasePending(productId)
                        }

                        is PlayPurchaseVerifier.Result.SafeError -> {
                            if (result.retryable) {
                                retryVerification(productId, purchaseToken)
                            } else {
                                listener.onPurchaseError()
                            }
                        }
                    }
                }
            },
            RETRY_DELAY_MS * nextAttempt
        )
    }

    private fun runWhenReady(action: () -> Unit) {
        if (billingClient.isReady) {
            action()
        } else {
            start()
        }
    }

    companion object {
        private const val MAX_VERIFICATION_RETRIES = 2
        private const val RETRY_DELAY_MS = 2_000L

        fun sha256Lowercase(value: String): String {
            val digest = MessageDigest
                .getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))

            return digest.joinToString(separator = "") { byte ->
                "%02x".format(byte)
            }
        }
    }
}
