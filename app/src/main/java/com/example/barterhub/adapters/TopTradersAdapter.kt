package com.example.barterhub.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.data.models.Trader
import java.util.Locale

class TopTradersAdapter(
    traders: List<Trader>,
    private val onTraderClick: (Trader) -> Unit
) : RecyclerView.Adapter<TopTradersAdapter.ViewHolder>() {

    private var originalList: List<Trader> = traders
    private var filteredList: List<Trader> = traders

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val traderNameText: TextView = itemView.findViewById(R.id.traderNameText)
        val ratingText: TextView = itemView.findViewById(R.id.ratingText)
        val reviewsText: TextView = itemView.findViewById(R.id.reviewsText)
        val tradesText: TextView = itemView.findViewById(R.id.tradesText)
        val rankNumberText: TextView = itemView.findViewById(R.id.rankNumberText)
        val profileImageView: ImageView = itemView.findViewById(R.id.profileImageView)
        val verificationBadge: ImageView = itemView.findViewById(R.id.verificationBadge)
        val rankBadgeImage: ImageView = itemView.findViewById(R.id.rankBadgeImage)
        val achievementsLayout: LinearLayout = itemView.findViewById(R.id.achievementsLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top_trader, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val trader = filteredList[position]
        val context = holder.itemView.context

        holder.traderNameText.text = trader.username
        holder.ratingText.text = trader.getDisplayRating()
        holder.reviewsText.text = trader.getReviewsText()
        holder.tradesText.text = trader.getTradesText()

        if (trader.profileImageUrl.isNotEmpty()) {
            Glide.with(context)
                .load(trader.profileImageUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .into(holder.profileImageView)
        } else {
            holder.profileImageView.setImageResource(R.drawable.ic_profile_placeholder)
        }

        holder.verificationBadge.visibility =
            if (trader.isVerified) View.VISIBLE else View.GONE

        if (trader.rank in 1..10) {
            holder.rankBadgeImage.visibility = View.VISIBLE
            holder.rankNumberText.visibility = View.GONE

            holder.rankBadgeImage.setImageResource(
                when (trader.rank) {
                    1 -> R.drawable.ic_badge_top1
                    2 -> R.drawable.ic_badge_top2
                    3 -> R.drawable.ic_badge_top3
                    4 -> R.drawable.ic_badge_top4
                    5 -> R.drawable.ic_badge_top5
                    6 -> R.drawable.ic_badge_top6
                    7 -> R.drawable.ic_badge_top7
                    8 -> R.drawable.ic_badge_top8
                    9 -> R.drawable.ic_badge_top9
                    else -> R.drawable.ic_badge_top10
                }
            )
        } else {
            holder.rankBadgeImage.visibility = View.GONE
            holder.rankNumberText.visibility = View.VISIBLE
            holder.rankNumberText.text = "#${trader.rank}"
        }

        holder.achievementsLayout.removeAllViews()

        trader.badges.forEach { entry ->
            if (!entry.value) return@forEach

            val iconRes = when (entry.key) {
                "first_trade" -> R.drawable.ic_badge_first_trade
                "verified" -> R.drawable.ic_badge_verified
                "community" -> R.drawable.ic_badge_community
                "friendly" -> R.drawable.ic_badge_friendly
                "reliable" -> R.drawable.ic_badge_reliable
                else -> null
            }

            if (iconRes != null) {
                val badge = ImageView(context).apply {
                    setImageResource(iconRes)
                    layoutParams = ViewGroup.MarginLayoutParams(32, 32).apply {
                        marginEnd = 8
                    }
                }
                holder.achievementsLayout.addView(badge)
            }
        }

        holder.itemView.setOnClickListener {
            onTraderClick(trader)
        }
    }

    override fun getItemCount(): Int = filteredList.size

    fun updateList(newList: List<Trader>) {
        originalList = newList
        filteredList = newList
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val searchText = query.trim().lowercase(Locale.getDefault())

        filteredList = if (searchText.isEmpty()) {
            originalList
        } else {
            originalList.filter { trader ->
                trader.username.lowercase(Locale.getDefault()).contains(searchText)
            }
        }

        notifyDataSetChanged()
    }
}