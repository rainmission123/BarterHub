package com.example.barterhub.domain

import com.example.barterhub.data.UserRepository
import com.example.barterhub.data.UsernameRepository
import com.google.firebase.auth.FirebaseAuth

class SignupManager(
    private val auth: FirebaseAuth,
    private val usernameRepo: UsernameRepository,
    private val userRepo: UserRepository
) {

    fun signup(
        fullName: String,
        username: String,
        email: String,
        password: String,
        address: String,
        province: String,
        cityMunicipality: String,
        referralCode: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        usernameRepo.checkUsernameAvailable(
            username,
            onAvailable = {

                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->

                        val user = result.user
                        val uid = user?.uid ?: return@addOnSuccessListener

                        user.sendEmailVerification()
                            .addOnFailureListener {
                                // optional lang: wag pabagsakin signup kung delay/error email
                            }

                        val myReferralCode = "BH-" + uid.takeLast(6).uppercase()

                        usernameRepo.saveUsernameIndex(
                            username,
                            uid,
                            onSuccess = {
                                userRepo.saveUser(
                                    userId = uid,
                                    fullName = fullName,
                                    username = username,
                                    email = email,
                                    address = address,
                                    province = province,
                                    cityMunicipality = cityMunicipality,
                                    referralCode = myReferralCode,
                                    referredBy = referralCode,
                                    onSuccess = {
                                        onSuccess()
                                    },
                                    onError = onError
                                )
                            },
                            onError = onError
                        )
                    }
            },
            onTaken = {
                onError("Username already taken")
            },
            onError = onError
        )
    }
}