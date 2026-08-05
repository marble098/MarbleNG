package com.Marbleng

class XrayManager {


    fun start(config: Config): Boolean {

        println(
            "Starting ${config.type}://${config.address}:${config.port}"
        )

        return true
    }


    fun stop() {

        println("Xray stopped")

    }

}
