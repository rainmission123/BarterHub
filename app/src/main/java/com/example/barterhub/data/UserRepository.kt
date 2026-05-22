package com.example.barterhub.data

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class UserRepository {

    private val firestore = FirebaseFirestore.getInstance()

    // ✅ PRIVATE USERS NODE
    private val usersDb by lazy {
        FirebaseDatabase
            .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("users")
    }

    // ✅ PUBLIC USERS NODE
    private val publicUsersDb by lazy {
        FirebaseDatabase
            .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
            .getReference("public_users")
    }

    fun saveUser(
        userId: String,
        fullName: String,
        username: String,
        email: String,
        address: String,
        province: String,
        cityMunicipality: String,
        referralCode: String,
        referredBy: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        // ---------------------------------------------------
        // 🔥 FIRESTORE SAVE
        // ---------------------------------------------------

        val firestoreMap = hashMapOf(
            "fullName" to fullName,
            "username" to username,
            "email" to email,
            "address" to address,
            "province" to province,
            "cityMunicipality" to cityMunicipality,
            "referralCode" to referralCode,
            "referredBy" to (referredBy ?: ""),
            "createdAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("users")
            .document(userId)
            .set(firestoreMap)
            .addOnFailureListener {
                Log.e("UserRepository", "Firestore error", it)
            }

        // ---------------------------------------------------
        // 🔥 PRIVATE USER DATA
        // users/{uid}
        // ---------------------------------------------------

        val now = System.currentTimeMillis()

        val privateMap = hashMapOf(

            "fullName" to fullName,
            "username" to username,
            "email" to email,

            "address" to address,
            "province" to province,
            "cityMunicipality" to cityMunicipality,

            // ✅ Wallet
            "wallet" to mapOf(
                "coins" to 0
            ),

            // ✅ Premium
            "isPremium" to false,
            "premiumExpiry" to 0L,

            // ✅ Verification
            "isIDVerified" to "none",

            // ✅ Ratings
            "rating" to 0,
            "reviewsCount" to 0,

            // ✅ Profile
            "profileImage" to "",

            // ✅ Referral
            "referralCode" to referralCode,
            "referredBy" to (referredBy ?: ""),

            // ✅ Timestamps
            "createdAt" to now,
            "updatedAt" to now
        )

        // ---------------------------------------------------
        // 🔥 PUBLIC USER DATA
        // public_users/{uid}
        // ---------------------------------------------------

        val publicMap = hashMapOf(

            "fullName" to fullName,
            "username" to username,

            // ✅ Public profile image
            "profileImage" to "",

            // ✅ Public premium info
            "isPremium" to false,
            "premiumExpiry" to 0L,

            // ✅ Public verification
            "isIDVerified" to "none",

            // ✅ Public ratings
            "rating" to 0,
            "reviewsCount" to 0,

            // ✅ Public timestamps
            "createdAt" to now
        )

        // ---------------------------------------------------
        // 🔥 SAVE PRIVATE USER
        // ---------------------------------------------------

        usersDb.child(userId)
            .setValue(privateMap)

            .addOnSuccessListener {

                // ---------------------------------------------------
                // 🔥 SAVE PUBLIC USER
                // ---------------------------------------------------

                publicUsersDb.child(userId)
                    .setValue(publicMap)

                    .addOnSuccessListener {

                        Log.d(
                            "UserRepository",
                            "✅ User + Public User created"
                        )

                        onSuccess()
                    }

                    .addOnFailureListener {

                        onError(
                            it.message
                                ?: "Failed to save public user"
                        )
                    }
            }

            .addOnFailureListener {

                onError(
                    it.message
                        ?: "Failed to save user"
                )
            }
    }
}