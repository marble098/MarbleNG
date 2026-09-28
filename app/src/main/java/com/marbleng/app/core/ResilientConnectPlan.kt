package com.marbleng.app.core

/**
 * Plans a fast but bounded connection attempt from observed link conditions. The caller remains
 * responsible for executing probes and switching routes; this pure policy never invents endpoints
 * or weakens profile security.
 *
 * Three complementary ideas:
 * 1. staggered dual-stack racing avoids waiting for a broken IPv6/IPv4 path;
 * 2. link-aware probe parallelism speeds healthy links without stampeding congested radios;
 * 3. evidence-based failover hysteresis prevents a transient DPI/radio drop from flapping routes.
 */
object ResilientConnectPlan {
    data class Evidence(
        val rttMs: Double? = null,
        val lossPercent: Double = 0.0,
        val consecutiveFailures: Int = 0,
        val consecutiveSuccesses: Int = 0,
        val ipv4Available: Boolean = true,
        val ipv6Available: Boolean = true
    )

    data class Plan(
        val probeParallelism: Int,
        val ipv6RaceDelayMs: Long?,
        val ipv4RaceDelayMs: Long?,
        val shouldFailOver: Boolean,
        val shouldRecoverPrimary: Boolean,
        val retryDelayMs: Long
    )

    fun create(evidence: Evidence, maxParallelism: Int = 4): Plan {
        val loss = evidence.lossPercent.coerceIn(0.0, 100.0)
        val rtt = evidence.rttMs?.takeIf { it.isFinite() && it > 0.0 }
        val parallelism = when {
            loss >= 8.0 || (rtt != null && rtt >= 900.0) -> 1
            loss >= 3.0 || (rtt != null && rtt >= 450.0) -> 2
            else -> maxParallelism.coerceIn(1, 6)
        }

        // Race the non-preferred family immediately; delay the preferred one slightly so a good
        // first answer wins while still bounding the penalty of a black-holed preferred route.
        val raceDelay = when {
            loss >= 8.0 -> 0L
            rtt != null && rtt >= 450.0 -> 80L
            else -> 250L
        }
        val (ipv6Delay, ipv4Delay) = when {
            evidence.ipv6Available && evidence.ipv4Available -> raceDelay to 0L
            evidence.ipv6Available -> raceDelay to null
            evidence.ipv4Available -> null to 0L
            else -> null to null
        }

        val failover = evidence.consecutiveFailures >= 2
        val recover = evidence.consecutiveSuccesses >= 3
        val retry = when {
            evidence.consecutiveFailures <= 0 -> 0L
            loss >= 8.0 -> 250L
            else -> (500L shl (evidence.consecutiveFailures - 1).coerceIn(0, 4)).coerceAtMost(8_000L)
        }
        return Plan(parallelism, ipv6Delay, ipv4Delay, failover, recover, retry)
    }
}
