package com.marbleng.app.core

/**
 * Conservative Xray TCP liveness values. TCP_NODELAY is intentionally absent because Go already
 * enables it. MPTCP is intentionally opt-in elsewhere because kernel/server support is required.
 */
object SocketLivenessPolicy {
    data class Values(
        val keepAliveIdleSeconds: Int,
        val keepAliveIntervalSeconds: Int,
        val userTimeoutMs: Int
    )

    fun forTransport(method: String, chained: Boolean): Values {
        val normalized = method.lowercase()
        val base = when {
            normalized.contains("xhttp") || normalized.contains("splithttp") ->
                Values(45, 15, 60_000)
            normalized.contains("grpc") || normalized.contains("httpupgrade") ->
                Values(60, 15, 75_000)
            else -> Values(60, 15, 60_000)
        }
        return if (chained) {
            base.copy(
                keepAliveIdleSeconds = maxOf(base.keepAliveIdleSeconds, 75),
                userTimeoutMs = maxOf(base.userTimeoutMs, 90_000)
            )
        } else {
            base
        }
    }
}
