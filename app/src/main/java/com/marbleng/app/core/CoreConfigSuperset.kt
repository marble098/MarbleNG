package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import org.json.JSONObject

// MARBLE_CORE_CONFIG_SUPERSET_V165
//
// ─────────────────────────────────────────────────────────────────────────────────────────────
// One authority for "can the core that is selected right now actually run this profile?"
// ─────────────────────────────────────────────────────────────────────────────────────────────
//
// The answer used to be assembled three times, in three places, and none of the three agreed:
//
//  · `MarbleVpnService.profileCompatibilityIssue()` re-implemented the pinned core's own
//    `infra/conf: vless without TLS or other encryption is prohibited unless the server address is
//    a private IP or domain` rule *with its own private-address list*, to refuse a profile before
//    the TUN was built;
//  · `ProfilePreflightValidator` quarantined a similar-but-different set from Smart Rank;
//  · `ProfileSecurityAuditor.rankEligibility()` removed the same nodes from the rank pool a third
//    time, with a third definition of "unsecured".
//
// A subscription whose nodes are `vless://…&type=tcp&security=none` was therefore refused three
// times over — 42 of 42 nodes in the reported session, one preflight event per retry — while the
// second core in the same APK (`sing-box extended`, which has no such rule) runs every one of
// them, and while the core's own prohibition has **two exits a client can legitimately use**:
//
//  1. the destination is private *by the core's definition*, which is a wider set than the app's
//     hand-rolled one (it includes `0.0.0.0/8`, `192.0.0.0/24`, `198.18.0.0/15`, multicast and
//     reserved space, `::/127`, `ff00::/8`, and the reserved `.test`/`.internal`/`.localdomain`
//     classes);
//  2. the outbound *is* encrypted, just not with TLS: `encryption=mlkem768x25519plus.*` (the
//     post-quantum VLESS handshake) makes the core's own check return early, because
//     `Encryption != "" && Encryption != "none"`. That class of node was being reported as
//     "Unsupported VLESS" by an app that looked at `security` only.
//
// This object is now the single place where that decision is made. [wire] classifies what the
// selected core will see; [verdict] turns the classification into connect/don't-connect plus the
// one sentence the product prints. The destination test is [CorePrivateEndpoint], a literal
// transcription of `geodata.GetPrivateIPMatcher()` / `geodata.GetPrivateDomainMatcher()` from the
// pinned tag, so the app and the core cannot drift apart again — a mirror that disagrees with what
// it mirrors is not a guard, it is a new outage.
object CoreConfigSuperset {

    /** What the payload of a core-checked outbound looks like on the wire. */
    enum class Wire {
        /** TLS or REALITY: the core's rule is satisfied by the transport. */
        ENCRYPTED,

        /**
         * `encryption=mlkem768x25519plus…` — post-quantum VLESS. No TLS layer, and yet the core's
         * own prohibition does not apply: the payload is authenticated and encrypted with a hybrid
         * ML-KEM 768 × X25519 handshake.
         */
        POST_QUANTUM,

        /** Cleartext payload to a destination the pinned core counts as private. */
        PLAINTEXT_PRIVATE,

        /** Cleartext payload to a public destination: the one shape this policy can refuse. */
        PLAINTEXT_PUBLIC,

        /** Nothing in the document is subject to the core's rule (VMess, SS, QUIC-native, link-only). */
        UNKNOWN
    }

    /** A preflight decision, plus exactly one user-facing sentence about it. */
    data class Verdict(
        val runnable: Boolean,
        val wire: Wire,
        val reason: String,
        val detail: String = "",
        /** A warning that does *not* block the connection: surfaced on Home and in diagnostics. */
        val note: String = ""
    ) {
        val refused: Boolean get() = !runnable
        val cleartext: Boolean get() = wire == Wire.PLAINTEXT_PUBLIC || wire == Wire.PLAINTEXT_PRIVATE
    }

    /**
     * The refusal text. It is deliberately the string the product has always shown — already
     * translated in [MarblePersianLexicon] and already recognised by Bug Finder — so switching the
     * policy off lands on wording users know instead of a new mystery string.
     */
    const val PLAINTEXT_REFUSAL = "Unsupported VLESS • pick a server with TLS/REALITY"

    /** Shown next to a live session or a node row when the payload is cleartext to a public host. */
    const val PLAINTEXT_PUBLIC_NOTE = "Unencrypted public node • Marble dials it, your provider can read it"

    /** Cleartext, but only inside a network the core itself counts as private. */
    const val PLAINTEXT_PRIVATE_NOTE = "Unencrypted node on a private network"

    /**
     * The environment variable MarbleNG's Xray build reads to let the *application* own the
     * plaintext-outbound decision: `scripts/inject-xray-config-superset.py` patches
     * `requiresTransportSecurity` to consult it, and `XrayManager.createProcessBuilder` sets it.
     * Three files, one spelling — the integrity audit fails the build if they ever disagree.
     */
    const val PLAINTEXT_POLICY_ENV = "MARBLE_ALLOW_UNENCRYPTED_PUBLIC_OUTBOUND"

    /**
     * Two whole sentences, not templates. The Persian lexicon is an exact-match table, so a
     * user-visible refusal must be a literal — an interpolated `${'$'}{method}` version of this text
     * would have been readable in English and untranslated forever.
     */
    const val CORE_GAP_TRANSPORT_REMOVED =
        "Xray removed the HTTP/QUIC transports this node speaks • sing-box extended runs them " +
            "(Settings → Tunnel core)"

    /** See [CORE_GAP_TRANSPORT_REMOVED]. */
    const val CORE_GAP_KCP_CAMOUFLAGE =
        "mKCP header camouflage is not implemented by the pinned Xray core • sing-box extended " +
            "runs it (Settings → Tunnel core)"

    /**
     * A VLESS `encryption` value the pinned core's parser rejects outright
     * (`VLESS users: unsupported "encryption"`) is a load failure, not a preference. Naming it here
     * is what keeps it from becoming a dead core with an empty log: [XrayConfigRepairs] fixes every
     * value that has no VLESS meaning (`auto`, `none/none`, absent), and this is what is left — a
     * claim this protocol cannot honour.
     */
    const val CORE_GAP_VLESS_ENCRYPTION =
        "VLESS encryption value this core cannot parse • use none or a post-quantum " +
            "mlkem768x25519plus value (Settings → Servers → Edit config)"

    /**
     * The outbounds the pinned core actually inspects. `validateOutboundTransportSecurity` covers
     * exactly these two config shapes; every other protocol is the core's own business, and
     * pretending otherwise is how whole subscriptions got hidden.
     */
    private val CORE_CHECKED_PROTOCOLS = setOf("vless", "trojan")

    private val INFRA_OUTBOUND_PROTOCOLS = setOf("freedom", "blackhole", "dns", "loopback", "warp")

    /** The security values that satisfy the core's rule by themselves. */
    private val TRANSPORT_SECURITY = setOf("tls", "reality")

    /**
     * Decide whether a profile may start a session on [engine].
     *
     * `settings.allowUnencryptedPublicOutbound` is the product's consent gate, not a core bug
     * workaround: with it on, MarbleNG ships the cleartext-capable core (see
     * `scripts/inject-xray-config-superset.py`) and *says so* — the note reaches the Servers row,
     * the rank pool and the diagnostic bundle. With it off the historical fail-closed behaviour is
     * preserved exactly, including this engine's refusal text, and it is refused on **both** cores:
     * `MarbleVpnService.profileCompatibilityIssue` asks the same question before it branches, so the
     * switch cannot mean "cleartext allowed, as long as the other core is selected".
     */
    fun verdict(profile: ProxyProfile, engine: CoreEngine, settings: AppSettings): Verdict =
        when (val wire = wire(profile)) {
            Wire.PLAINTEXT_PUBLIC ->
                if (settings.allowUnencryptedPublicOutbound) {
                    Verdict(
                        runnable = true,
                        wire = wire,
                        reason = "plaintext-accepted",
                        detail = cleartextDetail(engine),
                        note = PLAINTEXT_PUBLIC_NOTE
                    )
                } else {
                    Verdict(
                        runnable = false,
                        wire = wire,
                        reason = "plaintext-prohibited",
                        detail = "$PLAINTEXT_REFUSAL • or turn on Settings → Engine → " +
                            "Dial unencrypted nodes"
                    )
                }

            Wire.PLAINTEXT_PRIVATE ->
                Verdict(
                    runnable = true,
                    wire = wire,
                    reason = "private-cleartext",
                    detail = "The destination is private under the core's own matcher, so no " +
                        "transport security is required for it.",
                    note = PLAINTEXT_PRIVATE_NOTE
                )

            Wire.POST_QUANTUM -> Verdict(true, wire, "post-quantum-vless")
            Wire.ENCRYPTED -> Verdict(true, wire, "transport-secured")
            Wire.UNKNOWN -> Verdict(true, wire, "not-core-checked")
        }

    /**
     * Classify a profile from the config the cores will be handed. For a link-only profile (no
     * emitted Xray JSON — TUIC, AnyTLS, SSH) the imported link's own fields are the evidence.
     */
    fun wire(profile: ProxyProfile): Wire {
        val document = profile.configJson.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
            ?: return wireFromProfileFields(profile)

        if (NativeSingBoxConfig.isNative(document)) return wireFromNative(document)

        val outbounds = document.optJSONArray("outbounds") ?: return wireFromProfileFields(profile)
        var best: Wire? = null
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            val protocol = outbound.optString("protocol").trim().lowercase()
            if (protocol.isEmpty()) continue
            if (protocol in INFRA_OUTBOUND_PROTOCOLS) continue
            val wire = if (protocol in CORE_CHECKED_PROTOCOLS) wireOfOutbound(outbound, protocol) else Wire.UNKNOWN
            if (best == null || exposureRank(wire) < exposureRank(best!!)) best = wire
        }
        return best ?: wireFromProfileFields(profile)
    }

    /**
     * Most-exposed classification wins, because the core validates every outbound separately and a
     * cleartext *entry* hop of a two-hop chain is just as cleartext on the wire as a cleartext exit.
     * [Wire.UNKNOWN] sorts last on purpose: a chain whose exit hop is VLESS+TLS is not a plaintext
     * node just because it also carries a `freedom` hop.
     */
    private fun exposureRank(wire: Wire): Int = when (wire) {
        Wire.PLAINTEXT_PUBLIC -> 0
        Wire.PLAINTEXT_PRIVATE -> 1
        Wire.UNKNOWN -> 4
        Wire.POST_QUANTUM -> 2
        Wire.ENCRYPTED -> 3
    }

    /** Classify one Xray-shaped outbound the way the core's own check would. */
    internal fun wireOfOutbound(outbound: JSONObject, protocol: String = ""): Wire {
        val resolvedProtocol = protocol.ifBlank { outbound.optString("protocol").trim().lowercase() }
        val stream = outbound.optJSONObject("streamSettings")
        val security = stream?.optString("security").orEmpty().trim().lowercase().ifBlank { "none" }
        // `StreamConfig.Build` switches on the lowercased security and treats "" and "none"
        // identically: any other value (including a bogus one) never reaches the plaintext path.
        if (security in TRANSPORT_SECURITY) return Wire.ENCRYPTED

        val settings = outbound.optJSONObject("settings")
        if (resolvedProtocol == "vless" && isPostQuantumEncryption(vlessEncryption(settings))) {
            return Wire.POST_QUANTUM
        }
        // Any other non-empty `encryption` is also payload encryption as far as the core cares.
        if (resolvedProtocol == "vless") {
            val encryption = vlessEncryption(settings)
            if (encryption.isNotBlank() && encryption != "none") return Wire.ENCRYPTED
        }
        return if (CorePrivateEndpoint.matches(endpointHost(outbound, settings))) {
            Wire.PLAINTEXT_PRIVATE
        } else {
            Wire.PLAINTEXT_PUBLIC
        }
    }

    /**
     * Structural parity with the core's VLESS encryption parser
     * (`infra/conf/vless.go`: `mlkem768x25519plus.{native|xorpub|random}.{1rtt|0rtt}[.padding…]`),
     * including the raw-URL-base64 length test it applies to every long segment. Implemented
     * without a Base64 dependency so the JVM unit tests and the device agree on the answer.
     */
    internal fun isPostQuantumEncryption(encryption: String): Boolean {
        val parts = encryption.trim().split(".")
        if (parts.size < 4) return false
        if (parts[0] != "mlkem768x25519plus") return false
        if (parts[1] !in setOf("native", "xorpub", "random")) return false
        if (parts[2] !in setOf("1rtt", "0rtt")) return false
        for (index in 3 until parts.size) {
            val token = parts[index]
            // The core treats a short segment as padding and only requires key material to
            // decode to 32 or 1184 bytes.
            if (token.length >= 20) {
                val decoded = rawUrlBase64ByteLength(token)
                if (decoded != 32 && decoded != 1184) return false
            }
        }
        return true
    }

    /** Bytes a raw-URL-base64 string decodes to, or `-1` when it is not that alphabet at all. */
    internal fun rawUrlBase64ByteLength(token: String): Int {
        if (token.isEmpty()) return -1
        for (c in token) {
            val ok = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_'
            if (!ok) return -1
        }
        return token.length * 6 / 8
    }

    /**
     * `settings.encryption`, falling back to the classic `vnext[0].users[0].encryption` — exactly
     * the two places the core reads it from, in the core's own priority order.
     */
    private fun vlessEncryption(settings: JSONObject?): String {
        settings ?: return ""
        settings.optString("encryption").trim().takeIf { it.isNotEmpty() }?.let { return it }
        val user = settings.optJSONArray("vnext")
            ?.optJSONObject(0)
            ?.optJSONArray("users")
            ?.optJSONObject(0)
        return user?.optString("encryption").orEmpty().trim()
    }

    /** The address the core's `requiresTransportSecurity` will be handed for this outbound. */
    private fun endpointHost(outbound: JSONObject, settings: JSONObject?): String {
        outbound.optString("address").trim().takeIf { it.isNotEmpty() }?.let { return it }
        settings ?: return ""
        sequenceOf(
            settings.optString("address").trim(),
            settings.optString("server").trim(),
            settings.optJSONArray("vnext")?.optJSONObject(0)?.optString("address").orEmpty().trim(),
            settings.optJSONArray("servers")?.optJSONObject(0)?.optString("address").orEmpty().trim()
        ).firstOrNull { it.isNotEmpty() }?.let { return it }
        return ""
    }

    private fun wireFromProfileFields(profile: ProxyProfile): Wire {
        val security = profile.security.trim().lowercase()
        // `xtls` is a removed value in the pinned core, but it means "TLS underneath" wherever it
        // appears in an imported link, and treating it as plaintext would invent a warning.
        if (security in TRANSPORT_SECURITY || security == "xtls") return Wire.ENCRYPTED
        if (profile.scheme.trim().lowercase() !in CORE_CHECKED_PROTOCOLS) return Wire.UNKNOWN
        return if (CorePrivateEndpoint.matches(profile.host)) Wire.PLAINTEXT_PRIVATE else Wire.PLAINTEXT_PUBLIC
    }

    /** A native sing-box document carries its own TLS options; `tls.enabled` is the whole answer. */
    private fun wireFromNative(document: JSONObject): Wire = runCatching {
        val outbounds = NativeSingBoxConfig.outbounds(NativeSingBoxConfig.root(document))
        var best: Wire? = null
        outbounds.forEach { outbound ->
            val type = outbound.optString("type").trim().lowercase()
            if (type !in setOf("vless", "trojan")) return@forEach
            val tls = outbound.optJSONObject("tls")
            val enabled = tls?.optBoolean("enabled", false) == true || tls?.has("reality") == true
            val server = outbound.optString("server").trim()
            val wire = when {
                enabled -> Wire.ENCRYPTED
                CorePrivateEndpoint.matches(server) -> Wire.PLAINTEXT_PRIVATE
                else -> Wire.PLAINTEXT_PUBLIC
            }
            if (best == null || exposureRank(wire) < exposureRank(best!!)) best = wire
        }
        best ?: Wire.UNKNOWN
    }.getOrDefault(Wire.UNKNOWN)

    private fun cleartextDetail(engine: CoreEngine): String = when (engine) {
        CoreEngine.XRAY ->
            "The pinned Xray core ships with MarbleNG's outbound-policy patch, so a public " +
                "VLESS/Trojan node without TLS is dialled instead of being refused at preflight. " +
                "The payload is not encrypted."
        CoreEngine.SINGBOX ->
            "sing-box extended has no transport-security requirement for these outbounds, so the " +
                "node is dialled as written. The payload is not encrypted."
    }

    /**
     * True when the product will dial a cleartext public node at all. Both the VPN preflight and
     * the rank-pool eligibility ask this one question instead of each keeping its own copy.
     */
    fun dialsPlaintextPublicNodes(settings: AppSettings): Boolean = settings.allowUnencryptedPublicOutbound

    /**
     * A config shape the **pinned Xray core** cannot express at all, named with the engine that can.
     *
     * This is not a preference and it is not a heuristic about what the user meant: it is a set of
     * two facts about the core MarbleNG compiles, each verified against the pinned tag.
     *
     *  · `TransportProtocol.Build` answers `h2`/`h3`/`http` and `quic` with
     *    `PrintRemovedFeatureError`, i.e. the config cannot load;
     *  · `KCPConfig.Build` parses `header`/`seed` and then never reads them, so a camouflaged mKCP
     *    server would be dialled without its camouflage and the handshake would fail after connect —
     *    a "connected, no Internet" session, which is the worst possible outcome for a user.
     *
     * For both, `sing-box extended` implements the shape natively, so the honest answer is "not on
     * this core, on the other one" — named in the same sentence the user reads. Before this existed,
     * the first shape was emitted as a config the core rejected at startup and the second was
     * emitted with the camouflage silently dropped.
     */
    fun coreGapIssue(profile: ProxyProfile, engine: CoreEngine): String? {
        if (engine != CoreEngine.XRAY) return null
        val document = profile.configJson.trim()
            .takeIf { it.startsWith("{") }
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
        var method = ""
        var headerType = ""
        val outbounds = document?.optJSONArray("outbounds")
        if (outbounds != null) {
            for (index in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(index) ?: continue
                val stream = outbound.optJSONObject("streamSettings") ?: continue
                if (method.isEmpty()) {
                    val resolved = stream.optString("method")
                        .ifBlank { stream.optString("network") }
                        .trim()
                        .lowercase()
                    if (resolved.isNotEmpty()) method = resolved
                }
                if (headerType.isEmpty()) {
                    val header = stream.optJSONObject("kcpSettings")?.optJSONObject("header")
                        ?: stream.optJSONObject("rawSettings")?.optJSONObject("header")
                    val type = header?.optString("type").orEmpty().trim().lowercase()
                    if (type.isNotEmpty()) headerType = type
                }
            }
        }
        if (method.isEmpty()) {
            method = profile.transport.trim().lowercase()
        }
        if (headerType.isEmpty()) {
            // Only the parameter that *names* a camouflage type counts: `seed` is its key, and a
            // link that carries a seed without a type is asking for `none`.
            headerType = ShareLinkParams.ofRawLink(profile.raw).get("headerType").trim().lowercase()
        }
        return when {
            method in setOf("http", "h2", "h3", "quic") -> CORE_GAP_TRANSPORT_REMOVED
            method in setOf("mkcp", "kcp") && headerType.isNotEmpty() && headerType != "none" ->
                CORE_GAP_KCP_CAMOUFLAGE
            vlessEncryptionUnparseable(document) -> CORE_GAP_VLESS_ENCRYPTION
            else -> null
        }
    }

    /**
     * True when a VLESS outbound names an `encryption` the pinned core's `VLESS users` parser would
     * reject. Values [XrayConfigRepairs] rewrites on the way in are not reported here: refusing a
     * config that Marble itself is about to repair would be a bug dressed as a policy.
     */
    private fun vlessEncryptionUnparseable(document: JSONObject?): Boolean {
        val outbounds = document?.optJSONArray("outbounds") ?: return false
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            if (outbound.optString("protocol").trim().lowercase() != "vless") continue
            val settings = outbound.optJSONObject("settings") ?: continue
            val declared = settings.optString("encryption").trim().ifBlank {
                settings.optJSONArray("vnext")?.optJSONObject(0)?.optJSONArray("users")
                    ?.optJSONObject(0)?.optString("encryption").orEmpty().trim()
            }
            if (declared.isEmpty()) continue
            if (declared.lowercase() in XrayConfigRepairs.VLESS_MEANINGLESS_ENCRYPTION) continue
            if (isPostQuantumEncryption(declared)) continue
            return true
        }
        return false
    }
}

/**
 * MARBLE_CORE_CONFIG_SUPERSET_V165 — the pinned core's own privacy boundary, transcribed.
 *
 * `infra/conf/xray.go` decides whether an outbound needs transport security by asking two matchers
 * built in `common/geodata/consts.go`:
 *
 * ```go
 * func requiresTransportSecurity(address *Address) bool {
 *     if address.Family().IsIP() {
 *         return !geodata.GetPrivateIPMatcher().Match(address.IP())
 *     }
 *     domain := strings.TrimSuffix(strings.ToLower(address.Domain()), ".")
 *     return !geodata.GetPrivateDomainMatcher().MatchAny(domain)
 * }
 * ```
 *
 * with the IP list `0/8, 10/8, 100.64/10, 127/8, 169.254/16, 172.16/12, 192.0.0/24, 192.0.2/24,
 * 192.88.99/24, 192.168/16, 198.18/15, 198.51.100/24, 203.0.113/24, 224/3, ::/127, fc00::/7,
 * fe80::/10, ff00::/8` and the domain rules `lan, localdomain, example, invalid, localhost, test,
 * local, home.arpa, internal` as *domain suffixes*, plus `^[a-z]([a-z0-9-]{0,61}[a-z0-9])?$` for
 * dotless names.
 *
 * MarbleNG used to keep a shorter list of its own. Every CIDR it forgot was a node the core would
 * have dialled and the app refused; every class it forgot to mirror was a node ranked, probed and
 * then killed at process start with "Xray rejected the generated configuration". One shared copy of
 * the real boundary fixes both.
 */
object CorePrivateEndpoint {

    /**
     * A CIDR base written the way the core's `geodata/consts.go` spells it — four octets — and
     * packed into an Int. Packing is done here rather than with a 0x80000000-and-above hex literal
     * because such a literal has no Int value in Kotlin's inference and turns the table into a
     * `Number` mixture.
     */
    private fun v4(first: Int, second: Int, third: Int, fourth: Int): Int =
        (first shl 24) or (second shl 16) or (third shl 8) or fourth

    private val PRIVATE_V4: List<Pair<Int, Int>> = listOf(
        v4(0, 0, 0, 0) to 8,        // 0.0.0.0/8
        v4(10, 0, 0, 0) to 8,       // 10.0.0.0/8
        v4(100, 64, 0, 0) to 10,    // 100.64.0.0/10
        v4(127, 0, 0, 0) to 8,      // 127.0.0.0/8
        v4(169, 254, 0, 0) to 16,   // 169.254.0.0/16
        v4(172, 16, 0, 0) to 12,    // 172.16.0.0/12
        v4(192, 0, 0, 0) to 24,     // 192.0.0.0/24
        v4(192, 0, 2, 0) to 24,     // 192.0.2.0/24
        v4(192, 88, 99, 0) to 24,   // 192.88.99.0/24
        v4(192, 168, 0, 0) to 16,   // 192.168.0.0/16
        v4(198, 18, 0, 0) to 15,    // 198.18.0.0/15
        v4(198, 51, 100, 0) to 24,  // 198.51.100.0/24
        v4(203, 0, 113, 0) to 24,   // 203.0.113.0/24
        v4(224, 0, 0, 0) to 3       // 224.0.0.0/3 — multicast and reserved space
    )

    private val PRIVATE_DOMAIN_SUFFIXES = setOf(
        "lan", "localdomain", "example", "invalid", "localhost", "test", "local", "home.arpa", "internal"
    )

    /** `regexp:^[a-z]([a-z0-9-]{0,61}[a-z0-9])?$` — the core's dotless-name rule. */
    private val DOTLESS = Regex("^[a-z]([a-z0-9-]{0,61}[a-z0-9])?$")

    /**
     * True when the pinned core treats this endpoint as private, i.e. when it does **not** require
     * transport security for it. An empty host is not private: there is nothing to judge, and the
     * core would refuse the config for the missing address anyway.
     */
    fun matches(raw: String): Boolean {
        val host = raw.trim().removePrefix("[").removeSuffix("]").lowercase().trimEnd('.')
        if (host.isEmpty()) return false

        parseIpv4(host)?.let { return isPrivateIpv4Octets(it) }
        parseIpv6(host)?.let { return isPrivateIpv6Bytes(it) }

        // An IPv4-mapped IPv6 literal (`::ffff:10.0.0.1`) is judged as the v4 address it carries,
        // matching how the core's netip-backed matcher answers for that family.
        parseMappedIpv4(host)?.let { return isPrivateIpv4Octets(it) }

        return isPrivateDomainName(host)
    }

    /** True for the v4 dotted-quad form only; a domain is not an address here. */
    fun parseIpv4(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for (index in 0 until 4) {
            val token = parts[index]
            if (token.isEmpty() || token.length > 3) return null
            if (!token.all { it in '0'..'9' }) return null
            val value = token.toIntOrNull() ?: return null
            if (value > 255) return null
            octets[index] = value
        }
        return octets
    }

    fun isPrivateIpv4Octets(octets: IntArray): Boolean {
        if (octets.size != 4) return false
        val value = (octets[0] shl 24) or (octets[1] shl 16) or (octets[2] shl 8) or octets[3]
        return PRIVATE_V4.any { (base, bits) ->
            val mask = if (bits >= 32) -1 else (-1 shl (32 - bits))
            (value and mask) == (base and mask)
        }
    }

    /** A full 16-byte IPv6 literal, `::` compression and an IPv4 tail included. */
    fun parseIpv6(host: String): ByteArray? {
        var value = host.trim().lowercase().removePrefix("[").removeSuffix("]")
        if (!value.contains(':')) return null

        // Replace a trailing dotted-quad with the two hex groups it stands for.
        val lastColon = value.lastIndexOf(':')
        if (lastColon >= 0 && lastColon < value.length - 1) {
            val tail = value.substring(lastColon + 1)
            if (tail.contains('.')) {
                val v4 = parseIpv4(tail) ?: return null
                val head = (v4[0] shl 8) or v4[1]
                val rest = (v4[2] shl 8) or v4[3]
                value = value.substring(0, lastColon + 1) + head.toString(16) + ":" + rest.toString(16)
            }
        }

        val groups = ArrayList<Int>(8)
        val double = value.indexOf("::")
        if (double >= 0) {
            if (value.indexOf("::", double + 1) >= 0) return null
            val left = value.substring(0, double)
            val right = value.substring(double + 2)
            val leftGroups = if (left.isEmpty()) emptyList() else left.split(':')
            val rightGroups = if (right.isEmpty()) emptyList() else right.split(':')
            if (leftGroups.any { parseHexGroup(it) == null }) return null
            if (rightGroups.any { parseHexGroup(it) == null }) return null
            val missing = 8 - leftGroups.size - rightGroups.size
            if (missing < 0) return null
            leftGroups.forEach { groups += parseHexGroup(it) ?: return null }
            repeat(missing) { groups += 0 }
            rightGroups.forEach { groups += parseHexGroup(it) ?: return null }
        } else {
            val parts = value.split(':')
            if (parts.size != 8) return null
            parts.forEach { groups += parseHexGroup(it) ?: return null }
        }
        if (groups.size != 8) return null

        val bytes = ByteArray(16)
        groups.forEachIndexed { index, group ->
            bytes[index * 2] = ((group ushr 8) and 0xFF).toByte()
            bytes[index * 2 + 1] = (group and 0xFF).toByte()
        }
        return bytes
    }

    private fun parseHexGroup(token: String): Int? {
        if (token.isEmpty() || token.length > 4) return null
        if (!token.all { it in '0'..'9' || it in 'a'..'f' }) return null
        return token.toInt(16)
    }

    private fun parseMappedIpv4(host: String): IntArray? {
        if (!host.startsWith("::ffff:") && !host.startsWith("::FFFF:")) return null
        val tail = host.substringAfterLast(':')
        return parseIpv4(tail)
    }

    fun isPrivateIpv6Bytes(bytes: ByteArray): Boolean {
        if (bytes.size != 16) return false
        // ::/127 — the unspecified address and the loopback address.
        val highAllZero = (0 until 15).all { bytes[it].toInt() == 0 }
        if (highAllZero && (bytes[15].toInt() and 0xFF) <= 1) return true
        // fc00::/7 — unique local addresses.
        if ((bytes[0].toInt() and 0xFE) == 0xFC) return true
        // fe80::/10 — link-local unicast.
        if ((bytes[0].toInt() and 0xFF) == 0xFE && (bytes[1].toInt() and 0xC0) == 0x80) return true
        // ff00::/8 — multicast.
        if ((bytes[0].toInt() and 0xFF) == 0xFF) return true
        // ::ffff:0:0/96 — an IPv4-mapped literal is judged as the v4 address it carries, which is how
        // the core answers it too: the geodata matcher runs on `netip.Addr` after `Unmap()`, so
        // `::ffff:10.0.0.1` is private and `::ffff:8.8.8.8` is not. Without this branch the app would
        // call a VPN-supplied, mapped private address public and refuse the node the core accepts.
        if (isIpv4Mapped(bytes)) {
            return isPrivateIpv4Octets(
                intArrayOf(
                    bytes[12].toInt() and 0xFF,
                    bytes[13].toInt() and 0xFF,
                    bytes[14].toInt() and 0xFF,
                    bytes[15].toInt() and 0xFF
                )
            )
        }
        return false
    }

    /** `::ffff:0:0/96`, checked on the bytes rather than on the text, so every spelling lands here. */
    private fun isIpv4Mapped(bytes: ByteArray): Boolean =
        (0 until 10).all { bytes[it].toInt() == 0 } &&
            (bytes[10].toInt() and 0xFF) == 0xFF &&
            (bytes[11].toInt() and 0xFF) == 0xFF

    /** The core's domain half: `Domain_Domain` suffix rules plus the dotless regexp. */
    fun isPrivateDomainName(host: String): Boolean {
        val name = host.trim().lowercase().trimEnd('.')
        if (name.isEmpty()) return false
        if (PRIVATE_DOMAIN_SUFFIXES.any { name == it || name.endsWith(".$it") }) return true
        return DOTLESS.matches(name)
    }

    /** Only used by the diagnostic plane, to explain a verdict in one token. */
    fun classify(raw: String): String = if (matches(raw)) "private" else "public"
}
