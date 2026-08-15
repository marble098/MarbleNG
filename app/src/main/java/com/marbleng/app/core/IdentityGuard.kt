package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.RoutingDefaults
import com.marbleng.app.model.RoutingMode

/**
 * Identity Guard pins the public identity of traffic that is meant to use the proxy.
 *
 * It does not spoof browser/device identity and cannot guarantee that a website will never apply
 * anti-abuse checks. Its network invariants are:
 *  - no mid-session proxy-exit rotation to a "faster" node;
 *  - no silent proxy failover while strict pinning is enabled;
 *  - same-family proxy exit rotation is detected before forwarding and during the session;
 *  - intentional Iran/private direct routing is allowed and is not classified as a leak;
 *  - arbitrary custom public direct destinations are stripped while Identity Guard is active;
 *  - explicit split tunneling is preserved; the privacy sentinel reports partial coverage when used;
 *  - classic DNS capture stays enabled for tunneled traffic.
 *
 * Therefore Iranian destinations can intentionally observe the ISP egress IP while international
 * destinations stay on one stable proxy exit for the user-started session.
 */
object IdentityGuard {
    fun apply(settings: AppSettings): AppSettings {
        if (!settings.identityGuardEnabled) return settings

        fun hasTag(raw: String, name: String, prefix: String): Boolean =
            raw.split(',', '
', '
', ';')
                .map(String::trim)
                .any {
                    it.equals(name, ignoreCase = true) ||
                        it.equals("$prefix:$name", ignoreCase = true)
                }

        val iranDirect =
            settings.routingMode in setOf(RoutingMode.GEO_DIRECT, RoutingMode.CUSTOM) &&
                hasTag(settings.routeGeoIpTags, "ir", "geoip") &&
                hasTag(settings.routeGeoSiteTags, "ir", "geosite")
        val privateOnly = settings.routingMode == RoutingMode.BYPASS_PRIVATE

        return settings.copy(
            continuousOptimizerEnabled = false,
            routingMode = when {
                iranDirect -> RoutingMode.GEO_DIRECT
                privateOnly -> RoutingMode.BYPASS_PRIVATE
                else -> RoutingMode.PROXY_ALL
            },
            routeGeoIpTags = if (iranDirect) RoutingDefaults.GEOIP_DIRECT_TAGS else "",
            routeGeoSiteTags = if (iranDirect) RoutingDefaults.GEOSITE_DIRECT_TAGS else "",
            // Identity Guard permits only the bounded Iran/private geo-direct policy. Arbitrary
            // hand-entered direct public destinations would create extra public identities.
            routeDirectDomains = "",
            routeDirectIps = "",
            routeBypassPrivate = iranDirect || privateOnly,
            iranDomesticDirect = iranDirect,
            dnsHijackEnabled = true
        )
    }
}
