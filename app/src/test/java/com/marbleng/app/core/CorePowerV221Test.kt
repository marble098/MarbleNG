package com.marbleng.app.core

import com.marbleng.app.model.AddressFamilyMode
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.PingBudget
import com.marbleng.app.model.PowerProfile
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.RoutingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_CORE_POWER_V221 — the power profiles are a projection with hard bounds.
 *
 * These tests exist because a tuning knob that quietly leaves its range is a config the pinned core
 * refuses to start, and a phone that boots a VPN into "not running" is worse than a slow one.
 */
class CorePowerV221Test {
    private val phone = CorePower.Device(cores = 8, memoryMb = 8_192)
    private val small = CorePower.Device(cores = 2, memoryMb = 1_500)
    private val unknown = CorePower.Device(cores = 1, memoryMb = 0)

    private fun realityProfile() = ProxyProfile(
        id = "p1",
        name = "reality",
        scheme = "vless",
        raw = "vless://x@host:443?security=reality&flow=xtls-rprx-vision",
        configJson = "{}",
        host = "host",
        port = 443,
        transport = "tcp",
        security = "reality"
    )

    private fun tlsProfile() = ProxyProfile(
        id = "p2",
        name = "tls",
        scheme = "vless",
        raw = "vless://x@host:443?security=tls",
        configJson = "{}",
        host = "host",
        port = 443,
        transport = "ws",
        security = "tls"
    )

    @Test
    fun balancedIsTheIdentityFunction() {
        val base = AppSettings(
            powerProfile = PowerProfile.BALANCED.id,
            muxEnabled = false,
            pingConcurrency = 13,
            xrayPolicyBufferSizeKb = 7
        )
        assertEquals(base, CorePower.tune(base, PowerProfile.BALANCED.id, phone))
    }

    @Test
    fun efficientGivesBatteryBackWithoutChangingIdentity() {
        val base = AppSettings(pingConcurrency = 16, tcpWorkers = 32, muxEnabled = true)
        val tuned = CorePower.tune(base, PowerProfile.EFFICIENT.id, small)

        assertFalse(tuned.muxEnabled)
        assertFalse(tuned.singBoxMuxEnabled)
        assertFalse(tuned.tcpFastOpenEnabled)
        assertTrue(tuned.pingConcurrency <= base.pingConcurrency)
        assertTrue(tuned.tcpWorkers <= base.tcpWorkers)
        assertEquals(base.addressFamilyMode, tuned.addressFamilyMode)
        assertEquals(base.routingMode, tuned.routingMode)
        assertEquals(base.coreEngineId, tuned.coreEngineId)
        assertEquals(base.dnsQueryStrategy, tuned.dnsQueryStrategy)
    }

    @Test
    fun turboAndExtremeAccelerateWithinTheBuildersRanges() {
        val base = AppSettings(pingConcurrency = 8, tcpWorkers = 12)
        for (profile in listOf(PowerProfile.TURBO, PowerProfile.EXTREME)) {
            val tuned = CorePower.tune(base, profile.id, phone, muxSafe = true)

            assertTrue("$profile must enable multiplexing", tuned.muxEnabled)
            assertTrue("$profile must enable fast open", tuned.tcpFastOpenEnabled)
            assertTrue("$profile must raise the buffer", tuned.xrayPolicyBufferSizeKb > 0)
            assertTrue(tuned.xrayPolicyBufferSizeKb in CorePower.MIN_BUFFER_KB..CorePower.MAX_BUFFER_KB)
            assertTrue(tuned.muxConcurrency in CorePower.MUX_MIN..CorePower.MUX_MAX)
            assertTrue(tuned.muxXudpConcurrency in CorePower.XUDP_MIN..CorePower.XUDP_MAX)
            assertTrue(tuned.pingConcurrency in PingBudget.CONCURRENCY_MIN..PingBudget.CONCURRENCY_MAX)
            assertTrue(tuned.singBoxMuxMaxConnections in 1..128)
        }

        val turbo = CorePower.tune(base, PowerProfile.TURBO.id, phone)
        val extreme = CorePower.tune(base, PowerProfile.EXTREME.id, phone)
        assertTrue(extreme.xrayPolicyBufferSizeKb >= turbo.xrayPolicyBufferSizeKb)
        assertTrue(extreme.muxConcurrency >= turbo.muxConcurrency)
    }

    @Test
    fun aRouteThatCannotCarryMultiplexNeverGetsIt() {
        val base = AppSettings()
        val tuned = CorePower.tune(base, PowerProfile.EXTREME.id, phone, muxSafe = false)

        assertFalse(tuned.muxEnabled)
        assertFalse(tuned.singBoxMuxEnabled)
        assertTrue("speed knobs that do not need mux still apply", tuned.tcpFastOpenEnabled)
    }

    @Test
    fun thePolicyMatchesTheRouteItInspects() {
        assertFalse(CorePower.muxSafeFor(realityProfile()))
        assertTrue(CorePower.muxSafeFor(tlsProfile()))
        assertTrue("an unknown profile is not evidence of danger", CorePower.muxSafeFor(null))
    }

    @Test
    fun smallAndUnknownDevicesAreNeverOversubscribed() {
        for (device in listOf(small, unknown)) {
            for (profile in PowerProfile.entries) {
                val tuned = CorePower.tune(AppSettings(), profile.id, device)
                assertTrue(
                    "ping concurrency out of range for $profile on $device",
                    tuned.pingConcurrency in PingBudget.CONCURRENCY_MIN..PingBudget.CONCURRENCY_MAX
                )
                assertTrue(
                    "tcp workers must stay bounded for $profile on $device",
                    tuned.tcpWorkers in 4..96
                )
                assertTrue(
                    "buffer out of range for $profile on $device",
                    tuned.xrayPolicyBufferSizeKb in CorePower.MIN_BUFFER_KB..CorePower.MAX_BUFFER_KB
                )
            }
        }
    }

    @Test
    fun everyProfileHasASentenceAPersonCanRead() {
        for (profile in PowerProfile.entries) {
            assertTrue(CorePower.describe(profile.id).isNotBlank())
            assertTrue(CorePower.summary(profile.id, phone).contains(profile.label))
        }
    }

    @Test
    fun tuningNeverTouchesRoutingOrAddressPolicy() {
        val base = AppSettings(
            routingMode = RoutingMode.CUSTOM,
            addressFamilyMode = AddressFamilyMode.FORCE_IPV6,
            ipv6Enabled = false,
            strictAddressFamily = true,
            dnsPrimaryDoH = "https://example.invalid/dns-query"
        )
        for (profile in PowerProfile.entries) {
            val tuned = CorePower.tune(base, profile.id, phone)
            assertEquals(base.routingMode, tuned.routingMode)
            assertEquals(base.addressFamilyMode, tuned.addressFamilyMode)
            assertEquals(base.ipv6Enabled, tuned.ipv6Enabled)
            assertEquals(base.strictAddressFamily, tuned.strictAddressFamily)
            assertEquals(base.dnsPrimaryDoH, tuned.dnsPrimaryDoH)
        }
    }
}
