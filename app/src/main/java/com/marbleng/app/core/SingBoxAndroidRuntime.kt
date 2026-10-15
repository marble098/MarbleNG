package com.marbleng.app.core

import java.io.File

object SingBoxAndroidRuntime {
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

    val ANDROID_FORBIDDEN_INBOUND_TYPES: List<String> = listOf("tun", "redirect", "tproxy")

    val ANDROID_FORBIDDEN_RULE_KEYS: List<String> = listOf(
        "find_process",
        "find_neighbor",
        "dhcp_lease_files",
        "include_package",
        "exclude_package",
        "include_uid",
        "exclude_uid"
    )

    val SCHEMA_ROUTE_RULE_KEYS: List<String> = listOf(
        "action", "answer", "auth_user", "clash_mode", "client", "client_subnet",
        "default_interface_address", "disable_cache", "disable_optimistic_cache", "domain",
        "domain_keyword", "domain_regex", "domain_suffix", "extra", "fallback_delay", "geoip",
        "geosite", "inbound", "interface_address", "invert", "ip_cidr", "ip_is_private",
        "ip_version", "method", "network", "network_interface_address", "network_is_constrained",
        "network_is_expensive", "network_strategy", "network_type", "no_drop", "ns", "outbound",
        "override_address", "override_gateway", "override_port", "package_name",
        "package_name_regex", "port", "port_range", "preferred_by", "process_name", "process_path",
        "process_path_regex", "protocol", "race", "rcode", "remove_client_subnet", "rewrite_ttl",
        "rule_set", "rule_set_ip_cidr_match_source", "rule_set_ipcidr_match_source", "rules",
        "server", "sniffer", "source_geoip", "source_hostname", "source_ip_cidr",
        "source_ip_is_private", "source_mac_address", "source_port", "source_port_range",
        "speculative", "strategy", "tag", "timeout", "tls_fragment", "tls_fragment_fallback_delay",
        "tls_record_fragment", "tls_spoof", "tls_spoof_method", "type", "udp_connect",
        "udp_disable_domain_unmapping", "udp_timeout", "user", "user_id", "wifi_bssid", "wifi_ssid"
    )

    val ANDROID_FORBIDDEN_DNS_TYPES: List<String> = listOf("dhcp")

    val DEPRECATION_ENV: Map<String, String> = emptyMap()

    fun isNetlinkBan(reason: String): Boolean {
        val text = reason.lowercase()
        return "netlink socket" in text && ("banned" in text || "android" in text)
    }

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

    private val PACKAGE_MANAGER_MARKERS: List<String> = listOf(
        "/data/system/packages.xml",
        "initialize package manager",
        "read packages list",
        "create package manager",
        "start package manager"
    )

    fun isCoreCrash(reason: String): Boolean {
        val text = reason.lowercase()

        return CRASH_MARKERS.any { marker -> marker in text } || "exited 2:" in text
    }

    fun isPackageManagerFault(reason: String): Boolean {
        val text = reason.lowercase()
        return PACKAGE_MANAGER_MARKERS.any { marker -> marker in text }
    }

    fun isUnusableCore(reason: String): Boolean = isCoreCrash(reason) || isNetlinkBan(reason)

    const val CRASH_REMEDIATION: String =
        "The sing-box core process crashed while starting (a Go panic, not a server refusal): " +
            "an app-UID child gets no netlink interface monitor on Android, and a core built " +
            "without MarbleNG's nil-monitor fix dereferences one in its `direct` outbound, so " +
            "every profile dies identically. MarbleNG compiles the core with that fix " +
            "(MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157). If this appears, the installed APK predates " +
            "it: update MarbleNG, or switch Settings → Tunnel core to Xray-core, which is " +
            "unaffected."

    const val PACKAGE_MANAGER_REMEDIATION: String =
        "sing-box tried to read Android's package list (/data/system/packages.xml), which an app " +
            "process may not open. Every GOOS=android core prints this once per start, including " +
            "the patched one MarbleNG ships (MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157), and MarbleNG " +
            "asks for no process/package rule, so the line alone is harmless. It only matters " +
            "next to a `panic:`/SIGSEGV in the same log — that combination is reported separately " +
            "as a crashed core."

    fun isPortBindConflict(reason: String): Boolean {
        val text = reason.lowercase()

        if (text.startsWith("core-port:")) return true
        return "bind: address already in use" in text ||
            ("address already in use" in text && "listen" in text)
    }

    const val PORT_REMEDIATION: String =
        "The local SOCKS port is held by another process on this device. MarbleNG reaps stale " +
            "MarbleNG core processes from the port before every start; a conflict that survives " +
            "that belongs to a different app — change Settings → local SOCKS port, stop the " +
            "other app, or restart the device."

    fun explain(reason: String): String {
        val note = when {
            isNetlinkBan(reason) -> NETLINK_REMEDIATION
            isCoreCrash(reason) -> CRASH_REMEDIATION

            isPortBindConflict(reason) -> PORT_REMEDIATION

            isStartupTimeout(reason) -> STARTUP_TIMEOUT_REMEDIATION
            isPackageManagerFault(reason) -> PACKAGE_MANAGER_REMEDIATION
            else -> return reason
        }
        return if (reason.contains(note)) reason else "$reason — $note"
    }

    fun isStartupTimeout(reason: String): Boolean =
        reason.trim().lowercase().startsWith("core-start-timeout")

    const val STARTUP_TIMEOUT_REMEDIATION: String =
        "the core did not finish starting inside MarbleNG's start-up window, and its own log is " +
            "the only witness: a tail that holds nothing but the Android package-list warning " +
            "means the process came up and then neither opened its local inbound nor answered " +
            "its controller, which no profile and no server can cause. Retry once; if it " +
            "repeats, switch Settings → Tunnel core to Xray-core (unaffected) and send Bug " +
            "Finder's SINGBOX section with this report."

    const val NETLINK_REMEDIATION: String =
        "sing-box asked Android for a netlink interface monitor, which app processes may not " +
            "open. MarbleNG removes every option that requires one (auto_detect_interface and " +
            "friends) before the core is started; a config that still requests it cannot run " +
            "without root/ADB."

    fun prepare(builder: ProcessBuilder, workingDir: File?, tempDir: File?): ProcessBuilder {
        val environment = builder.environment()
        environment.keys.filter { it.startsWith("ENABLE_DEPRECATED_") }.forEach(environment::remove)

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
