package com.Marbleng

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.LinearLayout


class MainActivity : Activity() {


    private val xray = XrayManager()


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)


        val layout = LinearLayout(this)

        layout.orientation = LinearLayout.VERTICAL


        val status = TextView(this)

        status.text = "Disconnected"


        val button = Button(this)

        button.text = "Connect"



        button.setOnClickListener {


            val config = Config(
                "Test Server",
                "example.com",
                443,
                "VLESS"
            )


            if(xray.start(config)){

                status.text = "Connected"

            }

        }



        layout.addView(status)

        layout.addView(button)


        setContentView(layout)

    }

}
