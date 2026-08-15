package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.SplitTunnelMode

/**
 * Identity Guard keeps one public identity stable for the lifetime of a user-started session.
 *
 * It does not spoof browser/device identity and does not attempt to defeat website anti-bot logic.
 * Its purpose is:
 *  - no mid-session rotation to a "faster" exit;
 *  - no silent failover to another public exit while strict pinning is enabled;
 *  - same-family public exit rotation is detected before forwarding and during the session;
 *  - no public/custom/domestic direct routes while the guard is enabled;
 *  - classic DNS capture stays enabled;
 *  - existing IPv4/IPv6 Full-TUN fail-closed behavior stays intact.
 *
 * A hostname-based proxy first hop can still require encrypted direct bootstrap DoH before the
 * proxy exists. Using an IP-literal proxy endpoint removes even that bootstrap dependency.
 */
object IdentityGuard {
    fun apply(settings: AppSettings): AppSettings {
        if (!settings.identityGuardEnabled) return settings

        return settings.copy(
            continuousOptimizerEnabled = false,
            routingMode = RoutingMode.PROXY_ALL,
            routeDirectDomains = "",
            routeDirectIps = "",
            routeBypassPrivate = false,
            iranDomesticDirect = false,
            splitTunnelMode = SplitTunnelMode.ALL_APPS,
            splitTunnelPackages = "",
            dnsHijackEnabled = true
        )
    }
}
