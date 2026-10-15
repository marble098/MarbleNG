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

object RoutingEngine {
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

    enum class IssueSeverity { ERROR, WARNING }

    data class RuleIssue(val severity: IssueSeverity, val message: String)

    private val PORT_TOKEN = Regex("^\\d{1,5}(-\\d{1,5})?$")
    private val IPV4_LITERAL = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val PROTOCOL_TOKEN = Regex("^[a-z0-9_-]{1,32}$", RegexOption.IGNORE_CASE)
    private val HOSTNAME = Regex(
        "^(?=.{1,253}$)([a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?)(\\.[a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?)*\\.?"
    )

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

    fun isEmittable(rule: RoutingRule): Boolean =
        rule.enabled && validateRule(rule).none { it.severity == IssueSeverity.ERROR }

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

        applyPrecisionRules(rulesOut, settings, seen)
    }

    fun outboundTag(outbound: RoutingOutbound, proxyTag: String): String = when (outbound) {
        RoutingOutbound.PROXY -> proxyTag
        RoutingOutbound.DIRECT -> "direct"
        RoutingOutbound.BLOCK -> "block"
    }

    fun readyGeoFiles(settings: AppSettings): Set<String> {
        val raw = settings.measuredGeoReadyFiles.trim()

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

    fun applyPrecisionRules(rulesOut: JSONArray, settings: AppSettings, seen: MutableSet<String>) {
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

        tokens.chunked(PRECISION_RULE_CHUNK).forEach { chunk ->
            addDomainRule(rulesOut, chunk, "direct")
        }
    }

    const val PRECISION_RULE_CHUNK: Int = 120

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

    data class RouteStep(
        val title: String,
        val detail: String,
        val outbound: RoutingOutbound?,
        val matched: Boolean?,
        val skipped: Boolean = false
    )

    data class RouteSimulation(
        val host: String,
        val isIpLiteral: Boolean,
        val steps: List<RouteStep>,
        val verdict: RoutingOutbound,
        val verdictReason: String
    )

    fun simulate(settings: AppSettings, host: String): RouteSimulation {
        val clean = host.trim().trimEnd('.').lowercase()

        val isIp = looksLikeLiteral(clean)
        val literalIp: InetAddress? = if (isIp) {
            runCatching { InetAddress.getByName(clean) }.getOrNull()
        } else {
            null
        }
        val steps = mutableListOf<RouteStep>()
        var verdict: RoutingOutbound? = null
        var reason = ""

        val precision = parseGeoPrecision(settings.geoPrecision)

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

        val literalV6 = literalIp is Inet6Address
        if (literalV6 && !settings.ipv6Enabled) {
            steps += RouteStep("IPv6", "::/0 blocked (IPv6 off)", RoutingOutbound.BLOCK, true)
            decide(RoutingOutbound.BLOCK, "IPv6 is switched off; ::/0 is blocked inside the tunnel")
        } else if (literalV6) {
            steps += RouteStep("IPv6", "IPv6 enabled", null, false)
        }

        val domainForMatch = clean.takeIf { !isIp }

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
                if (tokens.any { it.startsWith("geoip:", true) }) unverifiable = true
            }
            return if (unverifiable) null else false
        }

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

        if (settings.routeProxyDomains.isNotBlank()) {
            val hit = matchDomainTokens(splitDomains(settings.routeProxyDomains))
            steps += RouteStep(
                "Proxy list (domains)", settings.routeProxyDomains,
                RoutingOutbound.PROXY.takeIf { hit == true }, hit
            )
            if (hit == true) decide(RoutingOutbound.PROXY, "Matched the expert proxy-domain list")
        }

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
