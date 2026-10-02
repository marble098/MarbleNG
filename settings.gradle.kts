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
// ───────────────────────────────────────────────────────────────────────────────────
run {
    println("::error::DIAG-CHANNEL-ALIVE")
    System.err.println("::error::DIAG-CHANNEL-ALIVE-STDERR")
    val sample = java.util.Collections.synchronizedList(mutableListOf<String>())
    var emitting = false
    var n = 0
    org.gradle.api.logging.Logging.addOutputEventListener(
        object : org.gradle.api.logging.OutputEventListener {
            override fun onOutput(event: org.gradle.api.logging.OutputEvent?) {
                if (emitting) return
                val text = event?.toString() ?: return
                if (n < 4) {
                    emitting = true
                    sample.add(text)
                    println("::error::DIAG-EVENT[$n]=" + text
                        .replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A").take(300))
                    n++
                    emitting = false
                }
            }
        }
    )
}
