package com.marbleng.app

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.*
import com.marbleng.app.core.*
import com.marbleng.app.data.AppStore
import com.marbleng.app.data.SecretStore
import com.marbleng.app.model.*
import com.marbleng.app.net.*
import com.marbleng.app.vpn.MarbleVpnService
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

class AppRepository(private val context:Context,val xray:XrayManager){
    private val store=AppStore(context);private val secrets=SecretStore(context);private val io=Executors.newCachedThreadPool()
    val profiles=mutableStateListOf<ProxyProfile>().apply{addAll(store.loadProfiles())};val subscriptions=mutableStateListOf<Subscription>().apply{addAll(store.loadSubscriptions())};val history=mutableStateListOf<ConnectionRecord>().apply{addAll(store.loadHistory())}
    var settings by mutableStateOf(store.settings());private set;var state by mutableStateOf("DISCONNECTED");private set;var stateDetail by mutableStateOf("");private set;var busy by mutableStateOf(false);private set;var message by mutableStateOf("");private set
    var benchmarks by mutableStateOf<List<BenchmarkResult>>(emptyList());private set;var privacy by mutableStateOf<PrivacyReport?>(null);private set;var radarConfigs by mutableStateOf<List<String>>(emptyList());private set;var radarResults by mutableStateOf<List<BenchmarkResult>>(emptyList());private set
    fun profile(id:String)=profiles.firstOrNull{it.id==id}
    fun setRuntimeState(s:String,d:String){state=s;stateDetail=d}
    fun updateSettings(v:AppSettings){settings=v;store.saveSettings(v)}
    fun addSubscription(name:String,url:String){val id=sha(url).take(12);subscriptions.removeAll{it.id==id};subscriptions+=Subscription(id,name.ifBlank{"Subscription"},url,System.currentTimeMillis());store.saveSubscriptions(subscriptions);refresh(id)}
    fun refresh(id:String){val sub=subscriptions.firstOrNull{it.id==id}?:return;task("Refreshing ${sub.name}"){val text=http(sub.url);val parsed=ProxyParser.parseInput(text,sub.id,sub.name);profiles.removeAll{it.subscriptionId==sub.id};profiles.addAll(parsed);store.saveProfiles(profiles);message="${parsed.size} profiles imported"}}
    fun refreshAll(){task("Refreshing subscriptions"){subscriptions.forEach{sub->runCatching{val parsed=ProxyParser.parseInput(http(sub.url),sub.id,sub.name);profiles.removeAll{it.subscriptionId==sub.id};profiles.addAll(parsed)}};store.saveProfiles(profiles);message="Subscriptions refreshed"}}
    fun importText(text:String,name:String="Manual"){task("Importing"){val parsed=ProxyParser.parseInput(text,"manual",name);profiles.addAll(parsed.filter{p->profiles.none{it.id==p.id}});store.saveProfiles(profiles);message="${parsed.size} profiles imported"}}
    fun removeSubscription(id:String){subscriptions.removeAll{it.id==id};profiles.removeAll{it.subscriptionId==id};store.saveSubscriptions(subscriptions);store.saveProfiles(profiles)}
    fun removeProfile(id:String){profiles.removeAll{it.id==id};store.saveProfiles(profiles)}
    fun lastProfile()=profile(store.lastProfileId())
    fun auto(onConnect:(ProxyProfile)->Unit){lastProfile()?.let(onConnect)?:smart(onConnect)}
    fun markConnected(p:ProxyProfile){state="CONNECTED";stateDetail=p.name;if(settings.rememberLast)store.setLastProfileId(p.id);history+=ConnectionRecord(p.id,p.name,System.currentTimeMillis(),"connected");store.saveHistory(history)}
    fun startVpn(p:ProxyProfile){val i=Intent(context,MarbleVpnService::class.java).setAction(MarbleVpnService.ACTION_START).putExtra(MarbleVpnService.EXTRA_PROFILE,p.id);if(Build.VERSION.SDK_INT>=26)context.startForegroundService(i) else context.startService(i)}
    fun stopVpn(){context.startService(Intent(context,MarbleVpnService::class.java).setAction(MarbleVpnService.ACTION_STOP))}
    fun smart(onBest:(ProxyProfile)->Unit){task("Benchmarking"){val r=BenchmarkEngine(xray).run(profiles.toList(),settings){a,b,n->message="Testing $a/$b • $n"};benchmarks=r;val best=r.firstOrNull{it.success>0}?.let{profile(it.profileId)};message=if(best==null)"No working candidate" else "Best: ${best.name}";best?.let{android.os.Handler(android.os.Looper.getMainLooper()).post{onBest(it)}}}}
    fun fullTest(p:ProxyProfile){task("Full test ${p.name}"){benchmarks=BenchmarkEngine(xray).run(listOf(p),settings.copy(benchCandidates=1));message=benchmarks.firstOrNull()?.let{"${it.success}% • ${"%.0f".format(it.latencyMs)} ms • ${"%.1f".format(it.score)}"}?:"Test failed"}}
    fun audit(){task("Privacy audit"){privacy=PrivacyAuditor.audit(settings.socksPort);message=privacy?.note.orEmpty()}}
    fun googleAi(){task("Google AI check"){val r=SocksHttpClient.get(settings.socksPort,"gemini.google.com","/",10000,64000);message="Gemini reachability HTTP ${r.status} • ${"%.0f".format(r.elapsedMs)} ms"}}
    fun telegram(channel:String){task("Telegram radar • fetch"){
        val saved=channels();val normalized=channel.trim();if(normalized.isNotBlank()&&!saved.contains(normalized)){saved.add(normalized);saveChannels(saved)}
        val out=TelegramRadar.fetch(channel,if(state=="CONNECTED")settings.socksPort else null,settings.telegramMaxConfigs)
        val candidates=ProxyParser.parseInput(out.joinToString("\n"),"telegram","Telegram Radar")
        if(candidates.isEmpty()){radarConfigs=emptyList();radarResults=emptyList();message="No supported configs found";return@task}
        message="Telegram radar • tunnel lab ${candidates.size} configs"
        val testSettings=settings.copy(benchCandidates=minOf(settings.telegramMaxConfigs,candidates.size),benchSamples=settings.telegramTcpSamples.coerceIn(1,6))
        val results=BenchmarkEngine(xray).run(candidates,testSettings,settings.telegramTcpGate){a,b,n->message="Radar tunnel test $a/$b • $n"}
        radarResults=results
        val passed=results.filter{it.success>=settings.telegramPassMinSuccess}.map{it.profileId}.toSet()
        radarConfigs=candidates.filter{it.id in passed}.map{it.raw}
        if(settings.telegramAutoSub && radarConfigs.isNotEmpty()){profiles.removeAll{it.subscriptionId=="telegram-passed"};profiles.addAll(candidates.filter{it.id in passed}.map{it.copy(subscriptionId="telegram-passed",subscriptionName="Telegram Passed")});store.saveProfiles(profiles)}
        message="Radar: ${out.size} found • ${radarConfigs.size} passed ≥${settings.telegramPassMinSuccess}%"
    }}
    fun importRadar(){importText(radarConfigs.joinToString("\n"),"Telegram Radar")}
    fun channels()=store.channels();fun saveChannels(v:List<String>)=store.saveChannels(v)
    fun cloudflareToken()=secrets.get("cfToken");fun cloudflareAccount()=secrets.get("cfAccount");fun cloudflareKey()=secrets.get("cfAccessKey")
    fun deployWorker(token:String,account:String,script:String,key:String){task("Deploying Cloudflare Worker"){val r=CloudflareWorker.deploy(token,account,script,key);if(r.ok){secrets.put("cfToken",token);secrets.put("cfAccount",account);secrets.put("cfAccessKey",key)};message=r.message+(if(r.workerUrl.isNotBlank())" • ${r.workerUrl}" else "")}}
    fun forgetCloudflare(){secrets.put("cfToken","");secrets.put("cfAccount","");secrets.put("cfAccessKey","");message="Cloudflare credentials removed from Android Keystore-backed storage"}
    fun resetSettings(){updateSettings(AppSettings());message="Settings reset; privacy guard remains mandatory"}
    fun capabilities():String="""Xray native inputs: VLESS, VMess (legacy + URI), Trojan, Shadowsocks, Hysteria2, SOCKS, HTTP/HTTPS basic proxies, whole Xray JSON.\nTransports preserved: raw/TCP, WebSocket, XHTTP/SplitHTTP, HTTPUpgrade, gRPC, HTTP/H2, mKCP when present in Xray JSON/URI.\nSecurity preserved: TLS, REALITY, fingerprints, ALPN, ECH/final-mask fields when supplied.\nAndroid transport: VpnService → HEV SOCKS5 TUN → localhost Xray SOCKS5h.\nABIs: arm64-v8a, armeabi-v7a, x86_64, x86."""
    fun doctor():String{val checks=mutableListOf<String>();checks+=(if(java.io.File(context.applicationInfo.nativeLibraryDir,"libxray.so").exists())"✔ Xray native binary" else "✖ Xray native binary missing");checks+=(if(runCatching{System.loadLibrary("marbleng")}.isSuccess)"✔ HEV/JNI bridge" else "✖ HEV/JNI bridge");checks+=(if(profiles.isNotEmpty())"✔ ${profiles.size} profiles" else "⚠ No profiles yet");checks+="✔ SOCKS locked to 127.0.0.1:${settings.socksPort}";checks+="✔ Direct/freedom fallback removed by runtime hardener";checks+="✔ Android VPN TUN capture enabled";return checks.joinToString("\n")}
    fun clearMessage(){message=""}
    fun readLogs():String=runCatching{xray.logFile.readText().takeLast(32000)}.getOrDefault("No logs yet")
    fun coreLock():String=runCatching{context.assets.open("core-lock.json").bufferedReader().readText()}.getOrDefault("{}")
    fun checkCoreUpdates(){task("Checking cores"){val current=org.json.JSONObject(coreLock());val xr=current.getJSONObject("xray").getString("tag");val hv=current.getJSONObject("hev").getString("tag");val xJson=http("https://api.github.com/repos/XTLS/Xray-core/releases?per_page=20");val xa=org.json.JSONArray(xJson);var latestX=xr;for(i in 0 until xa.length()){val o=xa.getJSONObject(i);if(!o.optBoolean("draft")&&o.optBoolean("prerelease")){latestX=o.optString("tag_name");break}};val h=org.json.JSONObject(http("https://api.github.com/repos/heiher/hev-socks5-tunnel/releases/latest")).optString("tag_name",hv);message=if(latestX==xr&&h==hv)"Cores are current ($xr / $hv)" else "Update available: Xray $latestX • HEV $h. GitHub core-update workflow will rebuild a signed APK."}}
    private fun task(label:String,block:()->Unit){if(busy)return;busy=true;message=label;io.execute{try{block()}catch(t:Throwable){message="${t::class.simpleName}: ${t.message}"}finally{busy=false}}}
    private fun http(url:String):String{val c=URL(url).openConnection() as HttpURLConnection;c.connectTimeout=12000;c.readTimeout=30000;c.setRequestProperty("User-Agent","MarbleNG/1");return c.inputStream.bufferedReader().readText()}
    private fun sha(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
}
