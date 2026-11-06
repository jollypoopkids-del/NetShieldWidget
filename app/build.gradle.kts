plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sysutils.batteryguard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sysutils.batteryguard"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Подписываем ключом для удобной установки сразу на телефон
            signingConfig = signingConfigs.getByName("debug")
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
    // Чистый системный Android SDK — без лишних внешних библиотек и трекеров
}
