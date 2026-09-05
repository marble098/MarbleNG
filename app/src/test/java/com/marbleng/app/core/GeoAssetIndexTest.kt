package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

class GeoAssetIndexTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun resetIndex() {
        // The index is a process-wide singleton; every test must scan its own fixture cleanly.
        GeoAssetIndex.resetForTests()
    }

    // ---------------------------------------------------------------------------------------
    // Hand-encoded protobuf fixtures — the exact wire format Xray's geo files use.
    // ---------------------------------------------------------------------------------------

    private fun lenField(field: Int, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write((field shl 3) or 2)
        // varint length (all fixtures stay below 128 bytes)
        require(payload.size < 128)
        out.write(payload.size)
        out.write(payload)
        return out.toByteArray()
    }

    private fun varintField(field: Int, value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        out.write((field shl 3) or 0)
        var v = value
        while (true) {
            if (v and 0x7F.inv() == 0L) {
                out.write(v.toInt())
                break
            }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        return out.toByteArray()
    }

    private fun domain(type: Long, value: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(varintField(1, type))
        out.write(lenField(2, value.toByteArray()))
        return out.toByteArray()
    }

    private fun geoSite(code: String, domains: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(lenField(1, code.toByteArray()))
        domains.forEach { out.write(lenField(2, it)) }
        return out.toByteArray()
    }

    private fun geositeFile(vararg entries: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        entries.forEach { out.write(lenField(1, it)) }
        return out.toByteArray()
    }

    private fun geoip(country: String, cidrs: List<Pair<ByteArray, Int>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(lenField(1, country.toByteArray()))
        cidrs.forEach { (ip, prefix) ->
            val cidr = ByteArrayOutputStream()
            cidr.write(lenField(1, ip))
            cidr.write(varintField(2, prefix.toLong()))
            out.write(lenField(2, cidr.toByteArray()))
        }
        return out.toByteArray()
    }

    private fun geoipFile(vararg entries: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        entries.forEach { out.write(lenField(1, it)) }
        return out.toByteArray()
    }

    // 4-byte little/big-endian is irrelevant for containment — the scanner only counts them.
    private val ipA = byteArrayOf(1, 2, 3, 4)
    private val ipB = byteArrayOf(5, 6, 7, 8)

    @Test
    fun `scans tags and counts from both databases`() {
        val site = geositeFile(
            geoSite(
                "google",
                listOf(
                    domain(2, "google.com"),
                    domain(3, "www.google.com"),
                    domain(0, "plain-ignored")
                )
            ),
            geoSite("ads", listOf(domain(2, "doubleclick.net")))
        )
        val ip = geoipFile(
            geoip("ir", listOf(ipA to 8, ipB to 24)),
            geoip("private", listOf(ipA to 32))
        )
        val dir = tmp.newFolder()
        File(dir, "geosite.dat").writeBytes(site)
        File(dir, "geoip.dat").writeBytes(ip)

        val snapshot = GeoAssetIndex.update(dir)
        assertTrue(snapshot != null)
        assertEquals(listOf("google", "ads"), snapshot!!.geosite.map { it.tag })
        assertEquals(3, snapshot.geosite.first { it.tag == "google" }.count)
        assertEquals(1, snapshot.geosite.first { it.tag == "ads" }.count)
        assertEquals(listOf("ir", "private"), snapshot.geoip.map { it.tag })
        assertEquals(2, snapshot.geoip.first { it.tag == "ir" }.count)

        assertTrue(GeoAssetIndex.known(GeoAssetIndex.Kind.GEOSITE, "google") == true)
        assertTrue(GeoAssetIndex.known(GeoAssetIndex.Kind.GEOIP, "ir") == true)
        assertTrue(GeoAssetIndex.known(GeoAssetIndex.Kind.GEOIP, "zz") == false)
        assertTrue(GeoAssetIndex.known(GeoAssetIndex.Kind.GEOSITE, "nope") == false)
    }

    @Test
    fun `membership answers for full and root domain shapes`() {
        val site = geositeFile(
            geoSite(
                "google",
                listOf(
                    domain(2, "google.com"),
                    domain(3, "www.google.com")
                )
            )
        )
        val dir = tmp.newFolder()
        File(dir, "geosite.dat").writeBytes(site)

        GeoAssetIndex.update(dir)
        assertEquals(true, GeoAssetIndex.matchesGeosite("www.google.com"))
        assertEquals(true, GeoAssetIndex.matchesGeosite("play.google.com"))
        assertEquals(true, GeoAssetIndex.matchesGeosite("Google.COM."))
        assertEquals(false, GeoAssetIndex.matchesGeosite("example.com"))
        assertTrue(GeoAssetIndex.canVerifyGeositeMembership())
    }

    @Test
    fun `suggestions rank exact then prefix then category stem`() {
        val site = geositeFile(
            geoSite("google", listOf(domain(2, "google.com"))),
            geoSite("category-google", listOf(domain(2, "gstatic.com"))),
            geoSite("googledrive", listOf(domain(2, "drive.google.com")))
        )
        val dir = tmp.newFolder()
        File(dir, "geosite.dat").writeBytes(site)
        GeoAssetIndex.update(dir)

        val hits = GeoAssetIndex.suggest(GeoAssetIndex.Kind.GEOSITE, "goo")
        assertEquals("google", hits.first().tag)
        assertTrue(hits.contains(GeoAssetIndex.GeoEntry(GeoAssetIndex.Kind.GEOSITE, "category-google", 1)))
    }

    @Test
    fun `a corrupted file degrades to no index instead of crashing`() {
        val site = geositeFile(geoSite("google", listOf(domain(2, "google.com"))))
        val dir = tmp.newFolder()
        File(dir, "geosite.dat").writeBytes(site + byteArrayOf(0x7F, 0x2A, 0x11)) // junk tail
        File(dir, "geoip.dat").writeBytes(byteArrayOf(0xFF, 0xFF.toByte(), 0x01))

        val snapshot = GeoAssetIndex.update(dir)
        // The well-formed entries still landed; the junk ended the scan where it started.
        assertTrue(snapshot != null)
        assertTrue(snapshot!!.geoip.isEmpty())
    }

    @Test
    fun `missing files produce an empty update and builtin catalogs still suggest`() {
        val dir = tmp.newFolder()
        val snapshot = GeoAssetIndex.update(dir)
        assertTrue(snapshot != null)
        assertTrue(snapshot!!.geosite.isEmpty())
        assertTrue(GeoAssetIndex.suggest(GeoAssetIndex.Kind.GEOSITE, "googl").any { it.tag == "google" })
        assertTrue(GeoAssetIndex.suggest(GeoAssetIndex.Kind.GEOIP, "de").any { it.tag == "de" })
        // Membership is honest about what it cannot verify.
        assertNull(GeoAssetIndex.matchesGeosite("google.com"))
        assertFalse(GeoAssetIndex.canVerifyGeositeMembership())
    }
}
