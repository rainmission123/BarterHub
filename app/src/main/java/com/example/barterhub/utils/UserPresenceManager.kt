package com.example.barterhub.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

object UserPresenceManager {
    private const val DATABASE_URL = "https://barterhub-3c947-default-rtdb.firebaseio.com/"
    private const val TAG = "UserPresenceManager"
    private const val HEARTBEAT_INTERVAL_MS = 60_000L

    private val database = FirebaseDatabase.getInstance(DATABASE_URL)
    private val statusRef = database.getReference("status")
    private val connectedRef = database.getReference(".info/connected")

    private var currentUserId: String? = null
    private var connectedListener: ValueEventListener? = null
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null

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

                registerOnDisconnect(userId, userStatusRef, offlineStatus) {
                    setOnline(userId, userStatusRef, onlineStatus)
                    startHeartbeat(userId, userStatusRef)
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
        stopHeartbeat()

        val userId = currentUserId
        currentUserId = null

        if (markOffline && !userId.isNullOrBlank()) {
            setOffline(
                userId,
                statusRef.child(userId),
                mapOf(
                    "state" to "offline",
                    "lastSeen" to ServerValue.TIMESTAMP,
                    "isOnline" to false
                )
            )
        }
    }

    private fun registerOnDisconnect(
        userId: String,
        userStatusRef: DatabaseReference,
        offlineStatus: Map<String, Any>,
        onRegistered: () -> Unit
    ) {
        userStatusRef.onDisconnect().setValue(offlineStatus)
            .addOnSuccessListener {
                onRegistered()
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to register onDisconnect: ${error.message}")
            }
    }

    private fun setOnline(
        userId: String,
        userStatusRef: DatabaseReference,
        onlineStatus: Map<String, Any>
    ) {
        userStatusRef.setValue(onlineStatus)
            .addOnSuccessListener {
                Log.d(TAG, "Presence online SUCCESS: $userId")
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Presence online FAILED: ${error.message}", error)
            }
    }

    private fun startHeartbeat(userId: String, userStatusRef: DatabaseReference) {
        stopHeartbeat()

        heartbeatRunnable = object : Runnable {
            override fun run() {
                if (currentUserId != userId) return

                userStatusRef.updateChildren(
                    mapOf(
                        "state" to "online",
                        "lastSeen" to ServerValue.TIMESTAMP,
                        "isOnline" to true
                    )
                ).addOnFailureListener { error ->
                    Log.e(TAG, "Presence heartbeat failed: ${error.message}", error)
                }

                heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }

        heartbeatHandler.postDelayed(heartbeatRunnable!!, HEARTBEAT_INTERVAL_MS)
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let { heartbeatHandler.removeCallbacks(it) }
        heartbeatRunnable = null
    }

    private fun setOffline(
        userId: String,
        userStatusRef: DatabaseReference,
        offlineStatus: Map<String, Any>
    ) {
        userStatusRef.setValue(offlineStatus)
            .addOnFailureListener { error ->
                Log.e(TAG, "Presence offline FAILED: ${error.message}", error)
            }
    }
}
