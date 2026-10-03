package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.FragmentChoice
import com.marbleng.app.model.MuxChoice
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.ui.MarbleCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_FRAGMENT_PROFILES_V208 — the rewritten Fragment & Mux choice.
 *
 * The old system had two checkboxes and four raw numeric fields, and the recipe behind them was
 * written by whichever policy spoke last: the DPI ladder and Iran Mode each set
 * `fragmentEnabled` and the fragment numbers on their way to the config builder, so the user's
 * values were an *input* that anything downstream could replace. On sing-box it was worse — the
 * recipe was reduced to one boolean and the mux settings were never written at all, which is why
 * the feature "does not work at all".
 *
 * What is pinned here is the three-part answer:
 *
 *  1. a ladder of ready recipes, each with distinct fields and one sentence of copy;
 *  2. a blank id that means "the policies decide" and a `custom` id that means "my numbers" —
 *     two different things that must never be collapsed into one;
 *  3. the choice is applied last, so a recipe survives a base the policies already shaped.
 */
class FragmentProfileLadderV208Test {

    private fun profile(scheme: String = "vless", security: String = "tls", raw: String = "") =
        ProxyProfile(
            id = "p1",
            name = "Node",
            scheme = scheme,
            raw = raw,
            configJson = "{}",
            host = "1.2.3.4",
            port = 443,
            security = security
        )

    // ── the ladder itself ────────────────────────────────────────────────────────────────────

    @Test
    fun everyRecipeHasItsOwnIdentityOnDisk() {
        val ids = FragmentProfile.entries.map { it.id }
        assertEquals("recipe ids must be unique", ids.size, ids.distinct().size)
        assertTrue(ids.containsAll(listOf("off", "tlshello", "record_split", "gfw_knocker",
            "skip_chain", "full_fragment", "steel_cascade", "extreme")))
        val muxIds = MuxProfile.entries.map { it.id }
        assertEquals(muxIds.size, muxIds.distinct().size)
    }

    @Test
    fun theLadderWalksInOneDirectionOnly() {
        var previous = -1
        for (recipe in FragmentLadder) {
            assertTrue("${recipe.id} breaks the strength order", recipe.strength >= previous)
            previous = recipe.strength
        }
        assertEquals(FragmentProfile.OFF, FragmentLadder.first())
        assertFalse("the mildest recipe is off", FragmentLadder.first().enabled)
        assertTrue("the ladder ends on a real recipe", FragmentLadder.last().enabled)
    }

    @Test
    fun everyRecipeIsDescribedInExactlyOneSentence() {
        // The whole product's copy rule, applied to the copy this chapter added: an option the
        // user cannot picture from its row is an option they will never pick.
        for (recipe in FragmentProfile.entries) {
            assertTrue(
                "${recipe.id}: \"${recipe.summary}\"",
                MarbleCopy.isOneSentence(recipe.summary)
            )
        }
        for (recipe in MuxProfile.entries) {
            assertTrue("${recipe.id}: \"${recipe.summary}\"", MarbleCopy.isOneSentence(recipe.summary))
        }
    }

    @Test
    fun anUnknownIdFallsBackToOffInsteadOfGuessing() {
        assertEquals(FragmentProfile.OFF, FragmentProfile.byId("no-such-recipe"))
        assertEquals(FragmentProfile.OFF, FragmentProfile.byId(""))
        assertEquals(MuxProfile.OFF, MuxProfile.byId("no-such-recipe"))
        // Case-insensitive, because the id round-trips through JSON written by older builds.
        assertEquals(FragmentProfile.EXTREME, FragmentProfile.byId("  EXTREME "))
        assertEquals(MuxProfile.STEALTH, MuxProfile.byId("Stealth"))
    }

    // ── blank, custom and recipe are three different states ──────────────────────────────────

    @Test
    fun aBlankIdMeansThePoliciesDecideAndNotThatFragmentationIsOff() {
        assertFalse(TransportAdaptation.fragmentIsUserOwned(AppSettings()))
        assertFalse(TransportAdaptation.muxIsUserOwned(AppSettings()))
        assertNull(TransportAdaptation.namedFragment(""))
        assertNull(TransportAdaptation.namedMux(""))
    }

    @Test
    fun theCustomIdIsNotARecipe() {
        assertTrue(FragmentChoice.isCustom("custom"))
        assertTrue(FragmentChoice.isCustom(" CUSTOM "))
        assertNull(TransportAdaptation.namedFragment(FragmentChoice.CUSTOM))
        assertNull(TransportAdaptation.namedMux(MuxChoice.CUSTOM))
    }

    // ── the choice is applied last ───────────────────────────────────────────────────────────

    @Test
    fun aNamedRecipeSurvivesABaseThePoliciesAlreadyShaped() {
        // This is the regression the chapter exists for: a base whose fragmentation a policy has
        // just switched off must not be able to switch off a recipe the user picked.
        val shapedByPolicy = AppSettings(
            fragmentEnabled = false,
            fragmentPackets = "1-1",
            fragmentLength = "1",
            fragmentInterval = "4"
        )
        val user = AppSettings(fragmentProfileId = "gfw_knocker")
        val applied = TransportAdaptation.applyUserChoice(shapedByPolicy, user, profile())

        assertEquals("gfw_knocker", applied.fragmentProfileId)
        assertTrue(applied.fragmentEnabled)
        assertEquals(FragmentProfile.GFW_KNOCKER.packets, applied.fragmentPackets)
        assertEquals(FragmentProfile.GFW_KNOCKER.length, applied.fragmentLength)
        assertEquals(FragmentProfile.GFW_KNOCKER.interval, applied.fragmentInterval)
    }

    @Test
    fun aBlankChoiceLeavesThePoliciesInTheCharge() {
        val shapedByPolicy = AppSettings(
            fragmentEnabled = true,
            fragmentPackets = "1-3",
            fragmentLength = "1-3",
            fragmentInterval = "5-10"
        )
        val applied = TransportAdaptation.applyUserChoice(shapedByPolicy, AppSettings(), profile())

        assertEquals(shapedByPolicy.fragmentEnabled, applied.fragmentEnabled)
        assertEquals(shapedByPolicy.fragmentPackets, applied.fragmentPackets)
        assertEquals(shapedByPolicy.fragmentLength, applied.fragmentLength)
    }

    @Test
    fun aCustomRecipeKeepsTheNumbersTheUserTyped() {
        // A 1300-byte length belongs to no recipe on the ladder. It must reach the wire as 1300,
        // not rounded to the nearest recipe's 100-200 on the way.
        val user = AppSettings(
            fragmentProfileId = FragmentChoice.CUSTOM,
            fragmentEnabled = true,
            fragmentPackets = "2-6",
            fragmentLength = "1300",
            fragmentInterval = "37",
            fragmentMaxSplit = "9"
        )
        val applied = TransportAdaptation.applyUserChoice(AppSettings(), user, profile())

        assertEquals(FragmentChoice.CUSTOM, applied.fragmentProfileId)
        assertTrue(applied.fragmentEnabled)
        assertEquals("2-6", applied.fragmentPackets)
        assertEquals("1300", applied.fragmentLength)
        assertEquals("37", applied.fragmentInterval)
        assertEquals("9", applied.fragmentMaxSplit)
    }

    @Test
    fun aCustomMuxKeepsTheCountsTheUserTyped() {
        val user = AppSettings(
            muxProfileId = MuxChoice.CUSTOM,
            muxEnabled = true,
            muxConcurrency = 31,
            muxXudpConcurrency = 64,
            muxUdp443 = "allow"
        )
        val applied = TransportAdaptation.applyUserChoice(AppSettings(), user, profile())

        assertEquals(MuxChoice.CUSTOM, applied.muxProfileId)
        assertTrue(applied.muxEnabled)
        assertEquals(31, applied.muxConcurrency)
        assertEquals(64, applied.muxXudpConcurrency)
        assertEquals("allow", applied.muxUdp443)
    }

    // ── the one veto ─────────────────────────────────────────────────────────────────────────

    @Test
    fun multiplexingIsVetoedOnVisionAndRealityWhateverTheUserPicked() {
        assertTrue(TransportAdaptation.muxIsUnsafeFor(profile(security = "reality")))
        assertTrue(
            TransportAdaptation.muxIsUnsafeFor(
                profile(raw = "vless://x@1.2.3.4:443?flow=xtls-rprx-vision")
            )
        )
        assertFalse(TransportAdaptation.muxIsUnsafeFor(profile()))

        val user = AppSettings(muxProfileId = "balanced")
        val reality = TransportAdaptation.applyUserChoice(
            AppSettings(),
            user,
            profile(security = "reality")
        )
        assertFalse(reality.muxEnabled)
        assertEquals(MuxProfile.OFF.id, reality.muxProfileId)

        val plain = TransportAdaptation.applyUserChoice(AppSettings(), user, profile())
        assertTrue(plain.muxEnabled)
        assertEquals(MuxProfile.BALANCED.concurrency, plain.muxConcurrency)
    }

    @Test
    fun theVetoAlsoCoversHandTypedMuxValues() {
        val user = AppSettings(
            muxProfileId = MuxChoice.CUSTOM,
            muxEnabled = true,
            muxConcurrency = 12
        )
        val applied = TransportAdaptation.applyUserChoice(
            AppSettings(),
            user,
            profile(security = "reality")
        )
        assertFalse(applied.muxEnabled)
        // The numbers stay where the user put them; only the switch is refused.
        assertEquals(12, applied.muxConcurrency)
    }

    @Test
    fun aNullProfileIsTreatedAsSafeBecauseTheSchemeIsNotKnownYet() {
        val applied = TransportAdaptation.applyUserChoice(
            AppSettings(),
            AppSettings(muxProfileId = "light"),
            null
        )
        assertTrue(applied.muxEnabled)
        assertEquals(MuxProfile.LIGHT.concurrency, applied.muxConcurrency)
    }

    // ── one chooser reaching every consumer ──────────────────────────────────────────────────

    @Test
    fun aRecipeMaterialisesEveryFieldTheTwoCoresRead() {
        val applied = TransportAdaptation.withFragmentProfile(
            AppSettings(),
            FragmentProfile.OFFICIAL_SKIP_CHAIN
        )
        val recipe = FragmentProfile.OFFICIAL_SKIP_CHAIN
        assertEquals(recipe.enabled, applied.fragmentEnabled)
        assertEquals(recipe.packets, applied.fragmentPackets)
        assertEquals(recipe.length, applied.fragmentLength)
        assertEquals(recipe.interval, applied.fragmentInterval)
        assertEquals(recipe.maxSplit, applied.fragmentMaxSplit)
        assertEquals(recipe.innerEnabled, applied.fragmentInnerEnabled)
        assertEquals(recipe.innerPackets, applied.fragmentInnerPackets)
        assertEquals(recipe.innerLength, applied.fragmentInnerLength)
        assertEquals(recipe.innerInterval, applied.fragmentInnerInterval)
        assertEquals(recipe.innerMaxSplit, applied.fragmentInnerMaxSplit)
    }

    @Test
    fun turningFragmentationOffClearsTheWireRatherThanLeavingAStaleRecipe() {
        val armed = TransportAdaptation.withFragmentProfile(
            AppSettings(),
            FragmentProfile.EXTREME
        )
        val disarmed = TransportAdaptation.withFragmentProfile(armed, FragmentProfile.OFF)
        assertFalse(disarmed.fragmentEnabled)
        assertFalse(disarmed.fragmentInnerEnabled)
        assertEquals(FragmentProfile.OFF.id, disarmed.fragmentProfileId)
    }
}
