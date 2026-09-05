package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile

/**
 * Iran Mode Integration Engine — coordinates all anti-filtering subsystems.
 *
 * MARBLE_IRAN_ENGINE_V80: This is the orchestration layer that ties together:
 * 1. CensorshipAwareDnsResolver — multi-layer censorship-aware DNS
 * 2. SurvivalFirstRanker — survival-first node scoring
 * 3. ProfileFlapGuard — anti-flapping hysteresis
 * 4. TcpStressMonitor — reactive MTU/MSS auto-tuning
 * 5. LeakGuard — continuous IP/DNS leak prevention
 * 6. ProfileSecurityAuditor — security posture assessment
 * 7. HandoverCoordinator (enhanced) — dwell-time aware switching
 * 8. ContinuousRouteOptimizer (enhanced) — Iran-aware cooldowns
 *
 * All subsystems are network-scoped: state is reset when the network key changes.
 */
class IranModeEngine {

    // Subsystem instances
    val dnsResolver = CensorshipAwareDnsResolver
    val tcpStressMonitor = TcpStressMonitor()
    val leakGuard = LeakGuard()
    val flapGuard = ProfileFlapGuard(iranAware = true)
    /** MARBLE_SMART_RANK_V90: continuous per-network adaptive scorer. */
    val adaptiveScorer = AdaptiveAegisScorer()
    private var routeOptimizer: ContinuousRouteOptimizer? = null

    // State
    private var iranState: IranModeState = IranModeState()
    private var networkKey: String = ""
    private var activeProfileId: String = ""
    private var connectedSinceMs: Long = 0
    private var initialized: Boolean = false

    // Cached assessments
    private var securityAssessments: Map<String, ProfileSecurityAuditor.SecurityAssessment> = emptyMap()

    /**
     * Initialize or re-initialize the engine for the current network.
     */
    fun initialize(
        iranState: IranModeState,
        networkKey: String,
        settings: AppSettings,
        profiles: List<ProxyProfile>,
        optimizer: ContinuousRouteOptimizer? = null
    ) {
        val networkChanged = this.networkKey != networkKey
        this.iranState = iranState
        this.networkKey = networkKey
        this.routeOptimizer = optimizer

        if (networkChanged || !initialized) {
            // Reset all subsystems for new network
            dnsResolver.initializeForSession(networkKey, settings)
            tcpStressMonitor.resetForProfile("", networkKey)
            leakGuard.clearFindings()
            flapGuard.reset()
            initialized = true
        }

        // Update Iran mode awareness in subsystems
        flapGuard.let { /* iranAware is set at construction */ }
        routeOptimizer?.setIranMode(iranState.active)
        // MARBLE_SMART_RANK_V90: the optimizer feeds the per-network adaptive scorer.
        routeOptimizer?.setAdaptiveScorer(adaptiveScorer, adaptiveFingerprint())

        // Pre-compute security assessments
        securityAssessments = ProfileSecurityAuditor.batchAssess(profiles)
    }

    /**
     * Called when a profile successfully connects.
     */
    fun onConnected(profileId: String, settings: AppSettings) {
        activeProfileId = profileId
        connectedSinceMs = System.currentTimeMillis()
        flapGuard.onConnected(profileId)
        tcpStressMonitor.resetForProfile(profileId, networkKey)

        // Restore learned MTU if available
        val learnedLevel = tcpStressMonitor.learnedLevel(profileId, networkKey)
        if (learnedLevel != null) {
            // Already restored in resetForProfile
        }
    }

    /**
     * Called when a connection fails.
     */
    fun onConnectionFailed(profileId: String) {
        flapGuard.onConnectionFailure()
    }

    /**
     * Called when the tunnel disconnects.
     */
    fun onDisconnected() {
        leakGuard.setTunnelActive(false)
        activeProfileId = ""
        connectedSinceMs = 0
    }

    /**
     * Called when the tunnel is established.
     */
    fun onTunnelEstablished() {
        leakGuard.setTunnelActive(true)
    }

    /**
     * Observe TCP stress metrics from the live session.
     */
    fun observeTcpStress(
        retransmitRate: Double,
        lossRate: Double,
        mssRatio: Double,
        unackedSegments: Int,
        stressed: Boolean,
        rttMs: Int
    ): TcpStressMonitor.TuningDecision {
        tcpStressMonitor.observe(
            retransmitRate = retransmitRate,
            lossRate = lossRate,
            mssRatio = mssRatio,
            unackedSegments = unackedSegments,
            stressed = stressed,
            rttMs = rttMs,
            profileId = activeProfileId,
            networkKey = networkKey
        )
        return tcpStressMonitor.evaluate()
    }

    /**
     * Evaluate whether a profile switch should happen.
     */
    fun evaluateProfileSwitch(
        currentProfile: ProxyProfile,
        currentQuality: Double,
        candidateProfile: ProxyProfile,
        candidateQuality: Double,
        evidenceComplete: Boolean
    ): ProfileFlapGuard.SwitchDecision {
        return flapGuard.evaluateSwitch(
            targetProfileId = candidateProfile.id,
            currentQuality = currentQuality,
            targetQuality = candidateQuality,
            evidenceComplete = evidenceComplete
        )
    }

    /**
     * MARBLE_SMART_RANK_V90: hashed, non-identifying per-network fingerprint for the adaptive
     * scorer. SSID / mobile-network-code are optional and are only ever stored as SHA-256 hashes,
     * matching the privacy boundary documented in MarbleIntelligence.
     */
    fun adaptiveFingerprint(ssid: String? = null, mobileNetworkCode: String? = null): String =
        NetworkFingerprint.compose(networkKey, ssid, mobileNetworkCode)

    /**
     * MARBLE_SMART_RANK_V90: feed one live quality re-measurement (RTT, loss, jitter, stress flag)
     * into the adaptive scorer and return the soft-migration decision for the active route.
     */
    fun observeLiveQuality(
        profileId: String,
        quality: AdaptiveAegisScorer.Quality,
        candidateScores: Map<String, Double>,
        ssid: String? = null,
        mobileNetworkCode: String? = null
    ): AdaptiveAegisScorer.Decision =
        adaptiveScorer.evaluate(
            adaptiveFingerprint(ssid, mobileNetworkCode),
            profileId,
            quality,
            candidateScores
        )

    /** MARBLE_SMART_RANK_V90: persist a learned score for one profile on the current network. */
    fun recordAdaptiveScore(
        profileId: String,
        score: Double,
        state: AdaptiveAegisScorer.State = AdaptiveAegisScorer.State.HEALTHY
    ) {
        adaptiveScorer.recordScore(adaptiveFingerprint(), profileId, score, state)
    }

    /** MARBLE_SMART_RANK_V90: mark an adaptive switch so the 90s dwell hysteresis starts here. */
    fun noteAdaptiveSwitch(profileId: String) {
        adaptiveScorer.noteSwitch(profileId, adaptiveFingerprint())
    }

    /**
     * Get the recommended MTU/MSS settings based on current stress level.
     */
    fun recommendedMtuMss(
        settings: AppSettings,
        profile: ProxyProfile,
        hasIpv6: Boolean
    ): AdaptiveMtuPolicy.Recommendation {
        val stressLevel = tcpStressMonitor.currentMtuLevel()
        val decision = tcpStressMonitor.evaluate()

        val input = AdaptiveMtuPolicy.Input(
            physicalMtu = settings.mtuMax,
            configuredMin = settings.mtuMin,
            configuredMax = settings.mtuMax,
            networkTransport = if (iranState.isp?.kind == IranIspKind.MOBILE) "cellular" else "wifi",
            proxyScheme = profile.scheme,
            proxyTransport = profile.transport,
            stressLevel = stressLevel,
            tcpStressed = decision.urgency != TcpStressMonitor.TuningDecision.Urgency.NONE,
            retransmitRate = 0.0, // Updated by observeTcpStress
            lossRate = 0.0
        )

        return AdaptiveMtuPolicy.recommend(input)
    }

    /**
     * Get security assessment for a profile.
     */
    fun securityAssessment(profile: ProxyProfile): ProfileSecurityAuditor.SecurityAssessment? {
        return securityAssessments[profile.id]
    }

    /**
     * Get comprehensive engine status for diagnostics.
     */
    fun status(): Map<String, Any> {
        val now = System.currentTimeMillis()
        return mapOf(
            "iranActive" to iranState.active,
            "iranTier" to IranShield.tier(iranState),
            "networkKey" to networkKey,
            "activeProfileId" to activeProfileId,
            "connectedDurationMs" to if (connectedSinceMs > 0) now - connectedSinceMs else 0,
            "dnsStats" to dnsResolver.stats(),
            "tcpStress" to tcpStressMonitor.snapshot(),
            "flapGuard" to flapGuard.status(now),
            "securityProfiles" to securityAssessments.size,
            "adaptiveAegis" to adaptiveScorer.tableFor(adaptiveFingerprint()).let { table ->
                mapOf(
                    "fingerprint" to table.fingerprint,
                    "scores" to table.scores.size,
                    "states" to table.states.map { (k, v) -> "$k=${v.name.lowercase()}" }
                )
            },
            "initialized" to initialized
        )
    }

    /**
     * Get a summary of all active countermeasures.
     */
    fun activeCountermeasures(): List<String> {
        val measures = mutableListOf<String>()

        if (iranState.active) {
            measures += IranShield.countermeasures(iranState)
        }

        // Add V80-specific measures
        val dnsStats = dnsResolver.stats()
        if (dnsStats["activeResolvers"]!! > 0) {
            measures += "DNS: ${dnsStats["activeResolvers"]} healthy resolvers, ${dnsStats["poisonedDetections"]} poison attempts blocked"
        }

        val stressSnapshot = tcpStressMonitor.snapshot()
        val mtuLevel = stressSnapshot["currentLevel"] as? String ?: "FULL"
        if (mtuLevel != "FULL") {
            measures += "MTU: Adaptively reduced to $mtuLevel (${stressSnapshot["currentMtu"]}B) due to TCP stress"
        }

        if (securityAssessments.isNotEmpty()) {
            val weakCount = securityAssessments.values.count {
                it.level in setOf(ProfileSecurityAuditor.SecurityLevel.WEAK, ProfileSecurityAuditor.SecurityLevel.INSECURE)
            }
            if (weakCount > 0) {
                measures += "Security: $weakCount weak/insecure profiles deprioritized in ranking"
            }
        }

        measures += "Leak Guard: Active continuous IP/DNS monitoring"
        measures += "Flap Guard: Anti-oscillation with ${if (iranState.active) "3-minute" else "90-second"} minimum dwell"
        measures += "Aegis adaptive selector: per-network learned scores, 90s dwell hysteresis, catastrophic override"

        return measures
    }
}
