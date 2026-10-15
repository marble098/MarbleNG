package com.marbleng.app.core

import com.marbleng.app.model.GeoPrecision

object IranPrecisionPack {
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

    val TLD_SUFFIX: String = "ir"

    val ENTRIES: List<Entry> = listOf(

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

        Entry(Category.MESSENGER, "bale.ir"),
        Entry(Category.MESSENGER, "eitaa.com"),
        Entry(Category.MESSENGER, "rubika.ir"),
        Entry(Category.MESSENGER, "gap.im"),
        Entry(Category.MESSENGER, "igap.net"),
        Entry(Category.MESSENGER, "shad.ir"),
        Entry(Category.MESSENGER, "chatrsan.cloud"),
        Entry(Category.MESSENGER, "soroush-app.ir"),

        Entry(Category.MOBILITY, "snapp.ir"),
        Entry(Category.MOBILITY, "snappfood.ir"),
        Entry(Category.MOBILITY, "snappmarket.com"),
        Entry(Category.MOBILITY, "snapptrip.com"),
        Entry(Category.MOBILITY, "tapsi.ir"),
        Entry(Category.MOBILITY, "alibaba.ir"),
        Entry(Category.MOBILITY, "flightio.com"),
        Entry(Category.MOBILITY, "maxim.ir"),

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

        Entry(Category.APP_STORE, "cafebazaar.ir"),
        Entry(Category.APP_STORE, "myket.ir"),
        Entry(Category.APP_STORE, "getapp.ir"),

        Entry(Category.CDN, "arvancloud.ir"),
        Entry(Category.CDN, "arvancloud.com"),
        Entry(Category.CDN, "cdnfa.com"),
        Entry(Category.CDN, "parspack.com"),
        Entry(Category.CDN, "abzarcloud.ir"),

        Entry(Category.TELECOM, "irancell.ir"),
        Entry(Category.TELECOM, "mci.ir"),
        Entry(Category.TELECOM, "rightel.ir"),
        Entry(Category.TELECOM, "tci.ir"),
        Entry(Category.TELECOM, "shatel.ir"),
        Entry(Category.TELECOM, "asiatech.ir"),
        Entry(Category.TELECOM, "mobinnet.ir"),
        Entry(Category.TELECOM, "parsonline.net"),

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

        Entry(Category.MARKET, "tsetmc.com"),
        Entry(Category.MARKET, "codal.ir"),
        Entry(Category.MARKET, "tse.ir")
    )

    val KEYWORDS: List<String> = listOf(

        "snapp", "tapsi", "digikala", "divar", "basalam", "cafebazaar", "myket", "irancell",
        "rightel", "shatel", "mobinnet", "asiatech", "arvan", "aparat", "filimo",
        "namava", "telewebion", "varzesh3", "bale", "eitaa", "rubika", "igap", "shaparak",
        "tsetmc", "codal", "sanzesh", "tamin", "irna", "isna", "farsnews", "mehrnews",
        "tabnak", "tasnimnews", "alibaba", "flightio", "okala", "torob", "emalls", "sheypoor",
        "mobit", "technolife", "parspack", "zarinpal", "idpay", "payping", "nextpay"
    )

    fun entries(precision: GeoPrecision): List<Entry> = when (precision) {
        GeoPrecision.STANDARD -> emptyList()
        GeoPrecision.ENHANCED, GeoPrecision.MAXIMUM -> ENTRIES
    }

    fun suffixes(precision: GeoPrecision): List<String> = when (precision) {
        GeoPrecision.STANDARD -> emptyList()
        GeoPrecision.ENHANCED, GeoPrecision.MAXIMUM -> listOf(TLD_SUFFIX)
    }

    fun keywords(precision: GeoPrecision): List<String> = when (precision) {
        GeoPrecision.STANDARD, GeoPrecision.ENHANCED -> emptyList()
        GeoPrecision.MAXIMUM -> KEYWORDS
    }

    fun domains(precision: GeoPrecision): List<String> =
        entries(precision).map { it.domain.trim().lowercase() }.filter { it.isNotBlank() }.distinct()

    fun categoryCounts(precision: GeoPrecision): List<Pair<Category, Int>> =
        entries(precision)
            .groupingBy { it.category }
            .eachCount()
            .entries
            .map { it.key to it.value }
            .sortedByDescending { it.second }

    fun summary(precision: GeoPrecision): String = when (precision) {
        GeoPrecision.STANDARD -> "Geo tags only"
        else -> buildList {
            add("${domains(precision).size} domains")
            val keys = keywords(precision).size
            if (keys > 0) add("$keys keywords")
            add("${suffixes(precision).size} TLD")
        }.joinToString(" • ")
    }

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

    fun categoryOf(rawHost: String, precision: GeoPrecision): Category? {
        val host = rawHost.trim().trimEnd('.').lowercase()
        if (host.isEmpty() || precision == GeoPrecision.STANDARD) return null
        return entries(precision).firstOrNull { entry ->
            host == entry.domain || host.endsWith("." + entry.domain)
        }?.category
    }

    fun isWellFormed(): Boolean = ENTRIES.all { entry ->
        val domain = entry.domain.trim().lowercase()
        domain.length > 3 && domain == entry.domain && domain != TLD_SUFFIX && domain.contains('.')
    }
}
