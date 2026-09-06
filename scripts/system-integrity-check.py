#!/usr/bin/env python3
# MarbleNG source-wide architecture/integration preflight.
# Compatible with MARBLE_RELEASE_PUBLISH_RESILIENT_V182.
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
    # MARBLE_SINGBOX_CORE_V151 — the second engine: the switch, the config writer and the
    # process owner. They are required files now: a build that drops one of them silently
    # turns Settings → Tunnel core into a page that cannot start anything.
    "coreEngine": read("app/src/main/java/com/marbleng/app/core/CoreEngine.kt"),
    "singBoxBuilder": read("app/src/main/java/com/marbleng/app/core/SingBoxConfigBuilder.kt"),
    "singBox": read("app/src/main/java/com/marbleng/app/core/SingBoxManager.kt"),
    "coreLock": read("core-lock.json"),
    "hardener": read("app/src/main/java/com/marbleng/app/core/XrayConfigHardener.kt"),
    "bench": read("app/src/main/java/com/marbleng/app/core/BenchmarkEngine.kt"),
    "tuner": read("app/src/main/java/com/marbleng/app/core/ConnectionTuner.kt"),
    "bufferPolicy": read("app/src/main/java/com/marbleng/app/core/LatencyBufferPolicy.kt"),
    "bufferPolicyTest": read("app/src/test/java/com/marbleng/app/core/LatencyBufferPolicyTest.kt"),
    "mtuPolicy": read("app/src/main/java/com/marbleng/app/core/AdaptiveMtuPolicy.kt"),
    "networkPolicyTest": read("app/src/test/java/com/marbleng/app/core/NetworkPolicyTest.kt"),
    "singBoxTest": read("app/src/test/java/com/marbleng/app/core/SingBoxCoreV151Test.kt"),
    "probeMethodTest": read("app/src/test/java/com/marbleng/app/model/ProbeMethodV151Test.kt"),
    "optimizer": read("app/src/main/java/com/marbleng/app/core/ContinuousRouteOptimizer.kt"),
    "identity": read("app/src/main/java/com/marbleng/app/core/IdentityGuard.kt"),
    "shield": read("app/src/main/java/com/marbleng/app/core/IranShield.kt"),
    "intel": read("app/src/main/java/com/marbleng/app/core/MarbleIntelligence.kt"),
    "manual": read("app/src/main/java/com/marbleng/app/core/ManualConfigBuilder.kt"),
    "parser": read("app/src/main/java/com/marbleng/app/core/ProxyParser.kt"),
    "tlsPinning": read("app/src/main/java/com/marbleng/app/core/TlsPinningPolicy.kt"),
    "reachability": read("app/src/main/java/com/marbleng/app/core/MultiVectorReachability.kt"),
    "probe": read("app/src/main/java/com/marbleng/app/core/RouteProbe.kt"),
    "pinningTest": read("app/src/test/java/com/marbleng/app/core/TlsPinningPolicyTest.kt"),
    "pingTruthTest": read("app/src/test/java/com/marbleng/app/core/PingMethodTruthV149Test.kt"),
    "ssh": read("app/src/main/java/com/marbleng/app/core/SshTransportManager.kt"),
    "socks": read("app/src/main/java/com/marbleng/app/core/SocksHttpClient.kt"),
    "resolverPolicy": read("app/src/main/java/com/marbleng/app/core/ResolverEvidencePolicy.kt"),
    "deadlinePolicy": read("app/src/main/java/com/marbleng/app/core/LinkDeadlinePolicy.kt"),
    "backoffPolicy": read("app/src/main/java/com/marbleng/app/core/TurboBackoffPolicy.kt"),
    "dpiFetch": read("app/src/main/java/com/marbleng/app/core/DpiAwareFetcher.kt"),
    "dpiPolicy": read("app/src/main/java/com/marbleng/app/core/DpiEvasionPolicy.kt"),
    "udp": read("app/src/main/java/com/marbleng/app/core/SocksUdpProbe.kt"),
    "privacy": read("app/src/main/java/com/marbleng/app/net/PrivacyAuditor.kt"),
    "bug": read("app/src/main/java/com/marbleng/app/core/BugFinder.kt"),
    "diag": read("app/src/main/java/com/marbleng/app/core/RuntimeDiagnostics.kt"),
    "ui": read("app/src/main/java/com/marbleng/app/ui/Aether2026.kt"),
    "homeStyles": read("app/src/main/java/com/marbleng/app/ui/MarbleHomeStyles.kt"),
    "strings": read("app/src/main/java/com/marbleng/app/ui/MarbleStrings.kt"),
    "marbleApp": read("app/src/main/java/com/marbleng/app/ui/MarbleApp.kt"),
    "homeStudio": read("app/src/main/java/com/marbleng/app/ui/MarbleHomeStudio.kt"),
    "connectPlacement": read("app/src/main/java/com/marbleng/app/ui/MarbleConnectPlacement.kt"),
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
# MARBLE_SERVERS_LANGUAGE_V114 — "Library nodes" is "servers" everywhere the user can read it,
# including this guard message; the invariant is the guard, not the old wording.
check("server deletion is disconnected-only", "Disconnect before deleting servers" in files["repo"])
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

# MARBLE_HOME_STYLE_TRIM_V121 — three presentations of one connection surface.
# Parametric and Bioluminescent were removed from the product: they are not modelled, not
# implemented, not translated and not reachable anywhere.
home_styles = ("IOS_SLIDER", "IOS_FLOATING", "IOS_EMBOSSED", "IOS_MODULAR")
retired_home_styles = ("BIOLUMINESCENT", "PARAMETRIC")
check(
    "all four Home styles are modelled and persisted",
    "enum class HomeStyle" in files["models"]
    and all(style in files["models"] for style in home_styles)
    and "homeStyle" in files["store"]
    and "homeStyle = style.id" in files["ui"],
)
check(
    "retired Home styles are gone from every layer",
    all(
        style not in blob
        for style in retired_home_styles
        for blob in (files["models"], files["homeStyles"], files["ui"])
    ),
)
check(
    "every Home style has an implementation and is reachable",
    all(
        name in files["homeStyles"]
        for name in (
            "HomeThemeSlider",
            "HomeThemeFloating",
            "HomeThemeEmbossed",
            "HomeThemeModular",
        )
    )
    and "HomeStyleSurface(" in files["homeStyles"]
    and "HomeStyleSurface(" in files["ui"],
)
check(
    "every Home style renders the same evidence through one shared model",
    "data class HomeEvidence" in files["homeStyles"]
    and "buildHomeEvidence(" in files["ui"]
    and "IosStatusWideCard(" in files["homeStyles"]
    and "IosServerListBox(" in files["homeStyles"],
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

# iOS Slider Home presentation is modelled and is the product default
check(
    "iOS Slider Home style is modelled and is the product default",
    'IOS_SLIDER("ios_slider")' in files["models"]
    and "homeStyle: String = HomeStyle.IOS_SLIDER.id" in files["models"]
    and "HomeThemeSlider(" in files["homeStyles"]
    and "HomeStyleSurface(" in files["ui"]
    and "HomeStyleSurface(" in files["homeStyles"],
)
# MARBLE_SIGNATURE_STUDIO_REMOVED_V143 — the Signature studio product surface is gone from every
# layer: no UI file, no model type, no persisted setting, no composer wiring, no translation.
check(
    "Signature studio surface and settings are fully removed",
    "SignatureFloatingConnectOverlay(" not in files["ui"]
    and "SignatureStatusBanner(" not in files["ui"]
    and "SignatureCornerCluster(" not in files["ui"]
    and "HomeProContext" not in files["homeStyles"]
    and "rememberSignatureProContext(" not in files["ui"]
    and "ProAccent" not in files["models"]
    and "ProBannerScope" not in files["models"]
    and "ProShortcut" not in files["models"]
    and "proFloatingButtonEnabled" not in files["store"]
    and "proStatusBannerEnabled" not in files["store"]
    and "proCornerActionsEnabled" not in files["store"]
    and "proBannerScope" not in files["store"]
    and "proAccent" not in files["store"]
    and "proShortcut" not in files["store"],
)
# Home carries no legacy Signature swppers or server-card settings; the presentation is chosen
# in Settings and server selection stays shared between Home and the Servers page.
check(
    "Home carries no style switcher or server-card setting",
    "SignatureStyleSwitcher" not in files["ui"]
    and "ProServerCardStyle" not in files["models"]
    and "proServerRailEnabled" not in files["store"]
    and "proStyleSwitcherEnabled" not in files["store"],
)
check(
    "Home server list is synced with the Servers group, not a second rail",
    "IosServerListBox(" in files["homeStyles"]
    and "ServersQuery.visible(" in files["homeStyles"]
    and "librarySourceFilter" in files["homeStyles"]
    and "selectProfile(" in files["homeStyles"]
    and "SignatureServerRail" not in files["homeStyles"],
)
check(
    "selected-server endpoint ping is one-shot with failure kinds",
    "fun measureSelectedPing()" in files["repo"]
    and "selectedPingInFlight" in files["repo"]
    and "fun measureHomePing()" in files["repo"]
    and "classifyPingFailure(" in files["repo"],
)
check(
    "floating connection control is a bottom-end circular shutter",
    "fun ConnectButtonFloating(" in files["homeStudio"]
    and ".size(76.dp)" in files["homeStudio"]
    and "bottom-end" in files["connectPlacement"],
)
check(
    "dragged Signature floating-button position state is gone",
    "rememberProFabPosition" not in files["repo"]
    and "setProFabPosition" not in files["store"]
    and "proFabPosition" not in files["repo"],
)
check(
    "Home evidence is shared by deck, banner and floating button",
    "rememberDeckEvidence(" in files["ui"]
    and "deck = deck" in files["ui"],
)

# MARBLE_HOME_PING_RESCUE_V112 — the Home ping is a multi-mode ladder, not three 204 domains.
check(
    "connected Home ping runs the configured probe method through the live SOCKS port",
    "RouteProbe.measureUnified(" in files["repo"]
    and "settings.probeMethod" in files["repo"]
    and "home-connection-ping" in files["repo"]
    and "SocksHttpClient.get(" not in files["repo"],
)
# MARBLE_PING_USER_TAPPED_ONLY_V143 — ping is a one-shot user action. The bounded ladder still
# lives in the repository; the UI never arms a background probe.
check(
    "cold-tunnel ping miss gets exactly one bounded re-check",
    "MARBLE_HOME_PING_RESCUE_V112" in files["repo"]
    and "MARBLE_PING_USER_TAPPED_ONLY_V143" in files["ui"],
)

# MARBLE_SEAMLESS_LOOPS_V112 — loop restarts must be invisible.
check(
    "loop effects fade through a zero-at-both-ends envelope",
    "loopFade(" in files["homeStyles"]
    and "MARBLE_SEAMLESS_LOOPS_V112" in files["homeStyles"],
)

# MARBLE_HOME_PING_AUTOFIT_V112 — the ping can never overflow its readout.
check(
    "every stat readout renders through the auto-fit value text",
    "HomeStatValueText(" in files["homeStyles"]
    and "softWrap = false" in files["homeStyles"],
)

# MARBLE_SYSTEM_FONT_V112 — the device typeface is a first-class choice; Persian stays Vazir.
check(
    "system font option exists and Persian still forces Vazirmatn",
    "SYSTEM(\"system\")" in files["models"]
    and "AppFont.SYSTEM -> FontFamily.Default" in files["theme"]
    and "if (persian) VazirFamily else selectedFontFamily(fontId)" in files["theme"],
)

# MARBLE_NIGHT_OUTLINES_V112 — dark-theme frame personality is a user choice.
check(
    "night outline styles persist and reach the theme palette",
    "enum class DarkOutlineStyle" in files["models"]
    and "darkOutlineStyle" in files["store"]
    and "outlineStyleId = repo.settings.darkOutlineStyle" in files["marbleApp"]
    and "applyNightOutline(" in files["theme"]
    and "DarkOutlineStyle.HIDDEN ->" in files["theme"],
)

# MARBLE_BILINGUAL_V110 â€” English/Persian with a device-locale default.
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
# MARBLE_RESOLVER_EVIDENCE_V134 — serial failover is the default, not an absolute. Racing every
# encrypted resolver costs fan-out, so it may only be armed by attributed evidence that an endpoint
# in the emitted list is decisively failing. The default-off half of this invariant is pinned by
# DnsDeadlineConfigTest, which hardens with plain AppSettings and asserts a serial resolver graph.
check(
    "Xray encrypted DNS races only on attributed resolver-failure evidence",
    'put("enableParallelQuery", dnsParallelQuery)' in files["hardener"]
    and "val dnsParallelQuery = settings.adaptiveDnsEnabled &&" in files["hardener"]
    and "settings.measuredDnsParallel" in files["hardener"]
    and "fun parallelQueryJustified(" in files["resolverPolicy"]
    and "parallelQueryJustified(" in files["intel"],
)
check(
    "resolver failures are attributed to the endpoint the core named",
    "fun endpointOf(line: String): String?" in files["resolverPolicy"]
    and "ResolverFailureClassifier.isDnsRelated(line)" in files["resolverPolicy"],
)
check(
    "resolver demotion decays, is time-bounded, never deletes a resolver and rotates healthy first",
    # MARBLE_IRAN_AWARE_PING_RESOLVERS — order() now rotates the healthy part of the pool per
    # 10-minute epoch (anti-DPI discipline) while demoted endpoints still always stay last and a
    # list where every candidate is failing still returns unchanged.
    "DECAY_HALF_LIFE_MS" in files["resolverPolicy"]
    and "DEMOTE_TTL_MS" in files["resolverPolicy"]
    and "fun order(" in files["resolverPolicy"]
    and "if (healthy.isEmpty()) return distinct" in files["resolverPolicy"]
    and "rotated + failing" in files["resolverPolicy"],
)
check(
    "the emitted resolver list is ordered by attributed runtime evidence",
    "measuredDnsDemotedEndpoints" in files["models"]
    and "measuredDnsDemotedEndpoints" in files["hardener"]
    and "demoteLast(" in files["hardener"]
    and "recordResolverEvidence" in files["intel"],
)
check(
    "resolver health is reported as a rate over the session window, not a raw total",
    "ResolverEvidencePolicy.window(" in files["bug"]
    and "perMinute" in files["bug"],
)
check(
    "throwaway measurement cores inherit the measured link deadlines",
    files["xray"].count("link: LinkEvidence") >= 2
    and "benchmarkSettings, link" in files["xray"],
)
check(
    "acceleration trials budget themselves from the measured link",
    "LinkDeadlinePolicy.tuningTrialTimeoutMs" in files["tuner"]
    and "LinkDeadlinePolicy.tuningPassBudgetMs" in files["tuner"],
)
check(
    "an acceleration pass that never ran cannot escalate the transport backoff",
    "NOT_ATTEMPTED" in files["backoffPolicy"]
    and "TurboBackoffPolicy.Cause.NOT_ATTEMPTED" in files["vpn"],
)
check(
    "link evidence merges conservatively instead of guessing",
    "fun conservativeOf(other: LinkEvidence): LinkEvidence" in files["deadlinePolicy"]
    and "fun linkEvidenceFor(" in files["intel"],
)
check(
    "the resident diagnostics ring is bounded by size, not only by count",
    "RING_MAX_CHARS" in files["diag"]
    and "ringChars" in files["diag"]
    and "while (ring.size > RING_CAPACITY || ringChars > RING_MAX_CHARS)" in files["diag"],
)
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
    "Settings hub renders one dedicated page per title",
    "SettingsTabPage(" in files["ui"]
    and "settingsSections(" in files["ui"]
    and "SettingsSectionCard(" in files["ui"],
)
check(
    "Settings single-page migration marker exists",
    "MARBLE_SETTINGS_FLAT_SINGLE_PAGE_V115" in files["ui"]
    and "SettingsSubPage(" in files["ui"],
)
check(
    "Settings tab strip and adaptive rail are gone",
    "NavigationRail(" not in files["ui"]
    and "SettingsTabStrip(" not in files["ui"]
    and "compactTabsState" not in files["ui"],
)
check(
    "Per-app proxy lives inside Network & Routing",
    'card("Per-app proxy"' in files["ui"]
    and "SplitTunnelSettings(repo)" in files["ui"]
    and "SplitTunnelModeSelector(" in files["ui"],
)
check(
    "Settings workspace has no SubcomposeLayout content boundary",
    "BoxWithConstraints(" not in files["ui"],
)
check(
    "Settings sub-pages apply exactly one inset pass",
    "imePadding()" in files["ui"]
    and "windowInsetsPadding(WindowInsets.navigationBars)" not in files["ui"]
    and "systemBarsPadding()" not in files["ui"],
)
check(
    "Shared section source and expert gate still exist",
    "SettingsSectionSpec" in files["ui"]
    and "settingsSections(" in files["ui"]
    and "ExpertGateRow(" in files["ui"],
)
# MARBLE_ROUTING_SEPARATE_V143 — Routing is its own dedicated Settings page and the Home->Routing
# deep link jumps straight to it without mutating Expert mode.
check(
    "Routing focus keeps Expert mode untouched",
    'focusSection == "Routing"' in files["ui"]
    and "page = SettingsPages.ROUTING" in files["ui"]
    and "RoutingEntryCard(" in files["ui"]
    and "SettingsRoutingPage(" in files["ui"]
    and "copy(expertMode=true)" not in files["ui"],
)
check("Library long names use overflow marquee", "basicMarquee(" in files["ui"])
check(
    "legacy global chain settings are removed",
    "chainEnabled" not in files["models"] + files["store"] + files["ui"],
)
# MARBLE_PROBE_METHODS_V151 — the product exposes exactly three measurements: Real delay (a real
# page through the tunnel, PattNG's real ping), TCP ping (the port answers, PattNG's tcping) and
# URL test (the delay the running sing-box extended core measured for its own outbound).
#
# The five retired methods are checked for by *name*, in the enum body only: their names still
# appear in Models.kt prose, because the reason each one was removed is part of the record.
probe_method_enum = files["models"].split("enum class ProbeMethod {", 1)[1].split("\n}", 1)[0]
check(
    "product ping methods are exactly the V151 three",
    "enum class ProbeMethod {" in files["models"]
    and "REAL_DELAY" in probe_method_enum
    and "TCP_PING" in probe_method_enum
    and "URL_TEST" in probe_method_enum
    and not any(
        retired in probe_method_enum
        for retired in ("HYBRID", "TUNNEL", "TCP_CONNECT", "TCP_RECOMMENDED", "HTTP_GET", "HTTP_HEAD", "ICMP", "DNS")
    ),
)
check(
    "settings page offers every product ping method and none of the retired ones",
    "ProbeMethod.REAL_DELAY ->" in files["ui"]
    and "ProbeMethod.TCP_PING ->" in files["ui"]
    and "ProbeMethod.URL_TEST ->" in files["ui"]
    and "ProbeMethod.entries.forEach" in files["ui"]
    and "ProbeMethod.DNS ->" not in files["ui"]
    and "ProbeMethod.ICMP ->" not in files["ui"]
    and "ProbeMethod.HYBRID ->" not in files["ui"]
    and "ProbeMethod.TCP_CONNECT ->" not in files["ui"],
)
check(
    "no production code still selects a retired ping method",
    not any(
        "ProbeMethod." + retired in files[key]
        for retired in ("HYBRID", "TUNNEL", "TCP_CONNECT", "TCP_RECOMMENDED", "HTTP_GET", "HTTP_HEAD", "ICMP", "DNS")
        for key in ("repo", "bench", "probe", "ui", "vpn", "store", "models")
    ),
)
check(
    "the URL test is served by the sing-box core, not a re-implementation",
    "urlTestHook" in files["probe"]
    and "urlTestHook =" in files["repo"]
    and "urlTestLive(" in files["singBox"]
    and "urlTestProfile(" in files["singBox"]
    and "METHOD_URL_TEST" in files["probe"],
)
# A legacy stored method must never crash the settings screen or measure something the product no
# longer offers: every retired name maps onto the honest replacement.
check(
    "stored legacy ping methods migrate onto a real one",
    '"HYBRID", "TCP_RECOMMENDED", "HTTP_GET", "HTTP_HEAD", "ICMP" -> ProbeMethod.REAL_DELAY' in files["store"]
    and '"TUNNEL" -> ProbeMethod.REAL_DELAY' in files["store"]
    and '"TCP_CONNECT" -> ProbeMethod.TCP_PING' in files["store"],
)

# MARBLE_SINGBOX_CORE_V151 — the second engine is a product surface, so its four moving parts have
# to agree: the pin, the packaging, the switch, and the page the user reads it on.
check(
    "sing-box extended is pinned in core-lock.json",
    '"singbox"' in files["coreLock"] and '"tag"' in files["coreLock"],
)
check(
    "the native build installs the sing-box binary",
    "libsingbox.so" in files["native"] and "sing-box" in files["native"],
)
check(
    "the engine switch is one setting and one decision point",
    "coreEngineId" in files["models"]
    and "coreEngineId" in files["store"]
    and "fun setCoreEngine(" in files["repo"]
    and "settings.coreEngine()" in files["vpn"]
    and "singBox.start(" in files["vpn"]
    and "xray.start(" in files["vpn"],
)
check(
    "the engine page shows all three pinned versions",
    "BuildConfig.SINGBOX_CORE_TAG" in files["ui"]
    and "BuildConfig.XRAY_CORE_TAG" in files["ui"]
    and "BuildConfig.HEV_CORE_TAG" in files["ui"]
    and "SINGBOX_CORE_TAG" in files["gradle"],
)
# sing-box 1.12 renamed the DNS server address key. `address` parses to nothing, so a config that
# still uses it fails every lookup at run time — long after any compiler has approved it.
check(
    "sing-box DNS servers use the 1.12 server key",
    '.put("server", host)' in files["singBoxBuilder"]
    and '.put("address"' not in files["singBoxBuilder"],
)
# Xray's certificate-pinning keys are read here to *report* the limitation, never written into a
# sing-box config: the fork has no equivalent, and a key it does not know is a config it refuses.
check(
    "the sing-box config never invents an Xray-only schema",
    '.put("pinnedPeerCertSha256"' not in files["singBoxBuilder"]
    and '.put("verifyPeerCertByName"' not in files["singBoxBuilder"]
    and 'pinnedPeerCertSha256' in files["singBoxBuilder"]
    and '"cache_file"' in files["singBoxBuilder"]
    and '"clash_api"' in files["singBoxBuilder"]
    and '"unified_delay"' in files["singBoxBuilder"],
)
# The engine-agnostic reads in the VPN service must name the Xray manager in their else arm; a
# getter that reads itself compiles cleanly and then dies on the stack at connect time.
check(
    "engine-agnostic core reads never recurse",
    "else xray.isAlive" in files["vpn"]
    and "else xray.lastStartPhase" in files["vpn"]
    and "else xray.lastStartError" in files["vpn"]
    and "runCatching { xray.stop() }" in files["vpn"],
)
# Xray's transport telemetry comes from MarbleNG's own Xray patch; reading it on the sing-box
# engine would report another engine's numbers as this one's.
check(
    "Xray transport telemetry is read only on the Xray engine",
    "activeEngine == CoreEngine.XRAY" in files["vpn"],
)

# MARBLE_XRAY_THROUGHPUT_V151 — the two arithmetic bugs behind "connected on Xray but slow".
# Both are operator-precedence / unit errors that compile cleanly and only show up as a slow link.
check(
    "packet loss alone no longer clamps MTU to the minimum",
    "input.tcpStressed && (input.retransmitRate > 0.15 || input.lossRate > 0.15)" in files["mtuPolicy"]
    and "input.tcpStressed && input.retransmitRate > 0.15 || input.lossRate > 0.15" not in files["mtuPolicy"]
    and "packetLossAloneDoesNotClampTheMtu" in files["networkPolicyTest"],
)
check(
    "the recommended MSS is sized for the address family in use",
    "if (input.hasIpv6) 60 else 40" in files["mtuPolicy"]
    and "val overhead = 60" not in files["mtuPolicy"]
    and "recommendedMssFollowsTheAddressFamily" in files["networkPolicyTest"],
)
check(
    "the latency-first queue is sized to the link, not to a constant",
    "LatencyBufferPolicy.tcpBytes(" in files["intel"]
    and "fun tcpBytes(downstreamKbps: Int, rttMs: Double, tunedBytes: Int)" in files["bufferPolicy"]
    and "BASELINE_TCP_BYTES" in files["bufferPolicy"]
    and "aLongFatTunnelGetsEnoughQueueToFillThePipe" in files["bufferPolicyTest"],
)
check(
    "the V151 probe set and the sing-box schema are pinned by unit tests",
    "productMethodsAreExactlyThree" in files["probeMethodTest"]
    and "dnsServersUseTheSchemaSingBoxActuallyReads" in files["singBoxTest"]
    and "theThreeEngineSwitchesReachTheConfig" in files["singBoxTest"],
)

# MARBLE_HOME_IP_STRIP_V151 — the "Show complete IP information" caption is gone from Home. The
# words survive as the glyph's content description, so the strip is still readable out loud.
check(
    "Home no longer prints the IP-details caption",
    "t.ipDetails" not in files["homeStyles"].replace(
        "contentDescription = t.ipDetails", ""
    )
    and "contentDescription = t.ipDetails" in files["homeStyles"],
)
# MARBLE_MODULAR_CUSTOMIZER_V151 — Home style 4 offers Home style 2's floating control, and its
# Customize affordance can be hidden with the way back kept in Settings.
check(
    "the modular layout offers the floating control and can hide its customizer",
    "HomeFloatingSplitControl(" in files["homeStyles"]
    and files["homeStyles"].count("HomeFloatingSplitControl(") >= 3
    and "modularHideCustomizerButton" in files["models"]
    and "modularHideCustomizerButton" in files["store"]
    and "modularHideCustomizerButton" in files["homeStyles"]
    and "modularHideCustomizerButton" in files["ui"],
)
check(
    "Home no longer composes a top-of-page ping overlay",
    "HomePingInlinePanel" not in files["ui"]
    and "showPingInline" not in files["ui"],
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
    "release publishing is immutable, verified and collision-resilient",
    "draft:true" in build_release
    and "target_commitish:$target" in build_release
    and "release_upload_url" in build_release
    and "https://uploads.github.com/" in build_release
    and "for attempt in 1 2 3 4 5 6; do" in build_release
    and "repos/$repo/releases/assets/$existing_id" in build_release
    and "Remote asset count mismatch." in build_release
    and "Remote release verification failed: $name" in build_release
    and "cleanup_failed_release" in build_release
    and "version_taken()" in build_release
    and "git fetch --tags --force origin" in build_release
    and "repos/$repo/git/refs/tags/v$v" in build_release
    and 'gh release view "v$v"' in build_release
    and "SMART COLLISION-RESOLUTION ALGORITHM" in build_release
    and "Could not find a free version after 500 attempts" in build_release
    and "SELF-HEALING GUARD" in build_release
    and "Release $tag is already published; refusing to touch it." in build_release
    and 'gh release upload "$tag"' not in build_release
    and 'git push origin "$tag"' not in build_release,
)

check(
    "release version discovery checks GitHub drafts and remote tags",
    "version_taken()" in build_release
    and "repos/$repo/git/refs/tags/v$v" in build_release
    and 'gh release view "v$v"' in build_release
    and "git fetch --tags --force origin" in build_release,
)

check(
    "release collision algorithm advances rather than hard-failing",
    'while version_taken "$CANDIDATE"; do' in build_release
    and 'CANDIDATE="$(bump_patch "$CANDIDATE" 1)"' in build_release
    and "ATTEMPTS > 500" in build_release,
)

check(
    "stale unpublished draft releases are self-healed safely",
    "Removing stale unpublished draft for $tag." in build_release
    and "existing_published" in build_release
    and "Release $tag is already published; refusing to touch it." in build_release,
)

check(
    "successful build never auto-publishes a draft release",
    'gh release edit "$tag" --draft=false' not in build_release
    and "gh release edit $tag --draft=false" not in build_release
    and 'gh release edit "$tag" -p' not in build_release
    and "gh release edit $tag -p" not in build_release
    and "draft:false" not in build_release,
)

# MARBLE_TLS_PINNING_V149 — the "connected but no Internet" root cause must stay fixed.
check(
    "TLS pinning policy is the single authority",
    "object TlsPinningPolicy" in files["tlsPinning"]
    and "verifyPeerCertByName" in files["tlsPinning"]
    and "pinnedPeerCertSha256" in files["tlsPinning"],
)
check(
    "share links read Xray's short pinning keys",
    '"vcn"' in files["tlsPinning"] and '"pcs"' in files["tlsPinning"],
)
check(
    "the parser writes both pinning fields through the policy",
    "TlsPinningPolicy.sanitizeTlsSettings" in files["parser"]
    and "TlsPinningPolicy.VERIFY_BY_NAME_KEYS" in files["parser"]
    and "TlsPinningPolicy.PINNED_SHA256_KEYS" in files["parser"],
)
check(
    "the manual builder writes both pinning fields through the policy",
    "TlsPinningPolicy.sanitizeTlsSettings" in files["manual"]
    and "val verifyPeerCertByName" in files["manual"]
    and "val pinnedPeerCertSha256" in files["manual"],
)
check(
    "the hardener repairs stored profiles before the core sees them",
    files["hardener"].count("TlsPinningPolicy.sanitizeConfigDocument") >= 2,
)
check(
    "the pinning editor is reachable from the UI",
    "Verify peer certificate by name" in files["ui"]
    and "Certificate fingerprint (SHA-256)" in files["ui"],
)
check(
    # Xray v26 removed allowInsecure: emitting it makes TLSConfig.Build() reject the WHOLE config.
    "allowInsecure is never emitted into an Xray config",
    'put("allowInsecure"' not in files["parser"]
    and 'put("allowInsecure"' not in files["manual"]
    and 'put("allowInsecure"' not in files["hardener"],
)
check(
    "fingerprints are emitted as hex, which is what Xray parses",
    "hex.DecodeString" in files["tlsPinning"] or "lowercase hex" in files["tlsPinning"],
)

# MARBLE_PING_TRUTH_V149 — the four product ping methods must not convict healthy servers.
check(
    # `-q` hides the per-packet `time=` lines the RTT parser depends on, so every ICMP ping
    # returned "no-responses" even at 0% packet loss.
    "ICMP never re-acquires ping's quiet flag",
    'add("-q")' not in files["probe"],
)
# The rationale comment in MultiVectorReachability deliberately NAMES the JSSE calls it removed,
# so this invariant is asserted against imports and executable statements, not prose.
_reachability_code = "\n".join(
    line for line in files["reachability"].splitlines()
    if not line.lstrip().startswith(("*", "//", "/*"))
)
check(
    "the Layer-0 gate no longer asks the device CA store about a proxy certificate",
    "javax.net.ssl" not in files["reachability"]
    and "SSLSocketFactory" not in _reachability_code
    and "startHandshake()" not in _reachability_code
    and "endpointIdentificationAlgorithm" not in _reachability_code
    and "internal fun clientHello" in files["reachability"]
    and "internal fun isTlsRecord" in files["reachability"],
)
check(
    "HTTP methods accept any complete status line as a round trip",
    "probe.status > 0" in files["probe"] and "responseCode > 0" in files["probe"],
)
check(
    "HEAD never waits for a body a HEAD response cannot have",
    'httpMethod.equals("HEAD", ignoreCase = true)' in files["probe"],
)
check(
    "ping success rates use the attempts actually made",
    "val denominator = attempts" in files["probe"],
)
check(
    "V149 regressions are pinned by unit tests",
    "TlsPinningPolicyTest" in files["pinningTest"]
    and "PingMethodTruthV149Test" in files["pingTruthTest"],
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
