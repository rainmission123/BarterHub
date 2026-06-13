package com.example.barterhub.utils

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

object UserPresenceManager {
    private const val TAG = "UserPresenceManager"
    private const val DATABASE_URL = "https://barterhub-3c947-default-rtdb.firebaseio.com/"

    private val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(DATABASE_URL)
    }

    private var currentUserId: String? = null
    private var connectedListener: ValueEventListener? = null

    fun start(userId: String) {
        if (userId.isBlank()) return
        if (currentUserId == userId && connectedListener != null) return

        stop()
        currentUserId = userId

        val connectedRef = database.getReference(".info/connected")
        val statusRef = database.getReference("status").child(userId)

        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) == true
                if (!connected) return

                val offlineStatus = mapOf(
                    "state" to "offline",
                    "lastSeen" to ServerValue.TIMESTAMP
                )
                val onlineStatus = mapOf(
                    "state" to "online",
                    "lastSeen" to ServerValue.TIMESTAMP
                )

                statusRef.onDisconnect().setValue(offlineStatus)
                statusRef.setValue(onlineStatus)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Presence listener cancelled: ${error.message}")
            }
        }

        connectedRef.addValueEventListener(connectedListener as ValueEventListener)
    }

    fun stop(markOffline: Boolean = true) {
        val userId = currentUserId
        val listener = connectedListener

        if (listener != null) {
            database.getReference(".info/connected").removeEventListener(listener)
        }

        if (markOffline && !userId.isNullOrBlank()) {
            val offlineStatus = mapOf(
                "state" to "offline",
                "lastSeen" to ServerValue.TIMESTAMP
            )
            database.getReference("status").child(userId).setValue(offlineStatus)
        }

        connectedListener = null
        currentUserId = null
    }
}
