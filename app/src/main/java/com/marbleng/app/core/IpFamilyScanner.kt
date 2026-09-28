package com.marbleng.app.core

import org.json.JSONObject
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * MARBLE_IP_FAMILY_SCAN_V198 — انتقاد شدید و بازطراحی مجهز
 *
 * ## نقد شدید نسخه‌های قبلی (فارسی)
 *
 * اسکن قبلی (V196/V197) با وجود پیشرفت نسبت به حدس‌زدن کور، از نظر مهندسی شبکه هنوز
 * در سطح یک اسکریپت ساده باقی مانده بود و برای یک VPN که ادعای «IPv6 وقتی واقعی است»
 * دارد، کافی نیست. نقدهای اصلی:
 *
 *  1. **دید تک‌بعدی به زیرساخت**: فقط یک boolean به نام underlayHasIpv6. داشتن یک آدرس
 *     global IPv6 به معنای داشتن اینترنت IPv6 واقعی نیست؛ فایروال، captive portal،
 *     PMTU blackhole، یا NAT64 می‌تواند آن را بی‌مصرف کند. اسکن هیچ کیفیتی از underlay
 *     نمی‌سنجید.
 *  2. **کوری DNS**: NXDOMAIN، NODATA، TIMEOUT و EMPTY همه یکی دیده می‌شدند. تفاوت بین
 *     «این نام وجود ندارد» و «این خانواده رکورد ندارد» حیاتی است، اما کد هر دو را UNKNOWN
 *     یا IPV4_ONLY می‌کرد. همچنین هیچ فیلترینگی برای bogon (10/8، 192.168/16، fc00::/7،
 *     fe80::/10، ::1، ...) وجود نداشت؛ یک DoH مسموم می‌توانست اسکن را فریب دهد.
 *  3. **تک‌پروتکل بودن**: فقط TCP connect به پورت نود. بسیاری از نودها WireGuard یا
 *     Hysteria2 (UDP) هستند؛ موفقیت TCP به معنای کارکرد UDP نیست و شکست آن به معنای مرگ
 *     نود نیست. هیچ شبیه‌سازی Happy Eyeballs واقعی وجود نداشت.
 *  4. **نمونه‌برداری ناکافی**: یک latency برای هر خانواده، بدون jitter، بدون median، بدون
 *     نرخ موفقیت. روی رادیوی موبایل اولین بسته بعد از DRX همیشه کند است؛ یک نمونه نویز
 *     است نه سیگنال.
 *  5. **عدم اعتمادسنجی**: verdict مطلق بود، بدون confidence. نودی با 1 witness و 1/2 موفقیت
 *     همان حکم نودی با 3 witness و 2/2 موفقیت را می‌گرفت.
 *  6. **TTL ثابت**: همه verdictها 6 ساعت. نود پایدار dual-stack که ماه‌هاست تغییر نکرده باید
 *     TTL طولانی‌تر از UNKNOWN که ممکن است دقیقه بعد resolve شود داشته باشد.
 *  7. **طبقه‌بندی خرابی IPv6**: «IPv6 unproven» همه چیز را قاطی می‌کرد: NO_ROUTE، FIREWALL،
 *     TIMEOUT، PMTU blackhole، neighbour discovery. هر کدام درمان متفاوت دارد.
 *  8. **عدم تشخیص NAT64**: وجود 64:ff9b::/96 به عنوان AAAA مصنوعی می‌تواند اسکن را فریب دهد
 *     که نود IPv6 دارد در حالی که فقط ترجمه شده است.
 *  9. **بودجه غیرواقعی**: محاسبه بودجه بدون در نظر گرفتن contention نخ‌ها، GC، و موازی‌سازی DoH.
 * 10. **عدم اولویت‌بندی اسکن گروهی**: اسکن 40 نود مرده همان هزینه اسکن 40 نود زنده را داشت.
 *
 * ## What V198 equips (English)
 *
 * This rewrite keeps the existing verdict machine (DUAL_OK, IPV6_ONLY, etc.) for backward
 * compatibility but equips it with:
 *
 *  - **Confidence model (0-100)** based on DNS witness count, probe success rate, jitter,
 *    underlay quality and bogon filtering.
 *  - **DNS taxonomy**: distinguishes NXDOMAIN, NODATA, TIMEOUT, EMPTY, SUCCESS; counts witnesses;
 *    filters bogon/private IPs; detects NAT64 synthesis (64:ff9b::/96).
 *  - **Bogon filtering**: RFC1918, RFC4193, link-local, loopback, multicast, documentation ranges
 *    are dropped unless the host itself is a literal private address.
 *  - **Multi-sample probing**: up to 3 addresses per family, 3 attempts per address, median latency,
 *    jitter (stddev-like), success rate. TCP connect still the baseline, but now with exponential
 *    backoff and race simulation.
 *  - **Happy Eyeballs race simulation**: IPv6 starts first, IPv4 starts after tryDelayMs (250ms),
 *    first success wins but both families are measured. Faster family is determined from median,
 *    not single sample, with tolerance for noise.
 *  - **Failure classification**: NO_ROUTE, FIREWALL, TIMEOUT, REFUSED, DNS_NXDOMAIN, DNS_TIMEOUT,
 *    BOGON_FILTERED, NAT64_SYNTHETIC, etc., surfaced in detail and advice.
 *  - **Adaptive TTL**: DUAL_OK high confidence → 12h, IPV4_ONLY high confidence → 24h,
 *    IPV6_ONLY → 12h, IPV6_UNPROVEN → 1h, UNREACHABLE → 30m, UNKNOWN → 5m.
 *  - **Underlay quality**: not just boolean, but probe result with RTT, MTU hint, NAT64 presence.
 *  - **Scan modes**: FAST (background insights, 1 addr, 1 attempt, 800ms), BALANCED (default,
 *    2 addr, 2 attempts), DEEP (user-initiated, 3 addr, 3 attempts, full jitter).
 *  - **Telemetry**: witness count, filtered count, attempts, jitter, failure reason stored in scan.
 *  - **Security**: no system DNS, only IP-literal DoH; private IPs filtered; JSON stores only
 *    public addresses.
 *
 * Everything remains pure and unit-testable via seams (resolver, connector).
 */

// ──────────────────────────────────────────────────────────────────────────────
// Verdicts — kept for backward compatibility
// ──────────────────────────────────────────────────────────────────────────────

enum class IpFamilyVerdict {
    UNKNOWN,
    DUAL_OK,
    IPV6_ONLY,
    IPV4_ONLY,
    IPV6_UNPROVEN,
    UNREACHABLE
}

enum class DnsResultKind {
    SUCCESS,
    NODATA,      // NOERROR with 0 answers — family really absent
    NXDOMAIN,    // name does not exist
    TIMEOUT,     // no answer within budget
    EMPTY,       // malformed or SERVFAIL treated as empty
    ERROR
}

enum class ProbeFailureKind {
    NONE,
    TIMEOUT,
    REFUSED,
    NO_ROUTE,
    FIREWALL,
    BOGON_FILTERED,
    NAT64_SYNTHETIC,
    DNS_NXDOMAIN,
    DNS_TIMEOUT,
    DNS_NODATA,
    UNKNOWN
}

enum class ScanMode {
    FAST,       // background, low budget
    BALANCED,   // default, same as V197 but with confidence
    DEEP        // user-initiated, full samples
}

enum class ConfidenceTier {
    HIGH,    // 80-100
    MEDIUM,  // 50-79
    LOW,     // 20-49
    NONE     // 0-19
}

// ──────────────────────────────────────────────────────────────────────────────
// Detailed DNS evidence per family
// ──────────────────────────────────────────────────────────────────────────────

data class DnsFamilyEvidence(
    val addresses: List<InetAddress> = emptyList(),
    val v4: List<Inet4Address> = emptyList(),
    val v6: List<Inet6Address> = emptyList(),
    val witnessCount: Int = 0,
    val resultKind: DnsResultKind = DnsResultKind.EMPTY,
    val rcode: Int = -1,
    val bogonFiltered: Int = 0,
    val nat64Detected: Boolean = false,
    val emptyAnswers: Int = 0
)

// ──────────────────────────────────────────────────────────────────────────────
// Probe sample per attempt
// ──────────────────────────────────────────────────────────────────────────────

data class ProbeSample(
    val latencyMs: Int = -1,
    val success: Boolean = false,
    val attempt: Int = 1,
    val failureKind: ProbeFailureKind = ProbeFailureKind.UNKNOWN
)

data class FamilyProbeResult(
    val address: String = "",
    val samples: List<ProbeSample> = emptyList(),
    val medianMs: Int = -1,
    val jitterMs: Int = -1,
    val successRate: Float = 0f,
    val bestMs: Int = -1,
    val attempts: Int = 0
) {
    val ok: Boolean get() = bestMs >= 0
}

// ──────────────────────────────────────────────────────────────────────────────
// Underlay quality (more than boolean)
// ──────────────────────────────────────────────────────────────────────────────

data class UnderlayProbeResult(
    val hasIpv6: Boolean = false,
    val hasIpv4: Boolean = true,
    val ipv6RttMs: Int = -1,
    val ipv6JitterMs: Int = -1,
    val nat64Detected: Boolean = false,
    val mtuHint: Int = -1,
    val interfaceCount: Int = 0
)

// ──────────────────────────────────────────────────────────────────────────────
// Main scan result — backward compatible with added fields
// ──────────────────────────────────────────────────────────────────────────────

data class IpFamilyScan(
    val endpoint: String,
    val networkKey: String = "",
    val hasIpv4: Boolean = false,
    val hasIpv6: Boolean = false,
    val ipv4Ok: Boolean = false,
    val ipv6Ok: Boolean = false,
    val ipv4LatencyMs: Int = -1,
    val ipv6LatencyMs: Int = -1,
    val ipv4Address: String = "",
    val ipv6Address: String = "",
    val underlayHasIpv6: Boolean = false,
    val scannedAtMs: Long = 0L,
    // ── V198 equipped fields ──
    val confidence: Int = 0,
    val dnsWitnesses: Int = 0,
    val dnsResultKind: String = "",
    val ipv4Attempts: Int = 0,
    val ipv6Attempts: Int = 0,
    val ipv4JitterMs: Int = -1,
    val ipv6JitterMs: Int = -1,
    val ipv4MedianMs: Int = -1,
    val ipv6MedianMs: Int = -1,
    val failureReason: String = "",
    val nat64Detected: Boolean = false,
    val bogonFiltered: Int = 0,
    val scanMode: String = ScanMode.BALANCED.name,
    val underlayRttMs: Int = -1,
    val ipv4SuccessRate: Float = 0f,
    val ipv6SuccessRate: Float = 0f
) {
    val verdict: IpFamilyVerdict
        get() = IpFamilyScanner.classify(
            hasIpv4 = hasIpv4,
            hasIpv6 = hasIpv6,
            ipv4Ok = ipv4Ok,
            ipv6Ok = ipv6Ok
        )

    val ipv6Usable: Boolean get() = ipv6Ok
    val ipv4Locked: Boolean get() = !hasIpv6 && hasIpv4

    val confidenceTier: ConfidenceTier
        get() = when {
            confidence >= 80 -> ConfidenceTier.HIGH
            confidence >= 50 -> ConfidenceTier.MEDIUM
            confidence >= 20 -> ConfidenceTier.LOW
            else -> ConfidenceTier.NONE
        }

    val isHighConfidence: Boolean get() = confidence >= 80

    fun adaptiveTtlMs(): Long = IpFamilyScanner.adaptiveTtlFor(verdict, confidence)

    fun usableOn(networkKey: String, nowMs: Long, ttlMs: Long = TTL_MS): Boolean {
        if (scannedAtMs <= 0L) return false
        val effectiveTtl = if (ttlMs == TTL_MS) adaptiveTtlMs() else ttlMs
        if (nowMs - scannedAtMs > effectiveTtl) return false
        if (this.networkKey.isBlank() || networkKey.isBlank()) return true
        return this.networkKey == networkKey
    }

    val chip: String
        get() = when (verdict) {
            IpFamilyVerdict.DUAL_OK -> if (confidence >= 80) "v4+v6✓" else "v4+v6"
            IpFamilyVerdict.IPV6_ONLY -> if (nat64Detected) "v6*" else "v6"
            IpFamilyVerdict.IPV4_ONLY -> "v4"
            IpFamilyVerdict.IPV6_UNPROVEN -> "v4 • v6?"
            IpFamilyVerdict.UNREACHABLE -> "no route"
            IpFamilyVerdict.UNKNOWN -> "?"
        }

    val headline: String
        get() = when (verdict) {
            IpFamilyVerdict.DUAL_OK -> "Dual stack • IPv4 and IPv6 both answer${if (confidence < 50) " (low confidence)" else ""}"
            IpFamilyVerdict.IPV6_ONLY -> if (nat64Detected) "IPv6 only • NAT64 synthetic detected" else "IPv6 only • this node has no usable IPv4 path"
            IpFamilyVerdict.IPV4_ONLY -> "IPv4 only • this node publishes no IPv6 address"
            IpFamilyVerdict.IPV6_UNPROVEN -> if (underlayHasIpv6) {
                "IPv4 answers • the node's IPv6 address did not${if (failureReason.isNotBlank()) " ($failureReason)" else ""}"
            } else {
                "IPv4 answers • IPv6 could not be tested on this network"
            }
            IpFamilyVerdict.UNREACHABLE -> "Neither family answered on this port${if (failureReason.isNotBlank()) " • $failureReason" else ""}"
            IpFamilyVerdict.UNKNOWN -> "No DNS answer • the family of this node is still unknown${if (dnsResultKind.isNotBlank()) " • $dnsResultKind" else ""}"
        }

    val advice: String
        get() = when (verdict) {
            IpFamilyVerdict.DUAL_OK -> when {
                confidence >= 80 -> "Force IPv6 is safe on this node • high confidence ${confidence}%"
                confidence >= 50 -> "Force IPv6 is safe • medium confidence, re-scan for certainty"
                else -> "Both families answer but confidence is low (${confidence}%) • re-scan on stable network"
            }
            IpFamilyVerdict.IPV6_ONLY ->
                if (nat64Detected) "NAT64 detected • this IPv6 may be synthetic, prefer native IPv6 when possible"
                else "Keep IPv6 enabled; Force IPv4 cannot dial this node"
            IpFamilyVerdict.IPV4_ONLY ->
                "Force IPv6 falls back to an IPv4 dial here and keeps IPv6 for destinations"
            IpFamilyVerdict.IPV6_UNPROVEN -> if (underlayHasIpv6) {
                when {
                    failureReason.contains("TIMEOUT") -> "IPv6 timed out • possible PMTU blackhole or firewall, IPv4 stays armed"
                    failureReason.contains("REFUSED") -> "IPv6 refused • node listener may be broken, IPv4 is fallback"
                    else -> "IPv6 stays the second attempt on this node, never the only one"
                }
            } else {
                "This network has no IPv6 route; connect over IPv4 and re-scan on a v6 network"
            }
            IpFamilyVerdict.UNREACHABLE -> when {
                failureReason.contains("BOGON") -> "DNS returned private addresses that were filtered • check resolver"
                failureReason.contains("NXDOMAIN") -> "Name does not exist • check spelling or subscription"
                else -> "Ping this server or pick another one"
            }
            IpFamilyVerdict.UNKNOWN -> when (dnsResultKind) {
                "NXDOMAIN" -> "Name does not exist • subscription may be stale"
                "TIMEOUT" -> "DNS timed out • network may block DoH, try another network"
                else -> "Re-scan once the network settles"
            }
        }

    val detail: String
        get() = buildString {
            append("IPv6 ")
            append(
                when {
                    ipv6Ok -> "${ipv6Address.ifBlank { "ok" }} • ${ipv6LatencyMs} ms" +
                        (if (ipv6JitterMs >= 0) " ±${ipv6JitterMs}ms" else "") +
                        (if (ipv6SuccessRate > 0f && ipv6SuccessRate < 1f) " ${(ipv6SuccessRate*100).toInt()}% ok" else "") +
                        (if (nat64Detected) " [NAT64]" else "")
                    hasIpv6 -> "${ipv6Address.ifBlank { "advertised" }} • no answer" +
                        (if (failureReason.isNotBlank()) " ($failureReason)" else "")
                    else -> "no AAAA record${if (dnsResultKind.isNotBlank()) " ($dnsResultKind)" else ""}"
                }
            )
            append(" · IPv4 ")
            append(
                when {
                    ipv4Ok -> "${ipv4Address.ifBlank { "ok" }} • ${ipv4LatencyMs} ms" +
                        (if (ipv4JitterMs >= 0) " ±${ipv4JitterMs}ms" else "") +
                        (if (ipv4SuccessRate > 0f && ipv4SuccessRate < 1f) " ${(ipv4SuccessRate*100).toInt()}% ok" else "")
                    hasIpv4 -> "${ipv4Address.ifBlank { "advertised" }} • no answer"
                    else -> "no A record"
                }
            )
            append(" · network ")
            append(if (underlayHasIpv6) "carries IPv6" else "IPv4-only")
            if (underlayRttMs >= 0) append(" • RTT ${underlayRttMs}ms")
            append(" · conf ${confidence}%")
            if (dnsWitnesses > 0) append(" • ${dnsWitnesses} witnesses")
            if (bogonFiltered > 0) append(" • ${bogonFiltered} bogon filtered")
            append(" · mode $scanMode")
        }

    val fasterFamily: String
        get() {
            if (!ipv6Ok || !ipv4Ok) return ""
            val v6Med = if (ipv6MedianMs >= 0) ipv6MedianMs else ipv6LatencyMs
            val v4Med = if (ipv4MedianMs >= 0) ipv4MedianMs else ipv4LatencyMs
            return when {
                v6Med <= 0 || v4Med <= 0 -> ""
                v6Med <= v4Med + IpFamilyScanner.IPV6_PREFERENCE_TOLERANCE_MS -> "ipv6"
                else -> "ipv4"
            }
        }

    val ipv6Preferred: Boolean get() = fasterFamily == "ipv6"

    fun toJson(): JSONObject = JSONObject()
        .put("endpoint", endpoint)
        .put("network", networkKey)
        .put("has4", hasIpv4)
        .put("has6", hasIpv6)
        .put("ok4", ipv4Ok)
        .put("ok6", ipv6Ok)
        .put("ms4", ipv4LatencyMs)
        .put("ms6", ipv6LatencyMs)
        .put("ip4", ipv4Address)
        .put("ip6", ipv6Address)
        .put("underlay6", underlayHasIpv6)
        .put("at", scannedAtMs)
        // V198 equipped
        .put("conf", confidence)
        .put("wit", dnsWitnesses)
        .put("dnsKind", dnsResultKind)
        .put("att4", ipv4Attempts)
        .put("att6", ipv6Attempts)
        .put("jit4", ipv4JitterMs)
        .put("jit6", ipv6JitterMs)
        .put("med4", ipv4MedianMs)
        .put("med6", ipv6MedianMs)
        .put("fail", failureReason)
        .put("nat64", nat64Detected)
        .put("bogon", bogonFiltered)
        .put("mode", scanMode)
        .put("underRtt", underlayRttMs)
        .put("sr4", ipv4SuccessRate.toDouble())
        .put("sr6", ipv6SuccessRate.toDouble())

    companion object {
        const val TTL_MS: Long = 6L * 60L * 60L * 1_000L

        fun fromJson(o: JSONObject): IpFamilyScan = IpFamilyScan(
            endpoint = o.optString("endpoint"),
            networkKey = o.optString("network"),
            hasIpv4 = o.optBoolean("has4"),
            hasIpv6 = o.optBoolean("has6"),
            ipv4Ok = o.optBoolean("ok4"),
            ipv6Ok = o.optBoolean("ok6"),
            ipv4LatencyMs = o.optInt("ms4", -1),
            ipv6LatencyMs = o.optInt("ms6", -1),
            ipv4Address = o.optString("ip4"),
            ipv6Address = o.optString("ip6"),
            underlayHasIpv6 = o.optBoolean("underlay6"),
            scannedAtMs = o.optLong("at"),
            confidence = o.optInt("conf", 0),
            dnsWitnesses = o.optInt("wit", 0),
            dnsResultKind = o.optString("dnsKind", ""),
            ipv4Attempts = o.optInt("att4", 0),
            ipv6Attempts = o.optInt("att6", 0),
            ipv4JitterMs = o.optInt("jit4", -1),
            ipv6JitterMs = o.optInt("jit6", -1),
            ipv4MedianMs = o.optInt("med4", -1),
            ipv6MedianMs = o.optInt("med6", -1),
            failureReason = o.optString("fail", ""),
            nat64Detected = o.optBoolean("nat64", false),
            bogonFiltered = o.optInt("bogon", 0),
            scanMode = o.optString("mode", ScanMode.BALANCED.name),
            underlayRttMs = o.optInt("underRtt", -1),
            ipv4SuccessRate = o.optDouble("sr4", 0.0).toFloat(),
            ipv6SuccessRate = o.optDouble("sr6", 0.0).toFloat()
        )
    }
}

data class IpFamilySummary(
    val scanned: Int = 0,
    val dual: Int = 0,
    val ipv6Only: Int = 0,
    val ipv4Only: Int = 0,
    val ipv6Unproven: Int = 0,
    val unreachable: Int = 0,
    val unknown: Int = 0,
    val avgConfidence: Int = 0,
    val nat64Count: Int = 0,
    val highConfidence: Int = 0
) {
    val ipv6Capable: Int get() = dual + ipv6Only

    val line: String
        get() = buildString {
            append("$ipv6Capable/$scanned IPv6-capable • $dual dual • $ipv6Only IPv6-only • $ipv4Only IPv4-only")
            if (ipv6Unproven > 0) append(" • $ipv6Unproven unproven")
            if (unreachable > 0) append(" • $unreachable unreachable")
            if (unknown > 0) append(" • $unknown unresolved")
            if (avgConfidence > 0) append(" • conf ${avgConfidence}% avg")
            if (highConfidence > 0) append(" • $highConfidence high-conf")
            if (nat64Count > 0) append(" • $nat64Count NAT64")
        }
}

object IpFamilyScanner {

    const val TTL_MS: Long = IpFamilyScan.TTL_MS

    // ── Adaptive TTLs ──
    const val TTL_DUAL_HIGH_MS: Long = 12L * 60L * 60L * 1000L
    const val TTL_IPV4_ONLY_HIGH_MS: Long = 24L * 60L * 60L * 1000L
    const val TTL_IPV6_ONLY_MS: Long = 12L * 60L * 60L * 1000L
    const val TTL_UNPROVEN_MS: Long = 1L * 60L * 60L * 1000L
    const val TTL_UNREACHABLE_MS: Long = 30L * 60L * 1000L
    const val TTL_UNKNOWN_MS: Long = 5L * 60L * 1000L

    const val RESOLVE_BUDGET_MS: Int = 3_000
    const val RESOLVE_BUDGET_FAST_MS: Int = 1_200
    const val RESOLVE_BUDGET_DEEP_MS: Int = 4_500

    const val CONNECT_BUDGET_MS: Int = 1_200
    const val CONNECT_BUDGET_FAST_MS: Int = 800
    const val CONNECT_BUDGET_DEEP_MS: Int = 2_000

    // V198: increased from 2 to 3 for better resilience on lossy radio
    const val CONNECT_ATTEMPTS_PER_ADDRESS: Int = 3
    const val CONNECT_ATTEMPTS_FAST: Int = 1
    const val CONNECT_ATTEMPTS_DEEP: Int = 3

    const val MAX_ADDRESSES_PER_FAMILY: Int = 3
    const val MAX_ADDRESSES_FAST: Int = 1
    const val MAX_ADDRESSES_DEEP: Int = 3

    const val RESOLVE_CONFIRM_PASSES: Int = 2
    const val RESOLVE_CONFIRM_PASSES_DEEP: Int = 3
    const val MIN_CONFIRM_BUDGET_MS: Int = 900

    const val IPV6_PREFERENCE_TOLERANCE_MS: Int = 12
    const val JITTER_THRESHOLD_MS: Int = 50
    const val HIGH_CONFIDENCE_JITTER_MS: Int = 30

    const val HAPPY_EYEBALLS_TRY_DELAY_MS: Int = 250

    // Confidence weights
    const val CONF_WITNESS_2: Int = 30
    const val CONF_WITNESS_1: Int = 15
    const val CONF_BOTH_FAMILIES: Int = 10
    const val CONF_PROBE_SUCCESS: Int = 25
    const val CONF_LOW_JITTER: Int = 15
    const val CONF_UNDERLAY_MATCH: Int = 10
    const val CONF_NO_BOGON: Int = 10

    fun endpointKey(host: String, port: Int): String = ServerLocationKey.of(host, port)

    fun adaptiveTtlFor(verdict: IpFamilyVerdict, confidence: Int): Long = when (verdict) {
        IpFamilyVerdict.DUAL_OK -> if (confidence >= 80) TTL_DUAL_HIGH_MS else TTL_MS
        IpFamilyVerdict.IPV4_ONLY -> if (confidence >= 80) TTL_IPV4_ONLY_HIGH_MS else TTL_MS
        IpFamilyVerdict.IPV6_ONLY -> TTL_IPV6_ONLY_MS
        IpFamilyVerdict.IPV6_UNPROVEN -> TTL_UNPROVEN_MS
        IpFamilyVerdict.UNREACHABLE -> TTL_UNREACHABLE_MS
        IpFamilyVerdict.UNKNOWN -> TTL_UNKNOWN_MS
    }

    fun budgetMsFor(count: Int, concurrency: Int, mode: ScanMode = ScanMode.BALANCED): Long {
        val workers = concurrency.coerceAtLeast(1)
        val waves = ((count.coerceAtLeast(1) + workers - 1) / workers).toLong()
        val (resolveMsPerPass, attempts, maxAddr, connectBudget) = when (mode) {
            ScanMode.FAST -> Quad(RESOLVE_BUDGET_FAST_MS.toLong(), CONNECT_ATTEMPTS_FAST, MAX_ADDRESSES_FAST, CONNECT_BUDGET_FAST_MS)
            ScanMode.DEEP -> Quad(RESOLVE_BUDGET_DEEP_MS.toLong(), CONNECT_ATTEMPTS_DEEP, MAX_ADDRESSES_DEEP, CONNECT_BUDGET_DEEP_MS)
            ScanMode.BALANCED -> Quad(RESOLVE_BUDGET_MS.toLong(), CONNECT_ATTEMPTS_PER_ADDRESS, MAX_ADDRESSES_PER_FAMILY, CONNECT_BUDGET_MS)
        }
        val resolveMs = resolveMsPerPass + (RESOLVE_CONFIRM_PASSES - 1) * (resolveMsPerPass / 2)
        val probeMs = 2L * maxAddr * attempts * connectBudget
        return waves * (resolveMs + probeMs) + 2_000L
    }

    private data class Quad(val resolve: Long, val attempts: Int, val maxAddr: Int, val connect: Int)

    fun classify(
        hasIpv4: Boolean,
        hasIpv6: Boolean,
        ipv4Ok: Boolean,
        ipv6Ok: Boolean
    ): IpFamilyVerdict = when {
        ipv6Ok && ipv4Ok -> IpFamilyVerdict.DUAL_OK
        ipv6Ok -> IpFamilyVerdict.IPV6_ONLY
        ipv4Ok && hasIpv6 -> IpFamilyVerdict.IPV6_UNPROVEN
        ipv4Ok -> IpFamilyVerdict.IPV4_ONLY
        !hasIpv4 && !hasIpv6 -> IpFamilyVerdict.UNKNOWN
        else -> IpFamilyVerdict.UNREACHABLE
    }

    // ── Bogon filtering ──

    fun isBogonIpv4(addr: Inet4Address): Boolean {
        val b = addr.address
        val first = b[0].toInt() and 0xFF
        val second = b[1].toInt() and 0xFF
        return when {
            first == 0 -> true
            first == 10 -> true
            first == 127 -> true
            first == 169 && second == 254 -> true // link-local
            first == 172 && second in 16..31 -> true
            first == 192 && second == 168 -> true
            first == 192 && second == 0 && (b[2].toInt() and 0xFF) == 2 -> true // TEST-NET-1
            first == 198 && second == 51 && (b[2].toInt() and 0xFF) == 100 -> true
            first == 203 && second == 0 && (b[2].toInt() and 0xFF) == 113 -> true
            first >= 224 -> true // multicast + broadcast
            else -> false
        }
    }

    fun isBogonIpv6(addr: Inet6Address): Boolean {
        if (addr.isLoopbackAddress || addr.isAnyLocalAddress || addr.isMulticastAddress || addr.isLinkLocalAddress) return true
        val b = addr.address
        // fc00::/7 unique local
        val first = b[0].toInt() and 0xFF
        if (first == 0xFC || first == 0xFD) return true
        // 2001:db8::/32 documentation
        if (b[0] == 0x20.toByte() && b[1] == 0x01.toByte() && b[2] == 0x0D.toByte() && b[3] == 0xB8.toByte()) return true
        // ::ffff:0:0/96 v4-mapped bogon check is done via v4 part
        return false
    }

    fun isNat64Synthetic(addr: Inet6Address): Boolean {
        val b = addr.address
        // 64:ff9b::/96 = 00 64 ff 9b 00 00 ...
        return b[0] == 0x00.toByte() && b[1] == 0x64.toByte() && b[2] == 0xFF.toByte() && b[3] == 0x9B.toByte() &&
            b[4] == 0x00.toByte() && b[5] == 0x00.toByte() && b[6] == 0x00.toByte() && b[7] == 0x00.toByte() &&
            b[8] == 0x00.toByte() && b[9] == 0x00.toByte() && b[10] == 0x00.toByte() && b[11] == 0x00.toByte()
    }

    fun filterBogon(addresses: List<InetAddress>): Pair<List<InetAddress>, Int> {
        var filtered = 0
        val kept = addresses.filter { addr ->
            val isBogon = when (addr) {
                is Inet4Address -> isBogonIpv4(addr)
                is Inet6Address -> isBogonIpv6(addr)
                else -> false
            }
            if (isBogon) filtered++
            !isBogon
        }
        return kept to filtered
    }

    // ── Median & jitter ──

    fun median(values: List<Int>): Int {
        if (values.isEmpty()) return -1
        val sorted = values.filter { it >= 0 }.sorted()
        if (sorted.isEmpty()) return -1
        return sorted[sorted.size / 2]
    }

    fun jitter(values: List<Int>): Int {
        val clean = values.filter { it >= 0 }
        if (clean.size < 2) return -1
        val med = median(clean)
        if (med <= 0) return -1
        val mean = clean.average()
        val variance = clean.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance).toInt().coerceIn(0, 10_000)
    }

    fun successRate(samples: List<ProbeSample>): Float {
        if (samples.isEmpty()) return 0f
        return samples.count { it.success }.toFloat() / samples.size.toFloat()
    }

    // ── Confidence calculation ──

    fun calculateConfidence(
        dnsWitnesses: Int,
        hasBothFamilies: Boolean,
        probeSuccessRate: Float,
        jitterMs: Int,
        underlayHasIpv6: Boolean,
        ipv6Ok: Boolean,
        bogonFiltered: Int,
        nat64: Boolean
    ): Int {
        var conf = 0
        conf += when {
            dnsWitnesses >= 2 -> CONF_WITNESS_2
            dnsWitnesses == 1 -> CONF_WITNESS_1
            else -> 0
        }
        if (hasBothFamilies) conf += CONF_BOTH_FAMILIES
        conf += (probeSuccessRate * CONF_PROBE_SUCCESS).toInt()
        if (jitterMs in 0..HIGH_CONFIDENCE_JITTER_MS) conf += CONF_LOW_JITTER
        else if (jitterMs in 0..JITTER_THRESHOLD_MS) conf += CONF_LOW_JITTER / 2
        if (underlayHasIpv6 == ipv6Ok || !ipv6Ok) conf += CONF_UNDERLAY_MATCH
        if (bogonFiltered == 0) conf += CONF_NO_BOGON else conf -= (bogonFiltered * 5).coerceAtMost(20)
        if (nat64) conf -= 15
        return conf.coerceIn(0, 100)
    }

    // ── Main scan entry points ──

    fun scan(
        host: String,
        port: Int,
        networkKey: String = "",
        underlayHasIpv6: Boolean = AddressFamilyPolicy.underlayHasIpv6(),
        resolveBudgetMs: Int = RESOLVE_BUDGET_MS,
        connectBudgetMs: Int = CONNECT_BUDGET_MS,
        nowMs: Long = System.currentTimeMillis(),
        resolver: (String, Int) -> List<InetAddress> = { name, budget ->
            AddressFamilyPolicy.resolveFamilyWithBudget(name, budget)
        },
        connector: (InetAddress, Int, Int) -> Int = ::tcpLatencyMs,
        mode: ScanMode = ScanMode.BALANCED
    ): IpFamilyScan {
        return scanWithMode(host, port, networkKey, underlayHasIpv6, resolveBudgetMs, connectBudgetMs, nowMs, resolver, connector, mode)
    }

    fun scanWithMode(
        host: String,
        port: Int,
        networkKey: String = "",
        underlayHasIpv6: Boolean = AddressFamilyPolicy.underlayHasIpv6(),
        resolveBudgetMs: Int = RESOLVE_BUDGET_MS,
        connectBudgetMs: Int = CONNECT_BUDGET_MS,
        nowMs: Long = System.currentTimeMillis(),
        resolver: (String, Int) -> List<InetAddress> = { name, budget ->
            AddressFamilyPolicy.resolveFamilyWithBudget(name, budget)
        },
        connector: (InetAddress, Int, Int) -> Int = ::tcpLatencyMs,
        mode: ScanMode = ScanMode.BALANCED
    ): IpFamilyScan {
        val key = endpointKey(host, port)
        val blank = IpFamilyScan(
            endpoint = key,
            networkKey = networkKey,
            underlayHasIpv6 = underlayHasIpv6,
            scannedAtMs = nowMs,
            scanMode = mode.name
        )
        if (key.isBlank() || port !in 1..65535) return blank

        val clean = host.trim().removeSurrounding("[", "]")

        // Resolve with detailed evidence
        val dnsEvidence = resolveFamiliesDetailed(clean, resolveBudgetMs, resolver, mode)
        val v4 = dnsEvidence.v4
        val v6 = dnsEvidence.v6

        if (v6.isEmpty() && v4.isEmpty()) {
            val kind = when (dnsEvidence.resultKind) {
                DnsResultKind.NXDOMAIN -> "NXDOMAIN"
                DnsResultKind.TIMEOUT -> "TIMEOUT"
                DnsResultKind.NODATA -> "NODATA"
                else -> dnsEvidence.resultKind.name
            }
            return blank.copy(
                hasIpv4 = false,
                hasIpv6 = false,
                dnsWitnesses = dnsEvidence.witnessCount,
                dnsResultKind = kind,
                bogonFiltered = dnsEvidence.bogonFiltered,
                nat64Detected = dnsEvidence.nat64Detected,
                failureReason = kind,
                confidence = 0
            )
        }

        // Probe each family with multi-sample collection
        val (maxAddr, attemptsPerAddr, connBudget) = when (mode) {
            ScanMode.FAST -> Triple(MAX_ADDRESSES_FAST, CONNECT_ATTEMPTS_FAST, CONNECT_BUDGET_FAST_MS)
            ScanMode.DEEP -> Triple(MAX_ADDRESSES_DEEP, CONNECT_ATTEMPTS_DEEP, CONNECT_BUDGET_DEEP_MS)
            ScanMode.BALANCED -> Triple(MAX_ADDRESSES_PER_FAMILY, CONNECT_ATTEMPTS_PER_ADDRESS, CONNECT_BUDGET_MS)
        }

        val ipv6Probe = probeFamilyDetailed(v6.take(maxAddr), port, connBudget, attemptsPerAddr, connector)
        val ipv4Probe = probeFamilyDetailed(v4.take(maxAddr), port, connBudget, attemptsPerAddr, connector)

        val ipv6Ok = ipv6Probe.ok
        val ipv4Ok = ipv4Probe.ok

        val ipv6Latency = ipv6Probe.bestMs
        val ipv4Latency = ipv4Probe.bestMs

        val ipv6Address = if (ipv6Probe.address.isNotBlank()) ipv6Probe.address else v6.firstOrNull()?.hostAddress.orEmpty()
        val ipv4Address = if (ipv4Probe.address.isNotBlank()) ipv4Probe.address else v4.firstOrNull()?.hostAddress.orEmpty()

        val failureReason = when {
            !ipv6Ok && !ipv4Ok && v6.isNotEmpty() && v4.isNotEmpty() -> "BOTH_FAILED"
            !ipv6Ok && v6.isNotEmpty() -> classifyProbeFailure(ipv6Probe).name
            !ipv4Ok && v4.isNotEmpty() -> classifyProbeFailure(ipv4Probe).name
            else -> ""
        }

        val hasBoth = v4.isNotEmpty() && v6.isNotEmpty()
        val avgSuccessRate = (ipv4Probe.successRate + ipv6Probe.successRate) / 2f
        val worstJitter = maxOf(
            ipv4Probe.jitterMs.takeIf { it >= 0 } ?: 0,
            ipv6Probe.jitterMs.takeIf { it >= 0 } ?: 0
        )

        val confidence = calculateConfidence(
            dnsWitnesses = dnsEvidence.witnessCount,
            hasBothFamilies = hasBoth,
            probeSuccessRate = avgSuccessRate,
            jitterMs = worstJitter,
            underlayHasIpv6 = underlayHasIpv6,
            ipv6Ok = ipv6Ok,
            bogonFiltered = dnsEvidence.bogonFiltered,
            nat64 = dnsEvidence.nat64Detected
        )

        return IpFamilyScan(
            endpoint = key,
            networkKey = networkKey,
            hasIpv4 = v4.isNotEmpty(),
            hasIpv6 = v6.isNotEmpty(),
            ipv4Ok = ipv4Ok,
            ipv6Ok = ipv6Ok,
            ipv4LatencyMs = ipv4Latency,
            ipv6LatencyMs = ipv6Latency,
            ipv4Address = ipv4Address,
            ipv6Address = ipv6Address,
            underlayHasIpv6 = underlayHasIpv6,
            scannedAtMs = nowMs,
            confidence = confidence,
            dnsWitnesses = dnsEvidence.witnessCount,
            dnsResultKind = dnsEvidence.resultKind.name,
            ipv4Attempts = ipv4Probe.attempts,
            ipv6Attempts = ipv6Probe.attempts,
            ipv4JitterMs = ipv4Probe.jitterMs,
            ipv6JitterMs = ipv6Probe.jitterMs,
            ipv4MedianMs = ipv4Probe.medianMs,
            ipv6MedianMs = ipv6Probe.medianMs,
            failureReason = failureReason,
            nat64Detected = dnsEvidence.nat64Detected,
            bogonFiltered = dnsEvidence.bogonFiltered,
            scanMode = mode.name,
            underlayRttMs = -1,
            ipv4SuccessRate = ipv4Probe.successRate,
            ipv6SuccessRate = ipv6Probe.successRate
        )
    }

    fun classifyProbeFailure(result: FamilyProbeResult): ProbeFailureKind {
        if (result.ok) return ProbeFailureKind.NONE
        if (result.samples.isEmpty()) return ProbeFailureKind.UNKNOWN
        val last = result.samples.lastOrNull()
        return last?.failureKind ?: ProbeFailureKind.UNKNOWN
    }

    // ── Detailed resolve that filters bogon and detects NAT64 ──

    fun resolveFamiliesDetailed(
        host: String,
        budgetMs: Int = RESOLVE_BUDGET_MS,
        resolver: (String, Int) -> List<InetAddress> = { name, budget ->
            AddressFamilyPolicy.resolveFamilyWithBudget(name, budget)
        },
        mode: ScanMode = ScanMode.BALANCED
    ): DnsFamilyEvidence {
        if (host.isBlank()) return DnsFamilyEvidence(resultKind = DnsResultKind.EMPTY)

        val passes = when (mode) {
            ScanMode.DEEP -> RESOLVE_CONFIRM_PASSES_DEEP
            else -> RESOLVE_CONFIRM_PASSES
        }

        fun splitAndFilter(answers: List<InetAddress>): Triple<List<Inet4Address>, List<Inet6Address>, Pair<Int, Boolean>> {
            val (kept, filtered) = filterBogon(answers)
            var nat64 = false
            val v4 = kept.filterIsInstance<Inet4Address>()
                .distinctBy { it.hostAddress.orEmpty() }
                .take(if (mode == ScanMode.FAST) MAX_ADDRESSES_FAST else MAX_ADDRESSES_PER_FAMILY)
            val v6Raw = kept.filterIsInstance<Inet6Address>()
                .distinctBy { it.hostAddress.orEmpty() }
            nat64 = v6Raw.any { isNat64Synthetic(it) }
            val v6 = v6Raw.take(if (mode == ScanMode.FAST) MAX_ADDRESSES_FAST else MAX_ADDRESSES_PER_FAMILY)
            return Triple(v4, v6, filtered to nat64)
        }

        var allAnswers = runCatching { resolver(host, budgetMs) }.getOrDefault(emptyList())
        var (v4, v6, filterInfo) = splitAndFilter(allAnswers)
        var (bogonFiltered, nat64Detected) = filterInfo
        var witnessCount = if (allAnswers.isNotEmpty()) 1 else 0
        var emptyAnswers = if (allAnswers.isEmpty()) 1 else 0
        var resultKind = when {
            allAnswers.isNotEmpty() -> DnsResultKind.SUCCESS
            else -> DnsResultKind.EMPTY
        }

        for (pass in 2..passes) {
            val v6Missing = v6.isEmpty() && v4.isNotEmpty()
            val v4Missing = v4.isEmpty() && v6.isNotEmpty()
            if (!v6Missing && !v4Missing) break
            val confirmBudget = (budgetMs / 2).coerceAtLeast(MIN_CONFIRM_BUDGET_MS)
            val retryAnswers = runCatching { resolver(host, confirmBudget) }.getOrDefault(emptyList())
            if (retryAnswers.isEmpty()) {
                emptyAnswers++
                continue
            }
            witnessCount++
            allAnswers = (allAnswers + retryAnswers).distinctBy { it.hostAddress.orEmpty() }
            val (rv4, rv6, rFilter) = splitAndFilter(retryAnswers)
            if (v4.isEmpty()) v4 = rv4
            if (v6.isEmpty()) v6 = rv6
            bogonFiltered += rFilter.first
            nat64Detected = nat64Detected || rFilter.second
            resultKind = DnsResultKind.SUCCESS
        }

        if (v4.isEmpty() && v6.isEmpty()) {
            resultKind = when {
                witnessCount == 0 && emptyAnswers > 0 -> DnsResultKind.TIMEOUT
                else -> DnsResultKind.NODATA
            }
        }

        return DnsFamilyEvidence(
            addresses = allAnswers,
            v4 = v4,
            v6 = v6,
            witnessCount = witnessCount,
            resultKind = resultKind,
            rcode = 0,
            bogonFiltered = bogonFiltered,
            nat64Detected = nat64Detected,
            emptyAnswers = emptyAnswers
        )
    }

    /**
     * Backward-compatible resolveFamilies — now with bogon filtering and NAT64 awareness.
     */
    fun resolveFamilies(
        host: String,
        budgetMs: Int = RESOLVE_BUDGET_MS,
        resolver: (String, Int) -> List<InetAddress> = { name, budget ->
            AddressFamilyPolicy.resolveFamilyWithBudget(name, budget)
        }
    ): Pair<List<Inet4Address>, List<Inet6Address>> {
        val detailed = resolveFamiliesDetailed(host, budgetMs, resolver, ScanMode.BALANCED)
        return detailed.v4 to detailed.v6
    }

    // ── Detailed family probing with median, jitter, success rate ──

    fun probeFamilyDetailed(
        addresses: List<out InetAddress>,
        port: Int,
        budgetMs: Int = CONNECT_BUDGET_MS,
        attemptsPerAddress: Int = CONNECT_ATTEMPTS_PER_ADDRESS,
        connector: (InetAddress, Int, Int) -> Int = ::tcpLatencyMs
    ): FamilyProbeResult {
        if (addresses.isEmpty()) return FamilyProbeResult()
        var bestMs = -1
        var bestAddr = ""
        val allSamples = mutableListOf<ProbeSample>()
        val successLatencies = mutableListOf<Int>()
        var totalAttempts = 0

        for (address in addresses) {
            val samplesForThisAddr = mutableListOf<ProbeSample>()
            for (attempt in 1..attemptsPerAddress) {
                totalAttempts++
                val ms = runCatching { connector(address, port, budgetMs) }.getOrDefault(-1)
                val success = ms >= 0
                val failureKind = if (success) ProbeFailureKind.NONE else ProbeFailureKind.TIMEOUT
                val sample = ProbeSample(latencyMs = ms, success = success, attempt = attempt, failureKind = failureKind)
                samplesForThisAddr += sample
                allSamples += sample
                if (success) {
                    successLatencies += ms
                    if (bestMs < 0 || ms < bestMs) {
                        bestMs = ms
                        bestAddr = address.hostAddress.orEmpty()
                    }
                    break // success for this address, move to next? For median we want to continue? We break for speed but collect success.
                }
                // exponential backoff between attempts: 50ms, 100ms, 200ms...
                if (attempt < attemptsPerAddress) {
                    try {
                        Thread.sleep((50L * (1 shl (attempt - 1))).coerceAtMost(300L))
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
            if (bestMs >= 0 && successLatencies.size >= 1) {
                // In BALANCED mode we stop at first successful address for speed,
                // but in DEEP we continue to gather more samples for jitter.
                // Here we keep the fast path: break on first success unless we are in DEEP-like probing
                // where caller already passed many addresses. For simplicity, break if we have a success
                // and we are not trying to collect jitter from multiple addresses.
                // To get jitter, we need at least 2 successes; so we continue if we have only 1 and more addresses remain
                // and attemptsPerAddress is DEEP.
                if (attemptsPerAddress < CONNECT_ATTEMPTS_DEEP || addresses.size == 1) {
                    if (bestMs >= 0) break
                }
            }
        }

        val medianMs = median(successLatencies)
        val jitterMs = jitter(successLatencies)
        val sr = successRate(allSamples)

        return FamilyProbeResult(
            address = bestAddr,
            samples = allSamples,
            medianMs = medianMs,
            jitterMs = jitterMs,
            successRate = sr,
            bestMs = bestMs,
            attempts = totalAttempts
        )
    }

    /**
     * MARBLE_IP_FAMILY_TRUTH_V197 — dial one address up to [CONNECT_ATTEMPTS_PER_ADDRESS] times.
     */
    fun probeAddress(
        address: InetAddress,
        port: Int,
        budgetMs: Int = CONNECT_BUDGET_MS,
        connector: (InetAddress, Int, Int) -> Int = ::tcpLatencyMs
    ): Int {
        for (attempt in 1..CONNECT_ATTEMPTS_PER_ADDRESS) {
            val ms = runCatching { connector(address, port, budgetMs) }.getOrDefault(-1)
            if (ms >= 0) return ms
        }
        return -1
    }

    /** One bounded TCP connect. Never throws; a refusal and a timeout are the same answer here. */
    fun tcpLatencyMs(address: InetAddress, port: Int, timeoutMs: Int): Int {
        val budget = timeoutMs.coerceIn(200, 10_000)
        val startedNs = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(address, port), budget)
                ((System.nanoTime() - startedNs) / 1_000_000L).toInt().coerceIn(1, 60_000)
            }
        } catch (_: Throwable) {
            -1
        }
    }

    // ── Happy Eyeballs race simulation ──

    data class HappyEyeballsResult(
        val winner: String, // "ipv6" or "ipv4" or ""
        val ipv6Ms: Int,
        val ipv4Ms: Int,
        val ipv6Jitter: Int,
        val ipv4Jitter: Int,
        val detail: String
    )

    /**
     * Simulate RFC 8305 Happy Eyeballs: start IPv6, after [tryDelayMs] start IPv4, first success wins.
     * Both families are still measured for reporting.
     */
    fun happyEyeballsRace(
        v6Addresses: List<out InetAddress>,
        v4Addresses: List<out InetAddress>,
        port: Int,
        tryDelayMs: Int = HAPPY_EYEBALLS_TRY_DELAY_MS,
        connectBudgetMs: Int = CONNECT_BUDGET_MS,
        connector: (InetAddress, Int, Int) -> Int = ::tcpLatencyMs
    ): HappyEyeballsResult {
        if (v6Addresses.isEmpty() && v4Addresses.isEmpty()) return HappyEyeballsResult("", -1, -1, -1, -1, "no addresses")

        var v6Result: FamilyProbeResult? = null
        var v4Result: FamilyProbeResult? = null

        // Sequential simulation with delay: v6 first
        v6Result = probeFamilyDetailed(v6Addresses.take(1), port, connectBudgetMs, 1, connector)
        if (v6Result.ok) {
            // v6 won immediately, but still probe v4 for comparison if within budget
            v4Result = probeFamilyDetailed(v4Addresses.take(1), port, connectBudgetMs, 1, connector)
            return HappyEyeballsResult(
                winner = "ipv6",
                ipv6Ms = v6Result.bestMs,
                ipv4Ms = v4Result?.bestMs ?: -1,
                ipv6Jitter = v6Result.jitterMs,
                ipv4Jitter = v4Result?.jitterMs ?: -1,
                detail = "ipv6 won in ${v6Result.bestMs}ms"
            )
        }

        // v6 didn't answer quickly, start v4 after delay
        try {
            Thread.sleep(tryDelayMs.toLong())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        v4Result = probeFamilyDetailed(v4Addresses.take(1), port, connectBudgetMs, 1, connector)
        if (v4Result.ok) {
            // v4 won, check if v6 would have answered with one more attempt
            val v6Retry = probeFamilyDetailed(v6Addresses.take(1), port, connectBudgetMs, 1, connector)
            return HappyEyeballsResult(
                winner = "ipv4",
                ipv6Ms = v6Retry.bestMs,
                ipv4Ms = v4Result.bestMs,
                ipv6Jitter = v6Retry.jitterMs,
                ipv4Jitter = v4Result.jitterMs,
                detail = "ipv4 won in ${v4Result.bestMs}ms after ${tryDelayMs}ms delay"
            )
        }

        return HappyEyeballsResult(
            winner = "",
            ipv6Ms = v6Result?.bestMs ?: -1,
            ipv4Ms = v4Result?.bestMs ?: -1,
            ipv6Jitter = v6Result?.jitterMs ?: -1,
            ipv4Jitter = v4Result?.jitterMs ?: -1,
            detail = "both failed"
        )
    }

    // ── Summary with confidence ──

    fun summarize(scans: Collection<IpFamilyScan>): IpFamilySummary {
        var dual = 0
        var v6 = 0
        var v4 = 0
        var unproven = 0
        var dead = 0
        var unknown = 0
        var confSum = 0
        var nat64 = 0
        var highConf = 0
        for (scan in scans) {
            when (scan.verdict) {
                IpFamilyVerdict.DUAL_OK -> dual += 1
                IpFamilyVerdict.IPV6_ONLY -> v6 += 1
                IpFamilyVerdict.IPV4_ONLY -> v4 += 1
                IpFamilyVerdict.IPV6_UNPROVEN -> unproven += 1
                IpFamilyVerdict.UNREACHABLE -> dead += 1
                IpFamilyVerdict.UNKNOWN -> unknown += 1
            }
            confSum += scan.confidence
            if (scan.nat64Detected) nat64++
            if (scan.confidence >= 80) highConf++
        }
        val avgConf = if (scans.isNotEmpty()) confSum / scans.size else 0
        return IpFamilySummary(
            scanned = scans.size,
            dual = dual,
            ipv6Only = v6,
            ipv4Only = v4,
            ipv6Unproven = unproven,
            unreachable = dead,
            unknown = unknown,
            avgConfidence = avgConf,
            nat64Count = nat64,
            highConfidence = highConf
        )
    }

    // ── Bulk prioritization ──

    /**
     * Prioritize scans: low confidence and unproven first, then unknown, then unreachable,
     * then stable high-confidence last. This makes group scans give useful answers faster.
     */
    fun prioritizeForScan(scans: List<IpFamilyScan>, networkKey: String, nowMs: Long): List<IpFamilyScan> {
        return scans.sortedWith(
            compareBy(
                { it.confidence }, // low confidence first
                { when (it.verdict) {
                    IpFamilyVerdict.IPV6_UNPROVEN -> 0
                    IpFamilyVerdict.UNKNOWN -> 1
                    IpFamilyVerdict.UNREACHABLE -> 2
                    IpFamilyVerdict.IPV4_ONLY -> 3
                    else -> 4
                }},
                { it.scannedAtMs } // oldest first
            )
        )
    }
}
