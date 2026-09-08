package com.marbleng.app.core

import java.io.File

/** Android app-UID CLI contract. HEV/VpnService owns TUN, so sing-box must not enforce a
 * Linux interface monitor. The pinned core tolerates unavailable netlink with auto-detection
 * off (route/network.go). DNS is supplied separately by AndroidDnsBridge, NOT type:local.
 *
 * Generated configs must pass the pinned core WITHOUT deprecation escape hatches. Suppressing
 * migration failures made schema regressions look like network outages in previous builds. */
object SingBoxAndroidRuntime {

    /**
     * `route.*` keys that make the core demand a netlink-backed interface monitor, or that only
     * mean something for a core that owns the TUN itself. Every one of them is fatal or useless
     * inside MarbleNG's process model, where Android's `VpnService` owns the tunnel.
     *
     *  - `auto_detect_interface` — the single key that sets `enforceInterfaceMonitor`.
     *  - `default_network_strategy` / `default_network_type` / `default_fallback_network_type` /
     *    `default_fallback_delay` — `NewNetworkManager` rejects a network strategy without
     *    `auto_detect_interface`, so they can only ever arrive as a pair with the fatal key.
     *  - `default_interface` / `default_mark` — bind/mark the socket through the interface finder.
     *  - `override_android_vpn` — an option of the interface monitor that does not exist here.
     *  - `find_process` / `find_neighbor` / `dhcp_lease_files` — process/neighbour lookup needs
     *    the Android package manager and netlink; unavailable to an app-UID child process.
     */
    val ANDROID_FORBIDDEN_ROUTE_KEYS: List<String> = listOf(
    "auto_detect_interface",
    "default_network_strategy",
    "default_network_type",
    "default_fallback_network_type",
    "default_fallback_delay",
    "default_interface",
    "default_mark",
    "override_android_vpn",
    "find_process",
    "find_neighbor",
    "dhcp_lease_files",
    "include_package",
    "exclude_package",
    "include_uid",
    "exclude_uid"
)

    /**
     * Dial fields with the same problem one level down: they are resolved through the interface
     * finder / network manager the core cannot build on Android. `protect_path` is the SFA-only
     * socket-protect channel, which a CLI child has no server for.
     */
    val ANDROID_FORBIDDEN_DIAL_KEYS: List<String> = listOf(
    "bind_interface",
    "routing_mark",
    "network_strategy",
    "network_type",
    "fallback_network_type",
    "protect_path",
    "find_process",
    "find_neighbor",
    "include_package",
    "exclude_package",
    "include_uid",
    "exclude_uid"
)

    /** Inbound types the CLI must never be given: Android's `VpnService` owns the TUN. */
    val ANDROID_FORBIDDEN_INBOUND_TYPES: List<String> = listOf("tun", "redirect", "tproxy")

    /**
     * MARBLE_PACKAGES_XML_ROOT_CAUSE_V157 — the process/package matchers as they actually appear
     * in the wild: as fields on an individual `route.rules[]` object (and on an inbound's
     * sniff/policy block), not as top-level `route.*` keys. [ANDROID_FORBIDDEN_ROUTE_KEYS] and
     * [ANDROID_FORBIDDEN_DIAL_KEYS] each carry their own copy of the package/uid/process subset
     * for their own shape of object; this is the single canonical list for every *per-rule* or
     * *per-inbound* callsite, so a new caller can no longer hand-copy a partial subset of it and
     * silently drop one.
     *
     * Any one of these on Android forces the core to resolve installed packages through its own
     * platform `PackageManager` shim, which on a non-rooted app-UID process fails with
     * `initialize package manager: read packages list: open /data/system/packages.xml:
     * permission denied` — a fatal the pinned core does not nil-check before using the result,
     * so the process dies with `panic: runtime error: invalid memory address or nil pointer
     * dereference` / `SIGSEGV`. There is no code path back from that: the fix is to guarantee the
     * key never reaches the core at all, on every candidate a rule can appear in.
     */
    val ANDROID_FORBIDDEN_RULE_KEYS: List<String> = listOf(
        "find_process",
        "find_neighbor",
        "dhcp_lease_files",
        "include_package",
        "exclude_package",
        "include_uid",
        "exclude_uid"
    )

    /**
     * DNS transports that need the banned netlink socket (`dhcp` walks the interface table to
     * find the lease) — the config doctor drops them rather than letting the core die.
     */
    val ANDROID_FORBIDDEN_DNS_TYPES: List<String> = listOf("dhcp")

    /** Kept as diagnostic metadata: no deprecated features are enabled by this runtime. */
    val DEPRECATION_ENV: Map<String, String> = emptyMap()

    /**
     * True when [reason] is the Android netlink ban rather than any other startup failure. The
     * VPN service and the Engine page use it to print a remediation the user can act on instead
     * of a Go error nobody outside this file can read.
     */
    fun isNetlinkBan(reason: String): Boolean {
        val text = reason.lowercase()
        return "netlink socket" in text && ("banned" in text || "android" in text)
    }

    /**
     * The one-line explanation shown when [isNetlinkBan] matches. It names the cause (a config
     * key that forces an interface monitor) rather than repeating the core's "use root or ADB"
     * advice, which does not apply to a config MarbleNG controls.
     */
    const val NETLINK_REMEDIATION: String =
        "sing-box asked Android for a netlink interface monitor, which app processes may not " +
            "open. MarbleNG removes every option that requires one (auto_detect_interface and " +
            "friends) before the core is started; a config that still requests it cannot run " +
            "without root/ADB."

    /** Writable Android paths, bounded Go heap target, and no inherited legacy escape hatches. */
    fun prepare(builder: ProcessBuilder, workingDir: File?, tempDir: File?): ProcessBuilder {
        val environment = builder.environment()
        environment.keys.filter { it.startsWith("ENABLE_DEPRECATED_") }.forEach(environment::remove)
        // This is a soft GC target, not an OS memory cap. Bounded temporary process concurrency
        // and log buffers remain necessary; never infer a leak just from a LOW_MEMORY exit.
        environment["GOMEMLIMIT"] = "96MiB"
        tempDir?.let { dir ->
            runCatching { dir.mkdirs() }
            environment["TMPDIR"] = dir.absolutePath
        }
        workingDir?.let { dir ->
            runCatching { dir.mkdirs() }
            environment["HOME"] = dir.absolutePath
            runCatching { builder.directory(dir) }
        }
        return builder
    }
}
