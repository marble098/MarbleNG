package com.marbleng.app.core

object SocketLivenessPolicy {
    data class Values(
        val keepAliveIdleSeconds: Int,
        val keepAliveIntervalSeconds: Int,
        val userTimeoutMs: Int,
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

    fun iranRecommended(): Values = Values(
        keepAliveIdleSeconds = 90,
        keepAliveIntervalSeconds = 25,
        userTimeoutMs = 180_000,
        iranTuned = true
    )
}
