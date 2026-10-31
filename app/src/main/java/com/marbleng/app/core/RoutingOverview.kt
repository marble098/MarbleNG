package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRule
import com.marbleng.app.model.parseGeoPrecision

/**
 * MARBLE_ROUTING_REBUILD_V254 — one honest answer to "what happens to my traffic right now?".
 *
 * The old page asked a person to assemble that answer out of four independent mechanisms that all
 * write routing rules: the mode ([RoutingMode]), the implicit geo lists (`routeGeoIpTags`,
 * `routeGeoSiteTags`, the ads tag), the expert domain/IP text boxes, and the per-rule list behind
 * `customRoutingEnabled`. Nothing on the screen said which of them were actually in force — the
 * mode picker looked authoritative even when the rule list was switched off, the rule list looked
 * active even when the mode ignored it, and a geo database that had not downloaded yet silently
 * removed the geo half of the policy.
 *
 * This object is the single place that turns those layers into the words a person reads. It owns:
 *
 *  - [decide]: the seam that writes [RoutingMode] **and** `customRoutingEnabled` together, so the
 *    two switch rows can never disagree with each other again (on the old page, "Custom rules" as a
 *    mode and "Enable custom routing" as a switch were reachable in all four combinations, and only
 *    one of them did anything);
 *  - [summary]: the one-line verdict for the mode;
 *  - [sections]: one row per layer that is really emitting rules, with the count it contributes and
 *    a plain-English label, so nothing invisible is doing work;
 *  - [duplicateMatchers], [regexRules] and [activePresetId]: the three things a rebuilt rule list
 *    should refuse to hide — the same matcher twice, a regexp that costs on every request, and the
 *    preset that is already installed.
 *
 * The rule list on the routing page is now derived from the *outcome* (what is direct, what is
 * blocked) instead of from whichever storage layer happened to answer first — that is the
 * structural fix for "the page is full of things that make no sense".
 */
object RoutingOverview {
    const val MARKER: String = "MARBLE_ROUTING_REBUILD_V254"

    /**
     * MARBLE_ROUTING_REBUILD_V254 — one seam for the two flags that used to fight.
     *
     * [RoutingMode.CUSTOM] and `customRoutingEnabled = false` was the state that made the page
     * incoherent: the mode said "only my rules decide", the switch said "ignore my rules", and the
     * rule list rendered as if it were live. The reverse — custom routing on while the mode is
     * [RoutingMode.PROXY_ALL] — emitted the hand-made rules *after* the catch-all and therefore
     * never matched. Every write of the mode now goes through here.
     */
    fun decide(settings: AppSettings, mode: RoutingMode): AppSettings = settings.copy(
        routingMode = mode,
        customRoutingEnabled = mode == RoutingMode.CUSTOM
    )

    /** The mode a fresh install ships, restated here so the page and the tests agree on one name. */
    val DEFAULT_MODE: RoutingMode get() = RoutingMode.GEO_DIRECT

    /** One line a person can repeat out loud: what this mode does to an ordinary app. */
    fun summary(mode: RoutingMode): String = when (mode) {
        RoutingMode.PROXY_ALL -> "Everything takes the tunnel, except the local network"
        RoutingMode.BYPASS_PRIVATE -> "Your home network stays direct, everything else tunnels"
        RoutingMode.GEO_DIRECT -> "Iranian sites stay direct, everything else tunnels"
        RoutingMode.CUSTOM -> "Only your own rules decide; anything unmatched tunnels"
    }

    /** What the mode deliberately leaves alone, so a person knows where the fixed rules live. */
    fun note(mode: RoutingMode): String = when (mode) {
        RoutingMode.PROXY_ALL ->
            "Local addresses and the ad list still stay as they are; your own rules are paused in this mode."
        RoutingMode.BYPASS_PRIVATE ->
            "Home addresses stay direct. Turn on Iranian sites below if you want them direct too."
        RoutingMode.GEO_DIRECT ->
            "Uses the country lists below plus the domestic precision dial."
        RoutingMode.CUSTOM ->
            "The list below is the whole policy. Nothing is added behind your back."
    }

    /**
     * MARBLE_ROUTING_REBUILD_V254 — the layers that are really emitting rules, in the order the
     * core emits them. A layer with no rows is absent instead of being shown with a zero.
     */
    data class Section(
        val title: String,
        val detail: String,
        val count: Int,
        val outbound: RoutingOutbound
    )

    fun sections(settings: AppSettings): List<Section> {
        val implicit = RoutingEngine.implicitRules(settings)
        val out = mutableListOf<Section>()

        implicit.adsTag?.let {
            out += Section(
                title = "Ad and tracker sites blocked",
                detail = "Named inside the downloaded site list",
                count = 1,
                outbound = RoutingOutbound.BLOCK
            )
        }
        if (settings.routeBypassPrivate || implicit.forceBypassPrivate) {
            out += Section(
                title = "Home network direct",
                detail = "Private and link-local addresses never enter the tunnel",
                count = RoutingEngine.PRIVATE_CIDRS.size,
                outbound = RoutingOutbound.DIRECT
            )
        }
        if (implicit.directIpTags.isNotEmpty()) {
            out += Section(
                title = "Iranian addresses direct",
                detail = "Country codes read from the downloaded address list",
                count = implicit.directIpTags.size,
                outbound = RoutingOutbound.DIRECT
            )
        }
        if (implicit.directSiteTags.isNotEmpty()) {
            out += Section(
                title = "Iranian sites direct",
                detail = "Country codes read from the downloaded site list",
                count = implicit.directSiteTags.size,
                outbound = RoutingOutbound.DIRECT
            )
        }
        if (RoutingEngine.precisionActive(settings)) {
            val precision = parseGeoPrecision(settings.geoPrecision)
            out += Section(
                title = "Domestic precision",
                detail = IranPrecisionPack.summary(precision),
                count = IranPrecisionPack.domains(precision).size +
                    IranPrecisionPack.keywords(precision).size,
                outbound = RoutingOutbound.DIRECT
            )
        }
        val blockedDomains = countTokens(settings.routeBlockDomains)
        val blockedIps = countTokens(settings.routeBlockIps)
        if (blockedDomains + blockedIps > 0) {
            out += Section(
                title = "Your block list",
                detail = "Expert list from the previous layout",
                count = blockedDomains + blockedIps,
                outbound = RoutingOutbound.BLOCK
            )
        }
        val proxyDomains = countTokens(settings.routeProxyDomains)
        if (proxyDomains > 0) {
            out += Section(
                title = "Always through the tunnel",
                detail = "Expert list from the previous layout",
                count = proxyDomains,
                outbound = RoutingOutbound.PROXY
            )
        }
        val directDomains = countTokens(settings.routeDirectDomains)
        val directIps = countTokens(settings.routeDirectIps)
        if (directDomains + directIps > 0) {
            out += Section(
                title = "Your direct list",
                detail = "Expert list from the previous layout",
                count = directDomains + directIps,
                outbound = RoutingOutbound.DIRECT
            )
        }
        val rules = RoutingEngine.effectiveRules(settings)
        if (settings.customRoutingEnabled && rules.isNotEmpty()) {
            out += Section(
                title = "Your own rules",
                detail = "Written by you, read before the fallback",
                count = rules.count { it.enabled },
                outbound = RoutingOutbound.PROXY
            )
        }
        return out
    }

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
     * every request, which is why the rebuilt page states the count out loud.
     */
    fun regexRules(rules: List<RoutingRule>): List<RoutingRule> =
        rules.filter { REGEX_MARKER.containsMatchIn(it.matcher) }

    private fun countTokens(raw: String): Int = raw
        .split(',', '\n', '\r', ';')
        .map { it.trim() }
        .count { it.isNotBlank() }

    /**
     * The preset whose rule list is already in place, if any. Used by the preset chips so the
     * "selected" state is read from the rules themselves instead of being assumed.
     */
    fun activePresetId(settings: AppSettings): String? {
        val current = RoutingEngine.effectiveRules(settings)
            .map { it.id to it.outbound }
            .sortedBy { it.first }
        return RoutingPresets.Preset.entries.firstOrNull { preset ->
            preset.rules.map { it.id to it.outbound }.sortedBy { it.first } == current
        }?.id
    }

}
