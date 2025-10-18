package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.barterhub.R
import com.example.barterhub.adapters.MessagesAdapter
import com.example.barterhub.data.models.Message
import com.example.barterhub.databinding.FragmentChatBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

@Suppress("DEPRECATION")
class ChatFragment : Fragment() {

    private var currentUserProfilePic: String? = null
    private var partnerProfilePic: String? = null

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var messagesRef: DatabaseReference
    private lateinit var usersRef: DatabaseReference

    private lateinit var messagesAdapter: MessagesAdapter
    private val messagesList = mutableListOf<Message>()

    private var chatId = ""
    private var partnerId = ""
    private var partnerName = ""
    private var itemId = ""
    private var itemTitle = ""
    private var currentUserId = ""
    private var currentUserName = ""

    private var keyboardLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var messagesListener: ValueEventListener? = null

    private var isFragmentActive = false

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
        setupFirebase()
        setupArguments()
        setupUI()
        loadCurrentUserName()
    }

    private fun setupFirebase() {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
        messagesRef = database.getReference("chats")
        usersRef = database.getReference("users")
        currentUserId = auth.currentUser?.uid ?: ""
    }

    private fun setupArguments() {
        chatId = arguments?.getString("chatId").orEmpty()
        partnerId = arguments?.getString("partnerId").orEmpty()
        partnerName = arguments?.getString("partnerName") ?: "Chat Partner"
        itemId = arguments?.getString("itemId").orEmpty()
        itemTitle = arguments?.getString("itemTitle") ?: ""
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
        }

        // Update partner info in toolbar
        binding.partnerNameText.text = partnerName
        binding.partnerStatusText.text = if (itemTitle.isNotEmpty()) "About: $itemTitle" else "Online"
    }

    private fun setupRecyclerView() {
        messagesAdapter = MessagesAdapter(messagesList, currentUserId).apply {
            setProfilePictures(currentUserProfilePic, partnerProfilePic)

            setOnProfilePictureClickListener { profilePicUrl ->
                showProfilePictureDialog(profilePicUrl)
            }
        }

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = messagesAdapter
            itemAnimator = null
        }
    }

    private fun showProfilePictureDialog(profilePicUrl: String) {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(R.layout.dialog_profile_picture)
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()

        // Load the image in the dialog
        val imageView = dialog.findViewById<ImageView>(R.id.ivProfileDialog)
        imageView?.let {
            Glide.with(requireContext())
                .load(profilePicUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .into(it)
        }
    }

    private fun setupInputListeners() {
        // Send button click
        binding.sendButton.setOnClickListener {
            sendMessage()
        }

        // Enter key to send
        binding.messageEditText.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                sendMessage()
                true
            } else {
                false
            }
        }

        // Typing indicator
        binding.messageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSendButtonState(s?.isNotEmpty() == true)
            }
        })

        // Attach button
        binding.attachButton.setOnClickListener {
            showAttachmentOptions()
        }

    }

    private fun updateSendButtonState(hasText: Boolean) {
        binding.sendButton.isEnabled = hasText
        binding.sendButton.alpha = if (hasText) 1.0f else 0.5f
    }

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
        chatId = messagesRef.push().key ?: return

        val messageId = messagesRef.child(chatId).child("messages").push().key ?: return

        val message = Message(
            messageId = messageId,
            senderId = currentUserId,
            senderName = currentUserName,
            text = messageText,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            messageType = "text"
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
            senderId = currentUserId,
            senderName = currentUserName,
            text = messageText,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            messageType = "text"
        )

        messagesRef.child(chatId).child("messages").child(messageId).setValue(message)
            .addOnSuccessListener {
                val updates = mapOf(
                    "lastMessage" to messageText,
                    "lastMessageTime" to System.currentTimeMillis()
                )
                messagesRef.child(chatId).updateChildren(updates)

                // Scroll to bottom after sending
                scrollToBottom()
            }
            .addOnFailureListener {
                showSnackbar("Failed to send message")
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
                initializeChat()
            }

            override fun onCancelled(error: DatabaseError) {
                currentUserName = "You"
                initializeChat()
            }
        })
    }

    private fun initializeChat() {
        binding.messageEditText.hint = "Message $partnerName..."
        loadProfilePictures()
        loadMessages()
        adjustInitialPadding()
    }

    // 🔥 FIXED: SINGLE loadProfilePictures function
    private fun loadProfilePictures() {
        // Load current user profile picture
        if (currentUserId.isNotEmpty()) {
            usersRef.child(currentUserId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // 🔥 PALITAN: profilePicture → profileImageUrl
                    currentUserProfilePic = snapshot.child("profileImageUrl").getValue(String::class.java)

                    currentUserName = snapshot.child("fullName").getValue(String::class.java)
                        ?: snapshot.child("username").getValue(String::class.java)
                                ?: "You"
                    updateAdapterProfilePictures()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        // Load partner profile picture
        if (partnerId.isNotEmpty()) {
            usersRef.child(partnerId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // 🔥 PALITAN: profilePicture → profileImageUrl
                    partnerProfilePic = snapshot.child("profileImageUrl").getValue(String::class.java)

                    val partnerFullName = snapshot.child("fullName").getValue(String::class.java)
                    val partnerUsername = snapshot.child("username").getValue(String::class.java)

                    // Update partner name if available
                    partnerFullName?.let { partnerName = it }

                    updateAdapterProfilePictures()

                    // Update toolbar profile picture and name
                    binding.partnerNameText.text = partnerName
                    partnerProfilePic?.let { url ->
                        Glide.with(requireContext())
                            .load(url)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .circleCrop()
                            .into(binding.partnerProfileImage)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun updateAdapterProfilePictures() {
        if (::messagesAdapter.isInitialized) {
            messagesAdapter.setProfilePictures(currentUserProfilePic, partnerProfilePic)
            messagesAdapter.notifyDataSetChanged()
        }
    }

    private fun loadMessages() {
        if (chatId.isEmpty()) return

        messagesListener = messagesRef.child(chatId).child("messages")
            .orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                @SuppressLint("NotifyDataSetChanged")
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isFragmentActive) return

                    val newMessages = mutableListOf<Message>()
                    for (msgSnap in snapshot.children) {
                        msgSnap.getValue(Message::class.java)?.let { newMessages.add(it) }
                    }

                    messagesList.clear()
                    messagesList.addAll(newMessages)
                    messagesAdapter.notifyDataSetChanged()

                    if (messagesList.isNotEmpty()) {
                        scrollToBottom()
                        updateUnreadMessages()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    if (isFragmentActive) {
                        showSnackbar("Failed to load messages")
                    }
                }
            })
    }

    private fun updateUnreadMessages() {
        if (!isFragmentActive || chatId.isEmpty()) return

        messagesList.forEach { message ->
            if (message.senderId != currentUserId && !message.isRead) {
                val messageId = message.messageId
                if (!messageId.isNullOrEmpty()) {
                    messagesRef.child(chatId).child("messages").child(messageId)
                        .child("isRead").setValue(true)
                }
            }
        }
    }

    private fun setupKeyboardListener() {
        keyboardLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (!isFragmentActive || _binding == null) return@OnGlobalLayoutListener

            try {
                val rect = Rect()
                binding.root.getWindowVisibleDisplayFrame(rect)

                val screenHeight = binding.root.rootView.height
                val keypadHeight = screenHeight - rect.bottom

            } catch (e: Exception) {
            }
        }

        binding.root.viewTreeObserver.addOnGlobalLayoutListener(keyboardLayoutListener)
    }

    // 🔥 FIXED: Complete scroll to bottom FAB
    private fun setupScrollToBottomFab() {
        binding.messagesRecyclerView.addOnScrollListener(object :
            androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (!isFragmentActive) return

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount
            }
        })
    }
    private fun scrollToBottom() {
        if (isFragmentActive && messagesList.isNotEmpty()) {
            binding.messagesRecyclerView.smoothScrollToPosition(messagesList.size - 1)
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

    private fun showAttachmentOptions() {
        showSnackbar("Attachment feature coming soon!")
    }

    private fun showSnackbar(message: String) {
        if (isFragmentActive) {
            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        isFragmentActive = false

        // Clean up Firebase listeners
        messagesListener?.let {
            messagesRef.removeEventListener(it)
        }

        // Clean up keyboard listener
        keyboardLayoutListener?.let {
            if (_binding != null) {
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
            }
        }

        // Clear binding
        _binding = null
    }

    companion object {
        fun newInstance(
            chatId: String = "",
            partnerId: String,
            partnerName: String,
            itemId: String = "",
            itemTitle: String = ""
        ): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle().apply {
                    putString("chatId", chatId)
                    putString("partnerId", partnerId)
                    putString("partnerName", partnerName)
                    putString("itemId", itemId)
                    putString("itemTitle", itemTitle)
                }
            }
        }
    }
}