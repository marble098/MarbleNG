package com.Marbleng

import android.app.Service
import android.content.Intent
import android.os.IBinder


class XrayService : Service() {


    private lateinit var xrayManager: XrayManager


    override fun onCreate() {

        super.onCreate()

        xrayManager = XrayManager()

    }



    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {


        val config = Config(
            remark = "Test Server",
            address = "example.com",
            port = 443,
            type = "VLESS"
        )


        xrayManager.start(config)


        return START_STICKY

    }



    override fun onDestroy() {

        xrayManager.stop()

        super.onDestroy()

    }



    override fun onBind(intent: Intent?): IBinder? {

        return null

    }

}
