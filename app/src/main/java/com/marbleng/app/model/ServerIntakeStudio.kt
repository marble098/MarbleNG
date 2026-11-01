package com.marbleng.app.model

/**
 * MARBLE_INTAKE_GRID_V256 — what a clipboard actually holds, decided once.
 *
 * The old Add sheet asked the user to tell the app what they had pasted (a link? a subscription? a
 * file?) in three tabs. The app already knew: `AppRepository.importClipboard` classifies the same
 * text before it routes it, and a person who has copied a `vless://` link has never once wondered
 * whether it was a "subscription".
 *
 * [IntakeClassifier] is that decision, extracted so that the Add sheet's own copy (the pill under
 * the grid, the label on the clipboard card) and the import that follows it can never disagree —
 * and so the whole thing is testable on the host JVM, where a regex is cheap and a device is not.
 *
 * The rules mirror the repository's, deliberately including the ones that are stricter than they
 * look: a subscription must be **HTTPS** (a plaintext fetch is refused downstream), and a bare
 * `host:port` line is a *server*, not a source.
 */
enum class IntakeKind {
    /** Nothing on the clipboard. */
    EMPTY,

    /** One or more `vless://` / `vmess://` / `trojan://` / … share links. */
    SHARE_LINK,

    /** An Xray/sing-box JSON document or outbound array. */
    JSON,

    /** One or more `https://` subscription URLs. */
    SUBSCRIPTION,

    /** Anything else — a bare host, a SOCKS line, prose. The importer still gets a say. */
    OTHER;

    /** The kinds the Add sheet promises to recognise in one tap. */
    val isRecognised: Boolean get() = this != EMPTY && this != OTHER
}

object IntakeClassifier {
    const val MARKER: String = "MARBLE_INTAKE_GRID_V256"

    private val shareLink = Regex(
        "(?im)^(vless|vmess|trojan|ss|ssr|socks5?|http|https|hysteria2|hy2|tuic|ssh)://[^\\s]+$"
    )

    /** The schemes that are a proxy node even when the rest of the line is unusual. */
    private val proxySchemes = setOf(
        "vless", "vmess", "trojan", "ss", "ssr", "socks5", "socks", "hysteria2", "hy2", "tuic", "ssh"
    )

    private fun lines(text: String): List<String> =
        text.lineSequence().map(String::trim).filter(String::isNotBlank).toList()

    /**
     * A single line whose scheme is a proxy scheme, or a whole clipboard of them.
     *
     * `http://`/`https://` lines are *not* share links: an `https://` line is a subscription and an
     * `http://` one is refused by the importer, so calling either a node would be a promise the
     * import cannot keep.
     */
    fun looksLikeShareLink(text: String?): Boolean {
        val clean = text?.trim().orEmpty()
        if (clean.isBlank()) return false
        return lines(clean).any { line ->
            val scheme = line.substringBefore("://", "").lowercase()
            scheme in proxySchemes && shareLink.matches(line)
        }
    }

    fun looksLikeJson(text: String?): Boolean {
        val clean = text?.trim().orEmpty()
        return clean.startsWith("{") || clean.startsWith("[")
    }

    /**
     * A subscription is *every* line a plain web URL and none of them plaintext.
     *
     * A mixed clipboard (a link plus a URL) is [IntakeKind.OTHER]: the importer imports the links
     * it recognises, and the sheet says so instead of pretending it found a clean source.
     */
    fun subscriptionUrl(text: String?): String? {
        val clean = text?.trim().orEmpty()
        if (clean.isBlank()) return null
        val all = lines(clean)
        if (all.isEmpty()) return null
        if (!all.all { it.startsWith("https://", ignoreCase = true) }) return null
        return all.first()
    }

    fun kindOf(text: String?): IntakeKind {
        val clean = text?.trim().orEmpty()
        if (clean.isBlank()) return IntakeKind.EMPTY
        if (looksLikeJson(clean)) return IntakeKind.JSON
        if (subscriptionUrl(clean) != null) return IntakeKind.SUBSCRIPTION
        if (looksLikeShareLink(clean)) return IntakeKind.SHARE_LINK
        return IntakeKind.OTHER
    }

    /**
     * The one sentence the Add sheet puts under its grid, or "" when there is nothing to say.
     * The English keys are translated by `trx`; the URL is not translated, it is quoted.
     */
    fun clipboardNote(kind: IntakeKind): String = when (kind) {
        IntakeKind.SUBSCRIPTION -> "Subscription detected in clipboard: Tap to import"
        IntakeKind.SHARE_LINK -> "Server link detected in clipboard: Tap to import"
        IntakeKind.JSON -> "Configuration detected in clipboard: Tap to import"
        IntakeKind.EMPTY, IntakeKind.OTHER -> ""
    }

    /** The clipboard card's second line: where the tap will put the thing it found. */
    fun clipboardCardHint(kind: IntakeKind): String = when (kind) {
        IntakeKind.SUBSCRIPTION -> "A source, added as a subscription"
        IntakeKind.SHARE_LINK -> "A node, added to the manual group"
        IntakeKind.JSON -> "An outbound, imported as one node"
        IntakeKind.OTHER, IntakeKind.EMPTY -> "Detects links, JSON and HTTPS subscriptions"
    }

    /** The four cells, in the order the 2×2 grid draws them. */
    val gridOrder: List<AddServerAction> = listOf(
        AddServerAction.CLIPBOARD,
        AddServerAction.SCAN_QR,
        AddServerAction.IMPORT_FILE,
        AddServerAction.MANUAL
    )
}

/**
 * MARBLE_INTAKE_GRID_V256 — the 2×2.
 *
 * Four verbs, four cards, one tap each. The previous sheet had four *tabs* over the same four
 * verbs, plus a fifth (chain) that is now a row inside the manual builder where it belongs — a
 * chain is a way of writing one node, not a way of adding servers.
 */
enum class AddServerAction(val id: String, val title: String, val hint: String) {
    CLIPBOARD(
        id = "clipboard",
        title = "Clipboard",
        hint = "Detects JSON, VLESS, VMess, Trojan and HTTPS subscriptions"
    ),
    SCAN_QR(
        id = "qr",
        title = "Scan QR",
        hint = "Read a code from the camera or a gallery image"
    ),
    IMPORT_FILE(
        id = "file",
        title = "Import file",
        hint = "Load a .txt or .json configuration"
    ),
    MANUAL(
        id = "manual",
        title = "Manual",
        hint = "Build one node by hand, protocol by protocol"
    );

    companion object {
        fun fromId(raw: String?): AddServerAction? =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * MARBLE_SERVER_BUBBLE_V256 — the six things a row's ⋯ may do.
 *
 * The V250 sheet listed nine items in a full-height sheet for actions that are one tap: the
 * complaint was that a three-dot menu opened a page. Six verbs, in the order a person reaches for
 * them, each on one dense row inside a bubble anchored to the button that opened it.
 *
 * [destructive] is on the model, not in the composable, because the error tone is a promise about
 * what a tap does — a future seventh action must decide it here, not by remembering to pass a
 * colour.
 */
enum class ServerMenuAction(val id: String, val title: String, val destructive: Boolean = false) {
    EDIT("edit", "Edit"),
    PING("ping", "Ping"),
    COPY_LINK("copy_link", "Copy link"),
    EXPORT_QR("export_qr", "Export QR code"),
    MOVE_TO_GROUP("move_to_group", "Move to group"),
    DELETE("delete", "Delete", destructive = true);

    companion object {
        val order: List<ServerMenuAction> = entries.toList()

        fun fromId(raw: String?): ServerMenuAction? =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * MARBLE_IP_TILES_V256 — the copy affordance on the address page.
 *
 * A tap copies, and the icon becomes a check for [COPIED_HOLD_MS] before it goes back. The hold is
 * a number here so the page and its test cannot disagree about how long "copied" is claimed.
 */
object IpTileCopy {
    const val COPIED_HOLD_MS: Long = 1_500L

    /** The tile titles the page draws above a value; the keys `trx` translates. */
    fun labelKey(label: String): String = label
}
