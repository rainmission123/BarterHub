package com.example.barterhub.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.data.models.Conversation
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class ConversationsAdapter(
    private val conversations: List<Conversation>,
    private val onConversationClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationsAdapter.ConversationViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    inner class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.profileImage)
        val participantName: TextView = itemView.findViewById(R.id.participantName)
        val lastMessage: TextView = itemView.findViewById(R.id.lastMessage)
        val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        val unreadBadge: TextView = itemView.findViewById(R.id.unreadBadge)

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onConversationClick(conversations[adapterPosition])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = conversations[position]
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        // Find partner user
        val partnerId = conversation.participants.keys.firstOrNull { it != currentUserId }
        val partnerName = conversation.participantNames[partnerId] ?: "Unknown User"

        // 🔥 ADDED: Load profile picture
        val partnerProfilePic = partnerId?.let { conversation.participantProfilePics[it] }
        loadProfileImage(holder.profileImage, partnerProfilePic)

        // Set participant name
        holder.participantName.text = partnerName

        // Set last message
        holder.lastMessage.text = conversation.lastMessage ?: "Start a conversation"

        // Set timestamp
        holder.timestamp.text = formatTimestamp(conversation.lastMessageTime)

        // Set unread count
        if (conversation.unreadCount > 0) {
            holder.unreadBadge.visibility = View.VISIBLE
            holder.unreadBadge.text = if (conversation.unreadCount > 9) "9+" else conversation.unreadCount.toString()
        } else {
            holder.unreadBadge.visibility = View.GONE
        }
    }

    // 🔥 ADDED: Function to load profile image
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

    override fun getItemCount(): Int = conversations.size

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return ""

        val currentTime = System.currentTimeMillis()
        val diff = currentTime - timestamp

        return when {
            diff < 60000 -> "just now" // less than 1 minute
            diff < 3600000 -> "${diff / 60000}m ago" // less than 1 hour
            diff < 86400000 -> "${diff / 3600000}h ago" // less than 1 day
            else -> {
                val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
}