package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.barterhub.R
import com.example.barterhub.databinding.ActivityHomeBinding
import com.google.android.gms.ads.MobileAds
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import de.hdodenhof.circleimageview.CircleImageView
import com.google.android.material.snackbar.Snackbar
import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.barterhub.ads.AppOpenAdManager
import androidx.appcompat.app.AppCompatDelegate
import com.example.barterhub.utils.BottomNavBadgeManager


@Suppress("DEPRECATION")
class HomeActivity : AppCompatActivity() {
    private lateinit var badgeManager: BottomNavBadgeManager
    private lateinit var binding: ActivityHomeBinding
    lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)

        val desiredMode = if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != desiredMode) {
            AppCompatDelegate.setDefaultNightMode(desiredMode)
        }

        super.onCreate(savedInstanceState)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        badgeManager = BottomNavBadgeManager(binding.bottomNavigation)
        badgeManager.listenForMessagesBadge()
        badgeManager.listenForProfileBadge()

        binding.bottomNavigation.itemIconTintList =
            ContextCompat.getColorStateList(this, R.color.bottom_nav_icon_selector)

        binding.bottomNavigation.itemTextColor =
            ContextCompat.getColorStateList(this, R.color.bottom_nav_text_selector)

        drawerLayout = binding.drawerLayout
        val navigationView = binding.navigationView
        val swipeIndicator = binding.swipeIndicator

        // 👉 Swipe indicator click - toggle drawer
        swipeIndicator.setOnClickListener {
            if (drawerLayout.isDrawerOpen(navigationView)) {
                drawerLayout.closeDrawer(navigationView)
            } else {
                drawerLayout.openDrawer(navigationView)
            }
        }

        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                val translationX = drawerView.width * slideOffset
                swipeIndicator.translationX = translationX
            }

            override fun onDrawerOpened(drawerView: View) {
                swipeIndicator.translationX = drawerView.width.toFloat()
            }

            override fun onDrawerClosed(drawerView: View) {
                swipeIndicator.translationX = 0f
            }

            override fun onDrawerStateChanged(newState: Int) {}
        })

        swipeIndicator.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private val SWIPE_THRESHOLD = 50
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> startX = event.x
                    MotionEvent.ACTION_UP -> {
                        val diffX = event.x - startX
                        if (diffX > SWIPE_THRESHOLD && !drawerLayout.isDrawerOpen(navigationView)) {
                            drawerLayout.openDrawer(navigationView)
                        } else if (diffX < -SWIPE_THRESHOLD && drawerLayout.isDrawerOpen(navigationView)) {
                            drawerLayout.closeDrawer(navigationView)
                        }
                    }
                }
                return false
            }
        })

        setupNavController()
        initializeAds()
        setupAppOpenAd()
        AppOpenAdManager.loadAd(application)
        setupNavigationMenu()
        setupBackPressedHandler()
        setupSimpleWindowInsets()
        saveFcmToken()
        showSwipeTutorial()
        requestNotificationPermission()
    }

    private fun setupAppOpenAd() {
        // Initialize ads first
        MobileAds.initialize(this) {}

        // Load the ad
        AppOpenAdManager.loadAd(application)

        // Show ad after a short delay to ensure activity is ready
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            AppOpenAdManager.showAdIfAvailable(this@HomeActivity)
        }, 500)
    }

    override fun onStart() {
        super.onStart()

        // Check if we need to show ad (only if not shown yet)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            // Non-logged in user - show ad
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                AppOpenAdManager.forceShowAd(this@HomeActivity)
            }, 300)
        } else {
            // Check if premium
            checkUserPremiumBeforeShowingAd(uid)
        }
    }

    private fun checkUserPremiumBeforeShowingAd(uid: String) {
        FirebaseDatabase.getInstance()
            .reference
            .child("users")
            .child(uid)
            .get()
            .addOnSuccessListener { snap ->
                val isPremium = snap.child("isPremium").getValue(Boolean::class.java) ?: false
                val expiry = snap.child("premiumExpiry").getValue(Long::class.java) ?: 0L
                val now = System.currentTimeMillis()

                val premiumActive = isPremium && expiry > now

                if (!premiumActive) {
                    // Non-premium user - show ad
                    android.os.Handler(Looper.getMainLooper()).postDelayed({
                        AppOpenAdManager.forceShowAd(this@HomeActivity)
                    }, 300)
                } else {
                    Log.d("HOME_ACTIVITY", "👑 Premium user - no ads")
                }
            }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setDrawerWidth()
    }

    private fun setDrawerWidth() {
        val screenWidth = resources.displayMetrics.widthPixels
        val drawerWidth = (screenWidth * 0.7).toInt() // 70% width

        val layoutParams = binding.navigationView.layoutParams as DrawerLayout.LayoutParams
        layoutParams.width = drawerWidth
        binding.navigationView.layoutParams = layoutParams

        Log.d("DRAWER_DEBUG", "Drawer width set to: $drawerWidth")
    }

    private fun setupSimpleWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navBarHeight
            }
            insets
        }
    }

    private fun setupNavController() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateBottomNavigationVisibility(destination)
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    if (navController.currentDestination?.id != R.id.nav_home)
                        navController.popBackStack(R.id.nav_home, false)
                    animateBottomNavSelection()
                    true
                }
                R.id.nav_search -> {
                    if (navController.currentDestination?.id != R.id.nav_search)
                        navController.navigate(R.id.nav_search)
                    true
                }

                R.id.nav_add -> {
                    navigateToAddItem()
                    false
                }

                R.id.nav_messages -> {
                    if (navController.currentDestination?.id != R.id.nav_messages)
                        navController.navigate(R.id.nav_messages)
                    true
                }
                R.id.nav_profile -> {
                    if (navController.currentDestination?.id != R.id.nav_profile)
                        navController.navigate(R.id.nav_profile)
                    true
                }
                else -> false
            }
        }
    }

    private fun animateBottomNavSelection() {
        binding.bottomNavigation.animate()
            .scaleX(1.02f)
            .scaleY(1.02f)
            .setDuration(120)
            .withEndAction {
                binding.bottomNavigation.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .start()
            }
            .start()
    }


    private fun navigateToAddItem() {
        try {
            navController.navigate(R.id.addPhotosFragment)
        } catch (e: Exception) {
            Toast.makeText(this, "Navigation error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBottomNavigationVisibility(destination: NavDestination) {
        when (destination.id) {
            R.id.nav_home, R.id.nav_search, R.id.nav_add,
            R.id.nav_messages, R.id.nav_profile -> showBottomNavigation()
            else -> hideBottomNavigation()
        }
    }

    private fun showBottomNavigation() {
        binding.bottomNavigation.visibility = View.VISIBLE
        binding.navHostFragment.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = 0
        }
    }

    private fun hideBottomNavigation() {
        binding.bottomNavigation.visibility = View.GONE
        binding.navHostFragment.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = 0
        }
    }

    private fun initializeAds() {
        MobileAds.initialize(this) {}
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun setupNavigationMenu() {
        val navigationView: NavigationView = binding.navigationView
        navigationView.setupWithNavController(navController)

        val headerView = navigationView.getHeaderView(0)
        val profileImageView = headerView.findViewById<CircleImageView>(R.id.userProfileSection)
        val usernameText = headerView.findViewById<TextView>(R.id.userName)
        val emailText = headerView.findViewById<TextView>(R.id.userEmail)
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null) {
            FirebaseDatabase.getInstance().getReference("users").child(uid)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (isDestroyed || isFinishing) return
                        val username = snapshot.child("username").getValue(String::class.java) ?: "No Name"
                        val email = FirebaseAuth.getInstance().currentUser?.email ?: "No Email"
                        val imageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)

                        usernameText.text = username
                        emailText.text = email

                        if (!imageUrl.isNullOrEmpty()) {
                            Glide.with(applicationContext)
                                .load(imageUrl)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                                .into(profileImageView)
                        } else {
                            profileImageView.setImageResource(R.drawable.ic_profile_placeholder)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (isDestroyed || isFinishing) return
                        Log.e("FIREBASE_ERROR", "Failed to load user data: ${error.message}")
                    }
                })
        }

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_profile -> {
                    if (navController.currentDestination?.id != R.id.nav_profile)
                        navController.navigate(R.id.nav_profile)
                }
                R.id.nav_messages -> {
                    if (navController.currentDestination?.id != R.id.nav_messages)
                        navController.navigate(R.id.nav_messages)
                }
                R.id.nav_my_listings -> {
                    if (navController.currentDestination?.id != R.id.nav_my_listings)
                        navController.navigate(R.id.nav_my_listings)
                }
                R.id.nav_trade_requests -> {
                    if (navController.currentDestination?.id != R.id.tradeRequestsFragment)
                        navController.navigate(R.id.tradeRequestsFragment)
                }
                R.id.nav_settings -> {
                    if (navController.currentDestination?.id != R.id.nav_settings)
                        navController.navigate(R.id.nav_settings)
                }
                R.id.nav_logout -> {
                    FirebaseAuth.getInstance().signOut()
                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
            }
            drawerLayout.closeDrawer(navigationView)
            true
        }
    }

    private fun showSwipeTutorial() {
        val sharedPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val isFirstTime = sharedPrefs.getBoolean("first_time_swipe", true)

        if (isFirstTime) {
            Snackbar.make(binding.root, "👈 SWIPE FROM LEFT EDGE TO OPEN MENU", Snackbar.LENGTH_LONG)
                .setBackgroundTint(resources.getColor(R.color.com_facebook_messenger_blue))
                .setTextColor(resources.getColor(android.R.color.white))
                .setAction("GOT IT") { }
                .setActionTextColor(resources.getColor(android.R.color.white))
                .show()

            sharedPrefs.edit { putBoolean("first_time_swipe", false) }
        }
    }

    private fun saveFcmToken() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                val userId = currentUser.uid
                FirebaseDatabase.getInstance()
                    .getReference("users/$userId/fcmToken")
                    .setValue(token)
                Log.d("FCM_DEBUG", "Saved FCM token for user $userId")
            }
        }
    }


    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun setupBackPressedHandler() {
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentDestination = navController.currentDestination?.id

                if (currentDestination != null &&
                    currentDestination !in listOf(
                        R.id.nav_home, R.id.nav_search, R.id.nav_add,
                        R.id.nav_messages, R.id.nav_profile
                    )
                ) {
                    when (currentDestination) {
                        R.id.nav_chat -> navController.navigate(R.id.nav_messages)
                        else -> navController.popBackStack()
                    }
                } else {
                    if (!navController.popBackStack()) finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        badgeManager.removeListeners()
    }
}