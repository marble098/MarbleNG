package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import kotlin.math.max
import kotlin.math.min

/**
 * What is left of the DPI countermeasure table after MARBLE_CORE_OPTIONS_V211.
 *
 * This object used to carry thirteen named *fragment recipes* — `TLSHELLO`, `HAMRAH_STEEL`,
 * `RIGHTEL_STEEL`, `EXTREME_ANTI_DPI` … — and every one of them was applied by rewriting
 * `AppSettings.fragment*` on the way to the config builder. That is exactly the defect this
 * release removed: the values on the wire were chosen by whichever policy ran last, so the
 * settings screen could not be trusted and a user could not tell their own choice from the
 * engine's. Fragment shaping is gone from the product; what remains here are the two
 * countermeasures that are *not* packet shaping, and they are honest about their scope:
 *
 *  · [mtuCeiling] — how large a TUN segment may be on a filtered carrier. A clamp on the tunnel
 *    interface, not a rewrite of the user's core options.
 *  · [heal] — timing budgets (bench, precheck) widened by measured loss and jitter. Measured
 *    defects earn *time*, never a different transport.
 *
 * Neither touches a core option. Everything the two cores can be told is now the user's, in
 * Settings › Engine › Core options, and nothing on that page is overwritten on its way out.
 */
object DpiEvasionPolicy {
    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.6778.135 Mobile Safari/537.36"
    const val MARBLE_UA = "MarbleNG/3 Integrity"

    /** What the last measurements of one path say about it. */
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

    /** The largest TUN segment a filtered carrier is trusted with, per severity tier. */
    fun mtuCeiling(state: IranModeState, cellular: Boolean): Int {
        val tier = IranShield.tier(state)
        return when {
            !state.active -> 1500
            tier >= 3 -> 1280
            cellular -> 1380
            else -> 1420
        }
    }

    /**
     * Widen the *timing* budgets from live ping, jitter and loss. Never a transport change.
     *
     * MARBLE_FAKE_IP_V184 — plain high ping arms nothing: a 250 ms+ ping through a relay is
     * distance and capacity, not packet loss or DPI. What a degraded path earns is a longer bench
     * budget and a longer precheck, which is what a slow link actually needs; the MTU clamp follows
     * the carrier's tier and nothing else.
     */
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

    /** The subscription fetcher's one rule: a list is never fetched over cleartext HTTP. */
    fun neverCleartext(url: String): Boolean =
        url.trim().startsWith("https://", ignoreCase = true)
}
