package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.barterhub.R
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.barterhub.utils.ReceiptSaver


class ReceiptFragment : Fragment(R.layout.fragment_receipt) {

    companion object {
        private const val TAG = "ReceiptFragment"
    }

    private val db by lazy { FirebaseDatabase.getInstance().reference }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val receiptId = arguments?.getString("receiptId").orEmpty()
        if (receiptId.isBlank()) {
            Toast.makeText(requireContext(), "Receipt ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        val tvReceiptId = view.findViewById<TextView>(R.id.tvReceiptId)
        val tvReceiptDate = view.findViewById<TextView>(R.id.tvReceiptDate)
        val tvUserA = view.findViewById<TextView>(R.id.tvUserA)
        val tvUserB = view.findViewById<TextView>(R.id.tvUserB)
        val tvOfferedItem = view.findViewById<TextView>(R.id.tvOfferedItem)
        val tvTargetItem = view.findViewById<TextView>(R.id.tvTargetItem)
        val tvOfferedFrom = view.findViewById<TextView>(R.id.tvOfferedFrom)
        val tvOfferedTo = view.findViewById<TextView>(R.id.tvOfferedTo)
        val tvOfferedCondition = view.findViewById<TextView>(R.id.tvOfferedCondition)
        val tvTargetFrom = view.findViewById<TextView>(R.id.tvTargetFrom)
        val tvTargetTo = view.findViewById<TextView>(R.id.tvTargetTo)
        val tvTargetCondition = view.findViewById<TextView>(R.id.tvTargetCondition)
        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
        val tvChatId = view.findViewById<TextView>(R.id.tvChatId)
        val tvRequestId = view.findViewById<TextView>(R.id.tvRequestId)
        val tvFooterNote = view.findViewById<TextView>(R.id.tvFooterNote)
        val receiptRoot = view.findViewById<View>(R.id.receiptRoot)

        receiptRoot.setOnLongClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Receipt Options")
                .setItems(arrayOf("Save to Gallery")) { _, which ->
                    if (which == 0) {
                        Toast.makeText(requireContext(), "Saving...", Toast.LENGTH_SHORT).show()

                        ReceiptSaver.saveViewToGallery(
                            context = requireContext(),
                            view = receiptRoot,
                            fileNameNoExt = "BarterHub_Receipt_$receiptId",
                            tag = TAG
                        ) { uri ->
                            if (uri != null) {
                                Toast.makeText(requireContext(), "Saved to Gallery ✅", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(requireContext(), "Failed to save ❌", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .show()
            true
        }

        tvReceiptId.text = "Loading..."

        db.child("receipts").child(receiptId).get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    Toast.makeText(requireContext(), "Receipt not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                try {
                    val chatId = snap.child("chatId").getValue(String::class.java).orEmpty()
                    val requestId = snap.child("tradeRequestId").getValue(String::class.java).orEmpty()
                    val receiptNo = snap.child("receiptNo").getValue(String::class.java).orEmpty()
                    tvReceiptId.text = receiptNo.ifBlank { receiptId }

                    // Date
                    val timestamp = snap.child("timestamp").getValue(Long::class.java)
                        ?: snap.child("completedAt").getValue(Long::class.java)
                        ?: 0L
                    tvReceiptDate.text = formatDate(timestamp)

                    // IDs
                    tvChatId.text = if (chatId.length > 12) chatId.take(12) + "..." else chatId
                    tvRequestId.text = if (requestId.length > 12) requestId.take(12) + "..." else requestId

                    tvChatId.setOnClickListener {
                        copyToClipboard("Chat ID", chatId)
                        Toast.makeText(requireContext(), "Chat ID copied", Toast.LENGTH_SHORT).show()
                    }
                    tvRequestId.setOnClickListener {
                        copyToClipboard("Request ID", requestId)
                        Toast.makeText(requireContext(), "Request ID copied", Toast.LENGTH_SHORT).show()
                    }

                    // Default placeholders habang naglo-load ng trade_request
                    tvUserA.text = "Trader A"
                    tvUserB.text = "Trader B"
                    tvOfferedItem.text = "Loading..."
                    tvTargetItem.text = "Loading..."
                    tvOfferedCondition.text = "Loading..."
                    tvTargetCondition.text = "Loading..."

                    // Directions (fixed receipt format)
                    tvOfferedFrom.text = "A"
                    tvOfferedTo.text = "B"
                    tvTargetFrom.text = "B"
                    tvTargetTo.text = "A"

                    // Status
                    val status = snap.child("status").getValue(String::class.java).orEmpty().ifBlank { "completed" }
                    val statusText = when (status.lowercase()) {
                        "completed" -> "COMPLETED"
                        "accepted" -> "ACCEPTED"
                        "pending" -> "PENDING"
                        else -> status.uppercase()
                    }
                    tvStatus.text = statusText

                    tvStatus.setTextColor(
                        when (status.lowercase()) {
                            "completed" -> requireContext().getColor(R.color.teal_700)
                            "accepted" -> requireContext().getColor(R.color.colorAccent)
                            "pending" -> requireContext().getColor(R.color.colorPrimary)
                            else -> requireContext().getColor(android.R.color.darker_gray)
                        }
                    )

                    if (requestId.isBlank()) {
                        Log.w(TAG, "⚠️ tradeRequestId missing in receipt: $receiptId")

                        tvOfferedItem.text = "Unknown Item"
                        tvTargetItem.text = "Unknown Item"
                        tvOfferedCondition.text = "Unknown"
                        tvTargetCondition.text = "Unknown"
                        tvFooterNote.text = "✓ This serves as official proof of barter transaction"
                        return@addOnSuccessListener
                    }

                    db.child("trade_requests").child(requestId).get()
                        .addOnSuccessListener { reqSnap ->
                            if (!reqSnap.exists()) {
                                Log.w(TAG, "⚠️ trade_request not found: $requestId")

                                tvOfferedItem.text = "Unknown Item"
                                tvTargetItem.text = "Unknown Item"
                                tvOfferedCondition.text = "Unknown"
                                tvTargetCondition.text = "Unknown"
                                tvFooterNote.text = "✓ This serves as official proof of barter transaction"
                                return@addOnSuccessListener
                            }

                            // Users (fromUser/toUser)
                            val userA = reqSnap.child("fromUser").child("username").getValue(String::class.java)
                                .orEmpty().ifBlank { "Trader A" }
                            val userB = reqSnap.child("toUser").child("username").getValue(String::class.java)
                                .orEmpty().ifBlank { "Trader B" }

                            val fromUserLocation = reqSnap.child("fromUser").child("location").getValue(String::class.java).orEmpty()
                            val toUserLocation = reqSnap.child("toUser").child("location").getValue(String::class.java).orEmpty()

                            tvUserA.text = userA
                            tvUserB.text = userB

                            val offeredTitle = reqSnap.child("offeredItem").child("title").getValue(String::class.java).orEmpty()
                            val targetTitle = reqSnap.child("targetItem").child("title").getValue(String::class.java).orEmpty()

                            val offeredConditionRaw = reqSnap.child("offeredItem").child("condition").getValue(String::class.java).orEmpty()
                            val targetConditionRaw = reqSnap.child("targetItem").child("condition").getValue(String::class.java).orEmpty()

                            val targetItemId = reqSnap.child("targetItem").child("itemId").getValue(String::class.java).orEmpty()

                            tvOfferedItem.text = offeredTitle.ifBlank { "Unknown Item" }
                            tvTargetItem.text = targetTitle.ifBlank { "Unknown Item" }

                            tvOfferedCondition.text = offeredConditionRaw.ifBlank { "Unknown" }

                            val targetIsUnknown = targetConditionRaw.isBlank() || targetConditionRaw.equals("unknown", true)

                            if (!targetIsUnknown) {
                                tvTargetCondition.text = targetConditionRaw
                            } else {
                                tvTargetCondition.text = "Loading..."

                                if (targetItemId.isBlank()) {
                                    tvTargetCondition.text = "Unknown"
                                    Log.w(TAG, "⚠️ targetItemId missing, can't load condition from /items")
                                } else {
                                    db.child("items").child(targetItemId).child("condition").get()
                                        .addOnSuccessListener { itemSnap ->
                                            val realCondition = itemSnap.getValue(String::class.java).orEmpty()
                                            tvTargetCondition.text = realCondition.ifBlank { "Unknown" }
                                            Log.d(TAG, "✅ Fetched target condition from /items: $realCondition")
                                        }
                                        .addOnFailureListener { e ->
                                            tvTargetCondition.text = "Unknown"
                                            Log.e(TAG, "❌ Failed to fetch target condition from /items: ${e.message}")
                                        }
                                }
                            }

                            tvFooterNote.text =
                                if (fromUserLocation.isNotBlank() && toUserLocation.isNotBlank()) {
                                    "✓ Trade completed between $userA ($fromUserLocation) and $userB ($toUserLocation)"
                                } else {
                                    "✓ This serves as official proof of barter transaction"
                                }

                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "❌ Failed to load trade_request: ${e.message}")

                            tvOfferedItem.text = "Unknown Item"
                            tvTargetItem.text = "Unknown Item"
                            tvOfferedCondition.text = "Unknown"
                            tvTargetCondition.text = "Unknown"
                            tvFooterNote.text = "✓ This serves as official proof of barter transaction"
                        }

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error parsing receipt: ${e.message}")
                    Toast.makeText(requireContext(), "Error loading receipt", Toast.LENGTH_SHORT).show()
                }

            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to load receipt: ${e.message}")
                Toast.makeText(requireContext(), "Failed to load receipt", Toast.LENGTH_SHORT).show()
            }
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return "Unknown date"

        return try {
            val sdf = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (_: Exception) {
            "Invalid date"
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

}