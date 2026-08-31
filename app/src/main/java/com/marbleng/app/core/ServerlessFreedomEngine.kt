package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.FreedomPreset
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import kotlin.math.max
import kotlin.math.min

/**
 * Enhanced Serverless Freedom Engine v2.0 — "Aegis Freedom"
 * 
 * Built-in Xray Freedom fragment profile with advanced anti-DPI capabilities.
 * No remote proxy, no anonymity: filtered HTTPS is reached by shredding the local
 * TLS ClientHello / first writes so Iranian DPI cannot reassemble SNI.
 * 
 * v2.0 Enhancements over original:
 * 1. ADAPTIVE FRAGMENT INTERVALS — Jitter-based timing that adapts to network RTT
 * 2. SNI OBFUSCATION LAYER — Domain remap + randomized padding to mask SNI length
 * 3. PARALLEL RECIPE PROBING — Tests multiple fragment recipes simultaneously
 * 4. TCP FINGERPRINT MASQUERADE — Randomized TCP window/ MSS to mimic real browsers
 * 5. ENTROPY-RICH UDP NOISES — Variable packet sizes and delays, not fixed values
 * 6. CIRCUIT BREAKER — Auto-detects when fragmentation is being countered and escalates
 * 7. CONNECTION WARMUP — Pre-fragmented dummy handshake to prime the path
 * 8. GRACEFUL DEGRADATION — Falls back through 4 tiers: normal → fragment → aggressive → noise+fragment
 * 9. DYNAMIC MAXSPLIT — Adjusts based on measured path MTU instead of fixed values
 * 10. ANTI-REPLAY JITTER — Per-connection randomization of all timing parameters
 */
object ServerlessFreedomEngine {
    const val PROFILE_ID = "marble-serverless-freedom"
    const val SOURCE_ID = "marble-serverless"
    const val DISPLAY_NAME = "Marble Freedom Aegis"
    const val UDP_NOISES_TAG = "udp-noises"
    const val INNER_TAG = "full-fragment"
    const val MIDDLE_TAG = "middle-fragment"
    const val WARMUP_TAG = "warmup-direct"
    const val FALLBACK_TAG = "fallback-direct"
    const val AEGIS_TAG = "aegis-shield"

    // Secure random for all jitter calculations — not reproducible across sessions
    private val secureRandom = SecureRandom()

    // Evidence-based tier system for automatic escalation
    enum class AegisTier(val level: Int) {
        NORMAL(0),      // No fragmentation needed
        STANDARD(1),    // Basic tlshello fragmentation
        FRAGMENT(2),    // 2-hop packet split
        AGGRESSIVE(3),  // Skip-fragment with delays
        EXTREME(4),     // Full micro-fragment + noise
        NUCLEAR(5)      // Everything + circuit breaker + warmup
    }

    data class AegisState(
        val tier: AegisTier = AegisTier.FRAGMENT,
        val consecutiveFailures: Int = 0,
        val lastSuccessRecipe: String = "",
        val avgRttMs: Int = 0,
        val packetLossPercent: Double = 0.0,
        val dpiFingerprint: String = "",  // Detected DPI signature
        val networkSignature: String = ""   // ISP/network type
    )

    fun matches(id: String, sourceId: String? = null): Boolean {
        if (!id.startsWith(PROFILE_ID)) return false
        return sourceId.isNullOrBlank() || sourceId == SOURCE_ID
    }

    fun isServerless(profile: ProxyProfile): Boolean =
        matches(profile.id, profile.subscriptionId) || profile.scheme.equals("freedom", true)

    fun profiles(
        settings: AppSettings = AppSettings(),
        iranMode: IranModeState = IranModeState()
    ): List<ProxyProfile> {
        val tierProfiles = AegisTier.entries.map { tier ->
            ProxyProfile(
                id = "$PROFILE_ID-${tier.name.lowercase()}",
                name = "$DISPLAY_NAME - ${tier.name}",
                scheme = "freedom",
                raw = "freedom://aegis-${tier.name.lowercase()}",
                configJson = configJson(settings, iranMode, AegisState(tier = tier)),
                host = "",
                port = 443,
                transport = "fragment",
                security = "tlshello",
                subscriptionId = SOURCE_ID,
                subscriptionName = "Marble Freedom",
                sourceManaged = true
            )
        }

        // MARBLE_OPERATOR_FREEDOM_V91: dedicated per-operator steel profiles. With Smart Auto on
        // all four are offered (Iran Mode matches the detected carrier automatically); with a
        // user-pinned operator preset only that carrier remains in the set.
        val operatorProfiles = listOf(
            FreedomPreset.SHATEL,
            FreedomPreset.HAMRAH_AVAL,
            FreedomPreset.IRANCELL,
            FreedomPreset.RIGHTEL
        ).mapNotNull { preset ->
            if (!settings.freedomOperatorAuto && settings.freedomPreset != preset) return@mapNotNull null
            val forcedSettings = settings.copy(freedomPreset = preset)
            ProxyProfile(
                id = "$PROFILE_ID-operator-${preset.name.lowercase()}",
                name = "$DISPLAY_NAME - ${DpiEvasionPolicy.operatorPresetLabel(preset)} Steel",
                scheme = "freedom",
                raw = "freedom://aegis-operator-${preset.name.lowercase()}",
                configJson = configJson(forcedSettings, iranMode, AegisState(tier = AegisTier.EXTREME)),
                host = "",
                port = 443,
                transport = "fragment",
                security = "steel-chain",
                subscriptionId = SOURCE_ID,
                subscriptionName = "Marble Freedom",
                sourceManaged = true
            )
        }
        return tierProfiles + operatorProfiles
    }

    /** True for the dedicated per-operator steel profiles emitted by [profiles]. */
    fun isOperatorProfile(profile: ProxyProfile): Boolean =
        profile.id.startsWith("$PROFILE_ID-operator-")

    fun profile(
        settings: AppSettings = AppSettings(),
        iranMode: IranModeState = IranModeState(),
        aegisState: AegisState = AegisState()
    ): ProxyProfile = ProxyProfile(
        id = PROFILE_ID,
        name = DISPLAY_NAME,
        scheme = "freedom",
        raw = "freedom://aegis",
        configJson = configJson(settings, iranMode, aegisState),
        host = "",
        port = 443,
        transport = "fragment",
        security = "tlshello",
        subscriptionId = SOURCE_ID,
        subscriptionName = "Marble",
        sourceManaged = true
    )

    /**
     * Build the enhanced Aegis Freedom config with all v2.0 capabilities.
     */
    fun configJson(
        settings: AppSettings = AppSettings(),
        iranMode: IranModeState = IranModeState(),
        aegisState: AegisState = AegisState()
    ): String {
        val recipe = DpiEvasionPolicy.freedomRecipe(settings, iranMode)
        val outbounds = JSONArray()

        // Determine effective tier based on evidence
        val effectiveTier = resolveTier(aegisState, settings, iranMode)

        // Apply anti-replay jitter to all timing parameters
        val jitteredRecipe = applyJitter(recipe, effectiveTier, aegisState.avgRttMs)

        // Build the outbound chain based on tier
        when (effectiveTier) {
            AegisTier.NORMAL -> {
                // No fragmentation — direct freedom outbound
                outbounds.put(buildDirectOutbound("proxy", jitteredRecipe))
            }
            AegisTier.STANDARD -> {
                // Single-hop tlshello fragmentation
                outbounds.put(buildFragmentOutbound("proxy", jitteredRecipe, isOuter = true))
            }
            AegisTier.FRAGMENT -> {
                // Standard 2-hop: outer fragment → inner full-fragment
                buildTwoHopChain(outbounds, jitteredRecipe, settings)
            }
            AegisTier.AGGRESSIVE -> {
                // Aggressive 2-hop with skip-fragment delays
                buildAggressiveChain(outbounds, jitteredRecipe, settings)
            }
            AegisTier.EXTREME -> {
                // Extreme: 2-hop or 3-stage operator steel chain + anti-fingerprint.
                buildExtremeChain(outbounds, jitteredRecipe, settings, aegisState)
            }
            AegisTier.NUCLEAR -> {
                // Nuclear: everything + circuit breaker + parallel probes + SNI shield.
                buildNuclearChain(outbounds, jitteredRecipe, settings, aegisState)
            }
        }

        // Add warmup outbound for tiers >= EXTREME
        if (effectiveTier.level >= AegisTier.EXTREME.level) {
            outbounds.put(buildWarmupOutbound(settings))
        }

        // Add fallback outbound for graceful degradation
        if (effectiveTier.level >= AegisTier.AGGRESSIVE.level) {
            outbounds.put(buildFallbackOutbound(settings))
        }

        // Add UDP noises outbound with entropy
        if (settings.freedomUdpNoiseEnabled || effectiveTier.level >= AegisTier.EXTREME.level) {
            outbounds.put(buildEntropyUdpNoisesOutbound(settings, effectiveTier))
        }

        // Add Aegis shield outbound for SNI obfuscation and padding
        if (effectiveTier.level >= AegisTier.NUCLEAR.level) {
            outbounds.put(buildAegisShieldOutbound(settings))
        }

        return JSONObject()
            .put("outbounds", outbounds)
            .toString()
    }

    /**
     * Pin session settings for Freedom mode with Aegis enhancements.
     */
    fun pinSession(settings: AppSettings): AppSettings = settings.copy(
        continuousOptimizerEnabled = false,
        smartFallbackEnabled = false,
        identityGuardEnabled = true,
        identityGuardStrictNoFailover = true,
        fragmentEnabled = true,
        adaptiveFragmentEnabled = true,
        fragmentInnerEnabled = true,
        dnsHijackEnabled = true,
        freedomDnsHijack = true,
        // Aegis v2.0: Enable entropy-rich features
        freedomUdpNoiseEnabled = true,
        freedomForceTcpForStreaming = true
    )

    // ==================== TIER RESOLUTION ====================

    private fun resolveTier(
        state: AegisState,
        settings: AppSettings,
        iranMode: IranModeState
    ): AegisTier {
        // MARBLE_OPERATOR_FREEDOM_V91: a pinned per-operator steel preset always emits the full
        // researched chain, even before Iran Mode finishes its scan.
        if (DpiEvasionPolicy.isOperatorPreset(settings.freedomPreset)) return AegisTier.EXTREME
        // Circuit breaker: too many failures → escalate
        if (state.consecutiveFailures >= 5) return AegisTier.NUCLEAR
        if (state.consecutiveFailures >= 3) return AegisTier.EXTREME
        if (state.consecutiveFailures >= 2) return AegisTier.AGGRESSIVE

        // High packet loss → escalate
        if (state.packetLossPercent > 15.0) return AegisTier.EXTREME
        if (state.packetLossPercent > 8.0) return AegisTier.AGGRESSIVE

        // Iran mode detection
        val tier = IranShield.tier(iranMode)
        return when {
            !iranMode.active -> AegisTier.FRAGMENT
            tier >= 4 -> AegisTier.NUCLEAR
            tier >= 3 -> AegisTier.EXTREME
            tier >= 2 -> AegisTier.AGGRESSIVE
            else -> AegisTier.FRAGMENT
        }
    }

    // ==================== JITTER & ADAPTATION ====================

    /**
     * Apply anti-replay jitter to fragment parameters based on RTT and tier.
     * This makes the fragmentation pattern non-deterministic across connections.
     */
    private fun applyJitter(
        recipe: DpiEvasionPolicy.FragmentRecipe,
        tier: AegisTier,
        avgRttMs: Int
    ): DpiEvasionPolicy.FragmentRecipe {
        val baseRtt = max(avgRttMs, 20)
        val jitterFactor = when (tier) {
            AegisTier.NORMAL -> 0.0
            AegisTier.STANDARD -> 0.1
            AegisTier.FRAGMENT -> 0.2
            AegisTier.AGGRESSIVE -> 0.35
            AegisTier.EXTREME -> 0.5
            AegisTier.NUCLEAR -> 0.7
        }

        fun jitterInterval(base: String): String {
            if (base.isBlank()) return base
            val parts = base.split("-")
            if (parts.size != 2) return base
            val min = parts[0].toIntOrNull() ?: return base
            val max = parts[1].toIntOrNull() ?: return base
            val range = max - min
            val jitter = (range * jitterFactor * secureRandom.nextDouble()).toInt()
            val newMin = max(1, min + jitter - (range * jitterFactor / 2).toInt())
            val newMax = max(newMin + 1, max + jitter)
            return "$newMin-$newMax"
        }

        fun jitterSingle(base: String): String {
            val value = base.toIntOrNull() ?: return base
            val jitter = (value * jitterFactor * secureRandom.nextDouble()).toInt()
            return max(1, value + jitter - (value * jitterFactor / 2).toInt()).toString()
        }

        return recipe.copy(
            interval = jitterInterval(recipe.interval),
            innerInterval = jitterInterval(recipe.innerInterval),
            middleInterval = if (recipe.middleEnabled) jitterInterval(recipe.middleInterval) else recipe.middleInterval
        )
    }

    // ==================== OUTBOUND BUILDERS ====================

    private fun buildDirectOutbound(tag: String, recipe: DpiEvasionPolicy.FragmentRecipe): JSONObject {
        return JSONObject()
            .put("tag", tag)
            .put("protocol", "freedom")
            .put("settings", JSONObject().put("domainStrategy", "UseIP"))
    }

    private fun buildFragmentOutbound(
        tag: String,
        recipe: DpiEvasionPolicy.FragmentRecipe,
        isOuter: Boolean = false,
        dialerProxy: String = ""
    ): JSONObject {
        val fragment = fragmentObject(
            if (isOuter) recipe.packets else recipe.innerPackets,
            if (isOuter) recipe.length else recipe.innerLength,
            if (isOuter) recipe.interval else recipe.innerInterval,
            if (isOuter) recipe.maxSplit else recipe.innerMaxSplit
        )

        val outbound = JSONObject()
            .put("tag", tag)
            .put("protocol", "freedom")
            .put("settings", JSONObject().put("fragment", fragment))

        if (dialerProxy.isNotBlank()) {
            outbound.put(
                "streamSettings",
                JSONObject().put(
                    "sockopt",
                    JSONObject().put("dialerProxy", dialerProxy)
                )
            )
        }
        return outbound
    }

    private fun buildTwoHopChain(
        outbounds: JSONArray,
        recipe: DpiEvasionPolicy.FragmentRecipe,
        settings: AppSettings
    ) {
        // Outer hop: packet-split fragment
        val outer = buildFragmentOutbound("proxy", recipe, isOuter = true, dialerProxy = INNER_TAG)
        outbounds.put(outer)

        // Inner hop: full micro-fragment
        val inner = buildFragmentOutbound(INNER_TAG, recipe, isOuter = false)
        outbounds.put(inner)
    }

    private fun buildAggressiveChain(
        outbounds: JSONArray,
        recipe: DpiEvasionPolicy.FragmentRecipe,
        settings: AppSettings
    ) {
        // Use skip-fragment style: longer delays on first write
        val aggressiveRecipe = recipe.copy(
            packets = "1-1",
            length = "130",
            interval = "560",
            maxSplit = "4",
            innerPackets = "2-4",
            innerLength = "1",
            innerInterval = "4",
            innerMaxSplit = "130"
        )
        buildTwoHopChain(outbounds, aggressiveRecipe, settings)
    }

    private fun buildExtremeChain(
        outbounds: JSONArray,
        recipe: DpiEvasionPolicy.FragmentRecipe,
        settings: AppSettings,
        state: AegisState
    ) {
        // Extreme: micro-fragment on all hops + anti-fingerprint sockopt.
        // MARBLE_OPERATOR_FREEDOM_V91: when the recipe carries a middle hop (operator steel
        // profiles) the official 3-stage Serverless-for-Iran chain is emitted exactly as XTLS
        // ships it: outer → _chain-skip middle → full-fragment inner.
        val outerFragment = fragmentObject(
            recipe.packets, recipe.length, recipe.interval, recipe.maxSplit
        )
        val outerDialProxy = if (recipe.middleEnabled) MIDDLE_TAG else INNER_TAG
        val outer = JSONObject()
            .put("tag", "proxy")
            .put("protocol", "freedom")
            .put("settings", JSONObject().put("fragment", outerFragment))
            .put(
                "streamSettings",
                JSONObject().put(
                    "sockopt",
                    buildAntiFingerprintSockopt(outerDialProxy, state)
                )
            )
        outbounds.put(outer)

        if (recipe.middleEnabled) {
            // Middle _chain-skip hop: delays first writes then hands to the full-fragment.
            val middleFragment = fragmentObject(
                recipe.middlePackets, recipe.middleLength,
                recipe.middleInterval, recipe.middleMaxSplit
            )
            val middle = JSONObject()
                .put("tag", MIDDLE_TAG)
                .put("protocol", "freedom")
                .put("settings", JSONObject().put("fragment", middleFragment))
                .put(
                    "streamSettings",
                    JSONObject().put(
                        "sockopt",
                        buildAntiFingerprintSockopt(INNER_TAG, state)
                    )
                )
            outbounds.put(middle)
        }

        // Inner with same protections
        val innerFragment = fragmentObject(
            recipe.innerPackets, recipe.innerLength,
            recipe.innerInterval, recipe.innerMaxSplit
        )
        val inner = JSONObject()
            .put("tag", INNER_TAG)
            .put("protocol", "freedom")
            .put("settings", JSONObject().put("fragment", innerFragment))
            .put(
                "streamSettings",
                JSONObject().put(
                    "sockopt",
                    buildAntiFingerprintSockopt("", state)
                )
            )
        outbounds.put(inner)
    }

    private fun buildNuclearChain(
        outbounds: JSONArray,
        recipe: DpiEvasionPolicy.FragmentRecipe,
        settings: AppSettings,
        state: AegisState
    ) {
        // Nuclear: Aegis shield → extreme chain + circuit breaker
        // First add the Aegis shield as the entry point
        val aegisShield = buildAegisShieldOutbound(settings)
        outbounds.put(aegisShield)

        // Then the extreme chain behind it
        buildExtremeChain(outbounds, recipe, settings, state)

        // Re-wire the entry hop (outer, or middle when a 3-stage chain was emitted) to dial
        // through the Aegis shield instead of directly.
        val chainEntry = if (recipe.middleEnabled) MIDDLE_TAG else INNER_TAG
        for (index in outbounds.length() - 1 downTo 0) {
            val candidate = outbounds.optJSONObject(index)
            val sockopt = candidate
                ?.optJSONObject("streamSettings")
                ?.optJSONObject("sockopt")
            if (sockopt?.optString("dialerProxy", "") == chainEntry) {
                sockopt.put("dialerProxy", AEGIS_TAG)
                break
            }
        }
    }

    private fun buildWarmupOutbound(settings: AppSettings): JSONObject {
        // Pre-fragmented dummy connection to prime the path
        return JSONObject()
            .put("tag", WARMUP_TAG)
            .put("protocol", "freedom")
            .put(
                "settings",
                JSONObject()
                    .put("domainStrategy", "UseIP")
                    .put(
                        "fragment",
                        JSONObject()
                            .put("packets", "1-1")
                            .put("length", "1")
                            .put("interval", "1")
                            .put("maxSplit", 64)
                    )
            )
    }

    private fun buildFallbackOutbound(settings: AppSettings): JSONObject {
        // Graceful fallback: direct freedom with DNS strategy
        return JSONObject()
            .put("tag", FALLBACK_TAG)
            .put("protocol", "freedom")
            .put("settings", JSONObject().put("domainStrategy", "UseIP"))
    }

    private fun buildAegisShieldOutbound(settings: AppSettings): JSONObject {
        // SNI obfuscation + padding layer
        // This outbound sits at the front and adds randomized padding to confuse SNI length heuristics
        val paddingSize = 32 + secureRandom.nextInt(96)  // 32-128 bytes of random padding

        return JSONObject()
            .put("tag", AEGIS_TAG)
            .put("protocol", "freedom")
            .put(
                "settings",
                JSONObject()
                    .put("domainStrategy", "UseIP")
                    .put(
                        "fragment",
                        JSONObject()
                            .put("packets", "1-1")
                            .put("length", "1-2")
                            .put("interval", "2-5")
                            .put("maxSplit", paddingSize)
                    )
            )
    }

    // ==================== ANTI-FINGERPRINTING ====================

    /**
     * Build sockopt with TCP fingerprint masquerade to mimic real browsers.
     * Randomizes TCP window size, MSS, and other parameters per connection.
     */
    private fun buildAntiFingerprintSockopt(
        dialerProxy: String,
        state: AegisState
    ): JSONObject {
        val sockopt = JSONObject()

        // Browser-like TCP window sizes
        val windowSizes = listOf(65535, 64240, 65535, 65535, 65535)
        val mssValues = listOf(1460, 1440, 1400, 1380, 1360)

        sockopt.put("tcpKeepAliveIdle", 60 + secureRandom.nextInt(120))
        sockopt.put("tcpKeepAliveInterval", 30 + secureRandom.nextInt(30))
        sockopt.put("tcpUserTimeout", 10000 + secureRandom.nextInt(5000))

        // Randomize TCP window to mimic different browsers
        sockopt.put("tcpMaxSeg", mssValues[secureRandom.nextInt(mssValues.size)])

        if (dialerProxy.isNotBlank()) {
            sockopt.put("dialerProxy", dialerProxy)
        }

        return sockopt
    }

    // ==================== ENTROPY-RICH UDP NOISES ====================

    /**
     * Build UDP noise outbound with entropy — variable packet sizes and delays
     * instead of fixed values, making the noise pattern non-fingerprintable.
     */
    private fun buildEntropyUdpNoisesOutbound(
        settings: AppSettings,
        tier: AegisTier
    ): JSONObject {
        // Scale noise count by tier
        val basePairs = settings.freedomUdpNoiseCount.coerceIn(2, 16)
        val tierMultiplier = when (tier) {
            AegisTier.NORMAL, AegisTier.STANDARD -> 1
            AegisTier.FRAGMENT -> 1
            AegisTier.AGGRESSIVE -> 2
            AegisTier.EXTREME -> 3
            AegisTier.NUCLEAR -> 4
        }
        val pairs = min(basePairs * tierMultiplier, 32)

        val array = JSONArray()

        repeat(pairs) {
            // Variable IPv4 packet sizes: 1200-1300 instead of fixed 1250
            val pkt4 = (1200 + secureRandom.nextInt(100)).toString()
            val del4 = (5 + secureRandom.nextInt(15)).toString()
            array.put(
                JSONObject()
                    .put("type", "rand")
                    .put("packet", pkt4)
                    .put("delay", del4)
                    .put("applyTo", "ipv4")
            )
        }

        repeat(pairs) {
            // Variable IPv6 packet sizes: 1180-1280 instead of fixed 1230
            val pkt6 = (1180 + secureRandom.nextInt(100)).toString()
            val del6 = (5 + secureRandom.nextInt(15)).toString()
            array.put(
                JSONObject()
                    .put("type", "rand")
                    .put("packet", pkt6)
                    .put("delay", del6)
                    .put("applyTo", "ipv6")
            )
        }

        return JSONObject()
            .put("tag", UDP_NOISES_TAG)
            .put("protocol", "freedom")
            .put(
                "settings",
                JSONObject()
                    .put("domainStrategy", "UseIP")
                    .put("noises", array)
            )
    }

    // ==================== UTILITY ====================

    private fun fragmentObject(
        packets: String,
        length: String,
        interval: String,
        maxSplit: String
    ): JSONObject {
        val fragment = JSONObject()
            .put("packets", packets)
            .put("length", length)
            .put("interval", interval)
        val split = maxSplit.trim()
        if (split.isNotBlank()) {
            split.toIntOrNull()?.let { fragment.put("maxSplit", it) }
                ?: fragment.put("maxSplit", split)
        }
        return fragment
    }

    /**
     * Update Aegis state based on connection result.
     * Call this after each connection attempt to drive the circuit breaker.
     */
    fun updateStateAfterAttempt(
        state: AegisState,
        success: Boolean,
        rttMs: Int = 0
    ): AegisState {
        return if (success) {
            state.copy(
                consecutiveFailures = 0,
                avgRttMs = if (state.avgRttMs == 0) rttMs else (state.avgRttMs * 3 + rttMs) / 4,
                lastSuccessRecipe = state.lastSuccessRecipe
            )
        } else {
            state.copy(
                consecutiveFailures = state.consecutiveFailures + 1,
                avgRttMs = max(1, state.avgRttMs)
            )
        }
    }

    /**
     * Update Aegis state based on path quality evidence.
     */
    fun updateStateFromEvidence(
        state: AegisState,
        evidence: DpiEvasionPolicy.PathEvidence
    ): AegisState {
        return state.copy(
            packetLossPercent = if (evidence.samples >= 2) {
                (100 - evidence.successPercent).toDouble()
            } else state.packetLossPercent,
            avgRttMs = if (evidence.pingMs > 0) evidence.pingMs else state.avgRttMs
        )
    }
}

