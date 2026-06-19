package com.example.barterhub.utils

import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.example.barterhub.R

class BottomNavBadgeManager(
    private val bottomNav: BottomNavigationView
) {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase
        .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
        .reference

    private var messageListener: ValueEventListener? = null
    private var notificationListener: ValueEventListener? = null

    // =========================
    // 🔵 MESSAGES BADGE
    // =========================
    fun listenForMessagesBadge() {
        val uid = auth.currentUser?.uid ?: return

        val chatsRef = database.child("chats")

        messageListener = chatsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var totalUnread = 0

                for (chatSnap in snapshot.children) {
                    if (!chatSnap.child("participants").child(uid).exists() &&
                        !chatSnap.key.orEmpty().contains(uid)
                    ) {
                        continue
                    }

                    val unreadFromCounter = chatSnap.child("unreadCount")
                        .child(uid)
                        .getValue(Int::class.java)

                    totalUnread += unreadFromCounter
                        ?: chatSnap.child("messages").children.count { messageSnap ->
                            val read = messageSnap.child("read")
                                .getValue(Boolean::class.java) ?: true
                            val receiverId = messageSnap.child("receiverId")
                                .getValue(String::class.java)

                            !read && receiverId == uid
                        }
                }

                updateBadge(R.id.nav_messages, totalUnread)
            }

            override fun onCancelled(error: DatabaseError) {
                updateBadge(R.id.nav_messages, 0)
            }
        })
    }

    // =========================
    // 🔴 PROFILE NOTIFICATIONS BADGE
    // =========================
    fun listenForProfileBadge() {
        val uid = auth.currentUser?.uid ?: return

        val notifRef = database.child("notifications").child(uid)

        notificationListener = notifRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var unreadCount = 0

                for (notifSnap in snapshot.children) {
                    val isRead = notifSnap.child("read")
                        .getValue(Boolean::class.java) ?: false
                    val type = notifSnap.child("type")
                        .getValue(String::class.java)

                    if (!isRead && type != "system") {
                        unreadCount++
                    }
                }

                updateBadge(R.id.nav_profile, unreadCount)
            }

            override fun onCancelled(error: DatabaseError) {
                updateBadge(R.id.nav_profile, 0)
            }
        })
    }

    // =========================
    // 🔧 COMMON BADGE FUNCTION
    // =========================
    private fun updateBadge(menuId: Int, count: Int) {
        val badge = bottomNav.getOrCreateBadge(menuId)

        if (count > 0) {
            badge.isVisible = true
            badge.number = count.coerceAtMost(99)
        } else {
            badge.clearNumber()
            badge.isVisible = false
        }
    }

    // =========================
    // 🧹 CLEANUP
    // =========================
    fun removeListeners() {
        val uid = auth.currentUser?.uid ?: return

        messageListener?.let {
            database.child("chats").removeEventListener(it)
        }

        notificationListener?.let {
            database.child("notifications").child(uid).removeEventListener(it)
        }
    }
}
