package com.marbleng.app.core

/** Local runtime/configuration errors are not observations of the remote route. Use the same
 * boundary in history, ranking and presentation so a broken APK cannot poison every profile. */
object CoreFailurePolicy {
    fun isLocal(reason: String): Boolean {
        val value = reason.lowercase()
        return listOf("config-unsupported:", "core-config:", "core-install:", "core-assets:",
            "core-busy:", "core-check", "core-start", "core-port:", "core-unavailable:",
            "urltest-url:", "urltest-no-target", "urltest-http-400:", "urltest-http-401:",
            "xray-start", "singbox-start", "no-live-tunnel").any { value.startsWith(it) } ||
            SingBoxAndroidRuntime.isNetlinkBan(reason)
    }
}
