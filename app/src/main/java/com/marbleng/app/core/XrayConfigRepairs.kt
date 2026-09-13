package com.marbleng.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

// MARBLE_CORE_CONFIG_SUPERSET_V165
//
// ─────────────────────────────────────────────────────────────────────────────────────────────
// Lossless repairs for config shapes the pinned core refuses to *load*
// ─────────────────────────────────────────────────────────────────────────────────────────────
//
// "This profile is unsupported" and "this profile is written in a dialect the core no longer
// parses" are different sentences, and MarbleNG has been printing the first one for the second
// problem. Four shapes in that class are worth repairing instead of refusing, because each repair
// is a mechanical rewrite with exactly one possible meaning:
//
//  1. **`outbounds[].proxySettings`.** Xray removed the field and answers it with
//     `PrintRemovedFeatureError("outbound \"proxySettings\"", "\"streamSettings.sockopt.dialerProxy\"")`
//     — a *fatal* config-load error, not a warning. The chain builder (`composeChain`) used to emit
//     it, and so does every pasted v2rayN-style chain config in the wild, so a whole class of
//     multi-hop profiles could never start the core. The rewrite is the one the error message
//     instructs, and `transportLayer` is dropped because the pinned core has no such field.
//  2. **A VLESS user with no `encryption`.** The core requires the field on every user
//     (`VLESS users: please add/set "encryption":"none" for every user`). `encryption` names the
//     account's payload encryption, and every importer in the ecosystem writes `none` for a link
//     that omits it; filling it in cannot change a handshake, and leaving it out cannot connect.
//  3. **A `tlsSettings`/`realitySettings` block with no `security` key.** `StreamConfig.Build`
//     switches on `security` and treats an absent one as plaintext — i.e. it *ignores* the TLS block
//     the author wrote, and the session then dials cleartext into a TLS listener (or is refused by
//     the plaintext rule, reported as "Unsupported VLESS"). Naming the security a document already
//     carries is the faithful reading. A config that explicitly says `security:"none"` next to a TLS
//     block is left exactly as written: that is a statement, not an omission.
//  4. **A numeric field written as a string** (`"port": "443"`). The core's `uint16`/`uint32`
//     unmarshalling is strict, so quoting a port is a load failure for an unambiguous value.
//
// Nothing else is touched. No cipher substitution, no invented SNI, no transport swap, no dropped
// detour, no shortened chain: `docs/core-interoperability.md` is right that a document which passes
// a shape test while changing authentication is worse than a refusal.
object XrayConfigRepairs {

    /** What was rewritten, as stable machine-readable tokens for the diagnostic bundle. */
    data class Report(val document: String, val repairs: List<String>) {
        val changed: Boolean get() = repairs.isNotEmpty()
        val summary: String get() = repairs.joinToString(",")
    }

    /** The removed chain field this pass rewrites; named once so tests and code agree. */
    internal const val REMOVED_CHAIN_FIELD = "proxySettings"

    private val lastApplied = AtomicReference("")

    /**
     * Remember the most recent repair set, so the diagnostic plane can say *why* a config the core
     * rejected is now accepted. One field, last-write-wins, no locking: it is a report, not state any
     * decision depends on.
     */
    internal fun record(repairs: List<String>) {
        lastApplied.set(repairs.joinToString(","))
    }

    /** `""` when nothing has been rewritten in this process. */
    fun lastSummary(): String = lastApplied.get()

    /** Numeric outbound fields the core unmarshals as fixed-width integers. */
    private val NUMERIC_FIELDS = mapOf(
        "port" to 65535L,
        "server_port" to 65535L,
        "alterId" to 65535L,
        "alter_id" to 65535L,
        "level" to 255L
    )

    /** Account fields the simplified VLESS shape cannot carry, so lifting must not be attempted. */
    private val VLESS_UNFLATTENABLE_USER_FIELDS = setOf("reverse", "testpre", "testseed", "seed", "experiments")

    /**
     * Repair an Xray-shaped config document. Never throws, and always returns a usable document:
     * when nothing needed repairing the caller gets the original string back byte for byte, so a
     * profile's stored bytes keep their exact hash identity.
     */
    fun apply(source: String): Report {
        val trimmed = source.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return Report(source, emptyList())
        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return Report(source, emptyList())
        if (NativeSingBoxConfig.isNative(root)) return Report(source, emptyList())
        val outbounds = root.optJSONArray("outbounds") ?: return Report(source, emptyList())

        val repairs = LinkedHashSet<String>()
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            unchainProxySettings(outbound, repairs)
            coerceNumericStrings(outbound, repairs)

            val settings = outbound.optJSONObject("settings")
            when (outbound.optString("protocol").trim().lowercase()) {
                "vless" -> {
                    defaultVlessEncryption(outbound, settings, repairs)
                    liftClassicVnext(outbound, settings, repairs)
                }
                "trojan" -> liftTrojanPassword(outbound, settings, repairs)
                else -> Unit
            }
            nameInferredSecurity(outbound, repairs)
        }

        if (repairs.isEmpty()) return Report(source, emptyList())
        return Report(root.toString(), repairs.toList())
    }

    /**
     * `proxySettings: {tag}` → `streamSettings.sockopt.dialerProxy: tag`.
     *
     * An outbound that already names a `dialerProxy` keeps its own: one outbound cannot have two
     * dialers, and the field the core reads wins over the one it rejects. A `proxySettings` with no
     * usable tag is simply removed — it could only ever be a load error.
     */
    internal fun unchainProxySettings(outbound: JSONObject, repairs: MutableSet<String>): Boolean {
        val proxy = outbound.optJSONObject(REMOVED_CHAIN_FIELD) ?: return false
        val target = proxy.optString("tag").trim()
        outbound.remove(REMOVED_CHAIN_FIELD)
        if (target.isEmpty()) {
            repairs += "proxySettings-dropped"
            return true
        }
        val stream = outbound.optJSONObject("streamSettings")
            ?: JSONObject().also { outbound.put("streamSettings", it) }
        val sockopt = stream.optJSONObject("sockopt")
            ?: JSONObject().also { stream.put("sockopt", it) }
        if (sockopt.optString("dialerProxy").isNotBlank()) {
            repairs += "proxySettings-superseded-by-dialerProxy"
            return true
        }
        sockopt.put("dialerProxy", target)
        repairs += "proxySettings-to-dialerProxy"
        return true
    }

    /**
     * The VLESS `encryption` values that name no VLESS mode at all, and can therefore be written as
     * `none` without changing what the node does.
     *
     * The core's vocabulary for this field is exactly two words long — `none`, or a
     * `mlkem768x25519plus.…` post-quantum string — and it answers anything else with
     * `VLESS users: please add/set "encryption":"none" for every user` /
     * `VLESS users: unsupported "encryption": …`, both fatal load errors. Panels copy VMess's
     * `auto`/`none/none` into VLESS links constantly; `auto` on VLESS has never meant anything except
     * "the account carries no payload cipher".
     */
    internal val VLESS_MEANINGLESS_ENCRYPTION = setOf("", "none", "auto", "none/none", "zero", "plain", "default")

    /**
     * Every VLESS account object — classic `vnext[0].users[]` and the simplified outbound form the
     * core reads first — gets an `encryption` the core can parse. Only values that carry no VLESS
     * meaning are touched; a real cipher name is left in place and reported by
     * [CoreConfigSuperset.coreGapIssue] instead of being rewritten behind the user's back.
     */
    internal fun defaultVlessEncryption(
        outbound: JSONObject,
        settings: JSONObject?,
        repairs: MutableSet<String>
    ) {
        settings ?: return
        var touched = false
        val users = settings.optJSONArray("vnext")?.optJSONObject(0)?.optJSONArray("users")
            ?: settings.optJSONArray("users")
        if (users != null && users.length() > 0) {
            forEachObject(users) { user ->
                if (user.optString("encryption").trim().lowercase() in VLESS_MEANINGLESS_ENCRYPTION &&
                    user.optString("encryption") != "none"
                ) {
                    user.put("encryption", "none")
                    touched = true
                }
            }
        }
        val simplified = settings.optString("address").isNotBlank() || settings.optString("id").isNotBlank()
        if (simplified &&
            settings.optString("encryption").trim().lowercase() in VLESS_MEANINGLESS_ENCRYPTION &&
            settings.optString("encryption") != "none"
        ) {
            settings.put("encryption", "none")
            touched = true
        }
        if (touched) repairs += "vless-encryption-defaulted"
    }

    /**
     * Mirror a single-member classic `vnext[0]` into the simplified outbound shape the core prefers
     * (`settings.address/port/id/flow/encryption`). The array is **kept**, not moved: a user who
     * re-exports the profile gets back what they pasted, and the core reads the simplified fields
     * first (`if c.Address != nil`).
     *
     * Abandoned whenever anything could be lost in the copy — a second user, an account field the
     * simplified form has no place for — because then the lift would be a translation, and a
     * translation is not this pass's job.
     */
    internal fun liftClassicVnext(outbound: JSONObject, settings: JSONObject?, repairs: MutableSet<String>) {
        settings ?: return
        if (settings.optString("address").isNotBlank()) return
        val vnext = settings.optJSONArray("vnext") ?: return
        if (vnext.length() != 1) return
        val server = vnext.optJSONObject(0) ?: return
        val address = server.optString("address").trim()
        val port = portOf(server)
        if (address.isEmpty() || port == null) return

        val users = server.optJSONArray("users")
        if (users == null || users.length() != 1) return
        val user = users.optJSONObject(0) ?: return
        if (VLESS_UNFLATTENABLE_USER_FIELDS.any { user.has(it) }) return
        val id = user.optString("id").trim()
        if (id.isEmpty()) return

        settings.put("address", address)
        settings.put("port", port)
        settings.put("id", id)
        user.optString("flow").trim().takeIf { it.isNotEmpty() }?.let { settings.put("flow", it) }
        user.optString("encryption").trim().takeIf { it.isNotEmpty() }?.let { settings.put("encryption", it) }
        repairs += "vless-simplified-form"
    }

    /** A one-member `servers[]` trojan config: the core reads `password` off the client settings. */
    internal fun liftTrojanPassword(outbound: JSONObject, settings: JSONObject?, repairs: MutableSet<String>) {
        settings ?: return
        if (settings.optString("password").isNotBlank()) return
        val servers = settings.optJSONArray("servers") ?: return
        if (servers.length() != 1) return
        val password = servers.optJSONObject(0)?.optString("password").orEmpty().trim()
        if (password.isEmpty()) return
        settings.put("password", password)
        repairs += "trojan-password-lifted"
    }

    /**
     * `tlsSettings`/`realitySettings` present while `security` is missing or blank: name the
     * security the document already carries, because that is the only value under which the core
     * reads those blocks at all.
     */
    internal fun nameInferredSecurity(outbound: JSONObject, repairs: MutableSet<String>) {
        val stream = outbound.optJSONObject("streamSettings") ?: return
        if (stream.optString("security").trim().isNotEmpty()) return
        val security = when {
            stream.optJSONObject("realitySettings") != null -> "reality"
            stream.optJSONObject("tlsSettings") != null -> "tls"
            else -> return
        }
        stream.put("security", security)
        repairs += "security-inferred-from-$security"
    }

    /**
     * Un-quote numeric fields anywhere inside an outbound. A bounded walk: configs with a cycle (a
     * hand-edited `$ref`-style document) cannot spin this pass, and depth 8 is deeper than any
     * outbound schema the two cores model.
     */
    internal fun coerceNumericStrings(node: Any?, repairs: MutableSet<String>) =
        coerceNumericStrings(node, repairs, 0)

    private fun coerceNumericStrings(node: Any?, repairs: MutableSet<String>, depth: Int) {
        if (depth > 8) return
        when (node) {
            is JSONObject -> {
                node.keys().asSequence().toList().forEach { key ->
                    val limit = NUMERIC_FIELDS[key]
                    val value = node.opt(key)
                    if (limit != null && value is String) {
                        val numeric = value.trim().toLongOrNull()
                        if (numeric != null && numeric in 0..limit) {
                            node.put(key, numeric.toInt())
                            repairs += "$key-unquoted"
                        }
                    } else {
                        coerceNumericStrings(node.opt(key), repairs, depth + 1)
                    }
                }
            }
            is JSONArray -> for (index in 0 until node.length()) {
                coerceNumericStrings(node.opt(index), repairs, depth + 1)
            }
            else -> Unit
        }
    }

    private fun portOf(source: JSONObject): Int? = when (val raw = source.opt("port")) {
        is Int -> raw.takeIf { it in 1..65535 }
        is Long -> raw.toInt().takeIf { raw in 1L..65535L }
        is String -> raw.trim().toIntOrNull()?.takeIf { it in 1..65535 }
        else -> null
    }

    private fun forEachObject(array: JSONArray, block: (JSONObject) -> Unit) {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let(block)
        }
    }
}
