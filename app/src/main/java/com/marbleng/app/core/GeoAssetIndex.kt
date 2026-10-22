package com.marbleng.app.core

import java.io.File
import java.io.RandomAccessFile
import java.util.Arrays

object GeoAssetIndex {
    enum class Kind(val label: String) {
        GEOSITE("geosite"),
        GEOIP("geoip")
    }

    data class GeoEntry(
        val kind: Kind,
        val tag: String,
        val count: Int
    )

    class IndexedFile(
        val file: File,
        val kind: Kind
    )

    class FileState(
        val name: String,
        val kind: Kind,
        val stamp: Long,
        val size: Long,
        val entries: List<GeoEntry>,
        val hashes: LongArray?,
        val path: String = name,
        val parsedSuccessfully: Boolean = false
    )

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

        fun isUsableFile(file: File, kind: Kind): Boolean =
            files.any { state ->
                state.path == file.absolutePath && state.kind == kind && isFreshAndValid(state)
            }

        fun isUsableFile(name: String, kind: Kind): Boolean =
            files.any { state -> state.name == name && state.kind == kind && isFreshAndValid(state) }

        fun tagsForFile(name: String, kind: Kind): Set<String>? =
            files.firstOrNull { it.name == name && it.kind == kind && isFreshAndValid(it) }
                ?.entries?.mapTo(linkedSetOf()) { it.tag }

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

    private const val MAX_DOMAIN_HASHES = 350_000

    private const val MAX_MERGED_DOMAIN_HASHES = 700_000

    private const val DOMAIN_FULL = 3L
    private const val DOMAIN_ROOT = 2L

    private const val TYPE_SHIFT = 56
    private const val HASH_MASK = (1L shl TYPE_SHIFT) - 1

    @Volatile
    private var snapshot: Snapshot? = null

    @Volatile
    private var assetsDirPath: String? = null

    @Volatile
    private var fileStates: Map<String, FileState> = emptyMap()

    fun current(): Snapshot? = snapshot

    internal fun resetForTests() {
        snapshot = null
        assetsDirPath = null
        fileStates = emptyMap()
    }

    fun defaultFiles(assetsDir: File): List<IndexedFile> = listOf(
        IndexedFile(File(assetsDir, "geosite.dat"), Kind.GEOSITE),
        IndexedFile(File(assetsDir, "geoip.dat"), Kind.GEOIP)
    )

    @Synchronized
    fun update(assetsDir: File): Snapshot? = update(assetsDir, defaultFiles(assetsDir))

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

    private fun mergeHashes(states: List<FileState>): LongArray? {
        if (states.isEmpty() || states.any { !isFreshAndValid(it) || it.hashes == null }) return null
        val tables = states.mapNotNull { it.hashes }.filter { it.isNotEmpty() }
        if (tables.isEmpty()) return null
        val total = tables.sumOf { it.size }

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

    private class ScanResult(val entries: List<GeoEntry>, val hashes: LongArray?)

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

    fun suggest(kind: Kind, query: String, limit: Int = 8): List<GeoEntry> {
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

    fun known(kind: Kind, tag: String): Boolean? {
        val snap = snapshot ?: return null
        val clean = normalizeToken(tag)
        if (clean.isEmpty()) return null

        return snap.filesFor(kind, clean).isNotEmpty()
    }

    fun sourcesFor(kind: Kind, tag: String): List<String> {
        val snap = snapshot ?: return emptyList()
        val clean = normalizeToken(tag)
        if (clean.isEmpty()) return emptyList()
        return snap.filesFor(kind, clean)
    }

    fun fileCount(kind: Kind): Int = snapshot?.files?.count {
        it.kind == kind && isFreshAndValid(it)
    } ?: 0

    fun matchesGeosite(host: String): Boolean? {
        val snap = snapshot ?: return null
        if (!snap.allFilesFresh(Kind.GEOSITE)) return null
        val table = snap.geositeDomainHashes ?: return null
        val clean = host.trim().trimEnd('.').lowercase()
        if (clean.isEmpty() || !clean.any { it.isLetterOrDigit() }) return null

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

    fun canVerifyGeositeMembership(): Boolean = snapshot?.let {
        it.geositeDomainHashes != null && it.allFilesFresh(Kind.GEOSITE)
    } == true

    private fun scanGeosite(file: File): ScanResult? {
        val bytes = readFileBounded(file) ?: return null
        val counters = linkedMapOf<String, Int>()
        val hashes = DomainHashBuffer()
        val reader = ProtobufReader(bytes)

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
                    reader.varint()
                }
                else -> reader.skip()
            }
        }
        if (!reader.isComplete()) return false
        val tag = code?.trim()?.lowercase().orEmpty()
        if (tag.isNotEmpty()) counters[tag] = (counters[tag] ?: 0) + cidrCount
        return true
    }

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

    /**
     * MARBLE_SINGBOX_GEO_ROUTING_V222 — the payload of one dat tag, not just its size.
     *
     * The index above answers "does this tag exist and how big is it", because that is what the
     * Xray config and the routing editor need: Xray reads the .dat files itself, so Marble never
     * has to materialise a domain list. sing-box cannot read .dat at all — it only understands
     * its own rule sets — so geo routing on the sing-box engine needs the actual contents of the
     * tag the user chose. This is the one place that decodes them, from the same pinned files the
     * Xray engine already trusts, with the same defensive parser.
     */
    data class GeoPayload(
        val kind: Kind,
        val tag: String,
        val full: List<String>,
        val suffix: List<String>,
        val keywords: List<String>,
        val regexes: List<String>,
        val cidrs: List<String>,
        val truncated: Boolean = false,
        val reverseMatch: Boolean = false
    ) {
        val isEmpty: Boolean
            get() = full.isEmpty() && suffix.isEmpty() && keywords.isEmpty() &&
                regexes.isEmpty() && cidrs.isEmpty()

        val size: Int get() = full.size + suffix.size + keywords.size + regexes.size + cidrs.size
    }

    /** Upper bound on the items one tag may contribute, so a hostile .dat cannot exhaust the heap. */
    const val MAX_PAYLOAD_ITEMS: Int = 400_000

    /**
     * Extracts [tag] of [kind] from the given .dat [files] (already ordered by the caller's geo
     * source priority). Every readable file is merged into one payload, which is what makes
     * multi-source geo routing work on sing-box the same way it works on Xray.
     *
     * Returns null when no file could be read; an empty payload when the tag is simply absent.
     */
    fun extractPayload(
        kind: Kind,
        tag: String,
        files: List<File>,
        limit: Int = MAX_PAYLOAD_ITEMS
    ): GeoPayload? {
        val clean = normalizeToken(tag).substringAfterLast(':').trim()
        if (clean.isEmpty()) return null
        return extractPayloads(kind, listOf(clean), files, limit)?.get(clean)
    }

    /**
     * MARBLE_SINGBOX_GEO_ROUTING_V222 — the batched form of [extractPayload].
     *
     * One configuration can name twenty geo tags, and a geosite.dat is several megabytes. Reading
     * and parsing the file once per tag would be twenty identical passes over the same bytes on the
     * thread that is holding up the connect. This walks each file once, reads the tag code of every
     * entry, and only fully decodes the entries somebody asked for.
     *
     * Returns null when no file could be read at all; otherwise one payload per requested tag,
     * empty for a tag the files do not contain.
     */
    fun extractPayloads(
        kind: Kind,
        tags: Collection<String>,
        files: List<File>,
        limit: Int = MAX_PAYLOAD_ITEMS
    ): Map<String, GeoPayload>? {
        val wanted = LinkedHashMap<String, PayloadAccumulator>()
        tags.forEach { raw ->
            val clean = normalizeToken(raw).substringAfterLast(':').trim()
            if (clean.isNotEmpty() && !wanted.containsKey(clean)) {
                wanted[clean] = PayloadAccumulator()
            }
        }
        if (wanted.isEmpty() || files.isEmpty()) return null

        var readable = false
        for (file in files) {
            val bytes = readFileBounded(file) ?: continue
            readable = true
            val reader = ProtobufReader(bytes)
            while (reader.next()) {
                if (reader.fieldNumber != 1 || reader.wireType != 2) continue
                val entry = reader.messageBytes() ?: break
                val code = entryCode(entry) ?: continue
                val accumulator = wanted[code] ?: continue
                if (kind == Kind.GEOSITE) {
                    readSitePayloadInto(entry, accumulator, limit)
                } else {
                    readIpPayloadInto(entry, accumulator, limit)
                }
                if (wanted.values.all { it.saturated(limit) }) break
            }
        }
        if (!readable) return null
        return wanted.mapValues { (tag, accumulator) -> accumulator.toPayload(kind, tag) }
    }

    private class PayloadAccumulator {
        val full = LinkedHashSet<String>()
        val suffix = LinkedHashSet<String>()
        val keywords = LinkedHashSet<String>()
        val regexes = LinkedHashSet<String>()
        val cidrs = LinkedHashSet<String>()
        var reverse = false
        var truncated = false

        fun saturates(): Int = full.size + suffix.size + keywords.size + regexes.size + cidrs.size

        /**
         * A tag is done when it has all it is allowed to have. Only geosite payloads can be cut;
         * an IP list is a fixed set of ranges and is never truncated in practice.
         */
        fun saturated(limit: Int): Boolean = cidrs.size >= limit || saturates() >= limit

        fun toPayload(kind: Kind, tag: String) = GeoPayload(
            kind = kind,
            tag = tag,
            full = full.toList(),
            suffix = suffix.toList(),
            keywords = keywords.toList(),
            regexes = regexes.toList(),
            cidrs = cidrs.toList(),
            truncated = truncated,
            reverseMatch = reverse
        )
    }

    /** The tag code of one entry, read without decoding the entry's payload. */
    private fun entryCode(entry: ByteArray): String? {
        val reader = ProtobufReader(entry)
        while (reader.next()) {
            if (reader.fieldNumber == 1 && reader.wireType == 2) {
                return reader.stringBytes().trim().lowercase().takeIf { it.isNotEmpty() }
            }
            reader.skip()
        }
        return null
    }

    /**
     * Decodes one geosite entry into [accumulator]. The entry's code has already been matched by
     * the caller, so this only has to walk the domains.
     */
    private fun readSitePayloadInto(
        entry: ByteArray,
        accumulator: PayloadAccumulator,
        limit: Int
    ) {
        val reader = ProtobufReader(entry)
        val domains = ArrayList<Pair<Long, String>>(64)
        while (reader.next()) {
            when (reader.fieldNumber) {
                1 -> {
                    if (reader.wireType != 2) return
                    reader.stringBytes()
                }
                2 -> {
                    if (reader.wireType != 2) return
                    val domain = reader.messageBytes() ?: return
                    readDomainPayload(domain)?.let { domains += it }
                }
                else -> reader.skip()
            }
        }
        if (!reader.isComplete()) return
        var total = accumulator.saturates()
        for ((type, value) in domains) {
            val target = when (type) {
                DOMAIN_PLAIN -> accumulator.keywords
                DOMAIN_REGEX -> accumulator.regexes
                DOMAIN_ROOT -> accumulator.suffix
                DOMAIN_FULL -> accumulator.full
                else -> continue
            }
            if (total >= limit) {
                accumulator.truncated = true
                break
            }
            if (target.add(value)) total++
        }
    }

    private const val DOMAIN_PLAIN = 0L

    private const val DOMAIN_REGEX = 1L

    /** Returns type to value, or null for an entry no matcher can use. */
    private fun readDomainPayload(domain: ByteArray): Pair<Long, String>? {
        val reader = ProtobufReader(domain)
        var type = -1L
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
        if (!reader.isComplete() || type !in 0L..3L) return null
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty() || raw.length > 512) return null
        val clean = raw.lowercase()
        return when (type) {
            DOMAIN_PLAIN, DOMAIN_REGEX -> type to clean
            else -> {
                val host = clean.trimStart('.').trimEnd('.')
                if (host.isEmpty() || !isDomainPayloadValue(host)) return null
                type to host
            }
        }
    }

    /** A rule-set entry must be a hostname; a path or a port would make sing-box refuse the file. */
    private fun isDomainPayloadValue(value: String): Boolean =
        value.isNotEmpty() && value.length <= 253 &&
            value.none { it.isWhitespace() || it == '/' || it == ':' || it == '\\' } &&
            value.any { it.isLetterOrDigit() }

    /** Decodes one geoip entry into [accumulator]; the code has already been matched. */
    private fun readIpPayloadInto(
        entry: ByteArray,
        accumulator: PayloadAccumulator,
        limit: Int
    ) {
        val reader = ProtobufReader(entry)
        var reverse = false
        val ranges = ArrayList<String>(64)
        while (reader.next()) {
            when (reader.fieldNumber) {
                1 -> {
                    if (reader.wireType != 2) return
                    reader.stringBytes()
                }
                2 -> {
                    if (reader.wireType != 2) return
                    val range = reader.messageBytes() ?: return
                    readCidrPayload(range)?.let { ranges += it }
                }
                3 -> {
                    if (reader.wireType != 0) return
                    reverse = reader.varint() != 0L
                }
                else -> reader.skip()
            }
        }
        if (!reader.isComplete()) return
        if (reverse) accumulator.reverse = true
        for (range in ranges) {
            if (accumulator.cidrs.size >= limit) {
                accumulator.truncated = true
                break
            }
            accumulator.cidrs += range
        }
    }

    private fun readCidrPayload(range: ByteArray): String? {
        val reader = ProtobufReader(range)
        var ip: ByteArray? = null
        var prefix: Long? = null
        while (reader.next()) {
            when (reader.fieldNumber) {
                1 -> {
                    if (reader.wireType != 2) return null
                    ip = reader.valueBytes()
                }
                2 -> {
                    if (reader.wireType != 0) return null
                    prefix = reader.varint()
                }
                else -> reader.skip()
            }
        }
        if (!reader.isComplete()) return null
        val address = ip ?: return null
        val bits = prefix ?: return null
        val max = when (address.size) {
            4 -> 32L
            16 -> 128L
            else -> return null
        }
        if (bits !in 0L..max) return null
        return "${formatAddress(address)}/$bits"
    }

    internal fun formatAddress(bytes: ByteArray): String {
        if (bytes.size == 4) return bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
        if (bytes.size != 16) return ""
        val groups = IntArray(8) { index ->
            ((bytes[index * 2].toInt() and 0xFF) shl 8) or (bytes[index * 2 + 1].toInt() and 0xFF)
        }
        var bestStart = -1
        var bestLength = 0
        var index = 0
        while (index < 8) {
            if (groups[index] != 0) {
                index++
                continue
            }
            var end = index
            while (end < 8 && groups[end] == 0) end++
            if (end - index > bestLength) {
                bestStart = index
                bestLength = end - index
            }
            index = end
        }
        val builder = StringBuilder()
        index = 0
        while (index < 8) {
            if (index == bestStart && bestLength > 1) {
                builder.append("::")
                index += bestLength
                continue
            }
            if (builder.isNotEmpty() && !builder.endsWith(":")) builder.append(':')
            builder.append(Integer.toHexString(groups[index]))
            index++
        }
        return builder.toString()
    }

    private fun entriesSorted(counters: Map<String, Int>, kind: Kind): List<GeoEntry> =
        counters.entries
            .map { GeoEntry(kind, it.key, it.value) }
            .sortedWith(compareBy<GeoEntry> { -it.count }.thenBy { it.tag })

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

        private val parents = Array(4) { ParentFrame() }

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

        fun isComplete(): Boolean = !malformed && pos == limit

        fun valueLength(): Int = if (wireType == 2) valueEnd - valueStart else 0

        fun valueBytes(): ByteArray =
            if (wireType == 2) bytes.copyOfRange(valueStart, valueEnd) else ByteArray(0)

        /** A copy of the current length-delimited field, so it can be parsed on its own. */
        fun messageBytes(): ByteArray? =
            if (wireType == 2 && !malformed) bytes.copyOfRange(valueStart, valueEnd) else null

        fun stringBytes(): String {
            if (wireType != 2 || !validUtf8(valueStart, valueEnd)) {
                malformed = true
                return ""
            }
            return String(bytes, valueStart, valueEnd - valueStart, Charsets.UTF_8)
        }

        fun varint(): Long = varintValue

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

    private fun packHash(value: String, type: Long): Long =
        (fnv1a64(value) and HASH_MASK) or (type shl TYPE_SHIFT)

    private fun hashPresent(table: LongArray, packed: Long): Boolean =
        Arrays.binarySearch(table, packed) >= 0

    private fun fnv1a64(value: String): Long {
        var hash = -0x340d631b7bdddcdbL
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
