package com.example.barterhub.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.data.NotificationModel
import com.google.firebase.database.FirebaseDatabase
import de.hdodenhof.circleimageview.CircleImageView
import com.google.android.material.button.MaterialButton

class NotificationsAdapter(
    private val notifications: MutableList<NotificationModel>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_FRIEND_REQUEST = 1
        private const val TYPE_DEFAULT = 2
    }

    private val database = FirebaseDatabase.getInstance().reference
    private var onNotificationActionListener: OnNotificationActionListener? = null

    // Interface for callbacks
    interface OnNotificationActionListener {
        fun onAcceptFriend(notificationId: String?, fromUserId: String?, position: Int)
        fun onDeclineFriend(notificationId: String?, position: Int)
        fun onDeleteNotification(notificationId: String?, position: Int)
    }

    fun setOnNotificationActionListener(listener: OnNotificationActionListener) {
        this.onNotificationActionListener = listener
    }

    // 🔹 DETERMINE LAYOUT
    override fun getItemViewType(position: Int): Int {
        return if (notifications[position].type == "friend_request") {
            TYPE_FRIEND_REQUEST
        } else {
            TYPE_DEFAULT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_FRIEND_REQUEST -> {
                val view = inflater.inflate(
                    R.layout.item_notification_friend_request,
                    parent,
                    false
                )
                FriendRequestViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(
                    R.layout.item_notification,
                    parent,
                    false
                )
                DefaultNotificationViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val notification = notifications[position]
        when (holder) {
            is FriendRequestViewHolder -> holder.bind(notification, position)
            is DefaultNotificationViewHolder -> holder.bind(notification, position)
        }
    }

    override fun getItemCount(): Int = notifications.size

    // Function to update notification status locally
    fun updateNotificationStatus(notificationId: String, status: String, position: Int) {
        val actualPosition = if (position != -1) {
            position
        } else {
            notifications.indexOfFirst { it.id == notificationId }
        }

        if (actualPosition != -1 && actualPosition < notifications.size) {
            // Update the notification in the list
            notifications[actualPosition] = notifications[actualPosition].copy(
                status = status,
                read = true
            )

            // 🔹 Notify THIS SPECIFIC ITEM changed
            notifyItemChanged(actualPosition)

            Log.d("NotificationsAdapter", "📱 Updated notification $notificationId to $status at position $actualPosition")
        }
    }

    // Function to remove notification from list
    fun removeNotification(position: Int) {
        if (position != -1 && position < notifications.size) {
            notifications.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    // 🔹 FRIEND REQUEST VIEW HOLDER
    inner class FriendRequestViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        private val ivProfile = itemView.findViewById<CircleImageView>(R.id.ivProfile)
        private val tvMessage = itemView.findViewById<TextView>(R.id.tvMessage)
        private val tvTime = itemView.findViewById<TextView>(R.id.tvTime)
        private val btnAccept = itemView.findViewById<MaterialButton>(R.id.btnAccept)
        private val btnDecline = itemView.findViewById<MaterialButton>(R.id.btnDecline)
        private val llActionButtons = itemView.findViewById<LinearLayout>(R.id.llActionButtons)

        fun bind(notification: NotificationModel, position: Int) {
            // 🔹 Get sender name
            val senderName = notification.fromUserName ?: "Someone"

            // 🔹 Check status and update UI
            when (notification.status) {
                "accepted" -> {
                    // ✅ ACCEPTED STATE
                    tvMessage.text = "$senderName sent you a friend request ✓ Accepted"
                    llActionButtons.visibility = View.GONE  // Hide buttons

                    // Change text color to green
                    tvMessage.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.verified_green)
                    )

                    Log.d("NotificationsAdapter", "Showing accepted state for: $senderName")
                }

                "declined" -> {
                    // ❌ DECLINED STATE
                    tvMessage.text = "$senderName sent you a friend request ✗ Declined"
                    llActionButtons.visibility = View.GONE  // Hide buttons

                    // Change text color to red
                    tvMessage.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.red_600)
                    )

                    Log.d("NotificationsAdapter", "Showing declined state for: $senderName")
                }

                else -> {
                    // 🔘 PENDING STATE (show buttons)
                    tvMessage.text = "$senderName sent you a friend request"
                    llActionButtons.visibility = View.VISIBLE  // Show buttons

                    // Reset text color to default
                    tvMessage.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.colorOnSurface)
                    )

                    Log.d("NotificationsAdapter", "Showing pending state for: $senderName")
                }
            }

            // 🔹 Format time
            tvTime.text = getTimeAgo(notification.timestamp)

            // 🔹 Load profile image
            val profileUrl = notification.fromUserProfile
            if (!profileUrl.isNullOrEmpty() && profileUrl != "null") {
                Glide.with(itemView.context)
                    .load(profileUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .circleCrop()
                    .into(ivProfile)
            } else {
                ivProfile.setImageResource(R.drawable.ic_profile_placeholder)

                // Try to fetch if we have user ID
                if (!notification.fromUserId.isNullOrEmpty()) {
                    fetchAndUpdateProfile(notification.fromUserId!!, ivProfile)
                }
            }

            // 🔹 Set click listeners ONLY for pending requests
            if (notification.status != "accepted" && notification.status != "declined") {
                btnAccept.isEnabled = true
                btnDecline.isEnabled = true
                btnAccept.text = "Accept"
                btnDecline.text = "Decline"

                btnAccept.setOnClickListener {
                    // Disable buttons immediately
                    btnAccept.isEnabled = false
                    btnDecline.isEnabled = false
                    btnAccept.text = "Accepting..."

                    // Use callback to parent
                    onNotificationActionListener?.onAcceptFriend(
                        notification.id,
                        notification.fromUserId,
                        position
                    )
                }

                btnDecline.setOnClickListener {
                    btnAccept.isEnabled = false
                    btnDecline.isEnabled = false
                    btnDecline.text = "Declining..."

                    // Use callback to parent
                    onNotificationActionListener?.onDeclineFriend(notification.id, position)
                }
            } else {
                // Disable buttons for already handled requests
                btnAccept.isEnabled = false
                btnDecline.isEnabled = false
            }
        }

        private fun fetchAndUpdateProfile(userId: String, imageView: CircleImageView) {
            database.child("users").child(userId).child("profileImageUrl").get()
                .addOnSuccessListener { snapshot ->
                    val url = snapshot.getValue(String::class.java)
                    if (!url.isNullOrEmpty() && url != "null") {
                        Glide.with(imageView.context)
                            .load(url)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .circleCrop()
                            .into(imageView)
                    }
                }
        }
    }

    // 🔹 DEFAULT NOTIFICATION VIEW HOLDER
    inner class DefaultNotificationViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        private val tvMessage = itemView.findViewById<TextView>(R.id.tvNotificationMessage)
        private val tvTime = itemView.findViewById<TextView>(R.id.tvNotificationTime)
        private val ivDelete = itemView.findViewById<ImageView>(R.id.ivDeleteNotification)

        fun bind(notification: NotificationModel, position: Int) {
            // Set message for default notification
            tvMessage.text = notification.message ?: ""
            tvTime.text = getTimeAgo(notification.timestamp)

            ivDelete.setOnClickListener {
                onNotificationActionListener?.onDeleteNotification(notification.id, position)
            }
        }
    }

    // 🔹 TIME FORMATTER
    private fun getTimeAgo(time: Long): String {
        val diff = System.currentTimeMillis() - time
        val minutes = diff / 60000
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours hr ago"
            else -> "$days days ago"
        }
    }
}