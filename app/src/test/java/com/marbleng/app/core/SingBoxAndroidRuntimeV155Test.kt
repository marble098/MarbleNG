package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SINGBOX_ANDROID_RUNTIME_V155 — the sing-box extended engine, pinned against the two
 * faults that made it unusable on Android.
 *
 * The shipped log ended every attempt the same way:
 *
 * ```
 * SINGBOX | start-result | ok=false | reason=sing-box rejected the config:
 *   WARN `independent_cache` DNS option is deprecated …
 *   FATAL initialize network manager: create network monitor: netlink socket in Android is
 *   banned by Google
 * ```
 *
 * Both lines are decidable from the JSON alone, which is what this suite asserts:
 *
 *  1. **The netlink FATAL is a config bug, not a platform limit.** `route.NewNetworkManager` only
 *     fails on the banned socket when `enforceInterfaceMonitor` is set, and the only option that
 *     sets it is `route.auto_detect_interface`. A config that never asks for an interface monitor
 *     runs on a stock, unrooted device.
 *  2. **A deprecation scheduled for the running version is `os.Exit(1)`, not a warning.** On the
 *     pinned 1.14 core that includes the *absence* of `route.default_domain_resolver`, so the
 *     option has to be written, and every `ENABLE_DEPRECATED_*` flag has to be exported as a
 *     backstop for the ones nobody has hit yet.
 *
 * Nothing here spawns a process: the config and the environment *are* the contract.
 */
class SingBoxAndroidRuntimeV155Test {

    private val vlessLink =
        "vless://11111111-2222-3333-4444-555555555555@198.51.100.7:443" +
            "?encryption=none&security=tls&sni=edge.example.com&type=tcp#Node%20A"

    private fun profile() = ProxyProfile(
        id = "node-1",
        name = "Node 1",
        scheme = "vless",
        raw = vlessLink,
        configJson = "",
        host = "edge.example.com",
        port = 443
    )

    private fun build(settings: AppSettings = AppSettings()): JSONObject = JSONObject(
        SingBoxConfigBuilder.build(
            profile = profile(),
            settings = settings,
            socksPort = 10808,
            apiPort = 39090,
            apiSecret = "secret",
            logPath = "/data/user/0/com.marbleng.app/files/logs/singbox.log",
            cachePath = "/data/user/0/com.marbleng.app/files/singbox-cache.db",
            resolverPool = listOf("https://dns.quad9.net/dns-query")
        ).json
    )

    private fun dnsServerTags(config: JSONObject): List<String> {
        val servers = config.getJSONObject("dns").getJSONArray("servers")
        return (0 until servers.length()).map { servers.getJSONObject(it).getString("tag") }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1 — the netlink FATAL
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun theConfigNeverAsksAndroidForANetlinkInterfaceMonitor() {
        val route = build().getJSONObject("route")
        SingBoxAndroidRuntime.ANDROID_FORBIDDEN_ROUTE_KEYS.forEach { key ->
            assertFalse(
                "`$key` forces sing-box to build a netlink interface monitor, which Android bans " +
                    "for app UIDs — it is the whole reason the engine could not start",
                route.has(key)
            )
        }
    }

    @Test
    fun theOnlyInboundIsTheLocalMixedEndpointTheTunnelDials() {
        val inbounds = build().getJSONArray("inbounds")
        assertEquals(1, inbounds.length())
        val inbound = inbounds.getJSONObject(0)
        assertEquals("mixed", inbound.getString("type"))
        assertEquals("127.0.0.1", inbound.getString("listen"))
        assertFalse(
            "a tun inbound would make the core demand auto_route and the banned monitor with it",
            inbound.getString("type") in SingBoxAndroidRuntime.ANDROID_FORBIDDEN_INBOUND_TYPES
        )
    }

    @Test
    fun hardeningStripsEveryInterfaceMonitorRequestFromAForeignConfig() {
        val hostile = JSONObject(build().toString()).apply {
            getJSONObject("route")
                .put("auto_detect_interface", true)
                .put("default_interface", "wlan0")
                .put("default_mark", 255)
                .put("find_process", true)
                .put("override_android_vpn", true)
            getJSONArray("outbounds").getJSONObject(0)
                .put("bind_interface", "wlan0")
                .put("routing_mark", 1234)
            getJSONArray("inbounds").put(
                JSONObject().put("type", "tun").put("tag", "tun-in").put("auto_route", true)
            )
            getJSONObject("dns").getJSONArray("servers")
                .put(JSONObject().put("type", "dhcp").put("tag", "dns-dhcp"))
        }

        val healed = SingBoxConfigDoctor.hardenForAndroid(hostile.toString())
        assertTrue("a config that requests netlink must be repaired", healed.repaired)
        val config = JSONObject(healed.json)

        val route = config.getJSONObject("route")
        SingBoxAndroidRuntime.ANDROID_FORBIDDEN_ROUTE_KEYS.forEach { key ->
            assertFalse("`$key` must not survive hardening", route.has(key))
        }
        val proxy = config.getJSONArray("outbounds").getJSONObject(0)
        SingBoxAndroidRuntime.ANDROID_FORBIDDEN_DIAL_KEYS.forEach { key ->
            assertFalse("dial field `$key` must not survive hardening", proxy.has(key))
        }
        val inbounds = config.getJSONArray("inbounds")
        for (i in 0 until inbounds.length()) {
            assertFalse(
                "a tun inbound must not survive hardening",
                inbounds.getJSONObject(i).getString("type") == "tun"
            )
        }
        assertFalse(
            "a dhcp DNS transport reads its lease through netlink",
            dnsServerTags(config).contains("dns-dhcp")
        )
    }

    @Test
    fun theNetlinkBanIsRecognisedAndExplained() {
        val shipped =
            "FATAL initialize network manager: create network monitor: netlink socket in " +
                "Android is banned by Google, use the root or system (ADB) user to run sing-box"
        assertTrue(SingBoxAndroidRuntime.isNetlinkBan(shipped))
        assertTrue(
            "the VPN service must treat it as an engine fault, not walk 17 nodes that all fail",
            SingBoxConfigDoctor.isEngineLevelFault(shipped)
        )
        assertFalse(SingBoxAndroidRuntime.isNetlinkBan("connection reset by peer"))
        assertTrue(SingBoxAndroidRuntime.NETLINK_REMEDIATION.isNotBlank())
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2 — deprecations that exit the process
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun theDefaultDomainResolverIsNamedAndResolvable() {
        val config = build()
        val route = config.getJSONObject("route")
        val resolver = route.getString("default_domain_resolver")
        assertEquals(
            "a dial-time lookup must use the system resolver: it can never depend on the tunnel " +
                "it is helping to build",
            SingBoxConfigBuilder.DNS_LOCAL_TAG,
            resolver
        )
        assertTrue(
            "`default domain resolver not found` is a startup error; the tag must exist",
            resolver in dnsServerTags(config)
        )
    }

    @Test
    fun hardeningAddsAMissingDefaultDomainResolver() {
        val legacy = JSONObject(build().toString()).apply {
            getJSONObject("route").remove("default_domain_resolver")
        }
        val healed = SingBoxConfigDoctor.hardenForAndroid(legacy.toString())
        assertTrue(healed.repaired)
        val config = JSONObject(healed.json)
        val resolver = config.getJSONObject("route").getString("default_domain_resolver")
        assertTrue(resolver in dnsServerTags(config))
    }

    @Test
    fun hardeningRepointsADanglingDefaultDomainResolver() {
        val broken = JSONObject(build().toString()).apply {
            getJSONObject("route").put("default_domain_resolver", "a-tag-that-does-not-exist")
        }
        val healed = SingBoxConfigDoctor.hardenForAndroid(broken.toString())
        assertTrue(healed.repaired)
        val config = JSONObject(healed.json)
        assertTrue(
            config.getJSONObject("route").getString("default_domain_resolver")
                in dnsServerTags(config)
        )
    }

    @Test
    fun deprecatedOneFourOptionsAreNeverWritten() {
        val config = build()
        assertFalse(config.getJSONObject("dns").has("independent_cache"))
        val cacheFile = config.getJSONObject("experimental").getJSONObject("cache_file")
        assertFalse(
            "`store_rdrc` is deprecated in 1.14; `store_dns` replaces it",
            cacheFile.has("store_rdrc")
        )
        assertTrue(cacheFile.getBoolean("store_dns"))
    }

    @Test
    fun hardeningMigratesTheDeprecatedCacheAndRuleSetOptions() {
        val legacy = JSONObject(build().toString()).apply {
            getJSONObject("dns").put("independent_cache", true)
            getJSONObject("experimental").getJSONObject("cache_file")
                .remove("store_dns")
            getJSONObject("experimental").getJSONObject("cache_file")
                .put("store_rdrc", true)
            val sets = getJSONObject("route").getJSONArray("rule_set")
            for (i in 0 until sets.length()) {
                sets.getJSONObject(i).remove("path")
                sets.getJSONObject(i).put("type", "remote").put("url", "https://example.com/test.srs")
                sets.getJSONObject(i).put("download_detour", "direct")
            }
        }
        // All three are preflight migrations now: the doctor must not need the core to die first.
        val healed = SingBoxConfigDoctor.hardenForAndroid(legacy.toString())
        assertTrue(healed.repaired)
        val config = JSONObject(healed.json)
        assertFalse(config.getJSONObject("dns").has("independent_cache"))
        val cacheFile = config.getJSONObject("experimental").getJSONObject("cache_file")
        assertFalse(cacheFile.has("store_rdrc"))
        assertTrue(cacheFile.getBoolean("store_dns"))
        val sets = config.getJSONObject("route").getJSONArray("rule_set")
        for (i in 0 until sets.length()) {
            val set = sets.getJSONObject(i)
            assertFalse(set.has("download_detour"))
            assertEquals(
                SingBoxConfigBuilder.DIRECT_TAG,
                set.getJSONObject("http_client").getString("detour")
            )
        }
    }

    @Test
    fun deprecatedEscapeHatchesAreNotExportedOrInherited() {
        assertTrue(SingBoxAndroidRuntime.DEPRECATION_ENV.isEmpty())
        val process = ProcessBuilder("sing-box")
        process.environment()["ENABLE_DEPRECATED_MISSING_DOMAIN_RESOLVER"] = "true"
        SingBoxAndroidRuntime.prepare(process, null, null)
        assertFalse(process.environment().keys.any { it.startsWith("ENABLE_DEPRECATED_") })
    }

    @Test
    fun theDeprecationExitIsClassifiedAsAnEngineFault() {
        val fatal =
            "FATAL to continuing using this feature, set environment variable " +
                "ENABLE_DEPRECATED_MISSING_DOMAIN_RESOLVER=true"
        assertTrue(SingBoxConfigDoctor.isEngineLevelFault(fatal))
        // The error line the core prints just before that fatal one, verbatim from
        // experimental/deprecated: `<description> is deprecated in sing-box <x> and will be
        // removed in sing-box <y>, checkout documentation for migration: <link>`.
        assertTrue(
            SingBoxConfigDoctor.isEngineLevelFault(
                "missing `route.default_domain_resolver` or `domain_resolver` in dial fields " +
                    "is deprecated in sing-box 1.12.0 and will be removed in sing-box 1.14.0"
            )
        )
        // …and the bare description on its own, because the retained log is line-truncated.
        assertTrue(
            SingBoxConfigDoctor.isEngineLevelFault(
                "missing `route.default_domain_resolver` or `domain_resolver` in dial fields"
            )
        )
        // The route-start error for a tag that does not resolve to a DNS server.
        assertTrue(
            SingBoxConfigDoctor.isEngineLevelFault("default domain resolver not found: dns-local")
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3 — the rule-set HTTP client
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun ruleSetDownloadsUseANamedHttpClientThatGoesDirect() {
        val config = build()
        val clients = config.getJSONArray("http_clients")
        val tags = (0 until clients.length()).map { clients.getJSONObject(it).getString("tag") }
        assertTrue(SingBoxConfigBuilder.HTTP_CLIENT_DIRECT_TAG in tags)
        assertEquals(
            SingBoxConfigBuilder.DIRECT_TAG,
            clients.getJSONObject(tags.indexOf(SingBoxConfigBuilder.HTTP_CLIENT_DIRECT_TAG))
                .getString("detour")
        )
        assertEquals(
            SingBoxConfigBuilder.HTTP_CLIENT_DIRECT_TAG,
            config.getJSONObject("route").getString("default_http_client")
        )
        val sets = config.getJSONObject("route").getJSONArray("rule_set")
        for (i in 0 until sets.length()) {
            val set = sets.getJSONObject(i)
            assertFalse(
                "http_client and download_detour together are rejected by the core",
                set.has("download_detour")
            )
            assertEquals("local", set.getString("type"))
            assertFalse(set.has("url"))
            assertFalse(set.has("http_client"))
        }
    }

    @Test
    fun aPinnedCoreIsNeverDowngradedToDeprecatedHttpClientOptions() {
        val original = build().toString()
        val rejection = "decode config: json: unknown field http_client"
        val healed = SingBoxConfigDoctor.repair(original, rejection)
        assertFalse(healed.repaired)
        assertEquals(original, healed.json)
        assertFalse(healed.json.contains("download_detour"))
    }

    @Test
    fun repairLeavesAFreshConfigAloneWhenTheComplaintIsUnrelated() {
        val original = build().toString()
        val healed = SingBoxConfigDoctor.repair(original, "dial tcp: i/o timeout")
        assertFalse(
            "the builder must already write a config the pinned core accepts: ${healed.notes}",
            healed.repaired
        )
        assertEquals(original, healed.json)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 4 — the preflight must be a no-op on a config MarbleNG wrote
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun hardeningIsIdempotentAndSilentOnAConfigTheBuilderWrote() {
        val original = build().toString()
        val once = SingBoxConfigDoctor.hardenForAndroid(original)
        assertFalse(
            "the builder must already write a config the Android runtime accepts; a note here " +
                "means the writer and the doctor disagree: ${once.notes}",
            once.repaired
        )
        assertEquals(original, once.json)
        val twice = SingBoxConfigDoctor.hardenForAndroid(once.json)
        assertFalse(twice.repaired)
    }

    @Test
    fun hardeningSurvivesRubbishWithoutThrowing() {
        val broken = SingBoxConfigDoctor.hardenForAndroid("{ not json at all")
        assertFalse(broken.repaired)
        assertEquals("{ not json at all", broken.json)
        assertTrue(SingBoxConfigDoctor.hardenForAndroid("{}").json.isNotBlank())
        assertTrue(SingBoxConfigDoctor.hardenForAndroid(JSONArray().toString()).json.isNotBlank())
    }
}
