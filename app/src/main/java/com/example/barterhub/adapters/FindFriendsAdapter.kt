package com.example.barterhub.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.data.models.FriendStatus
import com.example.barterhub.data.models.User
import com.example.barterhub.databinding.ItemFindFriendBinding

class FindFriendsAdapter(
    private val users: List<User>,
    private val onItemClick: (User, Action) -> Unit
) : RecyclerView.Adapter<FindFriendsAdapter.ViewHolder>() {

    enum class Action {
        ADD_FRIEND,
        ACCEPT_REQUEST,
        CANCEL_REQUEST,
        VIEW_PROFILE
    }

    inner class ViewHolder(val binding: ItemFindFriendBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFindFriendBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]

        with(holder.binding) {
            // 1. Set user name
            userNameText.text = user.getDisplayName()

            // 2. Set location
            userLocationText.text = user.getLocation()

            // 3. Show online status - SIMPLIFIED: No bg_offline_status
            if (user.isOnline) {
                statusText.text = "Online"
                statusText.visibility = View.VISIBLE
                statusText.setTextColor(holder.itemView.context.resources.getColor(android.R.color.holo_green_dark))
            } else if (user.lastSeen > 0) {
                val timeAgo = getTimeAgo(user.lastSeen)
                statusText.text = "Last seen $timeAgo"
                statusText.visibility = View.VISIBLE
                statusText.setTextColor(holder.itemView.context.resources.getColor(android.R.color.darker_gray))
            } else {
                statusText.visibility = View.GONE
            }

            // 4. Load profile image
            val profileImageUrl = user.getProfileImage()
            if (profileImageUrl != null && profileImageUrl.isNotEmpty()) {
                try {
                    Glide.with(holder.itemView.context)
                        .load(profileImageUrl)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .circleCrop()
                        .into(profileImage)
                } catch (e: Exception) {
                    profileImage.setImageResource(R.drawable.ic_profile_placeholder)
                }
            } else {
                profileImage.setImageResource(R.drawable.ic_profile_placeholder)
            }

            when (user.friendStatus) {
                FriendStatus.NOT_FRIEND -> {
                    actionButton.visibility = View.VISIBLE
                    actionButton.text = "Add Friend"
                    actionButton.setIconResource(R.drawable.ic_add_friend)
                    actionButton.isEnabled = true
                    actionButton.isClickable = true
                    actionButton.alpha = 1.0f
                    actionButton.setOnClickListener {
                        onItemClick(user, Action.ADD_FRIEND)
                    }
                }

                FriendStatus.REQUEST_SENT -> {
                    actionButton.visibility = View.VISIBLE
                    actionButton.text = "Cancel Request"
                    actionButton.setIconResource(R.drawable.ic_clock)
                    actionButton.isEnabled = true
                    actionButton.isClickable = true
                    actionButton.alpha = 1.0f
                    actionButton.setOnClickListener {
                        onItemClick(user, Action.CANCEL_REQUEST)
                    }
                }

                FriendStatus.REQUEST_RECEIVED -> {
                    actionButton.visibility = View.VISIBLE
                    actionButton.text = "Accept Request"
                    actionButton.setIconResource(R.drawable.ic_check)
                    actionButton.isEnabled = true
                    actionButton.isClickable = true
                    actionButton.alpha = 1.0f
                    actionButton.setOnClickListener {
                        onItemClick(user, Action.ACCEPT_REQUEST)
                    }
                }

                FriendStatus.FRIENDS -> {
                    actionButton.visibility = View.VISIBLE
                    actionButton.text = "Friends"
                    actionButton.setIconResource(R.drawable.ic_check)
                    actionButton.isEnabled = false
                    actionButton.isClickable = false
                    actionButton.alpha = 0.7f
                    actionButton.setOnClickListener(null)
                }

                else -> {
                    actionButton.visibility = View.GONE
                    actionButton.setOnClickListener(null)
                }
            }

            root.setOnClickListener {
                onItemClick(user, Action.VIEW_PROFILE)
            }
        }
    }

    override fun getItemCount() = users.size

    // Helper function for time ago
    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60000 -> "just now"
            diff < 3600000 -> "${diff / 60000} min ago"
            diff < 86400000 -> "${diff / 3600000} hours ago"
            diff < 604800000 -> "${diff / 86400000} days ago"
            else -> "${diff / 604800000} weeks ago"
        }
    }
}