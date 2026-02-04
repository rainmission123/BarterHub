@file:Suppress("DEPRECATION")

package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.adapters.MessagesAdapter
import com.example.barterhub.data.models.Message
import com.example.barterhub.databinding.FragmentChatBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.storage
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
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import com.example.barterhub.utils.NotificationHelper


class ChatFragment : Fragment() {
    private var notificationListener: ChildEventListener? = null
    private var selectedVideoFileName: String? = null
    private var selectedVideoFileSize: Long? = null
    private var selectedVideoDuration: Long? = null
    private var currentUserProfilePic: String? = null
    private var partnerProfilePic: String? = null
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private lateinit var pickVideoLauncher: ActivityResultLauncher<String>
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var messagesRef: DatabaseReference
    private lateinit var usersRef: DatabaseReference
    private lateinit var storage: FirebaseStorage
    private lateinit var storageRef: StorageReference
    private lateinit var messagesAdapter: MessagesAdapter
    private val messagesList = mutableListOf<Message>()
    private lateinit var pickImagesLauncher: ActivityResultLauncher<String>
    private var chatId = ""
    private var partnerId = ""
    private var partnerName = ""
    private var itemId = ""
    private var itemTitle = ""
    private var currentUserId = ""
    private var currentUserName = ""
    private var keyboardLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var messagesListener: ChildEventListener? = null
    private var isFragmentActive = false
    private lateinit var statusRef: DatabaseReference
    private var partnerStatusListener: ValueEventListener? = null
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private var currentPhotoPath: String? = null
    private val messageChangeListeners = mutableListOf<ChildEventListener>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeActivityResultLaunchers()

        if (arguments?.getBoolean("isTradeAccepted", false) == true) {
            sendTradeAcceptedMessage()
        }

        isFragmentActive = true
        setupFirebase()
        setupArguments()
        setupMyPresence()
        observePartnerStatus()
        setupUI()
        loadCurrentUserName()
        updateUnreadMessages()

    }

    private fun initializeActivityResultLaunchers() {

        // 📷 Camera launcher
        takePictureLauncher = registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success && currentPhotoPath != null) {
                val imageFile = File(currentPhotoPath!!)
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    imageFile
                )

                uploadAndSendImage(uri)

            } else {
                showSnackbar("Camera cancelled or failed")
            }
        }

        // 🖼️ Gallery (multiple images)
        pickImagesLauncher = registerForActivityResult(
            ActivityResultContracts.GetMultipleContents()
        ) { uris ->
            uris?.forEach { uri ->
                checkStoragePermission()
                uploadAndSendImage(uri)
            }
        }

        // 🎥 Video (UNCHANGED)
        pickVideoLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                checkStoragePermission()
                handleVideoSelection(it) }
        }
    }

    private fun uploadImageToFirebase(
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit,
        onProgress: ((Int) -> Unit)? = null
    ) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(imageUri)
            val bytes = inputStream?.readBytes() ?: return

            val client = OkHttpClient()

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
                .addFormDataPart("upload_preset", "barterhub_ids")
                .addFormDataPart("cloud_name", "dtccox0s0")
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/dtccox0s0/image/upload")
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

    // ------------------------------
    // TRADE ACCEPTED MESSAGE
    // ------------------------------
    private fun sendTradeAcceptedMessage() {
        val targetItemTitle = arguments?.getString("targetItemTitle") ?: "Unknown Item"
        val offeredItemTitle = arguments?.getString("offeredItemTitle") ?: "Unknown Item"
        val offeredBy = arguments?.getString("offeredBy") ?: "Unknown User"
        val acceptedBy = arguments?.getString("acceptedBy") ?: "Unknown User"

        sendDetailedTradeSystemMessage(
            offeredBy = offeredBy,
            acceptedBy = acceptedBy,
            offeredItemName = offeredItemTitle,
            targetItemName = targetItemTitle,
            offeredItemPoints = 0,
            targetItemPoints = 0
        )
    }

    private fun sendDetailedTradeSystemMessage(
        offeredBy: String,
        acceptedBy: String,
        offeredItemName: String,
        targetItemName: String,
        offeredItemPoints: Int,
        targetItemPoints: Int
    ) {
        if (chatId.isEmpty()) return

        val messageId = messagesRef.child(chatId).child("messages").push().key ?: return

        val difference = offeredItemPoints - targetItemPoints
        val differenceText = when {
            difference > 0 -> "Difference: ${difference}BP (in ${offeredBy}'s favor)"
            difference < 0 -> "Difference: ${-difference}BP (in ${acceptedBy}'s favor)"
            else -> "Equal trade! ✅"
        }

        val systemMessage = mapOf(
            "messageId" to messageId,
            "receiverId" to partnerId,
            "senderId" to "system",
            "senderName" to "System",
            "text" to "Trade Accepted!",
            "timestamp" to System.currentTimeMillis(),
            "isRead" to false,
            "messageType" to "system_trade_accepted",
            "tradeDetails" to mapOf(
                "offeredBy" to offeredBy,
                "acceptedBy" to acceptedBy,
                "offeredItemName" to offeredItemName,
                "targetItemName" to targetItemName,
                "offeredItemPoints" to offeredItemPoints,
                "targetItemPoints" to targetItemPoints,
                "pointsDifference" to difference,
                "differenceText" to differenceText
            )
        )

        messagesRef.child(chatId).child("messages").child(messageId).setValue(systemMessage)
            .addOnSuccessListener {
                val lastMessageText = "Trade accepted: $offeredItemName ↔ $targetItemName"
                val updates = mapOf(
                    "lastMessage" to lastMessageText,
                    "lastMessageTime" to System.currentTimeMillis()
                )
                messagesRef.child(chatId).updateChildren(updates)

                val inboxRef = database.getReference("user_inbox")
                val inboxUpdate = mapOf(
                    "lastMessage" to lastMessageText,
                    "lastMessageTime" to System.currentTimeMillis()
                )
                inboxRef.child(currentUserId).child(chatId).updateChildren(inboxUpdate)
                inboxRef.child(partnerId).child(chatId).updateChildren(inboxUpdate)

                Log.d("DEBUG_TRADE", "Trade system message sent successfully")
            }
            .addOnFailureListener { e ->
                Log.e("DEBUG_TRADE", "Failed to send trade system message: ${e.message}")
            }
    }

    private fun setupFirebase() {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
        messagesRef = database.getReference("chats")
        usersRef = database.getReference("users")
        currentUserId = auth.currentUser?.uid ?: ""
        statusRef = database.getReference("status")


        // Initialize Firebase Storage with error handling
        try {
            storage = Firebase.storage
            storageRef = storage.reference
            Log.d("FIREBASE_DEBUG", "Firebase Storage initialized successfully")
        } catch (e: Exception) {
            Log.e("FIREBASE_DEBUG", "Failed to initialize Firebase Storage: ${e.message}")
            Toast.makeText(requireContext(), "Firebase Storage initialization failed", Toast.LENGTH_LONG).show()
        }

    }

    private fun setupMyPresence() {
        if (currentUserId.isEmpty()) return

        val myStatusRef = statusRef.child(currentUserId)

        val onlineStatus = mapOf(
            "state" to "online",
            "lastSeen" to System.currentTimeMillis()
        )

        val offlineStatus = mapOf(
            "state" to "offline",
            "lastSeen" to System.currentTimeMillis()
        )

        myStatusRef.setValue(onlineStatus)
        myStatusRef.onDisconnect().setValue(offlineStatus)
    }

    private fun observePartnerStatus() {
        if (partnerId.isEmpty()) return

        partnerStatusListener = statusRef.child(partnerId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val state = snapshot.child("state").getValue(String::class.java)

                    if (state == "online") {
                        binding.partnerStatusText.text = "Online"
                    } else {
                        binding.partnerStatusText.text = "Offline"
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun setupArguments() {
        chatId = arguments?.getString("chatId").orEmpty()
        partnerId = arguments?.getString("partnerId").orEmpty()
        partnerName = arguments?.getString("partnerName") ?: "Chat Partner"
        itemId = arguments?.getString("itemId").orEmpty()
        itemTitle = arguments?.getString("itemTitle") ?: ""

        Log.d("DEBUG_CHAT", "setupArguments: chatId=$chatId, partnerId=$partnerId, partnerName=$partnerName")
    }

    private fun setupUI() {
        setupToolbar()
        setupRecyclerView()
        setupInputListeners()
        setupKeyboardListener()
        setupScrollToBottomFab()
    }

    private fun setupToolbar() {
        binding.toolbar.apply {
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_info -> {
                        val menuItemView = binding.toolbar.findViewById<View>(R.id.menu_info)
                        showChatOptions(menuItemView ?: binding.toolbar)
                        true
                    }

                    else -> false
                }
            }
        }

        binding.partnerNameText.text = partnerName
        binding.partnerStatusText.text = "Offline"
    }

    private fun showChatOptions(anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.chat_options_menu, popup.menu)

        try {
            val fields = popup.javaClass.declaredFields
            for (field in fields) {
                if (field.name == "mPopup") {
                    field.isAccessible = true
                    val menuPopupHelper = field.get(popup)
                    val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                    val setForceIcons = classPopupHelper.getDeclaredMethod(
                        "setForceShowIcon", Boolean::class.javaPrimitiveType
                    )
                    setForceIcons.invoke(menuPopupHelper, false)

                    val setDivider = classPopupHelper.getDeclaredMethod(
                        "setDividerEnabled", Boolean::class.javaPrimitiveType
                    )
                    setDivider.invoke(menuPopupHelper, true)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_view_profile -> {
                    openPartnerProfile()
                    true
                }
                R.id.action_clear_chat -> {
                    showClearChatConfirmation()
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun openPartnerProfile() {
        if (partnerId.isEmpty()) {
            Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
            return
        }

        val bundle = Bundle().apply {
            putString("ownerId", partnerId)
        }

        findNavController().navigate(
            R.id.ownerProfileFragment,
            bundle
        )
    }

    private fun showClearChatConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear Chat")
            .setMessage("Are you sure you want to clear this chat? This will only clear it for you.")
            .setPositiveButton("Clear") { _, _ ->
                clearChatForCurrentUser()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearChatForCurrentUser() {
        if (chatId.isEmpty()) return

        val inboxRef = database.getReference("user_inbox")

        // Remove chat from YOUR inbox only
        inboxRef.child(currentUserId).child(chatId).removeValue()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Chat cleared", Toast.LENGTH_SHORT).show()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
    }

    private fun setupRecyclerView() {
        messagesAdapter = MessagesAdapter(
            messages = messagesList,
            currentUserId = currentUserId,
            chatId = chatId,
            currentUserProfilePic = currentUserProfilePic,
            partnerProfilePic = partnerProfilePic
        ).apply {

            setOnProfilePictureClickListener { profilePicUrl ->
                showProfilePictureDialog(profilePicUrl)
            }

            setOnViewProfileClickListener { senderId ->
                openUserProfile(senderId)
            }
        }

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
                reverseLayout = false
            }
            adapter = messagesAdapter
            itemAnimator = null
        }
    }

    private fun openUserProfile(userId: String) {
        if (userId.isEmpty()) {
            Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
            return
        }

        val bundle = Bundle().apply {
            putString("ownerId", userId)
        }

        findNavController().navigate(
            R.id.ownerProfileFragment,
            bundle
        )
    }



    private fun showProfilePictureDialog(profilePicUrl: String) {
        val dialog = AlertDialog.Builder(requireContext())
            .setView(R.layout.dialog_profile_picture)
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()

        val imageView = dialog.findViewById<ImageView>(R.id.ivProfileDialog)
        imageView?.let {
            Glide.with(requireContext())
                .load(profilePicUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .into(it)
        }
    }

    private fun setupInputListeners() {
        binding.sendButton.setOnClickListener {
            sendMessage()
        }

        binding.messageEditText.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                sendMessage()
                true
            } else {
                false
            }
        }

        binding.messageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSendButtonState(s?.isNotEmpty() == true)
            }
        })

        binding.attachButton.setOnClickListener {
            showAttachmentOptions()
        }

        binding.cameraButton.setOnClickListener {
            openCameraDirectly()
        }

    }

    private fun openCameraDirectly() {
        val imageFile = createImageFile()
        if (imageFile != null) {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                imageFile
            )
            takePictureLauncher.launch(uri)
        } else {
            showSnackbar("Failed to create image file")
        }
    }

    private fun updateSendButtonState(hasText: Boolean) {
        binding.sendButton.isEnabled = hasText
        binding.sendButton.alpha = if (hasText) 1.0f else 0.5f
    }

    private fun showAttachmentOptions() {
        // Options: Gallery (images) at Video only
        val options = arrayOf("Gallery", "Video")

        AlertDialog.Builder(requireContext())
            .setTitle("Choose Attachment")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // Gallery - Images only
                        pickImagesLauncher.launch("image/*")
                    }
                    1 -> { // Video
                        pickVideoLauncher.launch("video/*")
                    }
                }
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    private fun handleVideoSelection(videoUri: Uri) {
        try {
            val resolver = requireContext().contentResolver

            val mimeType = resolver.getType(videoUri)
            if (mimeType == null || !mimeType.startsWith("video/")) {
                showSnackbar("Please select a valid video file")
                return
            }

            // 🔹 METADATA
            selectedVideoFileName = getFileNameFromUri(videoUri)
            selectedVideoFileSize = getFileSizeFromUri(videoUri)
            selectedVideoDuration = getVideoDuration(videoUri)

            // 🔹 SIZE CHECK (100MB)
            if ((selectedVideoFileSize ?: 0L) > 100 * 1024 * 1024) {
                showSnackbar("Video file too large (max 100MB)")
                return
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

            messagesAdapter.addMessage(tempMessage)
            scrollToBottom()

            uploadVideoToCloudinary(
                videoUri,
                onProgress = { progress ->
                    requireActivity().runOnUiThread {
                        tempMessage.isUploading = true
                        tempMessage.uploadProgress = progress
                        messagesAdapter.updateMessage(tempMessage)
                    }
                },
                onSuccess = { videoUrl ->
                    requireActivity().runOnUiThread {
                        // UPDATE TEMP MESSAGE
                        tempMessage.isUploading = false
                        tempMessage.uploadProgress = 100
                        tempMessage.videoUrl = videoUrl
                        messagesAdapter.updateMessage(tempMessage)

                        // SAVE REAL MESSAGE TO FIREBASE
                        val messageId = messagesRef
                            .child(chatId)
                            .child("messages")
                            .push()
                            .key ?: return@runOnUiThread

                        val messageObj = Message(
                            messageId = messageId,
                            receiverId = partnerId,
                            senderId = currentUserId,
                            senderName = currentUserName,
                            text = "",
                            videoUrl = videoUrl,
                            fileName = selectedVideoFileName,
                            fileSize = selectedVideoFileSize,
                            videoDuration = selectedVideoDuration,
                            timestamp = System.currentTimeMillis(),
                            read = false,
                            messageType = "video",
                            itemId = itemId
                        )

                        messagesRef
                            .child(chatId)
                            .child("messages")
                            .child(messageId)
                            .setValue(messageObj)

                        // UPDATE LAST MESSAGE
                        messagesRef.child(chatId).updateChildren(
                            mapOf(
                                "lastMessage" to "🎬 Video",
                                "lastMessageTime" to System.currentTimeMillis()
                            )
                        )

                        showSnackbar("🎬 Video sent!")
                    }
                },
                onFailure = { exception ->
                    requireActivity().runOnUiThread {
                        showSnackbar("Upload failed: ${exception?.message ?: "Unknown error"}")
                        tempMessage.isUploading = false
                        messagesAdapter.updateMessage(tempMessage)
                    }
                }
            )

        } catch (e: Exception) {
            Log.e("DEBUG_CHAT", "Video error", e)
            showSnackbar("Error selecting video")
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    "video_${System.currentTimeMillis()}.mp4"
                }
            }
        } catch (_: Exception) {
            "video_${System.currentTimeMillis()}.mp4"
        }
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        return try {
            requireContext().contentResolver.openFileDescriptor(uri, "r")?.use { parcel ->
                parcel.statSize
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun getVideoDuration(uri: Uri): Long? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(requireContext(), uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            duration?.toLongOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun uploadVideoToCloudinary(
        videoUri: Uri,
        onProgress: ((Int) -> Unit)? = null,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(videoUri)
            val bytes = inputStream?.readBytes() ?: throw Exception("Failed to read video")

            val client = OkHttpClient()

            // Create request body with progress tracking
            val requestBody = object : RequestBody() {
                override fun contentType() = "video/*".toMediaType()

                override fun writeTo(sink: BufferedSink) {
                    val total = bytes.size.toLong()
                    var uploaded: Long = 0

                    val source = bytes.inputStream().source()
                    var read: Long
                    val SEGMENT_SIZE = 8192L // 8KB chunks

                    while (source.read(sink.buffer, SEGMENT_SIZE).also { read = it } != -1L) {
                        uploaded += read
                        sink.flush()

                        // Calculate progress
                        val progress = (100 * uploaded / total).toInt()
                        onProgress?.invoke(progress)
                    }
                }

                override fun contentLength() = bytes.size.toLong()
            }

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "video.mp4", requestBody)
                .addFormDataPart("upload_preset", "barterhub_ids")
                .addFormDataPart("cloud_name", "dtccox0s0")
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/dtccox0s0/video/upload")
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

    private fun uploadAndSendImage(imageUri: Uri) {

        if (chatId.isEmpty()) {
            showSnackbar("Chat not initialized")
            return
        }

        // 1️⃣ TEMP MESSAGE (LOCAL PREVIEW)
        val tempMessageId = "local_${System.currentTimeMillis()}"

        val tempMessage = Message(
            messageId = tempMessageId,
            senderId = currentUserId,
            senderName = currentUserName,
            imageUri = imageUri.toString(),
            imageUrl = null,
            messageType = "image",
            timestamp = System.currentTimeMillis(),
            isUploading = true,
            uploadProgress = 0
        )

        messagesAdapter.addMessage(tempMessage)
        scrollToBottom()

        // 2️⃣ UPLOAD TO FIREBASE STORAGE
        uploadImageToFirebase(
            imageUri,
            onProgress = { progress ->
                requireActivity().runOnUiThread {
                    tempMessage.isUploading = true
                    tempMessage.uploadProgress = progress
                    messagesAdapter.updateMessage(tempMessage)
                }
            },
            onSuccess = { downloadUrl ->
                requireActivity().runOnUiThread {
                    // 3️⃣ UPDATE TEMP MESSAGE (UI THREAD)
                    tempMessage.isUploading = false
                    tempMessage.uploadProgress = 100
                    tempMessage.imageUrl = downloadUrl
                    tempMessage.imageUri = null

                    messagesAdapter.updateMessage(tempMessage)

                    // 4️⃣ SAVE REAL MESSAGE TO FIREBASE
                    val messageId = messagesRef
                        .child(chatId)
                        .child("messages")
                        .push()
                        .key ?: return@runOnUiThread

                    val messageObj = Message(
                        messageId = messageId,
                        receiverId = partnerId,
                        senderId = currentUserId,
                        senderName = currentUserName,
                        text = "",
                        imageUrl = downloadUrl,
                        imageUri = null,
                        timestamp = System.currentTimeMillis(),
                        read = false,
                        messageType = "image",
                        itemId = itemId
                    )

                    messagesRef
                        .child(chatId)
                        .child("messages")
                        .child(messageId)
                        .setValue(messageObj)

                    // 5️⃣ UPDATE LAST MESSAGE FOR CHAT
                    messagesRef.child(chatId).updateChildren(
                        mapOf(
                            "lastMessage" to "📷 Image",
                            "lastMessageTime" to System.currentTimeMillis()
                        )
                    )
                }
            },
            onFailure = { exception ->
                requireActivity().runOnUiThread {
                    showSnackbar("Upload failed: ${exception?.message ?: "Unknown error"}")
                    tempMessage.isUploading = false
                    messagesAdapter.updateMessage(tempMessage)
                }
            }
        )
    }

    private fun createImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
                currentPhotoPath = absolutePath
            }
        } catch (e: Exception) {
            Log.e("DEBUG_CHAT", "createImageFile error: ${e.message}")
            null
        }
    }

    // ------------------------------
    // MESSAGING LOGIC
    // ------------------------------
    private fun sendMessage() {
        val messageText = binding.messageEditText.text?.toString()?.trim().orEmpty()
        if (messageText.isEmpty()) {
            showSnackbar("Please enter a message")
            return
        }

        if (chatId.isEmpty()) {
            createNewChat(messageText)
        } else {
            sendMessageToChat(messageText)
        }

        binding.messageEditText.text?.clear()
    }

    private fun createNewChat(messageText: String) {
        chatId = if (currentUserId < partnerId)
            "chat_${currentUserId}_$partnerId"
        else
            "chat_${partnerId}_$currentUserId"

        val messageId = messagesRef.child(chatId).child("messages").push().key ?: return

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

        val chatMap = mapOf(
            "participants" to mapOf(currentUserId to true, partnerId to true),
            "itemId" to itemId,
            "itemTitle" to itemTitle,
            "lastMessage" to messageText,
            "lastMessageTime" to System.currentTimeMillis(),
            "createdAt" to System.currentTimeMillis(),
            "messages" to mapOf(messageId to message)
        )

        messagesRef.child(chatId).setValue(chatMap).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val inboxRef = database.getReference("user_inbox")

                val currentUserInbox = mapOf(
                    "chatId" to chatId,
                    "partnerId" to partnerId,
                    "partnerName" to partnerName,
                    "lastMessage" to messageText,
                    "lastMessageTime" to System.currentTimeMillis(),
                    "unreadCount" to 0
                )

                val partnerInbox = mapOf(
                    "chatId" to chatId,
                    "partnerId" to currentUserId,
                    "partnerName" to currentUserName,
                    "lastMessage" to messageText,
                    "lastMessageTime" to System.currentTimeMillis(),
                    "unreadCount" to 1
                )

                inboxRef.child(currentUserId).child(chatId).setValue(currentUserInbox)
                inboxRef.child(partnerId).child(chatId).setValue(partnerInbox)

                scrollToBottom()
            } else {
                showSnackbar("Failed to start chat")
            }
        }
    }

    private fun sendMessageToChat(messageText: String) {
        val messageId = messagesRef.child(chatId).child("messages").push().key ?: return

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

        messagesRef.child(chatId).child("messages").child(messageId).setValue(message)
            .addOnSuccessListener {
                val updates = mapOf(
                    "lastMessage" to messageText,
                    "lastMessageTime" to System.currentTimeMillis()
                )
                messagesRef.child(chatId).updateChildren(updates)

                val inboxRef = database.getReference("user_inbox")
                val inboxUpdate = mapOf(
                    "lastMessage" to messageText,
                    "lastMessageTime" to System.currentTimeMillis()
                )

                inboxRef.child(currentUserId).child(chatId).updateChildren(inboxUpdate)
                inboxRef.child(partnerId).child(chatId).updateChildren(inboxUpdate)

                inboxRef.child(partnerId).child(chatId).child("unreadCount")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val currentCount = snapshot.getValue(Int::class.java) ?: 0
                            inboxRef.child(partnerId).child(chatId).child("unreadCount")
                                .setValue(currentCount + 1)

                        }

                        override fun onCancelled(error: DatabaseError) {}
                    })

                scrollToBottom()
            }
    }

    private fun loadCurrentUserName() {
        if (currentUserId.isEmpty()) {
            initializeChat()
            return
        }

        usersRef.child(currentUserId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentUserName = snapshot.child("fullName").getValue(String::class.java)
                    ?: snapshot.child("username").getValue(String::class.java)
                            ?: "You"

                if (chatId.isNotEmpty()) {
                    initializeChat()
                } else {
                    Log.d("DEBUG_CHAT", "chatId is empty after loading user name")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                currentUserName = "You"
                if (chatId.isNotEmpty()) {
                    initializeChat()
                }
            }
        })
    }

    private fun initializeChat() {
        binding.messageEditText.hint = "Message $partnerName..."
        loadProfilePictures()
        loadMessages() // This now uses ChildEventListener
        listenForMessageChanges() // Add this line
        adjustInitialPadding()
        listenForIncomingMessagesForNotification()
    }

    private fun listenForIncomingMessagesForNotification() {
        if (chatId.isEmpty()) return

        // Remove old listener to avoid duplicates
        notificationListener?.let {
            messagesRef.child(chatId).child("messages").removeEventListener(it)
        }

        notificationListener = messagesRef
            .child(chatId)
            .child("messages")
            .addChildEventListener(object : ChildEventListener {

                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    if (!isFragmentActive) return

                    val message = snapshot.getValue(Message::class.java) ?: return

                    // 🔥 IMPORTANT CONDITIONS
                    if (
                        message.senderId != currentUserId &&   // not from me
                        !message.read                          // unread
                    ) {
                        // 🚫 IF CHAT IS OPEN → NO NOTIFICATION
                        if (isFragmentVisible()) return

                        NotificationHelper.showMessageNotification(
                            requireContext(),
                            partnerName,
                            when (message.messageType) {
                                "image" -> "📷 Image"
                                "video" -> "🎬 Video"
                                "system_trade_accepted" -> "Trade accepted"
                                else -> message.text ?: "New message"
                            }
                        )
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, prev: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, prev: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private var childMessagesListener: ChildEventListener? = null

    private fun listenForMessageChanges() {
        if (chatId.isEmpty()) return

        Log.d("DEBUG_CHAT", "👂 Setting up message change listener")

        val changesListener = messagesRef.child(chatId).child("messages")
            .addChildEventListener(object : ChildEventListener {
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    if (!isFragmentActive) return

                    val updatedMessage = snapshot.getValue(Message::class.java) ?: return
                    updatedMessage.messageId = updatedMessage.messageId ?: snapshot.key.orEmpty()

                    val index = messagesList.indexOfFirst {
                        it.messageId == updatedMessage.messageId
                    }

                    if (index != -1) {
                        Log.d("DEBUG_CHAT", "📝 Message updated: ${updatedMessage.messageId}, type: ${updatedMessage.messageType}")

                        messagesList[index] = updatedMessage
                        messagesAdapter.notifyItemChanged(index)

                        // ✅ Special handling for system trade completed
                        if (updatedMessage.messageType == "system_trade_completed") {
                            Log.d("DEBUG_CHAT", "🎉 Trade completed detected, updating UI...")

                            // Update the system message UI
                            messagesAdapter.notifyDataSetChanged()

                            // You can also show a toast or snackbar
                            showSnackbar("✅ Trade marked as completed!")
                        }
                    }
                }

                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    // Already handled by loadMessages
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {
                    // Handle if needed
                }

                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                    // Handle if needed
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("DEBUG_CHAT", "❌ Message change listener error: ${error.message}")
                }
            })

        // Store reference to remove later
        messageChangeListeners.add(changesListener)
    }

    private fun loadProfilePictures() {
        // Load current user profile
        if (currentUserId.isNotEmpty()) {
            usersRef.child(currentUserId).get().addOnSuccessListener { snapshot ->
                currentUserProfilePic = snapshot.child("profileImageUrl").getValue(String::class.java)
                currentUserName = snapshot.child("fullName").getValue(String::class.java)
                    ?: snapshot.child("username").getValue(String::class.java) ?: "You"

                // ✅ Update adapter after loading
                updateAdapterWithProfilePics()
            }
        }

        // Load partner profile
        if (partnerId.isNotEmpty()) {
            usersRef.child(partnerId).get().addOnSuccessListener { snapshot ->
                partnerProfilePic = snapshot.child("profileImageUrl").getValue(String::class.java)
                val partnerFullName = snapshot.child("fullName").getValue(String::class.java)

                partnerFullName?.let { partnerName = it }
                binding.partnerNameText.text = partnerName

                // Load partner profile image in toolbar
                partnerProfilePic?.let { url ->
                    Glide.with(requireContext())
                        .load(url)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .circleCrop()
                        .into(binding.partnerProfileImage)
                }

                // ✅ Update adapter after loading partner pic
                updateAdapterWithProfilePics()
            }
        }
    }

    // ✅ ADD: Helper function to update adapter
    private fun updateAdapterWithProfilePics() {
        if (::messagesAdapter.isInitialized) {
            messagesAdapter.setProfilePictures(currentUserProfilePic, partnerProfilePic)
        }
    }

    private fun isFragmentVisible(): Boolean {
        return isAdded && isVisible && hasWindowFocus()
    }

    private fun hasWindowFocus(): Boolean {
        return activity?.window?.decorView?.hasWindowFocus() == true
    }

    private fun loadMessages() {
        if (chatId.isEmpty()) {
            Log.w("DEBUG_CHAT", "chatId is empty, cannot load messages")
            return
        }

        Log.d("DEBUG_CHAT", "🔍 Loading messages from: chats/$chatId/messages")

        // Remove previous listener if exists to avoid multiple triggers
        messagesListener?.let {
            messagesRef.child(chatId).child("messages").removeEventListener(it)
        }

        // Use ChildEventListener instead of ValueEventListener
        messagesListener = messagesRef.child(chatId).child("messages")
            .orderByChild("timestamp")
            .addChildEventListener(object : ChildEventListener {
                @SuppressLint("NotifyDataSetChanged")
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    if (!isFragmentActive) return

                    val message = snapshot.getValue(Message::class.java)
                    message?.let {
                        // Ensure required fields are not null
                        it.messageId = it.messageId ?: snapshot.key.orEmpty()
                        it.senderId = it.senderId ?: ""
                        it.receiverId = it.receiverId ?: ""
                        it.timestamp = it.timestamp ?: System.currentTimeMillis()

                        // Check if message already exists
                        val existingIndex = messagesList.indexOfFirst { m ->
                            m.messageId == it.messageId
                        }

                        if (existingIndex == -1) {
                            messagesList.add(it)
                            messagesList.sortBy { m -> m.timestamp }
                            messagesAdapter.notifyDataSetChanged()

                            if (messagesList.isNotEmpty()) {
                                scrollToBottom()
                                markUnreadMessagesAsRead()
                            }
                        }
                    }
                }

                // ✅ IMPORTANT: THIS IS WHAT YOU NEED!
                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    if (!isFragmentActive) return

                    val updatedMessage = snapshot.getValue(Message::class.java)
                    if (updatedMessage != null) {
                        updatedMessage.messageId = updatedMessage.messageId ?: snapshot.key.orEmpty()

                        val index = messagesList.indexOfFirst {
                            it.messageId == updatedMessage.messageId
                        }

                        if (index != -1) {
                            Log.d("DEBUG_CHAT", "Message changed at index $index: ${updatedMessage.messageType}")
                            messagesList[index] = updatedMessage

                            // Update the adapter
                            messagesAdapter.notifyItemChanged(index)

                            // ✅ If it's a system message that's now completed, refresh UI
                            if (updatedMessage.messageType == "system_trade_completed") {
                                messagesAdapter.notifyDataSetChanged()
                                Log.d("DEBUG_CHAT", "System trade completed detected, refreshing UI")
                            }
                        }
                    }
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {
                    if (!isFragmentActive) return

                    val removedMessage = snapshot.getValue(Message::class.java)
                    removedMessage?.let {
                        val index = messagesList.indexOfFirst { m ->
                            m.messageId == it.messageId
                        }
                        if (index != -1) {
                            messagesList.removeAt(index)
                            messagesAdapter.notifyItemRemoved(index)
                        }
                    }
                }

                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                    // Not needed for this implementation
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("DEBUG_CHAT", "❌ Failed to load messages: ${error.message}")
                    if (isFragmentActive) showSnackbar("Failed to load messages: ${error.message}")
                }
            })

        Log.d("DEBUG_CHAT", "✅ ChildEventListener set up for messages")
    }

    // Mark messages as read safely
    private fun markUnreadMessagesAsRead() {
        if (!isFragmentActive || chatId.isEmpty()) return

        val inboxRef = database.getReference("user_inbox")

        messagesList.forEach { message ->
            if (message.senderId != currentUserId && !message.read) {
                val messageId = message.messageId
                if (!messageId.isNullOrEmpty()) {
                    messagesRef.child(chatId).child("messages").child(messageId)
                        .child("read").setValue(true)  // ✅ CORRECT
                }
            }
        }

        inboxRef.child(currentUserId).child(chatId).child("unreadCount").setValue(0)
    }

    private fun updateUnreadMessages() {
        if (!isFragmentActive || chatId.isEmpty()) return

        messagesList.forEach { message ->
            if (message.senderId != currentUserId && !message.read) {
                val messageId = message.messageId
                if (!messageId.isNullOrEmpty()) {
                    messagesRef.child(chatId).child("messages").child(messageId)
                        .child("read").setValue(true)  // ✅ CORRECT
                }
            }
        }

        val inboxRef = database.getReference("user_inbox")
        inboxRef.child(currentUserId).child(chatId).child("unreadCount").setValue(0)
    }

    private fun setupKeyboardListener() {
        keyboardLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (!isFragmentActive || _binding == null) return@OnGlobalLayoutListener

            try {
                val rect = Rect()
                binding.root.getWindowVisibleDisplayFrame(rect)
                val screenHeight = binding.root.rootView.height
                val keypadHeight = screenHeight - rect.bottom
                // You can handle keyboard visibility changes here if needed
            } catch (_: Exception) {
                // Ignore exceptions
            }
        }

        binding.root.viewTreeObserver.addOnGlobalLayoutListener(keyboardLayoutListener)
    }

    private fun setupScrollToBottomFab() {
        binding.messagesRecyclerView.addOnScrollListener(object :
            androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (!isFragmentActive) return

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount

                // Show/hide scroll to bottom FAB based on scroll position
                if (totalItemCount - lastVisiblePosition > 5) {
                    // Show scroll to bottom button
                    // You can add a FAB here if needed
                } else {
                    // Hide scroll to bottom button
                }
            }
        })
    }

    private fun scrollToBottom() {
        if (isFragmentActive && messagesList.isNotEmpty()) {
            // Only scroll if user is near the bottom
            val layoutManager = binding.messagesRecyclerView.layoutManager as LinearLayoutManager
            val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
            val totalItemCount = layoutManager.itemCount

            // If user is within 5 items from the bottom, scroll
            if (totalItemCount - lastVisiblePosition <= 5) {
                binding.messagesRecyclerView.scrollToPosition(messagesList.size - 1)
            }
        }
    }

    private fun adjustInitialPadding() {
        if (isFragmentActive) {
            binding.root.post {
                if (isFragmentActive) {
                    val inputHeight = binding.inputContainer.height
                    binding.messagesRecyclerView.setPadding(0, 0, 0, inputHeight + 32)
                }
            }
        }
    }

    private fun showSnackbar(message: String) {
        if (isFragmentActive) {
            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun checkStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    100
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Clean up message change listeners
        messageChangeListeners.forEach { listener ->
            messagesRef.child(chatId).child("messages").removeEventListener(listener)
        }
        messageChangeListeners.clear()

        notificationListener?.let {
            messagesRef.child(chatId).child("messages").removeEventListener(it)
        }

        childMessagesListener?.let {
            messagesRef.child(chatId).child("messages").removeEventListener(it)
        }

        partnerStatusListener?.let {
            statusRef.child(partnerId).removeEventListener(it)
        }

        isFragmentActive = false

        // Note: messagesListener is now a ChildEventListener, so we need to remove it properly
        if (messagesListener is ChildEventListener) {
            messagesRef.child(chatId).child("messages").removeEventListener(messagesListener as ChildEventListener)
        }

        keyboardLayoutListener?.let {
            if (_binding != null) {
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
            }
        }

        _binding = null
    }

    companion object {
    }
}