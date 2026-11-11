package com.marbleng.app.core

/** Conservative, offline suggestions, NOT nationality detection or a trust verdict.
 * Exact package ids only: labels, locale and `ir.*` prefixes are not evidence.
 * Android package visibility limits coverage; unknown apps remain in the tunnel.
 */
object IranAppCatalog {
    // Curated exact IDs, reviewed against the hiddify/Android-GFW-Apps direct_ir list
    // and the Sophos Iranian banking-app research (2023). They are suggestions only:
    // package names do not authenticate an app or justify bypass without user review.
    val packages: Set<String> = setOf(
        "cab.snapp.passenger", "com.snappfood", "taxi.tap30.passenger",
        "com.digikala", "ir.divar", "com.sheypoor.mobile", "com.farsitel.bazaar",
        "ir.mservices.market", "ir.nasim", "ir.eitaa.messenger",
        "ir.resaneh1.iptv", "mobi.mmdt.ott", "com.sibche.aspardproject.app",
        "ir.aparat", "com.aparat.filimo", "ir.telewebion", "com.balad",
        "org.rajman.neshan.traffic.tehran.navigator", "ir.mci.ecareapp",
        "com.myirancell", "ir.rightel.myrightel",
        // Banking, payments and Iranian financial services.
        "com.pmb.mobile", "com.isc.bminew", "ir.bmi.bam.nativeweb",
        "mob.banking.android.sepah", "mob.banking.android.pasargad",
        "mob.banking.android.resalat", "mob.banking.android.gardesh",
        "ir.tejaratbank.tata.mobile.android.tejarat", "com.refahbank.dpi.android",
        "com.farazpardazan.enbank", "com.adpdigital.mbs.ayand", "com.isc.bsinew",
        "com.samanpr.blu", "com.mydigipay.app.android",
        "ir.hafhashtad.android780", "ir.sep.sesoot", "market.nobitex", "io.bitpin.app",
        "land.tether.tetherland",
        // Commerce, mobility, media, education and public services.
        "ir.alibaba", "ir.balad", "ir.basalam.app", "ir.basalam.basalam", "ir.torob",
        "ir.emalls.app", "ir.pindo.fletcher", "ir.filmnet.android", "ir.football360.android",
        "net.telewebion", "net.iGap", "mobi.mmdt.ottplus", "ir.medu.shad",
        "ir.eadl.edalatehamrah", "ir.ayantech.ghabzino", "ir.ayantech.pishkhan24",
        "ir.itoll.mycarapp", "ir.mytci"
    )

    fun suggestions(installedPackages: Iterable<String>): Set<String> =
        installedPackages.filterTo(sortedSetOf()) { it in packages }
}
