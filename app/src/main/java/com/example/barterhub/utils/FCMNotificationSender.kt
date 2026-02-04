package com.example.barterhub.utils

import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

object FCMNotificationSender {

    private const val FCM_URL = "https://fcm.googleapis.com/fcm/send"

    private const val SERVER_KEY = "KEY_MO_DITO"

    fun send(
        token: String,
        title: String,
        message: String
    ) {
        Thread {
            try {
                val payload = JSONObject().apply {
                    put("to", token)
                    put(
                        "notification",
                        JSONObject().apply {
                            put("title", title)
                            put("body", message)
                        }
                    )
                }

                val url = URL(FCM_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Authorization", "key=$SERVER_KEY")
                    setRequestProperty("Content-Type", "application/json")
                }

                val outputStream: OutputStream = conn.outputStream
                outputStream.write(payload.toString().toByteArray())
                outputStream.flush()
                outputStream.close()

                val responseCode = conn.responseCode
                conn.disconnect()

                // Optional: log success
                // Log.d("FCM", "Response code: $responseCode")

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
