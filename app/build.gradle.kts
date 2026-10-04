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
// MARBLE_SINGBOX_CORE_V151 — the second engine. sing-box extended is pinned exactly like the other
// two cores, so the version shown in Settings › Information is the binary inside this APK.
val singBoxCoreTag = coreLockField("singbox", "tag")
val singBoxCoreRepo = coreLockField("singbox", "repo")
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
        buildConfigField("String", "SINGBOX_CORE_TAG", "\"$singBoxCoreTag\"")
        buildConfigField("String", "SINGBOX_CORE_REPO", "\"$singBoxCoreRepo\"")
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
            // MARBLE_APK_INSTALL_CONTRACT_V195 -- this flag is the installer
            // extraction contract, not a style choice.  The Xray and sing-box
            // cores are executables launched from applicationInfo.nativeLibraryDir,
            // so the installer must extract lib/**/*.so at install time.  This
            // flag keeps the native entries DEFLATED and instructs AGP to inject
            // android:extractNativeLibs="true" into the merged manifest.  AGP
            // 8.3+/9.x defaults extractNativeLibs to "false" for minSdk >= 23,
            // and with compressed native entries that combination is rejected by
            // the package installer before the progress UI even appears
            // ("App not installed"; INSTALL_FAILED_INVALID_APK: Failed to extract
            // native libraries, res=-2).  The merged manifest is normalized below
            // as a second line of defense because AGP 9 forbids pinning the
            // attribute in the source AndroidManifest.xml.
            useLegacyPackaging = true

            keepDebugSymbols += setOf(
                "**/libmarbleng.so",
                "**/libhev-socks5-tunnel.so",
                "**/libsingbox.so",
                // libxray.so is a Go executable executed via ProcessBuilder;
                // AGP stripping must never touch the cores.
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

// -----------------------------------------------------------------------------
// MARBLE_APK_INSTALL_CONTRACT_V195 -- installer extraction contract
//
// libxray.so / libsingbox.so are Go executables that the app launches from
// applicationInfo.nativeLibraryDir, so every release APK must ship with
// extractNativeLibs="true" (or the attribute absent, which is the same thing).
// The source manifest cannot carry the attribute (AGP 9 rejects it) and AGP
// defaults it to "false" for minSdk >= 23, so the merged manifest under
// build/intermediates is normalized after every manifest-processing task and
// again before packaging: a silent "false" flip turns an otherwise healthy
// release into an uninstallable APK ("App not installed";
// INSTALL_FAILED_INVALID_APK: Failed to extract native libraries, res=-2).
// -----------------------------------------------------------------------------

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

// MARBLE_TEST_EVIDENCE_V120 — CI reads the console log and nothing else, so a failing test has to
// say *what* it saw. Gradle's default logging names the test and the line number, which is not
// enough to tell a wrong expectation from a wrong implementation without another full run.
tasks.withType<Test>().configureEach {
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Preserve useful Gradle output on the check-run itself. Some CI clients cannot retrieve the raw
// Actions log archive, so attach the failure excerpt to the check when tests or Kotlin compilation
// fail instead of leaving only Gradle's exit code.
val marbleVerificationOutput = StringBuilder()
val marbleVerificationTasks = setOf(
    "testDebugUnitTest",
    "compileDebugKotlin",
    "compileReleaseKotlin"
)
val marbleFailureReporters = marbleVerificationTasks.associateWith { taskName ->
    tasks.register("${taskName}FailureDetails") {
        doLast {
            val output = synchronized(marbleVerificationOutput) {
                marbleVerificationOutput.toString()
            }
            val lines = output.lines()
            val failureLine = Regex(
                "(?i)(^e: |\\berror\\b|\\bfailed\\b|\\bfailure\\b|\\bexception\\b|" +
                    "\\bcaused by\\b|unresolved reference|expecting|assertion|expected:|" +
                    "actual:|but was|could not|what went wrong|there were failing tests)"
            )
            val matching = lines.indices.filter { failureLine.containsMatchIn(lines[it]) }
            if (matching.isNotEmpty()) {
                val selected = sortedSetOf<Int>()
                matching.takeLast(35).forEach { index ->
                    if (index > 0) selected += index - 1
                    selected += index
                    if (index + 1 < lines.size) selected += index + 1
                }
                val excerpt = selected.joinToString("\n") { index ->
                    lines[index].take(400)
                }.takeLast(2_200)
                val message = "$taskName:\n$excerpt"
                    .replace("%", "%25")
                    .replace("\r", "%0D")
                    .replace("\n", "%0A")
                println("::error title=Gradle verification failure::$message")
            }
        }
    }
}
tasks.configureEach {
    if (name in marbleVerificationTasks) {
        logging.addStandardOutputListener { text ->
            synchronized(marbleVerificationOutput) {
                val remaining = 200_000 - marbleVerificationOutput.length
                if (remaining > 0) marbleVerificationOutput.append(text.take(remaining))
            }
        }
        logging.addStandardErrorListener { text ->
            synchronized(marbleVerificationOutput) {
                val remaining = 200_000 - marbleVerificationOutput.length
                if (remaining > 0) marbleVerificationOutput.append(text.take(remaining))
            }
        }
        finalizedBy(marbleFailureReporters.getValue(name))
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

    // MARBLE_QR_IMPORT_V121 — QR import decodes a picture the user already has (screenshot,
    // photo, saved image) with ZXing's pure-Java core. No camera dependency, no CAMERA
    // permission and no extra runtime: the image arrives through the system picker.
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")

    testImplementation("org.json:json:20260814")

    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )
}
