package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import kotlin.math.max
import kotlin.math.min

/**
 * Research-backed DPI countermeasures for Iranian operator networks.
 *
 * Iranian mobile and fixed ISPs (MCI/Hamrah-e-Avval, Irancell, TCI/Shatel and the national
 * gateway) inspect TLS ClientHello SNI, reassemble short TCP segments, RST oversized first-flight
 * records, and during clampdowns allowlist 80/443 while dropping UDP. Subscription "uploads"
 * (HTTPS GET of a provider list) fail the same way: GitHub/raw SNI is blocked, Marble's own
 * User-Agent is fingerprinted, and falling back to cleartext HTTP is both a leak and a block.
 *
 * This policy never recommends MitM, domain-fronting, or HTTP. Fragment recipes stay inside
 * Xray Freedom `fragment` / `noises` as documented by XTLS.
 */
object DpiEvasionPolicy {
    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.6778.135 Mobile Safari/537.36"
    const val MARBLE_UA = "MarbleNG/3 Integrity"

    data class FragmentRecipe(
        val packets: String,
        val length: String,
        val interval: String,
        val maxSplit: String = "",
        val innerPackets: String = "",
        val innerLength: String = "",
        val innerInterval: String = "",
        val innerMaxSplit: String = "",
        val middlePackets: String = "",
        val middleLength: String = "",
        val middleInterval: String = "",
        val middleMaxSplit: String = "",
        val rank: Int,
        val description: String
    ) {
        val innerEnabled: Boolean
            get() = innerPackets.isNotBlank()
        val middleEnabled: Boolean
            get() = middlePackets.isNotBlank()
    }

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

    val TLSHELLO: FragmentRecipe = FragmentRecipe(
        packets = "tlshello",
        length = "100-200",
        interval = "10-20",
        rank = 1,
        description = "ClientHello fragmentation • splits the SNI across TCP segments"
    )

    /**
     * GFW-knocker's battle-tested serverless outer hop (fragment 1-1 / 1-3 / 5-10) plus Marble's
     * 1-byte/4 ms inner split. NOTE: the old "tlshello" mode is intentionally not used here — it
     * rewrites the ClientHello into complete tiny TLS records (Xray #4370), a pattern that both
     * real servers (Fastly/Cloudflare/GitHub/AWS all RST it; verified on v26.7.28) and Iran's
     * current DPI reassemble and refilter (Xray discussion #5969 field notes, 2026-04).
     */
    val TLSHELLO_SNI: FragmentRecipe = FragmentRecipe(
        packets = "1-1",
        length = "1-3",
        interval = "5-10",
        innerPackets = "1-1",
        innerLength = "1",
        innerInterval = "4",
        innerMaxSplit = "517",
        rank = 5,
        description = "GFW-knocker serverless split • 1-1/1-3/5-10 first writes + 517-byte 1-byte split"
    )

    val RECORD_SPLIT: FragmentRecipe = FragmentRecipe(
        packets = "1-3",
        length = "10-30",
        interval = "10-20",
        rank = 3,
        description = "Aggressive TLS record shredding • first three writes split into 10-30 byte chunks"
    )

    val MAX_SLICE: FragmentRecipe = FragmentRecipe(
        packets = "1-3",
        length = "5-15",
        interval = "15-30",
        rank = 4,
        description = "Maximum bounded stream slicing • first three writes split into 5-15 byte chunks"
    )

    val FULL_FRAGMENT: FragmentRecipe = FragmentRecipe(
        packets = "1-1",
        length = "1",
        interval = "4",
        maxSplit = "517",
        rank = 5,
        description = "Full-stream fragment • 1-byte writes, 4 ms interval, 517 maxSplit"
    )

    /**
     * 3-Layer cascading anti-DPI shredder. The outer layer is GFW-knocker's packet-split
     * (1-1 / 1-3 / 5-10), NOT Xray's "tlshello" record-rewriter: "tlshello" emits complete tiny
     * TLS records that real servers (Fastly, Cloudflare, GitHub, AWS — RST), verified against
     * v26.7.28, and Iran's 2026 DPI reassembles and refilters anyway (Xray #4370, #5969).
     * Packet-split keeps the ClientHello a single valid TLS record while hiding the SNI from
     * per-packet DPI — the tested working shape (GFW-knocker config, official skip-fragment).
     */
    val MULTI_LAYER_CASCADE: FragmentRecipe = FragmentRecipe(
        packets = "1-1",
        length = "1-3",
        interval = "5-10",
        middlePackets = "1-3",
        middleLength = "10-30",
        middleInterval = "5-10",
        middleMaxSplit = "768",
        innerPackets = "1-1",
        innerLength = "1",
        innerInterval = "4",
        innerMaxSplit = "517",
        rank = 6,
        description = "3-Layer cascade • packet-split outer → record slicer → byte-level micro fragment"
    )

    /** Aggressive 3-layer TLS record & stream shredder for severe DPI inspection. */
    val AGGRESSIVE_CASCADE: FragmentRecipe = FragmentRecipe(
        packets = "1-3",
        length = "5-15",
        interval = "10-20",
        middlePackets = "1-3",
        middleLength = "10-30",
        middleInterval = "5-10",
        middleMaxSplit = "768",
        innerPackets = "1-1",
        innerLength = "1",
        innerInterval = "4",
        innerMaxSplit = "517",
        rank = 6,
        description = "Aggressive 3-layer TLS record & stream shredder"
    )

    /** Extreme byte-by-byte anti-censorship micro-fragmenting. */
    val EXTREME_ANTI_DPI: FragmentRecipe = FragmentRecipe(
        packets = "1-1",
        length = "1",
        interval = "2",
        maxSplit = "256",
        middlePackets = "1-2",
        middleLength = "2-5",
        middleInterval = "3-6",
        middleMaxSplit = "517",
        innerPackets = "1-1",
        innerLength = "1",
        innerInterval = "4",
        innerMaxSplit = "256",
        rank = 7,
        description = "Extreme deep micro-fragmenting • byte-by-byte anti-censorship shred"
    )

    fun freedomRecipe(settings: AppSettings, state: IranModeState = IranModeState()): FragmentRecipe {
        return when (settings.freedomPreset) {
            com.marbleng.app.model.FreedomPreset.MULTI_LAYER_CASCADE -> MULTI_LAYER_CASCADE
            com.marbleng.app.model.FreedomPreset.SNI_SHREDDER -> TLSHELLO_SNI
            com.marbleng.app.model.FreedomPreset.AGGRESSIVE_RECORD_SPLIT -> AGGRESSIVE_CASCADE
            com.marbleng.app.model.FreedomPreset.EXTREME_ANTI_DPI -> EXTREME_ANTI_DPI
            com.marbleng.app.model.FreedomPreset.CUSTOM -> FragmentRecipe(
                packets = settings.freedomOuterPackets.ifBlank { "1-1" },
                length = settings.freedomOuterLength.ifBlank { "1-3" },
                interval = settings.freedomOuterInterval.ifBlank { "5-10" },
                maxSplit = settings.freedomOuterMaxSplit,
                middlePackets = if (settings.freedomMiddleEnabled) settings.freedomMiddlePackets.ifBlank { "1-3" } else "",
                middleLength = if (settings.freedomMiddleEnabled) settings.freedomMiddleLength.ifBlank { "10-30" } else "",
                middleInterval = if (settings.freedomMiddleEnabled) settings.freedomMiddleInterval.ifBlank { "5-10" } else "",
                middleMaxSplit = if (settings.freedomMiddleEnabled) settings.freedomMiddleMaxSplit.ifBlank { "768" } else "",
                innerPackets = if (settings.freedomInnerEnabled) settings.freedomInnerPackets.ifBlank { "1-1" } else "",
                innerLength = if (settings.freedomInnerEnabled) settings.freedomInnerLength.ifBlank { "1" } else "",
                innerInterval = if (settings.freedomInnerEnabled) settings.freedomInnerInterval.ifBlank { "4" } else "",
                innerMaxSplit = if (settings.freedomInnerEnabled) settings.freedomInnerMaxSplit.ifBlank { "517" } else "",
                rank = 6,
                description = "Custom user-configured multi-layer fragment"
            )
            com.marbleng.app.model.FreedomPreset.SMART_ADAPTIVE -> {
                val tier = IranShield.tier(state)
                when {
                    tier >= 3 -> EXTREME_ANTI_DPI
                    tier >= 2 -> MULTI_LAYER_CASCADE
                    else -> MULTI_LAYER_CASCADE
                }
            }
        }
    }

    fun connectionRecipe(state: IranModeState): FragmentRecipe {
        val tier = IranShield.tier(state)
        val sni = CensorTechnique.SNI_FILTERING in state.techniques
        val reset = CensorTechnique.TCP_RESET in state.techniques
        return when {
            !state.active -> TLSHELLO
            CensorTechnique.NATIONAL_INTRANET in state.techniques ||
                tier >= 3 ||
                (sni && reset) -> TLSHELLO_SNI
            sni || tier >= 2 -> RECORD_SPLIT
            else -> TLSHELLO
        }
    }

    fun serverlessRecipe(state: IranModeState): FragmentRecipe {
        val base = connectionRecipe(state)
        return if (base.innerEnabled) base else TLSHELLO_SNI
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

    /**
     * Escalate (never weaken) fragment/MTU/timeouts from live ping, jitter and loss.
     * Applied after IranShield so a healthy Iran recipe is not replaced by a milder default.
     */
    fun heal(
        base: AppSettings,
        evidence: PathEvidence,
        state: IranModeState
    ): AppSettings {
        if (!evidence.degraded && !state.active) return base

        val current = recipeFrom(base)
        val needed = when {
            evidence.lossy -> MAX_SLICE
            evidence.highJitter && IranShield.tier(state) >= 2 -> TLSHELLO_SNI
            evidence.highJitter -> RECORD_SPLIT
            evidence.highPing -> if (current.rank >= RECORD_SPLIT.rank) current else RECORD_SPLIT
            else -> current
        }
        val chosen = if (needed.rank >= current.rank) needed else current
        var next = applyRecipe(base, chosen)

        if (evidence.degraded || state.active) {
            val cellular = state.isp?.kind == IranIspKind.MOBILE
            val ceiling = when {
                evidence.lossy -> min(mtuCeiling(state, cellular), 1280)
                evidence.highJitter -> min(mtuCeiling(state, cellular), 1360)
                evidence.highPing -> min(mtuCeiling(state, cellular), 1400)
                else -> mtuCeiling(state, cellular)
            }
            next = next.copy(mtuMax = min(next.mtuMax, ceiling).coerceAtLeast(next.mtuMin))
        }
        if (evidence.lossy || CensorTechnique.UDP_BLOCKED in state.techniques) {
            next = next.copy(muxUdp443 = "reject")
        }
        if (evidence.highPing || CensorTechnique.THROTTLING in state.techniques) {
            next = next.copy(
                benchTimeoutSec = max(next.benchTimeoutSec, 14),
                tcpPrecheckTimeoutMs = max(next.tcpPrecheckTimeoutMs, 3_000)
            )
        }
        return next
    }

    fun applyRecipe(base: AppSettings, recipe: FragmentRecipe): AppSettings = base.copy(
        fragmentEnabled = true,
        adaptiveFragmentEnabled = true,
        fragmentPackets = recipe.packets,
        fragmentLength = recipe.length,
        fragmentInterval = recipe.interval,
        fragmentMaxSplit = recipe.maxSplit,
        fragmentInnerEnabled = recipe.innerEnabled,
        fragmentInnerPackets = recipe.innerPackets.ifBlank { base.fragmentInnerPackets },
        fragmentInnerLength = recipe.innerLength.ifBlank { base.fragmentInnerLength },
        fragmentInnerInterval = recipe.innerInterval.ifBlank { base.fragmentInnerInterval },
        fragmentInnerMaxSplit = recipe.innerMaxSplit.ifBlank { base.fragmentInnerMaxSplit }
    )

    fun recipeFrom(settings: AppSettings): FragmentRecipe = FragmentRecipe(
        packets = settings.fragmentPackets.ifBlank { TLSHELLO.packets },
        length = settings.fragmentLength.ifBlank { TLSHELLO.length },
        interval = settings.fragmentInterval.ifBlank { TLSHELLO.interval },
        maxSplit = settings.fragmentMaxSplit,
        innerPackets = if (settings.fragmentInnerEnabled) settings.fragmentInnerPackets else "",
        innerLength = if (settings.fragmentInnerEnabled) settings.fragmentInnerLength else "",
        innerInterval = if (settings.fragmentInnerEnabled) settings.fragmentInnerInterval else "",
        innerMaxSplit = if (settings.fragmentInnerEnabled) settings.fragmentInnerMaxSplit else "",
        rank = rankOf(settings.fragmentPackets, settings.fragmentLength, settings.fragmentInnerEnabled),
        description = ""
    )

    fun rankOf(packets: String, length: String, inner: Boolean): Int = when {
        inner || (packets == "1-1" && length == "1") -> 5
        packets == "1-3" && length.startsWith("5") -> 4
        packets == "1-3" -> 3
        packets == "tlshello" && length == "6" -> 5
        else -> 1
    }

    fun neverCleartext(url: String): Boolean =
        url.trim().startsWith("https://", ignoreCase = true)
}
