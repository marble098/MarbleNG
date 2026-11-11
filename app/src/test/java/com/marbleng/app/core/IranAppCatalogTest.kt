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
}
