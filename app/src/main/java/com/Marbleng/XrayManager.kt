package com.marbleng


class XrayManager {


    fun start(config:Config){

        println(
            "Starting Xray ${config.server}:${config.port}"
        )

    }


    fun stop(){

        println("Stopping Xray")

    }

}
