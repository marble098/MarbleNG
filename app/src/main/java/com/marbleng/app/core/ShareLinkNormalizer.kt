package com.marbleng.app.core

import java.net.URI

object ShareLinkNormalizer {
    private val htmlSeparator = Regex("&amp;(?=[A-Za-z][A-Za-z0-9_.-]*=)", RegexOption.IGNORE_CASE)
    private val markdownHost = Regex("^\\[([^\\[\\]()\\s]+)]\\((https?://[^\\s()]+)\\)$", RegexOption.IGNORE_CASE)
    private val sniKeys = setOf("sni", "servername", "peer")

    fun normalize(raw: String): String {
        val question = raw.indexOf('?')
        if (question < 0) return raw

        val hash = raw.indexOf('#', question + 1)
        val end = if (hash < 0) raw.length else hash
        val query = raw.substring(question + 1, end)
        val separators = htmlSeparator.replace(query, "&")
        val fixed = separators.split('&').joinToString("&") { token ->
            val equals = token.indexOf('=')
            if (equals < 0 || token.substring(0, equals).lowercase() !in sniKeys) return@joinToString token
            val value = ShareLinkParams.of(token).get(token.substring(0, equals))
            val match = markdownHost.matchEntire(value) ?: return@joinToString token
            val label = match.groupValues[1]
            val target = runCatching { URI(match.groupValues[2]) }.getOrNull() ?: return@joinToString token
            if (target.host == null || !label.equals(target.host, ignoreCase = true) ||
                target.rawUserInfo != null || target.rawQuery != null || target.rawFragment != null ||
                target.rawPath !in setOf("", "/")) {
                return@joinToString token
            }

            token.substring(0, equals + 1) + label
        }
        return raw.substring(0, question + 1) + fixed + raw.substring(end)
    }
}
