package com.example.barterhub.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.barterhub.data.models.Message
import com.example.barterhub.data.repository.IChatRepository
import com.example.barterhub.utils.CloudinaryUtils
import com.example.barterhub.utils.FileHelper
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: IChatRepository,
    private val cloudinaryUtils: CloudinaryUtils
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state

    private val _events = MutableSharedFlow<ChatEvent>()
    val events = _events.asSharedFlow()

    // Original variables from ChatFragment
    private var chatId: String = ""
    private var partnerId: String = ""
    private var partnerName: String = ""
    private var itemId: String = ""
    private var itemTitle: String = ""
    private var currentUserId: String = ""
    private var currentUserName: String = ""

    // Listeners
    private var messagesListener: ChildEventListener? = null
    private var notificationListener: ChildEventListener? = null
    private var partnerStatusListener: ValueEventListener? = null

    // Upload tracking
    private var selectedVideoFileName: String? = null
    private var selectedVideoFileSize: Long? = null
    private var selectedVideoDuration: Long? = null

    fun initialize(
        chatId: String,
        partnerId: String,
        partnerName: String,
        itemId: String,
        itemTitle: String,
        currentUserId: String,
        currentUserName: String
    ) {
        this.chatId = chatId
        this.partnerId = partnerId
        this.partnerName = partnerName
        this.itemId = itemId
        this.itemTitle = itemTitle
        this.currentUserId = currentUserId
        this.currentUserName = currentUserName

        _state.value = _state.value.copy(
            partnerName = partnerName,
            currentUserName = currentUserName
        )

        setupUserPresence()
        observePartnerStatus()

        if (chatId.isNotEmpty()) {
            observeMessages()
            observeNewMessagesForNotification()
        }
    }

    private fun setupUserPresence() {
        chatRepository.setupUserPresence(currentUserId)
    }

    private fun observePartnerStatus() {
        partnerStatusListener = chatRepository.observePartnerStatus(partnerId) { status ->
            viewModelScope.launch {
                _state.value = _state.value.copy(
                    partnerStatus = if (status.equals("online", ignoreCase = true)) "Online" else "Offline"
                )
            }
        }
    }

    private fun observeMessages() {
        messagesListener = chatRepository.observeMessages(
            chatId = chatId,
            onMessageAdded = { message ->
                viewModelScope.launch {
                    // 👇 CHECK KUNG HIDDEN PARA SA CURRENT USER
                    if (message.isHiddenForUser(currentUserId)) {
                        return@launch // Skip this message
                    }

                    val currentMessages = _state.value.messages.toMutableList()
                    if (currentMessages.none { it.messageId == message.messageId }) {
                        currentMessages.add(message)
                        currentMessages.sortBy { it.timestamp ?: 0L }
                        _state.value = _state.value.copy(messages = currentMessages)

                        if (message.senderId != currentUserId && !message.read) {
                            markMessagesAsRead()
                        }

                    }
                }
            },
            onMessageChanged = { updatedMessage ->
                viewModelScope.launch {
                    // 👇 CHECK DIN DITO
                    if (updatedMessage.isHiddenForUser(currentUserId)) {
                        // If message became hidden, remove from list
                        val currentMessages = _state.value.messages.toMutableList()
                        currentMessages.removeAll { it.messageId == updatedMessage.messageId }
                        _state.value = _state.value.copy(messages = currentMessages)
                        return@launch
                    }

                    val currentMessages = _state.value.messages.toMutableList()
                    val index = currentMessages.indexOfFirst { it.messageId == updatedMessage.messageId }
                    if (index != -1) {
                        currentMessages[index] = updatedMessage
                        _state.value = _state.value.copy(messages = currentMessages)
                    }
                }
            },
            onMessageRemoved = { messageId ->
                viewModelScope.launch {
                    val currentMessages = _state.value.messages.toMutableList()
                    currentMessages.removeAll { it.messageId == messageId }
                    _state.value = _state.value.copy(messages = currentMessages)
                }
            }
        )
    }

    fun hideMessageForCurrentUser(message: Message) {
        viewModelScope.launch {
            try {
                if (chatId.isNotEmpty() && message.messageId.isNotEmpty()) {
                    chatRepository.hideMessageForUser(chatId, message.messageId, currentUserId)

                    // ✅ Check kung ito ang last message - DIRECT CHECK without function
                    val currentMessages = _state.value.messages
                        .filter { !it.isHiddenForUser(currentUserId) && it.messageId != message.messageId }

                    val shouldUpdateLastMessage = currentMessages.isEmpty() ||
                            currentMessages.maxByOrNull { it.timestamp ?: 0L }?.timestamp == message.timestamp

                    if (shouldUpdateLastMessage) {
                        val lastMessageData = chatRepository.getLastMessageAfterDeletion(chatId, currentUserId)

                        if (lastMessageData != null) {
                            chatRepository.updateLastMessage(
                                chatId,
                                lastMessageData.first,
                                lastMessageData.second
                            )
                        } else {
                            chatRepository.updateLastMessage(chatId, "", System.currentTimeMillis())
                        }
                    }

                    val currentMessagesList = _state.value.messages.toMutableList()
                    currentMessagesList.removeAll { it.messageId == message.messageId }
                    _state.value = _state.value.copy(messages = currentMessagesList)

                    _events.emit(ChatEvent.ShowMessage("Message deleted"))
                }
            } catch (e: Exception) {
                _events.emit(ChatEvent.ShowError("Failed to delete message: ${e.message}"))
            }
        }
    }

    private fun observeNewMessagesForNotification() {
        notificationListener = chatRepository.observeNewMessagesForNotification(
            chatId = chatId,
            onNewMessage = { message ->
                viewModelScope.launch {
                    if (message.senderId != currentUserId && !message.read) {
                        // Don't show notification if fragment is visible
                        // This will be handled by the fragment
                    }
                }
            }
        )
    }

    fun sendTextMessage(text: String) {
        viewModelScope.launch {
            try {
                if (chatId.isEmpty()) {
                    createNewChat(text)
                } else {
                    sendMessageToExistingChat(text)
                }
            } catch (e: Exception) {
                _events.emit(ChatEvent.ShowError("Failed to send message"))
            }
        }
    }

    private suspend fun createNewChat(messageText: String) {
        val messageId = "temp_${System.currentTimeMillis()}"
        val message = Message(
            messageId = messageId,
            receiverId = partnerId,
            senderId = currentUserId,
            senderName = currentUserName,
            text = messageText,
            timestamp = System.currentTimeMillis(),
            read = false,
            messageType = "text",
            itemId = itemId
        )

        chatId = chatRepository.createChat(
            userId1 = currentUserId,
            userId2 = partnerId,
            itemId = itemId,
            itemTitle = itemTitle,
            firstMessage = message
        )

        // Re-initialize with new chatId
        observeMessages()
        observeNewMessagesForNotification()
        _events.emit(ChatEvent.ShowMessage("Chat started"))
    }

    private suspend fun sendMessageToExistingChat(messageText: String) {
        val message = Message(
            senderId = currentUserId,
            receiverId = partnerId,
            senderName = currentUserName,
            text = messageText,
            timestamp = System.currentTimeMillis(),
            read = false,
            messageType = "text",
            itemId = itemId
        )

        chatRepository.sendMessage(chatId, message)
        chatRepository.updateLastMessage(chatId, messageText, System.currentTimeMillis())
    }

    fun sendImageMessage(uri: Uri, context: Context) {
        viewModelScope.launch {
            if (chatId.isEmpty()) {
                _events.emit(ChatEvent.ShowError("Chat not initialized"))
                return@launch
            }

            val tempMessageId = "local_${System.currentTimeMillis()}"
            val tempMessage = Message(
                messageId = tempMessageId,
                senderId = currentUserId,
                senderName = currentUserName,
                imageUri = uri.toString(),
                imageUrl = null,
                messageType = "image",
                timestamp = System.currentTimeMillis(),
                isUploading = true,
                uploadProgress = 0
            )

            // Add temp message to UI
            addTempMessage(tempMessage)

            cloudinaryUtils.uploadImage(
                context = context,
                imageUri = uri,
                onProgress = { progress ->
                    updateUploadProgress(tempMessageId, progress)
                },
                onSuccess = { imageUrl ->
                    viewModelScope.launch {
                        // Update temp message
                        removeTempMessage(tempMessageId)

                        // Send real message
                        val message = Message(
                            senderId = currentUserId,
                            receiverId = partnerId,
                            senderName = currentUserName,
                            imageUrl = imageUrl,
                            messageType = "image",
                            timestamp = System.currentTimeMillis(),
                            read = false,
                            itemId = itemId
                        )

                        chatRepository.sendMessage(chatId, message)
                        chatRepository.updateLastMessage(chatId, "📷 Image", System.currentTimeMillis())
                        _events.emit(ChatEvent.ShowMessage("📷 Image sent!"))
                    }
                },
                onFailure = { error ->
                    viewModelScope.launch {
                        removeTempMessage(tempMessageId)
                        _events.emit(ChatEvent.ShowError("Upload failed: ${error.message}"))
                    }
                }
            )
        }
    }

    fun handleVideoSelection(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri)
                if (mimeType == null || !mimeType.startsWith("video/")) {
                    _events.emit(ChatEvent.ShowError("Please select a valid video file"))
                    return@launch
                }

                // Get metadata
                selectedVideoFileName = FileHelper.getFileNameFromUri(context, uri)
                selectedVideoFileSize = FileHelper.getFileSizeFromUri(context, uri)
                selectedVideoDuration = FileHelper.getVideoDuration(context, uri)

                // Size check (100MB)
                if ((selectedVideoFileSize ?: 0L) > 100 * 1024 * 1024) {
                    _events.emit(ChatEvent.ShowError("Video file too large (max 100MB)"))
                    return@launch
                }

                val tempMessageId = "local_video_${System.currentTimeMillis()}"
                val tempMessage = Message(
                    messageId = tempMessageId,
                    senderId = currentUserId,
                    senderName = currentUserName,
                    videoUrl = null,
                    fileName = selectedVideoFileName,
                    fileSize = selectedVideoFileSize,
                    videoDuration = selectedVideoDuration,
                    messageType = "video",
                    timestamp = System.currentTimeMillis(),
                    isUploading = true,
                    uploadProgress = 0
                )

                addTempMessage(tempMessage)

                cloudinaryUtils.uploadVideo(
                    videoUri = uri,
                    context = context,
                    onProgress = { progress ->
                        updateUploadProgress(tempMessageId, progress)
                    },
                    onSuccess = { videoUrl ->
                        viewModelScope.launch {
                            removeTempMessage(tempMessageId)

                            val message = Message(
                                senderId = currentUserId,
                                receiverId = partnerId,
                                senderName = currentUserName,
                                videoUrl = videoUrl,
                                fileName = selectedVideoFileName,
                                fileSize = selectedVideoFileSize,
                                videoDuration = selectedVideoDuration,
                                messageType = "video",
                                timestamp = System.currentTimeMillis(),
                                read = false,
                                itemId = itemId
                            )

                            chatRepository.sendMessage(chatId, message)
                            chatRepository.updateLastMessage(chatId, "🎬 Video", System.currentTimeMillis())
                            _events.emit(ChatEvent.ShowMessage("🎬 Video sent!"))
                        }
                    },
                    onFailure = { error ->
                        viewModelScope.launch {
                            removeTempMessage(tempMessageId)
                            _events.emit(ChatEvent.ShowError("Upload failed: ${error.message}"))
                        }
                    }
                )

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Video error", e)
                _events.emit(ChatEvent.ShowError("Error selecting video"))
            }
        }
    }

    fun markMessagesAsRead() {
        viewModelScope.launch {
            if (chatId.isNotEmpty()) {
                chatRepository.markMessagesAsRead(chatId, currentUserId, _state.value.messages)
            }
        }
    }

    fun clearChatForCurrentUser() {
        viewModelScope.launch {
            if (chatId.isNotEmpty()) {
                chatRepository.clearChatForUser(chatId, currentUserId)
                _events.emit(ChatEvent.ShowMessage("Chat cleared"))
                _events.emit(ChatEvent.NavigateBack)
            }
        }
    }

    fun loadProfilePictures(currentUserPic: String?, partnerPic: String?) {
        _state.value = _state.value.copy(
            currentUserProfilePic = currentUserPic,
            partnerProfilePic = partnerPic
        )
    }

    fun setTradeAccepted(tradeData: TradeData) {
        _state.value = _state.value.copy(
            isTradeAccepted = true,
            tradeData = tradeData
        )
    }

    private fun addTempMessage(message: Message) {
        val currentMessages = _state.value.messages.toMutableList()
        currentMessages.add(message)
        currentMessages.sortBy { it.timestamp ?: 0L }
        _state.value = _state.value.copy(messages = currentMessages)
    }

    private fun removeTempMessage(messageId: String) {
        val currentMessages = _state.value.messages.toMutableList()
        currentMessages.removeAll { it.messageId == messageId }
        _state.value = _state.value.copy(messages = currentMessages)
    }

    private fun updateUploadProgress(messageId: String, progress: Int) {
        val currentProgress = _state.value.uploadProgress.toMutableMap()
        currentProgress[messageId] = progress
        _state.value = _state.value.copy(uploadProgress = currentProgress)

        // Update the message in the list
        val currentMessages = _state.value.messages.toMutableList()
        val index = currentMessages.indexOfFirst { it.messageId == messageId }
        if (index != -1) {
            currentMessages[index].uploadProgress = progress
            _state.value = _state.value.copy(messages = currentMessages)
        }
    }

    fun clearListeners() {
        if (chatId.isNotEmpty()) {
            messagesListener?.let { chatRepository.removeMessagesListener(chatId, it) }
            notificationListener?.let { chatRepository.removeMessagesListener(chatId, it) }
        }
        partnerStatusListener?.let { chatRepository.removeStatusListener(partnerId, it) }
    }
}
