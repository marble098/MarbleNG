package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.SingBoxMuxProtocols
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_CORE_OPTIONS_V211 — the sing-box half of the cores' own options.
 *
 * The fragment mapping that used to be pinned here is gone with the feature. What remains, and
 * what this file now pins, is the rule the whole release is built on: the policy translates the
 * *user's* multiplex settings into the pinned core's own field names, writes nothing when the user
 * asked for nothing, and never invents a key.
 *
 * Getting a key wrong on this core is not cosmetic: `sing-box check` rejects the whole document on
 * an unknown field, so a typo is a tunnel that refuses to start rather than a setting that is
 * quietly ignored.
 */
class SingBoxTransportPolicyV208Test {

    private fun profile(
        scheme: String = "vless",
        security: String = "",
        raw: String = ""
    ) = ProxyProfile(
        id = "node-1",
        name = "Node",
        scheme = scheme,
        raw = raw,
        configJson = "",
        host = "example.com",
        port = 443,
        security = security
    )

    // ── the user's multiplex object ──────────────────────────────────────────────────────────

    @Test
    fun anOffSettingWritesNoObjectAtAll() {
        assertNull(SingBoxTransportPolicy.multiplex(AppSettings()))
    }

    @Test
    fun anOnSettingWritesTheCoresOwnFieldNames() {
        val settings = AppSettings(
            singBoxMuxEnabled = true,
            singBoxMuxProtocol = "h2mux",
            singBoxMuxMaxConnections = 8,
            singBoxMuxMinStreams = 2,
            singBoxMuxMaxStreams = 64,
            singBoxMuxPadding = true
        )
        val mux = SingBoxTransportPolicy.multiplex(settings)
        assertNotNull(mux)
        assertEquals(true, mux!!.getBoolean("enabled"))
        assertEquals("h2mux", mux.getString("protocol"))
        assertEquals(8, mux.getInt("max_connections"))
        assertEquals(2, mux.getInt("min_streams"))
        assertEquals(64, mux.getInt("max_streams"))
        assertEquals(true, mux.getBoolean("padding"))
        // Xray's spelling must never leak into a sing-box document.
        assertFalse(mux.has("concurrency"))
        assertFalse(mux.has("xudpConcurrency"))
    }

    @Test
    fun storedValuesOutsideTheCoresRangeAreClampedNotWritten() {
        val settings = AppSettings(
            singBoxMuxEnabled = true,
            singBoxMuxMaxConnections = 9999,
            singBoxMuxMinStreams = -4,
            singBoxMuxMaxStreams = 99999
        )
        val mux = SingBoxTransportPolicy.multiplex(settings)!!
        assertTrue(mux.getInt("max_connections") in 1..128)
        assertTrue(mux.getInt("min_streams") in 0..128)
        assertTrue(mux.getInt("max_streams") in 1..1024)
    }

    @Test
    fun anUnknownStoredProtocolFallsBackToAKnownOne() {
        val settings = AppSettings(singBoxMuxEnabled = true, singBoxMuxProtocol = "not-a-protocol")
        val mux = SingBoxTransportPolicy.multiplex(settings)!!
        assertTrue(
            "a protocol the core does not know is a config it rejects",
            mux.getString("protocol") in SingBoxMuxProtocols.ALL
        )
    }

    @Test
    fun theFieldNameIsMultiplexAndNeverMux() {
        assertEquals("multiplex", SingBoxTransportPolicy.MultiplexField)
        val mux = SingBoxTransportPolicy.multiplex(AppSettings(singBoxMuxEnabled = true))!!
        assertFalse(mux.has("mux"))
    }

    // ── the veto that is a fact about the wire ───────────────────────────────────────────────

    @Test
    fun realityAndVisionAreReportedAsUnsafeForMultiplexing() {
        assertTrue(
            "REALITY negotiates its own flow control",
            SingBoxTransportPolicy.muxIsUnsafeFor(profile(security = "reality"))
        )
        assertTrue(
            "XTLS Vision does too",
            SingBoxTransportPolicy.muxIsUnsafeFor(profile(raw = "flow=xtls-rprx-vision"))
        )
        assertFalse(SingBoxTransportPolicy.muxIsUnsafeFor(profile(security = "tls")))
        assertFalse(SingBoxTransportPolicy.muxIsUnsafeFor(null))
    }

    @Test
    fun summaryStatesWhatIsActuallyOnTheWire() {
        assertTrue(SingBoxTransportPolicy.summary(AppSettings()).startsWith("Off"))
        val on = SingBoxTransportPolicy.summary(
            AppSettings(singBoxMuxEnabled = true, singBoxMuxProtocol = "yamux", singBoxMuxMaxConnections = 4)
        )
        assertTrue(on.contains("yamux"))
        assertTrue("the sentence states the numbers on the wire", on.contains("4 connection(s)"))
    }
}
