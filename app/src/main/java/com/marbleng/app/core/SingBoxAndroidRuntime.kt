package com.marbleng.app.core

import java.io.File

/** Android app-UID CLI contract. HEV/VpnService owns TUN, so sing-box must not enforce a
 * Linux interface monitor. The pinned core tolerates unavailable netlink with auto-detection
 * off (route/network.go). DNS is supplied separately by AndroidDnsBridge, NOT type:local.
 *
 * Generated configs must pass the pinned core WITHOUT deprecation escape hatches. Suppressing
 * migration failures made schema regressions look like network outages in previous builds.
 *
 * MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — tolerating the missing monitor is only half of the
 * contract: the core itself has to survive it. This object also classifies the runtime faults
 * that no config can fix (a Go panic, the app-UID package-manager probe, the netlink ban) so
 * the manager, the VPN service, the measurement plane and Bug Finder all answer "the core is
 * broken" instead of "these 17 servers are dead". */
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

    // ─────────────────────────────────────────────────────────────────────────────
    // MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — a dead core is not a dead server
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Signatures of a Go **runtime crash**, which is a fact about the binary and the device and
     * never a fact about the node being measured.
     *
     * The one MarbleNG shipped for three releases looked like this in the retained core log:
     *
     * ```
     * WARN network: initialize package manager: read packages list: open
     *      /data/system/packages.xml: permission denied
     * panic: runtime error: invalid memory address or nil pointer dereference
     * [signal SIGSEGV: segmentation violation code=0x1 addr=0x30 pc=0x5c0a469b6c]
     * ```
     *
     * `protocol/direct/outbound.go` called `InterfaceMonitor().MyInterfaces()` from
     * `Outbound.Start(StartStatePostStart)`. An app-UID child gets no interface monitor — sing-tun
     * refuses netlink on `GOOS=android` and `route.NewNetworkManager` tolerates that refusal — so
     * the call was a method on a nil interface. Every MarbleNG config carries a `direct` outbound
     * (bypass routing, rule-set downloads, the DNS bootstrap detour), so *every* start died with
     * exit status 2 — after the local inbound had already opened, which is exactly why a listening
     * port is not evidence that a core survived start-up (see [SingBoxCoreSelfTest.SETTLE_MS]).
     *
     * Upstream fixed it in `288411b0` ("Fix crash when interface monitor is unavailable",
     * SagerNet/sing-box#4498); `scripts/inject-singbox-android-fix.py` backports that commit onto
     * the pinned source and `scripts/prepare-native.sh` compiles the core here instead of shipping
     * the crashing upstream Android artifact. These markers stay: a core that crashes must be
     * reported as a broken core forever, whatever the next regression turns out to be.
     */
    private val CRASH_MARKERS: List<String> = listOf(
        "panic:",
        "fatal error:",
        "sigsegv",
        "segmentation violation",
        "invalid memory address or nil pointer dereference",
        "unexpected fault address",
        "goroutine 1 [running]",
        "goroutine stack exceeds",
        "sigabrt",
        "runtime error:"
    )

    /**
     * The Android package-manager probe the core runs unconditionally when it is a `GOOS=android`
     * build with no `PlatformInterface` (`route/network.go`:
     * `if C.IsAndroid && r.platformInterface == nil`). `/data/system/packages.xml` is
     * `0660 system:system`, so an app UID can only ever get `permission denied`.
     *
     * On its own this line is a WARN and is survivable — the core keeps going with a nil package
     * manager, and process/package rules are not something MarbleNG asks for. It matters because
     * it is the fingerprint of the process model that also produced the crash above, so a report
     * that contains it is a report about the core, not about a server.
     */
    private val PACKAGE_MANAGER_MARKERS: List<String> = listOf(
        "/data/system/packages.xml",
        "initialize package manager",
        "read packages list",
        "create package manager",
        "start package manager"
    )

    /** True when [reason] contains a Go runtime crash rather than a refused configuration. */
    fun isCoreCrash(reason: String): Boolean {
        val text = reason.lowercase()
        // Exit status 2 is what a Go panic exits with; 1 is `log.Fatal` (a refusal, which the
        // doctor already owns). A crash is identical for every candidate spelling of the same
        // node, so it must never be answered by trying the next reader or the next server.
        return CRASH_MARKERS.any { marker -> marker in text } || "exited 2:" in text
    }

    /** True when [reason] is the app-UID package-manager probe described above. */
    fun isPackageManagerFault(reason: String): Boolean {
        val text = reason.lowercase()
        return PACKAGE_MANAGER_MARKERS.any { marker -> marker in text }
    }

    /**
     * True when the fault is the *core's own* runtime contract with Android, i.e. no node, no
     * configuration and no network could have changed the outcome. This is the predicate that
     * decides whether a measurement batch keeps walking: 17 identical local faults are one fault,
     * not 17 dead servers.
     *
     * The package-manager probe is deliberately **not** part of it. That line is printed by every
     * `GOOS=android` core on every start-up, including the successful ones, because
     * `route/network.go` initialises the package manager unconditionally and only logs the
     * `permission denied` it always gets. A reason that contains it therefore says nothing about
     * why the core stopped — and treating it as fatal would have swallowed real per-node schema
     * refusals, whose log tail carries the same WARN above the actual `FATAL` line. It is evidence
     * ([isPackageManagerFault], Bug Finder's WARN, [PACKAGE_MANAGER_REMEDIATION]), not a verdict.
     */
    fun isUnusableCore(reason: String): Boolean = isCoreCrash(reason) || isNetlinkBan(reason)

    /**
     * The one-line explanation attached to a crash. It names the cause, the fix that is in this
     * build, and the one thing the user can do about a build that predates it — instead of
     * repeating a Go panic trace that reads like a server problem to everybody who is not inside
     * this file.
     */
    const val CRASH_REMEDIATION: String =
        "The sing-box core process crashed while starting (a Go panic, not a server refusal): " +
            "an app-UID child gets no netlink interface monitor on Android, and a core built " +
            "without MarbleNG's nil-monitor fix dereferences one in its `direct` outbound, so " +
            "every profile dies identically. MarbleNG compiles the core with that fix " +
            "(MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157). If this appears, the installed APK predates " +
            "it: update MarbleNG, or switch Settings → Tunnel core to Xray-core, which is " +
            "unaffected."

    /** The one-line explanation attached to the package-manager probe. */
    const val PACKAGE_MANAGER_REMEDIATION: String =
        "sing-box tried to read Android's package list (/data/system/packages.xml), which an app " +
            "process may not open. MarbleNG asks for no process/package rule, so this line alone " +
            "is harmless — but it identifies the core build that also crashed in its `direct` " +
            "outbound. Update MarbleNG, or switch Settings → Tunnel core to Xray-core."

    /**
     * MARBLE_SINGBOX_PORT_SOVEREIGNTY_V158 — True when the core died because the local SOCKS port
     * was already bound:
     *
     * ```
     * FATAL start service: start inbound/mixed[socks-in]: listen tcp 127.0.0.1:10808:
     * bind: address already in use
     * ```
     *
     * This is a fact about *this device's loopback*, not about the profile: every candidate
     * spelling of the same node binds the same port, so it must never walk the reader list
     * ([SingBoxManager.isConfigRefusal] excludes it), and it must never be recorded as a server
     * verdict. The manager reaps stale MarbleNG core processes from the port before start; a
     * conflict that still reaches the core is a holder we correctly did not signal.
     */
    fun isPortBindConflict(reason: String): Boolean {
        val text = reason.lowercase()
        // The canonical local-fault prefix both managers write (CorePortGuard's evidence and the
        // rewrite below), plus the core's own FATAL shape for a conflict that reached the bind.
        if (text.startsWith("core-port:")) return true
        return "bind: address already in use" in text ||
            ("address already in use" in text && "listen" in text)
    }

    /** The one-line explanation attached to a port the start could not bind. */
    const val PORT_REMEDIATION: String =
        "The local SOCKS port is held by another process on this device. MarbleNG reaps stale " +
            "MarbleNG core processes from the port before every start; a conflict that survives " +
            "that belongs to a different app — change Settings → local SOCKS port, stop the " +
            "other app, or restart the device."

    /**
     * The reason the product shows. A raw Go panic or an Android permission line is not an answer
     * a person can act on, so each recognised runtime fault travels with its remediation; anything
     * unrecognised is passed through verbatim, because guessing is how a real schema error once
     * got reported as a network outage.
     *
     * Idempotent: a reason is explained on the way out of the process session, again by the
     * manager that caught it and once more by the surface that prints it, and the same paragraph
     * three times reads like three separate faults.
     */
    fun explain(reason: String): String {
        val note = when {
            isNetlinkBan(reason) -> NETLINK_REMEDIATION
            isCoreCrash(reason) -> CRASH_REMEDIATION
            // Before the package-manager heuristic: the V157 packages.xml WARN is benign
            // startup evidence and rides along in reasons whose FATAL names a bind conflict
            // (the retained 09-08 12:48 line carries both). The kernel error is definitive;
            // the loose pm matcher must not outrank it.
            isPortBindConflict(reason) -> PORT_REMEDIATION
            isPackageManagerFault(reason) -> PACKAGE_MANAGER_REMEDIATION
            else -> return reason
        }
        return if (reason.contains(note)) reason else "$reason — $note"
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
