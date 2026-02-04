package com.example.barterhub.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.os.Handler
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

@SuppressLint("StaticFieldLeak")
object AppOpenAdManager {

    private var appOpenAd: AppOpenAd? = null
    private var isLoading = false
    private var isShowing = false
    private var hasShownThisSession = false
    private var currentActivity: Activity? = null

    private const val TAG = "AppOpenAd"

    // ✅ App Open Ad Unit ID
    private const val AD_UNIT_ID = "ca-app-pub-6533502787981664/5580053093"

    // ======================= LOAD AD =======================
    fun loadAd(application: Application) {
        if (isLoading || appOpenAd != null) return

        isLoading = true
        Log.d(TAG, "🎯 Loading App Open Ad with ID: $AD_UNIT_ID")

        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            application,
            AD_UNIT_ID,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "✅ SUCCESS: App Open Ad LOADED!")
                    appOpenAd = ad
                    isLoading = false

                    // Auto-show if we already have an activity
                    currentActivity?.let { activity ->
                        showAdIfAvailable(activity, false)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "❌ FAILED to load: ${error.message}")
                    Log.e(TAG, "Error code: ${error.code}")
                    isLoading = false

                    // Retry after 10 seconds
                    Handler(application.mainLooper).postDelayed({
                        loadAd(application)
                    }, 10000)
                }
            }
        )
    }

    // ======================= SHOW AD =======================
    fun showAdIfAvailable(activity: Activity, isPremium: Boolean) {
        Log.d(TAG, "=== SHOW AD CALLED ===")
        Log.d(TAG, "Activity: ${activity.javaClass.simpleName}")
        Log.d(TAG, "isPremium: $isPremium")
        Log.d(TAG, "isShowing: $isShowing")
        Log.d(TAG, "hasShownThisSession: $hasShownThisSession")
        Log.d(TAG, "appOpenAd is null: ${appOpenAd == null}")

        currentActivity = activity

        if (isPremium) {
            Log.d(TAG, "❌ Skipping - user is premium")
            return
        }

        if (isShowing) {
            Log.d(TAG, "❌ Skipping - ad is already showing")
            return
        }

        if (hasShownThisSession) {
            Log.d(TAG, "❌ Skipping - ad already shown this session")
            return
        }

        if (appOpenAd == null) {
            Log.d(TAG, "⚠️ Ad not loaded yet, loading now...")
            loadAd(activity.application)
            return
        }

        isShowing = true
        Log.d(TAG, "🚀 Attempting to show ad...")

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "✅ Ad dismissed successfully")
                appOpenAd = null
                isShowing = false
                hasShownThisSession = true
                loadAd(activity.application)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "❌ Failed to show ad: ${adError.message}")
                appOpenAd = null
                isShowing = false
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "🎉 AD SHOWED SUCCESSFULLY!")
            }
        }

        try {
            appOpenAd?.show(activity)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception showing ad: ${e.message}")
            isShowing = false
            appOpenAd = null
        }
    }

    // ======================= RESET SESSION =======================
    fun resetSession() {
        hasShownThisSession = false
    }
}
