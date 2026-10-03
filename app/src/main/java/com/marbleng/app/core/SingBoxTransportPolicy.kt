package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import org.json.JSONObject

// =============================================================================
// MARBLE_FRAGMENT_PROFILES_V208 — what one fragment / Mux recipe means on the
// sing-box extended wire.
//
// The critique, and it is a serious one:
//
//  1. **The sing-box engine silently did nothing with either knob.** The translator's entire
//     fragment implementation was `if (settings.fragmentEnabled) tls.put("fragment", true)` —
//     one boolean. `packets`, `length`, `interval`, `maxSplit` and the whole inner recipe were
//     dropped on the floor, so a user who picked "Extreme micro-fragment" and a user who picked
//     "ClientHello split" produced byte-identical configs on that engine. Mux was worse: the
//     builder only appended a *note* saying "Xray Mux.Cool is not sing-box smux" and wrote
//     nothing at all, while the pinned extended core has supported `multiplex` all along.
//     "Fragment and Mux do not work" was, on this engine, literally true.
//
//  2. **The one boolean it did write was the expensive one.** The pinned core documents
//     `tls.fragment` as *packet*-level handshake fragmentation with "poor performance", tells
//     you to "try `record_fragment` first", and — because Android gives the process no
//     `CAP_NET_RAW` — every handshake waits `fragment_fallback_delay` (500 ms by default)
//     instead of measuring the real wait. Turning that on for every profile is how a mild
//     recipe becomes a half-second handshake tax.
//
// What replaces it is a mapping that is honest about the difference between the two cores
// instead of pretending one recipe means the same bytes on both:
//
//   * Xray owns `packets` / `length` / `interval` / `maxSplit` verbatim (see
//     [XrayConfigHardener]); those numbers are the recipe.
//   * sing-box owns three booleans and one duration. The recipe's *strength* — the ladder the
//     profiles are already ordered by — decides which of the three the core is asked for, and
//     the recipe's interval decides the fallback delay. A mild recipe gets record-level
//     fragmentation only; an aggressive one additionally asks for packet fragmentation.
//
// Every function here is pure and JSON-only, so the mapping is pinned by
// `SingBoxFragmentWireV208Test` without a device, a core or a network.
// =============================================================================

/** The sing-box extended TLS-fragment / smux mapping of one fragment + Mux recipe. */
object SingBoxTransportPolicy {
    // The four field names the pinned core declares in `option/tls.go` and `option/multiplex.go`.
    // Named here rather than inlined at the write sites, because a misspelt key is not a warning:
    // the core fails to decode the outbound and the user gets a dead session with no reason.

    /** Split the handshake into several TLS records. The cheap half, and the one to try first. */
    const val RecordFragmentKey: String = "record_fragment"

    /** Packet-level handshake fragmentation. Documented by the core as "poor performance". */
    const val FragmentKey: String = "fragment"

    /** How long the packet path waits when it cannot measure the real wait — `"250ms"`. */
    const val FallbackDelayKey: String = "fragment_fallback_delay"

    /** The outbound field the pinned core reads for smux (`multiplex`, not `mux`). */
    const val MultiplexField: String = "multiplex"

    /**
     * The strength at which packet-level fragmentation is worth its cost.
     *
     * Below it, record fragmentation carries the recipe: it splits the handshake into several
     * TLS records, which is what a plaintext-matching filter actually chokes on, and it costs
     * nothing per handshake. At and above it the recipe is explicitly asking for the wire to
     * look wrong at the packet layer, which only `tls.fragment` does.
     */
    const val PacketFragmentStrength: Int = 3

    /** The core's own default wait, used when a recipe names no usable interval. */
    const val DefaultFallbackDelayMs: Int = 500

    /** The floor the core itself applies below: a wait under 20 ms is treated as local. */
    const val MinFallbackDelayMs: Int = 20

    /** A half-second handshake is the most this product will ever ask a user to pay. */
    const val MaxFallbackDelayMs: Int = 500

    /** True when the recipe asks the core to split the handshake into several TLS records. */
    fun recordFragment(settings: AppSettings): Boolean =
        settings.fragmentEnabled && strengthOf(settings) >= 1

    /**
     * True when the recipe additionally asks for packet-level fragmentation.
     *
     * Only the aggressive rungs: on Android this path cannot measure the real wait, so every
     * handshake pays [fallbackDelayMs] instead.
     */
    fun packetFragment(settings: AppSettings): Boolean =
        settings.fragmentEnabled && strengthOf(settings) >= PacketFragmentStrength

    /**
     * The wait the core falls back to, in milliseconds, derived from the recipe's own interval.
     *
     * The interval is the only number in the recipe that means "how long to hold the split", so
     * it is the honest source; the upper end of the range is used because that is the wait the
     * recipe is prepared to accept. Clamped into the band the core itself describes.
     */
    fun fallbackDelayMs(settings: AppSettings): Int {
        val upper = upperBoundOf(settings.fragmentInterval) ?: return DefaultFallbackDelayMs
        return upper.coerceIn(MinFallbackDelayMs, MaxFallbackDelayMs)
    }

    /** The strength of the recipe these settings name, or of the one their fields describe. */
    fun strengthOf(settings: AppSettings): Int =
        TransportAdaptation.selectedFragment(settings).strength

    /**
     * Write the recipe into one translated TLS options object.
     *
     * Off writes nothing at all — not `false` — so a config the user never touched stays free of
     * fields that would only invite a decoder to notice them.
     */
    fun applyFragment(tls: JSONObject, settings: AppSettings): JSONObject {
        if (!recordFragment(settings)) {
            tls.remove(FragmentKey)
            tls.remove(RecordFragmentKey)
            tls.remove(FallbackDelayKey)
            return tls
        }
        tls.put(RecordFragmentKey, true)
        if (packetFragment(settings)) {
            tls.put(FragmentKey, true)
            tls.put(FallbackDelayKey, "${fallbackDelayMs(settings)}ms")
        } else {
            tls.remove(FragmentKey)
            tls.remove(FallbackDelayKey)
        }
        return tls
    }

    /**
     * The smux object for one Mux recipe, or null when the recipe is off.
     *
     * Xray's Mux.Cool and sing-box's smux are different wire protocols, so the numbers cannot be
     * copied across — but they *can* be mapped, which is what the old "not compatible, disabled"
     * note refused to do. `concurrency` (how many logical streams Xray puts on one connection)
     * becomes `max_streams` (how many smux streams one connection carries); `xudpConcurrency`
     * has no smux equivalent and is honestly dropped; `padding` is on because a padded smux
     * frame is the closer of the two to what Mux.Cool puts on the wire.
     */
    fun multiplex(settings: AppSettings): JSONObject? {
        if (!settings.muxEnabled) return null
        val streams = settings.muxConcurrency.coerceIn(1, 128)
        return JSONObject()
            .put("enabled", true)
            .put("protocol", smuxProtocol(settings))
            .put("max_connections", maxConnectionsFor(streams))
            .put("max_streams", streams)
            .put("padding", true)
    }

    /**
     * Which smux dialect to ask for.
     *
     * `h2mux` is HTTP/2-framed and therefore the least distinctive of the three, which is the
     * whole point of choosing a multiplexer on a filtered link; the heavier concurrency recipes
     * want `yamux`, the dialect with the cheapest per-stream overhead.
     */
    fun smuxProtocol(settings: AppSettings): String =
        if (settings.muxConcurrency >= 12) "yamux" else "h2mux"

    /** One connection per four streams, never fewer than one, never more than the stream count. */
    fun maxConnectionsFor(streams: Int): Int =
        ((streams + 3) / 4).coerceAtLeast(1).coerceAtMost(streams.coerceAtLeast(1))

    /** The upper number of an `"a-b"` / `"a"` range, or null when the value is not numeric. */
    fun upperBoundOf(raw: String): Int? {
        val parts = raw.trim().split('-', limit = 2)
        val upper = parts.lastOrNull()?.trim()?.toIntOrNull() ?: parts.firstOrNull()?.trim()?.toIntOrNull()
        return upper?.takeIf { it > 0 }
    }
}
