package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.IranModePolicy
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
 * MARBLE_SOCKET_FLIGHT_V168 — physical-socket tuning (MPTCP, UDP fragment, Iran liveness, direct
 * hop tuning) is shared with the Xray writer through [CoreSocketPolicy].
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
     * MARBLE_FRAGMENT_PROFILES_V208 — the outbound protocols the pinned extended core accepts a
     * `multiplex` object on: exactly the four whose `*OutboundOptions` embed
     * `OutboundMultiplexOptions` in its own `option` files (vless, vmess, trojan, shadowsocks).
     *
     * This is a correctness gate, not a preference. sing-box decodes strictly: a `multiplex`
     * object on an http, socks, hysteria2 or tuic outbound is `json: unknown field`, and one
     * unknown field is a rejected config, which is a dead session rather than a ignored option.
     */
    val MARBLE_SMUX_PROTOCOLS: Set<String> = setOf("vless", "vmess", "trojan", "shadowsocks")

    /**
     * MARBLE_FAKE_IP_V184 — the sing-box `fakeip` DNS server (1.14 server form; the legacy
     * `dns.fakeip` object was removed in 1.14.0). In-process fake addresses for the app's A/AAAA
     * questions; the router restores the original domain per flow (`route/route.go`
     * prepareMatchMetadata) and the forced route `resolve` action resolves the real IP through
     * the normal DNS graph (where the fakeip transport is excluded, `allowFakeIP=false`), so the
     * proxy always dials the real address and no fake loop is possible.
     */
    const val DNS_FAKEIP_TAG = "dns-fakeip"

    /**
     * MARBLE_FAKE_IP_V184 — the fake address pool. The second /16 of IANA's reserved
     * 198.18.0.0/15 benchmarking block keeps roughly 65k addresses without overlapping this
     * TUN's own 198.18.0.1/32 interface address. It is shared with Xray so an engine switch cannot
     * change the address-space contract.
     */
    const val FAKE_IP_POOL = FakeIpPolicy.IPV4_POOL

    /** IP-literal DoH/DoT over DIRECT; never fall back to Android, local or plaintext DNS. */
    const val DNS_BOOTSTRAP_TAG = "dns-bootstrap"

    /**
     * Iranian DNS-injector / null-answer ranges that must never be dialled. Matches the Xray
     * `IRAN_POISON_BLOCK_IPS` list so both engines fail closed on the same block pages.
     */
    val IRAN_POISON_BLOCK_IPS = listOf(
        "10.10.34.0/24",
        "2001:4188:2:600::/64",
        "0.0.0.0",
        "::"
    )

    /**
     * MARBLE_SINGBOX_ANDROID_RUNTIME_V155 — the single named HTTP client every remote rule-set
     * download uses. See [routeConfig] for why it exists instead of `download_detour`.
     */
    const val HTTP_CLIENT_DIRECT_TAG = "marble-http-direct"

    /** The extended fork's own `parser` outbound reads the original share link itself. */
    const val STRATEGY_LINK = "link-parser"

    /**
     * MARBLE_SINGBOX_LINK_AUTHORITY_V156 — Marble reads the original share link with its own
     * [ProxyParser] and translates what it read. This is the second reader for the *same* truth,
     * so a fork parser that is behind on link syntax cannot kill a node on its own.
     */
    const val STRATEGY_LINK_TRANSLATED = "link-translated"

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

    /**
     * MARBLE_SINGBOX_LINK_AUTHORITY_V156 — every sing-box config this profile can be expressed
     * as, best first.
     *
     * A profile carries two truths: the **share link it was created from** (`vless://…`) and the
     * **Xray JSON** Marble derived from that link at import time. Until now the writer only ever
     * looked at the second one, so the extended core ran a translation of a translation and every
     * parameter Marble's translator does not model was silently dropped from the node the user
     * actually subscribed to. The link is the authority; the JSON is a cache of it.
     *
     * Three readers can serve a link, and one refusal must never kill the node:
     *
     *  1. [STRATEGY_LINK] — `{type: parser, link: …}`. The fork's own URI parser owns every
     *     parameter a link can express, including the ones this file has never heard of.
     *  2. [STRATEGY_LINK_TRANSLATED] — Marble re-reads the link with [ProxyParser] and translates
     *     the result, so a core reader that is behind on link syntax cannot kill the node either.
     *  3. [STRATEGY_TRANSLATED] — the stored Xray JSON, hop by hop. Still the only reader for a
     *     pasted JSON document and for chains the user built by hand.
     *
     * [AppSettings.singBoxPreferParser] chooses which end of that list leads. A pasted native
     * sing-box document short-circuits all of it: that document *is* the config.
     *
     * The manager walks this list and hands each entry to `sing-box check`; the first one the
     * core itself accepts carries the session.
     */
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

    // ─────────────────────────────────────────────────────────────────────────────
    // Readers
    // ─────────────────────────────────────────────────────────────────────────────

    /** One way of expressing a profile as sing-box outbounds, plus what it had to say. */
    private data class Candidate(
        val strategy: String,
        val outbounds: List<JSONObject>,
        val notes: List<String>
    )

    private data class CandidateSet(val candidates: List<Candidate>, val refusal: String)

    /**
     * MARBLE_SINGBOX_PINNED_PEER_V163 — why a certificate-pinning profile cannot run on the
     * extended core, or null when it can.
     *
     * The "connects on sing-box but no Internet" report was a VLESS/TCP/TLS node whose link
     * carried `pcs=<sha256>,…` (Xray `pinnedPeerCertSha256`, hex hash of the WHOLE leaf
     * certificate) and `vcn=<host>` (Xray `verifyPeerCertByName`) behind a fronted
     * `sni=spotify.com`. Xray honours those by skipping chain validation and matching the leaf
     * hash — the server is self-signed by design. The fork's `parser` outbound reads the same link,
     * silently ignores both keys, and verifies the certificate against the SNI with the system CA
     * store, so every handshake fails after the SOCKS inbound is already "up": the core is alive,
     * the tunnel is established, nothing ever completes. The two Marble translators already refuse
     * the JSON form (the fork only knows `certificate_public_key_sha256`, an SPKI hash that is not
     * derivable from a certificate hash, and `insecure: true` would discard the user's pin), but
     * the parser candidate ran first and *looked* like a success.
     *
     * The pin is a security promise the user made; it is never silently downgraded. The profile
     * is refused before the tunnel with the engine to use instead, and the same text reaches the
     * Engine page through [describe].
     */
    /**
     * MARBLE_SINGBOX_PINNED_COMPAT_V164 — pinned configs are no longer hard-refused. The
     * translator converts them to `tls.insecure: true` with a note explaining the downgrade.
     * The parser candidate is excluded (it silently ignores pins). This returns null for
     * all configs, letting the normal candidate-selection flow handle pinning through the
     * translation path.
     */
    fun pinnedPeerRefusal(profile: ProxyProfile): String? {
        // Pinned configs are now allowed: the translator handles them with insecure:true.
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

    /** True when the share-link query carries any `pcs` / `vcn` style verification key. */
    internal fun linkCarriesPin(link: String): Boolean {
        val params = ShareLinkParams.ofRawLink(link)
        val keys = TlsPinningPolicy.PINNED_SHA256_KEYS + TlsPinningPolicy.VERIFY_BY_NAME_KEYS
        return params.first(*keys.toTypedArray()).isNotBlank()
    }

    /**
     * MARBLE_REALITY_MLKEM_HANDSHAKE_V194 — true when the share link selects REALITY, explicitly
     * or by carrying its public key. The spelling mirrors [ProxyParser]'s own inference exactly
     * (`security`/`sec`, then `pbk`/`publicKey`/`realityPublicKey`), so the candidate set and the
     * importer can never disagree about which links these are.
     */
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
        // handles them with `tls.insecure: true`. The parser candidate is excluded for pinned
        // configs because sing-box's parser silently ignores pcs/vcn and would fail TLS
        // verification with no Internet.
        val link = shareLink(profile)
        val isPinned = link?.let { linkCarriesPin(it) } == true ||
            (profile.configJson.isNotBlank() && TlsPinningPolicy.configIsPinned(profile.configJson))
        // The pinned fork's URI parser only decodes base64url XHTTP `extra` and maps a subset of
        // its fields. Without a decoded xPaddingBytes it rejects its own outbound at check time;
        // with one it can silently discard session/obfuscation options. The translated readers
        // understand both extra formats and keep the complete wire configuration.
        val xhttpLink = link?.let {
            ShareLinkParams.ofRawLink(it).first("type", "network", "net")
                .lowercase() in setOf("xhttp", "splithttp")
        } == true

        // MARBLE_REALITY_MLKEM_HANDSHAKE_V194 — the pinned fork's link parser (`parser/link/*`
        // at the pinned commit) builds its REALITY options with only enabled/public_key/short_id:
        // `support_x25519mlkem768` stays false, so the core strips the hybrid share from the
        // ClientHello and every Xray >= v26.9.8 REALITY server answers with a decoy fallback that
        // surfaces as `reality verification failed`. Marble's own translation emits the flag (see
        // [SingBoxTransportTranslator]), so REALITY links skip the parser the same way XHTTP links
        // already do: a reader that cannot complete the handshake is not a fallback.
        val realityLink = link?.let { linkCarriesReality(it) } == true

        // The fork parser is excluded for pins (it ignores pcs/vcn), XHTTP (it loses extra
        // settings) and REALITY (it never emits the hybrid key share current servers require).
        // All three cases can pass schema validation but fail on the actual wire; prefer a
        // translation of the current link over a cached, potentially stale translation.
        val parser = if (isPinned || xhttpLink || realityLink) null else link?.let {
            Candidate(STRATEGY_LINK, listOf(parserOutbound(it, settings)), emptyList())
        }

        // Reader 2 — Marble reads the link itself and translates what it read.
        val linkNotes = mutableListOf<String>()
        if (xhttpLink) linkNotes += "XHTTP link uses Marble's lossless extra reader (the pinned core's link parser drops XHTTP options)."
        if (realityLink) linkNotes += "REALITY link skips the pinned core's link parser (it never emits the X25519MLKEM768 key share Xray >= v26.9.8 servers require); Marble's translation does."
        val fromLink = link
            ?.let { linkJson(it) }
            ?.let { json -> translatedCandidate(STRATEGY_LINK_TRANSLATED, json, settings, linkNotes, forTest) }
            ?.getOrNull()

        // Reader 3 — the stored Xray JSON. Its failure is the reason a JSON-only node is refused.
        val storedNotes = mutableListOf<String>()
        val storedResult = root?.let { translatedCandidate(STRATEGY_TRANSLATED, it, settings, storedNotes, forTest) }
        val stored = storedResult?.getOrNull()

        val ordered = when {
            xhttpLink -> listOfNotNull(fromLink, stored) // link extra outranks a stale cached translation
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

    /** The `{type: parser}` outbound: the extended fork reads the share link itself. */
    private fun parserOutbound(link: String, settings: AppSettings): JSONObject {
        // MARBLE_SOCKET_FLIGHT_V168 — the scheme tells the dial policy whether this parser
        // outbound opens QUIC/UDP (Hysteria2/TUIC/WireGuard) or a TCP socket.
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

    /**
     * Marble's own reading of a share link, as Xray JSON — the intermediate [translate] already
     * understands. This is what lets a link-only node (a scheme Marble's importer stores with a
     * blank `configJson`) and a profile whose stored JSON has drifted from its link still be
     * expressed without the fork's parser.
     */
    /**
     * How reader 2 turns a share link into the Xray JSON [translate] already understands.
     *
     * The production reader is [xrayJsonFromLink] → [ProxyParser], which parses URIs with
     * `android.net.Uri` and so cannot be called from a plain JVM unit test (the Android stub jar
     * throws `RuntimeException("Stub!")`, and this file's whole candidate model is unit-tested).
     * The seam is what keeps the *ordering* — the part this design is about — testable, and it
     * makes the parser's own syntax coverage a question for the instrumented suite instead of
     * something the candidate tests silently depend on. Defaults keep production on [ProxyParser].
     */
    @Volatile
    internal var linkJson: (String) -> JSONObject? = { link -> xrayJsonFromLink(link) }

    private fun xrayJsonFromLink(link: String): JSONObject? = runCatching {
        ProxyParser.parseInput(link)
            .singleOrNull()
            ?.configJson
            ?.takeIf { it.isNotBlank() }
            ?.let { JSONObject(it) }
    }.getOrNull()


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
        /** Kept for source compatibility; plaintext Android DNS bootstrap is no longer used. */
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

    /**
     * Writes everything around the proxy hop for one reader's outbounds: the local `mixed`
     * inbound hev-socks5-tunnel dials, the DNS graph, the routing rules and the Clash API the
     * native URL test drives. Identical for every strategy, which is what makes the URL test,
     * the routing policy and the measurement path mean the same thing on all of them.
     */
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
        val outbounds = JSONArray()
        candidate.outbounds.forEach { outbounds.put(it) }

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
            // MARBLE_SOCKET_FLIGHT_V168 — the direct hop carries the encrypted bootstrap DoH
            // (and direct-route traffic). It is a physical socket too, so it gets the same
            // MPTCP / UDP-fragment / liveness tuning instead of staying completely raw.
            .put(directOutbound(settings))
            .put(JSONObject().put("type", "block").put("tag", BLOCK_TAG))

        val root = JSONObject()
            .put(
                "log",
                JSONObject()
                    .put("level", singBoxLogLevel(settings))
                    .put("output", logPath)
                    // MARBLE_CORE_OPTIONS_V211 — the timestamp is the user's: a log read on the
                    // device is easier with one, a log written to a file for a bug report is easier
                    // without, and both are legitimate.
                    .put("timestamp", settings.singBoxLogTimestamp)
            )
            .put("dns", dnsConfig(settings, profile, resolverPool, underlayHasIpv6))
            .put("inbounds", inboundConfig(settings, socksPort))
            .put("outbounds", outbounds)
            .put(
                // The 1.14 default HTTP client is explicit. Managed rule sets themselves are
                // offline assets; this client never performs a startup rule download.
                "http_clients",
                JSONArray().put(
                    JSONObject()
                        .put("tag", HTTP_CLIENT_DIRECT_TAG)
                        .put("detour", DIRECT_TAG)
                )
            )
            .put("route", routeConfig(settings, notes, ruleSetPaths, underlayHasIpv6))
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
                                    .apply {
                                        // A cached app answer can outlive a core restart. Persist
                                        // the reverse fake-IP mapping too, otherwise sing-box
                                        // rejects that still-cached address as a missing record.
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

// <<< ANDROID_FINAL_SANITIZATION_START >>>
sanitizeAndroidCliConfig(root, notes)
// <<< ANDROID_FINAL_SANITIZATION_END >>>
// Native resource imports may override dial-time DNS. Restore the physical-family policy LAST.
enforcePhysicalDialPolicy(root, settings, underlayHasIpv6)

// MARBLE_CORE_OPTIONS_V211 — the user's own JSON, merged on top of everything this builder
// computed. It is the escape hatch that makes the settings surface "as complete as the core
// allows": `stats`, `api`, `metrics`, `observatory`, `reverse`, any `experimental` sub-object and
// every per-outbound dial field are reachable without a release. Four subsystems stay the app's
// (`inbounds`, `outbounds`, `route`, `dns`, `log`) because the tunnel, the local listener and the
// resolver graph are one document computed from the Routing page — a half-merged graph is not an
// option, it is a tunnel that starts and then does something nobody asked for.
val coreOptionPatch = CoreOptions.merge(root, settings.singBoxExtraJson)
if (coreOptionPatch.error.isNotBlank()) {
    notes += "Extra JSON refused: ${coreOptionPatch.error}"
    RuntimeDiagnostics.coreOption("singbox", "extra-json-refused", coreOptionPatch.error)
} else if (coreOptionPatch.changed) {
    notes += "Extra JSON applied: ${coreOptionPatch.applied.joinToString(", ")}"
    RuntimeDiagnostics.coreOption("singbox", "extra-json-merged", coreOptionPatch.applied.joinToString(","))
}

return Build(root.toString(), candidate.strategy, notes.distinct())
    }


    // <<< ADD_AFTER_ASSEMBLE >>>
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

        // این کلیدها در بعضی نسخه‌ها داخل route.rules نیز دیده می‌شوند.
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

        // MARBLE_PACKAGES_XML_ROOT_CAUSE_V157 — this used to hand-copy 5 of the 7 canonical
        // keys and silently omit `include_uid`/`exclude_uid`. A rule that reached the core with
        // either survived every candidate — connect *and* the throwaway URL-test/Real-delay
        // core alike, since both are built through this same sanitizer — and each one crashed
        // identically on `initialize package manager: read packages list: open
        // /data/system/packages.xml: permission denied` -> nil-pointer SIGSEGV. Sourcing the
        // list from SingBoxAndroidRuntime instead of a local literal means a future addition to
        // the canonical list reaches every callsite for free.
        removeKeys(
            rule,
            SingBoxAndroidRuntime.ANDROID_FORBIDDEN_RULE_KEYS,
            "route.rules[$index]",
            notes
        )

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
// <<< END_ADD_AFTER_ASSEMBLE >>>

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
        if (raw.isEmpty() || raw.length > 4096 || raw.contains('\n') || raw.contains('\r')) return null
        val scheme = raw.substringBefore("://", "").lowercase()
        if (scheme !in LINK_SCHEMES) return null
        // Also fixes links already stored by older versions, not only newly imported links.
        return ShareLinkNormalizer.normalize(raw)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Strategy 2 — Xray JSON → sing-box JSON
    // ─────────────────────────────────────────────────────────────────────────────

    private fun firstProxyOutbound(outbounds: JSONArray): JSONObject? {
        // First pass: non-infrastructure proxy protocol
        for (i in 0 until outbounds.length()) {
            val candidate = outbounds.optJSONObject(i) ?: continue
            val protocol = candidate.optString("protocol").lowercase()
            if (protocol in setOf("freedom", "direct", "blackhole", "block", "dns", "loopback")) continue
            return candidate
        }
        // Second pass: for serverless/direct configs, look for a freedom/direct outbound with fragment
        for (i in 0 until outbounds.length()) {
            val candidate = outbounds.optJSONObject(i) ?: continue
            val protocol = candidate.optString("protocol").lowercase()
            if (protocol in setOf("freedom", "direct") && hasFragment(candidate)) {
                return candidate
            }
        }
        // Third pass: if no other candidate exists, fall back to any freedom/direct outbound
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

    /**
     * Walks the Xray chain (`dialerProxy` / `proxySettings`) from the entry hop outwards and
     * returns the equivalent sing-box outbounds, each one detouring into the next.
     */
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

        // Serverless handling: if all hops in the chain are direct/freedom (e.g. Serverless-v50-fragA/B),
        // sing-box executes the direct dial natively; chaining direct-to-direct is redundant in sing-box.
        if (chain.all { it.optString("protocol").lowercase() in setOf("freedom", "direct") }) {
            notes += "Serverless direct/fragment chain mapped to sing-box direct outbound with full dial flight."
            val serverlessOutbound = JSONObject().put("tag", PROXY_TAG).put("type", "direct")
            applyDialTuning(serverlessOutbound, settings, protocolHint = "direct")
            return listOf(serverlessOutbound)
        }

        return chain.mapIndexed { index, hop ->
            val tag = if (index == 0) PROXY_TAG else "marble-hop-$index"
            // Entry dials THROUGH hop 1, hop 1 through hop 2. Never reverse this edge.
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

                // Hysteria's remaining transport controls are not decoration: dropping receive
                // windows, MTU discovery or port-hopping can make an otherwise valid node fail on
                // mobile networks. Translate both link-parser spellings and canonical JSON fields.
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
                // MARBLE_CORE_OPTIONS_V211 — an imported freedom hop's own `fragment` object has no
                // sing-box equivalent, and this engine must not pretend otherwise: the note says
                // what was left behind, and the import path is where the user sees it.
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
        /*
         * MARBLE_CORE_OPTIONS_V211 — the user's multiplex choice, written once.
         *
         * The pinned core accepts `multiplex` on exactly four outbound protocols (vless, vmess,
         * trojan, shadowsocks — `OutboundMultiplexOptions` in its own option files). Writing it
         * anywhere else is a config the core rejects at `sing-box check`, which kills the whole
         * session rather than ignoring the field, so the protocol set is a correctness gate and
         * not a preference: on any other protocol the request is *reported* — [marbleProtocolNote]
         * — instead of being silently written or silently dropped.
         *
         * Nothing here is derived any more. The object is the six fields the user set, in the
         * field names `option/multiplex.go` declares.
         */
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
        // MARBLE_SINGBOX_PROTOCOLS_V153 — sing-box's `network` field is a single string (`tcp` or
        // `udp`), not an array. Writing `["tcp","udp"]` made `sing-box check` reject every
        // translated config with a decoder error that the old doctor did not know. Omitting the
        // field is the correct way to say "both", and it is exactly what the core defaults to.
        // MARBLE_SOCKET_FLIGHT_V168 — only the terminal hop (no detour) opens a physical socket,
        // so only it carries dial fields. The protocol and transport hints tell the policy whether
        // that socket is QUIC/UDP (no TCP keep-alive) and which liveness profile applies.
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
     * MARBLE_SOCKET_FLIGHT_V168 — the single bridge to [CoreSocketPolicy] for a hop that opens
     * a physical socket: the proxy, the terminal hop of a translated chain and the direct hop.
     * Chained middle hops carry only `detour` and never reach this function.
     */
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

    /** The DIRECT outbound, tuned exactly like the proxy hop for the sockets it opens. */
    private fun directOutbound(settings: AppSettings): JSONObject =
        JSONObject()
            .put("type", "direct")
            .put("tag", DIRECT_TAG)
            .apply { applyDialTuning(this, settings, protocolHint = "direct") }

    // ─────────────────────────────────────────────────────────────────────────────
    // DNS
    // ─────────────────────────────────────────────────────────────────────────────

    private fun dnsConfig(
        settings: AppSettings,
        profile: ProxyProfile,
        resolverPool: List<String>,
        underlayHasIpv6: Boolean
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
        // MARBLE_RESOLVER_SINKHOLE_V163 — a domestic anti-sanction resolver (dns.shecan.ir …)
        // behind the PROXY detour is a DNS leak to an Iranian operator from the exit IP, and an
        // endpoint measured with an expired certificate cannot answer at any rank. Neither is
        // ever written; the stock pool covers the gap.
        val excluded = settings.measuredDnsExcludedEndpoints.split(',')
            .map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        fun allowed(url: String) = !ResolverEvidencePolicy.isDomesticResolver(url) && url.lowercase() !in excluded
        val filtered = candidates.map(String::trim).filter(String::isNotBlank).filter(::allowed).distinct()
        // The intelligence pool is emitted as given (its order is the evidence); the stock trio
        // only fills in when exclusion would otherwise leave the graph without a healthy peer.
        val pool = filtered.ifEmpty {
            listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query", "tls://9.9.9.9").filter(::allowed)
        }.take(3)
        require(pool.isNotEmpty()) { "No encrypted DNS resolver configured" }
        servers.put(JSONObject().put("type", "hosts").put("tag", DNS_HOSTS_TAG))
        // The pinned fakeip transport answers A and AAAA independently. With no inet6_range
        // the AAAA answer is empty and IPv6 can never be established from a fake address.
        if (FakeIpPolicy.isDnsPathArmed(settings)) {
            servers.put(JSONObject().put("type", "fakeip").put("tag", DNS_FAKEIP_TAG)
                .put("inet4_range", FAKE_IP_POOL).put("inet6_range", FakeIpPolicy.IPV6_POOL))
        }
        // Bootstrap the node hostname with IP-literal encrypted DIRECT resolvers ONLY. If they
        // are unavailable, fail closed: a local/Android DNS fallback would reveal the node name.
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
        // Even split/direct DNS goes through encrypted, IP-literal bootstrap; never OS DNS.
        servers.put(JSONObject().put("type", "fallback").put("tag", DNS_DIRECT_TAG)
            .put("servers", JSONArray().put(DNS_BOOTSTRAP_TAG))
            .put("strategy", "sequential").put("timeout", timeout))
        // Bounded sequential fallback divides the overall timeout among at most three peers.
        // General browsing DNS can NEVER fall through to the direct/system resolver.
        // MARBLE_RESOLVER_SINKHOLE_V163 — when this network has measured a decisively failing
        // endpoint in the pool, the peers race (`parallel`) exactly as the Xray hardener arms
        // `enableParallelQuery`: a sequential walk would otherwise pay the failing peer's whole
        // slice on every cold lookup before a healthy peer is even asked.
        val remoteStrategy = if (settings.adaptiveDnsEnabled && settings.measuredDnsParallel) "parallel" else "sequential"
        servers.put(JSONObject().put("type", "fallback").put("tag", DNS_REMOTE_TAG)
            .put("servers", remoteTags).put("strategy", remoteStrategy).put("timeout", timeout))
        val rules = JSONArray()
        profile.host.takeIf { it.isNotBlank() && !isLiteralAddress(it) }?.let { host ->
            rules.put(JSONObject().put("domain", JSONArray().put(host)).put("action", "route").put("server", DNS_BOOTSTRAP_TAG))
        }
        // MARBLE_FAKE_IP_V184 — every A/AAAA question the APP asks is answered from the fake
        // pool before it can reach a resolver, which is what makes the cold burst free. The
        // node-host rule above still wins, so VLESS bootstrap never sees a fake address. On the
        // ENGINE's own resolution paths (proxy dial, route `resolve`) the same rule is skipped —
        // the router walks rules with allowFakeIP=false there (dns/router.go), the fakeip
        // transport is excluded, and the domain falls through to the graph below: the Iranian
        // set to `dns-direct`, everything else to the final encrypted resolver over the proxy.
        // That exclusion is what keeps the chain from ever handing a fake address back to the
        // proxy. `final` therefore stays `dns-remote`: fakeip is the app's answer, not the
        // engine's.
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
                // Hostname DoH (AdGuard, etc.) bootstraps through encrypted-direct, never the
                // Iranian system resolver. IP-literal servers need no resolver of their own.
                if (!isLiteralAddress(host)) put("domain_resolver", DNS_BOOTSTRAP_TAG)
            }
    }

    /** Physical bootstrap is distinct from exit DNS. Never attempt an unavailable underlay
     * family or plaintext/system fallback; include at least one IPv6 peer on a dual-stack link. */
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
        // MARBLE_CORE_OPTIONS_V211 — `inbounds[].users` is the core's own credential list. It is
        // written only when the user set both halves: a username with no password is a config the
        // core refuses to decode, so a half-filled pair is reported by the settings screen (which
        // disables Save on it) rather than guessed at here.
        val users = inboundUsers(settings)
        val mixed = JSONObject()
            .put("type", "mixed")
            .put("tag", INBOUND_TAG)
            .put("listen", listen)
            .put("listen_port", socksPort)
            .put("tcp_fast_open", settings.tcpFastOpenEnabled)
        if (users != null) mixed.put("users", users)
        val inbounds = JSONArray().put(mixed)
        val httpPort = settings.singBoxHttpInboundPort
        if (httpPort in 1024..65535 && httpPort != socksPort) {
            val http = JSONObject()
                .put("type", "http")
                .put("tag", "http-in")
                .put("listen", listen)
                .put("listen_port", httpPort)
                // MARBLE_SOCKET_FLIGHT_V168 — parity with the mixed inbound.
                .put("tcp_fast_open", settings.tcpFastOpenEnabled)
            if (users != null) http.put("users", JSONArray(users.toString()))
            inbounds.put(http)
        }
        return inbounds
    }

    /**
     * The `users` array for the local inbounds, or null when the user wants no credential check.
     *
     * The core types this as a list of `{username, password}`, and an empty list is *not* the same
     * as an absent key: an empty array is accepted and means "no user can authenticate", which
     * would lock the phone out of its own proxy. So a half-filled pair yields null (no `users`
     * key at all) and both halves are required.
     */
    private fun inboundUsers(settings: AppSettings): JSONArray? {
        val username = settings.singBoxInboundUsername.trim()
        val password = settings.singBoxInboundPassword
        if (username.isBlank() || password.isBlank()) return null
        return JSONArray().put(JSONObject().put("username", username).put("password", password))
    }

    private fun dnsStrategy(settings: AppSettings): String =
        AddressFamilyPolicy.singBoxDestinationStrategy(settings)

    /** The core reads this for real node sockets, never for browsing through the proxy exit. */
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
                // Native imports can carry their own local/plaintext per-dial DNS resolver.
                // Overwrite it on EVERY physical dialer, including a parser link, a native
                // endpoint and a direct hop; route.default_domain_resolver alone is not enough.
                if (host.isNotBlank() || type in setOf("parser", "direct") || outbound.has("domain_resolver")) {
                    outbound.put("domain_resolver", physicalDomainResolver(settings, underlayHasIpv6))
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Routing
    // ─────────────────────────────────────────────────────────────────────────────

    private fun routeConfig(
        settings: AppSettings,
        notes: MutableList<String>,
        ruleSetPaths: Map<String, String>,
        underlayHasIpv6: Boolean
    ): JSONObject {
        val rules = JSONArray()
        if (settings.singBoxSniffEnabled) {
            val sniff = JSONObject().put("action", "sniff")
            // MARBLE_CORE_OPTIONS_V211 — `override_destination` is the one field of the action
            // that changes what the router sees: on, the destination is rewritten to the sniffed
            // domain before any rule matches; off, the rules keep matching the address the app
            // dialled. It is the user's switch because both readings are useful.
            if (settings.singBoxSniffOverrideDestination) sniff.put("override_destination", true)
            rules.put(sniff)
        }
        // MARBLE_FAKE_IP_V184 — fakeip makes `resolve` mandatory, not an Exclave option: a
        // restored fake flow reaches the router's pre-match with a DOMAIN destination, and
        // preMatchFlow rejects that flow unless DestinationAddresses was populated
        // (route/route.go: "a resolve action is required before routing to outbound"). The
        // resolve action is a no-op for IP destinations, so the only cost of arming it with
        // fakeip is one cached encrypted lookup per domain the proxy will dial anyway.
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
        // Fake IPs are private tokens and must NEVER match bypass-private/direct rules (the
        // IPv6 ULA pool is especially vulnerable to ip_is_private). Excluded families are
        // rejected above before fake IPs can be routed to the selected proxy.
        if (FakeIpPolicy.isDnsPathArmed(settings)) {
            rules.put(JSONObject().put("ip_cidr", JSONArray()
                .put(FakeIpPolicy.IPV4_POOL).put(FakeIpPolicy.IPV6_POOL))
                .put("action", "route").put("outbound", PROXY_TAG))
        }
        if (settings.iranModePolicy != IranModePolicy.OFF && settings.iranModeCountermeasures) {
            rules.put(JSONObject().put("ip_cidr", JSONArray(IRAN_POISON_BLOCK_IPS)).put("action", "reject"))
        }
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
                if (mapped == null) {
                    notes += unroutedGeoNote(ip, tag)
                    return
                }
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
                        if (mapped == null) {
                            notes += unroutedGeoNote(
                                user.kind == RoutingRuleKind.GEOIP,
                                user.matcher
                            )
                            return@forEach
                        }
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
            .put("default_domain_resolver", physicalDomainResolver(settings, underlayHasIpv6))
            .put("default_http_client", HTTP_CLIENT_DIRECT_TAG)
    }

    /**
     * The bundled rule set that answers [raw], or `null` when MarbleNG does not ship one.
     *
     * MARBLE_ROUTING_BOTH_CORES_V156 — this used to throw, and because routing is written for
     * every strategy the exception killed the whole config: one `geoip:us` rule in a shared
     * routing profile made *every* node unconnectable on the sing-box engine while the same
     * profile worked fine on Xray. A routing rule MarbleNG cannot honour is a rule it must say
     * it dropped, not a reason the engine will not start. The direction of the mistake is
     * deliberate: an unmatched destination falls through to `final`, which is the proxy, so
     * dropping a rule can only ever send traffic through the tunnel rather than leak it.
     */
    internal fun geoTag(ip: Boolean, raw: String): String? {
        val tag = raw.trim().lowercase().removePrefix("geoip:").removePrefix("geosite:")
        return when {
            ip && tag == "private" -> RULE_SET_GEOIP_PRIVATE
            ip && tag == "ir" -> RULE_SET_GEOIP_IR
            !ip && tag in setOf("ir", "category-ir") -> RULE_SET_GEOSITE_IR
            !ip && tag in setOf("ads", "category-ads-all", "geosite-ads", "ads-all") -> RULE_SET_ADS
            else -> null
        }
    }

    /** The note a dropped geo rule leaves behind, so the Engine page can name it. */
    /**
     * The note that stands in for a geo rule MarbleNG cannot honour on the sing-box engine.
     *
     * MARBLE_ROUTING_BOTH_CORES_V156 — the tag is reported the way the user typed it in
     * Settings, not the way [RoutingEngine] normalised it on the way here. `routeGeoIpTags =
     * "geoip:us"` reaches the writer as the bare token `us`, and a note naming only `us` gives
     * the user nothing to search their settings for, and does not even say whether the rule
     * that was dropped was a geoip one or a geosite one.
     */
    private fun unroutedGeoNote(ip: Boolean, raw: String): String {
        val typed = if (raw.contains(":")) raw else if (ip) "geoip:$raw" else "geosite:$raw"
        return "routing: '$typed' has no bundled sing-box rule set, so this one rule is not " +
            "applied on the sing-box engine (the same profile is routed in full on Xray)."
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
