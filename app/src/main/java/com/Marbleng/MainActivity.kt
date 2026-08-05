package com.Marbleng


import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.widget.Button
import android.widget.TextView
import android.widget.LinearLayout



class MainActivity : Activity() {


    private lateinit var statusText: TextView



    override fun onCreate(savedInstanceState: Bundle?) {


        super.onCreate(savedInstanceState)



        val layout = LinearLayout(this)

        layout.orientation = LinearLayout.VERTICAL



        statusText = TextView(this)

        statusText.text = "Disconnected"

        statusText.textSize = 20f



        val connectButton = Button(this)

        connectButton.text = "Connect"



        val disconnectButton = Button(this)

        disconnectButton.text = "Disconnect"



        connectButton.setOnClickListener {


            val intent = Intent(

                this,

                XrayService::class.java

            )


            startService(intent)



            statusText.text = "Connecting..."


        }




        disconnectButton.setOnClickListener {


            val intent = Intent(

                this,

                XrayService::class.java

            )


            stopService(intent)



            statusText.text = "Disconnected"


        }




        layout.addView(statusText)

        layout.addView(connectButton)

        layout.addView(disconnectButton)



        setContentView(layout)


    }


}
