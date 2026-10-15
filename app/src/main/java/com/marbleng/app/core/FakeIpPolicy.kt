package com.marbleng.app.core

import com.marbleng.app.model.AppSettings

internal object FakeIpPolicy {
    const val IPV4_POOL = "198.19.0.0/16"
    const val IPV6_POOL = "fc00:198:19::/64"

    const val XRAY_LRU_SIZE = 65_535

    fun isDnsPathArmed(settings: AppSettings): Boolean =
        settings.dnsFakeIpEnabled && settings.dnsHijackEnabled
}
