#!/usr/bin/env python3
# MarbleNG source-wide architecture/integration preflight.
#
# Gradle/Kotlin/native compilers remain the syntax/type/link authority. This checker catches
# structural drift between subsystems before expensive native compilation starts.

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    file = ROOT / path
    if not file.is_file():
        raise AssertionError(f"missing required file: {path}")
    raw = file.read_bytes()
    if b"\x00" in raw:
        raise AssertionError(f"NUL byte in source: {path}")
    return raw.decode("utf-8")

files = {
    "main": read("app/src/main/java/com/marbleng/app/MainActivity.kt"),
    "permissions": read("app/src/main/java/com/marbleng/app/ui/MarblePermissionOnboarding.kt"),
    "theme": read("app/src/main/java/com/marbleng/app/ui/AetherTheme.kt"),
    "design": read("app/src/main/java/com/marbleng/app/ui/MarbleDesignSystem.kt"),
    "app": read("app/src/main/java/com/marbleng/app/MarbleApplication.kt"),
    "repo": read("app/src/main/java/com/marbleng/app/AppRepository.kt"),
    "store": read("app/src/main/java/com/marbleng/app/data/AppStore.kt"),
    "models": read("app/src/main/java/com/marbleng/app/model/Models.kt"),
    "vpn": read("app/src/main/java/com/marbleng/app/vpn/MarbleVpnService.kt"),
    "xray": read("app/src/main/java/com/marbleng/app/core/XrayManager.kt"),
    "hardener": read("app/src/main/java/com/marbleng/app/core/XrayConfigHardener.kt"),
    "bench": read("app/src/main/java/com/marbleng/app/core/BenchmarkEngine.kt"),
    "tuner": read("app/src/main/java/com/marbleng/app/core/ConnectionTuner.kt"),
    "optimizer": read("app/src/main/java/com/marbleng/app/core/ContinuousRouteOptimizer.kt"),
    "identity": read("app/src/main/java/com/marbleng/app/core/IdentityGuard.kt"),
    "shield": read("app/src/main/java/com/marbleng/app/core/IranShield.kt"),
    "intel": read("app/src/main/java/com/marbleng/app/core/MarbleIntelligence.kt"),
    "manual": read("app/src/main/java/com/marbleng/app/core/ManualConfigBuilder.kt"),
    "ssh": read("app/src/main/java/com/marbleng/app/core/SshTransportManager.kt"),
    "socks": read("app/src/main/java/com/marbleng/app/core/SocksHttpClient.kt"),
    "dpiFetch": read("app/src/main/java/com/marbleng/app/core/DpiAwareFetcher.kt"),
    "dpiPolicy": read("app/src/main/java/com/marbleng/app/core/DpiEvasionPolicy.kt"),
    "serverless": read("app/src/main/java/com/marbleng/app/core/ServerlessFreedomEngine.kt"),
    "udp": read("app/src/main/java/com/marbleng/app/core/SocksUdpProbe.kt"),
    "privacy": read("app/src/main/java/com/marbleng/app/net/PrivacyAuditor.kt"),
    "bug": read("app/src/main/java/com/marbleng/app/core/BugFinder.kt"),
    "diag": read("app/src/main/java/com/marbleng/app/core/RuntimeDiagnostics.kt"),
    "ui": read("app/src/main/java/com/marbleng/app/ui/Aether2026.kt"),
    "homeStyles": read("app/src/main/java/com/marbleng/app/ui/MarbleHomeStyles.kt"),
    "strings": read("app/src/main/java/com/marbleng/app/ui/MarbleStrings.kt"),
    "marbleApp": read("app/src/main/java/com/marbleng/app/ui/MarbleApp.kt"),
    "tile": read("app/src/main/java/com/marbleng/app/quicktile/MarbleQuickTileService.kt"),
    "manifest": read("app/src/main/AndroidManifest.xml"),
    "security": read("app/src/main/res/xml/network_security_config.xml"),
    "native": read("scripts/prepare-native.sh"),
    "build": read(".github/workflows/build.yml"),
    "gradle": read("app/build.gradle.kts"),
    "verify": read(".github/workflows/verify.yml"),
}

workflow_sources = "\n".join(
    path.read_text(encoding="utf-8")
    for path in sorted((ROOT / ".github" / "workflows").glob("*.yml"))
)

checks = []

def check(name: str, condition: bool) -> None:
    checks.append((name, bool(condition)))

def integer_constant(text: str, name: str) -> int:
    match = re.search(rf"\b{name}\s*=\s*([0-9_]+)", text)
    if not match:
        raise AssertionError(f"missing numeric constant {name}")
    return int(match.group(1).replace("_", ""))

def action_uses_minimum(text: str, action: str, minimum_major: int) -> bool:
    versions = re.findall(rf"uses:\s*{re.escape(action)}@v([0-9]+)\b", text)
    return bool(versions) and all(int(version) >= minimum_major for version in versions)

# Lifecycle and application boundaries.
check(
    "diagnostics installed before repository construction",
    files["app"].find("RuntimeDiagnostics.install") < files["app"].find("XrayManager("),
)
check("VPN consent preserves source identity", "KEY_PENDING_PROFILE_SOURCE" in files["main"])
check("SAF import runs on IO dispatcher", "withContext(Dispatchers.IO)" in files["main"])
check("SAF import has a hard size cap", "MAX_IMPORT_BYTES" in files["main"])
check(
    "old unbounded MainActivity readText is gone",
    "bufferedReader()?.use { it.readText() }" not in files["main"],
)

# Repository / persistence / exact Library identity.
check("exact last route is persisted", "setLastProfileRef(p.id, p.subscriptionId)" in files["repo"])
check("remembered route stores source id", "lastProfileSourceId" in files["store"])
check("source-aware profile lookup exists", "fun profile(id: String, sourceId: String? = null)" in files["repo"])
check("source-aware UI deletion exists", "removeProfile(profile.id, profile.subscriptionId)" in files["ui"])
check("active provider row survives refresh", "active-profile-preserved-on-refresh" in files["repo"])
check("node deletion is disconnected-only", "Disconnect before deleting Library nodes" in files["repo"])
check("source deletion is disconnected-only", "Disconnect before deleting a subscription source" in files["repo"])

# Management plane.
check("Android cleartext is disabled", 'cleartextTrafficPermitted="false"' in files["security"])
check("subscription policy is HTTPS only", "isHttpsSubscriptionUrl" in files["repo"])
check("subscription payload is bounded", "MAX_SUBSCRIPTION_BYTES" in files["repo"])
check(
    "HTTPS redirects cannot downgrade",
    "Subscription redirect left HTTPS" in files["repo"] + files["dpiFetch"],
)
check("DPI-aware subscription fetch is wired", "DpiAwareFetcher.fetch" in files["repo"])
check("GitHub raw uses jsDelivr mirror", "cdn.jsdelivr.net/gh" in files["dpiFetch"])
check(
    "serverless Freedom fragment profile exists",
    "marble-serverless-freedom" in files["serverless"]
    and "full-fragment" in files["serverless"]
    and '"protocol", "freedom"' in files["serverless"],
)
check(
    "serverless profile is not a MitM listener",
    "dokodemo" not in files["serverless"].lower(),
)
check("Home Freedom switch exists", "HomeServerlessSwitch" in files["ui"])
check(
    "connection access is contextual and ordered",
    "missingConnectionPermissions" in files["main"]
    and "ConnectionPermissionDialog" in files["main"]
    and all(step in files["permissions"] for step in ("VPN", "NOTIFICATIONS", "BATTERY"))
)
check(
    "font choices persist and reach the theme",
    "enum class AppFont" in files["models"]
    and "fontFamily" in files["store"]
    and "AppFont.entries" in files["ui"]
    and "fontId" in files["theme"],
)
check(
    "Marble Freedom is a selectable Library source",
    "freedomLibraryProfiles" in files["repo"]
    and "SOURCE_ID" in files["repo"]
    and "Freedom (" in files["ui"],
)
check(
    "bottom dock glass is scroll-conditional",
    "glass = contentScrolling || pagerState.isScrollInProgress" in files["ui"]
    and "if (glass)" in files["ui"]
    and "dockSurface" in files["ui"],
)
check(
    "connected Home exposes in-app IP details",
    "HomeIpRow(" in files["homeStyles"]
    and "IpDetailsDialog" in files["ui"]
    and "ipDetails" in files["strings"],
)

# MARBLE_HOME_STYLE_V110 — four presentations of one connection surface.
home_styles = ("BIOLUMINESCENT", "COSMIC_ORBIT", "COSMIC_IMMERSION", "PARAMETRIC")
check(
    "all four Home styles are modelled and persisted",
    "enum class HomeStyle" in files["models"]
    and all(style in files["models"] for style in home_styles)
    and "homeStyle" in files["store"]
    and "homeStyle = style.id" in files["ui"],
)
check(
    "every Home style has an implementation and is reachable",
    all(
        name in files["homeStyles"]
        for name in (
            "HomeStyleBioluminescent",
            "HomeStyleCosmicOrbit",
            "HomeStyleCosmicImmersion",
            "HomeStyleParametric",
        )
    )
    and "HomeStyleSurface(" in files["homeStyles"]
    and "HomeStyleSurface(" in files["ui"],
)
check(
    "every Home style renders the same evidence through one shared model",
    "data class HomeEvidence" in files["homeStyles"]
    and "buildHomeEvidence(" in files["ui"]
    and all(
        files["homeStyles"].count(widget) >= 4
        for widget in ("HomeIdentityBlock(", "HomeIpRow(", "HomeSessionStats(", "HomePowerControl(")
    ),
)
check(
    "Home evidence covers node, source, IP+flag+3 actions, uptime and ping",
    all(
        field in files["homeStyles"]
        for field in ("nodeName", "sourceName", "flag", "connectedSinceMs", "pingState")
    )
    and all(
        action in files["homeStyles"]
        for action in ("onCopyIp", "onRefreshIp", "onIpDetails", "onTestPing")
    ),
)
check(
    "no Home style wraps the connect control in a quality indicator",
    "PrismConnectionStage(" not in files["homeStyles"]
    and "qualityScore" not in files["homeStyles"]
    and "liveRouteScore" not in files["homeStyles"],
)
check(
    "connection ping is one-shot and never a background timer",
    "fun measureConnectionPing()" in files["repo"]
    and "connectionPingInFlight" in files["repo"]
    and "enum class ConnectionPingState" in files["models"]
    and "ConnectionPingState.FAILED" in files["repo"],
)
check(
    "session uptime comes from the repository, not the UI clock",
    "connectedSinceMs" in files["repo"]
    and "rememberUptimeLabel(" in files["homeStyles"],
)

# MARBLE_BILINGUAL_V110 — English/Persian with a device-locale default.
check(
    "product is bilingual with a device-locale default",
    "enum class AppLanguage" in files["models"]
    and "SYSTEM" in files["models"]
    and "appLanguage" in files["store"]
    and "ProvideMarbleLanguage(" in files["strings"]
    and "ProvideMarbleLanguage(repo.settings.appLanguage)" in files["marbleApp"],
)
check(
    "Persian selection mirrors the layout direction",
    "LocalLayoutDirection provides direction" in files["strings"]
    and "LayoutDirection.Rtl" in files["strings"],
)
check(
    "both languages define the same string surface",
    "EnglishStrings = MarbleStrings(" in files["strings"]
    and "PersianStrings = MarbleStrings(" in files["strings"]
    and files["strings"].count("language = MarbleLanguage") >= 2,
)
check(
    "language and Home style are user-changeable in Settings",
    "appLanguage = language.id" in files["ui"]
    and "AppLanguage.entries.forEach" in files["ui"]
    and "HomeStyle.entries.chunked(2)" in files["ui"],
)
check(
    "AMOLED navigation surface is transparent",
    "Color.Transparent.toArgb()" in files["theme"]
    and "isNavigationBarContrastEnforced=false" in files["theme"]
    and "@android:color/transparent" in read("app/src/main/res/values/styles.xml"),
)
check(
    "release packaging requires a stable signer",
    "signing.properties" in files["gradle"]
    and "unsigned APKs are not installable" in files["gradle"]
    and "apksigner" in files["build"],
)
appearance_settings = files["ui"].split("private fun AppearanceSettings", 1)
appearance_body = appearance_settings[1].split("private fun ConnectionSettings", 1)[0] if len(appearance_settings) == 2 else ""
check(
    "Home layout options are not exposed in Settings",
    "homeShowLiveQuality" in files["models"]
    and "homeShowRouteRibbon" in files["models"]
    and "homeShowFreedomSwitch" in files["models"]
    and "homeShowServerSelector" not in appearance_body
    and "homeShowLiveQuality" not in appearance_body
    and "homeShowRouteRibbon" not in appearance_body,
)
check("fragment inner hops avoid legacy chainEnabled", "chainEnabled" not in files["dpiPolicy"])
check(
    "tunnel management helper rejects cleartext",
    "Only HTTPS management requests are allowed while a tunnel is active" in files["socks"],
)

# Xray / HEV ownership and temporary instances.
check("Xray lifecycle generation guard exists", "lifecycleGeneration" in files["xray"])
check("temporary Xray port allocator exists", "reservedTemporaryPorts" in files["xray"])
check("temporary callback receives allocated port", "block(actualPort)" in files["xray"])
check("Benchmark consumes allocated live port", "{ livePort ->" in files["bench"])
check("real Xray Rank has no TCP-only rejection", 'failureReason = "tcp-precheck"' not in files["bench"])
check("Xray live log rotates per connection", "beginLiveLogSession()" in files["xray"])
check(
    "native builder verifies Marble JNI symbols",
    "Java_com_marbleng_app_nativebridge_HevTunnel_run" in files["native"],
)
check(
    "native bridge dependency is verified",
    "libmarbleng.so -> libhev-socks5-tunnel.so" in files["native"],
)

# DNS, routing and identity.
check(
    "endpoint bootstrap is encrypted local DoH",
    "https+local://${dnsHostLiteral(ip)}/dns-query" in files["hardener"]
    and "https+local://${dnsHostLiteral(resolver)}/dns-query" in files["hardener"],
)
check("ordinary Xray DNS has no plaintext tcp53 fallback", '"tcp://$ip:53"' not in files["hardener"])
check("Xray encrypted DNS fallback is bounded and serial", 'put("enableParallelQuery", false)' in files["hardener"])
check(
    "adaptive resolver test sends a real DNS wire query",
    'application/dns-message' in files["intel"]
    and '"POST"' in files["intel"]
    and "dnsQuery" in files["intel"],
)
check(
    "built-in DNS is routed through selected proxy",
    '.put("inboundTag", JSONArray().put("xgc-dns"))' in files["hardener"]
    and '.put("outboundTag", firstTag)' in files["hardener"],
)
check(
    "Identity Guard prevents arbitrary route rotation",
    "continuousOptimizerEnabled = false" in files["identity"],
)

# Marble engine relationships: assert math/ordering, not stale historical exact values.
normal = integer_constant(files["vpn"], "ROUTE_PROBE_INTERVAL_TICKS")
degraded = integer_constant(files["vpn"], "ROUTE_DEGRADED_PROBE_TICKS")
heavy = integer_constant(files["vpn"], "ROUTE_HEAVY_PROBE_TICKS")
rtt_window = integer_constant(files["vpn"], "ROUTE_WINDOW_SIZE")
burst = integer_constant(files["vpn"], "LIVE_RTT_BURST_SAMPLES")
failures = integer_constant(files["vpn"], "PROBE_FAILURES_BEFORE_RECOVERY")

check("degraded probe cadence is faster", 1 <= degraded < normal)
check("heavy-traffic probing is slower", heavy > normal)
check(
    "route outcome window records misses",
    "routeOutcomeWindow" in files["vpn"] and "else -1" in files["vpn"],
)
check(
    "live RTT rotates provider-diverse literal targets",
    "JITTER_PROBE_TARGETS" in files["vpn"]
    and "1.1.1.1" in files["vpn"]
    and "8.8.8.8" in files["vpn"]
    and "9.9.9.9" in files["vpn"],
)
check(
    "live RTT never publishes SOCKS CONNECT setup as ping",
    "socks-connect-estimate" not in files["vpn"]
    and "SocksHttpClient.connectLatency(" not in files["vpn"],
)
check(
    "live RTT has certificate-verified domain fallback",
    "LIVE_DOMAIN_RTT_TARGETS" in files["vpn"]
    and "SocksHttpClient.tunnelRttBatch(" in files["vpn"],
)
check(
    "Iran auto-fragment is transport-aware",
    "MARBLE_TRANSPORT_AWARE_FRAGMENT_V50" in files["shield"]
    and '"1-5"' not in files["shield"],
)
check(
    "Home hides verbose live RTT evidence",
    "liveRouteProbeStatus" in files["repo"]
    and "repo.liveRouteProbeStatus" not in files["ui"],
)
check(
    "upload-only HEV stalls need route confirmation",
    "confirmRouteUnavailable" in files["vpn"]
    and "datapath-stall-suspected" in files["vpn"]
    and "datapath-stalled-confirmed" in files["vpn"],
)
check(
    "quality uses success and tail evidence",
    "successPercent" in files["repo"]
    and "tailLatencyMs" in files["repo"],
)
check("RTT burst fits rolling window", 2 <= burst <= rtt_window)
check("route recovery needs repeated failure evidence", failures >= 3)
check(
    "all VPN executors are shut down",
    all(
        value in files["vpn"]
        for value in (
            "timerWorker.shutdownNow()",
            "monitorWorker.shutdownNow()",
            "controlWorker.shutdownNow()",
            "connectionWorker.shutdownNow()",
        )
    ),
)

# Intelligence / Turbo / protocol bridges.
check("benchmark feeds persistent intelligence", "recordBenchmark" in files["bench"])
check("Turbo uses Xray callback live port", "{ livePort ->" in files["tuner"])
check("optimizer has switch cooldown", "optimizerSwitchCooldownSec" in files["optimizer"])
check("intelligence exposes health snapshot", "healthSnapshot" in files["intel"])
check(
    "intelligence learns jitter with schema migration",
    "jitter_ewma" in files["intel"] and "oldVersion < 2" in files["intel"],
)
check(
    "intelligence applies conservative confidence",
    "val wilson" in files["intel"] and "effectiveFailureStreak" in files["intel"],
)
check(
    "SSH is a real bridge, not fake Xray protocol",
    "ManualProtocol.SSH" in files["manual"]
    and "class SshTransportManager" in files["ssh"],
)
check("SOCKS HTTPS verifies endpoint identity", "endpointIdentificationAlgorithm" in files["socks"])
check("UDP probe validates STUN transaction", "STUN transaction mismatch" in files["udp"])

# Diagnostics.
check("Bug Finder classifies resolver health", "MARBLE_RESOLVER_HEALTH_V38" in files["bug"])
check(
    "Bug Finder detects missing live quality evidence",
    "MARBLE_LIVE_METRIC_OBSERVABILITY_V47" in files["bug"],
)
check(
    "Bug Finder distinguishes verified RTT from legacy estimates",
    "MARBLE_VERIFIED_EVIDENCE_CLASSIFICATION_V50" in files["bug"],
)
check(
    "privacy audit compares proxy and Android underlay",
    "underlayIp" in files["privacy"]
    and "network.openConnection" in files["privacy"],
)
check(
    "privacy audit reports separate IP and DNS scores",
    "ipLeakScore" in files["privacy"]
    and "dnsLeakScore" in files["privacy"],
)
check("diagnostics queue is bounded", "ArrayBlockingQueue" in files["diag"])
check("diagnostics redaction exists", "fun redact" in files["diag"])
check(
    "Bug Finder raw evidence stays out of Settings",
    "current.evidence.joinToString" not in files["ui"]
    # The checks toggle must exist; anchored on its state (stable across copy edits)
    # instead of a display string.
    and "checksExpanded" in files["ui"],
)
check(
    "Bug Finder reports passive and external leak scores separately",
    "Passive leak containment" in files["bug"]
    and "External anti-leak audit" in files["bug"]
    and "ipLeakScore" in files["bug"],
)

# UI / Home.
check("Home exact reconnect path exists", "repo.reconnectLastOrAuto(onConnect)" in files["ui"])
check("Library exact active-row check exists", "repo.isActiveProfile(profile)" in files["ui"])
check(
    "Settings tabs render the selected workspace",
    "SettingsTabPane(" in files["ui"]
    and "SettingsWorkspacePage(" in files["ui"]
    and "SettingsSectionCard(" in files["ui"],
)
check(
    "Settings workspace has explicit remaining-height viewport",
    "MARBLE_SETTINGS_CONTENT_VIEWPORT_HARDENING_V72" in files["ui"]
    and "MARBLE_SETTINGS_TOTAL_HOTFIX_V76" in files["ui"]
    and "modifier = Modifier.fillMaxSize()" in files["ui"],
)
check(
    "Settings mobile workspace uses a direct sticky LazyColumn",
    'stickyHeader(key = "settings-tabs-strip")' in files["ui"]
    and "SettingsTabStrip(" in files["ui"]
    and "remember(activeIndex) { LazyListState() }" in files["ui"],
)
check(
    "Settings tab strip host is height-bounded",
    ".height(58.dp)" in files["ui"]
    and ".matchParentSize()" in files["ui"],
)
check(
    "Settings workspace has no SubcomposeLayout content boundary",
    "BoxWithConstraints(" not in files["ui"],
)
check(
    "Settings workspace applies exactly one inset pass",
    "imePadding()" in files["ui"]
    and "windowInsetsPadding(WindowInsets.navigationBars)" not in files["ui"]
    and "systemBarsPadding()" not in files["ui"],
)
check(
    "Settings viewport tripwire and shared section source exist",
    "viewport-degraded-fallback" in files["ui"]
    and "SettingsSectionSpec" in files["ui"]
    and "ExpertGateRow(" in files["ui"],
)
check(
    "Routing focus never mutates Expert mode",
    "selectedTabIndex = SettingsWorkspaceTab.NETWORK.ordinal" in files["ui"]
    and "copy(expertMode=true)" not in files["ui"],
)
check("Library long names use overflow marquee", "basicMarquee(" in files["ui"])
check(
    "legacy global chain settings are removed",
    "chainEnabled" not in files["models"] + files["store"] + files["ui"],
)
check("DNS settings keep their Compose boundary", "@Composable\nprivate fun DnsSettings(" in files["ui"])
check(
    "Manual Library supports unbounded saved chains",
    "fun composeChain(sources: List<String>)" in files["hardener"]
    and "addManualChain" in files["repo"]
    and "ManualChainEditor" in files["ui"],
)
check(
    "Quick Tile reconnects exact last profile",
    "lastProfile()" in files["tile"]
    and "ACTION_CONNECT_LAST" in files["tile"]
    and "lastProfileSourceId" in files["store"],
)
check(
    "Quick Tile service is permission protected",
    "android.permission.BIND_QUICK_SETTINGS_TILE" in files["manifest"]
    and ".quicktile.MarbleQuickTileService" in files["manifest"],
)

# CI/release.
check("signed build checks out complete history", "fetch-depth: 0" in files["build"])
check("verify invokes central integrity audit", "scripts/system-integrity-check.py" in files["verify"])
check("signed build invokes central integrity audit", "scripts/system-integrity-check.py" in files["build"])
check(
    "native release assets bypass rate-limited metadata API",
    "api.github.com/repos/XTLS/Xray-core/releases/tags" not in files["native"]
    and "releases/download/${XRAY_TAG}/${XRAY_ASSET_NAME}" in files["native"],
)
check(
    "Xray Go cache follows the pinned dependency checksum",
    "cache-dependency-path: .bootstrap/xray/go.sum" in files["build"],
)
check(
    "workflow JavaScript actions use Node 24 generations",
    action_uses_minimum(workflow_sources, "actions/checkout", 7)
    and action_uses_minimum(workflow_sources, "actions/setup-go", 7)
    and action_uses_minimum(workflow_sources, "android-actions/setup-android", 4),
)

build_release = files["build"]

check(
    "release publishing is immutable, verified and tag-atomic",
    "draft:true" in build_release
    and "target_commitish:$target" in build_release
    and "release_upload_url" in build_release
    and "https://uploads.github.com/" in build_release
    and "for attempt in 1 2 3 4 5 6; do" in build_release
    and "repos/$repo/releases/assets/$existing_id" in build_release
    and "Remote asset count mismatch." in build_release
    and "Remote release verification failed: $name" in build_release
    and "cleanup_failed_release" in build_release
    and 'git rev-parse -q --verify "refs/tags/$tag"' in build_release
    and 'gh release view "$tag"' in build_release
    and 'gh release upload "$tag"' not in build_release
    and 'git push origin "$tag"' not in build_release,
)

check(
    "successful build never auto-publishes a draft release",
    'gh release edit "$tag" --draft=false' not in build_release
    and "gh release edit $tag --draft=false" not in build_release
    and 'gh release edit "$tag" -p' not in build_release
    and "gh release edit $tag -p" not in build_release
    and "draft:false" not in build_release,
)

# Global concurrency smells.
production = "\n".join(
    value for key, value in files.items()
    if key not in {"build", "verify", "native"}
)
check("no GlobalScope in production", "GlobalScope" not in production)

failed = [name for name, passed in checks if not passed]

print("=== MarbleNG SYSTEM INTEGRITY ===")
for name, passed in checks:
    print(f"[{'PASS' if passed else 'FAIL'}] {name}")

print(
    f"\nchecks={len(checks)} "
    f"pass={len(checks) - len(failed)} "
    f"fail={len(failed)}"
)

if failed:
    print("\nFailed invariants:")
    for name in failed:
        print(f" - {name}")
    raise SystemExit(1)

print("Source-wide architecture invariants are internally consistent.")
