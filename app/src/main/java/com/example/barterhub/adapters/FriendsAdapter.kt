package com.example.barterhub.adapters

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.data.models.User
import com.example.barterhub.databinding.ItemFriendBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class FriendsAdapter(
    private val friends: MutableList<User>,
    private val onItemClick: (User, Action) -> Unit
) : RecyclerView.Adapter<FriendsAdapter.ViewHolder>() {

    enum class Action {
        REMOVE_FRIEND,
        MESSAGE,
        VIEW_PROFILE
    }

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    inner class ViewHolder(val binding: ItemFriendBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val friend = friends[position]

        with(holder.binding) {
            userNameText.text = friend.getDisplayName()
            userLocationText.text = friend.getLocation()
            userRatingText.text = friend.getRatingText()
            tradesText.text = "• ${friend.tradesCompleted} trades"

            val profileImageUrl = friend.getProfileImage()
            if (!profileImageUrl.isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(profileImageUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .circleCrop()
                    .into(profileImage)
            } else {
                profileImage.setImageResource(R.drawable.ic_profile_placeholder)
            }

            onlineIndicator.visibility =
                if (friend.isOnline) View.VISIBLE else View.GONE

            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return

            friendsButton.setOnClickListener {
                onItemClick(friend, Action.REMOVE_FRIEND)
            }

            messageButton.setOnClickListener {
                onItemClick(friend, Action.MESSAGE)
            }

            cardView.setOnClickListener {
                onItemClick(friend, Action.VIEW_PROFILE)
            }

            profileImage.setOnLongClickListener {
                showQuickActionsDialog(holder.itemView, friend, pos)
                true
            }
        }
    }

    override fun getItemCount(): Int = friends.size

    // ✅ QUICK ACTIONS
    private fun showQuickActionsDialog(
        view: View,
        friend: User,
        position: Int
    ) {
        AlertDialog.Builder(view.context)
            .setTitle(friend.getDisplayName())
            .setItems(arrayOf("Send Message", "View Profile", "Remove Friend")) { _, which ->
                when (which) {
                    0 -> onItemClick(friend, Action.MESSAGE)
                    1 -> onItemClick(friend, Action.VIEW_PROFILE)
                    2 -> showRemoveFriendDialog(view, friend, position)
                }
            }
            .show()
    }

    // ✅ REMOVE FRIEND CONFIRMATION
    private fun showRemoveFriendDialog(
        view: View,
        friend: User,
        position: Int
    ) {
        AlertDialog.Builder(view.context)
            .setTitle("Remove Friend")
            .setMessage("Remove ${friend.getDisplayName()} from friends?")
            .setPositiveButton("Remove") { _, _ ->
                removeFriendFromFirebase(view, friend.userId, position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ✅ REMOVE FRIEND (FIREBASE + UI)
    private fun removeFriendFromFirebase(
        view: View,
        friendUserId: String,
        position: Int
    ) {
        val currentUserId = auth.currentUser?.uid ?: return

        val updates = hashMapOf<String, Any?>(
            "friends/$currentUserId/$friendUserId" to null,
            "friends/$friendUserId/$currentUserId" to null
        )

        database.updateChildren(updates)
            .addOnSuccessListener {
                if (position in friends.indices) {
                    friends.removeAt(position)
                    notifyItemRemoved(position)
                }

                Toast.makeText(
                    view.context,
                    "Friend removed",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener {
                Toast.makeText(
                    view.context,
                    "Failed to remove friend",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    fun updateFriends(newFriends: List<User>) {
        friends.clear()
        friends.addAll(newFriends)
        notifyDataSetChanged()
    }

    // ✅ UPDATE ONLINE STATUS
    fun updateOnlineStatus(userId: String, isOnline: Boolean) {
        val index = friends.indexOfFirst { it.userId == userId }
        if (index != -1) {
            friends[index] = friends[index].copy(isOnline = isOnline)
            notifyItemChanged(index)
        }
    }
}
