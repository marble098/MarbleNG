package com.marbleng.app.core

object AdaptiveMtuPolicy {
    data class Input(
        val physicalMtu: Int,
        val configuredMin: Int,
        val configuredMax: Int,
        val networkTransport: String,
        val proxyScheme: String,
        val proxyTransport: String,
        val stressLevel: TcpStressMonitor.MtuLevel? = null,
        val tcpStressed: Boolean = false,
        val mssRatio: Double = 1.0,
        val retransmitRate: Double = 0.0,
        val lossRate: Double = 0.0,
        val hasIpv6: Boolean = false
    )

    data class Recommendation(
        val mtu: Int,
        val ceiling: Int,
        val reason: String,
        val recommendedMss: Int = 0,
        val isReactive: Boolean = false,
        val action: Action = Action.HOLD
    ) {
        enum class Action {
            HOLD,
            REDUCE,
            INCREASE,
            CRITICAL
        }
    }

    fun recommend(input: Input): Recommendation {
        val physical = input.physicalMtu.takeIf { it in 1280..9000 } ?: 1500
        val configuredCeiling = input.configuredMax.coerceIn(1280, 9000)
        val hardCeiling = minOf(physical, configuredCeiling)
        var ceiling = hardCeiling
        val reasons = mutableListOf<String>()
        var isReactive = false
        var action = Recommendation.Action.HOLD

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

        if (input.stressLevel != null && input.stressLevel != TcpStressMonitor.MtuLevel.FULL) {
            ceiling = minOf(ceiling, input.stressLevel.mtu)
            reasons += "stress-reactive-${input.stressLevel.name.lowercase()}"
            isReactive = true
            action = Recommendation.Action.REDUCE
        }

        if (input.tcpStressed && (input.retransmitRate > 0.15 || input.lossRate > 0.15)) {
            val criticalCeiling = 1280
            if (ceiling > criticalCeiling) {
                ceiling = criticalCeiling
                reasons += "critical-stress-minimum"
                isReactive = true
                action = Recommendation.Action.CRITICAL
            }
        }

        if (input.mssRatio < 0.55) {
            val mssDrivenCeiling = (hardCeiling * input.mssRatio).toInt().coerceIn(1280, 1500)
            if (mssDrivenCeiling < ceiling) {
                ceiling = mssDrivenCeiling
                reasons += "pmtu-blackhole-${String.format("%.0f%%", input.mssRatio * 100)}"
                isReactive = true
                action = Recommendation.Action.REDUCE
            }
        }

        val requestedFloor = input.configuredMin.coerceIn(1280, 1500)
        val safeFloor = minOf(requestedFloor, ceiling)
        val chosen = ceiling.coerceIn(safeFloor, hardCeiling)

        val overhead = if (input.hasIpv6) 60 else 40
        val mssCeiling = if (input.hasIpv6) 1440 else 1460
        val recommendedMss = (chosen - overhead).coerceIn(1160, mssCeiling)

        return Recommendation(
            mtu = chosen,
            ceiling = hardCeiling,
            reason = reasons.ifEmpty { listOf("physical-ceiling") }.joinToString("+"),
            recommendedMss = recommendedMss,
            isReactive = isReactive,
            action = action
        )
    }

    fun needsAdjustment(input: Input): Boolean {
        return input.tcpStressed ||
            input.retransmitRate > 0.05 ||
            input.lossRate > 0.05 ||
            input.mssRatio < 0.55 ||
            (input.stressLevel != null && input.stressLevel != TcpStressMonitor.MtuLevel.FULL)
    }
}
