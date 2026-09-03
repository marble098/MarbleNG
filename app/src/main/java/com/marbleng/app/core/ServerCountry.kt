package com.marbleng.app.core

// MARBLE_SERVERS_COUNTRY_V120
//
// Subscriptions label their nodes however they like: a leading flag emoji, a bracketed ISO code,
// a spelled-out country, or nothing at all. The Servers page needs one stable answer per node for
// its flag column, its "Country" sort and its "Group by country" switch, so the guessing lives
// here — in one pure, unit-tested resolver instead of scattered string hacks in the UI.
//
// The resolver is offline by design. A GeoIP lookup would need the network the user is trying to
// fix, so every signal below comes from the text Marble already has.

/** Where a server is believed to live. [code] is empty when nothing in the label says. */
data class ServerCountry(
    /** ISO 3166-1 alpha-2, upper-case, or empty when unknown. */
    val code: String,
    /** English country name, or the raw code / "Unknown" when there is no table entry. */
    val name: String,
    /** The flag emoji for [code], or a neutral glyph when unknown. */
    val flag: String
) {
    val isKnown: Boolean get() = code.isNotBlank()

    /** Stable sort key: known countries alphabetically, unknown ones last. */
    val sortKey: String get() = if (isKnown) name.uppercase() else "\uFFFF"

    companion object {

        val UNKNOWN = ServerCountry(code = "", name = "Unknown", flag = "\u25C8")

        /**
         * ISO 3166-1 alpha-2 → English name. Covers the locations proxy providers actually sell plus
         * the neighbours users search for; an unmapped code still renders as its own code.
         */
        private val NAMES: Map<String, String> = mapOf(
            "AD" to "Andorra", "AE" to "United Arab Emirates", "AF" to "Afghanistan",
            "AL" to "Albania", "AM" to "Armenia", "AR" to "Argentina", "AT" to "Austria",
            "AU" to "Australia", "AZ" to "Azerbaijan", "BA" to "Bosnia", "BD" to "Bangladesh",
            "BE" to "Belgium", "BG" to "Bulgaria", "BH" to "Bahrain", "BN" to "Brunei",
            "BO" to "Bolivia", "BR" to "Brazil", "BY" to "Belarus", "CA" to "Canada",
            "CH" to "Switzerland", "CL" to "Chile", "CN" to "China", "CO" to "Colombia",
            "CR" to "Costa Rica", "CY" to "Cyprus", "CZ" to "Czechia", "DE" to "Germany",
            "DK" to "Denmark", "DO" to "Dominican Republic", "DZ" to "Algeria", "EC" to "Ecuador",
            "EE" to "Estonia", "EG" to "Egypt", "ES" to "Spain", "FI" to "Finland",
            "FR" to "France", "GB" to "United Kingdom", "GE" to "Georgia", "GH" to "Ghana",
            "GR" to "Greece", "GT" to "Guatemala", "HK" to "Hong Kong", "HR" to "Croatia",
            "HU" to "Hungary", "ID" to "Indonesia", "IE" to "Ireland", "IL" to "Israel",
            "IN" to "India", "IQ" to "Iraq", "IR" to "Iran", "IS" to "Iceland", "IT" to "Italy",
            "JO" to "Jordan", "JP" to "Japan", "KE" to "Kenya", "KH" to "Cambodia",
            "KR" to "South Korea", "KW" to "Kuwait", "KZ" to "Kazakhstan", "LB" to "Lebanon",
            "LI" to "Liechtenstein", "LK" to "Sri Lanka", "LT" to "Lithuania", "LU" to "Luxembourg",
            "LV" to "Latvia", "LY" to "Libya", "MA" to "Morocco", "MC" to "Monaco",
            "MD" to "Moldova", "ME" to "Montenegro", "MK" to "North Macedonia", "MT" to "Malta",
            "MU" to "Mauritius", "MV" to "Maldives", "MX" to "Mexico", "MY" to "Malaysia",
            "NG" to "Nigeria", "NI" to "Nicaragua", "NL" to "Netherlands", "NO" to "Norway",
            "NP" to "Nepal", "NZ" to "New Zealand", "OM" to "Oman", "PA" to "Panama",
            "PE" to "Peru", "PH" to "Philippines", "PK" to "Pakistan", "PL" to "Poland",
            "PT" to "Portugal", "PY" to "Paraguay", "QA" to "Qatar", "RO" to "Romania",
            "RS" to "Serbia", "RU" to "Russia", "SA" to "Saudi Arabia", "SE" to "Sweden",
            "SG" to "Singapore", "SI" to "Slovenia", "SK" to "Slovakia", "TH" to "Thailand",
            "TN" to "Tunisia", "TR" to "Turkey", "TW" to "Taiwan", "UA" to "Ukraine",
            "US" to "United States", "UY" to "Uruguay",
            "UZ" to "Uzbekistan", "VE" to "Venezuela", "VN" to "Vietnam", "ZA" to "South Africa"
        )

        /** Country names (and the aliases users type) mapped back to a code. */
        private val ALIASES: Map<String, String> = buildMap {
            NAMES.forEach { (code, name) -> put(name.uppercase(), code) }
            put("UK", "GB")
            put("USA", "US")
            put("UNITED STATES OF AMERICA", "US")
            put("ENGLAND", "GB")
            put("BRITAIN", "GB")
            put("UKRAINE", "UA")
            put("HOLLAND", "NL")
            put("DEUTSCHLAND", "DE")
            put("FRANCE", "FR")
            put("TÜRKIYE", "TR")
            put("TURKIYE", "TR")
            put("KOREA", "KR")
            put("PERSIA", "IR")
            put("AMERICA", "US")
        }

        /** Country-code TLDs that unambiguously name a country (".de", ".nl", …). */
        private val CCTLD: Map<String, String> = NAMES.keys.associateBy { it.lowercase() }

        /** A regional-indicator flag pair anywhere in a label. */
        private val FLAG_PATTERN = Regex("[\\uD83C][\\uDDE6-\\uDDFF][\\uD83C][\\uDDE6-\\uDDFF]")

        /**
         * A two-letter token separated from the rest of the label: `[DE] (DE) |DE| -DE- _DE_ /DE/
         * #DE`. Upper-case only, for the same reason as [EDGE_CODE]: a lower-case "(id)" in a node
         * label is an identifier, not Indonesia, and a real ISO code is capitalised by convention.
         */
        private val CODE_TOKEN = Regex("(?<![A-Z0-9])[\\[(|{<«/#_-]([A-Z]{2})[\\])|}>»/#_-](?![A-Z0-9])")

        /**
         * A leading or trailing bare two-letter code, e.g. "DE Frankfurt 01" or "Frankfurt 01 DE".
         * Deliberately case-sensitive: an ISO code is conventionally upper-case, so a lower-case
         * two-letter word at the start of a label ("No route", "In touch") is not read as a country.
         */
        private val EDGE_CODE = Regex("^([A-Z]{2})[ .\\-_:]|[ \\-_.:]([A-Z]{2})$")

        /** Resolve the country of a node from the label a subscription gave it. */
        fun of(name: String, host: String = ""): ServerCountry {
            fromFlag(name)?.let { return it }
            fromCodeToken(name)?.let { return it }
            fromEdgeCode(name)?.let { return it }
            fromCountryName(name)?.let { return it }
            fromHost(host)?.let { return it }
            return UNKNOWN
        }

        /** The flag emoji for an ISO alpha-2 code, or the neutral glyph. */
        fun flagFor(code: String): String {
            val normalized = code.trim().uppercase()
            if (normalized.length != 2 || normalized.any { it !in 'A'..'Z' }) return UNKNOWN.flag
            return buildString {
                normalized.forEach { letter ->
                    appendCodePoint(REGIONAL_INDICATOR_BASE + (letter - 'A'))
                }
            }
        }

        /** English name for a code; unmapped codes render as themselves. */
        fun nameFor(code: String): String {
            val normalized = code.trim().uppercase()
            return NAMES[normalized] ?: normalized.ifBlank { UNKNOWN.name }
        }

        private const val REGIONAL_INDICATOR_BASE = 0x1F1E6

        private fun fromFlag(name: String): ServerCountry? {
            val match = FLAG_PATTERN.find(name) ?: return null
            val code = buildString {
                match.value.codePoints().forEach { point ->
                    append('A' + (point - REGIONAL_INDICATOR_BASE))
                }
            }
            return code.takeIf { it.length == 2 }?.let(::describe)
        }

        private fun fromCodeToken(name: String): ServerCountry? {
            val match = CODE_TOKEN.find(name) ?: return null
            val candidate = match.groupValues[1].uppercase()
            // A bracketed token is only a country when the table knows it, so "[443]" or "(id)" in a
            // node label never becomes a fake country.
            return if (NAMES.containsKey(candidate)) describe(candidate) else null
        }

        private fun fromEdgeCode(name: String): ServerCountry? {
            val match = EDGE_CODE.find(name.trim()) ?: return null
            val candidate = match.groupValues[1] + match.groupValues[2]
            return if (NAMES.containsKey(candidate)) describe(candidate) else null
        }

        private fun fromCountryName(name: String): ServerCountry? {
            val words = name.uppercase().replace(Regex("[^A-Z\\u00C0-\\u024F ]"), " ")
                .split(Regex("\\s+")).filter(String::isNotBlank)
            // Longest alias first so "United Arab Emirates" wins over "United States".
            for (length in minOf(words.size, 4) downTo 1) {
                for (start in 0..words.size - length) {
                    val candidate = words.subList(start, start + length).joinToString(" ")
                    ALIASES[candidate]?.let { return describe(it) }
                }
            }
            return null
        }

        private fun fromHost(host: String): ServerCountry? {
            val normalized = host.trim().lowercase().removeSurrounding("[", "]")
            if (normalized.isBlank() || normalized.all { it.isDigit() || it == '.' || it == ':' }) {
                return null
            }
            val tld = normalized.substringAfterLast('.', "")
            if (tld.length != 2) return null
            return CCTLD[tld]?.let(::describe)
        }

        private fun describe(code: String): ServerCountry =
            ServerCountry(code = code, name = nameFor(code), flag = flagFor(code))
    }
}
