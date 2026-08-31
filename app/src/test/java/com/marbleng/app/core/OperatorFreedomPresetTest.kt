package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.FreedomPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_OPERATOR_FREEDOM_V91: per-Iranian-operator steel serverless profiles.
 *
 * Profiles are researched from the official XTLS Serverless-for-Iran chain
 * (skip-fragment 1-1/130/560/4 → _chain-skip 2-4/1/4/130 → full-fragment 1-1/1/4/517) plus
 * Xray-core discussion #5969 field notes observed on MCI / Irancell / Shatel (2026-04).
 */
class OperatorFreedomPresetTest {

    private fun isp(asn: Int, name: String, shortName: String = name): IranIsp =
        IranIsp(
            asn = asn,
            name = name,
            persianName = "",
            shortName = shortName,
            kind = IranIspKind.MOBILE,
            severity = FilterSeverity.EXTREME
        )

    @Test
    fun mapsCuratedAsnsToOperatorPresets() {
        assertEquals(FreedomPreset.HAMRAH_AVAL, DpiEvasionPolicy.operatorPresetFor(isp(197207, "Mobile Communication Company of Iran", "MCI")))
        assertEquals(FreedomPreset.IRANCELL, DpiEvasionPolicy.operatorPresetFor(isp(44244, "Iran Cell Service and Communication Company", "Irancell")))
        assertEquals(FreedomPreset.RIGHTEL, DpiEvasionPolicy.operatorPresetFor(isp(57218, "Rightel Telecommunication Company", "Rightel")))
        assertEquals(FreedomPreset.SHATEL, DpiEvasionPolicy.operatorPresetFor(isp(31549, "Shatel", "Shatel")))
    }

    @Test
    fun mapsNameFingerprintsWhenAsnIsUnknown() {
        assertEquals(FreedomPreset.HAMRAH_AVAL, DpiEvasionPolicy.operatorPresetFor(isp(0, "hamrah e aval", "MCI")))
        assertEquals(FreedomPreset.IRANCELL, DpiEvasionPolicy.operatorPresetFor(isp(0, "MTN Irancell", "MTN")))
        assertEquals(FreedomPreset.RIGHTEL, DpiEvasionPolicy.operatorPresetFor(isp(0, "Rightel", "Rightel")))
        assertEquals(FreedomPreset.SHATEL, DpiEvasionPolicy.operatorPresetFor(isp(0, "Arta Shatel", "Shatel")))
        assertNull(DpiEvasionPolicy.operatorPresetFor(isp(0, "Pars Online", "Parsi")))
    }

    @Test
    fun smartAutoMatchesDetectedCarrierToSteelRecipe() {
        val iran = IranModeState(active = true, isp = isp(197207, "MCI", "Hamrah"))
        val settings = AppSettings(freedomPreset = FreedomPreset.SMART_ADAPTIVE, freedomOperatorAuto = true)
        val recipe = DpiEvasionPolicy.freedomRecipe(settings, iran)
        assertEquals(DpiEvasionPolicy.HAMRAH_STEEL, recipe)
        assertTrue(recipe.middleEnabled)
        assertTrue(recipe.rank >= DpiEvasionPolicy.EXTREME_ANTI_DPI.rank)
    }

    @Test
    fun explicitOperatorPresetWinsOverDetection() {
        val iran = IranModeState(active = true, isp = isp(44244, "Irancell"))
        val settings = AppSettings(freedomPreset = FreedomPreset.SHATEL, freedomOperatorAuto = false)
        assertEquals(DpiEvasionPolicy.SHATEL_STEEL, DpiEvasionPolicy.freedomRecipe(settings, iran))
    }

    @Test
    fun profilesEmitTierLadderPlusOperatorSteelRows() {
        val profiles = ServerlessFreedomEngine.profiles(AppSettings(freedomOperatorAuto = true))
        val operators = profiles.filter(ServerlessFreedomEngine::isOperatorProfile)
        assertEquals(4, operators.size)
        assertTrue(profiles.size >= 10)
        operators.forEach { op ->
            val json = op.configJson.lowercase()
            assertTrue("operator profile must emit the 3-stage steel chain: ${op.id}", json.contains("middle-fragment"))
            assertTrue("operator profile must end in full-fragment: ${op.id}", json.contains("full-fragment"))
        }
    }

    @Test
    fun operatorProfileJsonRoundTripsThroughFreedomEngine() {
        val settings = AppSettings(freedomPreset = FreedomPreset.IRANCELL, freedomOperatorAuto = false)
        val operator = ServerlessFreedomEngine.profiles(settings)
            .firstOrNull(ServerlessFreedomEngine::isOperatorProfile)
        assertNotNull(operator)
        assertTrue(operator!!.configJson.contains("middle-fragment"))
        assertTrue(operator.configJson.contains("full-fragment"))
    }
}
