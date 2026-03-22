package com.example.barterhub.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.adapters.TopTradersAdapter
import com.example.barterhub.data.models.Trader
import com.example.barterhub.data.repository.TradeRepository
import com.example.barterhub.utils.LeaderboardUtils
import com.example.barterhub.utils.LocationHelper
import com.example.barterhub.utils.PremiumHelper
import com.example.barterhub.utils.ScoreCalculator
import com.google.android.material.appbar.MaterialToolbar

class TopTradersFragment : Fragment(R.layout.fragment_top_traders) {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TopTradersAdapter
    private lateinit var searchEditText: EditText
    private lateinit var clearSearchButton: ImageView
    private val tradeRepository = TradeRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.toolbar)
        recyclerView = view.findViewById(R.id.topTradersRecyclerView)
        searchEditText = view.findViewById(R.id.searchTopTradersEditText)
        clearSearchButton = view.findViewById(R.id.clearSearchButton)

        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.setHasFixedSize(true)

        adapter = TopTradersAdapter(emptyList()) { trader ->
            val bundle = Bundle().apply {
                putString("ownerId", trader.userId)
            }

            findNavController().navigate(
                R.id.ownerProfileFragment,
                bundle
            )
        }

        recyclerView.adapter = adapter

        setupSearch()
        loadTopTraders()
    }

    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                adapter.filter(query)
                clearSearchButton.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        clearSearchButton.setOnClickListener {
            searchEditText.text.clear()
        }
    }

    private fun loadTopTraders() {
        tradeRepository.fetchVerifiedTraders(
            onSuccess = { traders ->

                val premiumVerifiedTraders = traders.filter { trader ->
                    trader.isVerified &&
                            PremiumHelper.isPremiumActive(trader.isPremium, trader.premiumExpiry) &&
                            trader.tradesCompleted > 0
                }

                premiumVerifiedTraders.forEach { trader ->
                    val lastReset = trader.lastWeeklyReset ?: 0L
                    LeaderboardUtils.resetWeeklyTradesIfNeeded(trader.userId, lastReset)
                }

                LocationHelper.getCityFromLatLng(
                    requireContext(),
                    14.5995,
                    120.9842
                )

                val ranked = ScoreCalculator.rankTraders(premiumVerifiedTraders).take(500)
                saveLeaderboardRanks(ranked)
                adapter.updateList(ranked)

                Log.d("TopTraders", "Loaded ${ranked.size} premium verified active traders")
            },
            onError = { error ->
                Log.e("TopTraders", "Error loading traders: $error")
            }
        )
    }

    private fun saveLeaderboardRanks(ranked: List<Trader>) {
        val usersRef = com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("users")

        tradeRepository.fetchVerifiedTraders(
            onSuccess = { allTraders ->
                val updates = hashMapOf<String, Any>()

                allTraders.forEach { trader ->
                    updates["${trader.userId}/leaderboardRank"] = 0
                }

                ranked.forEach { trader ->
                    updates["${trader.userId}/leaderboardRank"] = trader.rank
                }

                usersRef.updateChildren(updates)
                    .addOnSuccessListener {
                        Log.d("TopTraders", "✅ Leaderboard ranks saved successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e("TopTraders", "❌ Failed to save leaderboard ranks: ${e.message}")
                    }
            },
            onError = { error ->
                Log.e("TopTraders", "❌ Failed to fetch traders for rank saving: $error")
            }
        )
    }
}