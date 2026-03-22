package com.example.barterhub.utils

import android.content.Context
import android.net.Uri
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object CloudinaryUtils {

    private const val CLOUD_NAME = "dtccox0s0"
    private const val UPLOAD_PRESET = "barterhub_ids"
    private const val BASE_URL = "https://api.cloudinary.com/v1_1/$CLOUD_NAME"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun uploadImage(
        context: Context,
        imageUri: Uri,
        onProgress: ((Int) -> Unit)? = null,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val bytes = inputStream?.readBytes() ?: return

            val requestBody = object : RequestBody() {
                override fun contentType() = "image/*".toMediaType()

                override fun writeTo(sink: BufferedSink) {
                    val buffer = bytes
                    val total = buffer.size.toLong()
                    var uploaded: Long = 0

                    val source = buffer.inputStream().source()
                    var read: Long
                    val SEGMENT_SIZE = 2048L
                    while (source.read(sink.buffer, SEGMENT_SIZE).also { read = it } != -1L) {
                        uploaded += read
                        sink.flush()
                        val progress = (100 * uploaded / total).toInt()
                        onProgress?.invoke(progress)
                    }
                }

                override fun contentLength() = bytes.size.toLong()
            }

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "image.jpg", requestBody)
                .addFormDataPart("upload_preset", UPLOAD_PRESET)
                .addFormDataPart("cloud_name", CLOUD_NAME)
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/image/upload")
                .post(multipartBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onFailure(e)
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    if (!response.isSuccessful) {
                        onFailure(Exception("Upload failed"))
                        return
                    }

                    val json = JSONObject(response.body.string())
                    val url = json.getString("secure_url")
                    onSuccess(url)
                }
            })

        } catch (e: Exception) {
            onFailure(e)
        }
    }

    fun uploadVideo(
        videoUri: Uri,
        context: Context,
        onProgress: ((Int) -> Unit)? = null,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val inputStream = context.contentResolver.openInputStream(videoUri)
            val bytes = inputStream?.readBytes() ?: throw Exception("Failed to read video")

            val requestBody = object : RequestBody() {
                override fun contentType() = "video/*".toMediaType()

                override fun writeTo(sink: BufferedSink) {
                    val total = bytes.size.toLong()
                    var uploaded: Long = 0

                    val source = bytes.inputStream().source()
                    var read: Long
                    val SEGMENT_SIZE = 8192L

                    while (source.read(sink.buffer, SEGMENT_SIZE).also { read = it } != -1L) {
                        uploaded += read
                        sink.flush()
                        val progress = (100 * uploaded / total).toInt()
                        onProgress?.invoke(progress)
                    }
                }

                override fun contentLength() = bytes.size.toLong()
            }

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "video.mp4", requestBody)
                .addFormDataPart("upload_preset", UPLOAD_PRESET)
                .addFormDataPart("cloud_name", CLOUD_NAME)
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/video/upload")
                .post(multipartBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onFailure(e)
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            onFailure(Exception("Upload failed with status: ${response.code}"))
                            return
                        }

                        val json = JSONObject(response.body!!.string())
                        val url = json.getString("secure_url")
                        onSuccess(url)
                    }
                }
            })
        } catch (e: Exception) {
            onFailure(e)
        }
    }
}