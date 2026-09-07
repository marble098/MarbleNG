package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRuleKind
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

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

    /**
     * MARBLE_SINGBOX_ANDROID_RUNTIME_V155 — the single named HTTP client every remote rule-set
     * download uses. See [routeConfig] for why it exists instead of `download_detour`.
     */
    const val HTTP_CLIENT_DIRECT_TAG = "marble-http-direct"

    const val STRATEGY_LINK = "link-parser"
    const val STRATEGY_TRANSLATED = "translated"
    const val STRATEGY_NATIVE = "native-singbox"

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
        if (profile.scheme.equals("ssh", true)) {
            return Support(false, "", "SSH profiles using Marble's Java bridge currently require Xray.", notes)
        }
        return try {
            val root = profile.configJson.takeIf { it.isNotBlank() }?.let(::JSONObject)
            when {
                root != null && NativeSingBoxConfig.isNative(root) -> {
                    NativeSingBoxConfig.outbounds(root) // validate selection and graph before promising support
                    Support(true, STRATEGY_NATIVE, "", notes)
                }
                root != null -> {
                    // The canonical document is authoritative. The fork's URI parser only reads
                    // a subset of XHTTP extra fields and can lose a user's edited JSON or chain.
                    translate(root, settings, notes)
                    Support(true, STRATEGY_TRANSLATED, "", notes)
                }
                shareLink(profile) != null -> Support(true, STRATEGY_LINK, "", notes)
                else -> Support(false, "", "The stored config is not readable JSON or a supported share link.", notes)
            }
        } catch (error: Exception) {
            Support(false, "", error.message ?: "Invalid proxy configuration", notes)
        }
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
        resolverPool: List<String> = emptyList(),
        /** Local, verified bundled rule sets. No download is allowed on the startup path. */
        ruleSetPaths: Map<String, String> = emptyMap(),
        /** Android resolver bridge; type:local in the CLI reads /etc/resolv.conf, not netd. */
        bootstrapDnsPort: Int = 0,
        forTest: Boolean = false
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
            STRATEGY_NATIVE -> NativeSingBoxConfig.outbounds(JSONObject(profile.configJson))
                .forEach { outbounds.put(it) }
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
            .put("dns", dnsConfig(settings, profile, resolverPool, bootstrapDnsPort))
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
            .put(
                // MARBLE_SINGBOX_ANDROID_RUNTIME_V155 — one named HTTP client, used by every
                // remote rule set and by `route.default_http_client`. `download_detour` (the old
                // spelling) is deprecated in 1.14, and an unnamed default client is deprecated
                // too; a single explicit client answers both, and keeps the promise that matters:
                // rule sets are fetched `direct`, never through a tunnel that does not exist yet.
                "http_clients",
                JSONArray().put(
                    JSONObject()
                        .put("tag", HTTP_CLIENT_DIRECT_TAG)
                        .put("detour", DIRECT_TAG)
                )
            )
            .put("route", routeConfig(settings, notes, ruleSetPaths))
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
                        if (settings.singBoxCacheFile && !forTest) {
                            put(
                                "cache_file",
                                JSONObject()
                                    .put("enabled", true)
                                    .put("path", cachePath)
                                    // MARBLE_SINGBOX_ANDROID_RUNTIME_V155 — `store_rdrc` is
                                    // deprecated in sing-box 1.14 (removal scheduled for 1.16);
                                    // `store_dns` is the documented replacement and persists the
                                    // whole DNS cache rather than only the rejected-domain cache.
                                    .put("store_dns", true)
                            )
                        }
                    }
            )

        if (support.strategy == STRATEGY_NATIVE) {
            NativeSingBoxConfig.copyResources(JSONObject(profile.configJson), root)
            notes += "Imported proxy graph retained; Marble owns the local inbound, DNS and routing policy."
        }
        return Build(root.toString(), support.strategy, notes.distinct())
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
        return chain.mapIndexed { index, hop ->
            val tag = if (index == 0) PROXY_TAG else "marble-hop-$index"
            // Entry dials THROUGH hop 1, hop 1 through hop 2. Never reverse this edge.
            val detour = if (index < chain.lastIndex) "marble-hop-${index + 1}" else null
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
        listOf("vnext", "servers").forEach { key ->
            if ((xraySettings.optJSONArray(key)?.length() ?: 0) > 1) {
                throw ConfigTranslationException("settings.$key", "multiple servers require explicit profile selection; they cannot be silently collapsed")
            }
        }

        when (protocol) {
            "vless", "vmess" -> {
                // MARBLE_SINGBOX_AUTOPARSER_V154 — Xray stores the endpoint under
                // `settings.vnext[0]` in the classic shape, but PattNG and Marble's own JSON
                // imports emit a direct `settings.{address,port,users|id|flow|…}` shape with no
                // `vnext`. The old reader only knew `vnext`, so a perfectly healthy direct-form
                // node threw "no vnext server" and its sing-box URL test was painted FAILED even
                // though one tap connected it. Accept both shapes; the direct form is simply the
                // settings object itself.
                val vnextServer = xraySettings.optJSONArray("vnext")?.optJSONObject(0)
                val settingsServer = xraySettings.takeIf {
                    it.optString("address").isNotBlank() && it.optInt("port", 0) > 0
                }
                val server = vnextServer ?: settingsServer
                    ?: error("$protocol outbound has neither a vnext[] server nor a direct {address,port} one")
                // The user may live under `server.users[0]` (classic) or, for direct-form imports,
                // on the server/settings object directly. Resolve every field across all three
                // locations so a partially populated user object never drops the whole node.
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
                    // VMess UDP parity: Xray's `packetEncoding` (`none`/`packetaddr`/`xudp`)
                    // maps to sing-box's `packet_encoding`. Without it a UDP-capable node
                    // silently loses its datagram channel on the sing-box engine.
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
                if (xraySettings.has("fragment") || xraySettings.has("noises") || xraySettings.has("redirect")) {
                    throw ConfigTranslationException("settings", "Xray freedom fragment/noises/redirect cannot be applied to a direct detour")
                }
                result.put("type", "direct")
            }
            else -> throw ConfigTranslationException("outbounds.protocol", "'$protocol' is not translatable to the pinned extended core")
        }

        SingBoxTransportTranslator.transport(stream, settings, notes)?.let { result.put("transport", it) }
        SingBoxTransportTranslator.tls(stream, settings, notes)?.let { tls ->
            if (result.optString("type") in setOf("hysteria", "hysteria2") || result.optJSONObject("transport")?.optString("type") == "quic") {
                if (tls.has("reality")) throw ConfigTranslationException("realitySettings", "REALITY cannot be applied to a QUIC transport")
                tls.remove("utls") // Xray also ignores uTLS fingerprints on QUIC; sing-box rejects them.
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
        if (outbound.optJSONObject("mux")?.optBoolean("enabled", false) == true ||
            outbound.optJSONObject("muxSettings")?.optBoolean("enabled", false) == true || settings.muxEnabled) {
            notes += "Xray Mux.Cool is not sing-box smux; optional Xray mux is disabled, not replaced with an incompatible wire protocol."
        }
        if (protocol !in setOf("freedom", "direct")) {
            require(result.optString("server").isNotBlank()) { "config-unsupported: settings.address: missing server" }
            require(result.optInt("server_port") in 1..65535) { "config-unsupported: settings.port: invalid port" }
        }
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
        resolverPool: List<String>,
        bootstrapDnsPort: Int
    ): JSONObject {
        val servers = JSONArray()
        val timeout = "${settings.singBoxConnectTimeoutSec.coerceIn(3, 20)}s"
        // Preserve evidence order. Prepending the configured pair here used to promote the
        // quarantined endpoints back to the front; adding unused transports was NOT fallback.
        val candidates = if (resolverPool.isNotEmpty()) resolverPool else listOf(
            settings.dnsPrimaryDoH.ifBlank { "https://1.1.1.1/dns-query" },
            settings.dnsSecondaryDoH.ifBlank { "https://8.8.8.8/dns-query" },
            "tls://9.9.9.9"
        )
        val pool = candidates.map(String::trim).filter(String::isNotBlank).distinct().take(3)
        require(pool.isNotEmpty()) { "No encrypted DNS resolver configured" }
        // type:local in the Android CLI does NOT call Android's resolver. The production
        // manager supplies a loopback bridge backed by ConnectivityManager / DnsResolver.
        servers.put(if (bootstrapDnsPort > 0) JSONObject().put("type", "udp").put("tag", DNS_LOCAL_TAG)
            .put("server", "127.0.0.1").put("server_port", bootstrapDnsPort)
            else JSONObject().put("type", "local").put("tag", DNS_LOCAL_TAG))
        servers.put(JSONObject().put("type", "hosts").put("tag", DNS_HOSTS_TAG))
        val remoteTags = JSONArray()
        pool.forEachIndexed { index, url ->
            val tag = "dns-remote-$index"
            servers.put(encryptedDnsServer(tag, url, PROXY_TAG))
            remoteTags.put(tag)
        }
        servers.put(JSONObject().put("type", "fallback").put("tag", DNS_DIRECT_TAG)
            .put("servers", JSONArray().put(DNS_LOCAL_TAG)).put("strategy", "sequential").put("timeout", timeout))
        // Bounded sequential fallback divides the overall timeout among at most three peers.
        // General browsing DNS can NEVER fall through to the direct/system resolver.
        servers.put(JSONObject().put("type", "fallback").put("tag", DNS_REMOTE_TAG)
            .put("servers", remoteTags).put("strategy", "sequential").put("timeout", timeout))
        val rules = JSONArray()
        profile.host.takeIf { it.isNotBlank() && !isLiteralAddress(it) }?.let { host ->
            rules.put(JSONObject().put("domain", JSONArray().put(host)).put("action", "route").put("server", DNS_LOCAL_TAG))
        }
        val implicit = RoutingEngine.implicitRules(settings)
        val directSites = implicit.directSiteTags.filter { settings.iranDomesticDirect || it != "ir" }
        if (directSites.isNotEmpty()) {
            rules.put(JSONObject().put("rule_set", JSONArray(directSites.map { geoTag(false, it) }))
                .put("action", "route").put("server", DNS_DIRECT_TAG))
        }
        // IP-only GeoIP sets cannot match an unanswered DNS question in 1.14. Never emit
        // the legacy address-filter rules here; routing matches IP answers separately.
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
                // Direct/bootstrap lookups never depend on the outbound they are building.
                if (!isLiteralAddress(host)) put("domain_resolver", DNS_LOCAL_TAG)
            }
    }

    private fun dnsStrategy(settings: AppSettings): String = when {
        !settings.ipv6Enabled -> "ipv4_only"
        settings.preferIpv6 -> "prefer_ipv6"
        else -> "prefer_ipv4"
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Routing
    // ─────────────────────────────────────────────────────────────────────────────

    private fun routeConfig(settings: AppSettings, notes: MutableList<String>, ruleSetPaths: Map<String, String>): JSONObject {
        val rules = JSONArray().put(JSONObject().put("action", "sniff"))
        if (settings.dnsHijackEnabled) {
            rules.put(JSONObject().put("port", 53).put("action", "hijack-dns"))
        }
        if (!settings.ipv6Enabled) rules.put(JSONObject().put("ip_cidr", JSONArray().put("::/0")).put("action", "reject"))
        val usedSets = linkedSetOf<String>()
        fun action(rule: JSONObject, outbound: RoutingOutbound): JSONObject = rule.apply {
            if (outbound == RoutingOutbound.BLOCK) put("action", "reject")
            else put("action", "route").put("outbound", if (outbound == RoutingOutbound.DIRECT) DIRECT_TAG else PROXY_TAG)
        }
        fun domainRules(raw: String, outbound: RoutingOutbound) {
            val fields = linkedMapOf<String, JSONArray>()
            splitTokens(raw).forEach { token ->
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
            val values = splitTokens(raw)
            if (values.isNotEmpty()) rules.put(action(JSONObject().put("ip_cidr", JSONArray(values)), outbound))
        }
        fun geoRule(ip: Boolean, tag: String, outbound: RoutingOutbound) {
            if (ip && tag.removePrefix("geoip:") == "private") {
                rules.put(action(JSONObject().put("ip_is_private", true), outbound))
            } else {
                val mapped = geoTag(ip, tag)
                usedSets += mapped
                rules.put(action(JSONObject().put("rule_set", JSONArray().put(mapped)), outbound))
            }
        }
        domainRules(settings.routeBlockDomains, RoutingOutbound.BLOCK)
        ipRule(settings.routeBlockIps, RoutingOutbound.BLOCK)
        domainRules(settings.routeProxyDomains, RoutingOutbound.PROXY)
        RoutingEngine.effectiveRules(settings).filter { it.enabled }.forEach { user ->
            val rule = JSONObject()
            when (user.kind) {
                RoutingRuleKind.DOMAIN -> {
                    // Mixed prefix kinds are OR alternatives; extra port/network criteria apply
                    // to the resulting logical OR, not to only one of its children.
                    val children = JSONArray()
                    splitTokens(user.matcher).forEach { token ->
                        val field = when {
                            token.startsWith("full:") -> "domain"
                            token.startsWith("regexp:") -> "domain_regex"
                            token.startsWith("domain:") -> "domain_suffix"
                            else -> "domain_keyword" // Xray's unprefixed domain rule is substring
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
                        val mapped = geoTag(user.kind == RoutingRuleKind.GEOIP, user.matcher)
                        usedSets += mapped
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
        usedSets.forEach { tag ->
            ruleSets.put(JSONObject().put("type", "local").put("tag", tag).put("format", "binary")
                .put("path", ruleSetPaths[tag] ?: "singbox-rules/$tag.srs"))
        }
        return JSONObject().put("rules", rules).put("rule_set", ruleSets).put("final", PROXY_TAG)
            .put("default_domain_resolver", DNS_LOCAL_TAG).put("default_http_client", HTTP_CLIENT_DIRECT_TAG)
    }

    internal fun geoTag(ip: Boolean, raw: String): String {
        val tag = raw.trim().lowercase().removePrefix("geoip:").removePrefix("geosite:")
        return when {
            ip && tag == "ir" -> RULE_SET_GEOIP_IR
            !ip && tag in setOf("ir", "category-ir") -> RULE_SET_GEOSITE_IR
            !ip && tag in setOf("ads", "category-ads-all", "geosite-ads", "ads-all") -> RULE_SET_ADS
            else -> throw ConfigTranslationException("routing.geo", "rule set '$raw' is not bundled for sing-box; choose a bundled ir/private/ads tag or use Xray")
        }
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
