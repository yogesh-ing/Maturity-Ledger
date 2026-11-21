plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from a local keystore only. Values live in ~/.gradle/gradle.properties or
// the environment: DESKDECK_STORE_FILE, DESKDECK_STORE_PASSWORD, DESKDECK_KEY_ALIAS, DESKDECK_KEY_PASSWORD.
fun signingValue(name: String): String? =
    (project.findProperty(name) as String?) ?: System.getenv(name)

val releaseStore = signingValue("DESKDECK_STORE_FILE")

// One copy of the panel bundle: the agent serves these same files as the editor's live preview, and
// the APK packages them as assets/panel. Copying into the build directory keeps the source tree
// clean and guarantees the preview and the phone run identical bytes.
val panelAssetsDir = layout.buildDirectory.dir("generated/panelAssets")
val syncPanelAssets by tasks.registering(Copy::class) {
    from(rootProject.file("../agent/src/web/panel"))
    into(panelAssetsDir.map { it.dir("panel") })
}
tasks.named("preBuild") { dependsOn(syncPanelAssets) }

android {
    namespace = "dev.deskdeck.panel"
    compileSdk = 36

    sourceSets {
        getByName("main").assets.srcDir(panelAssetsDir)
    }

    defaultConfig {
        applicationId = "dev.deskdeck.panel"
        minSdk = 23 // Android 6; old desk phones
        targetSdk = 36
        versionCode = 6
        versionName = "0.6.0"
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
