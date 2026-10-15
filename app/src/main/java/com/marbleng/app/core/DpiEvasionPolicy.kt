package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import kotlin.math.max
import kotlin.math.min

object DpiEvasionPolicy {
    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.6778.135 Mobile Safari/537.36"
    const val MARBLE_UA = "MarbleNG/3 Integrity"

    data class PathEvidence(
        val pingMs: Int = 0,
        val jitterMs: Int = 0,
        val successPercent: Int = 100,
        val samples: Int = 0
    ) {
        val lossy: Boolean get() = samples >= 2 && successPercent in 1..84
        val highPing: Boolean get() = samples >= 2 && pingMs >= 250
        val highJitter: Boolean get() = samples >= 2 && jitterMs >= 24
        val degraded: Boolean get() = lossy || highPing || highJitter
    }

    fun mtuCeiling(state: IranModeState, cellular: Boolean): Int {
        val tier = IranShield.tier(state)
        return when {
            !state.active -> 1500
            tier >= 3 -> 1280
            cellular -> 1380
            else -> 1420
        }
    }

    fun heal(
        base: AppSettings,
        evidence: PathEvidence,
        state: IranModeState
    ): AppSettings {
        if (!evidence.degraded && !state.active) return base

        var next = base
        if (state.active) {
            val cellular = state.isp?.kind == IranIspKind.MOBILE
            val ceiling = when {
                evidence.lossy -> min(mtuCeiling(state, cellular), 1280)
                evidence.highJitter -> min(mtuCeiling(state, cellular), 1360)
                else -> mtuCeiling(state, cellular)
            }
            next = next.copy(mtuMax = min(next.mtuMax, ceiling).coerceAtLeast(next.mtuMin))
        }
        if (evidence.highPing || CensorTechnique.THROTTLING in state.techniques) {
            next = next.copy(
                benchTimeoutSec = max(next.benchTimeoutSec, 14),
                tcpPrecheckTimeoutMs = max(next.tcpPrecheckTimeoutMs, 3_000)
            )
        }
        return next
    }

    fun neverCleartext(url: String): Boolean =
        url.trim().startsWith("https://", ignoreCase = true)
}
