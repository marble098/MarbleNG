package com.marbleng.app.core

/**
 * Offline knowledge base about Iranian networks.
 *
 * Nothing in here is a substitute for live detection: it is the naming/policy layer that turns a
 * raw ASN, carrier code or IP prefix into a human-readable ISP plus the filtering behaviour that
 * operator is documented to use. Iranian networks that are not listed here still activate Iran
 * Mode through the live country signal; they are simply labelled with whatever organisation name
 * the lookup returned instead of a curated Persian name.
 */

enum class IranIspKind { MOBILE, FIXED, INFRASTRUCTURE, HOSTING }

/**
 * Documented aggressiveness of an operator's filtering stack. MCI and Irancell run the strictest
 * DPI in the country; several fixed-line operators lag the national policy by days or weeks.
 */
enum class FilterSeverity { LIGHT, MODERATE, HEAVY, EXTREME }

data class IranIsp(
    val asn: Int,
    val name: String,
    val persianName: String,
    val shortName: String,
    val kind: IranIspKind,
    val severity: FilterSeverity,
    val note: String = ""
) {
    val label: String
        get() = if (asn > 0) "$shortName • AS$asn" else shortName
}

object IranNetworkRegistry {
    const val MOBILE_COUNTRY_CODE = "432"

    /** Private-space injectors used by the national filtering gateway for blocked answers. */
    val BLOCKPAGE_PREFIXES = listOf("10.10.34.")

    /**
     * Domestic "anti-sanction" resolvers. They are reachable only from inside Iranian networks, so
     * reaching them is a strong locality signal. MarbleNG never sends tunnel-endpoint lookups to
     * them: they are probe targets and direct-routing exceptions only.
     */
    val DOMESTIC_RESOLVERS = listOf(
        "178.22.122.100",  // Shecan
        "185.51.200.2",    // Shecan secondary
        "78.157.42.100",   // Electro
        "185.55.226.26",   // Begzar
        "10.202.10.10",    // Radar
        "10.202.10.202"    // 403.online
    )

    /** Public domestic resolvers only. RFC1918 targets are excluded from locality scoring. */
    val PUBLIC_DOMESTIC_RESOLVERS = DOMESTIC_RESOLVERS.filterNot { it.startsWith("10.") }

    /** Domains that are consistently filtered on Iranian consumer networks. */
    val FILTERED_PROBE_DOMAINS = listOf(
        "www.youtube.com",
        "www.facebook.com",
        "x.com",
        "telegram.org"
    )

    /** Reference points that stay reachable on an unfiltered link, used as probe controls. */
    val CONTROL_DOMAIN = "cloudflare.com"

    private val UNKNOWN = IranIsp(
        asn = 0,
        name = "Iranian network",
        persianName = "شبکه ایران",
        shortName = "Iranian ISP",
        kind = IranIspKind.FIXED,
        severity = FilterSeverity.HEAVY,
        note = "Operator not in the offline table; national filtering policy assumed."
    )

    /**
     * Curated table of the operators that carry the overwhelming majority of Iranian consumer
     * traffic. AS numbers verified against public ASN registries.
     */
    val ASN_TABLE: Map<Int, IranIsp> = listOf(
        IranIsp(
            197207, "Mobile Communication Company of Iran (MCI)", "همراه اول", "MCI / Hamrah-e Aval",
            IranIspKind.MOBILE, FilterSeverity.EXTREME,
            "Largest mobile operator. Runs the strictest DPI: SNI inspection, REALITY IP graylisting and protocol allowlisting."
        ),
        IranIsp(
            44244, "Iran Cell Service and Communication Company (MTN Irancell)", "ایرانسل", "Irancell",
            IranIspKind.MOBILE, FilterSeverity.EXTREME,
            "Aggressive volume-based endpoint blocking; high-traffic proxy IPs are commonly burned within days."
        ),
        IranIsp(
            57218, "Rightel Communication Service Company", "رایتل", "Rightel",
            IranIspKind.MOBILE, FilterSeverity.HEAVY,
            "Third mobile operator; follows the national policy with occasional lag."
        ),
        IranIsp(
            58224, "Iran Telecommunication Company (TCI)", "مخابرات ایران", "TCI / Mokhaberat",
            IranIspKind.FIXED, FilterSeverity.HEAVY,
            "National fixed-line incumbent; inconsistent enforcement across regional POPs."
        ),
        IranIsp(
            48159, "Telecommunication Infrastructure Company (TIC)", "شرکت ارتباطات زیرساخت", "TIC",
            IranIspKind.INFRASTRUCTURE, FilterSeverity.EXTREME,
            "Operates the national gateway where centralised filtering is applied."
        ),
        IranIsp(
            49666, "Telecommunication Infrastructure Company (TIC)", "شرکت ارتباطات زیرساخت", "TIC",
            IranIspKind.INFRASTRUCTURE, FilterSeverity.EXTREME,
            "Secondary national gateway ASN."
        ),
        IranIsp(
            12880, "Iran Information Technology Company (DCI/ITC)", "شرکت ارتباطات داده‌ها", "DCI / ITC",
            IranIspKind.INFRASTRUCTURE, FilterSeverity.HEAVY,
            "Historic data-communications backbone carrying many downstream ISPs."
        ),
        IranIsp(
            31549, "Aria Shatel", "شاتل", "Shatel",
            IranIspKind.FIXED, FilterSeverity.HEAVY,
            "Large private ISP; measurements show it tracking MCI's blocking decisions closely."
        ),
        IranIsp(
            16322, "Pars Online", "پارس آنلاین", "ParsOnline",
            IranIspKind.FIXED, FilterSeverity.MODERATE,
            "Private fixed-line ISP with historically looser TLS enforcement."
        ),
        IranIsp(
            43754, "Asiatech Data Transfer", "آسیاتک", "Asiatech",
            IranIspKind.FIXED, FilterSeverity.MODERATE
        ),
        IranIsp(
            49100, "Pishgaman Toseeh Ertebatat", "پیشگامان توسعه ارتباطات", "Pishgaman",
            IranIspKind.FIXED, FilterSeverity.MODERATE
        ),
        IranIsp(
            50810, "Mobin Net Communication", "مبین نت", "MobinNet",
            IranIspKind.FIXED, FilterSeverity.MODERATE,
            "Fixed wireless (TD-LTE) operator."
        ),
        IranIsp(
            42337, "Respina Networks & Beyond", "رسپینا", "Respina",
            IranIspKind.FIXED, FilterSeverity.MODERATE
        ),
        IranIsp(
            25184, "Afranet", "افرانت", "Afranet",
            IranIspKind.HOSTING, FilterSeverity.MODERATE
        ),
        IranIsp(
            39501, "Parvaresh Dadeha", "پرورش داده‌ها", "Parvaresh Dadeha",
            IranIspKind.FIXED, FilterSeverity.MODERATE
        ),
        IranIsp(
            41881, "Fanava Group", "گروه فناوا", "Fanava",
            IranIspKind.FIXED, FilterSeverity.MODERATE
        ),
        IranIsp(
            202468, "Noyan Abr Arvan (ArvanCloud)", "ابر آروان", "ArvanCloud",
            IranIspKind.HOSTING, FilterSeverity.HEAVY,
            "Domestic CDN/cloud inside the national network."
        )
    ).associateBy { it.asn }

    /**
     * MCC 432 network codes. Any 432 prefix means an Iranian mobile network regardless of whether
     * the specific MNC is listed.
     */
    private val MNC_TABLE: Map<String, Int> = mapOf(
        "11" to 197207, // MCI / Hamrah-e Aval
        "14" to 58224,  // Telecommunication Kish (TKC)
        "19" to 44244,  // MTN Irancell (legacy code)
        "35" to 44244,  // MTN Irancell
        "20" to 57218,  // Rightel
        "21" to 57218,  // Rightel
        "32" to 58224,  // Taliya (TCI hosted)
        "70" to 58224,  // TCI fixed/WLL
        "93" to 58224,  // Iraphone
        "99" to 58224   // TCI / Rightel WLL
    )

    private val MNC_NAMES: Map<String, String> = mapOf(
        "11" to "MCI / Hamrah-e Aval",
        "14" to "Telecommunication Kish (TKC)",
        "19" to "MTN Irancell",
        "35" to "MTN Irancell",
        "20" to "Rightel",
        "21" to "Rightel",
        "32" to "Taliya",
        "70" to "TCI",
        "93" to "Iraphone",
        "99" to "TCI"
    )

    /**
     * Organisation-name fingerprints. Iran has hundreds of allocated ASNs, so a curated AS table can
     * never be complete; matching the operator name returned by a live lookup keeps the smaller
     * regional ASNs of a known operator mapped to that operator's filtering profile.
     */
    private val NAME_FINGERPRINTS: List<Pair<String, Int>> = listOf(
        "irancell" to 44244,
        "iran cell" to 44244,
        "mtn" to 44244,
        "mobile communication company of iran" to 197207,
        "hamrah" to 197207,
        "mci" to 197207,
        "rightel" to 57218,
        "iran telecommunication company" to 58224,
        "telecommunication company of iran" to 58224,
        "mokhaberat" to 58224,
        "telecommunication infrastructure" to 48159,
        "information technology company" to 12880,
        "shatel" to 31549,
        "parsonline" to 16322,
        "pars online" to 16322,
        "asiatech" to 43754,
        "pishgaman" to 49100,
        "mobin net" to 50810,
        "mobinnet" to 50810,
        "respina" to 42337,
        "afranet" to 25184,
        "parvaresh" to 39501,
        "fanava" to 41881,
        "arvan" to 202468
    )

    /**
     * Well-known Iranian IPv4 allocations. This is a supporting signal only — it adds confidence
     * when a live country lookup is unavailable and never activates Iran Mode on its own.
     */
    private val IPV4_PREFIXES: List<Pair<String, Int>> = listOf(
        "2.144.0.0" to 14,
        "2.176.0.0" to 12,
        "5.52.0.0" to 16,
        "5.53.32.0" to 19,
        "5.112.0.0" to 12,
        "46.100.0.0" to 14,
        "78.38.0.0" to 15,
        "78.157.32.0" to 19,
        "80.191.0.0" to 16,
        "87.107.0.0" to 16,
        "91.98.0.0" to 15,
        "95.38.0.0" to 16,
        "151.232.0.0" to 13,
        "178.22.120.0" to 21,
        "178.131.0.0" to 16,
        "185.55.224.0" to 21,
        "188.158.0.0" to 15,
        "217.218.0.0" to 15
    )

    fun byAsn(asn: Int): IranIsp? = ASN_TABLE[asn]

    fun byOperatorCode(operator: String): IranIsp? {
        val code = operator.trim()
        if (!code.startsWith(MOBILE_COUNTRY_CODE) || code.length < 4) return null
        val mnc = code.substring(3)
        val asn = MNC_TABLE[mnc] ?: MNC_TABLE[mnc.padStart(2, '0')]
        if (asn != null) return ASN_TABLE[asn]
        return UNKNOWN.copy(
            name = "Iranian mobile network (MCC/MNC $code)",
            shortName = "Iranian mobile network",
            kind = IranIspKind.MOBILE,
            severity = FilterSeverity.EXTREME
        )
    }

    fun operatorCodeName(operator: String): String {
        val code = operator.trim()
        if (!code.startsWith(MOBILE_COUNTRY_CODE) || code.length < 4) return ""
        val mnc = code.substring(3)
        return MNC_NAMES[mnc] ?: MNC_NAMES[mnc.padStart(2, '0')] ?: ""
    }

    fun isIranianOperatorCode(operator: String): Boolean =
        operator.trim().length >= 4 && operator.trim().startsWith(MOBILE_COUNTRY_CODE)

    fun byOrganisation(org: String): IranIsp? {
        val needle = org.lowercase()
        if (needle.isBlank()) return null
        NAME_FINGERPRINTS.firstOrNull { needle.contains(it.first) }?.let { return ASN_TABLE[it.second] }
        return null
    }

    /**
     * Resolves the best label for a live observation. Falls back to the organisation string reported
     * by the lookup so unlisted Iranian ASNs are still named accurately.
     */
    fun resolve(asn: Int, organisation: String, operatorCode: String): IranIsp {
        byAsn(asn)?.let { return it }
        byOrganisation(organisation)?.let { return it.copy(asn = if (asn > 0) asn else it.asn) }
        byOperatorCode(operatorCode)?.let { return it.copy(asn = if (asn > 0) asn else it.asn) }
        val cleaned = organisation.substringAfter(' ', organisation).trim()
        return UNKNOWN.copy(
            asn = asn,
            name = cleaned.ifBlank { UNKNOWN.name },
            shortName = cleaned.ifBlank { UNKNOWN.shortName }
        )
    }

    fun unknownIsp(): IranIsp = UNKNOWN

    fun isBlockPageAddress(ip: String): Boolean = BLOCKPAGE_PREFIXES.any { ip.startsWith(it) }

    /** True when [ip] falls inside a known Iranian IPv4 allocation. */
    fun isKnownIranianIpv4(ip: String): Boolean {
        val value = ipv4ToLong(ip) ?: return false
        return IPV4_PREFIXES.any { (network, bits) ->
            val base = ipv4ToLong(network) ?: return@any false
            val mask = if (bits <= 0) 0L else (0xFFFFFFFFL shl (32 - bits)) and 0xFFFFFFFFL
            (value and mask) == (base and mask)
        }
    }

    private fun ipv4ToLong(ip: String): Long? {
        val parts = ip.trim().split('.')
        if (parts.size != 4) return null
        var out = 0L
        for (part in parts) {
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            out = (out shl 8) or octet.toLong()
        }
        return out
    }
}
