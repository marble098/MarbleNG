package com.marbleng.app.core

/**
 * Root-free MTU policy. It cannot replace RFC 8899 DPLPMTUD in the native transport, but it
 * prevents known outer-transport overhead from being ignored and never exceeds physical MTU.
 *
 * MARBLE_REACTIVE_MTU_V80: Now integrates with TcpStressMonitor for reactive
 * auto-tuning based on live TCP stress signals (retransmission, loss, MSS drops).
 * When stressed=true persists, MTU is progressively stepped down.
 * When stress clears, MTU is gradually stepped back up.
 */
object AdaptiveMtuPolicy {
    data class Input(
        val physicalMtu: Int,
        val configuredMin: Int,
        val configuredMax: Int,
        val networkTransport: String,
        val proxyScheme: String,
        val proxyTransport: String,
        /** MARBLE_REACTIVE_MTU_V80: current TCP stress level from TcpStressMonitor */
        val stressLevel: TcpStressMonitor.MtuLevel? = null,
        /** MARBLE_REACTIVE_MTU_V80: whether TCP stress is currently observed */
        val tcpStressed: Boolean = false,
        /** MARBLE_REACTIVE_MTU_V80: current MSS ratio (measured / expected) */
        val mssRatio: Double = 1.0,
        /** MARBLE_REACTIVE_MTU_V80: current retransmission rate */
        val retransmitRate: Double = 0.0,
        /** MARBLE_REACTIVE_MTU_V80: current loss rate */
        val lossRate: Double = 0.0
    )

    data class Recommendation(
        val mtu: Int,
        val ceiling: Int,
        val reason: String,
        /** MARBLE_REACTIVE_MTU_V80: recommended MSS based on MTU and IP family */
        val recommendedMss: Int = 0,
        /** MARBLE_REACTIVE_MTU_V80: whether this is a reactive (stress-driven) adjustment */
        val isReactive: Boolean = false,
        /** MARBLE_REACTIVE_MTU_V80: action to take */
        val action: Action = Action.HOLD
    ) {
        enum class Action {
            HOLD,       // No change needed
            REDUCE,     // Reduce MTU due to stress
            INCREASE,   // Increase MTU after stress clears
            CRITICAL    // Immediate reduction required
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

        // MARBLE_REACTIVE_MTU_V80: Apply stress-driven MTU reduction
        if (input.stressLevel != null && input.stressLevel != TcpStressMonitor.MtuLevel.FULL) {
            ceiling = minOf(ceiling, input.stressLevel.mtu)
            reasons += "stress-reactive-${input.stressLevel.name.lowercase()}"
            isReactive = true
            action = Recommendation.Action.REDUCE
        }

        // MARBLE_REACTIVE_MTU_V80: Critical stress requires immediate action
        if (input.tcpStressed && input.retransmitRate > 0.15 || input.lossRate > 0.15) {
            // Drop to minimum if stress is severe
            val criticalCeiling = 1280
            if (ceiling > criticalCeiling) {
                ceiling = criticalCeiling
                reasons += "critical-stress-minimum"
                isReactive = true
                action = Recommendation.Action.CRITICAL
            }
        }

        // MARBLE_REACTIVE_MTU_V80: MSS ratio indicates path MTU issue
        if (input.mssRatio < 0.55 && !input.tcpStressed) {
            // MSS has dropped significantly, suggest conservative MTU
            val mssDrivenCeiling = (hardCeiling * input.mssRatio).toInt().coerceIn(1280, 1500)
            if (mssDrivenCeiling < ceiling) {
                ceiling = mssDrivenCeiling
                reasons += "mss-degraded-${String.format("%.0f%%", input.mssRatio * 100)}"
                isReactive = true
                action = Recommendation.Action.REDUCE
            }
        }

        val requestedFloor = input.configuredMin.coerceIn(1280, 1500)
        val safeFloor = minOf(requestedFloor, ceiling)
        val chosen = ceiling.coerceIn(safeFloor, hardCeiling)

        // MARBLE_REACTIVE_MTU_V80: Compute recommended MSS
        // Assume IPv6 overhead (60 bytes) for conservative MSS
        val overhead = 60  // IPv6 + TCP header
        val recommendedMss = (chosen - overhead).coerceIn(1160, 1460)

        return Recommendation(
            mtu = chosen,
            ceiling = hardCeiling,
            reason = reasons.ifEmpty { listOf("physical-ceiling") }.joinToString("+"),
            recommendedMss = recommendedMss,
            isReactive = isReactive,
            action = action
        )
    }

    /**
     * MARBLE_REACTIVE_MTU_V80: Quick assessment of whether MTU needs adjustment.
     */
    fun needsAdjustment(input: Input): Boolean {
        return input.tcpStressed ||
            input.retransmitRate > 0.05 ||
            input.lossRate > 0.05 ||
            input.mssRatio < 0.55 ||
            (input.stressLevel != null && input.stressLevel != TcpStressMonitor.MtuLevel.FULL)
    }
}
