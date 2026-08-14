package com.example.barterhub.adapters.message.reactions

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MessageReactionController(
    private val chatId: String,
    private val currentUserId: String,
    private val database: DatabaseReference =
        FirebaseDatabase.getInstance().reference,
    private val onReactionsChanged: (
        messageId: String,
        reactions: Map<String, Map<String, Boolean>>
    ) -> Unit
) {

    companion object {
        private const val TAG = "ReactionController"
    }

    private val reactionListeners =
        mutableMapOf<String, ValueEventListener>()

    fun toggleReaction(
        messageId: String,
        emoji: String
    ) {
        if (messageId.isBlank() || emoji.isBlank()) {
            Log.e(TAG, "Cannot toggle reaction: blank messageId or emoji")
            return
        }

        val reactionsRef = getReactionsReference(messageId)

        reactionsRef.addListenerForSingleValueEvent(
            object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    val alreadySelected = snapshot
                        .child(emoji)
                        .child(currentUserId)
                        .exists()

                    val updates = mutableMapOf<String, Any?>()

                    for (emojiSnapshot in snapshot.children) {
                        val existingEmoji = emojiSnapshot.key ?: continue

                        if (
                            emojiSnapshot
                                .child(currentUserId)
                                .exists()
                        ) {
                            updates["$existingEmoji/$currentUserId"] = null
                        }
                    }

                    if (!alreadySelected) {
                        updates["$emoji/$currentUserId"] = true
                    }

                    if (updates.isEmpty()) {
                        return
                    }

                    reactionsRef
                        .updateChildren(updates)
                        .addOnSuccessListener {
                            Log.d(
                                TAG,
                                "Reaction updated: messageId=$messageId emoji=$emoji"
                            )
                        }
                        .addOnFailureListener { error ->
                            Log.e(
                                TAG,
                                "Failed to update reaction",
                                error
                            )
                        }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(
                        TAG,
                        "Reaction lookup cancelled: ${error.message}"
                    )
                }
            }
        )
    }

    fun addReaction(
        messageId: String,
        emoji: String
    ) {
        if (messageId.isBlank() || emoji.isBlank()) {
            return
        }

        val reactionsRef = getReactionsReference(messageId)

        reactionsRef.addListenerForSingleValueEvent(
            object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    val updates = mutableMapOf<String, Any?>()

                    for (emojiSnapshot in snapshot.children) {
                        val existingEmoji = emojiSnapshot.key ?: continue

                        if (
                            emojiSnapshot
                                .child(currentUserId)
                                .exists()
                        ) {
                            updates["$existingEmoji/$currentUserId"] = null
                        }
                    }

                    updates["$emoji/$currentUserId"] = true

                    reactionsRef
                        .updateChildren(updates)
                        .addOnFailureListener { error ->
                            Log.e(
                                TAG,
                                "Failed to add reaction",
                                error
                            )
                        }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(
                        TAG,
                        "Add reaction cancelled: ${error.message}"
                    )
                }
            }
        )
    }

    fun removeReaction(
        messageId: String,
        emoji: String
    ) {
        if (messageId.isBlank() || emoji.isBlank()) {
            return
        }

        getReactionsReference(messageId)
            .child(emoji)
            .child(currentUserId)
            .removeValue()
            .addOnSuccessListener {
                Log.d(
                    TAG,
                    "Reaction removed: messageId=$messageId emoji=$emoji"
                )
            }
            .addOnFailureListener { error ->
                Log.e(
                    TAG,
                    "Failed to remove reaction",
                    error
                )
            }
    }

    fun startListening(messageId: String) {
        if (
            messageId.isBlank() ||
            reactionListeners.containsKey(messageId)
        ) {
            return
        }

        val reactionsRef = getReactionsReference(messageId)

        val listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                val parsedReactions = parseReactions(snapshot)

                onReactionsChanged(
                    messageId,
                    parsedReactions
                )
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(
                    TAG,
                    "Reaction listener cancelled for $messageId: ${error.message}"
                )
            }
        }

        reactionsRef.addValueEventListener(listener)
        reactionListeners[messageId] = listener
    }

    fun stopListening(messageId: String) {
        val listener = reactionListeners.remove(messageId) ?: return

        getReactionsReference(messageId)
            .removeEventListener(listener)

        Log.d(
            TAG,
            "Reaction listener removed for $messageId"
        )
    }

    fun stopAll() {
        val listenerEntries = reactionListeners.toMap()

        listenerEntries.forEach { (messageId, listener) ->
            getReactionsReference(messageId)
                .removeEventListener(listener)
        }

        reactionListeners.clear()

        Log.d(TAG, "All reaction listeners removed")
    }

    private fun parseReactions(
        snapshot: DataSnapshot
    ): Map<String, Map<String, Boolean>> {

        val parsed =
            mutableMapOf<String, Map<String, Boolean>>()

        for (emojiSnapshot in snapshot.children) {
            val emoji = emojiSnapshot.key ?: continue

            val users =
                mutableMapOf<String, Boolean>()

            for (userSnapshot in emojiSnapshot.children) {
                val userId = userSnapshot.key ?: continue

                val reacted =
                    userSnapshot.getValue(Boolean::class.java) ?: false

                if (reacted) {
                    users[userId] = true
                }
            }

            if (users.isNotEmpty()) {
                parsed[emoji] = users
            }
        }

        return parsed
    }

    private fun getReactionsReference(
        messageId: String
    ): DatabaseReference {
        return database
            .child("chats")
            .child(chatId)
            .child("messages")
            .child(messageId)
            .child("reactions")
    }
}