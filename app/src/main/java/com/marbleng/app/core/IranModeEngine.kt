package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile

class IranModeEngine {
    val dnsResolver = CensorshipAwareDnsResolver
    val tcpStressMonitor = TcpStressMonitor()
    val leakGuard = LeakGuard()
    val flapGuard = ProfileFlapGuard(iranAware = true)
    val adaptiveScorer = AdaptiveAegisScorer()
    private var routeOptimizer: ContinuousRouteOptimizer? = null

    private var iranState: IranModeState = IranModeState()
    private var networkKey: String = ""
    private var activeProfileId: String = ""
    private var connectedSinceMs: Long = 0
    private var initialized: Boolean = false

    private var securityAssessments: Map<String, ProfileSecurityAuditor.SecurityAssessment> = emptyMap()

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
            dnsResolver.initializeForSession(networkKey, settings)
            tcpStressMonitor.resetForProfile("", networkKey)
            leakGuard.clearFindings()
            flapGuard.reset()
            initialized = true
        }

        flapGuard.let {  }
        routeOptimizer?.setIranMode(iranState.active)

        routeOptimizer?.setAdaptiveScorer(adaptiveScorer, adaptiveFingerprint())

        securityAssessments = ProfileSecurityAuditor.batchAssess(profiles)
    }

    fun onConnected(profileId: String, settings: AppSettings) {
        activeProfileId = profileId
        connectedSinceMs = System.currentTimeMillis()
        flapGuard.onConnected(profileId)
        tcpStressMonitor.resetForProfile(profileId, networkKey)

        val learnedLevel = tcpStressMonitor.learnedLevel(profileId, networkKey)
        if (learnedLevel != null) {
        }
    }

    fun onConnectionFailed(profileId: String) {
        flapGuard.onConnectionFailure()
    }

    fun onDisconnected() {
        leakGuard.setTunnelActive(false)
        activeProfileId = ""
        connectedSinceMs = 0
    }

    fun onTunnelEstablished() {
        leakGuard.setTunnelActive(true)
    }

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

    fun adaptiveFingerprint(ssid: String? = null, mobileNetworkCode: String? = null): String =
        NetworkFingerprint.compose(networkKey, ssid, mobileNetworkCode)

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

    fun recordAdaptiveScore(
        profileId: String,
        score: Double,
        state: AdaptiveAegisScorer.State = AdaptiveAegisScorer.State.HEALTHY
    ) {
        adaptiveScorer.recordScore(adaptiveFingerprint(), profileId, score, state)
    }

    fun noteAdaptiveSwitch(profileId: String) {
        adaptiveScorer.noteSwitch(profileId, adaptiveFingerprint())
    }

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
            retransmitRate = 0.0,
            lossRate = 0.0,

            hasIpv6 = hasIpv6
        )

        return AdaptiveMtuPolicy.recommend(input)
    }

    fun securityAssessment(profile: ProxyProfile): ProfileSecurityAuditor.SecurityAssessment? {
        return securityAssessments[profile.id]
    }

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

    fun activeCountermeasures(): List<String> {
        val measures = mutableListOf<String>()

        if (iranState.active) {
            measures += IranShield.countermeasures(iranState)
        }

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
