package com.example.barterhub

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.LoadAdError
import android.util.Log
import com.google.android.gms.ads.RequestConfiguration

class MyApplication : Application(), Application.ActivityLifecycleCallbacks {

    private var currentActivity: Activity? = null
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false

    override fun onCreate() {
        super.onCreate()

        // Initialize AdMob
        MobileAds.initialize(this) { initializationStatus ->
            Log.d("AdMob", "Initialization complete: $initializationStatus")
        }

        // ✅ Set your test device
        val requestConfiguration = RequestConfiguration.Builder()
            .setTestDeviceIds(listOf("E3D317C9005E01878813F4B69B467150"))
            .build()
        MobileAds.setRequestConfiguration(requestConfiguration)

        // Register Activity callbacks
        registerActivityLifecycleCallbacks(this)

        // Load App Open Ad
        loadAd()
    }


    /** Load App Open Ad */
    private fun loadAd() {
        if (isLoadingAd || isAdAvailable()) return

        isLoadingAd = true
        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            this,
            "ca-app-pub-3940256099942544/3419835294", // test unit
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d("AdMob", "App Open Ad loaded")
                    appOpenAd = ad
                    isLoadingAd = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e("AdMob", "App Open Ad failed to load: ${loadAdError.message}")
                    isLoadingAd = false
                }
            }
        )

    }

    /** Check if ad is available */
    private fun isAdAvailable(): Boolean {
        return appOpenAd != null
    }

    /** Show App Open Ad */
    fun showAdIfAvailable() {
        if (!isShowingAd && isAdAvailable() && currentActivity != null) {
            appOpenAd?.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("AdMob", "App Open Ad dismissed")
                    appOpenAd = null
                    isShowingAd = false
                    loadAd() // load next ad
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    Log.e("AdMob", "Ad failed to show: ${adError.message}")
                    appOpenAd = null
                    isShowingAd = false
                    loadAd()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d("AdMob", "App Open Ad shown")
                    isShowingAd = true
                }
            }

            appOpenAd?.show(currentActivity!!)
        } else {
            Log.d("AdMob", "Ad not ready to show")
        }
    }

    /** Activity Lifecycle Callbacks */
    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
        showAdIfAvailable() // show ad whenever activity resumes
    }

    override fun onActivityPaused(activity: Activity) {
        currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
