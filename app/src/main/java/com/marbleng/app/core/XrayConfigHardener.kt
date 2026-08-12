package com.marbleng.app.core

import org.json.JSONArray
import org.json.JSONObject

object XrayConfigHardener {
    private val infra=setOf("freedom","blackhole","dns","loopback")
    fun harden(source:String, socksPort:Int):String {
        val src=JSONObject(source); val old=src.optJSONArray("outbounds")?:JSONArray(); val byTag=linkedMapOf<String,JSONObject>()
        var firstTag=""
        for(i in 0 until old.length()){
            val orig=old.optJSONObject(i)?:continue; val clone=JSONObject(orig.toString()); val proto=clone.optString("protocol")
            val tag=clone.optString("tag").ifBlank { if(proto !in infra && firstTag.isBlank()) "proxy" else "out-$i" };clone.put("tag",tag);byTag[tag]=clone
            if(proto !in infra && firstTag.isBlank())firstTag=tag
        }
        require(firstTag.isNotBlank()){ "No proxy outbound" }
        val keep=linkedSetOf<String>()
        fun add(tag:String){val o=byTag[tag]?:return;if(o.optString("protocol") in infra)return;if(!keep.add(tag))return;o.optJSONObject("proxySettings")?.optString("tag")?.takeIf{it.isNotBlank()}?.let(::add)}
        add(firstTag)
        val out=JSONArray();keep.forEach{tag->byTag[tag]?.let{o->o.put("targetStrategy","AsIs");o.remove("sendThrough");out.put(o)}}
        out.put(JSONObject().put("tag","block").put("protocol","blackhole"))
        val inbound=JSONObject().put("tag","socks-in").put("listen","127.0.0.1").put("port",socksPort).put("protocol","socks")
            .put("settings",JSONObject().put("udp",true)).put("sniffing",JSONObject().put("enabled",true).put("routeOnly",true).put("destOverride",JSONArray(listOf("http","tls","quic"))))
        src.put("inbounds",JSONArray().put(inbound)); src.put("outbounds",out)
        src.put("dns",JSONObject().put("servers",JSONArray().put(JSONObject().put("address","https://1.1.1.1/dns-query").put("queryStrategy","UseIPv4"))
            .put(JSONObject().put("address","https://8.8.8.8/dns-query").put("queryStrategy","UseIPv4"))).put("queryStrategy","UseIPv4")
            .put("useSystemHosts",false).put("enableParallelQuery",true).put("tag","xgc-dns"))
        src.put("routing",JSONObject().put("domainStrategy","AsIs").put("rules",JSONArray()
            .put(JSONObject().put("type","field").put("inboundTag",JSONArray().put("xgc-dns")).put("outboundTag",firstTag))
            .put(JSONObject().put("type","field").put("inboundTag",JSONArray().put("socks-in")).put("outboundTag",firstTag))))
        src.put("log",JSONObject().put("loglevel","warning"))
        listOf("api","reverse","metrics","stats","observatory","burstObservatory","fakedns").forEach(src::remove)
        verify(src,socksPort,firstTag); return src.toString(2)
    }
    private fun verify(o:JSONObject,port:Int,tag:String){
        val ins=o.getJSONArray("inbounds"); require(ins.length()==1 && ins.getJSONObject(0).getInt("port")==port && ins.getJSONObject(0).getString("listen")=="127.0.0.1")
        val outs=o.getJSONArray("outbounds"); var direct=false; var selected=false
        for(i in 0 until outs.length()){val x=outs.getJSONObject(i);if(x.optString("protocol")=="freedom")direct=true;if(x.optString("tag")==tag)selected=true}
        require(!direct && selected){"privacy hardening failed"}
    }
}
