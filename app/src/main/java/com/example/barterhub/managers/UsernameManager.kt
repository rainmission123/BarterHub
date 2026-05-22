package com.example.barterhub.managers

import com.google.firebase.database.FirebaseDatabase

class UsernameManager {

    private val database = FirebaseDatabase.getInstance().reference

    fun updateUsername(
        uid: String,
        oldUsername: String,
        newUsernameInput: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val oldUsernameClean = oldUsername.trim().lowercase()
        val newUsername = newUsernameInput.trim().lowercase()

        if (!isValidUsername(newUsername)) {
            onError("Username must be 4-20 characters, lowercase letters, numbers, or underscore only.")
            return
        }

        if (oldUsernameClean == newUsername) {
            onSuccess()
            return
        }

        val usernamesRef = database.child("usernames").child(newUsername)

        usernamesRef.get()
            .addOnSuccessListener { snapshot ->
                val existingUid = snapshot.getValue(String::class.java)

                if (snapshot.exists() && existingUid != uid) {
                    onError("Username already taken.")
                    return@addOnSuccessListener
                }

                val updates = hashMapOf<String, Any?>()

                if (oldUsernameClean.isNotEmpty()) {
                    updates["usernames/$oldUsernameClean"] = null
                }

                updates["usernames/$newUsername"] = uid
                updates["users/$uid/username"] = newUsername
                updates["public_users/$uid/username"] = newUsername
                updates["users/$uid/updatedAt"] = System.currentTimeMillis()
                updates["public_users/$uid/updatedAt"] = System.currentTimeMillis()

                database.updateChildren(updates)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e ->
                        onError(e.message ?: "Failed to update username.")
                    }
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to check username.")
            }
    }

    fun isValidUsername(username: String): Boolean {
        val regex = Regex("^[a-z0-9_]{4,20}$")
        return regex.matches(username)
    }
}