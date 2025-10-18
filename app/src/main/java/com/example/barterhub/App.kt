package com.example.barterhub

import android.app.Application
import com.cloudinary.android.MediaManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        val config: HashMap<String, String> = HashMap()
        config["cloud_name"] = "dtccox0s0"
        config["api_key"] = "957854413961625"
        config["api_secret"] = "VYcwlAqTWWzmMhxnRtArlrPcaxA"

        MediaManager.init(this, config)
    }
}
