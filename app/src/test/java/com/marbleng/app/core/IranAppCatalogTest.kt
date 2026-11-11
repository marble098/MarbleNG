package com.marbleng.app.core

import org.junit.Assert.*
import org.junit.Test

class IranAppCatalogTest {
    @Test fun onlyExactKnownInstalledIdsAreSuggested() {
        assertEquals(setOf("ir.divar", "cab.snapp.passenger"), IranAppCatalog.suggestions(listOf(
            "ir.divar", "cab.snapp.passenger", "ir.unknown", "ir.divar.fake",
            "com.example.snapp", "IR.DIVAR", "ir.divar"
        )))
        assertTrue(IranAppCatalog.suggestions(emptyList()).isEmpty())
    }
    @Test fun bankingAndDomesticAppsAreExactReviewSuggestions() {
        val known = listOf("com.pmb.mobile", "ir.bmi.bam.nativeweb", "mob.banking.android.sepah",
            "com.samanpr.blu", "com.mydigipay.app.android", "ir.torob", "net.telewebion")
        assertEquals(known.toSet(), IranAppCatalog.suggestions(known + listOf(
            "com.pmb.mobile.fake", "com.example.bank", "ir.unknown.bank", "net.telewebion.fake"
        )))
    }
}
