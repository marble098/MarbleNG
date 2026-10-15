package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.XrayDomainStrategies
import org.json.JSONArray
import org.json.JSONObject

data class CoreOptionMerge(
    val applied: List<String> = emptyList(),
    val error: String = ""
) {
    val changed: Boolean get() = applied.isNotEmpty()
}

object CoreOptions {
    const val MARKER = "MARBLE_CORE_OPTIONS_V211"

    const val FAKEDNS = "fakedns"

    private val SNIFF_PROTOCOLS = listOf("http", "tls", "quic")

    fun xrayDestOverride(settings: AppSettings, fakeIpArmed: Boolean): JSONArray {
        val chosen = settings.xraySniffDestOverride
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it in SNIFF_PROTOCOLS || it == FAKEDNS }
        val ordered = chosen.ifEmpty { SNIFF_PROTOCOLS }
        val withFake = if (fakeIpArmed && FAKEDNS !in ordered) ordered + FAKEDNS else ordered
        return JSONArray(withFake)
    }

    fun xraySniffing(settings: AppSettings, fakeIpArmed: Boolean): JSONObject {
        val sniffing = JSONObject()
            .put("enabled", settings.xraySniffingEnabled)
            .put("routeOnly", settings.xraySniffingRouteOnly)
            .put("destOverride", xrayDestOverride(settings, fakeIpArmed))
        if (settings.xraySniffMetadataOnly) sniffing.put("metadataOnly", true)
        return sniffing
    }

    fun xraySocksSettings(settings: AppSettings): JSONObject = JSONObject()
        .put("udp", settings.xraySocksUdpEnabled)

    fun xrayUserSockopt(settings: AppSettings): JSONObject {
        val sockopt = JSONObject()
        val strategy = XrayDomainStrategies.parse(settings.xraySockoptDomainStrategy)
        if (strategy != "AsIs") sockopt.put("domainStrategy", strategy)
        if (settings.xrayTcpNoDelay) sockopt.put("tcpNoDelay", true)
        if (settings.xrayTcpKeepAliveIntervalSec > 0) {
            sockopt.put("tcpKeepAliveInterval", settings.xrayTcpKeepAliveIntervalSec)
        }
        if (settings.xrayTcpUserTimeoutMs > 0) sockopt.put("tcpUserTimeout", settings.xrayTcpUserTimeoutMs)
        if (settings.xrayTcpCongestion.isNotBlank()) sockopt.put("tcpCongestion", settings.xrayTcpCongestion.trim())
        if (!settings.xrayTcpMptcp) sockopt.put("tcpMptcp", false)
        if (settings.xrayTcpWindowClamp > 0) sockopt.put("tcpWindowClamp", settings.xrayTcpWindowClamp)
        return sockopt
    }

    fun applyXrayUserSockopt(sockopt: JSONObject, settings: AppSettings) {
        val user = xrayUserSockopt(settings)
        val keys = user.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            sockopt.put(key, user.get(key))
        }
    }

    fun xrayPolicy(settings: AppSettings): JSONObject? {
        val level = JSONObject()
        if (settings.xrayPolicyHandshakeSec > 0) level.put("handshake", settings.xrayPolicyHandshakeSec)
        if (settings.xrayPolicyConnIdleSec > 0) level.put("connIdle", settings.xrayPolicyConnIdleSec)
        if (settings.xrayPolicyUplinkOnlySec > 0) level.put("uplinkOnly", settings.xrayPolicyUplinkOnlySec)
        if (settings.xrayPolicyDownlinkOnlySec > 0) level.put("downlinkOnly", settings.xrayPolicyDownlinkOnlySec)
        if (settings.xrayPolicyBufferSizeKb > 0) level.put("bufferSize", settings.xrayPolicyBufferSizeKb)
        if (level.length() == 0) return null
        return JSONObject().put("levels", JSONObject().put("0", level))
    }

    fun xrayLog(settings: AppSettings): JSONObject {
        val log = JSONObject().put("loglevel", com.marbleng.app.model.XrayLogLevels.parse(settings.xrayLogLevel))
        if (settings.xrayDnsLog) log.put("dnsLog", true)
        return log
    }

    fun merge(target: JSONObject, extraJson: String, protectedKeys: Set<String> = PROTECTED_KEYS): CoreOptionMerge {
        val text = extraJson.trim()
        if (text.isEmpty()) return CoreOptionMerge()
        val parsed = runCatching { JSONObject(text) }.getOrElse {
            return CoreOptionMerge(error = "Not a JSON object: ${it.message ?: "parse error"}")
        }
        val refused = protectedKeys.filter { parsed.has(it) }
        if (refused.isNotEmpty()) {
            return CoreOptionMerge(
                error = "These keys are managed by Marble and cannot be overridden: " +
                    refused.sorted().joinToString(", ")
            )
        }
        val applied = mutableListOf<String>()
        mergeObjects(target, parsed, "", applied)
        return CoreOptionMerge(applied = applied)
    }

    val PROTECTED_KEYS: Set<String> = setOf(
        "inbounds", "outbounds", "routing", "dns", "log"
    )

    val SINGBOX_PROTECTED_KEYS: Set<String> = setOf(
        "inbounds", "outbounds", "route", "dns", "log"
    )

    private fun mergeObjects(
        target: JSONObject,
        patch: JSONObject,
        prefix: String,
        applied: MutableList<String>
    ) {
        val keys = patch.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            val incoming = patch.opt(key)
            val existing = target.opt(key)
            if (incoming is JSONObject && existing is JSONObject) {
                mergeObjects(existing, incoming, path, applied)
            } else {
                target.put(key, incoming)
                applied += path
            }
        }
    }
}
