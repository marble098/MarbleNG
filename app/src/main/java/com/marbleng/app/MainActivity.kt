package com.marbleng.app

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.ui.MarbleApp

class MainActivity:ComponentActivity(){
    private var pending:ProxyProfile?=null
    private val app get()=application as MarbleApplication
    private val vpnPermission=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){if(it.resultCode==Activity.RESULT_OK)pending?.let{p->app.repo.startVpn(p)};pending=null}
    private val openFile=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{u->runCatching{contentResolver.openInputStream(u)?.bufferedReader()?.use{it.readText()}}.getOrNull()?.let{app.repo.importText(it,"Imported file")}}}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MarbleApp(app.repo,::connect){openFile.launch(arrayOf("text/*","application/json","application/octet-stream"))}}}
    private fun connect(p:ProxyProfile){val prep=VpnService.prepare(this);if(prep==null)app.repo.startVpn(p)else{pending=p;vpnPermission.launch(prep)}}
}
