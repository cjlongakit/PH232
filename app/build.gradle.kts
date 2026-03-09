plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.ph232"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.ph232"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Import the Firebase BoM
    implementation(platform(libs.firebase.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // CameraX for QR Scanner
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    // Guava is often needed for CameraX's ListenableFuture
    implementation("com.google.guava:guava:31.1-android")

    // ML Kit Barcode Scanning
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // ZXing for QR Code Generation
    implementation("com.google.zxing:core:3.5.2")

    // Firebase dependencies (versions managed by BoM)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)

    // AndroidX Fragment
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
