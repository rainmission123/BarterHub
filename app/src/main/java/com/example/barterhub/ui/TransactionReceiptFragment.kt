package com.example.barterhub.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.barterhub.R
import com.example.barterhub.data.models.TransactionModel
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionReceiptFragment : Fragment() {

    private lateinit var tvReceiptTitle: TextView
    private lateinit var tvReceiptStatusBadge: TextView
    private lateinit var tvReceiptCoinsLabel: TextView
    private lateinit var tvReceiptAmount: TextView
    private lateinit var tvReceiptCoins: TextView
    private lateinit var tvReceiptDate: TextView
    private lateinit var tvReceiptStatus: TextView
    private lateinit var tvReceiptTransactionId: TextView
    private lateinit var tvReceiptReferenceNo: TextView
    private lateinit var tvReceiptPaymentMethod: TextView
    private lateinit var tvReceiptPaymentMethodIcon: TextView
    private lateinit var tvReceiptWalletBalanceLabel: TextView
    private lateinit var tvReceiptWalletBalance: TextView

    private lateinit var tvSender: TextView
    private lateinit var tvReceiver: TextView

    private lateinit var rowPaymentDetails: View
    private lateinit var rowSender: View
    private lateinit var rowReceiver: View

    private lateinit var btnCopyTransactionId: ImageButton
    private lateinit var btnCopyReferenceNo: ImageButton
    private lateinit var btnBackToWallet: MaterialButton
    private lateinit var btnShareReceipt: MaterialButton

    private var currentTransaction: TransactionModel? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.fragment_transaction_receipt,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)

        @Suppress("DEPRECATION")
        val transaction =
            arguments?.getSerializable("transaction") as? TransactionModel

        currentTransaction = transaction

        if (transaction != null) {
            displayReceipt(transaction)
        }
    }

    private fun initializeViews(view: View) {
        tvReceiptTitle = view.findViewById(R.id.tvReceiptTitle)
        tvReceiptStatusBadge = view.findViewById(R.id.tvReceiptStatusBadge)
        tvReceiptCoinsLabel = view.findViewById(R.id.tvReceiptCoinsLabel)

        tvReceiptAmount = view.findViewById(R.id.tvReceiptAmount)
        tvReceiptCoins = view.findViewById(R.id.tvReceiptCoins)
        tvReceiptDate = view.findViewById(R.id.tvReceiptDate)
        tvReceiptStatus = view.findViewById(R.id.tvReceiptStatus)

        tvReceiptTransactionId =
            view.findViewById(R.id.tvReceiptTransactionId)

        tvReceiptReferenceNo =
            view.findViewById(R.id.tvReceiptReferenceNo)

        tvReceiptPaymentMethod =
            view.findViewById(R.id.tvReceiptPaymentMethod)

        tvReceiptPaymentMethodIcon =
            view.findViewById(R.id.tvReceiptPaymentMethodIcon)

        tvReceiptWalletBalanceLabel =
            view.findViewById(R.id.tvReceiptWalletBalanceLabel)

        tvReceiptWalletBalance =
            view.findViewById(R.id.tvReceiptWalletBalance)

        tvSender = view.findViewById(R.id.tvSender)
        tvReceiver = view.findViewById(R.id.tvReceiver)

        rowPaymentDetails = view.findViewById(R.id.rowPaymentDetails)
        rowSender = view.findViewById(R.id.rowSender)
        rowReceiver = view.findViewById(R.id.rowReceiver)

        btnCopyTransactionId =
            view.findViewById(R.id.btnCopyTransactionId)

        btnCopyReferenceNo =
            view.findViewById(R.id.btnCopyReferenceNo)

        btnBackToWallet =
            view.findViewById(R.id.btnBackToWallet)

        btnShareReceipt =
            view.findViewById(R.id.btnShareReceipt)
    }

    private fun displayReceipt(transaction: TransactionModel) {
        val normalizedType =
            transaction.type.trim().lowercase(Locale.ROOT)

        val isCompleted =
            transaction.status.equals(
                "completed",
                ignoreCase = true
            ) ||
                    transaction.status.equals(
                        "success",
                        ignoreCase = true
                    ) ||
                    transaction.status.equals(
                        "paid",
                        ignoreCase = true
                    )

        when (normalizedType) {

            "send" -> {
                tvReceiptTitle.text =
                    if (isCompleted) {
                        "Coins Sent Successfully"
                    } else {
                        "Sent Coins"
                    }

                tvReceiptStatusBadge.text =
                    "COIN TRANSFER COMPLETED"

                tvReceiptCoinsLabel.text =
                    "Coins Sent"

                tvReceiptWalletBalanceLabel.text =
                    "Wallet Balance After Transfer"

                rowPaymentDetails.visibility = View.GONE
            }

            "receive" -> {
                tvReceiptTitle.text =
                    if (isCompleted) {
                        "Coins Received Successfully"
                    } else {
                        "Received Coins"
                    }

                tvReceiptStatusBadge.text =
                    "COIN TRANSFER COMPLETED"

                tvReceiptCoinsLabel.text =
                    "Coins Received"

                tvReceiptWalletBalanceLabel.text =
                    "Wallet Balance After Transfer"

                rowPaymentDetails.visibility = View.GONE
            }

            "buy_coins",
            "purchase",
            "cash-in" -> {
                tvReceiptTitle.text =
                    if (isCompleted) {
                        "Payment Successful"
                    } else {
                        transaction.title.ifBlank {
                            "Transaction Receipt"
                        }
                    }

                tvReceiptStatusBadge.text =
                    if (isCompleted) {
                        "SECURE PAYMENT CONFIRMED"
                    } else {
                        "PAYMENT ${formatStatus(transaction.status).uppercase(Locale.ROOT)}"
                    }

                tvReceiptCoinsLabel.text =
                    "Coins Received"

                tvReceiptWalletBalanceLabel.text =
                    "Wallet Balance After Payment"

                rowPaymentDetails.visibility = View.VISIBLE
            }

            else -> {
                tvReceiptTitle.text =
                    transaction.title.ifBlank {
                        "Transaction Receipt"
                    }

                tvReceiptStatusBadge.text =
                    formatStatus(transaction.status)
                        .uppercase(Locale.ROOT)

                tvReceiptCoinsLabel.text =
                    "Coins"

                tvReceiptWalletBalanceLabel.text =
                    "Wallet Balance After Transaction"

                rowPaymentDetails.visibility = View.GONE
            }
        }

        tvReceiptCoins.text =
            formatCoins(transaction.coins)

        tvReceiptAmount.text =
            formatAmount(transaction.amount)

        val displayPaymentMethod =
            transaction.paymentMethod.ifBlank {
                transaction.provider
            }

        tvReceiptPaymentMethod.text =
            formatPaymentMethod(displayPaymentMethod)

        bindPaymentMethodIcon(displayPaymentMethod)

        tvReceiptWalletBalance.text =
            formatWalletBalance(
                transaction.walletBalanceAfter
            )

        tvReceiptDate.text =
            formatReceiptDate(transaction)

        tvReceiptStatus.text =
            formatStatus(transaction.status)

        tvReceiptStatus.setTextColor(
            statusColor(transaction.status)
        )

        val transactionId =
            transaction.transactionId.ifBlank {
                "N/A"
            }

        val referenceNo =
            transaction.referenceNo.ifBlank {
                "Not available"
            }

        tvReceiptTransactionId.text =
            transactionId

        tvReceiptReferenceNo.text =
            referenceNo

        btnCopyTransactionId.setOnClickListener {
            copyToClipboard(
                "Transaction ID",
                transactionId
            )
        }

        btnCopyReferenceNo.setOnClickListener {
            copyToClipboard(
                "Reference Number",
                referenceNo
            )
        }

        btnBackToWallet.setOnClickListener {
            findNavController().popBackStack()
        }

        btnShareReceipt.setOnClickListener {
            shareReceipt(transaction)
        }

        bindSenderReceiver(transaction)
    }

    private fun bindSenderReceiver(
        transaction: TransactionModel
    ) {
        rowSender.visibility = View.GONE
        rowReceiver.visibility = View.GONE

        when (transaction.type.trim().lowercase(Locale.ROOT)) {

            "send" -> {
                tvReceiver.text =
                    "To: ${transaction.toName.ifBlank { "Recipient" }}"

                rowReceiver.visibility =
                    View.VISIBLE
            }

            "receive" -> {
                tvSender.text =
                    "From: ${transaction.fromName.ifBlank { "Sender" }}"

                rowSender.visibility =
                    View.VISIBLE
            }

            else -> {
                if (
                    transaction.title.equals(
                        "Notification",
                        ignoreCase = true
                    )
                ) {
                    tvSender.text =
                        "From: ${transaction.fromName.ifBlank { "Sender" }}"

                    tvReceiver.text =
                        "To: ${transaction.toName.ifBlank { "Recipient" }}"

                    rowSender.visibility =
                        View.VISIBLE

                    rowReceiver.visibility =
                        View.VISIBLE
                }
            }
        }
    }

    private fun formatCoins(coins: Int): String {
        return when {
            coins > 0 -> "+$coins Coins"
            coins < 0 -> "$coins Coins"
            else -> "0 Coins"
        }
    }

    private fun formatAmount(amount: Double): String {
        return if (amount > 0) {
            "₱${String.format(Locale.getDefault(), "%.2f", amount)}"
        } else {
            "N/A"
        }
    }

    private fun formatWalletBalance(
        walletBalanceAfter: Int?
    ): String {
        return when (walletBalanceAfter) {
            null -> "N/A"
            else -> "$walletBalanceAfter Coins"
        }
    }

    private fun formatPaymentMethod(
        paymentMethod: String
    ): String {
        return when (
            paymentMethod.lowercase(Locale.getDefault())
        ) {
            "gcash" -> "GCash"

            "grab_pay",
            "grabpay" -> "GrabPay"

            "card" -> "Card"

            "paymongo",
            "paymongo_checkout" ->
                "PayMongo Checkout"

            "google_play",
            "googleplay" ->
                "Google Play"

            "" -> "N/A"

            else ->
                paymentMethod.replaceFirstChar {
                    if (it.isLowerCase()) {
                        it.titlecase(Locale.getDefault())
                    } else {
                        it.toString()
                    }
                }
        }
    }

    private fun bindPaymentMethodIcon(
        paymentMethod: String
    ) {
        when (
            paymentMethod.lowercase(Locale.getDefault())
        ) {

            "gcash" -> {
                tvReceiptPaymentMethodIcon.text =
                    "GCash"

                tvReceiptPaymentMethodIcon.textSize =
                    10f

                tvReceiptPaymentMethodIcon
                    .setBackgroundResource(
                        R.drawable.bg_payment_method_gcash
                    )
            }

            "grab_pay",
            "grabpay" -> {
                tvReceiptPaymentMethodIcon.text =
                    "Grab"

                tvReceiptPaymentMethodIcon.textSize =
                    10f

                tvReceiptPaymentMethodIcon
                    .setBackgroundResource(
                        R.drawable.bg_payment_method_grabpay
                    )
            }

            "card" -> {
                tvReceiptPaymentMethodIcon.text =
                    "💳"

                tvReceiptPaymentMethodIcon.textSize =
                    18f

                tvReceiptPaymentMethodIcon
                    .setBackgroundResource(
                        R.drawable.bg_payment_method_card
                    )
            }

            "paymongo",
            "paymongo_checkout" -> {
                tvReceiptPaymentMethodIcon.text =
                    "Pay"

                tvReceiptPaymentMethodIcon.textSize =
                    11f

                tvReceiptPaymentMethodIcon
                    .setBackgroundResource(
                        R.drawable.bg_payment_method_card
                    )
            }

            "google_play",
            "googleplay" -> {
                tvReceiptPaymentMethodIcon.text =
                    "Play"

                tvReceiptPaymentMethodIcon.textSize =
                    10f

                tvReceiptPaymentMethodIcon
                    .setBackgroundResource(
                        R.drawable.bg_payment_method_card
                    )
            }

            else -> {
                tvReceiptPaymentMethodIcon.text =
                    "—"

                tvReceiptPaymentMethodIcon.textSize =
                    18f

                tvReceiptPaymentMethodIcon
                    .setBackgroundResource(
                        R.drawable.bg_payment_method_card
                    )
            }
        }
    }

    private fun formatStatus(status: String): String {
        return when (
            status.lowercase(Locale.getDefault())
        ) {
            "completed",
            "success",
            "paid" -> "Completed"

            "pending" -> "Pending"

            "failed",
            "cancelled",
            "canceled" -> "Failed"

            else -> status.ifBlank {
                "Completed"
            }
        }
    }

    private fun statusColor(status: String): Int {
        return when (
            status.lowercase(Locale.getDefault())
        ) {
            "completed",
            "success",
            "paid" ->
                resources.getColor(
                    R.color.green_dark,
                    null
                )

            "pending" ->
                resources.getColor(
                    R.color.orange_500,
                    null
                )

            else ->
                resources.getColor(
                    R.color.red_dark,
                    null
                )
        }
    }

    private fun formatReceiptDate(
        transaction: TransactionModel
    ): String {
        val timestamp =
            transaction.timestamp

        if (timestamp > 0L) {
            return SimpleDateFormat(
                "MMMM dd, yyyy • hh:mm a",
                Locale.getDefault()
            ).format(Date(timestamp))
        }

        return transaction.date.ifBlank {
            "N/A"
        }
    }

    private fun copyToClipboard(
        label: String,
        value: String
    ) {
        if (
            value == "N/A" ||
            value == "Not available"
        ) {
            Toast.makeText(
                requireContext(),
                "$label not available",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val clipboard =
            requireContext()
                .getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                label,
                value
            )
        )

        Toast.makeText(
            requireContext(),
            "$label copied",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun shareReceipt(
        transaction: TransactionModel
    ) {
        val normalizedType =
            transaction.type.trim().lowercase(Locale.ROOT)

        val receiptTitle =
            when (normalizedType) {
                "send" ->
                    "BarterHub Sent Coins Receipt"

                "receive" ->
                    "BarterHub Received Coins Receipt"

                "buy_coins",
                "purchase",
                "cash-in" ->
                    "BarterHub Payment Receipt"

                else ->
                    "BarterHub Transaction Receipt"
            }

        val coinsLabel =
            when (normalizedType) {
                "send" -> "Coins Sent"
                "receive" -> "Coins Received"
                else -> "Coins"
            }

        val balanceLabel =
            when (normalizedType) {
                "buy_coins",
                "purchase",
                "cash-in" ->
                    "Wallet Balance After Payment"

                "send",
                "receive" ->
                    "Wallet Balance After Transfer"

                else ->
                    "Wallet Balance After Transaction"
            }

        val receiptText =
            if (
                normalizedType == "buy_coins" ||
                normalizedType == "purchase" ||
                normalizedType == "cash-in"
            ) {
                """
                $receiptTitle

                ${tvReceiptTitle.text}
                $coinsLabel: ${tvReceiptCoins.text}
                Amount Paid: ${tvReceiptAmount.text}
                Payment Method: ${tvReceiptPaymentMethod.text}
                $balanceLabel: ${tvReceiptWalletBalance.text}
                Status: ${tvReceiptStatus.text}
                Date & Time: ${tvReceiptDate.text}
                Transaction ID: ${tvReceiptTransactionId.text}
                Reference Number: ${tvReceiptReferenceNo.text}
                """.trimIndent()
            } else {
                val partyLine =
                    when (normalizedType) {
                        "send" ->
                            "To: ${transaction.toName.ifBlank { "Recipient" }}"

                        "receive" ->
                            "From: ${transaction.fromName.ifBlank { "Sender" }}"

                        else -> ""
                    }

                """
                $receiptTitle

                ${tvReceiptTitle.text}
                $coinsLabel: ${tvReceiptCoins.text}
                $partyLine
                $balanceLabel: ${tvReceiptWalletBalance.text}
                Status: ${tvReceiptStatus.text}
                Date & Time: ${tvReceiptDate.text}
                Transaction ID: ${tvReceiptTransactionId.text}
                Reference Number: ${tvReceiptReferenceNo.text}
                """.trimIndent()
            }

        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"

                putExtra(
                    Intent.EXTRA_SUBJECT,
                    receiptTitle
                )

                putExtra(
                    Intent.EXTRA_TEXT,
                    receiptText
                )
            }

        startActivity(
            Intent.createChooser(
                intent,
                "Share Receipt"
            )
        )
    }
}