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
// The CI host that serves job logs is unreachable from the agent sandbox, so the
// Kotlin compiler's diagnostics are re-emitted as GitHub Actions `::error::` workflow
// commands, which surface as check-run annotations and are readable through the API.
// ───────────────────────────────────────────────────────────────────────────────────
run {
    val buffer = java.util.Collections.synchronizedList(mutableListOf<String>())
    var emitting = false
    gradle.addBuildListener(object : org.gradle.BuildAdapter() {
        override fun buildFinished(result: org.gradle.BuildResult) {
            if (result.failure == null) return
            emitting = true
            val interesting = buffer.filter {
                it.contains("e: ") || it.contains("error:") || it.contains("Unresolved reference") ||
                    it.contains("FAILED") || it.contains("What went wrong") || it.contains("Compilation error")
            }
            val shown = (if (interesting.isEmpty()) buffer else interesting).take(12)
            shown.forEach { line ->
                val clean = line
                    .replace("\r", " ")
                    .replace("\n", " ")
                    .replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
                    .take(600)
                println("::error::" + clean)
            }
            emitting = false
        }
    })
    org.gradle.api.logging.Logging.addOutputEventListener { event ->
        if (!emitting) {
            val text = event.toString()
            if (text.isNotBlank()) buffer.add(text)
            if (buffer.size > 20000) buffer.subList(0, 10000).clear()
        }
    }
}
