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
     *
     * 2-hop only (no middle): matches the official XTLS skip-fragment → full-fragment shape and
     * keeps multi-CDN first flights (YouTube / X / Reddit) under CDN idle cutoffs.
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

    /**
     * Official XTLS Serverless-for-Iran "skip-fragment" alternative pair:
     *   skip-fragment 1-1 / 130 / 560 / maxSplit 4  →  _chain-skip 2-4 / 1 / 4 / maxSplit 130
     * Longer first-packet delay defeats SNI reassembly that watches the first few ms.
     */
    val OFFICIAL_SKIP_CHAIN: FragmentRecipe = FragmentRecipe(
        packets = "1-1",
        length = "130",
        interval = "560",
        maxSplit = "4",
        innerPackets = "2-4",
        innerLength = "1",
        innerInterval = "4",
        innerMaxSplit = "130",
        rank = 6,
        description = "Official XTLS skip-fragment chain • delayed first write + paced follow-ups"
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
     * Default connection chain — the official XTLS 2-hop pair that actually completes TLS to
     * multi-CDN fronts (YouTube / X / Reddit / Fastly / Cloudflare):
     *   outer 1-1 / 1-3 / 5-10  →  full-fragment 1-1 / 1 / 4 / maxSplit 517
     * No middle hop: the previous 3-layer cascade added an untested slicer that pushed the first
     * flight past CDN idle cutoffs and left media sites half-loaded.
     */
    val MULTI_LAYER_CASCADE: FragmentRecipe = FragmentRecipe(
        packets = "1-1",
        length = "1-3",
        interval = "5-10",
        innerPackets = "1-1",
        innerLength = "1",
        innerInterval = "4",
        innerMaxSplit = "517",
        rank = 6,
        description = "Official 2-hop cascade • packet-split outer → byte-level full fragment"
    )

    /**
     * Aggressive path for extreme operators (MCI / Irancell clampdowns). Uses the official
     * XTLS skip-fragment delay pair rather than a third hop — longer first-write delay defeats
     * SNI reassembly without the CDN timeout tax of a middle hop.
     */
    val AGGRESSIVE_CASCADE: FragmentRecipe = FragmentRecipe(
        packets = "1-1",
        length = "130",
        interval = "560",
        maxSplit = "4",
        innerPackets = "2-4",
        innerLength = "1",
        innerInterval = "4",
        innerMaxSplit = "130",
        rank = 6,
        description = "Official skip-fragment cascade • delayed first write for severe DPI"
    )

    /**
     * Extreme path: GFW-knocker outer + official full-fragment inner, still 2 hops.
     * Byte-level shredding on both ends without a middle hop that stalls multi-CDN.
     */
    val EXTREME_ANTI_DPI: FragmentRecipe = FragmentRecipe(
        packets = "1-1",
        length = "1-3",
        interval = "5-10",
        maxSplit = "4",
        innerPackets = "1-1",
        innerLength = "1",
        innerInterval = "4",
        innerMaxSplit = "517",
        rank = 7,
        description = "Extreme 2-hop micro-fragment • GFW-knocker outer + full 517-byte shred"
    )

    // ============================================================================
    // Per-operator steel profiles (MARBLE_OPERATOR_FREEDOM_V91)
    //
    // Source: XTLS official Serverless-for-Iran chain (skip-fragment 1-1/130/560/4 →
    // _chain-skip 2-4/1/4/130 → full-fragment 1-1/1/4/517) plus Xray-core discussion #5969
    // field notes from MCI / Irancell / Shatel (April 2026): plain tlshello TCP fragmentation
    // is reassembled before inspection, so the steel chain uses delayed first writes and
    // byte-level shredding instead of a single tiny-record mode.
    // ============================================================================

    /**
     * MCI / Hamrah-e-Aval steel — strictest mobile DPI in Iran.
     * GFW-knocker packet-split outer, the official skip-fragment delay pair in the middle (the
     * hop XTLS ships for operators that reassemble the first few ms), finished by the 517-byte
     * full-fragment. 3-stage, no tlshello record rewriting (servers RST that shape).
     */
    val HAMRAH_STEEL: FragmentRecipe = FragmentRecipe(
        packets = "1-1",
        length = "1-3",
        interval = "5-10",
        maxSplit = "4",
        middlePackets = "1-1",
        middleLength = "130",
        middleInterval = "560",
        middleMaxSplit = "4",
        innerPackets = "1-1",
        innerLength = "1",
        innerInterval = "4",
        innerMaxSplit = "517",
        rank = 9,
        description = "MCI/Hamrah-e-Aval steel • split → skip-fragment 130/560 → full-fragment 517"
    )

    /**
     * MTN Irancell steel — heavy volume-based endpoint blocking and ~50% packet-loss throttling
     * on weak endpoints. Delayed first write plus the official _chain-skip then full-fragment,
     * with aggressive UDP noise configured by the preset layer.
     */
    val IRANCELL_STEEL: FragmentRecipe = FragmentRecipe(
        packets = "1-1",
        length = "130",
        interval = "560",
        maxSplit = "4",
        middlePackets = "2-4",
        middleLength = "1",
        middleInterval = "4",
        middleMaxSplit = "130",
        innerPackets = "1-1",
        innerLength = "1",
        innerInterval = "4",
        innerMaxSplit = "517",
        rank = 9,
        description = "MTN Irancell steel • skip-fragment → _chain-skip → full-fragment 517"
    )

    /**
     * Shatel steel — private fixed-line ISP that tracks MCI's blocking decisions closely.
     * GFW-knocker outer split, official _chain-skip, 517-byte full-fragment.
     */
    val SHATEL_STEEL: FragmentRecipe = FragmentRecipe(
        packets = "1-1",
        length = "1-3",
        interval = "5-10",
        maxSplit = "4",
        middlePackets = "2-4",
        middleLength = "1",
        middleInterval = "4",
        middleMaxSplit = "130",
        innerPackets = "1-1",
        innerLength = "1",
        innerInterval = "4",
        innerMaxSplit = "517",
        rank = 8,
        description = "Shatel steel • packet-split outer → _chain-skip → full-fragment 517"
    )

    /**
     * Rightel steel — third mobile operator; follows national policy with a lag, so its profile
     * keeps the proven record-split middle hop while still ending in the 517-byte shred.
     */
    val RIGHTEL_STEEL: FragmentRecipe = FragmentRecipe(
        packets = "1-1",
        length = "1-3",
        interval = "5-10",
        maxSplit = "4",
        middlePackets = "1-3",
        middleLength = "10-30",
        middleInterval = "5-10",
        middleMaxSplit = "768",
        innerPackets = "1-1",
        innerLength = "1",
        innerInterval = "4",
        innerMaxSplit = "517",
        rank = 7,
        description = "Rightel steel • split outer → record-split middle → full-fragment 517"
    )

    private val OPERATOR_STEEL = mapOf(
        197207L to HAMRAH_STEEL,   // MCI / Hamrah-e-Aval
        44244L to IRANCELL_STEEL,  // Irancell
        57218L to RIGHTEL_STEEL,   // Rightel
        31549L to SHATEL_STEEL     // Shatel
    )

    /**
     * Map a detected Iranian operator to its steel recipe.
     *
     * Matching uses the curated ASN table first (MCI 197207, Irancell 44244, Rightel 57218,
     * Shatel 31549) and falls back to operator-name fingerprints so regional ASNs of the same
     * operator still get the right profile.
     */
    fun operatorRecipeFor(isp: IranIsp?): FragmentRecipe? {
        if (isp == null) return null
        OPERATOR_STEEL[isp.asn.toLong()]?.let { return it }

        val name = "${isp.name} ${isp.shortName} ${isp.persianName}".lowercase()
        return when {
            "hamrah" in name || "mci" in name || "mobile communication company" in name ||
                "hamrah-e aval" in name -> HAMRAH_STEEL
            "irancell" in name || "iran cell" in name || "mtn" in name -> IRANCELL_STEEL
            "rightel" in name -> RIGHTEL_STEEL
            "shatel" in name || "aria shatel" in name -> SHATEL_STEEL
            else -> null
        }
    }

    fun connectionRecipe(state: IranModeState): FragmentRecipe {
        val tier = IranShield.tier(state)
        val sni = CensorTechnique.SNI_FILTERING in state.techniques
        val reset = CensorTechnique.TCP_RESET in state.techniques
        // MARBLE_OPERATOR_STEEL_V91 — match the detected Iranian operator to its steel profile
        // before the generic severity ladder. This is what makes the countermeasures feel tuned
        // per carrier without any user action. Only while the mode is actually active: an idle
        // detection must never shape a connection.
        if (state.active) operatorRecipeFor(state.isp)?.let { return it }
        return when {
            !state.active -> TLSHELLO
            CensorTechnique.NATIONAL_INTRANET in state.techniques ||
                tier >= 3 ||
                (sni && reset) -> TLSHELLO_SNI
            sni || tier >= 2 -> RECORD_SPLIT
            else -> TLSHELLO
        }
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
