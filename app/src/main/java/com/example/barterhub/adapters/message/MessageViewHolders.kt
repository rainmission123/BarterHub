package com.example.barterhub.adapters.message

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import de.hdodenhof.circleimageview.CircleImageView

open class SentMessageViewHolder(
    itemView: View
) : RecyclerView.ViewHolder(itemView) {

    val messageText: TextView =
        itemView.findViewById(R.id.tvMessageSent)

    val readStatus: TextView? =
        itemView.findViewById(R.id.tvReadStatus)

    val messageContainer: View =
        itemView.findViewById(R.id.sentMessageContainer)

    val timestampText: TextView =
        itemView.findViewById(R.id.tvTimestampSent)

    val reactionsContainer: LinearLayout? =
        itemView.findViewById(R.id.reactionsContainer)

    val tvReactionSummary: TextView? =
        itemView.findViewById(R.id.tvReactionSummary)

    var boundMessageId: String? = null
}

open class ReceivedMessageViewHolder(
    itemView: View
) : RecyclerView.ViewHolder(itemView) {

    val messageText: TextView =
        itemView.findViewById(R.id.tvMessageReceived)

    val timestampText: TextView =
        itemView.findViewById(R.id.tvTimestampReceived)

    val senderText: TextView =
        itemView.findViewById(R.id.tvSenderReceived)

    val profileImage: CircleImageView =
        itemView.findViewById(R.id.ivProfileReceived)

    val messageContainer: View =
        itemView.findViewById(R.id.receivedMessageContainer)

    val reactionsContainer: LinearLayout? =
        itemView.findViewById(R.id.reactionsContainer)

    val tvReactionSummary: TextView? =
        itemView.findViewById(R.id.tvReactionSummary)

    var boundMessageId: String? = null
}

open class ImageMessageViewHolder(
    itemView: View
) : RecyclerView.ViewHolder(itemView) {

    val ivProfile: ImageView =
        itemView.findViewById(R.id.ivProfile)

    val image1: ImageView =
        itemView.findViewById(R.id.image1)

    val image2: ImageView =
        itemView.findViewById(R.id.image2)

    val image3: ImageView =
        itemView.findViewById(R.id.image3)

    val extraCountText: TextView =
        itemView.findViewById(R.id.extraCountText)

    val progressBar2: ProgressBar =
        itemView.findViewById(R.id.progressBar2)

    val uploadOverlay: View =
        itemView.findViewById(R.id.uploadOverlay)

    val tvTimestamp: TextView =
        itemView.findViewById(R.id.tvTimestamp)

    val readStatus: TextView? =
        itemView.findViewById(R.id.tvReadStatus)

    val btnMessageMenu: ImageView? =
        itemView.findViewById(R.id.btnMessageMenu)

    val singleReactionContainer: LinearLayout? =
        itemView.findViewById(R.id.singleReactionContainer)

    val tvReactionEmoji: TextView? =
        itemView.findViewById(R.id.tvReactionEmoji)

    val tvReactionCount: TextView? =
        itemView.findViewById(R.id.tvReactionCount)

    var boundMessageId: String? = null
}

open class VideoMessageViewHolder(
    itemView: View
) : RecyclerView.ViewHolder(itemView) {

    val ivProfile: CircleImageView =
        itemView.findViewById(R.id.ivProfile)

    val videoContainer: CardView =
        itemView.findViewById(R.id.videoContainer)

    val videoThumbnail: ImageView =
        itemView.findViewById(R.id.videoThumbnail)

    val tvDuration: TextView =
        itemView.findViewById(R.id.tvDuration)

    val tvTimestamp: TextView =
        itemView.findViewById(R.id.tvTimestamp)

    val videoUploadProgress: ProgressBar? =
        itemView.findViewById(R.id.videoUploadProgress)

    val tvReadStatus: TextView? =
        itemView.findViewById(R.id.tvReadStatus)

    val btnVideoMenu: ImageView =
        itemView.findViewById(R.id.btnVideoMenu)

    val singleReactionContainer: LinearLayout? =
        itemView.findViewById(R.id.singleReactionContainer)

    val tvReactionEmoji: TextView? =
        itemView.findViewById(R.id.tvReactionEmoji)

    val tvReactionCount: TextView? =
        itemView.findViewById(R.id.tvReactionCount)

    var boundMessageId: String? = null
}