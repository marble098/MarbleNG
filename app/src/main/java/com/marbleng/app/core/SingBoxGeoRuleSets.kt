package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.RoutingRuleKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * MARBLE_SINGBOX_GEO_ROUTING_V222 — geo routing on the sing-box engine, for every tag the user
 * can actually choose.
 *
 * The sing-box engine used to route four geo tags and silently drop the rest: `geoip:private`
 * and `geoip:ir` became `ip_is_private` / the bundled `geoip-ir.srs`, `geosite:ir` and
 * `category-ads-all` became two more bundled rule sets, and every other tag — `cn`, `ru`,
 * `category-bank-ir`, `category-gov-ir`, `category-public-tracker`, `google`, anything the
 * routing editor offered as a suggestion — was reported as "no bundled rule set" and dropped.
 * The Routing page could hold the rule, the presets could contain it, the Xray engine applied it,
 * and the sing-box engine quietly ignored it. That is the definition of geo routing not working.
 *
 * The fix cannot be a bigger bundle: geosite has thousands of tags and geoip has one per country,
 * and a tag the user invents from their own .dat would still be missing. The fix is that the app
 * already owns the data — `GeoAssetIndex` parses the pinned Chocolate4U/v2fly .dat files for the
 * Xray engine — so this class turns any requested tag into a sing-box *source* rule set
 * (`{"version":3,"rules":[...]}`), written next to the app's cache, and hands the config builder a
 * tag→path map. The builder then emits a real `rule_set` for it exactly like the bundled ones.
 *
 * Properties this holds:
 *  - one writer, one reader: extraction lives in `GeoAssetIndex.extractPayload`, so the .dat
 *    parser is shared with the index and the routing editor;
 *  - multi-source: every readable .dat that contains the tag contributes, so the multi-source
 *    setting finally means the same thing on both engines;
 *  - offline: nothing is downloaded at connect time; if the assets are absent the rule is
 *    reported, never invented;
 *  - bounded: a tag contributes at most `MAX_ITEMS` entries and a set at most `MAX_SET_BYTES`;
 *  - cached: the file name carries a digest of the source stamps, so a reconnect reuses the file
 *    and an asset update invalidates it without a version number to remember.
 */
object SingBoxGeoRuleSets {
    const val MARKER = "MARBLE_SINGBOX_GEO_ROUTING_V222"

    /** The most tags one connection will materialise; more than this is a config mistake. */
    const val MAX_SETS: Int = 32

    /** A single generated set may not exceed this many bytes on disk. */
    const val MAX_SET_BYTES: Long = 32L * 1024L * 1024L

    private const val MAX_ITEMS = 400_000

    data class Request(val kind: GeoAssetIndex.Kind, val tag: String) {
        val id: String get() = idFor(kind, tag)
    }

    data class Plan(
        val paths: Map<String, String> = emptyMap(),
        val notes: List<String> = emptyList(),
        val generated: Int = 0,
        val reused: Int = 0,
        val skipped: Int = 0
    ) {
        val rules: Int get() = generated + reused
    }

    /** The rule-set tag sing-box sees. Stable, because the config references it. */
    fun idFor(kind: GeoAssetIndex.Kind, rawTag: String): String {
        val prefix = if (kind == GeoAssetIndex.Kind.GEOIP) "geoip" else "geosite"
        val tag = canonicalTag(rawTag).ifBlank { "empty" }
        return "$prefix-$tag"
    }

    /**
     * The user-facing tag, normalised once. Everything the app accepts — `geosite:cn`,
     * `ext-site:geosite-cn.dat:cn`, `GEOIP:CN` — lands on the same canonical token.
     */
    fun canonicalTag(raw: String): String {
        var token = raw.trim().lowercase()
        if (token.startsWith("ext-domain:") || token.startsWith("ext-site:") ||
            token.startsWith("ext-ip:")
        ) {
            token = token.substringAfter(':').substringAfter(':')
        } else {
            token = token.removePrefix("geosite:").removePrefix("geoip:").trim()
        }
        // `geosite:google@ads` filters a tag by attribute in Xray. A sing-box rule set has no
        // attribute filter, so the whole tag is routed rather than silently dropping the rule.
        token = token.substringBefore('@').trim().trimStart('*').trimStart('.')
        if (token.isBlank()) return ""
        val mapped = when (token) {
            "ads", "ads-all", "geosite-ads", "category-ads-all" -> "category-ads-all"
            "geosite-ir", "geoip-ir" -> "ir"
            else -> token
        }
        return mapped
            .map { ch ->
                when {
                    ch.isLetterOrDigit() -> ch
                    ch == '-' -> ch
                    ch == '!' -> '-'
                    else -> '-'
                }
            }
            .joinToString("")
            .replace(Regex("-{2,}"), "-")
            .trim('-')
    }

    fun formatOf(path: String): String =
        if (path.endsWith(".json", ignoreCase = true)) "source" else "binary"

    /**
     * Every geo tag this configuration can route: the implicit Iran/ads rules, the user's rule
     * list, and the `geosite:` / `geoip:` tokens the routing page also accepts inside the domain
     * and IP lists. Order is stable (implicit first) so a config built twice is identical.
     */
    fun requests(settings: AppSettings): List<Request> {
        val seen = LinkedHashMap<String, Request>()
        fun add(kind: GeoAssetIndex.Kind, raw: String) {
            val tag = canonicalTag(raw)
            if (tag.isBlank() || tag == "private") return
            val request = Request(kind, tag)
            if (!seen.containsKey(request.id)) seen[request.id] = request
        }
        fun addTokens(kind: GeoAssetIndex.Kind, raw: String, prefix: String) {
            split(raw).forEach { token ->
                if (token.startsWith(prefix, ignoreCase = true)) add(kind, token)
            }
        }

        val implicit = RoutingEngine.implicitRules(settings)
        implicit.adsTag?.let { add(GeoAssetIndex.Kind.GEOSITE, it) }
        implicit.directIpTags.forEach { add(GeoAssetIndex.Kind.GEOIP, it) }
        implicit.directSiteTags.forEach { add(GeoAssetIndex.Kind.GEOSITE, it) }

        RoutingEngine.effectiveRules(settings).filter { it.enabled }.forEach { rule ->
            when (rule.kind) {
                RoutingRuleKind.GEOIP -> add(GeoAssetIndex.Kind.GEOIP, rule.matcher)
                RoutingRuleKind.GEOSITE -> add(GeoAssetIndex.Kind.GEOSITE, rule.matcher)
                RoutingRuleKind.DOMAIN -> addTokens(GeoAssetIndex.Kind.GEOSITE, rule.matcher, "geosite:")
                RoutingRuleKind.IP -> addTokens(GeoAssetIndex.Kind.GEOIP, rule.matcher, "geoip:")
                RoutingRuleKind.PORT -> Unit
            }
        }

        listOf(
            settings.routeBlockDomains,
            settings.routeProxyDomains,
            settings.routeDirectDomains
        ).forEach { addTokens(GeoAssetIndex.Kind.GEOSITE, it, "geosite:") }
        listOf(settings.routeBlockIps, settings.routeDirectIps)
            .forEach { addTokens(GeoAssetIndex.Kind.GEOIP, it, "geoip:") }

        return seen.values.toList()
    }

    /**
     * Materialises [requests] next to the app's cache and returns the tags sing-box can reference.
     *
     * [bundled] is the map of the rule sets packaged in the APK. A bundled set always wins: it is
     * precompiled, verified by checksum at install, and smaller than any generated one.
     */
    fun plan(
        requests: List<Request>,
        bundled: Map<String, String>,
        snapshot: GeoAssetIndex.Snapshot?,
        outputDir: File,
        maxSets: Int = MAX_SETS,
        maxBytes: Long = MAX_SET_BYTES,
        maxItems: Int = MAX_ITEMS
    ): Plan {
        val paths = LinkedHashMap<String, String>()
        val notes = mutableListOf<String>()
        var generated = 0
        var reused = 0
        var skipped = 0

        val accepted = ArrayList<Request>(requests.size)
        for (request in requests) {
            if (paths.containsKey(request.id) || bundled.containsKey(request.id)) continue
            if (accepted.size >= maxSets) {
                notes += "geo routing: more than $maxSets geo tags are configured, so the rest are " +
                    "left to the core's final outbound"
                break
            }
            accepted += request
        }

        outputDir.mkdirs()
        val produced = LinkedHashSet<String>()

        // Pass one: decide the output file of every accepted request from the source stamps. A
        // file that already exists is a cache hit and never touches the .dat bytes again.
        val sources = HashMap<String, List<File>>(accepted.size)
        val targets = HashMap<String, File>(accepted.size)
        val pending = ArrayList<Request>(accepted.size)
        for (request in accepted) {
            if (bundled.containsKey(request.id)) continue
            val files = filesFor(snapshot, request)
            if (files.isEmpty()) {
                skipped++
                continue
            }
            sources[request.id] = files
            val target = File(outputDir, "${request.id}-${digestOf(request, files)}.json")
            targets[request.id] = target
            produced += target.absolutePath
            if (target.isFile && target.length() > 0L) {
                paths[request.id] = target.absolutePath
                reused++
            } else {
                pending += request
            }
        }

        // Pass two: read every .dat at most once per kind, and only for the tags that actually
        // need a file. Twenty tags no longer mean twenty passes over a multi-megabyte file.
        val filesByKind = LinkedHashMap<GeoAssetIndex.Kind, LinkedHashSet<File>>()
        val tagsByKind = LinkedHashMap<GeoAssetIndex.Kind, LinkedHashSet<String>>()
        for (request in pending) {
            filesByKind.getOrPut(request.kind) { LinkedHashSet() } += sources[request.id].orEmpty()
            tagsByKind.getOrPut(request.kind) { LinkedHashSet() } += canonicalTag(request.tag)
        }
        val payloads = LinkedHashMap<GeoAssetIndex.Kind, Map<String, GeoAssetIndex.GeoPayload>>()
        filesByKind.forEach { (kind, files) ->
            val extracted = GeoAssetIndex.extractPayloads(
                kind = kind,
                tags = tagsByKind[kind].orEmpty(),
                files = files.toList(),
                limit = maxItems
            )
            if (extracted != null) payloads[kind] = extracted
        }

        // Pass three: write the sets, in the caller's order, so a config built twice is identical.
        for (request in pending) {
            val target = targets.getValue(request.id)
            val payload = payloads[request.kind]?.get(canonicalTag(request.tag))
            if (payload == null) {
                skipped++
                notes += "geo routing: '${labelOf(request)}' could not be read from the local geo " +
                    "assets, so its rule is not applied on the sing-box engine"
                continue
            }
            if (payload.isEmpty || payload.reverseMatch) {
                skipped++
                notes += if (payload.reverseMatch) {
                    "geo routing: '${labelOf(request)}' is a reverse-match list; sing-box rule sets " +
                        "cannot express an inverse match, so this rule is not applied"
                } else {
                    "geo routing: '${labelOf(request)}' is empty in the local geo assets"
                }
                continue
            }
            val json = encode(payload)
            if (json.toByteArray(Charsets.UTF_8).size > maxBytes) {
                skipped++
                notes += "geo routing: '${labelOf(request)}' is larger than " +
                    "${maxBytes / (1024L * 1024L)} MB as a sing-box rule set"
                continue
            }
            val staged = File(outputDir, "${target.name}.tmp")
            try {
                staged.writeText(json, Charsets.UTF_8)
                if (!staged.renameTo(target)) {
                    staged.delete()
                    runCatching { target.writeText(json, Charsets.UTF_8) }
                }
            } finally {
                staged.delete()
            }
            if (!target.isFile || target.length() == 0L) {
                skipped++
                continue
            }
            paths[request.id] = target.absolutePath
            generated++
            if (payload.truncated) {
                notes += "geo routing: '${labelOf(request)}' was truncated at $maxItems entries so " +
                    "the tunnel can start on a small device"
            }
        }

        runCatching {
            outputDir.listFiles()?.forEach { stale ->
                if (stale.isFile && stale.name.endsWith(".json") &&
                    stale.absolutePath !in produced
                ) {
                    stale.delete()
                }
            }
        }

        return Plan(paths = paths, notes = notes, generated = generated, reused = reused, skipped = skipped)
    }

    private fun filesFor(snapshot: GeoAssetIndex.Snapshot?, request: Request): List<File> {
        val live = snapshot ?: return emptyList()
        val names = live.filesFor(request.kind, canonicalTag(request.tag))
        if (names.isEmpty()) return emptyList()
        val byName = live.files.filter { it.kind == request.kind }.associateBy { it.name }
        return names.mapNotNull { name -> byName[name]?.path?.takeIf { it.isNotBlank() } }
            .map(::File)
            .filter { it.isFile }
    }

    private fun digestOf(request: Request, files: List<File>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("${MARKER}:${request.kind.name}:${request.tag}".toByteArray(Charsets.UTF_8))
        files.forEach { file ->
            digest.update(file.absolutePath.toByteArray(Charsets.UTF_8))
            digest.update(file.lastModified().toString().toByteArray(Charsets.UTF_8))
            digest.update(file.length().toString().toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun encode(payload: GeoAssetIndex.GeoPayload): String {
        val rules = JSONArray()
        val domains = JSONObject()
        if (payload.full.isNotEmpty()) domains.put("domain", JSONArray(payload.full))
        if (payload.suffix.isNotEmpty()) domains.put("domain_suffix", JSONArray(payload.suffix))
        if (payload.keywords.isNotEmpty()) domains.put("domain_keyword", JSONArray(payload.keywords))
        if (payload.regexes.isNotEmpty()) domains.put("domain_regex", JSONArray(payload.regexes))
        if (domains.length() > 0) rules.put(domains)
        if (payload.cidrs.isNotEmpty()) rules.put(JSONObject().put("ip_cidr", JSONArray(payload.cidrs)))
        return JSONObject()
            .put("version", 3)
            .put("rules", rules)
            .toString()
    }

    fun labelOf(request: Request): String =
        (if (request.kind == GeoAssetIndex.Kind.GEOIP) "geoip:" else "geosite:") + request.tag

    private fun split(raw: String): List<String> =
        raw.split(',', '|', '\n', ';').map(String::trim).filter(String::isNotBlank)
}
