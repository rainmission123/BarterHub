package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.barterhub.R
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barterhub.adapters.TradeRequestsAdapter
import com.example.barterhub.databinding.FragmentTradeRequestsBinding
import com.example.barterhub.data.models.TradeRequest
import com.example.barterhub.data.models.TradeItem
import com.example.barterhub.data.models.TradeUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.example.barterhub.utils.ChatUtils

class TradeRequestsFragment : Fragment() {

    private var _binding: FragmentTradeRequestsBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private val requests = mutableListOf<TradeRequest>()
    private lateinit var adapter: TradeRequestsAdapter

    private var currentUserId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTradeRequestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""
        database = FirebaseDatabase.getInstance("https://barterhub-3c947-default-rtdb.firebaseio.com/").reference

        setupRecycler()
        setupSwipeRefresh()
        loadRequests()
        setupBackButton()

        // ✅ OPTIONAL: Fix broken image URLs (run once then comment out)
        // fixBrokenImageUrlsInFirebase()
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            if (!isAdded || _binding == null) {
                return@setOnRefreshListener
            }
            loadRequests()
        }

        binding.swipeRefreshLayout.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), com.example.barterhub.R.color.colorAccent),
            ContextCompat.getColor(requireContext(), com.example.barterhub.R.color.teal_700),
            ContextCompat.getColor(requireContext(), com.example.barterhub.R.color.colorPrimary)
        )
    }

    private fun setupRecycler() {
        adapter = TradeRequestsAdapter(requests, currentUserId) { request, action ->
            when (action) {
                "accept" -> acceptTradeRequest(request)
                else -> updateTradeRequestStatus(request.requestId, action)
            }
        }
        binding.recyclerTradeRequests.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerTradeRequests.adapter = adapter
    }

    private fun acceptTradeRequest(request: TradeRequest) {
        database.child("trade_requests").child(request.requestId).child("status").setValue("Accepted")
            .addOnSuccessListener {
                Log.d("TradeRequestsDebug", "✅ Trade accepted: ${request.requestId}")
                val chatId = ChatUtils.generateChatId(request.fromUser.userId, request.toUser.userId)
                Log.d("ChatDebug", "🎯 ACCEPT TRADE DEBUG:")
                Log.d("ChatDebug", "   From User: ${request.fromUser.userId}")
                Log.d("ChatDebug", "   To User: ${request.toUser.userId}")
                Log.d("ChatDebug", "   Item ID: ${request.targetItem.itemId}")
                Log.d("ChatDebug", "   Generated Chat ID: $chatId")

                checkAndCreateChat(chatId, request)
            }
            .addOnFailureListener { e ->
                Log.e("TradeRequestsDebug", "❌ Failed to accept trade: ${e.message}")
            }
    }

    private fun checkAndCreateChat(chatId: String, request: TradeRequest) {
        database.child("chats").child(chatId).get().addOnSuccessListener { snapshot ->
            fun continueAfterChatReady() {
                sendTradeAcceptedNotificationToRequester(chatId, request)
                navigateToChat(chatId, request)
            }

            if (snapshot.exists()) {
                Log.d("ChatDebug", "Chat already exists; trade_events Cloud Function will create accepted system message")
                continueAfterChatReady()
            } else {
                Log.d("ChatDebug", "Creating new chat metadata; trade_events Cloud Function will create accepted system message")
                saveChatInfo(chatId, request) {
                    continueAfterChatReady()
                }
            }
        }.addOnFailureListener { e ->
            Log.e("ChatDebug", "Error checking chat: ${e.message}")
        }
    }
    private fun saveChatInfo(chatId: String, request: TradeRequest, onSaved: (() -> Unit)? = null) {
        val fromUsername = if (request.fromUser.username.isNotEmpty() &&
            request.fromUser.username != "Unknown User") {
            request.fromUser.username
        } else {
            "User"
        }

        val toUsername = if (request.toUser.username.isNotEmpty() &&
            request.toUser.username != "Unknown User") {
            request.toUser.username
        } else {
            "User"
        }

        val targetItemTitle = if (request.targetItem.title.isNotEmpty() &&
            request.targetItem.title != "Unknown Item") {
            request.targetItem.title
        } else {
            "Item"
        }

        val chatData = mapOf(
            "chatId" to chatId,
            "participants" to mapOf(
                request.fromUser.userId to true,
                request.toUser.userId to true
            ),
            "user1Id" to request.fromUser.userId,
            "user1Name" to fromUsername,
            "user2Id" to request.toUser.userId,
            "user2Name" to toUsername,
            "itemId" to request.targetItem.itemId,
            "itemTitle" to targetItemTitle,
            "createdAt" to System.currentTimeMillis(),
            "lastMessage" to "Trade accepted! Discuss transaction details.",
            "lastMessageTime" to System.currentTimeMillis(),
            "tradeRequestId" to request.requestId
        )

        database.child("chats").child(chatId).setValue(chatData)
            .addOnSuccessListener {
                onSaved?.invoke()
            }
            .addOnSuccessListener {
                Log.d("ChatDebug", "✅ Chat info saved to chats/$chatId")
            }
            .addOnFailureListener { e ->
                Log.e("ChatDebug", "❌ Failed to save chat info: ${e.message}")
            }
    }

    private fun navigateToChat(chatId: String, request: TradeRequest) {
        val partnerId: String
        val partnerName: String

        if (currentUserId == request.fromUser.userId) {
            partnerId = request.toUser.userId
            partnerName = request.toUser.username.ifBlank { "User" }
        } else {
            partnerId = request.fromUser.userId
            partnerName = request.fromUser.username.ifBlank { "User" }
        }

        val targetItemTitle = if (
            request.targetItem.title.isNotEmpty() &&
            request.targetItem.title != "Unknown Item"
        ) {
            request.targetItem.title
        } else {
            "Item"
        }

        val offeredItemTitle = if (
            request.offeredItem.title.isNotEmpty() &&
            request.offeredItem.title != "Unknown Item"
        ) {
            request.offeredItem.title
        } else {
            "Item"
        }

        val offeredBy = request.fromUser.username.ifBlank { "User" }
        val acceptedBy = request.toUser.username.ifBlank { "User" }

        val bundle = Bundle().apply {
            putString("chatId", chatId)
            putString("partnerId", partnerId)
            putString("partnerName", partnerName)
            putString("itemId", request.targetItem.itemId)
            putString("itemTitle", targetItemTitle)
            putBoolean("isTradeAccepted", true)
            putString("targetItemTitle", targetItemTitle)
            putString("offeredItemTitle", offeredItemTitle)
            putString("offeredBy", offeredBy)
            putString("acceptedBy", acceptedBy)
            putInt("offeredItemPoints", 0)
            putInt("targetItemPoints", 0)
        }

        findNavController().navigate(R.id.action_tradeRequestsFragment_to_chatFragment, bundle)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadRequests() {
        if (!isAdded || _binding == null) {
            return
        }

        if (currentUserId.isEmpty()) return

        binding.swipeRefreshLayout.isRefreshing = true
        requests.clear()
        adapter.notifyDataSetChanged()

        database.child("trade_requests")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || _binding == null) {
                        return
                    }

                    requests.clear()

                    if (!snapshot.exists()) {
                        updateEmptyState()
                        binding.swipeRefreshLayout.isRefreshing = false
                        return
                    }

                    for (tradeSnap in snapshot.children) {
                        try {
                            val tradeMap = tradeSnap.value as? Map<*, *> ?: continue

                            val hiddenByMap = tradeMap["hiddenBy"] as? Map<*, *>
                            val isHidden = hiddenByMap?.get(currentUserId) as? Boolean ?: false
                            if (isHidden) continue

                            // Parse fromUser
                            val fromUserMap = tradeMap["fromUser"] as? Map<*, *> ?: continue

                            Log.d("FirebaseDebug", "🔥 RAW FIREBASE FROM USER DATA:")
                            Log.d("FirebaseDebug", "   Username: ${fromUserMap["username"]}")
                            Log.d("FirebaseDebug", "   Location: ${fromUserMap["location"]}")

                            val fromUser = TradeUser(
                                userId = fromUserMap["userId"] as? String ?: "",
                                username = fromUserMap["username"] as? String ?: "Unknown User",
                                profileImage = fixImageUrl(fromUserMap["profileImage"] as? String ?: ""),
                                location = fromUserMap["location"] as? String ?: "Manila, Philippines",
                                rating = (fromUserMap["rating"] as? Double) ?: 0.0
                            )

                            // Parse toUser
                            val toUserMap = tradeMap["toUser"] as? Map<*, *> ?: continue

                            Log.d("FirebaseDebug", "🔥 RAW FIREBASE TO USER DATA:")
                            Log.d("FirebaseDebug", "   Username: ${toUserMap["username"]}")

                            val toUser = TradeUser(
                                userId = toUserMap["userId"] as? String ?: "",
                                username = toUserMap["username"] as? String ?: "Unknown User",
                                profileImage = fixImageUrl(toUserMap["profileImage"] as? String ?: ""),
                                location = toUserMap["location"] as? String ?: "Manila, Philippines",
                                rating = (toUserMap["rating"] as? Double) ?: 0.0
                            )

                            // Parse targetItem
                            val targetItemMap = tradeMap["targetItem"] as? Map<*, *> ?: continue

                            Log.d("FirebaseDebug", "🔥 RAW FIREBASE TARGET ITEM:")
                            Log.d("FirebaseDebug", "   Title: ${targetItemMap["title"]}")

                            val targetItem = TradeItem(
                                itemId = targetItemMap["itemId"] as? String ?: "",
                                title = targetItemMap["title"] as? String ?: "Unknown Item",
                                description = targetItemMap["description"] as? String ?: "",
                                image = fixImageUrl(targetItemMap["image"] as? String ?: ""),
                                category = targetItemMap["category"] as? String ?: "Unknown",
                                condition = targetItemMap["condition"] as? String ?: "Unknown"
                            )

                            // Parse offeredItem
                            val offeredItemMap = tradeMap["offeredItem"] as? Map<*, *> ?: continue

                            Log.d("FirebaseDebug", "🔥 RAW FIREBASE OFFERED ITEM:")
                            Log.d("FirebaseDebug", "   Title: ${offeredItemMap["title"]}")

                            val offeredItem = TradeItem(
                                itemId = offeredItemMap["itemId"] as? String ?: "",
                                title = offeredItemMap["title"] as? String ?: "Unknown Item",
                                description = offeredItemMap["description"] as? String ?: "",
                                image = fixImageUrl(offeredItemMap["image"] as? String ?: ""),
                                category = offeredItemMap["category"] as? String ?: "Unknown",
                                condition = offeredItemMap["condition"] as? String ?: "Unknown"
                            )

                            // Parse other fields
                            val requestId = tradeSnap.key ?: ""
                            val status = tradeMap["status"] as? String ?: "Pending"
                            val message = tradeMap["message"] as? String ?: ""
                            val timestamp = tradeMap["createdAt"] as? Long ?: System.currentTimeMillis()

                            // Parse additional photos
                            val additionalPhotosString = tradeMap["additionalPhotos"] as? String ?: ""
                            val additionalPhotos = if (additionalPhotosString.isNotEmpty()) {
                                additionalPhotosString.split(",")
                            } else {
                                emptyList()
                            }

                            val preferredMeetup = tradeMap["preferredMeetup"] as? String ?: "Public Place"

                            if (toUser.userId == currentUserId || fromUser.userId == currentUserId) {
                                val request = TradeRequest(
                                    requestId = requestId,
                                    fromUser = fromUser,
                                    toUser = toUser,
                                    targetItem = targetItem,
                                    offeredItem = offeredItem,
                                    status = status,
                                    message = message,
                                    timestamp = timestamp,
                                    additionalPhotos = additionalPhotos,
                                    preferredMeetup = preferredMeetup
                                )

                                requests.add(request)

                                // ✅ LOG ACTUAL DATA
                                Log.d("TradeRequestsDebug", "✅ LOADED REAL DATA FOR TRADE REQUEST:")
                                Log.d("TradeRequestsDebug", "   Request ID: $requestId")
                                Log.d("TradeRequestsDebug", "   From User: ${fromUser.username}")
                                Log.d("TradeRequestsDebug", "   To User: ${toUser.username}")
                                Log.d("TradeRequestsDebug", "   Offered Item: ${offeredItem.title}")
                                Log.d("TradeRequestsDebug", "   Target Item: ${targetItem.title}")
                                Log.d("TradeRequestsDebug", "   Offered Image: ${offeredItem.image}")
                                Log.d("TradeRequestsDebug", "   Target Image: ${targetItem.image}")
                            }
                        } catch (e: Exception) {
                            Log.e("TradeRequests", "❌ Error parsing request: ${e.message}")
                        }
                    }

                    updateAdapterAndUI()
                }

                override fun onCancelled(error: DatabaseError) {
                    if (!isAdded || _binding == null) {
                        return
                    }

                    Log.e("TradeRequests", "❌ Failed to load: ${error.message}")
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.emptyState.visibility = View.VISIBLE
                }
            })
    }

    private fun sendTradeAcceptedNotificationToRequester(chatId: String, request: TradeRequest) {

        val acceptorId = currentUserId

        val requesterId = if (currentUserId == request.toUser.userId) {
            request.fromUser.userId
        } else {
            request.toUser.userId
        }

        if (requesterId.isBlank() || acceptorId.isBlank()) return

        val acceptorName = if (currentUserId == request.toUser.userId) {
            request.toUser.username
        } else {
            request.fromUser.username
        }.ifBlank { "Someone" }

        val event = hashMapOf<String, Any>(
            "type" to "trade_accepted",
            "toUserId" to requesterId,
            "fromUserId" to acceptorId,
            "fromUserName" to acceptorName,
            "chatId" to chatId,
            "partnerId" to acceptorId,
            "partnerName" to acceptorName,
            "requestId" to request.requestId,
            "tradeId" to request.requestId,
            "message" to "✅ $acceptorName accepted your trade request",
            "timestamp" to System.currentTimeMillis()
        )

        database.child("trade_events")
            .push()
            .setValue(event)
            .addOnSuccessListener {
                Log.d("TradeRequests", "✅ Trade accepted event sent")
            }
            .addOnFailureListener { e ->
                Log.e("TradeRequests", "❌ Failed trade accepted event: ${e.message}")
            }
    }
    private fun updateAdapterAndUI() {
        if (!isAdded || _binding == null) {
            return
        }

        requests.sortByDescending { it.timestamp }
        adapter.notifyDataSetChanged()
        updateEmptyState()
        binding.swipeRefreshLayout.isRefreshing = false

        Log.d("TradeRequestsDebug", "📊 Loaded ${requests.size} trade requests")

        // ✅ Log all loaded requests for debugging
        requests.forEachIndexed { index, request ->
            val type = if (request.toUser.userId == currentUserId) "RECEIVED" else "SENT"
            Log.d("TradeRequestsDebug", "$index. $type REQUEST:")
            Log.d("TradeRequestsDebug", "   From: ${request.fromUser.username}")
            Log.d("TradeRequestsDebug", "   To: ${request.toUser.username}")
            Log.d("TradeRequestsDebug", "   Offered: ${request.offeredItem.title}")
            Log.d("TradeRequestsDebug", "   Target: ${request.targetItem.title}")
        }
    }

    private fun updateEmptyState() {
        if (!isAdded || _binding == null) {
            return
        }

        val isEmpty = requests.isEmpty()
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerTradeRequests.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun updateTradeRequestStatus(requestId: String, status: String) {
        database.child("trade_requests").child(requestId).child("status").setValue(status)
            .addOnSuccessListener {
                Log.d("TradeRequestsDebug", "Updated trade $requestId to $status")
                loadRequests()
            }
            .addOnFailureListener { e ->
                Log.e("TradeRequestsDebug", "Failed to update trade: ${e.message}")
            }
    }

    private fun fixImageUrl(rawUrl: String): String {
        return when {
            rawUrl.isEmpty() -> ""
            rawUrl.contains("via.placeholder.com") -> ""
            rawUrl.startsWith("http") -> rawUrl // Valid URL
            else -> "" // Invalid format
        }.also {
            if (rawUrl.contains("via.placeholder.com")) {
                Log.d("ImageFix", "❌ Removed placeholder URL: $rawUrl")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
