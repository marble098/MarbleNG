pluginManagement {
    // MARBLE_TOOLCHAIN_AUTOPILOT_V211 — every version this build uses comes from one file,
    // `gradle/toolchain.properties`, written by `scripts/resolve-toolchain.py --write` (the
    // scheduled updater) and read here, in `app/build.gradle.kts` and by `gradlew`. A version
    // written in a second place is a version that drifts; the fallback below is only for a tree
    // that has lost the file, and it is the same number the file carries today.
    val toolchain = java.util.Properties().also { props ->
        val file = java.io.File(settingsDir, "gradle/toolchain.properties")
        if (file.isFile) file.inputStream().use(props::load)
    }

    fun pin(key: String, fallback: String): String =
        toolchain.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

    plugins {
        id("com.android.application") version pin("agp", "9.2.1")
        id("org.jetbrains.kotlin.plugin.compose") version pin("kotlin", "2.3.10")
    }

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MarbleNG"
include(":app")
