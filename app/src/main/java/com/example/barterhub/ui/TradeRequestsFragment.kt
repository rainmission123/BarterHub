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

                val chatId = generateChatId(request.fromUser.userId, request.toUser.userId, request.targetItem.itemId)

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
            if (snapshot.exists()) {
                Log.d("ChatDebug", "✅ Chat already exists, adding system message only")
                addSystemMessageToChat(chatId, request)
            } else {
                Log.d("ChatDebug", "✅ Creating new chat with system message")
                saveChatInfo(chatId, request)
                addSystemMessageToChat(chatId, request)
            }

            navigateToChat(chatId, request)
        }.addOnFailureListener { e ->
            Log.e("ChatDebug", "❌ Error checking chat: ${e.message}")
        }
    }

    private fun addSystemMessageToChat(chatId: String, request: TradeRequest) {
        val messageId = FirebaseDatabase.getInstance().reference.push().key ?: return

        // ✅ FIX: Get actual data from the request
        val fromUsername = if (request.fromUser.username.isNotEmpty() &&
            request.fromUser.username != "Unknown User") {
            request.fromUser.username
        } else {
            "User" // Fallback
        }

        val toUsername = if (request.toUser.username.isNotEmpty() &&
            request.toUser.username != "Unknown User") {
            request.toUser.username
        } else {
            "User" // Fallback
        }

        val offeredItemTitle = if (request.offeredItem.title.isNotEmpty() &&
            request.offeredItem.title != "Unknown Item") {
            request.offeredItem.title
        } else {
            "Item" // Fallback
        }

        val targetItemTitle = if (request.targetItem.title.isNotEmpty() &&
            request.targetItem.title != "Unknown Item") {
            request.targetItem.title
        } else {
            "Item" // Fallback
        }

        // ✅ FIX: Clean image URLs
        val offeredItemImage = fixImageUrl(request.offeredItem.image)
        val targetItemImage = fixImageUrl(request.targetItem.image)

        val systemMessage = mapOf(
            "messageId" to messageId,
            "senderId" to "system",
            "senderName" to "System",
            "text" to "Trade Accepted! ✅",
            "timestamp" to System.currentTimeMillis(),
            "messageType" to "system_trade_accepted",
            "isSystemMessage" to true,
            "isRead" to false,
            "tradeDetails" to mapOf(
                // ✅ Trade Info
                "tradeRequestId" to request.requestId,
                "status" to "Accepted",

                // ✅ User 1 Info (FROM USER - nag-offer) - USE ACTUAL DATA
                "fromUserId" to request.fromUser.userId,
                "offeredBy" to fromUsername, // ✅ ACTUAL USERNAME
                "fromUserLocation" to if (request.fromUser.location.isNotEmpty())
                    request.fromUser.location else "Unknown Location",
                "fromUserRating" to request.fromUser.rating,
                "fromUserProfileImage" to fixImageUrl(request.fromUser.profileImage),

                // ✅ User 2 Info (TO USER - nag-accept) - USE ACTUAL DATA
                "toUserId" to request.toUser.userId,
                "acceptedBy" to toUsername, // ✅ ACTUAL USERNAME
                "toUserLocation" to if (request.toUser.location.isNotEmpty())
                    request.toUser.location else "Unknown Location",
                "toUserRating" to request.toUser.rating,
                "toUserProfileImage" to fixImageUrl(request.toUser.profileImage),

                // ✅ Offered Item Details (ITEM NA INIOFFER) - USE ACTUAL DATA
                "offeredItemId" to request.offeredItem.itemId,
                "offeredItemName" to offeredItemTitle, // ✅ ACTUAL ITEM NAME
                "offeredItemDescription" to if (request.offeredItem.description.isNotEmpty())
                    request.offeredItem.description else "No description",
                "offeredItemImage" to offeredItemImage,
                "offeredItemCategory" to if (request.offeredItem.category.isNotEmpty())
                    request.offeredItem.category else "Unknown",
                "offeredItemCondition" to if (request.offeredItem.condition.isNotEmpty())
                    request.offeredItem.condition else "Unknown",

                // ✅ Target Item Details (ITEM NA TARGET) - USE ACTUAL DATA
                "targetItemId" to request.targetItem.itemId,
                "targetItemName" to targetItemTitle, // ✅ ACTUAL ITEM NAME
                "targetItemDescription" to if (request.targetItem.description.isNotEmpty())
                    request.targetItem.description else "No description",
                "targetItemImage" to targetItemImage,
                "targetItemCategory" to if (request.targetItem.category.isNotEmpty())
                    request.targetItem.category else "Unknown",
                "targetItemCondition" to if (request.targetItem.condition.isNotEmpty())
                    request.targetItem.condition else "Unknown",

                // ✅ Additional Info
                "message" to if (request.message.isNotEmpty()) request.message else "No message",
                "additionalPhotos" to request.additionalPhotos.joinToString(","),
                "preferredMeetup" to request.preferredMeetup
            )
        )

        database.child("chats").child(chatId).child("messages").child(messageId)
            .setValue(systemMessage)
            .addOnSuccessListener {
                Log.d("FirebaseDebug", "✅ REAL DATA SAVED TO SYSTEM MESSAGE:")
                Log.d("FirebaseDebug", "   From User: $fromUsername")
                Log.d("FirebaseDebug", "   To User: $toUsername")
                Log.d("FirebaseDebug", "   Offered Item: $offeredItemTitle")
                Log.d("FirebaseDebug", "   Target Item: $targetItemTitle")
                Log.d("FirebaseDebug", "   Offered Image: $offeredItemImage")
                Log.d("FirebaseDebug", "   Target Image: $targetItemImage")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseDebug", "❌ Failed to save system message: ${e.message}")
            }
    }

    private fun generateChatId(userA: String, userB: String, itemId: String): String {
        val sortedUsers = listOf(userA, userB).sorted()
        return "${sortedUsers[0]}_${sortedUsers[1]}"
    }

    private fun saveChatInfo(chatId: String, request: TradeRequest) {
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
            "user1Name" to fromUsername, // ✅ ACTUAL NAME
            "user2Id" to request.toUser.userId,
            "user2Name" to toUsername, // ✅ ACTUAL NAME
            "itemId" to request.targetItem.itemId,
            "itemTitle" to targetItemTitle, // ✅ ACTUAL ITEM NAME
            "createdAt" to System.currentTimeMillis(),
            "lastMessage" to "Trade accepted! Discuss transaction details.",
            "lastMessageTime" to System.currentTimeMillis(),
            "tradeRequestId" to request.requestId
        )

        database.child("chats").child(chatId).setValue(chatData)
            .addOnSuccessListener {
                Log.d("ChatDebug", "✅ Chat info saved to chats/$chatId")
            }
            .addOnFailureListener { e ->
                Log.e("ChatDebug", "❌ Failed to save chat info: ${e.message}")
            }
    }

    private fun navigateToChat(chatId: String, request: TradeRequest) {
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

        val offeredItemTitle = if (request.offeredItem.title.isNotEmpty() &&
            request.offeredItem.title != "Unknown Item") {
            request.offeredItem.title
        } else {
            "Item"
        }

        val targetItemTitle = if (request.targetItem.title.isNotEmpty() &&
            request.targetItem.title != "Unknown Item") {
            request.targetItem.title
        } else {
            "Item"
        }

        Log.d("ChatDebug", "📍 NAVIGATING TO CHAT WITH REAL DATA:")
        Log.d("ChatDebug", "   Chat ID: $chatId")
        Log.d("ChatDebug", "   Partner: $fromUsername")
        Log.d("ChatDebug", "   Offered Item: $offeredItemTitle")
        Log.d("ChatDebug", "   Target Item: $targetItemTitle")

        val bundle = Bundle().apply {
            putString("chatId", chatId)
            putString("partnerId", request.fromUser.userId)
            putString("partnerName", fromUsername) // ✅ ACTUAL NAME
            putString("itemId", request.targetItem.itemId)
            putString("itemTitle", targetItemTitle) // ✅ ACTUAL ITEM NAME
            putBoolean("isTradeAccepted", true)
            putString("targetItemTitle", targetItemTitle) // ✅ ACTUAL ITEM NAME
            putString("offeredItemTitle", offeredItemTitle) // ✅ ACTUAL ITEM NAME
            putString("offeredBy", fromUsername) // ✅ ACTUAL NAME
            putString("acceptedBy", toUsername) // ✅ ACTUAL NAME
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

    // ✅ HELPER FUNCTION: Fix image URLs
    private fun fixImageUrl(rawUrl: String): String {
        return when {
            rawUrl.isEmpty() -> "" // Empty string if no image
            rawUrl.contains("via.placeholder.com") -> "" // Remove placeholder URLs
            rawUrl.startsWith("http") -> rawUrl // Valid URL
            else -> "" // Invalid format
        }.also {
            if (rawUrl.contains("via.placeholder.com")) {
                Log.d("ImageFix", "❌ Removed placeholder URL: $rawUrl")
            }
        }
    }

    // ✅ OPTIONAL: Fix all broken image URLs in Firebase (run once)
    private fun fixBrokenImageUrlsInFirebase() {
        Log.d("FirebaseFix", "🔧 Starting to fix broken image URLs in Firebase...")

        database.child("trade_requests").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var fixedCount = 0

                for (tradeSnap in snapshot.children) {
                    val tradeId = tradeSnap.key ?: continue

                    // Check and fix targetItem image
                    val targetImage = tradeSnap.child("targetItem").child("image").getValue(String::class.java)
                    if (targetImage?.contains("via.placeholder.com") == true) {
                        database.child("trade_requests").child(tradeId).child("targetItem").child("image")
                            .setValue("")
                            .addOnSuccessListener {
                                fixedCount++
                                Log.d("FirebaseFix", "✅ Fixed targetItem image for trade $tradeId")
                            }
                    }

                    // Check and fix offeredItem image
                    val offeredImage = tradeSnap.child("offeredItem").child("image").getValue(String::class.java)
                    if (offeredImage?.contains("via.placeholder.com") == true) {
                        database.child("trade_requests").child(tradeId).child("offeredItem").child("image")
                            .setValue("")
                            .addOnSuccessListener {
                                fixedCount++
                                Log.d("FirebaseFix", "✅ Fixed offeredItem image for trade $tradeId")
                            }
                    }

                    // Check and fix user profile images
                    val fromProfileImage = tradeSnap.child("fromUser").child("profileImage").getValue(String::class.java)
                    if (fromProfileImage?.contains("via.placeholder.com") == true) {
                        database.child("trade_requests").child(tradeId).child("fromUser").child("profileImage")
                            .setValue("")
                            .addOnSuccessListener {
                                fixedCount++
                                Log.d("FirebaseFix", "✅ Fixed fromUser profile image for trade $tradeId")
                            }
                    }

                    val toProfileImage = tradeSnap.child("toUser").child("profileImage").getValue(String::class.java)
                    if (toProfileImage?.contains("via.placeholder.com") == true) {
                        database.child("trade_requests").child(tradeId).child("toUser").child("profileImage")
                            .setValue("")
                            .addOnSuccessListener {
                                fixedCount++
                                Log.d("FirebaseFix", "✅ Fixed toUser profile image for trade $tradeId")
                            }
                    }
                }

                Log.d("FirebaseFix", "🎉 Fixed $fixedCount broken image URLs in total")

                // Also fix in users collection
                fixUserProfileImages()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseFix", "Failed to fix images: ${error.message}")
            }
        })
    }

    private fun fixUserProfileImages() {
        database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var fixedCount = 0

                for (userSnap in snapshot.children) {
                    val userId = userSnap.key ?: continue
                    val profileImage = userSnap.child("profileImageUrl").getValue(String::class.java)

                    if (profileImage?.contains("via.placeholder.com") == true) {
                        database.child("users").child(userId).child("profileImageUrl")
                            .setValue("")
                            .addOnSuccessListener {
                                fixedCount++
                                Log.d("FirebaseFix", "✅ Fixed user profile image for $userId")
                            }
                    }
                }

                Log.d("FirebaseFix", "🎉 Fixed $fixedCount user profile images")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseFix", "Failed to fix user images: ${error.message}")
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}