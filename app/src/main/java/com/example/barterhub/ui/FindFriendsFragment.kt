package com.example.barterhub.ui

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.barterhub.R
import com.example.barterhub.adapters.FindFriendsAdapter
import com.example.barterhub.adapters.FriendsAdapter
import com.example.barterhub.data.models.FriendStatus
import com.example.barterhub.data.models.User
import com.example.barterhub.databinding.FragmentFindFriendsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.util.Log
import com.example.barterhub.ui.profile.AddFriendManager
import com.example.barterhub.utils.ChatUtils

class FindFriendsFragment : Fragment() {
    private lateinit var addFriendManager: AddFriendManager
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var _binding: FragmentFindFriendsBinding? = null
    private val binding: FragmentFindFriendsBinding
        get() = _binding ?: throw IllegalStateException("Fragment is not attached")
    private lateinit var findFriendsAdapter: FindFriendsAdapter
    private var friendsListener: ValueEventListener? = null
    private lateinit var friendsAdapter: FriendsAdapter
    private var currentFilter: FriendFilter = FriendFilter.ADD_FRIEND
    private val allUsers = mutableListOf<User>()
    private val filteredUsers = mutableListOf<User>()
    private var onlineStatusListener: ValueEventListener? = null

    enum class FriendFilter {
        ADD_FRIEND, // Users to add
        FRIENDS     // Current friends
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFindFriendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        addFriendManager = AddFriendManager(this)

        setupToolbar()
        debugDatabaseStructure()
        setupSearch()
        setupFilterButtons()
        setupAdapters()
        loadUsersFromFirebase()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Remove online status listener
        onlineStatusListener?.let {
            database.reference.child("status").removeEventListener(it)
        }

        friendsListener?.let {
            database.reference.child("friends").child(auth.currentUser?.uid ?: "").removeEventListener(it)
        }

        _binding = null
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Show/hide clear button
                binding.clearSearchButton.visibility =
                    if (s.isNullOrEmpty()) View.GONE else View.VISIBLE

                // Filter users based on search text
                filterUsers(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.clearSearchButton.setOnClickListener {
            binding.searchEditText.text?.clear()
        }
    }

    private fun setupFilterButtons() {
        // Set initial state
        updateFilterButtons()

        binding.addFriendButton.setOnClickListener {
            if (currentFilter != FriendFilter.ADD_FRIEND) {
                currentFilter = FriendFilter.ADD_FRIEND
                updateFilterButtons()
                filterUsers(binding.searchEditText.text.toString())
                updateAdapter()
            }
        }

        binding.friendsButton.setOnClickListener {
            if (currentFilter != FriendFilter.FRIENDS) {
                currentFilter = FriendFilter.FRIENDS
                updateFilterButtons()
                filterUsers(binding.searchEditText.text.toString())
                updateAdapter()
            }
        }
    }

    private fun updateFilterButtons() {
        val colorAccent = ContextCompat.getColor(requireContext(), R.color.com_facebook_messenger_blue)
        val colorWhite = ContextCompat.getColor(requireContext(), android.R.color.white)
        val transparent = ContextCompat.getColor(requireContext(), android.R.color.transparent)
        val typedArray = requireContext().theme.obtainStyledAttributes(
            intArrayOf(R.attr.postTextColor)
        )
        val postTextColor = typedArray.getColor(0, Color.BLACK)
        typedArray.recycle()

        when (currentFilter) {
            FriendFilter.ADD_FRIEND -> {
                binding.addFriendButton.apply {
                    setBackgroundColor(colorAccent)
                    setTextColor(colorWhite)
                    setStrokeColorResource(android.R.color.transparent)
                }
                binding.friendsButton.apply {
                    setBackgroundColor(transparent)
                    setTextColor(postTextColor)
                }
            }
            FriendFilter.FRIENDS -> {
                binding.friendsButton.apply {
                    setBackgroundColor(colorAccent)
                    setTextColor(colorWhite)
                    setStrokeColorResource(android.R.color.transparent)
                }
                binding.addFriendButton.apply {
                    setBackgroundColor(transparent)
                    setTextColor(postTextColor)
                }
            }
        }
    }

    private fun setupAdapters() {
        binding.friendsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.friendsRecyclerView.setHasFixedSize(true)

        findFriendsAdapter = FindFriendsAdapter(filteredUsers) { user, action ->
            when (action) {

                FindFriendsAdapter.Action.ADD_FRIEND -> {
                    addFriendManager.sendFriendRequest(user.userId)

                    val index = allUsers.indexOfFirst { it.userId == user.userId }
                    if (index != -1) {
                        allUsers[index] = allUsers[index].copy(
                            friendStatus = FriendStatus.REQUEST_SENT
                        )
                    }

                    filterUsers(binding.searchEditText.text.toString())
                }

                FindFriendsAdapter.Action.ACCEPT_REQUEST -> {
                    addFriendManager.acceptFriendRequest(user.userId)

                    val index = allUsers.indexOfFirst { it.userId == user.userId }
                    if (index != -1) {
                        allUsers[index] = allUsers[index].copy(
                            friendStatus = FriendStatus.FRIENDS
                        )
                    }

                    filterUsers(binding.searchEditText.text.toString())
                }

                FindFriendsAdapter.Action.CANCEL_REQUEST -> {
                    addFriendManager.cancelFriendRequest(user.userId)

                    val index = allUsers.indexOfFirst { it.userId == user.userId }
                    if (index != -1) {
                        allUsers[index] = allUsers[index].copy(
                            friendStatus = FriendStatus.NOT_FRIEND
                        )
                    }

                    filterUsers(binding.searchEditText.text.toString())
                }

                FindFriendsAdapter.Action.VIEW_PROFILE -> viewProfile(user.userId)
            }
        }

        friendsAdapter = FriendsAdapter(filteredUsers.toMutableList()) { user, action ->
            when (action) {
                FriendsAdapter.Action.REMOVE_FRIEND -> {
                    Log.d("FindFriends", "Remove friend: ${user.userId}")
                }
                FriendsAdapter.Action.MESSAGE -> openChat(user)
                FriendsAdapter.Action.VIEW_PROFILE -> viewProfile(user.userId)
            }
        }

        updateAdapter()
    }

    private fun openChat(user: User) {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = ChatUtils.generateChatId(currentUserId, user.userId)

        try {
            val action = FindFriendsFragmentDirections.actionFindFriendsFragmentToChatFragment(
                chatId = chatId,
                partnerId = user.userId,
                partnerName = user.getDisplayName(),
                itemId = "",
                itemTitle = "",
                targetItemTitle = "",
                offeredItemTitle = "",
                isTradeAccepted = false
            )
            findNavController().navigate(action)

        } catch (e: Exception) {
            Log.e("FindFriends", "Safe Args error: ${e.message}")

            val bundle = Bundle().apply {
                putString("chatId", chatId)
                putString("partnerId", user.userId)
                putString("partnerName", user.getDisplayName())
                putString("itemId", "")
                putString("itemTitle", "")
                putString("targetItemTitle", "")
                putString("offeredItemTitle", "")
                putBoolean("isTradeAccepted", false)
            }

            findNavController().navigate(R.id.nav_chat, bundle)
        }
    }
    private fun updateAdapter() {
        when (currentFilter) {
            FriendFilter.ADD_FRIEND -> {
                if (binding.friendsRecyclerView.adapter != findFriendsAdapter) {
                    binding.friendsRecyclerView.adapter = findFriendsAdapter
                }
            }
            FriendFilter.FRIENDS -> {
                binding.friendsRecyclerView.adapter = friendsAdapter
            }
        }
    }

    private fun loadUsersFromFirebase() {
        showLoading(true)
        val currentUserId = auth.currentUser?.uid ?: ""

        database.reference.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("FindFriends", "Snapshot children count: ${snapshot.childrenCount}")
                allUsers.clear()

                if (!snapshot.exists()) {
                    showEmptyState(true)
                    showLoading(false)
                    return
                }

                var loadedCount = 0
                var invalidCount = 0

                for (userSnapshot in snapshot.children) {
                    val userId = userSnapshot.key ?: continue

                    if (userId == currentUserId) {
                        continue
                    }

                    try {
                        if (userSnapshot.value !is Map<*, *>) {
                            continue
                        }

                        val rawData = userSnapshot.value as Map<*, *>

                        val username = rawData["username"] as? String
                        val email = rawData["email"] as? String
                        val fullName = rawData["fullName"] as? String

                        if (username.isNullOrEmpty() && email.isNullOrEmpty() && fullName.isNullOrEmpty()) {
                            Log.d("FindFriends", "Skipping invalid user (no name/email): $userId")
                            invalidCount++
                            continue
                        }

                        val user = userSnapshot.getValue(User::class.java)
                        if (user == null) {
                            Log.e("FindFriends", "Failed to parse user $userId")
                            continue
                        }

                        val userWithId = user.copy(
                            userId = userId,
                            friendStatus = determineFriendStatus(userId)
                        )

                        if (userWithId.address.isNullOrEmpty()) {

                        }

                        allUsers.add(userWithId)
                        loadedCount++

                        Log.d("FindFriends", "✅ Added: ${userWithId.getDisplayName()} | " +
                                "Address: '${userWithId.address}' | " +
                                "Rating: ${userWithId.rating}")

                    } catch (e: Exception) {
                        Log.e("FindFriends", "Error loading user $userId: ${e.message}")
                    }
                }

                Log.d("FindFriends", "✅ Loaded: $loadedCount valid users | Skipped: $invalidCount invalid users")

                startOnlineStatusListener()
                loadFriendRequests()
                showLoading(false)
                showEmptyState(allUsers.isEmpty())

                val message = if (allUsers.isEmpty()) "No users found" else "Found ${allUsers.size} users"
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }

            override fun onCancelled(error: DatabaseError) {
                showLoading(false)
                showEmptyState(true)
                Toast.makeText(requireContext(), "Failed to load users", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun startOnlineStatusListener() {
        val currentUserId = auth.currentUser?.uid ?: return

        onlineStatusListener?.let {
            database.reference.child("status").removeEventListener(it)
        }

        onlineStatusListener = database.reference.child("status").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (statusSnapshot in snapshot.children) {
                    val userId = statusSnapshot.key ?: continue

                    if (userId == currentUserId) continue

                    val state = statusSnapshot.child("state").getValue(String::class.java) ?: "offline"
                    val isOnline = state == "online"
                    val lastSeen = statusSnapshot.child("lastSeen").getValue(Long::class.java) ?: 0L

                    // Update user in our list
                    val index = allUsers.indexOfFirst { it.userId == userId }
                    if (index != -1) {
                        val currentUser = allUsers[index]
                        allUsers[index] = currentUser.copy(
                            isOnline = isOnline,
                            lastSeen = lastSeen
                        )

                        Log.d("FindFriends", "📱 Online status updated for ${currentUser.getDisplayName()}: isOnline=$isOnline, lastSeen=$lastSeen")

                        updateAdapterForUser(userId)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FindFriends", "Online status listener cancelled: ${error.message}")
            }
        })
    }

    private fun updateAdapterForUser(userId: String) {
        if (currentFilter == FriendFilter.FRIENDS) {
            friendsAdapter.updateOnlineStatus(userId, allUsers.find { it.userId == userId }?.isOnline ?: false)
        } else if (currentFilter == FriendFilter.ADD_FRIEND) {
            val index = filteredUsers.indexOfFirst { it.userId == userId }
            if (index != -1) findFriendsAdapter.notifyItemChanged(index)
        }
    }

    private fun showEmptyState(show: Boolean) {
        if (show) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.friendsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.friendsRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun determineFriendStatus(userId: String): FriendStatus {

        val existingUser = allUsers.find { it.userId == userId }
        if (existingUser != null && existingUser.friendStatus != FriendStatus.NOT_FRIEND) {
            return existingUser.friendStatus
        }

        return FriendStatus.NOT_FRIEND
    }

    private fun filterUsers(searchQuery: String) {
        filteredUsers.clear()

        val query = searchQuery.lowercase().trim()

        val filteredList = allUsers.filter { user ->
            val matchesFilter = when (currentFilter) {
                FriendFilter.ADD_FRIEND ->
                    user.friendStatus == FriendStatus.NOT_FRIEND ||
                            user.friendStatus == FriendStatus.REQUEST_SENT

                FriendFilter.FRIENDS ->
                    user.friendStatus == FriendStatus.FRIENDS
            }

            val matchesSearch =
                query.isEmpty() ||
                        user.username?.lowercase()?.contains(query) == true ||
                        user.fullName?.lowercase()?.contains(query) == true ||
                        user.email?.lowercase()?.contains(query) == true ||
                        user.address?.lowercase()?.contains(query) == true

            matchesFilter && matchesSearch
        }

        filteredUsers.addAll(filteredList)

        Log.d("FindFriends", "🔍 Filtered users: ${filteredUsers.size} (search: '$query')")

        when (currentFilter) {
            FriendFilter.ADD_FRIEND -> findFriendsAdapter.notifyDataSetChanged()
            FriendFilter.FRIENDS -> friendsAdapter.notifyDataSetChanged()
        }

        showEmptyState(filteredUsers.isEmpty())
    }


    private fun showLoading(show: Boolean) {
        binding.loadingProgressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.friendsRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
        binding.emptyStateLayout.visibility = View.GONE
    }

    private fun loadFriends() {
        val currentUserId = auth.currentUser?.uid ?: return

        // Remove old listener
        friendsListener?.let {
            database.reference.child("friends").child(currentUserId).removeEventListener(it)
        }

        friendsListener = database.reference
            .child("friends")
            .child(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || _binding == null) {
                        Log.d("FindFriends", "Fragment not attached, ignoring update")
                        return
                    }

                    try {
                        val friendIds = mutableSetOf<String>()

                        Log.d("FindFriends", "🔥 Firebase friends snapshot exists: ${snapshot.exists()}")

                        if (snapshot.exists()) {
                            for (friendSnapshot in snapshot.children) {
                                val friendId = friendSnapshot.key
                                val isFriend = friendSnapshot.getValue(Boolean::class.java) == true

                                Log.d("FindFriends", "   Friend: $friendId = $isFriend")

                                if (friendId != null && isFriend) {
                                    friendIds.add(friendId)
                                }
                            }
                        }

                        Log.d("FindFriends", "✅ Total friends found: ${friendIds.size}")
                        Log.d("FindFriends", "✅ Friend IDs: ${friendIds.joinToString()}")

                        // Update allUsers list with friend status
                        allUsers.forEachIndexed { index, user ->
                            if (friendIds.contains(user.userId)) {
                                allUsers[index] = user.copy(
                                    friendStatus = FriendStatus.FRIENDS
                                )
                                Log.d("FindFriends", "✅ Marked as friend: ${user.getDisplayName()} (${user.userId})")
                            } else {
                                if (allUsers[index].friendStatus == FriendStatus.FRIENDS) {
                                    allUsers[index] = user.copy(
                                        friendStatus = FriendStatus.NOT_FRIEND
                                    )
                                }
                            }
                        }
                        val friendsList = allUsers.filter { it.friendStatus == FriendStatus.FRIENDS }
                        friendsAdapter.updateFriends(friendsList)

                        // Update UI
                        if (isAdded && _binding != null) {
                            filterUsers(binding.searchEditText.text.toString())
                        }

                    } catch (e: Exception) {
                        Log.e("FindFriends", "❌ Error loading friends: ${e.message}", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FindFriends", "❌ Friends listener cancelled: ${error.message}")
                }
            })
    }

    private fun debugDatabaseStructure() {
        val currentUserId = auth.currentUser?.uid ?: return

        // Check friends at root level
        database.reference.child("friends").child(currentUserId).get()
            .addOnSuccessListener { snapshot ->
                Log.d("DEBUG", "=== FRIENDS AT ROOT LEVEL ===")
                if (!snapshot.exists()) {
                    Log.d("DEBUG", "❌ No friends found at root/friends/$currentUserId")
                } else {
                    for (friendSnapshot in snapshot.children) {
                        val friendId = friendSnapshot.key
                        val isFriend = friendSnapshot.value == true
                        Log.d("DEBUG", "Friend: $friendId = $isFriend")
                    }
                }
            }

        database.reference.child("users").child(currentUserId).child("friends").get()
            .addOnSuccessListener { snapshot ->
                Log.d("DEBUG", "=== FRIENDS UNDER USERS NODE ===")
                if (!snapshot.exists()) {
                    Log.d("DEBUG", "❌ No friends found at users/$currentUserId/friends")
                } else {
                    for (friendSnapshot in snapshot.children) {
                        val friendId = friendSnapshot.key
                        val isFriend = friendSnapshot.value == true
                        Log.d("DEBUG", "Friend: $friendId = $isFriend")
                    }
                }
            }
    }

    private fun loadFriendRequests() {
        val currentUserId = auth.currentUser?.uid ?: return

        val requestsRef = database.reference
            .child("userFriendRequests")
            .child(currentUserId)

        requestsRef.get()
            .addOnSuccessListener { snapshot ->
                val sentRequests = snapshot.child("sent")
                    .children
                    .mapNotNull { it.key }
                    .toSet()

                val receivedRequests = snapshot.child("received")
                    .children
                    .mapNotNull { it.key }
                    .toSet()

                allUsers.forEachIndexed { index, user ->
                    when {
                        sentRequests.contains(user.userId) -> {
                            allUsers[index] = user.copy(
                                friendStatus = FriendStatus.REQUEST_SENT
                            )
                        }

                        receivedRequests.contains(user.userId) -> {
                            allUsers[index] = user.copy(
                                friendStatus = FriendStatus.REQUEST_RECEIVED
                            )
                        }

                        allUsers[index].friendStatus != FriendStatus.FRIENDS -> {
                            allUsers[index] = user.copy(
                                friendStatus = FriendStatus.NOT_FRIEND
                            )
                        }
                    }
                }

                loadFriends()

                if (isAdded && _binding != null) {
                    filterUsers(binding.searchEditText.text.toString())
                }
            }
            .addOnFailureListener { e ->
                Log.e("FindFriends", "❌ Failed to load friend requests: ${e.message}", e)

                if (isAdded && _binding != null) {
                    filterUsers(binding.searchEditText.text.toString())
                }
            }
    }

    private fun viewProfile(userId: String) {
        val bundle = Bundle().apply {
            putString("ownerId", userId)
        }

        try {
            findNavController().navigate(R.id.ownerProfileFragment, bundle)
        } catch (e: Exception) {
            Log.e("FindFriends", "Navigation error: ${e.message}", e)
            showMessage("Failed to open profile")
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
