package com.example.barterhub

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

fun getAppCheckProviderFactory(): AppCheckProviderFactory =
    PlayIntegrityAppCheckProviderFactory.getInstance()
