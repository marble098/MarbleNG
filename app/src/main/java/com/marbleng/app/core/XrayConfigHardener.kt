package com.marbleng.app.core

import com.marbleng.app.model.RouteAction
import com.marbleng.app.model.RoutingSpec
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigHardener {
    private val infra = setOf("freedom", "blackhole", "dns", "loopback")

    /**
     * Produces a hardened runtime config.
     *
     * @param routing when null (or with no direct-producing rules) the result is
     *   fully fail-closed: every packet leaves through the proxy, no freedom
     *   outbound exists. When a [RoutingSpec] asks to bypass LAN or carries a
     *   DIRECT rule, a single `direct` freedom outbound is added and the routing
     *   table is expanded — an explicit, user-driven relaxation.
     */
    fun harden(source: String, socksPort: Int, routing: RoutingSpec? = null): String {
        val src = JSONObject(source)
        val old = src.optJSONArray("outbounds") ?: JSONArray()
        val byTag = linkedMapOf<String, JSONObject>()
        var firstTag = ""
        for (i in 0 until old.length()) {
            val orig = old.optJSONObject(i) ?: continue
            val clone = JSONObject(orig.toString())
            val proto = clone.optString("protocol")
            val tag = clone.optString("tag").ifBlank { if (proto !in infra && firstTag.isBlank()) "proxy" else "out-$i" }
            clone.put("tag", tag); byTag[tag] = clone
            if (proto !in infra && firstTag.isBlank()) firstTag = tag
        }
        require(firstTag.isNotBlank()) { "No proxy outbound" }

        val keep = linkedSetOf<String>()
        fun add(tag: String) {
            val o = byTag[tag] ?: return
            if (o.optString("protocol") in infra) return
            if (!keep.add(tag)) return
            o.optJSONObject("proxySettings")?.optString("tag")?.takeIf { it.isNotBlank() }?.let(::add)
        }
        add(firstTag)

        // Does the requested routing actually need a direct (freedom) egress?
        val enabledRules = routing?.rules?.filter { it.enabled } ?: emptyList()
        val needsDirect = routing != null && (routing.bypassLan || enabledRules.any { it.action == RouteAction.DIRECT })

        val out = JSONArray()
        keep.forEach { tag -> byTag[tag]?.let { o -> o.put("targetStrategy", "AsIs"); o.remove("sendThrough"); out.put(o) } }
        out.put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
        if (needsDirect) out.put(JSONObject().put("tag", "direct").put("protocol", "freedom").put("settings", JSONObject().put("domainStrategy", "UseIP")))

        val inbound = JSONObject().put("tag", "socks-in").put("listen", "127.0.0.1").put("port", socksPort).put("protocol", "socks")
            .put("settings", JSONObject().put("udp", true))
            .put("sniffing", JSONObject().put("enabled", true).put("routeOnly", true).put("destOverride", JSONArray(listOf("http", "tls", "quic"))))
        src.put("inbounds", JSONArray().put(inbound))
        src.put("outbounds", out)

        src.put("dns", JSONObject().put("servers", JSONArray()
            .put(JSONObject().put("address", "https://1.1.1.1/dns-query").put("queryStrategy", "UseIPv4"))
            .put(JSONObject().put("address", "https://8.8.8.8/dns-query").put("queryStrategy", "UseIPv4")))
            .put("queryStrategy", "UseIPv4").put("useSystemHosts", false).put("enableParallelQuery", true).put("tag", "xgc-dns"))

        // --- Routing table -----------------------------------------------------
        fun tagFor(a: RouteAction) = when (a) { RouteAction.PROXY -> firstTag; RouteAction.DIRECT -> "direct"; RouteAction.BLOCK -> "block" }
        val rules = JSONArray()
        rules.put(JSONObject().put("type", "field").put("inboundTag", JSONArray().put("xgc-dns")).put("outboundTag", firstTag))
        if (routing != null) {
            if (routing.bypassLan) rules.put(JSONObject().put("type", "field").put("ip", JSONArray().put("geoip:private")).put("outboundTag", "direct"))
            enabledRules.forEach { rule ->
                if (rule.domains.isEmpty() && rule.ips.isEmpty()) return@forEach
                val r = JSONObject().put("type", "field").put("outboundTag", tagFor(rule.action))
                if (rule.domains.isNotEmpty()) r.put("domain", JSONArray(rule.domains))
                if (rule.ips.isNotEmpty()) r.put("ip", JSONArray(rule.ips))
                rules.put(r)
            }
            if (routing.blockAds) rules.put(JSONObject().put("type", "field").put("domain", JSONArray().put("geosite:category-ads-all")).put("outboundTag", "block"))
        }
        // Catch-all: everything still on the socks inbound leaves through the proxy.
        rules.put(JSONObject().put("type", "field").put("inboundTag", JSONArray().put("socks-in")).put("outboundTag", firstTag))
        src.put("routing", JSONObject().put("domainStrategy", routing?.domainStrategy ?: "AsIs").put("rules", rules))

        src.put("log", JSONObject().put("loglevel", "warning"))
        listOf("api", "reverse", "metrics", "stats", "observatory", "burstObservatory", "fakedns").forEach(src::remove)
        verify(src, socksPort, firstTag, needsDirect)
        return src.toString(2)
    }

    private fun verify(o: JSONObject, port: Int, tag: String, allowDirect: Boolean) {
        val ins = o.getJSONArray("inbounds")
        require(ins.length() == 1 && ins.getJSONObject(0).getInt("port") == port && ins.getJSONObject(0).getString("listen") == "127.0.0.1")
        val outs = o.getJSONArray("outbounds")
        var direct = false; var selected = false
        for (i in 0 until outs.length()) {
            val x = outs.getJSONObject(i)
            if (x.optString("protocol") == "freedom") direct = true
            if (x.optString("tag") == tag) selected = true
        }
        require(selected) { "selected proxy outbound missing" }
        // Fail-closed unless the user explicitly enabled a direct route.
        require(allowDirect || !direct) { "privacy hardening failed" }
    }
}
