package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * MARBLE_SERVER_PLAN_V273 — the Servers page's subscription plan, on the host JVM.
 *
 * The report was two sentences long and both were about a *missing* number: the traffic a
 * subscription had spent and the time it had left. The cause was not one bug but two, one on each
 * side of the screen:
 *
 *  * the data never arrived — a refresh performed over the tunnel discarded the response headers,
 *    and `subscription-userinfo` is a header, so the plan was erased rather than updated;
 *  * the view had nowhere to put it — the plan line was drawn as a sibling of the title inside a
 *    horizontal row, where a `fillMaxWidth` has nothing to fill, so both labels collapsed to zero
 *    width and the header showed nothing at all.
 *
 * [PlanStatus.summarize] is the fix for the second half (decide the strings once, then place them),
 * and the source assertions below pin the first half: both transports read the header now, and the
 * header's own row is the strip, inside the column that owns the title.
 */
class SubscriptionPlanV273Test {

    private val zone = TimeZone.getTimeZone("UTC")
    private val gib = 1_073_741_824L

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long {
        val calendar = Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }
        return calendar.timeInMillis
    }

    private fun sub(
        upload: Long = 0,
        download: Long = 0,
        total: Long = 0,
        expireAt: Long = 0,
        updatedAt: Long = 0
    ) = Subscription(
        id = "s1",
        name = "Provider",
        url = "https://example.test/sub",
        updatedAt = updatedAt,
        uploadBytes = upload,
        downloadBytes = download,
        totalBytes = total,
        expireAt = expireAt
    )

    private fun main(path: String): String =
        File("src/main/java/com/marbleng/app/$path").readText()

    // -------------------------------------------------------------------------------------
    // The numbers
    // -------------------------------------------------------------------------------------

    @Test
    fun `a plan with a quota prints the pair, what is left and the share spent`() {
        val now = at(2026, 11, 5)
        val summary = PlanStatus.summarize(
            subscription = sub(upload = gib, download = 2 * gib, total = 10 * gib),
            nowMs = now,
            zone = zone
        )
        assertTrue(summary.reported)
        assertTrue(summary.hasQuota)
        assertEquals("3.00 GB / 10.00 GB", summary.usedLabel)
        assertEquals("7.00 GB", summary.remainingLabel)
        assertEquals("30%", summary.percentLabel)
        assertEquals(0.3f, summary.fraction, 0.0001f)
        assertEquals(PlanStatus.Tone.CALM, summary.tone)
    }

    @Test
    fun `a plan with an expiry prints the distance in words and the day itself`() {
        val now = at(2026, 11, 5)
        val summary = PlanStatus.summarize(
            subscription = sub(total = 10 * gib, expireAt = now + 6 * PlanStatus.DAY_MS),
            nowMs = now,
            zone = zone
        )
        assertTrue(summary.hasExpiry)
        assertFalse(summary.expired)
        assertEquals("in 6 days", summary.daysLabel)
        assertEquals("2026-11-11", summary.expiryDateLabel)
        // Persian reads the same instant in its own calendar: 1405-08-20.
        val persian = PlanStatus.summarize(
            subscription = sub(total = 10 * gib, expireAt = now + 6 * PlanStatus.DAY_MS),
            nowMs = now,
            persian = true,
            zone = zone
        )
        assertEquals("1405-08-20", persian.expiryDateLabel)
    }

    @Test
    fun `an expired plan says how long ago instead of pretending it is valid`() {
        val now = at(2026, 11, 5)
        val summary = PlanStatus.summarize(
            subscription = sub(total = 10 * gib, expireAt = now - 3 * PlanStatus.DAY_MS),
            nowMs = now,
            zone = zone
        )
        assertTrue(summary.expired)
        assertEquals("3 days ago", summary.daysLabel)
        assertEquals(PlanStatus.Tone.DANGER, summary.tone)
    }

    @Test
    fun `a plan near its ceiling is a warning even with months of validity left`() {
        val now = at(2026, 11, 5)
        val summary = PlanStatus.summarize(
            subscription = sub(
                download = 95 * gib / 10,
                total = 10 * gib,
                expireAt = now + 200 * PlanStatus.DAY_MS
            ),
            nowMs = now,
            zone = zone
        )
        assertEquals(PlanStatus.Tone.DANGER, summary.tone)
        assertFalse(summary.expired)
    }

    // -------------------------------------------------------------------------------------
    // The honest "unknown"
    // -------------------------------------------------------------------------------------

    @Test
    fun `a panel that reported nothing is unknown, not zero`() {
        val summary = PlanStatus.summarize(subscription = sub(), nowMs = at(2026, 11, 5), zone = zone)
        assertFalse("nothing was reported", summary.reported)
        assertFalse(summary.hasQuota)
        assertFalse(summary.hasExpiry)
        assertEquals("", summary.usedLabel)
        assertEquals("", summary.percentLabel)
        // An unreported plan says it is unreported; it does not claim to be perpetual.
        assertEquals(PlanStatus.UNKNOWN_EXPIRY, summary.daysLabel)
        assertEquals("", summary.expiryDateLabel)
        assertEquals(PlanStatus.Tone.UNKNOWN, summary.tone)
        assertFalse("a subscription is not the all-sources header", summary.isSource)
    }

    @Test
    fun `the all-sources header is a source, not a subscription with an empty plan`() {
        val summary = PlanStatus.summarize(subscription = null, nowMs = at(2026, 11, 5), zone = zone)
        assertTrue(summary.isSource)
        assertFalse(summary.reported)
        assertEquals("", summary.usedLabel)
    }

    @Test
    fun `a quota with no expiry is a complete plan on its own`() {
        val summary = PlanStatus.summarize(
            subscription = sub(download = gib, total = 4 * gib),
            nowMs = at(2026, 11, 5),
            zone = zone
        )
        assertTrue(summary.reported)
        assertTrue(summary.hasQuota)
        assertFalse(summary.hasExpiry)
        assertEquals("1.00 GB / 4.00 GB", summary.usedLabel)
        assertEquals("3.00 GB", summary.remainingLabel)
        assertEquals("No expiry", summary.daysLabel)
    }

    // -------------------------------------------------------------------------------------
    // Where it is placed, and where the numbers come from
    // -------------------------------------------------------------------------------------

    @Test
    fun `the plan belongs to the title block and is no longer a sibling of it`() {
        val servers = main("ui/ServersScreen.kt")
        val cards = main("ui/MarblePlanCards.kt")

        // The strip exists and the header draws it.
        assertTrue("ServerPlanStrip(" in servers)
        assertTrue("fun ServerPlanStrip(" in cards)
        // The V260 shape — two labels weight(1f, fill = false) inside a fillMaxWidth row that had
        // no width to fill — is gone from the header.
        assertFalse(
            "the broken inline plan row came back",
            "PlanStatus.quotaPairLabel(plan.usedBytes, plan.totalBytes)" in servers
        )
        // The strip is drawn from the one pure summary, never from a locally composed sentence.
        assertTrue("PlanStatus.summarize(" in servers)
        assertTrue("PlanStatus.summarize(" in cards || "planOf(" in cards)
        // It is inside the same column as the title, after the badge/freshness row.
        val column = servers.indexOf("ServerPlanStrip(")
        val title = servers.indexOf("style = MaterialTheme.typography.titleSmallEmphasized")
        val actionColumn = servers.indexOf("MarbleActionColumn(tag = \"server-group-action-")
        assertTrue("the strip follows the title", title in 0 until column)
        assertTrue("the strip is inside the leading block", column in 0 until actionColumn)
    }

    @Test
    fun `the plan arrives over both transports, headers and all`() {
        val socks = main("core/SocksHttpClient.kt")
        val repo = main("AppRepository.kt")

        // The tunnel path returns headers now, and the response type exists for exactly that.
        assertTrue("data class TextResponse(" in socks)
        assertTrue("fun getTextUrlResponse(" in socks)
        assertTrue("headers = probe.headers" in socks)
        // Both subscription fetches read the plan header off the response.
        assertTrue("SUBSCRIPTION_USERINFO_HEADER" in repo)
        assertEquals(
            "both transports read the header",
            2,
            Regex("SocksHttpClient\\.getTextUrlResponse\\(").findAll(repo).count()
        )
        assertEquals(
            "both transports hand the header to the fetcher",
            2,
            Regex("userInfo = response\\.headers").findAll(repo).count()
        )
        // The body-only shortcut that lost the plan must not come back.
        assertFalse(
            "the body-only tunnel fetch came back",
            "text = SocksHttpClient.getTextUrl(" in repo
        )
    }

    @Test
    fun `the plan plumbed through the repository reaches the store`() {
        val repo = main("AppRepository.kt")
        // A refresh writes all four plan fields, and keeps the previous value when a panel did not
        // send the header, which is what stops a body-only response from zeroing a plan.
        listOf(
            "uploadBytes = meta?.upload ?: current.uploadBytes",
            "downloadBytes = meta?.download ?: current.downloadBytes",
            "totalBytes = meta?.total ?: current.totalBytes",
            "expireAt = meta?.expireAt ?: current.expireAt"
        ).forEach { line ->
            assertTrue("missing plan write: $line", line in repo)
        }
    }
}
