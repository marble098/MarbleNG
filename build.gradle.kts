// MARBLE_TOOLCHAIN_AUTOPILOT_V211 — the plugin versions live in pluginManagement, in
// `settings.gradle.kts`, which reads them from `gradle/toolchain.properties`.
plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
}
