package com.marbleng.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

private enum class Tab(val label: String, val glyph: String) {
    HOME("Deck", "⌁"),
    LIB("Library", "▦"),
    LAB("Lab", "◎"),
    RADAR("Radar", "◉"),
    SETTINGS("Settings", "⚙︎")
}

@Composable
fun MarbleApp(repo: AppRepository, onConnect: (ProxyProfile) -> Unit, onImportFile: () -> Unit) {
    AetherFlowTheme(repo.settings.theme) {
        var tab by remember { mutableStateOf(Tab.HOME) }
        var dialog by remember { mutableStateOf<String?>(null) }
        val snackbar = remember { SnackbarHostState() }

        LaunchedEffect(repo.message, repo.busy) {
            if (!repo.busy && repo.message.isNotBlank()) {
                snackbar.showSnackbar(repo.message, duration = SnackbarDuration.Short)
                repo.clearMessage()
            }
        }

        Scaffold(
            containerColor = Aether.Void,
            bottomBar = { ModernBottomBar(tab, onSelect = { tab = it }) },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { pad ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.025f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            ) {
                when (tab) {
                    Tab.HOME -> AetherDeck(repo, onConnect, onOpenLibrary = { tab = Tab.LIB }) { dialog = it }
                    Tab.LIB -> Library(repo, onConnect, onImportFile)
                    Tab.LAB -> Lab(repo, onConnect)
                    Tab.RADAR -> Radar(repo)
                    Tab.SETTINGS -> Settings(repo) { dialog = it }
                }
                if (repo.busy) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }

            dialog?.let { d ->
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } },
                    title = { Text(d) },
                    text = { SelectionContainer { Text(dialogText(d, repo)) } }
                )
            }
        }
    }
}

private fun dialogText(d: String, r: AppRepository) = when (d) {
    "Logs" -> r.readLogs()
    "Core lock" -> r.coreLock()
    "Capabilities" -> r.capabilities()
    "System Doctor" -> r.doctor()
    "History" -> r.history.takeLast(100).reversed().joinToString("\n") {
        "${DateFormat.getDateTimeInstance().format(Date(it.at))} • ${it.name} • ${it.reason}"
    }
    else -> "MarbleNG"
}

@Composable
private fun ModernBottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Surface(
        color = Aether.VoidElevated,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, Aether.GlassBorderSoft)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Tab.entries.forEach { tab ->
                val active = selected == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        tab.glyph,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (active) MaterialTheme.colorScheme.secondary else Aether.InkFaint
                    )
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) Aether.Ink else Aether.InkMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 18.dp, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Aether.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Aether.InkFaint)
        }
        trailing?.invoke()
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 2.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Aether.InkFaint)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Aether.InkFaint)
        }
    }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Aether.Glass)
            .border(1.dp, Aether.GlassBorderSoft, RoundedCornerShape(20.dp))
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun SmallBadge(text: String, accent: Color = MaterialTheme.colorScheme.secondary) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = accent,
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.20f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun EmptyState(title: String, body: String) {
    Panel(Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Aether.Ink)
        Text(body, style = MaterialTheme.typography.bodySmall, color = Aether.InkFaint)
    }
}

// ---------------------------------------------------------------------------------------------
// LIBRARY
// ---------------------------------------------------------------------------------------------

private data class RenameTarget(val kind: String, val id: String, val current: String)

@Composable
private fun Library(repo: AppRepository, onConnect: (ProxyProfile) -> Unit, onImportFile: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var raw by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var addOpen by remember { mutableStateOf(false) }
    var pasteOpen by remember { mutableStateOf(false) }
    var filterId by remember { mutableStateOf("all") }
    var renameTarget by remember { mutableStateOf<RenameTarget?>(null) }
    var renameText by remember { mutableStateOf("") }
    var moveMenuFor by remember { mutableStateOf<String?>(null) }

    // Recomputed directly (no size-keyed remember) since rename/move mutate elements in place
    // without changing list size, and a size-only key would then serve stale derived data.
    val allProfiles = repo.profiles
    val counts = allProfiles.groupingBy { it.subscriptionId }.eachCount()
    val sourceFilters = listOf("all" to "All", "manual" to "Manual") + repo.subscriptions.map { it.id to it.name }

    val filteredBySource = if (filterId == "all") allProfiles.toList() else allProfiles.filter { it.subscriptionId == filterId }
    val visibleProfiles = if (search.isBlank()) filteredBySource else filteredBySource.filter {
        it.name.contains(search, true) || it.subscriptionName.contains(search, true) ||
            it.scheme.contains(search, true) || it.host.contains(search, true)
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(if (target.kind == "sub") "Rename subscription" else "Rename node") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (target.kind == "sub") repo.renameSubscription(target.id, renameText)
                        else repo.renameProfile(target.id, renameText)
                        renameTarget = null
                    },
                    enabled = renameText.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            PageHeader(
                "Library",
                "Subscriptions and nodes in one compact workspace",
                trailing = { SmallBadge("${repo.profiles.size} nodes") }
            )
        }

        item {
            Panel(Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Add source", style = MaterialTheme.typography.titleMedium, color = Aether.Ink)
                        Text("Subscription link, file, or pasted configs", style = MaterialTheme.typography.bodySmall, color = Aether.InkFaint)
                    }
                    TextButton(onClick = { addOpen = !addOpen }) { Text(if (addOpen) "Hide" else "Add") }
                }
                if (addOpen) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Subscription URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { repo.addSubscription(name, url); url = ""; name = "" },
                            enabled = url.startsWith("http") && !repo.busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Add") }
                        OutlinedButton(
                            onClick = repo::refreshAll,
                            enabled = repo.subscriptions.isNotEmpty() && !repo.busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Refresh all") }
                        OutlinedButton(onClick = onImportFile) { Text("File") }
                    }
                    TextButton(onClick = { pasteOpen = !pasteOpen }) {
                        Text(if (pasteOpen) "Hide pasted import" else "Import URI list / base64 / Xray JSON")
                    }
                    if (pasteOpen) {
                        OutlinedTextField(
                            value = raw,
                            onValueChange = { raw = it },
                            label = { Text("Paste configs") },
                            minLines = 3,
                            maxLines = 7,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { repo.importText(raw); raw = ""; pasteOpen = false },
                            enabled = raw.isNotBlank() && !repo.busy
                        ) { Text("Import pasted data") }
                    }
                }
            }
        }

        if (repo.subscriptions.isNotEmpty()) {
            item { SectionHeader("Subscriptions", "Rename, refresh or remove a source without touching the rest") }
            items(repo.subscriptions, key = { it.id }) { sub ->
                Panel(Modifier.padding(horizontal = 14.dp).fillMaxWidth(), PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sub.name, fontWeight = FontWeight.SemiBold, color = Aether.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(sub.url, style = MaterialTheme.typography.bodySmall, color = Aether.InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        SmallBadge("${counts[sub.id] ?: 0}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { repo.refresh(sub.id) }, enabled = !repo.busy) { Text("Refresh") }
                        TextButton(onClick = { renameText = sub.name; renameTarget = RenameTarget("sub", sub.id, sub.name) }) { Text("Rename") }
                        TextButton(onClick = { repo.removeSubscription(sub.id) }) { Text("Remove") }
                    }
                }
            }
        }

        item { SectionHeader("Node browser", "Filter by source, search, test and manage nodes") }
        item {
            Row(
                Modifier.padding(horizontal = 14.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                sourceFilters.forEach { (id, label) ->
                    FilterChip(
                        selected = filterId == id,
                        onClick = { filterId = id },
                        label = { Text("$label (${if (id == "all") allProfiles.size else counts[id] ?: 0})") }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search nodes") },
                singleLine = true,
                modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth()
            )
        }

        if (visibleProfiles.isEmpty()) {
            item { EmptyState("No matching nodes", "Add a subscription, import configs, or clear the search/filter.") }
        }

        items(visibleProfiles, key = { it.id }) { profile ->
            val bench = repo.benchmarks.firstOrNull { it.profileId == profile.id }
            Panel(Modifier.padding(horizontal = 14.dp).fillMaxWidth(), PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, fontWeight = FontWeight.SemiBold, color = Aether.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(profile.subscriptionName, profile.scheme, profile.transport, profile.security)
                                .filter { it.isNotBlank() }.joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = Aether.InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (bench != null && bench.success > 0) {
                        SmallBadge("${bench.latencyMs.toInt()} ms")
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(
                        onClick = { onConnect(profile) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text("Connect") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { repo.fullTest(profile) }, enabled = !repo.busy) { Text("Test") }
                    TextButton(onClick = { renameText = profile.name; renameTarget = RenameTarget("profile", profile.id, profile.name) }) { Text("Rename") }
                    Box {
                        TextButton(onClick = { moveMenuFor = profile.id }) { Text("Move") }
                        DropdownMenu(expanded = moveMenuFor == profile.id, onDismissRequest = { moveMenuFor = null }) {
                            repo.moveTargets().filter { it.first != profile.subscriptionId }.forEach { (id, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { repo.moveProfile(profile.id, id); moveMenuFor = null }
                                )
                            }
                        }
                    }
                    TextButton(onClick = { repo.removeProfile(profile.id) }) { Text("Delete") }
                }
                if (profile.host.isNotBlank()) {
                    Text(
                        "${profile.host}:${profile.port}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Aether.InkFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// LAB
// ---------------------------------------------------------------------------------------------

@Composable
private fun Lab(repo: AppRepository, onConnect: (ProxyProfile) -> Unit) {
    val settings = repo.settings
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            PageHeader(
                "Tunnel Lab",
                "Real SOCKS + TLS + HTTP tests, ranked by reliability and route quality",
                trailing = { SmallBadge("${repo.benchmarks.size} results") }
            )
        }
        item {
            Panel(Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Benchmark profile", style = MaterialTheme.typography.titleMedium, color = Aether.Ink)
                        Text(
                            "${settings.benchCandidates} candidates · ${settings.benchSamples} samples · ${settings.benchTimeoutSec}s timeout",
                            style = MaterialTheme.typography.bodySmall,
                            color = Aether.InkFaint
                        )
                    }
                    SmallBadge(benchModeLabel(settings.benchMode), MaterialTheme.colorScheme.primary)
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    BenchMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.benchMode == mode,
                            onClick = { repo.updateSettings(settings.copy(benchMode = mode)) },
                            label = { Text(benchModeLabel(mode)) }
                        )
                    }
                }
                Button(
                    onClick = { repo.smart(onConnect) },
                    enabled = !repo.busy && repo.profiles.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Benchmark and connect best") }
                Text(
                    "Score = success 55% · latency 20% · jitter 10% · throughput 15%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Aether.InkFaint
                )
            }
        }

        if (repo.benchmarks.isEmpty()) {
            item { EmptyState("No benchmark yet", "Run the lab to see ranked nodes, latency, jitter, throughput and success rate here.") }
        } else {
            item { SectionHeader("Ranked results", "Best score first") }
            items(repo.benchmarks, key = { it.profileId }) { result ->
                val rank = repo.benchmarks.indexOf(result) + 1
                Panel(Modifier.padding(horizontal = 14.dp).fillMaxWidth(), PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                        ) { Box(contentAlignment = Alignment.Center) { Text("#$rank", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) } }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(result.name, fontWeight = FontWeight.SemiBold, color = Aether.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "score ${"%.1f".format(result.score)} · success ${result.success}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = Aether.InkMuted
                            )
                        }
                        repo.profile(result.profileId)?.let { p ->
                            FilledTonalButton(
                                onClick = { onConnect(p) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) { Text("Use") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricBadge("RTT", "${result.latencyMs.toInt()} ms", Modifier.weight(1f))
                        MetricBadge("JITTER", "${result.jitterMs.toInt()} ms", Modifier.weight(1f))
                        MetricBadge("SPEED", formatSpeed(result.bytesPerSecond), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBadge(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Aether.Void.copy(alpha = 0.5f)).padding(9.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Aether.InkFaint)
        Text(value, style = MaterialTheme.typography.labelLarge, color = Aether.Ink)
    }
}

// ---------------------------------------------------------------------------------------------
// RADAR
// ---------------------------------------------------------------------------------------------

@Composable
private fun Radar(repo: AppRepository) {
    var channel by remember { mutableStateOf("") }
    var token by remember { mutableStateOf(repo.cloudflareToken()) }
    var account by remember { mutableStateOf(repo.cloudflareAccount()) }
    var key by remember { mutableStateOf(repo.cloudflareKey()) }
    var script by remember { mutableStateOf("marbleng-radar") }
    var workerOpen by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            PageHeader(
                "Radar",
                "Scan public Telegram previews, gate candidates, then test through real Xray",
                trailing = { SmallBadge("${repo.radarConfigs.size} passed") }
            )
        }

        item {
            Panel(Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Telegram scanner", style = MaterialTheme.typography.titleMedium, color = Aether.Ink)
                        Text(
                            "Pass ≥${repo.settings.telegramPassMinSuccess}% · ${repo.settings.telegramTcpSamples} samples · max ${repo.settings.telegramMaxConfigs}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Aether.InkFaint
                        )
                    }
                    SmallBadge(if (repo.settings.telegramTcpGate) "Smart gate" else "No gate")
                }
                OutlinedTextField(
                    value = channel,
                    onValueChange = { channel = it },
                    label = { Text("Channel, e.g. @channel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { repo.telegram(channel) },
                        enabled = channel.isNotBlank() && !repo.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Scan + test") }
                    OutlinedButton(
                        onClick = repo::importRadar,
                        enabled = repo.radarConfigs.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Import passed") }
                }
                if (repo.channels().isNotEmpty()) {
                    Text(
                        "Recent: ${repo.channels().take(5).joinToString("  ·  ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Aether.InkFaint,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (repo.radarResults.isEmpty()) {
            item { EmptyState("Radar is quiet", "Run a scan to populate tested candidates. Passed configs can be imported into the Library in one tap.") }
        } else {
            item { SectionHeader("Tested candidates") }
            items(repo.radarResults.take(30), key = { it.profileId }) { result ->
                Panel(Modifier.padding(horizontal = 14.dp).fillMaxWidth(), PaddingValues(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(result.name, fontWeight = FontWeight.SemiBold, color = Aether.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${result.success}% success · ${result.latencyMs.toInt()} ms · score ${"%.1f".format(result.score)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Aether.InkMuted
                            )
                        }
                        SmallBadge(
                            if (result.success >= repo.settings.telegramPassMinSuccess) "PASSED" else "REJECTED",
                            if (result.success >= repo.settings.telegramPassMinSuccess) Aether.Emerald else Aether.Danger
                        )
                    }
                }
            }
        }

        item {
            SectionHeader("Cloudflare worker", "Credentials stay encrypted in Android Keystore")
            Panel(
                Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .clickable { workerOpen = !workerOpen }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Worker Center", style = MaterialTheme.typography.titleMedium, color = Aether.Ink)
                        Text(
                            if (workerOpen) "Tap header to collapse" else "Deploy or update the Radar worker only when needed",
                            style = MaterialTheme.typography.bodySmall,
                            color = Aether.InkFaint
                        )
                    }
                    Text(if (workerOpen) "−" else "+", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (workerOpen) {
                    OutlinedTextField(token, { token = it }, label = { Text("API token") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(account, { account = it }, label = { Text("Account ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(script, { script = it }, label = { Text("Worker name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(key, { key = it }, label = { Text("Worker access key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { repo.deployWorker(token, account, script, key) },
                            enabled = token.isNotBlank() && account.isNotBlank() && !repo.busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Deploy") }
                        OutlinedButton(onClick = repo::forgetCloudflare, modifier = Modifier.weight(1f)) { Text("Forget credentials") }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// SETTINGS
// ---------------------------------------------------------------------------------------------

@Composable
private fun Settings(repo: AppRepository, onDialog: (String) -> Unit) {
    val s = repo.settings
    var geoIpUrl by remember(s.geoIpUrl) { mutableStateOf(s.geoIpUrl) }
    var geoSiteUrl by remember(s.geoSiteUrl) { mutableStateOf(s.geoSiteUrl) }
    var geoIpTags by remember(s.routeGeoIpTags) { mutableStateOf(s.routeGeoIpTags) }
    var geoSiteTags by remember(s.routeGeoSiteTags) { mutableStateOf(s.routeGeoSiteTags) }
    var directDomains by remember(s.routeDirectDomains) { mutableStateOf(s.routeDirectDomains) }
    var proxyDomains by remember(s.routeProxyDomains) { mutableStateOf(s.routeProxyDomains) }
    var blockDomains by remember(s.routeBlockDomains) { mutableStateOf(s.routeBlockDomains) }
    var directIps by remember(s.routeDirectIps) { mutableStateOf(s.routeDirectIps) }
    var blockIps by remember(s.routeBlockIps) { mutableStateOf(s.routeBlockIps) }
    var adsTag by remember(s.routeAdsTag) { mutableStateOf(s.routeAdsTag) }
    var routingAdvanced by remember { mutableStateOf(false) }
    val assets = repo.routingAssetStatus()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { PageHeader("Settings", "Connection, routing, benchmarks, Radar and maintenance") }

        item { SectionHeader("Connection") }
        item {
            Panel(Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
                Text("Transport mode", style = MaterialTheme.typography.titleMedium, color = Aether.Ink)
                Text(
                    "Choose between device-wide TUN and a localhost-only SOCKS5 endpoint.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Aether.InkFaint
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectTile(
                        "Full TUN",
                        "Android VpnService",
                        s.connectionMode == ConnectionMode.FULL_TUN,
                        Modifier.weight(1f)
                    ) { repo.setConnectionMode(ConnectionMode.FULL_TUN) }
                    SelectTile(
                        "Local proxy",
                        "127.0.0.1:${s.localProxyPort}",
                        s.connectionMode == ConnectionMode.LOCAL_PROXY,
                        Modifier.weight(1f)
                    ) { repo.setConnectionMode(ConnectionMode.LOCAL_PROXY) }
                }
                Text(
                    "Internal TUN SOCKS: 127.0.0.1:${s.socksPort} · Local proxy: 127.0.0.1:${s.localProxyPort}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Aether.InkFaint
                )
            }
        }

        item { SectionHeader("Routing", "Managed geo data + ordered direct / proxy / block policy") }
        item {
            Panel(Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Routing manager", style = MaterialTheme.typography.titleMedium, color = Aether.Ink)
                        Text("Fallback is always proxy; direct rules must match explicitly.", style = MaterialTheme.typography.bodySmall, color = Aether.InkFaint)
                    }
                    SmallBadge(routingModeLabel(s.routingMode), MaterialTheme.colorScheme.primary)
                }

                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    RoutingMode.entries.forEach { mode ->
                        FilterChip(
                            selected = s.routingMode == mode,
                            onClick = { repo.updateSettings(s.copy(routingMode = mode)) },
                            label = { Text(routingModeLabel(mode)) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssetTile("geoip.dat", assets.geoIpReady, assets.geoIpBytes, assets.geoIpRemote, Modifier.weight(1f))
                    AssetTile("geosite.dat", assets.geoSiteReady, assets.geoSiteBytes, assets.geoSiteRemote, Modifier.weight(1f))
                }

                OutlinedTextField(
                    geoIpUrl,
                    { geoIpUrl = it },
                    label = { Text("geoip.dat URL") },
                    placeholder = { Text("https://…/geoip.dat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    geoSiteUrl,
                    { geoSiteUrl = it },
                    label = { Text("geosite.dat URL") },
                    placeholder = { Text("https://…/geosite.dat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "A URL is downloaded once for that exact value. It is reused on every start; changing the URL downloads once again.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Aether.InkFaint
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            repo.updateSettings(s.copy(geoIpUrl = geoIpUrl.trim(), geoSiteUrl = geoSiteUrl.trim()))
                            repo.prepareRoutingAssets(false)
                        },
                        enabled = !repo.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Save + prepare") }
                    OutlinedButton(
                        onClick = {
                            repo.updateSettings(s.copy(geoIpUrl = geoIpUrl.trim(), geoSiteUrl = geoSiteUrl.trim()))
                            repo.prepareRoutingAssets(true)
                        },
                        enabled = !repo.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Update now") }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { routingAdvanced = !routingAdvanced }, modifier = Modifier.weight(1f)) {
                        Text(if (routingAdvanced) "Hide advanced rules" else "Advanced routing rules")
                    }
                    TextButton(onClick = repo::deleteRoutingAssets, enabled = !repo.busy) { Text("Delete local data") }
                }

                if (routingAdvanced) {
                    HorizontalDivider(color = Aether.GlassBorderSoft)
                    Text("Geo selectors", style = MaterialTheme.typography.labelLarge, color = Aether.Ink)
                    OutlinedTextField(
                        geoIpTags,
                        { geoIpTags = it },
                        label = { Text("Direct geoip tags") },
                        supportingText = { Text("Comma/newline list, e.g. private, ir") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        geoSiteTags,
                        { geoSiteTags = it },
                        label = { Text("Direct geosite tags") },
                        supportingText = { Text("Must exist in your geosite.dat") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Custom rules", style = MaterialTheme.typography.labelLarge, color = Aether.Ink)
                    RuleField("Direct domains", directDomains, { directDomains = it }, "example.com, geosite:tag")
                    RuleField("Force-proxy domains", proxyDomains, { proxyDomains = it }, "example.net")
                    RuleField("Block domains", blockDomains, { blockDomains = it }, "ads.example.com")
                    RuleField("Direct IP/CIDR", directIps, { directIps = it }, "10.0.0.0/8, geoip:tag")
                    RuleField("Block IP/CIDR", blockIps, { blockIps = it }, "203.0.113.0/24")

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Bypass private networks", style = MaterialTheme.typography.labelLarge, color = Aether.Ink)
                            Text("Adds geoip:private when routing is not Proxy all", style = MaterialTheme.typography.labelSmall, color = Aether.InkFaint)
                        }
                        Switch(
                            checked = s.routeBypassPrivate,
                            onCheckedChange = { repo.updateSettings(s.copy(routeBypassPrivate = it)) }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Block ad geosite tag", style = MaterialTheme.typography.labelLarge, color = Aether.Ink)
                            Text("Only enable if the tag exists in your geosite.dat", style = MaterialTheme.typography.labelSmall, color = Aether.InkFaint)
                        }
                        Switch(
                            checked = s.routeBlockAds,
                            onCheckedChange = { repo.updateSettings(s.copy(routeBlockAds = it)) }
                        )
                    }
                    if (s.routeBlockAds) {
                        OutlinedTextField(
                            adsTag,
                            { adsTag = it },
                            label = { Text("Ad geosite tag") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text("Domain strategy", style = MaterialTheme.typography.labelLarge, color = Aether.Ink)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("AsIs", "IPIfNonMatch", "IPOnDemand").forEach { strategy ->
                            FilterChip(
                                selected = s.routeDomainStrategy == strategy,
                                onClick = { repo.updateSettings(s.copy(routeDomainStrategy = strategy)) },
                                label = { Text(strategy) }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            repo.updateSettings(
                                s.copy(
                                    geoIpUrl = geoIpUrl.trim(),
                                    geoSiteUrl = geoSiteUrl.trim(),
                                    routeGeoIpTags = geoIpTags.trim(),
                                    routeGeoSiteTags = geoSiteTags.trim(),
                                    routeDirectDomains = directDomains.trim(),
                                    routeProxyDomains = proxyDomains.trim(),
                                    routeBlockDomains = blockDomains.trim(),
                                    routeDirectIps = directIps.trim(),
                                    routeBlockIps = blockIps.trim(),
                                    routeAdsTag = adsTag.trim()
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save routing rules") }
                }
            }
        }

        item { SectionHeader("Performance") }
        item {
            Panel(Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Smart benchmark", style = MaterialTheme.typography.titleMedium, color = Aether.Ink)
                        Text(
                            "${s.benchCandidates} candidates · ${s.benchSamples} samples · ${s.benchTimeoutSec}s timeout",
                            style = MaterialTheme.typography.bodySmall,
                            color = Aether.InkFaint
                        )
                    }
                    SmallBadge(benchModeLabel(s.benchMode))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    BenchMode.entries.forEach { mode ->
                        FilterChip(
                            selected = s.benchMode == mode,
                            onClick = { repo.updateSettings(s.copy(benchMode = mode)) },
                            label = { Text(benchModeLabel(mode)) }
                        )
                    }
                }
                StepperRow(
                    "Candidates",
                    s.benchCandidates,
                    onMinus = { repo.updateSettings(s.copy(benchCandidates = (s.benchCandidates - 5).coerceAtLeast(5))) },
                    onPlus = { repo.updateSettings(s.copy(benchCandidates = (s.benchCandidates + 5).coerceAtMost(80))) }
                )
                StepperRow(
                    "Samples",
                    s.benchSamples,
                    onMinus = { repo.updateSettings(s.copy(benchSamples = (s.benchSamples - 1).coerceAtLeast(1))) },
                    onPlus = { repo.updateSettings(s.copy(benchSamples = (s.benchSamples + 1).coerceAtMost(8))) }
                )
            }
        }

        item { SectionHeader("Radar policy") }
        item {
            Panel(Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
                StepperRow(
                    "Pass threshold",
                    s.telegramPassMinSuccess,
                    suffix = "%",
                    onMinus = { repo.updateSettings(s.copy(telegramPassMinSuccess = (s.telegramPassMinSuccess - 5).coerceAtLeast(25))) },
                    onPlus = { repo.updateSettings(s.copy(telegramPassMinSuccess = (s.telegramPassMinSuccess + 5).coerceAtMost(100))) }
                )
                ToggleRow("Smart TCP gate", "Pre-check candidates before expensive tunnel tests", s.telegramTcpGate) {
                    repo.updateSettings(s.copy(telegramTcpGate = it))
                }
                ToggleRow("Auto passed subscription", "Keep passed Radar configs as a managed library source", s.telegramAutoSub) {
                    repo.updateSettings(s.copy(telegramAutoSub = it))
                }
            }
        }

        item { SectionHeader("Appearance") }
        item {
            Panel(Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
                Text("Theme", style = MaterialTheme.typography.titleMedium, color = Aether.Ink)
                Text("Dark and Light, both tuned with energetic accent colors.", style = MaterialTheme.typography.bodySmall, color = Aether.InkFaint)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("dark" to "Dark", "light" to "Light").forEach { (id, label) ->
                        FilterChip(
                            selected = s.theme == id,
                            onClick = { repo.updateSettings(s.copy(theme = id)) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        item { SectionHeader("Maintenance") }
        item {
            Panel(Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MaintenanceButton("Check cores", Modifier.weight(1f)) { repo.checkCoreUpdates() }
                    MaintenanceButton("Doctor", Modifier.weight(1f)) { onDialog("System Doctor") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MaintenanceButton("Logs", Modifier.weight(1f)) { onDialog("Logs") }
                    MaintenanceButton("History", Modifier.weight(1f)) { onDialog("History") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MaintenanceButton("Capabilities", Modifier.weight(1f)) { onDialog("Capabilities") }
                    MaintenanceButton("Core lock", Modifier.weight(1f)) { onDialog("Core lock") }
                }
                HorizontalDivider(color = Aether.GlassBorderSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = repo::forgetCloudflare, modifier = Modifier.weight(1f)) { Text("Forget Worker") }
                    OutlinedButton(onClick = repo::resetSettings, modifier = Modifier.weight(1f)) { Text("Reset settings") }
                }
            }
        }
    }
}

@Composable
private fun SelectTile(title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) accent.copy(alpha = 0.13f) else Aether.Void.copy(alpha = 0.35f))
            .border(1.dp, if (selected) accent.copy(alpha = 0.35f) else Aether.GlassBorderSoft, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = if (selected) Aether.Ink else Aether.InkMuted)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = if (selected) accent else Aether.InkFaint)
    }
}

@Composable
private fun AssetTile(name: String, ready: Boolean, bytes: Long, remote: Boolean, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Aether.Void.copy(alpha = 0.40f))
            .padding(10.dp)
    ) {
        Text(name, style = MaterialTheme.typography.labelLarge, color = Aether.Ink)
        Text(
            if (ready) "${formatBytes(bytes)} · ${if (remote) "remote" else "bundled/local"}" else "not prepared",
            style = MaterialTheme.typography.labelSmall,
            color = if (ready) Aether.Emerald else Aether.Danger
        )
    }
}

@Composable
private fun RuleField(label: String, value: String, onValueChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { Text(hint) },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    suffix: String = "",
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Aether.Ink, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onMinus, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text("−") }
        Text("$value$suffix", modifier = Modifier.widthIn(min = 54.dp), style = MaterialTheme.typography.labelLarge, color = Aether.Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedButton(onClick = onPlus, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text("+") }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Aether.Ink)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Aether.InkFaint)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun MaintenanceButton(label: String, modifier: Modifier, action: () -> Unit) {
    OutlinedButton(onClick = action, modifier = modifier) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

private fun benchModeLabel(mode: BenchMode) = when (mode) {
    BenchMode.RELIABLE -> "Reliability"
    BenchMode.BALANCED -> "Balanced"
    BenchMode.FAST -> "Maximum speed"
    BenchMode.TURBO -> "Turbo"
    BenchMode.CUSTOM -> "Custom"
}

private fun routingModeLabel(mode: RoutingMode) = when (mode) {
    RoutingMode.PROXY_ALL -> "Proxy all"
    RoutingMode.BYPASS_PRIVATE -> "Private bypass"
    RoutingMode.GEO_DIRECT -> "Geo direct"
    RoutingMode.CUSTOM -> "Custom"
}

private fun formatSpeed(bytesPerSecond: Double): String = when {
    bytesPerSecond <= 0 -> "—"
    bytesPerSecond >= 1024 * 1024 -> "%.1f MB/s".format(bytesPerSecond / (1024.0 * 1024.0))
    bytesPerSecond >= 1024 -> "%.0f KB/s".format(bytesPerSecond / 1024.0)
    else -> "%.0f B/s".format(bytesPerSecond)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KiB".format(bytes / 1024.0)
    else -> "$bytes B"
}
