package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
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

    @Test
    fun sniPlusResetUsesChainedTlshello() {
        val recipe = DpiEvasionPolicy.connectionRecipe(
            IranModeState(
                active = true,
                techniques = setOf(CensorTechnique.SNI_FILTERING, CensorTechnique.TCP_RESET)
            )
        )
        assertEquals("tlshello", recipe.packets)
        assertEquals("6", recipe.length)
        assertTrue(recipe.innerEnabled)
        assertEquals("1-1", recipe.innerPackets)
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

    @Test
    fun serverlessPinsIdentityAndDisablesRotation() {
        val profile = ProxyProfile(
            id = ServerlessFreedomEngine.PROFILE_ID,
            name = ServerlessFreedomEngine.DISPLAY_NAME,
            scheme = "freedom",
            raw = "freedom://fragment",
            configJson = "{}",
            subscriptionId = ServerlessFreedomEngine.SOURCE_ID
        )
        assertTrue(ServerlessFreedomEngine.isServerless(profile))
        val pinned = ServerlessFreedomEngine.pinSession(AppSettings())
        assertFalse(pinned.continuousOptimizerEnabled)
        assertFalse(pinned.smartFallbackEnabled)
        assertTrue(pinned.identityGuardEnabled)
        assertTrue(pinned.identityGuardStrictNoFailover)
        assertTrue(pinned.fragmentEnabled)
        assertTrue(pinned.fragmentInnerEnabled)
    }
}
