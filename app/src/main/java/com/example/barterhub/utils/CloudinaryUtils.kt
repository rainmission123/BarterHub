package com.example.barterhub.utils

import android.content.Context
import android.net.Uri
import com.google.firebase.functions.FirebaseFunctions
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object CloudinaryUtils {

    private const val CLOUD_NAME = "dtccox0s0"
    private const val BASE_URL = "https://api.cloudinary.com/v1_1/$CLOUD_NAME"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun uploadImage(
        context: Context,
        imageUri: Uri,
        onProgress: ((Int) -> Unit)? = null,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        getCloudinarySignature(
            onSuccess = { sig ->
                uploadToCloudinary(
                    context = context,
                    uri = imageUri,
                    resourceType = "image",
                    fileName = "image.jpg",
                    mimeType = "image/*",
                    signatureData = sig,
                    onProgress = onProgress,
                    onSuccess = onSuccess,
                    onFailure = onFailure
                )
            },
            onFailure = onFailure
        )
    }

    fun uploadVideo(
        videoUri: Uri,
        context: Context,
        onProgress: ((Int) -> Unit)? = null,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        getCloudinarySignature(
            onSuccess = { sig ->
                uploadToCloudinary(
                    context = context,
                    uri = videoUri,
                    resourceType = "video",
                    fileName = "video.mp4",
                    mimeType = "video/*",
                    signatureData = sig,
                    onProgress = onProgress,
                    onSuccess = onSuccess,
                    onFailure = onFailure
                )
            },
            onFailure = onFailure
        )
    }

    private fun getCloudinarySignature(
        onSuccess: (CloudinarySignatureData) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        FirebaseFunctions.getInstance("us-central1")
            .getHttpsCallable("getCloudinarySignature")
            .call()
            .addOnSuccessListener { result ->
                try {
                    val data = result.data as? Map<*, *>
                        ?: throw Exception("Invalid Cloudinary signature response")

                    val cloudName = data["cloudName"] as? String
                        ?: throw Exception("Missing cloudName")
                    val apiKey = data["apiKey"] as? String
                        ?: throw Exception("Missing apiKey")
                    val folder = data["folder"] as? String
                        ?: throw Exception("Missing folder")
                    val signature = data["signature"] as? String
                        ?: throw Exception("Missing signature")

                    val timestampValue = data["timestamp"]
                        ?: throw Exception("Missing timestamp")

                    val timestamp = when (timestampValue) {
                        is Number -> timestampValue.toLong().toString()
                        is String -> timestampValue
                        else -> throw Exception("Invalid timestamp")
                    }

                    onSuccess(
                        CloudinarySignatureData(
                            cloudName = cloudName,
                            apiKey = apiKey,
                            timestamp = timestamp,
                            folder = folder,
                            signature = signature
                        )
                    )
                } catch (e: Exception) {
                    onFailure(e)
                }
            }
            .addOnFailureListener { e ->
                onFailure(Exception(e.message ?: "Failed to get Cloudinary signature"))
            }
    }

    private fun uploadToCloudinary(
        context: Context,
        uri: Uri,
        resourceType: String,
        fileName: String,
        mimeType: String,
        signatureData: CloudinarySignatureData,
        onProgress: ((Int) -> Unit)?,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes()
            } ?: throw Exception("Failed to read file")

            val requestBody = object : RequestBody() {
                override fun contentType() = mimeType.toMediaType()

                override fun contentLength() = bytes.size.toLong()

                override fun writeTo(sink: BufferedSink) {
                    val total = bytes.size.toLong()
                    var uploaded = 0L
                    val source = bytes.inputStream().source()
                    var read: Long
                    val segmentSize = 8192L

                    while (source.read(sink.buffer, segmentSize).also { read = it } != -1L) {
                        uploaded += read
                        sink.flush()
                        val progress = (100 * uploaded / total).toInt()
                        onProgress?.invoke(progress)
                    }
                }
            }

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, requestBody)
                .addFormDataPart("api_key", signatureData.apiKey)
                .addFormDataPart("timestamp", signatureData.timestamp)
                .addFormDataPart("folder", signatureData.folder)
                .addFormDataPart("signature", signatureData.signature)
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/$resourceType/upload")
                .post(multipartBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onFailure(e)
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            val errorBody = it.body?.string().orEmpty()
                            onFailure(Exception("Upload failed: ${it.code} $errorBody"))
                            return
                        }

                        val json = JSONObject(it.body?.string().orEmpty())
                        val url = json.getString("secure_url")
                        onSuccess(url)
                    }
                }
            })
        } catch (e: Exception) {
            onFailure(e)
        }
    }

    private data class CloudinarySignatureData(
        val cloudName: String,
        val apiKey: String,
        val timestamp: String,
        val folder: String,
        val signature: String
    )
}