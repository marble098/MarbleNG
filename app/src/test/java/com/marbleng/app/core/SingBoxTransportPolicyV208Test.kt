package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_FRAGMENT_PROFILES_V208 — the sing-box half of the rewritten Fragment & Mux mapping.
 *
 * Before this chapter the sing-box engine received one boolean for fragmentation and nothing at
 * all for multiplexing, so every recipe on the ladder produced the same bytes on that core and
 * the Mux page was decorative. What the pinned core actually reads is three booleans and one
 * duration (`record_fragment`, `fragment`, `fragment_fallback_delay`) plus a `multiplex` object,
 * and getting any of them wrong is not cosmetic: `fragment` is documented as "poor performance"
 * and, with no `CAP_NET_RAW` on Android, makes every handshake wait the fallback delay instead of
 * measuring the real one.
 *
 * So the mapping is pinned in both directions here — a mild recipe must NOT ask for packet
 * fragmentation, and an off recipe must write no keys at all rather than `false`.
 */
class SingBoxTransportPolicyV208Test {

    private fun settingsFor(recipe: FragmentProfile, extra: AppSettings.() -> AppSettings = { this }) =
        TransportAdaptation.withFragmentProfile(AppSettings(), recipe).extra()

    // ── fragmentation ────────────────────────────────────────────────────────────────────────

    @Test
    fun anOffRecipeWritesNoKeysAtAllAndStripsAStaleOne() {
        val tls = JSONObject().apply {
            put("record_fragment", true)
            put("fragment", true)
            put("fragment_fallback_delay", "500ms")
        }
        val written = SingBoxTransportPolicy.applyFragment(tls, AppSettings())

        assertFalse(written.has("fragment"))
        assertFalse(written.has("record_fragment"))
        assertFalse(written.has("fragment_fallback_delay"))
        // A config the user never touched must not grow fields that invite a decoder to notice.
        assertEquals(0, written.length())
    }

    @Test
    fun aMildRecipeAsksForRecordFragmentationOnly() {
        val settings = settingsFor(FragmentProfile.TLSHELLO)
        assertTrue(SingBoxTransportPolicy.recordFragment(settings))
        assertFalse(
            "a strength-1 recipe must not pay for packet fragmentation",
            SingBoxTransportPolicy.packetFragment(settings)
        )

        val written = SingBoxTransportPolicy.applyFragment(JSONObject(), settings)
        assertTrue(written.optBoolean("record_fragment"))
        assertFalse(written.has("fragment"))
        assertFalse(written.has("fragment_fallback_delay"))
    }

    @Test
    fun anAggressiveRecipeAdditionallyAsksForPacketFragmentation() {
        val settings = settingsFor(FragmentProfile.GFW_KNOCKER)
        assertTrue(SingBoxTransportPolicy.recordFragment(settings))
        assertTrue(SingBoxTransportPolicy.packetFragment(settings))

        val written = SingBoxTransportPolicy.applyFragment(JSONObject(), settings)
        assertTrue(written.optBoolean("record_fragment"))
        assertTrue(written.optBoolean("fragment"))
        assertTrue(written.has("fragment_fallback_delay"))
        assertTrue(written.getString("fragment_fallback_delay").endsWith("ms"))
    }

    @Test
    fun theStrengthThresholdSitsBetweenTheMildAndTheAggressiveRungs() {
        assertFalse(SingBoxTransportPolicy.packetFragment(settingsFor(FragmentProfile.OFF)))
        assertFalse(SingBoxTransportPolicy.packetFragment(settingsFor(FragmentProfile.TLSHELLO)))
        assertFalse(SingBoxTransportPolicy.packetFragment(settingsFor(FragmentProfile.RECORD_SPLIT)))
        assertTrue(SingBoxTransportPolicy.packetFragment(settingsFor(FragmentProfile.GFW_KNOCKER)))
        assertTrue(SingBoxTransportPolicy.packetFragment(settingsFor(FragmentProfile.OFFICIAL_SKIP_CHAIN)))
        assertTrue(SingBoxTransportPolicy.packetFragment(settingsFor(FragmentProfile.FULL_FRAGMENT)))
        assertTrue(SingBoxTransportPolicy.packetFragment(settingsFor(FragmentProfile.EXTREME)))
        assertEquals(3, SingBoxTransportPolicy.PacketFragmentStrength)
    }

    @Test
    fun theFallbackDelayComesFromTheRecipeAndStaysInsideTheCoresOwnBand() {
        // "560" is above the ceiling: a half-second handshake is the most this product charges.
        assertEquals(500, SingBoxTransportPolicy.fallbackDelayMs(settingsFor(FragmentProfile.OFFICIAL_SKIP_CHAIN)))
        // "5-10" and "2" are below the floor the core itself applies, so both land on 20.
        assertEquals(20, SingBoxTransportPolicy.fallbackDelayMs(settingsFor(FragmentProfile.GFW_KNOCKER)))
        assertEquals(20, SingBoxTransportPolicy.fallbackDelayMs(settingsFor(FragmentProfile.EXTREME)))
        // No usable interval: the core's own default, not zero.
        assertEquals(
            500,
            SingBoxTransportPolicy.fallbackDelayMs(
                AppSettings(fragmentEnabled = true, fragmentInterval = "")
            )
        )
    }

    @Test
    fun theUpperEndOfARangeIsTheWaitTheRecipeAccepts() {
        assertEquals(200, SingBoxTransportPolicy.upperBoundOf("100-200"))
        assertEquals(517, SingBoxTransportPolicy.upperBoundOf("517"))
        assertEquals(130, SingBoxTransportPolicy.upperBoundOf("  130  "))
        assertNull(SingBoxTransportPolicy.upperBoundOf(""))
        assertNull(SingBoxTransportPolicy.upperBoundOf("abc"))
        assertNull("a zero wait is not a wait", SingBoxTransportPolicy.upperBoundOf("0"))
    }

    @Test
    fun fragmentationOnlyEverFollowsTheSwitchNotTheFieldsLeftBehind() {
        // Fields from a previous recipe with the switch off must produce no fragmentation.
        val stale = AppSettings(
            fragmentEnabled = false,
            fragmentPackets = "1-1",
            fragmentLength = "1",
            fragmentInterval = "4"
        )
        assertFalse(SingBoxTransportPolicy.recordFragment(stale))
        assertEquals(0, SingBoxTransportPolicy.applyFragment(JSONObject(), stale).length())
    }

    // ── multiplexing ─────────────────────────────────────────────────────────────────────────

    @Test
    fun anOffMuxRecipeWritesNoMultiplexObject() {
        assertNull(SingBoxTransportPolicy.multiplex(AppSettings()))
    }

    @Test
    fun aMuxRecipeBecomesASmuxObjectWithTheFieldsTheCoreDeclares() {
        val settings = AppSettings(muxEnabled = true, muxConcurrency = 8)
        val mux = SingBoxTransportPolicy.multiplex(settings)

        assertEquals("the pinned core reads `multiplex`, not `mux`", "multiplex", SingBoxTransportPolicy.MultiplexField)
        assertTrue(mux != null)
        requireNotNull(mux)
        assertTrue(mux.optBoolean("enabled"))
        assertEquals("h2mux", mux.getString("protocol"))
        assertEquals(8, mux.getInt("max_streams"))
        assertEquals(2, mux.getInt("max_connections"))
        assertTrue(mux.optBoolean("padding"))
        // xudpConcurrency has no smux equivalent and is honestly dropped rather than misfiled.
        assertFalse(mux.has("xudp"))
        assertFalse(mux.has("max_xudp_streams"))
    }

    @Test
    fun aHeavyMuxRecipeChoosesTheDialectWithTheCheapestPerStreamOverhead() {
        assertEquals("h2mux", SingBoxTransportPolicy.smuxProtocol(AppSettings(muxConcurrency = 11)))
        assertEquals("yamux", SingBoxTransportPolicy.smuxProtocol(AppSettings(muxConcurrency = 12)))
        assertEquals("yamux", SingBoxTransportPolicy.smuxProtocol(AppSettings(muxConcurrency = 16)))
    }

    @Test
    fun oneConnectionCarriesFourStreamsAndNeverFewerThanOne() {
        assertEquals(1, SingBoxTransportPolicy.maxConnectionsFor(1))
        assertEquals(1, SingBoxTransportPolicy.maxConnectionsFor(4))
        assertEquals(2, SingBoxTransportPolicy.maxConnectionsFor(5))
        assertEquals(4, SingBoxTransportPolicy.maxConnectionsFor(16))
        assertEquals(32, SingBoxTransportPolicy.maxConnectionsFor(128))
        // A nonsense count still yields a usable object rather than zero connections.
        assertEquals(1, SingBoxTransportPolicy.maxConnectionsFor(0))
        assertEquals(1, SingBoxTransportPolicy.maxConnectionsFor(-8))
    }

    @Test
    fun anAbsurdConcurrencyIsClampedInsteadOfWrittenToTheWire() {
        val mux = SingBoxTransportPolicy.multiplex(AppSettings(muxEnabled = true, muxConcurrency = 99_999))
        requireNotNull(mux)
        assertEquals(128, mux.getInt("max_streams"))
    }

    @Test
    fun everyLadderRecipeProducesAValidMultiplexObject() {
        for (recipe in MuxProfile.entries.filter { it.enabled }) {
            val settings = TransportAdaptation.withMuxProfile(AppSettings(), recipe)
            val mux = SingBoxTransportPolicy.multiplex(settings)
            requireNotNull(mux) { "${recipe.id} produced no multiplex object" }
            assertTrue("${recipe.id} must be enabled on the wire", mux.optBoolean("enabled"))
            assertTrue(
                "${recipe.id}: max_connections must not exceed max_streams",
                mux.getInt("max_connections") <= mux.getInt("max_streams")
            )
            assertTrue(mux.getInt("max_streams") >= 1)
        }
    }
}
