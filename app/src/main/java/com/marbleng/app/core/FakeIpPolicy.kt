package com.marbleng.app.core

import com.marbleng.app.model.AppSettings

/**
 * Shared address-space contract for the fake-DNS implementations in both native cores.
 *
 * Marble's HEV TUN addresses are `198.18.0.1/32` and `fc00::1/128`. The IANA benchmarking
 * block's other /16 (`198.19.0.0/16`) and the separate ULA /64 (`fc00:198:19::/64`) cannot
 * collide with those interface addresses. Both cores must answer A AND AAAA with mapped fake
 * addresses so domains cannot escape Fake DNS via an unhandled IPv6 query.
 */
internal object FakeIpPolicy {
    const val IPV4_POOL = "198.19.0.0/16"
    const val IPV6_POOL = "fc00:198:19::/64"

    /**
     * Xray rejects an LRU whose base-2 logarithm is greater than or equal to the subnet's host-bit
     * count. A /16 therefore accepts 65,535 entries but rejects 65,536.
     */
    const val XRAY_LRU_SIZE = 65_535

    /** Fake answers only reach apps when classic DNS interception is active. */
    fun isDnsPathArmed(settings: AppSettings): Boolean =
        settings.dnsFakeIpEnabled && settings.dnsHijackEnabled
}
