package com.example.barterhub.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface PayMongoApiService {
    @POST("/paymongo")
    fun createCheckoutSession(
        @Header("Authorization") authorization: String,
        @Body request: PayMongoRequest
    ): Call<PayMongoResponse>
}
