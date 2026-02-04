package com.example.barterhub.binders

import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.adapters.MessagesAdapter
import com.example.barterhub.data.models.Message
import com.example.barterhub.ui.FullscreenImageActivity
import java.text.SimpleDateFormat
import java.util.*

class ImageMessageBinder(
    private val currentUserId: String,
    private val partnerProfilePic: String?,
    private val onReact: (Message) -> Unit  // 👈 ITO LANG ANG KAPAREHO SA VIDEO
) : MessageBinder {

    var onViewProfileClickListener: ((String) -> Unit)? = null


    companion object {
        private const val TAG = "ImageMessageBinder"
    }

    override fun bind(holder: RecyclerView.ViewHolder, message: Message, position: Int) {
        bindInternal(holder, message, true)
    }

    fun bind(
        holder: RecyclerView.ViewHolder,
        message: Message,
        position: Int,
        showProfilePic: Boolean
    ) {
        bindInternal(holder, message, showProfilePic)
    }

    private fun bindInternal(
        holder: RecyclerView.ViewHolder,
        message: Message,
        showProfilePic: Boolean
    ) {
        if (holder !is MessagesAdapter.ImageMessageViewHolder) return

        val root = holder.itemView as ConstraintLayout
        val set = ConstraintSet()
        set.clone(root)

        /* ===============================
         * 1️⃣ ALIGN LEFT / RIGHT
         * =============================== */
        if (message.senderId == currentUserId) {
            set.clear(R.id.imageContainer, ConstraintSet.START)
            set.clear(R.id.imageContainer, ConstraintSet.TOP)

            set.connect(
                R.id.imageContainer,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END,
                8.dpToPx(holder.itemView.context)
            )

            set.connect(
                R.id.imageContainer,
                ConstraintSet.TOP,
                ConstraintSet.PARENT_ID,
                ConstraintSet.TOP,
                6.dpToPx(holder.itemView.context)
            )

            holder.ivProfile.visibility = View.GONE

            // Hide menu for sent messages
            holder.btnMessageMenu?.visibility = View.GONE

        } else {
            set.clear(R.id.imageContainer, ConstraintSet.END)
            set.clear(R.id.imageContainer, ConstraintSet.TOP)

            if (showProfilePic) {
                set.connect(
                    R.id.imageContainer,
                    ConstraintSet.START,
                    R.id.ivProfile,
                    ConstraintSet.END,
                    12.dpToPx(holder.itemView.context)
                )

                holder.ivProfile.visibility = View.VISIBLE

                if (!partnerProfilePic.isNullOrEmpty()) {
                    Glide.with(holder.itemView.context)
                        .load(partnerProfilePic)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .into(holder.ivProfile)
                } else {
                    holder.ivProfile.setImageResource(R.drawable.ic_profile_placeholder)
                }

                set.connect(
                    R.id.imageContainer,
                    ConstraintSet.TOP,
                    R.id.ivProfile,
                    ConstraintSet.TOP,
                    0
                )
            } else {
                set.connect(
                    R.id.imageContainer,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START,
                    48.dpToPx(holder.itemView.context)
                )

                holder.ivProfile.visibility = View.GONE

                set.connect(
                    R.id.imageContainer,
                    ConstraintSet.TOP,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.TOP,
                    2.dpToPx(holder.itemView.context)
                )
            }

            // Show menu for received messages
            holder.btnMessageMenu?.let { btnMenu ->
                btnMenu.visibility = View.VISIBLE
                btnMenu.setOnClickListener {
                    showImageOptionsMenu(
                        context = holder.itemView.context,
                        anchorView = btnMenu,
                        message = message,
                        onReact = onReact,
                        onViewProfile = { senderId ->
                            onViewProfileClickListener?.invoke(senderId)
                        }
                    )

                }
            }
        }

        set.applyTo(root)

        /* ===============================
         * 2️⃣ FULL RESET
         * =============================== */
        holder.image1.visibility = View.GONE
        holder.image2.visibility = View.GONE
        holder.image3.visibility = View.GONE
        holder.extraCountText.visibility = View.GONE
        holder.progressBar2.visibility = View.GONE
        holder.uploadOverlay.visibility = View.GONE
        holder.tvTimestamp.visibility = View.GONE
        holder.readStatus?.visibility = View.GONE

        /* ===============================
         * 3️⃣ UPLOADING STATE
         * =============================== */
        if (message.isUploading && message.imageUri != null) {
            holder.uploadOverlay.visibility = View.VISIBLE
            holder.progressBar2.visibility = View.VISIBLE
            holder.progressBar2.progress = message.uploadProgress

            holder.image1.visibility = View.VISIBLE

            Glide.with(holder.itemView.context)
                .load(message.imageUri)
                .placeholder(R.drawable.ic_image_placeholder)
                .into(holder.image1)

            // No timestamp or read status while uploading
            return
        }

        /* ===============================
         * 4️⃣ FINAL IMAGE STATE
         * =============================== */
        val images = message.imageUrls ?: listOfNotNull(message.imageUrl)
        if (images.isEmpty()) return

        images.getOrNull(0)?.let {
            holder.image1.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(it)
                .placeholder(R.drawable.ic_image_placeholder)
                .into(holder.image1)
        }

        images.getOrNull(1)?.let {
            holder.image2.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(it)
                .placeholder(R.drawable.ic_image_placeholder)
                .into(holder.image2)
        }

        images.getOrNull(2)?.let {
            holder.image3.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(it)
                .placeholder(R.drawable.ic_image_placeholder)
                .into(holder.image3)
        }

        if (images.size > 3) {
            holder.extraCountText.visibility = View.VISIBLE
            holder.extraCountText.text =
                holder.itemView.context.getString(
                    R.string.extra_images_count,
                    images.size - 3
                )
        }

        /* ===============================
         * 5️⃣ FULLSCREEN CLICK
         * =============================== */
        val views = listOf(holder.image1, holder.image2, holder.image3)
        views.forEachIndexed { index, view ->
            view.setOnClickListener {
                if (index < images.size) {
                    openFullscreen(holder, images, index)
                }
            }
        }

        holder.extraCountText.setOnClickListener {
            openFullscreen(holder, images, 0)
        }

        /* ===============================
         * 6️⃣ TIMESTAMP (ALIGNED BASED ON SENDER)
         * =============================== */
        holder.tvTimestamp.text = formatTimestamp(message.timestamp)
        holder.tvTimestamp.visibility = View.VISIBLE

        val timestampParams = holder.tvTimestamp.layoutParams as android.view.ViewGroup.MarginLayoutParams
        timestampParams.setMargins(0, 4, 0, 0)

        if (message.senderId == currentUserId) {
            // Sent message - align to right
            holder.tvTimestamp.textAlignment = View.TEXT_ALIGNMENT_TEXT_END
        } else {
            // Received message - align to left
            holder.tvTimestamp.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        }

        /* ===============================
          * 7️⃣ READ STATUS FOR SENT MESSAGES
          * =============================== */
        holder.readStatus?.let { readStatusView: TextView ->

            if (message.senderId == currentUserId) {

                if (message.read) {
                    readStatusView.text =
                        holder.itemView.context.getString(R.string.seen)
                    readStatusView.setTextColor(
                        holder.itemView.context.getColor(R.color.colorAccent)
                    )
                    readStatusView.visibility = View.VISIBLE
                } else {
                    readStatusView.text =
                        holder.itemView.context.getString(R.string.sent)
                    readStatusView.setTextColor(
                        holder.itemView.context.getColor(R.color.text_hint)
                    )
                    readStatusView.visibility = View.VISIBLE
                }

                val readStatusParams = readStatusView.layoutParams as android.view.ViewGroup.MarginLayoutParams
                readStatusParams.setMargins(0, 4, 0, 0)
                readStatusView.layoutParams = readStatusParams

            } else {
                // Hide read status for received image messages
                readStatusView.visibility = View.GONE
            }
        }

        holder.tvTimestamp.layoutParams = timestampParams
    }

    private fun showImageOptionsMenu(
        context: Context,
        anchorView: View,
        message: Message,
        onReact: (Message) -> Unit,
        onViewProfile: (String) -> Unit
    ) {
        val styledContext = ContextThemeWrapper(context, R.style.PopupMenuStyle)
        val popup = PopupMenu(styledContext, anchorView)

        // 👇 IMAGE MENU
        popup.menuInflater.inflate(R.menu.image_message_menu, popup.menu)

        // Disable download if no image
        val downloadItem = popup.menu.findItem(R.id.menu_download_image)
        downloadItem.isEnabled =
            !message.imageUrl.isNullOrEmpty() ||
                    !message.imageUrls?.firstOrNull().isNullOrEmpty()

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {

                R.id.menu_download_image -> {
                    saveImage(context, message)
                    true
                }

                R.id.menu_react_image -> {
                    onReact(message)
                    true
                }

                R.id.menu_view_sender_profile -> {
                    onViewProfile(message.senderId)
                    true
                }

                else -> false
            }
        }

        // 👇 FORCE SHOW ICONS (SAFE)
        try {
            val fields = popup.javaClass.declaredFields
            for (field in fields) {
                if (field.name == "mPopup") {
                    field.isAccessible = true
                    val menuPopupHelper = field.get(popup)
                    val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                    val setForceIcons = classPopupHelper.getDeclaredMethod(
                        "setForceShowIcon",
                        Boolean::class.javaPrimitiveType
                    )
                    setForceIcons.invoke(menuPopupHelper, true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Popup menu error: ${e.message}")
        }

        popup.show()
    }

    private fun saveImage(context: Context, message: Message) {
        val imageUrl = message.imageUrl ?: message.imageUrls?.firstOrNull()

        if (imageUrl.isNullOrEmpty()) {
            Toast.makeText(context, "No image to save", Toast.LENGTH_SHORT).show()
            return
        }

        // Gamitin ang Glide para i-download ang image bilang bitmap
        Glide.with(context)
            .asBitmap()
            .load(imageUrl)
            .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                override fun onResourceReady(
                    resource: android.graphics.Bitmap,
                    transition: com.bumptech.glide.request.transition.Transition<in android.graphics.Bitmap>?
                ) {
                    // I-save ang bitmap sa gallery
                    saveBitmapToGallery(context, resource)
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    Toast.makeText(context, "Failed to download image", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun saveBitmapToGallery(context: Context, bitmap: android.graphics.Bitmap) {
        val filename = "BarterHub_${System.currentTimeMillis()}.png"

        val fos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // For Android 10+ (Scoped Storage)
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/BarterHub")
            }
            val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            resolver.openOutputStream(imageUri!!)
        } else {
            // For Android 9 or below
            val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).toString() + "/BarterHub"
            val file = java.io.File(imagesDir)
            if (!file.exists()) file.mkdirs()
            java.io.FileOutputStream(java.io.File(file, filename))
        }

        fos.use {
            it?.let { stream -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream) }
            Toast.makeText(context, "Image saved to gallery", Toast.LENGTH_SHORT).show()
        }
    }


    private fun openFullscreen(
        holder: MessagesAdapter.ImageMessageViewHolder,
        images: List<String>,
        start: Int
    ) {
        val intent = Intent(holder.itemView.context, FullscreenImageActivity::class.java)
        intent.putStringArrayListExtra("images", ArrayList(images))
        intent.putExtra("position", start)
        holder.itemView.context.startActivity(intent)
    }

    private fun formatTimestamp(timestamp: Long): String {
        return SimpleDateFormat("hh:mm a", Locale.getDefault())
            .format(Date(timestamp))
    }

    private fun Int.dpToPx(context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}