package com.marbleng.app.nativebridge

object HevTunnel {
    init { System.loadLibrary("marbleng") }
    external fun run(config:String,tunFd:Int):Int
    external fun quit()
    external fun stats():LongArray
}
