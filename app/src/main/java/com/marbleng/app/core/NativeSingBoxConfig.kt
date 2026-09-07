package com.marbleng.app.core

import org.json.JSONArray
import org.json.JSONObject

/** Native sing-box imports keep their proxy graph, rather than being misread as empty Xray
 * outbounds. Runtime-owned inbounds/routing are deliberately supplied by Marble for both cores. */
object NativeSingBoxConfig {
    private val infrastructure = setOf("direct", "block", "dns")

    fun isNative(root: JSONObject): Boolean = root.optJSONArray("outbounds")?.let { list ->
        (0 until list.length()).any { list.optJSONObject(it)?.optString("type").orEmpty().isNotBlank() }
    } ?: root.optString("type").isNotBlank()

    fun root(value: JSONObject): JSONObject = if (value.has("outbounds")) JSONObject(value.toString())
        else JSONObject().put("outbounds", JSONArray().put(JSONObject(value.toString())))

    fun entry(root: JSONObject): JSONObject {
        val objects = objects(root(root).getJSONArray("outbounds"))
        require(objects.none { it.has("protocol") }) { "config-unsupported: outbounds: mixed Xray/sing-box document" }
        val finalTag = root.optJSONObject("route")?.optString("final").orEmpty()
        if (finalTag.isNotBlank()) {
            return objects.firstOrNull { it.optString("tag") == finalTag }
                ?: throw ConfigTranslationException("route.final", "selected outbound '$finalTag' is missing")
        }
        return objects.firstOrNull { it.optString("type") !in infrastructure }
            ?: throw ConfigTranslationException("outbounds", "no proxy outbound")
    }

    private fun names(document: JSONObject): Map<String, String> {
        val selected = entry(document)
        val objects = objects(root(document).getJSONArray("outbounds"))
        val all = objects + objects(document.optJSONArray("endpoints"))
        val tags = all.map { it.optString("tag") }
        require(tags.filter { it.isNotBlank() }.distinct().size == tags.count { it.isNotBlank() }) {
            "config-unsupported: outbounds.tag: duplicate tags"
        }
        return all.associate { item ->
            val tag = item.optString("tag")
            tag to if (item.toString() == selected.toString()) SingBoxConfigBuilder.PROXY_TAG else "import-$tag"
        }
    }

    fun outbounds(document: JSONObject): List<JSONObject> {
        val source = root(document)
        val names = names(source)
        val selected = entry(source)
        val result = objects(source.getJSONArray("outbounds")).mapIndexed { index, item ->
            val originalTag = item.optString("tag")
            item.put("tag", if (item === selected || item.toString() == selected.toString()) SingBoxConfigBuilder.PROXY_TAG
                else names[originalTag] ?: "import-$index")
            rewriteReferences(item, names)
            item
        }
        val tags = result.map { it.getString("tag") }.toSet() + objects(source.optJSONArray("endpoints")).map { names[it.optString("tag")] }
        val edges = result.associate { outbound ->
            val refs = listOfNotNull(outbound.optString("detour").takeIf { it.isNotBlank() }) +
                strings(outbound.optJSONArray("outbounds"))
            refs.forEach { require(it in tags) { "config-unsupported: outbounds: missing referenced outbound '$it'" } }
            outbound.getString("tag") to refs
        }
        fun visit(tag: String, stack: Set<String>) {
            require(tag !in stack) { "config-unsupported: outbounds: cyclic dependency at '$tag'" }
            edges[tag].orEmpty().forEach { visit(it, stack + tag) }
        }
        visit(SingBoxConfigBuilder.PROXY_TAG, emptySet())
        return result
    }

    fun copyResources(document: JSONObject, target: JSONObject) {
        val names = names(document)
        // Outbound resource references must not dangle after Marble supplies its runtime shell.
        listOf("endpoints", "certificate", "certificate_providers").forEach { key ->
            document.opt(key)?.let { value ->
                val copy = when (value) {
                    is JSONArray -> JSONArray(value.toString())
                    is JSONObject -> JSONObject(value.toString())
                    else -> value
                }
                rewriteReferences(copy, names)
                if (key == "endpoints" && copy is JSONArray) objects(copy).forEach { endpoint ->
                    val old = endpoint.optString("tag")
                    endpoint.put("tag", names[old] ?: old)
                }
                target.put(key, copy)
            }
        }
        // A native per-dial resolver may name a native DNS transport. Keep these transports and
        // their detours, but never overwrite the application's bootstrap/final resolver graph.
        document.optJSONObject("dns")?.optJSONArray("servers")?.let { servers ->
            val destination = target.getJSONObject("dns").getJSONArray("servers")
            val reserved = objects(destination).map { it.optString("tag") }.toSet()
            objects(JSONArray(servers.toString())).forEach { server ->
                require(server.optString("tag") !in reserved) { "config-unsupported: dns.servers.tag: reserved Marble DNS tag" }
                rewriteReferences(server, names)
                destination.put(server)
            }
        }
        document.optJSONArray("http_clients")?.let { clients ->
            val destination = target.getJSONArray("http_clients")
            objects(JSONArray(clients.toString())).forEach { client ->
                require(client.optString("tag") != SingBoxConfigBuilder.HTTP_CLIENT_DIRECT_TAG) { "config-unsupported: http_clients.tag: reserved tag" }
                rewriteReferences(client, names)
                destination.put(client)
            }
        }
    }

    private fun rewriteReferences(value: Any, names: Map<String, String>) {
        when (value) {
            is JSONObject -> value.keys().asSequence().toList().forEach { key ->
                val child = value.get(key)
                when {
                    key in setOf("detour", "outbound", "default") && child is String -> names[child]?.let { value.put(key, it) }
                    key == "outbounds" && child is JSONArray -> for (i in 0 until child.length()) {
                        names[child.optString(i)]?.let { child.put(i, it) }
                    }
                    else -> rewriteReferences(child, names)
                }
            }
            is JSONArray -> for (i in 0 until value.length()) rewriteReferences(value.get(i), names)
        }
    }

    internal fun objects(array: JSONArray?): List<JSONObject> = if (array == null) emptyList()
        else (0 until array.length()).mapNotNull(array::optJSONObject)
    private fun strings(array: JSONArray?): List<String> = if (array == null) emptyList()
        else (0 until array.length()).map(array::getString)
}
