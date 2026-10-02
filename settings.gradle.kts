pluginManagement {
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

// ───────────────────────────────────────────────────────────────────────────────────
// TEMPORARY DIAGNOSTIC — REVERT BEFORE MERGE.
// Re-emit the Kotlin compiler's diagnostics as GitHub Actions `::error::` workflow
// commands so they surface as check-run annotations, which are readable through the
// API from a sandbox that cannot reach the Actions log host.
// ───────────────────────────────────────────────────────────────────────────────────
run {
    val seen = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    var emitting = false
    var emitted = 0
    org.gradle.api.logging.Logging.addOutputEventListener(
        object : org.gradle.api.logging.OutputEventListener {
            override fun onOutput(event: org.gradle.api.logging.OutputEvent?) {
                if (emitting || emitted >= 10) return
                val text = event?.toString() ?: return
                if (!(text.contains("e: file://") || text.contains("Unresolved reference") ||
                        text.contains("None of the following") ||
                        text.contains("Type mismatch") || text.contains("Unresolved") ||
                        text.contains("No value passed for parameter") ||
                        text.contains("Compilation error"))
                ) return
                val key = text.take(160)
                if (!seen.add(key)) return
                emitting = true
                val clean = text
                    .replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
                    .take(500)
                println("::error::" + clean)
                emitted++
                emitting = false
            }
        }
    )
}
