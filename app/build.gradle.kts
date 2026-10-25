plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// A throwaway signing key is committed at keystore/tiktv.jks so that every CI build
// is signed with the *same* identity. Without this, an APK update would fail with
// "App not installed" because each build would use a brand new debug key.
val sideloadKeystore = file("../keystore/tiktv.jks")

// CI passes these so every build installs as an update over the previous one.
val buildVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val buildVersionName = (project.findProperty("versionName") as String?) ?: "1.0.0"

android {
    namespace = "com.tiktv.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tiktv.app"
        minSdk = 23
        targetSdk = 34
        versionCode = buildVersionCode
        versionName = buildVersionName
    }

    signingConfigs {
        if (sideloadKeystore.exists()) {
            create("sideload") {
                storeFile = sideloadKeystore
                storePassword = "tiktv123"
                keyAlias = "tiktv"
                keyPassword = "tiktv123"
                storeType = "jks"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            if (sideloadKeystore.exists()) signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (sideloadKeystore.exists()) signingConfig = signingConfigs.getByName("sideload")
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
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module")
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// Deliberately no third-party dependencies: the app only uses the Android framework,
// which keeps the APK tiny and the build fast and resilient.
dependencies {}
