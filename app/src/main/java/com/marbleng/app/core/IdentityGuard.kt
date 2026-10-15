package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.RoutingDefaults
import com.marbleng.app.model.RoutingMode

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

            continuousOptimizerEnabled = false,

            routingMode = when {
                iranDirect -> RoutingMode.GEO_DIRECT
                privateOnly -> RoutingMode.BYPASS_PRIVATE
                else -> RoutingMode.PROXY_ALL
            },
            routeGeoIpTags =
                if (iranDirect) RoutingDefaults.GEOIP_DIRECT_TAGS else "",
            routeGeoSiteTags =
                if (iranDirect) RoutingDefaults.GEOSITE_DIRECT_TAGS else "",

            routeDirectDomains = "",
            routeDirectIps = "",
            routeBypassPrivate = iranDirect || privateOnly,
            iranDomesticDirect = iranDirect,

            dnsHijackEnabled = true
        )
    }
}
