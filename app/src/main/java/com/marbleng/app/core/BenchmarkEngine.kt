package com.marbleng.app.core

import com.marbleng.app.model.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.math.ln
import kotlin.math.max

class BenchmarkEngine(private val xray:XrayManager){
    fun run(profiles:List<ProxyProfile>,settings:AppSettings,usePrecheck:Boolean=true,onProgress:(Int,Int,String)->Unit={_,_,_->}):List<BenchmarkResult>{
        val s=when(settings.benchMode){
            BenchMode.RELIABLE->settings.copy(benchCandidates=maxOf(settings.benchCandidates,30),benchSamples=maxOf(settings.benchSamples,5),benchTimeoutSec=maxOf(settings.benchTimeoutSec,10),tcpWorkers=16)
            BenchMode.BALANCED->settings
            BenchMode.FAST->settings.copy(benchCandidates=minOf(settings.benchCandidates,15),benchSamples=minOf(settings.benchSamples,3),benchTimeoutSec=minOf(settings.benchTimeoutSec,6),tcpWorkers=24)
            BenchMode.TURBO->settings.copy(benchCandidates=minOf(settings.benchCandidates,10),benchSamples=minOf(settings.benchSamples,2),benchTimeoutSec=minOf(settings.benchTimeoutSec,4),tcpWorkers=28)
            BenchMode.CUSTOM->settings
        }
        val pool=Executors.newFixedThreadPool(s.tcpWorkers.coerceIn(1,32)); val pre=profiles.map{p->pool.submit<Pair<ProxyProfile,Double>>{p to tcp(p,s.tcpPrecheckTimeoutMs)}}.mapNotNull{runCatching{it.get()}.getOrNull()};pool.shutdown()
        val maxN=s.benchCandidates.coerceAtMost(profiles.size);val candidates=if(!usePrecheck) profiles.take(maxN) else {val udp=pre.filter{it.first.scheme=="hysteria2"};val tcp=pre.filter{it.first.scheme!="hysteria2"}.sortedBy{it.second};val reserve=if(udp.isEmpty())0 else minOf(udp.size,maxOf(2,maxN/3));(tcp.take(maxN-reserve)+udp.take(reserve)).map{it.first}.take(maxN)}; val raw=mutableListOf<BenchmarkResult>()
        candidates.forEachIndexed{idx,p->onProgress(idx+1,candidates.size,p.name);val times=mutableListOf<Double>();var speed=0.0;var ok=0
            repeat(s.benchSamples){sample->val port=18080+idx*10+sample;runCatching{xray.temporary(p,port){ val r=SocksHttpClient.get(port,"cp.cloudflare.com","/generate_204",s.benchTimeoutSec*1000,32*1024);if(r.status in 200..399){times+=r.elapsedMs;ok++};if(sample==0&&r.status>0){val z=SocksHttpClient.get(port,"speed.cloudflare.com","/__down?bytes=${s.benchBytes}",s.benchTimeoutSec*1000+4000,s.benchBytes+16384);speed=max(speed,z.bytesPerSecond)}}}}
            val success=if(s.benchSamples>0)ok*100/s.benchSamples else 0;val lat=if(times.isEmpty())9999.0 else times.sorted()[times.size/2];val mean=if(times.isEmpty())9999.0 else times.average();val jit=if(times.size<2)0.0 else kotlin.math.sqrt(times.sumOf{(it-mean)*(it-mean)}/times.size)
            raw+=BenchmarkResult(p.id,p.name,success,lat,jit,speed,0.0)
        }
        if(raw.isEmpty())return raw
        fun norm(values:List<Double>,x:Double,invert:Boolean=false):Double{val lo=values.min();val hi=values.max();val v=if(hi-lo<1e-9)0.5 else (x-lo)/(hi-lo);return if(invert)1-v else v}
        val l=raw.map{minOf(it.latencyMs,10000.0)};val j=raw.map{minOf(it.jitterMs,10000.0)};val sp=raw.map{ln(1+max(0.0,it.bytesPerSecond))}
        return raw.map{r->val score=if(r.success<=0)-1.0 else r.success*.55+norm(l,minOf(r.latencyMs,10000.0),true)*20+norm(j,minOf(r.jitterMs,10000.0),true)*10+norm(sp,ln(1+max(0.0,r.bytesPerSecond)))*15;r.copy(score=score)}.sortedWith(compareByDescending<BenchmarkResult>{it.score}.thenBy{it.latencyMs})
    }
    private fun tcp(p:ProxyProfile,timeout:Int):Double{if(p.host.isBlank()||p.port<=0)return 99999.0;val t=System.nanoTime();return try{Socket().use{it.connect(InetSocketAddress(p.host,p.port),timeout)};(System.nanoTime()-t)/1e6}catch(_:Throwable){99999.0}}
}
