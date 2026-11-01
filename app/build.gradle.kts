import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val toolchain =
    Properties().apply {
        val file = rootProject.file("gradle/toolchain.properties")
        if (file.isFile) file.inputStream().use { input -> load(input) }
    }

fun toolchainVersion(
    key: String,
    fallback: String
): String = toolchain.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

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

// MARBLE_RELEASE_INSTALL_GATE_V217 — the release APK's version properties must never
// silently fall back. build.yml always passes the manually selected -PVERSION_CODE and -PVERSION_NAME; if either ever
// stopped arriving, the old fallbacks would package versionCode 10000 / versionName
// "1.0.2" — a silent downgrade every phone already running a timestamp-coded release
// refuses to install ("App not installed"). With signing configured this is a release
// context, so the build fails loudly instead; a local tree without signing.properties
// keeps the plain developer fallbacks.
val versionCodeFromCi =
    if (signingConfigured) {
        providers
            .gradleProperty("VERSION_CODE")
            .orNull
            ?.toIntOrNull()
            ?: error(
                "Release signing is configured but VERSION_CODE is missing. " +
                    "Pass -PVERSION_CODE=<int> (build.yml always does); a silent " +
                    "fallback would ship a downgrade Android refuses to install."
            )
    } else {
        providers
            .gradleProperty("VERSION_CODE")
            .orNull
            ?.toIntOrNull()
            ?: 10000
    }

val versionNameFromCi =
    if (signingConfigured) {
        providers
            .gradleProperty("VERSION_NAME")
            .orNull
            ?.takeIf { it.isNotBlank() }
            ?: error(
                "Release signing is configured but VERSION_NAME is missing. " +
                    "Pass -PVERSION_NAME=<x.y.z> (build.yml always does)."
            )
    } else {
        providers
            .gradleProperty("VERSION_NAME")
            .orNull
            ?: "1.0.2"
    }

val coreLockFile = rootProject.file("core-lock.json")

val coreLockText =
    if (coreLockFile.isFile) {
        coreLockFile.readText()
    } else {
        ""
    }

fun coreLockField(component: String, field: String): String {
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

val singBoxCoreTag = coreLockField("singbox", "tag")
val singBoxCoreRepo = coreLockField("singbox", "repo")

// MARBLE_TUN2SOCKS_CORE_V222 — the second TUN datapath, pinned beside the others.
val tun2SocksCoreTag = coreLockField("tun2socks", "tag")
val tun2SocksCoreRepo = coreLockField("tun2socks", "repo")
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

        buildConfigField("String", "XRAY_CORE_TAG", "\"$xrayCoreTag\"")
        buildConfigField("String", "XRAY_CORE_REPO", "\"$xrayCoreRepo\"")
        buildConfigField("String", "HEV_CORE_TAG", "\"$hevCoreTag\"")
        buildConfigField("String", "HEV_CORE_REPO", "\"$hevCoreRepo\"")
        buildConfigField("String", "SINGBOX_CORE_TAG", "\"$singBoxCoreTag\"")
        buildConfigField("String", "SINGBOX_CORE_REPO", "\"$singBoxCoreRepo\"")
        buildConfigField("String", "TUN2SOCKS_CORE_TAG", "\"$tun2SocksCoreTag\"")
        buildConfigField("String", "TUN2SOCKS_CORE_REPO", "\"$tun2SocksCoreRepo\"")
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

    // V242: Compose regression tests inflate real resources (fonts, theme, manifest).
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        jniLibs {
            // MARBLE_APK_INSTALL_CONTRACT_V195 -- this flag is the installer

            useLegacyPackaging = true

            keepDebugSymbols += setOf(
                "**/libmarbleng.so",
                "**/libhev-socks5-tunnel.so",
                "**/libsingbox.so",
                "**/libtun2socks.so",

                "**/libxray.so"
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

// MARBLE_APK_INSTALL_CONTRACT_V195 -- installer extraction contract

tasks.configureEach {
    val marbleIntermediatesDir = file("build/intermediates")
    val marbleNormalizeExtractNativeLibs: (java.io.File) -> Unit = { root ->
        if (root.isDirectory) {
            var marbleNormalizedCount = 0
            for (candidate in root.walkTopDown()) {
                if (candidate.isFile && candidate.name == "AndroidManifest.xml") {
                    var text = candidate.readText()
                    if (text.contains("android:extractNativeLibs=\"false\"")) {
                        text = text.replace(
                            "android:extractNativeLibs=\"false\"",
                            "android:extractNativeLibs=\"true\""
                        )
                        candidate.writeText(text)
                        marbleNormalizedCount = marbleNormalizedCount + 1
                    }
                }
            }
            if (marbleNormalizedCount > 0) {
                println("::warning::MARBLE-INSTALL-CONTRACT-V195 normalized extractNativeLibs in " + marbleNormalizedCount + " merged manifest(s)")
            }
        }
    }
    if (name.startsWith("process") && name.contains("Manifest")) {
        doLast {
            marbleNormalizeExtractNativeLibs(marbleIntermediatesDir)
        }
    }
    if (name.startsWith("package")) {
        doFirst {
            marbleNormalizeExtractNativeLibs(marbleIntermediatesDir)
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

val prepareSingBoxRules by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python3", "scripts/prepare-singbox-rules.py")
    inputs.file(rootProject.file("singbox-rules-lock.json"))
    inputs.file(rootProject.file("scripts/prepare-singbox-rules.py"))
    outputs.dir(project.file("src/main/assets/singbox"))
}
tasks.named("preBuild") { dependsOn(prepareSingBoxRules) }

dependencies {
    implementation(
        platform(
            "androidx.compose:compose-bom:${toolchainVersion("composeBom", "2026.08.00")}"
        )
    )

    implementation(
        "androidx.core:core-ktx:${toolchainVersion("coreKtx", "1.19.0")}"
    )

    implementation(
        "androidx.activity:activity-compose:${toolchainVersion("activityCompose", "1.13.0")}"
    )

    implementation(
        "androidx.lifecycle:lifecycle-runtime-ktx:${toolchainVersion("lifecycleRuntimeKtx", "2.11.0")}"
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

    // MARBLE_M3_EXPRESSIVE_V240 — the expressive design system (MaterialExpressiveTheme,
    // MotionScheme, ButtonGroup, FloatingToolbar, wavy progress) lives on the Material 3
    // 1.5 line, ahead of whatever the BOM pins. The explicit version wins over the BOM's
    // managed one, and the toolchain autopilot keeps it the newest 1.5.x.
    implementation(
        "androidx.compose.material3:material3:${toolchainVersion("material3", "1.5.0-beta02")}"
    )

    implementation("com.github.mwiede:jsch:${toolchainVersion("jsch", "2.28.6")}")

    implementation("com.google.zxing:core:${toolchainVersion("zxing", "3.5.3")}")

    testImplementation("junit:junit:${toolchainVersion("junit", "4.13.2")}")

    testImplementation("org.json:json:${toolchainVersion("orgJson", "20260814")}")

    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )

    // V242: run the real Compose layout/animation on the JVM. The release crash was a
    // frame-level padding invariant, so a string-only source audit is not enough.
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:${toolchainVersion("robolectric", "4.17")}")
}
