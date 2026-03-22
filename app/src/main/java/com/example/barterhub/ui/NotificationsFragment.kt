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

        swipeRefresh.setOnRefreshListener {
            loadNotifications()
        }
    }

    private fun setupRecyclerView() {
        adapter = NotificationsAdapter(notificationsList)

        adapter.setOnNotificationActionListener(object : NotificationsAdapter.OnNotificationActionListener {
            override fun onAcceptFriend(notificationId: String?, fromUserId: String?, position: Int) {
                if (fromUserId == null) return
                addFriendManager.acceptFriendRequest(fromUserId)
                loadNotifications()
            }

            override fun onDeclineFriend(notificationId: String?, position: Int) {
                val notification = notificationsList.getOrNull(position) ?: return
                val fromUserId = notification.fromUserId ?: return
                addFriendManager.rejectFriendRequest(fromUserId)
                loadNotifications()
            }

            override fun onDeleteNotification(notificationId: String?, position: Int) {
                deleteNotification(notificationId, position)
            }
        })

        adapter.setOnNotificationClickListener(object : NotificationsAdapter.OnNotificationClickListener {
            override fun onNotificationClick(notification: NotificationModel) {

                val type = notification.type.orEmpty()

                if (type == "receipt_created" || type == "trade_receipt") {

                    val receiptId = notification.receiptId.orEmpty()
                    if (receiptId.isBlank()) {
                        Toast.makeText(requireContext(), "Receipt info missing", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val bundle = Bundle().apply {
                        putString("receiptId", receiptId)
                    }

                    findNavController().navigate(R.id.receiptFragment, bundle)
                    return
                }

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
                        val bundle = Bundle().apply { putString("ownerId", fromUserId) }
                        findNavController().navigate(R.id.ownerProfileFragment, bundle)
                    }
                    else -> {
                        Toast.makeText(requireContext(), "No action for this notification", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })


        rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        rvNotifications.adapter = adapter
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


                            // 🔍 DEBUG LOGS
                            Log.d("NotificationsDebug", "   Type: $type")
                            Log.d("NotificationsDebug", "   FromUserId: $fromUserId")
                            Log.d("NotificationsDebug", "   FromUserName: $fromUserName")
                            Log.d("NotificationsDebug", "   FromUserProfile: $fromUserProfile")

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
                                receiptId = receiptId
                            )

                            if (type == "friend_request" && fromUserId != null &&
                                (fromUserName.isNullOrEmpty() || fromUserProfile.isNullOrEmpty())) {

                                Log.d("NotificationsDebug", "   ⚠️ Missing user details, fetching...")
                                fetchUserDetails(fromUserId) { user ->
                                    // Update notification with fetched details
                                    val updatedNotif = notif.copy(
                                        fromUserName = user.username ?: user.fullName ?: "User",
                                        fromUserProfile = user.profileImageUrl
                                    )

                                    // Update in list
                                    val index = notificationsList.indexOfFirst { it.id == notifKey }
                                    if (index != -1) {
                                        notificationsList[index] = updatedNotif
                                        adapter.notifyItemChanged(index)
                                    }
                                }
                            }

                            notificationsList.add(notif)
                            Log.d("NotificationsDebug", "✅ Added notification: ${notif.type} from ${notif.fromUserName}")
                        }
                    } else {
                        Log.d("NotificationsDebug", "❌ No notifications found for user $userId")
                    }

                    // Sort by latest first
                    notificationsList.sortByDescending { it.timestamp }
                    adapter.notifyDataSetChanged()

                    layoutEmpty.visibility = if (notificationsList.isEmpty()) View.VISIBLE else View.GONE

                    markAllNotificationsAsRead()

                    swipeRefresh.isRefreshing = false

                }

                override fun onCancelled(error: DatabaseError) {
                    swipeRefresh.isRefreshing = false
                    Log.e("NotificationsDebug", "❌ Error loading notifications: ${error.message}")
                    Toast.makeText(requireContext(), "Failed to load notifications", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // 🔹 FETCH USER DETAILS FUNCTION
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

                        Log.d("NotificationsDebug", "   ✅ Fetched user: ${user.username}")
                        callback(user)
                    } catch (e: Exception) {
                        Log.e("NotificationsDebug", "   ❌ Error parsing user: ${e.message}")
                        callback(User(userId = userId))
                    }
                } else {
                    Log.d("NotificationsDebug", "   ❌ User not found: $userId")
                    callback(User(userId = userId))
                }
            }
            .addOnFailureListener { e ->
                Log.e("NotificationsDebug", "   ❌ Error fetching user: ${e.message}")
                callback(User(userId = userId))
            }
    }

    private fun markAllNotificationsAsRead() {
        val uid = auth.currentUser?.uid ?: return

        database.child("notifications").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return

                    val updates = mutableMapOf<String, Any>()

                    for (child in snapshot.children) {
                        val read = child.child("read").getValue(Boolean::class.java) ?: false
                        if (!read) {
                            updates["${child.key}/read"] = true
                        }
                    }

                    if (updates.isNotEmpty()) {
                        database.child("notifications").child(uid)
                            .updateChildren(updates)
                            .addOnFailureListener { e ->
                                Log.e("NotificationsFragment", "❌ markAllNotificationsAsRead failed: ${e.message}")
                            }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("NotificationsFragment", "❌ markAllNotificationsAsRead cancelled: ${error.message}")
                }
            })
    }

    private fun deleteNotification(notificationId: String?, position: Int) {
        val uid = auth.currentUser?.uid ?: return
        if (notificationId == null) return

        Log.d("NotificationsFragment", "🗑️ Deleting notification: $notificationId")

        database.child("notifications")
            .child(uid)
            .child(notificationId)
            .removeValue()
            .addOnSuccessListener {
                // Remove from adapter locally
                adapter.removeNotification(position)

                Toast.makeText(
                    requireContext(),
                    "Notification deleted",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                Log.e("NotificationsFragment", "❌ Error deleting notification: ${e.message}")
                Toast.makeText(
                    requireContext(),
                    "Failed to delete notification",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}