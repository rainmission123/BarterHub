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

    interface OnNotificationClickListener {
        fun onNotificationClick(notification: NotificationModel)
    }

    private var onNotificationClickListener: OnNotificationClickListener? = null

    fun setOnNotificationClickListener(listener: OnNotificationClickListener) {
        this.onNotificationClickListener = listener
    }

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

    fun updateNotificationStatus(notificationId: String, status: String, position: Int) {
        val actualPosition = if (position != -1) {
            position
        } else {
            notifications.indexOfFirst { it.id == notificationId }
        }

        if (actualPosition != -1 && actualPosition < notifications.size) {
            notifications[actualPosition] = notifications[actualPosition].copy(
                status = status,
                read = true
            )

            notifyItemChanged(actualPosition)

            Log.d("NotificationsAdapter", "📱 Updated notification $notificationId to $status at position $actualPosition")
        }
    }

    fun removeNotification(position: Int) {
        if (position != -1 && position < notifications.size) {
            notifications.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    inner class FriendRequestViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        private val ivDelete = itemView.findViewById<ImageView>(R.id.ivDeleteNotification)
        private val ivProfile = itemView.findViewById<CircleImageView>(R.id.ivProfile)
        private val tvMessage = itemView.findViewById<TextView>(R.id.tvMessage)
        private val tvTime = itemView.findViewById<TextView>(R.id.tvTime)
        private val btnAccept = itemView.findViewById<MaterialButton>(R.id.btnAccept)
        private val btnDecline = itemView.findViewById<MaterialButton>(R.id.btnDecline)
        private val llActionButtons = itemView.findViewById<LinearLayout>(R.id.llActionButtons)

        fun bind(notification: NotificationModel, position: Int) {
            val senderName = notification.fromUserName ?: "Someone"

            when (notification.status) {
                "accepted" -> {
                    tvMessage.text = "$senderName sent you a friend request ✓ Accepted"
                    llActionButtons.visibility = View.GONE  // Hide buttons

                    // Change text color to green
                    tvMessage.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.verified_green)
                    )

                    Log.d("NotificationsAdapter", "Showing accepted state for: $senderName")
                }

                "declined" -> {
                    tvMessage.text = "$senderName sent you a friend request ✗ Declined"
                    llActionButtons.visibility = View.GONE

                    tvMessage.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.red_600)
                    )

                    Log.d("NotificationsAdapter", "Showing declined state for: $senderName")
                }

                else -> {
                    tvMessage.text = "$senderName sent you a friend request"
                    llActionButtons.visibility = View.VISIBLE  // Show buttons

                    tvMessage.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.colorOnSurface)
                    )

                    Log.d("NotificationsAdapter", "Showing pending state for: $senderName")
                }
            }

            tvTime.text = getTimeAgo(notification.timestamp)

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

                if (!notification.fromUserId.isNullOrEmpty()) {
                    fetchAndUpdateProfile(notification.fromUserId!!, ivProfile)
                }
            }

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

            ivDelete.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onNotificationActionListener?.onDeleteNotification(notification.id, pos)
                }
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

    inner class DefaultNotificationViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        private val ivProfile = itemView.findViewById<CircleImageView>(R.id.ivNotificationProfile)
        private val ivIcon = itemView.findViewById<ImageView>(R.id.ivNotificationIcon)
        private val tvTitle = itemView.findViewById<TextView>(R.id.tvNotificationTitle)
        private val tvMessage = itemView.findViewById<TextView>(R.id.tvNotificationMessage)
        private val tvTime = itemView.findViewById<TextView>(R.id.tvNotificationTime)
        private val tvBadge = itemView.findViewById<TextView>(R.id.tvNotificationBadge)
        private val ivDelete = itemView.findViewById<ImageView>(R.id.ivDeleteNotification)

        fun bind(notification: NotificationModel, position: Int) {
            val sender = notification.fromUserName ?: "Someone"

            val title = when (notification.type) {
                "like", "like_item" -> "Liked your item"
                "message", "chat_message" -> "New message"
                "trade_request" -> "Trade request"
                "trade_accepted" -> "Trade accepted"
                "trade_rejected" -> "Trade rejected"
                "coins" -> "Coins received"
                "referral_reward" -> "Referral reward"
                "receipt_created", "trade_receipt" -> "Trade receipt"
                else -> "BarterHub notification"
            }

            val message = notification.message.takeIf { !it.isNullOrBlank() } ?: when (notification.type) {
                "like", "like_item" -> "$sender liked your item"
                "message", "chat_message" -> "$sender sent you a message"
                "trade_request" -> "$sender sent you a trade request"
                "trade_accepted" -> "Your trade request was accepted"
                "trade_rejected" -> "Your trade request was rejected"
                "coins" -> "$sender sent you ${notification.coins ?: 0} coins"
                "referral_reward" -> "You received a referral reward"
                "receipt_created", "trade_receipt" -> "Your trade receipt is ready"
                else -> "You have a new notification"
            }

            val iconRes = when (notification.type) {
                "like", "like_item" -> R.drawable.ic_like
                "message", "chat_message" -> R.drawable.ic_message
                "trade_request" -> R.drawable.ic_trade
                "trade_accepted" -> R.drawable.ic_check
                "trade_rejected" -> R.drawable.ic_close
                "coins" -> R.drawable.ic_coin
                "referral_reward" -> R.drawable.ic_bonus
                "receipt_created", "trade_receipt" -> R.drawable.ic_notifications
                else -> R.drawable.ic_notifications
            }

            tvTitle.text = title
            tvMessage.text = message
            tvTime.text = getTimeAgo(notification.timestamp)
            ivIcon.setImageResource(iconRes)

            tvBadge.visibility = if (notification.read == false) View.VISIBLE else View.GONE

            val profileUrl = notification.fromUserProfile
            if (!profileUrl.isNullOrBlank() && profileUrl != "null") {
                Glide.with(itemView.context)
                    .load(profileUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .circleCrop()
                    .into(ivProfile)
            } else {
                ivProfile.setImageResource(R.drawable.ic_profile_placeholder)

                if (!notification.fromUserId.isNullOrBlank()) {
                    fetchAndUpdateProfile(notification.fromUserId!!, ivProfile)
                }
            }

            itemView.setOnClickListener {
                onNotificationClickListener?.onNotificationClick(notification)
            }

            ivDelete.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onNotificationActionListener?.onDeleteNotification(notification.id, pos)
                }
            }
        }

        private fun fetchAndUpdateProfile(userId: String, imageView: CircleImageView) {
            database.child("public_users").child(userId).get()
                .addOnSuccessListener { snapshot ->
                    val url = snapshot.child("profileImageUrl").getValue(String::class.java)
                        ?: snapshot.child("profileImage").getValue(String::class.java)

                    if (!url.isNullOrBlank() && url != "null") {
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