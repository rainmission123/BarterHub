package com.example.barterhub.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import java.io.OutputStream
import androidx.core.graphics.createBitmap

object ReceiptSaver {

    fun saveViewToGallery(
        context: Context,
        view: View,
        fileNameNoExt: String,
        tag: String = "ReceiptSaver",
        onDone: (Uri?) -> Unit
    ) {
        try {
            val bitmap = createBitmapFromView(view)
            val uri = saveBitmapToMediaStore(context, bitmap, fileNameNoExt, tag)
            onDone(uri)
        } catch (e: Exception) {
            Log.e(tag, "❌ saveViewToGallery error: ${e.message}", e)
            onDone(null)
        }
    }

    private fun createBitmapFromView(view: View): Bitmap {
        // ensure measured
        if (view.width == 0 || view.height == 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }

        val bitmap = createBitmap(view.width, view.height)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun saveBitmapToMediaStore(
        context: Context,
        bitmap: Bitmap,
        fileNameNoExt: String,
        tag: String
    ): Uri? {
        val resolver = context.contentResolver
        val fileName = "$fileNameNoExt.jpg"
        val mimeType = "image/jpeg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/BarterHub"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        var out: OutputStream? = null
        return try {
            out = resolver.openOutputStream(uri) ?: return null
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.flush()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            Log.e(tag, "❌ saveBitmapToMediaStore error: ${e.message}", e)
            null
        } finally {
            try { out?.close() } catch (_: Exception) {}
        }
    }
}
