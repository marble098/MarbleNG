package com.marbleng.app.core

import android.net.Uri
import android.util.Base64
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.net.URLDecoder

object ProxyParser {
    private val schemes=Regex("(?i)^(vless|vmess|trojan|ss|hysteria2|hy2|socks|socks5|http|https)://")
    fun parseInput(input:String, subId:String="manual", subName:String="Manual"):List<ProxyProfile>{
        val text=decodeSubscription(input.trim())
        if(text.trimStart().startsWith("{")) return listOf(parseWholeJson(text,subId,subName))
        return text.replace("\r","\n").lines().flatMap { line ->
            val s=line.trim(); if(s.isBlank()||s.startsWith("#")) emptyList()
            else Regex("(?i)(?:vless|vmess|trojan|ss|hysteria2|hy2|socks5?|https?)://[^\\s]+")
                .findAll(s).mapNotNull { runCatching{parseUri(it.value,subId,subName)}.getOrNull() }.toList()
        }.distinctBy{it.id}
    }
    private fun decodeSubscription(s:String):String{
        if(schemes.containsMatchIn(s)||s.startsWith("{")||s.startsWith("[")) return s
        val c=s.filterNot(Char::isWhitespace); if(c.length<16)return s
        return runCatching{String(Base64.decode(pad64(c),Base64.DEFAULT)).trim()}.getOrDefault(s)
    }
    private fun pad64(s:String)=s+"=".repeat((4-s.length%4)%4)
    private fun id(raw:String)=MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).take(8).joinToString(""){"%02x".format(it)}
    private fun dec(s:String?)=URLDecoder.decode(s?:"","UTF-8")
    private fun parseWholeJson(raw:String, sid:String,sname:String):ProxyProfile{
        val o=JSONObject(raw); require(o.optJSONArray("outbounds")!=null){"Xray JSON has no outbounds"}
        val first=o.getJSONArray("outbounds").optJSONObject(0)?:JSONObject()
        return ProxyProfile(id(raw),"$sname (JSON)",first.optString("protocol","json"),raw,o.toString(),subscriptionId=sid,subscriptionName=sname)
    }
    private fun parseUri(raw:String,sid:String,sname:String):ProxyProfile = when(raw.substringBefore(":").lowercase()){
        "vless"->parseVless(raw,sid,sname); "vmess"->parseVmess(raw,sid,sname); "trojan"->parseTrojan(raw,sid,sname)
        "ss"->parseSs(raw,sid,sname); "hysteria2","hy2"->parseHy2(raw,sid,sname)
        "socks","socks5","http","https"->parseBasic(raw,sid,sname); else->error("unsupported")
    }
    private fun q(u:Uri,k:String,d:String="")=u.getQueryParameter(k)?:d
    private fun stream(u:Uri,host:String):JSONObject{
        val type=q(u,"type","tcp").lowercase(); val method=mapOf("tcp" to "raw","ws" to "websocket","splithttp" to "xhttp","kcp" to "mkcp")[type]?:type
        val st=JSONObject(); if(type in listOf("http","h2")){ st.put("network","http"); st.put("security","none");
            st.put("httpSettings",JSONObject().put("path",q(u,"path","/")).apply{q(u,"host").takeIf{it.isNotBlank()}?.let{put("host",JSONArray().put(it))}})
        } else { st.put("method",method); st.put("security","none"); val path=q(u,"path","/"); val h=q(u,"host")
            when(method){
                "xhttp"->st.put("xhttpSettings",JSONObject().put("path",path).apply{if(h.isNotBlank())put("host",h); q(u,"mode").takeIf{it.isNotBlank()}?.let{put("mode",it)}; q(u,"extra").takeIf{it.startsWith("{")}?.let{runCatching{put("extra",JSONObject(it))}}})
                "websocket"->st.put("wsSettings",JSONObject().put("path",path).apply{if(h.isNotBlank())put("host",h)})
                "httpupgrade"->st.put("httpupgradeSettings",JSONObject().put("path",path).apply{if(h.isNotBlank())put("host",h)})
                "grpc"->st.put("grpcSettings",JSONObject().apply{q(u,"serviceName",q(u,"path")).trimStart('/').takeIf{it.isNotBlank()}?.let{put("serviceName",it)};q(u,"authority").takeIf{it.isNotBlank()}?.let{put("authority",it)}})
                "raw"->st.put("rawSettings",JSONObject().put("header",JSONObject().put("type",q(u,"headerType","none"))))
            }
        }
        var sec=q(u,"security","none").lowercase(); if(sec=="xtls")sec="tls"; st.put("security",sec)
        val sni=q(u,"sni",host); val fp=q(u,"fp","chrome")
        if(sec=="tls") st.put("tlsSettings",JSONObject().put("serverName",sni).put("fingerprint",fp).apply{
            q(u,"alpn").takeIf{it.isNotBlank()}?.let{put("alpn",JSONArray(it.split(',')))}; if(q(u,"allowInsecure",q(u,"insecure")) in listOf("1","true"))put("allowInsecure",true)
        })
        if(sec=="reality") st.put("realitySettings",JSONObject().put("serverName",sni).put("fingerprint",fp).put("password",q(u,"pbk")).put("shortId",q(u,"sid")).apply{
            q(u,"spx").takeIf{it.isNotBlank()}?.let{put("spiderX",it)}; q(u,"pqv").takeIf{it.isNotBlank()}?.let{put("mldsa65Verify",it)}
        })
        q(u,"fm").takeIf{it.startsWith("{")}?.let{runCatching{st.put("finalmask",JSONObject(it))}}
        return st
    }
    private fun base(out:JSONObject)=JSONObject().put("log",JSONObject().put("loglevel","warning"))
        .put("inbounds",JSONArray().put(JSONObject().put("tag","socks-in").put("listen","127.0.0.1").put("port",10808).put("protocol","socks").put("settings",JSONObject().put("udp",true))))
        .put("outbounds",JSONArray().put(out).put(JSONObject().put("tag","block").put("protocol","blackhole")))
    private fun parseVless(raw:String,sid:String,sname:String):ProxyProfile{
        val u=Uri.parse(raw); val host=u.host?:error("host"); val port=u.port.takeIf{it>0}?:error("port"); val uid=dec(u.userInfo)
        val out=JSONObject().put("tag","proxy").put("protocol","vless").put("settings",JSONObject().put("address",host).put("port",port).put("id",uid).put("encryption",q(u,"encryption","none")).apply{q(u,"flow").takeIf{it.isNotBlank()}?.let{put("flow",it)}}).put("streamSettings",stream(u,host))
        return prof(raw,u.fragment?:"VLESS $host","vless",host,port,q(u,"type","tcp"),q(u,"security","none"),base(out),sid,sname)
    }
    private fun parseVmess(raw:String,sid:String,sname:String):ProxyProfile{
        val body=raw.removePrefix("vmess://").substringBefore('#')
        if(!body.substringBefore('?').contains('@')){
            val o=JSONObject(String(Base64.decode(pad64(body),Base64.DEFAULT))); val host=o.optString("add",o.optString("address")); val port=o.optString("port").toInt(); val uid=o.getString("id")
            val net=o.optString("net","tcp"); val sec=if(o.optString("tls").lowercase() in listOf("tls","1","true"))"tls" else "none"
            val fake=Uri.parse("vmess://$uid@$host:$port?type=${Uri.encode(net)}&security=$sec&host=${Uri.encode(o.optString("host"))}&path=${Uri.encode(o.optString("path"))}&sni=${Uri.encode(o.optString("sni"))}&fp=${Uri.encode(o.optString("fp","chrome"))}")
            val settings=JSONObject().put("address",host).put("port",port).put("id",uid).put("security",o.optString("scy","auto"))
            val out=JSONObject().put("tag","proxy").put("protocol","vmess").put("settings",settings).put("streamSettings",stream(fake,host))
            return prof(raw,o.optString("ps","VMess $host"),"vmess",host,port,net,sec,base(out),sid,sname)
        }
        val u=Uri.parse(raw); val host=u.host?:error("host"); val port=u.port; val uid=dec(u.userInfo)
        val out=JSONObject().put("tag","proxy").put("protocol","vmess").put("settings",JSONObject().put("address",host).put("port",port).put("id",uid).put("security",q(u,"encryption","auto"))).put("streamSettings",stream(u,host))
        return prof(raw,u.fragment?:"VMess $host","vmess",host,port,q(u,"type","tcp"),q(u,"security","none"),base(out),sid,sname)
    }
    private fun parseTrojan(raw:String,sid:String,sname:String):ProxyProfile{
        val u=Uri.parse(raw); val host=u.host?:error("host"); val port=u.port; val pass=dec(u.userInfo); val rebuilt=if(q(u,"security").isBlank()) Uri.parse(raw.replace("?","?security=tls&")) else u
        val out=JSONObject().put("tag","proxy").put("protocol","trojan").put("settings",JSONObject().put("address",host).put("port",port).put("password",pass)).put("streamSettings",stream(rebuilt,host))
        return prof(raw,u.fragment?:"Trojan $host","trojan",host,port,q(u,"type","tcp"),q(rebuilt,"security","tls"),base(out),sid,sname)
    }
    private fun parseSs(raw:String,sid:String,sname:String):ProxyProfile{
        val u=Uri.parse(raw); require(q(u,"plugin").isBlank()){ "SS plugin not native Xray" }
        var host=u.host; var port=u.port; var method=""; var pass=""
        if(host!=null && u.userInfo?.contains(':')==true){ val c=dec(u.userInfo).split(':',limit=2); method=c[0];pass=c[1] }
        else { val body=raw.removePrefix("ss://").substringBefore('#').substringBefore('?'); val decoded=String(Base64.decode(pad64(body),Base64.DEFAULT)); val du=Uri.parse("ss://$decoded");host=du.host;port=du.port; val c=dec(du.userInfo).split(':',limit=2);method=c[0];pass=c[1] }
        require(host!=null && port>0 && method.isNotBlank())
        val out=JSONObject().put("tag","proxy").put("protocol","shadowsocks").put("settings",JSONObject().put("address",host).put("port",port).put("method",method).put("password",pass))
        return prof(raw,u.fragment?:"SS $host","ss",host!!,port,"native","none",base(out),sid,sname)
    }
    private fun parseHy2(raw:String,sid:String,sname:String):ProxyProfile{
        val u=Uri.parse(raw); val host=u.host?:error("host"); val port=u.port.takeIf{it>0}?:443; val auth=dec(u.userInfo)
        val tls=JSONObject().put("serverName",q(u,"sni",host)).put("fingerprint",q(u,"fp","chrome")).put("alpn",JSONArray().put("h3")).apply{if(q(u,"insecure") in listOf("1","true"))put("allowInsecure",true)}
        val st=JSONObject().put("method","hysteria").put("security","tls").put("tlsSettings",tls).put("hysteriaSettings",JSONObject().put("version",2).put("auth",auth))
        val out=JSONObject().put("tag","proxy").put("protocol","hysteria").put("settings",JSONObject().put("version",2).put("address",host).put("port",port)).put("streamSettings",st)
        return prof(raw,u.fragment?:"Hysteria2 $host","hysteria2",host,port,"hysteria","tls",base(out),sid,sname)
    }
    private fun parseBasic(raw:String,sid:String,sname:String):ProxyProfile{
        val u=Uri.parse(raw); val host=u.host?:error("host"); val scheme=u.scheme!!.lowercase()
        val port=u.port.takeIf{it>0}?:when(scheme){"https"->443;"http"->80;else->1080}
        val user=dec(u.userInfo).substringBefore(':'); val pass=dec(u.userInfo).substringAfter(':',"")
        val protocol=if(scheme.startsWith("socks"))"socks" else "http"
        val settings=JSONObject().put("address",host).put("port",port)
        if(user.isNotBlank()){settings.put("user",user);settings.put("pass",pass)}
        val out=JSONObject().put("tag","proxy").put("protocol",protocol).put("settings",settings)
        if(scheme=="https"){
            out.put("streamSettings",JSONObject().put("method","raw").put("security","tls").put("tlsSettings",JSONObject().put("serverName",host)))
        }
        return prof(raw,"${protocol.uppercase()} $host",scheme,host,port,"native",if(scheme=="https")"tls" else "none",base(out),sid,sname)
    }
    private fun prof(raw:String,name:String,scheme:String,host:String,port:Int,transport:String,security:String,cfg:JSONObject,sid:String,sname:String)=
        ProxyProfile(id(raw),dec(name).take(120),scheme,raw,cfg.toString(),host,port,transport,security,sid,sname)
}
