package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.parseGeoPrecision
import com.marbleng.app.model.IranModePolicy
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRuleKind
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

object SingBoxConfigBuilder {
    const val INBOUND_TAG = "socks-in"
    const val PROXY_TAG = "marble-proxy"
    const val DIRECT_TAG = "direct"
    const val BLOCK_TAG = "block"
    const val DNS_REMOTE_TAG = "dns-remote"
    const val DNS_DIRECT_TAG = "dns-direct"
    const val DNS_LOCAL_TAG = "dns-local"
    const val DNS_HOSTS_TAG = "dns-hosts"

    val MARBLE_SMUX_PROTOCOLS: Set<String> = setOf("vless", "vmess", "trojan", "shadowsocks")

    const val DNS_FAKEIP_TAG = "dns-fakeip"

    const val FAKE_IP_POOL = FakeIpPolicy.IPV4_POOL

    const val DNS_BOOTSTRAP_TAG = "dns-bootstrap"

    val IRAN_POISON_BLOCK_IPS = listOf(
        "10.10.34.0/24",
        "2001:4188:2:600::/64",
        "0.0.0.0",
        "::"
    )

    const val HTTP_CLIENT_DIRECT_TAG = "marble-http-direct"

    const val STRATEGY_LINK = "link-parser"

    const val STRATEGY_LINK_TRANSLATED = "link-translated"

    const val STRATEGY_TRANSLATED = "translated"
    const val STRATEGY_NATIVE = "native-singbox"

    const val RULE_SET_GEOIP_IR = "geoip-ir"
    const val RULE_SET_GEOSITE_IR = "geosite-ir"
    const val RULE_SET_GEOIP_PRIVATE = "geoip-private"
    const val RULE_SET_ADS = "geosite-ads"

    private val LINK_SCHEMES = setOf(
        "vless", "vmess", "trojan", "ss", "hysteria", "hy2", "hysteria2", "tuic", "anytls"
    )

    data class Support(
        val supported: Boolean,
        val strategy: String,
        val reason: String,
        val notes: List<String>
    )

    data class Build(val json: String, val strategy: String, val notes: List<String>)

    fun describe(profile: ProxyProfile, settings: AppSettings): Support {
        val notes = mutableListOf<String>()
        if (profile.scheme.equals("ssh", true)) {
            return Support(false, "", "SSH profiles using Marble's Java bridge currently require Xray.", notes)
        }
        return try {
            val set = candidateSet(profile, settings)
            val first = set.candidates.firstOrNull()
            if (first != null) {
                Support(true, first.strategy, "", set.candidates.flatMap { it.notes }.distinct())
            } else {
                Support(false, "", set.refusal, notes)
            }
        } catch (error: Exception) {
            Support(false, "", error.message ?: "Invalid proxy configuration", notes)
        }
    }

    fun candidateBuilds(
        profile: ProxyProfile,
        settings: AppSettings,
        socksPort: Int,
        apiPort: Int,
        apiSecret: String,
        logPath: String,
        cachePath: String,
        resolverPool: List<String> = emptyList(),
        ruleSetPaths: Map<String, String> = emptyMap(),
        bootstrapDnsPort: Int = 0,
        forTest: Boolean = false,
        underlayHasIpv6: Boolean = AddressFamilyPolicy.underlayHasIpv6()
    ): List<Build> {
        require(!AddressFamilyPolicy.excludedIpv4Endpoint(profile.host, settings)) {
            AddressFamilyPolicy.IPV4_LITERAL_DISABLED
        }
        val set = candidateSet(profile, settings, forTest)
        require(set.candidates.isNotEmpty()) {
            set.refusal.ifBlank { "sing-box cannot run this profile" }
        }
        val errors = mutableListOf<String>()
        val builds = set.candidates.mapNotNull { candidate ->
            runCatching {
                assemble(
                    candidate = candidate,
                    profile = profile,
                    settings = settings,
                    socksPort = socksPort,
                    apiPort = apiPort,
                    apiSecret = apiSecret,
                    logPath = logPath,
                    cachePath = cachePath,
                    resolverPool = resolverPool,
                    ruleSetPaths = ruleSetPaths,
                    bootstrapDnsPort = bootstrapDnsPort,
                    forTest = forTest,
                    underlayHasIpv6 = underlayHasIpv6
                )
            }.onFailure { errors += it.message.orEmpty() }.getOrNull()
        }.distinctBy { it.json }
        require(builds.isNotEmpty()) {
            errors.firstOrNull { it.isNotBlank() } ?: set.refusal.ifBlank { "sing-box cannot run this profile" }
        }
        return builds
    }

    private data class Candidate(
        val strategy: String,
        val outbounds: List<JSONObject>,
        val notes: List<String>
    )

    private data class CandidateSet(val candidates: List<Candidate>, val refusal: String)

    /**
     * MARBLE_SINGBOX_PINNED_COMPAT_V164 — pinned configs are no longer hard-refused. The
     * translator converts them to `tls.insecure: true` with a note explaining the downgrade.
     * The parser candidate is excluded (it silently ignores pins). This returns null for
     * all configs, letting the normal candidate-selection flow handle pinning through the
     * translation path.
     */
    fun pinnedPeerRefusal(profile: ProxyProfile): String? {
        return null
    }

    const val PINNED_PEER_REFUSAL =
        "config-unsupported: tlsSettings.pinnedPeerCertSha256/verifyPeerCertByName: this server pins " +
            "its TLS certificate (pcs/vcn). sing-box extended cannot verify a certificate hash or a " +
            "name other than the SNI, so it would connect without Internet. Use the Xray core for it."

    /**
     * MARBLE_SINGBOX_PINNED_COMPAT_V164 — VLESS/VMess nodes with TLS certificate pinning
     * (`pcs`/`vcn`) are now translated to sing-box with `tls.insecure: true` and a note.
     *
     * sing-box extended cannot verify certificate hashes (`pinnedPeerCertSha256`) or names other
     * than the SNI (`verifyPeerCertByName`), but refusing pinned configs entirely forced users to
     * switch engines for a common real-world config. The translated path sets `insecure: true`
     * which accepts any certificate — losing the pin's verification but establishing a working
     * tunnel. The parser candidate is excluded because it silently ignores pins.
     */
    fun linkHasPins(link: String): Boolean = linkCarriesPin(link)

    internal fun linkCarriesPin(link: String): Boolean {
        val params = ShareLinkParams.ofRawLink(link)
        val keys = TlsPinningPolicy.PINNED_SHA256_KEYS + TlsPinningPolicy.VERIFY_BY_NAME_KEYS
        return params.first(*keys.toTypedArray()).isNotBlank()
    }

    internal fun linkCarriesReality(link: String): Boolean {
        val params = ShareLinkParams.ofRawLink(link)
        if (params.first("security", "sec").equals("reality", ignoreCase = true)) return true
        return params.first("pbk", "publicKey", "realityPublicKey").isNotBlank()
    }

    private fun candidateSet(profile: ProxyProfile, settings: AppSettings, forTest: Boolean = false): CandidateSet {
        val root = profile.configJson.takeIf { it.isNotBlank() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (root != null && NativeSingBoxConfig.isNative(root)) {
            return CandidateSet(
                listOf(Candidate(STRATEGY_NATIVE, NativeSingBoxConfig.outbounds(root), emptyList())),
                ""
            )
        }
        // MARBLE_SINGBOX_PINNED_COMPAT_V164 — pinned configs are no longer refused; the translator

        val link = shareLink(profile)
        val isPinned = link?.let { linkCarriesPin(it) } == true ||
            (profile.configJson.isNotBlank() && TlsPinningPolicy.configIsPinned(profile.configJson))

        val xhttpLink = link?.let {
            ShareLinkParams.ofRawLink(it).first("type", "network", "net")
                .lowercase() in setOf("xhttp", "splithttp")
        } == true

        val realityLink = link?.let { linkCarriesReality(it) } == true

        val parser = if (isPinned || xhttpLink || realityLink) null else link?.let {
            Candidate(STRATEGY_LINK, listOf(parserOutbound(it, settings)), emptyList())
        }

        val linkNotes = mutableListOf<String>()
        if (xhttpLink) linkNotes += "XHTTP link uses Marble's lossless extra reader (the pinned core's link parser drops XHTTP options)."
        if (realityLink) linkNotes += "REALITY link skips the pinned core's link parser (it never emits the X25519MLKEM768 key share Xray >= v26.9.8 servers require); Marble's translation does."
        val fromLink = link
            ?.let { linkJson(it) }
            ?.let { json -> translatedCandidate(STRATEGY_LINK_TRANSLATED, json, settings, linkNotes, forTest) }
            ?.getOrNull()

        val storedNotes = mutableListOf<String>()
        val storedResult = root?.let { translatedCandidate(STRATEGY_TRANSLATED, it, settings, storedNotes, forTest) }
        val stored = storedResult?.getOrNull()

        val ordered = when {
            xhttpLink -> listOfNotNull(fromLink, stored)
            settings.singBoxPreferParser -> listOfNotNull(parser, fromLink, stored)
            else -> listOfNotNull(stored, fromLink, parser)
        }.distinctBy { candidate -> candidate.outbounds.joinToString("|") { it.toString() } }

        val refusal = when {
            ordered.isNotEmpty() -> ""
            storedResult != null -> storedResult.exceptionOrNull()
                ?.let { it.message ?: it.javaClass.simpleName }
                ?: "The stored config cannot be expressed as a sing-box outbound."
            else -> "The stored config is not readable JSON or a supported share link."
        }
        return CandidateSet(ordered, refusal)
    }

    private fun translatedCandidate(
        strategy: String,
        root: JSONObject,
        settings: AppSettings,
        notes: MutableList<String>,
        forTest: Boolean = false
    ): Result<Candidate> = runCatching {
        Candidate(strategy, translate(root, settings, notes, forTest), notes.toList())
    }

    private fun parserOutbound(link: String, settings: AppSettings): JSONObject {
        val scheme = when (link.substringBefore("://", "").lowercase()) {
            "hy2" -> "hysteria2"
            else -> link.substringBefore("://", "").lowercase()
        }
        return JSONObject()
            .put("type", "parser")
            .put("tag", PROXY_TAG)
            .put("link", link)
            .apply { applyDialTuning(this, settings, protocolHint = scheme) }
    }

    @Volatile
    internal var linkJson: (String) -> JSONObject? = { link -> xrayJsonFromLink(link) }

    private fun xrayJsonFromLink(link: String): JSONObject? = runCatching {
        ProxyParser.parseInput(link)
            .singleOrNull()
            ?.configJson
            ?.takeIf { it.isNotBlank() }
            ?.let { JSONObject(it) }
    }.getOrNull()

    fun build(
        profile: ProxyProfile,
        settings: AppSettings,
        socksPort: Int,
        apiPort: Int,
        apiSecret: String,
        logPath: String,
        cachePath: String,
        resolverPool: List<String> = emptyList(),
        ruleSetPaths: Map<String, String> = emptyMap(),
        bootstrapDnsPort: Int = 0,
        forTest: Boolean = false,
        underlayHasIpv6: Boolean = AddressFamilyPolicy.underlayHasIpv6()
    ): Build = candidateBuilds(
        profile = profile,
        settings = settings,
        socksPort = socksPort,
        apiPort = apiPort,
        apiSecret = apiSecret,
        logPath = logPath,
        cachePath = cachePath,
        resolverPool = resolverPool,
        ruleSetPaths = ruleSetPaths,
        bootstrapDnsPort = bootstrapDnsPort,
        forTest = forTest,
        underlayHasIpv6 = underlayHasIpv6
    ).first()

    private fun assemble(
        candidate: Candidate,
        profile: ProxyProfile,
        settings: AppSettings,
        socksPort: Int,
        apiPort: Int,
        apiSecret: String,
        logPath: String,
        cachePath: String,
        resolverPool: List<String>,
        ruleSetPaths: Map<String, String>,
        bootstrapDnsPort: Int,
        forTest: Boolean,
        underlayHasIpv6: Boolean
    ): Build {
        val notes = candidate.notes.toMutableList()
        // MARBLE_SINGBOX_GEO_ROUTING_V222 — one registry for every rule set the finished document
        // references. DNS rules are written before route rules, and a set referenced only by a DNS
        // rule still has to exist in `route.rule_set` or the core refuses to start.
        val usedRuleSets = linkedSetOf<String>()
        val outbounds = JSONArray()
        candidate.outbounds.forEach { outbounds.put(it) }

        outbounds

            .put(directOutbound(settings))
            .put(JSONObject().put("type", "block").put("tag", BLOCK_TAG))

        val root = JSONObject()
            .put(
                "log",
                JSONObject()
                    .put("level", singBoxLogLevel(settings))
                    .put("output", logPath)
                    .put("timestamp", settings.singBoxLogTimestamp)
            )
            .put(
                "dns",
                dnsConfig(settings, profile, resolverPool, underlayHasIpv6, ruleSetPaths, usedRuleSets)
            )
            .put("inbounds", inboundConfig(settings, socksPort))
            .put("outbounds", outbounds)
            .put(

                "http_clients",
                JSONArray().put(
                    JSONObject()
                        .put("tag", HTTP_CLIENT_DIRECT_TAG)
                        .put("detour", DIRECT_TAG)
                )
            )
            .put(
                "route",
                routeConfig(settings, notes, ruleSetPaths, usedRuleSets, underlayHasIpv6)
            )
            .put(
                "experimental",
                JSONObject()
                    .put(
                        "clash_api",
                        JSONObject()
                            .put("external_controller", "127.0.0.1:$apiPort")
                            .put("secret", apiSecret)
                            .put("access_control_allow_private_network", true)
                    )

                    .put("unified_delay", JSONObject().put("enabled", settings.singBoxUnifiedDelay))
                    .apply {
                        if (settings.singBoxCacheFile && !forTest) {
                            put(
                                "cache_file",
                                JSONObject()
                                    .put("enabled", true)
                                    .put("path", cachePath)
                                    .put("store_dns", true)
                                    .apply {
                                        if (FakeIpPolicy.isDnsPathArmed(settings)) {
                                            put("store_fakeip", true)
                                        }
                                    }
                            )
                        }
                    }
            )

        if (candidate.strategy == STRATEGY_NATIVE) {
    NativeSingBoxConfig.copyResources(JSONObject(profile.configJson), root)
    notes += "Imported proxy graph retained; Marble owns the local inbound, DNS and routing policy."
}

sanitizeAndroidCliConfig(root, notes)

enforcePhysicalDialPolicy(root, settings, underlayHasIpv6)

val coreOptionPatch = CoreOptions.merge(root, settings.singBoxExtraJson, CoreOptions.SINGBOX_PROTECTED_KEYS)
if (coreOptionPatch.error.isNotBlank()) {
    notes += "Extra JSON refused: ${coreOptionPatch.error}"
    RuntimeDiagnostics.coreOption("singbox", "extra-json-refused", coreOptionPatch.error)
} else if (coreOptionPatch.changed) {
    notes += "Extra JSON applied: ${coreOptionPatch.applied.joinToString(", ")}"
    RuntimeDiagnostics.coreOption("singbox", "extra-json-merged", coreOptionPatch.applied.joinToString(","))
}

return Build(root.toString(), candidate.strategy, notes.distinct())
    }

private fun sanitizeAndroidCliConfig(
    root: JSONObject,
    notes: MutableList<String>
) {
    val route = root.optJSONObject("route")
    if (route != null) {
        SingBoxAndroidRuntime.ANDROID_FORBIDDEN_ROUTE_KEYS.forEach { key ->
            if (route.has(key)) {
                route.remove(key)
                notes += "android-sanitize: removed route.$key"
            }
        }

        route.optJSONArray("rules")?.let { rules ->
            sanitizeRuleArray(rules, notes)
        }
    }

    root.optJSONArray("inbounds")?.let { inbounds ->
        for (index in 0 until inbounds.length()) {
            inbounds.optJSONObject(index)?.let { inbound ->
                val type = inbound.optString("type").lowercase()
                if (type in SingBoxAndroidRuntime.ANDROID_FORBIDDEN_INBOUND_TYPES) {
                    throw IllegalArgumentException(
                        "android-sanitize: forbidden inbound type '$type'"
                    )
                }

                removeKeys(
                    inbound,
                    SingBoxAndroidRuntime.ANDROID_FORBIDDEN_RULE_KEYS,
                    "inbounds[$index]",
                    notes
                )
                if (inbound.has("sniff") || inbound.has("sniff_override_destination")) {
                    inbound.remove("sniff")
                    inbound.remove("sniff_override_destination")
                    notes += "removed deprecated inbound sniff field(s) from inbounds[$index] (replaced by route action sniff)"
                }
            }
        }
    }

    root.optJSONArray("outbounds")?.let { outbounds ->
        for (index in 0 until outbounds.length()) {
            outbounds.optJSONObject(index)?.let { outbound ->
                sanitizeDialObject(
                    outbound,
                    "outbounds[$index]",
                    notes
                )
            }
        }
    }

    root.optJSONObject("dns")?.let { dns ->
        dns.optJSONArray("servers")?.let { servers ->
            for (index in 0 until servers.length()) {
                servers.optJSONObject(index)?.let { server ->
                    val type = server.optString("type").lowercase()
                    if (type in SingBoxAndroidRuntime.ANDROID_FORBIDDEN_DNS_TYPES) {
                        server.remove("type")
                        server.put("type", "local")
                        notes += "android-sanitize: replaced dns.servers[$index].$type with local"
                    }
                }
            }
        }
    }
}

private fun sanitizeRuleArray(
    rules: JSONArray,
    notes: MutableList<String>
) {
    for (index in 0 until rules.length()) {
        val rule = rules.optJSONObject(index) ?: continue

        removeKeys(
            rule,
            SingBoxAndroidRuntime.ANDROID_FORBIDDEN_RULE_KEYS,
            "route.rules[$index]",
            notes
        )

        val unknown = mutableListOf<String>()
        val keys = rule.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in SingBoxAndroidRuntime.SCHEMA_ROUTE_RULE_KEYS) unknown += key
        }
        unknown.forEach { key ->
            rule.remove(key)
            notes += "schema-guard: removed route.rules[$index].$key (not in pinned core schema)"
        }

        rule.optJSONArray("rules")?.let { nested ->
            sanitizeRuleArray(nested, notes)
        }
    }
}

private fun sanitizeDialObject(
    json: JSONObject,
    location: String,
    notes: MutableList<String>
) {
    SingBoxAndroidRuntime.ANDROID_FORBIDDEN_DIAL_KEYS.forEach { key ->
        if (json.has(key)) {
            json.remove(key)
            notes += "android-sanitize: removed $location.$key"
        }
    }

    json.optJSONObject("detour")?.let { detour ->
        sanitizeDialObject(detour, "$location.detour", notes)
    }
}

private fun removeKeys(
    json: JSONObject,
    keys: List<String>,
    location: String,
    notes: MutableList<String>
) {
    keys.forEach { key ->
        if (json.has(key)) {
            json.remove(key)
            notes += "android-sanitize: removed $location.$key"
        }
    }
}

    fun localProxyAddress(port: Int): String = "127.0.0.1:$port"

    fun shareLink(profile: ProxyProfile): String? {
        val raw = profile.raw.trim()
        if (raw.isEmpty() || raw.length > 4096 || raw.contains('\n') || raw.contains('\r')) return null
        val scheme = raw.substringBefore("://", "").lowercase()
        if (scheme !in LINK_SCHEMES) return null

        return ShareLinkNormalizer.normalize(raw)
    }

    private fun firstProxyOutbound(outbounds: JSONArray): JSONObject? {
        for (i in 0 until outbounds.length()) {
            val candidate = outbounds.optJSONObject(i) ?: continue
            val protocol = candidate.optString("protocol").lowercase()
            if (protocol in setOf("freedom", "direct", "blackhole", "block", "dns", "loopback")) continue
            return candidate
        }

        for (i in 0 until outbounds.length()) {
            val candidate = outbounds.optJSONObject(i) ?: continue
            val protocol = candidate.optString("protocol").lowercase()
            if (protocol in setOf("freedom", "direct") && hasFragment(candidate)) {
                return candidate
            }
        }

        for (i in 0 until outbounds.length()) {
            val candidate = outbounds.optJSONObject(i) ?: continue
            val protocol = candidate.optString("protocol").lowercase()
            if (protocol in setOf("freedom", "direct")) {
                return candidate
            }
        }
        return null
    }

    private fun hasFragment(outbound: JSONObject): Boolean {
        val fragment = outbound.optJSONObject("settings")?.optJSONObject("fragment") ?: return false
        return fragment.optString("packets").isNotBlank()
    }

    private fun outboundByTag(outbounds: JSONArray, tag: String): JSONObject? {
        if (tag.isBlank()) return null
        for (i in 0 until outbounds.length()) {
            val candidate = outbounds.optJSONObject(i) ?: continue
            if (candidate.optString("tag") == tag) return candidate
        }
        return null
    }

    private fun hasCertificatePinning(outbound: JSONObject): Boolean {
        val tls = outbound.optJSONObject("streamSettings")?.optJSONObject("tlsSettings")
            ?: return false
        return tls.optString("pinnedPeerCertSha256").isNotBlank() ||
            tls.optString("verifyPeerCertByName").isNotBlank()
    }

    private fun translate(root: JSONObject, settings: AppSettings, notes: MutableList<String>, forTest: Boolean = false): List<JSONObject> {
        val outbounds = root.getJSONArray("outbounds")
        val entry = firstProxyOutbound(outbounds) ?: error("no proxy outbound")

        val chain = mutableListOf<JSONObject>()
        val visited = mutableSetOf<String>()
        var current = entry
        while (true) {
            val identity = current.optString("tag").ifBlank { "@entry" }
            require(visited.add(identity)) { "config-unsupported: outbounds: cyclic proxy chain at $identity" }
            require(chain.size < 16) { "config-unsupported: outbounds: proxy chain exceeds 16 hops" }
            chain += current
            val nextTag = current.optJSONObject("proxySettings")?.optString("tag").orEmpty()
                .ifBlank { current.optJSONObject("streamSettings")?.optJSONObject("sockopt")?.optString("dialerProxy").orEmpty() }
            if (nextTag.isBlank()) break
            current = outboundByTag(outbounds, nextTag)
                ?: throw ConfigTranslationException("outbounds.detour", "missing chain hop '$nextTag'")
        }
        if (chain.size > 1) notes += "Chained through ${chain.size - 1} extra hop(s)."

        if (chain.all { it.optString("protocol").lowercase() in setOf("freedom", "direct") }) {
            notes += "Serverless direct/fragment chain mapped to sing-box direct outbound with full dial flight."
            val serverlessOutbound = JSONObject().put("tag", PROXY_TAG).put("type", "direct")
            applyDialTuning(serverlessOutbound, settings, protocolHint = "direct")
            return listOf(serverlessOutbound)
        }

        return chain.mapIndexed { index, hop ->
            val tag = if (index == 0) PROXY_TAG else "marble-hop-$index"

            val detour = if (index < chain.lastIndex) "marble-hop-${index + 1}" else null
            translateHop(hop, tag, detour, settings, notes, forTest)
        }
    }

    private fun translateHop(
        outbound: JSONObject,
        tag: String,
        detour: String?,
        settings: AppSettings,
        notes: MutableList<String>,
        forTest: Boolean = false
    ): JSONObject {
        val protocol = outbound.optString("protocol").lowercase()
        val xraySettings = outbound.optJSONObject("settings") ?: JSONObject()
        val stream = outbound.optJSONObject("streamSettings") ?: JSONObject()
        val result = JSONObject().put("tag", tag)
        listOf("vnext", "servers").forEach { key ->
            if ((xraySettings.optJSONArray(key)?.length() ?: 0) > 1) {
                throw ConfigTranslationException("settings.$key", "multiple servers require explicit profile selection; they cannot be silently collapsed")
            }
        }

        when (protocol) {
            "vless", "vmess" -> {
                val vnextServer = xraySettings.optJSONArray("vnext")?.optJSONObject(0)
                val settingsServer = xraySettings.takeIf {
                    it.optString("address").isNotBlank() && it.optInt("port", 0) > 0
                }
                val server = vnextServer ?: settingsServer
                    ?: error("$protocol outbound has neither a vnext[] server nor a direct {address,port} one")

                val user0 = server.optJSONArray("users")?.optJSONObject(0)
                    ?: xraySettings.optJSONArray("users")?.optJSONObject(0)
                fun pick(key: String, from: JSONObject?): String =
                    from?.optString(key)?.takeIf { it.isNotBlank() }.orEmpty()
                fun resolve(vararg keys: String): String = keys
                    .mapNotNull { key -> pick(key, user0).ifBlank { pick(key, server) }.ifBlank { pick(key, xraySettings) } }
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                result.put("type", protocol)
                result.put("server", resolve("address").ifBlank { server.optString("address") })
                result.put("server_port", server.optInt("port", 0).takeIf { it > 0 }
                    ?: xraySettings.optInt("port", 0))
                result.put("uuid", resolve("id", "uuid"))
                if (protocol == "vless") {
                    resolve("packetEncoding").takeIf { it.isNotBlank() }?.let {
                        result.put("packet_encoding", if (it == "none") "" else it)
                    }
                    resolve("flow").takeIf { it.isNotBlank() }?.let { result.put("flow", it) }
                    resolve("encryption").takeIf { it.isNotBlank() }
                        ?.let { result.put("encryption", it) }
                } else {
                    result.put("security", resolve("security").ifBlank { "auto" })
                    val alterId = resolve("alterId").toIntOrNull()?.takeIf { it > 0 } ?: 0
                    if (alterId > 0) result.put("alter_id", alterId)
                    val globalPadding = user0?.optBoolean("globalPadding", false) == true ||
                        server.optBoolean("globalPadding", false)
                    if (globalPadding) result.put("global_padding", true)

                    resolve("packetEncoding").ifBlank { xraySettings.optString("packetEncoding") }
                        .takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
                        ?.let { result.put("packet_encoding", it) }
                }
            }

            "trojan" -> {
                val server = firstServer(xraySettings) ?: error("trojan outbound has no server")
                result.put("type", "trojan")
                result.put("server", server.optString("address"))
                result.put("server_port", server.optInt("port"))
                result.put("password", server.optString("password"))
            }

            "shadowsocks" -> {
                val server = firstServer(xraySettings) ?: error("shadowsocks outbound has no server")
                result.put("type", "shadowsocks")
                result.put("server", server.optString("address"))
                result.put("server_port", server.optInt("port"))
                result.put("method", server.optString("method"))
                result.put("password", server.optString("password"))
                server.optString("plugin").takeIf { it.isNotBlank() }?.let { result.put("plugin", it) }
                server.optString("plugin_opts").takeIf { it.isNotBlank() }
                    ?.let { result.put("plugin_opts", it) }
            }

            "socks" -> {
                val server = firstServer(xraySettings) ?: error("socks outbound has no server")
                result.put("type", "socks")
                result.put("server", server.optString("address"))
                result.put("server_port", server.optInt("port"))
                result.put("version", "5")
                server.optJSONArray("users")?.optJSONObject(0)?.let { user ->
                    putUserPass(result, user)
                } ?: putUserPass(result, server)
            }

            "http" -> {
                val server = firstServer(xraySettings) ?: error("http outbound has no server")
                result.put("type", "http")
                result.put("server", server.optString("address"))
                result.put("server_port", server.optInt("port"))
                server.optJSONArray("users")?.optJSONObject(0)?.let { user ->
                    putUserPass(result, user)
                } ?: putUserPass(result, server)
            }

            "hysteria2", "hysteria" -> {
                val server = firstServer(xraySettings)
                val address = sequenceOf<String?>(
                    server?.optString("address"),
                    xraySettings.optString("address")
                ).firstOrNull { !it.isNullOrBlank() }
                    ?: error("$protocol outbound has no address")
                val port = sequenceOf<Int?>(
                    server?.optInt("port", 0),
                    xraySettings.optInt("port", 0)
                ).firstOrNull { it != null && it > 0 } ?: error("$protocol outbound has no port")
                val version = when {
                    xraySettings.optInt("version") == 2 || stream.optJSONObject("hysteriaSettings")?.optInt("version") == 2 -> 2
                    protocol == "hysteria" -> 1
                    else -> (server?.optInt("version", 0) ?: 0).takeIf { it == 1 || it == 2 } ?: 2
                }

                val hySettings = stream.optJSONObject("hysteriaSettings")
                val authSource = server ?: xraySettings
                if (version == 1) {
                    result.put("type", "hysteria")
                    val auth = sequenceOf<String?>(
                        authSource.optString("auth_str"),
                        authSource.optString("auth"),
                        authSource.optString("password"),
                        hySettings?.optString("auth"),
                        hySettings?.optString("auth_str")
                    ).firstOrNull { !it.isNullOrBlank() }
                    if (auth != null) result.put("auth_str", auth)
                } else {
                    result.put("type", "hysteria2")
                    val auth = sequenceOf<String?>(
                        authSource.optString("password"),
                        authSource.optString("auth"),
                        authSource.optString("auth_str"),
                        hySettings?.optString("auth"),
                        hySettings?.optString("auth_str")
                    ).firstOrNull { !it.isNullOrBlank() }
                    if (auth != null) result.put("password", auth)
                }
                result.put("server", address)
                result.put("server_port", port)
                val up = sequenceOf<Int?>(
                    server?.optInt("up_mbps", 0)?.takeIf { it > 0 },
                    hySettings?.optInt("up_mbps", 0)?.takeIf { it > 0 },
                    hySettings?.optString("up")?.toIntOrNull()?.takeIf { it > 0 }
                ).firstOrNull { it != null }
                if (up != null) result.put("up_mbps", up)
                val down = sequenceOf<Int?>(
                    server?.optInt("down_mbps", 0)?.takeIf { it > 0 },
                    hySettings?.optInt("down_mbps", 0)?.takeIf { it > 0 },
                    hySettings?.optString("down")?.toIntOrNull()?.takeIf { it > 0 }
                ).firstOrNull { it != null }
                if (down != null) result.put("down_mbps", down)

                fun firstValue(vararg keys: String): Any? = keys.firstNotNullOfOrNull { key ->
                    sequenceOf(hySettings, server, xraySettings)
                        .filterNotNull()
                        .firstOrNull { it.has(key) }
                        ?.opt(key)
                }
                firstValue("recv_window_conn", "recvWindowConn")?.let { result.put("recv_window_conn", it) }
                firstValue("recv_window", "recvWindow")?.let { result.put("recv_window", it) }
                firstValue("disable_mtu_discovery", "disableMtuDiscovery")
                    ?.let { result.put("disable_mtu_discovery", it) }
                firstValue("fast_open", "fastOpen")?.let { result.put("fast_open", it) }
                firstValue("network")?.toString()?.takeIf(String::isNotBlank)?.let { result.put("network", it) }
                firstValue("ports")?.toString()?.takeIf(String::isNotBlank)?.let { ports ->
                    val values = JSONArray()
                    ports.split(',').map(String::trim).filter(String::isNotBlank).forEach { token ->
                        val number = token.toIntOrNull()
                        if (number != null) values.put(number)
                        else values.put(token.replace(Regex("^(\\d+)-(\\d+)$"), "$1:$2"))
                    }
                    if (values.length() > 0) result.put("server_ports", values)
                }
                firstValue("hop_interval", "hopInterval")?.toString()?.takeIf(String::isNotBlank)
                    ?.let { value -> result.put("hop_interval", if (value.all(Char::isDigit)) "${value}s" else value) }
                firstValue("udp_timeout", "udpIdleTimeout")?.toString()?.toIntOrNull()?.takeIf { it > 0 }
                    ?.let { result.put("udp_timeout", "${it}s") }

                val inlineObfs = hySettings?.optString("obfs").orEmpty()
                if (version == 1 && inlineObfs.isNotBlank()) result.put("obfs", inlineObfs)
                server?.optJSONObject("obfs")?.let { obfs ->
                    val obfsPassword = obfs.optString("password").ifBlank { obfs.optString("obfs") }
                    if (obfsPassword.isNotBlank()) {
                        if (version == 1) {
                            result.put("obfs", obfsPassword)
                        } else {
                            result.put(
                                "obfs",
                                JSONObject()
                                    .put("type", obfs.optString("type").ifBlank { "salamander" })
                                    .put("password", obfsPassword)
                            )
                        }
                    }
                }
            }

            "freedom", "direct" -> {
                if (xraySettings.has("noises") || xraySettings.has("redirect")) {
                    notes += "Xray freedom noises/redirect mapped to direct outbound for sing-box."
                }

                if (xraySettings.has("fragment")) {
                    notes += "The imported freedom hop is a fragment dialer; sing-box has no " +
                        "equivalent, so this hop dials directly."
                }
                result.put("type", "direct")
            }
            else -> throw ConfigTranslationException("outbounds.protocol", "'$protocol' is not translatable to the pinned extended core")
        }

        SingBoxTransportTranslator.transport(stream, settings, notes)?.let { result.put("transport", it) }
        SingBoxTransportTranslator.tls(stream, settings, notes)?.let { tls ->
            val quicTransport = result.optString("type") in setOf("hysteria", "hysteria2") ||
                result.optJSONObject("transport")?.optString("type") == "quic"
            if (quicTransport) {
                if (tls.has("reality")) throw ConfigTranslationException("realitySettings", "REALITY cannot be applied to a QUIC transport")
                tls.remove("utls")
            }
            // MARBLE_FRAGMENT_POLICY_V221 — the handshake treatment the user (or the Censorship
            // Lab) asked for. REALITY is excluded on purpose: its ClientHello *is* the camouflage,
            // and a fragmented record stream is a different fingerprint from the one the node was
            // built to present. QUIC transports are excluded because their TLS is inside QUIC.
            if (tls.optJSONObject("reality") == null) {
                FragmentPolicy.applyTo(tls, settings, quic = quicTransport, notes = notes)
            }
            result.put("tls", tls)
        }
        stream.optJSONObject("finalmask")?.let { mask ->
            val udp = mask.optJSONArray("udp")
            if (result.optString("type") == "hysteria2" && udp?.length() == 1 &&
                udp.getJSONObject(0).optString("type") == "salamander" && !mask.has("tcp")) {
                result.put("obfs", JSONObject().put("type", "salamander")
                    .put("password", udp.getJSONObject(0).getJSONObject("settings").getString("password")))
            } else if (mask.length() > 0) {
                throw ConfigTranslationException("streamSettings.finalmask", "this mask has no lossless mapping to sing-box")
            }
        }
        val marbleMultiplex = SingBoxTransportPolicy.multiplex(settings)
        if (marbleMultiplex != null && protocol in MARBLE_SMUX_PROTOCOLS) {
            result.put(SingBoxTransportPolicy.MultiplexField, marbleMultiplex)
            notes += "Multiplexing on (${marbleMultiplex.optString("protocol")}, " +
                "${settings.singBoxMuxMaxConnections} connection(s), " +
                "${settings.singBoxMuxMaxStreams} streams each)."
        } else if (marbleMultiplex != null) {
            notes += "Multiplexing is off for this node: '$protocol' carries no multiplex in the " +
                "pinned core, and writing the object anyway would be a rejected config."
        }
        if (protocol !in setOf("freedom", "direct")) {
            require(result.optString("server").isNotBlank()) { "config-unsupported: settings.address: missing server" }
            require(result.optInt("server_port") in 1..65535) { "config-unsupported: settings.port: invalid port" }
        }

        if (detour != null) {
            result.put("detour", detour)
        } else {
            applyDialTuning(
                result,
                settings,
                protocolHint = protocol,
                methodHint = stream.optString("method")
                    .ifBlank { stream.optString("network") }
            )
        }
        return result
    }

    private fun firstServer(settings: JSONObject): JSONObject? {
        settings.optJSONArray("servers")?.optJSONObject(0)?.let { return it }
        return settings.takeIf { it.optString("address").isNotBlank() && it.optInt("port", 0) > 0 }
    }

    private fun putUserPass(result: JSONObject, server: JSONObject) {
        server.optString("user").takeIf { it.isNotBlank() }?.let { result.put("username", it) }
        server.optString("pass").takeIf { it.isNotBlank() }?.let { result.put("password", it) }
    }

    private fun applyDialTuning(
        outbound: JSONObject,
        settings: AppSettings,
        protocolHint: String = "",
        methodHint: String = ""
    ) {
        CoreSocketPolicy.writeSingBoxPhysicalDial(
            outbound,
            settings,
            protocolHint,
            methodHint
        )
    }

    private fun directOutbound(settings: AppSettings): JSONObject =
        JSONObject()
            .put("type", "direct")
            .put("tag", DIRECT_TAG)
            .apply { applyDialTuning(this, settings, protocolHint = "direct") }

    private fun dnsConfig(
        settings: AppSettings,
        profile: ProxyProfile,
        resolverPool: List<String>,
        underlayHasIpv6: Boolean,
        ruleSetPaths: Map<String, String>,
        usedRuleSets: MutableSet<String>
    ): JSONObject {
        val servers = JSONArray()
        val timeout = "${settings.singBoxConnectTimeoutSec.coerceIn(3, 20)}s"

        val candidates = if (resolverPool.isNotEmpty()) resolverPool else listOf(
            settings.dnsPrimaryDoH.ifBlank { "https://1.1.1.1/dns-query" },
            settings.dnsSecondaryDoH.ifBlank { "https://8.8.8.8/dns-query" },
            "tls://9.9.9.9"
        )

        val excluded = settings.measuredDnsExcludedEndpoints.split(',')
            .map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        fun allowed(url: String) = !ResolverEvidencePolicy.isDomesticResolver(url) && url.lowercase() !in excluded
        val filtered = candidates.map(String::trim).filter(String::isNotBlank).filter(::allowed).distinct()

        val pool = filtered.ifEmpty {
            listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query", "tls://9.9.9.9").filter(::allowed)
        }.take(3)
        require(pool.isNotEmpty()) { "No encrypted DNS resolver configured" }
        servers.put(JSONObject().put("type", "hosts").put("tag", DNS_HOSTS_TAG))

        if (FakeIpPolicy.isDnsPathArmed(settings)) {
            servers.put(JSONObject().put("type", "fakeip").put("tag", DNS_FAKEIP_TAG)
                .put("inet4_range", FAKE_IP_POOL).put("inet6_range", FakeIpPolicy.IPV6_POOL))
        }

        val bootstrapPeers = JSONArray()
        bootstrapDoH(settings, underlayHasIpv6).forEachIndexed { index, url ->
            val tag = "dns-bootstrap-$index"
            servers.put(encryptedDnsServer(tag, url, DIRECT_TAG))
            bootstrapPeers.put(tag)
        }
        require(bootstrapPeers.length() > 0) { "No encrypted DNS bootstrap on this address family" }
        servers.put(JSONObject().put("type", "fallback").put("tag", DNS_BOOTSTRAP_TAG)
            .put("servers", bootstrapPeers).put("strategy", "sequential").put("timeout", timeout))
        val remoteTags = JSONArray()
        pool.forEachIndexed { index, url ->
            val tag = "dns-remote-$index"
            servers.put(encryptedDnsServer(tag, url, PROXY_TAG))
            remoteTags.put(tag)
        }

        servers.put(JSONObject().put("type", "fallback").put("tag", DNS_DIRECT_TAG)
            .put("servers", JSONArray().put(DNS_BOOTSTRAP_TAG))
            .put("strategy", "sequential").put("timeout", timeout))

        val remoteStrategy = if (settings.adaptiveDnsEnabled && settings.measuredSingBoxDnsParallel) "parallel" else "sequential"
        servers.put(JSONObject().put("type", "fallback").put("tag", DNS_REMOTE_TAG)
            .put("servers", remoteTags).put("strategy", remoteStrategy).put("timeout", timeout))
        val rules = JSONArray()
        profile.host.takeIf { it.isNotBlank() && !isLiteralAddress(it) }?.let { host ->
            rules.put(JSONObject().put("domain", JSONArray().put(host)).put("action", "route").put("server", DNS_BOOTSTRAP_TAG))
        }

        if (FakeIpPolicy.isDnsPathArmed(settings)) {
            rules.put(
                JSONObject()
                    .put("query_type", JSONArray().put("A").put("AAAA"))
                    .put("action", "route")
                    .put("server", DNS_FAKEIP_TAG)
            )
        }
        val implicit = RoutingEngine.implicitRules(settings)
        val directSites = implicit.directSiteTags.filter { settings.iranDomesticDirect || it != "ir" }
        val directSiteSets = directSites.mapNotNull { tag ->
            geoTag(false, tag, ruleSetPaths)?.also { usedRuleSets += it }
        }
        if (directSiteSets.isNotEmpty()) {
            rules.put(JSONObject().put("rule_set", JSONArray(directSiteSets))
                .put("action", "route").put("server", DNS_DIRECT_TAG))
        }

        return JSONObject().put("servers", servers).put("rules", rules).put("final", DNS_REMOTE_TAG)
            .put("strategy", dnsStrategy(settings)).put("timeout", timeout).put("cache_capacity", 2048)
    }

    private fun isLiteralAddress(host: String): Boolean {
        val raw = host.trim().removePrefix("[").removeSuffix("]")
        return raw.contains(':') || raw.split('.').let { parts ->
            parts.size == 4 && parts.all { (it.toIntOrNull() ?: -1) in 0..255 }
        }
    }

    private fun encryptedDnsServer(tag: String, url: String, detour: String): JSONObject {
        val parsed = runCatching { URI(url) }.getOrElse { error("Invalid DNS resolver URL") }
        val type = parsed.scheme?.lowercase().orEmpty()
        require(type in setOf("https", "tls", "quic", "h3")) { "DNS must use HTTPS, TLS, QUIC or HTTP/3, not plaintext" }
        require(parsed.userInfo == null && parsed.fragment == null) { "DNS resolver URL must not contain user info or a fragment" }
        val host = parsed.host?.removePrefix("[")?.removeSuffix("]").orEmpty()
        require(host.isNotBlank()) { "DNS resolver URL has no host" }
        return JSONObject().put("type", type).put("tag", tag).put("server", host).put("detour", detour)
            .apply {
                if (parsed.port != -1) {
                    require(parsed.port in 1..65535) { "DNS resolver has an invalid port" }
                    put("server_port", parsed.port)
                }
                if (type == "https" || type == "h3") {
                    require(parsed.rawQuery == null) { "DNS resolver query parameters are not supported by the core's path field" }
                    put("path", parsed.rawPath?.takeIf { it.isNotBlank() && it != "/" } ?: "/dns-query")
                }

                if (!isLiteralAddress(host)) put("domain_resolver", DNS_BOOTSTRAP_TAG)
            }
    }

    internal fun bootstrapDoH(settings: AppSettings, underlayHasIpv6: Boolean): List<String> {
        val plan = AddressFamilyPolicy.plan(settings, underlayHasIpv6)
        val v6Allowed = underlayHasIpv6 && !plan.blockIpv6Traffic
        val v4Allowed = !plan.blockIpv4Traffic
        fun isV6(url: String): Boolean? {
            val host = runCatching { URI(url).host }.getOrNull()
                ?.removePrefix("[")?.removeSuffix("]").orEmpty()
            return if (host.isNotBlank() && isLiteralAddress(host) &&
                !ResolverEvidencePolicy.isDomesticResolver(url)) host.contains(':') else null
        }
        val user = listOf(settings.dnsPrimaryDoH, settings.dnsSecondaryDoH).map(String::trim)
            .filter { url -> isV6(url)?.let { if (it) v6Allowed else v4Allowed } == true }
        val stockV4 = if (v4Allowed) listOf(
            "https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query", "tls://9.9.9.9"
        ) else emptyList()
        val stockV6 = if (v6Allowed) listOf(
            "https://[2606:4700:4700::1111]/dns-query",
            "https://[2001:4860:4860::8888]/dns-query"
        ) else emptyList()
        val userV6 = user.filter { isV6(it) == true }
        val userV4 = user.filter { isV6(it) == false }
        val ordered = when {
            !v6Allowed -> userV4 + stockV4
            !v4Allowed -> userV6 + stockV6
            plan.prioritizeIpv6 -> userV6 + stockV6.take(1) + userV4 + stockV4 + stockV6
            else -> userV4 + stockV4.take(1) + userV6 + stockV6 + stockV4
        }
        return ordered.distinct().take(3)
    }

    private fun singBoxLogLevel(settings: AppSettings): String {
        val raw = settings.singBoxLogLevel.trim().lowercase()
        return if (raw in setOf("trace", "debug", "info", "warn", "error", "fatal", "panic")) raw else "warn"
    }

    private fun inboundConfig(settings: AppSettings, socksPort: Int): JSONArray {
        val listen = if (settings.singBoxAllowLan) "0.0.0.0" else "127.0.0.1"

        val users = inboundUsers(settings)
        val mixed = JSONObject()
            .put("type", "mixed")
            .put("tag", INBOUND_TAG)
            .put("listen", listen)
            .put("listen_port", socksPort)
            .put("tcp_fast_open", settings.tcpFastOpenEnabled)
        applyInboundSniff(mixed, settings)
        if (users != null) mixed.put("users", users)
        val inbounds = JSONArray().put(mixed)
        val httpPort = settings.singBoxHttpInboundPort
        if (httpPort in 1024..65535 && httpPort != socksPort) {
            val http = JSONObject()
                .put("type", "http")
                .put("tag", "http-in")
                .put("listen", listen)
                .put("listen_port", httpPort)
                .put("tcp_fast_open", settings.tcpFastOpenEnabled)
            applyInboundSniff(http, settings)
            if (users != null) http.put("users", JSONArray(users.toString()))
            inbounds.put(http)
        }
        return inbounds
    }

    private fun applyInboundSniff(inbound: JSONObject, settings: AppSettings) {
        // MARBLE_SINGBOX_SNIFF_V111 — inbound-level sniff and sniff_override_destination
        // were deprecated in sing-box 1.11.0 and removed in 1.13.0 (decode config error on inbounds[0]).
        // Packet sniffing is now handled via route.rules actions ("action": "sniff").
    }

    private fun inboundUsers(settings: AppSettings): JSONArray? {
        val username = settings.singBoxInboundUsername.trim()
        val password = settings.singBoxInboundPassword
        if (username.isBlank() || password.isBlank()) return null
        return JSONArray().put(JSONObject().put("username", username).put("password", password))
    }

    private fun dnsStrategy(settings: AppSettings): String =
        AddressFamilyPolicy.singBoxDestinationStrategy(settings)

    private fun physicalDomainResolver(settings: AppSettings, underlayHasIpv6: Boolean): JSONObject =
        JSONObject().put("server", DNS_BOOTSTRAP_TAG).put("strategy",
            AddressFamilyPolicy.singBoxEndpointStrategy(
                AddressFamilyPolicy.plan(settings, underlayHasIpv6)))

    private fun enforcePhysicalDialPolicy(root: JSONObject, settings: AppSettings, underlayHasIpv6: Boolean) {
        listOf("outbounds", "endpoints").forEach { section ->
            val entries = root.optJSONArray(section) ?: return@forEach
            for (index in 0 until entries.length()) {
                val outbound = entries.optJSONObject(index) ?: continue
                val type = outbound.optString("type")
                val host = outbound.optString("server").ifBlank { outbound.optString("address") }
                require(!AddressFamilyPolicy.excludedIpv4Endpoint(host, settings)) {
                    AddressFamilyPolicy.IPV4_LITERAL_DISABLED
                }
                if (type in setOf("block", "selector", "urltest")) continue

                if (host.isNotBlank() || type in setOf("parser", "direct") || outbound.has("domain_resolver")) {
                    outbound.put("domain_resolver", physicalDomainResolver(settings, underlayHasIpv6))
                }
            }
        }
    }

    private fun routeConfig(
        settings: AppSettings,
        notes: MutableList<String>,
        ruleSetPaths: Map<String, String>,
        usedRuleSets: MutableSet<String>,
        underlayHasIpv6: Boolean
    ): JSONObject {
        val rules = JSONArray()
        if (settings.singBoxSniffEnabled) {
            rules.put(JSONObject().put("action", "sniff"))
        }

        if (settings.singBoxResolveDestination || FakeIpPolicy.isDnsPathArmed(settings)) {
            rules.put(JSONObject().put("action", "resolve"))
        }
        if (settings.dnsHijackEnabled) {
            rules.put(JSONObject().put("port", 53).put("action", "hijack-dns"))
        }
        val destinationStrategy = dnsStrategy(settings)
        if (destinationStrategy == "ipv4_only") {
            rules.put(JSONObject().put("ip_cidr", JSONArray().put("::/0")).put("action", "reject"))
        }
        if (destinationStrategy == "ipv6_only") {
            rules.put(JSONObject().put("ip_cidr", JSONArray().put("0.0.0.0/0")).put("action", "reject"))
        }

        if (FakeIpPolicy.isDnsPathArmed(settings)) {
            rules.put(JSONObject().put("ip_cidr", JSONArray()
                .put(FakeIpPolicy.IPV4_POOL).put(FakeIpPolicy.IPV6_POOL))
                .put("action", "route").put("outbound", PROXY_TAG))
        }
        if (settings.iranModePolicy != IranModePolicy.OFF && settings.iranModeCountermeasures) {
            rules.put(JSONObject().put("ip_cidr", JSONArray(IRAN_POISON_BLOCK_IPS)).put("action", "reject"))
        }
        fun geoRule(ip: Boolean, tag: String, outbound: RoutingOutbound) {
            if (ip && tag.removePrefix("geoip:") == "private") {
                rules.put(action(JSONObject().put("ip_is_private", true), outbound))
            } else {
                val mapped = geoTag(ip, tag, ruleSetPaths)
                if (mapped == null) {
                    notes += unroutedGeoNote(ip, tag)
                    return
                }
                usedRuleSets += mapped
                rules.put(action(JSONObject().put("rule_set", JSONArray().put(mapped)), outbound))
            }
        }
        fun domainRules(raw: String, outbound: RoutingOutbound) {
            val fields = linkedMapOf<String, JSONArray>()
            splitTokens(raw).forEach { token ->
                // MARBLE_SINGBOX_GEO_ROUTING_V222 — the domain lists accept `geosite:` tokens on
                // Xray, so they have to mean the same thing here instead of becoming a domain
                // literally named "geosite:cn".
                when {
                    token.startsWith("geosite:", true) ||
                        token.startsWith("ext-site:", true) ||
                        token.startsWith("ext-domain:", true) -> {
                        geoRule(false, token, outbound)
                        return@forEach
                    }
                }
                val field = when {
                    token.startsWith("full:") -> "domain"
                    token.startsWith("regexp:") -> "domain_regex"
                    token.startsWith("keyword:") -> "domain_keyword"
                    else -> "domain_suffix"
                }
                val value = token.removePrefix("full:").removePrefix("domain:").removePrefix("regexp:").removePrefix("keyword:")
                fields.getOrPut(field) { JSONArray() }.put(value)
            }
            fields.forEach { (field, values) -> rules.put(action(JSONObject().put(field, values), outbound)) }
        }
        fun ipRule(raw: String, outbound: RoutingOutbound) {
            val values = splitTokens(raw).filterNot {
                it.startsWith("geoip:", true) ||
                    it.startsWith("ext-ip:", true)
            }
            splitTokens(raw)
                .filter { it.startsWith("geoip:", true) || it.startsWith("ext-ip:", true) }
                .forEach { geoRule(true, it, outbound) }
            if (values.isNotEmpty()) rules.put(action(JSONObject().put("ip_cidr", JSONArray(values)), outbound))
        }
        domainRules(settings.routeBlockDomains, RoutingOutbound.BLOCK)
        ipRule(settings.routeBlockIps, RoutingOutbound.BLOCK)
        domainRules(settings.routeProxyDomains, RoutingOutbound.PROXY)
        RoutingEngine.effectiveRules(settings).filter { it.enabled }.forEach { user ->
            val rule = JSONObject()
            when (user.kind) {
                RoutingRuleKind.DOMAIN -> {
                    val children = JSONArray()
                    splitTokens(user.matcher).forEach { token ->
                        val field = when {
                            token.startsWith("full:") -> "domain"
                            token.startsWith("regexp:") -> "domain_regex"
                            token.startsWith("domain:") -> "domain_suffix"
                            else -> "domain_keyword"
                        }
                        children.put(JSONObject().put(field, JSONArray().put(token.substringAfter(':', token))))
                    }
                    rule.put("type", "logical").put("mode", "or").put("rules", children)
                }
                RoutingRuleKind.IP -> rule.put("ip_cidr", JSONArray(splitTokens(user.matcher)))
                RoutingRuleKind.PORT -> putPorts(rule, user.matcher.ifBlank { user.port })
                RoutingRuleKind.GEOIP, RoutingRuleKind.GEOSITE -> {
                    if (user.kind == RoutingRuleKind.GEOIP && user.matcher.removePrefix("geoip:") == "private") {
                        rule.put("ip_is_private", true)
                    } else {
                        val mapped = geoTag(
                            user.kind == RoutingRuleKind.GEOIP,
                            user.matcher,
                            ruleSetPaths
                        )
                        if (mapped == null) {
                            notes += unroutedGeoNote(
                                user.kind == RoutingRuleKind.GEOIP,
                                user.matcher
                            )
                            return@forEach
                        }
                        usedRuleSets += mapped
                        rule.put("rule_set", JSONArray().put(mapped))
                    }
                }
            }
            val constraints = JSONObject()
            if (user.kind != RoutingRuleKind.PORT && user.port.isNotBlank()) putPorts(constraints, user.port)
            if (user.network.isNotBlank()) constraints.put("network", JSONArray(splitTokens(user.network)))
            if (user.protocol.isNotBlank()) constraints.put("protocol", JSONArray(splitTokens(user.protocol)))
            val effective = if (constraints.length() > 0) JSONObject().put("type", "logical").put("mode", "and")
                .put("rules", JSONArray().put(rule).put(constraints)) else rule
            rules.put(action(effective, user.outbound))
        }

        applyPrecisionRules(rules, settings)
        val implicit = RoutingEngine.implicitRules(settings)
        implicit.adsTag?.let { geoRule(false, it, RoutingOutbound.BLOCK) }
        if (settings.routeBypassPrivate || implicit.forceBypassPrivate) {
            rules.put(action(JSONObject().put("ip_is_private", true), RoutingOutbound.DIRECT))
        }
        implicit.directIpTags.filter { settings.iranDomesticDirect || it != "ir" }
            .forEach { geoRule(true, it, RoutingOutbound.DIRECT) }
        implicit.directSiteTags.filter { settings.iranDomesticDirect || it != "ir" }
            .forEach { geoRule(false, it, RoutingOutbound.DIRECT) }
        domainRules(settings.routeDirectDomains, RoutingOutbound.DIRECT)
        ipRule(settings.routeDirectIps, RoutingOutbound.DIRECT)
        val ruleSets = JSONArray()
        usedRuleSets.sorted().forEach { tag ->
            val path = ruleSetPaths[tag]
            ruleSets.put(
                JSONObject()
                    .put("type", "local")
                    .put("tag", tag)
                    .put("format", path?.let(SingBoxGeoRuleSets::formatOf) ?: "binary")
                    .put("path", path ?: "singbox-rules/$tag.srs")
            )
        }
        return JSONObject().put("rules", rules).put("rule_set", ruleSets).put("final", PROXY_TAG)
            .put("default_domain_resolver", physicalDomainResolver(settings, underlayHasIpv6))
            .put("default_http_client", HTTP_CLIENT_DIRECT_TAG)
    }

    /**
     * MARBLE_SINGBOX_GEO_ROUTING_V222 — resolves any geo tag that the prepared rule-set map holds.
     *
     * The map is the bundled set (`geoip-ir`, `geosite-ir`, the ads bundle) plus every tag
     * `SingBoxGeoRuleSets` generated from the pinned .dat files for this configuration. A tag the
     * user can pick in Routing — `cn`, `ru`, `category-bank-ir`, `category-gov-ir`, or one from a
     * custom source — therefore resolves here instead of being dropped with a note.
     */
    internal fun geoTag(ip: Boolean, raw: String): String? = bundledGeoTag(ip, raw)

    internal fun geoTag(ip: Boolean, raw: String, ruleSetPaths: Map<String, String>): String? {
        if (ip && SingBoxGeoRuleSets.canonicalTag(raw) == "private") return RULE_SET_GEOIP_PRIVATE
        val kind = if (ip) GeoAssetIndex.Kind.GEOIP else GeoAssetIndex.Kind.GEOSITE
        val canonical = SingBoxGeoRuleSets.idFor(kind, raw)
        if (ruleSetPaths.containsKey(canonical)) return canonical
        // A generated set exists only when the tag was in the local .dat files; when the caller
        // holds no map at all the historical bundled mapping still applies unchanged.
        val bundled = bundledGeoTag(ip, raw)
        return when {
            bundled == null -> null
            ruleSetPaths.isEmpty() || ruleSetPaths.containsKey(bundled) -> bundled
            else -> null
        }
    }

    private fun bundledGeoTag(ip: Boolean, raw: String): String? {
        val tag = raw.trim().lowercase().removePrefix("geoip:").removePrefix("geosite:")
        return when {
            ip && tag == "private" -> RULE_SET_GEOIP_PRIVATE
            ip && tag == "ir" -> RULE_SET_GEOIP_IR
            !ip && tag in setOf("ir", "category-ir") -> RULE_SET_GEOSITE_IR
            !ip && tag in setOf("ads", "category-ads-all", "geosite-ads", "ads-all") -> RULE_SET_ADS
            else -> null
        }
    }

    private fun action(rule: JSONObject, outbound: RoutingOutbound): JSONObject = rule.apply {
        if (outbound == RoutingOutbound.BLOCK) put("action", "reject")
        else put("action", "route").put("outbound", if (outbound == RoutingOutbound.DIRECT) DIRECT_TAG else PROXY_TAG)
    }

    private fun applyPrecisionRules(rules: JSONArray, settings: AppSettings) {
        if (!RoutingEngine.precisionActive(settings)) return
        val precision = parseGeoPrecision(settings.geoPrecision)
        val suffixes = IranPrecisionPack.suffixes(precision) + IranPrecisionPack.domains(precision)
        val keywords = IranPrecisionPack.keywords(precision)
        suffixes.chunked(RoutingEngine.PRECISION_RULE_CHUNK).forEach { chunk ->
            rules.put(
                action(
                    JSONObject().put("domain_suffix", JSONArray(chunk)),
                    RoutingOutbound.DIRECT
                )
            )
        }
        keywords.chunked(RoutingEngine.PRECISION_RULE_CHUNK).forEach { chunk ->
            rules.put(
                action(
                    JSONObject().put("domain_keyword", JSONArray(chunk)),
                    RoutingOutbound.DIRECT
                )
            )
        }
    }

    private fun unroutedGeoNote(ip: Boolean, raw: String): String {
        val typed = if (raw.contains(":")) raw else if (ip) "geoip:$raw" else "geosite:$raw"
        return "routing: '$typed' is not in the local geo assets and has no bundled sing-box rule " +
            "set, so this one rule is not applied on the sing-box engine (download the geo assets " +
            "in Routing, or the same profile is routed in full on Xray)."
    }

    private fun putPorts(rule: JSONObject, raw: String) {
        val ports = JSONArray()
        val ranges = JSONArray()
        splitTokens(raw).forEach { value ->
            val port = value.toIntOrNull()
            if (port != null) {
                require(port in 1..65535) { "Invalid route port" }
                ports.put(port)
            } else {
                val parts = value.replace(':', '-').split('-')
                require(parts.size == 2 && parts.all { (it.toIntOrNull() ?: -1) in 1..65535 } && parts[0].toInt() <= parts[1].toInt()) { "Invalid route port range" }
                ranges.put("${parts[0]}:${parts[1]}")
            }
        }
        if (ports.length() > 0) rule.put("port", ports)
        if (ranges.length() > 0) rule.put("port_range", ranges)
    }

    private fun splitTokens(raw: String): List<String> = raw.split(',', '|', '\n', ';')
        .map(String::trim).filter(String::isNotBlank)
}
