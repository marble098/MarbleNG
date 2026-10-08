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
     * One database file the index should read.
     *
     * MARBLE_MULTI_SOURCE_ROUTING_V212 — the index stopped being "the two canonical files". Every
     * enabled geo source contributes a file of each family, and the union of all of them is what
     * suggestions, validation, provenance and the route simulator answer from. A source the user
     * switched off is simply not in this list, so its tags stop being suggested on the next scan.
     */
    class IndexedFile(
        val file: File,
        val kind: Kind
    )

    /** One file's slice of a snapshot: identity, freshness, tags and the match table. */
    class FileState(
        val name: String,
        val kind: Kind,
        val stamp: Long,
        val size: Long,
        val entries: List<GeoEntry>,
        val hashes: LongArray?,
        /** Absolute identity prevents a same-named file in another directory reusing this slice. */
        val path: String = name,
        /** True only when the complete protobuf file parsed without truncation or invalid wire data. */
        val parsedSuccessfully: Boolean = false
    )

    /**
     * Immutable result of one scan. [geositeDomainHashes] carries (fnv1a hash | type in the
     * top byte) values sorted ascending for binary search; `null` when the file exceeded the
     * bounded in-memory budget — suggestions and validation still work, only exact domain
     * matching in the simulator degrades to "cannot verify".
     *
     * MARBLE_MULTI_SOURCE_ROUTING_V212 — [geosite] and [geoip] are the *union* of every indexed
     * file in that family, so a tag any enabled source maintains is a tag the product knows.
     * [files] and [tagFiles] keep the union honest: they say which database a tag came from, which
     * is the answer the rule editor and the simulator need before they can say anything at all.
     */
    class Snapshot(
        val geosite: List<GeoEntry>,
        val geoip: List<GeoEntry>,
        val geositeDomainHashes: LongArray?,
        internal val geositeStamp: Long,
        internal val geositeSize: Long,
        internal val geoipStamp: Long,
        internal val geoipSize: Long,
        val scannedAtMs: Long,
        val files: List<FileState> = emptyList(),
        val tagFiles: Map<String, List<String>> = emptyMap()
    ) {
        /** The file names that contain [tag] in [kind]'s family, in scan order. */
        fun filesFor(kind: Kind, tag: String): List<String> {
            val clean = tag.trim().lowercase()
            if (clean.isEmpty()) return emptyList()
            return (tagFiles[tagKey(kind, clean)] ?: emptyList()).filter { name ->
                files.any { state ->
                    state.name == name && state.kind == kind && isFreshAndValid(state) &&
                        state.entries.any { it.tag == clean }
                }
            }.distinct()
        }

        /** True only when this exact path is still the successfully parsed file in this snapshot. */
        fun isUsableFile(file: File, kind: Kind): Boolean =
            files.any { state ->
                state.path == file.absolutePath && state.kind == kind && isFreshAndValid(state)
            }

        /** A name-based form for the managed asset directory, used by the config writer. */
        fun isUsableFile(name: String, kind: Kind): Boolean =
            files.any { state -> state.name == name && state.kind == kind && isFreshAndValid(state) }

        /** Complete tag set for one fresh database, or null when its index is absent/stale. */
        fun tagsForFile(name: String, kind: Kind): Set<String>? =
            files.firstOrNull { it.name == name && it.kind == kind && isFreshAndValid(it) }
                ?.entries?.mapTo(linkedSetOf()) { it.tag }

        /** Current live suggestions, excluding stale or partially parsed file slices. */
        fun entriesFor(kind: Kind): List<GeoEntry> {
            val best = linkedMapOf<String, GeoEntry>()
            files.asSequence()
                .filter { it.kind == kind && isFreshAndValid(it) }
                .flatMap { it.entries.asSequence() }
                .forEach { entry ->
                    val previous = best[entry.tag]
                    if (previous == null || entry.count > previous.count) best[entry.tag] = entry
                }
            return best.values.sortedWith(compareBy<GeoEntry> { -it.count }.thenBy { it.tag })
        }

        /** Geosite simulator answers become unknown as soon as any indexed file has changed. */
        fun allFilesFresh(kind: Kind): Boolean =
            files.filter { it.kind == kind }.all(::isFreshAndValid)
    }

    private fun isFreshAndValid(state: FileState): Boolean {
        if (!state.parsedSuccessfully || state.path.isBlank()) return false
        val file = File(state.path)
        return file.isFile && file.lastModified() == state.stamp && file.length() == state.size
    }

    private fun tagKey(kind: Kind, tag: String): String =
        "${kind.name}:${tag.trim().lowercase()}"

    /** Per-file hash budget. Tag membership is still complete when the simulator budget is reached. */
    private const val MAX_DOMAIN_HASHES = 350_000

    /** Global simulator budget across selected sources; over-budget means "unknown", never partial. */
    private const val MAX_MERGED_DOMAIN_HASHES = 700_000

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

    /** Per-file scan cache, keyed by absolute path: an unchanged file is never re-parsed. */
    @Volatile
    private var fileStates: Map<String, FileState> = emptyMap()

    /** The index the UI and the config writer should consult; null until the first scan ran. */
    fun current(): Snapshot? = snapshot

    /** Test isolation only: the index is a process-wide singleton keyed by the assets dir. */
    internal fun resetForTests() {
        snapshot = null
        assetsDirPath = null
        fileStates = emptyMap()
    }

    /** The canonical pair: the primary source's two databases. */
    fun defaultFiles(assetsDir: File): List<IndexedFile> = listOf(
        IndexedFile(File(assetsDir, "geosite.dat"), Kind.GEOSITE),
        IndexedFile(File(assetsDir, "geoip.dat"), Kind.GEOIP)
    )

    /**
     * Re-index the managed assets when they changed. Never throws: a malformed or half-written
     * download must degrade into "no suggestions" instead of breaking the caller. The in-memory
     * index is reused when size and mtime of both files are unchanged.
     */
    @Synchronized
    fun update(assetsDir: File): Snapshot? = update(assetsDir, defaultFiles(assetsDir))

    /**
     * MARBLE_MULTI_SOURCE_ROUTING_V212 — the multi-source form of [update].
     *
     * The cache is per file, so switching one source on re-parses only that source's database and
     * the union is rebuilt from cached slices. A snapshot is reused only when it was built from
     * exactly this set of files and none of them moved: a source added or removed changes the
     * union, so it re-scans, which is the whole point of the set being part of the identity.
     *
     * A parse failure publishes no membership from that file. Stale tags must never be treated as
     * evidence for a token that the current Xray process will load.
     */
    @Synchronized
    fun update(assetsDir: File, files: List<IndexedFile>): Snapshot? {
        val wanted = files.distinctBy { it.file.absolutePath }

        val old = snapshot
        if (old != null && assetsDirPath == assetsDir.absolutePath && old.files.size == wanted.size) {
            val unchanged = wanted.all { indexed ->
                val state = old.files.firstOrNull {
                    it.path == indexed.file.absolutePath && it.kind == indexed.kind
                } ?: return@all false
                state.stamp == stampOf(indexed.file) && state.size == sizeOf(indexed.file)
            }
            if (unchanged) return old
        }
        assetsDirPath = assetsDir.absolutePath

        val previous = fileStates
        val states = ArrayList<FileState>(wanted.size)
        val nextCache = HashMap<String, FileState>(wanted.size)
        for (indexed in wanted) {
            val file = indexed.file
            val key = file.absolutePath
            val stamp = stampOf(file)
            val size = sizeOf(file)
            val cached = previous[key]
            if (cached != null && cached.kind == indexed.kind && cached.stamp == stamp && cached.size == size) {
                states += cached
                nextCache[key] = cached
                continue
            }
            val scan = runCatching {
                if (indexed.kind == Kind.GEOSITE) scanGeosite(file) else scanGeoip(file)
            }.getOrNull()?.takeIf {
                // A concurrent refresh must never publish a slice read across two file versions.
                file.isFile && stampOf(file) == stamp && sizeOf(file) == size
            }
            val state = FileState(
                name = file.name,
                kind = indexed.kind,
                stamp = stamp,
                size = size,
                entries = scan?.entries ?: emptyList(),
                hashes = scan?.hashes,
                path = key,
                parsedSuccessfully = scan != null
            )
            states += state
            nextCache[key] = state
        }
        val retainedDomainHashes = states.asSequence()
            .filter { it.kind == Kind.GEOSITE }
            .sumOf { it.hashes?.size?.toLong() ?: 0L }
        if (retainedDomainHashes > MAX_MERGED_DOMAIN_HASHES) {
            // Above the global cap a merged table would be unusable anyway. Drop all per-file
            // tables too, rather than retaining up to six independent budgets in the process cache.
            for (index in states.indices) {
                val state = states[index]
                if (state.kind == Kind.GEOSITE && state.hashes != null) {
                    val compact = FileState(
                        name = state.name,
                        kind = state.kind,
                        stamp = state.stamp,
                        size = state.size,
                        entries = state.entries,
                        hashes = null,
                        path = state.path,
                        parsedSuccessfully = state.parsedSuccessfully
                    )
                    states[index] = compact
                    nextCache[state.path] = compact
                }
            }
        }
        fileStates = nextCache

        val siteStates = states.filter { it.kind == Kind.GEOSITE }
        val ipStates = states.filter { it.kind == Kind.GEOIP }

        val tagFiles = HashMap<String, List<String>>()
        states.filter { it.parsedSuccessfully }.forEach { state ->
            state.entries.forEach { entry ->
                val key = tagKey(entry.kind, entry.tag)
                tagFiles[key] = (tagFiles[key] ?: emptyList()) + state.name
            }
        }

        val siteState = states.firstOrNull { it.name == "geosite.dat" }
        val ipState = states.firstOrNull { it.name == "geoip.dat" }

        val next = Snapshot(
            geosite = unionEntries(siteStates, Kind.GEOSITE),
            geoip = unionEntries(ipStates, Kind.GEOIP),
            geositeDomainHashes = mergeHashes(siteStates),
            geositeStamp = siteState?.stamp ?: stampOf(File(assetsDir, "geosite.dat")),
            geositeSize = siteState?.size ?: sizeOf(File(assetsDir, "geosite.dat")),
            geoipStamp = ipState?.stamp ?: stampOf(File(assetsDir, "geoip.dat")),
            geoipSize = ipState?.size ?: sizeOf(File(assetsDir, "geoip.dat")),
            scannedAtMs = System.currentTimeMillis(),
            files = states,
            tagFiles = tagFiles
        )
        snapshot = next
        return next
    }

    private fun stampOf(file: File): Long = if (file.isFile) file.lastModified() else 0L

    private fun sizeOf(file: File): Long = if (file.isFile) file.length() else 0L

    /**
     * The union of one family's files: one entry per tag, carrying the largest count any source
     * reported for it. The count is a suggestion-ranking signal, so the largest is the honest one
     * — the tag that a rich source maintains with 40 000 domains is not a 3-domain tag just
     * because a small source also happens to list it.
     */
    private fun unionEntries(states: List<FileState>, kind: Kind): List<GeoEntry> {
        val usable = states.filter(::isFreshAndValid)
        if (usable.isEmpty()) return emptyList()
        if (usable.size == 1) return usable[0].entries
        val best = linkedMapOf<String, Int>()
        usable.forEach { state ->
            state.entries.forEach { entry ->
                val current = best[entry.tag]
                if (current == null || entry.count > current) best[entry.tag] = entry.count
            }
        }
        return entriesSorted(best, kind)
    }

    /** Every source's complete match table, merged within the global in-memory budget. */
    private fun mergeHashes(states: List<FileState>): LongArray? {
        if (states.isEmpty() || states.any { !isFreshAndValid(it) || it.hashes == null }) return null
        val tables = states.mapNotNull { it.hashes }.filter { it.isNotEmpty() }
        if (tables.isEmpty()) return null
        val total = tables.sumOf { it.size }
        // Returning a partial merged table would make a miss look definitive. Above the bounded
        // global budget the simulator must say "unknown" instead of silently losing later sources.
        if (total > MAX_MERGED_DOMAIN_HASHES) return null
        if (tables.size == 1) return tables[0]
        val merged = LongArray(total)
        var at = 0
        tables.forEach { table ->
            System.arraycopy(table, 0, merged, at, table.size)
            at += table.size
        }
        Arrays.sort(merged)
        var write = 0
        var last = 0L
        for (index in merged.indices) {
            val value = merged[index]
            if (index == 0 || value != last) {
                merged[write++] = value
                last = value
            }
        }
        return merged.copyOf(write)
    }

    /** Scan result of one .dat file: the tag list plus the optional match table. */
    private class ScanResult(val entries: List<GeoEntry>, val hashes: LongArray?)

    /** A capped primitive buffer: boxing every domain hash into ArrayList<Long> multiplies peak RAM. */
    private class DomainHashBuffer {
        private var values = LongArray(4_096)
        private var count = 0
        private var overflowed = false

        fun add(value: Long) {
            if (count >= MAX_DOMAIN_HASHES) {
                overflowed = true
                return
            }
            if (count == values.size) {
                values = values.copyOf(minOf(MAX_DOMAIN_HASHES, values.size * 2))
            }
            values[count++] = value
        }

        fun sortedArrayOrNull(): LongArray? {
            if (overflowed) return null
            return values.copyOf(count).also { Arrays.sort(it) }
        }
    }

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
        // An empty scan (missing/corrupt files) falls back to the built-in discovery catalog too:
        // "no database yet" and "database with no entries" must suggest identically.
        val live = snapshot?.entriesFor(kind).orEmpty()
        val entries = when (kind) {
            Kind.GEOSITE -> live.ifEmpty { BUILTIN_GEOSITE }
            Kind.GEOIP -> live.ifEmpty { BUILTIN_GEOIP }
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
        // Membership is file-specific and freshness-sensitive; a cached union from a replaced
        // database is not evidence that the current Xray process can load this tag.
        return snap.filesFor(kind, clean).isNotEmpty()
    }

    /**
     * MARBLE_MULTI_SOURCE_ROUTING_V212 — which indexed databases contain [tag].
     *
     * This is the provenance answer the multi-source routing layer needs: the rule editor says
     * "this tag comes from Chocolate4U Iran and Loyalsoldier" instead of guessing, and the
     * simulator can name the database that matched instead of the anonymous "geosite". Empty when
     * nothing indexed yet, which the caller has to read as "unknown", not as "no".
     */
    fun sourcesFor(kind: Kind, tag: String): List<String> {
        val snap = snapshot ?: return emptyList()
        val clean = normalizeToken(tag)
        if (clean.isEmpty()) return emptyList()
        return snap.filesFor(kind, clean)
    }

    /** The number of successfully parsed, still-current database files behind the union. */
    fun fileCount(kind: Kind): Int = snapshot?.files?.count {
        it.kind == kind && isFreshAndValid(it)
    } ?: 0

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
        if (!snap.allFilesFresh(Kind.GEOSITE)) return null
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
    fun canVerifyGeositeMembership(): Boolean = snapshot?.let {
        it.geositeDomainHashes != null && it.allFilesFresh(Kind.GEOSITE)
    } == true

    // ---------------------------------------------------------------------------------------
    // Protobuf wire scanning
    // ---------------------------------------------------------------------------------------

    private fun scanGeosite(file: File): ScanResult? {
        val bytes = readFileBounded(file) ?: return null
        val counters = linkedMapOf<String, Int>()
        val hashes = DomainHashBuffer()
        val reader = ProtobufReader(bytes)
        // GeoSiteList.entry = 1, wire type 2 (length-delimited).
        while (reader.next()) {
            if (reader.fieldNumber == 1) {
                if (reader.wireType != 2 || !reader.enterMessage()) return null
                val validEntry = scanGeoSiteEntry(reader, counters, hashes)
                if (!validEntry || !reader.leaveMessage()) return null
            } else {
                reader.skip()
            }
        }
        if (!reader.isComplete() || counters.isEmpty()) return null
        return ScanResult(entriesSorted(counters, Kind.GEOSITE), hashes.sortedArrayOrNull())
    }

    /** Walk one GeoSite entry, validating every nested Domain before trusting its tag. */
    private fun scanGeoSiteEntry(
        reader: ProtobufReader,
        counters: MutableMap<String, Int>,
        hashes: DomainHashBuffer
    ): Boolean {
        var code: String? = null
        var domainCount = 0
        while (reader.next()) {
            when (reader.fieldNumber) {
                1 -> {
                    if (reader.wireType != 2) return false
                    code = reader.stringBytes()
                }
                2 -> {
                    if (reader.wireType != 2 || !reader.enterMessage()) return false
                    domainCount++
                    val domain = readDomainHash(reader)
                    val validDomain = reader.leaveMessage()
                    if (domain == null || !validDomain) return false
                    if (domain != UNMATCHABLE_DOMAIN_HASH) hashes.add(domain)
                }
                else -> reader.skip()
            }
        }
        if (!reader.isComplete()) return false
        val tag = code?.trim()?.lowercase().orEmpty()
        if (tag.isNotEmpty()) counters[tag] = (counters[tag] ?: 0) + domainCount
        return true
    }

    private const val UNMATCHABLE_DOMAIN_HASH = Long.MIN_VALUE

    /** Reads a Domain; null means malformed, sentinel means valid but not hash-matchable. */
    private fun readDomainHash(reader: ProtobufReader): Long? {
        var type = 0L
        var value: String? = null
        while (reader.next()) {
            when (reader.fieldNumber) {
                1 -> {
                    if (reader.wireType != 0) return null
                    type = reader.varint()
                }
                2 -> {
                    if (reader.wireType != 2) return null
                    value = reader.stringBytes()
                }
                3 -> {
                    if (reader.wireType != 2 || !reader.enterMessage()) return null
                    val validAttribute = validDomainAttribute(reader)
                    val leftAttribute = reader.leaveMessage()
                    if (!validAttribute || !leftAttribute) return null
                }
                else -> reader.skip()
            }
        }
        if (!reader.isComplete()) return null
        val clean = value?.trim()?.lowercase().orEmpty()
        if (clean.isEmpty() || (type != DOMAIN_FULL && type != DOMAIN_ROOT)) {
            return UNMATCHABLE_DOMAIN_HASH
        }
        return packHash(clean, type)
    }

    /** Validate the nested Attribute message Xray decodes while loading a geosite entry. */
    private fun validDomainAttribute(reader: ProtobufReader): Boolean {
        while (reader.next()) {
            when (reader.fieldNumber) {
                1 -> {
                    if (reader.wireType != 2) return false
                    reader.stringBytes()
                }
                2, 3 -> {
                    if (reader.wireType != 0) return false
                    reader.varint()
                }
                else -> reader.skip()
            }
        }
        return reader.isComplete()
    }

    private fun scanGeoip(file: File): ScanResult? {
        val bytes = readFileBounded(file) ?: return null
        val counters = linkedMapOf<String, Int>()
        val reader = ProtobufReader(bytes)
        // GeoIPList.entry = 1, wire type 2.
        while (reader.next()) {
            if (reader.fieldNumber == 1) {
                if (reader.wireType != 2 || !reader.enterMessage()) return null
                val validEntry = scanGeoIpEntry(reader, counters)
                if (!validEntry || !reader.leaveMessage()) return null
            } else {
                reader.skip()
            }
        }
        if (!reader.isComplete() || counters.isEmpty()) return null
        return ScanResult(entriesSorted(counters, Kind.GEOIP), null)
    }

    private fun scanGeoIpEntry(reader: ProtobufReader, counters: MutableMap<String, Int>): Boolean {
        var code: String? = null
        var cidrCount = 0
        while (reader.next()) {
            when (reader.fieldNumber) {
                1 -> {
                    if (reader.wireType != 2) return false
                    code = reader.stringBytes()
                }
                2 -> {
                    if (reader.wireType != 2 || !reader.enterMessage()) return false
                    val validCidr = validCidr(reader)
                    val leftCidr = reader.leaveMessage()
                    if (!validCidr || !leftCidr) return false
                    cidrCount++
                }
                3 -> {
                    if (reader.wireType != 0) return false
                    reader.varint() // reverse_match is a protobuf bool.
                }
                else -> reader.skip()
            }
        }
        if (!reader.isComplete()) return false
        val tag = code?.trim()?.lowercase().orEmpty()
        if (tag.isNotEmpty()) counters[tag] = (counters[tag] ?: 0) + cidrCount
        return true
    }

    /** Xray's CIDR requires a four- or sixteen-byte IP and a prefix valid for that family. */
    private fun validCidr(reader: ProtobufReader): Boolean {
        var ipSize = 0
        var prefix: Long? = null
        while (reader.next()) {
            when (reader.fieldNumber) {
                1 -> {
                    if (reader.wireType != 2) return false
                    ipSize = reader.valueLength()
                }
                2 -> {
                    if (reader.wireType != 0) return false
                    prefix = reader.varint()
                }
                else -> reader.skip()
            }
        }
        val maxPrefix = when (ipSize) {
            4 -> 32L
            16 -> 128L
            else -> return false
        }
        val prefixValue = prefix ?: return false
        return reader.isComplete() && prefixValue in 0L..maxPrefix
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
        private class ParentFrame {
            var fieldNumber = 0
            var wireType = -1
            var pos = 0
            var limit = 0
            var valueStart = 0
            var valueEnd = 0
            var varintValue = 0L
        }

        var fieldNumber: Int = 0
            private set
        var wireType: Int = -1
            private set
        private var pos = 0
        private var limit = bytes.size
        private var valueStart = 0
        private var valueEnd = 0
        private var varintValue = 0L
        private var malformed = false
        private var depth = 0
        // Known Xray geo messages nest at most three levels. Reusing this tiny stack avoids a
        // ByteArray and reader allocation for every domain/CIDR in multi-megabyte geo databases.
        private val parents = Array(4) { ParentFrame() }

        /** Positions the cursor on the next complete field; malformed input is never end-of-file. */
        fun next(): Boolean {
            if (malformed || pos == limit) return false
            if (pos < 0 || pos > limit) return fail()
            val tag = readVarintRaw() ?: return fail()
            val rawFieldNumber = tag ushr 3
            if (rawFieldNumber !in 1L..536_870_911L) return fail()
            fieldNumber = rawFieldNumber.toInt()
            wireType = (tag and 0x7).toInt()

            when (wireType) {
                0 -> {
                    varintValue = readVarintRaw() ?: return fail()
                    valueStart = pos
                    valueEnd = pos
                }
                1 -> {
                    if (limit - pos < 8) return fail()
                    valueStart = pos
                    valueEnd = pos + 8
                    pos = valueEnd
                }
                2 -> {
                    val length = readVarintRaw() ?: return fail()
                    if (length < 0L || length > (limit - pos).toLong()) return fail()
                    valueStart = pos
                    valueEnd = pos + length.toInt()
                    pos = valueEnd
                }
                5 -> {
                    if (limit - pos < 4) return fail()
                    valueStart = pos
                    valueEnd = pos + 4
                    pos = valueEnd
                }
                else -> return fail()
            }
            return true
        }

        /** Enter the length-delimited field [next] just positioned on, without copying its bytes. */
        fun enterMessage(): Boolean {
            if (malformed || wireType != 2 || depth >= parents.size) return fail()
            val parent = parents[depth++]
            parent.fieldNumber = fieldNumber
            parent.wireType = wireType
            parent.pos = pos
            parent.limit = limit
            parent.valueStart = valueStart
            parent.valueEnd = valueEnd
            parent.varintValue = varintValue

            pos = valueStart
            limit = valueEnd
            fieldNumber = 0
            wireType = -1
            valueStart = pos
            valueEnd = pos
            varintValue = 0L
            return true
        }

        /** Leave a nested message, returning false when any byte in it was malformed. */
        fun leaveMessage(): Boolean {
            if (depth <= 0) return fail()
            val complete = isComplete()
            val parent = parents[--depth]
            fieldNumber = parent.fieldNumber
            wireType = parent.wireType
            pos = parent.pos
            limit = parent.limit
            valueStart = parent.valueStart
            valueEnd = parent.valueEnd
            varintValue = parent.varintValue
            if (!complete) malformed = true
            return complete
        }

        /** True only when the entire current message ended on a valid field boundary. */
        fun isComplete(): Boolean = !malformed && pos == limit

        /** Length of the current length-delimited value; zero for other wire types. */
        fun valueLength(): Int = if (wireType == 2) valueEnd - valueStart else 0

        fun stringBytes(): String {
            if (wireType != 2 || !validUtf8(valueStart, valueEnd)) {
                malformed = true
                return ""
            }
            return String(bytes, valueStart, valueEnd - valueStart, Charsets.UTF_8)
        }

        /** The varint payload of a wire-type-0 field; only valid right after [next]. */
        fun varint(): Long = varintValue

        /** next() validates and consumes every supported wire value before returning. */
        fun skip() = Unit

        private fun validUtf8(start: Int, end: Int): Boolean {
            var index = start
            fun continuation(at: Int): Boolean =
                at < end && (bytes[at].toInt() and 0xC0) == 0x80
            fun byte(at: Int): Int = bytes[at].toInt() and 0xFF

            while (index < end) {
                val first = byte(index)
                when {
                    first <= 0x7F -> index++
                    first in 0xC2..0xDF -> {
                        if (!continuation(index + 1)) return false
                        index += 2
                    }
                    first == 0xE0 -> {
                        if (index + 2 >= end || byte(index + 1) !in 0xA0..0xBF || !continuation(index + 2)) return false
                        index += 3
                    }
                    first in 0xE1..0xEC || first in 0xEE..0xEF -> {
                        if (!continuation(index + 1) || !continuation(index + 2)) return false
                        index += 3
                    }
                    first == 0xED -> {
                        if (index + 2 >= end || byte(index + 1) !in 0x80..0x9F || !continuation(index + 2)) return false
                        index += 3
                    }
                    first == 0xF0 -> {
                        if (index + 3 >= end || byte(index + 1) !in 0x90..0xBF ||
                            !continuation(index + 2) || !continuation(index + 3)
                        ) return false
                        index += 4
                    }
                    first in 0xF1..0xF3 -> {
                        if (!continuation(index + 1) || !continuation(index + 2) || !continuation(index + 3)) return false
                        index += 4
                    }
                    first == 0xF4 -> {
                        if (index + 3 >= end || byte(index + 1) !in 0x80..0x8F ||
                            !continuation(index + 2) || !continuation(index + 3)
                        ) return false
                        index += 4
                    }
                    else -> return false
                }
            }
            return true
        }

        private fun fail(): Boolean {
            malformed = true
            return false
        }

        private fun readVarintRaw(): Long? {
            var result = 0L
            for (index in 0 until 10) {
                if (pos >= limit) return null
                val b = bytes[pos].toInt() and 0xFF
                pos++
                if (index == 9 && b > 1) return null
                result = result or ((b and 0x7F).toLong() shl (index * 7))
                if (b and 0x80 == 0) return result
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
