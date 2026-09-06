package com.example.barterhub

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

fun getAppCheckProviderFactory(): AppCheckProviderFactory =
    DebugAppCheckProviderFactory.getInstance()
