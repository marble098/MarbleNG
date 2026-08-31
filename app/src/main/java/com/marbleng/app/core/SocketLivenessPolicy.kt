package com.marbleng.app.core

/**
 * Conservative Xray TCP liveness values. TCP_NODELAY is intentionally absent because Go already
 * enables it. MPTCP is intentionally opt-in elsewhere because kernel/server support is required.
 *
 * MARBLE_IRAN_LIVENESS_V80: Enhanced with Iran-specific settings that account for
 * the longer RTT and higher loss characteristics of filtered international transit.
 */
object SocketLivenessPolicy {
    data class Values(
        val keepAliveIdleSeconds: Int,
        val keepAliveIntervalSeconds: Int,
        val userTimeoutMs: Int,
        /** MARBLE_IRAN_LIVENESS_V80: whether these values are Iran-tuned */
        val iranTuned: Boolean = false
    )

    fun forTransport(method: String, chained: Boolean, iranMode: Boolean = false): Values {
        val normalized = method.lowercase()
        val base = when {
            normalized.contains("xhttp") || normalized.contains("splithttp") ->
                Values(45, 15, 60_000)
            normalized.contains("grpc") || normalized.contains("httpupgrade") ->
                Values(60, 15, 75_000)
            else -> Values(60, 15, 60_000)
        }

        // MARBLE_IRAN_LIVENESS_V80: More generous timeouts for Iran's filtered transit
        // International traffic through Iran experiences higher RTT and more retransmissions,
        // so keepalive and timeout values need to be higher to avoid premature disconnections.
        val iranAdjusted = if (iranMode) {
            base.copy(
                keepAliveIdleSeconds = (base.keepAliveIdleSeconds * 1.8).toInt().coerceIn(60, 180),
                keepAliveIntervalSeconds = (base.keepAliveIntervalSeconds * 1.5).toInt().coerceIn(20, 45),
                userTimeoutMs = (base.userTimeoutMs * 2.0).toInt().coerceIn(90_000, 300_000),
                iranTuned = true
            )
        } else {
            base
        }

        return if (chained) {
            iranAdjusted.copy(
                keepAliveIdleSeconds = maxOf(iranAdjusted.keepAliveIdleSeconds, 75),
                userTimeoutMs = maxOf(iranAdjusted.userTimeoutMs, 90_000)
            )
        } else {
            iranAdjusted
        }
    }

    /**
     * MARBLE_IRAN_LIVENESS_V80: Recommended TCP keepalive settings for Iran's
     * high-latency international transit. These values prevent false disconnections
     * while still detecting dead peers within a reasonable timeframe.
     */
    fun iranRecommended(): Values = Values(
        keepAliveIdleSeconds = 90,
        keepAliveIntervalSeconds = 25,
        userTimeoutMs = 180_000,
        iranTuned = true
    )
}
