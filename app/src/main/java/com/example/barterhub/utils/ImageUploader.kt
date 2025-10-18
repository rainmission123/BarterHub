package com.example.barterhub.utils

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.UUID

object ImageUploader {

    fun uploadProfilePicture(
        context: Context,
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference
        val profilePicturesRef: StorageReference = storageRef.child("profile_pictures/${UUID.randomUUID()}")

        profilePicturesRef.putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                // Get download URL
                profilePicturesRef.downloadUrl
                    .addOnSuccessListener { uri ->
                        onSuccess(uri.toString())
                    }
                    .addOnFailureListener { exception ->
                        onFailure(exception)
                    }
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    fun uploadChatImage(
        context: Context,
        imageUri: Uri,
        chatId: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference
        val chatImagesRef: StorageReference = storageRef.child("chat_images/$chatId/${UUID.randomUUID()}")

        chatImagesRef.putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                chatImagesRef.downloadUrl
                    .addOnSuccessListener { uri ->
                        onSuccess(uri.toString())
                    }
                    .addOnFailureListener { exception ->
                        onFailure(exception)
                    }
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }
}