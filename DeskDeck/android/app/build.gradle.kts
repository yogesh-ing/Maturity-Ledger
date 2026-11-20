plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from a local keystore only. Values live in ~/.gradle/gradle.properties or
// the environment: DESKDECK_STORE_FILE, DESKDECK_STORE_PASSWORD, DESKDECK_KEY_ALIAS, DESKDECK_KEY_PASSWORD.
fun signingValue(name: String): String? =
    (project.findProperty(name) as String?) ?: System.getenv(name)

val releaseStore = signingValue("DESKDECK_STORE_FILE")

android {
    namespace = "dev.deskdeck.panel"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.deskdeck.panel"
        minSdk = 23 // Android 6; old desk phones
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0"
    }

    signingConfigs {
        if (releaseStore != null && file(releaseStore).exists()) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = signingValue("DESKDECK_STORE_PASSWORD")
                keyAlias = signingValue("DESKDECK_KEY_ALIAS")
                keyPassword = signingValue("DESKDECK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            // Fail closed: never fall back to the debug key for a release APK.
            val releaseTaskRequested = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            if (releaseTaskRequested && signingConfigs.findByName("release") == null) {
                throw GradleException(
                    "Release signing is not configured. Set DESKDECK_STORE_FILE (and the password, alias, " +
                        "key password) in ~/.gradle/gradle.properties. Use assembleDebug for local builds."
                )
            }
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // A framework call missing its API guard crashes old phones before the panel opens.
        fatal += "NewApi"
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
}
