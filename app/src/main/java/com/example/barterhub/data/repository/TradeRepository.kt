package com.example.barterhub.data.repository

import android.util.Log
import com.example.barterhub.data.models.Trader
import com.google.firebase.database.*

class TradeRepository {

    private val database: DatabaseReference =
        FirebaseDatabase.getInstance().getReference("users")

    fun fetchVerifiedTraders(
        onSuccess: (List<Trader>) -> Unit,
        onError: (String) -> Unit
    ) {
        database.addListenerForSingleValueEvent(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                val traders = mutableListOf<Trader>()

                for (userSnapshot in snapshot.children) {
                    try {
                        val isVerified =
                            userSnapshot.child("isIDVerified")
                                .getValue(String::class.java) == "verified"

                        if (!isVerified) continue

                        val isPremium = userSnapshot.child("isPremium")
                            .getValue(Boolean::class.java) ?: false

                        val premiumExpiry = userSnapshot.child("premiumExpiry")
                            .getValue(Long::class.java) ?: 0L

                        val badgesSnapshot = userSnapshot.child("badges")
                        val badges = mutableMapOf<String, Boolean>()

                        if (badgesSnapshot.exists()) {
                            for (badgeEntry in badgesSnapshot.children) {
                                val key = badgeEntry.key ?: continue
                                val value = badgeEntry.getValue(Boolean::class.java) ?: false
                                badges[key] = value
                            }
                        }

                        val trader = Trader(
                            userId = userSnapshot.key ?: continue,
                            username = userSnapshot.child("username").getValue(String::class.java) ?: "Unknown",
                            profileImageUrl = userSnapshot.child("profileImageUrl").getValue(String::class.java) ?: "",
                            rating = userSnapshot.child("rating").getValue(Double::class.java) ?: 0.0,
                            reviewsCount = userSnapshot.child("reviewsCount").getValue(Long::class.java)?.toInt() ?: 0,
                            tradesCompleted = userSnapshot.child("tradesCompleted").getValue(Long::class.java)?.toInt() ?: 0,
                            isVerified = true,
                            isPremium = isPremium,
                            premiumExpiry = premiumExpiry,
                            address = userSnapshot.child("address").getValue(String::class.java),
                            lastWeeklyReset = userSnapshot.child("lastWeeklyReset").getValue(Long::class.java),
                            badges = badges
                        )

                        Log.d(
                            "TradeRepository",
                            "Trader=${trader.username}, isPremium=${trader.isPremium}, premiumExpiry=${trader.premiumExpiry}"
                        )

                        traders.add(trader)

                    } catch (e: Exception) {
                        Log.e("TradeRepository", "Error parsing trader", e)
                    }
                }

                onSuccess(traders)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        })
    }
}