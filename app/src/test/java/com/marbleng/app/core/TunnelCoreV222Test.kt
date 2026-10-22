package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_TUN2SOCKS_CORE_V222 — the second datapath's contract, tested without a device.
 *
 * The datapath is not a second proxy engine: it is the engine that carries the VpnService TUN
 * device to whichever proxy core the user selected. These tests pin the three things that can
 * only be caught here: the id round-trip that Settings persists, the clamped options the native
 * call receives, and the promise that a build without `libtun2socks.so` degrades to HEV with a
 * reason instead of offering a choice that would crash on the first connect.
 */
class TunnelCoreV222Test {
    @Test
    fun theDatapathIdSurvivesEverySpellingTheStoreAndDocsUse() {
        assertEquals(TunnelCore.HEV, parseTunnelCore("hev"))
        assertEquals(TunnelCore.HEV, parseTunnelCore(" HEV "))
        assertEquals(TunnelCore.HEV, parseTunnelCore("hev-socks5-tunnel"))
        assertEquals(TunnelCore.TUN2SOCKS, parseTunnelCore("tun2socks"))
        assertEquals(TunnelCore.TUN2SOCKS, parseTunnelCore("Tun2Socks"))
        assertEquals(TunnelCore.TUN2SOCKS, parseTunnelCore("t2s"))
        assertEquals("a stored blank is the historical default", TunnelCore.HEV, parseTunnelCore(""))
        assertEquals("an unknown id never invents a core", TunnelCore.HEV, parseTunnelCore("mihomo"))
        TunnelCore.entries.forEach { core ->
            assertEquals(core, parseTunnelCore(core.id))
        }
    }

    @Test
    fun theDatapathIsAnAxisOfItsOwnNextToTheProxyCore() {
        val settings = AppSettings(coreEngineId = "singbox", tunnelCoreId = "tun2socks")
        assertEquals(CoreEngine.SINGBOX, settings.coreEngine())
        assertEquals(TunnelCore.TUN2SOCKS, settings.tunnelCore())
        // The Xray engine is not a tunnel engine, and sing-box is not a datapath: the two
        // selections compose, which is what the Settings page states.
        assertEquals(CoreEngine.XRAY, parseCoreEngine("xray"))
    }

    @Test
    fun hevIsAlwaysAvailableAndTun2SocksExplainsItselfWhenItIsNot() {
        assertTrue(TunnelCoreInfo.isAvailable(TunnelCore.HEV))
        assertEquals("", TunnelCoreInfo.unavailableReason(TunnelCore.HEV))

        // A JVM test has no Android native library directory, so the second datapath must report
        // itself unavailable — the same path a device with an ABI the APK does not ship takes.
        assertFalse(TunnelCoreInfo.isAvailable(TunnelCore.TUN2SOCKS))
        val reason = TunnelCoreInfo.unavailableReason(TunnelCore.TUN2SOCKS)
        assertTrue(reason, reason.contains(TunnelCoreInfo.TUN2SOCKS_LIBRARY))
        assertTrue(reason, reason.contains("hev-socks5-tunnel"))

        val state = resolveTunnelCoreState(TunnelCore.TUN2SOCKS)
        assertFalse(state.available)
        assertEquals("tun2socks", state.label)
    }

    @Test
    fun theNativeCallIsBuiltFromClampedSettingsAndNothingElse() {
        val config = Tun2SocksPolicy.buildConfig(
            socksPort = 10808,
            mtu = 1500,
            tunFd = 42,
            udpTimeoutSec = 0,
            tcpSendBufferKb = -5,
            tcpReceiveBufferKb = 999_999,
            moderateReceiveBuffer = true,
            logLevel = "TRACE",
            logPath = "  /data/logs/tun2socks.log  "
        )
        assertNotNull(config)
        config!!
        assertEquals("socks5://127.0.0.1:10808", config.proxy)
        assertEquals(1500, config.mtu)
        assertEquals(42, config.tunFd)
        assertEquals(Tun2SocksPolicy.DEFAULT_UDP_TIMEOUT_SEC, config.udpTimeoutSec)
        assertEquals(0L, config.tcpSendBufferBytes)
        assertEquals(Tun2SocksPolicy.MAX_BUFFER_KB * 1024L, config.tcpReceiveBufferBytes)
        assertTrue(config.moderateReceiveBuffer)
        assertEquals("warn", config.logLevel)
        assertEquals("/data/logs/tun2socks.log", config.logPath)
        assertTrue(config.summary().contains("mtu=1500"))
    }

    @Test
    fun aDescriptorOrAProxyTheTunnelCannotUseIsRefusedBeforeItStarts() {
        assertNull(
            "fd -1 is the revoked descriptor the service closes",
            Tun2SocksPolicy.buildConfig(10808, 1500, -1, 60, 0, 0, false, "warn", "")
        )
        assertNull(
            "port 0 is not a SOCKS inbound",
            Tun2SocksPolicy.buildConfig(0, 1500, 7, 60, 0, 0, false, "warn", "")
        )
        assertNull(
            "port 70000 is not a port",
            Tun2SocksPolicy.buildConfig(70000, 1500, 7, 60, 0, 0, false, "warn", "")
        )
    }

    @Test
    fun theMtuCapCanOnlyLowerTheSessionMtu() {
        assertEquals(1500, Tun2SocksPolicy.resolveMtu(1500, 0))
        assertEquals(1280, Tun2SocksPolicy.resolveMtu(1500, 1280))
        assertEquals(
            "a cap above the session MTU must not raise it",
            1400,
            Tun2SocksPolicy.resolveMtu(1400, 9000)
        )
        assertEquals("an out-of-range session MTU falls back to 1500", 1500, Tun2SocksPolicy.resolveMtu(0, 0))
        assertEquals(9000, Tun2SocksPolicy.resolveMtu(9000, 0))
        assertEquals(
            "a cap below the minimum is not a cap, it is a configuration mistake",
            9000,
            Tun2SocksPolicy.resolveMtu(9000, 1)
        )
    }

    @Test
    fun everyNumericOptionHasOneNormaliserTheSettingsPageAndStoreShare() {
        assertEquals(Tun2SocksPolicy.MIN_UDP_TIMEOUT_SEC, Tun2SocksPolicy.normalizeUdpTimeout(1))
        assertEquals(Tun2SocksPolicy.MAX_UDP_TIMEOUT_SEC, Tun2SocksPolicy.normalizeUdpTimeout(100_000))
        assertEquals(60, Tun2SocksPolicy.normalizeUdpTimeout(60))
        assertEquals(0, Tun2SocksPolicy.normalizeBufferKb(-1))
        assertEquals(Tun2SocksPolicy.MAX_BUFFER_KB, Tun2SocksPolicy.normalizeBufferKb(Int.MAX_VALUE))
        for (level in Tun2SocksPolicy.LOG_LEVELS) {
            assertEquals(level, Tun2SocksPolicy.normalizeLogLevel(" $level "))
        }
        assertEquals("warn", Tun2SocksPolicy.normalizeLogLevel(""))
        assertEquals("warn", Tun2SocksPolicy.normalizeLogLevel("verbose"))
    }

    @Test
    fun aStoredProfileIsNormalisedOnceAndKeepsItsOtherAxes() {
        val raw = AppSettings(
            coreEngineId = "singbox",
            tunnelCoreId = "TUN2SOCKS",
            tun2SocksMtu = 1,
            tun2SocksUdpTimeoutSec = 0,
            tun2SocksTcpSendBufferKb = -4,
            tun2SocksTcpReceiveBufferKb = Int.MAX_VALUE,
            tun2SocksLogLevel = "chatty",
            tun2SocksModerateReceiveBuffer = true
        )
        val clean = Tun2SocksPolicy.normalize(raw)
        assertEquals("tun2socks", clean.tunnelCoreId)
        assertEquals(
            "an out-of-range stored MTU becomes the session default, not the floor",
            1500,
            clean.tun2SocksMtu
        )
        assertEquals(Tun2SocksPolicy.DEFAULT_UDP_TIMEOUT_SEC, clean.tun2SocksUdpTimeoutSec)
        assertEquals(0, clean.tun2SocksTcpSendBufferKb)
        assertEquals(Tun2SocksPolicy.MAX_BUFFER_KB, clean.tun2SocksTcpReceiveBufferKb)
        assertEquals("warn", clean.tun2SocksLogLevel)
        assertTrue(clean.tun2SocksModerateReceiveBuffer)
        assertEquals("the proxy-core axis must survive", "singbox", clean.coreEngineId)
    }

    @Test
    fun theDefaultProductStillStartsWithTheHistoricalDatapath() {
        val defaults = AppSettings()
        assertEquals(TunnelCore.HEV, defaults.tunnelCore())
        assertEquals(0, defaults.tun2SocksMtu)
        assertEquals(60, defaults.tun2SocksUdpTimeoutSec)
        assertEquals("warn", defaults.tun2SocksLogLevel)
        assertFalse(defaults.tun2SocksModerateReceiveBuffer)
    }
}
