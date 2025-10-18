plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    id("com.google.gms.google-services")

}

android {
    namespace = "com.example.barterhub"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.barterhub"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // ✅ Firebase Bill of Materials (auto-manages version compatibility)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))

    // 🔥 Firebase Libraries (no need to specify versions individually)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.messaging.ktx)

    // 🌍 Google Play Services
    implementation(libs.play.services.auth)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.play.services.ads)

    // 🧭 AndroidX Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // 🧩 Core AndroidX + UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)

    // 🖼️ Image Libraries
    implementation(libs.glide)
    implementation(libs.circleimageview)
    implementation(libs.imagepicker)
    implementation(libs.lottie)

    // ☁️ Cloudinary
    implementation(libs.cloudinary.android)

    // 🌐 Networking
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    // 🧠 Kotlin + Desugaring
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)
    implementation(libs.kotlin.stdlib)

    // 🧪 Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // 🔹 Facebook SDK
    implementation("com.facebook.android:facebook-android-sdk:16.0.0")

    // 📸 CameraX
    val cameraxVersion = "1.3.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-extensions:$cameraxVersion") // optional
}
