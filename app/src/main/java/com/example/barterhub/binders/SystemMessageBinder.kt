package com.example.barterhub.binders

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.data.models.Message
import com.example.barterhub.data.models.TradeItem
import com.example.barterhub.data.models.TradeRequest
import com.example.barterhub.data.models.TradeUser
import com.example.barterhub.managers.TradeCompletionManager
import com.example.barterhub.viewholders.SystemMessageViewHolder
import com.google.firebase.database.FirebaseDatabase

class SystemMessageBinder(
    private val currentUserId: String,
    private val chatId: String,
    private val onTradeCompletedListener: ((TradeRequest) -> Unit)? = null,
    private val onMessageUpdated: (() -> Unit)? = null,
    private val onRatingCommentFocusChanged: ((Boolean) -> Unit)? = null
) : MessageBinder {

    companion object {
        private const val TAG = "SystemMessageBinder"
    }

    private val completionManager = TradeCompletionManager()

    override fun bind(holder: RecyclerView.ViewHolder, message: Message, position: Int) {
        if (holder !is SystemMessageViewHolder) return

        val currentMessageId = message.messageId
        holder.itemView.tag = currentMessageId

        showLoadingState(holder)

        val tradeRequest = extractTradeRequestFromMessage(message)

        if (tradeRequest == null) {
            holder.acceptedByText.text = "Trade not available"
            holder.instructionText.visibility = View.GONE
            Log.e(TAG, "Trade request is null")
            return
        }

        bindTradeDetails(holder, tradeRequest, currentMessageId)

        val statusFromDetails = (message.tradeDetails as? Map<*, *>)?.get("status") as? String

        val isCompleted =
            message.messageType == "system_trade_completed" ||
                    tradeRequest.status.equals("Completed", true) ||
                    statusFromDetails.equals("Completed", true)

        if (isCompleted) {
            showCompletedUI(holder)
            return
        }

        completionManager.checkUserActionStatus(
            currentUserId = currentUserId,
            tradeId = tradeRequest.requestId
        ) { userClickedCompleted, currentUserRated, partnerRated ->

            if (holder.itemView.tag != currentMessageId) {
                Log.d(TAG, "Holder reused, ignoring old callback")
                return@checkUserActionStatus
            }

            if (userClickedCompleted) {
                showWaitingUI(holder)
            } else {
                showInitialUI(holder, tradeRequest, currentMessageId)
            }
        }
    }

    private fun showLoadingState(holder: SystemMessageViewHolder) {
        holder.tradeActionButtons.visibility = View.GONE
        holder.btnCompleted.visibility = View.GONE
        holder.btnReportIssue.visibility = View.GONE
        holder.waitingText.visibility = View.GONE
        holder.tradeReminderWarning.visibility = View.VISIBLE
        holder.instructionText.visibility = View.VISIBLE
    }

    private fun showInitialUI(
        holder: SystemMessageViewHolder,
        request: TradeRequest,
        messageId: String
    ) {
        holder.tradeReminderWarning.visibility = View.VISIBLE
        holder.instructionText.visibility = View.VISIBLE
        holder.tradeActionButtons.visibility = View.VISIBLE
        holder.waitingText.visibility = View.GONE
        holder.btnCompleted.visibility = View.VISIBLE
        holder.btnCompleted.isEnabled = true
        holder.btnCompleted.alpha = 1f

        holder.btnReportIssue.visibility = View.VISIBLE
        holder.btnReportIssue.isEnabled = true
        holder.btnReportIssue.alpha = 1f

        holder.btnCompleted.setOnClickListener {
            holder.btnCompleted.isEnabled = false

            completionManager.confirmTradeCompletion(
                tradeId = request.requestId,
                chatId = chatId,
                messageId = messageId,
                onSuccess = { result ->
                    holder.btnCompleted.visibility = View.GONE
                    holder.btnReportIssue.visibility = View.GONE
                    holder.tradeActionButtons.visibility = View.GONE

                    if (result.completed) {
                        showCompletedUI(holder)
                        onTradeCompletedListener?.invoke(request.copy(status = "Completed"))
                    } else {
                        showWaitingUI(holder)
                    }

                    onMessageUpdated?.invoke()
                },
                onFailure = { error ->
                    holder.btnCompleted.isEnabled = true
                    Toast.makeText(holder.itemView.context, error, Toast.LENGTH_SHORT).show()
                }
            )
        }

        holder.btnReportIssue.setOnClickListener {
            openEmailReport(holder.itemView.context, request)
        }
    }

    private fun showWaitingUI(holder: SystemMessageViewHolder) {
        holder.btnCompleted.visibility = View.GONE
        holder.btnReportIssue.visibility = View.GONE
        holder.tradeActionButtons.visibility = View.GONE
        holder.tradeReminderWarning.visibility = View.GONE
        holder.instructionText.visibility = View.VISIBLE
        holder.waitingText.visibility = View.VISIBLE

        holder.acceptedByText.text = "Waiting for partner confirmation"
        holder.waitingText.text = "Waiting for your barter partner to confirm completion."
    }

    private fun showCompletedUI(holder: SystemMessageViewHolder) {
        holder.btnCompleted.visibility = View.GONE
        holder.btnReportIssue.visibility = View.GONE
        holder.tradeActionButtons.visibility = View.GONE
        holder.waitingText.visibility = View.GONE
        holder.tradeReminderWarning.visibility = View.GONE

        holder.acceptedByText.text = "Transaction Completed"
        holder.instructionText.visibility = View.GONE
    }

    private fun bindTradeDetails(
        holder: SystemMessageViewHolder,
        request: TradeRequest,
        messageId: String
    ) {
        holder.acceptedByText.text = "${request.toUser.username} accepted the trade"
        holder.offeredByText.text = request.fromUser.username
        holder.acceptedByUserText.text = request.toUser.username
        holder.offeredItemText.text = request.offeredItem.title
        holder.targetItemText.text = request.targetItem.title

        ChatDisplayNameResolver.resolve(
            uid = request.fromUser.userId,
            fallbackUsername = request.fromUser.username
        ) { fromDisplayName ->
            if (holder.itemView.tag != messageId) return@resolve
            holder.offeredByText.text = fromDisplayName
        }

        ChatDisplayNameResolver.resolve(
            uid = request.toUser.userId,
            fallbackUsername = request.toUser.username
        ) { toDisplayName ->
            if (holder.itemView.tag != messageId) return@resolve
            holder.acceptedByText.text = "$toDisplayName accepted the trade"
            holder.acceptedByUserText.text = toDisplayName
        }
    }

    private fun extractTradeRequestFromMessage(message: Message): TradeRequest? {
        return try {
            val tradeDetails = message.tradeDetails as? Map<*, *> ?: return null

            TradeRequest(
                requestId = tradeDetails["tradeRequestId"] as? String ?: message.messageId,

                fromUser = TradeUser(
                    userId = tradeDetails["fromUserId"] as? String ?: "",
                    username = tradeDetails["offeredBy"] as? String ?: "Unknown User",
                    profileImage = tradeDetails["fromUserProfileImage"] as? String ?: "",
                    location = tradeDetails["fromUserLocation"] as? String ?: "",
                    rating = (tradeDetails["fromUserRating"] as? Double) ?: 0.0
                ),

                toUser = TradeUser(
                    userId = tradeDetails["toUserId"] as? String ?: "",
                    username = tradeDetails["acceptedBy"] as? String ?: "Unknown User",
                    profileImage = tradeDetails["toUserProfileImage"] as? String ?: "",
                    location = tradeDetails["toUserLocation"] as? String ?: "",
                    rating = (tradeDetails["toUserRating"] as? Double) ?: 0.0
                ),

                offeredItem = TradeItem(
                    itemId = tradeDetails["offeredItemId"] as? String ?: "",
                    title = tradeDetails["offeredItemName"] as? String ?: "Unknown Item",
                    description = tradeDetails["offeredItemDescription"] as? String ?: "",
                    image = tradeDetails["offeredItemImage"] as? String ?: "",
                    category = tradeDetails["offeredItemCategory"] as? String ?: "Unknown",
                    condition = tradeDetails["offeredItemCondition"] as? String ?: "Unknown"
                ),

                targetItem = TradeItem(
                    itemId = tradeDetails["targetItemId"] as? String ?: "",
                    title = tradeDetails["targetItemName"] as? String ?: "Unknown Item",
                    description = tradeDetails["targetItemDescription"] as? String ?: "",
                    image = tradeDetails["targetItemImage"] as? String ?: "",
                    category = tradeDetails["targetItemCategory"] as? String ?: "Unknown",
                    condition = tradeDetails["targetItemCondition"] as? String ?: "Unknown"
                ),

                status = tradeDetails["status"] as? String ?: "Accepted",
                message = tradeDetails["message"] as? String ?: "",
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting trade request: ${e.message}")
            null
        }
    }

    private fun openEmailReport(context: Context, request: TradeRequest) {
        val partnerName = if (request.fromUser.userId == currentUserId) {
            request.toUser.username
        } else {
            request.fromUser.username
        }

        val email = "barterhubph.support@gmail.com"
        val subject = "Issue Report - Trade ${request.requestId}"
        val body = """
            Trade Partner: $partnerName
            Offered Item: ${request.offeredItem.title}
            Target Item: ${request.targetItem.title}
            
            Please describe the issue below:
            
            
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Send Report"))
        } catch (_: Exception) {
            Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
        }
    }
}

internal object ChatDisplayNameResolver {
    private const val DATABASE_URL = "https://barterhub-3c947-default-rtdb.firebaseio.com/"
    private val database = FirebaseDatabase.getInstance(DATABASE_URL).reference
    private val cache = mutableMapOf<String, String>()

    fun resolve(uid: String, fallbackUsername: String, onResolved: (String) -> Unit) {
        val fallback = fallbackUsername.ifBlank { "Trade partner" }
        if (uid.isBlank()) {
            onResolved(fallback)
            return
        }

        cache[uid]?.let {
            onResolved(it)
            return
        }

        database.child("public_users").child(uid).child("fullName").get()
            .addOnSuccessListener { publicSnapshot ->
                val publicName = publicSnapshot.getValue(String::class.java).orEmpty().trim()
                if (publicName.isNotBlank()) {
                    cache[uid] = publicName
                    onResolved(publicName)
                    return@addOnSuccessListener
                }

                resolveUserFullName(uid, fallback, onResolved)
            }
            .addOnFailureListener {
                resolveUserFullName(uid, fallback, onResolved)
            }
    }

    private fun resolveUserFullName(uid: String, fallback: String, onResolved: (String) -> Unit) {
        database.child("users").child(uid).child("fullName").get()
            .addOnSuccessListener { userSnapshot ->
                val userName = userSnapshot.getValue(String::class.java).orEmpty().trim()
                val displayName = userName.ifBlank { fallback }
                cache[uid] = displayName
                onResolved(displayName)
            }
            .addOnFailureListener {
                cache[uid] = fallback
                onResolved(fallback)
            }
    }
}
