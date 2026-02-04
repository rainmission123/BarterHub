package com.example.barterhub.binders

import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.adapters.MessagesAdapter
import com.example.barterhub.data.models.Message
import android.app.DownloadManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.navigation.Navigation.findNavController
import com.example.barterhub.ui.OwnerProfileFragment

class VideoMessageBinder(
    private val currentUserId: String,
    private var partnerProfilePic: String?,
    private val onReact: (Message) -> Unit
) : MessageBinder {

    companion object {
        private const val TAG = "VideoMessageBinder"
    }

    override fun bind(holder: RecyclerView.ViewHolder, message: Message, position: Int) {
        bind(holder, message, true)
    }

    fun bind(
        holder: RecyclerView.ViewHolder,
        message: Message,
        showProfilePic: Boolean
    ) {
        if (holder !is MessagesAdapter.VideoMessageViewHolder) return

        val context = holder.itemView.context
        val rootLayout = holder.itemView as ConstraintLayout
        val set = ConstraintSet()
        set.clone(rootLayout)

        // ===============================
        // 1️⃣ UPLOAD PROGRESS BAR
        // ===============================
        holder.videoUploadProgress?.let { progressBar ->
            if (message.isUploading == true) {
                progressBar.visibility = View.VISIBLE
                progressBar.progress = message.uploadProgress ?: 0
                Log.d(TAG, "Video uploading: ${message.uploadProgress}%")

                // Make video thumbnail dimmed during upload
                holder.videoThumbnail.alpha = 0.7f
                holder.videoThumbnail.setOnClickListener(null)

                // Hide read status while uploading
                holder.tvReadStatus?.visibility = View.GONE
            } else {
                progressBar.visibility = View.GONE
                holder.videoThumbnail.alpha = 1.0f
            }
        }

        // ===============================
        // 2️⃣ Align bubble
        // ===============================
        if (message.senderId == currentUserId) {
            set.clear(R.id.videoCardContainer, ConstraintSet.START)
            set.connect(
                R.id.videoCardContainer,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END,
                8.dpToPx(context)
            )
            holder.ivProfile.visibility = View.GONE
            holder.videoCardContainer.setBackgroundResource(R.drawable.bg_video_sent)
        } else {
            set.clear(R.id.videoCardContainer, ConstraintSet.END)

            if (showProfilePic && partnerProfilePic != null) {
                set.connect(
                    R.id.videoCardContainer,
                    ConstraintSet.START,
                    R.id.ivProfile,
                    ConstraintSet.END,
                    12.dpToPx(context)
                )
                holder.ivProfile.visibility = View.VISIBLE
                Glide.with(context).clear(holder.ivProfile)
                Glide.with(context)
                    .load(partnerProfilePic)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .circleCrop()
                    .into(holder.ivProfile)
            } else {
                set.connect(
                    R.id.videoCardContainer,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START,
                    48.dpToPx(context)
                )
                holder.ivProfile.visibility = View.GONE
            }

            holder.videoCardContainer.setBackgroundResource(R.drawable.bg_video_received)
        }

        set.applyTo(rootLayout)

                // ===============================
                // 3️⃣ THREE DOT MENU (OPTIONS)
                // ===============================
        holder.btnVideoMenu?.let { btnMenu ->
            if (message.senderId != currentUserId) {
                // IKAW ang RECEIVER ng video
                btnMenu.visibility = View.VISIBLE
                btnMenu.setOnClickListener {
                    showVideoOptionsMenu(context, btnMenu, message, onReact)
                }
            } else {
                // ❌ IKAW ang SENDER - hide the menu
                btnMenu.visibility = View.GONE
            }
        }

        holder.tvTimestamp.text =
            android.text.format.DateFormat.format("hh:mm a", message.timestamp)

        // ===============================
        // 4️⃣ READ STATUS FOR SENT MESSAGES
        // ===============================
        holder.tvReadStatus?.let { readStatusView: TextView ->
            if (message.senderId == currentUserId) {
                // Use message.read (not message.isRead)
                if (message.read) {
                    readStatusView.text = context.getString(R.string.seen)
                    readStatusView.setTextColor(context.getColor(R.color.colorAccent))
                    readStatusView.visibility = View.VISIBLE
                } else {
                    readStatusView.text = context.getString(R.string.sent)
                    readStatusView.setTextColor(context.getColor(R.color.text_hint))
                    readStatusView.visibility = View.VISIBLE
                }
            } else {
                // Hide read status for received video messages
                readStatusView.visibility = View.GONE
            }
        }

        // ===============================
        // 5️⃣ Duration
        // ===============================
        message.videoDuration?.let {
            holder.tvDuration.text = formatDuration(it)
            holder.tvDuration.visibility = View.VISIBLE
        } ?: run {
            holder.tvDuration.visibility = View.GONE
        }

        // ===============================
        // 6️⃣ Thumbnail
        // ===============================
        if (!message.videoUrl.isNullOrEmpty()) {
            // Video is already uploaded
            Glide.with(context)
                .asBitmap()
                .load(message.videoUrl)
                .frame(1_000_000) // 1 second
                .placeholder(R.drawable.ic_video_placeholder)
                .centerCrop()
                .into(holder.videoThumbnail)

            // Enable video playback
            holder.videoThumbnail.setOnClickListener {
                playVideoDirectly(context, message.videoUrl)
            }
        } else {
            // Still uploading or no video URL
            holder.videoThumbnail.setImageResource(R.drawable.ic_video_placeholder)

            // Disable click while uploading
            if (message.isUploading != true) {
                holder.videoThumbnail.setOnClickListener(null)
            }
        }

    }

    private fun showVideoOptionsMenu(
        context: Context,
        anchorView: View,
        message: Message,
        onReact: (Message) -> Unit
    ) {
        // Gumamit ng ContextThemeWrapper para ma-apply ang custom style
        val styledContext = android.view.ContextThemeWrapper(context, R.style.PopupMenuStyle)
        val popup = androidx.appcompat.widget.PopupMenu(styledContext, anchorView)

        // Inflate yung custom menu
        popup.menuInflater.inflate(R.menu.video_message_menu, popup.menu)

        // Kung may video URL, enable download option
        val downloadItem = popup.menu.findItem(R.id.menu_download_video)
        downloadItem.isEnabled = !message.videoUrl.isNullOrEmpty()

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_download_video -> {
                    downloadVideo(context, message.videoUrl ?: "")
                    true
                }

                R.id.menu_react_video -> {
                    onReact(message) // Reuse existing reaction dialog
                    true
                }

                R.id.menu_view_sender_profile -> {
                    openUserProfile(anchorView, message.senderId)
                    true
                }


                else -> false
            }
        }

        try {
            // Para ma-show yung icons sa popup menu
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
            e.printStackTrace()
        }

        popup.show()
    }

    private fun downloadVideo(context: Context, videoUrl: String) {
        if (videoUrl.isEmpty()) {
            Toast.makeText(context, "Video URL is empty", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = Uri.parse(videoUrl)
            val request = DownloadManager.Request(uri).apply {
                setTitle("Downloading video")
                setDescription("Downloading video from BarterHub")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "BarterHub_${System.currentTimeMillis()}.mp4"
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openUserProfile(view: View, userId: String) {
        if (userId.isEmpty()) {
            Toast.makeText(view.context, "User not found", Toast.LENGTH_SHORT).show()
            return
        }

        val bundle = Bundle().apply {
            putString("ownerId", userId)
        }

        // Use view to find NavController
        androidx.navigation.Navigation.findNavController(view)
            .navigate(R.id.ownerProfileFragment, bundle)
    }


    private fun playVideoDirectly(context: Context, videoUrl: String?) {
        if (videoUrl.isNullOrEmpty()) {
            Log.d(TAG, "Video URL is empty, cannot play")
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(android.net.Uri.parse(videoUrl), "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Log.e(TAG, "No app available to play video")
                // You can show a toast or snackbar here
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing video: ${e.message}")
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun Int.dpToPx(context: Context): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            context.resources.displayMetrics
        ).toInt()

}