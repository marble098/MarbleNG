package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRuleKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * MARBLE_SINGBOX_CORE_V151 — the sing-box extended configuration writer.
 *
 * MarbleNG stores every node as Xray JSON ([ProxyProfile.configJson]) because that is the format
 * the whole product was built around. sing-box has its own schema, so this object is the single
 * bridge between the two. It has two strategies and always tells the caller which one it used:
 *
 *  1. **[STRATEGY_LINK]** — the profile still carries its original share link, and sing-box
 *     extended ships a native `parser` outbound that understands exactly that link. Handing the
 *     link to the core is strictly better than re-deriving it: the core's own parser owns every
 *     parameter the link can express, so nothing MarbleNG does not know about is silently dropped.
 *  2. **[STRATEGY_TRANSLATED]** — a pasted Xray JSON (or a link the parser outbound does not
 *     cover) is translated hop by hop into sing-box outbounds. Chains survive: an Xray
 *     `dialerProxy` becomes a sing-box `detour`.
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

    /** Protocols the JSON translator knows. Anything else is reported, never guessed. */
    private val TRANSLATABLE_PROTOCOLS = setOf(
        "vless", "vmess", "trojan", "shadowsocks", "socks", "http", "hysteria2", "hysteria"
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

        val notes = support.notes.toMutableList()
        val outbounds = JSONArray()

        when (support.strategy) {
            STRATEGY_LINK -> outbounds.put(
                JSONObject()
                    .put("type", "parser")
                    .put("tag", PROXY_TAG)
                    .put("link", shareLink(profile) ?: "")
                    .apply { applyDialTuning(this, settings) }
            )
            else -> {
                val translated = translate(JSONObject(profile.configJson), settings, notes)
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

        return Build(root.toString(), support.strategy, notes)
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
     * Walks the Xray chain (`dialerProxy` / `proxySettings`) from the entry hop outwards and
     * returns the equivalent sing-box outbounds, each one detouring into the next.
     */
    private fun translate(root: JSONObject, settings: AppSettings, notes: MutableList<String>): List<JSONObject> {
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
                // MARBLE_SINGBOX_AUTOPARSER_V154 — both server shapes. The array form
                // (`settings.vnext[]`) is what Xray itself writes; the direct form
                // (`settings.{address, port, id}`) is what every PattNG export and every Marble
                // share-link import store. The old reader only knew `vnext`, so a direct-form
                // node died with "no vnext server" — and a refusal there killed the whole node.
                val vnext = xraySettings.optJSONArray("vnext")?.optJSONObject(0)
                val direct = xraySettings.takeIf {
                    it.optString("address").isNotBlank() && it.optInt("port", 0) > 0
                }
                val server = vnext ?: direct
                    ?: error("$protocol outbound has no vnext server")
                // Field-by-field user resolution: the uuid may live on the users[] entry (array
                // form) or on the settings object itself (direct form), and the flow/encryption
                // and security/alterId knobs follow the same split.
                val user = (vnext?.optJSONArray("users")?.optJSONObject(0))
                    ?: xraySettings.optJSONArray("users")?.optJSONObject(0)
                    ?: JSONObject()
                val uuid = firstNonBlank(
                    user.optString("id"),
                    direct?.optString("id").orEmpty()
                ).orEmpty()
                result.put("type", protocol)
                result.put("server", server.optString("address"))
                result.put("server_port", server.optInt("port"))
                result.put("uuid", uuid)
                if (protocol == "vless") {
                    firstNonBlank(
                        user.optString("flow"),
                        xraySettings.optString("flow")
                    )?.let { result.put("flow", it) }
                    firstNonBlank(
                        user.optString("encryption"),
                        xraySettings.optString("encryption")
                    )?.let { result.put("encryption", it) }
                } else {
                    result.put("security", firstNonBlank(
                        user.optString("security"),
                        xraySettings.optString("security")
                    ) ?: "auto")
                    val alterId = maxOf(user.optInt("alterId", 0), xraySettings.optInt("alterId", 0))
                    if (alterId > 0) result.put("alter_id", alterId)
                    if (
                        user.optBoolean("globalPadding", false) ||
                        xraySettings.optBoolean("globalPadding", false)
                    ) {
                        result.put("global_padding", true)
                    }
                    // vmess `packetEncoding` → sing-box `packet_encoding`. Without this mapping
                    // UDP over the XUDP/packetaddr channel was silently lost: the field dropped,
                    // and the proxy fell back to plain UDP on a node that spoke the packet
                    // protocol.
                    firstNonBlank(
                        user.optString("packetEncoding"),
                        xraySettings.optString("packetEncoding")
                    )?.let { result.put("packet_encoding", it) }
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
                // MARBLE_SINGBOX_AUTOPARSER_V154 — SIP002 `uot`: Xray wraps UDP in TCP on this
                // flag, and sing-box spells the same request `udp_over_tcp`. The field was read
                // nowhere before, so a UOT node silently dropped to unencrypted UDP semantics.
                if (server.optBoolean("uot", false) || xraySettings.optBoolean("uot", false)) {
                    result.put("udp_over_tcp", JSONObject().put("enabled", true))
                }
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
                // MARBLE_SINGBOX_AUTOPARSER_V154 — the explicit `version` wins; the protocol
                // name is only the tiebreak. Stored hy2 nodes are `protocol: hysteria` +
                // `version: 2`, so the old name-only rule mistyped them as v1 and the QUIC
                // handshake died on the first auth check.
                val hySettings = stream.optJSONObject("hysteriaSettings")
                val explicitVersion = sequenceOf<Int>(
                    xraySettings.optInt("version", 0),
                    server?.optInt("version", 0) ?: 0,
                    hySettings?.optInt("version", 0) ?: 0
                ).firstOrNull { it == 1 || it == 2 }
                val version = explicitVersion ?: if (protocol == "hysteria2") 2 else 1
                // MARBLE_SINGBOX_PROTOCOLS_V153 — Hysteria v1 and v2 are different sing-box
                // outbound types. The old writer always typed `hysteria2` and read `password` on
                // the Xray v1 settings (which use `auth_str`), so a Hysteria v1 node ran as a v2
                // auth mismatch and died at the first QUIC handshake.
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
                // MARBLE_SINGBOX_AUTOPARSER_V154 — the rate fields arrive as integers, strings,
                // or human strings ("100 Mbps"): digit-extraction instead of raw `toString`.
                // The core REQUIRES both rates on v1, so the missing ones get the documented
                // 10/50 defaults instead of a config `sing-box check` refuses.
                val up = firstMbps(
                    optMbps(server, "up_mbps"),
                    optMbps(hySettings, "up_mbps"),
                    optMbps(xraySettings, "up_mbps"),
                    mbpsDigits(hySettings?.optString("up"))
                )
                val down = firstMbps(
                    optMbps(server, "down_mbps"),
                    optMbps(hySettings, "down_mbps"),
                    optMbps(xraySettings, "down_mbps"),
                    mbpsDigits(hySettings?.optString("down"))
                )
                result.put("up_mbps", up ?: 10)
                result.put("down_mbps", down ?: 50)
                // Obfs, from every source the emitters actually use: the QUIC `finalmask`
                // (v2), the `settings.obfs` string (v1 share links) and the legacy
                // `hysteriaSettings.obfs` / server-level object.
                val finalmask = stream.optJSONObject("finalmask")?.optJSONArray("udp")?.optJSONObject(0)
                if (version == 1) {
                    val obfsRaw = firstNonBlank(
                        xraySettings.optString("obfs"),
                        hySettings?.optString("obfs"),
                        server?.optString("obfs"),
                        finalmask?.optString("type").orEmpty()
                    )
                    if (obfsRaw != null && !obfsRaw.equals("none", ignoreCase = true)) {
                        result.put("obfs", obfsRaw)
                    }
                } else {
                    when {
                        finalmask != null -> {
                            val type = finalmask.optString("type").ifBlank { "salamander" }
                            if (!type.equals("none", ignoreCase = true)) {
                                val fmSettings = finalmask.optJSONObject("settings") ?: JSONObject()
                                val password = fmSettings.optJSONObject(type)?.optString("password")
                                    .ifBlank { fmSettings.optString("password") }
                                result.put(
                                    "obfs",
                                    JSONObject().put("type", type).apply {
                                        if (password.isNotBlank()) put("password", password)
                                    }
                                )
                            }
                        }
                        else -> server?.optJSONObject("obfs")?.let { obfs ->
                            val obfsPassword = obfs.optString("password").ifBlank { obfs.optString("obfs") }
                            if (obfsPassword.isNotBlank()) {
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
            }

            // MARBLE_SINGBOX_AUTOPARSER_V154 — WireGuard, translated from Xray's
            // `secretKey` + `peers` shape onto sing-box's `private_key` + `server`/`peers`
            // schema. The endpoint string ("host:port") is split back into its parts; the
            // default allowed IPs are what the Xray documentation shows, so a bare config still
            // routes all traffic through the tunnel.
            "wireguard" -> {
                val privateKey = firstNonBlank(
                    xraySettings.optString("secretKey"),
                    xraySettings.optString("privateKey")
                ) ?: error("wireguard outbound has no client key")
                val peer = xraySettings.optJSONArray("peers")?.optJSONObject(0)
                    ?: error("wireguard outbound has no peer")
                val endpoint = peer.optString("endpoint").ifBlank {
                    firstNonBlank(
                        xraySettings.optString("endpoint"),
                        xraySettings.optString("address")
                    ).orEmpty()
                }
                val parsed = splitEndpoint(endpoint)
                    ?: error("wireguard outbound has no readable endpoint")
                val host = parsed.first
                val port = parsed.second
                result.put("type", "wireguard")
                result.put("private_key", privateKey)
                result.put("server", host)
                result.put("server_port", port)
                val peerOut = JSONObject()
                    .put("public_key", peer.optString("publicKey"))
                peer.optJSONArray("allowedIPs")?.let { allowed ->
                    if (allowed.length() > 0) peerOut.put("allowed_ips", allowed)
                } ?: peerOut.put(
                    "allowed_ips",
                    JSONArray().put("0.0.0.0/0").put("::/0")
                )
                peer.optString("keepAlive").replace(Regex("[^0-9]"), "")
                    .toIntOrNull()?.takeIf { it > 0 }
                    ?.let { peerOut.put("keep_alive", it) }
                result.put("peers", JSONArray().put(peerOut))
                xraySettings.optInt("mtu", 0).takeIf { it > 0 }?.let { result.put("mtu", it) }
                xraySettings.optInt("workers", 0).takeIf { it > 0 }
                    ?.let { result.put("workers", it) }
                xraySettings.optJSONArray("reserved")?.let { reserved ->
                    if (reserved.length() > 0) result.put("reserved", reserved)
                }
            }

            else -> error("unsupported protocol for sing-box: $protocol")
        }

        transport(stream, notes)?.let { result.put("transport", it) }
        tls(stream, settings, notes)?.let { result.put("tls", it) }
        val transportMethod = stream.optString("network").ifBlank { stream.optString("method") }.lowercase()
        multiplex(outbound, settings, protocol, transportMethod)?.let { result.put("multiplex", it) }
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

    // ─────────────────────────────────────────────────────────────────────────────
    // MARBLE_SINGBOX_AUTOPARSER_V154 — translator helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /** The first non-blank of the candidates; `null` when every candidate is blank. */
    private fun firstNonBlank(vararg values: String): String? =
        values.firstOrNull { it.isNotBlank() }

    /**
     * "100", "100 Mbps", "100mbps/s" → 100. Human-emitted rate strings are real on the wire
     * (Hysteria share links in particular), and `toString()` of a string value is how the old
     * reader shipped the literal word "Mbps" into a numeric field.
     */
    private fun mbpsDigits(text: String?): Int? {
        if (text == null) return null
        val digits = text.replace(Regex("[^0-9]"), "")
        return digits.toIntOrNull()?.takeIf { it > 0 }
    }

    /** An integer-or-string mbps field: 100 and "100 Mbps" both work. */
    private fun optMbps(obj: JSONObject?, key: String): Int? {
        val value = obj?.opt(key) ?: return null
        return when (value) {
            is Number -> value.toInt().takeIf { it > 0 }
            is String -> mbpsDigits(value)
            else -> null
        }
    }

    private fun firstMbps(vararg values: Int?): Int? = values.firstOrNull { it != null && it > 0 }

    /** "host:443", "[2001:db8::1]:8443" → ("host", 443). `null` when not a host:port pair. */
    private fun splitEndpoint(raw: String): Pair<String, Int>? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        return when {
            text.startsWith("[") -> {
                // Bracketed IPv6: [addr]:port
                val close = text.indexOf(']')
                if (close < 0) return null
                val host = text.substring(1, close)
                if (host.isEmpty()) return null
                val port = text.substring(close + 1).removePrefix(":").toIntOrNull() ?: return null
                host to port
            }
            text.count { it == ':' } == 1 -> {
                // host:port — a single colon means no IPv6 literal
                val host = text.substringBeforeLast(':')
                val port = text.substringAfterLast(':').toIntOrNull()
                if (host.isEmpty() || port == null) return null
                host to port
            }
            else -> null
        }
    }

    /**
     * MARBLE_SINGBOX_AUTOPARSER_V154 — the link-only sing-box-extended protocols (TUIC and
     * AnyTLS) translated from the raw share link by Marble itself.
     *
     * Those nodes store a blank `configJson` (there is no Xray JSON shape for them), so handing
     * them to the core's parser outbound was the only reader — and a stale core reader killed
     * them. Translating the link here means the TRANSLATED candidate always exists for a
     * readable link, and the parser candidate is the second chance instead of the only one.
     *
     * The parse is deliberately dependency-free (no `Uri`): the share-link grammar here is
     * `scheme://userInfo@host:port?key=value&…#fragment`, and a pure-Kotlin reader keeps the
     * unit tests honest on the JVM.
     */
    private fun putUserPass(result: JSONObject, server: JSONObject) {
        server.optString("user").takeIf { it.isNotBlank() }?.let { result.put("username", it) }
        server.optString("pass").takeIf { it.isNotBlank() }?.let { result.put("password", it) }
    }

    /** Xray `streamSettings` → sing-box `transport`. TCP is the absence of a transport. */
    private fun transport(stream: JSONObject, notes: MutableList<String>): JSONObject? {
        val method = stream.optString("network").ifBlank { stream.optString("method") }.lowercase()
        return when (method) {
            "", "tcp", "raw" -> null
            "ws" -> {
                val ws = stream.optJSONObject("wsSettings") ?: JSONObject()
                JSONObject()
                    .put("type", "ws")
                    .apply {
                        ws.optString("path").takeIf { it.isNotBlank() }?.let { put("path", it) }
                        ws.optJSONObject("headers")?.optString("Host")?.takeIf { it.isNotBlank() }
                            ?.let { put("headers", JSONObject().put("Host", it)) }
                        ws.optInt("maxEarlyData", 0).takeIf { it > 0 }
                            ?.let { put("max_early_data", it) }
                        ws.optString("earlyDataHeaderName").takeIf { it.isNotBlank() }
                            ?.let { put("early_data_header_name", it) }
                    }
            }
            "grpc" -> {
                val grpc = stream.optJSONObject("grpcSettings") ?: JSONObject()
                JSONObject()
                    .put("type", "grpc")
                    .apply {
                        grpc.optString("serviceName").takeIf { it.isNotBlank() }
                            ?.let { put("service_name", it) }
                        grpc.optBoolean("multi_mode", false)
                            .takeIf { it }
                            ?.let { put("permit_without_stream", it) }
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
        source.optString("fingerprint").takeIf { it.isNotBlank() && it != "unsafe" }
            ?.let { fingerprint ->
                tls.put(
                    "utls",
                    JSONObject().put("enabled", true).put("fingerprint", fingerprint)
                )
            }
        if (security == "reality") {
            source.optString("publicKey").takeIf { it.isNotBlank() }?.let { publicKey ->
                tls.put(
                    "reality",
                    JSONObject()
                        .put("enabled", true)
                        .put("public_key", publicKey)
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

    private fun multiplex(
        outbound: JSONObject,
        settings: AppSettings,
        protocol: String,
        transportMethod: String
    ): JSONObject? {
        val source = outbound.optJSONObject("muxSettings")
        val enabled = source?.optBoolean("enabled", false) == true || settings.muxEnabled
        if (!enabled) return null
        // Mux is meaningless on a UDP-based protocol and actively harmful there; sing-box
        // rejects the field on the QUIC-family outbounds, so the writer gates it to the
        // TCP family instead of letting the core refuse the config.
        if (
            protocol in setOf("hysteria2", "hysteria", "wireguard", "quic") ||
            transportMethod.equals("quic", ignoreCase = true)
        ) {
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
