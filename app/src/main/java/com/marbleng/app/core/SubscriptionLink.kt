package com.marbleng.app.core

// MARBLE_SUBSCRIPTION_PASTE_V123
//
// Adding a subscription by pasting its link used to fail silently. The Add sheet demanded a
// *name* as well as a URL, so a user who pasted a link and tapped "Add subscription" pressed a
// disabled button; and when the sheet did submit, it closed itself whether the repository had
// accepted the source or refused it, so the only explanation was a transient message the user had
// usually already missed. Both ends of that flow are fixed here:
//
//   • [normalize] turns what a clipboard actually contains — a link wrapped in whitespace,
//     newlines, angle brackets or quotes, sometimes prefixed by "link:" — into a bare URL,
//   • [isSubscriptionUrl] lets any paste box recognise a subscription link and route it to the
//     subscription flow instead of the config-link parser,
//   • [isSecure] is the HTTPS check the repository enforces,
//   • [nameFor] derives a readable group name from the host so a name is never what blocks the add.
//
// The class is pure Kotlin with no Android imports, so it is covered by ordinary JVM unit tests.

/** Recognition and normalization of a pasted subscription link. */
object SubscriptionLink {

    private const val HTTPS_SCHEME = "https://"
    private const val HTTP_SCHEME = "http://"

    /**
     * Characters a clipboard frequently adds around a link: quotes, angle brackets, brackets and
     * the zero-width marks some messengers insert.
     */
    private val WRAPPER_CHARACTERS = setOf(
        '"', '\'', '`', '<', '>', '(', ')', '[', ']', '{', '}',
        '\u200c', '\u200d', '\u200e', '\u200f', '\ufeff'
    )

    /** Trailing sentence punctuation a copied link picks up. */
    private val TRAILING_PUNCTUATION = setOf(',', ';', '.', '!', '?', ':')

    /**
     * Normalize pasted text into the one URL it contains, or `null` when it holds no URL at all.
     *
     * The first URL-looking token wins, so a link pasted together with the sentence it arrived in
     * ("your sub: https://…") still works, and a doubled scheme ("link:https://…") is reduced to
     * its last, real occurrence.
     */
    fun normalize(text: String): String? {
        val token = text
            .split(Regex("\\s+"))
            .map(::trimWrappers)
            .firstOrNull {
                it.startsWith(HTTPS_SCHEME, true) || it.startsWith(HTTP_SCHEME, true)
            }
            ?: return null

        val lowered = token.lowercase()
        val schemeAt = maxOf(
            lowered.lastIndexOf(HTTPS_SCHEME),
            lowered.lastIndexOf(HTTP_SCHEME)
        )
        val candidate = if (schemeAt > 0) token.substring(schemeAt) else token

        val authority = candidate
            .substringAfter("://", "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        if (authority.isBlank() || !authority.contains('.') || authority.contains(' ')) return null
        return candidate
    }

    /** True when the pasted text is a subscription link rather than a list of config links. */
    fun isSubscriptionUrl(text: String): Boolean = normalize(text) != null

    /** MarbleNG only fetches remote sources over TLS. */
    fun isSecure(url: String): Boolean = normalize(url)?.startsWith(HTTPS_SCHEME, true) == true

    /**
     * A readable group name for a subscription link: the provider's host without `www.` and
     * without its TLD, with dashes and underscores turned back into spaces. Never blank.
     */
    fun nameFor(url: String): String {
        val host = normalize(url)
            ?.substringAfter("://", "")
            ?.substringBefore('/')
            ?.substringBefore('?')
            ?.substringBefore('#')
            .orEmpty()
        val label = host
            .removePrefix("www.")
            .substringBeforeLast('.')
            .replace('.', ' ')
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercaseChar) }
        return label.ifBlank { host.ifBlank { "Subscription" } }
    }

    private fun trimWrappers(value: String): String {
        var start = 0
        var end = value.length
        while (start < end && (value[start].isWhitespace() || value[start] in WRAPPER_CHARACTERS)) start++
        while (end > start && (value[end - 1].isWhitespace() || value[end - 1] in WRAPPER_CHARACTERS)) end--
        while (end > start && value[end - 1] in TRAILING_PUNCTUATION) end--
        return value.substring(start, end)
    }
}
