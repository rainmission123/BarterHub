package com.example.barterhub.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.data.models.TradeHistoryItem  // import model dito

class TradeHistoryAdapter(private val trades: List<TradeHistoryItem>) :
    RecyclerView.Adapter<TradeHistoryAdapter.TradeHistoryViewHolder>() {

    class TradeHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemTitle: TextView = itemView.findViewById(R.id.itemTitle)
        val partnerName: TextView = itemView.findViewById(R.id.partnerName)
        val tradeDate: TextView = itemView.findViewById(R.id.tradeDate)
        val status: TextView = itemView.findViewById(R.id.status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TradeHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trade_history, parent, false)
        return TradeHistoryViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: TradeHistoryViewHolder, position: Int) {
        val trade = trades[position]
        holder.itemTitle.text = trade.itemName
        holder.partnerName.text = "With: ${trade.tradedWith}"
        holder.tradeDate.text = "Date: ${trade.date}"
        holder.status.text = "Status: ${trade.status}"
    }

    override fun getItemCount() = trades.size
}
