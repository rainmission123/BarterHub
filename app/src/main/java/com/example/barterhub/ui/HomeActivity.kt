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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
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
import androidx.core.content.edit
import com.google.android.material.snackbar.Snackbar

@Suppress("DEPRECATION")
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        // 👉 Make indicator follow drawer movement
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // Move indicator along with drawer
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

        // Optional swipe gesture sa indicator
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

        // The rest of your setup calls
        setupNavController()
        initializeAds()
        setupNavigationMenu()
        setupBackPressedHandler()
        setupSimpleWindowInsets()
        saveFcmToken()
        showSwipeTutorial()
    }


    // ✅ Recalculate drawer width after layout
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
                    true
                }
                R.id.nav_search -> {
                    if (navController.currentDestination?.id != R.id.nav_search)
                        navController.navigate(R.id.nav_search)
                    true
                }
                R.id.nav_add -> {
                    if (navController.currentDestination?.id != R.id.nav_add)
                        navController.navigate(R.id.nav_add)
                    true
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

        // 🔹 Load user data
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

        // 🔹 Handle the Dark Mode switch
        val darkModeItem = navigationView.menu.findItem(R.id.nav_dark_mode)
        val switchLayout = darkModeItem.actionView
        val switchDarkMode = switchLayout?.findViewById<android.widget.Switch>(R.id.switchDarkMode)

        if (switchDarkMode == null) {
            Log.e("DARK_MODE", "❌ switchDarkMode is NULL! Check if ID exists in XML")
        } else {
            Log.d("DARK_MODE", "✅ switchDarkMode connected successfully!")

            // Set the initial switch state from SharedPreferences
            val sharedPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            val isDarkMode = sharedPrefs.getBoolean("dark_mode", false)
            switchDarkMode.isChecked = isDarkMode

            // Listen for switch toggle
            switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
                AppCompatDelegate.setDefaultNightMode(
                    if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )
                sharedPrefs.edit { putBoolean("dark_mode", isChecked) }
                Toast.makeText(this, if (isChecked) "Dark mode enabled" else "Light mode enabled", Toast.LENGTH_SHORT).show()
            }
        }

        // 🔹 Handle the rest of the drawer items
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
            // Mas visible na Snackbar
            Snackbar.make(binding.root, "👈 SWIPE FROM LEFT EDGE TO OPEN MENU", Snackbar.LENGTH_LONG)
                .setBackgroundTint(resources.getColor(R.color.colorAccent))
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
}