package com.marbleng.app

import android.app.Application
import com.marbleng.app.core.XrayManager

class MarbleApplication:Application(){lateinit var xray:XrayManager;lateinit var repo:AppRepository;override fun onCreate(){super.onCreate();xray=XrayManager(this);repo=AppRepository(this,xray)}}
