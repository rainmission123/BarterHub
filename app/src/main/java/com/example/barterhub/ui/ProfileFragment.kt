    @file:Suppress("DEPRECATION")

    package com.example.barterhub.ui

    import android.annotation.SuppressLint
    import android.content.Intent
    import android.os.Bundle
    import android.os.Handler
    import android.os.Looper
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
    import com.facebook.CallbackManager
    import com.facebook.FacebookCallback
    import com.facebook.FacebookException
    import com.facebook.login.LoginManager
    import com.facebook.login.LoginResult
    import com.google.android.material.button.MaterialButton
    import com.google.android.material.textview.MaterialTextView
    import com.google.firebase.auth.FacebookAuthProvider
    import com.google.firebase.auth.FirebaseAuth
    import com.google.firebase.database.DataSnapshot
    import com.google.firebase.database.DatabaseError
    import com.google.firebase.database.DatabaseReference
    import com.google.firebase.database.FirebaseDatabase
    import com.google.firebase.database.ValueEventListener
    import android.app.Activity
    import androidx.activity.result.contract.ActivityResultContracts
    import com.google.android.gms.auth.api.signin.GoogleSignIn
    import com.google.android.gms.auth.api.signin.GoogleSignInClient
    import com.google.android.gms.auth.api.signin.GoogleSignInOptions
    import com.google.android.gms.common.api.ApiException
    import com.google.firebase.auth.GoogleAuthProvider

    class ProfileFragment : Fragment(R.layout.fragment_profile) {
        companion object {
            private const val TAG = "FB_LINK"
        }
        private fun debug(msg: String) {
            Log.d(TAG, msg)
        }

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
        private lateinit var statsRow: LinearLayout
        private lateinit var badgeManager: ProfileBadgeManager
        private lateinit var premiumManager: ProfilePremiumManager
        private lateinit var ratingManager: ProfileRatingManager
        private lateinit var dataLoader: ProfileDataLoader
        private lateinit var verificationManager: ProfileVerificationManager
        private lateinit var fbCallbackManager: CallbackManager
        private lateinit var tvFacebookStatus: TextView
        private lateinit var btnLinkFacebook: View
        private lateinit var btnLinkGoogle: View
        private lateinit var tvGoogleStatus: TextView
        private lateinit var googleSignInClient: GoogleSignInClient
        private lateinit var emailVerifiedStatus: TextView

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

            val userId = auth.currentUser?.uid

            verificationManager.removeListener()

            // remove notification listener
            notificationManager.removeListener()

            if (::premiumManager.isInitialized) {
                premiumManager.clear()
            }

            // remove rating listeners
            if (::ratingManager.isInitialized && userId != null) {
                ratingManager.clear(userId)
            }

            _binding = null
        }

        @SuppressLint("SetTextI18n")
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

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

            currentUserId?.let { userId ->
                database.child("users").child(userId).child("isPremium")
                    .addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val isPremium = snapshot.getValue(Boolean::class.java) ?: false
                            updatePremiumStatsVisibility(isPremium)
                        }

                        override fun onCancelled(error: DatabaseError) {
                            updatePremiumStatsVisibility(false)
                            Log.e("ProfileFragment", "Failed to load premium status: ${error.message}")
                        }
                    })
            }

            currentUserId?.let {
                ratingManager.setupRealTimeRatingListeners(
                    userId = it,
                    ratingBar = ratingBar,
                    ratingText = ratingText,
                    reviewsCountText = reviewsCountText,
                    memberSinceText = memberSinceText
                )
                dataLoader.setupItemsListedListener(it, itemsListedText)

                loadAdditionalStats(it)
            }

            setupClickListeners(view)
            setupVerificationStatusListener()
            setupFacebookLinking(view)
            setupGoogleLinking(view)
            updateLinkedAccountsUI()
            updateEmailVerificationUI()

            if (currentUserId != null) {
                setupRealTimeTradeStats(currentUserId)
            }
        }

        private val googleLinkLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

                if (result.resultCode != Activity.RESULT_OK) {
                    showLoading(false)
                    Toast.makeText(requireContext(), "Google link cancelled", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }

                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)

                try {
                    val account = task.getResult(ApiException::class.java)

                    val idToken = account.idToken

                    if (idToken.isNullOrEmpty()) {
                        showLoading(false)
                        Toast.makeText(requireContext(), "Google token missing", Toast.LENGTH_LONG).show()
                        return@registerForActivityResult
                    }

                    linkGoogleToCurrentUser(idToken)

                } catch (e: ApiException) {

                    showLoading(false)

                    Toast.makeText(requireContext(),
                        "Google error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        private fun setupGoogleLinking(view: View) {

            btnLinkGoogle = view.findViewById(R.id.btnLinkGoogle)
            tvGoogleStatus = view.findViewById(R.id.tvGoogleStatus)

            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

            googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)

            btnLinkGoogle.setOnClickListener {

                val user = auth.currentUser

                if (user == null) {
                    Toast.makeText(requireContext(),
                        "Login first before linking",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                showLoading(true)

                googleSignInClient.signOut().addOnCompleteListener {

                    googleLinkLauncher.launch(
                        googleSignInClient.signInIntent
                    )

                }
            }
        }

        private fun linkGoogleToCurrentUser(idToken: String) {

            val user = auth.currentUser

            if (user == null) {
                showLoading(false)
                return
            }

            val credential = GoogleAuthProvider.getCredential(idToken, null)

            user.linkWithCredential(credential)
                .addOnSuccessListener {

                    auth.currentUser?.reload()?.addOnCompleteListener {
                        showLoading(false)
                        updateLinkedAccountsUI()
                        updateEmailVerificationUI()


                        Toast.makeText(
                            requireContext(),
                            "Google linked successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    Toast.makeText(requireContext(), e.message, Toast.LENGTH_LONG).show()
                }
        }

        private fun setupFacebookLinking(view: View) {
            fbCallbackManager = CallbackManager.Factory.create()

            btnLinkFacebook = view.findViewById(R.id.btnLinkFacebook)
            tvFacebookStatus = view.findViewById(R.id.tvFacebookStatus)

            debug("setupFacebookLinking() ✅ btnLinkFacebook=${true} tvFacebookStatus=${true}")

            LoginManager.getInstance().registerCallback(
                fbCallbackManager,
                object : FacebookCallback<LoginResult> {

                    override fun onSuccess(result: LoginResult) {
                        debug("FB onSuccess ✅ userId=${result.accessToken.userId} token=${result.accessToken.token.take(10)}...")

                        val token = result.accessToken.token
                        if (token.isBlank()) {
                            debug("FB token missing ❌")
                            showLoading(false)
                            Toast.makeText(requireContext(), "Facebook token missing", Toast.LENGTH_LONG).show()
                            return
                        }
                        linkFacebookToCurrentUser(token)
                    }

                    override fun onCancel() {
                        debug("FB onCancel ⚠️")
                        showLoading(false)
                        Toast.makeText(requireContext(), "Facebook link cancelled", Toast.LENGTH_SHORT).show()
                    }

                    override fun onError(error: FacebookException) {
                        debug("FB onError ❌ ${error.message}")
                        Log.e(TAG, "Facebook SDK error", error)
                        showLoading(false)
                        Toast.makeText(requireContext(), "Facebook error: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            )

            btnLinkFacebook.setOnClickListener {
                debug("Link Facebook clicked ✅ currentUser=${auth.currentUser?.uid}")

                val user = auth.currentUser
                if (user == null) {
                    Toast.makeText(requireContext(), "Please login first using Email/Google.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                showLoading(true)

                LoginManager.getInstance().logOut()

                debug("Calling logInWithReadPermissions() ...")
                LoginManager.getInstance().logInWithReadPermissions(
                    this,
                    listOf("email", "public_profile")
                )
            }
        }

        private fun linkFacebookToCurrentUser(fbToken: String) {
            val user = auth.currentUser
            if (user == null) {
                debug("No logged in user to link ❌")
                showLoading(false)
                Toast.makeText(requireContext(), "No logged-in user to link.", Toast.LENGTH_LONG).show()
                return
            }

            debug("linkWithCredential() start... uid=${user.uid}")
            val credential = FacebookAuthProvider.getCredential(fbToken)

            user.linkWithCredential(credential)
                .addOnSuccessListener {
                    debug("linkWithCredential SUCCESS ✅ reloading user...")
                    auth.currentUser?.reload()?.addOnCompleteListener { reloadTask ->
                        debug("reload complete ✅ success=${reloadTask.isSuccessful}")
                        showLoading(false)
                        updateLinkedAccountsUI()
                        Toast.makeText(requireContext(), "✅ Facebook linked successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    debug("linkWithCredential FAILED ❌ ${e.message}")
                    Log.e(TAG, "linkWithCredential failed", e)
                    showLoading(false)

                    Toast.makeText(requireContext(), "❌ Link failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        @Deprecated("Deprecated in Java")
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            debug("onActivityResult ✅ requestCode=$requestCode resultCode=$resultCode data=$data")

            if (::fbCallbackManager.isInitialized) {
                val handled = fbCallbackManager.onActivityResult(requestCode, resultCode, data)
                debug("fbCallbackManager handled? = $handled")
            } else {
                debug("fbCallbackManager NOT initialized yet!")
            }
        }

        private fun updateLinkedAccountsUI() {

            val user = auth.currentUser ?: return
            val providers = user.providerData.map { it.providerId }.toSet()

            val isFacebookLinked = providers.contains("facebook.com")
            val isGoogleLinked = providers.contains("google.com")

            val green = ContextCompat.getColor(requireContext(), R.color.success_green)
            val gray = ContextCompat.getColor(requireContext(), R.color.gray)

            if (::tvFacebookStatus.isInitialized) {
                tvFacebookStatus.text = if (isFacebookLinked) "Linked" else "Not linked"
                tvFacebookStatus.setTextColor(if (isFacebookLinked) green else gray)
            }

            if (::tvGoogleStatus.isInitialized) {
                tvGoogleStatus.text = if (isGoogleLinked) "Linked" else "Not linked"
                tvGoogleStatus.setTextColor(if (isGoogleLinked) green else gray)
            }
        }

        private fun loadAdditionalStats(userId: String) {
            Log.d("ProfileFragment", "📊 Loading additional stats for user: $userId")

            dataLoader.loadTradesCount(userId) { tradesCount ->
                if (!isAdded) return@loadTradesCount

                Handler(Looper.getMainLooper()).post {
                    val currentTrades = tradesCountText.text.toString().toIntOrNull() ?: 0
                    if (tradesCount > currentTrades) {
                        tradesCountText.text = tradesCount.toString()
                        updateStatsColor(tradesCountText, tradesCount, true)
                        Log.d("ProfileFragment", "✅ Trades count updated: $tradesCount")
                    }
                }
            }

            dataLoader.calculateSuccessRate(userId) { successRate ->
                if (!isAdded) return@calculateSuccessRate

                Handler(Looper.getMainLooper()).post {
                    val currentSuccessRate = successRateText.text.toString()
                        .removeSuffix("%").toIntOrNull() ?: 0
                    if (successRate > currentSuccessRate) {
                        successRateText.text = "$successRate%"
                        updateStatsColor(successRateText, successRate, false)
                        Log.d("ProfileFragment", "✅ Success rate updated: $successRate%")
                    }
                }
            }
        }

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

        private fun setupRealTimeTradeStats(userId: String) {
            database.child("users").child(userId).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return

                    val tradesCompleted = snapshot.child("tradesCompleted").getValue(Int::class.java) ?: 0
                    tradesCountText.text = tradesCompleted.toString()
                    updateStatsColor(tradesCountText, tradesCompleted, true)

                    val successRate = snapshot.child("successRate").getValue(Int::class.java) ?:
                    if (tradesCompleted > 0) 100 else 0
                    successRateText.text = "$successRate%"
                    updateStatsColor(successRateText, successRate, false)

                    Log.d("ProfileFragment", "🔄 Real-time stats - Trades: $tradesCompleted, Success: $successRate%")
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileFragment", "❌ Real-time trade stats failed: ${error.message}")
                }
            })
        }

        private fun setupClickListeners(view: View) {
            view.findViewById<MaterialButton>(R.id.addFriendButton).setOnClickListener {
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

        private fun updateEmailVerificationUI() {
            val user = auth.currentUser ?: return
            val providers = user.providerData.map { it.providerId }.toSet()

            val hasPasswordProvider = providers.contains("password")
            val isVerified = hasPasswordProvider && user.isEmailVerified

            val green = ContextCompat.getColor(requireContext(), R.color.success_green)
            val gray = ContextCompat.getColor(requireContext(), R.color.gray)

            emailVerifiedStatus.text = if (isVerified) "Verified" else "Not verified"
            emailVerifiedStatus.setTextColor(if (isVerified) green else gray)

            val iconRes = if (isVerified) R.drawable.ic_check_circle else R.drawable.ic_info
            emailVerifiedStatus.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
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
            emailVerifiedStatus = view.findViewById(R.id.emailVerifiedStatus)

            ratingBar = view.findViewById(R.id.ratingBar)
            ratingText = view.findViewById(R.id.ratingText)
            reviewsCountText = view.findViewById(R.id.reviewsCountText)
            tradesCountText = view.findViewById(R.id.tradesCountText)
            itemsListedText = view.findViewById(R.id.itemsListedText)
            successRateText = view.findViewById(R.id.successRateText)
            memberSinceText = view.findViewById(R.id.memberSinceText)
            statsRow = view.findViewById(R.id.statsRow)

            progressBar = view.findViewById(R.id.progressBar)
            mainContent = view.findViewById(R.id.mainContent)

            statsRow.visibility = View.GONE

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

        private fun updatePremiumStatsVisibility(isPremium: Boolean) {
            if (!isAdded) return
            statsRow.visibility = if (isPremium) View.VISIBLE else View.GONE
        }

        private fun setupBioExpansion() {
            val bioTextView = binding.bioTextView
            val viewAllTextView = binding.viewAllTextView

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