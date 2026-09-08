package com.marbleng.app.core

import java.net.URL

/** A real request through the selected outbound, never a substituted TCP-connect number. */
data class CoreUrlTestResult(val delayMs: Long, val ok: Boolean, val detail: String = "", val live: Boolean = false)

/**
 * MARBLE_URLTEST_SINGBOX_ONLY_V156 — the URL contract of the one URL test the product has.
 *
 * The URL test is sing-box extended's own measurement (`GET /proxies/{tag}/delay`), so this is
 * the only place a target is checked. It used to be checked twice, once here for a Kotlin HTTP
 * client that ran the same button on the Xray engine and once in the session; the Xray look-alike
 * is gone, and the guard survived as one testable rule.
 *
 *  - **HTTPS only.** The pinned Clash API silently substitutes its own gstatic URL for an
 *    `http://` target, which would publish a number for a route MarbleNG never asked about.
 *  - **No user info.** A `user:pass@` in a URL is a credential, and a delay test sends its target
 *    to a third-party reference site. Refusing is the only safe answer; stripping would hide
 *    from the user that their configured URL carried one.
 */
internal object UrlTestTarget {
    const val INVALID = "urltest-url: an HTTPS URL without user info is required"

    /** `null` when [url] may be measured, otherwise the honest failure reason. */
    fun validate(url: String): String? {
        val target = runCatching { URL(url) }.getOrNull() ?: return INVALID
        val acceptable = target.protocol == "https" &&
            target.host.orEmpty().isNotBlank() &&
            target.userInfo == null
        return if (acceptable) null else INVALID
    }
}
