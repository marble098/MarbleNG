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
    private fun hasGeoTag(raw: String, name: String, prefix: String): Boolean =
        raw.split(',', '\n', '\r', ';')
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .any { token ->
                token.equals(name, ignoreCase = true) ||
                    token.equals("$prefix:$name", ignoreCase = true)
            }

    fun apply(settings: AppSettings): AppSettings {
        if (!settings.identityGuardEnabled) return settings

        val iranDirect =
            settings.routingMode in setOf(RoutingMode.GEO_DIRECT, RoutingMode.CUSTOM) &&
                hasGeoTag(settings.routeGeoIpTags, "ir", "geoip") &&
                hasGeoTag(settings.routeGeoSiteTags, "ir", "geosite")

        val privateOnly =
            settings.routingMode == RoutingMode.BYPASS_PRIVATE

        return settings.copy(
            // Stable identity applies to the selected proxy exit. Do not let optimizer/failover
            // silently replace that exit while this guard is active.
            continuousOptimizerEnabled = false,

            // Keep only the bounded intentional direct policy:
            // Iran + private networks, or private-only. Everything else remains proxy-all.
            routingMode = when {
                iranDirect -> RoutingMode.GEO_DIRECT
                privateOnly -> RoutingMode.BYPASS_PRIVATE
                else -> RoutingMode.PROXY_ALL
            },
            routeGeoIpTags =
                if (iranDirect) RoutingDefaults.GEOIP_DIRECT_TAGS else "",
            routeGeoSiteTags =
                if (iranDirect) RoutingDefaults.GEOSITE_DIRECT_TAGS else "",

            // Arbitrary hand-entered public direct routes create additional public identities,
            // so Identity Guard strips them while retaining explicit proxy/block rules.
            routeDirectDomains = "",
            routeDirectIps = "",
            routeBypassPrivate = iranDirect || privateOnly,
            iranDomesticDirect = iranDirect,

            // Keep classic DNS interception enabled on tunneled traffic.
            dnsHijackEnabled = true
        )
    }
}
