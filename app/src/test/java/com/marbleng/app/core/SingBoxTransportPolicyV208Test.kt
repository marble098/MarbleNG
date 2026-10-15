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
