package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.FragmentStrategy
import com.marbleng.app.model.parseFragmentStrategy
import org.json.JSONObject

/**
 * MARBLE_FRAGMENT_POLICY_V221 — the one place that decides how a TLS handshake leaves the phone.
 *
 * The pinned extended core exposes two client-side treatments (verified against the fork's own
 * `docs/configuration/shared/tls.md` at the pinned tag):
 *
 *  * `record_fragment` — split the ClientHello across several TLS records. Cheap, no delay, and the
 *    fork's own guidance says to try it first.
 *  * `fragment` + `fragment_fallback_delay` — split the raw handshake across TCP segments and wait
 *    a bounded time before the server's answer is trusted. Heavier; for names already known to be
 *    blocked.
 *
 * The fork also ships a `spoof` that injects a forged ClientHello with a whitelisted name — and it
 * is deliberately never emitted here: it needs raw sockets (`CAP_NET_RAW`) and elevated privileges,
 * which an Android app has no way to obtain. A config the core refuses to start is not a bypass.
 *
 * QUIC-carrying outbounds (Hysteria 2, TUIC) are excluded: their TLS lives inside QUIC packets, so
 * a "TLS record" fragment has nothing to split and the generator would emit a key the transport
 * does not read.
 */
object FragmentPolicy {
    const val MARKER: String = "MARBLE_FRAGMENT_POLICY_V221"

    const val RECORD_KEY: String = "record_fragment"
    const val FRAGMENT_KEY: String = "fragment"
    const val FALLBACK_KEY: String = "fragment_fallback_delay"
    const val FALLBACK_DELAY: String = "500ms"

    /** What a strategy means on the wire. Pure data so a test never has to parse JSON to pin it. */
    data class Spec(
        val recordFragment: Boolean = false,
        val fragmentHandshake: Boolean = false,
        val fallbackDelay: String = ""
    ) {
        val empty: Boolean get() = !recordFragment && !fragmentHandshake
    }

    fun specFor(strategyId: String, quic: Boolean = false): Spec {
        if (quic) return Spec()
        return when (parseFragmentStrategy(strategyId)) {
            FragmentStrategy.OFF -> Spec()
            // AUTO is resolved before the document is built: the repository upgrades it to RECORD
            // only when its own censored-network evidence is positive, and to OFF otherwise. The
            // builder therefore writes nothing for AUTO, which is what keeps a default install
            // byte-identical to the document it produced before this chapter existed.
            FragmentStrategy.AUTO -> Spec()
            FragmentStrategy.RECORD -> Spec(recordFragment = true)
            FragmentStrategy.FULL -> Spec(
                recordFragment = true,
                fragmentHandshake = true,
                fallbackDelay = FALLBACK_DELAY
            )
        }
    }

    /**
     * The strategy that will actually reach a document.
     *
     * `Automatic` is not a wire value: it is a promise to look at the network. On a path the app has
     * measured as filtering it becomes the cheap record split; everywhere else it becomes `Off`, so
     * an install that is not being censored produces exactly the document it produced before this
     * chapter existed. Pure, so a test can pin both halves without a repository.
     */
    fun effectiveStrategyId(storedStrategyId: String, censored: Boolean): String {
        val strategy = parseFragmentStrategy(storedStrategyId)
        return if (strategy == FragmentStrategy.AUTO) {
            if (censored) FragmentStrategy.RECORD.id else FragmentStrategy.OFF.id
        } else {
            strategy.id
        }
    }

    /**
     * Write the treatment onto a translated TLS object. Returns true when something was written.
     * [notes] receives one human sentence, because the Settings page shows the generator's own
     * reasoning rather than a second copy of it.
     */
    fun applyTo(
        tls: JSONObject,
        settings: AppSettings,
        quic: Boolean = false,
        notes: MutableList<String>? = null
    ): Boolean {
        val spec = specFor(settings.fragmentStrategy, quic)
        if (spec.empty) return false
        if (spec.fragmentHandshake) {
            tls.put(FRAGMENT_KEY, true)
            if (spec.fallbackDelay.isNotBlank()) tls.put(FALLBACK_KEY, spec.fallbackDelay)
        }
        if (spec.recordFragment) tls.put(RECORD_KEY, true)
        notes?.add(
            if (spec.fragmentHandshake) {
                "TLS handshake fragmented across TCP segments (fallback ${spec.fallbackDelay})."
            } else {
                "ClientHello split across TLS records."
            }
        )
        return true
    }

    fun describe(strategyId: String): String = when (parseFragmentStrategy(strategyId)) {
        FragmentStrategy.OFF -> "The handshake leaves the phone in one piece."
        FragmentStrategy.AUTO -> "Split only when this network is measured as filtering; quiet everywhere else."
        FragmentStrategy.RECORD -> "The ClientHello is split across TLS records."
        FragmentStrategy.FULL -> "The whole handshake is split across TCP segments, with a fallback delay."
    }
}
