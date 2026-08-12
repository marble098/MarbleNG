import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ============================================================================
// Version
// ============================================================================

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
        ?: "1.0.0-dev"

// ============================================================================
// Signing
//
// signing.properties is generated only in CI / local signed builds.
// Never commit release.jks or signing.properties.
// ============================================================================

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

// ============================================================================
// Android
// ============================================================================

android {
    namespace = "com.marbleng.app"

    compileSdk = 36

    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.marbleng.app"

        minSdk = 26
        targetSdk = 36

        versionCode = versionCodeFromCi
        versionName = versionNameFromCi

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // ========================================================================
    // Signing configurations
    // ========================================================================

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

    // ========================================================================
    // Build types
    // ========================================================================

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

    // ========================================================================
    // ABI APKs
    //
    // Generates:
    //   arm64-v8a
    //   armeabi-v7a
    //   x86_64
    //   x86
    //   universal
    // ========================================================================

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

    // ========================================================================
    // Build features
    // ========================================================================

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ========================================================================
    // Packaging
    //
    // Xray is executed from applicationInfo.nativeLibraryDir.
    // Legacy native-library packaging keeps native files available there.
    // ========================================================================

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

    // ========================================================================
    // Java
    // ========================================================================

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_17

        targetCompatibility =
            JavaVersion.VERSION_17
    }
}

// ============================================================================
// Dependencies
// ============================================================================

dependencies {

    implementation(
        platform(
            "androidx.compose:compose-bom:2026.06.00"
        )
    )

    implementation(
        "androidx.core:core-ktx:1.17.0"
    )

    implementation(
        "androidx.activity:activity-compose:1.11.0"
    )

    implementation(
        "androidx.lifecycle:lifecycle-runtime-ktx:2.9.4"
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
        "androidx.compose.material3:material3"
    )

    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )
}
