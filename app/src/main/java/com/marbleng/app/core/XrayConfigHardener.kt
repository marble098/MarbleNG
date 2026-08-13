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

        /*
         * Prevent proxy-server hostname resolution from falling back to Android/system DNS.
         * Xray's Force* sockopt strategies use the built-in DNS module and fail closed instead
         * of falling back to Go's system resolver. Endpoint domains receive two direct encrypted
         * bootstrap DoH resolvers; all ordinary DNS stays on remote DoH routed through the proxy.
         */
        val bootstrapDomains = linkedSetOf<String>()
        keep.forEach { tag ->
            byTag[tag]?.let { bootstrapDomains += endpointDomains(it) }
        }

        val out = JSONArray()
        keep.forEach { tag ->
            byTag[tag]?.let { outbound ->
                outbound.put("targetStrategy", "AsIs")
                outbound.remove("sendThrough")

                if (endpointDomains(outbound).isNotEmpty()) {
                    val stream = outbound.optJSONObject("streamSettings")
                        ?: JSONObject().also { outbound.put("streamSettings", it) }
                    val sockopt = stream.optJSONObject("sockopt")
                        ?: JSONObject().also { stream.put("sockopt", it) }
                    sockopt.put(
                        "domainStrategy",
                        forceDomainStrategy(sockopt.optString("domainStrategy"))
                    )
                }

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

        val dnsServers = JSONArray()
        if (bootstrapDomains.isNotEmpty()) {
            val domains = JSONArray(bootstrapDomains.map { "full:$it" })
            dnsServers.put(
                JSONObject()
                    .put("address", "https+local://1.1.1.1/dns-query")
                    .put("domains", domains)
                    .put("skipFallback", true)
                    .put("queryStrategy", "UseIP")
                    .put("timeoutMs", 2500)
            )
            dnsServers.put(
                JSONObject()
                    .put("address", "https+local://8.8.8.8/dns-query")
                    .put("domains", JSONArray(bootstrapDomains.map { "full:$it" }))
                    .put("skipFallback", true)
                    .put("queryStrategy", "UseIP")
                    .put("timeoutMs", 2500)
            )
        }

        // Non-local DoH enters routing with xgc-dns and is forced through the selected proxy.
        dnsServers.put(
            JSONObject()
                .put("address", "https://1.1.1.1/dns-query")
                .put("queryStrategy", "UseIP")
                .put("timeoutMs", 4000)
        )
        dnsServers.put(
            JSONObject()
                .put("address", "https://8.8.8.8/dns-query")
                .put("queryStrategy", "UseIP")
                .put("timeoutMs", 4000)
        )

        src.put(
            "dns",
            JSONObject()
                .put("servers", dnsServers)
                .put("queryStrategy", "UseIP")
                .put("useSystemHosts", false)
                .put("disableFallbackIfMatch", bootstrapDomains.isNotEmpty())
                .put("enableParallelQuery", true)
                .put("tag", "xgc-dns")
        )

        val rules = JSONArray()

        // Xray's ordinary built-in DNS requests always stay inside the selected proxy path.
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

        /*
         * Remove unrelated runtime subsystems from imported full JSON configs. They are not needed
         * by MarbleNG's single selected outbound and can open listeners, start active probes or
         * retain stale API/statistics dependencies that make mobile startup less deterministic.
         */
        listOf("api", "reverse", "metrics", "stats", "observatory", "burstObservatory", "fakedns")
            .forEach(src::remove)

        verify(src, socksPort, firstTag, needsDirect)
        return src.toString(2)
    }

    private fun endpointDomains(outbound: JSONObject): List<String> {
        val found = linkedSetOf<String>()

        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = value.opt(key)
                        if (key.equals("address", ignoreCase = true) && child is String) {
                            normalizeDomain(child)?.let(found::add)
                        } else {
                            walk(child)
                        }
                    }
                }
                is JSONArray -> {
                    for (i in 0 until value.length()) walk(value.opt(i))
                }
            }
        }

        walk(outbound.optJSONObject("settings"))
        return found.toList()
    }

    private fun normalizeDomain(raw: String): String? {
        val host = raw.trim().removePrefix("[").removeSuffix("]").trimEnd('.')
        if (host.isBlank() || host.equals("localhost", ignoreCase = true)) return null
        if (host.contains(':')) return null // IPv6 literals or host:port are not DNS names.
        if (Regex("""^\d{1,3}(?:\.\d{1,3}){3}$""").matches(host)) return null
        if (!host.any { it.isLetter() }) return null
        return host.lowercase()
    }

    private fun forceDomainStrategy(current: String): String = when (current) {
        "ForceIP", "ForceIPv4", "ForceIPv6", "ForceIPv4v6", "ForceIPv6v4" -> current
        "UseIP" -> "ForceIP"
        "UseIPv6" -> "ForceIPv6"
        "UseIPv4v6" -> "ForceIPv4v6"
        "UseIPv6v4" -> "ForceIPv6v4"
        "UseIPv4" -> "ForceIPv4"
        else -> "ForceIPv4"
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

    private fun splitIps(raw: String): List<String> = splitTokens(raw)

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
            if (outbound.optString("tag") == "direct" && outbound.optString("protocol") == "freedom") {
                direct = true
            }
        }
        require(selected) { "Selected proxy outbound missing" }
        require(direct == needsDirect) { "Routing direct-outbound invariant failed" }

        val dns = o.getJSONObject("dns")
        require(!dns.toString().contains("\"localhost\"")) { "System DNS must not be used" }
    }
}
