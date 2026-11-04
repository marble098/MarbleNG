package com.marbleng.app

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.w3c.dom.Element

/**
 * MARBLE_SECURITY_HARDENING_V265 — pins the two hardening changes from the 2026-11-04 audit:
 *
 *  - the merged manifest declares WAKE_LOCK (the low-latency Wi-Fi lock in the VPN service
 *    requires it; without the declaration acquire() can fail with a permission error);
 *  - network_security_config.xml still denies cleartext globally **but** explicitly permits it
 *    for loopback only, so the local Xray/sing-box controller APIs on 127.0.0.1 keep working on
 *    Android 16 and older (minSdk 26), where the base-config alone would block them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SecurityHardeningV265Test {

    @Test
    fun mergedManifestDeclaresWakeLock() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = info.requestedPermissions?.toList() ?: emptyList()
        assertTrue(
            "WAKE_LOCK must be declared for WifiLock.acquire() in MarbleVpnService",
            "android.permission.WAKE_LOCK" in requested
        )
    }

    @Test
    fun networkSecurityConfigAllowsCleartextOnlyForLoopback() {
        val file = listOf(
            File("src/main/res/xml/network_security_config.xml"),
            File("app/src/main/res/xml/network_security_config.xml")
        ).firstOrNull { it.isFile }
        assertNotNull("network_security_config.xml not found from the unit-test working dir", file)

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = doc.documentElement
        assertEquals("network-security-config", root.tagName)

        val base = root.getElementsByTagName("base-config").item(0) as Element
        assertEquals(
            "global cleartext must stay denied",
            "false",
            base.getAttribute("cleartextTrafficPermitted")
        )

        val domainConfigs = root.getElementsByTagName("domain-config")
        assertEquals("exactly one loopback exception is expected", 1, domainConfigs.length)
        val domainConfig = domainConfigs.item(0) as Element
        assertEquals("true", domainConfig.getAttribute("cleartextTrafficPermitted"))
        val domains = (0 until domainConfig.getElementsByTagName("domain").length).map { i ->
            (domainConfig.getElementsByTagName("domain").item(i) as Element).textContent.trim()
        }
        assertTrue("localhost must be exempt", "localhost" in domains)
        assertTrue("127.0.0.1 must be exempt", "127.0.0.1" in domains)
        assertTrue("no wildcard/public domain may be exempt", domains.all { it in setOf("localhost", "127.0.0.1", "::1") })
    }
}
