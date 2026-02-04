package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.data.models.Trader
import com.example.barterhub.data.repository.TradeRepository
import com.example.barterhub.utils.LeaderboardUtils
import com.example.barterhub.utils.LocationHelper
import com.example.barterhub.utils.ScoreCalculator
import com.google.android.material.card.MaterialCardView

class TopTradersFragment : Fragment(R.layout.fragment_top_traders) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TopTradersAdapter
    private val tradeRepository = TradeRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.topTradersRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = TopTradersAdapter(emptyList())
        recyclerView.adapter = adapter

        loadTopTraders()
    }

    private fun loadTopTraders() {
        tradeRepository.fetchVerifiedTraders(
            onSuccess = { traders ->
                traders.forEach { trader ->
                    val lastReset = trader.lastWeeklyReset ?: 0L
                    LeaderboardUtils.resetWeeklyTradesIfNeeded(trader.userId, lastReset)
                }

                LocationHelper.getCityFromLatLng(
                    requireContext(),
                    14.5995,
                    120.9842
                )

                val ranked = ScoreCalculator.rankTraders(traders)
                adapter.updateList(ranked)

                Log.d("TopTraders", "Loaded ${ranked.size} traders")
            },
            onError = { error ->
                Log.e("TopTraders", "Error loading traders: $error")
            }
        )
    }

    // =====================================================
    // ================== ADAPTER ==========================
    // =====================================================

    class TopTradersAdapter(private var traders: List<Trader>) :
        RecyclerView.Adapter<TopTradersAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            // ✅ COMPLETE VIEWHOLDER WITH PREMIUM BADGE
            val root: ViewGroup = itemView.findViewById(R.id.root)
            val traderNameText: TextView = itemView.findViewById(R.id.traderNameText)
            val ratingText: TextView = itemView.findViewById(R.id.ratingText)
            val reviewsText: TextView = itemView.findViewById(R.id.reviewsText)
            val tradesText: TextView = itemView.findViewById(R.id.tradesText)
            val profileImageView: ImageView = itemView.findViewById(R.id.profileImageView)
            val verificationBadge: ImageView = itemView.findViewById(R.id.verificationBadge)
            val rankNumberText: TextView = itemView.findViewById(R.id.rankNumberText)
            val rankBadge: MaterialCardView = itemView.findViewById(R.id.rankBadge)
            val achievementsLayout: LinearLayout = itemView.findViewById(R.id.achievementsLayout)
            val premiumTextBadge: TextView = itemView.findViewById(R.id.premiumTextBadge) // ✅ ADDED
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_top_trader, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val trader = traders[position]
            val context = holder.itemView.context

            holder.traderNameText.text = trader.username
            holder.ratingText.text = trader.getDisplayRating()
            holder.reviewsText.text = trader.getReviewsText()
            holder.tradesText.text = trader.getTradesText()

            // 🖼 Profile Image
            if (trader.profileImageUrl.isNotEmpty()) {
                Glide.with(context)
                    .load(trader.profileImageUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .into(holder.profileImageView)
            } else {
                holder.profileImageView.setImageResource(R.drawable.ic_profile_placeholder)
            }

            // ✅ PREMIUM BADGE - SHOW/HIDE BASED ON isPremium
            if (trader.isPremium) {
                holder.premiumTextBadge.visibility = View.VISIBLE
                Log.d("PremiumDebug", "Showing premium badge for: ${trader.username}")
            } else {
                holder.premiumTextBadge.visibility = View.GONE
                Log.d("PremiumDebug", "Hiding premium badge for: ${trader.username}")
            }

            // ✅ Verification Badge
            holder.verificationBadge.visibility =
                if (trader.isVerified) View.VISIBLE else View.GONE

            // 🏆 Rank UI
            holder.rankNumberText.text = "#${trader.rank}"
            when (trader.rank) {
                1 -> applyRankStyle(holder, context, R.color.gold, R.color.gold_light)
                2 -> applyRankStyle(holder, context, R.color.silver, R.color.silver_light)
                3 -> applyRankStyle(holder, context, R.color.bronze, R.color.bronze_light)
                else -> applyRankStyle(holder, context, R.color.gray, R.color.gray_light)
            }

            // =================================================
            // 🏅 BADGES (FROM PROFILE – SINGLE SOURCE OF TRUTH)
            // =================================================
            Log.d("BadgesDebug", "Trader: ${trader.username}")
            Log.d("BadgesDebug", "Badges: ${trader.badges}")
            Log.d("BadgesDebug", "Badges count: ${trader.badges?.size ?: 0}")

            holder.achievementsLayout.removeAllViews()

            val badgeMap: Map<String, Boolean> = trader.badges ?: emptyMap()
            Log.d("BadgesDebug", "Badge map size: ${badgeMap.size}")

            var addedCount = 0

            badgeMap.forEach { entry ->
                if (!entry.value) return@forEach

                val iconRes = when (entry.key) {
                    "first_trade" -> R.drawable.ic_badge_first_trade
                    "verified" -> R.drawable.ic_badge_verified
                    "top_trader" -> R.drawable.ic_badge_top_trader
                    "community" -> R.drawable.ic_badge_community
                    "friendly" -> R.drawable.ic_badge_friendly
                    "reliable" -> R.drawable.ic_badge_reliable
                    else -> R.drawable.ic_badge_generic
                }

                val badge = ImageView(context).apply {
                    setImageResource(iconRes)
                    layoutParams = ViewGroup.MarginLayoutParams(32, 32).apply {
                        marginEnd = 8
                    }
                }

                holder.achievementsLayout.addView(badge)
                addedCount++
            }

            Log.d("BadgesDebug", "Added $addedCount badges")
        }

        override fun getItemCount(): Int = traders.size

        fun updateList(newList: List<Trader>) {
            traders = newList
            notifyDataSetChanged()
        }

        private fun applyRankStyle(
            holder: ViewHolder,
            context: android.content.Context,
            textColor: Int,
            bgColor: Int
        ) {
            holder.rankNumberText.setTextColor(ContextCompat.getColor(context, textColor))
            holder.rankBadge.setCardBackgroundColor(ContextCompat.getColor(context, bgColor))
        }
    }
}