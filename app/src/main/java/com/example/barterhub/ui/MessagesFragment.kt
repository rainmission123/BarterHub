package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barterhub.R
import com.example.barterhub.adapters.ConversationsAdapter
import com.example.barterhub.databinding.FragmentMessagesBinding
import com.example.barterhub.data.models.Conversation
import com.example.barterhub.data.models.Message
import com.example.barterhub.data.models.User
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

        // Auto-create test conversation if empty after 3s
        viewLifecycleOwner.lifecycleScope.launch {
            delay(3000)
        }
    }

    private fun setupRecyclerView() {
        adapter = ConversationsAdapter(conversationList) { convo ->
            val currentUserId = auth.currentUser?.uid ?: return@ConversationsAdapter
            val partnerId = convo.participants.keys.firstOrNull { it != currentUserId }
                ?: return@ConversationsAdapter
            val partnerName = convo.participantNames[partnerId] ?: "Chat Partner"
            val partnerProfilePic = convo.participantProfilePics[partnerId] // 🔥 GET PROFILE PIC

            val bundle = Bundle().apply {
                putString("chatId", convo.chatId)
                putString("partnerId", partnerId)
                putString("partnerName", partnerName)
                putString("partnerProfilePic", partnerProfilePic) // 🔥 PASS PROFILE PIC
            }
            findNavController().navigate(R.id.action_messages_to_chatFragment, bundle)
        }

        binding.messagesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.messagesRecyclerView.adapter = adapter
        updateEmptyState()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchConversations() {
        val userId = auth.currentUser?.uid ?: return
        binding.progressBar.visibility = View.VISIBLE

        conversationsListener?.let { database.child("chats").removeEventListener(it) }

        conversationsListener = database.child("chats").addValueEventListener(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                conversationList.clear()
                if (!snapshot.exists()) {
                    binding.progressBar.visibility = View.GONE
                    updateEmptyState()
                    return
                }

                for (chatSnap in snapshot.children) {
                    val chatId = chatSnap.key ?: continue
                    val participantsSnap = chatSnap.child("participants")
                    if (!participantsSnap.hasChild(userId)) continue

                    val participants = mutableMapOf<String, Boolean>()
                    participantsSnap.children.forEach { p ->
                        participants[p.key ?: ""] = p.getValue(Boolean::class.java) ?: true
                    }

                    val lastMessage = chatSnap.child("lastMessage").getValue(String::class.java) ?: "Start a conversation"
                    val lastMessageTime = chatSnap.child("lastMessageTime").getValue(Long::class.java) ?: System.currentTimeMillis()

                    val messages = mutableMapOf<String, Message>()
                    chatSnap.child("messages").children.forEach { msgSnap ->
                        msgSnap.getValue(Message::class.java)?.let { messages[msgSnap.key!!] = it }
                    }

                    // 🔥 UPDATED: Fetch both names AND profile pictures
                    fetchParticipantDetails(participants) { namesMap, profilePicsMap ->
                        val convo = Conversation(
                            chatId = chatId,
                            participants = participants,
                            participantNames = namesMap,
                            participantProfilePics = profilePicsMap, // 🔥 ADD PROFILE PICS
                            messages = messages,
                            lastMessage = lastMessage,
                            lastMessageTime = lastMessageTime,
                            unreadCount = calculateUnreadCount(messages, userId)
                        )

                        conversationList.removeAll { it.chatId == chatId }
                        conversationList.add(convo)
                        conversationList.sortByDescending { it.lastMessageTime }
                        adapter.notifyDataSetChanged()
                        updateEmptyState()
                    }
                }

                binding.progressBar.visibility = View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed to load conversations", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // 🔥 UPDATED: Fetch both names and profile pictures
    private fun fetchParticipantDetails(participants: Map<String, Boolean>, onComplete: (Map<String, String>, Map<String, String?>) -> Unit) {
        val namesMap = mutableMapOf<String, String>()
        val profilePicsMap = mutableMapOf<String, String?>() // 🔥 ADD PROFILE PICS MAP
        var completed = 0
        val total = participants.size
        if (total == 0) { onComplete(emptyMap(), emptyMap()); return }

        participants.keys.forEach { uid ->
            database.child("users").child(uid).addListenerForSingleValueEvent(object :
                ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java)

                    // Get name
                    val name = user?.username
                        ?: snapshot.child("username").getValue(String::class.java)
                        ?: user?.email?.substringBefore("@")
                        ?: snapshot.child("email").getValue(String::class.java)?.substringBefore("@")
                        ?: "Unknown User"
                    namesMap[uid] = name

                    // 🔥 GET PROFILE PICTURE
                    val profilePic = snapshot.child("profileImageUrl").getValue(String::class.java)
                        ?: snapshot.child("profilePicture").getValue(String::class.java)
                        ?: snapshot.child("profilePic").getValue(String::class.java)
                    profilePicsMap[uid] = profilePic

                    completed++
                    if (completed == total) onComplete(namesMap, profilePicsMap)
                }
                override fun onCancelled(error: DatabaseError) {
                    namesMap[uid] = "Unknown User"
                    profilePicsMap[uid] = null // 🔥 NULL IF ERROR
                    completed++
                    if (completed == total) onComplete(namesMap, profilePicsMap)
                }
            })
        }
    }

    private fun calculateUnreadCount(messages: Map<String, Message>, currentUserId: String): Int {
        return messages.values.count { it.senderId != currentUserId && !it.isRead }
    }

    private fun updateEmptyState() {
        binding.messagesRecyclerView.visibility = if (conversationList.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyState.visibility = if (conversationList.isEmpty()) View.VISIBLE else View.GONE
    }


    override fun onDestroyView() {
        super.onDestroyView()
        conversationsListener?.let { database.child("chats").removeEventListener(it) }
        _binding = null
    }
}