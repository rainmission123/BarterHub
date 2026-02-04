package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.barterhub.R
import com.example.barterhub.databinding.FragmentProfileBinding
import com.example.barterhub.ui.profile.AddFriendManager
import com.example.barterhub.ui.profile.NotificationManager
import com.example.barterhub.ui.profile.ProfileBadgeManager
import com.example.barterhub.ui.profile.ProfileDataLoader
import com.example.barterhub.ui.profile.ProfilePremiumManager
import com.example.barterhub.ui.profile.ProfileRatingManager
import com.example.barterhub.ui.profile.ProfileVerificationManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private lateinit var addFriendManager: AddFriendManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var tvUnreadNotifications: TextView
    private lateinit var btnGetPremium: Button
    private lateinit var tvPremiumStatus: TextView
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var likesCountText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var mainContent: LinearLayout
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    // UI Elements
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var tvUserPhone: TextView
    private lateinit var tvUserBio: TextView
    private lateinit var tvUserLocation: TextView
    private lateinit var ivProfileImage: ImageView
    private lateinit var tvHeaderUserName: TextView
    private lateinit var idVerificationStatus: MaterialTextView
    private lateinit var ratingBar: androidx.appcompat.widget.AppCompatRatingBar
    private lateinit var ratingText: TextView
    private lateinit var reviewsCountText: TextView
    private lateinit var tradesCountText: TextView
    private lateinit var itemsListedText: TextView
    private lateinit var successRateText: TextView
    private lateinit var memberSinceText: TextView

    // Managers
    private lateinit var badgeManager: ProfileBadgeManager
    private lateinit var premiumManager: ProfilePremiumManager
    private lateinit var ratingManager: ProfileRatingManager
    private lateinit var dataLoader: ProfileDataLoader
    private lateinit var verificationManager: ProfileVerificationManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        verificationManager.removeListener()
        notificationManager.removeListener()
        _binding = null
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize managers
        badgeManager = ProfileBadgeManager(this)
        premiumManager = ProfilePremiumManager(this)
        ratingManager = ProfileRatingManager(this)
        dataLoader = ProfileDataLoader(this)
        verificationManager = ProfileVerificationManager(this)
        notificationManager = NotificationManager(this)
        addFriendManager = AddFriendManager(this)

        initializeViews(view)
        setupBioExpansion()

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        val currentUserId = auth.currentUser?.uid

        setupRatingSystem()
        showLoading(true)

        // ✅ FIXED: Load user data with all stats
        dataLoader.loadUserData(
            tvHeaderUserName = tvHeaderUserName,
            tvUserName = tvUserName,
            tvUserEmail = tvUserEmail,
            tvUserPhone = tvUserPhone,
            tvUserBio = tvUserBio,
            tvUserLocation = tvUserLocation,
            memberSinceText = memberSinceText,
            ivProfileImage = ivProfileImage,
            tradesCountText = tradesCountText,
            successRateText = successRateText,
            onLoadingComplete = {
                showLoading(false)

                // ✅ ADD THIS: After loading, update stats with fallback methods
                currentUserId?.let { userId ->
                    // If trades count is still 0, try to load from separate method
                    if (tradesCountText.text == "0") {
                        dataLoader.loadTradesCount(userId) { tradesCount ->
                            tradesCountText.text = tradesCount.toString()
                            updateStatsColor(tradesCountText, tradesCount, true)
                        }
                    }

                    // If success rate is still 0%, try to calculate from separate method
                    if (successRateText.text == "0%") {
                        dataLoader.calculateSuccessRate(userId) { successRate ->
                            successRateText.text = "$successRate%"
                            updateStatsColor(successRateText, successRate, false)
                        }
                    }
                }
            }
        )

        dataLoader.loadUserLikes(currentUserId) { totalLikes ->
            updateLikesUI(totalLikes)
        }
        currentUserId?.let {
            notificationManager.setupUnreadNotificationsListener(tvUnreadNotifications)
        }

        // Setup other features
        badgeManager.loadUserBadges(binding.badgesLinearLayout)
        premiumManager.checkPremiumStatus(tvPremiumStatus, btnGetPremium)
        premiumManager.showPremiumBottomSheet(btnGetPremium)

        currentUserId?.let {
            ratingManager.setupRealTimeRatingListeners(
                userId = it,
                ratingBar = ratingBar,
                ratingText = ratingText,
                reviewsCountText = reviewsCountText,
                memberSinceText = memberSinceText
            )
            dataLoader.setupItemsListedListener(it, itemsListedText)

            // ✅ ADD THIS: Extra stats loading as backup
            loadAdditionalStats(it)
        }

        setupClickListeners(view)
        setupVerificationStatusListener()
    }

    // ✅ ADD THIS NEW METHOD
    private fun loadAdditionalStats(userId: String) {
        // Load trades count as backup
        dataLoader.loadTradesCount(userId) { tradesCount ->
            // Only update if current value is "0" or less than what we found
            val currentTrades = tradesCountText.text.toString().toIntOrNull() ?: 0
            if (tradesCount > currentTrades) {
                tradesCountText.text = tradesCount.toString()
                updateStatsColor(tradesCountText, tradesCount, true)
            }
        }

        // Calculate success rate as backup
        dataLoader.calculateSuccessRate(userId) { successRate ->
            // Only update if current value is "0%" or less than what we found
            val currentSuccessRate = successRateText.text.toString()
                .removeSuffix("%").toIntOrNull() ?: 0
            if (successRate > currentSuccessRate) {
                successRateText.text = "$successRate%"
                updateStatsColor(successRateText, successRate, false)
            }
        }
    }

    // ✅ ADD THIS HELPER METHOD
    private fun updateStatsColor(textView: TextView, value: Int, isTrades: Boolean) {
        if (!isAdded || context == null) return

        val color = when {
            isTrades -> {
                when {
                    value > 50 -> ContextCompat.getColor(requireContext(), R.color.success_green)
                    value > 20 -> ContextCompat.getColor(requireContext(), R.color.premium_gold)
                    value > 0 -> ContextCompat.getColor(requireContext(), R.color.amber_200)
                    else -> ContextCompat.getColor(requireContext(), R.color.gray)
                }
            }
            else -> { // Success rate
                when {
                    value >= 90 -> ContextCompat.getColor(requireContext(), R.color.success_green)
                    value >= 70 -> ContextCompat.getColor(requireContext(), R.color.premium_gold)
                    value > 0 -> ContextCompat.getColor(requireContext(), R.color.amber_200)
                    else -> ContextCompat.getColor(requireContext(), R.color.gray)
                }
            }
        }
        textView.setTextColor(color)
    }

    private fun markAllNotificationsAsRead() {
        val currentUserId = auth.currentUser?.uid ?: return

        database.child("notifications").child(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return

                    val updates = mutableMapOf<String, Any>()

                    for (notificationSnapshot in snapshot.children) {
                        val notificationId = notificationSnapshot.key ?: continue
                        val isRead = notificationSnapshot.child("read").getValue(Boolean::class.java) ?: false

                        // Mark as read if currently unread
                        if (!isRead) {
                            updates["$notificationId/read"] = true
                        }
                    }

                    if (updates.isNotEmpty()) {
                        database.child("notifications").child(currentUserId)
                            .updateChildren(updates)
                            .addOnSuccessListener {
                                Log.d("ProfileFragment", "✅ Marked ${updates.size} notifications as read")
                                // Optional: Show a quick toast
                                Toast.makeText(requireContext(),
                                    "Marked ${updates.size} notifications as read",
                                    Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Log.e("ProfileFragment", "❌ Failed to mark notifications: ${e.message}")
                            }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileFragment", "Failed to mark notifications as read: ${error.message}")
                }
            })
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<MaterialButton>(R.id.addFriendButton).setOnClickListener {
            // Diretso sa FindFriendsFragment
            try {
                findNavController().navigate(R.id.action_profileFragment_to_findFriendsFragment)
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Navigation error: ${e.message}")
                Toast.makeText(requireContext(), "Find Friends not available", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<MaterialButton>(R.id.topTradersButton).setOnClickListener {
            navigateToTopTradersFragment()
        }

        view.findViewById<MaterialButton>(R.id.notificationsLayout).setOnClickListener {
            navigateToNotificationsFragment()
            markAllNotificationsAsRead()
        }

        view.findViewById<MaterialButton>(R.id.tradeHistoryButton).setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_tradeHistoryFragment)
        }

        view.findViewById<MaterialButton>(R.id.favoritesButton).setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_favoritesFragment)
        }

        view.findViewById<MaterialButton>(R.id.myListingsButton).setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_myListingsFragment)
        }

        view.findViewById<MaterialButton>(R.id.btnBuyCoins).setOnClickListener {
            navigateToWalletFragment()
        }

        view.findViewById<MaterialButton>(R.id.editProfileButton).setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        idVerificationStatus.setOnClickListener {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                database.child("users").child(currentUser.uid).child("isIDVerified")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val statusValue = snapshot.value
                            val status = when (statusValue) {
                                is String -> statusValue
                                is Boolean -> if (statusValue) "verified" else "not_verified"
                                is Int -> when (statusValue) {
                                    1 -> "verified"
                                    0 -> "not_verified"
                                    else -> statusValue.toString()
                                }
                                else -> null
                            }

                            if (status == "rejected" || status == null) {
                                findNavController().navigate(R.id.action_profileFragment_to_uploadIdFragment)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("ProfileFragment", "Error checking verification status: ${error.message}")
                        }
                    })
            }
        }
    }

    private fun setupVerificationStatusListener() {
        verificationManager.setupVerificationStatusListener(idVerificationStatus)
    }

    private fun navigateToNotificationsFragment() {
        try {
            findNavController().navigate(R.id.action_profileFragment_to_notificationsFragment)
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Navigation to notifications failed: ${e.message}")
            Toast.makeText(requireContext(), "Notifications feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToTopTradersFragment() {
        try {
            findNavController().navigate(R.id.action_profileFragment_to_topTradersFragment)
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Navigation to top traders failed: ${e.message}")
            Toast.makeText(requireContext(), "Top Traders feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToWalletFragment() {
        try {
            findNavController().navigate(R.id.action_profileFragment_to_walletFragment)
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Navigation to wallet failed: ${e.message}")
            Toast.makeText(requireContext(), "Wallet feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeViews(view: View) {
        tvPremiumStatus = view.findViewById(R.id.tvPremiumStatus)
        tvPremiumStatus.visibility = View.GONE

        tvUserName = view.findViewById(R.id.nameTextView)
        tvUserEmail = view.findViewById(R.id.emailTextView)
        tvUserPhone = view.findViewById(R.id.phoneTextView)
        tvUserBio = view.findViewById(R.id.bioTextView)
        tvUserLocation = view.findViewById(R.id.locationTextView)
        ivProfileImage = view.findViewById(R.id.profileImage)
        tvHeaderUserName = view.findViewById(R.id.userNameText)
        idVerificationStatus = view.findViewById(R.id.idVerificationStatus)
        likesCountText = view.findViewById(R.id.likesCountText)
        btnGetPremium = view.findViewById(R.id.btnGetPremium)
        tvUnreadNotifications = view.findViewById(R.id.tvUnreadNotifications)

        ratingBar = view.findViewById(R.id.ratingBar)
        ratingText = view.findViewById(R.id.ratingText)
        reviewsCountText = view.findViewById(R.id.reviewsCountText)
        tradesCountText = view.findViewById(R.id.tradesCountText)
        itemsListedText = view.findViewById(R.id.itemsListedText)
        successRateText = view.findViewById(R.id.successRateText)
        memberSinceText = view.findViewById(R.id.memberSinceText)

        progressBar = view.findViewById(R.id.progressBar)
        mainContent = view.findViewById(R.id.mainContent)

        // Clear texts
        tvUserName.text = ""
        tvUserEmail.text = ""
        tvUserPhone.text = ""
        tvUserBio.text = ""
        tvUserLocation.text = ""
        tvHeaderUserName.text = ""
        ratingText.text = ""
        reviewsCountText.text = ""
        tradesCountText.text = ""
        itemsListedText.text = ""
        successRateText.text = ""
        memberSinceText.text = ""
        likesCountText.text = "0"
        tvUnreadNotifications.visibility = View.GONE
    }

    private fun setupBioExpansion() {
        val bioTextView = requireView().findViewById<TextView>(R.id.bioTextView)
        val viewAllTextView = requireView().findViewById<TextView>(R.id.viewAllTextView)

        var isExpanded = false
        viewAllTextView.setOnClickListener {
            isExpanded = !isExpanded
            if (isExpanded) {
                bioTextView.maxLines = Int.MAX_VALUE
                bioTextView.ellipsize = null
                viewAllTextView.text = "View Less"
            } else {
                bioTextView.maxLines = 2
                bioTextView.ellipsize = TextUtils.TruncateAt.END
                viewAllTextView.text = "View All"
            }
        }
    }

    private fun setupRatingSystem() {
        ratingManager.setupRatingSystem(ratingBar, ratingText, reviewsCountText)
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            progressBar.visibility = View.VISIBLE
            mainContent.visibility = View.GONE
        } else {
            progressBar.visibility = View.GONE
            mainContent.visibility = View.VISIBLE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateLikesUI(totalLikes: Int) {
        if (!isAdded || context == null) return

        Log.d("ProfileDebug", "📱 FINAL LIKES COUNT: $totalLikes")
        likesCountText.text = totalLikes.toString()

        if (totalLikes > 0) {
            likesCountText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_500))
        } else {
            likesCountText.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
        }
    }
}