package com.marbleng.app.core

import org.json.JSONArray
import org.json.JSONObject

object SingBoxConfigDoctor {
    private val REMOVED_OUTBOUND_TYPES = setOf("dns")

    private val CONFIG_FAULT_MARKERS = listOf(
        "rejected the config",
        "deprecated in sing-box",
        "removed in sing-box",
        "decode config",
        "parse config",
        "invalid config",
        "unknown outbound",
        "unknown field",
        "cannot unmarshal",
        "cannot unmarshal array",
        "json: cannot",
        "wrong type for field",
        "invalid configuration",
        "netlink socket",
        "banned by google",
        "initialize network manager",
        "create network monitor",
        "independent_cache",

        "domain_resolver",
        "default domain resolver not found",
        "enable_deprecated_",
        "is conflict with",
        "unknown transport type",
        "only supported on",

        "initialize package manager",
        "read packages list",
        "packages.xml",
        "invalid memory address or nil pointer dereference"
    )

    data class Repair(
        val json: String,
        val repaired: Boolean,
        val notes: List<String>
    )

    @JvmOverloads
    fun repair(json: String, rejection: String = ""): Repair {
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: return Repair(json, false, emptyList())
        val notes = mutableListOf<String>()

        val outbounds = root.optJSONArray("outbounds")
        if (outbounds != null) {
            val kept = JSONArray()
            val removedTags = mutableSetOf<String>()
            for (i in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(i) ?: continue
                val type = outbound.optString("type").lowercase()
                if (type in REMOVED_OUTBOUND_TYPES) {
                    removedTags += outbound.optString("tag").ifBlank { type }
                    continue
                }
                kept.put(outbound)
            }
            if (removedTags.isNotEmpty()) {
                root.put("outbounds", kept)
                notes += "removed deprecated outbound(s): ${removedTags.joinToString(", ")} " +
                    "(rule actions replace them)"
                stripReferencesTo(root, removedTags, notes)
            }
        }

        migrateDnsServerAddressKey(root, notes)
        migrateNetworkAndTlsFields(root, notes)

        harden(root, notes)

        if (notes.isEmpty()) return Repair(json, false, emptyList())
        return Repair(root.toString(), true, notes)
    }

    fun hardenForAndroid(json: String): Repair {
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: return Repair(json, false, emptyList())
        val notes = mutableListOf<String>()
        harden(root, notes)
        if (notes.isEmpty()) return Repair(json, false, emptyList())
        return Repair(root.toString(), true, notes)
    }

    private fun harden(root: JSONObject, notes: MutableList<String>) {
        migrateDnsOptions(root, notes)
        migrateRouteOptions(root, notes)
        stripAndroidUnsafeRouteOptions(root, notes)
        stripAndroidUnsafeRuleFields(root, notes)
        stripAndroidUnsafeDialOptions(root, notes)
        stripAndroidUnsafeInbounds(root, notes)
        stripAndroidUnsafeDnsServers(root, notes)
        migrateCacheFileOptions(root, notes)
        migrateRuleSetDownloadDetour(root, notes)
        ensureDefaultDomainResolver(root, notes)
    }

    private fun stripReferencesTo(root: JSONObject, removedTags: Set<String>, notes: MutableList<String>) {
        val route = root.optJSONObject("route") ?: return
        val rules = route.optJSONArray("rules") ?: return
        val kept = JSONArray()
        var dropped = 0
        var hijacked = 0
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            val outbound = rule.optString("outbound")
            if (outbound !in removedTags) {
                kept.put(rule)
                continue
            }
            val protocols = rule.optJSONArray("protocol")
            val isDnsHijack = protocols != null &&
                (0 until protocols.length()).any { protocols.optString(it).equals("dns", true) }
            if (isDnsHijack) {
                kept.put(
                    JSONObject()
                        .put("protocol", JSONArray().put("dns"))
                        .put("action", "hijack-dns")
                )
                hijacked++
                continue
            }
            dropped++

            val pruned = JSONObject()
            rule.keys().forEach { key -> if (key != "outbound") pruned.put(key, rule.get(key)) }
            if (pruned.length() > 0) kept.put(pruned)
        }
        if (hijacked > 0 || dropped > 0) {
            route.put("rules", kept)
            if (hijacked > 0) {
                notes += "converted $hijacked dns-route rule(s) to the hijack-dns action"
            }
            if (dropped > 0) {
                notes += "dropped $dropped route rule reference(s) to removed outbound(s)"
            }
        }
    }

    private fun migrateDnsServerAddressKey(root: JSONObject, notes: MutableList<String>) {
        val dns = root.optJSONObject("dns") ?: return
        val servers = dns.optJSONArray("servers") ?: return
        var migrated = 0
        for (i in 0 until servers.length()) {
            val server = servers.optJSONObject(i) ?: continue
            if (server.has("address") && !server.has("server")) {
                server.put("server", server.optString("address"))
                server.remove("address")
                migrated++
            }
        }
        if (migrated > 0) {
            notes += "migrated $migrated DNS server(s) from the pre-1.12 `address` key to `server`"
        }
    }

    private fun migrateDnsOptions(root: JSONObject, notes: MutableList<String>) {
        val dns = root.optJSONObject("dns") ?: return
        if (dns.has("independent_cache")) {
            dns.remove("independent_cache")
            notes += "removed deprecated `independent_cache` DNS option"
        }
    }

    private fun migrateRouteOptions(root: JSONObject, notes: MutableList<String>) {
        val route = root.optJSONObject("route") ?: return
        if (route.has("auto_detect_interface")) {
            route.remove("auto_detect_interface")
            notes += "removed `auto_detect_interface` (avoiding banned Android netlink socket)"
        }
    }

    private fun stripAndroidUnsafeRouteOptions(root: JSONObject, notes: MutableList<String>) {
        val route = root.optJSONObject("route") ?: return
        val removed = SingBoxAndroidRuntime.ANDROID_FORBIDDEN_ROUTE_KEYS.filter { key ->

            route.has(key) && isRequesting(route.opt(key))
        }
        removed.forEach { route.remove(it) }
        if (removed.isNotEmpty()) {
            notes += "removed route option(s) that require an Android-banned netlink interface " +
                "monitor: ${removed.joinToString(", ")}"
        }
    }

    private fun stripAndroidUnsafeRuleFields(root: JSONObject, notes: MutableList<String>) {
        val rules = root.optJSONObject("route")?.optJSONArray("rules") ?: return
        val removedFrom = mutableListOf<String>()
        stripRuleArray(rules, "route.rules", removedFrom)
        if (removedFrom.isNotEmpty()) {
            notes += "removed Android-unsafe package/uid/process matcher(s) from ${removedFrom.size} " +
                "route rule(s) (would have forced a packages.xml read and crashed the core): " +
                removedFrom.joinToString(", ")
        }
    }

    private fun stripRuleArray(rules: JSONArray, path: String, removedFrom: MutableList<String>) {
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            val here = "$path[$i]"
            var removedHere = false
            SingBoxAndroidRuntime.ANDROID_FORBIDDEN_RULE_KEYS.forEach { key ->
                if (rule.has(key)) {
                    rule.remove(key)
                    removedHere = true
                }
            }
            if (removedHere) removedFrom += here
            rule.optJSONArray("rules")?.let { nested -> stripRuleArray(nested, "$here.rules", removedFrom) }
        }
    }

    private fun stripAndroidUnsafeDialOptions(root: JSONObject, notes: MutableList<String>) {
        val outbounds = root.optJSONArray("outbounds") ?: return
        var count = 0
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue
            SingBoxAndroidRuntime.ANDROID_FORBIDDEN_DIAL_KEYS.forEach { key ->
                if (outbound.has(key) && isRequesting(outbound.opt(key))) {
                    outbound.remove(key)
                    count++
                }
            }
        }
        if (count > 0) {
            notes += "removed $count interface-bound dial field(s) unavailable to an Android app " +
                "process"
        }
    }

    private fun stripAndroidUnsafeInbounds(root: JSONObject, notes: MutableList<String>) {
        val inbounds = root.optJSONArray("inbounds") ?: return
        val kept = JSONArray()
        val dropped = mutableListOf<String>()
        var strippedSniff = 0
        for (i in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(i) ?: continue
            val type = inbound.optString("type").lowercase()
            if (type in SingBoxAndroidRuntime.ANDROID_FORBIDDEN_INBOUND_TYPES) {
                dropped += type
                continue
            }
            if (inbound.has("sniff") || inbound.has("sniff_override_destination")) {
                inbound.remove("sniff")
                inbound.remove("sniff_override_destination")
                strippedSniff++
            }
            kept.put(inbound)
        }
        if (dropped.isNotEmpty()) {
            root.put("inbounds", kept)
            notes += "removed ${dropped.joinToString(", ")} inbound(s): the Android VpnService " +
                "owns the tunnel, the core only serves the local mixed inbound"
        }
        if (strippedSniff > 0) {
            notes += "removed legacy inbound-level sniff fields from $strippedSniff inbound(s) " +
                "(deprecated in sing-box 1.11.0, removed in 1.13.0; route actions replace them)"
        }
    }

    private fun stripAndroidUnsafeDnsServers(root: JSONObject, notes: MutableList<String>) {
        val dns = root.optJSONObject("dns") ?: return
        val servers = dns.optJSONArray("servers") ?: return
        val kept = JSONArray()
        val droppedTags = mutableSetOf<String>()
        for (i in 0 until servers.length()) {
            val server = servers.optJSONObject(i) ?: continue
            val type = server.optString("type").lowercase()
            if (type in SingBoxAndroidRuntime.ANDROID_FORBIDDEN_DNS_TYPES) {
                droppedTags += server.optString("tag").ifBlank { type }
                continue
            }
            kept.put(server)
        }
        if (droppedTags.isEmpty()) return
        dns.put("servers", kept)
        notes += "removed DNS transport(s) that need netlink on Android: " +
            droppedTags.joinToString(", ")

        val rules = dns.optJSONArray("rules") ?: return
        val keptRules = JSONArray()
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            if (rule.optString("server") in droppedTags) continue
            keptRules.put(rule)
        }
        dns.put("rules", keptRules)
        if (dns.optString("final") in droppedTags) dns.remove("final")
    }

    private fun migrateCacheFileOptions(root: JSONObject, notes: MutableList<String>) {
        val cacheFile = root.optJSONObject("experimental")?.optJSONObject("cache_file") ?: return
        if (!cacheFile.has("store_rdrc")) return
        val enabled = cacheFile.optBoolean("store_rdrc", false)
        cacheFile.remove("store_rdrc")
        if (enabled && !cacheFile.has("store_dns")) cacheFile.put("store_dns", true)
        notes += "migrated the deprecated `store_rdrc` cache-file option to `store_dns`"
    }

    private fun migrateRuleSetDownloadDetour(root: JSONObject, notes: MutableList<String>) {
        val ruleSets = root.optJSONObject("route")?.optJSONArray("rule_set") ?: return
        var migrated = 0
        var conflicts = 0
        for (i in 0 until ruleSets.length()) {
            val ruleSet = ruleSets.optJSONObject(i) ?: continue
            if (!ruleSet.has("download_detour")) continue
            val detour = ruleSet.optString("download_detour")
            ruleSet.remove("download_detour")
            if (ruleSet.optJSONObject("http_client") != null) {
                conflicts++
                continue
            }
            if (detour.isNotBlank()) {
                ruleSet.put("http_client", JSONObject().put("detour", detour))
            }
            migrated++
        }
        if (migrated > 0) {
            notes += "migrated $migrated rule-set(s) from the deprecated `download_detour` to " +
                "`http_client`"
        }
        if (conflicts > 0) {
            notes += "dropped $conflicts conflicting `download_detour` field(s) (the rule-set " +
                "already carries an `http_client`)"
        }
    }

    private fun ensureDefaultDomainResolver(root: JSONObject, notes: MutableList<String>) {
        val servers = root.optJSONObject("dns")?.optJSONArray("servers") ?: return
        val route = root.optJSONObject("route") ?: JSONObject().also { root.put("route", it) }
        val byTag = (0 until servers.length()).mapNotNull { servers.optJSONObject(it) }
            .associateBy { it.optString("tag") }
        fun encryptedDirect(tag: String, seen: Set<String> = emptySet()): Boolean {
            if (tag.isBlank() || tag in seen) return false
            val server = byTag[tag] ?: return false
            return when (server.optString("type").lowercase()) {
                "fallback" -> {
                    val peers = server.optJSONArray("servers") ?: return false
                    peers.length() > 0 && (0 until peers.length()).all {
                        encryptedDirect(peers.optString(it), seen + tag)
                    }
                }
                "https", "tls", "h3", "quic" ->
                    server.optString("detour") == SingBoxConfigBuilder.DIRECT_TAG &&
                        AddressFamilyPolicy.isLiteralIp(server.optString("server"))
                else -> false
            }
        }
        val existing = route.opt("default_domain_resolver")
        val currentTag = when (existing) {
            is String -> existing
            is JSONObject -> existing.optString("server")
            else -> ""
        }
        val chosen = currentTag.takeIf(::encryptedDirect)
            ?: listOf(SingBoxConfigBuilder.DNS_BOOTSTRAP_TAG, SingBoxConfigBuilder.DNS_DIRECT_TAG)
                .firstOrNull { encryptedDirect(it) }
            ?: byTag.keys.firstOrNull { encryptedDirect(it) }
            ?: throw IllegalArgumentException("No encrypted IP-literal direct DNS bootstrap; refusing system DNS fallback")
        fun families(tag: String): List<Boolean> {
            val server = byTag.getValue(tag)
            if (server.optString("type") != "fallback") return listOf(server.getString("server").contains(':'))
            val peers = server.getJSONArray("servers")
            return (0 until peers.length()).flatMap { families(peers.getString(it)) }
        }
        val options = families(chosen)
        val preferred = options.firstOrNull() == true
        val desiredStrategy = when {
            options.all { it } && root.optJSONObject("dns")?.optString("strategy") == "ipv6_only" -> "ipv6_only"
            options.none { it } && root.optJSONObject("dns")?.optString("strategy") == "ipv4_only" -> "ipv4_only"
            preferred -> "prefer_ipv6"
            else -> "prefer_ipv4"
        }
        if (existing is JSONObject && currentTag == chosen &&
            existing.optString("strategy") == desiredStrategy) return

        route.put("default_domain_resolver", JSONObject()
            .put("server", chosen).put("strategy", desiredStrategy))
        notes += "set encrypted `route.default_domain_resolver` to `$chosen` ($desiredStrategy)"
    }

    private fun isRequesting(value: Any?): Boolean = when (value) {
        null, JSONObject.NULL -> false
        is Boolean -> value
        is Number -> value.toDouble() != 0.0
        is String -> value.isNotBlank()
        is JSONArray -> value.length() > 0
        is JSONObject -> value.length() > 0
        else -> true
    }

    private fun migrateNetworkAndTlsFields(root: JSONObject, notes: MutableList<String>) {
        val outbounds = root.optJSONArray("outbounds") ?: return
        var networkMigrated = 0
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue

            val network = outbound.opt("network")
            if (network is JSONArray) {
                val values = (0 until network.length()).map { network.optString(it).trim().lowercase() }
                if (values.isNotEmpty() && values.all { it == "tcp" || it == "udp" }) {
                    if (values.distinct().size == 1) outbound.put("network", values.first())
                    else outbound.remove("network")
                    networkMigrated++
                }
            }
        }
        if (networkMigrated > 0) {
            notes += "migrated $networkMigrated outbound(s) from the array `network` field to the " +
                "sing-box scalar form (both networks are the default)"
        }
    }

    fun isEngineLevelFault(reason: String): Boolean {
        val text = reason.lowercase()
        return CONFIG_FAULT_MARKERS.any { marker -> text.contains(marker) } ||
            SingBoxAndroidRuntime.isUnusableCore(reason)
    }
}
