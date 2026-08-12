package com.marbleng.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.model.*
import java.text.DateFormat
import java.util.Date

private enum class Tab(val label:String,val icon:String){HOME("Deck","⚡"),LIB("Library","📦"),LAB("Lab","🧠"),RADAR("Radar","📡"),SETTINGS("Settings","⚙")}

@Composable fun MarbleApp(repo:AppRepository,onConnect:(ProxyProfile)->Unit,onImportFile:()->Unit){
    AetherFlowTheme{var tab by remember{mutableStateOf(Tab.HOME)};var dialog by remember{mutableStateOf<String?>(null)}
        Scaffold(bottomBar={NavigationBar{Tab.entries.forEach{t->NavigationBarItem(selected=tab==t,onClick={tab=t},icon={Text(t.icon)},label={Text(t.label)})}}}){pad->
            Box(Modifier.fillMaxSize().padding(pad).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background,MaterialTheme.colorScheme.surface.copy(alpha=.78f))))){
                when(tab){Tab.HOME->AetherDeck(repo,onConnect,onOpenLibrary={tab=Tab.LIB}){dialog=it};Tab.LIB->Library(repo,onConnect,onImportFile);Tab.LAB->Lab(repo,onConnect);Tab.RADAR->Radar(repo);Tab.SETTINGS->Settings(repo){dialog=it}}
            }
            if(repo.message.isNotBlank()) AlertDialog(onDismissRequest={if(!repo.busy)repo.clearMessage()},confirmButton={if(!repo.busy)TextButton(onClick=repo::clearMessage){Text("OK")}},title={Text(if(repo.busy)"Working" else "MarbleNG")},text={Text(repo.message)})
            dialog?.let{d->AlertDialog(onDismissRequest={dialog=null},confirmButton={TextButton(onClick={dialog=null}){Text("Close")}},title={Text(d)},text={SelectionContainer{Text(dialogText(d,repo))}})}
        }
    }
}
private fun dialogText(d:String,r:AppRepository)=when(d){
    "Logs"->r.readLogs();"Core lock"->r.coreLock();"Capabilities"->r.capabilities();"System Doctor"->r.doctor()
    "History"->r.history.takeLast(100).reversed().joinToString("\n"){"${DateFormat.getDateTimeInstance().format(Date(it.at))} • ${it.name} • ${it.reason}"}
    else->"MarbleNG"
}
@Composable private fun Header(title:String,sub:String){Column(Modifier.padding(18.dp)){Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black);Text(sub,color=MaterialTheme.colorScheme.secondary)}}
@Composable private fun GlassCard(mod:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Card(mod,shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface.copy(alpha=.93f))){Column(Modifier.padding(16.dp),content=content)}}
@Composable private fun ActionGrid(actions:List<Pair<String,()->Unit>>){Column(Modifier.padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){actions.chunked(2).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{(n,a)->OutlinedButton(onClick=a,Modifier.weight(1f)){Text(n)}};if(row.size==1)Spacer(Modifier.weight(1f))}}}}

@Composable private fun Library(repo:AppRepository,onConnect:(ProxyProfile)->Unit,onImportFile:()->Unit){
    var url by remember{mutableStateOf("")};var name by remember{mutableStateOf("")};var raw by remember{mutableStateOf("")}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Header("Subscription Library","Add • refresh • file/raw import • browse • connect")}
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){OutlinedTextField(url,{url=it},label={Text("Subscription URL")},modifier=Modifier.fillMaxWidth());OutlinedTextField(name,{name=it},label={Text("Name")},modifier=Modifier.fillMaxWidth());Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={repo.addSubscription(name,url);url="";name=""},enabled=url.startsWith("http")){Text("Add")};OutlinedButton(onClick=repo::refreshAll,enabled=repo.subscriptions.isNotEmpty()){Text("Refresh all")};OutlinedButton(onClick=onImportFile){Text("File")}}}}
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){OutlinedTextField(raw,{raw=it},label={Text("URI list / base64 / Xray JSON")},minLines=3,modifier=Modifier.fillMaxWidth());Button(onClick={repo.importText(raw);raw=""},enabled=raw.isNotBlank()){Text("Import pasted data")}}}
        if(repo.subscriptions.isNotEmpty())item{Text("Subscriptions",Modifier.padding(horizontal=18.dp),fontWeight=FontWeight.Bold)}
        items(repo.subscriptions,key={it.id}){s->GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){Text(s.name,fontWeight=FontWeight.Bold);Text(s.url,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall);Row{TextButton(onClick={repo.refresh(s.id)}){Text("Refresh")};TextButton(onClick={repo.removeSubscription(s.id)}){Text("Remove")}}}}
        item{Text("Config browser (${repo.profiles.size})",Modifier.padding(horizontal=18.dp),fontWeight=FontWeight.Bold)}
        items(repo.profiles,key={it.id}){p->GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${p.subscriptionName} • ${p.scheme} • ${p.transport} • ${p.security}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)};Button(onClick={onConnect(p)}){Text("Connect")}};Row{TextButton(onClick={repo.fullTest(p)}){Text("Full test")};TextButton(onClick={repo.removeProfile(p.id)}){Text("Delete")}}}}
    }
}

@Composable private fun Lab(repo:AppRepository,onConnect:(ProxyProfile)->Unit){
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Header("Smart Tunnel Lab","Real Xray SOCKS tests • success 55% • latency 20% • jitter 10% • throughput 15%")}
        item{Button(onClick={repo.smart(onConnect)},enabled=!repo.busy&&repo.profiles.isNotEmpty(),modifier=Modifier.padding(horizontal=14.dp).fillMaxWidth()){Text("Run smart benchmark + connect best")}}
        items(repo.benchmarks){r->GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){Text(r.name,fontWeight=FontWeight.Bold);Text("${r.success}%  •  ${"%.0f".format(r.latencyMs)} ms  •  jitter ${"%.0f".format(r.jitterMs)} ms");Text("${"%.0f".format(r.bytesPerSecond/1024)} KB/s  •  score ${"%.1f".format(r.score)}",color=MaterialTheme.colorScheme.secondary)}}
    }
}

@Composable private fun Radar(repo:AppRepository){
    var channel by remember{mutableStateOf("")};var token by remember{mutableStateOf(repo.cloudflareToken())};var account by remember{mutableStateOf(repo.cloudflareAccount())};var key by remember{mutableStateOf(repo.cloudflareKey())};var script by remember{mutableStateOf("marbleng-radar")}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Header("Telegram Radar","Public preview → smart gate → real tunnel lab → passed subscription")};if(repo.channels().isNotEmpty())item{Text("Saved: "+repo.channels().take(8).joinToString("  •  "),Modifier.padding(horizontal=18.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)}
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){OutlinedTextField(channel,{channel=it},label={Text("Channel e.g. @channel")},modifier=Modifier.fillMaxWidth());Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={repo.telegram(channel)},enabled=channel.isNotBlank()&&!repo.busy){Text("Scan + test")};OutlinedButton(onClick=repo::importRadar,enabled=repo.radarConfigs.isNotEmpty()){Text("Import passed")}};Text("Pass threshold ${repo.settings.telegramPassMinSuccess}% • ${repo.settings.telegramTcpSamples} samples • max ${repo.settings.telegramMaxConfigs}",style=MaterialTheme.typography.bodySmall)}}
        if(repo.radarResults.isNotEmpty())items(repo.radarResults.take(30)){r->GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){Text(r.name,fontWeight=FontWeight.Bold);Text("${r.success}% • ${"%.0f".format(r.latencyMs)} ms • score ${"%.1f".format(r.score)}")}}
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){Text("Cloudflare Worker Center",fontWeight=FontWeight.Bold);Text("Token is encrypted with Android Keystore.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary);OutlinedTextField(token,{token=it},label={Text("API token")},modifier=Modifier.fillMaxWidth());OutlinedTextField(account,{account=it},label={Text("Account ID")},modifier=Modifier.fillMaxWidth());OutlinedTextField(script,{script=it},label={Text("Worker name")},modifier=Modifier.fillMaxWidth());OutlinedTextField(key,{key=it},label={Text("Worker access key")},modifier=Modifier.fillMaxWidth());Row{Button(onClick={repo.deployWorker(token,account,script,key)},enabled=token.isNotBlank()){Text("Deploy")};TextButton(onClick=repo::forgetCloudflare){Text("Forget credentials")}}}}
    }
}

@Composable private fun Settings(repo:AppRepository,onDialog:(String)->Unit){
    val s=repo.settings
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Header("Settings","Appearance • performance • Telegram • privacy • update policy")}
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){Text("Appearance",fontWeight=FontWeight.Bold);Row{listOf("aurora","ocean","sunset","matrix","mono").forEach{t->FilterChip(selected=s.theme==t,onClick={repo.updateSettings(s.copy(theme=t))},label={Text(t.take(3).uppercase())},modifier=Modifier.padding(end=4.dp))}}}}
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){Text("Privacy Guard",fontWeight=FontWeight.Bold);Text("ALWAYS ON • fail-closed runtime • remote DNS • localhost-only SOCKS",color=Color(0xFF4DFFB8));Text("For the strongest OS kill switch, enable Android Always-on VPN + Block connections without VPN.",style=MaterialTheme.typography.bodySmall)}}
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){Text("Benchmark profile",fontWeight=FontWeight.Bold);Row{BenchMode.entries.forEach{m->FilterChip(selected=s.benchMode==m,onClick={repo.updateSettings(s.copy(benchMode=m))},label={Text(m.name.take(3))},modifier=Modifier.padding(end=4.dp))}};Text("Candidates ${s.benchCandidates} • samples ${s.benchSamples} • timeout ${s.benchTimeoutSec}s",style=MaterialTheme.typography.bodySmall);Row{TextButton(onClick={repo.updateSettings(s.copy(benchCandidates=(s.benchCandidates-5).coerceAtLeast(5)))}){Text("− candidates")};TextButton(onClick={repo.updateSettings(s.copy(benchCandidates=(s.benchCandidates+5).coerceAtMost(80)))}){Text("+ candidates")}}}}
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){Text("Telegram test policy",fontWeight=FontWeight.Bold);Text("Max ${s.telegramMaxConfigs} • samples ${s.telegramTcpSamples} • pass ≥${s.telegramPassMinSuccess}%");Row{FilterChip(selected=s.telegramAutoSub,onClick={repo.updateSettings(s.copy(telegramAutoSub=!s.telegramAutoSub))},label={Text("Auto passed-sub")});Spacer(Modifier.width(6.dp));FilterChip(selected=s.telegramTcpGate,onClick={repo.updateSettings(s.copy(telegramTcpGate=!s.telegramTcpGate))},label={Text("Smart gate")})};Row{TextButton(onClick={repo.updateSettings(s.copy(telegramPassMinSuccess=(s.telegramPassMinSuccess-5).coerceAtLeast(25)))}){Text("− threshold")};TextButton(onClick={repo.updateSettings(s.copy(telegramPassMinSuccess=(s.telegramPassMinSuccess+5).coerceAtMost(100)))}){Text("+ threshold")}}}}
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){Text("SOCKS5h / native",fontWeight=FontWeight.Bold);Text("127.0.0.1:${s.socksPort} • LAN exposure locked off");Text("HEV + Xray: arm64-v8a • armeabi-v7a • x86_64 • x86",color=MaterialTheme.colorScheme.secondary)}}
        item{ActionGrid(listOf("⬆ Check cores" to {repo.checkCoreUpdates()},"🔐 Core lock" to {onDialog("Core lock")},"🧰 Doctor" to {onDialog("System Doctor")},"🗂 Capabilities" to {onDialog("Capabilities")},"🧾 Logs" to {onDialog("Logs")},"🕘 History" to {onDialog("History")},"☁ Forget Worker" to {repo.forgetCloudflare()},"↺ Reset settings" to {repo.resetSettings()}))}
    }
}
