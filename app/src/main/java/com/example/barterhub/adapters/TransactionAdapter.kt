package com.example.barterhub.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.data.models.TransactionModel


class TransactionAdapter(
    private val list: List<TransactionModel>
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = list[position]

        // Basic data
        holder.tvTitle.text = transaction.title
        holder.tvAmount.text = "₱${transaction.amount}"
        holder.tvDate.text = transaction.date

        // 🔥 STATUS LOGIC — DITO MO ILALAGAY
        when (transaction.status) {
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
        }
    }

    override fun getItemCount(): Int = list.size
}
