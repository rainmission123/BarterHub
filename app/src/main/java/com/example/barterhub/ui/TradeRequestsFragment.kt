package com.example.barterhub.ui

import android.R
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barterhub.adapters.TradeRequestsAdapter
import com.example.barterhub.databinding.FragmentTradeRequestsBinding
import com.example.barterhub.data.models.TradeRequest
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
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadRequests()
            binding.swipeRefreshLayout.isRefreshing = false
        }

        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.holo_blue_bright,
            R.color.holo_green_light,
            R.color.holo_orange_light,
            R.color.holo_red_light
        )
    }

    private fun setupRecycler() {
        adapter = TradeRequestsAdapter(requests, currentUserId) { request, action ->
            updateTradeRequestStatus(request.requestId, action)
        }
        binding.recyclerTradeRequests.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerTradeRequests.adapter = adapter
    }

    private fun loadRequests() {
        if (currentUserId.isEmpty()) return

        binding.swipeRefreshLayout.isRefreshing = true
        requests.clear()
        adapter.notifyDataSetChanged()

        database.child("trade_requests")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    requests.clear()

                    if (!snapshot.exists()) {
                        updateEmptyState()
                        binding.swipeRefreshLayout.isRefreshing = false
                        return
                    }

                    var loadedCount = 0
                    var totalValidRequests = 0

                    // First, count how many requests belong to current user (as owner OR requester)
                    for (reqSnap in snapshot.children) {
                        val reqMap = reqSnap.value as? Map<*, *> ?: continue
                        val ownerId = reqMap["owner"] as? String ?: ""
                        val requesterId = reqMap["requester"] as? String ?: ""

                        // 🔥 UPDATED FILTER: Show requests where current user is either OWNER or REQUESTER
                        if (ownerId == currentUserId || requesterId == currentUserId) {
                            totalValidRequests++
                        }
                    }

                    if (totalValidRequests == 0) {
                        updateEmptyState()
                        binding.swipeRefreshLayout.isRefreshing = false
                        return
                    }

                    // Now load only the requests that belong to current user
                    for (reqSnap in snapshot.children) {
                        val reqMap = reqSnap.value as? Map<*, *> ?: continue

                        val requestId = reqSnap.key ?: ""
                        val itemId = reqMap["itemId"] as? String ?: ""
                        val ownerId = reqMap["owner"] as? String ?: ""
                        val requesterId = reqMap["requester"] as? String ?: ""
                        val status = reqMap["status"] as? String ?: "Pending"
                        val date = reqMap["date"] as? String ?: ""

                        // 🔥 UPDATED FILTER: Show requests where current user is either OWNER or REQUESTER
                        if (ownerId != currentUserId && requesterId != currentUserId) {
                            continue // Skip requests that don't involve current user
                        }

                        Log.d("TradeRequestsDebug", "Loading request for current user. Request ID: $requestId")
                        Log.d("TradeRequestsDebug", "Owner: $ownerId, Requester: $requesterId, Current User: $currentUserId")

                        val request = TradeRequest(
                            requestId = requestId,
                            itemId = itemId,
                            owner = ownerId,
                            requester = requesterId,
                            status = status,
                            date = date,
                            requesterName = "",
                            requesterPhoto = "",
                            itemTitle = "",
                            itemImage = ""
                        )

                        loadRequesterAndItemDetails(request) { enhancedRequest ->
                            requests.add(enhancedRequest)
                            loadedCount++
                            if (loadedCount == totalValidRequests) {
                                updateAdapterAndUI()
                            }
                        }
                    }

                    // If no requests were found for current user after filtering
                    if (totalValidRequests == 0) {
                        updateEmptyState()
                        binding.swipeRefreshLayout.isRefreshing = false
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("TradeRequestsFragment", "Failed to load requests: ${error.message}")
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.emptyState.visibility = View.VISIBLE
                }
            })
    }

    private fun loadRequesterAndItemDetails(
        request: TradeRequest,
        onComplete: (TradeRequest) -> Unit
    ) {
        Log.d("TradeRequestsDebug", "Fetching details for requestId=${request.requestId}")

        val enhancedRequest = request

        // Load requester info (the person who sent the request)
        database.child("users").child(request.requester)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(requesterSnapshot: DataSnapshot) {
                    val requesterName = requesterSnapshot.child("fullName").getValue(String::class.java)
                        ?: requesterSnapshot.child("username").getValue(String::class.java)
                        ?: "Unknown User"
                    val requesterPhoto = requesterSnapshot.child("profileImageUrl").getValue(String::class.java) ?: ""

                    // Load owner info (the person who owns the item)
                    database.child("users").child(request.owner)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(ownerSnapshot: DataSnapshot) {
                                val ownerName = ownerSnapshot.child("fullName").getValue(String::class.java)
                                    ?: ownerSnapshot.child("username").getValue(String::class.java)
                                    ?: "Unknown Owner"
                                val ownerPhoto = ownerSnapshot.child("profileImageUrl").getValue(String::class.java) ?: ""

                                // Load item info
                                database.child("items").child(request.itemId)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(itemSnapshot: DataSnapshot) {
                                            val itemTitle = itemSnapshot.child("title").getValue(String::class.java) ?: "Unknown Item"
                                            val itemImage = itemSnapshot.child("imageUrl").getValue(String::class.java) ?: ""

                                            val finalRequest = enhancedRequest.copy(
                                                requesterName = requesterName,
                                                requesterPhoto = requesterPhoto,
                                                ownerName = ownerName,
                                                ownerPhoto = ownerPhoto,
                                                itemTitle = itemTitle,
                                                itemImage = itemImage
                                            )

                                            // 🔥 ADDED: Determine request type
                                            val requestType = if (request.owner == currentUserId) {
                                                "RECEIVED" // Current user is the item owner
                                            } else {
                                                "SENT" // Current user is the requester
                                            }

                                            Log.d("TradeRequestsDebug", "Request Type: $requestType - $requesterName wants '${itemTitle}' from $ownerName")
                                            onComplete(finalRequest)
                                        }

                                        override fun onCancelled(error: DatabaseError) {
                                            val finalRequest = enhancedRequest.copy(
                                                requesterName = requesterName,
                                                requesterPhoto = requesterPhoto,
                                                ownerName = ownerName,
                                                ownerPhoto = ownerPhoto,
                                                itemTitle = "Unknown Item",
                                                itemImage = ""
                                            )
                                            onComplete(finalRequest)
                                        }
                                    })
                            }

                            override fun onCancelled(error: DatabaseError) {
                                // If owner info fails, still try to load item info
                                database.child("items").child(request.itemId)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(itemSnapshot: DataSnapshot) {
                                            val itemTitle = itemSnapshot.child("title").getValue(String::class.java) ?: "Unknown Item"
                                            val itemImage = itemSnapshot.child("imageUrl").getValue(String::class.java) ?: ""

                                            val finalRequest = enhancedRequest.copy(
                                                requesterName = requesterName,
                                                requesterPhoto = requesterPhoto,
                                                ownerName = "Unknown Owner",
                                                ownerPhoto = "",
                                                itemTitle = itemTitle,
                                                itemImage = itemImage
                                            )
                                            onComplete(finalRequest)
                                        }

                                        override fun onCancelled(error: DatabaseError) {
                                            onComplete(enhancedRequest.copy(
                                                requesterName = requesterName,
                                                requesterPhoto = requesterPhoto,
                                                ownerName = "Unknown Owner",
                                                ownerPhoto = "",
                                                itemTitle = "Unknown Item",
                                                itemImage = ""
                                            ))
                                        }
                                    })
                            }
                        })
                }

                override fun onCancelled(error: DatabaseError) {
                    // If requester info fails, still try to load owner and item info
                    database.child("users").child(request.owner)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(ownerSnapshot: DataSnapshot) {
                                val ownerName = ownerSnapshot.child("fullName").getValue(String::class.java)
                                    ?: ownerSnapshot.child("username").getValue(String::class.java)
                                    ?: "Unknown Owner"

                                database.child("items").child(request.itemId)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(itemSnapshot: DataSnapshot) {
                                            val itemTitle = itemSnapshot.child("title").getValue(String::class.java) ?: "Unknown Item"
                                            val itemImage = itemSnapshot.child("imageUrl").getValue(String::class.java) ?: ""

                                            val finalRequest = enhancedRequest.copy(
                                                requesterName = "Unknown User",
                                                requesterPhoto = "",
                                                ownerName = ownerName,
                                                ownerPhoto = "",
                                                itemTitle = itemTitle,
                                                itemImage = itemImage
                                            )
                                            onComplete(finalRequest)
                                        }

                                        override fun onCancelled(error: DatabaseError) {
                                            onComplete(enhancedRequest.copy(
                                                requesterName = "Unknown User",
                                                requesterPhoto = "",
                                                ownerName = ownerName,
                                                ownerPhoto = "",
                                                itemTitle = "Unknown Item",
                                                itemImage = ""
                                            ))
                                        }
                                    })
                            }

                            override fun onCancelled(error: DatabaseError) {
                                onComplete(enhancedRequest.copy(
                                    requesterName = "Unknown User",
                                    requesterPhoto = "",
                                    ownerName = "Unknown Owner",
                                    ownerPhoto = "",
                                    itemTitle = "Unknown Item",
                                    itemImage = ""
                                ))
                            }
                        })
                }
            })
    }

    private fun updateAdapterAndUI() {
        // Sort by date (newest first)
        requests.sortByDescending { it.date }
        adapter.notifyDataSetChanged()
        updateEmptyState()
        binding.swipeRefreshLayout.isRefreshing = false

        Log.d("TradeRequestsDebug", "Loaded ${requests.size} requests for current user")

        // Log each request type
        requests.forEach { request ->
            val type = if (request.owner == currentUserId) "RECEIVED" else "SENT"
            Log.d("TradeRequestsDebug", "- $type: ${request.itemTitle} (Status: ${request.status})")
        }
    }

    private fun updateEmptyState() {
        val isEmpty = requests.isEmpty()
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerTradeRequests.visibility = if (isEmpty) View.GONE else View.VISIBLE

        if (isEmpty) {
            Log.d("TradeRequestsDebug", "No trade requests found for current user")
        }
    }

    private fun updateTradeRequestStatus(requestId: String, status: String) {
        database.child("trade_requests").child(requestId).child("status").setValue(status)
            .addOnSuccessListener {
                Log.d("TradeRequestsDebug", "Updated request $requestId to $status")
                // Refresh the list to show updated status
                loadRequests()
            }
            .addOnFailureListener { e ->
                Log.e("TradeRequestsDebug", "Failed to update request: ${e.message}")
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}