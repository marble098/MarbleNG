package com.marbleng

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.LinearLayout

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val layout = LinearLayout(this)

        layout.orientation = LinearLayout.VERTICAL


        val title = TextView(this)

        title.text = "Trivox Xray Client"

        title.textSize = 22f


        val button = Button(this)

        button.text = "Connect"


        button.setOnClickListener {

            title.text = "Starting Xray..."

        }


        layout.addView(title)

        layout.addView(button)


        setContentView(layout)

    }

}
