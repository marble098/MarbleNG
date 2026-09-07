package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRuleKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * MARBLE_SINGBOX_CORE_V151 / MARBLE_SINGBOX_AUTOPARSER_V154 — the sing-box extended
 * configuration writer and the automatic Xray → sing-box converter.
 *
 * MarbleNG stores every node as Xray JSON ([ProxyProfile.configJson]) because that is the format
 * the whole product was built around. sing-box has its own schema, so this object is the single
 * bridge between the two. V154 made the bridge *automatic*: [candidateBuilds] emits every config
 * shape a node can be (native `parser` outbound + Marble's own translation, in preference
 * order), and the manager runs each one under `sing-box check` until one is accepted — a node
 * never dies because one reader had a blind spot. The two strategies:
 *
 *  1. **[STRATEGY_LINK]** — the profile still carries its original share link, and sing-box
 *     extended ships a native `parser` outbound that understands exactly that link. Handing the
 *     link to the core is strictly better than re-deriving it: the core's own parser owns every
 *     parameter the link can express, so nothing MarbleNG does not know about is silently dropped.
 *  2. **[STRATEGY_TRANSLATED]** — a pasted Xray JSON, a stored node, or a link the parser
 *     outbound refuses is translated hop by hop into sing-box outbounds. The converter reads
 *     every shape MarbleNG can hold: `vnext`-array AND direct-form VLESS/VMess settings, Trojan,
 *     Shadowsocks (+ SIP002 `uot`), SOCKS/HTTP, Hysteria v1/v2 with salamander obfs, WireGuard,
 *     and Raw/WS/gRPC/H2/HTTPUpgrade/XHTTP transports, with TLS/Reality/ECH and Mux carried
 *     over. Chains survive: an Xray `dialerProxy` becomes a sing-box `detour`. TUIC/AnyTLS nodes
 *     are translated directly from their raw share link.
 *
 * Everything around the proxy hop is written by MarbleNG for both strategies: the local `mixed`
 * inbound that hev-socks5-tunnel dials, the DNS graph, the routing rules, and the Clash API that
 * the native URL test drives.
 */
object SingBoxConfigBuilder {

    const val INBOUND_TAG = "socks-in"
    const val PROXY_TAG = "marble-proxy"
    const val DIRECT_TAG = "direct"
    const val BLOCK_TAG = "block"
    const val DNS_REMOTE_TAG = "dns-remote"
    const val DNS_DIRECT_TAG = "dns-direct"
    const val DNS_LOCAL_TAG = "dns-local"
    const val DNS_HOSTS_TAG = "dns-hosts"

    const val STRATEGY_LINK = "link-parser"
    const val STRATEGY_TRANSLATED = "translated"

    /**
     * The sing-box rule sets MarbleNG uses. They are the MetaCubeX sing-box compilations — the
     * same Iran/ads data the Xray engine consumes as `geoip.dat`/`geosite.dat`, published in the
     * `.srs` format sing-box reads. `download_detour: direct` matters: a fresh install has no
     * working tunnel yet when the first rule set is fetched.
     */
    private const val RULE_SET_BASE =
        "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/sing/geo"
    private const val RULE_SET_MIRROR =
        "https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@sing/geo"

    const val RULE_SET_GEOIP_IR = "geoip-ir"
    const val RULE_SET_GEOSITE_IR = "geosite-ir"
    const val RULE_SET_GEOIP_PRIVATE = "geoip-private"
    const val RULE_SET_ADS = "geosite-ads"

    /** Schemes sing-box extended's own `parser` outbound can read. */
    private val LINK_SCHEMES = setOf(
        "vless", "vmess", "trojan", "ss", "hysteria", "hy2", "hysteria2", "tuic", "anytls"
    )

    /**
     * Protocols the JSON translator knows — every client shape Xray-core can express that
     * sing-box carries a native outbound for:
     *
     *  - `vless`/`vmess` in BOTH Xray forms (`settings.vnext[].users[]` and the simplified
     *    direct form `settings.{address,port,id,…}` that modern Xray also accepts, which is the
     *    shape MarbleNG's own share-link parser stores). The old translator only read `vnext`,
     *    so every node MarbleNG itself had imported — and every PattNG-style subscription —
     *    failed translation with "no vnext server" and was silently downgraded to the native
     *    link parser instead of being converted.
     *  - `trojan`, `shadowsocks` (incl. SIP002 `uot` → `udp_over_tcp`), `socks`, `http`.
     *  - `hysteria` v1 (`auth_str`) and v2 (`password` + salamander `obfs` from the stored
     *    `finalmask`, which the old writer never looked at — obfs nodes therefore connected
     *    without their obfuscation on the sing-box engine and died at the first QUIC packet).
     *  - `wireguard` — full Warp-style configs (secret key, peer, reserved, local addresses).
     *
     * For the two protocols that have no Xray outbound shape at all (`tuic`, `anytls`) Marble's
     * importer keeps the raw link instead of a config; those are translated straight from the
     * link, so a subscription server works even when the core's own parser is a version behind.
     */
    private val TRANSLATABLE_PROTOCOLS = setOf(
        "vless", "vmess", "trojan", "shadowsocks", "socks", "http",
        "hysteria2", "hysteria", "wireguard", "tuic", "anytls"
    )

    data class Support(
        val supported: Boolean,
        val strategy: String,
        val reason: String,
        val notes: List<String>
    )

    data class Build(val json: String, val strategy: String, val notes: List<String>)

    // ─────────────────────────────────────────────────────────────────────────────
    // Capability probe
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Decides, before any process is spawned, whether this profile can run on sing-box extended.
     *
     * This is a *promise the user can read*: the Engine page shows the reason verbatim, and the
     * connect path refuses with the same text instead of letting the core print a schema error
     * nobody can act on. It never throws.
     */
    fun describe(profile: ProxyProfile, settings: AppSettings): Support {
        val notes = mutableListOf<String>()

        if (profile.scheme.equals("ssh", ignoreCase = true)) {
            return Support(
                supported = false,
                strategy = "",
                reason = "SSH chains are bridged by the Xray engine.",
                notes = notes
            )
        }

        // MARBLE_SINGBOX_CORE_V151 — the link parser is preferred, but it is a preference, not a
        // law. With the preference on, a profile that carries a share link is handed to the core's
        // own reader, which understands link syntax Marble does not have to be updated for. With
        // it off, Marble translates the stored config itself — and the parser still catches every
        // profile that cannot be translated, because refusing a node Marble could have run is
        // worse than running it the other way round.
        val link = shareLink(profile)
        if (settings.singBoxPreferParser && link != null) {
            return Support(true, STRATEGY_LINK, "", notes)
        }
        fun viaParser(why: String): Support? = link?.let {
            Support(true, STRATEGY_LINK, "", notes + "$why The core's link parser runs this node.")
        }

        // TUIC / AnyTLS carry no stored Xray config by design — Marble translates the raw share
        // link itself, so those nodes survive even a version-stale core parser.
        val linkOnlyScheme = profile.scheme.lowercase()
        if (profile.configJson.isBlank() && linkOnlyScheme in setOf("tuic", "anytls")) {
            if (link != null) return Support(true, STRATEGY_TRANSLATED, "", notes)
            return viaParser("This node has no stored config to translate.")
                ?: Support(false, "", "This node has no stored config to translate.", notes)
        }

        val root = runCatching { JSONObject(profile.configJson) }.getOrNull()
            ?: return viaParser("The stored config is not readable JSON.")
                ?: Support(false, "", "The stored config is not readable JSON.", notes)

        val outbounds = root.optJSONArray("outbounds")
            ?: return viaParser("The stored config has no outbounds.")
                ?: Support(false, "", "The stored config has no outbounds.", notes)

        val entry = firstProxyOutbound(outbounds)
            ?: return viaParser("The stored config has no proxy outbound.")
                ?: Support(false, "", "The stored config has no proxy outbound.", notes)

        val protocol = entry.optString("protocol").lowercase()
        if (protocol !in TRANSLATABLE_PROTOCOLS) {
            return viaParser("sing-box extended has no $protocol client in this profile's shape.")
                ?: Support(
                    supported = false,
                    strategy = "",
                    reason = "sing-box extended has no $protocol client in this profile's shape. " +
                        "Use the Xray engine for this node.",
                    notes = notes
                )
        }

        // Xray-only knobs. Reporting them is honest; pretending they were applied is not.
        if (settings.fragmentEnabled) {
            notes += "Fragmentation is mapped to sing-box TLS fragmentation; the Xray " +
                "packet/length/interval shaping is Xray-only."
        }
        if (hasCertificatePinning(entry)) {
            notes += "This node pins a peer certificate. sing-box verifies against the system " +
                "trust store instead; use the Xray engine if pinning is required."
        }

        return Support(true, STRATEGY_TRANSLATED, "", notes)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Configuration
    // ─────────────────────────────────────────────────────────────────────────────

    fun build(
        profile: ProxyProfile,
        settings: AppSettings,
        socksPort: Int,
        apiPort: Int,
        apiSecret: String,
        logPath: String,
        cachePath: String,
        /**
         * MARBLE_SINGBOX_PROTOCOLS_V153 — extra encrypted resolver URLs Marble Intelligence has
         * measured or selected. The first entry is the remote resolver through the tunnel, the
         * second is the direct resolver, and any remaining entries become additional remote DoH
         * servers that are still present in the config even when the user's primary pair is
         * demoted. The old writer only emitted the two configured literals, which is how every
         * stock DoH endpoint could be demoted together and leave sing-box with no healthy
         * encrypted resolver at all.
         */
        resolverPool: List<String> = emptyList()
    ): Build {
        val support = describe(profile, settings)
        require(support.supported) { support.reason.ifBlank { "sing-box cannot run this profile" } }
        return write(profile, settings, support, null, socksPort, apiPort, apiSecret, logPath, cachePath, resolverPool)
    }

    /**
     * MARBLE_SINGBOX_AUTOPARSER_V154 — every config this profile can become, best first.
     *
     * One link on the wire can parse three ways — the core's native `parser` outbound, Marble's
     * own share-link parser (the stored Xray JSON), or a link the stored config no longer
     * resembles. Single-strategy building made one reader's blind spot fatal: a node the core
     * parser choked on could fail the whole URL test even when Marble's own translation was
     * letter-perfect. The manager therefore walks this list, `sing-box check`-ing each candidate
     * and keeping the first one the core accepts — fully automatic, no setting to learn.
     *
     * Order honours [AppSettings.singBoxPreferParser]: the upstream parser first when preferred
     * (it understands link syntax Marble does not have to be updated for), Marble's translation
     * first otherwise. Both readers are always in the list whenever both exist.
     */
    fun candidateBuilds(
        profile: ProxyProfile,
        settings: AppSettings,
        socksPort: Int,
        apiPort: Int,
        apiSecret: String,
        logPath: String,
        cachePath: String,
        resolverPool: List<String> = emptyList()
    ): List<Build> {
        val support = describe(profile, settings)
        if (!support.supported) return emptyList()
        val order = if (settings.singBoxPreferParser) {
            listOf(STRATEGY_LINK, STRATEGY_TRANSLATED)
        } else {
            listOf(STRATEGY_TRANSLATED, STRATEGY_LINK)
        }
        return order.mapNotNull { strategy ->
            runCatching {
                write(
                    profile, settings, support, strategy,
                    socksPort, apiPort, apiSecret, logPath, cachePath, resolverPool
                )
            }.getOrNull()
        }.distinctBy { it.strategy }
    }

    /**
     * The full config for one explicit strategy. [forcedStrategy] == null means "the strategy
     * [Support.strategy] selected"; anything else is attempted and simply fails (throws) when the
     * profile cannot be expressed that way — a link-less node cannot run the parser outbound, an
     * untranslatable protocol cannot be translated.
     */
    private fun write(
        profile: ProxyProfile,
        settings: AppSettings,
        support: Support,
        forcedStrategy: String?,
        socksPort: Int,
        apiPort: Int,
        apiSecret: String,
        logPath: String,
        cachePath: String,
        resolverPool: List<String>
    ): Build {
        val strategy = forcedStrategy ?: support.strategy
        val notes = support.notes.toMutableList()
        val outbounds = JSONArray()

        when (strategy) {
            STRATEGY_LINK -> {
                val link = shareLink(profile)
                    ?: error("This node carries no share link to hand to the core parser")
                outbounds.put(
                    JSONObject()
                        .put("type", "parser")
                        .put("tag", PROXY_TAG)
                        .put("link", link)
                        .apply { applyDialTuning(this, settings) }
                )
            }
            else -> {
                val translated = translate(profile, settings, notes)
                translated.forEach { outbounds.put(it) }
            }
        }

        // MARBLE_SINGBOX_DNS_ACTION_V152 — no `dns` outbound is written any more. sing-box
        // deprecated it in 1.11.0 and REMOVED it in 1.13.0 ("dns outbound is deprecated in
        // sing-box 1.11.0 and removed in sing-box 1.13.0, use rule actions instead"), and the
        // pinned extended core (v1.14.x) rejects the whole config for carrying one — which is
        // exactly how every profile in the shipped build died at `sing-box check` and pushed the
        // session into BLOCKED. The route's `hijack-dns` rule action written below is the
        // replacement the error message itself names, and it is all DNS interception needs: the
        // mixed inbound's port-53 traffic is sniffed, matched by the `protocol: dns` rule and
        // answered by the dns module through the configured servers.
        outbounds
            .put(JSONObject().put("type", "direct").put("tag", DIRECT_TAG))
            .put(JSONObject().put("type", "block").put("tag", BLOCK_TAG))

        val root = JSONObject()
            .put(
                "log",
                JSONObject()
                    .put("level", "warn")
                    .put("output", logPath)
                    .put("timestamp", true)
            )
            .put("dns", dnsConfig(settings, profile, resolverPool))
            .put(
                "inbounds",
                JSONArray().put(
                    JSONObject()
                        .put("type", "mixed")
                        .put("tag", INBOUND_TAG)
                        .put("listen", "127.0.0.1")
                        .put("listen_port", socksPort)
                        .put("tcp_fast_open", settings.tcpFastOpenEnabled)
                )
            )
            .put("outbounds", outbounds)
            .put("route", routeConfig(settings, notes))
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
                    // Unified delay measures a real round trip instead of trusting a cached
                    // handshake, which is what makes the native URL test comparable to the
                    // MarbleNG real-delay measurement.
                    .put("unified_delay", JSONObject().put("enabled", settings.singBoxUnifiedDelay))
                    .apply {
                        // MARBLE_SINGBOX_CORE_V151 — the cache file is a user decision, so the
                        // key is only written when they asked for it. sing-box treats a missing
                        // `cache_file` block as "no cache", which is exactly the promise.
                        if (settings.singBoxCacheFile) {
                            put(
                                "cache_file",
                                JSONObject()
                                    .put("enabled", true)
                                    .put("path", cachePath)
                                    .put("store_rdrc", true)
                            )
                        }
                    }
            )

        return Build(root.toString(), strategy, notes)
    }

    /**
     * The local SOCKS endpoint the URL test measures through, in the same `host:port` form the
     * rest of the product uses for the Xray engine.
     */
    fun localProxyAddress(port: Int): String = "127.0.0.1:$port"

    // ─────────────────────────────────────────────────────────────────────────────
    // Strategy 1 — the native link parser
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * The share link this profile was created from, when there is exactly one and sing-box's own
     * parser understands the scheme. A subscription blob or a pasted JSON is not a link.
     *
     * MARBLE_SINGBOX_PROTOCOLS_V153 — the previous guard rejected *any* whitespace in the raw
     * link. Proxy links routinely carry a display-name fragment such as `#NL - Server A`, and
     * that single unencoded space made Marble refuse to hand the link to the core's own parser,
     * dropping the profile onto the translation path. Translation is still fixes, but handing the
     * link to the parser preserves every parameter the share link can express. The guard now only
     * rejects a multiline blob; a single-line link is passed through even when its fragment
     * contains spaces.
     */
    fun shareLink(profile: ProxyProfile): String? {
        val raw = profile.raw.trim()
        if (raw.isEmpty() || raw.length > 4096 || raw.contains('\n')) return null
        val scheme = raw.substringBefore("://", "").lowercase()
        if (scheme !in LINK_SCHEMES) return null
        return raw
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Strategy 2 — Xray JSON → sing-box JSON
    // ─────────────────────────────────────────────────────────────────────────────

    private fun firstProxyOutbound(outbounds: JSONArray): JSONObject? {
        for (i in 0 until outbounds.length()) {
            val candidate = outbounds.optJSONObject(i) ?: continue
            val protocol = candidate.optString("protocol").lowercase()
            if (protocol in setOf("freedom", "blackhole", "dns", "loopback")) continue
            return candidate
        }
        return null
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

    /**
     * MARBLE_SINGBOX_AUTOPARSER_V154 — the automatic Xray → sing-box converter.
     *
     * Walks the Xray chain (`dialerProxy` / `proxySettings`) from the entry hop outwards and
     * returns the equivalent sing-box outbounds, each one detouring into the next. Both Xray
     * settings shapes are read — the classic `vnext`/`servers` arrays and the simplified direct
     * form (`settings.{address,port,id}`) that modern Xray accepts and MarbleNG's own share-link
     * parser stores — so every node from a subscription converts instead of falling over at the
     * first missing `vnext` array. TUIC and AnyTLS nodes carry no stored Xray config at all;
     * their outbound is translated straight from the raw share link.
     */
    private fun translate(profile: ProxyProfile, settings: AppSettings, notes: MutableList<String>): List<JSONObject> {
        if (profile.configJson.isBlank()) {
            return listOf(
                linkOutbound(profile, settings, notes)
                    ?: error("This node has no stored config and its link is not translatable")
            )
        }
        val root = runCatching { JSONObject(profile.configJson) }.getOrNull()
            ?: error("The stored config is not readable JSON")
        val outbounds = root.getJSONArray("outbounds")
        val entry = firstProxyOutbound(outbounds) ?: error("no proxy outbound")

        val chain = mutableListOf<JSONObject>()
        var current: JSONObject? = entry
        var guard = 0
        while (current != null && guard++ < 8) {
            chain += current
            val nextTag = current.optJSONObject("proxySettings")?.optString("tag").orEmpty()
                .ifBlank {
                    current.optJSONObject("streamSettings")
                        ?.optJSONObject("sockopt")
                        ?.optString("dialerProxy")
                        .orEmpty()
                }
            current = outboundByTag(outbounds, nextTag)
        }
        if (chain.size > 1) {
            notes += "Chained through ${chain.size - 1} extra hop(s)."
        }

        return chain.mapIndexed { index, hop ->
            val tag = if (index == 0) PROXY_TAG else "marble-hop-$index"
            val detour = if (index == 0) null else "marble-hop-${index - 1}"
            translateHop(hop, tag, detour, settings, notes)
        }
    }

    private fun translateHop(
        outbound: JSONObject,
        tag: String,
        detour: String?,
        settings: AppSettings,
        notes: MutableList<String>
    ): JSONObject {
        val protocol = outbound.optString("protocol").lowercase()
        val xraySettings = outbound.optJSONObject("settings") ?: JSONObject()
        val stream = outbound.optJSONObject("streamSettings") ?: JSONObject()
        val result = JSONObject().put("tag", tag)

        when (protocol) {
            "vless", "vmess" -> {
                // MARBLE_SINGBOX_AUTOPARSER_V154 — Xray has TWO legitimate ways to name the same
                // node in `settings`: the `vnext[0].users[0]` arrays and the simplified direct
                // form (`address`, `port`, `id`, …) that Xray-core `conf` accepts and that
                // MarbleNG's own share-link parser stores. The old translator only read `vnext`,
                // so a node imported from any PattNG/Xray-style subscription could not be
                // converted at all. User fields are resolved per field with the same precedence.
                val vnext = xraySettings.optJSONArray("vnext")?.optJSONObject(0)
                val user = vnext?.optJSONArray("users")?.optJSONObject(0)
                result.put("type", protocol)
                result.put(
                    "server",
                    vnext?.optString("address")?.takeIf { it.isNotBlank() }
                        ?: xraySettings.optString("address").takeIf { it.isNotBlank() }
                        ?: error("$protocol outbound has no server address")
                )
                result.put(
                    "server_port",
                    (vnext?.optInt("port", 0) ?: 0).takeIf { it > 0 }
                        ?: xraySettings.optInt("port", 0).takeIf { it > 0 }
                        ?: error("$protocol outbound has no server port")
                )
                result.put(
                    "uuid",
                    user?.optString("id")?.takeIf { it.isNotBlank() }
                        ?: xraySettings.optString("id")
                )
                if (protocol == "vless") {
                    (user?.optString("flow")?.takeIf { it.isNotBlank() }
                        ?: xraySettings.optString("flow").takeIf { it.isNotBlank() })
                        ?.let { result.put("flow", it) }
                    (user?.optString("encryption")?.takeIf { it.isNotBlank() }
                        ?: xraySettings.optString("encryption").takeIf { it.isNotBlank() })
                        ?.let { result.put("encryption", it) }
                } else {
                    result.put(
                        "security",
                        user?.optString("security")?.takeIf { it.isNotBlank() }
                            ?: xraySettings.optString("security").ifBlank { "auto" }
                    )
                    val alterId = when {
                        user != null && user.has("alterId") -> user.optInt("alterId", 0)
                        else -> xraySettings.optInt("alterId", 0)
                    }
                    if (alterId > 0) result.put("alter_id", alterId)
                    if (user?.optBoolean("globalPadding", false) == true ||
                        xraySettings.optBoolean("globalPadding", false)
                    ) {
                        result.put("global_padding", true)
                    }
                    // VMess UDP-over-TCP: Xray's `packetEncoding` becomes sing-box
                    // `packet_encoding` (`packetaddr`/`xudp`); without it an XUDP node would lose
                    // UDP entirely on the sing-box engine.
                    val packetEncoding = sequenceOf(
                        user?.optString("packetEncoding"),
                        xraySettings.optString("packetEncoding")
                    ).firstOrNull { !it.isNullOrBlank() }
                    when (packetEncoding?.lowercase()) {
                        "packet" -> result.put("packet_encoding", "packetaddr")
                        "xudp" -> result.put("packet_encoding", "xudp")
                    }
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
                // SIP002 `uot` — UDP over TCP. Xray spells it `uot`; sing-box wants the object.
                val uot = server.opt("uot")
                val uotOn = when (uot) {
                    is Boolean -> uot
                    is JSONObject -> uot.optBoolean("enabled", true)
                    else -> uot?.toString()?.let { it.equals("true", true) || it == "1" } == true
                }
                if (uotOn) result.put("udp_over_tcp", JSONObject().put("enabled", true))
            }

            // MARBLE_SINGBOX_AUTOPARSER_V154 — WireGuard (Mullvad/Warp-style Xray configs).
            // Xray: settings.{secretKey, address[], mtu, workers, reserved, peers[]}.
            // sing-box: {type wireguard, local_address[], private_key, peer_public_key,
            //            pre_shared_key, server, server_port, reserved, mtu, workers}.
            "wireguard" -> {
                val peers = xraySettings.optJSONArray("peers")?.optJSONObject(0)
                    ?: error("wireguard outbound has no peer")
                val endpoint = peers.optString("endpoint").takeIf { it.isNotBlank() }
                    ?: error("wireguard outbound has no endpoint")
                val (endpointHost, endpointPort) = splitHostPort(endpoint)
                if (endpointPort !in 1..65535) error("wireguard endpoint has no port")

                val localAddresses = JSONArray()
                run {
                    val raw = xraySettings.opt("address")
                    when (raw) {
                        is JSONArray -> (0 until raw.length()).forEach { index ->
                            raw.optString(index).takeIf { it.isNotBlank() }?.let(localAddresses::put)
                        }
                        is String -> raw.takeIf { it.isNotBlank() }?.let(localAddresses::put)
                    }
                }
                if (localAddresses.length() == 0) error("wireguard outbound has no local address")

                result.put("type", "wireguard")
                result.put("server", endpointHost)
                result.put("server_port", endpointPort)
                result.put("local_address", localAddresses)
                result.put("private_key", xraySettings.optString("secretKey"))
                peers.optString("publicKey").takeIf { it.isNotBlank() }
                    ?.let { result.put("peer_public_key", it) }
                peers.optString("preSharedKey").takeIf { it.isNotBlank() }
                    ?.let { result.put("pre_shared_key", it) }
                // keepAlive has no sing-box outbound field (it belongs to the tun inbound); the
                // NAT is kept warm by the server side instead, so it is intentionally not mapped.
                xraySettings.optInt("mtu", 0).takeIf { it > 0 }?.let { result.put("mtu", it) }
                xraySettings.optInt("workers", 0).takeIf { it > 0 }?.let { result.put("workers", it) }
                xraySettings.opt("reserved")?.let { reserved ->
                    when (reserved) {
                        is JSONArray -> if (reserved.length() > 0) result.put("reserved", reserved)
                        is String -> reserved.takeIf { it.isNotBlank() }?.let {
                            result.put("reserved", it)
                        }
                    }
                }
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
                    xraySettings.optString("address"),
                    // Some exporters spell the endpoint `server`/`server_address`; read them too.
                    xraySettings.optString("server"),
                    xraySettings.optString("server_address")
                ).firstOrNull { !it.isNullOrBlank() }
                    ?: error("$protocol outbound has no address")
                val port = sequenceOf<Int?>(
                    server?.optInt("port", 0),
                    xraySettings.optInt("port", 0)
                ).firstOrNull { it != null && it > 0 } ?: error("$protocol outbound has no port")
                // MARBLE_SINGBOX_AUTOPARSER_V154 — the protocol NAME is ambiguous: Marble's own
                // hy2 importer stores `protocol: "hysteria"` with `settings.version: 2`, exactly
                // like a pasted Xray v1 config that never carries a version field. The explicit
                // version wins; the name is only the tiebreak. The old `protocol == "hysteria" →
                // v1` rule mis-typed every stored hy2 node as v1 and failed its auth.
                val declaredVersion = sequenceOf(
                    server?.optInt("version", 0),
                    xraySettings.optInt("version", 0)
                ).firstOrNull { it == 1 || it == 2 }
                val version = declaredVersion ?: if (protocol == "hysteria") 1 else 2
                // MARBLE_SINGBOX_PROTOCOLS_V153 — Hysteria v1 and v2 are different sing-box
                // outbound types. The old writer always typed `hysteria2` and read `password` on
                // the Xray v1 settings (which use `auth_str`), so a Hysteria v1 node ran as a v2
                // auth mismatch and died at the first QUIC handshake.
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
                // Rates arrive as ints, plain digit strings, or human text ("100 Mbps") — every
                // form a hy2 link carries. sing-box hysteria v1 REQUIRES both; missing values get
                // the library defaults so a rate-less node still builds.
                fun rate(vararg raw: String?): Int? = raw.asSequence()
                    .filterNotNull()
                    .map { Regex("\\d+").find(it)?.value?.toIntOrNull() }
                    .firstOrNull { it != null && it > 0 }
                val up = rate(
                    server?.opt("up_mbps")?.toString(),
                    hySettings?.opt("up_mbps")?.toString(),
                    hySettings?.optString("up")
                )
                val down = rate(
                    server?.opt("down_mbps")?.toString(),
                    hySettings?.opt("down_mbps")?.toString(),
                    hySettings?.optString("down")
                )
                if (version == 1) {
                    result.put("up_mbps", up ?: 10)
                    result.put("down_mbps", down ?: 50)
                } else {
                    if (up != null) result.put("up_mbps", up)
                    if (down != null) result.put("down_mbps", down)
                }

                // MARBLE_SINGBOX_AUTOPARSER_V154 — salamander/gecko obfuscation. Marble's own
                // importer stores it in Xray's `finalmask.udp[0]` shape; Xray JSON from other
                // clients puts it in `settings.obfs`. The old writer read NEITHER for stored
                // nodes, so obfs nodes connected un-obfuscated on the sing-box engine and died.
                var obfsType = ""
                var obfsPassword = ""
                server?.optJSONObject("obfs")?.let { obfs ->
                    obfsType = obfs.optString("type")
                    obfsPassword = obfs.optString("password").ifBlank { obfs.optString("obfs") }
                }
                if (obfsPassword.isBlank()) {
                    xraySettings.optJSONObject("obfs")?.let { obfs ->
                        obfsType = obfs.optString("type").ifBlank { obfsType }
                        obfsPassword = obfs.optString("password").ifBlank { obfs.optString("obfs") }
                    }
                }
                if (obfsPassword.isBlank()) {
                    stream.optJSONObject("finalmask")?.optJSONArray("udp")?.optJSONObject(0)
                        ?.let { mask ->
                            obfsType = mask.optString("type").ifBlank { obfsType }
                            val maskSettings = mask.optJSONObject("settings") ?: mask
                            obfsPassword = maskSettings.optString("password")
                        }
                }
                if (obfsPassword.isBlank()) {
                    hySettings?.optJSONObject("obfs")?.let { obfs ->
                        obfsType = obfs.optString("type").ifBlank { obfsType }
                        obfsPassword = obfs.optString("password").ifBlank { obfs.optString("obfs") }
                    }
                }
                if (obfsPassword.isNotBlank() || obfsType.equals("salamander", true)) {
                    if (version == 1) {
                        // Hysteria v1's `obfs` is the raw xplus/passphrase string on both cores.
                        val v1Obfs = sequenceOf(
                            xraySettings.optString("obfs"),
                            server?.optString("obfs"),
                            hySettings?.optString("obfs"),
                            obfsPassword
                        ).firstOrNull { it.isNotBlank() }
                        if (v1Obfs != null) result.put("obfs", v1Obfs)
                    } else {
                        result.put(
                            "obfs",
                            JSONObject()
                                .put(
                                    "type",
                                    obfsType.takeIf { it.isNotBlank() && !it.equals("none", true) }
                                        ?: "salamander"
                                )
                                .put("password", obfsPassword)
                        )
                    }
                }
            }

            else -> error("unsupported protocol for sing-box: $protocol")
        }

        transport(stream, notes)?.let { result.put("transport", it) }

        // MARBLE_SINGBOX_AUTOPARSER_V154 — TLS must not depend on a `streamSettings` object
        // existing. MarbleNG's own hy2/hysteria importer DID write one (method + tlsSettings),
        // but hand-imported Xray JSON and the direct-form emitters for tuic/hy2-style nodes can
        // carry the security material in `settings` directly: a hysteria-family node with no
        // streamSettings at all would otherwise emit a plaintext QUIC client and die at the
        // first handshake. The stream copy wins; `settings` is the fallback reader.
        val tlsJson = tls(stream, settings, notes) ?: settingsLevelTls(xraySettings, protocol, notes)
        tlsJson?.let { result.put("tls", it) }
        multiplex(outbound, settings, protocol)?.let { result.put("multiplex", it) }
        // MARBLE_SINGBOX_PROTOCOLS_V153 — sing-box's `network` field is a single string (`tcp` or
        // `udp`), not an array. Writing `["tcp","udp"]` made `sing-box check` reject every
        // translated config with a decoder error that the old doctor did not know. Omitting the
        // field is the correct way to say "both", and it is exactly what the core defaults to.
        if (detour != null) result.put("detour", detour) else applyDialTuning(result, settings)
        return result
    }

    /**
     * The proxy endpoint from an Xray `settings` object.
     *
     * Xray servers use the array form (`servers[0]`), but Marble's own share-link parser wrote the
     * direct form (`address`/`port`) for Shadowsocks, Trojan, SOCKS/HTTP and Hysteria. The old
     * translator only read `servers`, so a profile created from those share links (or pasted in
     * the direct form) failed with "no server" when the parser preference was off. Both shapes
     * are now accepted.
     */
    private fun firstServer(settings: JSONObject): JSONObject? {
        settings.optJSONArray("servers")?.optJSONObject(0)?.let { return it }
        return settings.takeIf { it.optString("address").isNotBlank() && it.optInt("port", 0) > 0 }
    }

    private fun putUserPass(result: JSONObject, server: JSONObject) {
        server.optString("user").takeIf { it.isNotBlank() }?.let { result.put("username", it) }
        server.optString("pass").takeIf { it.isNotBlank() }?.let { result.put("password", it) }
    }

    /**
     * MARBLE_SINGBOX_AUTOPARSER_V154 — translate a TUIC or AnyTLS node straight from its raw
     * share link. These two protocols have no Xray outbound shape, so Marble's importer stores
     * only the link; converting them here means a subscription server runs even when the pinned
     * core's own `parser` implementation is a point release behind the link's syntax.
     *
     * Query parameters follow the share-link conventions used by NekoBox/sing-box clients:
     * `sni`(`peer`), `alpn`, `insecure`/`allowInsecure`, `congestion_control`(`cc`),
     * `udp_relay_mode`(`urm`), `udp_over_stream`(`uos`), plus `tfo`.
     */
    private fun linkOutbound(
        profile: ProxyProfile,
        settings: AppSettings,
        notes: MutableList<String>
    ): JSONObject? {
        val link = shareLink(profile) ?: return null
        val scheme = profile.scheme.lowercase()
        if (scheme != "tuic" && scheme != "anytls") return null
        val parsed = parseSimpleLink(link) ?: return null
        val host = profile.host.takeIf { it.isNotBlank() } ?: parsed.host
        val port = if (profile.port in 1..65535) profile.port else parsed.port
        if (host.isBlank() || port !in 1..65535) return null

        val query = parsed.query
        fun q(vararg keys: String): String =
            keys.asSequence().mapNotNull { query[it] }.firstOrNull { it.isNotBlank() } ?: ""

        val tls = JSONObject().put("enabled", true)
        tls.put("server_name", q("sni", "peer", "server_name").ifBlank { host })
        q("alpn").split(',').map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }
            ?.let { list -> tls.put("alpn", JSONArray(list)) }
        if (q("insecure", "allowInsecure", "skip-cert-verify").equalsAny("1", "true")) {
            tls.put("insecure", true)
        }

        val result = JSONObject().put("tag", PROXY_TAG)
        return when (scheme) {
            "tuic" -> result
                .put("type", "tuic")
                .put("server", host)
                .put("server_port", port)
                .put("uuid", parsed.user.substringBefore(':'))
                .put("password", parsed.user.substringAfter(':', "").ifBlank {
                    q("password", "key")
                })
                .apply {
                    q("congestion_control", "cc").takeIf { it.isNotBlank() }
                        ?.let { put("congestion_control", it) }
                    q("udp_relay_mode", "urm").takeIf { it.isNotBlank() }
                        ?.let { put("udp_relay_mode", it) }
                    if (q("udp_over_stream", "uos").equalsAny("1", "true")) {
                        put("udp_over_stream", true)
                    }
                    put("tls", tls)
                }
            else -> result
                .put("type", "anytls")
                .put("server", host)
                .put("server_port", port)
                .put("password", parsed.user.ifBlank { q("password", "key") })
                .put("tls", tls)
        }
    }

    private data class SimpleLink(
        val host: String,
        val port: Int,
        val user: String,
        val query: Map<String, String>
    )

    /** scheme://user@host:port?k=v&k2=v2#name — decoded, tolerant of a missing user part. */
    private fun parseSimpleLink(link: String): SimpleLink? = runCatching {
        val afterScheme = link.substringAfter("://")
        val authorityAndRest = afterScheme.substringBefore('#')
        val authority = authorityAndRest.substringBefore('?')
        val queryPart = authorityAndRest.substringAfter('?', "")
        val rawUser = authority.substringBeforeLast('@', "").trim()
        val user = runCatching {
            java.net.URLDecoder.decode(rawUser, "UTF-8")
        }.getOrDefault(rawUser)
        val hostPort = authority.substringAfterLast('@')
        val host = hostPort.substringBeforeLast(':', "").trim().removePrefix("[").removeSuffix("]")
        val port = hostPort.substringAfterLast(':', "").toIntOrNull() ?: 0
        val query = queryPart.split('&')
            .filter { it.contains('=') }
            .associate { pair ->
                val key = pair.substringBefore('=').trim()
                val value = runCatching {
                    java.net.URLDecoder.decode(pair.substringAfter('='), "UTF-8")
                }.getOrDefault(pair.substringAfter('='))
                key to value
            }
        SimpleLink(host = host, port = port, user = user, query = query)
    }.getOrNull()

    private fun String?.equalsAny(vararg values: String): Boolean =
        this != null && values.any { this.equals(it, ignoreCase = true) }

    /** `host:port` with IPv6-literal bracket support. */
    private fun splitHostPort(endpoint: String): Pair<String, Int> {
        val value = endpoint.trim()
        if (value.startsWith("[")) {
            val close = value.indexOf(']')
            if (close > 0) {
                val host = value.substring(1, close)
                val port = value.substring(close + 1).removePrefix(":").toIntOrNull() ?: 0
                return host to port
            }
        }
        val colon = value.lastIndexOf(':')
        if (colon > 0 && value.indexOf(':') == colon) {
            return value.substring(0, colon) to (value.substring(colon + 1).toIntOrNull() ?: 0)
        }
        return value to 0
    }

    /** Xray `streamSettings` → sing-box `transport`. TCP is the absence of a transport. */
    private fun transport(stream: JSONObject, notes: MutableList<String>): JSONObject? {
        val method = stream.optString("network").ifBlank { stream.optString("method") }.lowercase()
        return when (method) {
            "", "tcp", "raw" -> {
                // TCP-with-HTTP-header obfuscation is Xray-only; sing-box has no `raw` transport
                // variant, so the node degrades to a plain TCP hop rather than failing the build.
                val headerType = stream.optJSONObject("rawSettings")?.optJSONObject("header")
                    ?.optString("type").orEmpty()
                if (headerType.isNotBlank() && !headerType.equals("none", true)) {
                    notes += "\"headerType: $headerType\" TCP obfuscation is Xray-only; this node runs without header disguise on sing-box."
                }
                null
            }
            "ws", "websocket" -> {
                val ws = stream.optJSONObject("wsSettings") ?: JSONObject()
                // MARBLE_SINGBOX_AUTOPARSER_V154 — the Host header lives in THREE places across
                // real-world configs: `headers.Host` (Xray's documented form), the flat
                // `host` key MarbleNG's own share-link parser writes, and `headers.host`
                // (lowercase, from some exporters). Reading only the first lost the disguise
                // domain of every Marble-imported WS node on the sing-box engine — the CDN
                // answered 404 for the default vhost and the node looked dead.
                val wsHost = sequenceOf(
                    ws.optJSONObject("headers")?.optString("Host"),
                    ws.optJSONObject("headers")?.optString("host"),
                    ws.optString("host")
                ).firstOrNull { !it.isNullOrBlank() }
                JSONObject()
                    .put("type", "ws")
                    .apply {
                        ws.optString("path").takeIf { it.isNotBlank() }?.let { put("path", it) }
                        wsHost?.let { put("headers", JSONObject().put("Host", it)) }
                        ws.optInt("maxEarlyData", 0).takeIf { it > 0 }
                            ?.let { put("max_early_data", it) }
                        ws.optString("earlyDataHeaderName").takeIf { it.isNotBlank() }
                            ?.let { put("early_data_header_name", it) }
                    }
            }
            "grpc" -> {
                val grpc = stream.optJSONObject("grpcSettings") ?: JSONObject()
                // Xray spells this `multiMode` (camelCase); the old reader only knew `multi_mode`,
                // so multiMode nodes silently lost the flag.
                val multiMode = grpc.optBoolean("multi_mode", false) ||
                    grpc.optBoolean("multiMode", false)
                JSONObject()
                    .put("type", "grpc")
                    .apply {
                        grpc.optString("serviceName").takeIf { it.isNotBlank() }
                            ?.let { put("service_name", it) }
                        if (multiMode) put("permit_without_stream", true)
                    }
            }
            "h2", "http" -> {
                val http = stream.optJSONObject("httpSettings") ?: JSONObject()
                JSONObject()
                    .put("type", "http")
                    .apply {
                        http.optString("path").takeIf { it.isNotBlank() }?.let { put("path", it) }
                        http.optJSONArray("host")?.let { hosts ->
                            if (hosts.length() > 0) put("host", hosts)
                        }
                        http.optString("method").takeIf { it.isNotBlank() }
                            ?.let { put("method", it) }
                    }
            }
            "httpupgrade" -> {
                val upgrade = stream.optJSONObject("httpupgradeSettings") ?: JSONObject()
                JSONObject()
                    .put("type", "httpupgrade")
                    .apply {
                        upgrade.optString("host").takeIf { it.isNotBlank() }
                            ?.let { put("host", it) }
                        upgrade.optString("path").takeIf { it.isNotBlank() }
                            ?.let { put("path", it) }
                    }
            }
            "xhttp", "splithttp" -> {
                val xhttp = stream.optJSONObject("xhttpSettings") ?: JSONObject()
                JSONObject()
                    .put("type", "xhttp")
                    .apply {
                        xhttp.optString("host").takeIf { it.isNotBlank() }
                            ?.let { put("host", it) }
                        xhttp.optString("path").takeIf { it.isNotBlank() }
                            ?.let { put("path", it) }
                        xhttp.optString("mode").takeIf { it.isNotBlank() }
                            ?.let { put("mode", it) }
                    }
            }
            // MARBLE_SINGBOX_PROTOCOLS_V153 — KCP and QUIC are Xray v2ray transports, not
            // sing-box transports. The old writer invented `type: kcp` / `type: quic`, which
            // `sing-box check` refuses with "unknown field". sing-box cannot carry those
            // transports, so the honest result is the core's default transport (TCP) plus a
            // diagnostic note — not a config the core cannot parse.
            "kcp", "mkcp" -> {
                notes += "KCP transport is Xray-only; this node falls back to the sing-box default transport."
                null
            }
            "quic" -> {
                notes += "QUIC transport is Xray-only; this node falls back to the sing-box default transport."
                null
            }
            else -> null
        }
    }

    private fun tls(stream: JSONObject, settings: AppSettings, notes: MutableList<String>): JSONObject? {
        val security = stream.optString("security").lowercase()
        if (security == "none" || security.isBlank()) return null
        val source = if (security == "reality") {
            stream.optJSONObject("realitySettings") ?: JSONObject()
        } else {
            stream.optJSONObject("tlsSettings") ?: JSONObject()
        }

        val tls = JSONObject().put("enabled", true)
        source.optString("serverName").takeIf { it.isNotBlank() }
            ?.let { tls.put("server_name", it) }
        source.optJSONArray("alpn")?.let { alpn ->
            if (alpn.length() > 0) tls.put("alpn", alpn)
        }
        // `allowInsecure` is a removed feature in Xray, but a legacy profile can still carry it;
        // sing-box spells the same request `insecure`.
        if (source.optBoolean("allowInsecure", false)) tls.put("insecure", true)
        // MARBLE_SINGBOX_AUTOPARSER_V154 — TLS floors/ceilings survive the conversion.
        source.optString("minVersion").takeIf { it.isNotBlank() }
            ?.let { tls.put("min_version", it) }
        source.optString("maxVersion").takeIf { it.isNotBlank() }
            ?.let { tls.put("max_version", it) }
        if (source.optString("cipherSuites").isNotBlank()) {
            notes += "Pinned cipher suites are Xray-only; sing-box picks suites from the fingerprint."
        }
        // ECH: Xray stores `echConfigList` (base64 config list, or a DoH URL). sing-box wants
        // `ech.config`; a DoH lookup form has no sing-box equivalent and is reported, not guessed.
        source.optString("echConfigList").takeIf { it.isNotBlank() }?.let { ech ->
            if (ech.contains("://")) {
                notes += "ECH-over-DoH is Xray-only; this node runs without ECH on sing-box."
            } else {
                tls.put("ech", JSONObject().put("enabled", true).put("config", JSONArray().put(ech)))
            }
        }
        source.optString("fingerprint").takeIf { it.isNotBlank() && it != "unsafe" }
            ?.let { fingerprint ->
                tls.put(
                    "utls",
                    JSONObject().put("enabled", true).put("fingerprint", fingerprint)
                )
            }
        if (security == "reality") {
            // MARBLE_SINGBOX_AUTOPARSER_V154 — Xray's REALITY public key arrives under TWO keys:
            // `publicKey` (documented) and `password` (the key MarbleNG's own share-link parser
            // stores, after the `pbk` link parameter). Reading only the first dropped the key of
            // every Marble-imported Reality node on the sing-box engine: TLS built, Reality has no
            // key, handshake dies. Both spellings are read, generic-Xray first.
            val publicKey = source.optString("publicKey").takeIf { it.isNotBlank() }
                ?: source.optString("password").takeIf { it.isNotBlank() }
            publicKey?.let { key ->
                tls.put(
                    "reality",
                    JSONObject()
                        .put("enabled", true)
                        .put("public_key", key)
                        .apply {
                            source.optString("shortId").takeIf { it.isNotBlank() }
                                ?.let { put("short_id", it) }
                        }
                )
            }
        }
        // MARBLE_SINGBOX_PROTOCOLS_V153 — Xray's transport-fragment knob is not a TLS option in
        // sing-box; the only supported place is route-options.tls_fragment. Writing `fragment`
        // inside `tls` made the core reject the whole config when `fragmentEnabled` was on.
        if (settings.fragmentEnabled) {
            notes += "Fragmentation is mapped to sing-box route options, not a TLS fragment field."
        }
        return tls
    }

    /**
     * TLS material a profile stores in its Xray `settings` instead of `streamSettings` — the
     * hysteria/hysteria2 family (whose transport is QUIC and never carries a ws/grpc block) and
     * the occasional hand-built config. `streamSettings` is authoritative; this is the fallback.
     */
    private fun settingsLevelTls(
        xraySettings: JSONObject,
        protocol: String,
        notes: MutableList<String>
    ): JSONObject? {
        val source = xraySettings.optJSONObject("tls")
            ?: xraySettings.optJSONObject("tlsSettings")
            ?: return if (protocol == "hysteria2" || protocol == "hysteria") {
                // QUIC protocols are TLS by definition on both engines; no node runs plaintext.
                JSONObject().put("enabled", true)
            } else {
                null
            }
        val tls = JSONObject().put("enabled", true)
        notes += "TLS material was stored outside streamSettings; the settings copy was used."
        source.optString("serverName").takeIf { it.isNotBlank() }
            ?.let { tls.put("server_name", it) }
        source.optString("sni").takeIf { it.isNotBlank() && !tls.has("server_name") }
            ?.let { tls.put("server_name", it) }
        source.optString("peer").takeIf { it.isNotBlank() && !tls.has("server_name") }
            ?.let { tls.put("server_name", it) }
        source.optString("alpn").takeIf { it.isNotBlank() }?.let { raw ->
            val list = JSONArray()
            raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach(list::put)
            if (list.length() > 0) tls.put("alpn", list)
        }
        if (source.optBoolean("insecure", false) || source.optBoolean("allowInsecure", false)) {
            tls.put("insecure", true)
        }
        return tls
    }

    private fun multiplex(outbound: JSONObject, settings: AppSettings, protocol: String): JSONObject? {
        // The hardener writes `mux`; hand-imports may carry `muxSettings`. Both are read.
        val source = outbound.optJSONObject("muxSettings") ?: outbound.optJSONObject("mux")
        val enabled = source?.optBoolean("enabled", false) == true || settings.muxEnabled
        if (!enabled) return null
        // Mux exists only for TCP-family outbounds; on QUIC/WireGuard it is meaningless and the
        // core refuses the field outright (which would discard this strategy candidate).
        if (protocol !in setOf("vless", "vmess", "trojan", "shadowsocks", "socks", "http")) {
            return null
        }
        val mux = JSONObject().put("enabled", true)
        mux.put("protocol", source?.optString("protocol").orEmpty().ifBlank { "smux" })
        val concurrency = source?.optInt("concurrency", 0) ?: 0
        if (concurrency > 0) {
            mux.put("max_connections", concurrency)
        } else if (settings.muxConcurrency > 0) {
            mux.put("max_connections", settings.muxConcurrency.coerceIn(1, 128))
        }
        if (source?.optBoolean("padding", true) != false) mux.put("padding", true)
        return mux
    }

    private fun applyDialTuning(outbound: JSONObject, settings: AppSettings) {
        outbound.put("tcp_fast_open", settings.tcpFastOpenEnabled)
        outbound.put("connect_timeout", "${settings.singBoxConnectTimeoutSec.coerceIn(3, 60)}s")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // DNS
    // ─────────────────────────────────────────────────────────────────────────────

    private fun dnsConfig(
        settings: AppSettings,
        profile: ProxyProfile,
        resolverPool: List<String>
    ): JSONObject {
        val servers = JSONArray()

        // MARBLE_SINGBOX_PROTOCOLS_V153 — the first two entries are the user's primary pair, the
        // remaining entries are Marble Intelligence's measured pool (stock DoH on independent
        // infrastructure). The old writer emitted only the two literals from `settings`, so when
        // those two were the demoted endpoints the sing-box engine had no healthy encrypted
        // resolver even though the Xray engine's hardener would have raced the whole pool.
        val pool = resolverPool
            .map { it.trim() }
            .filter { it.startsWith("https://") || it.startsWith("http://") }
            .let { selected ->
                val defaults = listOf(
                    settings.dnsPrimaryDoH.ifBlank { "https://1.1.1.1/dns-query" },
                    settings.dnsSecondaryDoH.ifBlank { "https://8.8.8.8/dns-query" }
                )
                (defaults + selected)
                    .distinctBy { dohHost(it) + dohPath(it) }
                    .take(6)
            }

        servers.put(dohServer(DNS_REMOTE_TAG, pool[0], PROXY_TAG))
        // The direct resolver answers the proxy endpoint's own hostname and the rule-set
        // downloads; it must never detour into the tunnel it is trying to establish.
        servers.put(dohServer(DNS_DIRECT_TAG, pool[1], DIRECT_TAG))
        // The rest are remote fallbacks through the tunnel. They are available to Marble's
        // evidence loop even when a demoted endpoint stays in the pool.
        pool.drop(2).forEachIndexed { index, url ->
            servers.put(dohServer("dns-remote-$index", url, PROXY_TAG))
        }
        servers.put(JSONObject().put("type", "hosts").put("tag", DNS_HOSTS_TAG))
        // MARBLE_SINGBOX_DNS_ACTION_V152 — the system resolver is the one resolver a censored
        // underlay cannot afford to block, and the shipped log proved the inverse: every public
        // DoH literal (1.1.1.1 / 8.8.8.8 / 9.9.9.9) was demoted for deadline storms, so domain
        // egress died while literal-IP egress kept working (`literalIpHttps=true,
        // domainHttps=false`). `type: local` asks Android's own resolver, so the node's hostname
        // bootstrap always has a path that does not depend on an encrypted endpoint being alive.
        servers.put(JSONObject().put("type", "local").put("tag", DNS_LOCAL_TAG))

        val rules = JSONArray()
        // The proxy endpoint's own hostname resolves through the system resolver first: the
        // tunnel cannot be established through a resolver that needs the tunnel.
        profile.host.takeIf { it.isNotBlank() && !isLiteralAddress(it) }?.let { host ->
            rules.put(
                JSONObject()
                    .put("domain", JSONArray().put(host))
                    .put("server", DNS_LOCAL_TAG)
            )
        }
        // MARBLE_SINGBOX_PROTOCOLS_V153 — DNS rules may only reference rule sets that are also
        // defined in `route.rule_set`. The old writer emitted these Iranian/private rules
        // unconditionally, so a user who disabled bypass-private or Iran direct (and thereby the
        // corresponding `route.rule_set` entry) produced a config the core rejected. The rules
        // follow the same settings as routing now.
        if (settings.routingMode == RoutingMode.GEO_DIRECT && settings.iranDomesticDirect) {
            rules.put(
                JSONObject()
                    .put("rule_set", JSONArray().put(RULE_SET_GEOIP_IR).put(RULE_SET_GEOSITE_IR))
                    .put("server", DNS_DIRECT_TAG)
            )
        }
        if (settings.routeBypassPrivate) {
            rules.put(
                JSONObject()
                    .put("rule_set", JSONArray().put(RULE_SET_GEOIP_PRIVATE))
                    .put("server", DNS_DIRECT_TAG)
            )
        }

        return JSONObject()
            .put("servers", servers)
            .put("rules", rules)
            .put("final", DNS_REMOTE_TAG)
            .put("strategy", dnsStrategy(settings))
            .put("timeout", "${settings.singBoxConnectTimeoutSec.coerceIn(3, 20)}s")
            .put("independent_cache", true)
    }

    /** True for IPv4/IPv6 literals — addresses never need the DNS bootstrap rule. */
    private fun isLiteralAddress(host: String): Boolean {
        val raw = host.trim().removePrefix("[").removeSuffix("]")
        val octets = raw.split('.')
        val v4 = octets.size == 4 && octets.all { (it.toIntOrNull() ?: -1) in 0..255 }
        return v4 || raw.contains(':')
    }

    private fun dohHost(url: String): String {
        val withoutScheme = url.trim().removePrefix("https://").removePrefix("http://")
        return withoutScheme.substringBefore('/').trim().lowercase()
    }

    private fun dohPath(url: String): String {
        val withoutScheme = url.trim().removePrefix("https://").removePrefix("http://")
        return "/" + withoutScheme.substringAfter('/', "dns-query")
    }

    private fun dohServer(tag: String, url: String, detour: String): JSONObject {
        val host = dohHost(url)
        return JSONObject()
            .put("type", "https")
            .put("tag", tag)
            .put("server", host)
            .put("path", dohPath(url))
            .put("detour", detour)
    }

    private fun dnsStrategy(settings: AppSettings): String = when {
        !settings.ipv6Enabled -> "ipv4_only"
        settings.preferIpv6 -> "prefer_ipv6"
        else -> "prefer_ipv4"
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Routing
    // ─────────────────────────────────────────────────────────────────────────────

    private fun routeConfig(settings: AppSettings, notes: MutableList<String>): JSONObject {
        val rules = JSONArray()

        // Sniffing is what lets a TLS/HTTP connection be routed by domain at all; without it the
        // engine only ever sees an IP and every domain rule is dead weight.
        rules.put(JSONObject().put("action", "sniff"))
        rules.put(
            JSONObject()
                .put("protocol", JSONArray().put("dns"))
                .put("action", "hijack-dns")
        )

        val iranDirect = settings.routingMode == RoutingMode.GEO_DIRECT && settings.iranDomesticDirect
        val bypassPrivate = settings.routeBypassPrivate

        if (iranDirect || bypassPrivate) {
            val sets = JSONArray()
            if (bypassPrivate) sets.put(RULE_SET_GEOIP_PRIVATE)
            if (iranDirect) sets.put(RULE_SET_GEOIP_IR).put(RULE_SET_GEOSITE_IR)
            rules.put(
                JSONObject()
                    .put("rule_set", sets)
                    .put("outbound", DIRECT_TAG)
            )
        } else if (bypassPrivate) {
            rules.put(JSONObject().put("ip_is_private", true).put("outbound", DIRECT_TAG))
        }

        if (settings.routeBlockAds) {
            rules.put(
                JSONObject()
                    .put("rule_set", JSONArray().put(RULE_SET_ADS))
                    .put("action", "reject")
            )
        }

        RoutingEngine.effectiveRules(settings).forEach { rule ->
            if (!rule.enabled) return@forEach
            val mapped = JSONObject()
            when (rule.kind) {
                RoutingRuleKind.DOMAIN -> mapped.put("domain_keyword", splitList(rule.matcher))
                RoutingRuleKind.IP -> mapped.put("ip_cidr", splitList(rule.matcher))
                RoutingRuleKind.PORT -> mapped.put("port_range", splitList(rule.matcher))
                RoutingRuleKind.GEOSITE, RoutingRuleKind.GEOIP -> {
                    // geoip.dat/geosite.dat are Xray databases; sing-box reads .srs rule sets.
                    notes += "Geo rule \"${rule.remark.ifBlank { rule.matcher }}\" is Xray-only."
                    return@forEach
                }
            }
            mapped.put(
                "outbound",
                when (rule.outbound) {
                    RoutingOutbound.PROXY -> PROXY_TAG
                    RoutingOutbound.DIRECT -> DIRECT_TAG
                    RoutingOutbound.BLOCK -> BLOCK_TAG
                }
            )
            rules.put(mapped)
        }

        val ruleSets = JSONArray()
        if (bypassPrivate) ruleSets.put(remoteRuleSet(RULE_SET_GEOIP_PRIVATE, "geoip/private.srs"))
        if (iranDirect) {
            ruleSets.put(remoteRuleSet(RULE_SET_GEOIP_IR, "geoip/ir.srs"))
            ruleSets.put(remoteRuleSet(RULE_SET_GEOSITE_IR, "geosite/category-ir.srs"))
        }
        if (settings.routeBlockAds) {
            ruleSets.put(remoteRuleSet(RULE_SET_ADS, "geosite/category-ads-all.srs"))
        }

        return JSONObject()
            .put("rules", rules)
            .put("rule_set", ruleSets)
            .put("final", PROXY_TAG)
            .put("auto_detect_interface", true)
    }

    private fun remoteRuleSet(tag: String, relativePath: String): JSONObject = JSONObject()
        .put("type", "remote")
        .put("tag", tag)
        .put("format", "binary")
        .put("url", "$RULE_SET_BASE/$relativePath")
        .put("download_detour", DIRECT_TAG)
        .put("update_interval", "7d")

    private fun splitList(raw: String): JSONArray {
        val array = JSONArray()
        raw.split(',', '|', '\n').forEach { token ->
            token.trim().takeIf { it.isNotBlank() }?.let { array.put(it) }
        }
        return array
    }
}
