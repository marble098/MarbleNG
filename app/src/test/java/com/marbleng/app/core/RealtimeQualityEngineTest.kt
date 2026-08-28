package com.marbleng.app.core
import com.marbleng.app.model.BenchMode
import com.marbleng.app.model.WorkloadProfile
import org.junit.Assert.assertTrue
import org.junit.Test
class RealtimeQualityEngineTest{
 @Test fun stableRouteBeatsSlightlyFasterJitteryRoute(){
  val a=RealtimeQualityEngine.score(RealtimeEvidence(100.0,105.0,112.0,4.0,8.0,0.0,2.0,samples=6),WorkloadProfile.INTERACTIVE,BenchMode.BALANCED)
  val b=RealtimeQualityEngine.score(RealtimeEvidence(100.0,96.0,180.0,32.0,84.0,0.0,28.0,samples=6),WorkloadProfile.INTERACTIVE,BenchMode.BALANCED)
  assertTrue(a.selected>b.selected)}
 @Test fun robustEstimatorExposesTailAndLoss(){val r=requireNotNull(LinkQualityEstimator.summarize(listOf(90,91,90,92,215,91,-1,94)));assertTrue(r.p95RttMs>=r.p90RttMs);assertTrue(r.p95IpdvMs>=r.medianIpdvMs);assertTrue(r.lossPercent>0);assertTrue(r.spikePercent>0)}
}
