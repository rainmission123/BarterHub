package com.example.barterhub.binders

import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.data.models.Message
import com.example.barterhub.viewholders.TradeRatingViewHolder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions

class TradeRatingBinder(
    private val currentUserId: String,
    private val chatId: String,
    private val onMessageUpdated: (() -> Unit)? = null,
    private val onRatingCommentFocusChanged: ((Boolean) -> Unit)? = null
) : MessageBinder {

    companion object {
        private const val TAG = "TradeRatingBinder"
    }

    private val ratingSubmissionsInFlight = mutableSetOf<String>()

    override fun bind(holder: RecyclerView.ViewHolder, message: Message, position: Int) {
        if (holder !is TradeRatingViewHolder) return

        val currentMessageId = message.messageId
        holder.itemView.tag = currentMessageId

        val tradeDetails = message.tradeDetails ?: run {
            holder.tvRatingTitle.text = "Rating unavailable"
            holder.tvRateUserName.text = "Trade details missing"
            holder.ratingBar.visibility = View.GONE
            holder.etRatingComment.visibility = View.GONE
            holder.btnSubmitRating.visibility = View.GONE
            return
        }

        val tradeId = getString(tradeDetails, "tradeRequestId")
            ?: message.tradeId
            ?: message.requestId
            ?: ""

        val fromUserId = getString(tradeDetails, "fromUserId").orEmpty()
        val toUserId = getString(tradeDetails, "toUserId").orEmpty()

        val fromUserName = getString(tradeDetails, "offeredBy")
            ?: getString(tradeDetails, "fromUserName")
            ?: "Trade partner"

        val toUserName = getString(tradeDetails, "acceptedBy")
            ?: getString(tradeDetails, "toUserName")
            ?: "Trade partner"

        if (tradeId.isBlank()) {
            holder.tvRatingTitle.text = "Rating unavailable"
            holder.tvRateUserName.text = "Missing trade ID"
            holder.ratingBar.visibility = View.GONE
            holder.etRatingComment.visibility = View.GONE
            holder.btnSubmitRating.visibility = View.GONE
            return
        }

        val partnerId: String
        val partnerName: String

        when (currentUserId) {
            fromUserId -> {
                partnerId = toUserId
                partnerName = toUserName
            }

            toUserId -> {
                partnerId = fromUserId
                partnerName = fromUserName
            }

            else -> {
                holder.tvRatingTitle.text = "Rating unavailable"
                holder.tvRateUserName.text = "You are not part of this trade"
                holder.ratingBar.visibility = View.GONE
                holder.etRatingComment.visibility = View.GONE
                holder.btnSubmitRating.visibility = View.GONE
                return
            }
        }

        if (getNestedString(tradeDetails, "ratingStatus", currentUserId) == "rated") {
            holder.tvRatingTitle.text = "⭐ Thank you for rating!"
            holder.tvRateUserName.text = "Your review has been saved."
            holder.ratingBar.visibility = View.GONE
            holder.etRatingComment.visibility = View.GONE
            holder.btnSubmitRating.visibility = View.GONE
            return
        }

        holder.tvRatingTitle.text = "Rate your barter partner"
        holder.tvRateUserName.text = "Rate $partnerName"
        holder.ratingBar.visibility = View.VISIBLE
        holder.etRatingComment.visibility = View.VISIBLE
        holder.btnSubmitRating.visibility = View.VISIBLE

        ChatDisplayNameResolver.resolve(
            uid = partnerId,
            fallbackUsername = partnerName
        ) { partnerDisplayName ->
            if (holder.itemView.tag != currentMessageId) return@resolve
            holder.tvRateUserName.text = "Rate $partnerDisplayName"
        }

        holder.ratingBar.rating = 0f
        holder.etRatingComment.setText("")
        holder.etRatingComment.clearFocus()
        holder.etRatingComment.setOnFocusChangeListener { _, hasFocus ->
            onRatingCommentFocusChanged?.invoke(hasFocus)
        }

        holder.btnSubmitRating.isEnabled = true
        holder.btnSubmitRating.text = "Submit Review"

        holder.btnSubmitRating.setOnClickListener {
            val rating = holder.ratingBar.rating.toInt()
            val comment = holder.etRatingComment.text
                ?.toString()
                ?.trim()
                ?.take(250)
                .orEmpty()

            if (rating < 1 || rating > 5) {
                Toast.makeText(
                    holder.itemView.context,
                    "Please select a rating",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            submitRating(
                holder = holder,
                messageId = message.messageId,
                tradeId = tradeId,
                reviewedUserId = partnerId,
                rating = rating,
                comment = comment
            )
        }
    }

    private fun submitRating(
        holder: TradeRatingViewHolder,
        messageId: String,
        tradeId: String,
        reviewedUserId: String,
        rating: Int,
        comment: String
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val submissionKey = "${tradeId}_${currentUser.uid}"

        if (!ratingSubmissionsInFlight.add(submissionKey)) {
            Log.d(TAG, "Rating already being submitted for $submissionKey")
            return
        }

        holder.btnSubmitRating.isEnabled = false
        holder.btnSubmitRating.text = "Submitting..."
        holder.etRatingComment.clearFocus()
        onRatingCommentFocusChanged?.invoke(false)

        val data = hashMapOf(
            "tradeId" to tradeId,
            "chatId" to chatId,
            "messageId" to messageId,
            "reviewedUserId" to reviewedUserId,
            "rating" to rating,
            "comment" to comment
        )

        FirebaseFunctions.getInstance("asia-southeast1")
            .getHttpsCallable("submitTradeReview")
            .call(data)
            .addOnSuccessListener {
                ratingSubmissionsInFlight.remove(submissionKey)

                holder.ratingBar.isEnabled = false
                holder.ratingBar.setIsIndicator(true)
                holder.etRatingComment.isEnabled = false
                holder.btnSubmitRating.isEnabled = false
                holder.btnSubmitRating.text = "Review submitted"
                holder.tvRatingTitle.text = "⭐ Thank you for rating!"
                holder.tvRateUserName.text = "Your review has been saved."

                Toast.makeText(
                    holder.itemView.context,
                    "Review submitted successfully.",
                    Toast.LENGTH_SHORT
                ).show()

                onMessageUpdated?.invoke()
            }

            .addOnFailureListener { e ->
                ratingSubmissionsInFlight.remove(submissionKey)

                Log.e(TAG, "Failed to submit review", e)

                holder.btnSubmitRating.isEnabled = true
                holder.btnSubmitRating.text = "Submit Review"

                Toast.makeText(
                    holder.itemView.context,
                    e.message ?: "Failed to submit review.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun getString(map: Map<String, Any>, key: String): String? {
        return map[key] as? String
    }

    private fun getNestedString(map: Map<String, Any>, parentKey: String, childKey: String): String? {
        val childMap = map[parentKey] as? Map<*, *> ?: return null
        return childMap[childKey] as? String
    }
}
