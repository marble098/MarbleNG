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

// ---------------------------------------------------------------------------------------------
// MARBLE_ROUTE_ATELIER_V207 — TEMPORARY CI DIAGNOSTIC. Delete as soon as the JVM step of PR #186 is
// green; it is not part of the product and it does nothing outside a GitHub runner.
//
// The only failing step on this branch is the JVM step, and the Actions log archive is not reachable
// from every review tool. A finalizer did not run (the build stops on the first failure, and the
// configuration cache serializes nothing of mine), so the probe runs *before* the real work instead:
// it invokes the same Gradle targets once, on its own, reads the text back, and forwards the
// compiler's `e: file:line:col` lines and the failing test names into the two channels any API client
// can read — the check run's annotations and the job summary. It always succeeds, so the build that
// follows is still the build that judges this branch; the recursion guard stops the inner invocation
// from probing again.
// ---------------------------------------------------------------------------------------------
private fun marbleNestedGradle(
    workspace: String,
    targets: List<String>,
    minutes: Long
): Pair<Int, String> {
    val log = java.io.File(System.getProperty("java.io.tmpdir"), "marble-probe.log")
    val builder = java.lang.ProcessBuilder(
        mutableListOf(
            "gradle",
            "--no-daemon",
            "--console=plain",
            "--project-cache-dir",
            "$workspace/.marble-probe-cache",
            "--build-cache"
        ) + targets
    )
    builder.directory(java.io.File(workspace))
    builder.environment()["MARBLE_PROBE"] = "1"
    builder.redirectErrorStream(true)
    builder.redirectOutput(log)
    val process = builder.start()
    if (!process.waitFor(minutes, java.util.concurrent.TimeUnit.MINUTES)) {
        process.destroyForcibly()
        return -1 to "probe timed out after $minutes minutes"
    }
    return process.exitValue() to if (log.exists()) log.readText() else ""
}

println("::error title=diag-alive::marble probe block evaluated in app/build.gradle.kts")

val marbleJvmProbe =
    tasks.register("marbleJvmDiagnosticProbe") {
        group = "verification"
        description = "TEMPORARY: read the JVM diagnostics back into the CI annotations."
        doLast {
            val runner = System.getenv("GITHUB_ACTIONS")
            val probing = System.getenv("MARBLE_PROBE")
            if (runner.isNullOrEmpty() || !probing.isNullOrEmpty()) {
                println("::error title=diag-skipped::runner=$runner probing=$probing")
                return@doLast
            }
            val workspace = System.getenv("GITHUB_WORKSPACE") ?: return@doLast
            val compile = runCatching {
                marbleNestedGradle(workspace, listOf(":app:compileDebugKotlin"), 12L)
            }.getOrElse { -2 to "probe could not run: ${it.javaClass.name}" }
            var code = compile.first
            var text = compile.second
            if (code == 0) {
                val rest = runCatching {
                    marbleNestedGradle(workspace, listOf(":app:testDebugUnitTest", ":app:compileReleaseKotlin"), 12L)
                }.getOrElse { -2 to "probe could not run: ${it.javaClass.name}" }
                code = rest.first
                text = text + "\n" + rest.second
            }
            val interesting = text.lines().filter { line ->
                line.startsWith("e: ") ||
                    line.contains(": error:") ||
                    line.contains(" FAILED") ||
                    line.startsWith("* What went wrong") ||
                    line.startsWith("FAILURE:") ||
                    line.startsWith("Caused by:") ||
                    line.startsWith("Execution failed")
            }.distinct()
            val body = buildString {
                appendLine("### Marble JVM diagnostics (temporary probe)")
                appendLine()
                appendLine("nested run exit code $code; ${interesting.size} interesting line(s) in ${text.length} chars")
                appendLine()
                appendLine("```")
                val lines = if (interesting.isEmpty()) text.lines().takeLast(50) else interesting.take(80)
                lines.forEach { appendLine(it.take(240)) }
                appendLine("```")
            }
            System.getenv("GITHUB_STEP_SUMMARY")
                ?.takeIf { it.isNotEmpty() }
                ?.let { path -> runCatching { java.io.File(path).appendText(body) } }
            println("::error title=marble-diagnostics::exit=$code lines=${interesting.size} chars=${text.length}")
            interesting.take(30).forEach { line ->
                val match = Regex("^e: file://(.+?):(\\d+):(\\d+)\\s*(.*)$").find(line)
                if (match != null) {
                    val (path, lineNo, column, message) = match.destructured
                    println(
                        "::error file=" + path.substringAfterLast("/") +
                            ",line=" + lineNo + ",col=" + column + "::" +
                            path.substringAfterLast("/MarbleNG/", path) + ": " + message.take(160).replace("%", "%25")
                    )
                } else {
                    println("::error title=jvm-diagnostic::" + line.take(220).replace("%", "%25"))
                }
            }
            if (interesting.isEmpty()) {
                text.lines().takeLast(12).forEach {
                    println("::error title=probe-tail::" + it.take(200).replace("%", "%25"))
                }
            }
        }
    }

tasks.matching { it.name.startsWith("compile") || it.name.startsWith("test") }.configureEach {
    if (name != "marbleJvmDiagnosticProbe") {
        dependsOn(marbleJvmProbe)
    }
}
