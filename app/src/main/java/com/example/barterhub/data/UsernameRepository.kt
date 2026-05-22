package com.example.barterhub.data

import com.google.firebase.database.FirebaseDatabase

class UsernameRepository {

    private val usernamesRef by lazy {
        FirebaseDatabase
            .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("usernames")
    }

    fun checkUsernameAvailable(
        username: String,
        onAvailable: () -> Unit,
        onTaken: () -> Unit,
        onError: (String) -> Unit
    ) {
        usernamesRef.child(username).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) onTaken() else onAvailable()
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to check username")
            }
    }

    fun saveUsernameIndex(
        username: String,
        uid: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        usernamesRef.child(username).setValue(uid)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to save username")
            }
    }
}