package com.marbleng.app.core

/** Local runtime/configuration errors are not observations of the remote route. Use the same
 * boundary in history, ranking and presentation so a broken APK cannot poison every profile. */
object CoreFailurePolicy {

    /**
     * `core-crash:` and `core-selftest:` are MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 additions: the
     * reason the self-test in [SingBoxCoreSelfTest] reports when the binary itself cannot start.
     * Those two are the difference between "these 17 servers are down" (which the measurement
     * plane used to print, because a crashed core produced no delay and no other verdict) and
     * "this device cannot run this core". A crash that reaches here from a real child still
     * arrives as `core-start: exited 2: …`, which the list already covers.
     */
    private val LOCAL_PREFIXES = listOf(
        "config-unsupported:", "core-config:", "core-install:", "core-assets:",
        "core-busy:", "core-check", "core-start", "core-port:", "core-unavailable:",
        "core-crash:", "core-selftest:",
        "urltest-url:", "urltest-no-target", "urltest-http-400:", "urltest-http-401:",
        "xray-start", "singbox-start", "no-live-tunnel"
    )

    fun isLocal(reason: String): Boolean {
        val value = reason.lowercase()
        // Prefixes only, deliberately: a URL-test reason can carry a server's own response body,
        // and classifying a remote string as a local fault would silently drop real observations.
        return LOCAL_PREFIXES.any { value.startsWith(it) } ||
            SingBoxAndroidRuntime.isNetlinkBan(reason)
    }
}
