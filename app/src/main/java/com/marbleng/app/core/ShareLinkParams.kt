package com.marbleng.app.core

class ShareLinkParams private constructor(private val values: Map<String, String>) {
    fun raw(key: String): String? = values[key.lowercase()]

    fun has(key: String): Boolean = values.containsKey(key.lowercase())

    fun get(key: String, default: String = ""): String =
        values[key.lowercase()].orEmpty().ifBlank { default }

    fun first(vararg keys: String, default: String = ""): String {
        for (key in keys) {
            values[key.lowercase()]?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return default
    }

    fun isTruthy(vararg keys: String): Boolean =
        first(*keys).trim().lowercase() in setOf("1", "true", "yes", "on")

    fun keys(): Set<String> = values.keys

    companion object {
        val EMPTY = ShareLinkParams(emptyMap())

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

                if (!values.containsKey(key)) values[key] = percentDecode(rawValue)
            }
            return if (values.isEmpty()) EMPTY else ShareLinkParams(values)
        }

        fun ofRawLink(raw: String): ShareLinkParams {
            val link = ShareLinkNormalizer.normalize(raw)
            val hash = link.indexOf('#')
            val queryMark = link.indexOf('?')
            if (queryMark < 0) return EMPTY
            val from = queryMark + 1
            val end = if (hash > queryMark) hash else link.length
            if (from >= end) return EMPTY
            return of(link.substring(from, end))
        }

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
