package com.marbleng.app.core

import java.net.URI

/**
 * Repairs *presentation* artifacts of a pasted share link, before either core sees the link.
 * This is not a protocol converter: credentials, REALITY keys, XHTTP extra, and the fragment are
 * kept verbatim. HTML only escapes query separators; Markdown may wrap an SNI in a clickable URL.
 * Never infer a different SNI from the URL: unwrap only when its host is exactly the label.
 */
object ShareLinkNormalizer {
    private val htmlSeparator = Regex("&amp;(?=[A-Za-z][A-Za-z0-9_.-]*=)", RegexOption.IGNORE_CASE)
    private val markdownHost = Regex("^\\[([^\\[\\]()\\s]+)]\\((https?://[^\\s()]+)\\)$", RegexOption.IGNORE_CASE)
    private val sniKeys = setOf("sni", "servername", "peer")

    fun normalize(raw: String): String {
        val question = raw.indexOf('?')
        if (question < 0) return raw
        // A `#Name?security=reality` link has its parameters *in* the fragment. Preserve the
        // name, but still repair those parameters. A normal `?a=b#Name` leaves the name alone.
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
            // The label is already a DNS name proven equal to the URL's parsed host. It cannot
            // smuggle a different authority or modify a REALITY key.
            token.substring(0, equals + 1) + label
        }
        return raw.substring(0, question + 1) + fixed + raw.substring(end)
    }
}
