package com.marbleng.app.core

import android.content.Context
import com.marbleng.app.model.ProxyProfile
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

class XrayManager(private val context:Context) {
    @Volatile private var process:Process?=null
    val isAlive get()=process?.isAlive==true
    val logFile get()=File(context.filesDir,"logs/xray.log")
    private val bin get()=File(context.applicationInfo.nativeLibraryDir,"libxray.so")
    private val assetsDir=File(context.filesDir,"xray-assets")
    fun prepareAssets(){assetsDir.mkdirs();listOf("geoip.dat","geosite.dat").forEach{name->val dst=File(assetsDir,name);if(!dst.exists())runCatching{context.assets.open("xray/$name").use{src->dst.outputStream().use(src::copyTo)}}}}
    fun start(profile:ProxyProfile,port:Int):Boolean{stop();prepareAssets();val cfg=File(context.filesDir,"runtime.json");cfg.writeText(XrayConfigHardener.harden(profile.configJson,port));
        val test=ProcessBuilder(bin.absolutePath,"run","-test","-c",cfg.absolutePath).redirectErrorStream(true).start();if(test.waitFor()!=0)return false
        logFile.parentFile?.mkdirs();process=ProcessBuilder(bin.absolutePath,"run","-c",cfg.absolutePath).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(logFile)).apply{environment()["XRAY_LOCATION_ASSET"]=assetsDir.absolutePath}.start()
        return waitPort(port,5000)
    }
    fun stop(){process?.destroy();runCatching{process?.waitFor()};process=null}
    fun temporary(profile:ProxyProfile,port:Int,block:(Int)->Unit):Boolean{prepareAssets();val cfg=File(context.cacheDir,"bench-$port.json");cfg.writeText(XrayConfigHardener.harden(profile.configJson,port));
        val p=ProcessBuilder(bin.absolutePath,"run","-c",cfg.absolutePath).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).apply{environment()["XRAY_LOCATION_ASSET"]=assetsDir.absolutePath}.start()
        return try{if(!waitPort(port,5000))false else {block(port);true}}finally{p.destroy();runCatching{p.waitFor()};cfg.delete()}
    }
    private fun waitPort(port:Int,ms:Long):Boolean{val end=System.currentTimeMillis()+ms;while(System.currentTimeMillis()<end){if(runCatching{Socket().use{it.connect(InetSocketAddress("127.0.0.1",port),200)}}.isSuccess)return true;Thread.sleep(100)};return false}
}
