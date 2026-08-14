package com.example.barterhub.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.barterhub.R
import com.example.barterhub.adapters.message.ImageMessageViewHolder as BaseImageMessageViewHolder
import com.example.barterhub.adapters.message.ReceivedMessageViewHolder as BaseReceivedMessageViewHolder
import com.example.barterhub.adapters.message.SentMessageViewHolder as BaseSentMessageViewHolder
import com.example.barterhub.adapters.message.VideoMessageViewHolder as BaseVideoMessageViewHolder
import com.example.barterhub.adapters.message.reactions.MessageReactionBinder
import com.example.barterhub.adapters.message.reactions.MessageReactionController
import com.example.barterhub.adapters.message.reactions.ReactionPickerDialog
import com.example.barterhub.binders.ImageMessageBinder
import com.example.barterhub.binders.SystemMessageBinder
import com.example.barterhub.binders.TextMessageBinder
import com.example.barterhub.binders.VideoMessageBinder
import com.example.barterhub.data.models.Message
import com.example.barterhub.data.models.TradeRequest
import com.example.barterhub.viewholders.SystemMessageViewHolder
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

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
    }

    /*
     * Compatibility holder classes.
     *
     * Pinananatili ang MessagesAdapter.SentMessageViewHolder at iba pa
     * dahil iyon pa ang ginagamit ng existing message binders.
     *
     * Ang actual View fields ay nasa MessageViewHolders.kt na.
     */

    class SentMessageViewHolder(
        itemView: View
    ) : BaseSentMessageViewHolder(itemView)

    class ReceivedMessageViewHolder(
        itemView: View
    ) : BaseReceivedMessageViewHolder(itemView)

    class ImageMessageViewHolder(
        itemView: View
    ) : BaseImageMessageViewHolder(itemView)

    class VideoMessageViewHolder(
        itemView: View
    ) : BaseVideoMessageViewHolder(itemView)

    private var adapterContext: Context? = null

    private var onProfilePictureClickListener:
            ((String) -> Unit)? = null

    private var onViewProfileClickListener:
            ((String) -> Unit)? = null

    private var onTradeCompletedListener:
            ((TradeRequest) -> Unit)? = null

    private var onMessageDeletedListener:
            ((Message, Int) -> Unit)? = null

    private var onRatingCommentFocusChangedListener:
            ((Boolean) -> Unit)? = null

    private val ratingStatusMap = mutableMapOf<String, RatingStatus>()
    private var reviewsListener: ValueEventListener? = null

    private val database = FirebaseDatabase.getInstance().reference


    private lateinit var textMessageBinder: TextMessageBinder
    private lateinit var imageMessageBinder: ImageMessageBinder
    private lateinit var videoMessageBinder: VideoMessageBinder

    private val systemMessageBinder: SystemMessageBinder

    private val reactionController: MessageReactionController

    private val reactionBinder: MessageReactionBinder

    private val reactionPickerDialog: ReactionPickerDialog


    init {
        reactionController = MessageReactionController(
            chatId = chatId,
            currentUserId = currentUserId,
            onReactionsChanged = { messageId, reactions ->
                updateMessageReactions(
                    messageId = messageId,
                    reactions = reactions
                )
            }
        )

        reactionBinder = MessageReactionBinder(
            currentUserId = currentUserId,
            onAddReaction = { messageId, emoji ->
                reactionController.addReaction(
                    messageId = messageId,
                    emoji = emoji
                )
            },
            onRemoveReaction = { messageId, emoji ->
                reactionController.removeReaction(
                    messageId = messageId,
                    emoji = emoji
                )
            }
        )

        reactionPickerDialog = ReactionPickerDialog(
            currentUserId = currentUserId,
            findMessagePosition = { messageId ->
                messages.indexOfFirst {
                    it.messageId == messageId
                }
            },
            onReactionSelected = { messageId, emoji ->
                reactionController.toggleReaction(
                    messageId = messageId,
                    emoji = emoji
                )
            },
            onDeleteMessage = { message, position ->
                onMessageDeletedListener?.invoke(
                    message,
                    position
                )
            }
        )

        systemMessageBinder = SystemMessageBinder(
            currentUserId = currentUserId,
            chatId = chatId,
            onTradeCompletedListener = { tradeRequest ->
                onTradeCompletedListener?.invoke(tradeRequest)
            },
            onMessageUpdated = {
                notifyDataSetChanged()
            }
        )



        rebuildMessageBinders()
        setupRealTimeRatingMonitor()
    }

    private fun rebuildMessageBinders() {
        textMessageBinder = TextMessageBinder(
            currentUserId = currentUserId,
            partnerProfilePic = partnerProfilePic,
            chatId = chatId,
            onProfilePictureClickListener = { profilePicture ->
                onProfilePictureClickListener?.invoke(
                    profilePicture
                )
            },
            onMessageDeleted = { message, position ->
                onMessageDeletedListener?.invoke(
                    message,
                    position
                )

                Log.d(
                    TAG,
                    "Text message delete callback: ${message.messageId}"
                )
            }
        )

        imageMessageBinder = ImageMessageBinder(
            currentUserId = currentUserId,
            partnerProfilePic = partnerProfilePic,
            onReact = { message ->
                showReactionPicker(message)
            }
        ).also { binder ->
            binder.onViewProfileClickListener = { senderId ->
                onViewProfileClickListener?.invoke(senderId)
            }
        }

        videoMessageBinder = VideoMessageBinder(
            currentUserId = currentUserId,
            partnerProfilePic = partnerProfilePic,
            onReact = { message ->
                showReactionPicker(message)
            }
        )
    }

    fun setOnProfilePictureClickListener(
        listener: (String) -> Unit
    ) {
        onProfilePictureClickListener = listener
    }

    fun setOnViewProfileClickListener(
        listener: (String) -> Unit
    ) {
        onViewProfileClickListener = listener

        imageMessageBinder.onViewProfileClickListener = {
                senderId ->
            listener(senderId)
        }
    }

    fun setOnTradeCompletedListener(
        listener: (TradeRequest) -> Unit
    ) {
        onTradeCompletedListener = listener
    }

    fun setOnRatingCommentFocusChangedListener(
        listener: (Boolean) -> Unit
    ) {
        onRatingCommentFocusChangedListener = listener
    }

    fun setOnMessageDeletedListener(
        listener: (Message, Int) -> Unit
    ) {
        onMessageDeletedListener = listener

        /*
         * Rebuild TextMessageBinder dahil constructor callback
         * ang ginagamit nito para sa delete.
         */
        rebuildMessageBinders()

        notifyDataSetChanged()

        Log.d(
            TAG,
            "Message deleted listener updated"
        )
    }

    fun setProfilePictures(
        currentUserPic: String?,
        partnerPic: String?
    ) {
        currentUserProfilePic = currentUserPic
        partnerProfilePic = partnerPic

        rebuildMessageBinders()

        notifyDataSetChanged()
    }

    override fun getItemViewType(
        position: Int
    ): Int {
        val message = messages[position]

        return when {
            message.isSystemMessage ||
                    message.senderId == "system" ||
                    message.messageType == "system_trade_accepted" ||
                    message.messageType == "system_trade_completed" -> {
                VIEW_TYPE_SYSTEM
            }

            message.messageType == "video" -> {
                VIEW_TYPE_VIDEO
            }

            message.messageType == "image" -> {
                VIEW_TYPE_IMAGE
            }

            message.senderId == currentUserId -> {
                VIEW_TYPE_SENT
            }

            else -> {
                VIEW_TYPE_RECEIVED
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater =
            LayoutInflater.from(parent.context)

        return when (viewType) {
            VIEW_TYPE_SENT -> {
                SentMessageViewHolder(
                    inflater.inflate(
                        R.layout.item_message_sent,
                        parent,
                        false
                    )
                )
            }

            VIEW_TYPE_RECEIVED -> {
                ReceivedMessageViewHolder(
                    inflater.inflate(
                        R.layout.item_message_received,
                        parent,
                        false
                    )
                )
            }

            VIEW_TYPE_SYSTEM -> {
                SystemMessageViewHolder(
                    inflater.inflate(
                        R.layout.system_message_item,
                        parent,
                        false
                    )
                )
            }

            VIEW_TYPE_IMAGE -> {
                ImageMessageViewHolder(
                    inflater.inflate(
                        R.layout.item_message_image,
                        parent,
                        false
                    )
                )
            }

            VIEW_TYPE_VIDEO -> {
                VideoMessageViewHolder(
                    inflater.inflate(
                        R.layout.item_message_video,
                        parent,
                        false
                    )
                )
            }


            else -> {
                throw IllegalArgumentException(
                    "Invalid message view type: $viewType"
                )
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        val message = messages[position]

        val showProfilePicture =
            shouldShowProfilePicture(position)

        Log.d(
            TAG,
            "Binding position=$position, " +
                    "messageId=${message.messageId}, " +
                    "messageType=${message.messageType}"
        )

        when (holder) {

            is ReceivedMessageViewHolder -> {
                holder.boundMessageId =
                    message.messageId

                textMessageBinder.bind(
                    holder,
                    message,
                    position,
                    showProfilePicture
                )

                reactionBinder.bind(
                    holder,
                    message
                )

                holder.messageContainer
                    .setOnLongClickListener {
                        showReactionPicker(
                            context = holder.itemView.context,
                            message = message
                        )

                        true
                    }
            }

            is SentMessageViewHolder -> {
                holder.boundMessageId =
                    message.messageId

                textMessageBinder.bind(
                    holder,
                    message,
                    position,
                    showProfilePicture
                )

                reactionBinder.bind(
                    holder,
                    message
                )

                holder.messageContainer
                    .setOnLongClickListener {
                        showReactionPicker(
                            context = holder.itemView.context,
                            message = message
                        )

                        true
                    }

                holder.reactionsContainer
                    ?.setOnClickListener {
                        showReactionPicker(
                            context = holder.itemView.context,
                            message = message
                        )
                    }
            }

            is ImageMessageViewHolder -> {
                holder.boundMessageId =
                    message.messageId

                imageMessageBinder.bind(
                    holder,
                    message,
                    position,
                    showProfilePicture
                )

                reactionBinder.bind(
                    holder,
                    message
                )

                reactionController.startListening(
                    message.messageId
                )

                holder.singleReactionContainer
                    ?.setOnClickListener {
                        handleSingleReactionClick(
                            context = holder.itemView.context,
                            message = message
                        )
                    }

                holder.itemView
                    .findViewById<CardView>(
                        R.id.imageContainer
                    )
                    ?.setOnLongClickListener {
                        showReactionPicker(
                            context = holder.itemView.context,
                            message = message
                        )

                        true
                    }
            }

            is VideoMessageViewHolder -> {
                holder.boundMessageId =
                    message.messageId

                videoMessageBinder.bind(
                    holder,
                    message,
                    showProfilePicture
                )

                reactionBinder.bind(
                    holder,
                    message
                )

                reactionController.startListening(
                    message.messageId
                )

                holder.singleReactionContainer
                    ?.setOnClickListener {
                        handleSingleReactionClick(
                            context = holder.itemView.context,
                            message = message
                        )
                    }

                holder.videoContainer
                    .setOnLongClickListener {
                        showReactionPicker(
                            context = holder.itemView.context,
                            message = message
                        )

                        true
                    }
            }

            is SystemMessageViewHolder -> {
                systemMessageBinder.bind(
                    holder,
                    message,
                    position
                )
            }
        }
    }

    data class RatingStatus(
        var currentUserRated: Boolean = false,
        var partnerRated: Boolean = false,
        var totalRatings: Int = 0
    )

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
    private fun handleSingleReactionClick(
        context: Context,
        message: Message
    ) {
        val topReaction =
            message.reactions.entries
                .maxByOrNull {
                    it.value.size
                }

        val currentUserReacted =
            topReaction
                ?.value
                ?.containsKey(currentUserId)
                ?: false

        if (
            topReaction != null &&
            currentUserReacted
        ) {
            reactionController.removeReaction(
                messageId = message.messageId,
                emoji = topReaction.key
            )
        } else {
            showReactionPicker(
                context = context,
                message = message
            )
        }
    }

    private fun showReactionPicker(
        message: Message
    ) {
        val context = adapterContext

        if (context == null) {
            Log.w(
                TAG,
                "Cannot show reaction picker: adapter is not attached"
            )
            return
        }

        reactionPickerDialog.show(
            context = context,
            message = message
        )
    }

    private fun showReactionPicker(
        context: Context,
        message: Message
    ) {
        reactionPickerDialog.show(
            context = context,
            message = message
        )
    }

    private fun updateMessageReactions(
        messageId: String,
        reactions: Map<String, Map<String, Boolean>>
    ) {
        val index = messages.indexOfFirst {
            it.messageId == messageId
        }

        if (index == -1) {
            return
        }

        if (messages[index].reactions == reactions) {
            return
        }

        messages[index] =
            messages[index].copy(
                reactions = reactions
            )

        notifyItemChanged(
            index,
            "reactions"
        )
    }

    override fun onViewRecycled(
        holder: RecyclerView.ViewHolder
    ) {
        when (holder) {
            is ImageMessageViewHolder -> {
                holder.boundMessageId
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        reactionController.stopListening(it)
                    }

                holder.boundMessageId = null
            }

            is VideoMessageViewHolder -> {
                holder.boundMessageId
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        reactionController.stopListening(it)
                    }

                holder.boundMessageId = null
            }

            is SentMessageViewHolder -> {
                holder.boundMessageId = null
            }

            is ReceivedMessageViewHolder -> {
                holder.boundMessageId = null
            }
        }

        super.onViewRecycled(holder)
    }

    override fun onAttachedToRecyclerView(
        recyclerView: RecyclerView
    ) {
        super.onAttachedToRecyclerView(recyclerView)

        adapterContext =
            recyclerView.context
    }

    override fun onDetachedFromRecyclerView(
        recyclerView: RecyclerView
    ) {
        reactionController.stopAll()

        adapterContext = null

        super.onDetachedFromRecyclerView(recyclerView)
    }

    private fun shouldShowProfilePicture(
        position: Int
    ): Boolean {
        return messages[position].senderId !=
                currentUserId
    }

    override fun getItemCount(): Int =
        messages.size
}
