package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.RoutingMode
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigHardener {
    private val infra = setOf("freedom", "blackhole", "dns", "loopback")

    fun harden(source: String, socksPort: Int, settings: AppSettings = AppSettings()): String {
        val src = JSONObject(source)
        val old = src.optJSONArray("outbounds") ?: JSONArray()
        val byTag = linkedMapOf<String, JSONObject>()
        var firstTag = ""

        for (i in 0 until old.length()) {
            val orig = old.optJSONObject(i) ?: continue
            val clone = JSONObject(orig.toString())
            val proto = clone.optString("protocol")
            val tag = clone.optString("tag").ifBlank {
                if (proto !in infra && firstTag.isBlank()) "proxy" else "out-$i"
            }
            clone.put("tag", tag)
            byTag[tag] = clone
            if (proto !in infra && firstTag.isBlank()) firstTag = tag
        }
        require(firstTag.isNotBlank()) { "No proxy outbound" }

        val keep = linkedSetOf<String>()
        fun add(tag: String) {
            val outbound = byTag[tag] ?: return
            if (outbound.optString("protocol") in infra) return
            if (!keep.add(tag)) return
            outbound.optJSONObject("proxySettings")
                ?.optString("tag")
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
        }
        add(firstTag)

        val needsDirect = settings.routingMode != RoutingMode.PROXY_ALL ||
            settings.routeDirectDomains.isNotBlank() ||
            settings.routeDirectIps.isNotBlank()

        val out = JSONArray()
        keep.forEach { tag ->
            byTag[tag]?.let { outbound ->
                outbound.put("targetStrategy", "AsIs")
                outbound.remove("sendThrough")
                out.put(outbound)
            }
        }
        if (needsDirect) {
            out.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        }
        out.put(JSONObject().put("tag", "block").put("protocol", "blackhole"))

        val inbound = JSONObject()
            .put("tag", "socks-in")
            .put("listen", "127.0.0.1")
            .put("port", socksPort)
            .put("protocol", "socks")
            .put("settings", JSONObject().put("udp", true))
            .put(
                "sniffing",
                JSONObject()
                    .put("enabled", true)
                    .put("routeOnly", true)
                    .put("destOverride", JSONArray(listOf("http", "tls", "quic")))
            )

        src.put("inbounds", JSONArray().put(inbound))
        src.put("outbounds", out)

        src.put(
            "dns",
            JSONObject()
                .put(
                    "servers",
                    JSONArray()
                        .put(JSONObject().put("address", "https://1.1.1.1/dns-query").put("queryStrategy", "UseIPv4"))
                        .put(JSONObject().put("address", "https://8.8.8.8/dns-query").put("queryStrategy", "UseIPv4"))
                )
                .put("queryStrategy", "UseIPv4")
                .put("useSystemHosts", false)
                .put("enableParallelQuery", true)
                .put("tag", "xgc-dns")
        )

        val rules = JSONArray()

        // Xray's own DNS requests always stay inside the selected proxy path.
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("inboundTag", JSONArray().put("xgc-dns"))
                .put("outboundTag", firstTag)
        )

        addDomainRule(rules, splitDomains(settings.routeBlockDomains), "block")
        addIpRule(rules, splitIps(settings.routeBlockIps), "block")

        if (settings.routeBlockAds && settings.routeAdsTag.isNotBlank()) {
            addDomainRule(rules, listOf("geosite:${settings.routeAdsTag.trim()}"), "block")
        }

        // Explicit proxy rules are evaluated before direct rules so users can create exceptions.
        addDomainRule(rules, splitDomains(settings.routeProxyDomains), firstTag)

        if (needsDirect) {
            val directDomains = linkedSetOf<String>()
            val directIps = linkedSetOf<String>()

            if (settings.routeBypassPrivate && settings.routingMode != RoutingMode.PROXY_ALL) {
                directIps += "geoip:private"
            }

            if (settings.routingMode == RoutingMode.GEO_DIRECT || settings.routingMode == RoutingMode.CUSTOM) {
                splitTokens(settings.routeGeoIpTags).forEach { tag ->
                    directIps += if (tag.startsWith("geoip:")) tag else "geoip:$tag"
                }
                splitTokens(settings.routeGeoSiteTags).forEach { tag ->
                    directDomains += if (tag.startsWith("geosite:")) tag else "geosite:$tag"
                }
            }

            if (settings.routingMode == RoutingMode.CUSTOM || settings.routeDirectDomains.isNotBlank()) {
                directDomains += splitDomains(settings.routeDirectDomains)
            }
            if (settings.routingMode == RoutingMode.CUSTOM || settings.routeDirectIps.isNotBlank()) {
                directIps += splitIps(settings.routeDirectIps)
            }

            addDomainRule(rules, directDomains.toList(), "direct")
            addIpRule(rules, directIps.toList(), "direct")
        }

        // Fail-closed fallback: anything not matched above stays on the proxy outbound.
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("inboundTag", JSONArray().put("socks-in"))
                .put("outboundTag", firstTag)
        )

        val domainStrategy = settings.routeDomainStrategy.takeIf {
            it in setOf("AsIs", "IPIfNonMatch", "IPOnDemand")
        } ?: "AsIs"

        src.put("routing", JSONObject().put("domainStrategy", domainStrategy).put("rules", rules))
        src.put("log", JSONObject().put("loglevel", "warning"))
        listOf("api", "reverse", "metrics", "stats", "observatory", "burstObservatory", "fakedns").forEach(src::remove)

        verify(src, socksPort, firstTag, needsDirect)
        return src.toString(2)
    }

    private fun addDomainRule(rules: JSONArray, values: List<String>, outboundTag: String) {
        if (values.isEmpty()) return
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("domain", JSONArray(values))
                .put("outboundTag", outboundTag)
        )
    }

    private fun addIpRule(rules: JSONArray, values: List<String>, outboundTag: String) {
        if (values.isEmpty()) return
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("ip", JSONArray(values))
                .put("outboundTag", outboundTag)
        )
    }

    private fun splitTokens(raw: String): List<String> = raw
        .split(',', '\n', '\r', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    private fun splitDomains(raw: String): List<String> = splitTokens(raw).map { value ->
        when {
            value.startsWith("geosite:", true) -> value
            value.startsWith("domain:", true) -> value
            value.startsWith("full:", true) -> value
            value.startsWith("keyword:", true) -> value
            value.startsWith("regexp:", true) -> value
            else -> "domain:$value"
        }
    }

    private fun splitIps(raw: String): List<String> = splitTokens(raw).map { value ->
        if (value.startsWith("geoip:", true)) value else value
    }

    private fun verify(o: JSONObject, port: Int, selectedTag: String, needsDirect: Boolean) {
        val ins = o.getJSONArray("inbounds")
        require(
            ins.length() == 1 &&
                ins.getJSONObject(0).getInt("port") == port &&
                ins.getJSONObject(0).getString("listen") == "127.0.0.1"
        ) { "SOCKS hardening failed" }

        val outs = o.getJSONArray("outbounds")
        var selected = false
        var direct = false
        for (i in 0 until outs.length()) {
            val outbound = outs.getJSONObject(i)
            if (outbound.optString("tag") == selectedTag) selected = true
            if (outbound.optString("tag") == "direct" && outbound.optString("protocol") == "freedom") direct = true
        }
        require(selected) { "Selected proxy outbound missing" }
        require(direct == needsDirect) { "Routing direct-outbound invariant failed" }
    }
}
