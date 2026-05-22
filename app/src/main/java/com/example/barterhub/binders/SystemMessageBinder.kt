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
import com.example.barterhub.managers.TradeNotificationManager
import com.example.barterhub.viewholders.SystemMessageViewHolder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SystemMessageBinder(
    private val currentUserId: String,
    private val chatId: String,
    private val onTradeCompletedListener: ((TradeRequest) -> Unit)? = null,
    private val onMessageUpdated: (() -> Unit)? = null
) : MessageBinder {

    companion object {
        private const val TAG = "SystemMessageBinder"
    }

    private val completionManager = TradeCompletionManager()
    private val notificationManager = TradeNotificationManager()

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

        bindTradeDetails(holder, tradeRequest)

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

            when {
                currentUserRated && partnerRated -> {
                    completionManager.updateTradeStatusToCompleted(
                        currentUserId = currentUserId,
                        chatId = chatId,
                        tradeId = tradeRequest.requestId,
                        messageId = currentMessageId,
                        request = tradeRequest,
                        onCompleted = {
                            showCompletedUI(holder)
                            onTradeCompletedListener?.invoke(tradeRequest)
                            onMessageUpdated?.invoke()
                        },
                        onFailure = { error ->
                            Toast.makeText(holder.itemView.context, error, Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                userClickedCompleted || currentUserRated -> {
                    if (currentUserRated) {
                        showWaitingForPartnerUI(holder, tradeRequest)
                    } else {
                        showRatingUI(holder, tradeRequest, currentMessageId)
                    }
                }

                else -> {
                    showInitialUI(holder, tradeRequest, currentMessageId)
                }
            }
        }
    }

    private fun showLoadingState(holder: SystemMessageViewHolder) {
        holder.tradeActionButtons.visibility = View.GONE
        holder.btnCompleted.visibility = View.GONE
        holder.btnReportIssue.visibility = View.GONE
        holder.ratingContainer.visibility = View.GONE
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
        holder.btnCompleted.visibility = View.VISIBLE
        holder.btnReportIssue.visibility = View.VISIBLE
        holder.ratingContainer.visibility = View.GONE
        holder.waitingText.visibility = View.GONE

        holder.btnCompleted.setOnClickListener {
            completionManager.saveUserClickedCompleted(
                currentUserId = currentUserId,
                tradeId = request.requestId,
                messageId = messageId,
                onSuccess = {
                    notificationManager.notifyCompletedClicked(
                        currentUserId = currentUserId,
                        chatId = chatId,
                        request = request
                    )

                    holder.ratingContainer.visibility = View.VISIBLE
                    holder.btnCompleted.visibility = View.GONE
                    holder.btnReportIssue.visibility = View.GONE

                    setupRating(holder, request, messageId)
                },
                onFailure = { error ->
                    Toast.makeText(holder.itemView.context, error, Toast.LENGTH_SHORT).show()
                }
            )
        }

        holder.btnReportIssue.setOnClickListener {
            openEmailReport(holder.itemView.context, request)
        }
    }

    private fun showRatingUI(
        holder: SystemMessageViewHolder,
        request: TradeRequest,
        messageId: String
    ) {
        holder.tradeReminderWarning.visibility = View.VISIBLE
        holder.tradeActionButtons.visibility = View.GONE
        holder.btnCompleted.visibility = View.GONE
        holder.btnReportIssue.visibility = View.GONE
        holder.ratingContainer.visibility = View.VISIBLE
        holder.waitingText.visibility = View.GONE

        setupRating(holder, request, messageId)
    }

    private fun showWaitingForPartnerUI(
        holder: SystemMessageViewHolder,
        request: TradeRequest
    ) {
        holder.tradeReminderWarning.visibility = View.VISIBLE
        holder.tradeActionButtons.visibility = View.GONE
        holder.ratingContainer.visibility = View.GONE
        holder.btnCompleted.visibility = View.GONE
        holder.btnReportIssue.visibility = View.GONE
        holder.waitingText.visibility = View.VISIBLE

        val partnerName = getPartnerName(request)
        holder.waitingText.text = "✅ You have rated\n⏳ Waiting for $partnerName to rate..."
    }

    private fun showCompletedUI(holder: SystemMessageViewHolder) {
        holder.btnCompleted.visibility = View.GONE
        holder.btnReportIssue.visibility = View.GONE
        holder.tradeActionButtons.visibility = View.GONE
        holder.ratingContainer.visibility = View.GONE
        holder.waitingText.visibility = View.GONE
        holder.tradeReminderWarning.visibility = View.GONE

        holder.acceptedByText.text = "✅ Transaction Completed"
        holder.instructionText.visibility = View.GONE
    }

    private fun bindTradeDetails(
        holder: SystemMessageViewHolder,
        request: TradeRequest
    ) {
        holder.acceptedByText.text = "${request.toUser.username} accepted the trade"
        holder.offeredByText.text = request.fromUser.username
        holder.acceptedByUserText.text = request.toUser.username
        holder.offeredItemText.text = request.offeredItem.title
        holder.targetItemText.text = request.targetItem.title
    }

    private fun setupRating(
        holder: SystemMessageViewHolder,
        request: TradeRequest,
        messageId: String
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val partner = when (currentUser.uid) {
            request.fromUser.userId -> request.toUser
            request.toUser.userId -> request.fromUser
            else -> return
        }

        holder.tvRateUserName.text = "Rate ${partner.username}"
        holder.ratingBar.rating = 0f
        holder.btnSubmitRating.isEnabled = true
        holder.btnSkipRating.isEnabled = true

        holder.btnSubmitRating.setOnClickListener {
            val rating = holder.ratingBar.rating

            if (rating == 0f) {
                Toast.makeText(
                    holder.itemView.context,
                    "Please select a rating",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            holder.btnSubmitRating.isEnabled = false
            holder.btnSkipRating.isEnabled = false

            submitRating(
                holder = holder,
                request = request,
                partner = partner,
                rating = rating,
                comment = "",
                messageId = messageId
            )
        }

        holder.btnSkipRating.setOnClickListener {
            holder.btnSubmitRating.isEnabled = false
            holder.btnSkipRating.isEnabled = false

            submitRating(
                holder = holder,
                request = request,
                partner = partner,
                rating = 0f,
                comment = "Rating skipped",
                messageId = messageId
            )
        }
    }

    private fun submitRating(
        holder: SystemMessageViewHolder,
        request: TradeRequest,
        partner: TradeUser,
        rating: Float,
        comment: String,
        messageId: String
    ) {
        val db = FirebaseDatabase.getInstance().reference
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val reviewId = db.child("reviews").push().key ?: return

        val reviewData = hashMapOf<String, Any>(
            "reviewId" to reviewId,
            "tradeId" to request.requestId,
            "reviewerId" to currentUser.uid,
            "reviewerName" to (currentUser.displayName ?: "Anonymous"),
            "reviewedUserId" to partner.userId,
            "reviewedUserName" to partner.username,
            "rating" to rating,
            "comment" to comment,
            "timestamp" to System.currentTimeMillis()
        )

        db.child("reviews")
            .child(reviewId)
            .setValue(reviewData)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Rating saved")

                notificationManager.notifyRated(
                    currentUserId = currentUserId,
                    chatId = chatId,
                    request = request
                )

                completionManager.checkUserActionStatus(
                    currentUserId = currentUserId,
                    tradeId = request.requestId
                ) { _, currentUserRated, partnerRated ->

                    if (currentUserRated && partnerRated) {
                        completionManager.updateTradeStatusToCompleted(
                            currentUserId = currentUserId,
                            chatId = chatId,
                            tradeId = request.requestId,
                            messageId = messageId,
                            request = request,
                            onCompleted = {
                                showCompletedUI(holder)
                                onTradeCompletedListener?.invoke(request)
                                onMessageUpdated?.invoke()
                            },
                            onFailure = { error ->
                                Toast.makeText(holder.itemView.context, error, Toast.LENGTH_SHORT).show()
                                resetButtons(holder)
                            }
                        )
                    } else {
                        showWaitingForPartnerUI(holder, request)
                        onMessageUpdated?.invoke()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save rating: ${e.message}")
                Toast.makeText(
                    holder.itemView.context,
                    "Failed to submit rating: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                resetButtons(holder)
            }
    }

    private fun resetButtons(holder: SystemMessageViewHolder) {
        holder.btnSubmitRating.isEnabled = true
        holder.btnSkipRating.isEnabled = true
    }

    private fun getPartnerName(request: TradeRequest): String {
        return if (request.fromUser.userId == currentUserId) {
            request.toUser.username
        } else {
            request.fromUser.username
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
            Log.e(TAG, "❌ Error extracting trade request: ${e.message}")
            null
        }
    }

    private fun openEmailReport(context: Context, request: TradeRequest) {
        val partnerName = when (FirebaseAuth.getInstance().currentUser?.uid) {
            request.fromUser.userId -> request.toUser.username
            request.toUser.userId -> request.fromUser.username
            else -> "Unknown"
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