package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CensorshipAwareDnsResolverBootstrapTest {
    @Test
    fun preTunnelPoolContainsOnlyLiteralHttpsEndpoints() {
        val resolvers = CensorshipAwareDnsResolver.initializeForSession(
            "bootstrap-policy-test",
            AppSettings(
                dnsPrimaryDoH = "https://dns.adguard-dns.com/dns-query",
                dnsSecondaryDoH = "https://custom-resolver.example/dns-query"
            )
        )
        assertTrue(resolvers.isNotEmpty())
        assertTrue(resolvers.all { DnsResolverCatalog.isXrayBootstrapSafe(it.endpoint) })
        assertFalse(resolvers.any { it.endpoint.contains("adguard-dns.com") })
        assertFalse(resolvers.any { it.endpoint.contains("custom-resolver.example") })
    }
}
