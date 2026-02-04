package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.os.Bundle
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
import com.example.barterhub.databinding.FragmentMessagesBinding
import com.example.barterhub.data.models.Conversation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MessagesFragment : Fragment() {
    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ConversationsAdapter
    private val conversationList = mutableListOf<Conversation>()
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/").reference

    private var conversationsListener: ValueEventListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        fetchConversations()

        binding.fabLottie.setOnClickListener {
            val bundle = Bundle().apply {
                putString(
                    "BOT_INITIAL_MESSAGE",
                    "Hi! I'm your BarterHub bot. How can I help you?"
                )
            }
            findNavController()
                .navigate(R.id.action_messages_to_botChatFragment, bundle)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            delay(5000)
        }
    }

    private fun setupRecyclerView() {
        adapter = ConversationsAdapter(conversationList) { convo ->
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
            findNavController().navigate(R.id.action_messages_to_chatFragment, bundle)
        }

        adapter.setOnConversationLongClickListener { conversation, position ->
            showDeleteConversationDialog(conversation, position)
            true
        }

        binding.messagesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.messagesRecyclerView.adapter = adapter
        updateEmptyState()
    }

    private fun showDeleteConversationDialog(conversation: Conversation, position: Int) {
        val currentUserId = auth.currentUser?.uid ?: return
        val partnerId = conversation.participants.keys.firstOrNull { it != currentUserId }
        val partnerName = conversation.participantNames[partnerId] ?: "this conversation"

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Conversation")
            .setMessage("Are you sure you want to delete your conversation with $partnerName?")
            .setPositiveButton("Delete") { dialog, which ->
                deleteConversation(conversation.chatId, position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteConversation(chatId: String, position: Int) {
        val userId = auth.currentUser?.uid ?: return

        val chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId)

        chatRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    showSnackbar("Chat not found.")
                    return
                }

                val participants = snapshot.child("participants").children.mapNotNull { it.key }

                val updates = hashMapOf<String, Any?>()
                updates["/chats/$chatId"] = null // delete full chat

                participants.forEach { pid ->
                    updates["/user_chats/$pid/$chatId"] = null
                }

                database.updateChildren(updates)
                    .addOnSuccessListener {
                        if (position in conversationList.indices) {
                            conversationList.removeAt(position)
                            adapter.notifyItemRemoved(position)
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
        com.google.android.material.snackbar.Snackbar.make(binding.root, message,
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchConversations() {
        try {
            val userId = auth.currentUser?.uid ?: return
            if (_binding == null) return

            binding.progressBar.visibility = View.VISIBLE
            conversationList.clear()

            // Remove old listener
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

                                // ✅ Check if userId is part of the chatId
                                if (!chatId.contains(userId)) continue

                                val partnerId = extractPartnerIdFromChatId(chatId, userId)
                                if (partnerId != null) {
                                    val lastMessage = chatSnap.child("lastMessage").getValue(String::class.java)
                                        ?: getLastMessageFromMessages(chatSnap)
                                        ?: "New message"

                                    val lastMessageTime = chatSnap.child("lastMessageTime").getValue(Long::class.java)
                                        ?: getLastMessageTimeFromMessages(chatSnap)
                                        ?: System.currentTimeMillis()

                                    loadPartnerDetails(chatId, partnerId, lastMessage, lastMessageTime)
                                    chatsProcessed++
                                }
                            }

                            binding.progressBar.visibility = View.GONE
                            if (chatsProcessed == 0) updateEmptyState()

                        } catch (e: Exception) {
                            Log.e("DEBUG_MESSAGES", "Error in onDataChange: ${e.message}")
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
            Log.e("DEBUG_MESSAGES", "Error in fetch: ${e.message}")
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

        // Return the first valid partner ID found
        return potentialUserIds.firstOrNull()
    }

    private fun getLastMessageFromMessages(chatSnap: DataSnapshot): String? {
        val messagesSnap = chatSnap.child("messages")
        if (!messagesSnap.exists()) return null

        val lastMessage = messagesSnap.children.lastOrNull()?.child("text")?.getValue(String::class.java)
        Log.d("DEBUG_MESSAGES", "Got last message from messages: $lastMessage")
        return lastMessage
    }

    private fun getLastMessageTimeFromMessages(chatSnap: DataSnapshot): Long? {
        val messagesSnap = chatSnap.child("messages")
        if (!messagesSnap.exists()) return null

        val lastMessageTime = messagesSnap.children.lastOrNull()?.child("timestamp")?.getValue(Long::class.java)
        return lastMessageTime
    }


    private fun loadPartnerDetails(chatId: String, partnerId: String, lastMessage: String, lastMessageTime: Long) {
        val userId = auth.currentUser?.uid ?: return

        database.child("users").child(partnerId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return

                val partnerName = snapshot.child("username").getValue(String::class.java)
                    ?: snapshot.child("fullName").getValue(String::class.java)
                    ?: "Chat Partner"

                val partnerProfilePic = snapshot.child("profileImageUrl").getValue(String::class.java)
                    ?: snapshot.child("profilePicture").getValue(String::class.java)

                Log.d("DEBUG_MESSAGES", "✅ Partner Details: $partnerName ($partnerId)")

                val convo = Conversation(
                    chatId = chatId,
                    participants = mapOf(userId to true, partnerId to true),
                    participantNames = mapOf(userId to "You", partnerId to partnerName),
                    participantProfilePics = mapOf(partnerId to partnerProfilePic),
                    messages = mapOf(),
                    lastMessage = lastMessage,
                    lastMessageTime = lastMessageTime,
                    unreadCount = 0
                )

                // Remove if exists and add new
                conversationList.removeAll { it.chatId == chatId }
                conversationList.add(convo)
                conversationList.sortByDescending { it.lastMessageTime }
                adapter.notifyDataSetChanged()

                binding.progressBar.visibility = View.GONE
                updateEmptyState()

                Log.d("DEBUG_MESSAGES", "🎉 LOADED CHAT: $partnerName - $lastMessage")
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding == null) return

                Log.e("DEBUG_MESSAGES", "Failed to load partner $partnerId: ${error.message}")

                val convo = Conversation(
                    chatId = chatId,
                    participants = mapOf(userId to true, partnerId to true),
                    participantNames = mapOf(userId to "You", partnerId to "Chat Partner"),
                    participantProfilePics = mapOf(),
                    messages = mapOf(),
                    lastMessage = lastMessage,
                    lastMessageTime = lastMessageTime,
                    unreadCount = 0
                )

                conversationList.removeAll { it.chatId == chatId }
                conversationList.add(convo)
                conversationList.sortByDescending { it.lastMessageTime }
                adapter.notifyDataSetChanged()

                binding.progressBar.visibility = View.GONE
                updateEmptyState()
            }
        })
    }

    private fun updateEmptyState() {
        binding.messagesRecyclerView.visibility = if (conversationList.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyState.visibility = if (conversationList.isEmpty()) View.VISIBLE else View.GONE
    }


    override fun onDestroyView() {
        super.onDestroyView()
        val userId = auth.currentUser?.uid
        conversationsListener?.let {
            userId?.let { uid ->
            }
        }
        _binding = null
    }
}