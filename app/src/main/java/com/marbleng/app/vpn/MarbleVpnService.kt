package com.marbleng.app.vpn

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.marbleng.app.MarbleApplication
import com.marbleng.app.core.XrayManager
import com.marbleng.app.core.RuntimeDiagnostics
import com.marbleng.app.nativebridge.HevTunnel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MarbleVpnService:VpnService(){
    companion object { const val ACTION_START="com.marbleng.START";const val ACTION_STOP="com.marbleng.STOP";const val EXTRA_PROFILE="profile";const val CHANNEL="marbleng-vpn";const val NOTIFY=7301 }
    private var tun:ParcelFileDescriptor?=null; private var hevFd=-1; private val running=AtomicBoolean(false);private val worker=Executors.newCachedThreadPool();private lateinit var xray:XrayManager;private lateinit var diag:RuntimeDiagnostics;@Volatile private var activeSession="";@Volatile private var hevActive=false // MarbleNG Smart VPN Diagnostics v1
    override fun onCreate(){super.onCreate();xray=(application as MarbleApplication).xray;diag=RuntimeDiagnostics(this);createChannel();diag.event("VPN","service-created","system" to diag.systemSnapshot())}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{diag.event("VPN","command","action" to intent?.action,"startId" to startId,"flags" to flags);when(intent?.action){ACTION_STOP->shutdown(true);ACTION_START->intent.getStringExtra(EXTRA_PROFILE)?.let(::startTunnel)};return START_STICKY}
    private fun startTunnel(id:String){if(running.get()){diag.event("VPN","restart-request","previousSession" to activeSession);shutdown(true)};val app=application as MarbleApplication;val p=app.repo.profile(id);if(p==null){diag.event("VPN","profile-missing","profileId" to id.take(12));return};val settings=app.repo.settings;val session=System.currentTimeMillis().toString(36);activeSession=session;diag.prepareHevSession();diag.event("VPN","connect-request","session" to session,"profileId" to p.id.take(12),"profileName" to p.name,"socksPort" to settings.socksPort)
        startForeground(NOTIFY,note("Connecting • ${p.name}",false));running.set(true)
        worker.execute{
            diag.event("XRAY","start-begin","session" to session,"port" to settings.socksPort)
            if(!xray.start(p,settings.socksPort,app.repo.routingSpec())){diag.event("XRAY","start-failed","session" to session,"alive" to xray.isAlive);blocked("Xray rejected profile");return@execute}
            diag.event("XRAY","socks-ready","session" to session,"alive" to xray.isAlive,"port" to settings.socksPort)
            val builder=Builder().setSession("MarbleNG • ${p.name}").setMtu(8500).setBlocking(false).addAddress("198.18.0.1",32).addRoute("0.0.0.0",0).addDnsServer("1.1.1.1")
            runCatching{builder.addAddress("fc00::1",128).addRoute("::",0)}.onSuccess{diag.event("TUN","ipv6-enabled","session" to session)}.onFailure{diag.error("TUN","ipv6-builder-failed",it,"session" to session)}
            runCatching{builder.addDisallowedApplication(packageName)}.onSuccess{diag.event("TUN","self-disallowed","session" to session,"package" to packageName)}.onFailure{diag.error("TUN","self-disallow-failed",it,"session" to session)}
            val established=runCatching{builder.establish()}.onFailure{diag.error("TUN","establish-exception",it,"session" to session)}.getOrNull();tun=established;if(tun==null){blocked("VPN establish failed");return@execute}
            diag.event("TUN","established","session" to session,"vpnFd" to tun!!.fd,"mtu" to 8500)
            val dupFd=runCatching{ParcelFileDescriptor.dup(tun!!.fileDescriptor).detachFd()}.onFailure{diag.error("TUN","dup-fd-failed",it,"session" to session)}.getOrNull();if(dupFd==null){blocked("TUN fd duplication failed");return@execute};hevFd=dupFd;diag.event("TUN","fd-ready","session" to session,"hevFd" to hevFd)
            val cfg=listOf(
                "tunnel:",
                "  mtu: 8500",
                "  ipv4: 198.18.0.1",
                "  ipv6: 'fc00::1'",
                "  icmp: 'reply'",
                "socks5:",
                "  address: '127.0.0.1'",
                "  port: ${settings.socksPort}",
                "  udp: 'udp'",
                "  pipeline: false",
                "misc:",
                "  log-file: '${diag.hevLog.absolutePath}'",
                "  log-level: debug",
                "  task-stack-size: 86016",
                "  tcp-buffer-size: 65536",
                "  udp-recv-buffer-size: 524288"
            ).joinToString(separator="\n",postfix="\n") // MarbleNG HEV YAML/LF hotfix v2
            if(cfg.contains("\\n") || !cfg.contains('\n')){diag.event("HEV","yaml-invalid","session" to session,"bytes" to cfg.toByteArray().size);blocked("Internal HEV YAML encoding failure");return@execute}
            diag.event("HEV","config-ready","session" to session,"bytes" to cfg.toByteArray().size,"lines" to cfg.lineSequence().count(),"sha256" to diag.sha256(cfg),"nativeLog" to diag.hevLog.absolutePath)
            app.repo.markConnected(p);notifyNow("Protected • ${p.name}",true);hevActive=true;startHevMonitor(session);startTelemetry(session)
            diag.event("HEV","run-enter","session" to session,"hevFd" to hevFd,"xrayAlive" to xray.isAlive)
            val result=runCatching{HevTunnel.run(cfg,hevFd)};hevActive=false
            val code=result.getOrElse{diag.error("HEV","jni-run-exception",it,"session" to session,"hevFd" to hevFd);-10001}
            diag.event("HEV","run-exit","session" to session,"code" to code,"runningFlag" to running.get(),"xrayAlive" to xray.isAlive,"stats" to hevStats(),"nativeLogBytes" to diag.hevLog.length())
            if(running.get()){blocked("HEV stopped ($code) — traffic held")}
        }
    }
    private fun startHevMonitor(session:String){worker.execute{var ticks=0;while(running.get()&&hevActive&&activeSession==session){try{Thread.sleep(2000)}catch(_:InterruptedException){Thread.currentThread().interrupt();return@execute};if(!running.get()||!hevActive||activeSession!=session)break;ticks++;if(ticks%2==0)diag.event("HEV","heartbeat","session" to session,"stats" to hevStats(),"xrayAlive" to xray.isAlive,"tunOpen" to (tun!=null),"hevFd" to hevFd,"nativeLogBytes" to diag.hevLog.length())}}}
    private fun hevStats():String=if(hevFd<0)"not-started" else runCatching{val s=HevTunnel.stats();if(s.size>=4)"txPackets=${s[0]},txBytes=${s[1]},rxPackets=${s[2]},rxBytes=${s[3]}" else "unexpected-size=${s.size}"}.getOrElse{"stats-error=${it::class.simpleName}:${it.message}"}
    // Live throughput: per-second deltas from HEV byte counters (tx=upload, rx=download). Ping is handled by the shared repo probe.
    private fun startTelemetry(session:String){worker.execute{
        val repo=(application as MarbleApplication).repo;var lastUp=-1L;var lastDown=-1L;var lastT=System.nanoTime()
        while(running.get()&&hevActive&&activeSession==session){
            try{Thread.sleep(1000)}catch(_:InterruptedException){Thread.currentThread().interrupt();return@execute}
            if(!running.get()||!hevActive||activeSession!=session)break
            val s=runCatching{HevTunnel.stats()}.getOrNull()
            if(s!=null&&s.size>=4){val now=System.nanoTime();val dt=(now-lastT)/1e9;val up=s[1];val down=s[3]
                if(lastUp>=0&&dt>0.25){repo.updateTelemetry(((down-lastDown)/dt).toLong(),((up-lastUp)/dt).toLong())}
                lastUp=up;lastDown=down;lastT=now}
        }
    }}
    private fun blocked(reason:String){hevActive=false;diag.event("VPN","blocked","session" to activeSession,"reason" to reason,"xrayAlive" to xray.isAlive,"hevFd" to hevFd,"tunOpen" to (tun!=null),"stats" to hevStats(),"hevLogBytes" to diag.hevLog.length());xray.stop();notifyNow("BLOCKED • $reason",true);(application as MarbleApplication).repo.setRuntimeState("BLOCKED",reason)}
    private fun shutdown(explicit:Boolean){diag.event("VPN","shutdown-begin","session" to activeSession,"explicit" to explicit,"xrayAlive" to xray.isAlive,"hevFd" to hevFd,"stats" to hevStats());running.set(false);hevActive=false;runCatching{HevTunnel.quit()}.onFailure{diag.error("HEV","quit-failed",it,"session" to activeSession)};xray.stop();runCatching{if(hevFd>=0)ParcelFileDescriptor.adoptFd(hevFd).close()}.onFailure{diag.error("TUN","hev-fd-close-failed",it,"fd" to hevFd)};hevFd=-1;runCatching{tun?.close()}.onFailure{diag.error("TUN","vpn-fd-close-failed",it)};tun=null;(application as MarbleApplication).repo.setRuntimeState("DISCONNECTED",if(explicit)"User disconnected" else "Stopped");diag.event("VPN","shutdown-complete","session" to activeSession,"explicit" to explicit);activeSession="";stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    override fun onRevoke(){diag.event("VPN","permission-revoked","session" to activeSession);if(running.get())shutdown(false);super.onRevoke()}
    override fun onDestroy(){diag.event("VPN","service-destroy","session" to activeSession,"running" to running.get());if(running.get())shutdown(false);super.onDestroy()}
    private fun createChannel(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"MarbleNG VPN",NotificationManager.IMPORTANCE_LOW))}
    private fun note(text:String,ongoing:Boolean)=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_warning).setContentTitle("MarbleNG").setContentText(text).setOngoing(ongoing).build()
    private fun notifyNow(t:String,o:Boolean){getSystemService(NotificationManager::class.java).notify(NOTIFY,note(t,o))}
}
