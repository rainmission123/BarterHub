package com.example.barterhub.ui.profile

import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
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

        val timestamp = System.currentTimeMillis()
        val updates = hashMapOf<String, Any>()

        updates["userFriendRequests/$fromUserId/sent/$toUserId"] = timestamp
        updates["userFriendRequests/$toUserId/received/$fromUserId"] = timestamp

        db.updateChildren(updates)
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

        val requestRef = db.child("userFriendRequests")
            .child(currentUserId)
            .child("received")
            .child(fromUserId)

        requestRef.get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    toast("This friend request is no longer available")
                    removeStaleFriendRequestNotification(currentUserId, fromUserId)
                    return@addOnSuccessListener
                }

                val updates = hashMapOf<String, Any?>(
                    "friends/$currentUserId/$fromUserId" to true,
                    "friends/$fromUserId/$currentUserId" to true,
                    "userFriendRequests/$currentUserId/received/$fromUserId" to null,
                    "userFriendRequests/$fromUserId/sent/$currentUserId" to null
                )

                db.child("notifications").child(currentUserId).get()
                    .addOnSuccessListener { notifSnapshot ->
                        for (child in notifSnapshot.children) {
                            val type = child.child("type").getValue(String::class.java)
                            val notifFromUserId = child.child("fromUserId").getValue(String::class.java)

                            if (type == "friend_request" && notifFromUserId == fromUserId) {
                                updates["notifications/$currentUserId/${child.key}"] = null
                            }
                        }

                        db.updateChildren(updates)
                            .addOnSuccessListener {
                                Log.d("AddFriendManager", "✅ Friend accepted")
                                toast("You are now friends")
                            }
                            .addOnFailureListener {
                                Log.e("AddFriendManager", "❌ Accept failed: ${it.message}")
                                toast("Failed to accept friend request")
                            }
                    }
                    .addOnFailureListener {
                        Log.e("AddFriendManager", "❌ Failed to load notifications: ${it.message}")
                        toast("Failed to accept friend request")
                    }
            }
            .addOnFailureListener {
                Log.e("AddFriendManager", "❌ Failed to verify friend request: ${it.message}")
                toast("Failed to accept friend request")
            }
    }

    // ===============================
    // ❌ REJECT FRIEND REQUEST
    // ===============================
    fun rejectFriendRequest(fromUserId: String) {
        val currentUserId = auth.currentUser?.uid ?: return

        db.child("notifications").child(currentUserId).get()
            .addOnSuccessListener { snapshot ->
                val updates = hashMapOf<String, Any?>(
                    "userFriendRequests/$currentUserId/received/$fromUserId" to null,
                    "userFriendRequests/$fromUserId/sent/$currentUserId" to null
                )

                for (notifSnapshot in snapshot.children) {
                    val type = notifSnapshot.child("type").getValue(String::class.java)
                    val notifFromUserId = notifSnapshot.child("fromUserId").getValue(String::class.java)

                    if (type == "friend_request" && notifFromUserId == fromUserId) {
                        updates["notifications/$currentUserId/${notifSnapshot.key}"] = null
                    }
                }

                db.updateChildren(updates)
                    .addOnSuccessListener {
                        toast("Friend request rejected")
                    }
                    .addOnFailureListener {
                        Log.e("AddFriendManager", "❌ Reject failed: ${it.message}")
                        toast("Failed to reject request")
                    }
            }
            .addOnFailureListener {
                Log.e("AddFriendManager", "❌ Failed to load notifications: ${it.message}")
                toast("Failed to reject request")
            }
    }

    // ===============================
    // ❌ CANCEL SENT REQUEST
    // ===============================
    fun cancelFriendRequest(targetUserId: String) {
        val currentUserId = auth.currentUser?.uid ?: return

        db.child("notifications").child(targetUserId).get()
            .addOnSuccessListener { snapshot ->
                val updates = hashMapOf<String, Any?>(
                    "userFriendRequests/$currentUserId/sent/$targetUserId" to null,
                    "userFriendRequests/$targetUserId/received/$currentUserId" to null
                )

                for (notifSnapshot in snapshot.children) {
                    val type = notifSnapshot.child("type").getValue(String::class.java)
                    val fromUserId = notifSnapshot.child("fromUserId").getValue(String::class.java)

                    if (type == "friend_request" && fromUserId == currentUserId) {
                        updates["notifications/$targetUserId/${notifSnapshot.key}"] = null
                    }
                }

                db.updateChildren(updates)
                    .addOnSuccessListener {
                        toast("Request cancelled")
                    }
                    .addOnFailureListener {
                        Log.e("AddFriendManager", "❌ Failed to cancel request: ${it.message}")
                        toast("Failed to cancel request")
                    }
            }
            .addOnFailureListener {
                Log.e("AddFriendManager", "❌ Failed to load notifications: ${it.message}")
                toast("Failed to cancel request")
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

    private fun removeStaleFriendRequestNotification(currentUserId: String, fromUserId: String) {
        db.child("notifications").child(currentUserId).get()
            .addOnSuccessListener { snapshot ->
                val updates = hashMapOf<String, Any?>()

                for (notifSnapshot in snapshot.children) {
                    val type = notifSnapshot.child("type").getValue(String::class.java)
                    val notifFromUserId = notifSnapshot.child("fromUserId").getValue(String::class.java)

                    if (type == "friend_request" && notifFromUserId == fromUserId) {
                        updates["notifications/$currentUserId/${notifSnapshot.key}"] = null
                    }
                }

                if (updates.isNotEmpty()) {
                    db.updateChildren(updates)
                }
            }
    }

    // ===============================
    // 🧩 UTIL
    // ===============================
    private fun toast(message: String) {
        if (fragment.isAdded) {
            Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}