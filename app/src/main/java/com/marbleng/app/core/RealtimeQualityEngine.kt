package com.marbleng.app.core

import com.marbleng.app.model.BenchMode
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.WorkloadProfile
import kotlin.math.exp
import kotlin.math.ln

data class RealtimeEvidence(
    val successPercent:Double,val medianRttMs:Double,val p95RttMs:Double,
    val jitterMs:Double,val p95JitterMs:Double,val lossPercent:Double,val spikePercent:Double,
    val bytesPerSecond:Double=0.0,val udpSuccessPercent:Double=0.0,
    val loadedLatencyMs:Double=0.0,val samples:Int=0)

data class RealtimeScore(val selected:Double,val interactive:Double,val streaming:Double,
    val stability:Double,val resilience:Double,val reliabilityAxis:Double,val latencyAxis:Double,
    val jitterAxis:Double,val tailAxis:Double,val lossAxis:Double)

/** Shared jitter-first quality model. MARBLE_REALTIME_ENGINE_V70 */
object RealtimeQualityEngine {
    fun score(r:BenchmarkResult,w:WorkloadProfile,b:BenchMode=BenchMode.BALANCED)=score(
        RealtimeEvidence(r.success.toDouble(),r.latencyMs,
            r.p95LatencyMs.takeIf{it>0}?:r.p90LatencyMs.takeIf{it>0}?:r.latencyMs,
            r.jitterMs,r.p95JitterMs.takeIf{it>0}?:r.jitterMs,
            r.lossPercent.takeIf{r.sampleCount>0}?:(100.0-r.success).coerceIn(0.0,100.0),
            r.spikePercent,r.bytesPerSecond,r.udpSuccess.toDouble(),r.loadedLatencyMs,r.sampleCount),w,b)

    fun score(e:RealtimeEvidence,w:WorkloadProfile,b:BenchMode=BenchMode.BALANCED):RealtimeScore{
        if(e.successPercent<=0||e.medianRttMs<=0)return RealtimeScore(0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0)
        val conf=(e.samples.coerceAtLeast(1)/4.0).coerceIn(.30,1.0)
        fun shrink(v:Double,n:Double=55.0)=(v*conf+n*(1-conf)).coerceIn(0.0,100.0)
        fun decay(v:Double,s:Double)=100*exp(-v.coerceAtLeast(0.0)/s)
        val rel=shrink(e.successPercent.coerceIn(0.0,100.0),65.0)
        val lat=shrink(decay(e.medianRttMs,240.0))
        val rawTail=decay(e.p95RttMs.coerceAtLeast(e.medianRttMs),330.0)
        val loaded=if(e.loadedLatencyMs>0)decay(e.loadedLatencyMs,380.0) else rawTail
        val tail=shrink(rawTail*.72+loaded*.28)
        val jm=e.jitterMs.coerceAtLeast(0.0); val jt=e.p95JitterMs.coerceAtLeast(jm)
        val jbase=decay(jm,38.0)*.60+decay(jt,85.0)*.40
        val jit=shrink(jbase*.82+(100-e.spikePercent.coerceIn(0.0,100.0))*.18)
        val loss=shrink(100-e.lossPercent.coerceIn(0.0,100.0),70.0)
        val speed=if(e.bytesPerSecond<=0)50.0 else {
            val mbps=e.bytesPerSecond*8/1_000_000.0;(ln(1+mbps)/ln(101.0)*100).coerceIn(0.0,100.0)}
        val udp=if(e.udpSuccessPercent<=0)55.0 else e.udpSuccessPercent.coerceIn(0.0,100.0)
        val auto=rel*.35+lat*.25+jit*.20+tail*.10+loss*.05+speed*.05
        val interactive=rel*.28+lat*.27+jit*.23+tail*.12+loss*.07+speed*.03
        val streaming=rel*.28+speed*.43+lat*.10+jit*.08+tail*.06+loss*.05
        val stability=rel*.32+lat*.16+jit*.22+tail*.14+loss*.12+speed*.04
        val resilience=rel*.40+udp*.12+lat*.13+jit*.13+tail*.09+loss*.11+speed*.02
        val selected=when(w){
            WorkloadProfile.INTERACTIVE->interactive;WorkloadProfile.STREAMING->streaming
            WorkloadProfile.STABILITY->stability;WorkloadProfile.STEALTH->resilience
            WorkloadProfile.AUTO->when(b){BenchMode.RELIABLE->stability
                BenchMode.FAST,BenchMode.TURBO->interactive*.72+auto*.28
                BenchMode.BALANCED,BenchMode.CUSTOM->auto}}
        return RealtimeScore(selected.coerceIn(0.0,100.0),interactive.coerceIn(0.0,100.0),
            streaming.coerceIn(0.0,100.0),stability.coerceIn(0.0,100.0),resilience.coerceIn(0.0,100.0),
            rel,lat,jit,tail,loss)
    }
}
