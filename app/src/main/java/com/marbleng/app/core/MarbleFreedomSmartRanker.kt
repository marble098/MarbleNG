package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ProxyProfile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Smart Aegis — intelligent profile selection with survival-first ranking.
 *
 * MARBLE_SURVIVAL_FIRST_RANK_V80: Completely rewritten to address the Iran filtering
 * problem where nodes that timeout on HTTPS probes are actually perfectly functional.
 *
 * Key changes:
 * 1. Timeout is NOT a death sentence in Iran — uncertain nodes are kept
 * 2. Historical health data overrides single probe failures
 * 3. Security posture is considered (VMess without FS is deprioritized)
 * 4. Longer timeouts in Iran Mode (2.5x multiplier)
 * 5. Uncertain nodes get a chance through passive re-evaluation
 * 6. Profile flap guard prevents rapid switching
 */
object MarbleFreedomSmartRanker {

    // Multiple diverse probe targets to avoid single-target false negatives
    private val FILTERED_TARGETS = listOf(
        "www.instagram.com" to "/",
        "www.youtube.com" to "/",
        "twitter.com" to "/",
        "www.reddit.com" to "/",
        "cp.cloudflare.com" to "/generate_204",
        "connectivitycheck.gstatic.com" to "/generate_204",
        "www.google.com" to "/generate_204"
    )

    // MARBLE_SURVIVAL_FIRST_RANK_V80: Also test with IP-literal targets
    // that bypass DNS issues
    private val IP_TARGETS = listOf(
        "1.1.1.1" to "/cdn-cgi/trace",
        "1.0.0.1" to "/cdn-cgi/trace"
    )

    fun bestProfile(
        settings: AppSettings,
        iranMode: IranModeState,
        xray: XrayManager,
        intelligence: MarbleIntelligence?,
        onProgress: (String) -> Unit
    ): ProxyProfile {
        val profiles = ServerlessFreedomEngine.profiles(settings, iranMode)
        if (profiles.isEmpty()) return profiles.firstOrNull() ?: return defaultProfile()

        val iranActive = iranMode.active
        val networkKey = intelligence?.currentSnapshot()?.key() ?: "unknown"

        // MARBLE_SURVIVAL_FIRST_RANK_V80: Start with historical intelligence
        if (intelligence != null && settings.healthHistoryEnabled) {
            val healthRanked = rankFromHistory(profiles, intelligence, settings)
            if (healthRanked != null) {
                onProgress("Smart Aegis: Historical pick → ${healthRanked.name}")
                return healthRanked
            }
        }

        // MARBLE_SURVIVAL_FIRST_RANK_V80: Assess security posture of all profiles
        val securityAssessments = ProfileSecurityAuditor.batchAssess(profiles)

        // MARBLE_SURVIVAL_FIRST_RANK_V80: Determine timeout based on environment
        val baseTimeout = if (iranActive) 10_000 else 4_000  // 2.5x in Iran
        val totalBudget = if (iranActive) 12_000 else 8_000

        onProgress("Smart Aegis: Testing with ${baseTimeout}ms timeout...")

        // MARBLE_SURVIVAL_FIRST_RANK_V80: Multi-target probing
        val results = mutableMapOf<String, ProbeAttempt>()
        val latch = CountDownLatch(profiles.size)

        for (profile in profiles) {
            Thread {
                val attempt = probeProfile(profile, xray, settings, baseTimeout, iranActive)
                synchronized(results) {
                    results[profile.id] = attempt
                }
                latch.countDown()
            }.start()
        }

        latch.await(totalBudget.toLong(), TimeUnit.MILLISECONDS)

        // MARBLE_SURVIVAL_FIRST_RANK_V80: Score all profiles with survival-first logic
        val scored = profiles.map { profile ->
            val probeResult = results[profile.id]
            val securityScore = securityAssessments[profile.id]?.score ?: 50.0
            val securityPenalty = ProfileSecurityAuditor.rankPenalty(profile)

            val probeScore = when {
                probeResult == null -> 30.0  // No result = uncertain, not dead
                probeResult.success -> {
                    val latencyScore = 100.0 * (1.0 - (probeResult.latencyMs / 5000.0).coerceIn(0.0, 1.0))
                    latencyScore.coerceIn(0.0, 100.0)
                }
                probeResult.isTimeout -> 35.0  // Timeout in Iran = uncertain
                probeResult.isConnectionRefused -> 10.0  // Refused = more likely dead
                probeResult.isXrayStartFailure -> 5.0  // Config issue
                else -> 20.0  // Unknown failure
            }

            // MARBLE_SURVIVAL_FIRST_RANK_V80: Weighted composite score
            val compositeScore = if (iranActive) {
                // In Iran: more weight on security and historical data
                probeScore * 0.30 + securityScore * 0.30 + securityPenalty * 0.20 + 20.0
            } else {
                // Normal: more weight on probe results
                probeScore * 0.50 + securityScore * 0.30 + securityPenalty * 0.20
            }

            ScoredProfile(
                profile = profile,
                score = compositeScore.coerceIn(0.0, 100.0),
                probeResult = probeResult,
                securityLevel = securityAssessments[profile.id]?.level ?: ProfileSecurityAuditor.SecurityLevel.UNKNOWN
            )
        }.sortedByDescending { it.score }

        // MARBLE_SURVIVAL_FIRST_RANK_V80: Select best with uncertainty handling
        val best = scored.firstOrNull() ?: return defaultProfile()

        // If the best is uncertain but there's history, use historical data
        if (best.probeResult != null && !best.probeResult.success &&
            best.probeResult.isTimeout && intelligence != null) {
            val historical = intelligence.healthOf(best.profile.id)
            if (historical != null && historical.successEwma > 60.0) {
                onProgress("Smart Aegis: Surviving on history → ${best.profile.name} (${String.format("%.0f", historical.successEwma)}% EWMA)")
                return best.profile
            }
        }

        // MARBLE_SURVIVAL_FIRST_RANK_V80: Record the result
        if (best.probeResult?.success == true && intelligence != null && settings.healthHistoryEnabled) {
            intelligence.recordBenchmark(
                best.profile,
                BenchmarkResult(
                    profileId = best.profile.id,
                    name = best.profile.name,
                    success = 100,
                    latencyMs = best.probeResult.latencyMs,
                    bytesPerSecond = 0.0,
                    score = best.score,
                    probeKind = "TUNNEL"
                ),
                settings
            )
        }

        // MARBLE_SURVIVAL_FIRST_RANK_V80: Log uncertain profiles for passive re-evaluation
        val uncertain = scored.filter {
            it.probeResult != null && !it.probeResult.success && it.probeResult.isTimeout
        }
        if (uncertain.isNotEmpty()) {
            onProgress("Smart Aegis: ${uncertain.size} uncertain nodes kept for passive eval")
        }

        onProgress("Smart Aegis: Selected ${best.profile.name} (score: ${String.format("%.1f", best.score)})")
        return best.profile
    }

    /**
     * Rank profiles from historical health data.
     * Returns the best historically proven profile if confidence is high enough.
     */
    private fun rankFromHistory(
        profiles: List<ProxyProfile>,
        intelligence: MarbleIntelligence,
        settings: AppSettings
    ): ProxyProfile? {
        val healthScores = profiles.mapNotNull { profile ->
            val health = intelligence.healthOf(profile.id) ?: return@mapNotNull null
            // Only trust high-confidence historical data
            if (health.successEwma >= 60.0 && health.latencyEwma < 9000.0) {
                val securityPenalty = ProfileSecurityAuditor.rankPenalty(profile)
                Triple(profile, health, health.latencyEwma + securityPenalty * 10.0)
            } else null
        }

        if (healthScores.isEmpty()) return null

        val best = healthScores.minByOrNull { it.third }
        return best?.first
    }

    /**
     * Probe a single profile with multiple targets and timeout tolerance.
     */
    private fun probeProfile(
        profile: ProxyProfile,
        xray: XrayManager,
        settings: AppSettings,
        timeoutMs: Int,
        iranActive: Boolean
    ): ProbeAttempt {
        // Try filtered targets first, then IP targets
        val targets = if (iranActive) {
            FILTERED_TARGETS.shuffled().take(2) + IP_TARGETS
        } else {
            FILTERED_TARGETS.shuffled().take(2)
        }

        var bestMs = Double.MAX_VALUE
        var success = false
        var lastError = ""

        for (target in targets) {
            var targetMs = -1.0
            val targetResult = runCatching {
                xray.temporary(profile, 0, settings.copy(benchSamples = 1)) { port ->
                    val r = SocksHttpClient.get(port, target.first, target.second, timeoutMs, 8192)
                    if (r.status in 200..499) {
                        targetMs = r.elapsedMs
                    }
                }
            }

            targetResult.onFailure { error ->
                lastError = error.message ?: error::class.java.simpleName
            }

            if (targetResult.isSuccess && targetMs > 0 && targetMs < bestMs) {
                bestMs = targetMs
                success = true
            }

            // If we got a success, no need to try more targets
            if (success) break
        }

        val isTimeout = lastError.contains("timeout", true) || lastError.contains("timed out", true)
        val isRefused = lastError.contains("refused", true) || lastError.contains("reset", true)
        val isXrayFail = lastError.contains("xray-start", true) || lastError.contains("start", true)

        return ProbeAttempt(
            success = success,
            latencyMs = if (success) bestMs else 9999.0,
            isTimeout = isTimeout,
            isConnectionRefused = isRefused,
            isXrayStartFailure = isXrayFail,
            error = lastError
        )
    }

    private data class ProbeAttempt(
        val success: Boolean,
        val latencyMs: Double,
        val isTimeout: Boolean,
        val isConnectionRefused: Boolean,
        val isXrayStartFailure: Boolean,
        val error: String
    )

    private data class ScoredProfile(
        val profile: ProxyProfile,
        val score: Double,
        val probeResult: ProbeAttempt?,
        val securityLevel: ProfileSecurityAuditor.SecurityLevel
    )

    private fun defaultProfile(): ProxyProfile = ProxyProfile(
        id = "default", name = "Default", scheme = "vless", raw = "",
        configJson = "", host = "", port = 443, transport = "xhttp", security = "reality"
    )
}
