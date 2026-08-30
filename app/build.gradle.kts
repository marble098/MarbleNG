import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

val versionCodeFromCi =
    providers.gradleProperty("VERSION_CODE").orNull?.toIntOrNull() ?: 10000

val versionNameFromCi =
    providers.gradleProperty("VERSION_NAME").orNull ?: "1.0.2"

val signingPropertiesFile = rootProject.file("signing.properties")
val signingProps = Properties().apply {
    if (signingPropertiesFile.exists()) signingPropertiesFile.inputStream().use { load(it) }
}
val signingConfigured = signingPropertiesFile.exists()
fun signingValue(name: String): String =
    signingProps.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: error("Signing config missing: $name in ${signingPropertiesFile.absolutePath}")

if (signingConfigured) listOf("storeFile","storePassword","keyAlias","keyPassword").forEach(::signingValue)

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
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (signingConfigured) {
                val configuredStore = rootProject.file(signingValue("storeFile"))
                require(configuredStore.isFile) { "Release keystore missing: ${configuredStore.absolutePath}" }
                require(configuredStore.length() > 0L) { "Release keystore empty: ${configuredStore.absolutePath}" }
                storeFile = configuredStore
                storePassword = signingValue("storePassword")
                keyAlias = signingValue("keyAlias")
                keyPassword = signingValue("keyPassword")
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingConfigured) signingConfig = signingConfigs.getByName("release")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
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
            keepDebugSymbols += setOf("**/libmarbleng.so", "**/libhev-socks5-tunnel.so")
        }
        resources { excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kapt {
        correctErrorTypes = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.56.2")
    kapt("com.google.dagger:hilt-android-compiler:2.56.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // SSH
    implementation("com.github.mwiede:jsch:2.28.6")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
