package com.example.barterhub.utils

import com.example.barterhub.data.models.Trader

object ScoreCalculator {

    fun calculateScore(trader: Trader): Double {
        val baseScore = (trader.tradesCompleted * 5) +
                (trader.reviewsCount * 3) +
                (trader.rating * 10)

        val premiumBoost = if (
            PremiumHelper.isPremiumActive(trader.isPremium, trader.premiumExpiry)
        ) 25 else 0

        return baseScore + premiumBoost
    }

    fun rankTraders(traders: List<Trader>, limit: Int = 10): List<Trader> {
        return traders
            .map { trader ->
                val score = calculateScore(trader)
                trader.copy(score = score)
            }
            .sortedByDescending { it.score }
            .take(limit)
            .mapIndexed { index, trader ->
                trader.copy(rank = index + 1)
            }
    }
}