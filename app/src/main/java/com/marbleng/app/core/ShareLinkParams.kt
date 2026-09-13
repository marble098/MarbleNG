package com.marbleng.app.core

// MARBLE_CORE_CONFIG_SUPERSET_V165
//
// ─────────────────────────────────────────────────────────────────────────────────────────────
// Share-link query parameters, read the way the ecosystem actually writes them
// ─────────────────────────────────────────────────────────────────────────────────────────────
//
// The importer used to look a parameter up with `Uri.getQueryParameter(key)`, i.e. an exact,
// case-sensitive match against one spelling. That is a promise about the links a user receives:
//
//   · `vless://…?type=tcp&security=reality&SNI=www.microsoft.com&PublicKey=abc…` — the capitalised
//     spellings several panels and Telegram bots emit — came back **"no security"**, so a REALITY
//     node was imported as plaintext, labelled "Unsupported VLESS", and refused;
//   · `vless://…#Germany 1?type=tcp&security=none` — a name placed *before* the parameters, which
//     is legal in a URI but is not a query string any more — lost every parameter, same outcome;
//   · `vmess://…?net=ws`, `…&network=grpc&serviceName=@grpc` — the `net`/`network` spellings of
//     `type`, and the `service` alias of `serviceName`, are all over the wild and were invisible.
//
// Those are not "unsupported configs"; they are the same configs, written by somebody else. This
// object owns the reading: lowercase-keyed lookup with declared aliases, `%XX` decoding that keeps
// UTF-8 intact, and a raw-link fallback that finds the parameters even when they sit after the
// fragment. `Uri` is deliberately not involved, so the rule is unit-testable on a JVM instead of
// only on a device.
class ShareLinkParams private constructor(private val values: Map<String, String>) {

    /** `null` when absent; blank is preserved so a caller can distinguish "" from missing. */
    fun raw(key: String): String? = values[key.lowercase()]

    fun has(key: String): Boolean = values.containsKey(key.lowercase())

    /** The value for [key], or [default] when the parameter is absent or empty. */
    fun get(key: String, default: String = ""): String =
        values[key.lowercase()].orEmpty().ifBlank { default }

    /** First non-blank value among [keys]; used for the aliases the ecosystem uses interchangeably. */
    fun first(vararg keys: String, default: String = ""): String {
        for (key in keys) {
            values[key.lowercase()]?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return default
    }

    /** Truthiness as the panels write it: `true`, `1`, `yes`, `on` (case-insensitive). */
    fun isTruthy(vararg keys: String): Boolean =
        first(*keys).trim().lowercase() in setOf("1", "true", "yes", "on")

    /** Every key the link carries, lowercased — for diagnostics and for tests. */
    fun keys(): Set<String> = values.keys

    companion object {
        val EMPTY = ShareLinkParams(emptyMap())

        /** Parse an already-extracted `a=1&b=2` query. */
        fun of(query: String?): ShareLinkParams {
            if (query.isNullOrBlank()) return EMPTY
            val values = LinkedHashMap<String, String>()
            query.split('&').forEach { token ->
                if (token.isEmpty()) return@forEach
                val equals = token.indexOf('=')
                val rawKey = if (equals < 0) token else token.substring(0, equals)
                val rawValue = if (equals < 0) "" else token.substring(equals + 1)
                if (rawKey.isEmpty()) return@forEach
                val key = percentDecode(rawKey).trim().lowercase()
                if (key.isEmpty()) return@forEach
                // The first spelling wins, matching a URI reader that never overwrites a parameter
                // it has already bound (`security=none&security=reality` is a broken link, and the
                // safest reading of a broken link is the one the author wrote first).
                if (!values.containsKey(key)) values[key] = percentDecode(rawValue)
            }
            return if (values.isEmpty()) EMPTY else ShareLinkParams(values)
        }

        /**
         * The parameters of a whole share link, including the shape where the node name comes
         * before them (`vless://id@host:443#Name?type=xhttp&security=tls`).
         *
         * `Uri.encodedQuery` is authoritative whenever it has something to say; this fallback only
         * runs when it is empty, so no link can be read two different ways.
         */
        fun ofRawLink(raw: String): ShareLinkParams {
            val hash = raw.indexOf('#')
            val queryMark = raw.indexOf('?')
            if (queryMark < 0) return EMPTY
            val from = queryMark + 1
            val end = if (hash > queryMark) hash else raw.length
            if (from >= end) return EMPTY
            return of(raw.substring(from, end))
        }

        /**
         * Percent-decoding, `+` left alone.
         *
         * Android's `Uri.decode` does not turn `+` into a space either, and share links carry
         * base64 payloads (`pbk`, `echConfigList`, `mldsa65Verify`) where a `+` is data. A decoder
         * that "helpfully" rewrote it would silently corrupt a key.
         */
        private fun percentDecode(value: String): String {
            if (!value.contains('%')) return value
            val out = StringBuilder(value.length)
            var bytes: ArrayList<Byte>? = null
            var index = 0
            while (index < value.length) {
                val c = value[index]
                if (c == '%' && index + 2 < value.length) {
                    val high = Character.digit(value[index + 1], 16)
                    val low = Character.digit(value[index + 2], 16)
                    if (high >= 0 && low >= 0) {
                        val sink = bytes ?: ArrayList<Byte>(8).also { bytes = it }
                        sink.add(((high shl 4) or low).toByte())
                        index += 3
                        continue
                    }
                }
                bytes?.let { pending ->
                    out.append(String(pending.toByteArray(), Charsets.UTF_8))
                    bytes = null
                }
                out.append(c)
                index += 1
            }
            bytes?.let { pending -> out.append(String(pending.toByteArray(), Charsets.UTF_8)) }
            return out.toString()
        }
    }
}
