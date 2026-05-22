package com.example.barterhub.ui.profile

import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class AddFriendManager(private val fragment: Fragment) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    fun sendFriendRequest(
        toUserId: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val fromUserId = auth.currentUser?.uid ?: return
        if (fromUserId == toUserId) return

        Log.d("AddFriendManager", "🤝 Friend request: $fromUserId → $toUserId")

        val alreadyFriendRef = db.child("friends")
            .child(fromUserId)
            .child(toUserId)

        val sentRequestRef = db.child("userFriendRequests")
            .child(fromUserId)
            .child("sent")
            .child(toUserId)

        val receivedRequestRef = db.child("userFriendRequests")
            .child(fromUserId)
            .child("received")
            .child(toUserId)

        alreadyFriendRef.get()
            .addOnSuccessListener { friendSnapshot ->
                if (friendSnapshot.getValue(Boolean::class.java) == true) {
                    toast("You are already friends")
                    onError?.invoke("already_friends")
                    return@addOnSuccessListener
                }

                sentRequestRef.get()
                    .addOnSuccessListener { sentSnapshot ->
                        if (sentSnapshot.exists()) {
                            toast("Friend request already sent")
                            onError?.invoke("already_sent")
                            return@addOnSuccessListener
                        }

                        receivedRequestRef.get()
                            .addOnSuccessListener { receivedSnapshot ->
                                if (receivedSnapshot.exists()) {
                                    toast("This user already sent you a friend request")
                                    onError?.invoke("already_received")
                                    return@addOnSuccessListener
                                }

                                val timestamp = System.currentTimeMillis()
                                val updates = hashMapOf<String, Any?>()

                                updates["userFriendRequests/$fromUserId/sent/$toUserId"] = timestamp
                                updates["userFriendRequests/$toUserId/received/$fromUserId"] = timestamp

                                db.updateChildren(updates)
                                    .addOnSuccessListener {
                                        toast("Friend request sent")
                                        onSuccess?.invoke()
                                    }
                                    .addOnFailureListener {
                                        Log.e("AddFriendManager", "❌ Failed to send request: ${it.message}")
                                        toast("Failed to send friend request")
                                        onError?.invoke(it.message ?: "send_failed")
                                    }
                            }
                            .addOnFailureListener {
                                Log.e("AddFriendManager", "❌ Failed to check received request: ${it.message}")
                                toast("Failed to send friend request")
                                onError?.invoke(it.message ?: "check_received_failed")
                            }
                    }
                    .addOnFailureListener {
                        Log.e("AddFriendManager", "❌ Failed to check sent request: ${it.message}")
                        toast("Failed to send friend request")
                        onError?.invoke(it.message ?: "check_sent_failed")
                    }
            }
            .addOnFailureListener {
                Log.e("AddFriendManager", "❌ Failed to check friendship: ${it.message}")
                toast("Failed to send friend request")
                onError?.invoke(it.message ?: "check_friendship_failed")
            }
    }

    fun acceptFriendRequest(
        fromUserId: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val currentUserId = auth.currentUser?.uid ?: return

        val requestRef = db.child("userFriendRequests")
            .child(currentUserId)
            .child("received")
            .child(fromUserId)

        requestRef.get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    toast("This friend request is no longer available")
                    onError?.invoke("request_missing")
                    return@addOnSuccessListener
                }

                val updates = hashMapOf<String, Any?>(
                    "friends/$currentUserId/$fromUserId" to true,
                    "friends/$fromUserId/$currentUserId" to true,
                    "userFriendRequests/$currentUserId/received/$fromUserId" to null,
                    "userFriendRequests/$fromUserId/sent/$currentUserId" to null
                )

                db.updateChildren(updates)
                    .addOnSuccessListener {
                        Log.d("AddFriendManager", "✅ Friend accepted")
                        toast("You are now friends")
                        onSuccess?.invoke()
                    }
                    .addOnFailureListener {
                        Log.e("AddFriendManager", "❌ Accept failed: ${it.message}")
                        toast("Failed to accept friend request")
                        onError?.invoke(it.message ?: "accept_failed")
                    }
            }
            .addOnFailureListener {
                Log.e("AddFriendManager", "❌ Failed to verify friend request: ${it.message}")
                toast("Failed to accept friend request")
                onError?.invoke(it.message ?: "verify_request_failed")
            }
    }

    fun rejectFriendRequest(
        fromUserId: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val currentUserId = auth.currentUser?.uid ?: return

        db.child("notifications").child(currentUserId).get()
            .addOnSuccessListener { snapshot ->
                val updates = hashMapOf<String, Any?>(
                    "userFriendRequests/$currentUserId/received/$fromUserId" to null,
                    "userFriendRequests/$fromUserId/sent/$currentUserId" to null
                )

                for (notifSnapshot in snapshot.children) {
                    val type = notifSnapshot.child("type").getValue(String::class.java)
                    val notifFromUserId = notifSnapshot.child("fromUserId")
                        .getValue(String::class.java)

                    if (type == "friend_request" && notifFromUserId == fromUserId) {
                        updates["notifications/$currentUserId/${notifSnapshot.key}"] = null
                    }
                }

                db.updateChildren(updates)
                    .addOnSuccessListener {
                        toast("Friend request rejected")
                        onSuccess?.invoke()
                    }
                    .addOnFailureListener {
                        Log.e("AddFriendManager", "❌ Reject failed: ${it.message}")
                        toast("Failed to reject request")
                        onError?.invoke(it.message ?: "reject_failed")
                    }
            }
            .addOnFailureListener {
                Log.e("AddFriendManager", "❌ Failed to load notifications: ${it.message}")
                toast("Failed to reject request")
                onError?.invoke(it.message ?: "load_notifications_failed")
            }
    }

    fun cancelFriendRequest(
        targetUserId: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val currentUserId = auth.currentUser?.uid ?: return

        db.child("notifications").child(targetUserId).get()
            .addOnSuccessListener { snapshot ->
                val updates = hashMapOf<String, Any?>(
                    "userFriendRequests/$currentUserId/sent/$targetUserId" to null,
                    "userFriendRequests/$targetUserId/received/$currentUserId" to null
                )

                for (notifSnapshot in snapshot.children) {
                    val type = notifSnapshot.child("type").getValue(String::class.java)
                    val fromUserId = notifSnapshot.child("fromUserId")
                        .getValue(String::class.java)

                    if (type == "friend_request" && fromUserId == currentUserId) {
                        updates["notifications/$targetUserId/${notifSnapshot.key}"] = null
                    }
                }

                db.updateChildren(updates)
                    .addOnSuccessListener {
                        toast("Request cancelled")
                        onSuccess?.invoke()
                    }
                    .addOnFailureListener {
                        Log.e("AddFriendManager", "❌ Failed to cancel request: ${it.message}")
                        toast("Failed to cancel request")
                        onError?.invoke(it.message ?: "cancel_failed")
                    }
            }
            .addOnFailureListener {
                Log.e("AddFriendManager", "❌ Failed to load notifications: ${it.message}")
                toast("Failed to cancel request")
                onError?.invoke(it.message ?: "load_notifications_failed")
            }
    }

    private fun removeStaleFriendRequestNotification(
        currentUserId: String,
        fromUserId: String
    ) {
        db.child("notifications").child(currentUserId).get()
            .addOnSuccessListener { snapshot ->
                val updates = hashMapOf<String, Any?>()

                for (notifSnapshot in snapshot.children) {
                    val type = notifSnapshot.child("type").getValue(String::class.java)
                    val notifFromUserId = notifSnapshot.child("fromUserId")
                        .getValue(String::class.java)

                    if (type == "friend_request" && notifFromUserId == fromUserId) {
                        updates["notifications/$currentUserId/${notifSnapshot.key}"] = null
                    }
                }

                if (updates.isNotEmpty()) {
                    db.updateChildren(updates)
                }
            }
    }

    private fun toast(message: String) {
        if (fragment.isAdded) {
            Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}