package com.example.barterhub.billing

data class CoinProduct(
    val productId: String,
    val coins: Int
) {
    companion object {
        val BARTER_COINS_100 = CoinProduct("barter_coins_100", 100)
        val BARTER_COINS_200 = CoinProduct("barter_coins_200", 200)
        val BARTER_COINS_500 = CoinProduct("barter_coins_500", 500)

        val all: List<CoinProduct> = listOf(
            BARTER_COINS_100,
            BARTER_COINS_200,
            BARTER_COINS_500
        )

        fun fromProductId(productId: String): CoinProduct? {
            return all.firstOrNull { it.productId == productId }
        }
    }
}
