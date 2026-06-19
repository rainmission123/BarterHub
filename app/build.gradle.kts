import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    id("com.google.gms.google-services")
    id("androidx.navigation.safeargs.kotlin") version "2.7.1"
}

/**
 * Read secrets from local.properties (DO NOT COMMIT local.properties)
 */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.barterhub"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jorian.barterhub"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        manifestPlaceholders["MAPS_API_KEY"] =
            localProps.getProperty("MAPS_API_KEY", "")

        buildConfigField(
            "String",
            "CLOUDINARY_CLOUD_NAME",
            "\"${localProps.getProperty("CLOUDINARY_CLOUD_NAME", "")}\""
        )
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // signingConfig = signingConfigs.getByName("release")
        }
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
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    // Firebase
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.messaging.ktx)
    implementation("com.google.firebase:firebase-functions-ktx")

    // Mapbox
    implementation("com.mapbox.maps:android:10.16.1")
    implementation("com.mapbox.plugin:maps-locationcomponent:10.16.1")

    // Google Play Services
    implementation(libs.play.services.auth)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation("com.google.android.gms:play-services-ads:24.9.0")
    implementation(libs.play.services.wallet)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Core + UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)

    // Images + UI
    implementation(libs.glide)
    implementation(libs.circleimageview)
    implementation(libs.imagepicker)
    implementation(libs.lottie)
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    implementation("androidx.gridlayout:gridlayout:1.1.0")

    // Cloudinary
    implementation(libs.cloudinary.android)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    // Facebook
    implementation("com.facebook.android:facebook-android-sdk:16.0.0")

    // CameraX
    val cameraxVersion = "1.3.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-extensions:$cameraxVersion")

    // QR Scanner
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")

    // Desugaring
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ScratchView
    implementation("com.github.cooltechworks:ScratchView:v1.1") {
        exclude(group = "com.android.support", module = "support-v4")
    }

    implementation("com.google.android.material:material:1.13.0")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("androidx.core:core-splashscreen:1.2.0")

}
