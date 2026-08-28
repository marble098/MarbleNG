package com.marbleng.app.core
import com.marbleng.app.model.AppSettings
import kotlin.math.roundToInt

data class SmartIpRaceDecision(val tryDelayMs:Int,val maxConcurrentTry:Int,val prioritizeIpv6:Boolean,val reason:String)
/** Adaptive Xray Happy-Eyeballs/multi-address racing. MARBLE_REALTIME_ENGINE_V70 */
object SmartIpRacePolicy{
 fun decide(n:NetworkSnapshot,h:NodeHealthRecord?,s:AppSettings):SmartIpRaceDecision{
  if(!s.adaptiveHappyEyeballsEnabled)return SmartIpRaceDecision(s.happyEyeballsTryDelayMs.coerceIn(0,500),s.happyEyeballsMaxConcurrent.coerceIn(2,8),s.ipv6Enabled&&s.preferIpv6,"manual")
  val lat=h?.latencyEwma?.takeIf{it in 1.0..8999.0}?:0.0;val jit=h?.jitterEwma?.coerceAtLeast(0.0)?:0.0
  val bad=(h?.failureStreak?:0)>0||(h?.successEwma?:100.0)<82||jit>=28
  val d=when{bad->0;lat>0&&jit>=16->(lat*.12).roundToInt().coerceIn(20,55);n.transport=="cellular"->45;n.transport=="wifi"->35;n.metered->45;else->55}
  return SmartIpRaceDecision(d,if(bad||!n.metered)4 else 3,s.ipv6Enabled&&s.preferIpv6&&n.hasIpv6,if(bad)"unstable-race" else n.transport)
 }
}
