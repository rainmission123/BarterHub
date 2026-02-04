import android.content.Context
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.barterhub.BuildConfig

object CloudinaryUtils {

    @Volatile private var initialized = false

    fun initCloudinary(context: Context) {
        if (initialized) return

        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
        val apiKey = BuildConfig.CLOUDINARY_API_KEY

        // Optional: fail-fast kung wala sa local.properties
        if (cloudName.isBlank() || apiKey.isBlank()) {
            throw IllegalStateException("Cloudinary keys missing. Check local.properties.")
        }

        val config = mapOf(
            "cloud_name" to cloudName,
            "api_key" to apiKey
            // ✅ NO api_secret in app code
        )

        try {
            MediaManager.init(context.applicationContext, config)
            initialized = true
        } catch (_: IllegalStateException) {
            // MediaManager already initialized by another call
            initialized = true
        }
    }

    fun uploadImage(uri: String, callback: (String?, String?) -> Unit) {
        MediaManager.get().upload(uri)
            .callback(object : UploadCallback {
                override fun onSuccess(requestId: String?, resultData: Map<*, *>?) {
                    val secureUrl = resultData?.get("secure_url") as? String
                    callback(secureUrl, null)
                }

                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}

                override fun onReschedule(requestId: String?, error: ErrorInfo?) {
                    callback(null, error?.description)
                }

                override fun onError(requestId: String?, error: ErrorInfo?) {
                    callback(null, error?.description)
                }

                override fun onStart(requestId: String?) {}
            })
            .dispatch()
    }
}
