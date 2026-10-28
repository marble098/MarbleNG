package com.marbleng.app.core
import java.io.File
import java.io.RandomAccessFile
import org.json.JSONObject

data class TransportTelemetrySnapshot(val atMs:Long,val sockets:Int,val rttMs:Int,val p95RttMs:Int,
 val rttVarMs:Int,val retransDelta:Int,val totalRetrans:Int,val lost:Int,val unacked:Int,val pmtu:Int,
 val mss:Int,val cwndPackets:Int,val pacingBps:Long,val deliveryBps:Long,val lostDelta:Int=0,
 // MARBLE_TCP_INFO_TRUTH_V238 — established, non-loopback sockets with a real kernel RTT. Older
 // core builds do not write the field; the default keeps those files readable (every socket is
 // then assumed valid, the pre-V238 behaviour).
 val validSockets:Int=-1){
 fun fresh(now:Long=System.currentTimeMillis(),maxAgeMs:Long=7000)=atMs>0&&now-atMs in 0L..maxAgeMs}
object TransportTelemetry{
 fun latest(file:File):TransportTelemetrySnapshot?{
  if(!file.isFile||file.length()<=0)return null
  return runCatching{RandomAccessFile(file,"r").use{r->val len=r.length();val start=(len-32768).coerceAtLeast(0);r.seek(start)
   val b=ByteArray((len-start).toInt());r.readFully(b);val line=String(b,Charsets.UTF_8).lineSequence().filter{it.trim().startsWith("{")}.lastOrNull()?:return null
   val o=JSONObject(line);TransportTelemetrySnapshot(o.optLong("atMs"),o.optInt("sockets"),o.optInt("rttMs"),o.optInt("p95RttMs"),o.optInt("rttVarMs"),o.optInt("retransDelta"),o.optInt("totalRetrans"),o.optInt("lost"),o.optInt("unacked"),o.optInt("pmtu"),o.optInt("mss"),o.optInt("cwndPackets"),o.optLong("pacingBps"),o.optLong("deliveryBps"),o.optInt("lostDelta"),o.optInt("validSockets",-1))}}.getOrNull()
 }
}
