package com.example.barterhub.binders

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.adapters.MessagesAdapter
import com.example.barterhub.data.models.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class TextMessageBinder(
    private val currentUserId: String,
    private val partnerProfilePic: String?,
    private val chatId: String,
    private val onProfilePictureClickListener: ((String) -> Unit)? = null,
    private val onMessageDeleted: ((Message, Int) -> Unit)? = null
) : MessageBinder {

    // ✅ INTERFACE IMPLEMENTATION - REQUIRED
    override fun bind(holder: RecyclerView.ViewHolder, message: Message, position: Int) {
        // Default: show profile pic
        bind(holder, message, position, true)
    }

    // ✅ OVERLOADED VERSION WITH showProfilePic
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

        // ✅ TIMESTAMP
        holder.timestampText.text = formatTimestamp(message.timestamp)

        // ✅ FIXED: Use message.read instead of message.isRead
        holder.readStatus?.let { readStatusView ->
            when {
                message.read -> {  // ✅ PALITAN: message.read (hindi message.isRead)
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

        // Set up long click for deletion
        holder.messageContainer.setOnLongClickListener {
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

        // ✅ SHOW/HIDE PROFILE PIC BASED ON PARAMETER
        if (showProfilePic && !partnerProfilePic.isNullOrEmpty()) {
            holder.profileImage.visibility = android.view.View.VISIBLE

            Glide.with(holder.itemView.context)
                .load(partnerProfilePic)
                .placeholder(R.drawable.ic_profile_placeholder)
                .into(holder.profileImage)

            // Profile image click
            holder.profileImage.setOnClickListener {
                onProfilePictureClickListener?.invoke(partnerProfilePic)
            }
        } else {
            holder.profileImage.visibility = android.view.View.GONE
        }

        // Set up long click for deletion
        holder.messageContainer.setOnLongClickListener {
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

        AlertDialog.Builder(context)
            .setTitle("Delete Message")
            .setMessage("Delete this message for both users?")
            .setPositiveButton("Delete") { _, _ ->
                deleteMessageForBoth(message, position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMessageForBoth(message: Message, position: Int) {
        val db = FirebaseDatabase.getInstance().reference
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val messageId = message.messageId ?: return

        // Determine chat IDs
        val otherUserId = if (message.senderId == currentUserId) {
            message.receiverId
        } else {
            message.senderId
        }

        val chatId1 = "${currentUserId}_$otherUserId"
        val chatId2 = "${otherUserId}_$currentUserId"

        val updates = hashMapOf<String, Any?>(
            "/chats/$chatId1/messages/$messageId" to null,
            "/chats/$chatId2/messages/$messageId" to null
        )

        db.updateChildren(updates)
            .addOnSuccessListener {
                onMessageDeleted?.invoke(message, position)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("TextMessageBinder", "Failed to delete message: ${e.message}")
            }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault())
            .format(Date(timestamp))
    }


}