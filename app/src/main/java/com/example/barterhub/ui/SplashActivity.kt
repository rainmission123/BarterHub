package com.example.barterhub.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.barterhub.R
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private val splashDelay = 2300L
    private val loadingInterval = 320L

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvLoading: TextView
    private lateinit var ivSplashLogo: ImageView
    private lateinit var tvAppName: TextView
    private lateinit var tvTagline: TextView

    private var loadingStep = 0
    private var logoPulseAnimator: ObjectAnimator? = null

    private val loadingRunnable = object : Runnable {
        override fun run() {
            loadingStep = (loadingStep + 1) % 4
            tvLoading.text = when (loadingStep) {
                0 -> "Loading"
                1 -> "Loading."
                2 -> "Loading.."
                else -> "Loading..."
            }
            handler.postDelayed(this, loadingInterval)
        }
    }

    private val navigateRunnable = Runnable {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val nextIntent = if (currentUser != null) {
            Intent(this, HomeActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }

        startActivity(nextIntent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)

        val desiredMode = if (isDark) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }

        if (AppCompatDelegate.getDefaultNightMode() != desiredMode) {
            AppCompatDelegate.setDefaultNightMode(desiredMode)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        ivSplashLogo = findViewById(R.id.ivSplashLogo)
        tvAppName = findViewById(R.id.tvAppName)
        tvTagline = findViewById(R.id.tvTagline)
        tvLoading = findViewById(R.id.tvLoading)

        startEntryAnimations()
        startLogoPulse()

        handler.post(loadingRunnable)
        handler.postDelayed(navigateRunnable, splashDelay)
    }

    private fun startEntryAnimations() {
        ivSplashLogo.alpha = 0f
        ivSplashLogo.scaleX = 0.90f
        ivSplashLogo.scaleY = 0.90f
        ivSplashLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(850)
            .setInterpolator(DecelerateInterpolator())
            .start()

        tvAppName.alpha = 0f
        tvAppName.translationY = 18f
        tvAppName.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(120)
            .setDuration(650)
            .setInterpolator(DecelerateInterpolator())
            .start()

        tvTagline.alpha = 0f
        tvTagline.translationY = 14f
        tvTagline.animate()
            .alpha(0.82f)
            .translationY(0f)
            .setStartDelay(220)
            .setDuration(650)
            .setInterpolator(DecelerateInterpolator())
            .start()

        tvLoading.alpha = 0f
        tvLoading.translationY = 8f
        tvLoading.animate()
            .alpha(0.58f)
            .translationY(0f)
            .setStartDelay(320)
            .setDuration(600)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun startLogoPulse() {
        logoPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            ivSplashLogo,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.03f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.03f, 1f)
        ).apply {
            duration = 1800L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    override fun onDestroy() {
        logoPulseAnimator?.cancel()
        handler.removeCallbacks(loadingRunnable)
        handler.removeCallbacks(navigateRunnable)
        super.onDestroy()
    }
}