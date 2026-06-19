package com.example.barterhub.utils

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

object UserPresenceManager {
    private const val DATABASE_URL = "https://barterhub-3c947-default-rtdb.firebaseio.com/"
    private const val TAG = "UserPresenceManager"

    private val database = FirebaseDatabase.getInstance(DATABASE_URL)
    private val statusRef = database.getReference("status")
    private val connectedRef = database.getReference(".info/connected")

    private var currentUserId: String? = null
    private var connectedListener: ValueEventListener? = null

    fun start(userId: String) {
        if (userId.isBlank()) return
        if (currentUserId == userId && connectedListener != null) return

        stop(markOffline = false)
        currentUserId = userId

        val userStatusRef = statusRef.child(userId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) == true
                if (!connected) return

                val offlineStatus = mapOf(
                    "state" to "offline",
                    "lastSeen" to ServerValue.TIMESTAMP,
                    "isOnline" to false
                )

                val onlineStatus = mapOf(
                    "state" to "online",
                    "lastSeen" to ServerValue.TIMESTAMP,
                    "isOnline" to true
                )

                userStatusRef.onDisconnect().setValue(offlineStatus)
                    .addOnSuccessListener {
                        userStatusRef.setValue(onlineStatus)
                            .addOnSuccessListener {
                                Log.d(TAG, "Presence online SUCCESS: $userId")
                            }
                            .addOnFailureListener { error ->
                                Log.e(TAG, "Presence online FAILED: ${error.message}", error)
                            }
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Failed to register onDisconnect: ${error.message}")
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Connection listener cancelled: ${error.message}")
            }
        }

        connectedListener = listener
        connectedRef.addValueEventListener(listener)
    }

    fun stop(markOffline: Boolean = true) {
        connectedListener?.let { connectedRef.removeEventListener(it) }
        connectedListener = null

        val userId = currentUserId
        currentUserId = null

        if (markOffline && !userId.isNullOrBlank()) {
            statusRef.child(userId).setValue(
                mapOf(
                    "state" to "offline",
                    "lastSeen" to ServerValue.TIMESTAMP,
                    "isOnline" to false
                )
            )
        }
    }
}
