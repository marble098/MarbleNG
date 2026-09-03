import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionCodeFromCi =
    providers
        .gradleProperty("VERSION_CODE")
        .orNull
        ?.toIntOrNull()
        ?: 10000

val versionNameFromCi =
    providers
        .gradleProperty("VERSION_NAME")
        .orNull
        ?: "1.0.2"

val signingPropertiesFile =
    rootProject.file("signing.properties")

val signingProps =
    Properties().apply {
        if (signingPropertiesFile.exists()) {
            signingPropertiesFile.inputStream().use { input ->
                load(input)
            }
        }
    }

val signingConfigured =
    signingPropertiesFile.exists()

// MARBLE_INFORMATION_PAGE_V114 — Settings › Information reports the exact cores this build ships.
// They are read from core-lock.json, the same file CI verifies and the same file the native build
// downloads from, so the numbers on screen cannot drift from the binaries inside the APK.
val coreLockFile = rootProject.file("core-lock.json")

val coreLockText =
    if (coreLockFile.isFile) {
        coreLockFile.readText()
    } else {
        ""
    }

fun coreLockField(component: String, field: String): String {
    // Deliberately no JSON parser and no regex escaping in the build script: core-lock.json is a
    // flat two-level file, and plain string scoping reads exactly like the file itself.
    val block = coreLockText
        .substringAfter("\"$component\"", "")
        .substringBefore("}", "")
    return block
        .substringAfter("\"$field\"", "")
        .substringAfter("\"", "")
        .substringBefore("\"", "")
        .ifBlank { "unknown" }
}

val xrayCoreTag = coreLockField("xray", "tag")
val xrayCoreRepo = coreLockField("xray", "repo")
val hevCoreTag = coreLockField("hev", "tag")
val hevCoreRepo = coreLockField("hev", "repo")
val marbleSourceUrl = "https://github.com/marble098/MarbleNG"

fun signingValue(name: String): String {
    return signingProps
        .getProperty(name)
        ?.takeIf { it.isNotBlank() }
        ?: error(
            "Signing configuration is incomplete: missing '$name' " +
                "in ${signingPropertiesFile.absolutePath}"
        )
}

if (signingConfigured) {
    listOf(
        "storeFile",
        "storePassword",
        "keyAlias",
        "keyPassword"
    ).forEach(::signingValue)
}

android {
    namespace = "com.marbleng.app"

    compileSdk = 37

    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.marbleng.app"

        minSdk = 26
        targetSdk = 37

        versionCode = versionCodeFromCi
        versionName = versionNameFromCi

        vectorDrawables {
            useSupportLibrary = true
        }

        // MARBLE_INFORMATION_PAGE_V114 — Settings › Information reports the real cores and links
        // straight to the repository, so both are compiled in rather than hardcoded in the UI.
        buildConfigField("String", "XRAY_CORE_TAG", "\"$xrayCoreTag\"")
        buildConfigField("String", "XRAY_CORE_REPO", "\"$xrayCoreRepo\"")
        buildConfigField("String", "HEV_CORE_TAG", "\"$hevCoreTag\"")
        buildConfigField("String", "HEV_CORE_REPO", "\"$hevCoreRepo\"")
        buildConfigField("String", "SOURCE_URL", "\"$marbleSourceUrl\"")
    }

    signingConfigs {
        create("release") {

            if (signingConfigured) {

                val configuredStore =
                    rootProject.file(
                        signingValue("storeFile")
                    )

                require(configuredStore.isFile) {
                    "Release keystore does not exist: " +
                        configuredStore.absolutePath
                }

                require(configuredStore.length() > 0L) {
                    "Release keystore is empty: " +
                        configuredStore.absolutePath
                }

                storeFile = configuredStore

                storePassword =
                    signingValue("storePassword")

                keyAlias =
                    signingValue("keyAlias")

                keyPassword =
                    signingValue("keyPassword")
            }
        }
    }

    buildTypes {

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {

            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )

            if (signingConfigured) {
                signingConfig =
                    signingConfigs.getByName("release")
            }
        }
    }

    splits {
        abi {
            isEnable = true

            reset()

            include(
                "arm64-v8a",
                "armeabi-v7a",
                "x86_64",
                "x86"
            )

            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {

        jniLibs {
            useLegacyPackaging = true

            keepDebugSymbols += setOf(
                "**/libmarbleng.so",
                "**/libhev-socks5-tunnel.so"
            )
        }

        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_17

        targetCompatibility =
            JavaVersion.VERSION_17
    }
}

// Never let a developer accidentally install an unsigned release APK. Play Protect warnings on
// sideloaded builds are commonly caused by a missing/rotated certificate; the CI workflow restores
// the stable MarbleNG signer before assembleRelease. Debug/compile tasks remain usable locally.
tasks.configureEach {
    if (!signingConfigured && (name == "assembleRelease" || name == "bundleRelease")) {
        doFirst {
            throw GradleException(
                "Release signing is not configured. Build the signed artifact in GitHub Actions " +
                    "or provide signing.properties; unsigned APKs are not installable release deliverables."
            )
        }
    }
}

dependencies {

    implementation(
        platform(
            "androidx.compose:compose-bom:2026.08.00"
        )
    )

    implementation(
        "androidx.core:core-ktx:1.19.0"
    )

    implementation(
        "androidx.activity:activity-compose:1.13.0"
    )

    implementation(
        "androidx.lifecycle:lifecycle-runtime-ktx:2.11.0"
    )

    implementation(
        "androidx.compose.ui:ui"
    )

    implementation(
        "androidx.compose.ui:ui-tooling-preview"
    )

    implementation(
        "androidx.compose.foundation:foundation"
    )

    implementation(
        "androidx.compose.animation:animation"
    )

    implementation(
        "androidx.compose.material3:material3"
    )

    implementation("com.github.mwiede:jsch:2.28.6")

    testImplementation("junit:junit:4.13.2")

    testImplementation("org.json:json:20260814")

    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )
}
