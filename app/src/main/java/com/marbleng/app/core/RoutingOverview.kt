package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.RoutingDefaults
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRule
import com.marbleng.app.model.parseGeoPrecision

/**
 * MARBLE_ROUTING_CLARITY_V280 — one question, two answers, and one place that turns the router into
 * words a person can check.
 *
 * The V254 pass rewrote the routing page around the *decision*, but it kept four routing modes, six
 * "ready set" presets and a separate switch for the hand-made rule list. Measured against the
 * engine, three of those controls were not controls at all:
 *
 *  - [RoutingMode.BYPASS_PRIVATE] emitted exactly the same rules as [RoutingMode.PROXY_ALL] — the
 *    private ranges are added from `routeBypassPrivate`, which ships on and has no switch;
 *  - the presets only wrote a rule list (and switched the mode to [RoutingMode.CUSTOM]), which is
 *    the same job the mode picker and the rule editor already do;
 *  - `customRoutingEnabled` gated the hand-made rules in Xray but not in sing-box, and the Xray
 *    config verifier demanded a route for every saved rule while the emitter skipped it — a saved
 *    country-list rule plus any mode other than CUSTOM made Xray reject the whole config.
 *
 * What is left is the pair of outcomes a person actually chooses between, plus rules that always
 * run. [applyPolicy] is the single writer, [layers] is the single reader for "what is in force
 * right now", and the page no longer restates any of it.
 */
object RoutingOverview {
    const val MARKER: String = "MARBLE_ROUTING_CLARITY_V280"

    /** The whole routing decision: Iranian traffic stays out of the tunnel, or it does not. */
    enum class Policy(val id: String) {
        IRAN_DIRECT("iran-direct"),
        TUNNEL_ALL("tunnel-all");

        companion object {
            fun of(id: String): Policy = entries.firstOrNull { it.id == id } ?: IRAN_DIRECT
        }
    }

    /** The policy a settings blob describes, read from the two flags the engine really uses. */
    fun policyOf(settings: AppSettings): Policy =
        if (settings.routingMode == RoutingMode.GEO_DIRECT && settings.iranDomesticDirect) {
            Policy.IRAN_DIRECT
        } else {
            Policy.TUNNEL_ALL
        }

    /**
     * The only writer of the routing mode.
     *
     * Choosing "Iranian sites stay direct" also re-arms `iranDomesticDirect` and restores the
     * country tags if a previous pass blanked them, so the mode can never promise a direct path
     * that the engine has nothing to match. Choosing the tunnel writes [RoutingMode.PROXY_ALL] and
     * leaves every hand-made rule untouched.
     */
    fun applyPolicy(settings: AppSettings, policy: Policy): AppSettings = when (policy) {
        Policy.IRAN_DIRECT -> settings.copy(
            routingMode = RoutingMode.GEO_DIRECT,
            iranDomesticDirect = true,
            routeGeoIpTags = settings.routeGeoIpTags.ifBlank { RoutingDefaults.GEOIP_DIRECT_TAGS },
            routeGeoSiteTags = settings.routeGeoSiteTags.ifBlank { RoutingDefaults.GEOSITE_DIRECT_TAGS }
        )

        Policy.TUNNEL_ALL -> settings.copy(
            routingMode = RoutingMode.PROXY_ALL,
            iranDomesticDirect = false
        )
    }

    fun title(policy: Policy): String = when (policy) {
        Policy.IRAN_DIRECT -> "Iranian sites stay direct"
        Policy.TUNNEL_ALL -> "Everything through the tunnel"
    }

    /** One line a person can repeat out loud: what this policy does to an ordinary app. */
    fun summary(policy: Policy): String = when (policy) {
        Policy.IRAN_DIRECT -> "Iranian sites open directly, everything else rides the tunnel"
        Policy.TUNNEL_ALL -> "Everything rides the tunnel, except your home network"
    }

    /** What the policy leaves alone, so nobody has to guess where the other half lives. */
    fun note(policy: Policy): String = when (policy) {
        Policy.IRAN_DIRECT ->
            "Ads stay blocked and your own rules below still apply. Only the country lists decide what counts as Iranian."
        Policy.TUNNEL_ALL ->
            "Your own rules below still run, so you can send a single site direct or block it."
    }

    /**
     * One row per layer that is really emitting rules right now, in the order the core reads them.
     * A layer whose file is not on disk is absent instead of being promised.
     */
    data class Layer(
        val title: String,
        val detail: String,
        val outbound: RoutingOutbound,
        val count: Int = 0
    )

    fun layers(
        settings: AppSettings,
        geoIpReady: Boolean = true,
        geoSiteReady: Boolean = true
    ): List<Layer> {
        val out = mutableListOf<Layer>()
        val policy = policyOf(settings)
        val implicit = RoutingEngine.implicitRules(settings)

        if (settings.routeBypassPrivate) {
            out += Layer(
                title = "Home network",
                detail = "Your own addresses never enter the tunnel",
                outbound = RoutingOutbound.DIRECT,
                count = RoutingEngine.PRIVATE_CIDRS.size
            )
        }
        if (implicit.adsTag != null && geoSiteReady) {
            out += Layer(
                title = "Ads and trackers",
                detail = "Blocked from the downloaded site list",
                outbound = RoutingOutbound.BLOCK,
                count = 1
            )
        }
        if (policy == Policy.IRAN_DIRECT) {
            if (implicit.directSiteTags.isNotEmpty() && geoSiteReady) {
                out += Layer(
                    title = "Iranian sites",
                    detail = "Direct, from the downloaded site list",
                    outbound = RoutingOutbound.DIRECT
                )
            }
            if (implicit.directIpTags.isNotEmpty() && geoIpReady) {
                out += Layer(
                    title = "Iranian addresses",
                    detail = "Direct, from the downloaded address list",
                    outbound = RoutingOutbound.DIRECT
                )
            }
            if (RoutingEngine.precisionActive(settings)) {
                val precision = parseGeoPrecision(settings.geoPrecision)
                out += Layer(
                    title = "Domestic brands and .ir",
                    detail = IranPrecisionPack.summary(precision),
                    outbound = RoutingOutbound.DIRECT,
                    count = IranPrecisionPack.domains(precision).size +
                        IranPrecisionPack.keywords(precision).size
                )
            }
        }
        return out
    }

    /** The files the current policy needs and does not have yet, named the way the page names them. */
    fun missingLists(
        settings: AppSettings,
        geoIpReady: Boolean,
        geoSiteReady: Boolean
    ): List<String> = buildList {
        if (!geoIpReady && RoutingEngine.needsGeoIp(settings)) add("Address list")
        if (!geoSiteReady && RoutingEngine.needsGeoSite(settings)) add("Site list")
    }

    /** Every rule that is on. A paused rule stays saved; it just does not claim anything. */
    fun liveRules(settings: AppSettings): List<RoutingRule> =
        RoutingEngine.effectiveRules(settings).filter { it.enabled }

    /**
     * The same matcher asked for twice with the same outcome is one rule and a copy. Ordered by
     * the second occurrence, so a row can say "same as the one above" instead of silently working.
     */
    fun duplicateMatchers(rules: List<RoutingRule>): Map<String, String> {
        val firstSeen = HashMap<String, String>()
        val duplicates = HashMap<String, String>()
        rules.forEach { rule ->
            val key = keyOf(rule)
            val original = firstSeen[key]
            if (original == null) {
                firstSeen[key] = rule.id
            } else {
                duplicates[rule.id] = original
            }
        }
        return duplicates
    }

    private fun keyOf(rule: RoutingRule): String = buildString {
        append(rule.kind.name)
        append('|')
        append(rule.matcher.trim().lowercase())
        append('|')
        append(rule.outbound.name)
        append('|')
        append(rule.port.trim())
        append('|')
        append(rule.network.trim().lowercase())
        append('|')
        append(rule.protocol.trim().lowercase())
    }

    private val REGEX_MARKER = Regex("(^|[\\s,;])regexp:", RegexOption.IGNORE_CASE)

    /**
     * Every hand-made rule that carries a `regexp:` token — the only rule shape that costs time on
     * every request, which is why the page states the count out loud.
     */
    fun regexRules(rules: List<RoutingRule>): List<RoutingRule> =
        rules.filter { REGEX_MARKER.containsMatchIn(it.matcher) }

    /**
     * MARBLE_ROUTING_CLARITY_V280 — the five comma-separated lists the core still reads
     * (`routeDirectDomains` and friends) were edited by a sheet the page has now dropped. A reader
     * who still has values in them would otherwise keep a policy they can no longer see, so the
     * page offers one action that turns each list into ordinary rules and clears the box.
     */
    fun legacyListCount(settings: AppSettings): Int =
        countTokens(settings.routeDirectDomains) +
            countTokens(settings.routeDirectIps) +
            countTokens(settings.routeProxyDomains) +
            countTokens(settings.routeBlockDomains) +
            countTokens(settings.routeBlockIps)

    private fun countTokens(raw: String): Int = raw
        .split(',', '\n', '\r', ';')
        .map { it.trim() }
        .count { it.isNotBlank() }
}
