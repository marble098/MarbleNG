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
        // MARBLE_SINGBOX_AUTOPARSER_V154 — the core's own link `parser` outbound refusing the
        // share link is a *config* fault (Marble's reader set, not the network), so it must
        // classify as engine-level: the session falls back to Marble's translation or the Xray
        // engine instead of walking every remaining profile through the same refusal.
        "invalid link",
        "unsupported scheme",
        "unknown protocol"
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
     */
    fun repair(json: String): Repair {
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

        if (notes.isEmpty()) return Repair(json, false, emptyList())
        return Repair(root.toString(), true, notes)
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
     * MARBLE_SINGBOX_PROTOCOLS_V153 — the two translation bugs that made every non-parser
     * profile fail at `sing-box check`.
     *
     * The builder used to write `"network": ["tcp","udp"]`. sing-box's common `network` field is
     * a scalar (`tcp` or `udp`), so the core printed `cannot unmarshal array into Go struct
     * field ... of type string` and rejected the whole config. The doctor migrates that in place
     * to the scalar the core expects; for Xray's habitual `["tcp","udp"]` the correct fix is to
     * omit the field entirely (both networks are already the default).
     *
     * It also removes the Xray-only `fragment` key from `tls`. sing-box's outbound TLS options do
     * not contain that field, and `fragment` belongs to route-options instead.
     */
    private fun migrateNetworkAndTlsFields(root: JSONObject, notes: MutableList<String>) {
        val outbounds = root.optJSONArray("outbounds") ?: return
        var networkMigrated = 0
        var fragmentRemoved = 0
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue

            val network = outbound.opt("network")
            if (network is JSONArray) {
                val values = (0 until network.length()).map { network.optString(it).trim().lowercase() }
                if (values.all { it == "tcp" || it == "udp" }) {
                    outbound.remove("network")
                    networkMigrated++
                } else {
                    outbound.remove("network")
                    networkMigrated++
                }
            }

            outbound.optJSONObject("tls")?.let { tls ->
                if (tls.has("fragment")) {
                    tls.remove("fragment")
                    fragmentRemoved++
                }
            }
        }
        if (networkMigrated > 0) {
            notes += "migrated $networkMigrated outbound(s) from the array `network` field to the " +
                "sing-box scalar form (both networks are the default)"
        }
        if (fragmentRemoved > 0) {
            notes += "removed $fragmentRemoved Xray-only TLS `fragment` field(s)"
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
