package com.marbleng.app.core

/**
 * Root-free MTU policy. It cannot replace RFC 8899 DPLPMTUD in the native transport, but it
 * prevents known outer-transport overhead from being ignored and never exceeds physical MTU.
 */
object AdaptiveMtuPolicy {
    data class Input(
        val physicalMtu: Int,
        val configuredMin: Int,
        val configuredMax: Int,
        val networkTransport: String,
        val proxyScheme: String,
        val proxyTransport: String
    )

    data class Recommendation(
        val mtu: Int,
        val ceiling: Int,
        val reason: String
    )

    fun recommend(input: Input): Recommendation {
        val physical = input.physicalMtu.takeIf { it in 1280..9000 } ?: 1500
        val configuredCeiling = input.configuredMax.coerceIn(1280, 9000)
        val hardCeiling = minOf(physical, configuredCeiling)
        var ceiling = hardCeiling
        val reasons = mutableListOf<String>()

        if (input.networkTransport.equals("cellular", true)) {
            ceiling = minOf(ceiling, 1420)
            reasons += "cellular-headroom"
        }

        val scheme = input.proxyScheme.lowercase()
        val transport = input.proxyTransport.lowercase()
        when {
            scheme in setOf("hysteria", "hysteria2", "hy2") -> {
                ceiling = minOf(ceiling, 1380)
                reasons += "udp-tunnel-overhead"
            }
            "kcp" in transport || "quic" in transport -> {
                ceiling = minOf(ceiling, 1380)
                reasons += "datagram-transport-overhead"
            }
            scheme == "wireguard" -> {
                ceiling = minOf(ceiling, 1360)
                reasons += "wireguard-overhead"
            }
        }

        val requestedFloor = input.configuredMin.coerceIn(1280, 1500)
        val safeFloor = minOf(requestedFloor, ceiling)
        val chosen = ceiling.coerceIn(safeFloor, hardCeiling)
        return Recommendation(
            mtu = chosen,
            ceiling = hardCeiling,
            reason = reasons.ifEmpty { listOf("physical-ceiling") }.joinToString("+")
        )
    }
}
