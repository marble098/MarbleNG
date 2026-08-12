package com.marbleng.app.net

import com.marbleng.app.core.SocksHttpClient
import org.json.JSONArray

data class PrivacyReport(val proxyIp:String,val cloudflareLocation:String,val dnsServers:String,val healthy:Boolean,val note:String)
object PrivacyAuditor{
    fun audit(port:Int):PrivacyReport{
        val trace=runCatching{String(SocksHttpClient.get(port,"www.cloudflare.com","/cdn-cgi/trace").body)}.getOrDefault("")
        val ip=Regex("(?m)^ip=(.+)$").find(trace)?.groupValues?.get(1)?.trim().orEmpty();val loc=Regex("(?m)^loc=(.+)$").find(trace)?.groupValues?.get(1)?.trim().orEmpty()
        val id=runCatching{String(SocksHttpClient.get(port,"bash.ws","/id").body).filter(Char::isDigit).take(16)}.getOrDefault("")
        if(id.isNotBlank()) for(i in 1..6) runCatching{SocksHttpClient.get(port,"$i.$id.bash.ws","/",5000,4096)}
        val dns=if(id.isBlank())"inconclusive" else runCatching{String(SocksHttpClient.get(port,"bash.ws","/dnsleak/test/$id?json",10000,256000).body)}.mapCatching{s->val a=JSONArray(s);(0 until a.length()).mapNotNull{a.optJSONObject(it)}.filter{it.optString("type")=="dns"}.joinToString{it.optString("ip","?")+" / "+it.optString("country_name","?")}}.getOrDefault("inconclusive")
        return PrivacyReport(ip,loc,dns,ip.isNotBlank(),if(ip.isBlank())"Proxy egress could not be verified" else "All diagnostic requests used SOCKS5h; no direct management probe was sent.")
    }
}
