package com.example.barterhub.ui

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.adapters.MessagesAdapter
import com.example.barterhub.data.models.Message
import com.example.barterhub.data.repository.ChatRepository
import com.example.barterhub.databinding.FragmentChatBinding
import com.example.barterhub.ui.viewmodel.ChatEvent
import com.example.barterhub.ui.viewmodel.ChatState
import com.example.barterhub.ui.viewmodel.ChatViewModel
import com.example.barterhub.ui.viewmodel.TradeData
import com.example.barterhub.utils.*
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    // ViewModel
    private lateinit var viewModel: ChatViewModel

    // Adapter
    private lateinit var messagesAdapter: MessagesAdapter
    private val messagesList = mutableListOf<Message>()

    // Arguments
    private var chatId = ""
    private var partnerId = ""
    private var partnerName = ""
    private var itemId = ""
    private var itemTitle = ""
    private var currentUserId = ""
    private var currentUserName = ""

    // Profile pics
    private var currentUserProfilePic: String? = null
    private var partnerProfilePic: String? = null

    // Camera
    private var currentPhotoPath: String? = null

    // Fragment state
    private var isFragmentActive = false

    // Activity Result Launchers
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoPath != null) {
            val imageFile = File(currentPhotoPath!!)
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                imageFile
            )
            viewModel.sendImageMessage(uri, requireContext())
        } else {
            showSnackbar("Camera cancelled or failed")
        }
    }

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris?.forEach { uri ->
            PermissionHelper.checkStoragePermission(this@ChatFragment)
            viewModel.sendImageMessage(uri, requireContext())
        }
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            PermissionHelper.checkStoragePermission(this@ChatFragment)
            viewModel.handleVideoSelection(it, requireContext())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isFragmentActive = true
        parseArguments()
        setupFirebaseAndViewModel()
        setupUI()
        loadCurrentUserName()
        observeViewModel()
    }

    private fun parseArguments() {
        chatId = arguments?.getString("chatId").orEmpty()
        partnerId = arguments?.getString("partnerId").orEmpty()
        partnerName = arguments?.getString("partnerName") ?: "Chat Partner"
        itemId = arguments?.getString("itemId").orEmpty()
        itemTitle = arguments?.getString("itemTitle").orEmpty()

        Log.d("DEBUG_CHAT", "setupArguments: chatId=$chatId, partnerId=$partnerId, partnerName=$partnerName")
    }

    private fun setupFirebaseAndViewModel() {
        val auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""

        val database = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
        val repository = ChatRepository(database)

        viewModel = ChatViewModel(
            chatRepository = repository,
            cloudinaryUtils = CloudinaryUtils
        )

        // Check for trade accepted flag
        if (arguments?.getBoolean("isTradeAccepted", false) == true) {
            val tradeData = TradeData(
                targetItemTitle = arguments?.getString("targetItemTitle") ?: "Unknown Item",
                offeredItemTitle = arguments?.getString("offeredItemTitle") ?: "Unknown Item",
                offeredBy = arguments?.getString("offeredBy") ?: "Unknown User",
                acceptedBy = arguments?.getString("acceptedBy") ?: "Unknown User",
                requestId = arguments?.getString("requestId") ?: ""
            )
            viewModel.setTradeAccepted(tradeData)
        }
    }

    private fun loadCurrentUserName() {
        if (currentUserId.isEmpty()) {
            initializeViewModel()
            return
        }

        val database = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
        val usersRef = database.getReference("users")

        usersRef.child(currentUserId).get().addOnSuccessListener { snapshot ->
            currentUserName = snapshot.child("fullName").getValue(String::class.java)
                ?: snapshot.child("username").getValue(String::class.java)
                        ?: "You"

            initializeViewModel()
        }.addOnFailureListener {
            currentUserName = "You"
            initializeViewModel()
        }
    }

    private fun initializeViewModel() {
        viewModel.initialize(
            chatId = chatId,
            partnerId = partnerId,
            partnerName = partnerName,
            itemId = itemId,
            itemTitle = itemTitle,
            currentUserId = currentUserId,
            currentUserName = currentUserName
        )
    }

    private fun setupUI() {
        setupToolbar()
        setupRecyclerView()
        setupInputListeners()
        setupKeyboardListener()
        loadProfilePictures()
    }

    private fun setupToolbar() {
        binding.toolbar.apply {
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_info -> {
                        showChatOptions()
                        true
                    }
                    else -> false
                }
            }
        }

        binding.partnerNameText.text = partnerName
        binding.partnerStatusText.text = "Offline"
    }

    private fun setupRecyclerView() {
        messagesAdapter = MessagesAdapter(
            messages = messagesList,
            currentUserId = currentUserId,
            chatId = chatId,
            currentUserProfilePic = currentUserProfilePic,
            partnerProfilePic = partnerProfilePic
        ).apply {
            // 👇 SET THE DELETE LISTENER HERE
            setOnMessageDeletedListener { message, position ->
                Log.d("ChatFragment", "Deleting message: ${message.messageId}")
                viewModel.hideMessageForCurrentUser(message)
            }

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

    private fun updateSendButtonState(hasText: Boolean) {
        binding.sendButton.isEnabled = hasText
        binding.sendButton.alpha = if (hasText) 1.0f else 0.5f
    }

    private fun showAttachmentOptions() {
        val options = arrayOf("Gallery", "Video")

        AlertDialog.Builder(requireContext())
            .setTitle("Choose Attachment")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImagesLauncher.launch("image/*")
                    1 -> pickVideoLauncher.launch("video/*")
                }
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    private fun openCameraDirectly() {
        val imageFile = FileHelper.createImageFile(requireContext())
        if (imageFile != null) {
            currentPhotoPath = imageFile.absolutePath
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

    private fun sendMessage() {
        val messageText = binding.messageEditText.text?.toString()?.trim().orEmpty()
        if (messageText.isEmpty()) {
            showSnackbar("Please enter a message")
            return
        }

        viewModel.sendTextMessage(messageText)
        binding.messageEditText.text?.clear()
    }

    private fun loadProfilePictures() {
        val database = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
        val usersRef = database.getReference("users")

        // Load current user profile
        if (currentUserId.isNotEmpty()) {
            usersRef.child(currentUserId).get().addOnSuccessListener { snapshot ->
                currentUserProfilePic = snapshot.child("profileImageUrl").getValue(String::class.java)
                viewModel.loadProfilePictures(currentUserProfilePic, partnerProfilePic)
            }
        }

        // Load partner profile
        if (partnerId.isNotEmpty()) {
            usersRef.child(partnerId).get().addOnSuccessListener { snapshot ->
                partnerProfilePic = snapshot.child("profileImageUrl").getValue(String::class.java)
                val partnerFullName = snapshot.child("fullName").getValue(String::class.java)

                partnerFullName?.let {
                    partnerName = it
                    binding.partnerNameText.text = partnerName
                }

                partnerProfilePic?.let { url ->
                    Glide.with(requireContext())
                        .load(url)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .circleCrop()
                        .into(binding.partnerProfileImage)
                }

                viewModel.loadProfilePictures(currentUserProfilePic, partnerProfilePic)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                updateUI(state)
            }
        }

        lifecycleScope.launch {
            viewModel.events.collect { event ->
                handleEvent(event)
            }
        }
    }

    private fun updateUI(state: ChatState) {
        // Update messages
        messagesList.clear()
        messagesList.addAll(state.messages)
        messagesAdapter.notifyDataSetChanged()
        scrollToBottom()

        // Update toolbar
        binding.partnerNameText.text = state.partnerName
        binding.partnerStatusText.text = state.partnerStatus
        binding.messageEditText.hint = "Message ${state.partnerName}..."

        // Update profile pictures in adapter
        messagesAdapter.setProfilePictures(
            state.currentUserProfilePic,
            state.partnerProfilePic
        )

        // Update upload progress
        state.uploadProgress.forEach { (messageId, progress) ->
            val index = messagesList.indexOfFirst { it.messageId == messageId }
            if (index != -1) {
                messagesList[index].uploadProgress = progress
                messagesAdapter.notifyItemChanged(index)
            }
        }
    }

    private fun handleEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.ShowError -> showSnackbar(event.message)
            is ChatEvent.ShowMessage -> showSnackbar(event.message)
            is ChatEvent.NavigateToProfile -> openUserProfile(event.userId)
            is ChatEvent.NavigateToPartnerProfile -> openPartnerProfile()
            ChatEvent.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun openPartnerProfile() {
        if (partnerId.isEmpty()) {
            Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
            return
        }

        val bundle = Bundle().apply {
            putString("ownerId", partnerId)
        }

        findNavController().navigate(R.id.ownerProfileFragment, bundle)
    }

    private fun openUserProfile(userId: String) {
        if (userId.isEmpty()) {
            Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
            return
        }

        val bundle = Bundle().apply {
            putString("ownerId", userId)
        }

        findNavController().navigate(R.id.ownerProfileFragment, bundle)
    }

    private fun showChatOptions() {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), binding.toolbar)
        popup.menuInflater.inflate(R.menu.chat_options_menu, popup.menu)

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

    private fun showClearChatConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear Chat")
            .setMessage("Are you sure you want to clear this chat? This will only clear it for you.")
            .setPositiveButton("Clear") { _, _ ->
                viewModel.clearChatForCurrentUser()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showProfilePictureDialog(profilePicUrl: String) {
        val dialog = AlertDialog.Builder(requireContext())
            .setView(R.layout.dialog_profile_picture)
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
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

    private fun setupKeyboardListener() {
        // Keep the keyboard listener logic
        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            if (!isFragmentActive || _binding == null) return@addOnGlobalLayoutListener
            // Keyboard handling logic
        }
    }

    private fun scrollToBottom() {
        if (isFragmentActive && messagesList.isNotEmpty()) {
            val layoutManager = binding.messagesRecyclerView.layoutManager as LinearLayoutManager
            val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
            val totalItemCount = layoutManager.itemCount

            if (totalItemCount - lastVisiblePosition <= 5) {
                binding.messagesRecyclerView.scrollToPosition(messagesList.size - 1)
            }
        }
    }

    private fun showSnackbar(message: String) {
        if (isFragmentActive) {
            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.markMessagesAsRead()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentActive = false
        viewModel.clearListeners()
        _binding = null
    }
}