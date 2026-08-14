package com.example.barterhub.adapters.message.reactions

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.adapters.message.ImageMessageViewHolder
import com.example.barterhub.adapters.message.ReceivedMessageViewHolder
import com.example.barterhub.adapters.message.SentMessageViewHolder
import com.example.barterhub.adapters.message.VideoMessageViewHolder
import com.example.barterhub.data.models.Message

class MessageReactionBinder(
    private val currentUserId: String,
    private val onAddReaction: (
        messageId: String,
        emoji: String
    ) -> Unit,
    private val onRemoveReaction: (
        messageId: String,
        emoji: String
    ) -> Unit
) {

    fun bind(
        holder: RecyclerView.ViewHolder,
        message: Message
    ) {
        when (holder) {
            is SentMessageViewHolder -> {
                bindTextReactions(
                    holder = holder,
                    reactionsContainer = holder.reactionsContainer,
                    reactionSummary = holder.tvReactionSummary,
                    message = message
                )
            }

            is ReceivedMessageViewHolder -> {
                bindTextReactions(
                    holder = holder,
                    reactionsContainer = holder.reactionsContainer,
                    reactionSummary = holder.tvReactionSummary,
                    message = message
                )
            }

            is ImageMessageViewHolder -> {
                bindSingleReaction(
                    container = holder.singleReactionContainer,
                    emojiView = holder.tvReactionEmoji,
                    countView = holder.tvReactionCount,
                    message = message
                )
            }

            is VideoMessageViewHolder -> {
                bindSingleReaction(
                    container = holder.singleReactionContainer,
                    emojiView = holder.tvReactionEmoji,
                    countView = holder.tvReactionCount,
                    message = message
                )
            }
        }
    }

    private fun bindTextReactions(
        holder: RecyclerView.ViewHolder,
        reactionsContainer: LinearLayout?,
        reactionSummary: TextView?,
        message: Message
    ) {
        reactionsContainer?.removeAllViews()

        if (message.reactions.isEmpty()) {
            reactionsContainer?.visibility = View.GONE
            reactionSummary?.visibility = View.GONE
            return
        }

        reactionsContainer?.visibility = View.VISIBLE

        val topReactions = message.reactions
            .entries
            .sortedByDescending { it.value.size }
            .take(3)

        topReactions.forEach { entry ->
            val emoji = entry.key
            val usersMap = entry.value
            val context = holder.itemView.context

            val reactionView = LayoutInflater
                .from(context)
                .inflate(
                    R.layout.reaction_item,
                    reactionsContainer,
                    false
                )

            val tvEmoji =
                reactionView.findViewById<TextView>(
                    R.id.tvReactionEmoji
                )

            val tvCount =
                reactionView.findViewById<TextView>(
                    R.id.tvReactionCount
                )

            tvEmoji.text = emoji
            tvCount.text = usersMap.size.toString()

            val currentUserReacted =
                usersMap.containsKey(currentUserId)

            if (currentUserReacted) {
                reactionView.setBackgroundResource(
                    R.drawable.bg_reaction_selected
                )

                tvCount.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.colorPrimary
                    )
                )
            } else {
                reactionView.setBackgroundResource(
                    R.drawable.bg_reaction_default
                )

                tvCount.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.text_secondary
                    )
                )
            }

            reactionView.setOnClickListener {
                if (currentUserReacted) {
                    onRemoveReaction(
                        message.messageId,
                        emoji
                    )
                } else {
                    onAddReaction(
                        message.messageId,
                        emoji
                    )
                }
            }

            reactionsContainer?.addView(reactionView)
        }

        val totalReactions =
            message.reactions.values.sumOf { it.size }

        if (totalReactions > 3) {
            reactionSummary?.text =
                "+${totalReactions - 3}"

            reactionSummary?.visibility =
                View.VISIBLE
        } else {
            reactionSummary?.visibility =
                View.GONE
        }
    }

    private fun bindSingleReaction(
        container: LinearLayout?,
        emojiView: TextView?,
        countView: TextView?,
        message: Message
    ) {
        if (
            container == null ||
            emojiView == null ||
            countView == null
        ) {
            return
        }

        val topReaction =
            message.reactions.entries
                .maxByOrNull { it.value.size }

        if (topReaction == null) {
            container.visibility = View.GONE
            return
        }

        val emoji = topReaction.key
        val usersMap = topReaction.value
        val currentUserReacted =
            usersMap.containsKey(currentUserId)

        container.visibility = View.VISIBLE
        emojiView.text = emoji
        countView.text = usersMap.size.toString()

        if (currentUserReacted) {
            container.setBackgroundResource(
                R.drawable.bg_reaction_selected
            )

            countView.setTextColor(
                ContextCompat.getColor(
                    container.context,
                    R.color.colorPrimary
                )
            )
        } else {
            container.setBackgroundResource(
                R.drawable.bg_reaction_default
            )

            countView.setTextColor(
                ContextCompat.getColor(
                    container.context,
                    R.color.text_secondary
                )
            )
        }
    }
}