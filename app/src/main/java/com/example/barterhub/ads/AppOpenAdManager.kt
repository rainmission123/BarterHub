package com.example.barterhub.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

@SuppressLint("StaticFieldLeak")
object AppOpenAdManager {

    private const val TAG = "AppOpenAd"
    private const val AD_UNIT_ID = "ca-app-pub-6533502787981664/5580053093"

    private var appOpenAd: AppOpenAd? = null
    private var isLoading = false
    private var isShowing = false
    private var hasShownThisSession = false
    private var lastUid: String? = null
    private var isCheckingPremium = false
    private var currentActivity: Activity? = null
    private var loadAttempts = 0
    private val maxLoadAttempts = 3

    fun loadAd(application: Application) {
        // Check if user is premium before loading ad
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            checkPremiumStatus(uid) { isPremium ->
                if (isPremium) {
                    Log.d(TAG, "👑 User is premium, skipping ad load")
                    return@checkPremiumStatus
                }

                // Proceed with ad load if not premium
                if (isLoading || appOpenAd != null) return@checkPremiumStatus

                isLoading = true
                Log.d(TAG, "🎯 Loading App Open Ad with ID: $AD_UNIT_ID")

                val request = AdRequest.Builder().build()

                AppOpenAd.load(
                    application,
                    AD_UNIT_ID,
                    request,
                    object : AppOpenAd.AppOpenAdLoadCallback() {

                        override fun onAdLoaded(ad: AppOpenAd) {
                            Log.d(TAG, "✅ App Open Ad LOADED!")
                            appOpenAd = ad
                            isLoading = false
                            loadAttempts = 0

                            // Reset full screen callback
                            setupFullScreenCallback(ad)

                            // Show ad immediately if activity is ready and hasn't shown yet
                            currentActivity?.let { act ->
                                if (!isShowing && !hasShownThisSession) {
                                    showAdIfAvailable(act)
                                }
                            }
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Log.e(TAG, "❌ Failed to load: ${error.message} (code=${error.code})")
                            appOpenAd = null
                            isLoading = false

                            loadAttempts++
                            if (loadAttempts < maxLoadAttempts) {
                                // Retry after delay
                                Handler(Looper.getMainLooper()).postDelayed({
                                    loadAd(application)
                                }, 30_000) // 30 seconds delay
                            } else {
                                Log.e(TAG, "❌ Max load attempts reached")
                                loadAttempts = 0
                            }
                        }
                    }
                )
            }
        } else {
            // No user logged in, load ad normally
            performAdLoad(application)
        }
    }

    private fun performAdLoad(application: Application) {
        if (isLoading || appOpenAd != null) return

        isLoading = true
        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            application,
            AD_UNIT_ID,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoading = false
                    setupFullScreenCallback(ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAd = null
                    isLoading = false
                }
            }
        )
    }

    private fun setupFullScreenCallback(ad: AppOpenAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "✅ Ad dismissed")
                appOpenAd = null
                isShowing = false
                hasShownThisSession = true

                // Preload next ad
                currentActivity?.application?.let { loadAd(it) }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "❌ Failed to show: ${adError.message}")
                appOpenAd = null
                isShowing = false

                // Try to load another ad
                currentActivity?.application?.let { loadAd(it) }
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "🎉 Ad showed successfully")
                isShowing = true
            }
        }
    }

    fun showAdIfAvailable(activity: Activity) {
        currentActivity = activity

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        // If user is not logged in, show ad
        if (uid == null) {
            showAdForNonLoggedIn(activity)
            return
        }

        // Check if user switched
        if (uid != lastUid) {
            Log.d(TAG, "🔄 User switched: $lastUid -> $uid")
            resetForUserSwitch()
            lastUid = uid
        }

        // Check conditions
        if (isShowing) {
            Log.d(TAG, "⏭️ Skip: ad is already showing")
            return
        }

        if (hasShownThisSession) {
            Log.d(TAG, "⏭️ Skip: already shown this session")
            return
        }

        // Check premium status
        checkPremiumStatus(uid) { isPremium ->
            if (isPremium) {
                Log.d(TAG, "👑 Premium user - no ad shown")
                return@checkPremiumStatus
            }

            // Show ad for non-premium user
            showAdForNonPremium(activity)
        }
    }

    private fun showAdForNonLoggedIn(activity: Activity) {
        if (appOpenAd != null && !isShowing && !hasShownThisSession) {
            showAdInternal(activity)
        } else if (appOpenAd == null && !isLoading) {
            loadAd(activity.application)
        }
    }

    private fun showAdForNonPremium(activity: Activity) {
        if (appOpenAd != null && !isShowing && !hasShownThisSession) {
            showAdInternal(activity)
        } else if (appOpenAd == null && !isLoading) {
            Log.d(TAG, "📦 Ad not loaded, loading now...")
            loadAd(activity.application)

            // If ad is loading, try to show after load
            if (!hasShownThisSession) {
                // Wait for ad to load then show
                waitForAdAndShow(activity)
            }
        }
    }

    private fun waitForAdAndShow(activity: Activity) {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (appOpenAd != null && !isShowing && !hasShownThisSession) {
                    showAdInternal(activity)
                } else if (!hasShownThisSession) {
                    // Try again after delay
                    handler.postDelayed(this, 1000)
                }
            }
        }, 1000)
    }

    private fun showAdInternal(activity: Activity) {
        val ad = appOpenAd ?: return

        try {
            ad.show(activity)
            Log.d(TAG, "🚀 Showing App Open Ad...")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception showing ad: ${e.message}")
            appOpenAd = null
            isShowing = false
            loadAd(activity.application)
        }
    }

    private fun checkPremiumStatus(uid: String, callback: (Boolean) -> Unit) {
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
                Log.d(TAG, "Premium check: uid=$uid active=$premiumActive")
                callback(premiumActive)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Premium check failed: ${e.message}")
                callback(false)
            }
    }

    fun onPremiumStateChanged() {
        Log.d(TAG, "🔔 Premium state changed - resetting session")
        hasShownThisSession = false
    }

    private fun resetForUserSwitch() {
        hasShownThisSession = false
        isShowing = false
        isCheckingPremium = false
        appOpenAd = null
        isLoading = false
    }

    fun resetSession() {
        hasShownThisSession = false
    }

    fun forceShowAd(activity: Activity) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            // Non-logged in user
            hasShownThisSession = false
            showAdIfAvailable(activity)
        } else {
            checkPremiumStatus(uid) { isPremium ->
                if (!isPremium) {
                    hasShownThisSession = false
                    showAdIfAvailable(activity)
                }
            }
        }
    }
}