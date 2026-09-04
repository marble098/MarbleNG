package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.RoutingMode
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigHardener {
    // MARBLE_ENCRYPTED_DNS_ONLY_V38
    // MARBLE_CLEAN_XRAY_LOG_V13
    // MARBLE_DNS_RESILIENCE_V16
    // MARBLE_DNS_DEJITTER_V18
    // MARBLE_DNS_TRANSPORT_FALLBACK_V23
    // MARBLE_IP_FAMILY_POLICY_V24
    // MARBLE_DNS_FAST_FALLBACK_V25
    // MARBLE_DNS_EOF_QUARANTINE_V26
    // MARBLE_RUNTIME_POLISH_V29
    // MARBLE_EXTREME_NETWORK_V30
    // MARBLE_DNS_PROVIDER_FAILOVER_V35
    // MARBLE_V2RAYNG_SMART_RANK_V45
    // MARBLE_DNS_RACE_V47
    // MARBLE_DNS_SERIAL_FAILOVER_V49
    // MARBLE_DNS_BURST_TOLERANCE_V59
    // MARBLE_PATTNG_NATIVE_RANK_V62
    // MARBLE_REALTIME_ENGINE_V70
    // MARBLE_UNIFIED_ADDRESS_FAMILY_V65
    private val infra = setOf("freedom", "blackhole", "dns", "loopback")
    private val compatibilityDependencyProtocols = setOf(
        "freedom", "http", "shadowsocks", "socks", "trojan", "vless", "vmess", "hysteria", "wireguard"
    )
    private val muxProtocols = setOf("shadowsocks", "trojan", "vless", "vmess")

    /**
     * MARBLE_ENDPOINT_BOOTSTRAP_V132 — independent literal resolvers for the node hostname.
     *
     * The endpoint hostname is resolved by the `https+local://` servers below and by NOTHING
     * else: Xray builds "list 1" from every server whose `domains` field matches the query and
     * `disableFallbackIfMatch` then suppresses "list 2" completely, so the proxy-routed DoH
     * chain is invisible to the endpoint lookup (it has to be — that chain needs the endpoint
     * IP before it can carry anything).
     *
     * Two literals are therefore a single point of failure, and the two defaults (1.1.1.1 and
     * 8.8.8.8) are the two most-filtered public resolvers on censored networks. When both were
     * unreachable Xray never dialled the node at all, while the very same server connected and
     * pinged in another client that lets the OS or the remote resolve the name. The user's own
     * resolvers keep first place; these independent literals follow them.
     */
    private val STOCK_BOOTSTRAP_DNS_IPS = listOf(
        "1.1.1.1", "8.8.8.8", "9.9.9.9", "1.0.0.1", "8.8.4.4", "149.112.112.112"
    )
    private val STOCK_BOOTSTRAP_DNS_IPV6 = listOf(
        "2606:4700:4700::1111", "2001:4860:4860::8888", "2620:fe::fe"
    )
    private const val MAX_BOOTSTRAP_DNS_IPS = 6

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
     * Compose an unbounded persisted Manual chain: client -> hop 1 -> ... -> exit -> Internet.
     *
     * Every source is namespaced before merging, including helper outbounds and internal
     * proxySettings/dialerProxy references. The tail of each later hop is attached to the primary
     * outbound of the preceding hop, so already-composite/custom configs remain intact. There is no
     * arbitrary hop-count cap; practical device memory and Xray validation are the natural bounds.
     */
    fun composeChain(sources: List<String>): String {
        require(sources.size >= 2) { "A chain needs at least two hops" }

        data class Segment(
            val primaryTag: String,
            val byTag: LinkedHashMap<String, JSONObject>
        )

        val segments = sources.mapIndexed { segmentIndex, source ->
            val root = JSONObject(source)
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
                outbound.optJSONObject("proxySettings")?.let { proxy ->
                    originalTags[proxy.optString("tag")]?.let { proxy.put("tag", it) }
                }
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
                val next = outbound.optJSONObject("proxySettings")?.optString("tag").orEmpty()
                if (next.isBlank() || next !in segment.byTag) return outbound
                tag = next
            }
            error("Chain hop contains a proxySettings cycle")
        }

        for (index in 1 until segments.size) {
            tail(segments[index]).put(
                "proxySettings",
                JSONObject()
                    .put("tag", segments[index - 1].primaryTag)
                    .put("transportLayer", true)
            )
        }

        val exit = segments.last()
        exit.byTag.getValue(exit.primaryTag).put("tag", "proxy")
        val remappedExitTag = exit.primaryTag
        segments.forEach { segment ->
            segment.byTag.values.forEach { outbound ->
                outbound.optJSONObject("proxySettings")?.let { proxy ->
                    if (proxy.optString("tag") == remappedExitTag) proxy.put("tag", "proxy")
                }
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

    /**
     * Dedicated real-delay config, modelled after v2rayNG 2.3.5 postProcessForSpeedtest().
     * A CLI child needs one local SOCKS inbound (gomobile core.Dial does not), but everything not
     * required to establish the selected outbound is removed so repeated Rank runs are isolated.
     */
    fun hardenForDelayTest(
        source: String,
        socksPort: Int,
        settings: AppSettings = AppSettings(),
        underlayHasIpv6: Boolean = AddressFamilyPolicy.underlayHasIpv6()
    ): String {
        require(socksPort in 1..65535) { "Invalid delay-test SOCKS port" }
        val root = JSONObject(source)
        val imported = root.optJSONArray("outbounds") ?: error("Xray JSON has no outbounds")
        val byTag = linkedMapOf<String, JSONObject>()
        var selectedTag = ""

        for (index in 0 until imported.length()) {
            val outbound = imported.optJSONObject(index)?.let { JSONObject(it.toString()) } ?: continue
            val protocol = outbound.optString("protocol").lowercase()
            val tag = outbound.optString("tag").ifBlank {
                if (protocol !in infra && selectedTag.isBlank()) "proxy" else "delay-out-$index"
            }
            outbound.put("tag", tag)
            byTag[tag] = outbound
            if (protocol !in infra && selectedTag.isBlank()) selectedTag = tag
        }
        // Match the serverless Freedom path: a config may legitimately contain only freedom/direct
        // outbounds (NORMAL tier). Treat the first one as the exit when no non-infra proxy exists.
        if (selectedTag.isBlank()) {
            selectedTag = byTag.entries.firstOrNull { (_, outbound) ->
                outbound.optString("protocol").lowercase() in setOf("freedom", "direct")
            }?.key.orEmpty()
        }
        require(selectedTag.isNotBlank()) { "No proxy outbound for delay test" }

        val required = linkedSetOf<String>()
        fun keep(tag: String) {
            val outbound = byTag[tag] ?: return
            if (!required.add(tag)) return
            outbound.optJSONObject("proxySettings")?.optString("tag")
                ?.takeIf { it.isNotBlank() }?.let(::keep)
            outbound.optJSONObject("streamSettings")?.optJSONObject("sockopt")
                ?.optString("dialerProxy")?.takeIf { it.isNotBlank() }?.let(::keep)
        }
        keep(selectedTag)

        val outbounds = JSONArray()
        required.forEach { tag ->
            byTag[tag]?.let { outbound ->
                outbound.remove("mux")
                val protocol = outbound.optString("protocol").lowercase()
                val dialerProxy = outbound.optJSONObject("streamSettings")
                    ?.optJSONObject("sockopt")
                    ?.optString("dialerProxy")
                    .orEmpty()
                val chained = outbound.optJSONObject("proxySettings")
                    ?.optString("tag")
                    ?.isNotBlank() == true || dialerProxy.isNotBlank()
                // Same family plan as the tunnel, so a measured delay describes the path the user
                // will actually get instead of the one the OS resolver happened to prefer. A
                // serverless freedom/direct hop has no proxy endpoint to resolve, so it needs the
                // outbound-level plan when it is the hop that opens the real socket.
                if (protocol in setOf("freedom", "direct") && !chained) {
                    val plan = AddressFamilyPolicy.plan(
                        settings = settings,
                        underlayHasIpv6 = underlayHasIpv6,
                        tcpTransport = true
                    )
                    val settingsObject = outbound.optJSONObject("settings")
                        ?: JSONObject().also { outbound.put("settings", it) }
                    settingsObject.put("domainStrategy", plan.endpointStrategy)
                    settingsObject.put("targetStrategy", plan.endpointStrategy)
                } else if (endpointDomains(outbound).isNotEmpty()) {
                    applyAddressFamily(outbound, settings, underlayHasIpv6)
                }
                outbounds.put(outbound)
            }
        }

        val inbound = JSONObject()
            .put("tag", "delay-socks")
            .put("listen", "127.0.0.1")
            .put("port", socksPort)
            .put("protocol", "socks")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", true))
            .put("sniffing", JSONObject().put("enabled", false))

        root.put("inbounds", JSONArray().put(inbound))
        root.put("outbounds", outbounds)
        listOf(
            "dns", "fakedns", "routing", "stats", "policy", "api", "reverse", "metrics",
            "observatory", "burstObservatory"
        ).forEach { root.remove(it) }
        root.put("log", JSONObject().put("loglevel", "warning"))
        return root.toString()
    }

    /**
     * Build Rank from the SAME production-compatible outbound graph as a real Marble connection,
     * then remove only runtime sections that are irrelevant to an outbound delay test.
     *
     * This follows PattNG's getV2rayConfig4Speedtest() architecture: unified config first,
     * post-process second.
     */
    fun hardenForNativeRank(
        source: String,
        settings: AppSettings = AppSettings(),
        underlayHasIpv6: Boolean = AddressFamilyPolicy.underlayHasIpv6()
    ): String {
        // The local port is discarded with the inbounds below; it exists only so production
        // hardening executes through exactly the same code path.
        val root = JSONObject(harden(source, 19091, settings))
        root.put("inbounds", JSONArray())
        // Xray installs its *system* resolver whenever a config has no dns app, so removing the
        // section did not just simplify the rank instance — it made ranking resolve node hostnames in
        // plaintext over the underlay and pick a family by luck, while the real tunnel used encrypted
        // DoH with an explicit order. The rank config therefore keeps a dns app, rewritten to the
        // underlay-local DoH form so ranking does not depend on the tunnel it is measuring.
        listOf(
            "fakedns", "routing", "stats", "policy", "api", "reverse", "metrics",
            "observatory", "burstObservatory"
        ).forEach(root::remove)

        val rankResolvers = listOf(settings.dnsPrimaryIp, settings.dnsSecondaryIp)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val rankPlan = AddressFamilyPolicy.plan(settings = settings)
        if (rankResolvers.isEmpty()) {
            root.remove("dns")
        } else {
            root.put(
                "dns",
                JSONObject()
                    .put(
                        "servers",
                        JSONArray(
                            rankResolvers.map { resolver ->
                                JSONObject().put(
                                    "address",
                                    "https+local://${dnsHostLiteral(resolver)}/dns-query"
                                )
                            }
                        )
                    )
                    .put("queryStrategy", rankPlan.dnsQueryStrategy)
                    .put("disableCache", false)
                    .put("useSystemHosts", false)
                    .put("tag", "xgc-dns")
            )
        }

        root.optJSONArray("outbounds")?.let { outbounds ->
            for (index in 0 until outbounds.length()) {
                outbounds.optJSONObject(index)?.remove("mux")
            }
        }
        root.put("log", JSONObject().put("loglevel", "none"))
        return root.toString()
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
                if (isSelectableProxy(clone) && firstTag.isBlank()) "proxy" else "out-$i"
            }
            clone.put("tag", tag)
            byTag[tag] = clone
            if (isSelectableProxy(clone) && firstTag.isBlank()) firstTag = tag
        }
        // Serverless Freedom can intentionally emit a plain freedom/direct outbound (NORMAL tier
        // or a hand-imported Freedom-only JSON). Xray accepts that as a valid exit; the generic
        // "freedom is infrastructure" rule must only apply when a real proxy outbound also exists.
        // Otherwise the whole config is rejected as "No proxy outbound" and Marble Freedom fails.
        if (firstTag.isBlank()) {
            firstTag = byTag.values.firstOrNull { outbound ->
                val protocol = outbound.optString("protocol").lowercase()
                protocol in setOf("freedom", "direct")
            }?.optString("tag").orEmpty()
        }
        require(firstTag.isNotBlank()) { "No proxy outbound" }

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
        // Freedom ships a dedicated udp-noises outbound that is not on the dialer chain.
        // Keep it whenever present so the routing rules below can send QUIC/UDP-443 there.
        byTag.keys.filter { tag ->
            tag == ServerlessFreedomEngine.UDP_NOISES_TAG ||
                (byTag[tag]?.let { hasNoises(it) && !hasFragment(it) } == true)
        }.forEach { add(it) }

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

                // Overlay live fragment recipes onto Freedom hops that already shred TLS (serverless).
        // Named Freedom presets bake the correct hop values in ServerlessFreedomEngine.configJson
        // (including SMART_ADAPTIVE escalation from Iran Mode). Re-overlaying from settings would
        // undo that recipe with stale freedomOuter* fields, so only CUSTOM (or non-Freedom
        // fragment hops) is rewritten here.
        val isFreedomSelected = byTag[firstTag]?.optString("protocol")?.lowercase() == "freedom"
        val freedomCustom = isFreedomSelected &&
            settings.freedomPreset == com.marbleng.app.model.FreedomPreset.CUSTOM
        keep.forEach { tag ->
            val outbound = byTag[tag] ?: return@forEach
            if (!hasFragment(outbound)) return@forEach
            // Trust the emitted Freedom chain for every non-CUSTOM preset.
            if (isFreedomSelected && !freedomCustom) return@forEach
            if (tag == "middle-fragment" || tag == "freedom-middle") {
                val middlePackets = if (freedomCustom) settings.freedomMiddlePackets.ifBlank { "1-3" }
                    else settings.fragmentInnerPackets
                val middleLength = if (freedomCustom) settings.freedomMiddleLength.ifBlank { "10-30" }
                    else settings.fragmentInnerLength
                val middleInterval = if (freedomCustom) settings.freedomMiddleInterval.ifBlank { "5-10" }
                    else settings.fragmentInnerInterval
                val middleMaxSplit = if (freedomCustom) settings.freedomMiddleMaxSplit.ifBlank { "768" }
                    else settings.fragmentInnerMaxSplit
                overlayFragment(
                    outbound,
                    packets = middlePackets,
                    length = middleLength,
                    interval = middleInterval,
                    maxSplit = middleMaxSplit
                )
            } else {
                val inner = tag != firstTag && (settings.fragmentInnerEnabled || isFreedomSelected)
                val packets = when {
                    freedomCustom && inner -> settings.freedomInnerPackets.ifBlank { settings.fragmentInnerPackets }
                    freedomCustom -> settings.freedomOuterPackets.ifBlank { settings.fragmentPackets }
                    inner -> settings.fragmentInnerPackets
                    else -> settings.fragmentPackets
                }
                val length = when {
                    freedomCustom && inner -> settings.freedomInnerLength.ifBlank { settings.fragmentInnerLength }
                    freedomCustom -> settings.freedomOuterLength.ifBlank { settings.fragmentLength }
                    inner -> settings.fragmentInnerLength
                    else -> settings.fragmentLength
                }
                val interval = when {
                    freedomCustom && inner -> settings.freedomInnerInterval.ifBlank { settings.fragmentInnerInterval }
                    freedomCustom -> settings.freedomOuterInterval.ifBlank { settings.fragmentInterval }
                    inner -> settings.fragmentInnerInterval
                    else -> settings.fragmentInterval
                }
                val maxSplit = when {
                    freedomCustom && inner -> settings.freedomInnerMaxSplit.ifBlank { settings.fragmentInnerMaxSplit }
                    freedomCustom -> settings.freedomOuterMaxSplit.ifBlank { settings.fragmentMaxSplit }
                    inner -> settings.fragmentInnerMaxSplit
                    else -> settings.fragmentMaxSplit
                }
                overlayFragment(
                    outbound,
                    packets = packets,
                    length = length,
                    interval = interval,
                    maxSplit = maxSplit
                )
            }
        }

        val selectedAlreadyFragments = byTag[firstTag]?.let { selected ->
            hasFragment(selected) ||
                selected.optJSONObject("streamSettings")
                    ?.optJSONObject("sockopt")
                    ?.optString("dialerProxy")
                    ?.let { hop -> byTag[hop]?.let(::hasFragment) } == true
        } == true

        // Fragment is attached as a Freedom dialer only to a physical proxy hop.
        // For a two-hop chain, the exit already has proxySettings -> entry, so Fragment lands
        // on the entry hop and never destroys the exit transport layer.
        val fragmentOutbound = if (settings.fragmentEnabled && !selectedAlreadyFragments) {
            val innerPackets = if (settings.fragmentInnerEnabled) {
                settings.fragmentInnerPackets.ifBlank { "1-1" }
            } else {
                settings.fragmentPackets.ifBlank { "tlshello" }
            }
            val innerLength = if (settings.fragmentInnerEnabled) {
                settings.fragmentInnerLength.ifBlank { "1" }
            } else {
                settings.fragmentLength.ifBlank { "100-200" }
            }
            val innerInterval = if (settings.fragmentInnerEnabled) {
                settings.fragmentInnerInterval.ifBlank { "4" }
            } else {
                settings.fragmentInterval.ifBlank { "10-20" }
            }
            val innerMaxSplit = if (settings.fragmentInnerEnabled) {
                settings.fragmentInnerMaxSplit.ifBlank { "517" }
            } else {
                settings.fragmentMaxSplit
            }
            JSONObject()
                .put("tag", "fragment-direct")
                .put("protocol", "freedom")
                .put(
                    "settings",
                    JSONObject().put(
                        "fragment",
                        fragmentSettings(innerPackets, innerLength, innerInterval, innerMaxSplit)
                    )
                )
        } else {
            null
        }

        val tlsFragmentOutbound = if (
            fragmentOutbound != null &&
            settings.fragmentInnerEnabled
        ) {
            JSONObject()
                .put("tag", "tls-fragment")
                .put("protocol", "freedom")
                .put(
                    "settings",
                    JSONObject().put(
                        "fragment",
                        fragmentSettings(
                            settings.fragmentPackets.ifBlank { "tlshello" },
                            settings.fragmentLength.ifBlank { "6" },
                            settings.fragmentInterval.ifBlank { "0" },
                            settings.fragmentMaxSplit
                        )
                    )
                )
                .put(
                    "streamSettings",
                    JSONObject().put(
                        "sockopt",
                        JSONObject().put("dialerProxy", "fragment-direct")
                    )
                )
        } else {
            null
        }

        if (fragmentOutbound != null) {
            val attachTag = if (tlsFragmentOutbound != null) "tls-fragment" else "fragment-direct"
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
                    sockopt.put("dialerProxy", attachTag)
                }
            }
        }

        val needsDirect = settings.routingMode != RoutingMode.PROXY_ALL ||
            (isFreedomSelected && settings.freedomDirectDomestic) ||
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

        // One underlay probe per config build. Both the endpoint strategies and the DNS record
        // selection are derived from it, so "IPv6 enabled" can never mean different things in two
        // parts of the same config.
        val underlayHasIpv6 = AddressFamilyPolicy.underlayHasIpv6()
        // The config-level plan: DNS record selection and the IPv6 block rule are decided once here
        // so they can never disagree with the per-outbound endpoint plans below.
        val dnsPlan = AddressFamilyPolicy.plan(
            settings = settings,
            underlayHasIpv6 = underlayHasIpv6
        )

        val out = JSONArray()
        keep.forEach { tag ->
            byTag[tag]?.let { outbound ->
                // targetStrategy defaults to AsIs. Remove imported overrides so endpoint
                // resolution can be controlled by sockopt and Happy Eyeballs.
                outbound.remove("targetStrategy")
                outbound.remove("sendThrough")

                if (endpointDomains(outbound).isNotEmpty()) {
                    applyAddressFamily(outbound, settings, underlayHasIpv6)

                    // Liveness tuning belongs to the long-lived tunnel only: a throwaway delay test
                    // never keeps a socket open long enough for keep-alives to matter.
                    val streamObject = outbound.optJSONObject("streamSettings")
                    val sockoptObject = streamObject?.optJSONObject("sockopt")
                    if (
                        sockoptObject != null &&
                        outbound.optString("protocol").lowercase() != "wireguard"
                    ) {
                        val method = streamObject.optString("method").lowercase()
                        val tcpTransport = method !in setOf("hysteria", "mkcp")
                        val chained = outbound.optJSONObject("proxySettings")
                            ?.optString("tag")
                            ?.isNotBlank() == true
                        if (tcpTransport) {
                            val liveness = SocketLivenessPolicy.forTransport(method, chained)
                            sockoptObject.put("tcpKeepAliveIdle", liveness.keepAliveIdleSeconds)
                            sockoptObject.put("tcpKeepAliveInterval", liveness.keepAliveIntervalSeconds)
                            sockoptObject.put("tcpUserTimeout", liveness.userTimeoutMs)
                            if (settings.tcpFastOpenEnabled) sockoptObject.put("tcpFastOpen", true) else sockoptObject.remove("tcpFastOpen")
                            if (settings.tcpMaxSeg in 536..9000) sockoptObject.put("tcpMaxSeg", settings.tcpMaxSeg) else sockoptObject.remove("tcpMaxSeg")
                        } else {
                            sockoptObject.remove("tcpKeepAliveIdle"); sockoptObject.remove("tcpKeepAliveInterval"); sockoptObject.remove("tcpUserTimeout")
                            sockoptObject.remove("tcpFastOpen"); sockoptObject.remove("tcpMaxSeg")
                        }
                    }
                }

                out.put(outbound)
            }
        }

        /*
         * Freedom/direct fragment hops that dial the Internet directly must resolve the USER's
         * destination hostname, not a proxy endpoint. The official XTLS Xray-examples
         * serverless_for_Iran.jsonc and the GFW-knocker serverless config both give that hop an
         * explicit domain strategy (ForceIP + Happy Eyeballs / UseIP) and resolve it through
         * Xray's encrypted DNS module.
         * Without it Xray hands the domain to the OS resolver, which on Iranian networks is
         * poisoned (10.10.34.0/24 answers) or silently drops — so every domain-ATYP connection
         * through Marble Freedom fails, including the app's own SOCKS probes while connected.
         * Only the innermost hop gets the plan: it is the one that opens the real socket, and the
         * resolution happens at its Dial()/PacketWriter before any dialerProxy redirect.
         * The dedicated udp-noises outbound also dials real sockets for QUIC/UDP-443 and needs
         * the same resolution plan.
         */
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
            val chained = outbound.optJSONObject("proxySettings")
                ?.optString("tag")
                ?.isNotBlank() == true ||
                outbound.optJSONObject("streamSettings")
                    ?.optJSONObject("sockopt")
                    ?.optString("dialerProxy")
                    ?.isNotBlank() == true
            if (chained) return@forEach
            // UDP dials use the outbound-level targetStrategy/domainStrategy; TCP uses
            // sockopt.domainStrategy + Happy Eyeballs. The official XTLS config writes
            // targetStrategy: ForceIPv6v4 on udp-noises, so the two paths must be planned
            // differently instead of pretending a UDP PacketWriter can race.
            val hopPlan = if (isNoiseOnly) {
                AddressFamilyPolicy.plan(
                    settings = settings,
                    underlayHasIpv6 = underlayHasIpv6,
                    tcpTransport = false
                )
            } else {
                applyAddressFamily(outbound, settings, underlayHasIpv6)
            }
            // UDP packets are resolved by the hop's native PacketWriter, which reads the
            // outbound-level (settings) strategy rather than sockopt. Write both, as the
            // GFW-knocker config does, so TCP and UDP agree on the same plan.
            val settingsObject = outbound.optJSONObject("settings")
                ?: JSONObject().also { outbound.put("settings", it) }
            settingsObject.put("domainStrategy", hopPlan.endpointStrategy)
            settingsObject.put("targetStrategy", hopPlan.endpointStrategy)
        }

        if (tlsFragmentOutbound != null) out.put(tlsFragmentOutbound)
        if (fragmentOutbound != null) out.put(fragmentOutbound)

        if (needsDirect) {
            // Direct routes must honour the same family plan as the tunnel: a Freedom outbound left
            // on AsIs hands the hostname to the OS resolver and dials whichever answer arrives first,
            // which is how "IPv6 enabled" could still never use IPv6 — and how an IPv4-only user could
            // still leak AAAA lookups.
            out.put(
                JSONObject()
                    .put("tag", "direct")
                    .put("protocol", "freedom")
                    .put(
                        "settings",
                        JSONObject()
                            .put("domainStrategy", dnsPlan.endpointStrategy)
                            .put("targetStrategy", dnsPlan.endpointStrategy)
                    )
            )
        }

        // Traditional Android DNS packets are intercepted before they can emerge as plaintext
        // port-53 traffic from the proxy exit. A/AAAA are imported into Xray's built-in DNS module;
        // other record types receive an empty NOERROR response so they cannot bypass encrypted DNS.
        // Freedom always needs dns-out when freedomDnsHijack is on — otherwise the port-53 rule
        // below would point at a missing outbound and classic DNS would leak to the poisoned ISP.
        val freedomNeedsDnsOut = (byTag[firstTag]?.optString("protocol")?.lowercase() == "freedom" ||
            settings.serverlessModeEnabled) && settings.freedomDnsHijack
        if (settings.dnsHijackEnabled || freedomNeedsDnsOut) {
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

        // The engine only accepts UseIP / UseIPv4 / UseIPv6 for dns.queryStrategy; anything else is
        // a hard config error, and the value must agree with the endpoint strategies written above or
        // an IPv6-preferred node is starved of AAAA records.
        val queryStrategy = dnsPlan.dnsQueryStrategy

        val isFreedomProfile = byTag[firstTag]?.optString("protocol")?.lowercase() == "freedom"
        val freedomDnsList = if (isFreedomProfile && settings.freedomDnsAuto) {
            val configured = listOf(
                settings.freedomDnsPrimaryDoH,
                settings.freedomDnsSecondaryDoH,
                settings.freedomDnsFallbackDoH
            ).map { it.trim() }.filter { it.startsWith("https://") }
            val clean = settings.freedomDnsCleanResolvers
                .split(',', '\n', '\r', ';')
                .map { it.trim() }
                .filter { it.startsWith("https://") }
            (configured + clean).distinctBy { it.lowercase() }
        } else {
            emptyList()
        }

        // Domain-host DoH servers cannot bootstrap their own hostname inside the Freedom chain:
        // with no dns.hosts pin Xray resolves them through the OS resolver (poisoned on Iranian
        // networks) or recurses back into this same DNS module. Keep only IP literals and the
        // pinned hosts above; everything else is dropped instead of shipping a broken
        // finalQuery fallback that silently poisons every lookup.
        val freedomDnsReady = freedomDnsList.filter { url ->
            val host = dohHost(url)
            host.isNotBlank() && (isIpLiteralHost(host) || FREEDOM_DOH_HOST_PINS.keys.any {
                it.equals(host, ignoreCase = true)
            })
        }
        val freedomDnsHosts = if (isFreedomProfile && freedomDnsReady.isNotEmpty()) {
            val used = FREEDOM_DOH_HOST_PINS.filter { (host, _) ->
                freedomDnsReady.any { dohHost(it).equals(host, ignoreCase = true) }
            }
            if (used.isEmpty()) null else JSONObject().also { root ->
                used.forEach { (host, ips) -> root.put(host, JSONArray(ips)) }
            }
        } else {
            null
        }

        val configuredBootstrapIps = if (
            isFreedomProfile && settings.freedomDnsPrimaryIp.isNotBlank()
        ) {
            listOf(settings.freedomDnsPrimaryIp, settings.freedomDnsSecondaryIp)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        } else {
            listOf(settings.dnsPrimaryIp, settings.dnsSecondaryIp)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        }
        // MARBLE_ENDPOINT_BOOTSTRAP_V132 — user intent first, then independent literals in both
        // families so a single filtered address can never orphan the node hostname. IPv6
        // literals only join when the underlay can actually carry IPv6; handing a v4-only
        // network a v6 resolver would just add entries that can never dial.
        val bootstrapIps = (
            configuredBootstrapIps +
                STOCK_BOOTSTRAP_DNS_IPS +
                (if (underlayHasIpv6) STOCK_BOOTSTRAP_DNS_IPV6 else emptyList())
            )
            .distinct()
            .take(MAX_BOOTSTRAP_DNS_IPS)

        val stockCloudflareDoh = "https://1.1.1.1/dns-query"
        val stockGoogleDoh = "https://8.8.8.8/dns-query"
        val stockQuad9Doh = "https://9.9.9.9/dns-query"
        val genericDoh = listOf(settings.dnsPrimaryDoH, settings.dnsSecondaryDoH)
            .map { it.trim() }
            .filter { it.startsWith("https://") }
            .distinct()
        val configuredRemoteDoh = if (isFreedomProfile) {
            // Even with the clean-resolver list switched off, a Freedom chain must never get an
            // unpinned hostname DoH: it would bootstrap through the poisoned OS resolver or
            // recurse into this module. Filter the generic pair to IP literals as well.
            if (freedomDnsReady.isNotEmpty()) freedomDnsReady
            else genericDoh.filter { url -> isIpLiteralHost(dohHost(url)) }
                .ifEmpty { listOf(stockCloudflareDoh, stockGoogleDoh) }
        } else {
            genericDoh.ifEmpty {
                listOf(
                    stockCloudflareDoh,
                    stockGoogleDoh
                )
            }
        }

        // Runtime evidence has now shown transient deadlines from both stock Cloudflare and Google
        // endpoints on different networks. Adaptive DNS must therefore never collapse to a single
        // provider. Preserve the user's order, then add independent encrypted fallbacks.
        val remoteDoh = if (isFreedomProfile && freedomDnsReady.isNotEmpty()) {
            freedomDnsReady.take(6)
        } else if (settings.adaptiveDnsEnabled) {
            (configuredRemoteDoh + listOf(stockCloudflareDoh, stockGoogleDoh, stockQuad9Doh))
                .distinctBy { it.lowercase() }
                .take(3)
        } else {
            configuredRemoteDoh
                .take(2)
        }

        val dnsServers = JSONArray()

        if (bootstrapDomains.isNotEmpty()) {
            bootstrapIps.forEachIndexed { index, ip ->
                // A single blocked literal must not burn the whole endpoint budget: the first
                // (user-chosen) resolver keeps its tight budget and every later candidate gets a
                // little more room before the next one is tried.
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

        // Adaptive DNS remains encrypted-only and bounded. Marble Intelligence measures the
        // configured resolvers and can reorder primary/secondary for the current network; Xray then
        // uses serial failover. This prevents a blocked resolver from creating parallel background
        // fan-out while still allowing the learned healthy provider to become primary next time.
        // Endpoint bootstrap keeps its dedicated https+local rules and is still leak-contained.
        // v59 real-device evidence: a healthy ~120 ms cellular tunnel produced deadline bursts
        // at the previous 850/1050 ms budgets during concurrent Android DNS fan-out. Keep serial,
        // encrypted provider failover, but give TLS/HTTP response bursts enough room before rotating.
        //
        // The Freedom chain fragments the FIRST write of every TCP stream into 1-byte packets with
        // 4 ms pacing (up to maxSplit 517), so a DoH TLS handshake alone can need a couple of
        // seconds before the first response byte. The stock 1.35 s budget makes Cloudflare/Google/
        // Quad9 look dead even when they are reachable. The official XTLS Xray-examples
        // serverless_for_Iran.jsonc gives its single DoH server 10 s (timeoutMs 10000) for
        // exactly this reason.
        // The fragment chain slows the first write of every connection, so the inflated budgets
        // apply to any Freedom profile regardless of which resolver list is active.
        // Freedom first-write fragmentation can take 1–2 s alone; official XTLS uses 10 s.
        // 8 s primary + 1 s per fallback is enough for cold multi-CDN lookups (YouTube/X/Reddit)
        // without waiting the full 10 s on a genuinely dead resolver.
        val freedomDnsTimed = isFreedomProfile
        fun dnsTimeoutMs(index: Int): Long =
            if (freedomDnsTimed) 8_000L + index * 1_000L
            else if (index == 0) 1_350L else 1_650L + (index - 1) * 250L

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

        // Ordinary app DNS is encrypted-only. Endpoint bootstrap still uses the dedicated
        // https+local resolvers above, but normal queries never race plaintext TCP/53 upstreams.
        // This aligns Privacy Sentinel with the actual resolver graph and removes TCP-DNS EOF noise.

        customSecondaryDoh.forEachIndexed { index, address ->
            dnsServers.put(
                JSONObject()
                    .put("address", address)
                    .put("queryStrategy", queryStrategy)
                    .put("timeoutMs", dnsTimeoutMs(index + 1))
                    .put("finalQuery", index == customSecondaryDoh.lastIndex)
            )
        }

        val dnsConfig = JSONObject()
            .put("servers", dnsServers)
            .put("queryStrategy", queryStrategy)
            .put("disableCache", false)
            // Xray optimistic cache: if both encrypted upstreams briefly time out, return a
            // previously validated answer immediately while the cache refreshes in background.
            // No plaintext/system-DNS fallback is introduced.
            // Freedom keeps answers longer (official XTLS uses 21600 s) so multi-CDN hosts
            // (googlevideo, twimg, redditmedia) survive brief DoH stalls without re-fragmenting.
            .put("serveStale", true)
            .put("serveExpiredTTL", if (isFreedomProfile) 21_600 else 1_800)
            .put("enableParallelQuery", false)
        // Pin the domain-host Freedom DoH endpoints to stable IPs. (Upstream instead remaps the
        // hostname to another domain in dns.hosts, e.g. cloudflare-dns.com →
        // challenges.cloudflare.com; a plain IP list is the other form HostAddress parses and
        // needs no fakedns or system hosts.) Either way the resolver bootstrap loop is broken
        // and the TLS SNI still carries the real DoH hostname.
        //
        // Freedom also inherits GFW-knocker's youtube.com → google.com ProxiedDomain remap so
        // the first media lookup rides a host the DPI is less likely to poison, while TLS SNI
        // still carries the real destination after sniffing.
        val hostsObject = freedomDnsHosts ?: if (isFreedomProfile) JSONObject() else null
        if (isFreedomProfile && hostsObject != null) {
            FREEDOM_DOMAIN_REMAPS.forEach { (from, to) ->
                if (!hostsObject.has(from)) hostsObject.put(from, to)
            }
            // cloudflare-dns.com → challenges.cloudflare.com (official XTLS ProxiedDomain)
            if (!hostsObject.has("cloudflare-dns.com")) {
                hostsObject.put("cloudflare-dns.com", "challenges.cloudflare.com")
            }
        }
        hostsObject?.let { if (it.length() > 0) dnsConfig.put("hosts", it) }
        dnsConfig
            .put("useSystemHosts", false)
            .put("disableFallbackIfMatch", bootstrapDomains.isNotEmpty())
            .put("tag", "xgc-dns")
        src.put("dns", dnsConfig)

        val rules = JSONArray()

        // DNS interception has highest priority: any app attempting classic UDP/TCP :53 is handed
        // to dns-out, which hijacks A/AAAA into the encrypted built-in DNS path above.
        val isFreedomMode = isFreedomProfile || settings.serverlessModeEnabled
        val dnsHijackActive = settings.dnsHijackEnabled || (isFreedomMode && settings.freedomDnsHijack)
        if (dnsHijackActive) {
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

        // Android VPN always captures ::/0. When the user turned IPv6 off, blocking here prevents
        // an OS-level IPv6 bypass; when it is on, the same prefix must stay routable or the tunnel
        // would black-hole its own preferred family.
        if (dnsPlan.blockIpv6Traffic) {
            addIpRule(rules, listOf("::/0"), "block")
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

        if (settings.routeBlockAds) {
            normalizeGeoSiteTag(settings.routeAdsTag)?.let { adsTag ->
                addDomainRule(rules, listOf(adsTag), "block")
            }
        }

        // Explicit proxy rules are evaluated before direct rules so users can create exceptions.
        addDomainRule(rules, splitDomains(settings.routeProxyDomains), firstTag)

        // Freedom: block Iran's DNS injector ranges and null answers before anything else can
        // dial them. Without this, half-resolved multi-CDN hosts (YouTube googlevideo shards,
        // X twimg, Reddit media) stick on 10.10.34.x block pages and the page loads half-broken.
        if (isFreedomMode) {
            addIpRule(rules, FREEDOM_POISON_BLOCK_IPS, "block")
        }

        // YouTube and several media stacks prefer QUIC. On Iranian links UDP/443 often blackholes
        // even when TCP/443 works with fragmentation, so Marble defaults Freedom to a fast TCP
        // fallback: reject the QUIC path before the app spends a long time retransmitting. Experts
        // can turn this off and use the official dedicated udp-noises outbound on networks where
        // UDP/443 is actually carried. The existing muxUdp443=reject policy now also has a real
        // routing effect instead of being only a mux hint.
        val rejectUdp443 = settings.muxUdp443 == "reject" ||
            (isFreedomMode && settings.freedomForceTcpForStreaming)
        if (rejectUdp443) {
            addQuicUdp443Rule(rules, "block")
        } else if (isFreedomMode) {
            val noiseTag = keep.firstOrNull { tag ->
                tag == ServerlessFreedomEngine.UDP_NOISES_TAG ||
                    (byTag[tag]?.let { hasNoises(it) && !hasFragment(it) } == true)
            }
            if (noiseTag != null && settings.freedomUdpNoiseEnabled) {
                addQuicUdp443Rule(rules, noiseTag)
            }
        }

        if (needsDirect) {
            val directDomains = linkedSetOf<String>()
            val directIps = linkedSetOf<String>()

            if ((settings.routeBypassPrivate && settings.routingMode != RoutingMode.PROXY_ALL) ||
                (isFreedomMode && settings.freedomDirectDomestic)
            ) {
                directIps += PRIVATE_CIDRS
            }

            if (settings.routingMode == RoutingMode.GEO_DIRECT || settings.routingMode == RoutingMode.CUSTOM ||
                (isFreedomMode && settings.freedomDirectDomestic)
            ) {
                val ipTagString = if (isFreedomMode && settings.freedomDirectDomestic && settings.routeGeoIpTags.isBlank()) "ir,private" else settings.routeGeoIpTags
                val siteTagString = if (isFreedomMode && settings.freedomDirectDomestic && settings.routeGeoSiteTags.isBlank()) "ir" else settings.routeGeoSiteTags
                splitTokens(ipTagString).forEach { tag ->
                    if (tag.equals("private", true) || tag.equals("geoip:private", true)) {
                        directIps += PRIVATE_CIDRS
                    } else {
                        normalizeGeoIpTag(tag)?.let(directIps::add)
                    }
                }
                splitTokens(siteTagString).forEach { tag ->
                    normalizeGeoSiteTag(tag)?.let(directDomains::add)
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

        // Freedom prefers IPOnDemand (official XTLS) so domain rules resolve before matching and
        // multi-CDN hosts never race a poisoned underlay answer against the encrypted path.
        val domainStrategy = if (isFreedomMode) {
            settings.freedomDomainStrategy.takeIf {
                it in setOf("AsIs", "IPIfNonMatch", "IPOnDemand")
            } ?: "IPOnDemand"
        } else {
            settings.routeDomainStrategy.takeIf {
                it in setOf("AsIs", "IPIfNonMatch", "IPOnDemand")
            } ?: "AsIs"
        }

        src.put("routing", JSONObject().put("domainStrategy", domainStrategy).put("rules", rules))
        // Runtime logs are for actionable failures. Xray prints compatibility/deprecation
        // advisories for transports such as HTTPUpgrade/WebSocket even when those transports are
        // still required by the remote server. Marble must not rewrite a client transport without
        // matching server-side support, so keep compatibility and surface only errors here.
        src.put("log", JSONObject().put("loglevel", "error"))

        /*
         * Remove unrelated runtime subsystems from imported full JSON configs. They are not needed
         * by MarbleNG's single selected outbound and can open listeners, start active probes or
         * retain stale API/statistics dependencies that make mobile startup less deterministic.
         */
        listOf("api", "reverse", "metrics", "stats", "observatory", "burstObservatory", "fakedns")
            .forEach(src::remove)

        verify(src, socksPort, firstTag, needsDirect, settings, underlayHasIpv6)
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

    /**
     * Write the address-family plan onto one outbound's endpoint resolution.
     *
     * The live tunnel, the CLI delay test and the in-process native rank instance call this, so a
     * node is always measured over the same family it will be used over — and so that "IPv6 enabled"
     * cannot mean one thing in the tunnel and another in the probes.
     */
    private fun applyAddressFamily(
        outbound: JSONObject,
        settings: AppSettings,
        underlayHasIpv6: Boolean
    ): IpFamilyPlan {
        val protocol = outbound.optString("protocol").lowercase()
        val stream = outbound.optJSONObject("streamSettings")
        val sockopt = stream?.optJSONObject("sockopt")
        val chained = outbound.optJSONObject("proxySettings")
            ?.optString("tag")
            ?.isNotBlank() == true
        val method = stream?.optString("method").orEmpty().lowercase()
        // WireGuard is UDP-only, and a v6-only plan has a single family to dial, so neither can use
        // Xray's TCP race; they get a deterministic address order instead.
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
        // Every hostname endpoint is resolved by the engine's own DNS module. Leaving the strategy at
        // AsIs would hand the node hostname to the OS resolver again, which is both an anti-leak hole
        // and the reason a v6-only node could never be reached.
        sockoptObject.put("domainStrategy", plan.endpointStrategy)
        // Xray races only when tryDelayMs and maxConcurrentTry are both non-zero, and its own default
        // disables the race — so an armed plan must always write the block, and a plan that cannot race
        // must always remove it instead of leaving "ForceIP", which means "pick one address at random".
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

    /** An IPv6 resolver literal only parses inside brackets; an IPv4 one must stay untouched. */
    private fun dnsHostLiteral(value: String): String =
        if (value.contains(':') && !value.startsWith("[")) "[$value]" else value

    private fun fragmentEligible(protocol: String, method: String): Boolean =
        protocol in setOf("http", "shadowsocks", "socks", "trojan", "vless", "vmess") &&
            method !in setOf("hysteria", "mkcp")

    /**
     * DoH hostnames Marble Freedom may use without a bootstrap loop.
     *
     * A `https://host/dns-query` server resolves its own hostname before it can answer anything.
     * Inside the Freedom chain that resolution would either fall back to the OS resolver
     * (poisoned on Iranian networks) or recurse back into this very DNS module. The official
     * XTLS/Xray-examples serverless_for_Iran.jsonc and the GFW-knocker serverless config break
     * that loop with domain→domain `dns.hosts` mappings; the pins below use the IP-list form of
     * the same HostAddress mechanism, which is exactly what v26.7.28 parses into
     * Config_HostMapping.Ip.
     */
    private val FREEDOM_DOH_HOST_PINS = mapOf(
        "dns.shecan.ir" to listOf("178.22.122.100", "185.51.200.2"),
        "dns.adguard-dns.com" to listOf("94.140.14.14", "94.140.15.15")
    )

    /**
     * Domain→domain ProxiedDomain remaps for Freedom. GFW-knocker maps youtube.com → google.com
     * so the first media lookup rides a host the DPI is less likely to poison; the sniffed TLS
     * SNI still carries the real destination on the wire.
     */
    private val FREEDOM_DOMAIN_REMAPS = mapOf(
        "domain:youtube.com" to "google.com",
        "domain:googlevideo.com" to "google.com",
        "domain:ytimg.com" to "google.com",
        "domain:ggpht.com" to "google.com",
        "domain:gvt1.com" to "google.com",
        "domain:gvt2.com" to "google.com"
    )

    /**
     * Iranian DNS-injector / null-answer ranges that must never be dialled. Matches the official
     * XTLS serverless_for_Iran.jsonc block list plus the well-known 10.10.34.34–36 injectors.
     */
    private val FREEDOM_POISON_BLOCK_IPS = listOf(
        "10.10.34.0/24",
        "2001:4188:2:600::/64",
        "0.0.0.0",
        "::"
    )

    private fun hasNoises(outbound: JSONObject): Boolean {
        val noises = outbound.optJSONObject("settings")?.optJSONArray("noises") ?: return false
        return noises.length() > 0
    }

    private fun dohHost(url: String): String = url.trim()
        .substringAfter("https://", "")
        .substringBefore('/')
        .substringBefore('?')
        .trim()

    private fun isIpLiteralHost(host: String): Boolean {
        val clean = host.removePrefix("[").removeSuffix("]")
        if (clean.contains(':')) {
            // Rough but sufficient IPv6 shape check: hex groups plus at most one "::".
            val parts = clean.split("::", limit = 2)
            if (parts.size > 2) return false
            return parts.all { group ->
                group.isEmpty() || group.split(':').all { it.matches(Regex("[0-9a-fA-F]{1,4}")) }
            }
        }
        if (!clean.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}"))) return false
        return clean.split('.').all { (it.toIntOrNull() ?: -1) in 0..255 }
    }

    /** Freedom/direct hops that already shred TLS are selectable routes, not infrastructure. */
    private fun isSelectableProxy(outbound: JSONObject): Boolean {
        val protocol = outbound.optString("protocol").lowercase()
        if (protocol !in infra) return true
        return protocol in setOf("freedom", "direct") && hasFragment(outbound)
    }

    private fun hasFragment(outbound: JSONObject): Boolean {
        val fragment = outbound.optJSONObject("settings")?.optJSONObject("fragment") ?: return false
        return fragment.optString("packets").isNotBlank()
    }

    private fun overlayFragment(
        outbound: JSONObject,
        packets: String,
        length: String,
        interval: String,
        maxSplit: String
    ) {
        val settingsObject = outbound.optJSONObject("settings")
            ?: JSONObject().also { outbound.put("settings", it) }
        settingsObject.put("fragment", fragmentSettings(packets, length, interval, maxSplit))
    }

    private fun fragmentSettings(
        packets: String,
        length: String,
        interval: String,
        maxSplit: String
    ): JSONObject {
        val fragment = JSONObject()
            .put("packets", packets)
            .put("length", length)
            .put("interval", interval)
        putMaxSplit(fragment, maxSplit)
        return fragment
    }

    private fun putMaxSplit(fragment: JSONObject, maxSplit: String) {
        val trimmed = maxSplit.trim()
        if (trimmed.isBlank()) return
        trimmed.toIntOrNull()?.let { fragment.put("maxSplit", it) }
            ?: fragment.put("maxSplit", trimmed)
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

    if (settings.routeBlockAds) {
        val ads = normalizeGeoSiteTag(settings.routeAdsTag)
            ?: error("Ad blocking is enabled without a valid geosite category")
        require(hasDomain(ads, "block")) { "Ad-block route invariant failed: $ads" }
    }

    if (settings.routingMode in setOf(RoutingMode.GEO_DIRECT, RoutingMode.CUSTOM)) {
        splitTokens(settings.routeGeoSiteTags).forEach { raw ->
            normalizeGeoSiteTag(raw)?.let { tag ->
                require(hasDomain(tag, "direct")) { "Missing direct domain route: $tag" }
            }
        }
        splitTokens(settings.routeGeoIpTags).forEach { raw ->
            if (!raw.equals("private", true) && !raw.equals("geoip:private", true)) {
                normalizeGeoIpTag(raw)?.let { tag ->
                    require(hasIp(tag, "direct")) { "Missing direct IP route: $tag" }
                }
            }
        }

        // MARBLE_UNIFIED_ADDRESS_FAMILY_V65 — the address family decision has to be visible in the
        // emitted config, because every failure mode in this area is silent: the engine accepts a
        // half-written policy and simply keeps dialling IPv4.
        // Same underlay verdict the config was built with, so a mid-flight network change cannot
        // make a correct config look like a violated invariant.
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
        if (familyPlan.blockIpv6Traffic) {
            require(hasIp("::/0", "block")) { "IPv6 is disabled but ::/0 is not blocked" }
            // No hostname endpoint may keep a v6-capable strategy while the user excluded IPv6.
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
            // Only hostname endpoints resolve a family at dial time; a config that already carries a
            // literal address is left exactly as its author wrote it.
            if (endpointDomains(outbound).isEmpty()) continue
            val sockopt = outbound.optJSONObject("streamSettings")?.optJSONObject("sockopt") ?: continue
            val strategy = sockopt.optString("domainStrategy")
            if (strategy.isBlank()) continue
            require(strategy in AddressFamilyPolicy.ENDPOINT_STRATEGIES) {
                "Unsupported endpoint domainStrategy: $strategy"
            }
            // ForceIP without an armed race is Xray's "pick one address at random" path. That is the
            // exact defect users reported as "IPv6 never activates", so it can never ship.
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
