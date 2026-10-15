package com.marbleng.app.core

data class ServerCountry(
    val code: String,
    val name: String,
    val flag: String
) {
    val isKnown: Boolean get() = code.isNotBlank()

    val sortKey: String get() = if (isKnown) name.uppercase() else "\uFFFF"

    companion object {
        val UNKNOWN = ServerCountry(code = "", name = "Unknown", flag = "\u25C8")

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

        private val CCTLD: Map<String, String> = NAMES.keys.associateBy { it.lowercase() }

        private const val REGIONAL_INDICATOR_BASE = 0x1F1E6
        private const val REGIONAL_INDICATOR_LAST = 0x1F1FF

        private val CODE_TOKEN = Regex("(?<![A-Z0-9])[\\[(|{<«/#_-]([A-Z]{2})[\\])|}>»/#_-](?![A-Z0-9])")

        private val EDGE_CODE = Regex("^([A-Z]{2})[ .\\-_:]|[ \\-_.:]([A-Z]{2})$")

        fun of(name: String, host: String = ""): ServerCountry {
            fromFlag(name)?.let { return it }
            fromCodeToken(name)?.let { return it }
            fromEdgeCode(name)?.let { return it }
            fromCountryName(name)?.let { return it }
            fromHost(host)?.let { return it }
            return UNKNOWN
        }

        fun flagFor(code: String): String {
            val normalized = code.trim().uppercase()
            if (normalized.length != 2 || normalized.any { it !in 'A'..'Z' }) return UNKNOWN.flag
            return buildString {
                normalized.forEach { letter ->
                    appendCodePoint(REGIONAL_INDICATOR_BASE + (letter - 'A'))
                }
            }
        }

        fun nameFor(code: String): String {
            val normalized = code.trim().uppercase()
            return NAMES[normalized] ?: normalized.ifBlank { UNKNOWN.name }
        }

        fun codeForName(name: String): String? = ALIASES[name.trim().uppercase()]

        private fun fromFlag(name: String): ServerCountry? {
            val points = name.codePoints().toArray()
            for (index in 0 until points.size - 1) {
                val first = points[index]
                val second = points[index + 1]
                if (first !in REGIONAL_INDICATOR_BASE..REGIONAL_INDICATOR_LAST) continue
                if (second !in REGIONAL_INDICATOR_BASE..REGIONAL_INDICATOR_LAST) continue
                val code = buildString {
                    append('A' + (first - REGIONAL_INDICATOR_BASE))
                    append('A' + (second - REGIONAL_INDICATOR_BASE))
                }

                return describe(code)
            }
            return null
        }

        private fun fromCodeToken(name: String): ServerCountry? {
            val match = CODE_TOKEN.find(name) ?: return null
            val candidate = match.groupValues[1].uppercase()

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
