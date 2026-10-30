package com.marbleng.app.model

/**
 * MARBLE_PLAIN_VOICE_V246 — Settings stopped talking like a datasheet.
 *
 * The report was one sentence long: "the titles and sentences in Settings, and in the rest of the
 * app, are complicated and hard to understand; rewrite them simpler and friendlier." It was right,
 * and the reason was structural rather than stylistic — every feature had been named after the
 * mechanism that implements it. A person who wants their Iranian sites to stay direct was asked to
 * understand "Geo direct domestic separation". A person who wants a simpler exit-IP story was sent
 * to "Identity Guard & Locations". The page that exists to change a setting was asking the user to
 * learn the architecture first.
 *
 * This object is the rule of that pass, not a translation table. It names the vocabulary the app is
 * no longer allowed to use in a setting, and it keeps every shipped rewrite as data so a JVM test
 * can prove each one is *actually* plainer instead of merely different:
 *
 * - no [JARGON] word may survive a rewrite,
 * - a rewrite must be different, or it is not a rewrite,
 * - a rewrite may grow a sentence by at most 25 % of its characters — clarity is worth a few
 *   letters, not a longer sentence.
 *
 * The Persian half of every rewrite lives beside every other Persian phrase, in
 * `MarblePersianLexicon.kt`, under the same marker. The source-wide audit in
 * `system-integrity-check.py` proves the old words are gone from the screens and the new ones are
 * in them.
 */
object PlainVoice {
    const val MARKER: String = "MARBLE_PLAIN_VOICE_V246"

    /**
     * Words that describe how the app is built instead of what it does. They are allowed in code
     * and in these chapters — the paragraph above is full of them — and never in a title a person
     * reads while trying to change a setting.
     */
    val JARGON: List<String> = listOf(
        "Datapath",
        "Prescription",
        "Prescriptions",
        "Verdict",
        "Seat",
        "Seats",
        "Telemetry",
        "Orchestrat",
        "Hardening",
        "RFC1918",
        "GeoSite",
        "GeoIP",
        // The phrase, not the word: "Geo direct domestic separation" named the routing table
        // instead of the outcome, and no single word in it could be flagged on its own.
        "Geo direct",
        "DPI"
    )

    /** One shipped rewrite: the words that were on screen, and the words that replaced them. */
    data class Rewrite(val before: String, val after: String)

    /**
     * Every title or sentence this chapter rewrote, in the order the Settings page shows them, so
     * the list can be read against a screenshot.
     */
    val REWRITES: List<Rewrite> = listOf(
        Rewrite("Connection Mode & Ports", "How you connect"),
        Rewrite("Full TUN device VPN", "Whole-phone VPN"),
        Rewrite("Local SOCKS5 proxy", "Local proxy"),
        Rewrite("Proxy Core Engine", "Proxy engine"),
        Rewrite("Tunnel Datapath", "Tunnel path"),
        Rewrite("Power & Speed Profile", "Speed and battery"),
        Rewrite("Routing & Geo Databases", "What goes direct"),
        Rewrite("Geo direct domestic separation", "Iranian sites stay direct"),
        Rewrite("Custom routing rules", "Your own rules"),
        Rewrite("DNS & IPv4 / IPv6", "DNS and IP version"),
        Rewrite("Per-App Split Tunnel", "Which apps use the tunnel"),
        Rewrite("Only selected apps use VPN", "Only the apps you pick"),
        Rewrite("Selected apps bypass VPN", "The apps you pick stay direct"),
        Rewrite("Iran Mode & Censorship Lab", "Iran mode & blocking test"),
        Rewrite("Detect DPI filtering, TLS resets and apply prescriptions", "Find what blocks you and fix it"),
        Rewrite("Ping & Delay Testing", "How ping is measured"),
        Rewrite("Real HTTPS tunnel delay", "Real tunnel delay"),
        Rewrite("TCP handshake ping", "Quick TCP ping"),
        Rewrite("Native core URL test", "Built-in core test"),
        Rewrite("Network Council Scanner", "Network check"),
        Rewrite("12 experts measure this link, then apply the best settings", "Twelve quick checks that tune the app for this network"),
        Rewrite("Gaming, Streaming and Extreme Bypass in one tap", "Gaming, streaming or hard bypass in one tap"),
        Rewrite("Automatic Server Selector", "Pick a server for me"),
        Rewrite("Off · manual server selection", "Off · you pick the server"),
        Rewrite("Smart Jitter Reducer", "Smooth out the jitter"),
        Rewrite("Off · the network policy stays untouched", "Off · nothing is changed"),
        Rewrite("Identity Guard & Locations", "Hide my real IP"),
        Rewrite("Exit IP protection and automatic country detection", "Protect your IP and find server countries"),
        Rewrite("Appearance & Accent", "Look and colour"),
        Rewrite("Tab Bar & Fourth Tab", "Bottom bar & fourth tab"),
        Rewrite("Four tabs enabled", "Four tabs"),
        Rewrite("Home Screen Ping Action", "Home ping button"),
        Rewrite("Connect button style, telemetry cards and shortcuts", "Connect style, cards and shortcuts"),
        Rewrite("Subscriptions & Auto-Refresh", "Subscriptions & refresh"),
        Rewrite("Manual refresh only", "Only when you ask"),
        Rewrite("Backup & Restore", "Backup"),
        Rewrite("Export or import all servers, subscriptions and settings", "Save or restore everything in one file"),
        Rewrite("Notifications & Background", "Notifications & battery"),
        Rewrite("Live notification stats and unrestricted battery access", "Live stats in the shade, and battery access"),
        Rewrite("Diagnostics, Logs & Bug Finder", "Fix & diagnose"),
        Rewrite("Privacy audit, system doctor, core logs and health checks", "Checks that find and fix problems"),
        Rewrite("Tap any row to configure • Use search to jump", "Tap a row to change it • Search to find one"),
        Rewrite("Commission the council", "Run the check"),
        Rewrite("Twelve specialists · one plan", "Twelve checks, one plan"),
        Rewrite("Why each seat ruled", "Why each one voted"),
        Rewrite("Dissent", "Objected"),
        Rewrite("Last verdict", "Last result"),
        Rewrite("Last saved verdict", "Last saved result"),
        Rewrite("Forget the last verdict", "Clear the last result"),
        Rewrite("Settings the council would write", "What it would change"),
        Rewrite("Apply the council's settings", "Apply these settings"),
        Rewrite("What was measured", "What we measured"),
        Rewrite("The twelve seats", "The twelve checks"),
        Rewrite("Apply Recommended Prescription", "Apply what it suggests"),
        Rewrite("Auto-Apply Decisive Prescriptions", "Apply clear results automatically"),
        Rewrite("Enable anti-filtering timeouts, resolver hardening and MTU healing", "Fight filtering with timeouts, safer DNS and MTU repair"),
        Rewrite("Runs bounded DNS, TCP and TLS handshake probes to identify active filtering on your current network.", "Checks your network for blocking and shows what it finds."),
        Rewrite("Send every connection through the proxy server", "Send everything through the server"),
        Rewrite("Send configured domestic country traffic directly", "Keep Iranian sites direct"),
        Rewrite("Keep local Wi-Fi and RFC1918 addresses direct", "Keep home network devices direct"),
        Rewrite("Apply domain, IP and Geo rules to tunnel traffic", "Match domain, IP and country rules"),
        Rewrite("Combine tags across multiple geo rule repositories", "Combine rules from several sources"),
        Rewrite("Create a GeoSite, GeoIP, Domain, IP or Port rule", "Make a rule for a site, IP or port"),
        Rewrite("Smart Presets", "One-tap modes"),
        Rewrite(
            "A preset bundles the tuning knobs behind a named decision. Your servers, routing, DNS and split tunnel are never changed.",
            "Pick a mode and the app tunes itself. Your servers, routing, DNS and tunnel stay as they are."
        ),
        Rewrite(
            "Turbo power, aggressive jitter reduction and raced connects — built for latency, not bulk.",
            "Full power and quicker connects. Made for games, not downloads."
        ),
        Rewrite(
            "Throughput first: multiplexing on, adaptive buffers, calmer jitter probing.",
            "Built for speed: bigger buffers and smoother video."
        ),
        Rewrite("Extreme Bypass", "Blocked network"),
        Rewrite(
            "For a hostile network: Iran Mode always on, record fragmentation, a conservative MTU and a wider connect race.",
            "For strict filtering: Iran mode on, small packets and more retries."
        ),
        Rewrite("Selector & Strategy", "How it picks"),
        Rewrite(
            "One tap bundles the tuning knobs — routing, DNS and servers stay untouched.",
            "One tap, and the app tunes itself — your routing, DNS and servers stay as they are."
        ),
    )

    /** Which jargon words are still in [text]. Empty means the sentence is allowed on a screen. */
    fun hits(text: String): List<String> =
        JARGON.filter { word -> text.contains(word, ignoreCase = true) }

    fun isPlain(text: String): Boolean = hits(text).isEmpty()

    /**
     * Some jargon never appears as a literal. It is an enum name printed straight into a subtitle —
     * `1.1.1.1 · FORCE_IPV4`, `On · LEAST_PING`, `Wi-Fi · DNS UseIPv4` — which is why the literal
     * audit could not see it. These maps are the same rule one level down: the words a person reads
     * instead of the constant the code stores. A screen interpolates the label and translates it
     * like any other string, so the Persian screen is covered by the same pass.
     */
    val ADDRESS_FAMILY_LABELS: Map<String, String> = mapOf(
        "SMART" to "Automatic",
        "PREFER_IPV4" to "IPv4 first",
        "PREFER_IPV6" to "IPv6 first",
        "FORCE_IPV4" to "IPv4 only",
        "FORCE_IPV6" to "IPv6 only"
    )

    /** `FORCE_IPV4` -> `IPv4 only`. An unknown constant loses its screaming case, never its meaning. */
    fun addressFamilyLabel(name: String): String =
        ADDRESS_FAMILY_LABELS[name.uppercase()] ?: name.replace('_', ' ').lowercase()

    /** `UseIPv4` is a resolver argument, not a sentence. */
    val DNS_STRATEGY_LABELS: Map<String, String> = mapOf(
        "USEIPV4" to "IPv4 only",
        "USEIPV6" to "IPv6 only",
        "USEIP" to "IPv4 or IPv6"
    )

    fun dnsStrategyLabel(raw: String): String =
        DNS_STRATEGY_LABELS[raw.uppercase()] ?: raw

    /**
     * A rewrite is plainer when it dropped the jargon, actually says something different, and did
     * not turn a label into a paragraph. The 25 % allowance is the budget for saying the same thing
     * with ordinary words: "Proxy Core Engine" may not become "The engine that runs your proxies".
     */
    fun isSimpler(before: String, after: String): Boolean =
        before != after && isPlain(after) && after.length <= before.length * 1.25

    /** The rewrites that break the rule, by their old wording. Empty for a clean pass. */
    fun violations(): List<String> =
        REWRITES.filterNot { isSimpler(it.before, it.after) }.map { it.before }
}
