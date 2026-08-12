package com.marbleng.app.core

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory
import kotlin.math.min

data class HttpProbe(val status:Int,val body:ByteArray,val elapsedMs:Double,val bytesPerSecond:Double)

object SocksHttpClient {
    fun get(port:Int, host:String, path:String="/", timeoutMs:Int=8000, maxBytes:Int=1024*1024):HttpProbe = request(port,host,443,"GET",path,null,timeoutMs,maxBytes)
    fun request(port:Int,host:String,targetPort:Int=443,method:String="GET",path:String="/",body:ByteArray?=null,timeoutMs:Int=10000,maxBytes:Int=1024*1024,headers:Map<String,String> = emptyMap()):HttpProbe{
        val start=System.nanoTime(); val tcp=Socket(); tcp.soTimeout=timeoutMs; tcp.connect(InetSocketAddress("127.0.0.1",port),timeoutMs)
        val o=BufferedOutputStream(tcp.getOutputStream()); val i=BufferedInputStream(tcp.getInputStream())
        o.write(byteArrayOf(5,1,0));o.flush(); require(i.read()==5 && i.read()==0){"SOCKS auth"}
        val hb=host.toByteArray(); o.write(byteArrayOf(5,1,0,3,hb.size.toByte()));o.write(hb);o.write(byteArrayOf((targetPort shr 8).toByte(),targetPort.toByte()));o.flush()
        val r=ByteArray(4); readFully(i,r); require(r[1].toInt()==0){"SOCKS connect ${r[1]}"}; when(r[3].toInt()) {1->skip(i,4);3->skip(i,i.read());4->skip(i,16)};skip(i,2)
        val ssl=(SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(tcp,host,targetPort,true) as javax.net.ssl.SSLSocket
        ssl.soTimeout=timeoutMs; ssl.startHandshake(); val so=BufferedOutputStream(ssl.getOutputStream()); val si=BufferedInputStream(ssl.getInputStream())
        val req=buildString{append("$method $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\nUser-Agent: MarbleNG/1\r\n");headers.forEach{(k,v)->append("$k: $v\r\n")};if(body!=null)append("Content-Length: ${body.size}\r\n");append("\r\n")}
        so.write(req.toByteArray());if(body!=null)so.write(body);so.flush()
        val raw=ByteArray(maxBytes); var n=0; while(n<raw.size){val z=runCatching{si.read(raw,n,min(8192,raw.size-n))}.getOrDefault(-1);if(z<=0)break;n+=z}
        val elapsed=(System.nanoTime()-start)/1e6; ssl.close(); val all=raw.copyOf(n); val sep="\r\n\r\n".toByteArray(); val idx=indexOf(all,sep); val head=String(if(idx>=0)all.copyOfRange(0,idx) else all)
        val status=head.lineSequence().firstOrNull()?.split(' ')?.getOrNull(1)?.toIntOrNull()?:0; val payload=if(idx>=0)all.copyOfRange(idx+4,all.size) else ByteArray(0)
        return HttpProbe(status,payload,elapsed, if(elapsed>0) payload.size/(elapsed/1000.0) else 0.0)
    }
    private fun readFully(i:BufferedInputStream,b:ByteArray){var p=0;while(p<b.size){val n=i.read(b,p,b.size-p);require(n>0);p+=n}}
    private fun skip(i:BufferedInputStream,n:Int){var left=n;val b=ByteArray(32);while(left>0){val z=i.read(b,0,min(left,b.size));require(z>0);left-=z}}
    private fun indexOf(a:ByteArray,b:ByteArray):Int{outer@ for(i in 0..a.size-b.size){for(j in b.indices)if(a[i+j]!=b[j])continue@outer;return i};return -1}
}
