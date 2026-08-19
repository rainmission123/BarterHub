package com.example.barterhub.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.data.models.TransactionModel

class TransactionAdapter(
    private val list: List<TransactionModel>,
    private val onItemClick: (TransactionModel) -> Unit  // Add click listener parameter
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(list[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = list[position]

        when (transaction.type) {
            "buy_coins" -> holder.tvTitle.text = "Bought Coins 💰"
            "send" -> holder.tvTitle.text = "Sent Coins 📤"
            "receive" -> holder.tvTitle.text = "Received Coins 📥"
            else -> holder.tvTitle.text = transaction.title
        }

        // Show coins instead of pesos
        val coinAmount = transaction.coins
        val formattedAmount = when {
            coinAmount > 0 -> "+$coinAmount Coins"
            coinAmount < 0 -> "$coinAmount Coins"
            else -> "0 Coins"
        }
        holder.tvAmount.text = formattedAmount

        // Set color based on positive or negative amount
        val color = if (coinAmount > 0) {
            ContextCompat.getColor(holder.itemView.context, R.color.green_dark)
        } else if (coinAmount < 0) {
            ContextCompat.getColor(holder.itemView.context, R.color.red_dark)
        } else {
            ContextCompat.getColor(holder.itemView.context, R.color.gray_500)
        }
        holder.tvAmount.setTextColor(color)

        holder.tvDate.text = transaction.date

        // STATUS LOGIC
        when (transaction.status.lowercase()) {
            "pending" -> {
                holder.tvStatus.text = "Pending"
                holder.tvStatus.setBackgroundResource(R.drawable.status_pending_bg)
            }
            "completed" -> {
                holder.tvStatus.text = "Completed"
                holder.tvStatus.setBackgroundResource(R.drawable.status_success_bg)
            }
            "failed" -> {
                holder.tvStatus.text = "Failed"
                holder.tvStatus.setBackgroundResource(R.drawable.status_failed_bg)
            }
            else -> {
                holder.tvStatus.text = "Completed"
                holder.tvStatus.setBackgroundResource(R.drawable.status_success_bg)
            }
        }
    }

    override fun getItemCount(): Int = list.size
}