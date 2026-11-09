plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.devkumar.jarvis"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.devkumar.jarvis"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.3"

        // सिर्फ़ असली फ़ोन वाले प्रोसेसर, ताकि APK छोटा रहे
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // हर बिल्ड पर एक ही साइनिंग key, ताकि नया APK पुराने ऐप के ऊपर इंस्टॉल हो सके (repo private रखें)
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")
    // ऑफ़लाइन वेक-वर्ड ("हेलो छोटू")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
}
