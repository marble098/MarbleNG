package com.marbleng.app.core

import com.marbleng.app.model.AppSettings

/**
 * Shared address-space contract for the fake-DNS implementations in both native cores.
 *
 * Marble's HEV TUN interface is `198.18.0.1/32`. The IANA special-purpose benchmarking block is
 * `198.18.0.0/15`, so using its other /16 (`198.19.0.0/16`) keeps fake answers non-routable on the
 * public Internet without overlapping the local interface address. Keeping this in one place also
 * prevents Xray and sing-box from handing out addresses from different pools.
 */
internal object FakeIpPolicy {
    const val IPV4_POOL = "198.19.0.0/16"

    /**
     * Xray rejects an LRU whose base-2 logarithm is greater than or equal to the subnet's host-bit
     * count. A /16 therefore accepts 65,535 entries but rejects 65,536.
     */
    const val XRAY_LRU_SIZE = 65_535

    /** Fake answers only reach apps when classic DNS interception is active. */
    fun isDnsPathArmed(settings: AppSettings): Boolean =
        settings.dnsFakeIpEnabled && settings.dnsHijackEnabled
}
