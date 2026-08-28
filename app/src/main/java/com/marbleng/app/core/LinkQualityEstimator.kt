package com.marbleng.app.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Robust rolling route statistics. MARBLE_REALTIME_ENGINE_V70 */
object LinkQualityEstimator {
    data class Summary(
        val medianRttMs:Int, val p90RttMs:Int, val p95RttMs:Int,
        val meanIpdvMs:Int, val medianIpdvMs:Int, val p95IpdvMs:Int,
        val ewmaJitterMs:Int, val madRttMs:Int, val jitterSamples:Int,
        val attempts:Int, val successes:Int, val successPercent:Int,
        val successLowerBoundPercent:Int, val lossPercent:Int, val spikePercent:Int
    )

    fun summarize(rawOutcomes:List<Int>):Summary? {
        if(rawOutcomes.isEmpty()) return null
        val outcomes=rawOutcomes.map{if(it>0)it.coerceIn(1,10_000) else -1}
        val rtts=outcomes.filter{it>0}; if(rtts.isEmpty()) return null
        val deltas=outcomes.zipWithNext().mapNotNull{(x,y)->if(x>0&&y>0)abs(y-x).coerceIn(0,10_000) else null}
        val sorted=rtts.sorted(); val med=sorted[sorted.size/2]
        val dev=rtts.map{abs(it-med)}.sorted(); val mad=if(dev.isEmpty())0 else dev[dev.size/2]
        val ds=deltas.sorted(); val mean=if(deltas.isEmpty())-1 else deltas.average().roundToInt()
        val dmed=if(ds.isEmpty())-1 else ds[ds.size/2]; val dp95=percentile(ds,.95)
        var ewma=-1.0
        deltas.forEach{d->ewma=if(ewma<0)d.toDouble() else ewma*.75+d*.25}
        val threshold=max(15.0,max(med*.25,mad*3.0))
        val spikes=if(deltas.isEmpty())0 else (deltas.count{it>=threshold}*100.0/deltas.size).roundToInt()
        val success=(rtts.size*100.0/outcomes.size).roundToInt().coerceIn(0,100)
        return Summary(med,percentile(sorted,.90),percentile(sorted,.95),mean,dmed,dp95,
            if(ewma<0)-1 else ewma.roundToInt(),mad,deltas.size,outcomes.size,rtts.size,success,
            (wilson(rtts.size,outcomes.size)*100).roundToInt().coerceIn(0,100),100-success,spikes.coerceIn(0,100))
    }
    private fun percentile(sorted:List<Int>,q:Double):Int{
        if(sorted.isEmpty())return -1
        val i=ceil(sorted.size*q.coerceIn(0.0,1.0)).toInt().coerceIn(1,sorted.size)-1
        return sorted[i]
    }
    private fun wilson(s:Int,n0:Int):Double{
        if(n0<=0)return 0.0; val z=1.959963984540054; val n=n0.toDouble(); val p=s/n
        val z2=z*z/n; val c=p+z2/2; val m=z*sqrt((p*(1-p)+z*z/(4*n))/n)
        return ((c-m)/(1+z2)).coerceIn(0.0,1.0)
    }
}
