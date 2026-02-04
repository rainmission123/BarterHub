package com.example.barterhub.binders

import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.data.models.Message
import com.example.barterhub.data.models.TradeRequest
import com.example.barterhub.data.models.TradeUser
import com.example.barterhub.data.models.TradeItem
import com.example.barterhub.viewholders.SystemMessageViewHolder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import android.util.Log

class SystemMessageBinder(
    private val currentUserId: String,
    private val chatId: String,
    private val onTradeCompletedListener: ((TradeRequest) -> Unit)? = null,
    private val onMessageUpdated: (() -> Unit)? = null
) : MessageBinder {

    companion object {
        private const val TAG = "SystemMessageBinder"
    }

    override fun bind(holder: RecyclerView.ViewHolder, message: Message, position: Int) {
        if (holder !is SystemMessageViewHolder) return

        Log.d(TAG, "Binding system message: ${message.messageId}")

        val tradeRequest = extractTradeRequestFromMessage(message)
        if (tradeRequest == null) {
            Log.e(TAG, "Trade request is null")
            return
        }

        // ✅ Step 1: Check current status (BOTH ratings AND if user clicked completed)
        checkUserActionStatus(tradeRequest.requestId, message.messageId) {
                userClickedCompleted, currentUserRated, partnerRated ->

            Log.d(TAG, "Status - Clicked: $userClickedCompleted, Current rated: $currentUserRated, Partner rated: $partnerRated")

            val isCompleted = message.messageType == "system_trade_completed"
                    || (tradeRequest.status == "Completed")

            if (isCompleted) {
                // ✅ ALREADY COMPLETED
                showCompletedUI(holder)
                holder.acceptedByText.text = "✅ Transaction Completed"
                return@checkUserActionStatus
            }

            // ✅ Step 2: Bind trade details
            bindTradeDetails(holder, tradeRequest)

            // ✅ Step 3: Show appropriate UI
            if (currentUserRated && partnerRated) {
                // ✅ BOTH HAVE RATED - Update to completed
                updateTradeStatusToCompleted(tradeRequest.requestId, message.messageId, tradeRequest)
                showCompletedUI(holder)
            } else if (userClickedCompleted || currentUserRated) {
                // ✅ User already clicked completed OR already rated
                if (currentUserRated) {
                    showWaitingForPartnerUI(holder, tradeRequest)
                } else {
                    showRatingUI(holder, tradeRequest, message.messageId)
                }
            } else {
                // ✅ User hasn't done anything yet
                showInitialUI(holder, tradeRequest, message.messageId)
            }
        }
    }

    private fun checkUserActionStatus(tradeId: String, messageId: String,
                                      callback: (Boolean, Boolean, Boolean) -> Unit) {
        val db = FirebaseDatabase.getInstance().reference

        // ✅ Check 1: If user already clicked "Completed" button
        db.child("user_actions").child(tradeId).child(currentUserId).child("clicked_completed")
            .get().addOnSuccessListener { clickedSnapshot ->
                val userClickedCompleted = clickedSnapshot.getValue(Boolean::class.java) ?: false

                // ✅ Check 2: Rating status
                db.child("reviews").orderByChild("tradeId").equalTo(tradeId)
                    .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                        override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                            var currentUserRated = false
                            var partnerRated = false

                            for (reviewSnap in snapshot.children) {
                                val reviewerId = reviewSnap.child("reviewerId").getValue(String::class.java)
                                val reviewedUserId = reviewSnap.child("reviewedUserId").getValue(String::class.java)

                                if (reviewerId == currentUserId) {
                                    currentUserRated = true
                                }

                                if (reviewerId != null && reviewerId != currentUserId && reviewedUserId == currentUserId) {
                                    partnerRated = true
                                }
                            }

                            callback(userClickedCompleted, currentUserRated, partnerRated)
                        }

                        override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                            Log.e(TAG, "Error checking reviews: ${error.message}")
                            callback(false, false, false)
                        }
                    })
            }.addOnFailureListener {
                callback(false, false, false)
            }
    }

    private fun showInitialUI(holder: SystemMessageViewHolder, request: TradeRequest, messageId: String) {
        // ✅ Show initial UI with "Completed" button
        holder.tradeReminderWarning.visibility = View.VISIBLE
        holder.tradeActionButtons.visibility = View.VISIBLE
        holder.btnCompleted.visibility = View.VISIBLE
        holder.btnReportIssue.visibility = View.VISIBLE
        holder.ratingContainer.visibility = View.GONE
        holder.waitingText.visibility = View.GONE

        holder.btnCompleted.setOnClickListener {
            Log.d(TAG, "Completed button clicked - Saving action to Firebase")

            // ✅ STEP 1: Save that user clicked "Completed"
            saveUserClickedCompleted(request.requestId, messageId)

            // ✅ STEP 2: Show rating UI
            holder.ratingContainer.visibility = View.VISIBLE
            holder.btnCompleted.visibility = View.GONE
            holder.btnReportIssue.visibility = View.GONE
            setupRating(holder, request, messageId)
        }

        holder.btnReportIssue.setOnClickListener {
            openEmailReport(holder.itemView.context, request)
        }
    }

    private fun saveUserClickedCompleted(tradeId: String, messageId: String) {
        val db = FirebaseDatabase.getInstance().reference

        val actionData = hashMapOf<String, Any>(
            "clicked_completed" to true,
            "timestamp" to System.currentTimeMillis(),
            "messageId" to messageId
        )

        db.child("user_actions").child(tradeId).child(currentUserId).setValue(actionData)
            .addOnSuccessListener {
                Log.d(TAG, "User action saved: clicked_completed = true")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save user action: ${e.message}")
            }
    }

    private fun showRatingUI(holder: SystemMessageViewHolder, request: TradeRequest, messageId: String) {
        // ✅ User already clicked "Completed" - Show rating UI directly
        holder.tradeReminderWarning.visibility = View.VISIBLE
        holder.tradeActionButtons.visibility = View.GONE
        holder.btnCompleted.visibility = View.GONE
        holder.btnReportIssue.visibility = View.GONE
        holder.ratingContainer.visibility = View.VISIBLE
        holder.waitingText.visibility = View.GONE

        setupRating(holder, request, messageId)
    }

    private fun showWaitingForPartnerUI(holder: SystemMessageViewHolder, request: TradeRequest) {
        // ✅ Current user already rated, waiting for partner
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

    private fun getPartnerName(request: TradeRequest): String {
        return if (request.fromUser.userId == currentUserId) {
            request.toUser.username
        } else {
            request.fromUser.username
        }
    }

    private fun bindTradeDetails(holder: SystemMessageViewHolder, request: TradeRequest) {
        try {
            // ✅ Use actual data from the trade request
            holder.acceptedByText.text = "${request.toUser.username} accepted the trade"
            holder.offeredByText.text = request.fromUser.username
            holder.acceptedByUserText.text = request.toUser.username
            holder.offeredItemText.text = request.offeredItem.title
            holder.targetItemText.text = request.targetItem.title

            Log.d(TAG, "Binding details - From: ${request.fromUser.username}, To: ${request.toUser.username}")
            Log.d(TAG, "Items - Offered: ${request.offeredItem.title}, Target: ${request.targetItem.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error binding trade details: ${e.message}")
        }
    }

    private fun setupRating(holder: SystemMessageViewHolder, request: TradeRequest, messageId: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        // Determine trade partner
        val partner = when (currentUser.uid) {
            request.fromUser.userId -> request.toUser
            request.toUser.userId -> request.fromUser
            else -> return
        }

        holder.tvRateUserName.text = "Rate ${partner.username}"

        // Reset rating bar
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

            // Prevent double click
            holder.btnSubmitRating.isEnabled = false
            holder.btnSkipRating.isEnabled = false

            submitRating(holder, request, partner, rating, "", messageId)
        }

        holder.btnSkipRating.setOnClickListener {
            holder.btnSubmitRating.isEnabled = false
            holder.btnSkipRating.isEnabled = false
            submitRating(holder, request, partner, 0f, "Rating skipped", messageId)
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

        Log.d(TAG, "Submitting rating for trade: ${request.requestId}")

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

        // ✅ STEP 1: Save the rating
        db.child("reviews").child(reviewId).setValue(reviewData)
            .addOnSuccessListener {
                Log.d(TAG, "Rating saved successfully for ${currentUser.uid}")

                // ✅ STEP 2: Check if BOTH have rated
                checkUserActionStatus(request.requestId, messageId) { _, currentUserRated, partnerRated ->
                    Log.d(TAG, "After rating - Current rated: $currentUserRated, Partner rated: $partnerRated")

                    if (currentUserRated && partnerRated) {
                        // ✅ BOTH HAVE RATED - Mark as completed
                        Log.d(TAG, "BOTH HAVE RATED! Marking as completed...")
                        updateTradeStatusToCompleted(request.requestId, messageId, request)
                    } else {
                        // ✅ Only one has rated - Show waiting UI
                        Log.d(TAG, "Only one has rated. Showing waiting UI...")
                        showWaitingForPartnerUI(holder, request)

                        // Refresh the binder to update UI
                        onMessageUpdated?.invoke()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save rating: ${e.message}")
                Toast.makeText(
                    holder.itemView.context,
                    "Failed to submit rating: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                resetButtons(holder)
            }
    }

    private fun updateTradeStatusToCompleted(tradeId: String, messageId: String, request: TradeRequest) {
        val db = FirebaseDatabase.getInstance().reference

        Log.d(TAG, "Updating trade $tradeId to Completed")

        // ✅ STEP 1: Update trade status in trade_requests
        db.child("trade_requests").child(tradeId).child("status")
            .setValue("Completed")
            .addOnSuccessListener {
                Log.d(TAG, "Trade status updated to Completed")

                // ✅ STEP 2: Increment tradesCompleted for both users
                incrementTradesCompleted(request.fromUser.userId)
                incrementTradesCompleted(request.toUser.userId)

                // ✅ STEP 3: Update the system message in Firebase
                val updatedTradeDetails = hashMapOf<String, Any>(
                    "tradeRequestId" to tradeId,
                    "fromUserId" to request.fromUser.userId,
                    "toUserId" to request.toUser.userId,
                    "offeredBy" to request.fromUser.username,
                    "acceptedBy" to request.toUser.username,
                    "offeredItemName" to request.offeredItem.title,
                    "targetItemName" to request.targetItem.title,
                    "status" to "Completed"
                )

                val updates = hashMapOf<String, Any>(
                    "messageType" to "system_trade_completed",
                    "tradeDetails" to updatedTradeDetails,
                    "text" to "Transaction Completed! ✅",
                    "isSystemMessage" to true
                )

                db.child("chats").child(chatId).child("messages").child(messageId)
                    .updateChildren(updates)
                    .addOnSuccessListener {
                        Log.d(TAG, "System message updated to completed in Firebase")

                        // ✅ STEP 4: Call the listeners to refresh UI
                        onTradeCompletedListener?.invoke(request)
                        onMessageUpdated?.invoke()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update system message: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update trade status: ${e.message}")
            }
    }

    // 🔹 Increment tradesCompleted for a given user
    private fun incrementTradesCompleted(userId: String) {
        val userRef = FirebaseDatabase.getInstance().reference.child("users/$userId")
        userRef.child("tradesCompleted").runTransaction(object : com.google.firebase.database.Transaction.Handler {
            override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                val currentCount = currentData.getValue(Int::class.java) ?: 0
                currentData.value = currentCount + 1
                return com.google.firebase.database.Transaction.success(currentData)
            }

            override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, snapshot: com.google.firebase.database.DataSnapshot?) {
                if (error != null) {
                    Log.e(TAG, "Failed to increment tradesCompleted for $userId: ${error.message}")
                } else {
                    Log.d(TAG, "Successfully incremented tradesCompleted for $userId")
                }
            }
        })
    }


    private fun resetButtons(holder: SystemMessageViewHolder) {
        holder.btnSubmitRating.isEnabled = true
        holder.btnSkipRating.isEnabled = true
    }

    private fun extractTradeRequestFromMessage(message: Message): TradeRequest? {
        return try {
            val tradeDetails = message.tradeDetails as? Map<*, *> ?: return null

            Log.d(TAG, "Extracting trade details: $tradeDetails")

            TradeRequest(
                requestId = tradeDetails["tradeRequestId"] as? String ?: message.messageId ?: "",
                fromUser = TradeUser(
                    userId = tradeDetails["fromUserId"] as? String ?: "",
                    username = tradeDetails["offeredBy"] as? String ?: "Unknown User"
                ),
                toUser = TradeUser(
                    userId = tradeDetails["toUserId"] as? String ?: "",
                    username = tradeDetails["acceptedBy"] as? String ?: "Unknown User"
                ),
                offeredItem = TradeItem(
                    itemId = tradeDetails["offeredItemId"] as? String ?: "",
                    title = tradeDetails["offeredItemName"] as? String ?: "Unknown Item",
                    description = tradeDetails["offeredItemDescription"] as? String ?: "",
                    image = tradeDetails["offeredItemImage"] as? String ?: "",
                    category = tradeDetails["offeredItemCategory"] as? String ?: "Unknown"
                ),
                targetItem = TradeItem(
                    itemId = tradeDetails["targetItemId"] as? String ?: "",
                    title = tradeDetails["targetItemName"] as? String ?: "Unknown Item",
                    description = tradeDetails["targetItemDescription"] as? String ?: "",
                    image = tradeDetails["targetItemImage"] as? String ?: "",
                    category = tradeDetails["targetItemCategory"] as? String ?: "Unknown"
                ),
                status = tradeDetails["status"] as? String ?: "Pending"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting trade request: ${e.message}")
            null
        }
    }

    private fun openEmailReport(context: android.content.Context, request: TradeRequest) {
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

        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("mailto:$email")
            putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
            putExtra(android.content.Intent.EXTRA_TEXT, body)
        }

        try {
            context.startActivity(
                android.content.Intent.createChooser(intent, "Send Report")
            )
        } catch (_: Exception) {
            Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
        }
    }
}