package com.marbleng.app.net

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object CloudflareWorker{
    data class Result(val ok:Boolean,val message:String,val workerUrl:String="")
    fun deploy(apiToken:String,accountId:String,scriptName:String,accessKey:String):Result{
        require(apiToken.isNotBlank())
        val resolvedAccount=accountId.ifBlank {
            val a=URL("https://api.cloudflare.com/client/v4/accounts?per_page=1").openConnection() as HttpURLConnection;a.setRequestProperty("Authorization","Bearer $apiToken")
            val body=runCatching{a.inputStream.bufferedReader().readText()}.getOrDefault("");runCatching{JSONObject(body).getJSONArray("result").getJSONObject(0).getString("id")}.getOrDefault("")
        };require(resolvedAccount.isNotBlank()){ "Cloudflare account could not be discovered" }
        val name=scriptName.ifBlank{"marbleng-radar"}.replace(Regex("[^a-zA-Z0-9-]"),"-").lowercase();val code=workerSource(accessKey)
        val u=URL("https://api.cloudflare.com/client/v4/accounts/$resolvedAccount/workers/scripts/$name");val c=u.openConnection() as HttpURLConnection
        c.requestMethod="PUT";c.doOutput=true;c.connectTimeout=12000;c.readTimeout=20000;c.setRequestProperty("Authorization","Bearer $apiToken");c.setRequestProperty("Content-Type","application/javascript")
        c.outputStream.use{it.write(code.toByteArray())};val body=(if(c.responseCode in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty();if(c.responseCode !in 200..299)return Result(false,"Cloudflare ${c.responseCode}: ${body.take(220)}")
        val sub=URL("https://api.cloudflare.com/client/v4/accounts/$resolvedAccount/workers/subdomain").openConnection() as HttpURLConnection;sub.setRequestProperty("Authorization","Bearer $apiToken");val sb=runCatching{sub.inputStream.bufferedReader().readText()}.getOrDefault("");val subdomain=runCatching{JSONObject(sb).getJSONObject("result").getString("subdomain")}.getOrDefault("")
        return Result(true,"Worker deployed",if(subdomain.isBlank())"" else "https://$name.$subdomain.workers.dev")
    }
    private fun workerSource(key:String)="""addEventListener('fetch', event => event.respondWith(handle(event.request))); async function handle(request) { const u=new URL(request.url); if(u.searchParams.get('key')!==${JSONObject.quote(key)}) return new Response('Forbidden',{status:403}); if(u.pathname==='/health') return new Response(JSON.stringify({ok:true,service:'MarbleNG Radar'}),{headers:{'content-type':'application/json'}}); const channel=(u.searchParams.get('channel')||'').replace(/^@/,''); if(!channel) return new Response('channel required',{status:400}); const r=await fetch('https://t.me/s/'+encodeURIComponent(channel),{headers:{'User-Agent':'Mozilla/5.0'}}); const t=await r.text(); const m=[...t.matchAll(/(?:vless|vmess|trojan|ss|hysteria2|hy2):\/\/[^\s<>&quot;]+/gi)].map(x=>x[0].replaceAll('&amp;','&')); return new Response([...new Set(m)].join('\n'),{headers:{'content-type':'text/plain; charset=utf-8','cache-control':'no-store'}}); }"""
}
