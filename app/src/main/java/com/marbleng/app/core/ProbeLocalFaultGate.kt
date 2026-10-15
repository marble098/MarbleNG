package com.marbleng.app.core

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ProbeLocalFaultGate {
    private val tripped = AtomicBoolean(false)
    private val firstReason = AtomicReference("")

    val isTripped: Boolean get() = tripped.get()

    val reason: String get() = firstReason.get()

    fun trip(reason: String, success: Int): Boolean {
        if (!isSweepFatal(reason, success)) return false
        firstReason.compareAndSet("", reason)
        return tripped.compareAndSet(false, true)
    }

    fun tripNow(reason: String): Boolean {
        firstReason.compareAndSet("", reason)
        return tripped.compareAndSet(false, true)
    }

    fun reset() {
        tripped.set(false)
        firstReason.set("")
    }

    fun mayStart(): Boolean = !tripped.get()

    val shouldStop: () -> Boolean = { tripped.get() }

    fun isSweepFatal(reason: String, success: Int): Boolean {
        if (success > 0 || reason.isBlank()) return false
        val value = reason.lowercase()

        if (value.startsWith("core-config:") || value.startsWith("config-unsupported:")) return false

        if (value.startsWith("core-busy:") ||
            value.startsWith("core-start-timeout:") ||
            value.startsWith("core-check-timeout:")
        ) {
            return false
        }

        if (SingBoxAndroidRuntime.isUnusableCore(reason)) return true
        return FATAL_PREFIXES.any { value.startsWith(it) }
    }

    companion object {
        private val FATAL_PREFIXES = listOf(
            "core-install:",
            "core-assets:",
            "core-crash:",
            "core-selftest:",
            "core-exit:",
            "core-port:",
            "core-unavailable:",
            "no-live-tunnel",
            "singbox-unavailable",
            "urltest-requires-singbox"
        )

        fun summary(reason: String): String = when {
            SingBoxAndroidRuntime.isCoreCrash(reason) ->
                "the tunnel core crashed while starting"
            SingBoxAndroidRuntime.isPackageManagerFault(reason) ->
                "the tunnel core could not read Android's package list"
            SingBoxAndroidRuntime.isNetlinkBan(reason) ->
                "the tunnel core asked Android for a netlink monitor an app process may not open"
            reason.startsWith("core-install:") ->
                "the tunnel core binary is missing from this build"
            reason.startsWith("core-assets:") ->
                "the tunnel core's bundled rule sets are missing or corrupt"
            reason.startsWith("core-port:") ->
                "Android would not allocate loopback ports for a measurement core"
            reason.startsWith("no-live-tunnel") ||
                reason.startsWith("singbox-unavailable") ||
                reason.startsWith("core-unavailable:") ->
                "there is no live tunnel to measure through"
            else -> headline(reason)
        }

        fun headline(reason: String): String {
            val withoutRemedy = REMEDIATIONS.fold(reason) { text, remedy ->
                val index = text.indexOf(" — $remedy")
                if (index >= 0) text.substring(0, index) else text
            }
            val lines = withoutRemedy.lines()
            val fatal = lines.firstOrNull { line ->
                val value = line.trim().lowercase()
                value.startsWith("panic:") || value.startsWith("fatal error:") ||
                    value.startsWith("[signal ")
            }
            val line = (fatal ?: lines.firstOrNull { it.isNotBlank() } ?: withoutRemedy).trim()
            return if (line.length > 200) line.take(200).trimEnd() + "…" else line
        }

        private val REMEDIATIONS = listOf(
            SingBoxAndroidRuntime.CRASH_REMEDIATION,
            SingBoxAndroidRuntime.PACKAGE_MANAGER_REMEDIATION,
            SingBoxAndroidRuntime.NETLINK_REMEDIATION,
            SingBoxAndroidRuntime.STARTUP_TIMEOUT_REMEDIATION
        )
    }
}
