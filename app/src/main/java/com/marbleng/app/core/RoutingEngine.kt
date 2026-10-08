package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.GeoPrecision
import com.marbleng.app.model.RoutingDefaults
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRule
import com.marbleng.app.model.RoutingRuleKind
import com.marbleng.app.model.parseGeoPrecision
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet6Address
import java.net.InetAddress
import java.util.UUID
import java.util.regex.Pattern

/**
 * MARBLE_ROUTING_ENGINE_V136 — the single source of truth for user routing.
 *
 * What was wrong (and is now fixed, structurally):
 *
 *  1. **Deleted rules resurrected themselves.** `parseRules("")` and even a literal `[]` fell
 *     back to [DEFAULT_RULES], so a user who removed the ads-block or geo rules got them back on
 *     the next launch — and could never actually turn the geo-specific defaults off. Now the
 *     stored list is the truth: empty means empty. First-run defaults are seeded explicitly by
 *     the store migration instead of being re-injected forever.
 *  2. **The routing mode was a lie.** PROXY_ALL still blocked ads and sent geo tags direct,
 *     BYPASS_PRIVATE differed from PROXY_ALL by nothing, and no screen could even change the
 *     mode. [implicitRules] now derives the mode's real behaviour in exactly one place, and every
 *     consumer (config writer, asset preloader, Bug Finder, simulator, UI) reads the same answer.
 *  3. **One bad token killed the whole core.** A port like `443 udp`, a malformed regexp or a
 *     `geosite:tag` that does not exist in the current geosite.dat made Xray reject the entire
 *     config at load time — the tunnel icon stayed "connected" while every site failed with the
 *     browser's "check your connection". [validateRule] catches this before save, and emission
 *     skips a rule that would be rejected, so a stale rule can cost one route, never the tunnel.
 *  4. **Nothing could answer "why is this site failing?"** [simulate] walks the exact ordered
 *     rule sequence the config writer emits — including the implicit mode rules — and names the
 *     winning rule and outbound, offline, using [GeoAssetIndex] for real geosite membership.
 *
 * Order is priority. Enabled rules only. Fail-closed: unmatched traffic stays on the proxy.
 */
object RoutingEngine {

    /**
     * The recommended balanced preset: block ad/tracker domains, keep Iranian destinations on the
     * direct underlay. Seeded on first run and restored by the Recommended preset; never silently
     * re-injected afterwards.
     */
    val DEFAULT_RULES: List<RoutingRule> = listOf(
        RoutingRule(
            id = "ads-block",
            enabled = true,
            kind = RoutingRuleKind.GEOSITE,
            matcher = RoutingDefaults.ADS_TAG,
            outbound = RoutingOutbound.BLOCK,
            remark = "Block ads"
        ),
        RoutingRule(
            id = "iran-ip-direct",
            enabled = true,
            kind = RoutingRuleKind.GEOIP,
            matcher = "ir",
            outbound = RoutingOutbound.DIRECT,
            remark = "Iranian IP direct"
        ),
        RoutingRule(
            id = "iran-domain-direct",
            enabled = true,
            kind = RoutingRuleKind.GEOSITE,
            matcher = "ir",
            outbound = RoutingOutbound.DIRECT,
            remark = "Iranian domain direct"
        )
    )

    // ---------------------------------------------------------------------------------------
    // Persistence — the stored list is the truth
    // ---------------------------------------------------------------------------------------

    fun parseRules(raw: String): List<RoutingRule> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(trimmed)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(RoutingRule.fromJson(o))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun serializeRules(rules: List<RoutingRule>): String {
        val arr = JSONArray()
        rules.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    /** The user's own ordered rules. No implicit fallback lives here any more. */
    fun effectiveRules(settings: AppSettings): List<RoutingRule> = parseRules(settings.routingRulesJson)

    fun newRule(): RoutingRule = RoutingRule(
        id = UUID.randomUUID().toString().take(12),
        enabled = true,
        kind = RoutingRuleKind.DOMAIN,
        matcher = "",
        outbound = RoutingOutbound.PROXY,
        remark = ""
    )

    fun move(rules: List<RoutingRule>, from: Int, to: Int): List<RoutingRule> {
        if (from !in rules.indices || to !in rules.indices || from == to) return rules
        val next = rules.toMutableList()
        val item = next.removeAt(from)
        next.add(to, item)
        return next
    }

    fun duplicate(rule: RoutingRule): RoutingRule = rule.copy(
        id = UUID.randomUUID().toString().take(12),
        remark = if (rule.remark.isBlank()) rule.remark else "${rule.remark} copy"
    )

    // ---------------------------------------------------------------------------------------
    // Mode semantics — the one definition every consumer shares
    // ---------------------------------------------------------------------------------------

    /**
     * What the routing mode contributes by itself, independent of the user's own rules.
     *
     *  - [RoutingMode.PROXY_ALL]  — everything proxied; ads switch and private bypass remain
     *    independent expert toggles.
     *  - [RoutingMode.BYPASS_PRIVATE] — private/LAN ranges always direct (the toggle is pinned on
     *    for this mode).
     *  - [RoutingMode.GEO_DIRECT] — the classic anti-censorship split: the configured geo tags
     *    (`routeGeoIpTags` / `routeGeoSiteTags`) go direct and private stays direct. Identity
     *    Guard keeps choosing this mode for Iranian users; everyone else simply leaves it.
     *  - [RoutingMode.CUSTOM] — only the user's own rules; no implicit geo behaviour at all.
     *
     * Ad blocking (`routeBlockAds`) is an explicit switch the user owns in every mode.
     */
    data class ImplicitRules(
        val adsTag: String?,
        val directIpTags: List<String>,
        val directSiteTags: List<String>,
        val forceBypassPrivate: Boolean
    ) {
        val isEmpty: Boolean
            get() = adsTag == null && directIpTags.isEmpty() && directSiteTags.isEmpty() &&
                !forceBypassPrivate
    }

    fun implicitRules(settings: AppSettings): ImplicitRules {
        val ads = settings.routeAdsTag.trim().takeIf { settings.routeBlockAds && it.isNotBlank() }
        return when (settings.routingMode) {
            RoutingMode.PROXY_ALL -> ImplicitRules(ads, emptyList(), emptyList(), false)
            RoutingMode.BYPASS_PRIVATE -> ImplicitRules(ads, emptyList(), emptyList(), true)
            RoutingMode.GEO_DIRECT -> ImplicitRules(
                ads,
                splitTags(settings.routeGeoIpTags),
                splitTags(settings.routeGeoSiteTags),
                true
            )
            RoutingMode.CUSTOM -> ImplicitRules(ads, emptyList(), emptyList(), false)
        }
    }

    private fun splitTags(raw: String): List<String> =
        raw.split(',', '\n', '\r', ';')
            .map { it.trim().removePrefix("geoip:").removePrefix("geosite:").trim().lowercase() }
            .filter { it.isNotBlank() && !it.equals("private", true) }
            .distinct()

    // ---------------------------------------------------------------------------------------
    // Validation — a rule that would be rejected by Xray never reaches it
    // ---------------------------------------------------------------------------------------

    enum class IssueSeverity { ERROR, WARNING }

    data class RuleIssue(val severity: IssueSeverity, val message: String)

    private val PORT_TOKEN = Regex("^\\d{1,5}(-\\d{1,5})?$")
    private val IPV4_LITERAL = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val PROTOCOL_TOKEN = Regex("^[a-z0-9_-]{1,32}$", RegexOption.IGNORE_CASE)
    private val HOSTNAME = Regex(
        "^(?=.{1,253}$)([a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?)(\\.[a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?)*\\.?"
    )

    /**
     * Everything wrong with one rule, worst first. ERROR means the engine would reject the
     * config or the rule cannot work; the editor refuses to save those. WARNING means the rule
     * will load but part of it cannot be verified offline.
     */
    fun validateRule(rule: RoutingRule): List<RuleIssue> {
        val issues = mutableListOf<RuleIssue>()
        fun error(message: String) = issues.add(RuleIssue(IssueSeverity.ERROR, message))

        val tokens = splitTokens(rule.matcher)
        when (rule.kind) {
            RoutingRuleKind.GEOSITE -> {
                val tag = rule.matcher.trim().removePrefix("geosite:").trim()
                if (tag.isBlank()) error("Geosite tag is empty")
                else if (GeoAssetIndex.known(GeoAssetIndex.Kind.GEOSITE, tag) == false) {
                    error("\"$tag\" is not in the loaded geosite.dat — update the geo assets or pick a suggestion")
                }
            }
            RoutingRuleKind.GEOIP -> {
                val tag = rule.matcher.trim().removePrefix("geoip:").trim()
                if (tag.isBlank()) error("GeoIP tag is empty")
                else if (!tag.equals("private", true) &&
                    GeoAssetIndex.known(GeoAssetIndex.Kind.GEOIP, tag) == false
                ) {
                    error("\"$tag\" is not in the loaded geoip.dat — update the geo assets or pick a suggestion")
                }
            }
            RoutingRuleKind.DOMAIN -> {
                if (tokens.isEmpty()) error("No domains entered")
                tokens.forEach { token -> validateDomainToken(token.lowercase(), ::error) }
            }
            RoutingRuleKind.IP -> {
                if (tokens.isEmpty()) error("No IPs or ranges entered")
                tokens.forEach { token -> validateIpToken(token, ::error) }
            }
            RoutingRuleKind.PORT -> {
                val ports = (rule.matcher.trim().ifBlank { rule.port }).trim()
                if (ports.isBlank()) error("No ports entered")
                else validatePorts(ports, ::error)
            }
        }

        if (rule.port.isNotBlank() && rule.kind != RoutingRuleKind.PORT) {
            validatePorts(rule.port, ::error)
        }
        val network = rule.network.trim().lowercase()
        if (network.isNotBlank() && network !in setOf("tcp", "udp", "tcp,udp")) {
            error("Network must be tcp, udp or tcp,udp")
        }
        rule.protocol.split(',').map(String::trim).filter(String::isNotBlank).forEach { proto ->
            if (!PROTOCOL_TOKEN.matches(proto)) {
                error("Protocol \"$proto\" is not a valid protocol name")
            }
        }
        return issues.sortedBy { it.severity }
    }

    /** True when the rule may be emitted into the Xray config at all. */
    fun isEmittable(rule: RoutingRule): Boolean =
        rule.enabled && validateRule(rule).none { it.severity == IssueSeverity.ERROR }

    /** The user rules that actually reach the engine, in priority order. */
    fun emittableUserRules(settings: AppSettings): List<RoutingRule> =
        effectiveRules(settings).filter { isEmittable(it) }

    private fun validateDomainToken(token: String, error: (String) -> Unit) {
        when {
            token.startsWith("geosite:", true) -> {
                val tag = token.substringAfter(':').trim()
                if (tag.isBlank()) {
                    error("Empty geosite: reference")
                } else if (GeoAssetIndex.known(GeoAssetIndex.Kind.GEOSITE, tag) == false) {
                    error("geosite:$tag is not in the loaded geosite.dat")
                }
            }
            token.startsWith("domain:", true) -> {
                val value = token.substringAfter(':').trim()
                if (!HOSTNAME.matches(value)) error("\"$value\" is not a valid domain")
            }
            token.startsWith("full:", true) -> {
                val value = token.substringAfter(':').trim()
                if (!HOSTNAME.matches(value)) error("\"$value\" is not a valid domain")
            }
            token.startsWith("keyword:", true) -> {
                val value = token.substringAfter(':').trim()
                if (value.length < 2) error("keyword needs at least 2 characters")
            }
            token.startsWith("regexp:", true) -> {
                val value = token.substringAfter(':').trim()
                try {
                    Pattern.compile(value)
                } catch (_: Exception) {
                    error("regexp \"$value\" does not compile")
                }
            }
            else -> {
                if (!HOSTNAME.matches(token)) {
                    error("\"$token\" is not a domain — use domain:, full:, keyword: or regexp:")
                }
            }
        }
    }

    private fun validateIpToken(token: String, error: (String) -> Unit) {
        when {
            token.startsWith("geoip:", true) -> {
                val tag = token.substringAfter(':').trim()
                if (tag.isBlank()) {
                    error("Empty geoip: reference")
                } else if (!tag.equals("private", true) &&
                    GeoAssetIndex.known(GeoAssetIndex.Kind.GEOIP, tag) == false
                ) {
                    error("geoip:$tag is not in the loaded geoip.dat")
                }
            }
            else -> {
                if (parseCidr(token) == null) {
                    error("\"$token\" is not an IP, an IP range or a CIDR")
                }
            }
        }
    }

    private fun validatePorts(raw: String, error: (String) -> Unit) {
        raw.split(',').map(String::trim).filter(String::isNotBlank).forEach { token ->
            if (!PORT_TOKEN.matches(token)) {
                error("\"$token\" is not a port — use 443, 80,8443 or 1000-2000")
            } else {
                val range = token.split('-')
                val low = range[0].toIntOrNull() ?: -1
                val high = range.lastOrNull()?.toIntOrNull() ?: -1
                if (low !in 0..65535 || high !in 0..65535 || (range.size == 2 && low > high)) {
                    error("Port \"$token\" is outside 0-65535")
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Asset needs — one answer for the preloader, Bug Finder and the UI
    // ---------------------------------------------------------------------------------------

    fun needsGeoIp(settings: AppSettings): Boolean {
        if (implicitRules(settings).directIpTags.isNotEmpty()) return true
        if (settings.customRoutingEnabled && emittableUserRules(settings).any { rule ->
                when (rule.kind) {
                    RoutingRuleKind.GEOIP ->
                        !rule.matcher.trim().removePrefix("geoip:").equals("private", true)
                    RoutingRuleKind.IP -> splitIps(rule.matcher).any {
                        it.startsWith("geoip:", true) && !it.equals("geoip:private", true)
                    }
                    else -> false
                }
            }
        ) {
            return true
        }
        return listOf(settings.routeDirectIps, settings.routeBlockIps).any { raw ->
            raw.split(',', '\n', '\r', ';').any {
                it.trim().startsWith("geoip:", true) && !it.trim().equals("geoip:private", true)
            }
        }
    }

    fun needsGeoSite(settings: AppSettings): Boolean {
        val implicit = implicitRules(settings)
        if (implicit.adsTag != null || implicit.directSiteTags.isNotEmpty()) return true
        if (settings.customRoutingEnabled && emittableUserRules(settings).any { rule ->
                when (rule.kind) {
                    RoutingRuleKind.GEOSITE -> true
                    RoutingRuleKind.DOMAIN -> splitDomains(rule.matcher).any {
                        it.startsWith("geosite:", true)
                    }
                    else -> false
                }
            }
        ) {
            return true
        }
        return listOf(settings.routeDirectDomains, settings.routeProxyDomains, settings.routeBlockDomains).any { raw ->
            raw.split(',', '\n', '\r', ';').any { it.trim().startsWith("geosite:", true) }
        }
    }

    /**
     * MARBLE_GEO_READY_GATE_V145 — geo routing is disabled until its databases are really here.
     *
     * ## What used to happen
     *
     * `XrayManager.start` compared the selected policy against the files on disk and, when
     * geoip.dat/geosite.dat were missing, it *failed the connection*:
     *
     * ```
     * error("geoip.dat is required by the selected routing policy but no local/bundled copy …")
     * ```
     *
     * The default policy (GEO_DIRECT + ad blocking) needs both files, so on any build/install
     * where the download had not finished — a fresh install on a censored link, a cleared cache,
     * an interrupted refresh — the product simply could not connect, and the message named a
     * file the user has no way to produce. The geo split is an *optimisation* (keep domestic
     * traffic off the tunnel, drop ad domains). Losing it must cost that optimisation, never the
     * connection.
     *
     * ## What happens now
     *
     * This function returns the same settings with every rule that would require a missing
     * database removed, so the config writer emits a policy the engine can actually load:
     *
     *  - `GEO_DIRECT` degrades to `BYPASS_PRIVATE` while geoip.dat is missing — private/LAN
     *    ranges stay direct (those are literal CIDRs, no database involved) and everything else
     *    is proxied, which is always safe;
     *  - geo tag lists and geo tokens inside the direct/proxy/block lists are dropped;
     *  - ad blocking (a geosite category) is switched off while geosite.dat is missing;
     *  - user rules that match on a missing database are disabled — the rule survives in
     *    Settings and starts working by itself the moment the asset lands.
     *
     * Pure function: the caller keeps the user's real settings, and the UI reads
     * [geoDowngradeReason] to explain the temporary state.
     */
    fun withGeoAssetGate(
        settings: AppSettings,
        geoIpReady: Boolean,
        geoSiteReady: Boolean
    ): AppSettings {
        if (geoIpReady && geoSiteReady) return settings
        var next = settings

        if (!geoIpReady) {
            if (next.routingMode == RoutingMode.GEO_DIRECT) {
                next = next.copy(routingMode = RoutingMode.BYPASS_PRIVATE, routeBypassPrivate = true)
            }
            next = next.copy(
                routeGeoIpTags = "",
                routeDirectIps = stripGeoTokens(next.routeDirectIps, "geoip:"),
                routeBlockIps = stripGeoTokens(next.routeBlockIps, "geoip:")
            )
        }

        if (!geoSiteReady) {
            next = next.copy(
                routeGeoSiteTags = "",
                routeBlockAds = false,
                routeDirectDomains = stripGeoTokens(next.routeDirectDomains, "geosite:"),
                routeProxyDomains = stripGeoTokens(next.routeProxyDomains, "geosite:"),
                routeBlockDomains = stripGeoTokens(next.routeBlockDomains, "geosite:")
            )
        }

        if (next.customRoutingEnabled) {
            val gated = parseRules(next.routingRulesJson).map { rule ->
                if (rule.enabled && ruleNeedsMissingAsset(rule, geoIpReady, geoSiteReady)) {
                    rule.copy(enabled = false)
                } else {
                    rule
                }
            }
            next = next.copy(routingRulesJson = serializeRules(gated))
        }

        return next
    }

    /** True when [rule] can only work once a database that is not on disk arrives. */
    private fun ruleNeedsMissingAsset(
        rule: RoutingRule,
        geoIpReady: Boolean,
        geoSiteReady: Boolean
    ): Boolean = when (rule.kind) {
        RoutingRuleKind.GEOSITE -> !geoSiteReady
        RoutingRuleKind.GEOIP ->
            !geoIpReady && !rule.matcher.trim().removePrefix("geoip:").equals("private", true)
        RoutingRuleKind.DOMAIN ->
            !geoSiteReady && splitDomains(rule.matcher).any { it.startsWith("geosite:", true) }
        RoutingRuleKind.IP ->
            !geoIpReady && splitIps(rule.matcher).any {
                it.startsWith("geoip:", true) && !it.equals("geoip:private", true)
            }
        RoutingRuleKind.PORT -> false
    }

    /** Drops `geoip:`/`geosite:` tokens from a comma/newline separated list, keeping literals. */
    private fun stripGeoTokens(raw: String, prefix: String): String {
        if (raw.isBlank()) return raw
        return raw.split(',', '\n', '\r', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot {
                it.startsWith(prefix, true) &&
                    !(prefix == "geoip:" && it.equals("geoip:private", true))
            }
            .joinToString(",")
    }

    /**
     * One line for the UI/diagnostics explaining what the gate switched off, or "" when the
     * databases are complete and the user's policy is running exactly as written.
     */
    fun geoDowngradeReason(
        settings: AppSettings,
        geoIpReady: Boolean,
        geoSiteReady: Boolean
    ): String {
        val missing = buildList {
            if (!geoIpReady && needsGeoIp(settings)) add("geoip.dat")
            if (!geoSiteReady && needsGeoSite(settings)) add("geosite.dat")
        }
        if (missing.isEmpty()) return ""
        return "Geo routing paused until ${missing.joinToString(" and ")} finishes downloading"
    }

    fun needsDirectOutbound(settings: AppSettings): Boolean {
        val implicit = implicitRules(settings)
        if (settings.routeBypassPrivate || implicit.forceBypassPrivate) return true
        if (implicit.directIpTags.isNotEmpty() || implicit.directSiteTags.isNotEmpty()) return true
        return settings.customRoutingEnabled && emittableUserRules(settings).any { it.outbound == RoutingOutbound.DIRECT }
    }

    // ---------------------------------------------------------------------------------------
    // Emission — implicit mode rules first, then the user's ordered rules
    // ---------------------------------------------------------------------------------------

    /**
     * Emits the implicit mode rules and the user's ordered rules. Rules whose validation found
     * an ERROR are skipped rather than emitted: a stale rule must cost one route, never the
     * whole core. [seen] deduplicates identical tokens between the implicit and the user layer.
     */
    fun applyUserRules(rulesOut: JSONArray, settings: AppSettings, proxyTag: String) {
        val implicit = implicitRules(settings)
        if (settings.routeBypassPrivate || implicit.forceBypassPrivate) {
            addIpRule(rulesOut, PRIVATE_CIDRS, "direct")
        }
        val seen = mutableSetOf<String>()
        fun mark(kind: String, matcher: String): Boolean =
            seen.add("$kind:${matcher.lowercase()}")

        implicit.adsTag?.let { ads ->
            normalizeGeoSite(ads)?.let { token ->
                // MARBLE_MULTI_SOURCE_ROUTING_V212 — the ad category is asked of every enabled
                // database, so "block ads" is as complete as the union of the sources.
                geoTokens(settings, GeoAssetRegistry.Kind.GEOSITE, token)
                    .filter { mark("geosite", it) }
                    .takeIf { it.isNotEmpty() }
                    ?.let { tokens -> addDomainRule(rulesOut, tokens, "block") }
            }
        }
        implicit.directIpTags.forEach { tag ->
            val token = normalizeGeoIp(tag) ?: return@forEach
            if (token == "geoip:private") {
                addIpRule(rulesOut, PRIVATE_CIDRS, "direct")
            } else {
                geoTokens(settings, GeoAssetRegistry.Kind.GEOIP, token)
                    .filter { mark("geoip", it) }
                    .takeIf { it.isNotEmpty() }
                    ?.let { tokens -> addIpRule(rulesOut, tokens, "direct") }
            }
        }
        implicit.directSiteTags.forEach { tag ->
            normalizeGeoSite(tag)?.let { token ->
                geoTokens(settings, GeoAssetRegistry.Kind.GEOSITE, token)
                    .filter { mark("geosite", it) }
                    .takeIf { it.isNotEmpty() }
                    ?.let { tokens -> addDomainRule(rulesOut, tokens, "direct") }
            }
        }

        // MARBLE_ROUTING_SEPARATE_V143 — user-authored custom rules are opt-in. The built-in
        // mode/ads/bypass rules above always stay active; only the custom layer is gated.
        if (settings.customRoutingEnabled) for (rule in emittableUserRules(settings)) {
            val tag = outboundTag(rule.outbound, proxyTag)
            val ports = (if (rule.kind == RoutingRuleKind.PORT) rule.matcher.trim() else rule.port)
                .trim()
            val network = rule.network.trim().lowercase()
            val protocols = rule.protocol.split(',').map(String::trim).filter(String::isNotBlank)

            fun putRule(build: (JSONObject) -> JSONObject) {
                var obj = JSONObject().put("type", "field")
                obj = build(obj)
                if (ports.isNotBlank()) obj = obj.put("port", ports)
                if (network.isNotBlank()) obj = obj.put("network", network)
                if (protocols.isNotEmpty()) {
                    obj = obj.put("protocol", JSONArray(protocols))
                }
                rulesOut.put(obj.put("outboundTag", tag))
            }

            when (rule.kind) {
                RoutingRuleKind.GEOSITE -> {
                    val token = normalizeGeoSite(rule.matcher) ?: continue
                    val tokens = geoTokens(settings, GeoAssetRegistry.Kind.GEOSITE, token)
                        .filter { mark("geosite", it) }
                    if (tokens.isEmpty()) continue
                    putRule { it.put("domain", JSONArray(tokens)) }
                }
                RoutingRuleKind.GEOIP -> {
                    val token = normalizeGeoIp(rule.matcher) ?: continue
                    if (token == "geoip:private") {
                        addIpRule(rulesOut, PRIVATE_CIDRS, tag)
                    } else {
                        val tokens = geoTokens(settings, GeoAssetRegistry.Kind.GEOIP, token)
                            .filter { mark("geoip", it) }
                        if (tokens.isEmpty()) continue
                        putRule { it.put("ip", JSONArray(tokens)) }
                    }
                }
                RoutingRuleKind.DOMAIN -> {
                    val domains = splitDomains(rule.matcher).filter { mark("domain", it) }
                    if (domains.isEmpty()) continue
                    putRule { it.put("domain", JSONArray(domains)) }
                }
                RoutingRuleKind.IP -> {
                    val ips = splitIps(rule.matcher).filter { mark("ip", it) }
                    if (ips.isEmpty()) continue
                    putRule { it.put("ip", JSONArray(ips)) }
                }
                RoutingRuleKind.PORT -> {
                    if (ports.isBlank()) continue
                    if (!mark("port", ports + network)) continue
                    putRule { it }
                }
            }
        }

        // MARBLE_MULTI_SOURCE_ROUTING_V212 — the curated domestic knowledge, last. Order is the
        // whole contract here: the core takes the FIRST rule that matches, so a domain the user
        // blocked or proxied by hand has already been emitted (and marked) by the time this runs,
        // and the product's own "keep domestic traffic here" answer can neither override it nor
        // duplicate it. Emitting it earlier would have made an invisible product rule win over a
        // rule the user wrote in Settings.
        applyPrecisionRules(rulesOut, settings, seen)
    }

    fun outboundTag(outbound: RoutingOutbound, proxyTag: String): String = when (outbound) {
        RoutingOutbound.PROXY -> proxyTag
        RoutingOutbound.DIRECT -> "direct"
        RoutingOutbound.BLOCK -> "block"
    }

    // ---------------------------------------------------------------------------------------
    // MARBLE_MULTI_SOURCE_ROUTING_V212 — several databases, fail-closed emission
    // ---------------------------------------------------------------------------------------

    /**
     * The asset files that are actually on disk right now.
     *
     * The connect path writes them into a transient settings field (the same pattern as
     * `measuredIpv6Unhealthy` and `measuredDnsDemotedEndpoints`): readiness is a fact about this
     * session's filesystem, not a preference, so it travels with the settings the writer reads
     * and is never persisted. An empty value means "assume only the canonical pair", which is the
     * safe reading — a source that has not reported itself is a source whose rules are not
     * emitted, and a missing `ext:` file is a config the core refuses to load.
     */
    fun readyGeoFiles(settings: AppSettings): Set<String> {
        val raw = settings.measuredGeoReadyFiles.trim()
        // Empty means a legacy/offline caller has not measured the files; `none` is an explicit
        // connection-time observation that no complete database was available.
        if (raw.equals("none", ignoreCase = true)) return emptySet()
        val parsed = raw
            .split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.matches(Regex("[A-Za-z0-9._-]+\\.dat")) && !it.contains("..") }
            .distinct()
        return if (raw.isEmpty() && !settings.measuredGeoMembershipKnown) {
            setOf(GeoAssetRegistry.PRIMARY_GEOIP_FILE, GeoAssetRegistry.PRIMARY_GEOSITE_FILE)
        } else if (raw.isEmpty()) {
            emptySet()
        } else {
            parsed.toSet()
        }
    }

    /**
     * Every token one geo tag expands to, one per source that can answer it and whose database is
     * on disk. The primary is the canonical `geoip:tag` / `geosite:tag` form; every other source
     * names its own file with `ext:`, which is exactly what the core's geo loader reads.
     *
     * An empty result means the tag cannot be emitted at all right now — the caller skips the rule
     * rather than writing a reference to a file that does not exist.
     */
    fun geoTokens(settings: AppSettings, kind: GeoAssetRegistry.Kind, tag: String): List<String> {
        val ready = readyGeoFiles(settings)
        val cleanTag = GeoAssetRegistry.tagOf(tag).trim().lowercase()
        return GeoAssetRegistry.tokensFor(settings, kind, tag).filter { token ->
            val file = GeoAssetRegistry.fileOf(token).ifEmpty {
                if (kind == GeoAssetRegistry.Kind.GEOIP) GeoAssetRegistry.PRIMARY_GEOIP_FILE
                else GeoAssetRegistry.PRIMARY_GEOSITE_FILE
            }
            if (kind == GeoAssetRegistry.Kind.GEOIP && cleanTag == "private") return@filter true
            if (file !in ready) return@filter false
            if (!settings.measuredGeoMembershipKnown) return@filter true
            settings.measuredGeoTagsByFile[file]
                ?.any { it.equals(cleanTag, ignoreCase = true) } == true
        }
    }

    /**
     * The curated domestic rules: the TLD as a suffix, every named domestic service as a root
     * domain, and — at the strongest precision level — the brand keywords.
     *
     * These are literal domains, so they need no geo database at all: the one class of routing that
     * keeps working when every download is blocked. They are emitted as a small number of rules
     * (not one rule per domain) because a rule is matched as a unit, and forty rules of one domain
     * each is forty times the matcher work for the same answer.
     */
    fun applyPrecisionRules(rulesOut: JSONArray, settings: AppSettings, seen: MutableSet<String>) {
        // Every entry here is a DIRECT rule, so it only belongs in a mode whose whole job is
        // keeping domestic traffic here. In CUSTOM the user's own list is the policy and a
        // product-authored direct rule would silently outrank it; in PROXY_ALL nothing is direct
        // by design. [precisionActive] is the one answer both engines and the UI read.
        if (!precisionActive(settings)) return
        val precision = parseGeoPrecision(settings.geoPrecision)
        val suffixes = IranPrecisionPack.suffixes(precision)
        val domains = IranPrecisionPack.domains(precision)
        val keywords = IranPrecisionPack.keywords(precision)

        val tokens = ArrayList<String>(suffixes.size + domains.size + keywords.size)
        suffixes.forEach { suffix ->
            val token = "domain:$suffix"
            if (seen.add("domain:$token")) tokens += token
        }
        domains.forEach { domain ->
            val token = "domain:$domain"
            if (seen.add("domain:$token")) tokens += token
        }
        keywords.forEach { keyword ->
            val token = "keyword:$keyword"
            if (seen.add("domain:$token")) tokens += token
        }
        if (tokens.isEmpty()) return

        // Chunked so one enormous `domain` array cannot exceed what the core will parse in a
        // single rule, and so a rule that is ever rejected by a future core costs one chunk.
        tokens.chunked(PRECISION_RULE_CHUNK).forEach { chunk ->
            addDomainRule(rulesOut, chunk, "direct")
        }
    }

    /** Domains per emitted precision rule: large enough to be few rules, small enough to parse. */
    const val PRECISION_RULE_CHUNK: Int = 120

    /**
     * True when the routing layer separates domestic traffic with the curated knowledge rather
     * than with geo tags alone. Bug Finder and the routing page both read this one answer.
     */
    fun precisionActive(settings: AppSettings): Boolean =
        settings.routingMode == RoutingMode.GEO_DIRECT &&
            settings.iranDomesticDirect &&
            parseGeoPrecision(settings.geoPrecision) != GeoPrecision.STANDARD

    val PRIVATE_CIDRS = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "100.64.0.0/10",
        "127.0.0.0/8", "169.254.0.0/16", "192.0.0.0/24", "192.88.99.0/24",
        "198.18.0.0/15", "198.51.100.0/24", "203.0.113.0/24", "224.0.0.0/4", "240.0.0.0/4",
        "::1/128", "fc00::/7", "fe80::/10"
    )

    fun addDomainRule(rules: JSONArray, values: List<String>, outboundTag: String) {
        if (values.isEmpty()) return
        rules.put(JSONObject().put("type", "field").put("domain", JSONArray(values)).put("outboundTag", outboundTag))
    }

    fun addIpRule(rules: JSONArray, values: List<String>, outboundTag: String) {
        if (values.isEmpty()) return
        rules.put(JSONObject().put("type", "field").put("ip", JSONArray(values)).put("outboundTag", outboundTag))
    }

    fun normalizeGeoSite(raw: String): String? {
        val body = raw.trim().removePrefix("geosite:").trim()
        return body.takeIf { it.isNotBlank() }?.let { "geosite:$it" }
    }

    fun normalizeGeoIp(raw: String): String? {
        val body = raw.trim().removePrefix("geoip:").trim()
        return body.takeIf { it.isNotBlank() }?.let { "geoip:$it" }
    }

    private fun splitTokens(raw: String): List<String> =
        raw.split(',', '\n', '\r', ';').map { it.trim() }.filter { it.isNotBlank() }.distinct()

    fun splitDomains(raw: String): List<String> = splitTokens(raw).mapNotNull { value ->
        when {
            value.startsWith("geosite:", true) -> normalizeGeoSite(value)
            value.startsWith("domain:", true) -> "domain:${value.substringAfter(':').trim()}"
            value.startsWith("full:", true) -> "full:${value.substringAfter(':').trim()}"
            value.startsWith("keyword:", true) -> "keyword:${value.substringAfter(':').trim()}"
            value.startsWith("regexp:", true) -> "regexp:${value.substringAfter(':').trim()}"
            else -> "domain:$value"
        }
    }.distinct()

    fun splitIps(raw: String): List<String> = splitTokens(raw).flatMap { token ->
        when {
            token.equals("geoip:private", true) -> PRIVATE_CIDRS
            token.startsWith("geoip:", true) -> normalizeGeoIp(token)?.let { listOf(it) } ?: emptyList()
            else -> listOf(token)
        }
    }.distinct()

    // ---------------------------------------------------------------------------------------
    // Simulator — "why is this site failing?", answered offline
    // ---------------------------------------------------------------------------------------

    /** What one evaluated layer decided about the tested host. */
    data class RouteStep(
        val title: String,
        val detail: String,
        val outbound: RoutingOutbound?,
        /** null = this layer cannot be verified offline (a geo database tag, a port input). */
        val matched: Boolean?,
        val skipped: Boolean = false
    )

    data class RouteSimulation(
        val host: String,
        val isIpLiteral: Boolean,
        val steps: List<RouteStep>,
        /** Outbound the connection would take; always resolvable (fallback is the proxy). */
        val verdict: RoutingOutbound,
        val verdictReason: String
    )

    /**
     * Walks the layers in exactly the order [com.marbleng.app.core.XrayConfigHardener] emits
     * them, so the answer on screen is the answer the engine takes. geosite membership is
     * verified against the real indexed domain lists; a geoip tag cannot be verified offline and
     * is reported as unverifiable instead of being folded into a fake no.
     */
    fun simulate(settings: AppSettings, host: String): RouteSimulation {
        val clean = host.trim().trimEnd('.').lowercase()
        // Literal detection is textual on purpose: InetAddress.getByName would otherwise perform
        // a real DNS lookup for every domain input — a simulator that leaks queries is itself a
        // defect. Only genuinely literal shapes are parsed, and parsing a literal never resolves.
        val isIp = looksLikeLiteral(clean)
        val literalIp: InetAddress? = if (isIp) {
            runCatching { InetAddress.getByName(clean) }.getOrNull()
        } else {
            null
        }
        val steps = mutableListOf<RouteStep>()
        var verdict: RoutingOutbound? = null
        var reason = ""

        // MARBLE_MULTI_SOURCE_ROUTING_V212 — the precision level of this simulation, and the one
        // place that turns "a geo tag matched" into "this database matched", which is the answer a
        // user needs when a domestic destination went through the tunnel.
        val precision = parseGeoPrecision(settings.geoPrecision)

        /** " • from Chocolate4U Iran, Loyalsoldier", or "" when nothing indexed can say. */
        fun provenanceNote(kind: GeoAssetIndex.Kind, tag: String): String {
            val sources = GeoAssetIndex.sourcesFor(kind, tag)
            return if (sources.isEmpty()) "" else " • from " + sources.joinToString(", ")
        }

        fun decide(outbound: RoutingOutbound, why: String) {
            if (verdict == null) {
                verdict = outbound
                reason = why
            }
        }

        // 1. Fail-closed IPv6 block when the family is switched off.
        val literalV6 = literalIp is Inet6Address
        if (literalV6 && !settings.ipv6Enabled) {
            steps += RouteStep("IPv6", "::/0 blocked (IPv6 off)", RoutingOutbound.BLOCK, true)
            decide(RoutingOutbound.BLOCK, "IPv6 is switched off; ::/0 is blocked inside the tunnel")
        } else if (literalV6) {
            steps += RouteStep("IPv6", "IPv6 enabled", null, false)
        }

        val domainForMatch = clean.takeIf { !isIp }

        /**
         * Domain-list matching shared by every domain layer. `null` means "this layer exists and
         * could still match, but the verdict lives inside the geo database" — reported honestly
         * instead of being folded into a fake no.
         */
        fun matchDomainTokens(tokens: List<String>): Boolean? {
            if (tokens.isEmpty()) return false
            var unverifiable = false
            for (token in tokens) {
                when {
                    token.startsWith("geosite:", true) -> {
                        when (val hit = domainForMatch?.let { GeoAssetIndex.matchesGeosite(it) }) {
                            true -> return true
                            null -> unverifiable = true
                            else -> Unit
                        }
                    }
                    domainForMatch == null -> Unit
                    token.startsWith("domain:", true) -> {
                        val value = token.substringAfter(':')
                        if (domainForMatch == value || domainForMatch.endsWith(".$value")) return true
                    }
                    token.startsWith("full:", true) -> {
                        if (domainForMatch == token.substringAfter(':')) return true
                    }
                    token.startsWith("keyword:", true) -> {
                        if (domainForMatch.contains(token.substringAfter(':'))) return true
                    }
                    token.startsWith("regexp:", true) -> {
                        val pattern = runCatching {
                            Pattern.compile(token.substringAfter(':'))
                        }.getOrNull()
                        if (pattern != null && pattern.matcher(domainForMatch).find()) return true
                    }
                    else -> {
                        if (domainForMatch == token || domainForMatch.endsWith(".$token")) return true
                    }
                }
            }
            return if (unverifiable) null else false
        }

        /** IP/CIDR list matching. A geoip tag can never be verified offline: it reports null. */
        fun matchIpTokens(tokens: List<String>): Boolean? {
            if (tokens.isEmpty()) return false
            var unverifiable = false
            if (literalIp != null) {
                for (token in tokens) {
                    if (token.startsWith("geoip:", true)) {
                        unverifiable = true
                        continue
                    }
                    val cidr = parseCidr(token) ?: continue
                    if (cidrContains(cidr, literalIp)) return true
                }
            } else {
                // A domain destination still carries an address when the engine matches IP
                // rules (the hijacked resolver answered it), so a geoip tag may claim it at
                // runtime — the simulator cannot see that address and says so.
                if (tokens.any { it.startsWith("geoip:", true) }) unverifiable = true
            }
            return if (unverifiable) null else false
        }

        // 2. Expert block lists.
        if (settings.routeBlockDomains.isNotBlank()) {
            val hit = matchDomainTokens(splitDomains(settings.routeBlockDomains))
            steps += RouteStep(
                "Block list (domains)", settings.routeBlockDomains,
                RoutingOutbound.BLOCK.takeIf { hit == true }, hit
            )
            if (hit == true) decide(RoutingOutbound.BLOCK, "Matched the expert block-domain list")
        }
        if (settings.routeBlockIps.isNotBlank()) {
            val hit = matchIpTokens(splitIps(settings.routeBlockIps))
            steps += RouteStep(
                "Block list (IPs)", settings.routeBlockIps,
                RoutingOutbound.BLOCK.takeIf { hit == true }, hit
            )
            if (hit == true) decide(RoutingOutbound.BLOCK, "Matched the expert block-IP list")
        }

        // 3. Expert proxy list.
        if (settings.routeProxyDomains.isNotBlank()) {
            val hit = matchDomainTokens(splitDomains(settings.routeProxyDomains))
            steps += RouteStep(
                "Proxy list (domains)", settings.routeProxyDomains,
                RoutingOutbound.PROXY.takeIf { hit == true }, hit
            )
            if (hit == true) decide(RoutingOutbound.PROXY, "Matched the expert proxy-domain list")
        }

        // 4. Implicit mode behaviour (ads + geo tags) — the layers applyUserRules emits first.
        val implicit = implicitRules(settings)
        implicit.adsTag?.let { ads ->
            val tag = normalizeGeoSite(ads) ?: return@let
            val hit = matchDomainTokens(listOf(tag))
            steps += RouteStep(
                "Ad blocking", tag,
                RoutingOutbound.BLOCK.takeIf { hit == true }, hit
            )
            if (hit == true) decide(RoutingOutbound.BLOCK, "Matched $tag from the ad-blocking rule")
        }
        implicit.directIpTags.forEach { tag ->
            val token = normalizeGeoIp(tag) ?: return@forEach
            val hit = matchIpTokens(listOf(token))
            steps += RouteStep(
                "Geo direct (IP)",
                token + if (hit == null) " • the live geo database decides at runtime" else "",
                RoutingOutbound.DIRECT.takeIf { hit == true },
                hit
            )
            if (hit == true) decide(RoutingOutbound.DIRECT, "Matched $token (geo direct policy)")
        }
        implicit.directSiteTags.forEach { tag ->
            val token = normalizeGeoSite(tag) ?: return@forEach
            // MARBLE_MULTI_SOURCE_ROUTING_V212 — one step per source, named: "which database
            // answered?" is the first question a user asks about a domestic site that got proxied.
            val tokens = geoTokens(settings, GeoAssetRegistry.Kind.GEOSITE, token)
                .ifEmpty { listOfNotNull(normalizeGeoSite(token)) }
            val hit = matchDomainTokens(tokens)
            steps += RouteStep(
                "Geo direct (domains)",
                tokens.joinToString(", ") + provenanceNote(
                    GeoAssetIndex.Kind.GEOSITE,
                    GeoAssetRegistry.tagOf(token)
                ),
                RoutingOutbound.DIRECT.takeIf { hit == true },
                hit
            )
            if (hit == true) decide(RoutingOutbound.DIRECT, "Matched $token (geo direct policy)")
        }

        // MARBLE_MULTI_SOURCE_ROUTING_V212 — the curated domestic layer, reported where it runs:
        // after the geo tags and before the user's own rules. It is the layer that answers for the
        // domestic service no database has listed yet, so a simulator that skipped it would keep
        // printing "proxy" for exactly the destinations this feature exists for.
        if (precisionActive(settings) && domainForMatch != null) {
            val category = IranPrecisionPack.categoryOf(domainForMatch, precision)
            val byTld = domainForMatch == IranPrecisionPack.TLD_SUFFIX ||
                domainForMatch.endsWith("." + IranPrecisionPack.TLD_SUFFIX)
            val byKeyword = IranPrecisionPack.keywords(precision)
                .any { it.length >= 4 && domainForMatch.contains(it) }
            if (category != null || byTld || byKeyword) {
                val detail = when {
                    category != null -> "${category.label} • ${IranPrecisionPack.summary(precision)}"
                    byTld -> "Domestic TLD (.${IranPrecisionPack.TLD_SUFFIX})"
                    else -> "Domestic brand keyword"
                }
                steps += RouteStep(
                    "Domestic precision", detail, RoutingOutbound.DIRECT, true
                )
                decide(RoutingOutbound.DIRECT, "Matched the curated domestic-destination rules")
            }
        }

        // 5. The user's own rules, in priority order.
        effectiveRules(settings).forEachIndexed { index, rule ->
            val title = "Rule ${index + 1} • ${rule.remark.ifBlank { rule.kind.name }}"
            if (!rule.enabled) {
                steps += RouteStep(
                    title,
                    describeMatcher(rule) + " — disabled",
                    null,
                    false,
                    skipped = true
                )
                return@forEachIndexed
            }
            val issues = validateRule(rule).filter { it.severity == IssueSeverity.ERROR }
            if (issues.isNotEmpty()) {
                steps += RouteStep(
                    title,
                    describeMatcher(rule) + " — skipped: " + issues.first().message,
                    null,
                    false,
                    skipped = true
                )
                return@forEachIndexed
            }
            val hit: Boolean? = when (rule.kind) {
                RoutingRuleKind.GEOSITE ->
                    matchDomainTokens(listOf(normalizeGeoSite(rule.matcher).orEmpty()))
                RoutingRuleKind.DOMAIN -> matchDomainTokens(splitDomains(rule.matcher))
                RoutingRuleKind.GEOIP ->
                    matchIpTokens(listOf(normalizeGeoIp(rule.matcher).orEmpty()))
                RoutingRuleKind.IP -> matchIpTokens(splitIps(rule.matcher))
                // Ports are not part of a host; the layer is reported without a fake verdict.
                RoutingRuleKind.PORT -> null
            }
            val extra = when {
                rule.kind == RoutingRuleKind.GEOIP && hit == null ->
                    " • the live geo database decides at runtime"
                rule.kind == RoutingRuleKind.PORT ->
                    " • port rules need a port to test; add one to the port box"
                else -> ""
            }
            steps += RouteStep(
                title,
                describeMatcher(rule) + extra,
                rule.outbound.takeIf { hit == true },
                hit
            )
            if (hit == true) {
                decide(
                    rule.outbound,
                    "Matched rule ${index + 1} (${rule.remark.ifBlank { rule.kind.name }}): " +
                        describeMatcher(rule)
                )
            }
        }

        // 6. Expert direct lists (below user rules, exactly like the emitted config).
        if (settings.routeDirectDomains.isNotBlank()) {
            val hit = matchDomainTokens(splitDomains(settings.routeDirectDomains))
            steps += RouteStep(
                "Direct list (domains)", settings.routeDirectDomains,
                RoutingOutbound.DIRECT.takeIf { hit == true }, hit
            )
            if (hit == true) decide(RoutingOutbound.DIRECT, "Matched the expert direct-domain list")
        }
        if (settings.routeDirectIps.isNotBlank()) {
            val hit = matchIpTokens(splitIps(settings.routeDirectIps))
            steps += RouteStep(
                "Direct list (IPs)", settings.routeDirectIps,
                RoutingOutbound.DIRECT.takeIf { hit == true }, hit
            )
            if (hit == true) decide(RoutingOutbound.DIRECT, "Matched the expert direct-IP list")
        }

        // 7. Fail-closed fallback.
        if (verdict == null) {
            steps += RouteStep(
                "Fallback",
                "Everything unmatched stays on the proxy",
                RoutingOutbound.PROXY,
                true
            )
            decide(
                RoutingOutbound.PROXY,
                "No rule could be verified as matching — the fail-closed fallback keeps it on the proxy"
            )
        }

        return RouteSimulation(clean, isIp, steps, verdict ?: RoutingOutbound.PROXY, reason)
    }

    private fun looksLikeLiteral(raw: String): Boolean {
        if (raw.contains(':')) return true
        val parts = raw.split('.')
        return parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
    }

    private fun describeMatcher(rule: RoutingRule): String {
        val parts = mutableListOf<String>()
        if (rule.matcher.isNotBlank()) parts += "${rule.kind.name.lowercase()}:${rule.matcher}"
        if (rule.port.isNotBlank() && rule.kind != RoutingRuleKind.PORT) parts += "port:${rule.port}"
        if (rule.network.isNotBlank()) parts += "net:${rule.network}"
        if (rule.protocol.isNotBlank()) parts += "proto:${rule.protocol}"
        return parts.joinToString(" + ").ifBlank { rule.kind.name }
    }

    // ---------------------------------------------------------------------------------------
    // IP/CIDR helpers shared with the simulator and its tests
    // ---------------------------------------------------------------------------------------

    /**
     * Parses `1.2.3.4`, `1.2.3.0/24`, `::1` or `fe80::/10`. The textual shape is checked before
     * any parsing so a word like "ir" can never trigger a DNS lookup inside a validator.
     */
    fun parseCidr(raw: String): Pair<InetAddress, Int>? {
        val token = raw.trim()
        if (token.isEmpty()) return null
        val slash = token.indexOf('/')
        if (slash >= 0 && token.indexOf('/', slash + 1) >= 0) return null
        val addressText = if (slash >= 0) token.substring(0, slash) else token
        val prefixText = if (slash >= 0) token.substring(slash + 1) else ""
        val isV6 = addressText.contains(':')
        val isV4 = IPV4_LITERAL.matches(addressText)
        if (!isV6 && !isV4) return null
        val address = runCatching { InetAddress.getByName(addressText) }.getOrNull() ?: return null
        val bits = if (address is Inet6Address) 128 else 32
        val prefix = if (prefixText.isBlank()) bits else prefixText.toIntOrNull() ?: return null
        if (prefix < 0 || prefix > bits) return null
        return address to prefix
    }

    /** True when [address] falls inside the parsed CIDR. Mixed-family comparisons are false. */
    fun cidrContains(cidr: Pair<InetAddress, Int>, address: InetAddress): Boolean {
        val (network, prefix) = cidr
        val networkBytes = network.address
        val addressBytes = address.address
        if (networkBytes.size != addressBytes.size) return false
        var remaining = prefix
        var index = 0
        while (remaining >= 8) {
            if (networkBytes[index] != addressBytes[index]) return false
            remaining -= 8
            index++
        }
        if (remaining > 0) {
            val mask = (0xFF shl (8 - remaining)) and 0xFF
            if ((networkBytes[index].toInt() and mask) != (addressBytes[index].toInt() and mask)) {
                return false
            }
        }
        return true
    }
}
