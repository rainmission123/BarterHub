package com.example.barterhub.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.data.models.Message
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class MessagesAdapter(
    private val messages: List<Message>,
    private val currentUserId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    // Profile picture URLs
    private var currentUserProfilePic: String? = null
    private var partnerProfilePic: String? = null

    // 🔥 ADD THESE: Click listeners
    private var onMessageLongClickListener: ((Message, Int) -> Unit)? = null
    private var onProfilePictureClickListener: ((String) -> Unit)? = null

    // 🔥 ADD THIS: Function to set profile picture click listener
    fun setOnProfilePictureClickListener(listener: (String) -> Unit) {
        onProfilePictureClickListener = listener
    }

    fun setOnMessageLongClickListener(listener: (Message, Int) -> Unit) {
        onMessageLongClickListener = listener
    }

    // Function to set profile pictures
    fun setProfilePictures(currentUserPic: String?, partnerPic: String?) {
        currentUserProfilePic = currentUserPic
        partnerProfilePic = partnerPic
    }

    // Sent Message ViewHolder (Right side - YOUR messages)
    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.tvMessageSent)
        val messageImage: ImageView = itemView.findViewById(R.id.ivMessageImageSent)
        val timestampText: TextView = itemView.findViewById(R.id.tvTimestampSent)
        val profileImage: ImageView = itemView.findViewById(R.id.ivProfileSent)
        val messageContainer: View = itemView.findViewById(R.id.sentMessageContainer)
    }

    // Received Message ViewHolder (Left side - PARTNER's messages)
    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.tvMessageReceived)
        val messageImage: ImageView = itemView.findViewById(R.id.ivMessageImageReceived)
        val timestampText: TextView = itemView.findViewById(R.id.tvTimestampReceived)
        val senderText: TextView = itemView.findViewById(R.id.tvSenderReceived)
        val profileImage: ImageView = itemView.findViewById(R.id.ivProfileReceived)
        val messageContainer: View = itemView.findViewById(R.id.receivedMessageContainer)
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return if (message.senderId == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_sent, parent, false)
                SentMessageViewHolder(view)
            }
            VIEW_TYPE_RECEIVED -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]

        when (holder) {
            is SentMessageViewHolder -> bindSentMessage(holder, message, position)
            is ReceivedMessageViewHolder -> bindReceivedMessage(holder, message, position)
        }
    }

    private fun bindSentMessage(holder: SentMessageViewHolder, message: Message, position: Int) {
        // Hide all first
        holder.messageText.visibility = View.GONE
        holder.messageImage.visibility = View.GONE

        // Show text if available
        if (!message.text.isNullOrEmpty()) {
            holder.messageText.visibility = View.VISIBLE
            holder.messageText.text = message.text
        }

        // Show image if available
        if (!message.imageUrl.isNullOrEmpty()) {
            holder.messageImage.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(message.imageUrl)
                .placeholder(R.drawable.ic_image_placeholder)
                .into(holder.messageImage)
        }

        // Timestamp
        holder.timestampText.text = formatTimestamp(message.timestamp ?: 0L)

        // Load profile picture
        loadProfileImage(holder.profileImage, currentUserProfilePic)

        holder.profileImage.setOnClickListener {
            currentUserProfilePic?.let { profilePicUrl ->
                onProfilePictureClickListener?.invoke(profilePicUrl)
            }
        }

        // Long press listener
        holder.messageContainer.setOnLongClickListener {
            onMessageLongClickListener?.invoke(message, position)
            true
        }
    }

    private fun bindReceivedMessage(holder: ReceivedMessageViewHolder, message: Message, position: Int) {
        // Hide all first
        holder.messageText.visibility = View.GONE
        holder.messageImage.visibility = View.GONE

        // Show text if available
        if (!message.text.isNullOrEmpty()) {
            holder.messageText.visibility = View.VISIBLE
            holder.messageText.text = message.text
        }

        // Show image if available
        if (!message.imageUrl.isNullOrEmpty()) {
            holder.messageImage.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(message.imageUrl)
                .placeholder(R.drawable.ic_image_placeholder)
                .into(holder.messageImage)
        }

        // Timestamp and sender
        holder.timestampText.text = formatTimestamp(message.timestamp ?: 0L)
        holder.senderText.text = message.senderName ?: "Unknown"

        // Load profile picture with better error handling
        loadProfileImage(holder.profileImage, partnerProfilePic)

        // 🔥 ADD THIS: Profile picture click listener
        holder.profileImage.setOnClickListener {
            partnerProfilePic?.let { profilePicUrl ->
                onProfilePictureClickListener?.invoke(profilePicUrl)
            }
        }

        // Long press listener
        holder.messageContainer.setOnLongClickListener {
            onMessageLongClickListener?.invoke(message, position)
            true
        }
    }

    private fun loadProfileImage(imageView: ImageView, profilePicUrl: String?) {
        if (!profilePicUrl.isNullOrEmpty()) {
            Glide.with(imageView.context)
                .load(profilePicUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.ic_profile_placeholder)
        }
    }

    override fun getItemCount(): Int = messages.size

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}