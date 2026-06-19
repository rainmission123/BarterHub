package com.example.barterhub.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.barterhub.R
import com.example.barterhub.adapters.NotificationsAdapter
import com.example.barterhub.data.NotificationModel
import com.example.barterhub.data.models.User
import com.example.barterhub.ui.profile.AddFriendManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class NotificationsFragment : Fragment(R.layout.fragment_notifications) {

    private lateinit var addFriendManager: AddFriendManager
    private lateinit var rvNotifications: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private val notificationsList = mutableListOf<NotificationModel>()
    private lateinit var adapter: NotificationsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addFriendManager = AddFriendManager(this)
        rvNotifications = view.findViewById(R.id.rvNotifications)
        swipeRefresh = view.findViewById(R.id.swipeRefreshNotifications)
        layoutEmpty = view.findViewById(R.id.layoutEmptyNotifications)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        setupRecyclerView()
        loadNotifications()
        markAllNotificationsAsRead()

        swipeRefresh.setOnRefreshListener {
            loadNotifications()
            markAllNotificationsAsRead()
        }
    }

    override fun onResume() {
        super.onResume()
        markAllNotificationsAsRead()
    }

    private fun markAllNotificationsAsRead() {
        val uid = auth.currentUser?.uid ?: return

        database.child("notifications")
            .child(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                for (notifSnapshot in snapshot.children) {
                    val type = notifSnapshot.child("type").getValue(String::class.java)
                    val isRead = notifSnapshot.child("read").getValue(Boolean::class.java) ?: false

                    if (!isRead && type != "system" && type != "message" && type != "chat_message") {
                        notifSnapshot.ref.child("read").setValue(true)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("NotificationsFragment", "❌ Failed to mark all notifications as read: ${e.message}")
            }
    }

    private fun setupRecyclerView() {
        adapter = NotificationsAdapter(notificationsList)

        adapter.setOnNotificationActionListener(object : NotificationsAdapter.OnNotificationActionListener {
            override fun onAcceptFriend(notificationId: String?, fromUserId: String?, position: Int) {
                if (fromUserId == null || notificationId.isNullOrBlank()) return

                addFriendManager.acceptFriendRequest(
                    fromUserId = fromUserId,
                    onSuccess = {
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                        if (currentUserId != null) {
                            val acceptEvent = mapOf(
                                "timestamp" to ServerValue.TIMESTAMP
                            )

                            val eventRef = FirebaseDatabase.getInstance()
                                .getReference("friend_accept_events")
                                .child(fromUserId)
                                .child(currentUserId)
                                .push()

                            eventRef.setValue(
                                mapOf(
                                    "timestamp" to ServerValue.TIMESTAMP
                                )
                            ).addOnSuccessListener {
                                Log.d(
                                    "FriendAcceptDebug",
                                    "✅ friend_accept_events written: recipient=$fromUserId acceptor=$currentUserId key=${eventRef.key}"
                                )
                            }.addOnFailureListener { e ->
                                Log.e(
                                    "FriendAcceptDebug",
                                    "❌ failed to write friend_accept_events: ${e.message}"
                                )
                            }
                        }

                        activity?.runOnUiThread {
                            updateNotificationStatus(notificationId, "accepted")
                            adapter.updateNotificationStatus(notificationId, "accepted", position)
                        }
                    },
                    onError = {
                        activity?.runOnUiThread {
                            loadNotifications()
                        }
                    }
                )
            }

            override fun onDeclineFriend(notificationId: String?, position: Int) {
                val notification = notificationsList.getOrNull(position) ?: return
                val fromUserId = notification.fromUserId ?: return
                if (notificationId.isNullOrBlank()) return

                addFriendManager.rejectFriendRequest(
                    fromUserId = fromUserId,
                    onSuccess = {
                        activity?.runOnUiThread {
                            updateNotificationStatus(notificationId, "declined")
                            adapter.updateNotificationStatus(notificationId, "declined", position)
                        }
                    },
                    onError = {
                        activity?.runOnUiThread {
                            loadNotifications()
                        }
                    }
                )
            }

            override fun onDeleteNotification(notificationId: String?, position: Int) {
                deleteNotification(notificationId, position)
            }
        })

        adapter.setOnNotificationClickListener(object : NotificationsAdapter.OnNotificationClickListener {
            override fun onNotificationClick(notification: NotificationModel) {
                markNotificationAsRead(notification.id)

                val type = notification.type.orEmpty()

                when (type) {
                    "receipt_created", "trade_receipt" -> {
                        val receiptId = notification.receiptId.orEmpty()
                        if (receiptId.isBlank()) {
                            Toast.makeText(requireContext(), "Receipt info missing", Toast.LENGTH_SHORT).show()
                            return
                        }

                        val bundle = Bundle().apply {
                            putString("receiptId", receiptId)
                        }

                        findNavController().navigate(R.id.receiptFragment, bundle)
                    }

                    "referral_reward" -> {
                        val invitedUserId = notification.invitedUserId.orEmpty()

                        if (invitedUserId.isBlank()) {
                            Toast.makeText(requireContext(), "Referral details missing", Toast.LENGTH_SHORT).show()
                            return
                        }

                        val bundle = Bundle().apply {
                            putString("ownerId", invitedUserId)
                        }

                        findNavController().navigate(R.id.ownerProfileFragment, bundle)
                    }

                    "friend_accept" -> {
                        val fromUserId = notification.fromUserId.orEmpty()
                        if (fromUserId.isBlank()) {
                            Toast.makeText(requireContext(), "Friend info missing", Toast.LENGTH_SHORT).show()
                            return
                        }

                        val bundle = Bundle().apply {
                            putString("ownerId", fromUserId)
                        }
                        findNavController().navigate(R.id.ownerProfileFragment, bundle)
                    }

                    "trade_request" -> {
                        findNavController().navigate(R.id.tradeRequestsFragment)
                    }

                    "trade_accepted",
                    "rating_submitted" -> {
                        val chatId = notification.chatId.orEmpty()
                        val partnerId = notification.partnerId.orEmpty()
                        val partnerName = notification.partnerName.orEmpty()

                        if (chatId.isBlank() || partnerId.isBlank()) {
                            Toast.makeText(
                                requireContext(),
                                "Chat information missing",
                                Toast.LENGTH_SHORT
                            ).show()
                            return
                        }

                        val bundle = Bundle().apply {
                            putString("chatId", chatId)
                            putString("partnerId", partnerId)
                            putString("partnerName", partnerName.ifBlank { "User" })
                            putBoolean("isTradeAccepted", true)
                        }

                        findNavController().navigate(
                            R.id.nav_chat,
                            bundle
                        )
                    }

                    else -> {
                        val itemId = notification.itemId
                        val fromUserId = notification.fromUserId

                        when {
                            !itemId.isNullOrBlank() -> {
                                val bundle = Bundle().apply {
                                    putString("itemId", itemId)
                                    putString("ownerId", fromUserId ?: "")
                                }
                                findNavController().navigate(R.id.nav_item_detail, bundle)
                            }

                            !fromUserId.isNullOrBlank() -> {
                                val bundle = Bundle().apply {
                                    putString("ownerId", fromUserId)
                                }
                                findNavController().navigate(R.id.ownerProfileFragment, bundle)
                            }

                            else -> {
                                Toast.makeText(requireContext(), "No action for this notification", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        })

        rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        rvNotifications.adapter = adapter
    }

    private fun updateNotificationStatus(notificationId: String, status: String) {
        val uid = auth.currentUser?.uid ?: return

        val updates = hashMapOf<String, Any>(
            "status" to status,
            "read" to true
        )

        database.child("notifications")
            .child(uid)
            .child(notificationId)
            .updateChildren(updates)
            .addOnSuccessListener {
                Log.d("NotificationsFragment", "✅ Notification $notificationId updated to $status")
            }
            .addOnFailureListener { e ->
                Log.e("NotificationsFragment", "❌ Failed to update notification status: ${e.message}")
            }
    }

    private fun loadNotifications() {
        val userId = auth.currentUser?.uid ?: return
        swipeRefresh.isRefreshing = true

        database.child("notifications").child(userId)
            .orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    notificationsList.clear()

                    if (snapshot.exists()) {
                        for (notifSnapshot in snapshot.children) {
                            val notifKey = notifSnapshot.key
                            Log.d("NotificationsDebug", "📦 Processing notification: $notifKey")

                            val type = notifSnapshot.child("type").getValue(String::class.java)
                            val fromUserId = notifSnapshot.child("fromUserId").getValue(String::class.java)
                            val fromUserName = notifSnapshot.child("fromUserName").getValue(String::class.java)
                            val fromUserProfile = notifSnapshot.child("fromUserProfile").getValue(String::class.java)
                                ?: notifSnapshot.child("fromUserProfilePic").getValue(String::class.java)
                            val itemId = notifSnapshot.child("itemId").getValue(String::class.java)
                            val read = notifSnapshot.child("read").getValue(Boolean::class.java) ?: false
                            val timestamp = notifSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                            val coins = notifSnapshot.child("coins").getValue(Int::class.java) ?: 0
                            val status = notifSnapshot.child("status").getValue(String::class.java)
                            val message = notifSnapshot.child("message").getValue(String::class.java)
                            val chatId = notifSnapshot.child("chatId").getValue(String::class.java)
                            val partnerId = notifSnapshot.child("partnerId").getValue(String::class.java)
                            val partnerName = notifSnapshot.child("partnerName").getValue(String::class.java)
                            val requestId = notifSnapshot.child("requestId").getValue(String::class.java)
                            val receiptId = notifSnapshot.child("receiptId").getValue(String::class.java)
                            val invitedUserId = notifSnapshot.child("invitedUserId").getValue(String::class.java)

                            val notif = NotificationModel(
                                id = notifKey,
                                type = type,
                                fromUserId = fromUserId,
                                fromUserName = fromUserName,
                                fromUserProfile = fromUserProfile,
                                itemId = itemId,
                                read = read,
                                timestamp = timestamp,
                                coins = coins,
                                status = status,
                                message = message,
                                chatId = chatId,
                                partnerId = partnerId,
                                partnerName = partnerName,
                                requestId = requestId,
                                receiptId = receiptId,
                                invitedUserId = invitedUserId
                            )

                            // 🔥 FIX: SKIP CHAT NOTIFICATIONS
                            if (type == "message" || type == "chat_message") {
                                continue
                            }

                            // 🔹 Handle friend request missing data
                            if (type == "friend_request" &&
                                fromUserId != null &&
                                (fromUserName.isNullOrEmpty() || fromUserProfile.isNullOrEmpty())
                            ) {
                                fetchUserDetails(fromUserId) { user ->
                                    val updatedNotif = notif.copy(
                                        fromUserName = user.username ?: user.fullName ?: "User",
                                        fromUserProfile = user.profileImageUrl
                                    )

                                    val index = notificationsList.indexOfFirst { it.id == notifKey }
                                    if (index != -1) {
                                        notificationsList[index] = updatedNotif
                                        adapter.notifyItemChanged(index)
                                    }
                                }
                            }

                            notificationsList.add(notif)
                        }
                    }

                    notificationsList.sortByDescending { it.timestamp }
                    adapter.notifyDataSetChanged()

                    layoutEmpty.visibility =
                        if (notificationsList.isEmpty()) View.VISIBLE else View.GONE

                    swipeRefresh.isRefreshing = false
                }

                override fun onCancelled(error: DatabaseError) {
                    swipeRefresh.isRefreshing = false
                    Log.e("NotificationsDebug", "❌ Error loading notifications: ${error.message}")
                    Toast.makeText(requireContext(), "Failed to load notifications", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun fetchUserDetails(userId: String, callback: (User) -> Unit) {
        database.child("users").child(userId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    try {
                        val data = snapshot.value as? Map<String, Any>
                        val user = User(
                            userId = userId,
                            username = data?.get("username") as? String,
                            fullName = data?.get("fullName") as? String,
                            email = data?.get("email") as? String,
                            profileImageUrl = data?.get("profileImageUrl") as? String,
                            address = data?.get("address") as? String,
                            phoneNumber = data?.get("phoneNumber") as? String,
                            bio = data?.get("bio") as? String,
                            rating = (data?.get("rating") as? Double?) ?: (data?.get("rating") as? Int?)?.toDouble(),
                            coins = (data?.get("coins") as? Int?) ?: 0
                        )
                        callback(user)
                    } catch (e: Exception) {
                        callback(User(userId = userId))
                    }
                } else {
                    callback(User(userId = userId))
                }
            }
            .addOnFailureListener {
                callback(User(userId = userId))
            }
    }

    private fun markNotificationAsRead(notificationId: String?) {
        val uid = auth.currentUser?.uid ?: return
        if (notificationId.isNullOrBlank()) return

        database.child("notifications")
            .child(uid)
            .child(notificationId)
            .child("read")
            .setValue(true)
            .addOnFailureListener { e ->
                Log.e("NotificationsFragment", "❌ Failed to mark notification as read: ${e.message}")
            }
    }

    private fun deleteNotification(notificationId: String?, position: Int) {
        val uid = auth.currentUser?.uid ?: return
        if (notificationId == null) return

        database.child("notifications")
            .child(uid)
            .child(notificationId)
            .removeValue()
            .addOnSuccessListener {
                adapter.removeNotification(position)
                Toast.makeText(requireContext(), "Notification deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("NotificationsFragment", "❌ Error deleting notification: ${e.message}")
                Toast.makeText(requireContext(), "Failed to delete notification", Toast.LENGTH_SHORT).show()
            }
    }
}
