package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpiEvasionPolicyTest {
    @Test
    fun neverCleartextRejectsHttp() {
        assertTrue(DpiEvasionPolicy.neverCleartext("https://example.com/sub"))
        assertFalse(DpiEvasionPolicy.neverCleartext("http://example.com/sub"))
        assertFalse(DpiEvasionPolicy.neverCleartext("ftp://example.com/sub"))
    }

    @Test
    fun healDoesNotWeakenAHealthyRecipe() {
        val base = DpiEvasionPolicy.applyRecipe(AppSettings(), DpiEvasionPolicy.TLSHELLO_SNI)
        val healed = DpiEvasionPolicy.heal(
            base,
            DpiEvasionPolicy.PathEvidence(
                pingMs = 80,
                jitterMs = 4,
                successPercent = 100,
                samples = 6
            ),
            IranModeState()
        )
        assertEquals(base.fragmentPackets, healed.fragmentPackets)
        assertEquals(base.fragmentLength, healed.fragmentLength)
        assertTrue(DpiEvasionPolicy.recipeFrom(healed).rank >= DpiEvasionPolicy.TLSHELLO_SNI.rank)
    }

    @Test
    fun healEscalatesOnPacketLoss() {
        val base = DpiEvasionPolicy.applyRecipe(AppSettings(), DpiEvasionPolicy.TLSHELLO)
        val healed = DpiEvasionPolicy.heal(
            base,
            DpiEvasionPolicy.PathEvidence(
                pingMs = 90,
                jitterMs = 6,
                successPercent = 40,
                samples = 4
            ),
            IranModeState()
        )
        assertEquals(DpiEvasionPolicy.MAX_SLICE.packets, healed.fragmentPackets)
        assertEquals(DpiEvasionPolicy.MAX_SLICE.length, healed.fragmentLength)
        assertTrue(healed.mtuMax <= 1280)
    }

    /**
     * MARBLE_FAKE_IP_V184 — a slow but stable link (high ping, no loss, no jitter) is distance
     * and capacity, not a packet-level defect. heal() must not arm the fragment recipe or cap
     * the MTU for it: WhatsApp-style flows pay a fresh handshake per connection, and shredding
     * every ClientHello into paced 10-30 byte records on a merely slow link made interactive
     * apps measurably worse. Only the timing budgets widen.
     */
    @Test
    fun healDoesNotFragmentOnPlainHighPing() {
        val base = DpiEvasionPolicy.applyRecipe(AppSettings(), DpiEvasionPolicy.TLSHELLO)
        val healed = DpiEvasionPolicy.heal(
            base,
            DpiEvasionPolicy.PathEvidence(
                pingMs = 320,
                jitterMs = 5,
                successPercent = 100,
                samples = 6
            ),
            IranModeState()
        )
        assertEquals(base.fragmentPackets, healed.fragmentPackets)
        assertEquals(base.fragmentLength, healed.fragmentLength)
        assertTrue(
            "MTU ceiling must not drop for a slow-but-stable link",
            healed.mtuMax >= 1500
        )
        assertTrue("slow links keep the widened bench budget", healed.benchTimeoutSec >= 14)
        assertTrue("slow links keep the widened precheck budget", healed.tcpPrecheckTimeoutMs >= 3_000)
    }

    /**
     * The re-gating must not disarm the real defects: sustained jitter still escalates the
     * fragment recipe (RECORD_SPLIT) and caps the MTU at 1360.
     */
    @Test
    fun healStillEscalatesOnJitter() {
        val base = DpiEvasionPolicy.applyRecipe(AppSettings(), DpiEvasionPolicy.TLSHELLO)
        val healed = DpiEvasionPolicy.heal(
            base,
            DpiEvasionPolicy.PathEvidence(
                pingMs = 90,
                jitterMs = 40,
                successPercent = 100,
                samples = 6
            ),
            IranModeState()
        )
        assertEquals(DpiEvasionPolicy.RECORD_SPLIT.packets, healed.fragmentPackets)
        assertEquals(DpiEvasionPolicy.RECORD_SPLIT.length, healed.fragmentLength)
        assertTrue(healed.mtuMax <= 1360)
    }

    /**
     * SNI + TCP-reset DPI uses the chained fragment recipe. The outer hop is the packet
     * split (1-1/1-3/5-10), NOT Xray's "tlshello" record-rewriter: real servers (Fastly,
     * Cloudflare, GitHub, AWS — RST, verified on v26.7.28) and Iran's 2026 DPI reject that
     * shape (Xray #4370, #5969). The recipe still chains directly-dialing fragmentation.
     */
    @Test
    fun sniPlusResetUsesChainedFragmentSplit() {
        val recipe = DpiEvasionPolicy.connectionRecipe(
            IranModeState(
                active = true,
                techniques = setOf(CensorTechnique.SNI_FILTERING, CensorTechnique.TCP_RESET)
            )
        )
        assertEquals("1-1", recipe.packets)
        assertEquals("1-3", recipe.length)
        assertEquals("5-10", recipe.interval)
        assertTrue(recipe.innerEnabled)
        assertEquals("1-1", recipe.innerPackets)
        assertEquals("4", recipe.innerInterval)
        assertEquals("517", recipe.innerMaxSplit)
    }

    @Test
    fun iranShieldCountermeasuresStayTypedAgainstState() {
        val lines = IranShield.countermeasures(
            IranModeState(
                active = true,
                techniques = setOf(CensorTechnique.SNI_FILTERING)
            )
        )
        assertTrue(lines.isNotEmpty())
        assertTrue(
            lines.any {
                it.contains("fragment", ignoreCase = true) ||
                    it.contains("shred", ignoreCase = true) ||
                    it.contains("TLS", ignoreCase = true)
            }
        )
    }

    @Test
    fun githubRawAndGistUseJsdelivrMirrors() {
        val raw = DpiAwareFetcher.candidateUrls(
            "https://raw.githubusercontent.com/owner/repo/main/list.txt"
        )
        assertEquals(
            listOf(
                "https://raw.githubusercontent.com/owner/repo/main/list.txt",
                "https://cdn.jsdelivr.net/gh/owner/repo@main/list.txt"
            ),
            raw
        )
        val gist = DpiAwareFetcher.candidateUrls(
            "https://gist.githubusercontent.com/owner/abc123/raw/deadbeef/nodes.txt"
        )
        assertTrue(gist.any { it.startsWith("https://cdn.jsdelivr.net/gh/") })
        assertTrue(gist.all { it.startsWith("https://") })
    }

    @Test
    fun candidateUrlsRejectCleartext() {
        var threw = false
        try {
            DpiAwareFetcher.candidateUrls("http://provider.example/sub")
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

}
