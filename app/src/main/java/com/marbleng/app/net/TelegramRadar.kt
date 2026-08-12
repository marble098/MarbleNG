package com.marbleng.app.net

import com.marbleng.app.core.SocksHttpClient
import java.net.HttpURLConnection
import java.net.URL

object TelegramRadar{
    private val uri=Regex("(?i)(?:vless|vmess|trojan|ss|hysteria2|hy2)://[^\\s<>&quot;]+")
    fun fetch(channel:String,proxyPort:Int?=null,max:Int=80):List<String>{
        val c=channel.trim().removePrefix("@").substringAfterLast("t.me/").substringBefore('/')
        val html=if(proxyPort!=null)String(SocksHttpClient.get(proxyPort,"t.me","/s/$c",12000,2_000_000).body) else (URL("https://t.me/s/$c").openConnection() as HttpURLConnection).run{connectTimeout=8000;readTimeout=12000;inputStream.bufferedReader().readText()}
        val decoded=html.replace("&amp;","&").replace("&#38;","&").replace("&quot;","\"").replace("&#x3D;","=")
        return uri.findAll(decoded).map{it.value}.distinct().take(max).toList()
    }
}
