package com.marbleng.app.core

object CoreFailurePolicy {
    private val LOCAL_PREFIXES = listOf(
        "config-unsupported:", "core-config:", "core-install:", "core-assets:",
        "core-busy:", "core-check", "core-start", "core-port:", "core-unavailable:",
        "core-crash:", "core-selftest:",
        "urltest-url:", "urltest-no-target", "urltest-http-400:", "urltest-http-401:",
        "xray-start", "singbox-start", "no-live-tunnel"
    )

    fun isLocal(reason: String): Boolean {
        val value = reason.lowercase()

        return LOCAL_PREFIXES.any { value.startsWith(it) } ||
            SingBoxAndroidRuntime.isNetlinkBan(reason)
    }
}
