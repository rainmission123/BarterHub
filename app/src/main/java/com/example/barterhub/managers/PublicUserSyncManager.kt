package com.example.barterhub.managers

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

object PublicUserSyncManager {

    private val db = FirebaseDatabase
        .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")

    fun ensurePublicUserExists() {

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val publicRef = db
            .getReference("public_users")
            .child(uid)

        // ---------------------------------------------------
        // ✅ CHECK IF PUBLIC USER EXISTS
        // ---------------------------------------------------

        publicRef.get()

            .addOnSuccessListener { publicSnap ->

                // ✅ Already exists
                if (publicSnap.exists()) {

                    Log.d(
                        "PublicUserSync",
                        "✅ public_users already exists"
                    )

                    return@addOnSuccessListener
                }

                // ---------------------------------------------------
                // ❌ Missing → CREATE FROM users NODE
                // ---------------------------------------------------

                db.getReference("users")
                    .child(uid)
                    .get()

                    .addOnSuccessListener { userSnap ->

                        if (!userSnap.exists()) {

                            Log.e(
                                "PublicUserSync",
                                "❌ users node missing"
                            )

                            return@addOnSuccessListener
                        }

                        // ---------------------------------------------------
                        // ✅ PROFILE IMAGE FIX
                        // Supports:
                        // profileImageUrl
                        // profileImage
                        // ---------------------------------------------------

                        val profileImageUrl =
                            userSnap.child("profileImageUrl")
                                .getValue(String::class.java)

                                ?: userSnap.child("profileImage")
                                    .getValue(String::class.java)

                                ?: ""

                        // ---------------------------------------------------
                        // ✅ CREATE SAFE PUBLIC MAP
                        // ---------------------------------------------------

                        val publicMap = hashMapOf(

                            // ✅ Public profile
                            "fullName" to (
                                    userSnap.child("fullName")
                                        .getValue(String::class.java)
                                        ?: ""
                                    ),

                            "username" to (
                                    userSnap.child("username")
                                        .getValue(String::class.java)
                                        ?: ""
                                    ),

                            // ✅ Compatible fields
                            "profileImageUrl" to profileImageUrl,
                            "profileImage" to profileImageUrl,

                            // ✅ Premium
                            "isPremium" to (
                                    userSnap.child("isPremium")
                                        .getValue(Boolean::class.java)
                                        ?: false
                                    ),

                            "premiumExpiry" to (
                                    userSnap.child("premiumExpiry")
                                        .getValue(Long::class.java)
                                        ?: 0L
                                    ),

                            // ✅ Verification
                            "isIDVerified" to (
                                    userSnap.child("isIDVerified")
                                        .getValue(String::class.java)
                                        ?: "none"
                                    ),

                            // ✅ Ratings
                            "rating" to (
                                    userSnap.child("rating")
                                        .getValue(Int::class.java)
                                        ?: 0
                                    ),

                            "reviewsCount" to (
                                    userSnap.child("reviewsCount")
                                        .getValue(Int::class.java)
                                        ?: 0
                                    ),

                            // ✅ Timestamp
                            "createdAt" to System.currentTimeMillis()
                        )

                        // ---------------------------------------------------
                        // ✅ SAVE PUBLIC USER
                        // ---------------------------------------------------

                        publicRef.setValue(publicMap)

                            .addOnSuccessListener {

                                Log.d(
                                    "PublicUserSync",
                                    "✅ public_users auto-created"
                                )
                            }

                            .addOnFailureListener {

                                Log.e(
                                    "PublicUserSync",
                                    "❌ Failed creating public user",
                                    it
                                )
                            }
                    }

                    .addOnFailureListener {

                        Log.e(
                            "PublicUserSync",
                            "❌ Failed reading users node",
                            it
                        )
                    }
            }

            .addOnFailureListener {

                Log.e(
                    "PublicUserSync",
                    "❌ Failed checking public_users",
                    it
                )
            }
    }
}