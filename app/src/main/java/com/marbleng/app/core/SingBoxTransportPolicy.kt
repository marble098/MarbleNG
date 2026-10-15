package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.SingBoxMuxProtocols
import org.json.JSONObject

object SingBoxTransportPolicy {
    const val MultiplexField: String = "multiplex"

    const val EnabledKey: String = "enabled"
    const val ProtocolKey: String = "protocol"
    const val MaxConnectionsKey: String = "max_connections"
    const val MinStreamsKey: String = "min_streams"
    const val MaxStreamsKey: String = "max_streams"
    const val PaddingKey: String = "padding"

    val PROTOCOLS: List<String> = SingBoxMuxProtocols.ALL

    fun multiplex(settings: AppSettings): JSONObject? {
        if (!settings.singBoxMuxEnabled) return null
        return JSONObject()
            .put(EnabledKey, true)
            .put(ProtocolKey, SingBoxMuxProtocols.parse(settings.singBoxMuxProtocol))
            .put(MaxConnectionsKey, settings.singBoxMuxMaxConnections.coerceIn(1, 128))
            .put(MinStreamsKey, settings.singBoxMuxMinStreams.coerceIn(0, 128))
            .put(MaxStreamsKey, settings.singBoxMuxMaxStreams.coerceIn(1, 1024))
            .put(PaddingKey, settings.singBoxMuxPadding)
    }

    fun muxIsUnsafeFor(profile: com.marbleng.app.model.ProxyProfile?): Boolean {
        if (profile == null) return false
        if (profile.security.lowercase().contains("reality")) return true
        return profile.raw.lowercase().contains("flow=xtls-rprx-vision")
    }

    fun summary(settings: AppSettings): String {
        if (!settings.singBoxMuxEnabled) return "Off • every stream opens its own connection."
        val protocol = SingBoxMuxProtocols.parse(settings.singBoxMuxProtocol)
        return "On • $protocol, ${settings.singBoxMuxMaxConnections} connection(s), " +
            "up to ${settings.singBoxMuxMaxStreams} streams each."
    }
}
