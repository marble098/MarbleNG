package com.Marbleng


import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.IBinder
import android.os.Build



class XrayService : Service() {


    private lateinit var xrayManager: XrayManager


    companion object {

        private const val CHANNEL_ID = "Marbleng_Xray"

    }



    override fun onCreate() {

        super.onCreate()


        xrayManager = XrayManager()


        createNotificationChannel()


    }




    override fun onStartCommand(

        intent: Intent?,

        flags: Int,

        startId: Int

    ): Int {



        startForeground(

            1,

            createNotification()

        )



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






    private fun createNotificationChannel(){


        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){


            val channel = NotificationChannel(

                CHANNEL_ID,

                "Xray VPN",

                NotificationManager.IMPORTANCE_LOW

            )


            val manager = getSystemService(

                NotificationManager::class.java

            )


            manager.createNotificationChannel(channel)

        }

    }





    private fun createNotification(): Notification {


        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){


            Notification.Builder(this, CHANNEL_ID)

                .setContentTitle("Marbleng")

                .setContentText("Xray is running")

                .setSmallIcon(android.R.drawable.ic_secure)

                .build()


        } else {


            Notification.Builder(this)

                .setContentTitle("Marbleng")

                .setContentText("Xray is running")

                .setSmallIcon(android.R.drawable.ic_secure)

                .build()


        }

    }


}
