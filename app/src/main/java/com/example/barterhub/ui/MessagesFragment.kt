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
    }

    private fun deleteConversation(chatId: String, position: Int) {
        val chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId)

        chatRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    showSnackbar("Chat not found.")
                    return
                }

                val participants = snapshot.child("participants").children.mapNotNull { it.key }

                val updates = hashMapOf<String, Any?>()
                updates["/chats/$chatId"] = null

                participants.forEach { pid ->
                    updates["/user_chats/$pid/$chatId"] = null
                }

                database.updateChildren(updates)
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
                        showSnackbar("Conversation permanently deleted")
                    }
                    .addOnFailureListener { e ->
                        showSnackbar("Failed to delete: ${e.message}")
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                showSnackbar("Failed: ${error.message}")
            }
        })
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

            conversationsListener?.let { database.child("chats").removeEventListener(it) }

            conversationsListener = database.child("chats")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            if (_binding == null) return

                            conversationList.clear()

                            if (!snapshot.exists()) {
                                binding.progressBar.visibility = View.GONE
                                updateEmptyState()
                                return
                            }

                            Log.d("DEBUG_MESSAGES", "Found ${snapshot.childrenCount} chats")
                            var chatsProcessed = 0

                            for (chatSnap in snapshot.children) {
                                if (_binding == null) break

                                val chatId = chatSnap.key ?: continue

                                if (chatId.startsWith("chat_")) continue
                                if (!chatId.contains(userId)) continue

                                val partnerId = extractPartnerIdFromChatId(chatId, userId) ?: continue

                                val lastMessage = chatSnap.child("lastMessage").getValue(String::class.java)
                                    ?: getLastMessageFromMessages(chatSnap)
                                    ?: "New message"

                                val lastMessageTime = chatSnap.child("lastMessageTime").getValue(Long::class.java)
                                    ?: getLastMessageTimeFromMessages(chatSnap)
                                    ?: System.currentTimeMillis()

                                val unreadCount = chatSnap.child("messages").children.count { messageSnap ->
                                    val read = messageSnap.child("read").getValue(Boolean::class.java) ?: true
                                    val receiverId = messageSnap.child("receiverId").getValue(String::class.java)
                                    !read && receiverId == userId
                                }

                                val isArchived = chatSnap.child("archivedBy")
                                    .child(userId)
                                    .getValue(Boolean::class.java) ?: false

                                loadPartnerDetails(
                                    chatId = chatId,
                                    partnerId = partnerId,
                                    lastMessage = lastMessage,
                                    lastMessageTime = lastMessageTime,
                                    unreadCount = unreadCount,
                                    isArchived = isArchived
                                )

                                chatsProcessed++
                            }

                            binding.progressBar.visibility = View.GONE
                            if (chatsProcessed == 0) {
                                applyFilters()
                                updateEmptyState()
                            }

                        } catch (e: Exception) {
                            Log.e("DEBUG_MESSAGES", "Error in onDataChange: ${e.message}", e)
                            if (_binding != null) binding.progressBar.visibility = View.GONE
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (_binding != null) {
                            binding.progressBar.visibility = View.GONE
                            Log.e("MESSAGES_FRAGMENT", "Error: ${error.message}")
                        }
                    }
                })

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

        database.child("public_users").child(partnerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return

                    val partnerName =
                        snapshot.child("fullName").getValue(String::class.java)
                            ?: snapshot.child("username").getValue(String::class.java)
                            ?: "Chat Partner"

                    val partnerProfilePic =
                        snapshot.child("profileImageUrl").getValue(String::class.java)
                            ?: snapshot.child("profileImage").getValue(String::class.java)
                            ?: snapshot.child("profilePicture").getValue(String::class.java)
                            ?: ""

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

                    // store archive flag temporarily in a side-path style via map if Conversation has no archived field
                    val convoWithArchiveTag = convo.copy(
                        lastMessage = if (isArchived) convo.lastMessage else convo.lastMessage
                    )

                    conversationList.add(convoWithArchiveTag)
                    conversationList.sortByDescending { it.lastMessageTime }

                    // keep archive state separately in tag map
                    archivedStateMap[chatId] = isArchived

                    applyFilters()
                    binding.progressBar.visibility = View.GONE
                    updateEmptyState()
                }

                override fun onCancelled(error: DatabaseError) {
                    if (_binding == null) return

                    val convo = Conversation(
                        chatId = chatId,
                        participants = mapOf(userId to true, partnerId to true),
                        participantNames = mapOf(userId to "You", partnerId to "Chat Partner"),
                        participantProfilePics = mapOf(),
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
            })
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
            database.child("chats").removeEventListener(it)
        }
        _binding = null
    }
}