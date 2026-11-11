package com.marbleng.app.core

/** Conservative, offline suggestions, NOT nationality detection or a trust verdict.
 * Exact package ids only: labels, locale and `ir.*` prefixes are not evidence.
 * Android package visibility limits coverage; unknown apps remain in the tunnel.
 */
object IranAppCatalog {
    val packages: Set<String> = setOf(
        "cab.snapp.passenger", "com.snappfood", "taxi.tap30.passenger",
        "com.digikala", "ir.divar", "com.sheypoor.mobile", "com.farsitel.bazaar",
        "ir.mservices.market", "ir.nasim", "ir.eitaa.messenger",
        "ir.resaneh1.iptv", "mobi.mmdt.ott", "com.sibche.aspardproject.app",
        "ir.aparat", "com.aparat.filimo", "ir.telewebion", "com.balad",
        "org.rajman.neshan.traffic.tehran.navigator", "ir.mci.ecareapp",
        "com.myirancell", "ir.rightel.myrightel"
    )

    fun suggestions(installedPackages: Iterable<String>): Set<String> =
        installedPackages.filterTo(sortedSetOf()) { it in packages }
}
