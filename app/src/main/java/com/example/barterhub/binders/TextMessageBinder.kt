package com.example.barterhub.binders

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.adapters.MessagesAdapter
import com.example.barterhub.data.models.Message
import java.text.SimpleDateFormat
import java.util.*

class TextMessageBinder(
    private val currentUserId: String,
    private val partnerProfilePic: String?,
    private val chatId: String,
    private val onProfilePictureClickListener: ((String) -> Unit)? = null,
    private val onMessageDeleted: ((Message, Int) -> Unit)? = null
) : MessageBinder {

    override fun bind(holder: RecyclerView.ViewHolder, message: Message, position: Int) {
        bind(holder, message, position, true)
    }

    fun bind(
        holder: RecyclerView.ViewHolder,
        message: Message,
        position: Int,
        showProfilePic: Boolean
    ) {
        when (holder) {
            is MessagesAdapter.SentMessageViewHolder -> {
                bindSentMessage(holder, message, position)
            }
            is MessagesAdapter.ReceivedMessageViewHolder -> {
                bindReceivedMessage(holder, message, position, showProfilePic)
            }
        }
    }

    private fun bindSentMessage(
        holder: MessagesAdapter.SentMessageViewHolder,
        message: Message,
        position: Int
    ) {
        holder.messageText.text = message.text ?: ""
        holder.timestampText.text = formatTimestamp(message.timestamp)

        // Read status
        holder.readStatus?.let { readStatusView ->
            when {
                message.read -> {
                    readStatusView.text = holder.itemView.context.getString(R.string.seen)
                    readStatusView.setTextColor(
                        holder.itemView.context.getColor(R.color.colorAccent)
                    )
                    readStatusView.visibility = android.view.View.VISIBLE
                }
                else -> {
                    readStatusView.text = holder.itemView.context.getString(R.string.sent)
                    readStatusView.setTextColor(
                        holder.itemView.context.getColor(R.color.text_hint)
                    )
                    readStatusView.visibility = android.view.View.VISIBLE
                }
            }
        }

        // 👇 IMPORTANT: Set long click listener
        holder.messageContainer.setOnLongClickListener {
            showDeleteDialog(holder.itemView.context, message, position)
            true
        }

        // 👇 Optional: Also set on the text view for better touch area
        holder.messageText.setOnLongClickListener {
            showDeleteDialog(holder.itemView.context, message, position)
            true
        }
    }

    private fun bindReceivedMessage(
        holder: MessagesAdapter.ReceivedMessageViewHolder,
        message: Message,
        position: Int,
        showProfilePic: Boolean
    ) {
        holder.messageText.text = message.text ?: ""
        holder.timestampText.text = formatTimestamp(message.timestamp)
        holder.senderText.text = message.senderName ?: holder.itemView.context.getString(R.string.unknown)

        // Profile picture
        if (showProfilePic && !partnerProfilePic.isNullOrEmpty()) {
            holder.profileImage.visibility = android.view.View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(partnerProfilePic)
                .placeholder(R.drawable.ic_profile_placeholder)
                .into(holder.profileImage)

            holder.profileImage.setOnClickListener {
                onProfilePictureClickListener?.invoke(partnerProfilePic)
            }
        } else {
            holder.profileImage.visibility = android.view.View.GONE
        }

        // 👇 IMPORTANT: Set long click listener
        holder.messageContainer.setOnLongClickListener {
            showDeleteDialog(holder.itemView.context, message, position)
            true
        }

        // 👇 Optional: Also set on the text view for better touch area
        holder.messageText.setOnLongClickListener {
            showDeleteDialog(holder.itemView.context, message, position)
            true
        }
    }

    private fun showDeleteDialog(context: Context, message: Message, position: Int) {
        if (message.isSystemMessage) {
            android.widget.Toast.makeText(
                context,
                "System messages cannot be deleted",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val options = arrayOf("Delete for me", "Cancel")

        AlertDialog.Builder(context)
            .setTitle("Delete Message")
            .setItems(options) { _, which ->
                if (which == 0) {
                    onMessageDeleted?.invoke(message, position)
                }
            }
            .show()
    }

    private fun formatTimestamp(timestamp: Long): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault())
            .format(Date(timestamp))
    }
}