package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.FreedomPreset
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

    /**
     * MARBLE_SMART_RANK_V90: exact user-facing prompt shown when a majority of the candidate pool
     * is quarantined with stale/broken data. Rank stops instead of silently finishing empty and the
     * message auto-offers a subscription refresh.
     */
    const val STALE_SUBSCRIPTIONS_MESSAGE =
        "پروفایل‌ها به‌روز نیستند، لطفاً Refresh Subscriptions را بزنید"

    /** MARBLE_OPERATOR_FREEDOM_V91: profile id emitted for an operator steel preset. */
    private fun operatorProfileId(preset: FreedomPreset): String =
        "${ServerlessFreedomEngine.PROFILE_ID}-operator-${preset.name.lowercase()}"

    /**
     * Latest decision snapshot for diagnostics. Set at the end of [bestProfile] so Bug Finder /
     * RuntimeDiagnostics can surface the ranking reason, uncertain/failed distinction and any
     * quarantine without running a second expensive decision.
     */
    @Volatile
    var lastRankingDecision: DiagnosticsSummary.RankingDecision? = null

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
        crossCheckSources: ProfileAddressCrossCheck.CrossCheckSources = ProfileAddressCrossCheck.CrossCheckSources(),
        onProgress: (String) -> Unit
    ): ProxyProfile {
        val allProfiles = ServerlessFreedomEngine.profiles(settings, iranMode)
        if (allProfiles.isEmpty()) return defaultProfile()

        // MARBLE_OPERATOR_FREEDOM_V91: Smart Auto with a detected Iranian carrier skips the probe
        // ladder and picks that carrier's researched steel profile immediately — this is both the
        // per-operator boost and the fastest possible connect.
        if (settings.freedomOperatorAuto) {
            val detectedPreset = DpiEvasionPolicy.operatorPresetFor(iranMode.isp)
            if (detectedPreset != null) {
                allProfiles.firstOrNull { it.id == operatorProfileId(detectedPreset) }?.let { matched ->
                    onProgress(
                        "Smart Aegis: ${DpiEvasionPolicy.operatorPresetLabel(detectedPreset)} " +
                            "steel profile → ${matched.name}"
                    )
                    lastRankingDecision = DiagnosticsSummary.RankingDecision(
                        selectedProfileId = matched.id,
                        decisionReason = "operator-matched-${detectedPreset.name.lowercase()}",
                        healthCount = 1, uncertainCount = 0, failedCount = 0
                    )
                    return matched
                }
            }
        }
        // When auto mode has no carrier match, probe only the tier ladder; the operator rows stay
        // selectable in Library but are not raced against generic tiers.
        val probePool = if (settings.freedomOperatorAuto) {
            allProfiles.filterNot(ServerlessFreedomEngine::isOperatorProfile)
        } else {
            allProfiles
        }
        if (probePool.isEmpty()) return defaultProfile()

        // MARBLE_SMART_RANK_V90: remove censorship-unsafe nodes (VLESS without TLS/REALITY, VMess
        // without forward secrecy) before they can fail a benchmark, then cross-check each
        // candidate's address against the local cache + fresh subscription before quarantining.
        val (securitySafe, deprecated) = ProfileSecurityAuditor.partitionForRank(probePool)
        if (deprecated.isNotEmpty()) {
            onProgress(
                "Smart Aegis: hidden ${deprecated.size} unsafe profile(s): " +
                    deprecated.joinToString { "${it.first.name}=${it.second}" }
            )
        }

        // MARBLE_PROFILE_QUARANTINE_V1: preflight before any probe so a structurally broken
        // profile (e.g. "Turkey 4-All" VLESS/TLS invalid-config) can never poison ranking or
        // selection. Quarantined profiles are excluded from the probe set entirely.
        val (profiles, invalid) = ProfilePreflightValidator.partition(securitySafe, crossCheckSources)
        val quarantinedIds = invalid.mapTo(mutableSetOf()) { it.first.id }

        if (invalid.isNotEmpty()) {
            onProgress(
                "Smart Aegis: quarantined ${invalid.size} invalid profile(s): " +
                    invalid.joinToString { "${it.first.name}=${it.second.reason}" }
            )
        }

        // MARBLE_SMART_RANK_V90: a majority quarantine means the subscription is stale/broken, not
        // that the nodes are dead. Stop, show the Persian refresh prompt, and auto-offer a
        // subscription refresh — never silently finish empty.
        val majorityQuarantined = ProfilePreflightValidator.isMajorityQuarantined(
            invalid.size, securitySafe.size
        )
        val majorityExcluded = allProfiles.isNotEmpty() &&
            (invalid.size + deprecated.size) * 2 > allProfiles.size
        if (majorityQuarantined || majorityExcluded || profiles.isEmpty()) {
            onProgress(STALE_SUBSCRIPTIONS_MESSAGE)
            lastRankingDecision = DiagnosticsSummary.RankingDecision(
                decisionReason = if (majorityQuarantined || majorityExcluded) {
                    "stale-subscriptions-majority-excluded"
                } else {
                    "all-profiles-quarantined"
                },
                healthCount = 0, uncertainCount = 0,
                failedCount = allProfiles.size
            )
            return defaultProfile()
        }

        val iranActive = iranMode.active
        val networkKey = intelligence?.currentSnapshot()?.key() ?: "unknown"

        // MARBLE_SURVIVAL_FIRST_RANK_V80: Start with historical intelligence
        if (intelligence != null && settings.healthHistoryEnabled) {
            val healthRanked = rankFromHistory(profiles, intelligence, settings)
            if (healthRanked != null) {
                onProgress("Smart Aegis: Historical pick → ${healthRanked.name}")
                lastRankingDecision = DiagnosticsSummary.RankingDecision(
                    selectedProfileId = healthRanked.id,
                    decisionReason = "historical-pick",
                    healthCount = 1, uncertainCount = 0, failedCount = 0
                )
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
                val attempt = probeProfile(profile, xray, settings, baseTimeout, iranActive, intelligence)
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

        // MARBLE_SURVIVAL_FIRST_RANK_V80: Align Aegis decisions with the survival rank engine so
        // the Smart Aegis timeout improvements and the Smart Rank engine never contradict each
        // other. Build a probe BenchmarkResult for each scored profile and re-rank survival-first.
        val probeResults = scored.map { sp ->
            sp.profile to BenchmarkResult(
                profileId = sp.profile.id,
                name = sp.profile.name,
                success = if (sp.probeResult?.success == true) 100 else 0,
                latencyMs = sp.probeResult?.latencyMs ?: 9_999.0,
                bytesPerSecond = 0.0,
                score = sp.score,
                probeKind = "TUNNEL",
                failureReason = sp.probeResult?.error ?: ""
            )
        }

        val healthHistories = profiles.associate { p ->
            p.id to (intelligence?.let { SurvivalFirstRanker.fromNodeHealth(it.healthOf(p.id)) })
        }.filterValues { it != null }

        val reRanked = probeResults.sortedWith(
            compareBy<Pair<ProxyProfile, BenchmarkResult>> { pair ->
                val (p, r) = pair
                val score = SurvivalFirstRanker.scoreForSurvival(
                    profile = p,
                    probeResult = r,
                    historicalHealth = healthHistories[p.id],
                    tcpStress = null,
                    connectedDurationMs = 0,
                    settings = settings,
                    iranActive = iranActive,
                    quarantined = p.id in quarantinedIds
                )
                score.classification.rank
            }.thenByDescending { pair ->
                val (p, r) = pair
                SurvivalFirstRanker.scoreForSurvival(
                    profile = p,
                    probeResult = r,
                    historicalHealth = healthHistories[p.id],
                    tcpStress = null,
                    connectedDurationMs = 0,
                    settings = settings,
                    iranActive = iranActive,
                    quarantined = p.id in quarantinedIds
                ).survivalScore
            }
        )

        val bestResult = reRanked.firstOrNull()
        val bestProfileObj = bestResult?.first ?: return defaultProfile()
        val bestProbe = scored.firstOrNull { it.profile.id == bestProfileObj.id }

        // MARBLE_SURVIVAL_FIRST_RANK_V80: Record the result
        val bestProbeResult = bestProbe?.probeResult
        if (bestProbeResult?.success == true && intelligence != null && settings.healthHistoryEnabled) {
            intelligence.recordBenchmark(
                bestProfileObj,
                BenchmarkResult(
                    profileId = bestProfileObj.id,
                    name = bestProfileObj.name,
                    success = 100,
                    latencyMs = bestProbeResult.latencyMs,
                    bytesPerSecond = 0.0,
                    score = bestProbe?.score ?: 0.0,
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
            onProgress("Smart Aegis: ${uncertain.size} uncertain servers kept for passive eval")
        }

        // Machine-readable decision snapshot for diagnostics.
        lastRankingDecision = DiagnosticsSummary.RankingDecision(
            selectedProfileId = bestProfileObj.id,
            decisionReason = SurvivalFirstRanker.scoreForSurvival(
                profile = bestProfileObj,
                probeResult = bestResult?.second,
                historicalHealth = healthHistories[bestProfileObj.id],
                tcpStress = null,
                connectedDurationMs = 0,
                settings = settings,
                iranActive = iranActive,
                quarantined = bestProfileObj.id in quarantinedIds
            ).rankingDecisionReason,
            healthCount = scored.count { it.probeResult?.success == true },
            uncertainCount = uncertain.size,
            failedCount = scored.count { it.probeResult?.success != true } - uncertain.size
        )

        onProgress(
            "Smart Aegis: Selected ${bestProfileObj.name} " +
                "(class: ${SurvivalFirstRanker.scoreForSurvival(
                    profile = bestProfileObj,
                    probeResult = bestResult?.second,
                    historicalHealth = healthHistories[bestProfileObj.id],
                    tcpStress = null,
                    connectedDurationMs = 0,
                    settings = settings,
                    iranActive = iranActive,
                    quarantined = bestProfileObj.id in quarantinedIds
                ).classification})"
        )
        return bestProfileObj
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
        iranActive: Boolean,
        intelligence: MarbleIntelligence? = null
    ): ProbeAttempt {
        // MARBLE_TUNING_MEASUREMENT_PLANE_V134 — the throwaway core this ranker judges a node with is
        // hardened from the same measured link evidence as the live tunnel. Without it the measurement
        // core kept the legacy 1350/1650 ms encrypted-DNS budgets and could not resolve the probe
        // hostname on a slow route, so every attempt looked like a dead node.
        val linkEvidence = runCatching { intelligence?.linkEvidenceFor(profile.id) }.getOrNull()
            ?: LinkEvidence.UNKNOWN
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
            // MARBLE_SMART_RANK_V90: up to 2 attempts per target with exponential backoff so a
            // single HTTPS timeout can never fail a node (timeouts are rampant under Iran's
            // throttling). Refused/reset errors are deterministic and are not retried.
            var attempt = 0
            while (attempt < 2 && targetMs <= 0) {
                attempt++
                val targetResult = runCatching {
                    xray.temporary(
                        profile,
                        0,
                        settings.copy(benchSamples = 1),
                        link = linkEvidence
                    ) { port ->
                        val r = SocksHttpClient.get(port, target.first, target.second, timeoutMs, 8192)
                        if (r.status in 200..499) {
                            targetMs = r.elapsedMs
                        }
                    }
                }

                targetResult.onFailure { error ->
                    lastError = error.message ?: error::class.java.simpleName
                }

                val retriable = targetMs <= 0 &&
                    lastError.contains("timeout", true) &&
                    attempt < 2
                if (retriable) {
                    try {
                        Thread.sleep(120L * attempt)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }

            if (targetMs > 0 && targetMs < bestMs) {
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
