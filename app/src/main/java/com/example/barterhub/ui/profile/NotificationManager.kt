package com.example.barterhub.ui.profile

import android.util.Log
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class NotificationManager(private val fragment: Fragment) {

    private val auth = FirebaseAuth.getInstance()
    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference
    private var notificationListener: ValueEventListener? = null

    fun setupUnreadNotificationsListener(tvUnreadNotifications: TextView) {
        val currentUserId = auth.currentUser?.uid ?: return

        Log.d("NotificationDebug", "🔔 Setting up notifications listener for user: $currentUserId")

        // Remove existing listener if any
        notificationListener?.let {
            database.child("notifications").child(currentUserId).removeEventListener(it)
        }

        notificationListener = database.child("notifications").child(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!fragment.isAdded) return

                    var unreadCount = 0

                    if (snapshot.exists()) {
                        for (notificationSnapshot in snapshot.children) {
                            val read = notificationSnapshot.child("read")
                                .getValue(Boolean::class.java) ?: false
                            val type = notificationSnapshot.child("type")
                                .getValue(String::class.java)

                            // Count unread notifications (excluding some types if needed)
                            if (!read && type != "system") {
                                unreadCount++
                            }

                            Log.d("NotificationDebug", "📌 Notification ${notificationSnapshot.key}: read=$read, type=$type")
                        }
                    }

                    Log.d("NotificationDebug", "📱 Total unread notifications: $unreadCount")
                    updateNotificationUI(unreadCount, tvUnreadNotifications)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("NotificationManager", "❌ Notifications listener cancelled: ${error.message}")
                    updateNotificationUI(0, tvUnreadNotifications)
                }
            })
    }

    private fun updateNotificationUI(count: Int, tvUnreadNotifications: TextView) {
        if (!fragment.isAdded) return

        if (count > 0) {
            tvUnreadNotifications.text = if (count > 9) "9+" else count.toString()
            tvUnreadNotifications.visibility = TextView.VISIBLE
        } else {
            tvUnreadNotifications.visibility = TextView.GONE
        }
    }

    fun removeListener() {
        val currentUserId = auth.currentUser?.uid ?: return
        notificationListener?.let {
            database.child("notifications").child(currentUserId).removeEventListener(it)
        }
    }
}