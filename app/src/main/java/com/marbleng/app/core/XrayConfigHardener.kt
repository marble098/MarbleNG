package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.RoutingMode
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigHardener {
    private val infra = setOf("freedom", "blackhole", "dns", "loopback")
    private val compatibilityDependencyProtocols = setOf(
        "freedom", "http", "shadowsocks", "socks", "trojan", "vless", "vmess", "hysteria", "wireguard"
    )
    private val muxProtocols = setOf("shadowsocks", "trojan", "vless", "vmess")

    /**
     * RFC1918/RFC6598/RFC3927/link-local/loopback/multicast ranges. Kept as literal CIDRs so
     * "private bypass" never depends on geoip.dat: Xray matches these without any geo database.
     */
    private val PRIVATE_CIDRS = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "100.64.0.0/10",
        "127.0.0.0/8", "169.254.0.0/16", "192.0.0.0/24", "192.88.99.0/24",
        "198.18.0.0/15", "198.51.100.0/24", "203.0.113.0/24", "224.0.0.0/4", "240.0.0.0/4",
        "::1/128", "fc00::/7", "fe80::/10"
    )


    /**
     * Compose client -> entry -> exit -> Internet.
     *
     * transportLayer=true preserves the exit outbound's REALITY/XHTTP/gRPC/etc streamSettings
     * while Xray dials that transport through the entry outbound.
     */
    fun composeChain(entrySource: String, exitSource: String): String {
        fun firstProxy(root: JSONObject): JSONObject {
            val outbounds = root.optJSONArray("outbounds") ?: error("Xray JSON has no outbounds")
            for (i in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(i) ?: continue
                if (outbound.optString("protocol") !in infra) {
                    return JSONObject(outbound.toString())
                }
            }
            error("Xray JSON has no proxy outbound")
        }

        val entry = firstProxy(JSONObject(entrySource))
            .put("tag", "chain-entry")

        val exitRoot = JSONObject(exitSource)
        val exit = firstProxy(exitRoot)
            .put("tag", "proxy")
            .put(
                "proxySettings",
                JSONObject()
                    .put("tag", "chain-entry")
                    .put("transportLayer", true)
            )

        exitRoot.put("outbounds", JSONArray().put(exit).put(entry))
        return exitRoot.toString()
    }

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
            val protocol = outbound.optString("protocol").lowercase()
            if (tag != firstTag) {
                if (!settings.configCompatibilityMode && protocol in infra) return
                if (settings.configCompatibilityMode && protocol !in compatibilityDependencyProtocols) return
            }
            if (!keep.add(tag)) return
            outbound.optJSONObject("proxySettings")?.optString("tag")
                ?.takeIf { it.isNotBlank() }?.let(::add)
            if (settings.configCompatibilityMode) {
                outbound.optJSONObject("streamSettings")?.optJSONObject("sockopt")
                    ?.optString("dialerProxy")?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        add(firstTag)

        // Mux is opt-in. The official Xray docs describe it as connection reuse / latency
        // reduction, not as a bulk-throughput accelerator.
        byTag[firstTag]?.let { selected ->
            if (selected.optString("protocol").lowercase() in muxProtocols) {
                selected.put(
                    "mux",
                    JSONObject()
                        .put("enabled", settings.muxEnabled)
                        .put("concurrency", settings.muxConcurrency.coerceIn(1, 128))
                        .put("xudpConcurrency", settings.muxXudpConcurrency.coerceIn(1, 1024))
                        .put("xudpProxyUDP443", settings.muxUdp443.takeIf { it in setOf("reject", "allow", "skip") } ?: "skip")
                )
            } else if (settings.configCompatibilityMode) {
                selected.remove("mux")
            }
        }

        // Fragment is attached as a Freedom dialer only to a physical proxy hop.
        // For a two-hop chain, the exit already has proxySettings -> entry, so Fragment lands
        // on the entry hop and never destroys the exit transport layer.
        val fragmentOutbound = if (settings.fragmentEnabled) {
            JSONObject()
                .put("tag", "fragment-direct")
                .put("protocol", "freedom")
                .put(
                    "settings",
                    JSONObject().put(
                        "fragment",
                        JSONObject()
                            .put("packets", settings.fragmentPackets.ifBlank { "tlshello" })
                            .put("length", settings.fragmentLength.ifBlank { "100-200" })
                            .put("interval", settings.fragmentInterval.ifBlank { "10-20" })
                    )
                )
        } else {
            null
        }

        if (fragmentOutbound != null) {
            keep.forEach { tag ->
                val outbound = byTag[tag] ?: return@forEach
                val protocol = outbound.optString("protocol").lowercase()
                val method = outbound.optJSONObject("streamSettings")?.optString("method")?.lowercase().orEmpty()
                if (!fragmentEligible(protocol, method)) return@forEach
                val alreadyChained = outbound.optJSONObject("proxySettings")
                    ?.optString("tag")
                    ?.isNotBlank() == true

                val stream = outbound.optJSONObject("streamSettings")
                    ?: JSONObject().also { outbound.put("streamSettings", it) }
                val sockopt = stream.optJSONObject("sockopt")
                    ?: JSONObject().also { stream.put("sockopt", it) }

                if (!alreadyChained && sockopt.optString("dialerProxy").isBlank()) {
                    sockopt.put("dialerProxy", "fragment-direct")
                }
            }
        }

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
                // targetStrategy defaults to AsIs. Remove imported overrides so endpoint
                // resolution can be controlled by sockopt and Happy Eyeballs.
                outbound.remove("targetStrategy")
                outbound.remove("sendThrough")

                if (endpointDomains(outbound).isNotEmpty()) {
                    val protocol = outbound.optString("protocol").lowercase()
                    if (protocol == "wireguard") {
                        val settingsObject = outbound.optJSONObject("settings")
                            ?: JSONObject().also { outbound.put("settings", it) }
                        settingsObject.put(
                            "domainStrategy",
                            forceDomainStrategy(settingsObject.optString("domainStrategy"), settings.dnsQueryStrategy)
                        )
                    } else {
                        val stream = outbound.optJSONObject("streamSettings")
                            ?: JSONObject().also { outbound.put("streamSettings", it) }
                        val sockopt = stream.optJSONObject("sockopt")
                            ?: JSONObject().also { stream.put("sockopt", it) }
                        val endpointStrategy = forceDomainStrategy(sockopt.optString("domainStrategy"), settings.dnsQueryStrategy)
                        sockopt.put("domainStrategy", endpointStrategy)
                        val chained = outbound.optJSONObject("proxySettings")?.optString("tag")?.isNotBlank() == true
                        val method = stream.optString("method").lowercase()
                        val tcpTransport = method !in setOf("hysteria", "mkcp")
                        if (settings.adaptiveDualStackEnabled && endpointStrategy == "ForceIP" && tcpTransport &&
                            !chained && sockopt.optString("dialerProxy").isBlank()) {
                            sockopt.put("happyEyeballs", JSONObject().put("tryDelayMs", 250)
                                .put("prioritizeIPv6", false).put("interleave", 1).put("maxConcurrentTry", 3))
                        } else {
                            sockopt.remove("happyEyeballs")
                        }
                    }
                }

                out.put(outbound)
            }
        }

        if (fragmentOutbound != null) out.put(fragmentOutbound)

        if (needsDirect) {
            out.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        }

        // Traditional Android DNS packets are intercepted before they can emerge as plaintext
        // port-53 traffic from the proxy exit. A/AAAA are imported into Xray's built-in DNS module;
        // other record types receive an empty NOERROR response so they cannot bypass encrypted DNS.
        if (settings.dnsHijackEnabled) {
            out.put(
                JSONObject()
                    .put("tag", "dns-out")
                    .put("protocol", "dns")
                    .put(
                        "settings",
                        JSONObject().put(
                            "rules",
                            JSONArray()
                                .put(JSONObject().put("action", "hijack").put("qType", "1,28"))
                                .put(JSONObject().put("action", "return").put("rCode", 0))
                        )
                    )
            )
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

        val queryStrategy = settings.dnsQueryStrategy.takeIf {
            it in setOf("UseIP", "UseIPv4", "UseIPv6", "UseSystem")
        } ?: "UseIP"

        val bootstrapIps = listOf(settings.dnsPrimaryIp, settings.dnsSecondaryIp)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val remoteDoh = listOf(settings.dnsPrimaryDoH, settings.dnsSecondaryDoH)
            .map { it.trim() }
            .filter { it.startsWith("https://") }
            .distinct()
            .ifEmpty {
                listOf(
                    "https://1.1.1.1/dns-query",
                    "https://8.8.8.8/dns-query"
                )
            }

        val dnsServers = JSONArray()

        if (bootstrapDomains.isNotEmpty()) {
            bootstrapIps.forEach { ip ->
                dnsServers.put(
                    JSONObject()
                        .put("address", "https+local://$ip/dns-query")
                        .put("domains", JSONArray(bootstrapDomains.map { "full:$it" }))
                        .put("skipFallback", true)
                        .put("queryStrategy", queryStrategy)
                        .put("timeoutMs", 2500)
                )
            }
        }

        // Non-local DoH enters routing with xgc-dns and is forced through the selected proxy.
        remoteDoh.forEach { address ->
            dnsServers.put(
                JSONObject()
                    .put("address", address)
                    .put("queryStrategy", queryStrategy)
                    .put("timeoutMs", 4000)
            )
        }

        src.put(
            "dns",
            JSONObject()
                .put("servers", dnsServers)
                .put("queryStrategy", queryStrategy)
                .put("useSystemHosts", false)
                .put("disableFallbackIfMatch", bootstrapDomains.isNotEmpty())
                .put("enableParallelQuery", true)
                .put("tag", "xgc-dns")
        )

        val rules = JSONArray()

        // DNS interception has highest priority: any app attempting classic UDP/TCP :53 is handed
        // to dns-out, which hijacks A/AAAA into the encrypted built-in DNS path above.
        if (settings.dnsHijackEnabled) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("socks-in"))
                    .put("port", "53")
                    .put("outboundTag", "dns-out")
            )
        }

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
                directIps += PRIVATE_CIDRS
            }

            if (settings.routingMode == RoutingMode.GEO_DIRECT || settings.routingMode == RoutingMode.CUSTOM) {
                splitTokens(settings.routeGeoIpTags).forEach { tag ->
                    when {
                        tag.equals("private", true) || tag.equals("geoip:private", true) -> directIps += PRIVATE_CIDRS
                        tag.startsWith("geoip:") -> directIps += tag
                        else -> directIps += "geoip:$tag"
                    }
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
                        if ((key.equals("address", ignoreCase = true) || key.equals("endpoint", ignoreCase = true)) && child is String) {
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
        var host = raw.trim().trimEnd('.')
        if (host.isBlank() || host.contains('/')) return null
        if (host.startsWith("[")) {
            val close = host.indexOf(']')
            if (close <= 1) return null
            host = host.substring(1, close)
        } else {
            val colon = host.lastIndexOf(':')
            if (colon > 0 && host.indexOf(':') == colon && host.substring(colon + 1).toIntOrNull() != null) {
                host = host.substring(0, colon)
            }
        }
        host = host.trim().trimEnd('.')
        if (host.isBlank() || host.equals("localhost", true) || host.contains(':')) return null
        if (Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$").matches(host)) return null
        if (!host.any { it.isLetter() }) return null
        return host.lowercase()
    }

    private fun fragmentEligible(protocol: String, method: String): Boolean =
        protocol in setOf("http", "shadowsocks", "socks", "trojan", "vless", "vmess") &&
            method !in setOf("hysteria", "mkcp")

    private fun forceDomainStrategy(
        current: String,
        queryStrategy: String
    ): String {
        // Physical single-stack networks remain strict. Dual-stack defaults to ForceIP so Xray
        // can race IPv4/IPv6 instead of silently falling back to forced IPv4.
        if (
            queryStrategy ==
            "UseIPv4"
        ) {
            return "ForceIPv4"
        }

        if (
            queryStrategy ==
            "UseIPv6"
        ) {
            return "ForceIPv6"
        }

        return when (current) {
            "ForceIP",
            "ForceIPv4",
            "ForceIPv6",
            "ForceIPv4v6",
            "ForceIPv6v4" ->
                current

            "UseIP" ->
                "ForceIP"

            "UseIPv6" ->
                "ForceIPv6"

            "UseIPv4v6" ->
                "ForceIPv4v6"

            "UseIPv6v4" ->
                "ForceIPv6v4"

            "UseIPv4" ->
                "ForceIPv4"

            else ->
                "ForceIP"
        }
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

    private fun splitIps(raw: String): List<String> = splitTokens(raw).flatMap { token ->
        if (token.equals("geoip:private", true)) PRIVATE_CIDRS else listOf(token)
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
