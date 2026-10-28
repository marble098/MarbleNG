package com.marbleng.app.core

/**
 * MARBLE_TUN_MTU_CEILING_V238 — the largest TUN MTU a proxy transport may be given.
 *
 * The report (v11.2.3, P1-7) caught TUN devices built at 1500 while the actual tunnel adds
 * REALITY/XHTTP (or TLS/WS/gRPC) overhead on an underlay that is itself 1500 bytes: the inner
 * packet cannot fit the outer one, the core fragments or the path blackholes it, and the
 * `path-mtu-learned … committed=1500` line never translated into a smaller device.
 *
 * The rule is now structural: the TUN MTU is capped by the protocol's own overhead before any
 * user maximum is applied. The caps sit in the 1400–1420 band the report derives from
 * overhead arithmetic (IPv4+TCP+TLS ≈ 80 bytes over a 1500 underlay). UDP-based tunnels keep
 * their own, tighter, caps in [AdaptiveMtuPolicy].
 */
object ProtocolMtuCeiling {

    const val DEFAULT_TUN_CEILING = 1420

    const val PLAIN_CEILING = 1460

    fun forScheme(scheme: String, transport: String): Int {
        val s = scheme.trim().lowercase()
        val t = transport.trim().lowercase()
        if (s in setOf("hysteria", "hysteria2", "hy2", "tuic", "wireguard")) {
            // Datagram tunnels: AdaptiveMtuPolicy caps tighter; never raise above 1380 here.
            return DATAGRAM_CEILING
        }
        // Any TLS-shaped or multiplexed TCP transport pays header+record overhead on the path.
        val tlsShaped = s in TLS_SCHEMES ||
            t in setOf("ws", "websocket", "grpc", "xhttp", "splithttp", "httpupgrade") ||
            "reality" in t ||
            "tls" in t
        return if (tlsShaped) DEFAULT_TUN_CEILING else PLAIN_CEILING
    }

    const val DATAGRAM_CEILING = 1380

    private val TLS_SCHEMES = setOf("trojan", "vless", "vmess", "socks", "http", "ssh")
}
