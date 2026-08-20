package com.example.barterhub.billing

import com.android.billingclient.api.ProductDetails

data class CoinProduct(
    val productId: String,
    val coins: Int,
    val details: ProductDetails? = null
) {
    val formattedPrice: String?
        get() = details
            ?.oneTimePurchaseOfferDetails
            ?.formattedPrice

    companion object {
        const val PRODUCT_100 = "barter_coins_100"
        const val PRODUCT_200 = "barter_coins_200"
        const val PRODUCT_500 = "barter_coins_500"

        val supported = listOf(
            CoinProduct(PRODUCT_100, 100),
            CoinProduct(PRODUCT_200, 200),
            CoinProduct(PRODUCT_500, 500)
        )

        fun forCoins(coins: Int): CoinProduct? = supported.firstOrNull { it.coins == coins }

        fun coinsForProduct(productId: String): Int? =
            supported.firstOrNull { it.productId == productId }?.coins
    }
}
