package com.marbleng.app.core

// MARBLE_TLS_PINNING_V149

import org.json.JSONObject

/**
 * MARBLE_TLS_PINNING_V149 — the single, authoritative mapping of Marble's two peer-verification
 * options onto Xray's native `streamSettings.tlsSettings` fields.
 *
 * ## The bug this object exists to make impossible
 *
 * Marble exposes two product options:
 *
 *  - **"Verify peer certificate by name"** → `tlsSettings.verifyPeerCertByName`
 *    (a *string*, comma separated, share-link key `vcn`);
 *  - **"Certificate fingerprint (SHA-256)"** → `tlsSettings.pinnedPeerCertSha256`
 *    (a *string*, comma separated, share-link key `pcs`).
 *
 * Neither field was ever written into the Xray config Marble generates. `ProxyParser` only read
 * the long-form `pinnedPeerCertSha256` query key — never the canonical `pcs` short key that real
 * share links use, and never `vcn` at all — and `ManualConfigBuilder` had no fields for either.
 * A link such as
 *
 * ```
 * vless://…@vps1.example:8443?security=tls&sni=spotify.com&vcn=vps1.example&pcs=<sha256>,<sha256>
 * ```
 *
 * therefore produced a `tlsSettings` block with **only** `serverName=spotify.com`. Xray fell back
 * to standard chain validation against the system CA store, and because a pinning server is
 * self-signed (or serves a certificate for a name that is not the fronted SNI), *every single*
 * TLS handshake failed. The app reported "connected" — the core process was alive and the SOCKS
 * inbound was listening — while no stream ever completed: the exact "connected but no Internet"
 * signature.
 *
 * ## Format truth (pinned against Xray-core v26.7.28 `infra/conf/transport_security.go`)
 *
 *  - `pinnedPeerCertSha256` is **hex**, not base64. Xray runs
 *    `hex.DecodeString(strings.ReplaceAll(v, ":", ""))` on each comma-separated element and
 *    rejects anything that does not decode to exactly 32 bytes. OpenSSL colon format is accepted;
 *    base64 is not. [normalizeFingerprints] therefore accepts hex, colon-hex **and** base64 from
 *    the user and always emits lowercase hex, so a paste from any tool works.
 *  - `verifyPeerCertByName` is a comma-separated list of DNS names. When it is present Xray sets
 *    `InsecureSkipVerify` and verifies the chain against those names in its own callback, which
 *    is what lets a fronted `serverName` coexist with the real certificate name.
 *  - `allowInsecure` is a **removed feature** in this core: `TLSConfig.Build()` returns
 *    `errors.PrintRemovedFeatureError` when it is `true`, so a single legacy `allowInsecure: true`
 *    makes Xray reject the whole configuration at load time. Marble was still emitting it from
 *    both the parser and the manual builder. [sanitizeTlsSettings] translates it into the
 *    supported replacement instead of shipping a config the core will refuse.
 */
object TlsPinningPolicy {

    /** Xray requires exactly 32 bytes (SHA-256) per pin. */
    private const val SHA256_BYTES = 32

    /** Query keys that carry `verifyPeerCertByName`, canonical first. */
    val VERIFY_BY_NAME_KEYS = listOf(
        "vcn",
        "verifyPeerCertByName",
        "verify_peer_cert_by_name",
        "verifypeercertbyname",
        "peerCertName",
        "certName"
    )

    /** Query keys that carry `pinnedPeerCertSha256`, canonical first. */
    val PINNED_SHA256_KEYS = listOf(
        "pcs",
        "pinnedPeerCertSha256",
        "pinned_peer_cert_sha256",
        "pinnedpeercertsha256",
        "pinSHA256",
        "pinsha256",
        "certFingerprint",
        "fingerprintSha256"
    )

    /** Keys that request the removed `allowInsecure` behaviour. */
    val ALLOW_INSECURE_KEYS = listOf(
        "allowInsecure",
        "insecure",
        "allow_insecure",
        "skip-cert-verify",
        "skipCertVerify"
    )

    private val TRUTHY = setOf("1", "true", "yes", "on")

    fun isTruthy(value: String?): Boolean =
        value != null && value.trim().lowercase() in TRUTHY

    /**
     * Normalizes the user's "verify peer certificate by name" input into the exact string shape
     * Xray parses: comma-separated, trimmed, de-duplicated, no empty elements.
     *
     * Accepts commas, semicolons, whitespace and newlines as separators, because that is what
     * people actually paste. Returns "" when nothing usable remains — callers must then omit the
     * field entirely rather than writing an empty string (an empty `verifyPeerCertByName` is
     * indistinguishable from "unset" to Xray, but omitting keeps the emitted JSON honest).
     */
    fun normalizeVerifyNames(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.split(',', ';', '\n', '\r', ' ', '\t')
            .asSequence()
            .map { it.trim().trim('.') }
            .filter { it.isNotBlank() }
            .map { it.removePrefix("*.").ifBlank { it } }
            .map { it.lowercase() }
            .distinct()
            .joinToString(",")
    }

    /**
     * Normalizes certificate fingerprints into the comma-separated lowercase hex list Xray
     * accepts, dropping anything that is not a real SHA-256 digest.
     *
     * Input tolerated per element:
     *  - `ab:cd:ef…` OpenSSL colon hex (colons and spaces removed);
     *  - `abcdef…` bare hex, any case;
     *  - `sha256/<base64>` or bare standard/URL-safe base64 (32 bytes), converted to hex;
     *  - a `sha256:` / `SHA-256=` prefix on any of the above.
     *
     * An element that does not decode to exactly 32 bytes is *dropped*, never passed through:
     * Xray fails the whole configuration on a malformed pin, so one bad element in a share link
     * must not be allowed to take the rest of the profile down with it.
     */
    fun normalizeFingerprints(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.split(',', ';', '\n', '\r', ' ', '\t')
            .asSequence()
            .mapNotNull { normalizeOneFingerprint(it) }
            .distinct()
            .joinToString(",")
    }

    /** One fingerprint element as lowercase hex, or null when it is not a 32-byte digest. */
    fun normalizeOneFingerprint(raw: String): String? {
        var value = raw.trim()
        if (value.isEmpty()) return null
        for (prefix in listOf("sha256/", "sha-256/", "sha256:", "sha-256:", "sha256=", "sha-256=")) {
            if (value.startsWith(prefix, ignoreCase = true)) {
                value = value.substring(prefix.length).trim()
            }
        }
        if (value.isEmpty()) return null

        val hexCandidate = value.replace(":", "").replace("-", "").replace(" ", "")
        if (hexCandidate.length == SHA256_BYTES * 2 && hexCandidate.all { it.isHexDigit() }) {
            return hexCandidate.lowercase()
        }

        // Base64 (standard or URL-safe, padded or not) is what several other clients export.
        decodeBase64(value)?.let { bytes ->
            if (bytes.size == SHA256_BYTES) {
                return bytes.joinToString("") { "%02x".format(it) }
            }
        }
        return null
    }

    /**
     * True when [raw] contains at least one element that looks like a fingerprint but is not a
     * valid 32-byte SHA-256 digest. Used by the manual editor to explain a rejected paste instead
     * of silently discarding it.
     */
    fun hasInvalidFingerprint(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return raw.split(',', ';', '\n', '\r', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { normalizeOneFingerprint(it) == null }
    }

    /**
     * Writes both pinning fields into an existing `tlsSettings` object and removes the legacy
     * `allowInsecure` flag that this Xray core refuses to load.
     *
     * Precedence, deliberately explicit:
     *  1. real pins always win — when a fingerprint or a verify-name is present, `allowInsecure`
     *     is dropped silently, because pinning is strictly stronger and the two cannot coexist;
     *  2. when the user asked for `allowInsecure` and supplied no pins, the flag is translated
     *     into `verifyPeerCertByName` carrying the certificate name we can infer (the explicit
     *     [insecureFallbackName], normally the endpoint host). That is the documented replacement
     *     and it keeps a self-signed-but-correctly-named server working instead of handing Xray a
     *     config it will reject outright;
     *  3. if neither a pin nor a fallback name exists, `allowInsecure` is simply removed and the
     *     profile keeps standard verification — a strictly better outcome than a core that will
     *     not start.
     */
    fun sanitizeTlsSettings(
        tls: JSONObject,
        verifyPeerCertByName: String? = null,
        pinnedPeerCertSha256: String? = null,
        allowInsecureRequested: Boolean = false,
        insecureFallbackName: String = ""
    ): JSONObject {
        val existingNames = tls.optString("verifyPeerCertByName", "")
        val existingPins = tls.optString("pinnedPeerCertSha256", "")
        val legacyInsecure = allowInsecureRequested || tls.optBoolean("allowInsecure", false)
        tls.remove("allowInsecure")

        val names = normalizeVerifyNames(
            listOf(verifyPeerCertByName.orEmpty(), existingNames)
                .filter { it.isNotBlank() }
                .joinToString(",")
        )
        val pins = normalizeFingerprints(
            listOf(pinnedPeerCertSha256.orEmpty(), existingPins)
                .filter { it.isNotBlank() }
                .joinToString(",")
        )

        if (pins.isNotBlank()) {
            tls.put("pinnedPeerCertSha256", pins)
        } else {
            tls.remove("pinnedPeerCertSha256")
        }

        val effectiveNames = when {
            names.isNotBlank() -> names
            legacyInsecure && pins.isBlank() -> normalizeVerifyNames(insecureFallbackName)
            else -> ""
        }
        if (effectiveNames.isNotBlank()) {
            tls.put("verifyPeerCertByName", effectiveNames)
        } else {
            tls.remove("verifyPeerCertByName")
        }
        return tls
    }

    /**
     * Applies [sanitizeTlsSettings] to every `tlsSettings` block reachable from a whole Xray
     * config document, including nested `sockopt.dialerProxy` chains and pasted JSON profiles.
     *
     * This is the belt to the parser's braces: a profile imported as raw Xray JSON never went
     * through the share-link parser, so without this pass a pasted config could still carry the
     * removed `allowInsecure` flag or a base64/OpenSSL-formatted pin that the core rejects.
     * Returns the number of TLS blocks that were changed.
     */
    fun sanitizeConfigDocument(root: JSONObject): Int {
        var changed = 0
        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val tls = value.optJSONObject("tlsSettings")
                    if (tls != null) {
                        val before = tls.toString()
                        val fallback = tls.optString("serverName", "")
                        sanitizeTlsSettings(tls, insecureFallbackName = fallback)
                        if (tls.toString() != before) changed += 1
                    }
                    value.keys().asSequence().toList().forEach { key -> walk(value.opt(key)) }
                }

                is org.json.JSONArray -> {
                    for (index in 0 until value.length()) walk(value.opt(index))
                }
            }
        }
        walk(root)
        return changed
    }

    /**
     * True when this TLS block pins the peer in any way. Probes use it to stop convicting a
     * healthy pinned endpoint with the *device's* CA store, which by definition cannot validate
     * a certificate the user pinned precisely because no public CA signed it.
     */
    fun isPinned(tls: JSONObject?): Boolean {
        if (tls == null) return false
        return tls.optString("pinnedPeerCertSha256", "").isNotBlank() ||
            tls.optString("verifyPeerCertByName", "").isNotBlank()
    }

    /** True when the profile config pins its peer certificate anywhere in its outbound chain. */
    fun configIsPinned(configJson: String): Boolean = runCatching {
        var pinned = false
        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    if (isPinned(value.optJSONObject("tlsSettings"))) pinned = true
                    value.keys().asSequence().toList().forEach { key -> walk(value.opt(key)) }
                }

                is org.json.JSONArray -> {
                    for (index in 0 until value.length()) walk(value.opt(index))
                }
            }
        }
        walk(JSONObject(configJson))
        pinned
    }.getOrDefault(false)

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun decodeBase64(value: String): ByteArray? {
        val cleaned = value.trim().replace('-', '+').replace('_', '/').filterNot { it == '\n' || it == '\r' }
        if (cleaned.isEmpty()) return null
        val padded = when (cleaned.length % 4) {
            2 -> "$cleaned=="
            3 -> "$cleaned="
            0 -> cleaned
            else -> return null
        }
        return runCatching { java.util.Base64.getDecoder().decode(padded) }.getOrNull()
    }
}
