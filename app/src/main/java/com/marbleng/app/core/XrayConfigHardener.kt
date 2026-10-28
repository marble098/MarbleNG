package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.IranModePolicy
import com.marbleng.app.model.RoutingMode
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigHardener {
    private val infra = setOf("freedom", "direct", "blackhole", "block", "dns", "loopback")
    private val compatibilityDependencyProtocols = setOf(
        "freedom", "http", "shadowsocks", "socks", "trojan", "vless", "vmess", "hysteria", "wireguard"
    )
    private val muxProtocols = setOf("shadowsocks", "trojan", "vless", "vmess")

    private val STOCK_BOOTSTRAP_DNS_IPS = listOf(
        "1.1.1.1", "8.8.8.8", "9.9.9.9", "1.0.0.1", "8.8.4.4", "149.112.112.112"
    )
    private val STOCK_BOOTSTRAP_DNS_IPV6 = listOf(
        "2606:4700:4700::1111", "2001:4860:4860::8888", "2620:fe::fe"
    )
    private const val MAX_BOOTSTRAP_DNS_IPS = 6

    private val PRIVATE_CIDRS = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "100.64.0.0/10",
        "127.0.0.0/8", "169.254.0.0/16", "192.0.0.0/24", "192.88.99.0/24",
        "198.18.0.0/15", "198.51.100.0/24", "203.0.113.0/24", "224.0.0.0/4", "240.0.0.0/4",
        "::1/128", "fc00::/7", "fe80::/10"
    )

    fun composeChain(sources: List<String>): String {
        require(sources.size >= 2) { "A chain needs at least two hops" }

        data class Segment(
            val primaryTag: String,
            val byTag: LinkedHashMap<String, JSONObject>
        )

        val segments = sources.mapIndexed { segmentIndex, source ->

            val hopRepairs = XrayConfigRepairs.apply(source)
            if (hopRepairs.changed) XrayConfigRepairs.record(hopRepairs.repairs)
            val root = XrayConfigAdapter.document(if (hopRepairs.changed) hopRepairs.document else source)
            val imported = root.optJSONArray("outbounds") ?: error("Chain hop ${segmentIndex + 1} has no outbounds")
            val originalTags = linkedMapOf<String, String>()
            val clones = mutableListOf<Pair<String, JSONObject>>()
            var primaryOriginal = ""

            for (index in 0 until imported.length()) {
                val outbound = imported.optJSONObject(index)?.let { JSONObject(it.toString()) } ?: continue
                val protocol = outbound.optString("protocol").lowercase()
                val oldTag = outbound.optString("tag").ifBlank { "out-$index" }
                val uniqueOldTag = if (oldTag in originalTags) "$oldTag-$index" else oldTag
                val newTag = "chain-$segmentIndex-$index"
                originalTags[uniqueOldTag] = newTag
                clones += uniqueOldTag to outbound
                if (primaryOriginal.isBlank() && protocol !in infra) primaryOriginal = uniqueOldTag
            }
            require(primaryOriginal.isNotBlank()) { "Chain hop ${segmentIndex + 1} has no proxy outbound" }

            val namespaced = linkedMapOf<String, JSONObject>()
            clones.forEach { (oldTag, outbound) ->
                val newTag = originalTags.getValue(oldTag)
                outbound.put("tag", newTag)

                outbound.optJSONObject("streamSettings")?.optJSONObject("sockopt")?.let { sockopt ->
                    originalTags[sockopt.optString("dialerProxy")]?.let { sockopt.put("dialerProxy", it) }
                }
                namespaced[newTag] = outbound
            }
            Segment(originalTags.getValue(primaryOriginal), namespaced)
        }

        fun tail(segment: Segment): JSONObject {
            var tag = segment.primaryTag
            val visited = mutableSetOf<String>()
            while (visited.add(tag)) {
                val outbound = segment.byTag[tag] ?: break
                val next = outbound.optJSONObject("streamSettings")
                    ?.optJSONObject("sockopt")
                    ?.optString("dialerProxy")
                    .orEmpty()
                if (next.isBlank() || next !in segment.byTag) return outbound
                tag = next
            }
            error("Chain hop contains a dialerProxy cycle")
        }

        for (index in 1 until segments.size) {
            val hop = tail(segments[index])
            val stream = hop.optJSONObject("streamSettings")
                ?: JSONObject().also { hop.put("streamSettings", it) }
            val sockopt = stream.optJSONObject("sockopt")
                ?: JSONObject().also { stream.put("sockopt", it) }
            sockopt.put("dialerProxy", segments[index - 1].primaryTag)
        }

        val exit = segments.last()
        exit.byTag.getValue(exit.primaryTag).put("tag", "proxy")
        val remappedExitTag = exit.primaryTag
        segments.forEach { segment ->
            segment.byTag.values.forEach { outbound ->
                outbound.optJSONObject("streamSettings")?.optJSONObject("sockopt")?.let { sockopt ->
                    if (sockopt.optString("dialerProxy") == remappedExitTag) sockopt.put("dialerProxy", "proxy")
                }
            }
        }

        val ordered = JSONArray().put(exit.byTag.getValue(remappedExitTag))
        segments.asReversed().forEach { segment ->
            segment.byTag.forEach { (tag, outbound) ->
                if (segment !== exit || tag != remappedExitTag) ordered.put(outbound)
            }
        }

        return JSONObject(sources.last())
            .put("outbounds", ordered)
            .toString()
    }

    fun hardenForDelayTest(
        source: String,
        socksPort: Int,
        settings: AppSettings = AppSettings(),
        underlayHasIpv6: Boolean = AddressFamilyPolicy.underlayHasIpv6(),
        link: LinkEvidence = LinkEvidence.UNKNOWN
    ): String = harden(source, socksPort, settings, link, underlayHasIpv6)

    fun hardenForNativeRank(
        source: String,
        settings: AppSettings = AppSettings(),
        underlayHasIpv6: Boolean = AddressFamilyPolicy.underlayHasIpv6()
    ): String {
        val root = JSONObject(harden(source, 19091, settings, underlayHasIpv6 = underlayHasIpv6))
        root.put("inbounds", JSONArray())

        listOf(
            "fakedns", "routing", "stats", "policy", "api", "reverse", "metrics",
            "observatory", "burstObservatory"
        ).forEach(root::remove)

        val dns = root.optJSONObject("dns") ?: error("Rank requires encrypted DNS")
        val servers = dns.optJSONArray("servers") ?: error("Rank requires encrypted DNS servers")
        val encrypted = JSONArray()
        for (index in 0 until servers.length()) {
            val server = servers.optJSONObject(index) ?: continue
            if (server.optString("address") != "fakedns") encrypted.put(server)
        }
        require(encrypted.length() > 0) { "No encrypted rank resolver" }
        dns.put("servers", encrypted)

        root.optJSONArray("outbounds")?.let { outbounds ->
            for (index in 0 until outbounds.length()) {
                outbounds.optJSONObject(index)?.remove("mux")
            }
        }
        root.put("log", JSONObject().put("loglevel", "none"))
        return root.toString()
    }

    fun harden(
        source: String,
        socksPort: Int,
        settings: AppSettings = AppSettings(),
        link: LinkEvidence = LinkEvidence.UNKNOWN,
        underlayHasIpv6: Boolean = AddressFamilyPolicy.underlayHasIpv6()
    ): String {
        val repairs = XrayConfigRepairs.apply(source)
        if (repairs.changed) XrayConfigRepairs.record(repairs.repairs)
        val src = XrayConfigAdapter.document(if (repairs.changed) repairs.document else source)
        val old = src.optJSONArray("outbounds") ?: JSONArray()
        val byTag = linkedMapOf<String, JSONObject>()
        var firstTag = ""

        val importedChainHops = mutableSetOf<String>()

        renameReservedImportedTags(old)

        for (i in 0 until old.length()) {
            val orig = old.optJSONObject(i) ?: continue
            val clone = JSONObject(orig.toString())
            val proto = clone.optString("protocol")
            val tag = clone.optString("tag").ifBlank {
                if (isSelectableProxy(clone) && firstTag.isBlank()) "proxy" else "out-$i"
            }
            clone.put("tag", tag)
            if (tag in byTag) {
                continue
            }
            byTag[tag] = clone
            if (isSelectableProxy(clone) && firstTag.isBlank()) firstTag = tag
            if (orig.optJSONObject("proxySettings")?.optString("tag").orEmpty().isNotBlank() ||
                orig.optJSONObject("streamSettings")?.optJSONObject("sockopt")
                    ?.optString("dialerProxy").orEmpty().isNotBlank()
            ) {
                importedChainHops += tag
            }
        }

        if (firstTag.isBlank()) {
            firstTag = byTag.values.firstOrNull { outbound ->
                val protocol = outbound.optString("protocol").lowercase()
                protocol in setOf("freedom", "direct")
            }?.optString("tag").orEmpty()
        }
        require(firstTag.isNotBlank()) { "No proxy outbound" }

        if (AddressFamilyPolicy.preference(settings, true) == IpFamilyPreference.IPV6_ONLY) {
            byTag.values.forEach { outbound ->
                require(!hasIpv4LiteralEndpoint(outbound, settings)) { AddressFamilyPolicy.IPV4_LITERAL_DISABLED }
            }
        }

        val keep = linkedSetOf<String>()
        fun add(tag: String, viaDialer: Boolean = false) {
            val outbound = byTag[tag] ?: return
            val protocol = outbound.optString("protocol").lowercase()
            if (tag != firstTag) {
                val fragmentHop = protocol in setOf("freedom", "direct") &&
                    (hasFragment(outbound) || viaDialer)
                val noiseHop = protocol in setOf("freedom", "direct") && hasNoises(outbound)
                if (!fragmentHop && !noiseHop) {
                    if (!settings.configCompatibilityMode && protocol in infra) return
                    if (settings.configCompatibilityMode && protocol !in compatibilityDependencyProtocols) return
                }
            }
            if (!keep.add(tag)) return
            outbound.optJSONObject("proxySettings")?.optString("tag")
                ?.takeIf { it.isNotBlank() }?.let(::add)
            outbound.optJSONObject("streamSettings")?.optJSONObject("sockopt")
                ?.optString("dialerProxy")?.takeIf { it.isNotBlank() }?.let { add(it, viaDialer = true) }
        }
        add(firstTag)

        byTag.keys.filter { tag ->
            byTag[tag]?.let { hasNoises(it) && !hasFragment(it) } == true
        }.forEach { add(it) }

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

        val iranActive = settings.iranModePolicy != IranModePolicy.OFF &&
            settings.iranModeCountermeasures

        val needsDirect = RoutingEngine.needsDirectOutbound(settings) ||
            settings.routeDirectDomains.isNotBlank() ||
            settings.routeDirectIps.isNotBlank()

        val bootstrapDomains = linkedSetOf<String>()
        keep.forEach { tag ->
            byTag[tag]?.let { bootstrapDomains += endpointDomains(it) }
        }

        val dnsPlan = AddressFamilyPolicy.plan(
            settings = settings,
            underlayHasIpv6 = underlayHasIpv6,
            link = link
        )

        val ipv6ResolverAllowed = underlayHasIpv6 &&
            dnsPlan.preference != IpFamilyPreference.IPV4_ONLY
        fun resolverFamilyAllowed(candidate: String): Boolean {
            val host = candidate.removePrefix("[").removeSuffix("]").substringBefore('/')
            val isV6Literal = host.contains(':')
            return if (isV6Literal) ipv6ResolverAllowed
                else dnsPlan.preference != IpFamilyPreference.IPV6_ONLY
        }

        val out = JSONArray()
        keep.forEach { tag ->
            byTag[tag]?.let { outbound ->

                outbound.remove("targetStrategy")
                outbound.remove("sendThrough")
                // MARBLE_FREEDOM_SOCKOPT_STRATEGY_V163 — an imported freedom hop that still

                if (outbound.optString("protocol").lowercase() in setOf("freedom", "direct")) {
                    val legacy = outbound.optJSONObject("settings")
                    val imported = outbound.optJSONObject("streamSettings")?.optJSONObject("sockopt")
                        ?.optString("domainStrategy").orEmpty()
                        .ifBlank { legacy?.optString("targetStrategy").orEmpty() }
                        .ifBlank { legacy?.optString("domainStrategy").orEmpty() }
                    if (legacy?.has("domainStrategy") == true || legacy?.has("targetStrategy") == true) {
                        writeFreedomResolveStrategy(outbound, imported)
                    }
                }

                if (endpointDomains(outbound).isNotEmpty()) {
                    applyAddressFamily(
                        outbound,
                        settings,
                        underlayHasIpv6,
                        chainedFromImport = tag in importedChainHops
                    )

                    val streamObject = outbound.optJSONObject("streamSettings")
                    val sockoptObject = streamObject?.optJSONObject("sockopt")
                    if (
                        sockoptObject != null &&
                        outbound.optString("protocol").lowercase() != "wireguard"
                    ) {
                        val method = streamObject.optString("method").lowercase()
                        val tcpTransport = method !in setOf("hysteria", "mkcp")

                        val chained = outbound.optString("tag") in importedChainHops
                        if (tcpTransport) {
                            val liveness = SocketLivenessPolicy.forTransport(
                                method,
                                chained,
                                iranMode = iranActive
                            )
                            sockoptObject.put("tcpKeepAliveIdle", liveness.keepAliveIdleSeconds)
                            sockoptObject.put("tcpKeepAliveInterval", liveness.keepAliveIntervalSeconds)
                            sockoptObject.put("tcpUserTimeout", liveness.userTimeoutMs)
                            if (settings.tcpFastOpenEnabled) sockoptObject.put("tcpFastOpen", true) else sockoptObject.remove("tcpFastOpen")
                            if (settings.tcpMaxSeg in 536..9000) sockoptObject.put("tcpMaxSeg", settings.tcpMaxSeg) else sockoptObject.remove("tcpMaxSeg")

                            if (!sockoptObject.has("tcpCongestion")) sockoptObject.put("tcpCongestion", "bbr")
                            if (!sockoptObject.has("tcpMptcp")) sockoptObject.put("tcpMptcp", true)
                        } else {
                            sockoptObject.remove("tcpKeepAliveIdle"); sockoptObject.remove("tcpKeepAliveInterval"); sockoptObject.remove("tcpUserTimeout")
                            sockoptObject.remove("tcpFastOpen"); sockoptObject.remove("tcpMaxSeg")
                            sockoptObject.remove("tcpMptcp"); sockoptObject.remove("tcpCongestion")
                        }
                    }
                }

                outbound.optJSONObject("streamSettings")?.optJSONObject("sockopt")?.let { sockopt ->
                    CoreOptions.applyXrayUserSockopt(sockopt, settings)
                }

                out.put(outbound)
            }
        }

        keep.forEach { tag ->
            val outbound = byTag[tag] ?: return@forEach
            val protocol = outbound.optString("protocol").lowercase()
            if (protocol != "freedom" && protocol != "direct") return@forEach
            val isNoiseOnly = hasNoises(outbound) && !hasFragment(outbound)
            val isPlainServerlessExit = !hasFragment(outbound) &&
                !isNoiseOnly &&
                tag == firstTag &&
                protocol in setOf("freedom", "direct")
            if (!hasFragment(outbound) && !isNoiseOnly && !isPlainServerlessExit) return@forEach
            if (tag in importedChainHops) return@forEach

            val hopPlan = if (isNoiseOnly) {
                AddressFamilyPolicy.plan(
                    settings = settings,
                    underlayHasIpv6 = underlayHasIpv6,
                    tcpTransport = false
                )
            } else {
                applyAddressFamily(outbound, settings, underlayHasIpv6)
            }
            // MARBLE_FREEDOM_SOCKOPT_STRATEGY_V163 — the pinned core reads ONE field for both the

            writeFreedomResolveStrategy(outbound, hopPlan.endpointStrategy)

            if (hasFragment(outbound)) {
                val freedomSockopt = outbound.optJSONObject("streamSettings")
                    ?.optJSONObject("sockopt")
                val dialsDirect = freedomSockopt?.optString("dialerProxy").isNullOrBlank()
                if (freedomSockopt != null && dialsDirect) {
                    CoreSocketPolicy.writeXrayPhysicalTcpSockopt(
                        freedomSockopt,
                        settings,
                        iranActive
                    )
                    CoreOptions.applyXrayUserSockopt(freedomSockopt, settings)
                }
            }
        }

        keep.forEach { tag ->
            byTag[tag]?.let { CoreSocketPolicy.applyDefaultUtlsFingerprint(it) }
        }

        if (needsDirect) {
            out.put(
                JSONObject()
                    .put("tag", "direct")
                    .put("protocol", "freedom")
                    .put("settings", JSONObject())
                    .also { writeFreedomResolveStrategy(it, dnsPlan.endpointStrategy) }
            )
        }

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

        val listen = if (settings.xrayAllowLan) "0.0.0.0" else "127.0.0.1"

        val fakeIpArmed = FakeIpPolicy.isDnsPathArmed(settings) && settings.xraySniffingEnabled

        fun sniffing(): JSONObject = CoreOptions.xraySniffing(settings, fakeIpArmed)
        val inbound = JSONObject()
            .put("tag", "socks-in")
            .put("listen", listen)
            .put("port", socksPort)
            .put("protocol", "socks")
            .put("settings", CoreOptions.xraySocksSettings(settings))
            .put("sniffing", sniffing())

        CoreSocketPolicy.xrayLocalInboundStream(settings)?.let { inbound.put("streamSettings", it) }
        val inbounds = JSONArray().put(inbound)
        val httpPort = settings.xrayHttpInboundPort
        if (httpPort in 1024..65535 && httpPort != socksPort) {
            inbounds.put(
                JSONObject()
                    .put("tag", "http-in")
                    .put("listen", listen)
                    .put("port", httpPort)
                    .put("protocol", "http")
                    .put("settings", JSONObject())
                    .put("sniffing", sniffing())
                    .also { httpInbound ->
                        CoreSocketPolicy.xrayLocalInboundStream(settings)
                            ?.let { httpInbound.put("streamSettings", it) }
                    }
            )
        }
        src.put("inbounds", inbounds)
        src.put("outbounds", out)

        val queryStrategy = dnsPlan.dnsQueryStrategy

        val configuredBootstrapIps = listOf(settings.dnsPrimaryIp, settings.dnsSecondaryIp)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val bootstrapIps = run {
            val configured = configuredBootstrapIps.filter { resolverFamilyAllowed(it) }.distinct()
            val stockV4 = STOCK_BOOTSTRAP_DNS_IPS.filter { resolverFamilyAllowed(it) }
            if (!ipv6ResolverAllowed) {
                configured + stockV4
            } else {
                val alreadyHasV6 = configured.any { it.contains(':') }
                if (alreadyHasV6) {
                    configured + stockV4
                } else {
                    configured + STOCK_BOOTSTRAP_DNS_IPV6.first() + stockV4
                }
            }
        }.distinct().take(MAX_BOOTSTRAP_DNS_IPS)

        val stockDoh = DnsResolverCatalog.STOCK_DOH
        val stockCloudflareDoh = stockDoh[0]
        val stockGoogleDoh = stockDoh[1]

        val excludedResolvers = ResolverEvidencePolicy.normalize(settings.measuredDnsExcludedEndpoints)
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        fun resolverAllowed(url: String): Boolean =
            DnsResolverCatalog.isXrayBootstrapSafe(url) &&
                !ResolverEvidencePolicy.isDomesticResolver(url) &&
                ResolverEvidencePolicy.normalize(url) !in excludedResolvers
        val genericDoh = listOf(settings.dnsPrimaryDoH, settings.dnsSecondaryDoH)
            .map { it.trim() }
            .filter { it.startsWith("https://", ignoreCase = true) }
            .filter(::resolverAllowed)
            .distinct()
        val configuredRemoteDoh = genericDoh.ifEmpty {
            listOf(
                stockCloudflareDoh,
                stockGoogleDoh
            )
        }

        val demotedResolvers = ResolverEvidencePolicy.normalize(
            settings.measuredDnsDemotedEndpoints
        ).split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        fun demoteLast(pool: List<String>): List<String> {
            if (demotedResolvers.isEmpty() || pool.size < 2) return pool
            val (healthy, failing) = pool.partition { candidate ->
                ResolverEvidencePolicy.normalize(candidate) !in demotedResolvers
            }
            return if (healthy.isEmpty()) pool else healthy + failing
        }

        val remoteDoh = if (settings.adaptiveDnsEnabled) {
            val candidates = (configuredRemoteDoh + stockDoh)
                .filter(::resolverAllowed)
                .distinctBy { ResolverEvidencePolicy.normalize(it) }
            val candidateKeys = candidates.map(ResolverEvidencePolicy::normalize).toSet()
            val measuredOrder = settings.measuredDnsResolverOrder
                .split(',', '\n', ';')
                .map { it.trim() }
                .filter {
                    it.startsWith("https://", ignoreCase = true) && resolverAllowed(it) &&
                        ResolverEvidencePolicy.normalize(it) in candidateKeys
                }
                .distinctBy { ResolverEvidencePolicy.normalize(it) }
            val fallbackOrder = demoteLast(candidates)
            (measuredOrder + fallbackOrder.filterNot { candidate ->
                val key = ResolverEvidencePolicy.normalize(candidate)
                measuredOrder.any { ResolverEvidencePolicy.normalize(it) == key }
            }).take(3)
        } else {
            configuredRemoteDoh
                .take(2)
        }

        val dnsServers = JSONArray()

        if (bootstrapDomains.isNotEmpty()) {
            require(bootstrapIps.isNotEmpty()) {
                "No encrypted, family-compatible direct DNS bootstrap for this node on the physical network"
            }
            bootstrapIps.forEachIndexed { index, ip ->

                dnsServers.put(
                    JSONObject()
                        .put("address", "https+local://${dnsHostLiteral(ip)}/dns-query")
                        .put("domains", JSONArray(bootstrapDomains.map { "full:$it" }))
                        .put("skipFallback", true)
                        .put("queryStrategy", queryStrategy)
                        .put("timeoutMs", if (index == 0) 2_500 else 3_000)
                )
            }
        }

        if (fakeIpArmed) {
            dnsServers.put(
                JSONObject()
                    .put("address", "fakedns")
                    .put("queryStrategy", queryStrategy)
            )
        }

        val selectedFragmented = byTag[firstTag]
            ?.optJSONObject("settings")
            ?.optJSONObject("fragment")
            ?.optString("packets")
            ?.trim()
            ?.let { it != "0" && it.toIntOrNull() != 0 } == true
        fun dnsTimeoutMs(index: Int): Long =
            LinkDeadlinePolicy.dnsServerTimeoutMs(
                evidence = link,
                index = index,
                fragmented = selectedFragmented
            )

        remoteDoh.firstOrNull()?.let { address ->
            dnsServers.put(
                JSONObject()
                    .put("address", address)
                    .put("queryStrategy", queryStrategy)
                    .put("timeoutMs", dnsTimeoutMs(0))
            )
        }

        val customSecondaryDoh = remoteDoh
            .drop(1)

        customSecondaryDoh.forEachIndexed { index, address ->
            dnsServers.put(
                JSONObject()
                    .put("address", address)
                    .put("queryStrategy", queryStrategy)
                    .put("timeoutMs", dnsTimeoutMs(index + 1))
                    .put("finalQuery", index == customSecondaryDoh.lastIndex)
            )
        }

        val dnsParallelQuery = settings.adaptiveDnsEnabled &&
            !selectedFragmented &&
            settings.measuredDnsParallel

        val dnsConfig = JSONObject()
            .put("servers", dnsServers)
            .put("queryStrategy", queryStrategy)
            .put("disableCache", false)
            .put("serveStale", true)
            .put("serveExpiredTTL", 1_800)
            .put("enableParallelQuery", dnsParallelQuery)
        dnsConfig
            .put("useSystemHosts", false)
            .put("disableFallbackIfMatch", bootstrapDomains.isNotEmpty())
            .put("tag", "xgc-dns")
        src.put("dns", dnsConfig)

        val rules = JSONArray()

        val dnsHijackActive = settings.dnsHijackEnabled
        if (dnsHijackActive) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("socks-in"))
                    .put("port", "53")
                    .put("outboundTag", "dns-out")
            )
            // MARBLE_PRIVATE_DNS_BLOCK_V238 — Android's Private DNS (DoT) keeps retrying
            // tcp:198.18.0.2:853 (the TUN's own address) while the hijack answers port 53. Every
            // retried DoT query rode `socks-in → direct` (report P1) and delayed the platform's
            // fallback to plain DNS. Blocking 853 at the inbound makes Android switch to the
            // hijacked path in seconds instead of minutes.
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("inboundTag", JSONArray().put("socks-in"))
                    .put("port", "853")
                    .put("outboundTag", "block")
            )
        }

        rules.put(
            JSONObject()
                .put("type", "field")
                .put("inboundTag", JSONArray().put("xgc-dns"))
                .put("outboundTag", firstTag)
        )

        if (dnsPlan.blockIpv6Traffic) addIpRule(rules, listOf("::/0"), "block")
        if (dnsPlan.blockIpv4Traffic) addIpRule(rules, listOf("0.0.0.0/0"), "block")

        if (fakeIpArmed) {
            addIpRule(rules, listOf(FakeIpPolicy.IPV4_POOL, FakeIpPolicy.IPV6_POOL), firstTag)
        }

        if (firstTag == "ssh-proxy") {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("network", "udp")
                    .put("outboundTag", "block")
            )
        }

        addDomainRule(rules, splitDomains(settings.routeBlockDomains), "block")
        addIpRule(rules, splitIps(settings.routeBlockIps), "block")
        addDomainRule(rules, splitDomains(settings.routeProxyDomains), firstTag)
        // Ads geosite tag: normalizeGeoSiteTag(settings.routeAdsTag) — now RoutingEngine + RoutingDefaults.ADS_TAG
        RoutingEngine.applyUserRules(rules, settings, firstTag)

        if (iranActive) {
            addIpRule(rules, IRAN_POISON_BLOCK_IPS, "block")
        }

        val rejectUdp443 = settings.muxUdp443 == "reject"
        if (rejectUdp443) {
            addQuicUdp443Rule(rules, "block")
        } else {
            val noiseTag = keep.firstOrNull { tag ->
                byTag[tag]?.let { hasNoises(it) && !hasFragment(it) } == true
            }
            if (noiseTag != null) {
                addQuicUdp443Rule(rules, noiseTag)
            }
        }

        if (settings.routeDirectDomains.isNotBlank()) {
            addDomainRule(rules, splitDomains(settings.routeDirectDomains), "direct")
        }
        if (settings.routeDirectIps.isNotBlank()) {
            addIpRule(rules, splitIps(settings.routeDirectIps), "direct")
        }

        // MARBLE_NTP_DIRECT_V238 — the report (P2-8) showed hev dropping NTP replies with
        // "symmetric NAT … drop packet from …:123". NTP is a fixed, authenticated-by-time
        // exchange with no user data; carrying it through the proxy's UDP relay adds exactly the
        // address rewriting that makes the reply source mismatch the mapping. Real-address NTP
        // now rides the direct outbound (the reply then comes from the socket the query used).
        // Domain-resolved NTP still matches the FakeDNS rule above and stays on the proxy, so a
        // fake address never reaches the direct dialer.
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("network", "udp")
                .put("port", 123)
                .put("outboundTag", "direct")
        )

        rules.put(
            JSONObject()
                .put("type", "field")
                .put("inboundTag", JSONArray().put("socks-in"))
                .put("outboundTag", firstTag)
        )

        val domainStrategy = settings.routeDomainStrategy.takeIf {
            it in setOf("AsIs", "IPIfNonMatch", "IPOnDemand")
        } ?: "IPIfNonMatch"

        val domainMatcher = settings.routeDomainMatcher.takeIf {
            it in setOf("hybrid", "linear", "mph")
        } ?: "hybrid"

        val routingObj = JSONObject()
            .put("domainStrategy", domainStrategy)
            .put("domainMatcher", domainMatcher)
            .put("rules", rules)

        src.put("routing", routingObj)

        src.put("log", CoreOptions.xrayLog(settings))

        listOf("api", "reverse", "metrics", "stats", "observatory", "burstObservatory", "fakedns")
            .forEach(src::remove)

        if (fakeIpArmed) {
            src.put("fakedns", JSONArray()
                .put(JSONObject().put("ipPool", FakeIpPolicy.IPV4_POOL)
                    .put("poolSize", FakeIpPolicy.XRAY_LRU_SIZE))
                .put(JSONObject().put("ipPool", FakeIpPolicy.IPV6_POOL)
                    .put("poolSize", FakeIpPolicy.XRAY_LRU_SIZE)))
        }

        CoreOptions.xrayPolicy(settings)?.let { policy -> src.put("policy", policy) }
        val extra = CoreOptions.merge(src, settings.xrayExtraJson)
        if (extra.error.isNotBlank()) {
            RuntimeDiagnostics.coreOption("xray", "extra-json-refused", extra.error)
        } else if (extra.changed) {
            RuntimeDiagnostics.coreOption("xray", "extra-json-merged", extra.applied.joinToString(","))
        }

        TlsPinningPolicy.sanitizeConfigDocument(src)
        verify(src, socksPort, firstTag, needsDirect, settings, underlayHasIpv6)
        return src.toString(2)
    }

    private fun hasIpv4LiteralEndpoint(outbound: JSONObject, settings: AppSettings): Boolean {
        fun walk(value: Any?): Boolean = when (value) {
            is JSONObject -> {
                val keys = value.keys()
                var found = false
                while (keys.hasNext() && !found) {
                    val key = keys.next()
                    val child = value.opt(key)
                    found = if (key.equals("address", true) || key.equals("endpoint", true)) {
                        if (child !is String) false else {
                            val raw = child.trim()
                            val host = when {
                                raw.startsWith("[") -> raw.substringAfter('[').substringBefore(']')
                                raw.count { it == ':' } == 1 &&
                                    raw.substringAfterLast(':').toIntOrNull() != null ->
                                    raw.substringBeforeLast(':')
                                else -> raw
                            }
                            AddressFamilyPolicy.excludedIpv4Endpoint(host, settings)
                        }
                    } else walk(child)
                }
                found
            }
            is JSONArray -> (0 until value.length()).any { walk(value.opt(it)) }
            else -> false
        }
        return walk(outbound.optJSONObject("settings"))
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

    /**
     * MARBLE_FREEDOM_SOCKOPT_STRATEGY_V163 — the single supported home of a freedom/direct hop's
     * resolve strategy. `sockopt.domainStrategy` is what the pinned Xray core actually reads for
     * both TCP dials and UDP packets; `settings.domainStrategy` and `settings.targetStrategy` are
     * a deprecated alias that the core migrates with a start-up warning today and rejects
     * tomorrow. A hand-imported config that still carries the alias is cleaned here too, so the
     * emitted document never triggers the migration path. When the plan is `AsIs` nothing is
     * written and any imported alias is dropped, exactly as the core would migrate it.
     */
    internal fun writeFreedomResolveStrategy(outbound: JSONObject, strategy: String) {
        outbound.optJSONObject("settings")?.let { settingsObject ->
            settingsObject.remove("domainStrategy")
            settingsObject.remove("targetStrategy")
        }
        outbound.remove("targetStrategy")
        val streamObject = outbound.optJSONObject("streamSettings")
            ?: JSONObject().also { outbound.put("streamSettings", it) }
        val sockoptObject = streamObject.optJSONObject("sockopt")
            ?: JSONObject().also { streamObject.put("sockopt", it) }
        if (strategy.isBlank() || strategy.equals("AsIs", true)) {
            sockoptObject.remove("domainStrategy")
        } else {
            sockoptObject.put("domainStrategy", strategy)
        }
    }

    private val IRAN_POISON_BLOCK_IPS = listOf(
        "10.10.34.0/24",
        "2001:4188:2:600::/64",
        "0.0.0.0",
        "::"
    )

    private fun hasNoises(outbound: JSONObject): Boolean {
        val noises = outbound.optJSONObject("settings")?.optJSONArray("noises") ?: return false
        return noises.length() > 0
    }

    private fun applyAddressFamily(
        outbound: JSONObject,
        settings: AppSettings,
        underlayHasIpv6: Boolean,
        chainedFromImport: Boolean? = null
    ): IpFamilyPlan {
        val protocol = outbound.optString("protocol").lowercase()
        val stream = outbound.optJSONObject("streamSettings")
        val sockopt = stream?.optJSONObject("sockopt")

        val chained = chainedFromImport ?: (
            outbound.optJSONObject("proxySettings")
                ?.optString("tag")
                ?.isNotBlank() == true ||
                sockopt?.optString("dialerProxy").orEmpty().isNotBlank()
            )
        val method = stream?.optString("method").orEmpty().lowercase()

        val tcpTransport = protocol != "wireguard" && method !in setOf("hysteria", "mkcp")
        val plan = AddressFamilyPolicy.plan(
            settings = settings,
            underlayHasIpv6 = underlayHasIpv6,
            tcpTransport = tcpTransport,
            chained = chained,
            dialerProxy = sockopt?.optString("dialerProxy").orEmpty(),
            importedStrategy = sockopt?.optString("domainStrategy")
                ?: outbound.optJSONObject("settings")?.optString("domainStrategy").orEmpty()
        )

        if (protocol == "wireguard") {
            val settingsObject = outbound.optJSONObject("settings")
                ?: JSONObject().also { outbound.put("settings", it) }
            settingsObject.put("domainStrategy", plan.endpointStrategy)
            return plan
        }

        val streamObject = stream ?: JSONObject().also { outbound.put("streamSettings", it) }
        val sockoptObject = streamObject.optJSONObject("sockopt")
            ?: JSONObject().also { streamObject.put("sockopt", it) }

        sockoptObject.put("domainStrategy", plan.endpointStrategy)

        if (plan.raceEnabled) {
            sockoptObject.put(
                "happyEyeballs",
                JSONObject()
                    .put("tryDelayMs", plan.tryDelayMs)
                    .put("prioritizeIPv6", plan.prioritizeIpv6)
                    .put("interleave", 1)
                    .put("maxConcurrentTry", plan.maxConcurrentTry)
            )
        } else {
            sockoptObject.remove("happyEyeballs")
        }
        return plan
    }

    private fun dnsHostLiteral(value: String): String =
        if (value.contains(':') && !value.startsWith("[")) "[$value]" else value

    internal val RESERVED_OUTBOUND_TAGS: Set<String> = setOf(
        "block", "direct", "dns-out"
    )

    internal fun importedAliasFor(tag: String): String = "import-$tag"

    internal fun renameReservedImportedTags(outbounds: JSONArray): Map<String, String> {
        val present = linkedSetOf<String>()
        for (i in 0 until outbounds.length()) {
            outbounds.optJSONObject(i)?.optString("tag")?.takeIf { it.isNotBlank() }?.let(present::add)
        }
        val renames = linkedMapOf<String, String>()
        present.filter { it in RESERVED_OUTBOUND_TAGS }.forEach { tag ->
            var candidate = importedAliasFor(tag)
            var suffix = 2
            while (candidate in present || candidate in renames.values) {
                candidate = importedAliasFor(tag) + "-" + suffix++
            }
            renames[tag] = candidate
        }
        if (renames.isEmpty()) return renames
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue
            renames[outbound.optString("tag")]?.let { outbound.put("tag", it) }
            outbound.optJSONObject("streamSettings")?.optJSONObject("sockopt")?.let { sockopt ->
                renames[sockopt.optString("dialerProxy")]?.let { sockopt.put("dialerProxy", it) }
            }
            outbound.optJSONObject("proxySettings")?.let { proxy ->
                renames[proxy.optString("tag")]?.let { proxy.put("tag", it) }
            }
        }
        return renames
    }

    private fun isSelectableProxy(outbound: JSONObject): Boolean {
        val protocol = outbound.optString("protocol").lowercase()
        if (protocol !in infra) return true
        return protocol in setOf("freedom", "direct") && hasFragment(outbound)
    }

    private fun hasFragment(outbound: JSONObject): Boolean {
        val fragment = outbound.optJSONObject("settings")?.optJSONObject("fragment") ?: return false
        return fragment.optString("packets").isNotBlank()
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

    private fun addQuicUdp443Rule(rules: JSONArray, outboundTag: String) {
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("inboundTag", JSONArray().put("socks-in"))
                .put("network", "udp")
                .put("protocol", JSONArray().put("quic"))
                .put("outboundTag", outboundTag)
        )
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("inboundTag", JSONArray().put("socks-in"))
                .put("network", "udp")
                .put("port", "443")
                .put("outboundTag", outboundTag)
        )
    }

    private fun normalizeGeoSiteTag(raw: String): String? {
        val token = raw.trim()
        if (token.isBlank()) return null
        val body = if (token.startsWith("geosite:", ignoreCase = true)) {
            token.substringAfter(':').trim()
        } else {
            token
        }
        return body.takeIf { it.isNotBlank() }?.let { "geosite:$it" }
    }

    private fun normalizeGeoIpTag(raw: String): String? {
        val token = raw.trim()
        if (token.isBlank()) return null
        val body = if (token.startsWith("geoip:", ignoreCase = true)) {
            token.substringAfter(':').trim()
        } else {
            token
        }
        return body.takeIf { it.isNotBlank() }?.let { "geoip:$it" }
    }

    private fun splitTokens(raw: String): List<String> = raw
        .split(',', '\n', '\r', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    private fun splitDomains(raw: String): List<String> = splitTokens(raw).mapNotNull { value ->
        when {
            value.startsWith("geosite:", true) -> normalizeGeoSiteTag(value)
            value.startsWith("domain:", true) -> "domain:${value.substringAfter(':').trim()}"
            value.startsWith("full:", true) -> "full:${value.substringAfter(':').trim()}"
            value.startsWith("keyword:", true) -> "keyword:${value.substringAfter(':').trim()}"
            value.startsWith("regexp:", true) -> "regexp:${value.substringAfter(':').trim()}"
            else -> "domain:$value"
        }
    }.distinct()

    private fun splitIps(raw: String): List<String> = splitTokens(raw).flatMap { token ->
        when {
            token.equals("geoip:private", true) -> PRIVATE_CIDRS
            token.startsWith("geoip:", true) -> normalizeGeoIpTag(token)?.let { listOf(it) } ?: emptyList()
            else -> listOf(token)
        }
    }.distinct()

    private fun verify(
    o: JSONObject,
    port: Int,
    selectedTag: String,
    needsDirect: Boolean,
    settings: AppSettings,
    underlayHasIpv6: Boolean = AddressFamilyPolicy.underlayHasIpv6()
) {
    val ins = o.getJSONArray("inbounds")
    require(
        ins.length() == 1 &&
            ins.getJSONObject(0).getInt("port") == port &&
            ins.getJSONObject(0).getString("listen") == "127.0.0.1"
    ) { "SOCKS hardening failed" }

    val outs = o.getJSONArray("outbounds")
    var selected = false
    var direct = false
    var block = false
    for (i in 0 until outs.length()) {
        val outbound = outs.getJSONObject(i)
        if (outbound.optString("tag") == selectedTag) selected = true
        if (outbound.optString("tag") == "direct" && outbound.optString("protocol") == "freedom") direct = true
        if (outbound.optString("tag") == "block" && outbound.optString("protocol") == "blackhole") block = true
    }
    require(selected) { "Selected proxy outbound missing" }
    require(block) { "Routing blackhole outbound missing" }
    require(direct == needsDirect) { "Routing direct-outbound invariant failed" }

    val dns = o.getJSONObject("dns")
    require(!dns.toString().contains("\"localhost\"")) { "System DNS must not be used" }

    val routing = o.getJSONObject("routing")
    val rules = routing.getJSONArray("rules")
    require(rules.length() > 0) { "Routing rule set is empty" }

    val fallback = rules.getJSONObject(rules.length() - 1)
    require(fallback.optString("outboundTag") == selectedTag) {
        "Unmatched routing fallback must stay on the selected proxy"
    }

    fun hasDomain(value: String, outbound: String): Boolean {
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            if (rule.optString("outboundTag") != outbound) continue
            val domains = rule.optJSONArray("domain") ?: continue
            for (j in 0 until domains.length()) {
                if (domains.optString(j).equals(value, ignoreCase = true)) return true
            }
        }
        return false
    }

    fun hasIp(value: String, outbound: String): Boolean {
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            if (rule.optString("outboundTag") != outbound) continue
            val ips = rule.optJSONArray("ip") ?: continue
            for (j in 0 until ips.length()) {
                if (ips.optString(j).equals(value, ignoreCase = true)) return true
            }
        }
        return false
    }

    RoutingEngine.emittableUserRules(settings).forEach { rule ->
        val tag = RoutingEngine.outboundTag(rule.outbound, selectedTag)
        when (rule.kind) {
            com.marbleng.app.model.RoutingRuleKind.GEOSITE ->
                RoutingEngine.normalizeGeoSite(rule.matcher)?.let { token ->
                    require(hasDomain(token, tag)) { "Missing geosite route: $token" }
                }
            com.marbleng.app.model.RoutingRuleKind.GEOIP ->
                if (!rule.matcher.equals("private", true)) {
                    RoutingEngine.normalizeGeoIp(rule.matcher)?.let { token ->
                        require(hasIp(token, tag)) { "Missing geoip route: $token" }
                    }
                }
            else -> Unit
        }
    }

    if (true) {
        val familyPlan = AddressFamilyPolicy.plan(
            settings = settings,
            underlayHasIpv6 = underlayHasIpv6
        )
        require(dns.optString("queryStrategy") == familyPlan.dnsQueryStrategy) {
            "DNS record strategy disagrees with the IPv6 setting"
        }
        dns.optJSONArray("servers")?.let { servers ->
            for (index in 0 until servers.length()) {
                val server = servers.optJSONObject(index) ?: continue
                require(server.optString("queryStrategy") == familyPlan.dnsQueryStrategy) {
                    "A DNS server entry disagrees with the IPv6 setting"
                }
            }
        }
        if (familyPlan.blockIpv4Traffic) {
            require(hasIp("0.0.0.0/0", "block")) { "Force IPv6 must block captured IPv4 traffic" }
        }
        if (familyPlan.blockIpv6Traffic) {
            require(hasIp("::/0", "block")) { "IPv6 is disabled but ::/0 is not blocked" }

            for (index in 0 until outs.length()) {
                val outbound = outs.optJSONObject(index) ?: continue
                if (endpointDomains(outbound).isEmpty()) continue
                val strategy = outbound.optJSONObject("streamSettings")?.optJSONObject("sockopt")
                    ?.optString("domainStrategy").orEmpty()
                    .ifBlank { outbound.optJSONObject("settings")?.optString("domainStrategy").orEmpty() }
                require(strategy.isBlank() || strategy == "ForceIPv4" || strategy == "UseIPv4") {
                    "IPv6 is disabled but $strategy was written for ${outbound.optString("tag")}"
                }
            }
        }
        for (index in 0 until outs.length()) {
            val outbound = outs.optJSONObject(index) ?: continue
            // MARBLE_FREEDOM_SOCKOPT_STRATEGY_V163 — a freedom hop that the hardener planned must

            if (outbound.optString("protocol").lowercase() in setOf("freedom", "direct")) {
                val legacy = outbound.optJSONObject("settings")
                val hasAlias = legacy?.has("domainStrategy") == true ||
                    legacy?.has("targetStrategy") == true ||
                    outbound.has("targetStrategy")
                val planned = outbound.optJSONObject("streamSettings")?.optJSONObject("sockopt")
                    ?.has("domainStrategy") == true
                require(!(hasAlias && planned)) {
                    "Deprecated freedom.domainStrategy alias written next to sockopt.domainStrategy for ${outbound.optString("tag")}"
                }
            }

            if (endpointDomains(outbound).isEmpty()) continue
            val sockopt = outbound.optJSONObject("streamSettings")?.optJSONObject("sockopt") ?: continue
            val strategy = sockopt.optString("domainStrategy")
            if (strategy.isBlank()) continue
            require(strategy in AddressFamilyPolicy.ENDPOINT_STRATEGIES) {
                "Unsupported endpoint domainStrategy: $strategy"
            }

            if (strategy == "ForceIP") {
                val race = sockopt.optJSONObject("happyEyeballs")
                require(
                    race != null &&
                        race.optInt("tryDelayMs") >= AddressFamilyPolicy.MIN_TRY_DELAY_MS &&
                        race.optInt("maxConcurrentTry") >= AddressFamilyPolicy.MIN_CONCURRENT_TRY
                ) { "Dual-stack endpoint left without an armed Happy Eyeballs race" }
            } else {
                require(!sockopt.has("happyEyeballs")) {
                    "Happy Eyeballs written for a single-family endpoint strategy"
                }
            }
        }
    }
}
}
