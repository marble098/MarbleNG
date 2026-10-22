package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.FlatAccent
import com.marbleng.app.model.FragmentStrategy
import com.marbleng.app.model.PowerProfile
import com.marbleng.app.model.parseFlatAccent
import com.marbleng.app.model.parseFragmentStrategy
import com.marbleng.app.model.parsePowerProfile
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_MERIDIAN_V221 — the chapter's small, load-bearing pieces.
 *
 * The warm cache and the fragment policy are both *invisible* when they work, which is exactly why
 * they are pinned here: a cache that never expires is a route to a dead address, and a fragment
 * policy that writes the wrong key is a config the core refuses to start.
 */
class MeridianV221Test {
    private fun address(last: Int): InetAddress =
        InetAddress.getByAddress(byteArrayOf(10, 0, 0, last.toByte()))

    @Test
    fun aWarmNameAnswersInsideItsTtlAndForgetsAfterIt() {
        ConnectWarmup.clear()
        val host = "Example.COM"
        ConnectWarmup.remember(host, listOf(address(1)), nowMs = 1_000L)

        assertEquals(1, ConnectWarmup.cached("example.com", nowMs = 1_500L)?.size)
        assertEquals(1, ConnectWarmup.cached("[example.com]", nowMs = 1_500L)?.size)
        assertNull(
            "an entry past its TTL must be dropped, not returned",
            ConnectWarmup.cached(host, nowMs = 1_000L + ConnectWarmup.TTL_MS + 1L)
        )
        assertNull(ConnectWarmup.cached(host, nowMs = 999L))
    }

    @Test
    fun aNegativeAnswerIsNeverCached() {
        ConnectWarmup.clear()
        ConnectWarmup.remember("empty.example", emptyList(), nowMs = 10L)
        assertNull(ConnectWarmup.cached("empty.example", nowMs = 11L))
        assertEquals(0, ConnectWarmup.size())
    }

    @Test
    fun theCacheIsBounded() {
        ConnectWarmup.clear()
        for (i in 0 until ConnectWarmup.MAX_HOSTS * 3) {
            ConnectWarmup.remember("host-$i.example", listOf(address(i % 250 + 1)), nowMs = i.toLong())
        }
        assertTrue(
            "the warm cache must stay bounded, was ${ConnectWarmup.size()}",
            ConnectWarmup.size() <= ConnectWarmup.MAX_HOSTS
        )
        ConnectWarmup.clear()
        assertEquals(0, ConnectWarmup.size())
    }

    @Test
    fun warmingAnAlreadyWarmListDoesNoWork() {
        ConnectWarmup.clear()
        ConnectWarmup.remember("warm.example", listOf(address(9)))
        val warmed = ConnectWarmup.warm(listOf("warm.example"), AppSettings(), timeoutMs = 250)
        assertEquals("nothing to resolve, so nothing was resolved", 0, warmed)
        ConnectWarmup.clear()
    }

    @Test
    fun theFragmentPolicyMapsEveryStrategyOntoRealCoreKeys() {
        assertTrue(FragmentPolicy.specFor(FragmentStrategy.OFF.id).empty)

        // Automatic is a promise about the network, not a key: it writes nothing by itself, and
        // the repository folds it into RECORD only on a path it has measured as filtering.
        assertTrue(FragmentPolicy.specFor(FragmentStrategy.AUTO.id).empty)
        assertEquals(
            FragmentStrategy.RECORD.id,
            FragmentPolicy.effectiveStrategyId(FragmentStrategy.AUTO.id, censored = true)
        )
        assertEquals(
            FragmentStrategy.OFF.id,
            FragmentPolicy.effectiveStrategyId(FragmentStrategy.AUTO.id, censored = false)
        )
        assertEquals(
            FragmentStrategy.FULL.id,
            FragmentPolicy.effectiveStrategyId(FragmentStrategy.FULL.id, censored = false)
        )

        val record = FragmentPolicy.specFor(FragmentStrategy.RECORD.id)
        assertTrue(record.recordFragment)
        assertFalse(record.fragmentHandshake)

        val full = FragmentPolicy.specFor(FragmentStrategy.FULL.id)
        assertTrue(full.recordFragment)
        assertTrue(full.fragmentHandshake)
        assertEquals(FragmentPolicy.FALLBACK_DELAY, full.fallbackDelay)
    }

    @Test
    fun aQuicTransportIsNeverGivenATlsRecordTreatment() {
        for (strategy in FragmentStrategy.entries) {
            assertTrue(
                "$strategy must be a no-op for QUIC",
                FragmentPolicy.specFor(strategy.id, quic = true).empty
            )
        }
    }

    @Test
    fun everyFragmentStrategyHasAReadableSentence() {
        for (strategy in FragmentStrategy.entries) {
            assertTrue(FragmentPolicy.describe(strategy.id).isNotBlank())
        }
    }

    @Test
    fun theNewIdsParseAndUnknownValuesFallBackSafely() {
        assertEquals(FlatAccent.OCEAN, parseFlatAccent("blue"))
        assertEquals(FlatAccent.VIOLET, parseFlatAccent("  VIOLET "))
        assertEquals(FlatAccent.DEFAULT, parseFlatAccent("not-a-colour"))

        assertEquals(PowerProfile.EFFICIENT, parsePowerProfile("battery"))
        assertEquals(PowerProfile.EXTREME, parsePowerProfile("MAX"))
        assertEquals(PowerProfile.DEFAULT, parsePowerProfile(""))

        assertEquals(FragmentStrategy.OFF, parseFragmentStrategy("none"))
        assertEquals(FragmentStrategy.FULL, parseFragmentStrategy("fragment"))
        assertEquals(FragmentStrategy.DEFAULT, parseFragmentStrategy("???"))
    }

    @Test
    fun aFreshSettingsObjectCarriesTheFlatChaptersDefaults() {
        val settings = AppSettings()
        assertTrue("flat is the shipped surface", settings.flatInterface)
        assertTrue("warmup is on", settings.warmupOnLaunch)
        assertFalse("a scan never applies itself without consent", settings.censorshipAutoApply)
        assertEquals(FlatAccent.OCEAN.id, settings.accentTheme)
        assertEquals(PowerProfile.BALANCED.id, settings.powerProfile)
        assertEquals(FragmentStrategy.AUTO.id, settings.fragmentStrategy)
    }
}
