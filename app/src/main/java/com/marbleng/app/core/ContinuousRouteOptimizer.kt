package com.marbleng.app.core
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ProxyProfile
import java.util.Locale
import kotlin.math.max

data class ActiveRouteQuality(val latencyMs:Int,val samples:Int,val jitterMs:Int=-1,val p95LatencyMs:Int=0,val lossPercent:Int=0,val spikePercent:Int=0)
data class OptimizerPlan(val candidates:List<ProxyProfile>,val deep:Boolean,val cycle:Int)
data class OptimizerDecision(val target:ProxyProfile?=null,val summary:String,val gain:Double=0.0)
/** Continuous jitter/tail/loss-aware route controller. MARBLE_REALTIME_ENGINE_V70 */
class ContinuousRouteOptimizer(private val intelligence:MarbleIntelligence){
 private var cycle=0;private var lastScanAt=0L;private var lastSwitchAt=0L;private var pendingProfileId="";private var pendingWins=0
 @Synchronized fun reset(lastScan:Long=System.currentTimeMillis()){cycle=0;lastScanAt=lastScan;pendingProfileId="";pendingWins=0}
 @Synchronized fun shouldScan(s:AppSettings,a:ActiveRouteQuality,thermalBudget:Double,liveDownBps:Long,force:Boolean=false,now:Long=System.currentTimeMillis()):Boolean{
  if(!s.continuousOptimizerEnabled||a.samples<3||thermalBudget<.45)return false
  val base=s.optimizerIntervalSec.coerceIn(60,900)*1000L
  val bad=a.latencyMs>=300||a.jitterMs>=25||a.lossPercent>=10||a.spikePercent>=20||(a.p95LatencyMs>0&&a.p95LatencyMs>=max(420,a.latencyMs*2))
  val interval=if(bad)max(45_000L,base/2)else base*(if(thermalBudget<.65)2 else 1)
  val elapsed=now-lastScanAt;if(!force&&elapsed<interval)return false
  val heavy=liveDownBps>=3L*1024*1024
  return !(s.optimizerAvoidHeavyTraffic&&heavy&&!bad&&elapsed<interval*2)
 }
 @Synchronized fun plan(active:ProxyProfile,profiles:List<ProxyProfile>,s:AppSettings,now:Long=System.currentTimeMillis()):OptimizerPlan{
  val ordered=intelligence.orderCandidates(profiles,s).filterNot{it.id==active.id};if(ordered.isEmpty()){lastScanAt=now;return OptimizerPlan(emptyList(),false,cycle)}
  val limit=s.optimizerCandidateCount.coerceIn(2,8).coerceAtMost(ordered.size);val stableCount=minOf(2,max(1,limit/2));val stable=ordered.take(stableCount);val pool=ordered.drop(stableCount);val count=(limit-stable.size).coerceAtLeast(0);val explore=mutableListOf<ProxyProfile>()
  if(pool.isNotEmpty()&&count>0){val start=((cycle.toLong()*count.toLong())%pool.size.toLong()).toInt();repeat(count){o->explore+=pool[(start+o)%pool.size]}}
  val selected=(stable+explore+ordered).distinctBy{it.id}.take(limit);cycle++;lastScanAt=now;return OptimizerPlan(selected,cycle%s.optimizerDeepScanEvery.coerceIn(3,20)==0,cycle)
 }
 @Synchronized fun resolveTarget(active:ProxyProfile,profiles:List<ProxyProfile>,results:List<BenchmarkResult>,s:AppSettings,now:Long=System.currentTimeMillis()):OptimizerDecision{
  val cur=results.firstOrNull{it.profileId==active.id}?:return OptimizerDecision(summary="Autopilot • active route verification unavailable")
  val best=results.asSequence().filter{it.profileId!=active.id&&it.success>=75}.maxByOrNull{it.score}?:run{clear();return OptimizerDecision(summary="Autopilot • ${active.name} remains best-known healthy route")}
  val cool=s.optimizerSwitchCooldownSec.coerceIn(60,1800)*1000L;if(lastSwitchAt>0&&now-lastSwitchAt<cool){clear();return OptimizerDecision(summary="Autopilot • challenger seen, switch cooldown is protecting stability")}
  fun jit(r:BenchmarkResult)=r.jitterMs.takeIf{r.sampleCount>=2&&it>=0}?:-1.0
  fun tail(r:BenchmarkResult)=r.p95LatencyMs.takeIf{it>0}?:r.p90LatencyMs.takeIf{it>0}?:r.latencyMs
  fun loss(r:BenchmarkResult)=r.lossPercent.takeIf{r.sampleCount>0}?:(100.0-r.success).coerceIn(0.0,100.0)
  val lGain=if(cur.latencyMs>0&&cur.latencyMs<9000)(cur.latencyMs-best.latencyMs)/cur.latencyMs else 1.0
  val sGain=if(cur.bytesPerSecond>64*1024)(best.bytesPerSecond-cur.bytesPerSecond)/cur.bytesPerSecond else if(best.bytesPerSecond>cur.bytesPerSecond+256*1024)1.0 else 0.0
  val cj=jit(cur);val bj=jit(best);val jGain=if(cj>0&&bj>=0)(cj-bj)/cj else 0.0;val ct=tail(cur);val bt=tail(best);val tGain=if(ct>0)(ct-bt)/ct else 0.0;val lossGain=loss(cur)-loss(best)
  val gain=best.score-cur.score;val emergency=cur.success<75&&best.success>=75
  val safe=emergency||(best.latencyMs<=cur.latencyMs*1.15&&(cj<0||bj<0||bj<=cj*1.35+3)&&bt<=ct*1.20+12&&loss(best)<=loss(cur)+5)
  val meaningful=emergency||(gain>=5&&(lGain>=.10||sGain>=.25||jGain>=.20||tGain>=.15||lossGain>=5)&&safe)
  if(!meaningful){clear();return OptimizerDecision(summary="Autopilot • ${active.name} held • challenger gain ${String.format(Locale.US, "%.1f", gain)} below realtime hysteresis")}
  if(pendingProfileId==best.profileId)pendingWins++ else{pendingProfileId=best.profileId;pendingWins=1};val req=if(emergency)1 else s.optimizerConfirmations.coerceIn(1,3)
  if(pendingWins<req)return OptimizerDecision(summary="Autopilot • ${best.name} leads • confirmation $pendingWins/$req")
  val target=profiles.firstOrNull{it.id==best.profileId}?:return OptimizerDecision(summary="Autopilot • winning route no longer exists");clear();val jl=bj.takeIf{it>=0}?.let{" • jitter ${it.toInt()} ms"}.orEmpty()
  return OptimizerDecision(target,"Autopilot • ${best.name} wins • ${best.latencyMs.toInt()} ms$jl • gain ${String.format(Locale.US, "%.1f", gain)}",gain)
 }
 @Synchronized fun noteSwitch(now:Long=System.currentTimeMillis()){lastSwitchAt=now;clear()}
 @Synchronized private fun clear(){pendingProfileId="";pendingWins=0}
}
