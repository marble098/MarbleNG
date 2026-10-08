package com.marbleng.app.core

// MARBLE_MULTI_SOURCE_ROUTING_V212 — several geoip/geosite databases, one routing decision.
//
// The report: *"routing sends Iranian traffic direct, but it is still weak; some Iranian sites and
// apps still go through the proxy. The app must be able to use several geoip and geosite
// databases at the same time, and separate traffic much more finely."*
//
// What was wrong, structurally:
//
//  1. **One source, one file, one opinion.** [com.marbleng.app.model.AppSettings.geoAssetSourceId]
//     named a single source, `XrayManager` wrote it to the two canonical file names
//     (`geoip.dat`, `geosite.dat`), and every rule was `geoip:<tag>` / `geosite:<tag>`, which the
//     core resolves *only* against those two files. A tag that another database maintains and the
//     chosen one does not — `category-ads-ir`, a bank category, a messenger category — simply did
//     not exist, and the traffic it described fell through to the proxy.
//  2. **No union was possible.** There was no place to say "this tag from that source", so the
//     only way to add coverage was to replace the source, which is how a user with a working
//     Iranian database lost it the day they wanted Loyalsoldier's world coverage.
//  3. **Nothing described the set.** The routing page could show one row: the source, its two file
//     sizes. It could not answer "which databases is my routing reading, and which one answered
//     for this tag?" — which is the first question a user asks when a domestic site is proxied.
//
// What replaces it is a **registry**: an ordered set of sources, the first of which is the
// primary (the one the APK bundles, so a fresh install with no network still routes correctly),
// each contributing its own file on disk, and every geo rule emitted with the file it belongs to.
//
// The engine supports this natively, which is what makes it safe. Xray's rule parser at the pinned
// tag (`common/geodata/rule_parser.go`) rewrites `geoip:<tag>` into `ext:geoip.dat:<TAG>` and
// accepts `ext:<file>:<TAG>` / `ext-ip:<file>:<TAG>` for IP rules and `ext:<file>:<tag>` /
// `ext-domain:<file>:<tag>` / `ext-site:<file>:<tag>` for domain rules — so N databases is N files
// in the asset directory plus one reference each, with no merging and no invented format. The
// primary keeps the canonical names so nothing that already works (the bundled assets, the
// readiness gates, the diagnostics) has to learn a new path.
//
// Everything here is pure: parsing, ordering, file names and the tokens a rule references are all
// functions of the stored settings, which is what makes the whole surface unit-testable and what
// keeps the two cores — and the simulator — reading one identical answer.

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.GeoPrecision
import com.marbleng.app.model.RoutingDefaults
import com.marbleng.app.model.parseGeoPrecision
import org.json.JSONArray
import org.json.JSONObject

object GeoAssetRegistry {

    /** Which of the two database families a spec, a file or a token is about. */
    enum class Kind { GEOIP, GEOSITE }

    /** How many sources the routing layer will read at once. */
    const val MAX_SOURCES: Int = 6

    /**
     * The canonical asset file names.
     *
     * The primary source keeps these two names on purpose: they are the names the APK bundles its
     * verified databases under, the names every readiness gate and diagnostic already reports, and
     * the names `geoip:` / `geosite:` resolve to inside the core. Renaming them to fit the
     * multi-source scheme would have bought nothing and broken a first-install offline route.
     */
    const val PRIMARY_GEOIP_FILE: String = "geoip.dat"
    const val PRIMARY_GEOSITE_FILE: String = "geosite.dat"

    /**
     * The id of the built-in entry that is a *form*, not a source: picking it opens the custom-URL
     * editor. It must never resolve to a database, so it is filtered out of the catalog.
     */
    const val CUSTOM_FORM_ID: String = "custom"

    /** One geo asset source: a pair of databases (either of which may be absent). */
    data class SourceSpec(
        val id: String,
        val label: String,
        val geoIpUrl: String = "",
        val geoSiteUrl: String = "",
        val geoIpMirror: String = "",
        val geoSiteMirror: String = "",
        /** True when the APK ships a verified copy, so this source works with no network. */
        val bundled: Boolean = false,
        /** True when the user defined this source in Settings rather than picking a built-in one. */
        val custom: Boolean = false
    ) {
        fun urlFor(kind: Kind): String =
            if (kind == Kind.GEOIP) geoIpUrl.trim() else geoSiteUrl.trim()

        fun mirrorFor(kind: Kind): String =
            if (kind == Kind.GEOIP) geoIpMirror.trim() else geoSiteMirror.trim()

        /** A source that publishes only one family is still a source: half a union beats none. */
        fun provides(kind: Kind): Boolean = urlFor(kind).isNotBlank()

        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("label", label)
            .put("geoIpUrl", geoIpUrl)
            .put("geoSiteUrl", geoSiteUrl)
    }

    /**
     * What one source is doing on this device right now.
     *
     * [primary] is the source that owns the canonical file names; a source that is enabled but not
     * yet on disk is not an error (the gate simply skips it), so the UI reports the truth instead
     * of a red badge.
     */
    data class SourceState(
        val spec: SourceSpec,
        val primary: Boolean,
        val geoIpReady: Boolean,
        val geoSiteReady: Boolean,
        val geoIpBytes: Long,
        val geoSiteBytes: Long,
        val updatedAtMs: Long
    ) {
        /** True when every family this source publishes is usable by the core. */
        fun usable(): Boolean =
            (!spec.provides(Kind.GEOIP) || geoIpReady) &&
                (!spec.provides(Kind.GEOSITE) || geoSiteReady)

        fun readyFor(kind: Kind): Boolean =
            if (kind == Kind.GEOIP) geoIpReady else geoSiteReady
    }

    /** The user's routing data preference, parsed out of [AppSettings]. */
    data class Selection(
        val ids: List<String>,
        val custom: List<SourceSpec>,
        val precision: GeoPrecision,
        val multiSource: Boolean
    ) {
        /** The primary source id: the first of the ordered set, which is never empty. */
        val primaryId: String get() = ids.firstOrNull() ?: RoutingDefaults.SOURCE_CHOCOLATE4U
    }

    // -------------------------------------------------------------------------------------------
    // Parsing — a stored preference becomes an ordered, deduplicated set
    // -------------------------------------------------------------------------------------------

    /**
     * Splits a stored id list.
     *
     * The list is the truth, and an empty list means "the primary only" rather than "nothing":
     * routing without a geo database is a worse product than routing with the one the APK ships,
     * and the primary is the one source that cannot be removed because it is bundled.
     */
    fun parseIds(raw: String): List<String> {
        val cleaned = raw.split(',', '\n', '\r', ';', '|')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it != "all" }
            .distinct()
            .take(MAX_SOURCES)
        if (cleaned.isEmpty()) return listOf(RoutingDefaults.SOURCE_CHOCOLATE4U)
        return cleaned
    }

    fun serializeIds(ids: List<String>): String =
        parseIds(ids.joinToString(",")).joinToString(",")

    /** Ids with [id] moved to the front — used when the user picks a new primary source. */
    fun withPrimary(ids: List<String>, id: String): List<String> {
        val clean = id.trim().lowercase()
        if (clean.isBlank()) return parseIds(ids.joinToString(","))
        return (listOf(clean) + parseIds(ids.joinToString(",")).filterNot { it == clean })
            .distinct()
            .take(MAX_SOURCES)
    }

    /** Ids with [id] added, if there is room. */
    fun withAdded(ids: List<String>, id: String): List<String> {
        val clean = id.trim().lowercase()
        if (clean.isBlank()) return parseIds(ids.joinToString(","))
        val current = parseIds(ids.joinToString(","))
        if (clean in current) return current
        return (current + clean).take(MAX_SOURCES)
    }

    /** Ids without [id], always keeping at least the primary. */
    fun without(ids: List<String>, id: String): List<String> {
        val clean = id.trim().lowercase()
        val rest = parseIds(ids.joinToString(",")).filterNot { it == clean }
        return rest.ifEmpty { listOf(RoutingDefaults.SOURCE_CHOCOLATE4U) }
    }

    /** True when the stored set has room for one more source. */
    fun hasRoom(ids: String): Boolean = parseIds(ids).size < MAX_SOURCES

    /** The user's own sources, parsed leniently: a broken entry is skipped, never thrown. */
    fun customSources(raw: String): List<SourceSpec> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val array = runCatching { JSONArray(trimmed) }.getOrNull() ?: return emptyList()
        val out = ArrayList<SourceSpec>()
        for (i in 0 until array.length()) {
            val entry = runCatching { array.getJSONObject(i) }.getOrNull() ?: continue
            // The stored id already carries the `custom-` prefix ([serializeCustom] writes it), so
            // it is only added to a bare one. Prefixing unconditionally would rename the source on
            // every save/load round trip and orphan the id the user's own set points at.
            val bare = safeId(entry.optString("id").ifBlank { entry.optString("label") })
            val id = if (bare.startsWith("custom-")) bare else "custom-$bare"
            val geoIpUrl = entry.optString("geoIpUrl").trim()
            val geoSiteUrl = entry.optString("geoSiteUrl").trim()
            if (id.isBlank() || (geoIpUrl.isBlank() && geoSiteUrl.isBlank())) continue
            if (!geoIpUrl.startsWith("https://", true) && geoIpUrl.isNotBlank()) continue
            if (!geoSiteUrl.startsWith("https://", true) && geoSiteUrl.isNotBlank()) continue
            out += SourceSpec(
                id = id,
                label = entry.optString("label").ifBlank { id },
                geoIpUrl = geoIpUrl,
                geoSiteUrl = geoSiteUrl,
                custom = true
            )
        }
        return out.distinctBy { it.id }.take(MAX_SOURCES)
    }

    fun serializeCustom(sources: List<SourceSpec>): String {
        val array = JSONArray()
        sources.filter { it.custom }.forEach { array.put(it.toJson()) }
        return array.toString()
    }

    /**
     * One id from arbitrary user input.
     *
     * The id becomes part of a file name on disk, so it is reduced to a safe alphabet rather than
     * validated and rejected: two ids that differ only in characters a file name cannot carry are
     * the same source, and a name that cannot be written must never reach `File(dir, …)`.
     */
    fun safeId(raw: String): String {
        val cleaned = raw.trim().lowercase()
            .map { if (it.isLetterOrDigit() || it == '-') it else '-' }
            .joinToString("")
            .trim('-')
            .replace(Regex("-{2,}"), "-")
        return cleaned.take(32)
    }

    fun parseSelection(settings: AppSettings): Selection = Selection(
        ids = parseIds(settings.geoAssetSourceIds.ifBlank { settings.geoAssetSourceId }),
        custom = customSources(settings.geoCustomSourcesJson),
        precision = parseGeoPrecision(settings.geoPrecision),
        multiSource = settings.geoMultiSourceEnabled
    )

    // -------------------------------------------------------------------------------------------
    // Resolution — the ordered list of sources the routing layer actually reads
    // -------------------------------------------------------------------------------------------

    /** Every source the product knows about: built-ins, the classic custom URLs, then JSON ones. */
    fun catalog(settings: AppSettings): List<SourceSpec> =
        RoutingDefaults.SOURCES
            // The pseudo-source that only opens the custom-URL editor is a form, not a database —
            // unless the user actually typed URLs into it, in which case it is the source those
            // URLs describe ([customUrlSource]) and it has to resolve like any other.
            .filterNot { it.id == CUSTOM_FORM_ID }
            .map { it.toSpec() } +
            listOfNotNull(customUrlSource(settings)) +
            customSources(settings.geoCustomSourcesJson)

    /**
     * The source described by the two classic URL fields, if the user typed anything into them.
     *
     * Pre-V212 installs stored one pair of URLs under `geoIpUrl`/`geoSiteUrl` and selected the
     * entry called *Custom HTTPS URLs* to use them. That selection has to keep working after the
     * registry arrives, so those two strings are read as a source of their own — with the same id
     * the settings screen already stores — instead of being silently dropped. The shipped default
     * URLs are the primary source, not a custom one, so they do not produce a second entry.
     */
    fun customUrlSource(settings: AppSettings): SourceSpec? {
        val ip = settings.geoIpUrl.trim()
        val site = settings.geoSiteUrl.trim()
        if (ip.isBlank() && site.isBlank()) return null
        if (ip == RoutingDefaults.GEOIP_URL && site == RoutingDefaults.GEOSITE_URL) return null
        return SourceSpec(
            id = CUSTOM_FORM_ID,
            label = "Custom URLs",
            geoIpUrl = ip,
            geoSiteUrl = site,
            custom = true
        )
    }

    /**
     * The sources the routing layer reads, in priority order.
     *
     * Multi-source off means the primary alone — the pre-V212 behaviour, one switch away. The
     * primary is always first and always present, because it is the source the APK can supply
     * offline; unknown ids are dropped rather than passed to the core as a file that does not
     * exist.
     */
    fun resolve(settings: AppSettings): List<SourceSpec> {
        val selection = parseSelection(settings)
        val known = catalog(settings).associateBy { it.id }
        val requested = if (selection.multiSource) selection.ids else listOf(selection.primaryId)
        val resolved = requested.mapNotNull { known[it] }
        // A stored list whose entries all disappeared (a removed custom source) still routes: the
        // primary is added back rather than leaving the engine with no database at all.
        val ordered = resolved.ifEmpty {
            listOfNotNull(
                known[selection.primaryId]
                    ?: known[RoutingDefaults.SOURCE_CHOCOLATE4U]
                    ?: known.values.firstOrNull()
            )
        }
        // The order is the priority, and the first entry owns the canonical file names — which is
        // what lets a user make any source their primary, including one the APK cannot supply.
        return ordered.distinctBy { it.id }.take(MAX_SOURCES)
    }

    /** The file on disk one source's [kind] database lives in. */
    fun fileName(spec: SourceSpec, kind: Kind, primary: Boolean): String = when {
        primary && kind == Kind.GEOIP -> PRIMARY_GEOIP_FILE
        primary && kind == Kind.GEOSITE -> PRIMARY_GEOSITE_FILE
        kind == Kind.GEOIP -> "geoip-${safeId(spec.id)}.dat"
        else -> "geosite-${safeId(spec.id)}.dat"
    }

    /**
     * The token a rule must carry to read [tag] out of [spec].
     *
     * The primary is addressed the classic way (`geoip:ir`) so its rules are byte-identical to the
     * ones every earlier release wrote; every other source names its own file, which is exactly
     * what the core's `ext:` loader is for. `geoip:private` never names a file: it is a built-in
     * the core answers without any database.
     */
    fun token(kind: Kind, tag: String, spec: SourceSpec, primary: Boolean): String? {
        val clean = tag.trim().removePrefix("geoip:").removePrefix("geosite:").trim()
        if (clean.isEmpty()) return null
        if (kind == Kind.GEOIP && clean.equals("private", true)) return "geoip:private"
        if (!spec.provides(kind)) return null
        return if (primary) {
            "${if (kind == Kind.GEOIP) "geoip" else "geosite"}:$clean"
        } else {
            "ext:${fileName(spec, kind, false)}:$clean"
        }
    }

    /** Every token for [tag], one per source that can answer it, primary first. */
    fun tokensFor(settings: AppSettings, kind: Kind, tag: String): List<String> {
        val sources = resolve(settings)
        if (sources.isEmpty()) return emptyList()
        return sources.mapIndexedNotNull { index, spec ->
            token(kind, tag, spec, index == 0)
        }.distinct()
    }

    /** True when [token] names a file — i.e. it came from a non-primary source. */
    fun isFileReference(token: String): Boolean = token.startsWith("ext:", true)

    /** The file a `ext:<file>:<tag>` token reads, or "" for a canonical `geoip:`/`geosite:` token. */
    fun fileOf(token: String): String =
        if (isFileReference(token)) token.removePrefix("ext:").substringBefore(":") else ""

    /** The tag inside any geo token, whatever its shape. */
    fun tagOf(token: String): String {
        val trimmed = token.trim()
        val body = when {
            trimmed.startsWith("ext-domain:", true) -> trimmed.substringAfter(":", "")
            trimmed.startsWith("ext-site:", true) -> trimmed.substringAfter(":", "")
            trimmed.startsWith("ext-ip:", true) -> trimmed.substringAfter(":", "")
            trimmed.startsWith("ext:", true) -> trimmed.substringAfter(":", "")
            trimmed.startsWith("geoip:", true) -> trimmed.substringAfter(":", "")
            trimmed.startsWith("geosite:", true) -> trimmed.substringAfter(":", "")
            else -> trimmed
        }
        // `ext:<file>:<tag>` still carries the file name; the tag is whatever follows the last ':'.
        return body.substringAfterLast(":", body).trim().lowercase()
    }

    // -------------------------------------------------------------------------------------------
    // Reporting — one sentence the user can act on
    // -------------------------------------------------------------------------------------------

    /**
     * What the routing layer is reading, as a short human summary.
     *
     * "3 sources • geoip ×2, geosite ×2 • 41.2 MB" is the answer to "why did that site go through
     * the proxy?": it names the databases before it names a rule.
     */
    fun summary(states: List<SourceState>): String {
        val usable = states.filter { it.usable() }
        if (usable.isEmpty()) return "No geo database ready yet"
        val ip = usable.count { it.spec.provides(Kind.GEOIP) && it.geoIpReady }
        val site = usable.count { it.spec.provides(Kind.GEOSITE) && it.geoSiteReady }
        val bytes = usable.sumOf { it.geoIpBytes + it.geoSiteBytes }
        val parts = buildList {
            add("${usable.size} source${if (usable.size == 1) "" else "s"}")
            add("geoip ×$ip")
            add("geosite ×$site")
            add(formatBytes(bytes))
        }
        return parts.joinToString(" • ")
    }

    /**
     * Which sources can answer for [tag] — the provenance line of the rule editor and simulator.
     *
     * The answer comes from the indexed databases, not from the selection: a source that is
     * enabled but whose file is missing or corrupt did not answer anything, and saying it did is
     * exactly the kind of confident wrong answer that sends a user hunting through their rules.
     */
    fun provenance(states: List<SourceState>, kind: Kind, tag: String): List<String> {
        val clean = tag.trim().lowercase()
        if (clean.isBlank()) return emptyList()
        val indexedKind = if (kind == Kind.GEOIP) GeoAssetIndex.Kind.GEOIP else GeoAssetIndex.Kind.GEOSITE
        val files = states.filter { it.readyFor(kind) && it.spec.provides(kind) }
            .associateBy { fileName(it.spec, kind, it.primary) }
        return GeoAssetIndex.sourcesFor(indexedKind, clean)
            .mapNotNull { file -> files[file]?.spec?.label }
            .distinct()
    }

    fun formatBytes(bytes: Long): String = when {
        bytes <= 0L -> "0 KB"
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }

    /** [com.marbleng.app.model.GeoAssetSource] → the registry's own shape. */
    private fun com.marbleng.app.model.GeoAssetSource.toSpec(): SourceSpec = SourceSpec(
        id = id,
        label = label,
        geoIpUrl = geoIpUrl,
        geoSiteUrl = geoSiteUrl,
        geoIpMirror = geoIpMirror,
        geoSiteMirror = geoSiteMirror,
        // Only the shipped default source has a verified copy inside the APK; every other source
        // has to be downloaded before its rules can be emitted.
        bundled = id == RoutingDefaults.SOURCE_CHOCOLATE4U,
        custom = id == "custom"
    )
}
