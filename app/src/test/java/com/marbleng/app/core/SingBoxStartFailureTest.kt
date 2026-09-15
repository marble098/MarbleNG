package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SingBoxStartFailure] and fallback proxy outbound generation.
 */
class SingBoxStartFailureTest {

    @Test
    fun `no proxy outbound is classified as NO_PROXY_OUTBOUND`() {
        val err = "create service: outbound: no proxy outbound found in configuration"
        val fault = SingBoxStartFailure.classify(err)
        assertNotNull(fault)
        assertEquals(SingBoxStartFailure.Kind.NO_PROXY_OUTBOUND, fault!!.kind)
        assertTrue(fault.headline.contains("no proxy outbound"))
        assertEquals("Configuration rejected by sing-box", SingBoxStartFailure.faultClass(err, "default"))
    }

    @Test
    fun `port bind conflict is classified as PORT_IN_USE`() {
        val err = "listen tcp 127.0.0.1:10808: bind: address already in use"
        val fault = SingBoxStartFailure.classify(err)
        assertNotNull(fault)
        assertEquals(SingBoxStartFailure.Kind.PORT_IN_USE, fault!!.kind)
        assertEquals("Local port in use", SingBoxStartFailure.faultClass(err, "default"))
    }

    @Test
    fun `core crash is classified as CORE_CRASH`() {
        val err = "fatal error: runtime: out of memory"
        val fault = SingBoxStartFailure.classify(err)
        assertNotNull(fault)
        assertEquals(SingBoxStartFailure.Kind.CORE_CRASH, fault!!.kind)
        assertEquals("Core crashed on start", SingBoxStartFailure.faultClass(err, "default"))
    }

    @Test
    fun `the classifier reads the runtime timeline when the in-memory reason is empty`() {
        val runtime = "2026-09-15T10:05:27.376400Z | thread=pool-35-thread-1 | SINGBOX | start-result | " +
            "session=mu2iabil-c7cfad26 | ok=false | elapsedMs=223 | phase=failed | alive=false | " +
            "inbound=false | controller=true | reason=outbound: no proxy outbound"
        val fault = SingBoxStartFailure.classify("", runtime)
        assertNotNull(fault)
        assertEquals(SingBoxStartFailure.Kind.NO_PROXY_OUTBOUND, fault!!.kind)

        val recovered = runtime + "\n2026-09-15T10:06:00.000000Z | thread=pool-40-thread-1 | SINGBOX | start-result | " +
            "session=mu2iaz00-a4ac6e18 | ok=true | elapsedMs=410 | phase=ready | alive=true"
        assertNull(SingBoxStartFailure.classify("", recovered))
    }

    @Test
    fun `serverless profile with direct outbound can build valid singbox config`() {
        val rawConfig = JSONObject()
            .put("outbounds", JSONArray()
                .put(JSONObject().put("tag", "direct").put("protocol", "direct"))
                .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
            ).toString()

        val profile = ProxyProfile(
            id = "serverless-test-1",
            name = "Serverless Test",
            scheme = "direct",
            raw = rawConfig,
            configJson = rawConfig,
            host = "127.0.0.1",
            port = 0
        )

        val buildResult = SingBoxConfigBuilder.build(
            profile = profile,
            settings = AppSettings(),
            socksPort = 10808,
            apiPort = 39090,
            apiSecret = "test-secret",
            logPath = "",
            cachePath = "cache.db"
        )

        val json = JSONObject(buildResult.json)
        val outbounds = json.getJSONArray("outbounds")
        assertTrue("outbounds must not be empty", outbounds.length() > 0)
        val directOutbound = (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .firstOrNull { it.optString("type") == "direct" }
        assertNotNull("must contain direct outbound", directOutbound)
    }
}
