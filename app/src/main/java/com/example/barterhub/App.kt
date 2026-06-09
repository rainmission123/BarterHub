package com.example.barterhub

import android.app.Application
import com.cloudinary.android.MediaManager

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        val config = hashMapOf(
            "cloud_name" to "dtccox0s0",
            "secure" to "true"
        )

        MediaManager.init(this, config)
    }
}