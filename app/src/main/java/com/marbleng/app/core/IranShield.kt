package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.WorkloadProfile
import kotlin.math.max
import kotlin.math.min

/**
 * Iran Mode's countermeasure engine.
 *
 * [IranModeDetector] answers "which Iranian network am I on and how is it filtering right now".
 * IranShield turns that answer into concrete Xray/tunnel policy: TLS record shredding profiles,
 * resolver selection, domestic-direct routing, transport preference and failover aggressiveness.
 *
 * Every decision here is a documented response to a documented filtering technique — nothing is
 * enabled speculatively, because each countermeasure costs latency, battery or both.
 */
object IranShield {
    // MARBLE_TRANSPORT_AWARE_FRAGMENT_V50

    /** 1 = light touch, 2 = full DPI evasion, 3 = clampdown/blackout posture. */
    fun tier(state: IranModeState): Int {
        if (!state.active) return 0
        val severity = state.severity
        val techniques = state.techniques
        return when {
            CensorTechnique.NATIONAL_INTRANET in techniques ||
                CensorTechnique.PROTOCOL_ALLOWLIST in techniques ||
                severity == FilterSeverity.EXTREME -> 3

            CensorTechnique.SNI_FILTERING in techniques ||
                CensorTechnique.TCP_RESET in techniques ||
                severity == FilterSeverity.HEAVY -> 2

            else -> 1
        }
    }

    private data class FragmentProfile(
        val packets: String,
        val length: String,
        val interval: String,
        val description: String
    )

    private fun fragmentProfile(tier: Int): FragmentProfile = when (tier) {
        3 -> FragmentProfile(
            "1-3", "5-15", "15-30",
            "Maximum bounded stream slicing • first three writes split into 5-15 byte chunks"
        )
        2 -> FragmentProfile(
            "1-3", "10-30", "10-20",
            "Aggressive TLS record shredding • first three records split into 10-30 byte chunks"
        )
        else -> FragmentProfile(
            "tlshello", "100-200", "10-20",
            "ClientHello fragmentation • splits the SNI across TCP segments"
        )
    }

    /**
     * Rewrites the effective settings used for a connection while Iran Mode is active.
     *
     * @param geoIpReady whether geoip.dat is present; domestic-direct routing is only enabled when
     *        Xray can actually resolve the `ir` country set, otherwise the core would fail to start.
     */
    fun apply(
        base: AppSettings,
        profile: ProxyProfile?,
        state: IranModeState,
        geoIpReady: Boolean
    ): AppSettings {
        if (!state.active) return base

        val tier = tier(state)
        val fragment = fragmentProfile(tier)
        val autoFragment = shouldAutoFragment(profile)
        val udpBlocked = CensorTechnique.UDP_BLOCKED in state.techniques
        val cellular = state.isp?.kind == IranIspKind.MOBILE

        var next = if (!base.iranModeCountermeasures) base else base.copy(
            // --- Anti-DPI transport shaping -------------------------------------------------
            // Clear HTTP-like transports already have their own framing. Do not mutate
            // XHTTP/VLESS-ENC first writes merely because Iran Mode is active. Explicit user
            // Fragment settings are still preserved via base.*.
            fragmentEnabled = if (autoFragment) true else base.fragmentEnabled,
            fragmentPackets = if (autoFragment) fragment.packets else base.fragmentPackets,
            fragmentLength = if (autoFragment) fragment.length else base.fragmentLength,
            fragmentInterval = if (autoFragment) fragment.interval else base.fragmentInterval,
            adaptiveFragmentEnabled =
                if (autoFragment) true else base.adaptiveFragmentEnabled,

            // --- Encrypted resolution ---------------------------------------------------------
            // Preserve Marble Intelligence's network-scoped measured order. Hard-coding Google
            // first here overrode a learned healthy resolver on every Iran Mode connection and
            // caused repeated DoH deadline bursts in the attached runtime log.
            dnsHijackEnabled = true,
            adaptiveDnsEnabled = true,

            // --- Failover posture -------------------------------------------------------------
            // The Marble Intelligence master switch is intentionally left to the user; only the
            // per-connection resilience knobs are raised here.
            raceConnectEnabled = true,
            raceWidth = max(base.raceWidth, 4),
            smartFallbackEnabled = true,
            fallbackCount = max(base.fallbackCount, 5),
            networkChangeRecoveryEnabled = true,
            autoReconnectAfterKillSwitch = true,
            benchTimeoutSec = max(base.benchTimeoutSec, 12),
            tcpPrecheckTimeoutMs = max(base.tcpPrecheckTimeoutMs, 2_500),
            optimizerSwitchCooldownSec = min(base.optimizerSwitchCooldownSec, 180),
            optimizerIntervalSec = min(base.optimizerIntervalSec, 150)
        )

        if (base.iranModeCountermeasures) {
            // Mobile DPI stacks are far less tolerant of large records; keep the TUN under the
            // fragmentation ceiling so shredded ClientHellos are never reassembled downstream.
            val mtuCeiling = when {
                tier >= 3 -> 1380
                cellular -> 1400
                else -> 1420
            }
            next = next.copy(mtuMax = min(next.mtuMax, mtuCeiling))

            // QUIC/UDP is dropped nationally during clampdowns; failing it fast makes apps fall back
            // to TCP through the tunnel instead of stalling on retransmits.
            if (udpBlocked || tier >= 3) {
                next = next.copy(muxUdp443 = "reject")
            }

            if (base.workloadProfile == WorkloadProfile.AUTO && tier >= 2) {
                next = next.copy(workloadProfile = WorkloadProfile.STEALTH)
            }

            // XTLS Vision negotiates its own flow control; multiplexing on top of it is both slower
            // and a stronger fingerprint, so it is never left on for a Vision/REALITY path.
            if (profile != null && usesVisionOrReality(profile)) {
                next = next.copy(muxEnabled = false)
            }
        }

        // Domestic traffic stays off the tunnel: it is faster, it keeps Iranian banking/government
        // services working, and it keeps tunnel volume down — endpoint IPs are graylisted by
        // transferred volume, so not tunnelling domestic traffic directly extends node lifetime.
        if (base.iranDomesticDirect) {
            next = if (geoIpReady) {
                next.copy(
                    routingMode = if (base.routingMode == RoutingMode.CUSTOM) RoutingMode.CUSTOM
                    else RoutingMode.GEO_DIRECT,
                    routeGeoIpTags = mergeTokens(base.routeGeoIpTags, listOf("private", "ir")),
                    routeBypassPrivate = true
                )
            } else {
                next.copy(
                    routingMode = if (base.routingMode == RoutingMode.PROXY_ALL) RoutingMode.BYPASS_PRIVATE
                    else base.routingMode,
                    routeDirectIps = mergeTokens(
                        base.routeDirectIps,
                        IranNetworkRegistry.PUBLIC_DOMESTIC_RESOLVERS
                    ),
                    routeBypassPrivate = true
                )
            }
        }

        return next
    }

    /**
     * Transport preference bias, in predicted-score points, used to order candidates while Iran Mode
     * is active. Encodes what independent measurement work reports as surviving Iranian DPI.
     */
    fun profileBias(profile: ProxyProfile, state: IranModeState): Double {
        if (!state.active) return 0.0
        var bias = 0.0

        val security = profile.security.lowercase()
        val transport = profile.transport.lowercase()
        val scheme = profile.scheme.lowercase()
        val raw = profile.raw.lowercase()
        val vlessEncrypted =
            scheme == "vless" &&
                raw.contains("encryption=") &&
                !raw.contains("encryption=none")

        // REALITY/TLS keep the strongest transport-camouflage bias. VLESS Encryption protects
        // payloads and permits public security=none, but it does not look like ordinary HTTPS.
        if (security.contains("reality")) bias += 26.0
        else if (security.contains("tls")) bias += 10.0
        else if (vlessEncrypted) bias -= 4.0
        else bias -= 18.0

        if (raw.contains("flow=xtls-rprx-vision")) bias += 12.0

        // CDN-frontable transports keep working when the origin IP is graylisted.
        if (transport.contains("ws") || transport.contains("websocket") ||
            transport.contains("httpupgrade") || transport.contains("xhttp") ||
            transport.contains("splithttp") || transport.contains("grpc")
        ) {
            bias += 12.0
        }

        // The national gateway only allows 53/80/443 during clampdowns.
        when (profile.port) {
            443 -> bias += 12.0
            80, 8080, 2053, 2083, 2087, 2096, 8443 -> bias += 4.0
            else -> if (CensorTechnique.PROTOCOL_ALLOWLIST in state.techniques) bias -= 22.0
        }

        // An IP-literal endpoint cannot be broken by DNS poisoning of the endpoint hostname.
        if (isIpLiteral(profile.host)) bias += 8.0

        // UDP transports are unusable when datagram transit is blocked.
        val udpTransport = scheme.contains("hysteria") || transport.contains("hysteria") ||
            transport.contains("kcp") || transport.contains("quic") || scheme.contains("wireguard")
        if (udpTransport) {
            bias -= if (CensorTechnique.UDP_BLOCKED in state.techniques || tier(state) >= 3) 45.0 else 8.0
        }

        if (scheme == "vmess" && !security.contains("tls") && !security.contains("reality")) bias -= 14.0
        if (scheme == "socks" || scheme == "http") bias -= 30.0

        return bias
    }

    /** Human-readable list of what Iran Mode is doing, shown in the UI. */
    fun countermeasures(state: IranModeState): List<String> {
        if (!state.active) return emptyList()
        val tier = tier(state)
        val out = mutableListOf<String>()

        out += fragmentProfile(tier).description
        out += "Encrypted DoH resolution with measured provider order and bounded serial fallback"
        out += "Plaintext :53 hijacked into the tunnel so the ISP resolver cannot inject block pages"
        out += "Transport preference: REALITY/XTLS-Vision and CDN-frontable transports on port 443"
        out += "Endpoint hostnames resolved inside Xray only — no lookups leak to Iranian resolvers"
        out += "Wide connection race with at least five standby routes and fast blackout failover"
        out += "Domestic (geoip:ir) traffic routed direct to cut tunnel volume and extend node lifetime"

        if (CensorTechnique.UDP_BLOCKED in state.techniques || tier >= 3) {
            out += "QUIC/UDP-443 rejected at the tunnel so apps fall back to TCP instead of stalling"
        }
        if (CensorTechnique.PROTOCOL_ALLOWLIST in state.techniques) {
            out += "Non-allowlisted destination ports deprioritised — only 80/443 style paths are raced"
        }
        if (CensorTechnique.NATIONAL_INTRANET in state.techniques) {
            out += "Blackout posture: continuous re-probing while only the national network is reachable"
        }
        if (CensorTechnique.THROTTLING in state.techniques) {
            out += "Extended handshake timeouts tuned for throttled international transit"
        }
        if (state.isp?.kind == IranIspKind.MOBILE) {
            out += "MTU capped for mobile DPI so shredded records are not reassembled downstream"
        }
        return out
    }

    private fun shouldAutoFragment(profile: ProxyProfile?): Boolean {
        if (profile == null) return true
        val security = profile.security.lowercase()
        val transport = profile.transport.lowercase()
        val clearHttpLike =
            security == "none" &&
                transport in setOf(
                    "xhttp", "splithttp", "grpc", "ws", "websocket", "httpupgrade"
                )
        return !clearHttpLike
    }

    private fun usesVisionOrReality(profile: ProxyProfile): Boolean =
        profile.security.lowercase().contains("reality") ||
            profile.raw.lowercase().contains("flow=xtls-rprx-vision")

    private fun isIpLiteral(host: String): Boolean {
        val value = host.trim()
        if (value.isEmpty()) return false
        if (value.contains(':')) return true
        return Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$").matches(value)
    }

    private fun mergeTokens(existing: String, additions: List<String>): String {
        val tokens = linkedSetOf<String>()
        existing.split(',', '\n', '\r', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { tokens += it }
        additions.forEach { tokens += it }
        return tokens.joinToString(",")
    }
}
