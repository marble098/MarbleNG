package com.marbleng.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
private fun benchModeLabel(m:BenchMode)=when(m){BenchMode.RELIABLE->"Reliability";BenchMode.BALANCED->"Balanced";BenchMode.FAST->"Maximum Speed";BenchMode.TURBO->"Turbo Burst";BenchMode.CUSTOM->"Custom"}
// --- Shared Aether Flow building blocks (used across every page) ------------
@Composable private fun Header(title:String,sub:String){Column(Modifier.padding(start=24.dp,end=20.dp,top=22.dp,bottom=6.dp)){Text(title,style=MaterialTheme.typography.headlineMedium,color=Aether.Ink);Text(sub,style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)}}
@Composable private fun SectionLabel(text:String){Text(text.uppercase(),Modifier.padding(start=22.dp,top=4.dp),style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)}
@Composable private fun GlassCard(mod:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Column(mod.clip(RoundedCornerShape(22.dp)).background(Aether.Glass).border(BorderStroke(1.dp,Aether.GlassBorderSoft),RoundedCornerShape(22.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}
@Composable private fun AetherPill(label:String,mod:Modifier=Modifier,onClick:()->Unit){Box(mod.clip(RoundedCornerShape(16.dp)).background(Aether.Glass).border(BorderStroke(1.dp,Aether.GlassBorderSoft),RoundedCornerShape(16.dp)).clickable(onClick=onClick).padding(vertical=14.dp,horizontal=12.dp),contentAlignment=Alignment.Center){Text(label,style=MaterialTheme.typography.labelLarge,color=Aether.Ink,maxLines=1,overflow=TextOverflow.Ellipsis)}}
@Composable private fun ActionGrid(actions:List<Pair<String,()->Unit>>){Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){actions.chunked(2).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){row.forEach{(n,a)->AetherPill(n,Modifier.weight(1f),a)};if(row.size==1)Spacer(Modifier.weight(1f))}}}}

// --- Extra Aether primitives -------------------------------------------------
@Composable private fun PrimaryButton(label:String,modifier:Modifier=Modifier,enabled:Boolean=true,onClick:()->Unit){
    val brush=if(enabled)Brush.horizontalGradient(listOf(Aether.Amethyst,Aether.Cyan)) else Brush.horizontalGradient(listOf(Aether.Slate,Aether.Slate))
    Box((if(enabled)modifier.clickable(onClick=onClick) else modifier).clip(RoundedCornerShape(16.dp)).background(brush).padding(vertical=15.dp),contentAlignment=Alignment.Center){
        Text(label,style=MaterialTheme.typography.labelLarge,color=if(enabled)Aether.Void else Aether.InkFaint)
    }
}
@Composable private fun ToggleRow(title:String,subtitle:String,checked:Boolean,onToggle:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium,color=Aether.Ink);if(subtitle.isNotBlank())Text(subtitle,style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)}
        Switch(checked=checked,onCheckedChange=onToggle)
    }
}
@Composable private fun Tag(text:String,accent:Color=Aether.InkMuted){Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Aether.GlassStrong).padding(horizontal=8.dp,vertical=3.dp)){Text(text,style=MaterialTheme.typography.labelSmall,color=accent)}}
@Composable private fun RowScope.StatTile(value:String,label:String){Column(Modifier.weight(1f)){Text(value,style=MaterialTheme.typography.headlineMedium,color=Aether.Ink);Text(label.uppercase(),style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)}}
@Composable private fun Stepper(label:String,onMinus:()->Unit,onPlus:()->Unit){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){AetherPill("−",Modifier.width(52.dp),onMinus);Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=Aether.InkMuted);AetherPill("+",Modifier.width(52.dp),onPlus)}}
private fun routeActionColor(a:RouteAction)=when(a){RouteAction.PROXY->Aether.Amethyst;RouteAction.DIRECT->Aether.Cyan;RouteAction.BLOCK->Aether.Danger}

@Composable private fun Library(repo:AppRepository,onConnect:(ProxyProfile)->Unit,onImportFile:()->Unit){
    var url by remember{mutableStateOf("")};var name by remember{mutableStateOf("")};var raw by remember{mutableStateOf("")}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Header("Node Library","Sources, subscriptions & saved nodes")}
        item{Row(Modifier.padding(horizontal=14.dp).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            GlassCard(Modifier.weight(1f)){Row{StatTile(repo.profiles.size.toString(),"Nodes")}}
            GlassCard(Modifier.weight(1f)){Row{StatTile(repo.subscriptions.size.toString(),"Sources")}}
        }}
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
            Text("Add a subscription",style=MaterialTheme.typography.titleMedium,color=Aether.Ink)
            OutlinedTextField(url,{url=it},label={Text("Subscription URL")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(name,{name=it},label={Text("Name (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                PrimaryButton("Add",Modifier.weight(1f),enabled=url.startsWith("http")){repo.addSubscription(name,url);url="";name=""}
                AetherPill("Refresh all",Modifier.weight(1f)){if(repo.subscriptions.isNotEmpty())repo.refreshAll()}
                AetherPill("File",Modifier.weight(1f),onImportFile)
            }
        }}
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
            Text("Paste configs",style=MaterialTheme.typography.titleMedium,color=Aether.Ink)
            OutlinedTextField(raw,{raw=it},label={Text("URI list / base64 / Xray JSON")},minLines=3,modifier=Modifier.fillMaxWidth())
            PrimaryButton("Import pasted data",Modifier.fillMaxWidth(),enabled=raw.isNotBlank()){repo.importText(raw);raw=""}
        }}
        if(repo.subscriptions.isNotEmpty())item{SectionLabel("Subscriptions")}
        items(repo.subscriptions,key={it.id}){s->GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
            Text(s.name,style=MaterialTheme.typography.titleMedium,color=Aether.Ink)
            Text(s.url,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){AetherPill("Refresh",Modifier.weight(1f)){repo.refresh(s.id)};AetherPill("Remove",Modifier.weight(1f)){repo.removeSubscription(s.id)}}
        }}
        item{SectionLabel("Saved nodes · ${repo.profiles.size}")}
        items(repo.profiles,key={it.id}){p->GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text(p.name,style=MaterialTheme.typography.titleMedium,color=Aether.Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(p.subscriptionName,style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)
                }
                PrimaryButton("Connect",Modifier.width(118.dp)){onConnect(p)}
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                if(p.scheme.isNotBlank())Tag(p.scheme.uppercase(),Aether.AmethystBright)
                if(p.transport.isNotBlank())Tag(p.transport)
                if(p.security.isNotBlank())Tag(p.security)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){AetherPill("Full test",Modifier.weight(1f)){repo.fullTest(p)};AetherPill("Delete",Modifier.weight(1f)){repo.removeProfile(p.id)}}
        }}
    }
}

@Composable private fun Lab(repo:AppRepository,onConnect:(ProxyProfile)->Unit){
    val best=repo.benchmarks.maxByOrNull{it.score}?.score?.takeIf{it>0}?:1.0
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Header("Tunnel Lab","Real Xray SOCKS benchmarks, ranked")}
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
            Text("Weighted score",style=MaterialTheme.typography.titleMedium,color=Aether.Ink)
            Text("Success 55% · latency 20% · jitter 10% · throughput 15%",style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)
            PrimaryButton(if(repo.busy)"Running…" else "Run benchmark + connect best",Modifier.fillMaxWidth(),enabled=!repo.busy&&repo.profiles.isNotEmpty()){repo.smart(onConnect)}
        }}
        if(repo.benchmarks.isEmpty())item{Text("No results yet — run a benchmark to rank your nodes.",Modifier.padding(horizontal=22.dp),style=MaterialTheme.typography.bodyMedium,color=Aether.InkFaint)}
        itemsIndexed(repo.benchmarks){i,r->GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("#${i+1}",style=MaterialTheme.typography.titleMedium,color=if(i==0)Aether.CyanBright else Aether.InkFaint)
                Spacer(Modifier.width(12.dp))
                Text(r.name,style=MaterialTheme.typography.titleMedium,color=Aether.Ink,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                Text("${"%.1f".format(r.score)}",style=MaterialTheme.typography.titleMedium,color=Aether.AmethystBright)
            }
            ScoreBar((r.score/best).toFloat().coerceIn(0f,1f))
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                Tag("${r.success}% ok"); Tag("${"%.0f".format(r.latencyMs)} ms"); Tag("jit ${"%.0f".format(r.jitterMs)}"); Tag("${"%.0f".format(r.bytesPerSecond/1024)} KB/s")
            }
        }}
    }
}
@Composable private fun ScoreBar(fraction:Float){Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Aether.GlassStrong)){Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(Aether.Amethyst,Aether.Cyan))))}}

@Composable private fun Radar(repo:AppRepository){
    var channel by remember{mutableStateOf("")};var token by remember{mutableStateOf(repo.cloudflareToken())};var account by remember{mutableStateOf(repo.cloudflareAccount())};var key by remember{mutableStateOf(repo.cloudflareKey())};var script by remember{mutableStateOf("marbleng-radar")}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Header("Telegram Radar","Discover → gate → tunnel-test → subscribe")}
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
            Text("Scan a channel",style=MaterialTheme.typography.titleMedium,color=Aether.Ink)
            OutlinedTextField(channel,{channel=it},label={Text("Channel e.g. @channel")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                PrimaryButton(if(repo.busy)"Working…" else "Scan + test",Modifier.weight(1f),enabled=channel.isNotBlank()&&!repo.busy){repo.telegram(channel)}
                AetherPill("Import passed",Modifier.weight(1f)){if(repo.radarConfigs.isNotEmpty())repo.importRadar()}
            }
            Text("Pass ≥${repo.settings.telegramPassMinSuccess}% • ${repo.settings.telegramTcpSamples} samples • max ${repo.settings.telegramMaxConfigs}",style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)
            if(repo.channels().isNotEmpty())Text("Saved: "+repo.channels().take(8).joinToString(" · "),style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)
        }}
        if(repo.radarResults.isNotEmpty())item{SectionLabel("Results · ${repo.radarResults.size}")}
        items(repo.radarResults.take(30)){r->GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
            Text(r.name,style=MaterialTheme.typography.titleMedium,color=Aether.Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){Tag("${r.success}%",if(r.success>=repo.settings.telegramPassMinSuccess)Aether.Emerald else Aether.InkMuted);Tag("${"%.0f".format(r.latencyMs)} ms");Tag("score ${"%.1f".format(r.score)}")}
        }}
        item{SectionLabel("Cloudflare Worker")}
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
            Text("Deploy a relay Worker",style=MaterialTheme.typography.titleMedium,color=Aether.Ink)
            Text("Credentials are encrypted with the Android Keystore.",style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)
            OutlinedTextField(token,{token=it},label={Text("API token")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(account,{account=it},label={Text("Account ID")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(script,{script=it},label={Text("Worker name")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(key,{key=it},label={Text("Worker access key")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){PrimaryButton("Deploy",Modifier.weight(1f),enabled=token.isNotBlank()){repo.deployWorker(token,account,script,key)};AetherPill("Forget",Modifier.weight(1f)){repo.forgetCloudflare()}}
        }}
    }
}

@Composable private fun Settings(repo:AppRepository,onDialog:(String)->Unit){
    val s=repo.settings
    var ruleName by remember{mutableStateOf("")};var ruleAction by remember{mutableStateOf(RouteAction.DIRECT)}
    var ruleDomains by remember{mutableStateOf("")};var ruleIps by remember{mutableStateOf("")}
    var geoip by remember{mutableStateOf(s.geoipUrl)};var geosite by remember{mutableStateOf(s.geositeUrl)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Header("Settings","Connection, routing, appearance & diagnostics")}

        // Connection ---------------------------------------------------------
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
            Text("Connection mode",style=MaterialTheme.typography.titleMedium,color=Aether.Ink)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(selected=s.tunnelMode=="vpn",onClick={repo.updateSettings(s.copy(tunnelMode="vpn"))},label={Text("Full Tunnel (VPN)")})
                FilterChip(selected=s.tunnelMode=="proxy",onClick={repo.updateSettings(s.copy(tunnelMode="proxy"))},label={Text("Local Proxy")})
            }
            Text(if(s.tunnelMode=="proxy")"Local SOCKS proxy on 127.0.0.1:${s.localProxyPort} — point your apps/browser here." else "Device-wide TUN capture through HEV + Xray.",style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)
            if(s.tunnelMode=="proxy")Stepper("Local proxy port ${s.localProxyPort}",{repo.updateSettings(s.copy(localProxyPort=(s.localProxyPort-1).coerceAtLeast(1025)))},{repo.updateSettings(s.copy(localProxyPort=(s.localProxyPort+1).coerceAtMost(65535)))})
        }}

        // Routing ------------------------------------------------------------
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
            ToggleRow("Smart routing","Split traffic between proxy, direct & block",s.routingEnabled){repo.updateSettings(s.copy(routingEnabled=it))}
            if(s.routingEnabled){
                HorizontalDivider(color=Aether.GlassBorderSoft)
                Text("Routing databases",style=MaterialTheme.typography.labelLarge,color=Aether.Ink)
                Text(if(repo.geoReady())"Installed · ${repo.geoStatus()}" else "Not downloaded yet · ${repo.geoStatus()}",style=MaterialTheme.typography.labelSmall,color=if(repo.geoReady())Aether.Emerald else Aether.InkFaint)
                OutlinedTextField(geoip,{geoip=it},label={Text("geoip.dat URL")},singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(geosite,{geosite=it},label={Text("geosite.dat URL")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    PrimaryButton(if(repo.busy)"Downloading…" else "Save & download",Modifier.weight(1f),enabled=!repo.busy&&geoip.startsWith("http")&&geosite.startsWith("http")){repo.updateSettings(s.copy(geoipUrl=geoip,geositeUrl=geosite));repo.downloadGeoAssets(true)}
                    AetherPill("Re-download",Modifier.weight(1f)){repo.downloadGeoAssets(true)}
                }
                Text("Downloaded once and cached; geoip:/geosite: rules need these files.",style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)
                HorizontalDivider(color=Aether.GlassBorderSoft)
                Text("Domain strategy",style=MaterialTheme.typography.labelLarge,color=Aether.Ink)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    listOf("AsIs","IPIfNonMatch","IPOnDemand").forEach{st->FilterChip(selected=s.routeDomainStrategy==st,onClick={repo.updateSettings(s.copy(routeDomainStrategy=st))},label={Text(st)})}
                }
                ToggleRow("Bypass LAN / private IPs","geoip:private routed direct",s.bypassLan){repo.updateSettings(s.copy(bypassLan=it))}
                ToggleRow("Block ads & trackers","geosite:category-ads-all blocked",s.blockAds){repo.updateSettings(s.copy(blockAds=it))}
            }
        }}
        if(s.routingEnabled){
            item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
                Text("Add routing rule",style=MaterialTheme.typography.titleMedium,color=Aether.Ink)
                OutlinedTextField(ruleName,{ruleName=it},label={Text("Rule name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){RouteAction.entries.forEach{a->FilterChip(selected=ruleAction==a,onClick={ruleAction=a},label={Text(a.name.lowercase().replaceFirstChar{it.uppercase()})})}}
                OutlinedTextField(ruleDomains,{ruleDomains=it},label={Text("Domains (comma / newline) e.g. geosite:cn, ir")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(ruleIps,{ruleIps=it},label={Text("IPs / CIDR e.g. geoip:cn, 10.0.0.0/8")},modifier=Modifier.fillMaxWidth())
                PrimaryButton("Add rule",Modifier.fillMaxWidth(),enabled=ruleDomains.isNotBlank()||ruleIps.isNotBlank()){repo.addRoutingRule(ruleName,ruleAction,ruleDomains,ruleIps);ruleName="";ruleDomains="";ruleIps=""}
            }}
            if(repo.routingRules.isNotEmpty())item{SectionLabel("Rules · ${repo.routingRules.size}")}
            items(repo.routingRules,key={it.id}){rule->GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text(rule.name,style=MaterialTheme.typography.titleMedium,color=Aether.Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text((rule.domains+rule.ips).joinToString(" · ").ifBlank{"no matchers"},style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    Switch(checked=rule.enabled,onCheckedChange={repo.toggleRoutingRule(rule.id)})
                }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Tag(rule.action.name,routeActionColor(rule.action))
                    Spacer(Modifier.weight(1f))
                    AetherPill("Delete",Modifier.width(110.dp)){repo.removeRoutingRule(rule.id)}
                }
            }}
        }

        // Smart Route intelligence ------------------------------------------
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
            Text("Smart Route ranking",style=MaterialTheme.typography.titleMedium,color=Aether.Ink)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){BenchMode.entries.forEach{m->FilterChip(selected=s.benchMode==m,onClick={repo.updateSettings(s.copy(benchMode=m))},label={Text(benchModeLabel(m))})}}
            Text("Candidates ${s.benchCandidates} • samples ${s.benchSamples} • timeout ${s.benchTimeoutSec}s",style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)
            Stepper("Candidate pool",{repo.updateSettings(s.copy(benchCandidates=(s.benchCandidates-5).coerceAtLeast(5)))},{repo.updateSettings(s.copy(benchCandidates=(s.benchCandidates+5).coerceAtMost(80)))})
        }}

        // Appearance ---------------------------------------------------------
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
            Text("Appearance",style=MaterialTheme.typography.titleMedium,color=Aether.Ink)
            Text("Accent palette (Aether Flow is the default)",style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("aurora" to "Aurora","ocean" to "Ocean","sunset" to "Sunset","matrix" to "Matrix","mono" to "Mono").forEach{(id,label)->FilterChip(selected=s.theme==id,onClick={repo.updateSettings(s.copy(theme=id))},label={Text(label)})}}
        }}

        // Privacy ------------------------------------------------------------
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
            Text("Privacy guard",style=MaterialTheme.typography.titleMedium,color=Aether.Ink)
            Text("ALWAYS ON · fail-closed runtime · remote DNS · localhost-only SOCKS",style=MaterialTheme.typography.labelSmall,color=Aether.Emerald)
            Text("For the strongest kill switch, enable Android Always-on VPN + Block connections without VPN.",style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)
        }}

        // Telegram policy ----------------------------------------------------
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
            Text("Telegram test policy",style=MaterialTheme.typography.titleMedium,color=Aether.Ink)
            ToggleRow("Auto passed-sub","Save nodes that pass the tunnel lab",s.telegramAutoSub){repo.updateSettings(s.copy(telegramAutoSub=it))}
            ToggleRow("Smart gate","TCP pre-check before full test",s.telegramTcpGate){repo.updateSettings(s.copy(telegramTcpGate=it))}
            Stepper("Pass threshold ${s.telegramPassMinSuccess}%",{repo.updateSettings(s.copy(telegramPassMinSuccess=(s.telegramPassMinSuccess-5).coerceAtLeast(25)))},{repo.updateSettings(s.copy(telegramPassMinSuccess=(s.telegramPassMinSuccess+5).coerceAtMost(100)))})
        }}

        // Native info --------------------------------------------------------
        item{GlassCard(Modifier.padding(horizontal=14.dp).fillMaxWidth()){
            Text("Native engine",style=MaterialTheme.typography.titleMedium,color=Aether.Ink)
            Text("SOCKS5h 127.0.0.1:${s.socksPort} • LAN exposure locked off",style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)
            Text("HEV + Xray: arm64-v8a • armeabi-v7a • x86_64 • x86",style=MaterialTheme.typography.labelSmall,color=Aether.InkFaint)
        }}

        item{SectionLabel("Diagnostics")}
        item{ActionGrid(listOf("⬆ Check cores" to {repo.checkCoreUpdates()},"🔐 Core lock" to {onDialog("Core lock")},"🧰 Doctor" to {onDialog("System Doctor")},"🗂 Capabilities" to {onDialog("Capabilities")},"🧾 Logs" to {onDialog("Logs")},"🕘 History" to {onDialog("History")},"☁ Forget Worker" to {repo.forgetCloudflare()},"↺ Reset settings" to {repo.resetSettings()}))}
    }
}
