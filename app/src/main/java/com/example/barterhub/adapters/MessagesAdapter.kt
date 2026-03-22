package com.example.barterhub.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.*
import androidx.viewpager2.widget.ViewPager2
import com.example.barterhub.R
import com.example.barterhub.binders.ImageMessageBinder
import com.example.barterhub.binders.SystemMessageBinder
import com.example.barterhub.binders.TextMessageBinder
import com.example.barterhub.binders.VideoMessageBinder
import com.example.barterhub.data.models.Message
import com.example.barterhub.data.models.TradeRequest
import com.example.barterhub.viewholders.SystemMessageViewHolder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.database.*
import de.hdodenhof.circleimageview.CircleImageView

@SuppressLint("NotifyDataSetChanged")
class MessagesAdapter(
    private val messages: MutableList<Message>,
    private val currentUserId: String,
    private val chatId: String,
    private var currentUserProfilePic: String? = null,
    private var partnerProfilePic: String? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val VIEW_TYPE_SYSTEM = 3
        private const val VIEW_TYPE_IMAGE = 4
        private const val VIEW_TYPE_VIDEO = 5
        private const val TAG = "MessagesAdapter"
        private lateinit var holderSafeContext: Context
    }

    private var onProfilePictureClickListener: ((String) -> Unit)? = null
    private var onViewProfileClickListener: ((String) -> Unit)? = null
    private var onTradeCompletedListener: ((TradeRequest) -> Unit)? = null
    private var onMessageDeletedListener: ((Message, Int) -> Unit)? = null
    private val ratingStatusMap = mutableMapOf<String, RatingStatus>()
    private var reviewsListener: ValueEventListener? = null
    private val reactionListeners = mutableMapOf<String, ValueEventListener>()

    // Firebase
    private val database = FirebaseDatabase.getInstance().reference
    private var textMessageBinder: TextMessageBinder
    private var imageMessageBinder: ImageMessageBinder
    private var videoMessageBinder: VideoMessageBinder
    private val systemMessageBinder: SystemMessageBinder

    init {
        textMessageBinder = TextMessageBinder(
            currentUserId = currentUserId,
            partnerProfilePic = partnerProfilePic,
            chatId = chatId,
            onProfilePictureClickListener = { pic -> onProfilePictureClickListener?.invoke(pic) },
            onMessageDeleted = { message, position ->
                // Call the listener if it's set
                onMessageDeletedListener?.invoke(message, position)
                Log.d(TAG, "Message deleted callback from binder: ${message.messageId}")
            }
        )

        imageMessageBinder = ImageMessageBinder(
            currentUserId = currentUserId,
            partnerProfilePic = partnerProfilePic,
            onReact = { message ->
                showReactionDialog(
                    context = holderSafeContext,
                    message = message
                )
            }
        )

        videoMessageBinder = VideoMessageBinder(
            currentUserId = currentUserId,
            partnerProfilePic = partnerProfilePic,
            onReact = { message ->
                showReactionDialog(
                    context = holderSafeContext,
                    message = message
                )
            }
        )

        systemMessageBinder = SystemMessageBinder(
            currentUserId = currentUserId,
            chatId = chatId,
            onTradeCompletedListener = { tradeRequest -> onTradeCompletedListener?.invoke(tradeRequest) },
            onMessageUpdated = { notifyDataSetChanged() }
        )

        setupRealTimeRatingMonitor()
    }

    fun setOnProfilePictureClickListener(listener: (String) -> Unit) {
        onProfilePictureClickListener = listener
    }

    fun setOnViewProfileClickListener(listener: (String) -> Unit) {
        onViewProfileClickListener = listener
    }

    fun setProfilePictures(currentUserPic: String?, partnerPic: String?) {
        currentUserProfilePic = currentUserPic
        partnerProfilePic = partnerPic
        updateBindersWithProfilePics()
    }

    private fun updateBindersWithProfilePics() {
        textMessageBinder = TextMessageBinder(
            currentUserId = currentUserId,
            partnerProfilePic = partnerProfilePic,
            chatId = chatId,
            onProfilePictureClickListener = { pic ->
                onProfilePictureClickListener?.invoke(pic)
            },
            onMessageDeleted = { message, position ->
                // Use the renamed listener
                onMessageDeletedListener?.invoke(message, position)
                Log.d(TAG, "Message deleted from binder update: ${message.messageId}")
            }
        )

        imageMessageBinder = ImageMessageBinder(
            currentUserId = currentUserId,
            partnerProfilePic = partnerProfilePic,
            onReact = { message ->
                showReactionDialog(
                    context = holderSafeContext,
                    message = message
                )
            }
        )

        imageMessageBinder.onViewProfileClickListener = { senderId ->
            onViewProfileClickListener?.invoke(senderId)
        }

        videoMessageBinder = VideoMessageBinder(
            currentUserId = currentUserId,
            partnerProfilePic = partnerProfilePic,
            onReact = { message ->
                showReactionDialog(
                    context = holderSafeContext,
                    message = message
                )
            }
        )

        notifyDataSetChanged()
    }


    inner class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.tvMessageSent)
        val readStatus: TextView? = itemView.findViewById(R.id.tvReadStatus)
        val messageContainer: View = itemView.findViewById(R.id.sentMessageContainer)
        val timestampText: TextView = itemView.findViewById(R.id.tvTimestampSent)
        val reactionsContainer: LinearLayout? = itemView.findViewById(R.id.reactionsContainer)
        val tvReactionSummary: TextView? = itemView.findViewById(R.id.tvReactionSummary)
    }

    inner class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.tvMessageReceived)
        val timestampText: TextView = itemView.findViewById(R.id.tvTimestampReceived)
        val senderText: TextView = itemView.findViewById(R.id.tvSenderReceived)
        val profileImage: CircleImageView = itemView.findViewById(R.id.ivProfileReceived)
        val messageContainer: View = itemView.findViewById(R.id.receivedMessageContainer)
        val moreOptions: ImageView = itemView.findViewById(R.id.ivMoreReceived)
        val reactionsContainer: LinearLayout? = itemView.findViewById(R.id.reactionsContainer)
        val tvReactionSummary: TextView? = itemView.findViewById(R.id.tvReactionSummary)
    }

    inner class ImageMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivProfile: ImageView = itemView.findViewById(R.id.ivProfile)
        val image1: ImageView = itemView.findViewById(R.id.image1)
        val image2: ImageView = itemView.findViewById(R.id.image2)
        val image3: ImageView = itemView.findViewById(R.id.image3)
        val extraCountText: TextView = itemView.findViewById(R.id.extraCountText)
        val progressBar2: ProgressBar = itemView.findViewById(R.id.progressBar2)
        val uploadOverlay: View = itemView.findViewById(R.id.uploadOverlay)
        val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        val readStatus: TextView? = itemView.findViewById(R.id.tvReadStatus)
        val btnMessageMenu: ImageView? = itemView.findViewById(R.id.btnMessageMenu)
        val singleReactionContainer: LinearLayout? = itemView.findViewById(R.id.singleReactionContainer)
        val tvReactionEmoji: TextView? = itemView.findViewById(R.id.tvReactionEmoji)
        val tvReactionCount: TextView? = itemView.findViewById(R.id.tvReactionCount)
    }

    inner class VideoMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivProfile: CircleImageView = itemView.findViewById(R.id.ivProfile)
        val videoContainer: CardView = itemView.findViewById(R.id.videoContainer)
        val videoThumbnail: ImageView = itemView.findViewById(R.id.videoThumbnail)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        val videoUploadProgress: ProgressBar? = itemView.findViewById(R.id.videoUploadProgress)
        val tvReadStatus: TextView? = itemView.findViewById(R.id.tvReadStatus)
        val btnVideoMenu: ImageView = itemView.findViewById(R.id.btnVideoMenu)
        val singleReactionContainer: LinearLayout? = itemView.findViewById(R.id.singleReactionContainer)
        val tvReactionEmoji: TextView? = itemView.findViewById(R.id.tvReactionEmoji)
        val tvReactionCount: TextView? = itemView.findViewById(R.id.tvReactionCount)
    }

    data class RatingStatus(
        var currentUserRated: Boolean = false,
        var partnerRated: Boolean = false,
        var totalRatings: Int = 0
    )

    private fun saveReactionToFirebase(messageId: String, emoji: String) {
        Log.d(TAG, "saveReactionToFirebase: messageId=$messageId, emoji=$emoji, currentUserId=$currentUserId")

        val reactionsRef = database
            .child("chats")
            .child(chatId)
            .child("messages")
            .child(messageId)
            .child("reactions")

        reactionsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Firebase reactions snapshot: ${snapshot.value}")

                var userAlreadyReactedWithThisEmoji = false

                // Check if user already reacted with this emoji
                if (snapshot.child(emoji).child(currentUserId).exists()) {
                    userAlreadyReactedWithThisEmoji = true
                }

                // Remove ALL previous reactions of this user
                for (emojiSnap in snapshot.children) {
                    if (emojiSnap.child(currentUserId).exists()) {
                        emojiSnap.child(currentUserId).ref.removeValue()
                        Log.d(TAG, "Removed previous reaction: ${emojiSnap.key}")
                    }
                }

                // Add new reaction if not already exists
                if (!userAlreadyReactedWithThisEmoji) {
                    reactionsRef.child(emoji).child(currentUserId).setValue(true)
                        .addOnSuccessListener {
                            Log.d(TAG, "Reaction added successfully: $emoji")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to add reaction: ${e.message}")
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "saveReactionToFirebase cancelled: ${error.message}")
            }
        })
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when {
            message.isSystemMessage || message.senderId == "system"
                    || message.messageType == "system_trade_accepted"
                    || message.messageType == "system_trade_completed" -> VIEW_TYPE_SYSTEM
            message.messageType == "video" -> VIEW_TYPE_VIDEO
            message.messageType == "image" -> VIEW_TYPE_IMAGE
            message.senderId == currentUserId -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        holderSafeContext = parent.context

        return when (viewType) {
            VIEW_TYPE_SENT -> SentMessageViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_sent, parent, false)
            )
            VIEW_TYPE_RECEIVED -> ReceivedMessageViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_received, parent, false)
            )
            VIEW_TYPE_SYSTEM -> SystemMessageViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.system_message_item, parent, false)
            )
            VIEW_TYPE_IMAGE -> ImageMessageViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_image, parent, false)
            )
            VIEW_TYPE_VIDEO -> VideoMessageViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_video, parent, false)
            )
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    private fun setupReactionsDisplay(holder: RecyclerView.ViewHolder, message: Message) {
        when (holder) {
            is ReceivedMessageViewHolder -> {
                setupTextMessageReactions(holder, message)
            }
            is SentMessageViewHolder -> {
                setupTextMessageReactions(holder, message)
            }
            is VideoMessageViewHolder -> {
                setupVideoMessageReactions(holder, message)
                setupReactionListenerForMessage(message)
            }
            is ImageMessageViewHolder -> {  // 👈 DAGDAG ITO
                setupImageMessageReactions(holder, message)
                setupReactionListenerForMessage(message)
            }
        }
    }

    private fun setupImageMessageReactions(holder: ImageMessageViewHolder, message: Message) {
        Log.d(TAG, "setupImageMessageReactions for image message: ${message.messageId}")
        Log.d(TAG, "Image reactions: ${message.reactions}")

        holder.singleReactionContainer?.let { container ->
            holder.tvReactionEmoji?.let { emojiView ->
                holder.tvReactionCount?.let { countView ->

                    // Check if message has reactions
                    if (message.reactions.isNotEmpty()) {
                        Log.d(TAG, "Image message has ${message.reactions.size} reaction(s)")

                        // Get the most popular reaction
                        val topReaction = message.reactions.entries
                            .maxByOrNull { it.value.size }

                        Log.d(TAG, "Top reaction for image: $topReaction")

                        topReaction?.let { (emoji, usersMap) ->
                            val userIds = usersMap.keys.toList()
                            Log.d(TAG, "Setting image reaction: $emoji, count: ${userIds.size}")

                            container.visibility = View.VISIBLE
                            emojiView.text = emoji
                            countView.text = userIds.size.toString()

                            // Highlight if current user reacted
                            if (usersMap.containsKey(currentUserId)) {
                                container.setBackgroundResource(R.drawable.bg_reaction_selected)
                                countView.setTextColor(ContextCompat.getColor(container.context, R.color.colorPrimary))
                                Log.d(TAG, "Current user reacted to this image")
                            } else {
                                container.setBackgroundResource(R.drawable.bg_reaction_default)
                                countView.setTextColor(ContextCompat.getColor(container.context, R.color.text_secondary))
                            }
                        }
                    } else {
                        Log.d(TAG, "Image message has no reactions")
                        container.visibility = View.GONE
                    }
                }
            }
        }
    }

    fun setOnMessageDeletedListener(listener: (Message, Int) -> Unit) {
        this.onMessageDeletedListener = listener
        Log.d(TAG, "Message deleted listener set")

        // Update textMessageBinder with new listener
        textMessageBinder = TextMessageBinder(
            currentUserId = currentUserId,
            partnerProfilePic = partnerProfilePic,
            chatId = chatId,
            onProfilePictureClickListener = { pic -> onProfilePictureClickListener?.invoke(pic) },
            onMessageDeleted = { message, position ->
                listener.invoke(message, position)
                Log.d(TAG, "Message deleted via new listener: ${message.messageId}")
            }
        )
        notifyDataSetChanged()
    }

    private fun setupTextMessageReactions(holder: RecyclerView.ViewHolder, message: Message) {
        val reactionsContainer: LinearLayout?
        val reactionSummary: TextView?

        when (holder) {
            is ReceivedMessageViewHolder -> {
                reactionsContainer = holder.reactionsContainer
                reactionSummary = holder.tvReactionSummary
            }
            is SentMessageViewHolder -> {
                reactionsContainer = holder.reactionsContainer
                reactionSummary = holder.tvReactionSummary
            }
            else -> return
        }

        // Clear existing reactions
        reactionsContainer?.removeAllViews()

        Log.d(TAG, "setupTextMessageReactions for message: ${message.messageId}")
        Log.d(TAG, "Reactions count: ${message.reactions.size}")
        Log.d(TAG, "Reactions data: ${message.reactions}")

        // Check if message has reactions
        if (message.reactions.isNotEmpty()) {
            reactionsContainer?.visibility = View.VISIBLE

            // Display top 3 reactions
            val topReactions = message.reactions.entries
                .sortedByDescending { it.value.size }
                .take(3)

            Log.d(TAG, "Top reactions: $topReactions")

            topReactions.forEach { (emoji, usersMap) ->
                val userIds = usersMap.keys.toList()
                Log.d(TAG, "Processing emoji: $emoji, users: $userIds")

                // Inflate reaction item
                val reactionView = LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.reaction_item, reactionsContainer, false)

                val tvEmoji = reactionView.findViewById<TextView>(R.id.tvReactionEmoji)
                val tvCount = reactionView.findViewById<TextView>(R.id.tvReactionCount)

                tvEmoji.text = emoji
                tvCount.text = userIds.size.toString()

                // Highlight if current user reacted
                if (usersMap.containsKey(currentUserId)) {
                    reactionView.setBackgroundResource(R.drawable.bg_reaction_selected)
                    tvCount.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.colorPrimary))
                } else {
                    reactionView.setBackgroundResource(R.drawable.bg_reaction_default)
                    tvCount.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_secondary))
                }

                reactionView.setOnClickListener {
                    Log.d(TAG, "Reaction clicked: $emoji")
                    if (usersMap.containsKey(currentUserId)) {
                        // Remove reaction
                        removeReaction(message.messageId, emoji)
                    } else {
                        // Add reaction
                        saveReactionToFirebase(message.messageId, emoji)
                    }
                }

                reactionsContainer?.addView(reactionView)
            }

            val totalReactions = message.reactions.values.sumOf { it.size }
            if (totalReactions > 3) {
                reactionSummary?.text = "+${totalReactions - 3}"
                reactionSummary?.visibility = View.VISIBLE
            } else {
                reactionSummary?.visibility = View.GONE
            }
        } else {
            reactionsContainer?.visibility = View.GONE
            reactionSummary?.visibility = View.GONE
        }
    }
    // FOR VIDEO MESSAGES (single reaction)
    private fun setupVideoMessageReactions(holder: VideoMessageViewHolder, message: Message) {
        Log.d(TAG, "setupVideoMessageReactions for video message: ${message.messageId}")
        Log.d(TAG, "Video reactions: ${message.reactions}")

        holder.singleReactionContainer?.let { container ->
            holder.tvReactionEmoji?.let { emojiView ->
                holder.tvReactionCount?.let { countView ->

                    // Check if message has reactions
                    if (message.reactions.isNotEmpty()) {
                        Log.d(TAG, "Video message has ${message.reactions.size} reaction(s)")

                        // Get the most popular reaction
                        val topReaction = message.reactions.entries
                            .maxByOrNull { it.value.size }

                        Log.d(TAG, "Top reaction for video: $topReaction")

                        topReaction?.let { (emoji, usersMap) ->
                            val userIds = usersMap.keys.toList()
                            Log.d(TAG, "Setting video reaction: $emoji, count: ${userIds.size}")

                            container.visibility = View.VISIBLE
                            emojiView.text = emoji
                            countView.text = userIds.size.toString()

                            // Highlight if current user reacted
                            if (usersMap.containsKey(currentUserId)) {
                                container.setBackgroundResource(R.drawable.bg_reaction_selected)
                                countView.setTextColor(ContextCompat.getColor(container.context, R.color.colorPrimary))
                                Log.d(TAG, "Current user reacted to this video")
                            } else {
                                container.setBackgroundResource(R.drawable.bg_reaction_default)
                                countView.setTextColor(ContextCompat.getColor(container.context, R.color.text_secondary))
                            }
                        }
                    } else {
                        Log.d(TAG, "Video message has no reactions")
                        container.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun removeReaction(messageId: String, emoji: String) {
        Log.d(TAG, "removeReaction: messageId=$messageId, emoji=$emoji")

        database.child("chats")
            .child(chatId)
            .child("messages")
            .child(messageId)
            .child("reactions")
            .child(emoji)
            .child(currentUserId)
            .removeValue()
            .addOnSuccessListener {
                Log.d(TAG, "Reaction removed successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to remove reaction: ${e.message}")
            }
    }

    private fun setupReactionListenerForMessage(message: Message) {
        val messageId = message.messageId
        if (messageId.isEmpty() || reactionListeners.containsKey(messageId)) return

        Log.d(TAG, "Setting up reaction listener for message: $messageId")

        val reactionRef = database.child("chats")
            .child(chatId)
            .child("messages")
            .child(messageId)
            .child("reactions")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Reaction data changed for message: $messageId")
                Log.d(TAG, "Snapshot value: ${snapshot.value}")

                val newReactions = mutableMapOf<String, Map<String, Boolean>>()

                for (emojiSnap in snapshot.children) {
                    val emoji = emojiSnap.key ?: continue
                    val usersMap = mutableMapOf<String, Boolean>()

                    for (userSnap in emojiSnap.children) {
                        val userId = userSnap.key
                        val hasReacted = userSnap.getValue(Boolean::class.java) ?: false
                        if (userId != null) {
                            usersMap[userId] = hasReacted
                        }
                    }

                    if (usersMap.isNotEmpty()) {
                        newReactions[emoji] = usersMap
                    }
                }

                Log.d(TAG, "Parsed reactions: $newReactions")

                val index = messages.indexOfFirst { it.messageId == messageId }
                if (index != -1) {
                    val updatedMessage = messages[index].copy(reactions = newReactions)
                    messages[index] = updatedMessage
                    notifyItemChanged(index, "reactions")
                }

            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Reaction listener cancelled: ${error.message}")
            }
        }

        reactionRef.addValueEventListener(listener)
        reactionListeners[messageId] = listener
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val showProfilePic = shouldShowProfilePic(position)

        Log.d(TAG, "onBindViewHolder position: $position, messageId: ${message.messageId}, type: ${message.messageType}")

        when (holder) {
            is ReceivedMessageViewHolder -> {
                textMessageBinder.bind(holder, message, position, showProfilePic)
                setupReactionsDisplay(holder, message)

                holder.moreOptions.setOnClickListener {
                    showReactionDialog(holder.itemView.context, message)
                }
            }

            is SentMessageViewHolder -> {
                textMessageBinder.bind(holder, message, position, showProfilePic)
                setupReactionsDisplay(holder, message)

                holder.messageContainer.setOnLongClickListener {
                    showReactionDialog(holder.itemView.context, message)
                    true
                }

                holder.reactionsContainer?.setOnClickListener {
                    showReactionDialog(holder.itemView.context, message)
                }
            }

            is ImageMessageViewHolder -> {
                imageMessageBinder.bind(holder, message, position, showProfilePic)
                setupReactionsDisplay(holder, message)

                holder.singleReactionContainer?.setOnClickListener {
                    val topReaction = message.reactions.entries.maxByOrNull { it.value.size }
                    if (topReaction != null && topReaction.value.containsKey(currentUserId)) {
                        removeReaction(message.messageId, topReaction.key)
                    } else {
                        showReactionDialog(holder.itemView.context, message)
                    }
                }

                holder.itemView.findViewById<androidx.cardview.widget.CardView>(R.id.imageContainer)
                    ?.setOnLongClickListener {
                        showReactionDialog(holder.itemView.context, message)
                        true
                    }
            }

            is VideoMessageViewHolder -> {
                videoMessageBinder.bind(holder, message, showProfilePic)
                setupReactionsDisplay(holder, message)

                holder.singleReactionContainer?.setOnClickListener {
                    val topReaction = message.reactions.entries.maxByOrNull { it.value.size }
                    if (topReaction != null && topReaction.value.containsKey(currentUserId)) {
                        removeReaction(message.messageId, topReaction.key)
                    } else {
                        showReactionDialog(holder.itemView.context, message)
                    }
                }

                // 👇 FIXED: Use videoContainer instead of videoCardContainer
                holder.itemView.findViewById<androidx.cardview.widget.CardView>(R.id.videoContainer)
                    ?.setOnLongClickListener {
                        showReactionDialog(holder.itemView.context, message)
                        true
                    }
            }

            is SystemMessageViewHolder -> {
                systemMessageBinder.bind(holder, message, position)
            }
        }
    }
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)

        // Clean up listeners for video AND image messages
        if (holder is VideoMessageViewHolder || holder is ImageMessageViewHolder) {
            val position = holder.adapterPosition
            if (position in 0 until messages.size) {
                val message = messages[position]
                val listener = reactionListeners[message.messageId]
                listener?.let {
                    database.child("chats")
                        .child(chatId)
                        .child("messages")
                        .child(message.messageId)
                        .child("reactions")
                        .removeEventListener(it)
                    reactionListeners.remove(message.messageId)
                    Log.d(TAG, "Removed listener for message: ${message.messageId}")
                }
            }
        }
    }

    private fun shouldShowProfilePic(position: Int): Boolean {
        val currentMessage = messages[position]
        return currentMessage.senderId != currentUserId
    }

    private fun setupRealTimeRatingMonitor() {
        val db = FirebaseDatabase.getInstance().reference
        reviewsListener = db.child("reviews").addValueEventListener(object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(snapshot: DataSnapshot) {
                ratingStatusMap.clear()
                val ratingsByTrade = mutableMapOf<String, MutableList<String>>()
                for (reviewSnapshot in snapshot.children) {
                    val tradeId = reviewSnapshot.child("tradeId").getValue(String::class.java)
                    val reviewerId = reviewSnapshot.child("reviewerId").getValue(String::class.java)
                    if (tradeId != null && reviewerId != null) {
                        ratingsByTrade.getOrPut(tradeId) { mutableListOf() }.add(reviewerId)
                    }
                }
                for ((tradeId, reviewerIds) in ratingsByTrade) {
                    val status = RatingStatus(
                        currentUserRated = reviewerIds.contains(currentUserId),
                        partnerRated = reviewerIds.any { it != currentUserId },
                        totalRatings = reviewerIds.size
                    )
                    ratingStatusMap[tradeId] = status
                }
                notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error monitoring ratings: ${error.message}")
            }
        })
    }

    private fun showReactionDialog(context: Context, message: Message) {
        val messageId = message.messageId

        if (messageId.isBlank()) {
            Log.e(TAG, "Cannot show reaction dialog: messageId is blank")
            Toast.makeText(context, "Cannot react to this message", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.emoji_picker_dialog, null)

        val tabLayout = dialogView.findViewById<TabLayout>(R.id.tabLayout)
        val rvEmojis = dialogView.findViewById<RecyclerView>(R.id.rvEmojis)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)

        if (tabLayout == null || rvEmojis == null || btnClose == null) {
            Log.e(TAG, "Dialog layout missing required views")
            return
        }

        val dialog = android.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val quickEmojis = mapOf(
            R.id.emojiLike to "👍",
            R.id.emojiLove to "❤️",
            R.id.emojiHaha to "😂",
            R.id.emojiWow to "😮",
            R.id.emojiSad to "😢",
            R.id.emojiAngry to "😠"
        )

        quickEmojis.forEach { (id, emoji) ->
            val emojiView = dialogView.findViewById<TextView>(id)
            emojiView?.setOnClickListener {
                Log.d(TAG, "Quick reaction clicked: $emoji for message: $messageId")
                saveReactionToFirebase(messageId, emoji)
                dialog.dismiss()
            }
        }

        val emojiCategories = listOf(
            Pair("😊 Smileys", listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃",
                "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😙",
                "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔",
                "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥",
                "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮",
                "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "😎", "🤓",
                "🧐", "😕", "😟", "🙁", "☹️", "😮", "😯", "😲", "😳", "🥺",
                "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣",
                "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬", "😈",
                "👿", "💀", "☠️", "💩", "🤡", "👹", "👺", "👻", "👽", "👾",
                "🤖", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾"
            )),
            Pair("❤️ Hearts", listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
                "❤️‍🔥", "❤️‍🩹", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟",
                "☮️", "✝️", "☪️", "🕉", "☸️", "✡️", "🔯", "🕎", "☯️", "☦️",
                "🛐", "⛎", "♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏",
                "♐", "♑", "♒", "♓", "🆔", "⚛️", "🉑", "☢️", "☣️", "📴"
            )),
            Pair("👋 Hands", listOf(
                "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞",
                "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍",
                "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝",
                "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂",
                "🦻", "👃", "🧠", "🦷", "🦴", "👀", "👁️", "👅", "👄", "💋",
                "🩸", "💘", "💓", "💔", "💕", "💖", "💗", "💙", "💚", "💛"
            )),
            Pair("🎉 Objects", listOf(
                "💯", "✨", "🌟", "💥", "💫", "💦", "💨", "🕳️", "🎈", "🎊",
                "🎉", "🎁", "🏆", "🥇", "🥈", "🥉", "⚽", "🏀", "🏈", "⚾",
                "🎾", "🏐", "🏉", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍",
                "🏏", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽",
                "🛹", "🛼", "🛷", "⛸️", "🥌", "🎿", "⛷️", "🏂", "🪂", "🏋️",
                "🤼", "🤸", "🤺", "⛹️", "🤾", "🏌️", "🏇", "🧘", "🏄", "🏊",
                "🤽", "🚣", "🧗", "🚵", "🚴", "🏆", "🥇", "🥈", "🥉", "🏅"
            ))
        )

        val viewPager = ViewPager2(context).apply {
            adapter = EmojiPagerAdapter(context, emojiCategories) { selectedEmoji ->
                Log.d(TAG, "Emoji selected from grid: $selectedEmoji for message: $messageId")
                saveReactionToFirebase(messageId, selectedEmoji)
                dialog.dismiss()
            }
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
        }

        try {
            val parent = rvEmojis.parent as? ViewGroup
            if (parent != null) {
                val index = parent.indexOfChild(rvEmojis)
                parent.removeView(rvEmojis)
                val layoutParams = rvEmojis.layoutParams ?: ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    280.dpToPx(context)
                )
                parent.addView(viewPager, index, layoutParams)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing RecyclerView with ViewPager2: ${e.message}")
        }

        try {
            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = emojiCategories[position].first
            }.attach()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up TabLayoutMediator: ${e.message}")
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val layoutParams = window.attributes
            layoutParams.gravity = Gravity.BOTTOM
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            window.attributes = layoutParams
        }

        dialog.show()
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    override fun getItemCount(): Int = messages.size

}