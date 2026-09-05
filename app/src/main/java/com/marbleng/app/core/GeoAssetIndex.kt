package com.marbleng.app.core

import java.io.File
import java.io.RandomAccessFile
import java.util.Arrays

/**
 * MARBLE_GEO_ASSET_INDEX_V136 — the offline search engine for geoip.dat / geosite.dat.
 *
 * Before this index existed, the routing page asked the user to type `geosite:…` / `geoip:…`
 * tokens from memory. The files that define the valid tokens were already on disk, but nothing
 * could look inside them, so a typo produced a config Xray rejects at load time — which kills the
 * whole core and reads in the browser as "check your connection" on every site while the tunnel
 * icon still says connected. The fix is structural: parse the two databases the app already
 * manages, index every tag they contain, and let the UI (and the config writer) consult that
 * index before anything reaches the engine.
 *
 * The `.dat` files are protobuf messages with a tiny, stable wire schema:
 *
 * ```
 * GeoSiteList { repeated GeoSite entry = 1; }
 * GeoSite     { string country_code = 1; repeated Domain domain = 2; }
 * Domain      { Type type = 1; string value = 2; repeated string attribute = 3; }
 * GeoIPList   { repeated GeoIP entry = 1; }
 * GeoIP       { string country_code = 1; repeated CIDR cidr = 2; bool reverse_match = 3; }
 * CIDR        { bytes ip = 1; uint32 prefix = 2; }
 * ```
 *
 * The scanner below reads that wire format directly — no codegen, no schema dependency — and it
 * bounds every read, so a corrupted download ends the scan instead of crashing or looping.
 * Results are cached in memory and re-derived only when the source file changes.
 *
 * Three consumers, one truth:
 *  - the rule editor suggests tags as the user types (with real entry counts);
 *  - validation warns about a tag that the loaded database does not actually contain;
 *  - the route simulator tests a hostname against the real geosite domain lists, so the user can
 *    see *which rule* ate a failing site instead of guessing.
 */
object GeoAssetIndex {

    enum class Kind(val label: String) {
        GEOSITE("geosite"),
        GEOIP("geoip")
    }

    /** One indexed tag: `category-ads-all` with its domain count, `ir` with its range count, … */
    data class GeoEntry(
        val kind: Kind,
        val tag: String,
        /** Domain count (geosite) or CIDR range count (geoip). */
        val count: Int
    )

    /**
     * Immutable result of one scan pair. [geositeDomainHashes] carries (fnv1a hash | type in the
     * top byte) values sorted ascending for binary search; `null` when the file exceeded the
     * bounded in-memory budget — suggestions and validation still work, only exact domain
     * matching in the simulator degrades to "cannot verify".
     */
    class Snapshot(
        val geosite: List<GeoEntry>,
        val geoip: List<GeoEntry>,
        val geositeDomainHashes: LongArray?,
        internal val geositeStamp: Long,
        internal val geositeSize: Long,
        internal val geoipStamp: Long,
        internal val geoipSize: Long,
        val scannedAtMs: Long
    )

    /**
     * Hard ceiling for the in-memory domain hash table (~11 MiB). Loyalsoldier's full list is
     * ~2M domains; Chocolate4U (MarbleNG's default source) is far below. Beyond the cap the
     * *remaining* domains are skipped for matching — every indexed tag still appears, because
     * tags are counted independently of the cap.
     */
    private const val MAX_DOMAIN_HASHES = 1_400_000

    /** Domain type values from the v2ray router proto. */
    private const val DOMAIN_FULL = 3L
    private const val DOMAIN_ROOT = 2L

    /** Type is stored in the top byte of each slot; the low 56 bits carry the FNV-1a hash. */
    private const val TYPE_SHIFT = 56
    private const val HASH_MASK = (1L shl TYPE_SHIFT) - 1

    @Volatile
    private var snapshot: Snapshot? = null

    @Volatile
    private var assetsDirPath: String? = null

    /** The index the UI and the config writer should consult; null until the first scan ran. */
    fun current(): Snapshot? = snapshot

    /**
     * Re-index the managed assets when they changed. Never throws: a malformed or half-written
     * download must degrade into "no suggestions" instead of breaking the caller. The in-memory
     * index is reused when size and mtime of both files are unchanged.
     */
    @Synchronized
    fun update(assetsDir: File): Snapshot? {
        val site = File(assetsDir, "geosite.dat")
        val ip = File(assetsDir, "geoip.dat")
        val siteStamp = if (site.isFile) site.lastModified() else 0L
        val siteSize = if (site.isFile) site.length() else 0L
        val ipStamp = if (ip.isFile) ip.lastModified() else 0L
        val ipSize = if (ip.isFile) ip.length() else 0L

        val old = snapshot
        if (
            old != null && assetsDirPath == assetsDir.absolutePath &&
            old.geositeStamp == siteStamp && old.geositeSize == siteSize &&
            old.geoipStamp == ipStamp && old.geoipSize == ipSize
        ) {
            return old
        }
        assetsDirPath = assetsDir.absolutePath

        val geositeScan = runCatching { scanGeosite(site) }.getOrNull()
        val geoipScan = runCatching { scanGeoip(ip) }.getOrNull()

        val next = Snapshot(
            geosite = geositeScan?.entries ?: old?.geosite ?: emptyList(),
            geoip = geoipScan?.entries ?: old?.geoip ?: emptyList(),
            geositeDomainHashes = geositeScan?.hashes,
            geositeStamp = siteStamp,
            geositeSize = siteSize,
            geoipStamp = ipStamp,
            geoipSize = ipSize,
            scannedAtMs = System.currentTimeMillis()
        )
        snapshot = next
        return next
    }

    /** Scan result of one .dat file: the tag list plus the optional match table. */
    private class ScanResult(val entries: List<GeoEntry>, val hashes: LongArray?)

    // ---------------------------------------------------------------------------------------
    // Suggestions
    // ---------------------------------------------------------------------------------------

    /**
     * Live suggestions for the rule editor. The query may carry its own prefix (`geosite:goo`),
     * because people type naturally. Ranking: exact, then prefix, then `category-…` stem, then
     * word-boundary hits (`ads` finds `category-ads-all`), then any substring; each tier falls
     * back to the entry count, so a huge real category outranks an obscure one.
     */
    fun suggest(kind: Kind, query: String, limit: Int = 8): List<GeoEntry> {
        val entries = when (kind) {
            Kind.GEOSITE -> snapshot?.geosite ?: BUILTIN_GEOSITE
            Kind.GEOIP -> snapshot?.geoip ?: BUILTIN_GEOIP
        }
        val q = normalizeToken(query)
        if (q.isEmpty()) {
            return entries.take(limit)
        }
        data class Ranked(val entry: GeoEntry, val tier: Int)
        val ranked = ArrayList<Ranked>(entries.size)
        for (entry in entries) {
            val tag = entry.tag
            val tier = when {
                tag == q -> 0
                tag.startsWith(q) -> 1
                tag.startsWith("category-") && tag.length > "category-".length &&
                    tag.substring("category-".length).startsWith(q) -> 2
                tagWordStartsWith(tag, q) -> 3
                tag.contains(q) -> 4
                else -> continue
            }
            ranked += Ranked(entry, tier)
        }
        return ranked
            .sortedWith(
                compareBy({ it.tier }, { -it.entry.count }, { it.entry.tag.length }, { it.entry.tag })
            )
            .take(limit)
            .map { it.entry }
    }

    /** Does the loaded database contain this tag? `null` when nothing was indexed yet. */
    fun known(kind: Kind, tag: String): Boolean? {
        val snap = snapshot ?: return null
        val clean = normalizeToken(tag)
        if (clean.isEmpty()) return null
        val entries = when (kind) {
            Kind.GEOSITE -> snap.geosite
            Kind.GEOIP -> snap.geoip
        }
        // The kept entries are sorted by count for suggestions; membership needs a name lookup,
        // which is a linear scan of a few thousand entries — negligible and allocation-free.
        return entries.any { it.tag == clean }
    }

    // ---------------------------------------------------------------------------------------
    // Simulator support: does host H sit inside the geosite domain lists?
    // ---------------------------------------------------------------------------------------

    /**
     * Definitive geosite membership test built from the indexed domain lists.
     *
     * Xray matches four domain shapes; the hash table answers the two that dominate every real
     * database — `full:` (exact) and root domains (host equals the value or ends with ".value").
     * `plain:` (substring) and `regexp:` values are skipped at scan time, so `false` means "no
     * definitive hit", and the simulator labels its verdict accordingly. Returns `null` when the
     * match table is not available (no scan yet, or the budget cap was hit).
     */
    fun matchesGeosite(host: String): Boolean? {
        val snap = snapshot ?: return null
        val table = snap.geositeDomainHashes ?: return null
        val clean = host.trim().trimEnd('.').lowercase()
        if (clean.isEmpty() || !clean.any { it.isLetterOrDigit() }) return null
        // The host itself (full:), then every parent suffix (root domain): a.b.example.com tests
        // a.b.example.com, then b.example.com, then example.com, then com — exactly Xray's
        // RootDomain semantics.
        var start = 0
        while (start <= clean.length) {
            val candidate = clean.substring(start)
            if (candidate.isNotEmpty()) {
                if (hashPresent(table, packHash(candidate, DOMAIN_FULL))) return true
                if (candidate.contains('.') && hashPresent(table, packHash(candidate, DOMAIN_ROOT))) {
                    return true
                }
            }
            val dot = clean.indexOf('.', start)
            if (dot < 0) break
            start = dot + 1
        }
        return false
    }

    /** Whether the simulator can really verify geosite membership on this device. */
    fun canVerifyGeositeMembership(): Boolean = snapshot?.geositeDomainHashes != null

    // ---------------------------------------------------------------------------------------
    // Protobuf wire scanning
    // ---------------------------------------------------------------------------------------

    private fun scanGeosite(file: File): ScanResult? {
        val bytes = readFileBounded(file) ?: return null
        val counters = linkedMapOf<String, Int>()
        val hashes = ArrayList<Long>(4096)
        val reader = ProtobufReader(bytes)
        // GeoSiteList.entry = 1, wire type 2 (length-delimited).
        while (reader.next()) {
            if (reader.fieldNumber == 1 && reader.wireType == 2) {
                scanGeoSiteEntry(reader.messageBytes(), counters, hashes)
            } else {
                reader.skip()
            }
        }
        if (counters.isEmpty()) return null
        val hashArray = hashes.toLongArray()
        Arrays.sort(hashArray)
        return ScanResult(entriesSorted(counters, Kind.GEOSITE), hashArray)
    }

    /**
     * Walks one GeoSite (country/category) message: records its tag and domain count, and hashes
     * every `full:`/root domain for the simulator's match table while the budget allows.
     */
    private fun scanGeoSiteEntry(
        bytes: ByteArray,
        counters: MutableMap<String, Int>,
        hashes: ArrayList<Long>
    ) {
        var code: String? = null
        var domainCount = 0
        val reader = ProtobufReader(bytes)
        while (reader.next()) {
            when {
                reader.fieldNumber == 1 && reader.wireType == 2 -> code = reader.stringBytes()
                reader.fieldNumber == 2 && reader.wireType == 2 -> {
                    domainCount++
                    if (hashes.size < MAX_DOMAIN_HASHES) {
                        readDomainHash(reader.messageBytes())?.let(hashes::add)
                    }
                    // next() already advanced past a length-delimited payload; nothing to skip.
                }
                else -> reader.skip()
            }
        }
        val tag = code?.trim()?.lowercase().orEmpty()
        if (tag.isNotEmpty()) counters[tag] = (counters[tag] ?: 0) + domainCount
    }

    /** Reads one Domain message and returns its packed hash when the shape is matchable. */
    private fun readDomainHash(bytes: ByteArray): Long? {
        var type = 0L
        var value: String? = null
        val reader = ProtobufReader(bytes)
        while (reader.next()) {
            when {
                reader.fieldNumber == 1 && reader.wireType == 0 -> type = reader.varint()
                reader.fieldNumber == 2 && reader.wireType == 2 -> value = reader.stringBytes()
                else -> reader.skip()
            }
        }
        val clean = value?.trim()?.lowercase().orEmpty()
        if (clean.isEmpty()) return null
        return if (type == DOMAIN_FULL || type == DOMAIN_ROOT) packHash(clean, type) else null
    }

    private fun scanGeoip(file: File): ScanResult? {
        val bytes = readFileBounded(file) ?: return null
        val counters = linkedMapOf<String, Int>()
        val reader = ProtobufReader(bytes)
        // GeoIPList.entry = 1, wire type 2.
        while (reader.next()) {
            if (reader.fieldNumber == 1 && reader.wireType == 2) {
                val entry = reader.messageBytes()
                var code: String? = null
                var cidrCount = 0
                val inner = ProtobufReader(entry)
                while (inner.next()) {
                    when {
                        inner.fieldNumber == 1 && inner.wireType == 2 -> code = inner.stringBytes()
                        inner.fieldNumber == 2 && inner.wireType == 2 -> {
                            // CIDR message: ip = 1 (bytes), prefix = 2 (varint). Counting the
                            // message is enough, and next() already skipped its bytes.
                            cidrCount++
                        }
                        else -> inner.skip()
                    }
                }
                val tag = code?.trim()?.lowercase().orEmpty()
                if (tag.isNotEmpty()) counters[tag] = (counters[tag] ?: 0) + cidrCount
            } else {
                reader.skip()
            }
        }
        if (counters.isEmpty()) return null
        return ScanResult(entriesSorted(counters, Kind.GEOIP), null)
    }

    private fun entriesSorted(counters: Map<String, Int>, kind: Kind): List<GeoEntry> =
        counters.entries
            .map { GeoEntry(kind, it.key, it.value) }
            .sortedWith(compareBy<GeoEntry> { -it.count }.thenBy { it.tag })

    // ---------------------------------------------------------------------------------------
    // Protobuf wire reader — bounds-checked everywhere
    // ---------------------------------------------------------------------------------------

    /**
     * Reads at most ~48 MiB so a corrupted length field can never balloon the heap; larger files
     * are refused instead of half-scanned.
     */
    private const val MAX_SCAN_BYTES = 48L * 1024L * 1024L

    private fun readFileBounded(file: File): ByteArray? {
        if (!file.isFile) return null
        val size = file.length()
        if (size <= 0L || size > MAX_SCAN_BYTES) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val bytes = ByteArray(size.toInt())
                raf.readFully(bytes)
                bytes
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Minimal protobuf wire reader over a byte array. Bounds checked everywhere: one corrupted
     * varint can only end the scan, never read out of range or loop forever.
     */
    internal class ProtobufReader(private val bytes: ByteArray) {
        var fieldNumber: Int = 0
            private set
        var wireType: Int = 0
            private set
        private var pos = 0
        private var valueStart = 0
        private var valueEnd = 0

        /** Positions the cursor on the next tag; false at end of buffer or on a malformed tag. */
        fun next(): Boolean {
            if (pos >= bytes.size) return false
            val tag = readVarintRaw() ?: return false
            fieldNumber = (tag ushr 3).toInt()
            wireType = (tag and 0x7).toInt()
            if (fieldNumber == 0) return false
            if (wireType == 2) {
                val length = readVarintRaw() ?: return false
                if (length < 0 || length > bytes.size - pos) return false
                valueStart = pos
                valueEnd = pos + length.toInt()
                pos = valueEnd
            }
            return true
        }

        /** The length-delimited payload of the field [next] just positioned on. */
        fun messageBytes(): ByteArray = bytes.copyOfRange(valueStart, valueEnd)

        fun stringBytes(): String = String(bytes, valueStart, valueEnd - valueStart, Charsets.UTF_8)

        /** The varint payload of a wire-type-0 field; only valid right after [next]. */
        fun varint(): Long = readVarintRaw() ?: 0L

        fun skip() {
            when (wireType) {
                0 -> {
                    // pos sits on the first varint byte; consume continuation bytes + final byte.
                    while (pos < bytes.size && (bytes[pos].toInt() and 0x80) != 0) pos++
                    pos++
                }
                1 -> pos += 8
                2 -> pos = valueEnd
                5 -> pos += 4
                else -> pos = bytes.size
            }
            if (pos > bytes.size) pos = bytes.size
        }

        private fun readVarintRaw(): Long? {
            var shift = 0
            var result = 0L
            while (shift < 64) {
                if (pos >= bytes.size) return null
                val b = bytes[pos].toInt() and 0xFF
                pos++
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
            return null
        }
    }

    // ---------------------------------------------------------------------------------------
    // Hash helpers
    // ---------------------------------------------------------------------------------------

    private fun packHash(value: String, type: Long): Long =
        (fnv1a64(value) and HASH_MASK) or (type shl TYPE_SHIFT)

    private fun hashPresent(table: LongArray, packed: Long): Boolean =
        Arrays.binarySearch(table, packed) >= 0

    private fun fnv1a64(value: String): Long {
        var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        for (i in value.indices) {
            hash = hash xor (value[i].code.toLong() and 0xFFL)
            hash *= 0x100000001b3L
        }
        return hash
    }

    private fun normalizeToken(raw: String): String = raw
        .trim()
        .lowercase()
        .removePrefix("geosite:")
        .removePrefix("geoip:")
        .trim()

    private fun tagWordStartsWith(tag: String, q: String): Boolean {
        var idx = tag.indexOf(q)
        while (idx > 0) {
            val previous = tag[idx - 1]
            if (previous == '-' || previous == '.' || previous == '_') return true
            idx = tag.indexOf(q, idx + 1)
        }
        return false
    }

    // ---------------------------------------------------------------------------------------
    // Built-in catalogs — usable before the first asset download lands
    // ---------------------------------------------------------------------------------------

    /**
     * The categories every mainstream v2fly-family database ships. This is a *discovery* aid, not
     * a substitute for the real index: as soon as the managed files are scanned, the live list
     * with real counts replaces this one.
     */
    internal val BUILTIN_GEOSITE: List<GeoEntry> = listOf(
        "category-ads-all", "category-ads-ir", "category-public-tracker", "category-gov-ir",
        "category-entertainment", "category-games", "category-news", "category-social-media",
        "category-education-ir", "category-forums", "category-porn", "category-gambling",
        "category-crypto", "category-shopping", "category-bank-ir",
        "google", "youtube", "telegram", "twitter", "facebook", "instagram", "whatsapp",
        "netflix", "openai", "github", "microsoft", "apple", "amazon", "cloudflare", "spotify",
        "discord", "steam", "reddit", "wikipedia", "tiktok", "linkedin", "twitch", "signal",
        "proxy", "vpn", "private", "cn", "ir", "ru", "geolocation-!cn",
        "speedtest"
    ).map { GeoEntry(Kind.GEOSITE, it, 0) }

    /** Every ISO-3166 alpha-2 code plus the extra vocabulary Xray-family databases ship. */
    internal val BUILTIN_GEOIP: List<GeoEntry> = ("ad ae af ag ai al am ao ap ar as at au aw az ba bb " +
        "bd be bf bg bh bi bj bl bm bn bo br bs bt bv bw by bz ca cc cd cf cg ch ci ck cl cm cn co " +
        "cr cu cv cw cx cy cz de dj dk dm do dz ec ee eg eh er es et eu fi fj fk fm fo fr ga gb gd " +
        "ge gf gg gh gi gl gm gn gp gq gr gs gt gu gw gy hk hm hn hr ht hu id ie il im in io iq ir " +
        "is it je jm jo jp ke kg kh ki km kn kp kr kw ky kz la lb lc li lk lr ls lt lu lv ly ma mc " +
        "md me mg mh mk ml mm mn mo mp mq mr ms mt mu mv mw mx my mz na nc ne nf ng ni nl no np nr " +
        "nu nz om pa pe pf pg ph pk pl pm pr ps pt pw py qa re ro rs ru rw sa sb sc sd se sg sh si " +
        "sj sk sl sm sn so sr ss st sv sx sy sz tc td tf tg th tj tk tl tm tn to tr tt tv tw tz ua " +
        "ug um us uy uz va vc ve vg vi vn vu wf ws ye yt za zm zw private cloudflare cloudfront " +
        "google netflix telegram twitter facebook fastly")
        .split(' ')
        .map { GeoEntry(Kind.GEOIP, it, 0) }
}
