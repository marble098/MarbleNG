package com.marbleng.app.vpn

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.marbleng.app.MarbleApplication
import com.marbleng.app.core.XrayManager
import com.marbleng.app.nativebridge.HevTunnel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MarbleVpnService:VpnService(){
    companion object { const val ACTION_START="com.marbleng.START";const val ACTION_STOP="com.marbleng.STOP";const val EXTRA_PROFILE="profile";const val CHANNEL="marbleng-vpn";const val NOTIFY=7301 }
    private var tun:ParcelFileDescriptor?=null; private var hevFd=-1; private val running=AtomicBoolean(false);private val worker=Executors.newCachedThreadPool();private lateinit var xray:XrayManager
    override fun onCreate(){super.onCreate();xray=(application as MarbleApplication).xray;createChannel()}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{when(intent?.action){ACTION_STOP->shutdown(true);ACTION_START->intent.getStringExtra(EXTRA_PROFILE)?.let(::startTunnel)};return START_STICKY}
    private fun startTunnel(id:String){if(running.get())shutdown(true);val app=application as MarbleApplication;val p=app.repo.profile(id)?:return;val settings=app.repo.settings
        startForeground(NOTIFY,note("Connecting • ${p.name}",false));running.set(true)
        worker.execute{
            if(!xray.start(p,settings.socksPort)){blocked("Xray rejected profile");return@execute}
            val builder=Builder().setSession("MarbleNG • ${p.name}").setMtu(8500).setBlocking(false).addAddress("198.18.0.1",32).addRoute("0.0.0.0",0).addDnsServer("1.1.1.1")
            runCatching{builder.addAddress("fc00::1",128).addRoute("::",0)}
            runCatching{builder.addDisallowedApplication(packageName)}
            tun=builder.establish();if(tun==null){blocked("VPN establish failed");return@execute}
            hevFd=ParcelFileDescriptor.dup(tun!!.fileDescriptor).detachFd();val cfg=listOf(
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
                "  log-level: warn",
                "  task-stack-size: 86016",
                "  tcp-buffer-size: 65536",
                "  udp-recv-buffer-size: 524288"
            ).joinToString(separator="\n",postfix="\n") // MarbleNG HEV YAML/LF hotfix v2
            if(cfg.contains("\\n") || !cfg.contains('\n')){blocked("Internal HEV YAML encoding failure");return@execute}
            app.repo.markConnected(p);notifyNow("Protected • ${p.name}",true)
            val code=runCatching{HevTunnel.run(cfg,hevFd)}.getOrDefault(-1)
            if(running.get()){blocked("HEV stopped ($code) — traffic held")}
        }
    }
    private fun blocked(reason:String){xray.stop();notifyNow("BLOCKED • $reason",true);(application as MarbleApplication).repo.setRuntimeState("BLOCKED",reason)}
    private fun shutdown(explicit:Boolean){running.set(false);runCatching{HevTunnel.quit()};xray.stop();runCatching{if(hevFd>=0)ParcelFileDescriptor.adoptFd(hevFd).close()};hevFd=-1;runCatching{tun?.close()};tun=null;(application as MarbleApplication).repo.setRuntimeState("DISCONNECTED",if(explicit)"User disconnected" else "Stopped");stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    override fun onDestroy(){if(running.get())shutdown(false);super.onDestroy()}
    private fun createChannel(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"MarbleNG VPN",NotificationManager.IMPORTANCE_LOW))}
    private fun note(text:String,ongoing:Boolean)=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_warning).setContentTitle("MarbleNG").setContentText(text).setOngoing(ongoing).build()
    private fun notifyNow(t:String,o:Boolean){getSystemService(NotificationManager::class.java).notify(NOTIFY,note(t,o))}
}
