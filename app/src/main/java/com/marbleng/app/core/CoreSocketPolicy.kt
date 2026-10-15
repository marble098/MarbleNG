package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.IranModePolicy
import org.json.JSONObject

object CoreSocketPolicy {
    const val MARKER = "MARBLE_SOCKET_FLIGHT_V168"

    private val UDP_PROTOCOLS = setOf("hysteria", "hysteria2", "tuic", "wireguard")

    private val UDP_METHODS = setOf("kcp", "mkcp", "quic", "hysteria", "hysteria2")

    fun opensUdpSocket(protocol: String, method: String = ""): Boolean {
        val p = protocol.trim().lowercase()
        val m = method.trim().lowercase()
        return p in UDP_PROTOCOLS || m in UDP_METHODS
    }

    fun iranActive(settings: AppSettings): Boolean =
        settings.iranModePolicy != IranModePolicy.OFF && settings.iranModeCountermeasures

    fun writeXrayPhysicalTcpSockopt(
        sockopt: JSONObject,
        settings: AppSettings,
        iranActive: Boolean,
        methodHint: String = "raw",
        chained: Boolean = false
    ) {
        val liveness = SocketLivenessPolicy.forTransport(methodHint, chained, iranActive)
        if (!sockopt.has("tcpKeepAliveIdle")) sockopt.put("tcpKeepAliveIdle", liveness.keepAliveIdleSeconds)
        if (!sockopt.has("tcpKeepAliveInterval")) sockopt.put("tcpKeepAliveInterval", liveness.keepAliveIntervalSeconds)
        if (!sockopt.has("tcpUserTimeout")) sockopt.put("tcpUserTimeout", liveness.userTimeoutMs)
        if (settings.tcpFastOpenEnabled && !sockopt.has("tcpFastOpen")) sockopt.put("tcpFastOpen", true)
        if (settings.tcpMaxSeg in 536..9000 && !sockopt.has("tcpMaxSeg")) sockopt.put("tcpMaxSeg", settings.tcpMaxSeg)

        if (!sockopt.has("tcpCongestion")) sockopt.put("tcpCongestion", "bbr")

        if (!sockopt.has("tcpMptcp")) sockopt.put("tcpMptcp", true)
    }

    fun xrayLocalInboundStream(settings: AppSettings): JSONObject? =
        if (settings.tcpFastOpenEnabled) {
            JSONObject().put("sockopt", JSONObject().put("tcpFastOpen", true))
        } else {
            null
        }

    fun applyDefaultUtlsFingerprint(outbound: JSONObject) {
        val stream = outbound.optJSONObject("streamSettings") ?: return
        val security = stream.optString("security").trim().lowercase()
        if (security != "tls" && security != "reality") return
        val method = stream.optString("method")
            .ifBlank { stream.optString("network") }
            .trim()
            .lowercase()
        if (method in UDP_METHODS) return
        val tlsKey = if (security == "reality") "realitySettings" else "tlsSettings"
        val tls = stream.optJSONObject(tlsKey) ?: return
        if (tls.optString("fingerprint").isBlank()) tls.put("fingerprint", "chrome")
    }

    fun writeSingBoxPhysicalDial(
        outbound: JSONObject,
        settings: AppSettings,
        protocolHint: String = "",
        methodHint: String = ""
    ) {
        outbound.put("tcp_fast_open", settings.tcpFastOpenEnabled)
        outbound.put("connect_timeout", "${settings.singBoxConnectTimeoutSec.coerceIn(3, 60)}s")
        outbound.put("tcp_multi_path", true)
        outbound.put("udp_fragment", true)
        if (!opensUdpSocket(protocolHint, methodHint)) {
            val liveness = SocketLivenessPolicy.forTransport(
                methodHint.ifBlank { protocolHint }.ifBlank { "raw" },
                false,
                iranActive(settings)
            )
            outbound.put("tcp_keep_alive", "${liveness.keepAliveIdleSeconds}s")
            outbound.put("tcp_keep_alive_interval", "${liveness.keepAliveIntervalSeconds}s")
        }
    }
}
