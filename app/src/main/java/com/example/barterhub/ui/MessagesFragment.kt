package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barterhub.R
import com.example.barterhub.adapters.ConversationsAdapter
import com.example.barterhub.data.models.Conversation
import com.example.barterhub.databinding.FragmentMessagesBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MessagesFragment : Fragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ConversationsAdapter
    private val conversationList = mutableListOf<Conversation>()
    private val filteredConversationList = mutableListOf<Conversation>()
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase
        .getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/")
        .reference
    private var conversationsListener: ValueEventListener? = null
    private var selectedFilter = FilterType.ALL
    private var searchQuery: String = ""
    private var isChatMenuOpen = false

    enum class FilterType {
        ALL,
        UNREAD,
        ARCHIVED
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupChips()
        setupSearch()
        fetchConversations()

        binding.botFabContainer.setOnClickListener {
            toggleChatMenu()
        }

        binding.tvChatBot.setOnClickListener {
            closeChatMenu()

            val bundle = Bundle().apply {
                putString(
                    "BOT_INITIAL_MESSAGE",
                    "Hi! I'm your BarterHub bot. How can I help you?"
                )
            }
            findNavController().navigate(R.id.action_messages_to_botChatFragment, bundle)
        }

        binding.tvChatSupport.setOnClickListener {
            closeChatMenu()
            showSnackbar("Chat Support is coming soon")
        }

        binding.tvFaq.setOnClickListener {
            closeChatMenu()
            showSnackbar("FAQ is coming soon")
        }

        binding.tvReportProblem.setOnClickListener {
            closeChatMenu()
            showSnackbar("Report Problem is coming soon")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            delay(5000)
        }
    }

    private fun toggleChatMenu() {
        isChatMenuOpen = !isChatMenuOpen

        if (isChatMenuOpen) {
            binding.tvChatBot.visibility = View.VISIBLE
            binding.tvChatSupport.visibility = View.VISIBLE
            binding.tvFaq.visibility = View.VISIBLE
            binding.tvReportProblem.visibility = View.VISIBLE
            binding.fabChatIcon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        } else {
            closeChatMenu()
        }
    }

    private fun closeChatMenu() {
        isChatMenuOpen = false
        binding.tvChatBot.visibility = View.GONE
        binding.tvChatSupport.visibility = View.GONE
        binding.tvFaq.visibility = View.GONE
        binding.tvReportProblem.visibility = View.GONE
        binding.fabChatIcon.setImageResource(R.drawable.ic_chat)
    }

    private fun setupRecyclerView() {
        adapter = ConversationsAdapter(filteredConversationList) { convo ->
            val currentUserId = auth.currentUser?.uid ?: return@ConversationsAdapter
            val partnerId = convo.participants.keys.firstOrNull { it != currentUserId }
                ?: return@ConversationsAdapter
            val partnerName = convo.participantNames[partnerId] ?: "Chat Partner"
            val partnerProfilePic = convo.participantProfilePics[partnerId]

            val bundle = Bundle().apply {
                putString("chatId", convo.chatId)
                putString("partnerId", partnerId)
                putString("partnerName", partnerName)
                putString("partnerProfilePic", partnerProfilePic)
            }

            clearUnreadCount(convo.chatId, currentUserId)

            findNavController().navigate(R.id.action_messages_to_chatFragment, bundle)
        }

        adapter.setOnConversationLongClickListener { conversation, position ->
            showConversationOptionsDialog(conversation, position)
            true
        }

        binding.messagesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.messagesRecyclerView.adapter = adapter
        updateEmptyState()
    }

    private fun setupChips() {
        binding.allChip.setOnClickListener {
            selectedFilter = FilterType.ALL
            updateChipStyles()
            applyFilters()
        }

        binding.unreadChip.setOnClickListener {
            selectedFilter = FilterType.UNREAD
            updateChipStyles()
            applyFilters()
        }

        binding.archivedChip.setOnClickListener {
            selectedFilter = FilterType.ARCHIVED
            updateChipStyles()
            applyFilters()
        }

        updateChipStyles()
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim().orEmpty()
                applyFilters()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
    }

    private fun updateChipStyles() {
        val activeText = "#00C8D4"
        val inactiveText = "#D3B6E6"

        val activeStroke = "#00C8D4"
        val inactiveStroke = "#6A0080"

        binding.allChip.setTextColor(android.graphics.Color.parseColor(if (selectedFilter == FilterType.ALL) activeText else inactiveText))
        binding.unreadChip.setTextColor(android.graphics.Color.parseColor(if (selectedFilter == FilterType.UNREAD) activeText else inactiveText))
        binding.archivedChip.setTextColor(android.graphics.Color.parseColor(if (selectedFilter == FilterType.ARCHIVED) activeText else inactiveText))

        binding.allChip.chipStrokeColor =
            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(if (selectedFilter == FilterType.ALL) activeStroke else inactiveStroke))
        binding.unreadChip.chipStrokeColor =
            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(if (selectedFilter == FilterType.UNREAD) activeStroke else inactiveStroke))
        binding.archivedChip.chipStrokeColor =
            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(if (selectedFilter == FilterType.ARCHIVED) activeStroke else inactiveStroke))
    }

    private fun showConversationOptionsDialog(conversation: Conversation, position: Int) {
        val currentUserId = auth.currentUser?.uid ?: return
        val partnerId = conversation.participants.keys.firstOrNull { it != currentUserId }
        val partnerName = conversation.participantNames[partnerId] ?: "this conversation"

        val isArchived = isConversationArchived(conversation)
        val options = mutableListOf<String>()

        options.add(if (isArchived) "Unarchive Conversation" else "Archive Conversation")
        options.add("Delete Conversation")

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(partnerName)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Archive Conversation" -> archiveConversation(conversation.chatId, true)
                    "Unarchive Conversation" -> archiveConversation(conversation.chatId, false)
                    "Delete Conversation" -> showDeleteConversationDialog(conversation, position)
                }
            }
            .show()
    }

    private fun showDeleteConversationDialog(conversation: Conversation, position: Int) {
        val currentUserId = auth.currentUser?.uid ?: return
        val partnerId = conversation.participants.keys.firstOrNull { it != currentUserId }
        val partnerName = conversation.participantNames[partnerId] ?: "this conversation"

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Conversation")
            .setMessage("Are you sure you want to delete your conversation with $partnerName?")
            .setPositiveButton("Delete") { _, _ ->
                deleteConversation(conversation.chatId, position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun archiveConversation(chatId: String, archive: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return

        database.child("chats")
            .child(chatId)
            .child("archivedBy")
            .child(currentUserId)
            .setValue(archive)
            .addOnSuccessListener {
                showSnackbar(if (archive) "Conversation archived" else "Conversation unarchived")
            }
            .addOnFailureListener { e ->
                showSnackbar("Failed: ${e.message}")
            }
    }

    private fun clearUnreadCount(chatId: String, currentUserId: String) {
        database.child("chats")
            .child(chatId)
            .child("unreadCount")
            .child(currentUserId)
            .setValue(0)

        database.child("user_inbox")
            .child(currentUserId)
            .child(chatId)
            .child("unreadCount")
            .setValue(0)
    }

    private fun deleteConversation(chatId: String, position: Int) {
        val currentUserId = auth.currentUser?.uid ?: return
        val conversation = filteredConversationList.getOrNull(position)
            ?: conversationList.firstOrNull { it.chatId == chatId }
        val partnerId = conversation?.participants?.keys?.firstOrNull { it != currentUserId }.orEmpty()
        val partnerName = conversation?.participantNames?.get(partnerId).orEmpty()
        val lastMessage = conversation?.lastMessage.orEmpty()
        val lastMessageTime = conversation?.lastMessageTime ?: System.currentTimeMillis()
        val deletedInboxEntry = mapOf(
            "chatId" to chatId,
            "partnerId" to partnerId,
            "partnerName" to partnerName,
            "lastMessage" to lastMessage,
            "lastMessageTime" to lastMessageTime,
            "unreadCount" to 0,
            "deleted" to true,
            "deletedAt" to System.currentTimeMillis()
        )

        database.child("user_inbox")
            .child(currentUserId)
            .child(chatId)
            .setValue(deletedInboxEntry)
            .addOnSuccessListener {
                if (position in filteredConversationList.indices) {
                    val removedChatId = filteredConversationList[position].chatId
                    filteredConversationList.removeAt(position)
                    conversationList.removeAll { it.chatId == removedChatId }
                    adapter.notifyDataSetChanged()
                } else {
                    adapter.notifyDataSetChanged()
                }
                updateEmptyState()
                showSnackbar("Conversation deleted")
            }
            .addOnFailureListener { e ->
                showSnackbar("Failed to delete: ${e.message}")
            }
    }

    private fun showSnackbar(message: String) {
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
            .show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchConversations() {
        try {
            val userId = auth.currentUser?.uid ?: return
            if (_binding == null) return

            binding.progressBar.visibility = View.VISIBLE
            conversationList.clear()
            filteredConversationList.clear()

            val inboxRef = database.child("user_inbox").child(userId)
            Log.d("MESSAGES_INBOX_PATH", "Listening to user_inbox/$userId")
            conversationsListener?.let { inboxRef.removeEventListener(it) }

            conversationsListener = inboxRef
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            if (_binding == null) return

                            conversationList.clear()
                            Log.d("MESSAGES_INBOX_COUNT", "exists=${snapshot.exists()} count=${snapshot.childrenCount}")

                            if (!snapshot.exists()) {
                                binding.progressBar.visibility = View.GONE
                                updateEmptyState()
                                return
                            }

                            Log.d("DEBUG_MESSAGES", "Found ${snapshot.childrenCount} inbox conversations")
                            val inboxItems = snapshot.children.toList()
                            var pendingChatReads = inboxItems.size
                            var conversationsQueued = 0

                            fun finishChatRead() {
                                pendingChatReads--
                                if (pendingChatReads <= 0 && conversationsQueued == 0) {
                                    binding.progressBar.visibility = View.GONE
                                    applyFilters()
                                    updateEmptyState()
                                }
                            }

                            for (inboxSnap in inboxItems) {
                                if (_binding == null) break

                                val inboxKey = inboxSnap.key.orEmpty()
                                val inboxChatId = inboxSnap.child("chatId").getValue(String::class.java)
                                val chatId = inboxKey.ifBlank { inboxChatId.orEmpty() }

                                Log.d(
                                    "MESSAGES_INBOX_CHILD",
                                    "key=$inboxKey chatId=$inboxChatId partnerId=${inboxSnap.child("partnerId").getValue(String::class.java)} " +
                                            "partnerName=${inboxSnap.child("partnerName").getValue(String::class.java)} " +
                                            "lastMessage=${inboxSnap.child("lastMessage").getValue(String::class.java)} " +
                                            "lastMessageTime=${inboxSnap.child("lastMessageTime").value} " +
                                            "unreadCount=${inboxSnap.child("unreadCount").value}"
                                )

                                if (chatId.isBlank()) {
                                    Log.d("MESSAGES_INBOX_CHILD", "Skipping blank chatId for key=$inboxKey")
                                    finishChatRead()
                                    continue
                                }

                                if (inboxSnap.child("deleted").getValue(Boolean::class.java) == true) {
                                    Log.d("MESSAGES_INBOX_CHILD", "Skipping deleted conversation $chatId")
                                    finishChatRead()
                                    continue
                                }

                                database.child("chats")
                                    .child(chatId)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(chatSnap: DataSnapshot) {
                                            if (_binding == null) return

                                            if (!chatSnap.exists()) {
                                                Log.d("MESSAGES_INBOX_CHILD", "Skipping $chatId because chat node does not exist")
                                                finishChatRead()
                                                return
                                            }

                                            if (!isCurrentUserParticipant(chatSnap, userId)) {
                                                Log.d(
                                                    "MESSAGES_INBOX_CHILD",
                                                    "Using inbox membership fallback for $chatId; no participant fields found on chat"
                                                )
                                            }

                                            val partnerId = firstNonBlank(
                                                inboxSnap.child("partnerId").getValue(String::class.java),
                                                resolvePartnerId(chatSnap, userId),
                                                extractPartnerIdFromChatId(chatId, userId)
                                            )

                                            if (partnerId.isNullOrBlank()) {
                                                Log.d("MESSAGES_INBOX_CHILD", "Skipping $chatId because partnerId is blank")
                                                finishChatRead()
                                                return
                                            }

                                            val lastMessage = firstNonBlank(
                                                inboxSnap.child("lastMessage").getValue(String::class.java),
                                                chatSnap.child("lastMessage").getValue(String::class.java),
                                                getLastMessageFromMessages(chatSnap)
                                            ) ?: "New message"

                                            val lastMessageTime =
                                                inboxSnap.child("lastMessageTime").asLong()
                                                    ?: chatSnap.child("lastMessageTime").asLong()
                                                    ?: getLastMessageTimeFromMessages(chatSnap)
                                                    ?: System.currentTimeMillis()

                                            val unreadCount =
                                                inboxSnap.child("unreadCount").asInt()
                                                    ?: chatSnap.child("unreadCount").child(userId).asInt()
                                                    ?: 0

                                            val isArchived =
                                                inboxSnap.child("archived").getValue(Boolean::class.java)
                                                    ?: chatSnap.child("archivedBy")
                                                        .child(userId)
                                                        .getValue(Boolean::class.java)
                                                    ?: false

                                            conversationsQueued++
                                            loadPartnerDetails(
                                                chatId = chatId,
                                                partnerId = partnerId,
                                                lastMessage = lastMessage,
                                                lastMessageTime = lastMessageTime,
                                                unreadCount = unreadCount,
                                                isArchived = isArchived
                                            )
                                            finishChatRead()
                                        }

                                        override fun onCancelled(error: DatabaseError) {
                                            Log.e("MESSAGES_FRAGMENT", "Chat read blocked for $chatId: ${error.message}")
                                            finishChatRead()
                                        }
                                    })
                            }

                        } catch (e: Exception) {
                            Log.e("DEBUG_MESSAGES", "Error in onDataChange: ${e.message}", e)
                            if (_binding != null) binding.progressBar.visibility = View.GONE
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (_binding != null) {
                            binding.progressBar.visibility = View.GONE
                            Log.e(
                                "MESSAGES_INBOX_ERROR",
                                "Inbox listener cancelled: ${error.message}",
                                error.toException()
                            )
                            Log.e("MESSAGES_FRAGMENT", "Error: ${error.message}")
                        }
                    }
                })

            Log.d("MESSAGES_INBOX_LISTENER", "ValueEventListener attached")

        } catch (e: Exception) {
            Log.e("DEBUG_MESSAGES", "Error in fetch: ${e.message}", e)
            if (_binding != null) {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun extractPartnerIdFromChatId(chatId: String, currentUserId: String): String? {
        Log.d("DEBUG_MESSAGES", "Extracting from chatId: $chatId")

        val parts = chatId.split('_')

        val potentialUserIds = parts.filter { part ->
            part.length >= 20 &&
                    part.length <= 35 &&
                    part != currentUserId &&
                    !part.startsWith("-O") &&
                    !part.startsWith("chat")
        }

        Log.d("DEBUG_MESSAGES", "Filtered partner IDs: $potentialUserIds")
        return potentialUserIds.firstOrNull()
    }

    private fun isCurrentUserParticipant(chatSnap: DataSnapshot, currentUserId: String): Boolean {
        if (chatSnap.child("participants").child(currentUserId).exists()) return true
        if (chatSnap.child("participantIds").child(currentUserId).exists()) return true

        return listOf(
            "user1Id",
            "user2Id",
            "user1",
            "user2",
            "buyerId",
            "sellerId",
            "ownerId",
            "requesterId"
        ).any { field ->
            chatSnap.child(field).getValue(String::class.java) == currentUserId
        }
    }

    private fun resolvePartnerId(chatSnap: DataSnapshot, currentUserId: String): String? {
        val participantKey = chatSnap.child("participants").children
            .mapNotNull { it.key }
            .firstOrNull { it != currentUserId }

        if (!participantKey.isNullOrBlank()) return participantKey

        val participantId = chatSnap.child("participantIds").children
            .mapNotNull { it.key }
            .firstOrNull { it != currentUserId }

        if (!participantId.isNullOrBlank()) return participantId

        return listOf(
            "user1Id",
            "user2Id",
            "user1",
            "user2",
            "buyerId",
            "sellerId",
            "ownerId",
            "requesterId"
        ).mapNotNull { field ->
            chatSnap.child(field).getValue(String::class.java)
        }.firstOrNull { it != currentUserId }
    }

    private fun DataSnapshot.asInt(): Int? {
        return getValue(Int::class.java) ?: getValue(Long::class.java)?.toInt()
    }

    private fun DataSnapshot.asLong(): Long? {
        return getValue(Long::class.java)
            ?: getValue(Int::class.java)?.toLong()
            ?: getValue(Double::class.java)?.toLong()
    }

    private fun getLastMessageFromMessages(chatSnap: DataSnapshot): String? {
        val messagesSnap = chatSnap.child("messages")
        if (!messagesSnap.exists()) return null

        return messagesSnap.children.lastOrNull()
            ?.child("text")
            ?.getValue(String::class.java)
    }

    private fun getLastMessageTimeFromMessages(chatSnap: DataSnapshot): Long? {
        val messagesSnap = chatSnap.child("messages")
        if (!messagesSnap.exists()) return null

        return messagesSnap.children.lastOrNull()
            ?.child("timestamp")
            ?.getValue(Long::class.java)
    }

    private fun loadPartnerDetails(
        chatId: String,
        partnerId: String,
        lastMessage: String,
        lastMessageTime: Long,
        unreadCount: Int,
        isArchived: Boolean
    ) {
        val userId = auth.currentUser?.uid ?: return

        database.child("users").child(partnerId).get()
            .addOnSuccessListener { userSnapshot ->
                database.child("public_users").child(partnerId).get()
                    .addOnSuccessListener { publicSnapshot ->
                        if (_binding == null) return@addOnSuccessListener

                        val partnerName = firstNonBlank(
                            publicSnapshot.child("fullName").getValue(String::class.java),
                            userSnapshot.child("fullName").getValue(String::class.java),
                            publicSnapshot.child("username").getValue(String::class.java),
                            userSnapshot.child("username").getValue(String::class.java)
                        ) ?: "Chat Partner"

                        val partnerProfilePic = firstNonBlank(
                            userSnapshot.child("profileImageUrl").getValue(String::class.java),
                            userSnapshot.child("profileImage").getValue(String::class.java),
                            publicSnapshot.child("profileImageUrl").getValue(String::class.java),
                            publicSnapshot.child("profileImage").getValue(String::class.java),
                            publicSnapshot.child("profilePicture").getValue(String::class.java)
                        ).orEmpty()

                        upsertConversation(
                            chatId = chatId,
                            userId = userId,
                            partnerId = partnerId,
                            partnerName = partnerName,
                            partnerProfilePic = partnerProfilePic,
                            lastMessage = lastMessage,
                            lastMessageTime = lastMessageTime,
                            unreadCount = unreadCount,
                            isArchived = isArchived
                        )
                    }
                    .addOnFailureListener {
                        if (_binding == null) return@addOnFailureListener

                        val partnerName = firstNonBlank(
                            userSnapshot.child("fullName").getValue(String::class.java),
                            userSnapshot.child("username").getValue(String::class.java)
                        ) ?: "Chat Partner"

                        val partnerProfilePic = firstNonBlank(
                            userSnapshot.child("profileImageUrl").getValue(String::class.java),
                            userSnapshot.child("profileImage").getValue(String::class.java)
                        ).orEmpty()

                        upsertConversation(
                            chatId = chatId,
                            userId = userId,
                            partnerId = partnerId,
                            partnerName = partnerName,
                            partnerProfilePic = partnerProfilePic,
                            lastMessage = lastMessage,
                            lastMessageTime = lastMessageTime,
                            unreadCount = unreadCount,
                            isArchived = isArchived
                        )
                    }
            }
            .addOnFailureListener {
                database.child("public_users").child(partnerId)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (_binding == null) return

                            val partnerName =
                                snapshot.child("fullName").getValue(String::class.java)
                                    ?: snapshot.child("username").getValue(String::class.java)
                                    ?: "Chat Partner"

                            val partnerProfilePic =
                                firstNonBlank(
                                    snapshot.child("profileImageUrl").getValue(String::class.java),
                                    snapshot.child("profileImage").getValue(String::class.java),
                                    snapshot.child("profilePicture").getValue(String::class.java)
                                ).orEmpty()

                            upsertConversation(
                                chatId = chatId,
                                userId = userId,
                                partnerId = partnerId,
                                partnerName = partnerName,
                                partnerProfilePic = partnerProfilePic,
                                lastMessage = lastMessage,
                                lastMessageTime = lastMessageTime,
                                unreadCount = unreadCount,
                                isArchived = isArchived
                            )
                        }

                        override fun onCancelled(error: DatabaseError) {
                            upsertConversation(
                                chatId = chatId,
                                userId = userId,
                                partnerId = partnerId,
                                partnerName = "Chat Partner",
                                partnerProfilePic = "",
                                lastMessage = lastMessage,
                                lastMessageTime = lastMessageTime,
                                unreadCount = unreadCount,
                                isArchived = isArchived
                            )
                        }
                    })
            }
    }

    private fun upsertConversation(
        chatId: String,
        userId: String,
        partnerId: String,
        partnerName: String,
        partnerProfilePic: String,
        lastMessage: String,
        lastMessageTime: Long,
        unreadCount: Int,
        isArchived: Boolean
    ) {
        if (_binding == null) return

        val convo = Conversation(
            chatId = chatId,
            participants = mapOf(userId to true, partnerId to true),
            participantNames = mapOf(userId to "You", partnerId to partnerName),
            participantProfilePics = mapOf(partnerId to partnerProfilePic),
            messages = mapOf(),
            lastMessage = lastMessage,
            lastMessageTime = lastMessageTime,
            unreadCount = unreadCount
        )

        conversationList.removeAll { it.chatId == chatId }
        conversationList.add(convo)
        conversationList.sortByDescending { it.lastMessageTime }
        archivedStateMap[chatId] = isArchived

        applyFilters()
        binding.progressBar.visibility = View.GONE
        updateEmptyState()
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private val archivedStateMap = mutableMapOf<String, Boolean>()

    @SuppressLint("NotifyDataSetChanged")
    private fun applyFilters() {
        val query = searchQuery.lowercase().trim()

        val result = conversationList.filter { convo ->
            val partnerName = convo.participantNames.values
                .firstOrNull { it != "You" }
                ?.lowercase()
                .orEmpty()

            val lastMessage = convo.lastMessage.orEmpty().lowercase()
            val matchesSearch = query.isBlank() ||
                    partnerName.contains(query) ||
                    lastMessage.contains(query)

            val isArchived = archivedStateMap[convo.chatId] == true
            val isUnread = convo.unreadCount > 0

            val matchesFilter = when (selectedFilter) {
                FilterType.ALL -> !isArchived
                FilterType.UNREAD -> !isArchived && isUnread
                FilterType.ARCHIVED -> isArchived
            }

            matchesSearch && matchesFilter
        }.sortedByDescending { it.lastMessageTime }

        filteredConversationList.clear()
        filteredConversationList.addAll(result)
        Log.d(
            "MESSAGES_ADAPTER_SUBMIT_COUNT",
            "conversationList=${conversationList.size} filtered=${filteredConversationList.size} filter=$selectedFilter query='$query'"
        )
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun isConversationArchived(conversation: Conversation): Boolean {
        return archivedStateMap[conversation.chatId] == true
    }

    private fun updateEmptyState() {
        val isEmpty = filteredConversationList.isEmpty()
        binding.messagesRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        conversationsListener?.let {
            auth.currentUser?.uid?.let { uid ->
                database.child("user_inbox").child(uid).removeEventListener(it)
            }
        }
        _binding = null
    }
}
