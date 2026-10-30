package com.marbleng.app.model

import com.marbleng.app.core.ServersRowSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_PLAIN_VOICE_V246 — two complaints, pinned.
 *
 * 1. "When I select a server, a text appears behind the ping and the three dots." It was the
 *    swipe-to-delete label showing through a selected row's translucent fill; [ServersRowSurface]
 *    now draws it only while the row is actually being swiped.
 * 2. "The titles and sentences in Settings are complicated." [PlainVoice] keeps every rewrite the
 *    chapter shipped and this test proves each one is really plainer — no jargon, not identical,
 *    and at most 25 % longer.
 */
class PlainVoiceV246Test {

    @Test
    fun theRevealIsInvisibleAtRestAndFullAfterAFifthOfTheSwipe() {
        assertEquals(0f, ServersRowSurface.revealAlpha(0f), 0f)
        assertEquals(0f, ServersRowSurface.revealAlpha(0.01f), 0f)
        assertEquals(0f, ServersRowSurface.revealAlpha(Float.NaN), 0f)
        assertEquals(1f, ServersRowSurface.revealAlpha(ServersRowSurface.REVEAL_FULL), 0f)
        assertEquals(1f, ServersRowSurface.revealAlpha(1f), 0f)
        // Clamped at both ends: a spring overshoot may hand it 1.4.
        assertEquals(1f, ServersRowSurface.revealAlpha(1.4f), 0f)

        var previous = 0f
        var step = 0
        while (step <= 100) {
            val alpha = ServersRowSurface.revealAlpha(step / 100f)
            assertTrue("alpha went backwards at $step", alpha >= previous)
            assertTrue("alpha out of range at $step", alpha in 0f..1f)
            previous = alpha
            step++
        }
    }

    @Test
    fun theRevealNeedsRealTravelNotATremor() {
        // A two-percent wobble is a finger resting, not a decision to delete something.
        assertEquals(0f, ServersRowSurface.revealAlpha(0.02f), 0f)
        assertTrue(ServersRowSurface.revealAlpha(0.10f) > 0.4f)
        assertTrue(ServersRowSurface.REVEAL_FLOOR < ServersRowSurface.REVEAL_FULL)
    }

    @Test
    fun everyShippedRewriteIsActuallySimpler() {
        assertTrue("no rewrites are recorded", PlainVoice.REWRITES.isNotEmpty())
        assertEquals(emptyList<String>(), PlainVoice.violations())
        PlainVoice.REWRITES.forEach { rewrite ->
            assertTrue(
                "${rewrite.before} -> ${rewrite.after} kept the jargon",
                PlainVoice.isPlain(rewrite.after)
            )
            assertFalse(
                "${rewrite.before} -> ${rewrite.after} did not change anything",
                rewrite.before == rewrite.after
            )
            assertTrue(
                "${rewrite.before} -> ${rewrite.after} grew too long",
                rewrite.after.length <= rewrite.before.length * 1.25
            )
        }
    }

    @Test
    fun theRuleItselfRejectsTheThingsTheReportComplainedAbout() {
        assertTrue(PlainVoice.isPlain("How you connect"))
        assertFalse(PlainVoice.isPlain("Geo direct domestic separation"))
        assertFalse(PlainVoice.isPlain("Tunnel Datapath"))
        assertFalse(PlainVoice.isPlain("Apply Recommended Prescription"))
        assertFalse(PlainVoice.isPlain("Council verdict"))

        // A rewrite that merely renames the mechanism is not plainer.
        assertFalse(PlainVoice.isSimpler("Tunnel Datapath", "Tunnel Datapath"))
        assertFalse(PlainVoice.isSimpler("Tunnel Datapath", "Datapath of the tunnel"))
        // Clarity may cost a few characters, never a longer sentence.
        assertTrue(PlainVoice.isSimpler("Proxy Core Engine", "Proxy engine"))
        assertFalse(
            PlainVoice.isSimpler(
                "Proxy Core Engine",
                "The engine that runs every proxy on this device"
            )
        )
        assertEquals(listOf("DPI"), PlainVoice.hits("DPI filters"))
    }

    @Test
    fun constantsNeverReachTheScreenAsShouting() {
        // `1.1.1.1 · FORCE_IPV4` was on the Settings hub for years: an enum name in a subtitle.
        assertEquals("IPv4 only", PlainVoice.addressFamilyLabel("FORCE_IPV4"))
        assertEquals("IPv6 only", PlainVoice.addressFamilyLabel("FORCE_IPV6"))
        assertEquals("Automatic", PlainVoice.addressFamilyLabel("SMART"))
        assertEquals("IPv4 first", PlainVoice.addressFamilyLabel("PREFER_IPV4"))
        // An unknown constant loses its screaming case but never its meaning.
        assertEquals("some mode", PlainVoice.addressFamilyLabel("SOME_MODE"))
        assertFalse(PlainVoice.addressFamilyLabel("FORCE_IPV4").contains("_"))

        // `UseIPv4` is a resolver argument, not a sentence.
        assertEquals("IPv4 only", PlainVoice.dnsStrategyLabel("UseIPv4"))
        assertEquals("IPv6 only", PlainVoice.dnsStrategyLabel("UseIPv6"))
        assertEquals("IPv4 or IPv6", PlainVoice.dnsStrategyLabel("UseIP"))

        // Every label is plain: the same rule applies to the words a constant turns into.
        PlainVoice.ADDRESS_FAMILY_LABELS.values.forEach { assertTrue(it, PlainVoice.isPlain(it)) }
        PlainVoice.DNS_STRATEGY_LABELS.values.forEach { assertTrue(it, PlainVoice.isPlain(it)) }
    }

    @Test
    fun theRewriteListIsUniqueSoAScreenshotCanBeReadAgainstIt() {
        val befores = PlainVoice.REWRITES.map { it.before }
        val afters = PlainVoice.REWRITES.map { it.after }
        assertEquals(befores.size, befores.toSet().size)
        assertEquals(afters.size, afters.toSet().size)
        // No rewrite may land on another rewrite's old wording: that would make the audit lie.
        assertTrue(befores.none { it in afters })
    }
}
