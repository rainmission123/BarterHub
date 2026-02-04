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
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class ConversationsAdapter(
    private val conversations: List<Conversation>,
    private val onConversationClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationsAdapter.ConversationViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    // Long click listener
    private var onConversationLongClickListener: ((Conversation, Int) -> Unit)? = null
    fun setOnConversationLongClickListener(listener: (Conversation, Int) -> Unit) {
        onConversationLongClickListener = listener
    }

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

            itemView.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onConversationLongClickListener?.invoke(conversations[position], position)
                    true
                } else {
                    false
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

        // Partner info
        val partnerId = conversation.participants.keys.firstOrNull { it != currentUserId }
        val partnerName = conversation.participantNames[partnerId] ?: "Unknown User"
        val partnerProfilePic = partnerId?.let { conversation.participantProfilePics[it] }

        holder.participantName.text = partnerName
        loadProfileImage(holder.profileImage, partnerProfilePic)
        holder.lastMessage.text = conversation.lastMessage ?: "Start a conversation"
        holder.timestamp.text = formatTimestamp(conversation.lastMessageTime)

        // -----------------------
        // REAL-TIME UNREAD BADGE
        // -----------------------
        partnerId?.let { pid ->
            val messagesRef = FirebaseDatabase.getInstance()
                .getReference("chats/${conversation.chatId}/messages")

            messagesRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val unreadCount = snapshot.children.count {
                        val read = it.child("read").getValue(Boolean::class.java) ?: true
                        val receiverId = it.child("receiverId").getValue(String::class.java)
                        !read && receiverId == currentUserId
                    }

                    if (unreadCount > 0) {
                        holder.unreadBadge.visibility = View.VISIBLE
                        holder.unreadBadge.text = if (unreadCount > 9) "9+" else unreadCount.toString()
                    } else {
                        holder.unreadBadge.visibility = View.GONE
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    holder.unreadBadge.visibility = View.GONE
                }
            })
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

    override fun getItemCount(): Int = conversations.size

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60000 -> "just now"
            diff < 3600000 -> "${diff / 60000}m ago"
            diff < 86400000 -> "${diff / 3600000}h ago"
            else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
