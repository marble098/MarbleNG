pluginManagement {
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
