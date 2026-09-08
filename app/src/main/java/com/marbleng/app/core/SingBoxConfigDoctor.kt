package com.marbleng.app.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * MARBLE_ENGINE_SELF_HEAL_V152 — the sing-box config doctor.
 *
 * The shipped 8.0.6 log ends in BLOCKED seventeen profiles deep because every start was refused
 * by the core itself:
 *
 * ```
 * sing-box rejected the config: outbounds[3]: dns outbound is deprecated in sing-box 1.11.0
 * and removed in sing-box 1.13.0, use rule actions instead
 * ```
 *
 * That class of fault is *structural*, not per-node: failover walked all 17 servers and every
 * one of them died on the same line. The doctor is Marble Intelligence's automatic answer. When
 * the core rejects a config, the JSON is repaired against the known removals and renames of the
 * sing-box 1.12/1.13 migration, re-checked, and the session proceeds — the user connects instead
 * of reading a schema error. [isEngineLevelFault] is the companion half: it tells the VPN service
 * that a failure belongs to the engine (so falling over to another *node* is pointless and the
 * Xray engine should carry the session instead).
 *
 * Everything here is pure JSON surgery over the exact text [SingBoxManager] wrote, so it is
 * unit-testable without a device and without spawning the core.
 */
object SingBoxConfigDoctor {

    /**
     * outbound types the modern core refuses to parse. The `dns` outbound was deprecated in
     * sing-box 1.11.0 and removed in 1.13.0; the replacement is the `hijack-dns` rule action,
     * which [SingBoxConfigBuilder.routeConfig] already writes.
     */
    private val REMOVED_OUTBOUND_TYPES = setOf("dns")

    /** Sentence fragments the core prints when it rejects a config rather than a network. */
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
        // MARBLE_SINGBOX_ANDROID_RUNTIME_V155 — the 1.14 impending-deprecation exits. The core
        // logs `<description> is deprecated in sing-box 1.12.0 …` at error level, then
        // `to continuing using this feature, set environment variable ENABLE_DEPRECATED_…=true`
        // at fatal level, then calls os.Exit(1). Every one of them is a config fault. The two
        // domain-resolver markers below spell the option the way the core spells it —
        // "missing `route.default_domain_resolver` or `domain_resolver` in dial fields" from the
        // deprecation note, and "default domain resolver not found: <tag>" from route start.
        "domain_resolver",
        "default domain resolver not found",
        "enable_deprecated_",
        "is conflict with",
        "unknown transport type",
        "only supported on",
        // MARBLE_PACKAGES_XML_ROOT_CAUSE_V157 — a package/uid/process matcher that survived
        // sanitization (see [stripAndroidUnsafeRuleFields]) forces the core to open
        // /data/system/packages.xml, which an app-UID process is denied; the pinned core does
        // not nil-check that failure before using the result, so it panics and exits 2. Every
        // candidate for the same profile carries the same rule, so without this marker the
        // engine walked all 17 endpoints to an identical crash instead of recognising it as one
        // structural fault and self-healing onto the other engine after the first.
        "initialize package manager",
        "read packages list",
        "packages.xml",
        "invalid memory address or nil pointer dereference"
    )

    data class Repair(
        /** The repaired JSON text; identical to the input when nothing could be fixed. */
        val json: String,
        /** True when at least one known migration applied. */
        val repaired: Boolean,
        /** One human-readable line per applied migration, for diagnostics and the log. */
        val notes: List<String>
    )

    /**
     * Repairs [json] against the sing-box 1.12/1.13 removals. Never throws: an unreadable config
     * comes back unrepaired, because only the core's own verdict is authoritative.
     *
     * @param rejection the verbatim text the core printed when it refused this config, when the
     *   caller has it. Most migrations are unconditional, but a few are trade-offs that are only
     *   correct in the presence of a specific complaint — see [downgradeHttpClients].
     */
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
        // MARBLE_SINGBOX_ANDROID_RUNTIME_V155 — everything the preflight pass already guarantees
        // is re-applied here, because `repair` also runs on configs the builder never wrote
        // (a re-check after a partial repair, a config restored from disk).
        harden(root, notes)
        // A pinned modern core must not be "repaired" back to removed download_detour fields.
        // Unknown schema remains a visible configuration failure, not a downgrade.

        if (notes.isEmpty()) return Repair(json, false, emptyList())
        return Repair(root.toString(), true, notes)
    }

    /**
     * MARBLE_SINGBOX_ANDROID_RUNTIME_V155 — the **preflight** pass, run on every config before
     * it is ever handed to the core.
     *
     * [repair] is reactive: it only runs once `sing-box check` has already refused a config. That
     * is the wrong shape for the two faults that made the sing-box engine unusable on Android,
     * because both of them are *predictable*: a config either asks for a netlink interface
     * monitor or it does not, and either carries a 1.14 impending deprecation or it does not.
     * Waiting for the core to die, parsing its Go error and guessing the repair is strictly worse
     * than never emitting the option. Hardening is therefore unconditional, idempotent, and cheap
     * enough to run on the throwaway URL-test config too.
     */
    fun hardenForAndroid(json: String): Repair {
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: return Repair(json, false, emptyList())
        val notes = mutableListOf<String>()
        harden(root, notes)
        if (notes.isEmpty()) return Repair(json, false, emptyList())
        return Repair(root.toString(), true, notes)
    }

    /** The shared body of [hardenForAndroid] and the tail of [repair]. */
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

    /**
     * Route rules may still point at an outbound that was just removed; a dangling reference is
     * a fresh rejection. A `protocol: dns` rule aimed at the removed `dns` outbound becomes the
     * `hijack-dns` rule action — the exact replacement the deprecation error names. Any other
     * dangling target is dropped so the rule's own matcher falls through to `route.final`.
     */
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
            // Keep the matcher, lose the dead target: the `final` outbound answers instead.
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

    /**
     * sing-box 1.12 renamed the DNS server address key `address` → `server`. A config written
     * with the old key parses to nothing and every lookup dies at run time — silently, long
     * after `check` approved it.
     */
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

    /**
     * sing-box 1.14 deprecated `independent_cache` (removed in 1.16). Omitting it restores
     * clean parsing without warnings or rejections.
     */
    private fun migrateDnsOptions(root: JSONObject, notes: MutableList<String>) {
        val dns = root.optJSONObject("dns") ?: return
        if (dns.has("independent_cache")) {
            dns.remove("independent_cache")
            notes += "removed deprecated `independent_cache` DNS option"
        }
    }

    /**
     * sing-box `auto_detect_interface` attempts to create Linux netlink socket monitors, which
     * are banned on Android without root/ADB privileges. Removing it prevents fatal process exits.
     */
    private fun migrateRouteOptions(root: JSONObject, notes: MutableList<String>) {
        val route = root.optJSONObject("route") ?: return
        if (route.has("auto_detect_interface")) {
            route.remove("auto_detect_interface")
            notes += "removed `auto_detect_interface` (avoiding banned Android netlink socket)"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // MARBLE_SINGBOX_ANDROID_RUNTIME_V155 — the preflight hardening pass
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Drops every `route.*` key that makes `route.NewNetworkManager` demand a netlink-backed
     * interface monitor. This is *the* fix for
     * `initialize network manager: create network monitor: netlink socket in Android is banned by
     * Google`: with none of these keys present `enforceInterfaceMonitor` is false and the core
     * tolerates the banned socket, exactly as the upstream code intends.
     */
    private fun stripAndroidUnsafeRouteOptions(root: JSONObject, notes: MutableList<String>) {
        val route = root.optJSONObject("route") ?: return
        val removed = SingBoxAndroidRuntime.ANDROID_FORBIDDEN_ROUTE_KEYS.filter { key ->
            // `false`/`0`/`""` are already the Go zero value; only a *requesting* value matters,
            // and removing an explicit negative would churn the config for nothing.
            route.has(key) && isRequesting(route.opt(key))
        }
        removed.forEach { route.remove(it) }
        if (removed.isNotEmpty()) {
            notes += "removed route option(s) that require an Android-banned netlink interface " +
                "monitor: ${removed.joinToString(", ")}"
        }
    }

    /**
     * MARBLE_PACKAGES_XML_ROOT_CAUSE_V157 — the per-rule half of [stripAndroidUnsafeRouteOptions].
     *
     * `route.NewNetworkManager`'s netlink ban is a top-level `route.*` concern, but the
     * package/uid/process matchers this repairs are fields of an individual `route.rules[]`
     * object (and of its nested `type: logical` children), so scanning only the top level never
     * finds them. [harden] previously relied on [SingBoxConfigBuilder]'s own one-time sanitizer
     * for this, which is correct for a config this app just assembled — but [harden] also runs
     * inside [repair], on configs the builder never wrote (a re-check after a partial repair, a
     * config restored from disk). Doing the same job here, from the same canonical
     * [SingBoxAndroidRuntime.ANDROID_FORBIDDEN_RULE_KEYS] list, makes the doctor self-sufficient
     * regardless of how a config reaches it: any survivor is what caused
     * `initialize package manager: read packages list: open /data/system/packages.xml:
     * permission denied` -> nil-pointer SIGSEGV on every URL test, Real delay probe and connect
     * attempt for the affected profile alike.
     */
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

    /** The dial-field half of [stripAndroidUnsafeRouteOptions], applied to every outbound. */
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

    /**
     * A `tun`/`redirect`/`tproxy` inbound belongs to a core that owns the network stack. MarbleNG's
     * TUN is Android's own `VpnService`, and a `tun` inbound with `auto_route` is the other way to
     * force the interface monitor the platform bans.
     */
    private fun stripAndroidUnsafeInbounds(root: JSONObject, notes: MutableList<String>) {
        val inbounds = root.optJSONArray("inbounds") ?: return
        val kept = JSONArray()
        val dropped = mutableListOf<String>()
        for (i in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(i) ?: continue
            val type = inbound.optString("type").lowercase()
            if (type in SingBoxAndroidRuntime.ANDROID_FORBIDDEN_INBOUND_TYPES) {
                dropped += type
                continue
            }
            kept.put(inbound)
        }
        if (dropped.isNotEmpty()) {
            root.put("inbounds", kept)
            notes += "removed ${dropped.joinToString(", ")} inbound(s): the Android VpnService " +
                "owns the tunnel, the core only serves the local mixed inbound"
        }
    }

    /** A `dhcp` DNS transport reads the lease through netlink; on Android it can only fail. */
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
        // A rule pointing at a server that no longer exists is a fresh rejection.
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

    /**
     * `store_rdrc` is deprecated in sing-box 1.14 and scheduled for removal in 1.16; the
     * documented replacement persists the whole DNS cache instead:
     *
     * ```json
     * { "experimental": { "cache_file": { "enabled": true, "store_dns": true } } }
     * ```
     */
    private fun migrateCacheFileOptions(root: JSONObject, notes: MutableList<String>) {
        val cacheFile = root.optJSONObject("experimental")?.optJSONObject("cache_file") ?: return
        if (!cacheFile.has("store_rdrc")) return
        val enabled = cacheFile.optBoolean("store_rdrc", false)
        cacheFile.remove("store_rdrc")
        if (enabled && !cacheFile.has("store_dns")) cacheFile.put("store_dns", true)
        notes += "migrated the deprecated `store_rdrc` cache-file option to `store_dns`"
    }

    /**
     * `download_detour` on a remote rule-set is deprecated in 1.14 in favour of an HTTP client:
     *
     * ```json
     * { "type": "remote", "url": "…", "http_client": { "detour": "direct" } }
     * ```
     *
     * The core also rejects a rule-set that carries both (`http_client is conflict with
     * deprecated download_detour field`), so the migration is a move, never a copy.
     */
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

    /**
     * sing-box 1.12 introduced `route.default_domain_resolver`; 1.14 schedules the *absence* of
     * it for removal, which on the pinned core means `deprecated.Report` takes the impending
     * branch and calls `os.Exit(1)`:
     *
     * ```
     * missing `route.default_domain_resolver` or `domain_resolver` in dial fields is deprecated
     * in sing-box 1.12.0 and will be removed in sing-box 1.14.0
     * to continuing using this feature, set environment variable
     * ENABLE_DEPRECATED_MISSING_DOMAIN_RESOLVER=true
     * ```
     *
     * The report fires whenever a dialer has to resolve a domain and more than one DNS transport
     * is configured — which is every MarbleNG config, because the resolver pool is plural by
     * design. The fix is to name the resolver explicitly, and the only correct answer for a
     * *dial* is a resolver that does not need the tunnel: the system one.
     */
    private fun ensureDefaultDomainResolver(root: JSONObject, notes: MutableList<String>) {
        val servers = root.optJSONObject("dns")?.optJSONArray("servers") ?: return
        if (servers.length() < 2) return
        val route = root.optJSONObject("route") ?: JSONObject().also { root.put("route", it) }
        val existing = route.opt("default_domain_resolver")
        val tags = (0 until servers.length()).mapNotNull { index ->
            servers.optJSONObject(index)?.optString("tag")?.takeIf { it.isNotBlank() }
        }
        val currentTag = when (existing) {
            is String -> existing
            is JSONObject -> existing.optString("server")
            else -> ""
        }
        if (currentTag.isNotBlank() && currentTag in tags) return

        // Preference order mirrors the bootstrap order of the config itself: the system resolver
        // first (it can never depend on the tunnel it is helping to build), then the direct
        // resolver. A config with neither gets a `local` transport appended rather than being
        // pointed at a resolver that lives behind the proxy — that would be a bootstrap loop,
        // which is a subtler and much worse failure than the deprecation this fixes.
        fun tagOfType(type: String): String? = (0 until servers.length()).firstNotNullOfOrNull { i ->
            servers.optJSONObject(i)
                ?.takeIf { it.optString("type").equals(type, ignoreCase = true) }
                ?.optString("tag")
                ?.takeIf { it.isNotBlank() }
        }
        val chosen = tagOfType("local")
            ?: tags.firstOrNull { it == SingBoxConfigBuilder.DNS_DIRECT_TAG }
            ?: SingBoxConfigBuilder.DNS_LOCAL_TAG.also { tag ->
                servers.put(JSONObject().put("type", "local").put("tag", tag))
                notes += "added a system-resolver DNS transport (`$tag`) for dial-time lookups"
            }
        route.put("default_domain_resolver", chosen)

        notes += if (currentTag.isBlank()) {
            "set `route.default_domain_resolver` to `$chosen` (its absence is fatal on sing-box 1.14)"
        } else {
            "repointed `route.default_domain_resolver` from the unknown `$currentTag` to `$chosen`"
        }
    }

    /** True for a JSON value that actually asks for the feature, rather than a written-out zero. */
    private fun isRequesting(value: Any?): Boolean = when (value) {
        null, JSONObject.NULL -> false
        is Boolean -> value
        is Number -> value.toDouble() != 0.0
        is String -> value.isNotBlank()
        is JSONArray -> value.length() > 0
        is JSONObject -> value.length() > 0
        else -> true
    }

    /**
     * MARBLE_SINGBOX_PROTOCOLS_V153 — the two translation bugs that made every non-parser
     * profile fail at `sing-box check`.
     *
     * The builder used to write `"network": ["tcp","udp"]`. sing-box's common `network` field is
     * a scalar (`tcp` or `udp`), so the core printed `cannot unmarshal array into Go struct
     * field ... of type string` and rejected the whole config. The doctor migrates that in place
     * to the scalar the core expects; for Xray's habitual `["tcp","udp"]` the correct fix is to
     * omit the field entirely (both networks are already the default).
     *
     * TLS fragmentation is valid in the pinned 1.14 schema and is never stripped.
     */
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

            // tls.fragment is a supported boolean in 1.14 option/tls.go. Keep it.

        }
        if (networkMigrated > 0) {
            notes += "migrated $networkMigrated outbound(s) from the array `network` field to the " +
                "sing-box scalar form (both networks are the default)"
        }

    }

    /**
     * True when [reason] describes the *engine* refusing to run a config, as opposed to a node
     * or network failure. The VPN service uses this to stop walking failover candidates (every
     * candidate would fail identically) and to self-heal onto the other engine instead.
     */
    fun isEngineLevelFault(reason: String): Boolean {
        val text = reason.lowercase()
        return CONFIG_FAULT_MARKERS.any { marker -> text.contains(marker) }
    }
}
