package com.example.barterhub.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.data.models.TradeHistoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TradeHistoryAdapter(
    private val trades: MutableList<TradeHistoryItem> = mutableListOf()
) : RecyclerView.Adapter<TradeHistoryAdapter.TradeHistoryViewHolder>() {

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

    override fun onBindViewHolder(holder: TradeHistoryViewHolder, position: Int) {
        val trade = trades[position]

        val itemName = trade.itemName?.trim().takeUnless { it.isNullOrEmpty() } ?: "(No item name)"
        val tradedWith = trade.tradedWith?.trim().takeUnless { it.isNullOrEmpty() } ?: "-"
        val formattedDate = formatTimestamp(trade.date)
        val statusRaw = trade.status?.trim().takeUnless { it.isNullOrEmpty() } ?: "Unknown"

        holder.itemTitle.text = itemName
        holder.partnerName.text = "With: $tradedWith"
        holder.tradeDate.text = "Date: $formattedDate"
        holder.status.text = "Status: $statusRaw"

        applyStatusColor(holder, statusRaw)
    }

    override fun getItemCount(): Int = trades.size

    fun submitList(newTrades: List<TradeHistoryItem>) {
        trades.clear()
        trades.addAll(newTrades)
        notifyDataSetChanged()
    }

    /**
     * Converts Firebase timestamp (System.currentTimeMillis) into readable format.
     * Supports:
     * - "1771833869828" (millis)
     * - already formatted strings (returns as-is)
     */
    private fun formatTimestamp(raw: String?): String {
        val cleaned = raw?.trim()
        if (cleaned.isNullOrEmpty()) return "-"

        val millis = cleaned.toLongOrNull() ?: return cleaned

        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    private fun applyStatusColor(holder: TradeHistoryViewHolder, statusRaw: String) {
        val ctx = holder.itemView.context
        val statusLower = statusRaw.lowercase(Locale.getDefault())

        val colorRes = when {
            statusLower.contains("completed") || statusLower.contains("success") ->
                android.R.color.holo_green_dark

            statusLower.contains("pending") || statusLower.contains("processing") ->
                android.R.color.holo_orange_dark

            statusLower.contains("cancel") || statusLower.contains("failed") || statusLower.contains("reject") ->
                android.R.color.holo_red_dark

            else ->
                android.R.color.darker_gray
        }

        holder.status.setTextColor(ContextCompat.getColor(ctx, colorRes))
    }
}