package com.marbleng.app.core

// MARBLE_MULTI_SOURCE_ROUTING_V212 — the curated domestic-destination knowledge.
//
// The report: *"routing sends Iranian traffic direct, but it is still weak; sometimes Iranian
// sites or apps still go through the proxy."*
//
// Why a database union alone does not fix that: every geo database is somebody's published
// snapshot, and the `ir` category inside any of them is a list somebody maintains. The moment a
// domestic service publishes on a domain that list has not caught up with — a bank's new payment
// host, a messenger's CDN, an app's API on a `.com` — the traffic falls through to the proxy
// default. Adding a second or third source widens the net, but all three share the same blind
// spot, because they are all lists of the same shape maintained by the same kind of process.
//
// So the product keeps its own list, and this file is it. It is deliberately:
//
//  - **domains only.** No CIDR ranges. An Iranian IP block that is wrong sends someone else's
//    traffic out of the tunnel — a leak, not a mistake — and a range this code cannot verify is a
//    range it must not assert. The IP side of "domestic stays direct" is `geoip:ir` in every
//    enabled database ([GeoAssetRegistry]), which is verified by the database's own publisher.
//  - **services a user can name.** Banks, government, messengers, ride-hailing, shops, app
//    stores, ISPs, media. Each one is a service Iranians actually open, which is what makes the
//    list reviewable: a reader can look at it and say "yes, that one is domestic".
//  - **three levels, one dial** ([com.marbleng.app.model.GeoPrecision]). STANDARD emits nothing
//    from this file; ENHANCED emits the domains; MAXIMUM adds brand keywords, so a domestic
//    service on a TLD nobody published still reads as domestic. Keyword matching costs a little
//    work per new connection, which is why the strongest level is a choice and not the default.
//
// Everything is pure data plus a matcher, so the rule the engine gets, the answer the simulator
// prints and the count the settings page reports all come from one list.

import com.marbleng.app.model.GeoPrecision

object IranPrecisionPack {

    /** What a group of entries is, for the settings report and the diagnostics line. */
    enum class Category(val label: String) {
        PAYMENT("Payments"),
        BANK("Banks"),
        GOVERNMENT("Government"),
        MESSENGER("Messengers"),
        MOBILITY("Ride & travel"),
        COMMERCE("Shopping"),
        APP_STORE("App stores"),
        CDN("CDN & hosting"),
        TELECOM("Operators"),
        MEDIA("Media"),
        EDUCATION("Education"),
        HEALTH("Health"),
        MARKET("Markets")
    }

    data class Entry(val category: Category, val domain: String)

    /**
     * The domestic TLD. It is a suffix, not a domain: `.ir` covers every second-level host in the
     * country, including the thousands this file does not list, and it is the single highest-value
     * rule in the whole routing layer.
     */
    val TLD_SUFFIX: String = "ir"

    /**
     * Every curated destination, in the order the settings page groups them.
     *
     * Reviewing this list is reviewing the feature: each entry is a named domestic service. An
     * entry that is not domestic is a routing bug, so entries are added conservatively and a
     * domain that cannot be named is left to `geoip:ir`.
     */
    val ENTRIES: List<Entry> = listOf(
        // Payment network + the PSPs that carry domestic card payments. Missing these is the
        // classic "the site loads but the payment fails" report, because the payment host is a
        // different domain from the shop's.
        Entry(Category.PAYMENT, "shaparak.ir"),
        Entry(Category.PAYMENT, "asanpardakht.ir"),
        Entry(Category.PAYMENT, "behpardakht.com"),
        Entry(Category.PAYMENT, "sadadpsp.ir"),
        Entry(Category.PAYMENT, "pec.ir"),
        Entry(Category.PAYMENT, "sepordeh.com"),
        Entry(Category.PAYMENT, "zarinpal.com"),
        Entry(Category.PAYMENT, "idpay.ir"),
        Entry(Category.PAYMENT, "payping.ir"),
        Entry(Category.PAYMENT, "nextpay.org"),
        Entry(Category.PAYMENT, "parsianpardakht.ir"),
        Entry(Category.PAYMENT, "samanparsian.ir"),

        // Banks. Several publish on .com, which is exactly the case a geo tag misses.
        Entry(Category.BANK, "bankmellat.ir"),
        Entry(Category.BANK, "bmi.ir"),
        Entry(Category.BANK, "banksepah.ir"),
        Entry(Category.BANK, "enbank.ir"),
        Entry(Category.BANK, "tejaratbank.ir"),
        Entry(Category.BANK, "refah-bank.ir"),
        Entry(Category.BANK, "parsian-bank.ir"),
        Entry(Category.BANK, "sinabank.ir"),
        Entry(Category.BANK, "maskanbank.ir"),
        Entry(Category.BANK, "postbank.ir"),
        Entry(Category.BANK, "bpi.ir"),
        Entry(Category.BANK, "sb24.com"),
        Entry(Category.BANK, "karafarinbank.com"),
        Entry(Category.BANK, "edbi.ir"),
        Entry(Category.BANK, "bank-maskan.ir"),

        // Government services. A citizen logging in to one of these from a foreign exit address is
        // not only slow, some of them refuse the session outright.
        Entry(Category.GOVERNMENT, "iran.gov.ir"),
        Entry(Category.GOVERNMENT, "president.ir"),
        Entry(Category.GOVERNMENT, "mfa.ir"),
        Entry(Category.GOVERNMENT, "moj.ir"),
        Entry(Category.GOVERNMENT, "police.ir"),
        Entry(Category.GOVERNMENT, "epolice.ir"),
        Entry(Category.GOVERNMENT, "sabteahval.ir"),
        Entry(Category.GOVERNMENT, "tax.gov.ir"),
        Entry(Category.GOVERNMENT, "salamat.gov.ir"),
        Entry(Category.GOVERNMENT, "adliran.ir"),

        // Domestic messengers. Their traffic is the loudest "my Iranian app is proxied" report,
        // because a chat app keeps a long-lived connection and the user sees it stall.
        Entry(Category.MESSENGER, "bale.ir"),
        Entry(Category.MESSENGER, "eitaa.com"),
        Entry(Category.MESSENGER, "rubika.ir"),
        Entry(Category.MESSENGER, "gap.im"),
        Entry(Category.MESSENGER, "igap.net"),
        Entry(Category.MESSENGER, "shad.ir"),
        Entry(Category.MESSENGER, "chatrsan.cloud"),
        Entry(Category.MESSENGER, "soroush-app.ir"),

        // Ride-hailing, food and travel: high-frequency, latency-sensitive and domestic-only.
        Entry(Category.MOBILITY, "snapp.ir"),
        Entry(Category.MOBILITY, "snappfood.ir"),
        Entry(Category.MOBILITY, "snappmarket.com"),
        Entry(Category.MOBILITY, "snapptrip.com"),
        Entry(Category.MOBILITY, "tapsi.ir"),
        Entry(Category.MOBILITY, "alibaba.ir"),
        Entry(Category.MOBILITY, "flightio.com"),
        Entry(Category.MOBILITY, "maxim.ir"),

        // Shopping. The two largest publish on .com, so no .ir rule reaches them.
        Entry(Category.COMMERCE, "digikala.com"),
        Entry(Category.COMMERCE, "digistyle.com"),
        Entry(Category.COMMERCE, "divar.ir"),
        Entry(Category.COMMERCE, "sheypoor.com"),
        Entry(Category.COMMERCE, "basalam.com"),
        Entry(Category.COMMERCE, "torob.com"),
        Entry(Category.COMMERCE, "emalls.ir"),
        Entry(Category.COMMERCE, "bama.ir"),
        Entry(Category.COMMERCE, "okala.com"),
        Entry(Category.COMMERCE, "technolife.ir"),
        Entry(Category.COMMERCE, "mobit.ir"),
        Entry(Category.COMMERCE, "19kala.com"),

        // The two domestic app stores. An update check that leaves the country is slow, metered
        // and sometimes blocked, so these matter more than their size suggests.
        Entry(Category.APP_STORE, "cafebazaar.ir"),
        Entry(Category.APP_STORE, "myket.ir"),
        Entry(Category.APP_STORE, "getapp.ir"),

        // Domestic CDN and hosting: a large share of Iranian sites sit behind one of these, and a
        // proxied CDN hop is the difference between a page that opens and one that times out.
        Entry(Category.CDN, "arvancloud.ir"),
        Entry(Category.CDN, "arvancloud.com"),
        Entry(Category.CDN, "cdnfa.com"),
        Entry(Category.CDN, "parspack.com"),
        Entry(Category.CDN, "abzarcloud.ir"),

        // Operators. Recharging a SIM or reading a balance from a foreign exit is the kind of
        // thing that fails quietly.
        Entry(Category.TELECOM, "irancell.ir"),
        Entry(Category.TELECOM, "mci.ir"),
        Entry(Category.TELECOM, "rightel.ir"),
        Entry(Category.TELECOM, "tci.ir"),
        Entry(Category.TELECOM, "shatel.ir"),
        Entry(Category.TELECOM, "asiatech.ir"),
        Entry(Category.TELECOM, "mobinnet.ir"),
        Entry(Category.TELECOM, "parsonline.net"),

        // Media. Video CDNs are the heaviest domestic traffic there is.
        Entry(Category.MEDIA, "aparat.com"),
        Entry(Category.MEDIA, "filimo.com"),
        Entry(Category.MEDIA, "namava.ir"),
        Entry(Category.MEDIA, "telewebion.com"),
        Entry(Category.MEDIA, "varzesh3.com"),
        Entry(Category.MEDIA, "irna.ir"),
        Entry(Category.MEDIA, "isna.ir"),
        Entry(Category.MEDIA, "farsnews.ir"),
        Entry(Category.MEDIA, "mehrnews.com"),
        Entry(Category.MEDIA, "tabnak.ir"),
        Entry(Category.MEDIA, "khabaronline.ir"),
        Entry(Category.MEDIA, "entekhab.ir"),
        Entry(Category.MEDIA, "tasnimnews.com"),

        Entry(Category.EDUCATION, "sanzesh.org"),
        Entry(Category.EDUCATION, "aut.ac.ir"),
        Entry(Category.EDUCATION, "ut.ac.ir"),
        Entry(Category.EDUCATION, "sharif.edu"),
        Entry(Category.EDUCATION, "iau.ir"),

        Entry(Category.HEALTH, "tamin.ir"),
        Entry(Category.HEALTH, "ihio.gov.ir"),

        // The stock exchange and its disclosure portal: session-bound, domestic, and unusable
        // through a foreign exit.
        Entry(Category.MARKET, "tsetmc.com"),
        Entry(Category.MARKET, "codal.ir"),
        Entry(Category.MARKET, "tse.ir")
    )

    /**
     * Brand stems for [GeoPrecision.MAXIMUM].
     *
     * A domestic service is not always on the domain it is known by: `digikala` runs
     * `digikala.net` and `digikalajet.com`, `snapp` runs `snapp.site`, `arvan` runs
     * `arvanstorage.com`. A keyword rule catches the family instead of the host, which is the
     * difference between a routing layer that knows a country and one that knows a list. It is a
     * substring match on every new connection, so it is the strongest level's cost.
     */
    val KEYWORDS: List<String> = listOf(
        // No three-letter stem: a keyword is a substring of every host the device connects to,
        // so a short one is a leak that looks like a rule. MCI is covered by its own `mci.ir`
        // entry above, which is the precise form of the same fact.
        "snapp", "tapsi", "digikala", "divar", "basalam", "cafebazaar", "myket", "irancell",
        "rightel", "shatel", "mobinnet", "asiatech", "arvan", "aparat", "filimo",
        "namava", "telewebion", "varzesh3", "bale", "eitaa", "rubika", "igap", "shaparak",
        "tsetmc", "codal", "sanzesh", "tamin", "irna", "isna", "farsnews", "mehrnews",
        "tabnak", "tasnimnews", "alibaba", "flightio", "okala", "torob", "emalls", "sheypoor",
        "mobit", "technolife", "parspack", "zarinpal", "idpay", "payping", "nextpay"
    )

    /** The entries a precision level emits: STANDARD is the honest "only what I configured". */
    fun entries(precision: GeoPrecision): List<Entry> = when (precision) {
        GeoPrecision.STANDARD -> emptyList()
        GeoPrecision.ENHANCED, GeoPrecision.MAXIMUM -> ENTRIES
    }

    /** The domain suffixes a precision level emits. */
    fun suffixes(precision: GeoPrecision): List<String> = when (precision) {
        GeoPrecision.STANDARD -> emptyList()
        GeoPrecision.ENHANCED, GeoPrecision.MAXIMUM -> listOf(TLD_SUFFIX)
    }

    /** The keyword stems a precision level emits. */
    fun keywords(precision: GeoPrecision): List<String> = when (precision) {
        GeoPrecision.STANDARD, GeoPrecision.ENHANCED -> emptyList()
        GeoPrecision.MAXIMUM -> KEYWORDS
    }

    /** Every curated domain, deduplicated and lower-cased, for the writers and the report. */
    fun domains(precision: GeoPrecision): List<String> =
        entries(precision).map { it.domain.trim().lowercase() }.filter { it.isNotBlank() }.distinct()

    /** Per-category counts, for the settings line that has to justify the dial. */
    fun categoryCounts(precision: GeoPrecision): List<Pair<Category, Int>> =
        entries(precision)
            .groupingBy { it.category }
            .eachCount()
            .entries
            .map { it.key to it.value }
            .sortedByDescending { it.second }

    /** "118 domains • 46 keywords • 1 TLD" — the number behind the dial. */
    fun summary(precision: GeoPrecision): String = when (precision) {
        GeoPrecision.STANDARD -> "Geo tags only"
        else -> buildList {
            add("${domains(precision).size} domains")
            val keys = keywords(precision).size
            if (keys > 0) add("$keys keywords")
            add("${suffixes(precision).size} TLD")
        }.joinToString(" • ")
    }

    // -------------------------------------------------------------------------------------------
    // Offline matcher — the simulator, Bug Finder and the tests all read this one answer
    // -------------------------------------------------------------------------------------------

    /**
     * Does [host] belong to a domestic destination this pack names?
     *
     * The three shapes are Xray's own: the TLD as a root domain, each curated domain as a root
     * domain (host equals it or ends with `.` + it), and — at [GeoPrecision.MAXIMUM] only — the
     * brand keyword as a substring. Returns false rather than throwing for nonsense input,
     * because a matcher that can be crashed by a hostname is a matcher that can be crashed by the
     * internet.
     */
    fun matches(rawHost: String, precision: GeoPrecision): Boolean {
        val host = rawHost.trim().trimEnd('.').lowercase()
        if (host.isEmpty() || precision == GeoPrecision.STANDARD) return false
        if (host == TLD_SUFFIX || host.endsWith(".$TLD_SUFFIX")) return true
        for (domain in domains(precision)) {
            if (host == domain || host.endsWith(".$domain")) return true
        }
        for (keyword in keywords(precision)) {
            if (keyword.length >= 4 && host.contains(keyword)) return true
        }
        return false
    }

    /** Which category answered, or null: the provenance line of the route simulator. */
    fun categoryOf(rawHost: String, precision: GeoPrecision): Category? {
        val host = rawHost.trim().trimEnd('.').lowercase()
        if (host.isEmpty() || precision == GeoPrecision.STANDARD) return null
        return entries(precision).firstOrNull { entry ->
            host == entry.domain || host.endsWith("." + entry.domain)
        }?.category
    }

    /** A sanity rule the tests pin: no entry may be a bare TLD or an empty string. */
    fun isWellFormed(): Boolean = ENTRIES.all { entry ->
        val domain = entry.domain.trim().lowercase()
        domain.length > 3 && domain == entry.domain && domain != TLD_SUFFIX && domain.contains('.')
    }
}
