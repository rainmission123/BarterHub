package com.example.barterhub.ui.profile

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.barterhub.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class AddFriendManager(private val fragment: Fragment) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    // ===============================
    // 🤝 SEND FRIEND REQUEST
    // ===============================
    fun sendFriendRequest(toUserId: String) {
        val fromUserId = auth.currentUser?.uid ?: return

        if (fromUserId == toUserId) return

        Log.d("AddFriendManager", "🤝 Friend request: $fromUserId → $toUserId")

        val requestData = mapOf(
            "status" to "pending",
            "timestamp" to ServerValue.TIMESTAMP
        )

        // ✅ NEW STRUCTURE
        db.child("friendRequests")
            .child(toUserId)
            .child(fromUserId)
            .setValue(requestData)
            .addOnSuccessListener {
                createFriendRequestNotification(toUserId, fromUserId)
                toast("Friend request sent")
            }
            .addOnFailureListener {
                Log.e("AddFriendManager", "❌ Failed to send request: ${it.message}")
            }
    }

    // ===============================
    // ✅ ACCEPT FRIEND REQUEST
    // ===============================
    fun acceptFriendRequest(fromUserId: String) {
        val currentUserId = auth.currentUser?.uid ?: return

        val updates = hashMapOf<String, Any?>(
            // ✅ Add friends (both sides)
            "friends/$currentUserId/$fromUserId" to true,
            "friends/$fromUserId/$currentUserId" to true,

            // ❌ Remove pending request
            "friendRequests/$currentUserId/$fromUserId" to null
        )

        db.updateChildren(updates)
            .addOnSuccessListener {
                Log.d("AddFriendManager", "✅ Friend accepted")
                toast("You are now friends")
            }
            .addOnFailureListener {
                Log.e("AddFriendManager", "❌ Accept failed: ${it.message}")
            }
    }

    // ===============================
    // ❌ REJECT FRIEND REQUEST
    // ===============================
    fun rejectFriendRequest(fromUserId: String) {
        val currentUserId = auth.currentUser?.uid ?: return

        db.child("friendRequests")
            .child(currentUserId)
            .child(fromUserId)
            .removeValue()
            .addOnSuccessListener {
                toast("Friend request rejected")
            }
    }

    // ===============================
    // 🔔 NOTIFICATION
    // ===============================
    private fun createFriendRequestNotification(
        toUserId: String,
        fromUserId: String
    ) {
        val notificationId = db.child("notifications")
            .child(toUserId)
            .push()
            .key ?: return

        val notificationData = mapOf(
            "type" to "friend_request",
            "fromUserId" to fromUserId,
            "read" to false,
            "timestamp" to ServerValue.TIMESTAMP
        )

        db.child("notifications")
            .child(toUserId)
            .child(notificationId)
            .setValue(notificationData)
    }

    // ===============================
    // 📍 NAVIGATION / UI OPTIONS
    // ===============================
    fun goDirectlyToFindFriends() {
        try {
            fragment.findNavController()
                .navigate(R.id.action_profileFragment_to_findFriendsFragment)
        } catch (e: Exception) {
            toast("Find Friends feature is not available")
        }
    }

    fun showSimpleAddFriendOptions() {
        if (!fragment.isAdded) return

        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle("Add Friend")
            .setMessage("Go to Find Friends screen?")
            .setPositiveButton("Find Friends") { dialog, _ ->
                goDirectlyToFindFriends()
                dialog.dismiss()
            }
            .setNegativeButton("Share Invite Link") { dialog, _ ->
                shareInviteLink()
                dialog.dismiss()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    fun handleAddFriendClick() {
        goDirectlyToFindFriends()
    }

    // ===============================
    // 🔗 INVITE LINK
    // ===============================
    private fun shareInviteLink() {
        val userId = auth.currentUser?.uid ?: return
        val inviteLink = "https://barterhub.ph/invite/$userId"

        val shareMessage =
            "Join me on BarterHub! Let's trade items together.\n$inviteLink"

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareMessage)
            type = "text/plain"
        }

        fragment.startActivity(
            Intent.createChooser(shareIntent, "Share Invite Link")
        )

        toast("Share this link with your friends!")
    }

    // ===============================
    // 🧩 UTIL
    // ===============================
    private fun toast(message: String) {
        if (fragment.isAdded) {
            Toast.makeText(
                fragment.requireContext(),
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
