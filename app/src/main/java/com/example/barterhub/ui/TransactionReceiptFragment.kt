package com.example.barterhub.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.barterhub.R
import com.example.barterhub.data.models.TransactionModel

class TransactionReceiptFragment : Fragment() {

    private lateinit var tvReceiptTitle: TextView
    private lateinit var tvReceiptAmount: TextView
    private lateinit var tvReceiptCoins: TextView
    private lateinit var tvReceiptDate: TextView
    private lateinit var tvReceiptStatus: TextView
    private lateinit var tvReceiptTransactionId: TextView
    private lateinit var tvReceiptReferenceNo: TextView
    private lateinit var tvSender: TextView  // Sender
    private lateinit var tvReceiver: TextView // Receiver

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_transaction_receipt, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)

        val transaction = arguments?.getSerializable("transaction") as? TransactionModel
        transaction?.let { displayReceipt(it) }
    }

    private fun initializeViews(view: View) {
        tvReceiptTitle = view.findViewById(R.id.tvReceiptTitle)
        tvReceiptAmount = view.findViewById(R.id.tvReceiptAmount)
        tvReceiptCoins = view.findViewById(R.id.tvReceiptCoins)
        tvReceiptDate = view.findViewById(R.id.tvReceiptDate)
        tvReceiptStatus = view.findViewById(R.id.tvReceiptStatus)
        tvReceiptTransactionId = view.findViewById(R.id.tvReceiptTransactionId)
        tvReceiptReferenceNo = view.findViewById(R.id.tvReceiptReferenceNo)
        tvSender = view.findViewById(R.id.tvSender)
        tvReceiver = view.findViewById(R.id.tvReceiver)
    }

    private fun displayReceipt(transaction: TransactionModel) {
        tvReceiptTitle.text = transaction.title
        tvReceiptDate.text = transaction.date

        // Coins display
        val formattedCoins = when {
            transaction.coins > 0 -> "+${transaction.coins} Coins"
            transaction.coins < 0 -> "${transaction.coins} Coins"
            else -> "0 Coins"
        }
        tvReceiptCoins.text = formattedCoins

        // Peso amount (only for Purchase Coins)
        if (transaction.title == "Purchase Coins" && transaction.amount > 0) {
            tvReceiptAmount.text = "₱${String.format("%.2f", transaction.amount)}"
            tvReceiptAmount.visibility = View.VISIBLE
        } else {
            tvReceiptAmount.visibility = View.GONE
        }

        // Status
        when (transaction.status.lowercase()) {
            "completed" -> {
                tvReceiptStatus.text = "COMPLETED"
                tvReceiptStatus.setTextColor(resources.getColor(R.color.green_dark, null))
            }
            "pending" -> {
                tvReceiptStatus.text = "PENDING"
                tvReceiptStatus.setTextColor(resources.getColor(R.color.red, null))
            }
            "failed" -> {
                tvReceiptStatus.text = "FAILED"
                tvReceiptStatus.setTextColor(resources.getColor(R.color.red_dark, null))
            }
        }

        // Transaction IDs
        tvReceiptTransactionId.text = transaction.transactionId.ifEmpty { "N/A" }
        tvReceiptReferenceNo.text = transaction.referenceNo.ifEmpty { "N/A" }

        // 🟢 Sender/Receiver logic
        tvSender.visibility = View.GONE
        tvReceiver.visibility = View.GONE

        when (transaction.title) {
            "Sent Coins" -> {
                tvReceiver.text = "To: ${transaction.toName}"
                tvReceiver.visibility = View.VISIBLE
            }
            "Received Coins" -> {
                tvSender.text = "From: ${transaction.fromName}"
                tvSender.visibility = View.VISIBLE
            }
            "Notification" -> {
                // Optional: for notifications
                tvSender.text = "From: ${transaction.fromName}"
                tvReceiver.text = "To: ${transaction.toName}"
                tvSender.visibility = View.VISIBLE
                tvReceiver.visibility = View.VISIBLE
            }
            // Buy Coins → hide both, already default GONE
        }
    }
}