package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.IranModePolicy
import org.json.JSONObject

/**
 * MARBLE_SOCKET_FLIGHT_V168 — one authority for the options applied to the sockets that actually
 * cross the physical network.
 *
 * The audit behind this object found four concrete defects in the connection data path:
 *
 *  1. **Xray fragment chains tuned the wrong socket.** Keep-alives, TCP_USER_TIMEOUT, TCP Fast
 *     Open and MSS were written onto the proxy hop's `sockopt`, but when fragmentation is on the
 *     real TCP socket to the server is opened by the terminal `freedom` fragment dialer
 *     (`sockopt.dialerProxy`). Xray applies the *freedom hop's* sockopt to that socket, so every
 *     fragmented connection — the dominant anti-filter shape in Iran — ran with naked sockets:
 *     no liveness probes, no user-timeout, nothing. A paused filtered link then killed sessions
 *     silently.
 *  2. **Multipath TCP was never offered.** Both pinned cores support it (Xray `tcpMptcp`,
 *     sing-box `tcp_multi_path`). On Linux/Android the Go dialer falls back to plain TCP on ANY
 *     MPTCP error or unsupported kernel (`net/mptcpsock_linux.go.dialMPTCP` retries plain TCP), so
 *     offering it is free and, when the kernel, path and server support it, hands a moving
 *     phone one connection over several radio paths — less handover stalls, more throughput.
 *  3. **sing-box's only dial tuning was TFO + a timeout.** The direct outbound that carries
 *     encrypted DNS bootstrap had no tuning at all, QUIC/Hysteria got no `udp_fragment` (the
 *     documented PMTU-black-hole remedy), and sing-box had none of the Iran liveness profile the
 *     Xray writer already had, so the "same setting, two cores" promise broke at the socket layer.
 *  4. **The TUN→SOCKS leg paid a full extra SOCKS round trip per connection** because HEV's
 *     documented `socks5.pipeline` handshake was never enabled, even though the SOCKS server is
 *     always one of Marble's own pinned cores (both of which read a pipelined handshake).
 *
 * Everything here fills *omissions*: a value the imported configuration or the user explicitly
 * set is preserved, and nothing this object writes can make an unsupported kernel fail a dial —
 * the cores log and ignore an option the platform refuses (Xray: "failed to apply socket
 * options" is LogInfo, not a dial error).
 */
object CoreSocketPolicy {

    const val MARKER = "MARBLE_SOCKET_FLIGHT_V168"

    /** Proxy protocols whose transport is UDP/QUIC rather than a TCP byte stream. */
    private val UDP_PROTOCOLS = setOf("hysteria", "hysteria2", "tuic", "wireguard")

    /** Xray stream methods that ride UDP (KCP/QUIC) and therefore have no TCP socket to tune. */
    private val UDP_METHODS = setOf("kcp", "mkcp", "quic", "hysteria", "hysteria2")

    /** True when a hop speaking [protocol]/[method] opens a UDP (QUIC/KCP/WireGuard) socket. */
    fun opensUdpSocket(protocol: String, method: String = ""): Boolean {
        val p = protocol.trim().lowercase()
        val m = method.trim().lowercase()
        return p in UDP_PROTOCOLS || m in UDP_METHODS
    }

    /** The Iran-liveness signal, computed with the exact same rule in both config writers. */
    fun iranActive(settings: AppSettings): Boolean =
        settings.iranModePolicy != IranModePolicy.OFF && settings.iranModeCountermeasures

    // ── Xray ────────────────────────────────────────────────────────────────────────────────

    /**
     * Physical TCP tuning for a hop that opens the real socket to the server: the proxy hop when
     * it dials directly, or a *terminal* freedom fragment dialer (no `dialerProxy` of its own).
     *
     * Only omissions are filled, so a hand-imported fragment chain keeps every value its author
     * chose. [methodHint] is the proxy transport the freedom dialer carries ("raw" for a
     * generated fragment hop, which only ever wraps a TCP stream).
     */
    fun writeXrayPhysicalTcpSockopt(
        sockopt: JSONObject,
        settings: AppSettings,
        iranActive: Boolean,
        methodHint: String = "raw",
        chained: Boolean = false
    ) {
        val liveness = SocketLivenessPolicy.forTransport(methodHint, chained, iranActive)
        if (!sockopt.has("tcpKeepAliveIdle")) sockopt.put("tcpKeepAliveIdle", liveness.keepAliveIdleSeconds)
        if (!sockopt.has("tcpKeepAliveInterval")) sockopt.put("tcpKeepAliveInterval", liveness.keepAliveIntervalSeconds)
        if (!sockopt.has("tcpUserTimeout")) sockopt.put("tcpUserTimeout", liveness.userTimeoutMs)
        if (settings.tcpFastOpenEnabled && !sockopt.has("tcpFastOpen")) sockopt.put("tcpFastOpen", true)
        if (settings.tcpMaxSeg in 536..9000 && !sockopt.has("tcpMaxSeg")) sockopt.put("tcpMaxSeg", settings.tcpMaxSeg)
        // Offer BBR on lossy filtered transit: a kernel without the module answers TCP_CONGESTION
        // with ENOENT, the pinned Xray logs that at info level and keeps cubic (verified in
        // v26.9.9 system_dialer.go: applyOutboundSocketOptions errors never fail the dial), so
        // the omission is always safe to fill and an imported value is preserved.
        if (!sockopt.has("tcpCongestion")) sockopt.put("tcpCongestion", "bbr")
        // Go's multipath dialer transparently retries plain TCP on any MPTCP failure, so an
        // explicit omission is always filled; an explicit true/false from the source is kept.
        if (!sockopt.has("tcpMptcp")) sockopt.put("tcpMptcp", true)
    }

    /** The local inbounds' streamSettings block, or null when the listener needs no sockopt. */
    fun xrayLocalInboundStream(settings: AppSettings): JSONObject? =
        if (settings.tcpFastOpenEnabled) {
            JSONObject().put("sockopt", JSONObject().put("tcpFastOpen", true))
        } else {
            null
        }

    /**
     * Fill a missing uTLS ClientHello fingerprint with Chrome's.
     *
     * A TLS/REALITY hop that leaves `fingerprint` empty uses the Go crypto/tls ClientHello, which
     * DPI fingerprints trivially and classifies as proxy traffic; every share link and the manual
     * builder already default to Chrome, so this only closes the gap for hand-imported/pasted
     * JSON. QUIC/KCP/Hysteria ride UDP and have no uTLS layer, and any explicit value (including
     * "unsafe") is the operator's choice and is preserved.
     */
    fun applyDefaultUtlsFingerprint(outbound: JSONObject) {
        val stream = outbound.optJSONObject("streamSettings") ?: return
        val security = stream.optString("security").trim().lowercase()
        if (security != "tls" && security != "reality") return
        val method = stream.optString("method")
            .ifBlank { stream.optString("network") }
            .trim()
            .lowercase()
        if (method in UDP_METHODS) return
        val tlsKey = if (security == "reality") "realitySettings" else "tlsSettings"
        val tls = stream.optJSONObject(tlsKey) ?: return
        if (tls.optString("fingerprint").isBlank()) tls.put("fingerprint", "chrome")
    }

    // ── sing-box ─────────────────────────────────────────────────────────────────────────────

    /**
     * The sing-box Dialer Fields for the one hop of a chain that opens a physical socket (the
     * hop without a `detour`). Mirrors the Xray writer's decisions:
     *
     *  - `tcp_multi_path` — MPTCP with Go's automatic plain-TCP fallback;
     *  - `udp_fragment` — lets the kernel fragment large QUIC/Hysteria datagrams instead of
     *    dropping them (the standard PMTU-black-hole countermeasure, required on the DIRECT hop
     *    so encrypted HTTP/3/DoQ bootstrap DNS can leave the device too);
     *  - `tcp_keep_alive` / interval — the same Iran liveness profile the Xray writer uses;
     *  - `tcp_fast_open` / `connect_timeout` — the fields that were already shipped, kept here
     *    so there is one writer and the DIRECT bootstrap outbound cannot be forgotten again.
     */
    fun writeSingBoxPhysicalDial(
        outbound: JSONObject,
        settings: AppSettings,
        protocolHint: String = "",
        methodHint: String = ""
    ) {
        outbound.put("tcp_fast_open", settings.tcpFastOpenEnabled)
        outbound.put("connect_timeout", "${settings.singBoxConnectTimeoutSec.coerceIn(3, 60)}s")
        outbound.put("tcp_multi_path", true)
        outbound.put("udp_fragment", true)
        if (!opensUdpSocket(protocolHint, methodHint)) {
            val liveness = SocketLivenessPolicy.forTransport(
                methodHint.ifBlank { protocolHint }.ifBlank { "raw" },
                false,
                iranActive(settings)
            )
            outbound.put("tcp_keep_alive", "${liveness.keepAliveIdleSeconds}s")
            outbound.put("tcp_keep_alive_interval", "${liveness.keepAliveIntervalSeconds}s")
        }
    }
}
