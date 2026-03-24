plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dainon.safepulse"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dainon.safepulse"
        minSdk = 30       // Wear OS 3.0+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // SafePulse 서버 URL (배포 시 변경)
        buildConfigField("String", "SERVER_URL", "\"http://10.0.2.2:4000\"")
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

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // Wear OS
    implementation("androidx.wear:wear:1.3.0")
    implementation("com.google.android.gms:play-services-wearable:18.1.0")

    // Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Health Services (심박수, SpO₂)
    implementation("androidx.health:health-services-client:1.0.0-rc02")

    // Samsung Health SDK (Galaxy Watch 전용)
    // implementation(files("libs/samsung-health-sdk.aar"))

    // 네트워크
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // 코루틴
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
