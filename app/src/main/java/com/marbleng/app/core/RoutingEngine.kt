package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.RoutingDefaults
import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRule
import com.marbleng.app.model.RoutingRuleKind
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * v2rayNG-style ordered routing: each user rule is one Xray field rule.
 * Order is priority. Enabled rules only. Fail-closed unmatched traffic stays on proxy.
 */
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
        if (trimmed.isBlank()) return DEFAULT_RULES
        return runCatching {
            val arr = JSONArray(trimmed)
            if (arr.length() == 0) return@runCatching DEFAULT_RULES
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(RoutingRule.fromJson(o))
                }
            }.ifEmpty { DEFAULT_RULES }
        }.getOrDefault(DEFAULT_RULES)
    }

    fun serializeRules(rules: List<RoutingRule>): String {
        val arr = JSONArray()
        rules.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    fun effectiveRules(settings: AppSettings): List<RoutingRule> {
        val parsed = parseRules(settings.routingRulesJson)
        return if (parsed.isEmpty()) DEFAULT_RULES else parsed
    }

    fun newRule(): RoutingRule = RoutingRule(
        id = UUID.randomUUID().toString().take(12),
        enabled = true,
        kind = RoutingRuleKind.DOMAIN,
        matcher = "",
        outbound = RoutingOutbound.PROXY,
        remark = "Custom rule"
    )

    fun move(rules: List<RoutingRule>, from: Int, to: Int): List<RoutingRule> {
        if (from !in rules.indices || to !in rules.indices || from == to) return rules
        val next = rules.toMutableList()
        val item = next.removeAt(from)
        next.add(to, item)
        return next
    }

    fun needsGeoIp(settings: AppSettings): Boolean =
        effectiveRules(settings).any { it.enabled && it.kind == RoutingRuleKind.GEOIP && it.matcher.isNotBlank() } ||
            splitHasGeoIp(settings.routeDirectIps) ||
            splitHasGeoIp(settings.routeBlockIps)

    fun needsGeoSite(settings: AppSettings): Boolean =
        effectiveRules(settings).any { it.enabled && it.kind == RoutingRuleKind.GEOSITE && it.matcher.isNotBlank() }

    fun needsDirectOutbound(settings: AppSettings): Boolean =
        settings.routeBypassPrivate ||
            effectiveRules(settings).any { it.enabled && it.outbound == RoutingOutbound.DIRECT }

    fun applyUserRules(rulesOut: JSONArray, settings: AppSettings, proxyTag: String) {
        if (settings.routeBypassPrivate) {
            addIpRule(rulesOut, PRIVATE_CIDRS, "direct")
        }
        for (rule in effectiveRules(settings)) {
            if (!rule.enabled) continue
            val tag = outboundTag(rule.outbound, proxyTag)
            when (rule.kind) {
                RoutingRuleKind.GEOSITE -> {
                    val token = normalizeGeoSite(rule.matcher) ?: continue
                    addDomainRule(rulesOut, listOf(token), tag)
                }
                RoutingRuleKind.GEOIP -> {
                    val token = normalizeGeoIp(rule.matcher) ?: continue
                    if (token == "geoip:private") addIpRule(rulesOut, PRIVATE_CIDRS, tag)
                    else addIpRule(rulesOut, listOf(token), tag)
                }
                RoutingRuleKind.DOMAIN -> {
                    val domains = splitDomains(rule.matcher)
                    if (domains.isNotEmpty()) addDomainRule(rulesOut, domains, tag)
                }
                RoutingRuleKind.IP -> {
                    val ips = splitIps(rule.matcher)
                    if (ips.isNotEmpty()) addIpRule(rulesOut, ips, tag)
                }
                RoutingRuleKind.PORT -> {
                    val ports = rule.matcher.trim()
                    if (ports.isBlank()) continue
                    rulesOut.put(
                        JSONObject()
                            .put("type", "field")
                            .put("port", ports)
                            .put("outboundTag", tag)
                    )
                }
            }
        }
    }

    fun outboundTag(outbound: RoutingOutbound, proxyTag: String): String = when (outbound) {
        RoutingOutbound.PROXY -> proxyTag
        RoutingOutbound.DIRECT -> "direct"
        RoutingOutbound.BLOCK -> "block"
    }

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

    private fun splitHasGeoIp(raw: String): Boolean =
        raw.split(',', '\n', '\r', ';').any {
            it.trim().startsWith("geoip:", true) && !it.trim().equals("geoip:private", true)
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
}
