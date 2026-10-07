package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.SingBoxMuxProtocols
import org.json.JSONObject

// =============================================================================
// MARBLE_CORE_OPTIONS_V211 — what one multiplexing choice means on the
// sing-box extended wire.
//
// This file used to translate a *fragment recipe* into sing-box's three TLS-boolean fields, and
// derived the multiplex object from Xray's `muxConcurrency`. Both are gone, and the reason is the
// report that opened this chapter: the fragment section was a control that changed nothing a user
// could see, and the numbers under it were replaced by whichever automatic policy ran last.
//
// What is left is the honest mapping of the one transport feature both cores really share —
// multiplexing — and it is now a *direct* translation of the user's own fields:
//
//   `singBoxMuxEnabled`       → `multiplex.enabled`
//   `singBoxMuxProtocol`      → `multiplex.protocol`  (h2mux | smux | yamux)
//   `singBoxMuxMaxConnections`→ `multiplex.max_connections`
//   `singBoxMuxMinStreams`    → `multiplex.min_streams`
//   `singBoxMuxMaxStreams`    → `multiplex.max_streams`
//   `singBoxMuxPadding`       → `multiplex.padding`
//
// Every field name here is the one the pinned core declares in `option/multiplex.go`; a misspelt
// key is not a warning, it is a fatal decode error and a dead session with no reason. `mux` is
// therefore never written — that spelling does not exist in this core.
//
// Off writes **nothing** — no `multiplex` object at all, not `{"enabled": false}` — so a config
// the user never touched stays free of fields that would only invite a decoder to notice them.
// =============================================================================

/** The sing-box extended multiplex mapping of the user's Mux options. */
object SingBoxTransportPolicy {

    /** The outbound field the pinned core reads for smux (`multiplex`, never `mux`). */
    const val MultiplexField: String = "multiplex"

    /** The six keys of `option/multiplex.go`, named once so no write site can misspell one. */
    const val EnabledKey: String = "enabled"
    const val ProtocolKey: String = "protocol"
    const val MaxConnectionsKey: String = "max_connections"
    const val MinStreamsKey: String = "min_streams"
    const val MaxStreamsKey: String = "max_streams"
    const val PaddingKey: String = "padding"

    /** The three dialects the pinned core accepts, in the order the UI offers them. */
    val PROTOCOLS: List<String> = SingBoxMuxProtocols.ALL

    /**
     * The multiplex object for the user's settings, or null when multiplexing is off.
     *
     * Nothing is derived and nothing is inferred: this is the user's own choice, written once.
     */
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

    /**
     * True when multiplexing may be attached to this profile at all.
     *
     * One rule survives from the chapter that removed fragmentation, and it is a statement about
     * the wire rather than a policy: XTLS Vision negotiates its own flow control and REALITY
     * already carries a camouflage handshake, so multiplexing on top of either is a slower
     * connection *and* a louder fingerprint. The veto is returned to the caller instead of being
     * applied silently, so the settings screen can say which profile it applies to.
     */
    fun muxIsUnsafeFor(profile: com.marbleng.app.model.ProxyProfile?): Boolean {
        if (profile == null) return false
        if (profile.security.lowercase().contains("reality")) return true
        return profile.raw.lowercase().contains("flow=xtls-rprx-vision")
    }

    /** The one sentence a settings row prints about the current multiplex choice. */
    fun summary(settings: AppSettings): String {
        if (!settings.singBoxMuxEnabled) return "Off • every stream opens its own connection."
        val protocol = SingBoxMuxProtocols.parse(settings.singBoxMuxProtocol)
        return "On • $protocol, ${settings.singBoxMuxMaxConnections} connection(s), " +
            "up to ${settings.singBoxMuxMaxStreams} streams each."
    }
}
