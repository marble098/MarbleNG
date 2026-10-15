package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.GeoPrecision
import com.marbleng.app.model.RoutingDefaults
import com.marbleng.app.model.parseGeoPrecision
import org.json.JSONArray
import org.json.JSONObject

object GeoAssetRegistry {
    enum class Kind { GEOIP, GEOSITE }

    const val MAX_SOURCES: Int = 6

    const val PRIMARY_GEOIP_FILE: String = "geoip.dat"
    const val PRIMARY_GEOSITE_FILE: String = "geosite.dat"

    const val CUSTOM_FORM_ID: String = "custom"

    data class SourceSpec(
        val id: String,
        val label: String,
        val geoIpUrl: String = "",
        val geoSiteUrl: String = "",
        val geoIpMirror: String = "",
        val geoSiteMirror: String = "",
        val bundled: Boolean = false,
        val custom: Boolean = false
    ) {
        fun urlFor(kind: Kind): String =
            if (kind == Kind.GEOIP) geoIpUrl.trim() else geoSiteUrl.trim()

        fun mirrorFor(kind: Kind): String =
            if (kind == Kind.GEOIP) geoIpMirror.trim() else geoSiteMirror.trim()

        fun provides(kind: Kind): Boolean = urlFor(kind).isNotBlank()

        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("label", label)
            .put("geoIpUrl", geoIpUrl)
            .put("geoSiteUrl", geoSiteUrl)
    }

    data class SourceState(
        val spec: SourceSpec,
        val primary: Boolean,
        val geoIpReady: Boolean,
        val geoSiteReady: Boolean,
        val geoIpBytes: Long,
        val geoSiteBytes: Long,
        val updatedAtMs: Long
    ) {
        fun usable(): Boolean =
            (!spec.provides(Kind.GEOIP) || geoIpReady) &&
                (!spec.provides(Kind.GEOSITE) || geoSiteReady)

        fun readyFor(kind: Kind): Boolean =
            if (kind == Kind.GEOIP) geoIpReady else geoSiteReady
    }

    data class Selection(
        val ids: List<String>,
        val custom: List<SourceSpec>,
        val precision: GeoPrecision,
        val multiSource: Boolean
    ) {
        val primaryId: String get() = ids.firstOrNull() ?: RoutingDefaults.SOURCE_CHOCOLATE4U
    }

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

    fun withPrimary(ids: List<String>, id: String): List<String> {
        val clean = id.trim().lowercase()
        if (clean.isBlank()) return parseIds(ids.joinToString(","))
        return (listOf(clean) + parseIds(ids.joinToString(",")).filterNot { it == clean })
            .distinct()
            .take(MAX_SOURCES)
    }

    fun withAdded(ids: List<String>, id: String): List<String> {
        val clean = id.trim().lowercase()
        if (clean.isBlank()) return parseIds(ids.joinToString(","))
        val current = parseIds(ids.joinToString(","))
        if (clean in current) return current
        return (current + clean).take(MAX_SOURCES)
    }

    fun without(ids: List<String>, id: String): List<String> {
        val clean = id.trim().lowercase()
        val rest = parseIds(ids.joinToString(",")).filterNot { it == clean }
        return rest.ifEmpty { listOf(RoutingDefaults.SOURCE_CHOCOLATE4U) }
    }

    fun hasRoom(ids: String): Boolean = parseIds(ids).size < MAX_SOURCES

    fun customSources(raw: String): List<SourceSpec> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val array = runCatching { JSONArray(trimmed) }.getOrNull() ?: return emptyList()
        val out = ArrayList<SourceSpec>()
        for (i in 0 until array.length()) {
            val entry = runCatching { array.getJSONObject(i) }.getOrNull() ?: continue

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

    fun catalog(settings: AppSettings): List<SourceSpec> =
        RoutingDefaults.SOURCES

            .filterNot { it.id == CUSTOM_FORM_ID }
            .map { it.toSpec() } +
            listOfNotNull(customUrlSource(settings)) +
            customSources(settings.geoCustomSourcesJson)

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

    fun resolve(settings: AppSettings): List<SourceSpec> {
        val selection = parseSelection(settings)
        val known = catalog(settings).associateBy { it.id }
        val requested = if (selection.multiSource) selection.ids else listOf(selection.primaryId)
        val resolved = requested.mapNotNull { known[it] }

        val ordered = resolved.ifEmpty {
            listOfNotNull(
                known[selection.primaryId]
                    ?: known[RoutingDefaults.SOURCE_CHOCOLATE4U]
                    ?: known.values.firstOrNull()
            )
        }

        return ordered.distinctBy { it.id }.take(MAX_SOURCES)
    }

    fun fileName(spec: SourceSpec, kind: Kind, primary: Boolean): String = when {
        primary && kind == Kind.GEOIP -> PRIMARY_GEOIP_FILE
        primary && kind == Kind.GEOSITE -> PRIMARY_GEOSITE_FILE
        kind == Kind.GEOIP -> "geoip-${safeId(spec.id)}.dat"
        else -> "geosite-${safeId(spec.id)}.dat"
    }

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

    fun tokensFor(settings: AppSettings, kind: Kind, tag: String): List<String> {
        val sources = resolve(settings)
        if (sources.isEmpty()) return emptyList()
        return sources.mapIndexedNotNull { index, spec ->
            token(kind, tag, spec, index == 0)
        }.distinct()
    }

    fun isFileReference(token: String): Boolean = token.startsWith("ext:", true)

    fun fileOf(token: String): String =
        if (isFileReference(token)) token.removePrefix("ext:").substringBefore(":") else ""

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

        return body.substringAfterLast(":", body).trim().lowercase()
    }

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

        else -> "%.1f MB".format(java.util.Locale.US, bytes / (1024.0 * 1024.0))
    }

    private fun com.marbleng.app.model.GeoAssetSource.toSpec(): SourceSpec = SourceSpec(
        id = id,
        label = label,
        geoIpUrl = geoIpUrl,
        geoSiteUrl = geoSiteUrl,
        geoIpMirror = geoIpMirror,
        geoSiteMirror = geoSiteMirror,

        bundled = id == RoutingDefaults.SOURCE_CHOCOLATE4U,
        custom = id == "custom"
    )
}
