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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.barterhub.data.models.TradeHistoryItem

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

        holder.itemView.tag = message.messageId

        Log.d(TAG, "Binding system message: ${message.messageId}")

        showLoadingState(holder)

        val tradeRequest = extractTradeRequestFromMessage(message)
        if (tradeRequest == null) {
            Log.e(TAG, "Trade request is null")
            holder.acceptedByText.text = "Trade not available"
            return
        }

        bindTradeDetails(holder, tradeRequest)

        val statusFromDetails = (message.tradeDetails as? Map<*, *>)?.get("status") as? String
        val isCompletedFast =
            message.messageType == "system_trade_completed" ||
                    tradeRequest.status.equals("Completed", true) ||
                    (statusFromDetails?.equals("Completed", true) == true)


        if (isCompletedFast) {
            showCompletedUI(holder)
            return
        }

        val currentMessageId = message.messageId
        holder.itemView.tag = currentMessageId

        checkUserActionStatus(tradeRequest.requestId) {
                userClickedCompleted, currentUserRated, partnerRated ->

            // ✅ IMPORTANT: stop if holder was recycled
            if (holder.itemView.tag != currentMessageId) {
                Log.d(TAG, "Holder reused, ignoring old callback")
                return@checkUserActionStatus
            }

            Log.d(TAG, "Status - Clicked: $userClickedCompleted, Current rated: $currentUserRated, partner rated: $partnerRated")

            val statusNow = (message.tradeDetails as? Map<*, *>)?.get("status") as? String
            val isCompletedNow =
                message.messageType == "system_trade_completed" ||
                        tradeRequest.status.equals("Completed", true) ||
                        (statusNow?.equals("Completed", true) == true)

            if (isCompletedNow) {
                showCompletedUI(holder)
                return@checkUserActionStatus
            }

            if (currentUserRated && partnerRated) {
                updateTradeStatusToCompleted(tradeRequest.requestId, message.messageId, tradeRequest)
                showCompletedUI(holder)
            } else if (userClickedCompleted || currentUserRated) {
                if (currentUserRated) {
                    showWaitingForPartnerUI(holder, tradeRequest)
                } else {
                    showRatingUI(holder, tradeRequest, message.messageId)
                }
            } else {
                showInitialUI(holder, tradeRequest, message.messageId)
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

    private fun checkUserActionStatus(tradeId: String,
                                      callback: (Boolean, Boolean, Boolean) -> Unit) {
        val db = FirebaseDatabase.getInstance().reference

        // ✅ Check 1: If user already clicked "Completed" button
        db.child("user_actions").child(tradeId).child(currentUserId).child("clicked_completed")
            .get().addOnSuccessListener { clickedSnapshot ->
                val userClickedCompleted = clickedSnapshot.getValue(Boolean::class.java) ?: false

                // ✅ Check 2: Rating status
                db.child("reviews").orderByChild("tradeId").equalTo(tradeId)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
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

                        override fun onCancelled(error: DatabaseError) {
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
                checkUserActionStatus(request.requestId) { _, currentUserRated, partnerRated ->
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

        // 🔴 CHECK MUNA KUNG COMPLETED NA
        db.child("trade_requests").child(tradeId).child("status").get()
            .addOnSuccessListener { snapshot ->
                val currentStatus = snapshot.getValue(String::class.java)
                if (currentStatus == "Completed") {
                    Log.d(TAG, "⚠️ Trade $tradeId already completed, skipping status update...")

                    // ✅ Check na lang kung may receipt at notifications
                    ensureReceiptExists(request)  // <-- Hindi na ito gagawa ng bagong receipt
                    return@addOnSuccessListener
                }

                // Proceed with completion
                proceedWithTradeCompletion(tradeId, messageId, request)
            }
    }

    private fun proceedWithTradeCompletion(tradeId: String, messageId: String, request: TradeRequest) {
        val db = FirebaseDatabase.getInstance().reference

        db.child("trade_requests").child(tradeId).child("status")
            .setValue("Completed")
            .addOnSuccessListener {
                Log.d(TAG, "Trade status updated to Completed")
                saveTradeHistory(request)

                updateUserTradeStats(request.fromUser.userId, request.toUser.userId, tradeId)
                createTradeReceipt(request)

                // Update system message
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

    private fun ensureReceiptExists(request: TradeRequest) {
        val db = FirebaseDatabase.getInstance().reference

        Log.d(TAG, "🔍 ensureReceiptExists() for trade: ${request.requestId}")

        // Mag-check muna sa receipts node directly
        db.child("receipts").orderByChild("tradeRequestId").equalTo(request.requestId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // May receipt na, kunin ang ID
                        var receiptId = ""
                        for (receipt in snapshot.children) {
                            receiptId = receipt.key ?: continue
                            Log.d(TAG, "✅ Found receipt: $receiptId for trade ${request.requestId}")
                            break
                        }
                        if (receiptId.isNotEmpty()) {
                            checkAndSendMissingNotifications(request, receiptId)
                        } else {
                            Log.e(TAG, "❌ Receipt exists but no ID found!")
                        }
                    } else {
                        Log.d(TAG, "⚠️ No receipt found for trade ${request.requestId}")
                        // 🚨 HUWAG NG TUMAWAG NG createTradeReceipt() DITO!
                        // ITO ANG NAGIGING DAHILAN NG DOUBLE RECEIPT!
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "❌ Error checking receipts: ${error.message}")
                    // Huwag na ring gumawa ng receipt dito
                }
            })
    }

    private fun updateUserTradeStats(userAId: String, userBId: String, tradeId: String) {

        // Update both users
        updateSingleUserTradeStats(userAId, tradeId)
        updateSingleUserTradeStats(userBId, tradeId)
    }

    private fun updateSingleUserTradeStats(userId: String, tradeId: String) {
        val db = FirebaseDatabase.getInstance().reference
        val userRef = db.child("users").child(userId)

        // Run transaction para safe ang increment
        userRef.child("tradesCompleted").runTransaction(object : com.google.firebase.database.Transaction.Handler {
            override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                val currentTrades = currentData.getValue(Int::class.java) ?: 0
                currentData.value = currentTrades + 1
                return com.google.firebase.database.Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null) {
                    Log.e(TAG, "Failed to increment tradesCompleted: ${error.message}")
                    return
                }

                // After updating tradesCompleted, recalculate success rate
                recalculateSuccessRate(userId)

                // Save this trade to user's trade history
                saveTradeToUserHistory(userId, tradeId)
            }
        })
    }

    private fun recalculateSuccessRate(userId: String) {
        val db = FirebaseDatabase.getInstance().reference
        val userRef = db.child("users").child(userId)

        // Get user's trade stats
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tradesCompleted = snapshot.child("tradesCompleted").getValue(Int::class.java) ?: 0
                val totalTrades = snapshot.child("totalTrades").getValue(Int::class.java) ?: tradesCompleted
                val failedTrades = snapshot.child("failedTrades").getValue(Int::class.java) ?: 0

                // Calculate success rate: (completed trades) / (total trades) * 100
                // Kung walang totalTrades, gamitin ang tradesCompleted as denominator
                val denominator = if (totalTrades > 0) totalTrades else tradesCompleted + failedTrades
                val successRate = if (denominator > 0) {
                    ((tradesCompleted.toDouble() / denominator.toDouble()) * 100).toInt()
                } else {
                    100 // Default if no trades
                }

                // Save success rate
                userRef.child("successRate").setValue(successRate)
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ Success rate updated for user $userId: $successRate%")
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to recalculate success rate: ${error.message}")
            }
        })
    }

    private fun saveTradeToUserHistory(userId: String, tradeId: String) {
        val db = FirebaseDatabase.getInstance().reference
        db.child("users").child(userId).child("tradeHistory").child(tradeId)
            .setValue(true)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Trade $tradeId saved to user $userId history")
            }
    }

    private fun createTradeReceipt(request: TradeRequest) {
        val db = FirebaseDatabase.getInstance().reference
        val receiptId = db.child("receipts").push().key ?: return

        db.child("receipts_by_trade").child(request.requestId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    // May receipt na! Wag na gumawa ng bago
                    val existingReceiptId = snapshot.value.toString()
                    Log.d(TAG, "⚠️ Receipt already exists for trade ${request.requestId}: $existingReceiptId")
                    Log.d(TAG, "⚠️ Skipping duplicate receipt creation")

                    // Pero i-check pa rin kung may notifications na
                    checkAndSendMissingNotifications(request, existingReceiptId)
                    return@addOnSuccessListener
                }

                createNewReceipt(request, receiptId)
            }
    }

    private fun createNewReceipt(request: TradeRequest, receiptId: String) {
        val db = FirebaseDatabase.getInstance().reference
        val timestamp = System.currentTimeMillis()
        val receiptNo = generateReceiptNumber()
        val chatDisplayId = generateChatDisplayId()
        val requestDisplayId = generateRequestDisplayId()

        Log.d(TAG, "========== 🧾 CREATING NEW RECEIPT ==========")
        Log.d(TAG, "Receipt ID: $receiptId")
        Log.d(TAG, "Trade ID: ${request.requestId}")
        Log.d(TAG, "Called by: $currentUserId")

        val receiptData = hashMapOf<String, Any>(
            "receiptId" to receiptId,
            "receiptNo" to receiptNo,
            "chatDisplayId" to chatDisplayId,
            "requestDisplayId" to requestDisplayId,
            "chatId" to chatId,
            "tradeRequestId" to request.requestId,
            "timestamp" to timestamp,
            "completedAt" to timestamp,
            "status" to "completed",
            "fromUserId" to request.fromUser.userId,
            "offeredBy" to request.fromUser.username,
            "fromUserProfileImage" to request.fromUser.profileImage,
            "fromUserLocation" to request.fromUser.location,
            "toUserId" to request.toUser.userId,
            "acceptedBy" to request.toUser.username,
            "toUserProfileImage" to request.toUser.profileImage,
            "toUserLocation" to request.toUser.location,
            "offeredItemId" to request.offeredItem.itemId,
            "offeredItemName" to request.offeredItem.title,
            "offeredItemDescription" to request.offeredItem.description,
            "offeredItemImage" to request.offeredItem.image,
            "offeredItemCategory" to request.offeredItem.category,
            "offeredItemCondition" to request.offeredItem.condition,
            "targetItemId" to request.targetItem.itemId,
            "targetItemName" to request.targetItem.title,
            "targetItemDescription" to request.targetItem.description,
            "targetItemImage" to request.targetItem.image,
            "targetItemCategory" to request.targetItem.category,
            "targetItemCondition" to request.targetItem.condition
        )

        val updates = hashMapOf<String, Any>(
            "receipts/$receiptId" to receiptData,
            "receipts_by_trade/${request.requestId}" to receiptId
        )

        db.updateChildren(updates)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Receipt saved: $receiptId")
                Log.d(TAG, "✅ receipts_by_trade/${request.requestId} = $receiptId")

                // Save receipt reference to users
                saveReceiptToUser(request.fromUser.userId, receiptId)
                saveReceiptToUser(request.toUser.userId, receiptId)

                // Send notification to both users
                pushReceiptNotification(request, receiptId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to create receipt: ${e.message}")
            }
    }

    private fun checkAndSendMissingNotifications(request: TradeRequest, receiptId: String) {
        val db = FirebaseDatabase.getInstance().reference
        val notifId = "receipt_$receiptId"

        Log.d(TAG, "🔍 CHECKING NOTIFICATIONS FOR RECEIPT: $receiptId")
        Log.d(TAG, "🔍 Expected notifId: $notifId")

        db.child("notifications").child(request.fromUser.userId).child(notifId).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    Log.d(TAG, "⚠️ Notification missing for ${request.fromUser.username}, sending...")
                    sendNotificationToUser(request.fromUser.userId, request.toUser, request, receiptId)
                } else {
                    Log.d(TAG, "✅ Notification already exists for ${request.fromUser.username}")
                }
            }

        db.child("notifications").child(request.toUser.userId).child(notifId).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    Log.d(TAG, "⚠️ Notification missing for ${request.toUser.username}, sending...")
                    sendNotificationToUser(request.toUser.userId, request.fromUser, request, receiptId)
                } else {
                    Log.d(TAG, "✅ Notification already exists for ${request.toUser.username}")
                }
            }
    }

    private fun sendNotificationToUser(userId: String, partner: TradeUser, request: TradeRequest, receiptId: String) {
        val db = FirebaseDatabase.getInstance().reference
        val notifId = "receipt_$receiptId"

        val notification = hashMapOf<String, Any>(
            "id" to notifId,
            "type" to "receipt_created",
            "receiptId" to receiptId,
            "requestId" to request.requestId,
            "chatId" to chatId,
            "partnerId" to partner.userId,
            "partnerName" to partner.username,
            "message" to "✅ Transaction completed! Receipt is ready.",
            "timestamp" to System.currentTimeMillis(),
            "read" to false
        )

        db.child("notifications").child(userId).child(notifId)
            .setValue(notification)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Missing notification sent to $userId")
            }
    }

    private fun saveReceiptToUser(userId: String, receiptId: String) {
        FirebaseDatabase.getInstance().reference
            .child("users").child(userId).child("receipts").child(receiptId)
            .setValue(true)
    }

    private fun pushReceiptNotification(request: TradeRequest, receiptId: String) {
        val db = FirebaseDatabase.getInstance().reference
        val timestamp = System.currentTimeMillis()

        val notifAId = "receipt_$receiptId"
        val notifA = hashMapOf<String, Any>(
            "id" to notifAId,
            "type" to "receipt_created",
            "receiptId" to receiptId,
            "requestId" to request.requestId,
            "chatId" to chatId,
            "partnerId" to request.toUser.userId,
            "partnerName" to request.toUser.username,
            "message" to "✅ Transaction completed! Receipt is ready.",
            "timestamp" to timestamp,
            "read" to false
        )

        // Notification for User B (toUser)
        val notifBId = "receipt_$receiptId"
        val notifB = hashMapOf<String, Any>(
            "id" to notifBId,
            "type" to "receipt_created",
            "receiptId" to receiptId,
            "requestId" to request.requestId,
            "chatId" to chatId,
            "partnerId" to request.fromUser.userId,
            "partnerName" to request.fromUser.username,
            "message" to "✅ Transaction completed! Receipt is ready.",
            "timestamp" to timestamp,
            "read" to false
        )

        // Save notifications
        db.child("notifications").child(request.fromUser.userId).child(notifAId)
            .setValue(notifA)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Receipt notification sent to ${request.fromUser.username}")
            }

        db.child("notifications").child(request.toUser.userId).child(notifBId)
            .setValue(notifB)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Receipt notification sent to ${request.toUser.username}")
            }
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

    private fun generateReceiptNumber(): String {
        val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
        val random = (100000..999999).random()
        return "RCPT-$year-$random"
    }

    private fun generateChatDisplayId(): String {
        val random = (10000..99999).random()
        return "CHT-$random"
    }

    private fun generateRequestDisplayId(): String {
        val random = (10000..99999).random()
        return "REQ-$random"
    }

    private fun saveTradeHistory(request: TradeRequest) {
        val db = FirebaseDatabase.getInstance().reference
        val tradeId = request.requestId
        val date = System.currentTimeMillis().toString()

        // history for fromUser (yung natanggap niyang item = target item)
        val fromUserHistory = TradeHistoryItem(
            itemName = request.targetItem.title,
            tradedWith = request.toUser.username,
            date = date,
            status = "Completed"
        )

        // history for toUser (yung natanggap niyang item = offered item)
        val toUserHistory = TradeHistoryItem(
            itemName = request.offeredItem.title,
            tradedWith = request.fromUser.username,
            date = date,
            status = "Completed"
        )

        val updates = hashMapOf<String, Any>(
            "trades/${request.fromUser.userId}/$tradeId" to fromUserHistory,
            "trades/${request.toUser.userId}/$tradeId" to toUserHistory
        )

        db.updateChildren(updates)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Trade history saved for both users: tradeId=$tradeId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save trade history: ${e.message}")
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