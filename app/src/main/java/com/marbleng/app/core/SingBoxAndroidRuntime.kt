package com.marbleng.app.core

import java.io.File

/**
 * MARBLE_SINGBOX_ANDROID_RUNTIME_V155 — the execution contract for running the sing-box extended
 * **CLI** as a child process on Android.
 *
 * ## Why this file exists
 *
 * The official sing-box Android client (SFA) never spawns `sing-box`. It links `libbox` and hands
 * the core a **PlatformInterface** implemented on top of Android's `ConnectivityManager`, so
 * `route.NewNetworkManager` takes the `usePlatformDefaultInterfaceMonitor` branch and never opens
 * a netlink socket. MarbleNG ships the upstream *release binary* instead (one process per engine,
 * fed by `hev-socks5-tunnel`), which means `platformInterface == nil` and the core falls back to
 * the Linux monitors. Android ≥ 11 blocks `AF_NETLINK`/`NETLINK_ROUTE` for app UIDs, and
 * `sing-tun` turns that into a hard error:
 *
 * ```
 * netlink socket in Android is banned by Google, use the root or system (ADB) user to run
 * sing-box, or switch to the sing-box Android graphical interface client
 * ```
 *
 * Reading `route/network.go` of the pinned core (`shtorm-7/sing-box-extended
 * v1.14.0-extended-2.7.1`) shows exactly when that error is fatal:
 *
 * ```go
 * enforceInterfaceMonitor := options.AutoDetectInterface
 * if !usePlatformDefaultInterfaceMonitor {
 *     networkMonitor, err := tun.NewNetworkUpdateMonitor(logger)
 *     if !((err != nil && !enforceInterfaceMonitor) || errors.Is(err, os.ErrInvalid)) {
 *         if err != nil { return nil, E.Cause(err, "create network monitor") }
 *         ...
 * ```
 *
 * A banned netlink socket is **tolerated** — the core simply runs without an interface monitor —
 * unless something in the config *enforces* one. So the netlink FATAL is not an Android
 * limitation MarbleNG has to live with: it is a config bug, and [ANDROID_FORBIDDEN_ROUTE_KEYS]
 * plus [SingBoxConfigDoctor.hardenForAndroid] is the fix. A rooted device or ADB shell is only
 * needed by configs that genuinely want interface monitoring (a `tun` inbound with `auto_route`,
 * `default_network_strategy`, DHCP DNS…), none of which MarbleNG uses: the TUN belongs to
 * Android's own `VpnService` and the core only ever sees a local `mixed` inbound.
 *
 * ## The second half: impending deprecations are `os.Exit(1)`
 *
 * `experimental/deprecated` classifies every deprecated option with a *scheduled removal*
 * version. Once `ScheduledVersion.Minor - Version.Minor <= 1`, `stderrManager.ReportDeprecated`
 * stops warning and calls `logger.Fatal(…)`, which is `os.Exit(1)` in `log/observable.go`:
 *
 * ```go
 * f.logger.Error(feature.MessageWithLink())
 * f.logger.Fatal("to continuing using this feature, set environment variable ENABLE_DEPRECATED_" +
 *     feature.EnvName + "=true")
 * ```
 *
 * On the pinned 1.14 core that already applies to `missing route.default_domain_resolver`,
 * legacy domain-strategy options and outbound DNS rule items — every one of which kills
 * `sing-box check` **and** `sing-box run` before a single packet moves. MarbleNG writes a config
 * that carries none of them ([SingBoxConfigBuilder]), but a hand-edited profile, a future core
 * bump or a deprecation MarbleNG has not seen yet must never be able to take the engine down.
 * [DEPRECATION_ENV] is the documented escape hatch, applied to every child process: an impending
 * deprecation degrades back to a warning in the log instead of an exit code.
 */
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
        "dhcp_lease_files"
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
        "protect_path"
    )

    /** Inbound types the CLI must never be given: Android's `VpnService` owns the TUN. */
    val ANDROID_FORBIDDEN_INBOUND_TYPES: List<String> = listOf("tun", "redirect", "tproxy")

    /**
     * DNS transports that need the banned netlink socket (`dhcp` walks the interface table to
     * find the lease) — the config doctor drops them rather than letting the core die.
     */
    val ANDROID_FORBIDDEN_DNS_TYPES: List<String> = listOf("dhcp")

    /**
     * `ENABLE_DEPRECATED_<EnvName>` for every note in the pinned core's
     * `experimental/deprecated/constants.go`. Setting them all to `true` is exactly what the
     * core's own fatal message asks for, and it is a no-op for any option MarbleNG does not
     * write — the manager reports nothing it does not see.
     */
    val DEPRECATION_ENV: Map<String, String> = listOf(
        "OUTBOUND_DNS_RULE_ITEM",
        "MISSING_DOMAIN_RESOLVER",
        "LEGACY_DOMAIN_STRATEGY_OPTIONS",
        "INLINE_ACME_OPTIONS",
        "LEGACY_RULE_SET_DOWNLOAD_DETOUR",
        "DNS_RULE_RULE_SET_IP_CIDR_ACCEPT_EMPTY",
        "LEGACY_DNS_ADDRESS_FILTER",
        "LEGACY_DNS_RULE_STRATEGY",
        "INDEPENDENT_DNS_CACHE",
        "STORE_RDRC",
        "IMPLICIT_DEFAULT_HTTP_CLIENT"
    ).associate { "ENABLE_DEPRECATED_$it" to "true" }

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

    /**
     * Prepares [builder] to run a sing-box extended child process from an Android app UID.
     *
     *  - every `ENABLE_DEPRECATED_*` flag, so an impending deprecation is a warning, not
     *    `os.Exit(1)`;
     *  - `TMPDIR` pointed at the app's own cache: Go's `os.TempDir()` returns `/tmp` on Android,
     *    which does not exist, so anything the core spools (rule-set downloads, cache writes)
     *    would fail with `no such file or directory`;
     *  - `HOME` for the same reason — the core resolves `~` for its working directory.
     */
    fun prepare(builder: ProcessBuilder, workingDir: File?, tempDir: File?): ProcessBuilder {
        val environment = builder.environment()
        DEPRECATION_ENV.forEach { (key, value) -> environment[key] = value }
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
