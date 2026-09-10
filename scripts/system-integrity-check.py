#!/usr/bin/env python3
# MarbleNG source-wide architecture/integration preflight.
# Compatible with MARBLE_RELEASE_PUBLISH_RESILIENT_V182.
#
# Gradle/Kotlin/native compilers remain the syntax/type/link authority. This checker catches
# structural drift between subsystems before expensive native compilation starts.

from pathlib import Path
import importlib.util
import json
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

def workflow(name: str) -> str:
    """The workflow as it will run once `docs/workflows-pending/` has been installed.

    The token this branch pushes with cannot write `.github/workflows/*` — GitHub refuses a
    `workflows`-less App token — so workflow changes are staged under `docs/workflows-pending/`
    with a replace table in the README there. Invariants read the staged copy when one exists: it
    is the exact file a maintainer is about to copy into place, and asserting the live file instead
    would either fail the PR for a change that is already written or silently stop checking it.
    Every staged copy is a complete file derived from the live one, so invariants about what a
    workflow already does keep holding against it.
    """
    pending = ROOT / "docs" / "workflows-pending" / name
    if pending.is_file():
        return read(f"docs/workflows-pending/{name}")
    return read(f".github/workflows/{name}")

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
    "coreUrlTest": read("app/src/main/java/com/marbleng/app/core/CoreUrlTest.kt"),
    "cancelGate": read("app/src/main/java/com/marbleng/app/core/ProbeCancelGate.kt"),
    "rankEngine": read("app/src/main/java/com/marbleng/app/core/PattRankEngine.kt"),
    "linkAuthorityTest": read("app/src/test/java/com/marbleng/app/core/SingBoxLinkAuthorityV156Test.kt"),
    "probeTruthTest": read("app/src/test/java/com/marbleng/app/core/ProbeTruthV156Test.kt"),
    # MARBLE_PING_FALSE_FAILED_V159 — the shared target walk of the two core-measured pings and
    # the test that pins its budget contract.
    "probeTargetWalk": read("app/src/main/java/com/marbleng/app/core/ProbeTargetWalk.kt"),
    "probeTransientTest": read(
        "app/src/test/java/com/marbleng/app/core/ProbeTransientTruthV159Test.kt"
    ),
    # MARBLE_PING_SPEED_V160 — the device-sized measurement pool, the remembered-ping disk form
    # and the two unit tests that pin both.
    "coreBudget": read("app/src/main/java/com/marbleng/app/core/MeasurementCoreBudget.kt"),
    "coreBudgetTest": read("app/src/test/java/com/marbleng/app/core/MeasurementCoreBudgetTest.kt"),
    "rememberedPingTest": read("app/src/test/java/com/marbleng/app/model/RememberedPingV160Test.kt"),
    "singBoxBuilder": read("app/src/main/java/com/marbleng/app/core/SingBoxConfigBuilder.kt"),
    "sinkholeTest": read("app/src/test/java/com/marbleng/app/core/ResolverSinkholeV163Test.kt"),
    "sinkholeDoc": read("docs/RESOLVER_SINKHOLE_V163.md"),
    "pinnedPeerDoc": read("docs/SINGBOX_PINNED_PEER_V163.md"),
    "singBox": read("app/src/main/java/com/marbleng/app/core/SingBoxManager.kt"),
    "singBoxSession": read("app/src/main/java/com/marbleng/app/core/SingBoxProcessSession.kt"),
    # MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — the Android core crashed for every profile, and the
    # product reported it as 17 dead servers. These four files are the answer: the runtime
    # contract that classifies a crash, the canary that asks the binary instead of a server, the
    # gate that stops a sweep on the first local fault, and the test that pins all three.
    "androidRuntime": read("app/src/main/java/com/marbleng/app/core/SingBoxAndroidRuntime.kt"),
    "portGuard": read("app/src/main/java/com/marbleng/app/core/CorePortGuard.kt"),
    "portGuardTest": read(
        "app/src/test/java/com/marbleng/app/core/SingBoxPortSovereigntyV158Test.kt"
    ),
    "portSovereigntyDoc": read("docs/SINGBOX_PORT_SOVEREIGNTY_V158.md"),
    "socksClient": read("app/src/main/java/com/marbleng/app/core/SocksHttpClient.kt"),
    "coreSelfTest": read("app/src/main/java/com/marbleng/app/core/SingBoxCoreSelfTest.kt"),
    "localFaultGate": read("app/src/main/java/com/marbleng/app/core/ProbeLocalFaultGate.kt"),
    "coreCrashTest": read("app/src/test/java/com/marbleng/app/core/SingBoxCoreCrashV157Test.kt"),
    # MARBLE_SINGBOX_STARTUP_GATE_V162 — the local inbound is the tunnel, the controller is a
    # measurement surface. The reported session died at `core-start-timeout` after 12 s with a
    # core that was still alive, because the readiness condition was "inbound AND controller" and
    # the controller is the last thing this core starts. The gate, its two witnesses and the test
    # that proves both on real child processes.
    "startupGateTest": read(
        "app/src/test/java/com/marbleng/app/core/SingBoxStartupGateV162Test.kt"
    ),
    "startupGateDoc": read("docs/SINGBOX_STARTUP_GATE_V162.md"),
    "nativeCoreTest": read("app/src/test/java/com/marbleng/app/core/SingBoxNativeIntegrationTest.kt"),
    "injector": read("scripts/inject-singbox-android-fix.py"),
    # MARBLE_SINGBOX_GO127_FORCE_CLOSE_V161 — the second sing-box backport: the Go 1.27
    # toolchain (mandated by the pinned Xray's go.mod) made the fork's stale
    # v2rayxhttp linkname an undefined symbol at link time. Its injector, its chapter
    # and the PR-time smoke that should have caught run #247 are pinned together.
    "injectorGo127": read("scripts/inject-singbox-go127-fix.py"),
    "go127Doc": read("docs/SINGBOX_GO127_FORCE_CLOSE_V161.md"),
    "coreUpdater": read("scripts/update-core-lock.sh"),
    # MARBLE_ENGINE_SELF_HEAL_V152 — the config doctor is the automatic repair half of the
    # 8.0.6 BLOCKED-root-cause fix, and its unit test pins the shipped failure verbatim.
    "singBoxDoctor": read("app/src/main/java/com/marbleng/app/core/SingBoxConfigDoctor.kt"),
    "singBoxSelfHealTest": read("app/src/test/java/com/marbleng/app/core/SingBoxSelfHealV152Test.kt"),
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
    "build": workflow("build.yml"),
    "gradle": read("app/build.gradle.kts"),
    "verify": workflow("verify.yml"),
    "updateCores": workflow("update-cores.yml"),
}

workflow_sources = "\n".join(
    path.read_text(encoding="utf-8")
    for path in sorted((ROOT / ".github" / "workflows").glob("*.yml"))
) + "\n" + "\n".join(
    # Staged copies count too: a workflow that cannot be pushed yet must still pin its actions.
    path.read_text(encoding="utf-8")
    for path in sorted((ROOT / "docs" / "workflows-pending").glob("*.yml"))
)

checks = []

def check(name: str, condition: bool) -> None:
    checks.append((name, bool(condition)))

def _fun_body(source: str, signature: str) -> str:
    """Body of the first function whose source contains `signature`, up to its closing brace."""
    if signature not in source:
        return ""
    return source.split(signature, 1)[1].split("\n}", 1)[0]

def integer_constant(text: str, name: str) -> int:
    match = re.search(rf"\b{name}\s*=\s*([0-9_]+)", text)
    if not match:
        raise AssertionError(f"missing numeric constant {name}")
    return int(match.group(1).replace("_", ""))

def action_uses_minimum(text: str, action: str, minimum_major: int) -> bool:
    versions = re.findall(rf"uses:\s*{re.escape(action)}@v([0-9]+)\b", text)
    return bool(versions) and all(int(version) >= minimum_major for version in versions)

def cache_dependency_paths(source: str) -> set:
    """Every path a `setup-go` cache is keyed on, in either the inline or the block form.

    MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 made this a parser instead of a substring test: the
    build now caches two pinned Go modules (Xray and the sing-box source it compiles), which the
    inline form cannot express. Matching a literal string would have failed the moment the second
    core was added, and matching a bare filename would have passed even if the cache stopped being
    keyed on it at all.
    """
    lines = source.splitlines()
    paths = set()
    for index, line in enumerate(lines):
        stripped = line.strip()
        if not stripped.startswith("cache-dependency-path:"):
            continue
        indent = len(line) - len(line.lstrip())
        inline = stripped.split(":", 1)[1].strip()
        if inline and inline not in {"|", "|-", ">", ">-"}:
            paths.update(part for part in re.split(r"[\s,]+", inline) if part)
            continue
        for follow in lines[index + 1:]:
            if not follow.strip():
                continue
            if len(follow) - len(follow.lstrip()) <= indent:
                break
            paths.add(follow.strip().lstrip("-").strip())
    return paths

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

# MARBLE_HOME_THEME_TWO_DEFAULT_V160 — every presentation is modelled and reachable; the product
# default is Theme 2 (Floating), and it is named once so the model, the store and the parser
# cannot disagree about it. The Slider stays a fully supported choice for anyone who picked it.
check(
    "Theme 2 (Floating) is modelled and is the product default",
    'IOS_FLOATING("ios_floating")' in files["models"]
    and "homeStyle: String = HomeStyle.DEFAULT.id" in files["models"]
    and "val DEFAULT: HomeStyle get() = IOS_FLOATING" in files["models"]
    and "HomeStyle.DEFAULT" in files["store"]
    and "HomeThemeFloating(" in files["homeStyles"]
    and "HomeStyleSurface(" in files["ui"]
    and "HomeStyleSurface(" in files["homeStyles"],
)
check(
    "every presentation including the former default is still modelled and reachable",
    all(
        style in files["models"] and implementation in files["homeStyles"]
        for style, implementation in (
            ('IOS_SLIDER("ios_slider")', "HomeThemeSlider("),
            ('IOS_FLOATING("ios_floating")', "HomeThemeFloating("),
            ('IOS_EMBOSSED("ios_embossed")', "HomeThemeEmbossed("),
            ('IOS_MODULAR("ios_modular")', "HomeThemeModular("),
        )
    ),
)
# A first launch opens on the default; an install that chose a presentation keeps the choice, so
# the store must ask whether a value was ever written instead of handing a default to getString.
check(
    "a first launch is the only thing the Home-style default applies to",
    'prefs.getString("homeStyle", null)' in files["store"]
    and "HomeStyle.IOS_SLIDER.id) ?: HomeStyle.IOS_SLIDER.id" not in files["store"],
)
check(
    "a first launch is the only thing the typeface default applies to",
    'prefs.getString("fontFamily", null)' in files["store"]
    and "AppFont.VAZIR.id) ?: AppFont.VAZIR.id" not in files["store"],
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
# MARBLE_URLTEST_SINGBOX_ONLY_V156 — the URL test is sing-box extended's own delay controller and
# nothing else. The Xray look-alike (a Kotlin HTTPS HEAD through Xray's SOCKS inbound) measured
# something different behind the same label, so the same server reported two incomparable numbers
# depending on the selected core. The method now refuses on any engine that does not own it, and
# Settings stops offering it there.
check(
    "URL Test belongs to sing-box extended alone and refuses elsewhere",
    "urlTestHook" in files["probe"]
    and "urlTestHook =" in files["repo"]
    and "urlTestLive(" in files["singBox"]
    and "urlTestProfile(" in files["singBox"]
    and "METHOD_URL_TEST" in files["probe"]
    and "URL_TEST_ENGINE_GATE" in files["probe"]
    and "RouteProbe.URL_TEST_ENGINE_GATE" in files["repo"]
    and "availableOn(" in files["coreEngine"]
    and "candidate.availableOn(s.coreEngine())" in files["ui"]
    # The retired Xray substitute must not come back under any name.
    and "SocksUrlTest" not in files["repo"] + files["probe"] + files["bench"]
    and "object SocksUrlTest" not in files["coreUrlTest"],
)
# ───────────────────────────────────────────────────────────────────────────────────────────────
# MARBLE_PING_FALSE_FAILED_V159 — URL test and Real delay both worked, yet occasionally published
# a healthy server as FAILED. Two budget defects: the URL test shared ONE deadline across its
# fallback targets (the coldest request ate the whole timeout and the fallbacks starved), and the
# two Real-delay Home paths fetched a single origin while the Rank sweep walked every DelayTest
# candidate. ProbeTargetWalk now owns one shared walk with a full budget per target.
# ───────────────────────────────────────────────────────────────────────────────────────────────

check(
    "the URL test never shares one deadline across its fallback targets again",
    "private fun testTargets(" not in files["singBox"]
    and "val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos" not in files["singBox"]
    and "ProbeTargetWalk.urlTest(urls, timeoutMs) { url, budget -> child.delay(url, budget) }" in files["singBox"]
    and "ProbeTargetWalk.urlTest(urls, timeoutMs) { url, budget -> urlTestLive(" in files["singBox"]
    # The spawn-storm mercy: one retry when the child never came up, never for a measurement that ran.
    and "repeat(2) {" in files["singBox"]
    and "fun urlTestProfileTargets(profile: ProxyProfile" in files["singBox"],
)
check(
    "the walk itself is the honest budget: full per target, capped, interruptible",
    "object ProbeTargetWalk" in files["probeTargetWalk"]
    and "fun perTargetBudgetMs(timeoutMs: Int): Int" in files["probeTargetWalk"]
    and "timeoutMs.coerceIn(500, 30_000)" in files["probeTargetWalk"]
    and "MAX_TARGETS = 3" in files["probeTargetWalk"]
    and "SingBoxProcessSession.checkInterrupted()" in files["probeTargetWalk"]
    and "if (last.ok) return last" in files["probeTargetWalk"]
    and "if (measured.successPercent > 0) return measured" in files["probeTargetWalk"],
)
check(
    "Real delay walks every DelayTest candidate on both Home paths",
    "fun tunnelHttpsMeasureTargets(" in files["probe"]
    and "urls = DelayTest.candidates(settings.delayTestUrl)" in files["probe"]
    and "ProbeTargetWalk.realDelay(urls) { url ->" in files["probe"]
    and "val targets = DelayTest.candidates(probeSettings.delayTestUrl)" in files["repo"]
    and "RouteProbe.tunnelHttpsMeasureTargets(" in files["repo"]
    # One throwaway core walks every candidate; a measurement that ran is never respawned for.
    and "if (measured == null) attempt()" in files["repo"],
)
check(
    "the V159 transient-failure truth is pinned in unit tests",
    "class ProbeTransientTruthV159Test" in files["probeTransientTest"]
    and "aFallbackTargetReceivesTheFullBudgetAfterTheFirstTargetSpentItsOwn" in files["probeTransientTest"]
    and "realDelayHandsTheNextOriginTheSameBudgetWhenTheFirstIsSilent" in files["probeTransientTest"]
    and "aCancelledSweepStopsBeforeTheNextTarget" in files["probeTransientTest"]
    and "atMostThreeDistinctTargetsAreEverWalked" in files["probeTransientTest"],
)
# ───────────────────────────────────────────────────────────────────────────────────────────────
# MARBLE_PING_SPEED_V160 — Real delay and URL test had to get faster *without* changing what they
# measure, and the measurement itself had to survive a restart.
#
#  - Real delay paid the whole cold start of the route once per sample: SOCKS negotiation, TCP
#    through the tunnel and a TLS handshake, times the sample count. All samples now run on the
#    session the first one opened, with the quiet gap kept between them, which is what Rank has
#    done since V156 and what v2rayNG calls "attempt two reuses the same verified session".
#  - the URL test's readiness poll napped a flat 60 ms between probes, and the pool that carries
#    the spawns was a constant four whatever the device was.
#  - a ping was the only thing the product did not write down, so a restart showed a Servers list
#    with no latency anywhere.
# ───────────────────────────────────────────────────────────────────────────────────────────────

check(
    "Real delay takes every sample on one session and keeps the quiet gap between them",
    "samples = rounds," in files["probe"]
    and "spacingMs = PingBudget.SAMPLE_SPACING_MS" in files["probe"]
    # The old per-round loop, verbatim: one fresh connection and one full handshake per sample.
    # (The quiet pause between samples still belongs to the other multi-sample methods, so the
    # assertion names the whole call shape instead of one of its tokens.)
    and "port = socksPort, url = url, samples = 1," not in files["probe"]
    # The gap has to exist and has to be interruptible, or a cancelled sweep waits out a nap.
    and "private fun quietGap(spacingMs: Long): Boolean" in files["socksClient"]
    and "if (spacingMs > 0L && !quietGap(spacingMs)) break" in files["socksClient"]
    # A one-sample run must not become twice as expensive on a dead node.
    and "val attempts = if (rounds > 1) CONSECUTIVE_FAILURES_BEFORE_ABANDON else 1" in files["probe"],
)
check(
    "the URL test stops napping between readiness probes",
    "var pollDelayMs = 4L" in files["singBoxSession"]
    and "Thread.sleep(minOf(pollDelayMs, left()).coerceAtLeast(1))" in files["singBoxSession"]
    and "pollDelayMs = (pollDelayMs * 2L).coerceAtMost(25L)" in files["singBoxSession"]
    and "Thread.sleep(minOf(60, left())" not in files["singBoxSession"],
)
check(
    "the measurement pool is sized by the device and never below the shipped floor",
    "object MeasurementCoreBudget" in files["coreBudget"]
    and "fun ceiling(cpus: Int, memoryClassMb: Int, lowRam: Boolean = false): Int" in files["coreBudget"]
    and "const val BASE = 4" in files["coreBudget"]
    and "grantMeasurementSlots(MeasurementCoreBudget.read(context))" in files["singBox"]
    and "xray.singBox?.measurementCoreCeiling ?: SingBoxManager.MAX_TEMPORARY_CORES" in files["bench"]
    and "class MeasurementCoreBudgetTest" in files["coreBudgetTest"],
)
check(
    "the last ping of every server survives a restart",
    "fun loadBenchmarks(): List<BenchmarkResult>" in files["store"]
    and "fun saveBenchmarks(v: List<BenchmarkResult>)" in files["store"]
    and "var benchmarks by mutableStateOf(rememberedBenchmarks())" in files["repo"]
    and "private fun rememberedBenchmarks(): List<BenchmarkResult>" in files["repo"]
    and "private fun persistBenchmarks()" in files["repo"]
    and "val measuredAtMs: Long = 0L" in files["models"]
    and "fun toJson() = JSONObject().apply {" in files["models"]
    and "class RememberedPingV160Test" in files["rememberedPingTest"],
)
check(
    "Settings names the tunnel core next to its own title",
    "badge = CoreEngineInfo.displayName(repo.activeCoreEngine)" in files["ui"]
    and "badge: String = \"\"," in files["ui"]
    and "CoreEngineInfo.displayName(engine) +" in files["ui"],
)

# ───────────────────────────────────────────────────────────────────────────────────────────────
# MARBLE_V156 — the share link is the authority, the URL test belongs to one engine, every bulk
# measurement can be cancelled, Real delay works with no tunnel up, and the Home header no longer
# repeats the session state next to the logo.
# ───────────────────────────────────────────────────────────────────────────────────────────────

check(
    "the share link, not the derived Xray JSON, leads the sing-box config",
    "candidateSet(" in files["singBoxBuilder"]
    and "settings.singBoxPreferParser" in files["singBoxBuilder"]
    and "STRATEGY_LINK_TRANSLATED" in files["singBoxBuilder"]
    and "ProxyParser.parseInput(link)" in files["singBoxBuilder"]
    # The writer must not quietly ignore the preference the Settings page exposes.
    and "singBoxPreferParser" in files["store"]
    and "aStoredNodeIsHandedToTheCoresOwnParserFirst" in files["linkAuthorityTest"]
    and "everyReaderIsOfferedSoOneRefusalCannotKillTheNode" in files["linkAuthorityTest"],
)

check(
    "routing is written once, for every reader, and never kills the engine",
    "routingIsIdenticalForEveryReaderOfTheSameNode" in files["linkAuthorityTest"]
    and "anUnbundledGeoRuleIsReportedInsteadOfKillingTheEngine" in files["linkAuthorityTest"]
    and "internal fun geoTag(ip: Boolean, raw: String): String?" in files["singBoxBuilder"]
    and "is not bundled for sing-box" not in files["singBoxBuilder"],
)

check(
    "every stored node is reconciled with the link it came from",
    "reconcileProfilesWithTheirLinks()" in files["repo"]
    and "private fun reconcileWithLink(" in files["repo"]
    and "SingBoxConfigBuilder.shareLink(profile)" in files["repo"]
    and "profile-link-reconcile" in files["repo"],
)

check(
    "Real delay measures with no tunnel up instead of reporting no-live-tunnel",
    "realDelayHook" in files["probe"]
    and "realDelayHook =" in files["repo"]
    and "installRealDelayHook()" in files["repo"]
    and "realDelayWithoutATunnelSaysSoOnlyWhenNoHookCanBuildOne" in files["probeTruthTest"]
    # The spawn-storm mercy that kept healthy nodes reporting xray-start/singbox-start.
    and "fun attempt(): Boolean = xray.temporary(" in files["bench"]
    and "if (spawned || times.isNotEmpty()) spawned else attempt()" in files["bench"],
)

check(
    "a measurement core skips only the overhead a measurement does not need",
    "validate: Boolean = true" in files["singBoxSession"]
    and "awaitApi: Boolean = true" in files["singBoxSession"]
    # MARBLE_SINGBOX_STARTUP_GATE_V162 — the controller is now a bounded phase of its own, and
    # whether it is *required* follows awaitApi, so a caller that never dials it never waits for
    # it. The measurement contract the rest of this check pins is otherwise untouched.
    and "requireController: Boolean = awaitApi" in files["singBoxSession"]
    and "probes.controller(apiPort, secret, remaining)" in files["singBoxSession"]
    and "validate = false" in files["singBox"]
    and "MAX_TEMPORARY_CORES" in files["singBox"]
    and "SingBoxManager.MAX_TEMPORARY_CORES" in files["bench"],
)

check(
    "every bulk measurement can be cancelled and keeps what it measured",
    "class ProbeCancelGate" in files["cancelGate"]
    and "fun cancelProbes()" in files["repo"]
    and "probeCancelGate.arm()" in files["repo"]
    and "probeCancelGate.reset()" in files["repo"]
    and "shouldStop: () -> Boolean" in files["bench"]
    and "shouldStop: () -> Boolean" in files["rankEngine"]
    and "shouldStop = probeShouldStop" in files["repo"]
    # The same control from every surface that can start a sweep.
    and "repo.cancelProbes()" in files["ui"] + files["homeStyles"]
    and files["ui"].count("repo.cancelProbes()") >= 2
    and "HomeGlyph.STOP" in files["homeStyles"]
    and "HomeIcon.STOP" in files["ui"]
    and "aCancelArmsOnceAndCanBeReusedByTheNextSweep" in files["probeTruthTest"],
)

# The status word and its dot sat between the wordmark and the actions. `repo.state == "CONNECTED"`
# elsewhere in the file builds the evidence model the banner below reads; that stays. What must not
# come back is the header rendering a status word of its own.
# Comments are stripped first: the reason the status word was removed is recorded in the comment
# right where it used to be, and that prose must not read as the readout coming back.
home_top_bar = "\n".join(
    line.split("//", 1)[0]
    for line in files["homeStyles"]
    .split("internal fun HomeTopActionBar(", 1)[1]
    .split("internal fun ", 1)[0]
    .splitlines()
)
check(
    "the Home header no longer repeats the session state next to the logo",
    "MARBLE_HOME_TOPBAR_NO_STATUS_V156" in files["homeStyles"]
    and "val stateLabel" not in files["homeStyles"]
    and "val stateTone = homeStateTone(evidence)" not in files["homeStyles"]
    and "stateLabel" not in home_top_bar
    and "stateTone" not in home_top_bar
    and "CONNECTED" not in home_top_bar
    and "READY" not in home_top_bar
    and "MarbleWordmark()" in home_top_bar
    # All four presentations share this one header, so removing it there removes it everywhere.
    and files["homeStyles"].count("HomeTopActionBar(evidence, actions, repo)") == 4,
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

# MARBLE_SINGBOX_DNS_ACTION_V152 — the root cause of the shipped 8.0.6 total outage: the builder
# wrote a `dns` outbound, deprecated in sing-box 1.11.0 and REMOVED in 1.13.0, and the pinned
# extended core (v1.14.x) rejected every profile's config for carrying one — BLOCKED, kill
# switch, and a 17-node failover replaying the same refusal. The builder must never write it and
# the route must carry the `hijack-dns` rule action the deprecation error itself names.
check(
    "the sing-box config never carries the removed dns outbound",
    'put("type", "dns")' not in files["singBoxBuilder"]
    and 'put("action", "hijack-dns")' in files["singBoxBuilder"]
    and 'DNS_OUT_TAG' not in files["singBoxBuilder"]
    and '"hijack-dns"' in files["singBoxTest"],
)
# MARBLE_SINGBOX_BOOTSTRAP_DOH_V163 — domain egress must not depend on one public DoH literal
# being alive, and the node's own hostname must not ask the Iranian system resolver (which
# answers 10.10.34.35/36). Encrypted IP-literal DoH over DIRECT is first; dns-local last-resort.
check(
    "the node hostname bootstraps via encrypted-direct DNS",
    "DNS_BOOTSTRAP_TAG" in files["singBoxBuilder"]
    and "DNS_LOCAL_TAG" in files["singBoxBuilder"]
    and "isLiteralAddress" in files["singBoxBuilder"]
    and "theProxyHostnameBootstrapsThroughEncryptedDirectDns" in files["singBoxTest"],
)
# MARBLE_ENGINE_SELF_HEAL_V152 — a config rejection is engine-level, and Marble Intelligence
# answers it automatically: the doctor repairs the known removals in place and re-checks, and
# the VPN service latches an engine fault so failover stops replaying it node by node and the
# session rides the other engine instead.
check(
    "the sing-box config doctor exists and is wired into the manager",
    'object SingBoxConfigDoctor' in files["singBoxDoctor"]
    and 'fun repair(' in files["singBoxDoctor"]
    and 'fun isEngineLevelFault(' in files["singBoxDoctor"]
    and 'SingBoxConfigDoctor.hardenForAndroid(' in files["singBox"]
    and 'lastSelfHealNotes' in files["singBox"],
)
check(
    "a local core fault neither changes engines nor penalizes healthy servers",
    'activeEngine = settings.coreEngine()' in files["vpn"]
    and 'allowRecovery = false' in files["vpn"]
    and 'recordProfileFailure = false' in files["vpn"]
    and 'singBoxConfigFaultLatch' not in files["vpn"],
)
check(
    "the self-heal path is pinned by unit tests",
    'theShipped860FaultIsRepairedAutomatically' in files["singBoxSelfHealTest"]
    and 'thePre112DnsAddressKeyIsMigrated' in files["singBoxSelfHealTest"]
    and 'engineLevelFaultsAreRecognisedSoFailoverStopsWalkingNodes' in files["singBoxSelfHealTest"]
    and 'anAlreadyModernConfigComesBackUntouched' in files["singBoxSelfHealTest"],
)
# MARBLE_RESOLVER_POOL_WIDENED_V152 — when an operator disrupts a resolver *set* (the shipped log
# demoted all three original stock endpoints), the intelligence needs candidates on different
# infrastructure to promote, or "demote last, promote first" is a shuffle of dead endpoints.
check(
    "the stock DoH pool carries diverse fallbacks beyond the big three",
    'dns.adguard-dns.com' in files["intel"]
    and '149.112.112.112' in files["intel"]
    and '1.0.0.1' in files["intel"],
)
# MARBLE_SINGBOX_PROTOCOLS_V153 — the second engine consumes Marble Intelligence's own resolver
# order, not the raw candidate list, so demoted endpoints stay last even when the builder does not
# run effectiveSettings on the live start path.
check(
    "sing-box reads the evidence-ordered resolver pool from Marble Intelligence",
    "fun singBoxResolverPool(" in files["intel"]
    and "singBoxResolverPool(settings)" in files["singBox"],
)
# MARBLE_PING_AIR_V152 — the latency readouts on Servers and Home dropped their tinted fill: on
# a stacked subscription row the slab read as a chip fighting the protocol badge, and the
# glyph/number/unit triad was pressed against its own walls. The measurement stands alone in
# its quality tone now; the width floor survives so numbers still column-align.
check(
    "the Servers ping readout carries no background of its own",
    ".background(tone.copy(alpha = .12f))" not in _fun_body(files["ui"], "fun ServersPingCapsule")
    and "MARBLE_PING_AIR_V152" in files["ui"],
)
check(
    "the Home latency slab is tone-only",
    ".background(tone.copy(alpha = 0.12f))" not in _fun_body(files["homeStyles"], "fun HomeServerLatencySlab")
    and "MARBLE_PING_AIR_V152" in files["homeStyles"],
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
# MARBLE_SINGBOX_PROTOCOLS_V153 — the schema regressions that made translated profiles fail at
# `sing-box check` are blocked at the source and at the doctor: `network` must never be an array,
# Hysteria v1 and v2 must map to their own outbound types, and the throwaway URL-test config must
# run the same check pass as a real start.
check(
    "sing-box never writes an array network field and the doctor repairs one",
    'result.put("network", JSONArray()' not in files["singBoxBuilder"]
    and "migrateNetworkAndTlsFields" in files["singBoxDoctor"]
    and "translatedOutboundsNeverCarryAnArrayNetworkField" in files["singBoxTest"]
    and "anArrayNetworkFieldAndTlsFragmentAreRepaired" in files["singBoxSelfHealTest"],
)
check(
    "Hysteria v1 and v2 translate to their own sing-box outbound types",
    'result.put("type", "hysteria2")' in files["singBoxBuilder"]
    and 'result.put("type", "hysteria")' in files["singBoxBuilder"]
    and "hysteriaV1TranslatesToTheV1OutboundNotHysteria2" in files["singBoxTest"],
)
# MARBLE_SINGBOX_LINK_AUTHORITY_V156 — both the connect path and the measurement path open their
# core through ONE opener that walks every representation of the profile, so the two cannot drift.
# The strictness difference is a parameter of that opener, not a second code path.
check(
    "connect and temporary probes use one tested process/check implementation",
    files["singBox"].count("SingBoxProcessSession.open(") == 1
    and "private fun openFirst(" in files["singBox"]
    and files["singBox"].count("openFirst(") >= 3
    and "candidateBuilds(" in files["singBoxBuilder"]
    and "lastStartStrategy" in files["singBox"]
    and 'listOf("check", "-c",' in files["singBoxSession"]
    and 'connection.responseCode == 200' in files["singBoxSession"]
    and 'singBoxResolverPool(settings)' in files["singBox"]
    and 'intelligence.effectiveSettings(profile, probeSettings)' in files["repo"],
)

# MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — an app-UID child of the *Android* sing-box artifact gets
# no netlink interface monitor, and a core built without the upstream nil-guards dereferences one
# in its `direct` outbound: every profile panicked with the same SIGSEGV, and the product reported
# it as "reachable = 0 of 17", as a BLOCKED "Core/configuration error", and — in a Bug Finder scan
# taken while DISCONNECTED — as no failure at all. The fix has four parts and each one is pinned
# here, because every part is the kind of thing a later refactor can silently undo.
check(
    "the Android core is compiled from pinned source, never downloaded as an artifact",
    "scripts/inject-singbox-android-fix.py" in files["native"]
    and "SINGBOX_COMMIT" in files["native"]
    # The injected Go regression tests run on the host before a single ABI is built, and a failure
    # is fatal: a core that panics on Android must never reach jniLibs.
    and "go test \\" in files["native"]
    and "./protocol/direct" in files["native"]
    and 'die "sing-box Android CLI crash regression tests failed"' in files["native"]
    and "build_singbox" in files["native"]
    and not re.search(r"sing-box-[^\s\"']*android", files["native"]),
)
check(
    "the source build never enables the admin panel, whose assets are not in the source tree",
    # The fork's service/admin_panel/service.go embeds `dist`, a Vite bundle it builds with
    # `npm run build` in its own pipeline and gitignores in its own repository. Enabling the
    # tag on a source clone is an instant, unconditional
    # `pattern dist: no matching files found` - which is how every native build on main died
    # after PR #130. MarbleNG is a client and never configures an admin-panel service.
    "with_admin_panel" not in re.search(
        r'^SINGBOX_TAGS="([^"]*)"', files["native"], re.M
    ).group(1).split(",")
    # The graph is resolved for every ABI before a single ABI is compiled, so this class of
    # failure (absent generated asset, unresolvable import) costs seconds, not a full run.
    and "go list \\" in files["native"]
    and "./cmd/sing-box" in files["native"]
    and "sing-box Android package graph does not resolve" in files["native"],
)

check(
    "the crash backport is anchored to the upstream commit and proven in CI",
    "288411b0b9044c11a00a8ab478000e3ec1133101" in files["injector"]
    and "MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157" in files["injector"]
    and "protocol/direct/outbound.go" in files["injector"]
    and "scripts/inject-singbox-android-fix.py" in files["verify"]
    and "go test ./protocol/direct ./route" in files["verify"]
    # The core updater writes main directly, so anchor drift has to kill it *before* it commits a
    # lock nobody can build.
    and "scripts/inject-singbox-android-fix.py" in files["updateCores"],
)

# ── MARBLE_SINGBOX_GO127_FORCE_CLOSE_V161 ────────────────────────────────────────────
# Build run #247 died at "Building sing-box for arm64-v8a": the pinned Xray's go.mod had
# quietly moved the release toolchain from Go 1.26 to Go 1.27, and under Go 1.27 the
# pinned sing-box's v2rayxhttp linknamed a symbol x/net http2 no longer compiles in.
# The fix is a build-time backport with its own injector, and these checks pin every
# piece of it so the failure class cannot come back unnoticed:
#   * the injector is wired into the release build AND the PR gate AND the core updater;
#   * the toolchain-sensitive variant is linked with the same badlinkname tag the release
#     build compiles with, at PR time — the one gate that would have caught run #247
#     before the merge;
#   * the release tag set keeps the tags the force-close (and the fork's own hooks) need.
_release_tags = (
    re.search(r'^SINGBOX_TAGS="([^"]*)"', files["native"], re.M) or re.search(r"$^", "")
).group(1).split(",")
check(
    "the Go 1.27 force-close backport is anchored to the failed run and proven in CI",
    "MARBLE_SINGBOX_GO127_FORCE_CLOSE_V161" in files["injectorGo127"]
    and "transport/v2rayxhttp/dialer.go" in files["injectorGo127"]
    # the exact link error of run #247, recorded so a future reader can diff cause and fix
    and "golang.org/x/net/http2.(*Transport).connPool" in files["injectorGo127"]
    and "force_close_go127.go" in files["injectorGo127"]
    and "force_close_go127_stub.go" in files["injectorGo127"]
    and "force_close_legacy.go" in files["injectorGo127"]
    and "scripts/inject-singbox-go127-fix.py" in files["native"]
    and "scripts/inject-singbox-go127-fix.py" in files["verify"]
    and "scripts/inject-singbox-go127-fix.py" in files["updateCores"]
    and "go-version-file" in files["verify"],
)
check(
    "the sing-box force-close link pin runs with the release build's badlinkname tag",
    "-tags badlinkname" in files["native"]
    and "-tags badlinkname" in files["verify"]
    and "badlinkname" in _release_tags
    and "tfogo_checklinkname0" in _release_tags
    and "MARBLE_SINGBOX_GO127_FORCE_CLOSE_V161" in files["go127Doc"],
)
_pending_workflows = sorted(
    path.name for path in (ROOT / "docs" / "workflows-pending").glob("*.yml")
)
_pending_readme = read("docs/workflows-pending/README.md")
check(
    "staged workflow changes are documented for whoever installs them",
    bool(_pending_workflows)
    and all(name in _pending_readme for name in _pending_workflows)
    and all(f".github/workflows/{name}" in _pending_readme for name in _pending_workflows)
    # Nothing may be staged and then forgotten: a staged workflow has to still be a workflow.
    and all("jobs:" in workflow(name) for name in _pending_workflows),
)
_core_lock = json.loads(files["coreLock"])
check(
    "core-lock pins the sing-box source commit and the local patch level",
    len(_core_lock["singbox"].get("commit", "")) == 40
    and bool(_core_lock["singbox"].get("patch", ""))
    # Exactly one digest, and it is the host artifact the Go tests run against. An android/* digest
    # here would mean a prebuilt Android binary is still being fetched — the thing that shipped the
    # crash in the first place, and the reason the build now compiles every ABI from source.
    and len(_core_lock["singbox"].get("sha256", {})) == 1
    and all("-linux-amd64" in key for key in _core_lock["singbox"]["sha256"])
    and not any("android" in key for key in _core_lock["singbox"]["sha256"])
    and "resolve_singbox_commit" in files["coreUpdater"],
)
check(
    "a crashed core is classified as a core fault, never as a config refusal",
    "fun isCoreCrash(" in files["androidRuntime"]
    and "fun isUnusableCore(" in files["androidRuntime"]
    and "fun isPackageManagerFault(" in files["androidRuntime"]
    and "if (SingBoxAndroidRuntime.isUnusableCore(reason)) return false" in files["singBox"]
    and "SingBoxAndroidRuntime.isUnusableCore(reason)" in files["singBoxDoctor"]
    and "theReportedCrashIsClassifiedAsACoreFaultByEveryClassifier" in files["coreCrashTest"]
    # The package-manager WARN is printed by every healthy Android core too, so it must stay
    # evidence: counting it as fatal would turn real per-node schema refusals into "broken device".
    and "aPerNodeRefusalCarryingThePackageManagerWarningStaysPerNode" in files["coreCrashTest"],
)
check(
    "the manager asks the binary whether it can start before it asks any server",
    "fun selfTest(" in files["singBox"]
    and files["singBox"].count("requireUsableCore()") >= 3
    and "SingBoxCoreSelfTest.probe(" in files["singBox"]
    and "fun canaryConfig(" in files["coreSelfTest"]
    and '"direct"' in files["coreSelfTest"]
    and "SingBoxConfigBuilder.DIRECT_TAG" in files["coreSelfTest"]
    # A listening inbound does not prove the box started: outbounds are started after inbounds, so
    # the canary waits out a grace window or it can report PASS microseconds before the panic.
    and "settleMs" in files["singBoxSession"]
    and "private fun settle(" in files["singBoxSession"]
    and "settleMs = SETTLE_MS" in files["coreSelfTest"]
    and "theCoreSelfTestCanaryStartsThePinnedCoreAndServesItsLocalInbound" in files["nativeCoreTest"],
)
check(
    "one local fault stops a sweep instead of becoming seventeen dead servers",
    "probeLocalFaultGate" in files["repo"]
    and "probeLocalFaultGate.isTripped" in files["repo"]
    and "probeLocalFaultGate.trip(result.failureReason, result.success)" in files["repo"]
    and files["repo"].count("probeLocalFaultGate.reset()") == 2
    and files["repo"].count("batchSummary(") >= 3
    and "fun isSweepFatal(" in files["localFaultGate"]
    # What must NOT stop a sweep is half the contract: per-node refusals, capacity, slowness and
    # every network-shaped reason are the sweep's subject matter, not a device fault.
    and '"core-busy:"' in files["localFaultGate"]
    and '"config-unsupported:"' in files["localFaultGate"]
    and "theReasonsThatMustNotStopASweepDoNotStopIt" in files["coreCrashTest"],
)
check(
    "Bug Finder reports an unusable core in every app state",
    "SingBoxAndroidRuntime.isCoreCrash(coreEvidence)" in files["bug"]
    and '"SingBox core start-up"' in files["bug"]
    and "lastProbeFault" in files["bug"]
    and "lastProbeFault = probeLocalFault" in files["repo"],
)
check(
    "the blocked state names the fault's owner without switching engines behind the user's back",
    "faultClass" in files["vpn"]
    and '"Core cannot run on this device"' in files["vpn"]
    and "SingBoxAndroidRuntime.isUnusableCore(coreStartError)" in files["vpn"]
    # Engine selection stays an explicit user contract (MARBLE_SINGBOX_CORE_V151): a broken core
    # is reported, never answered by silently running the other binary.
    and "Explicit engine selection is a contract" in files["vpn"]
    and "activeEngine = CoreEngine." not in files["vpn"],
)

# MARBLE_SINGBOX_PORT_SOVEREIGNTY_V158 — the 09:18–09:26 incident: four `bind: address already in
# use` refusals over eight minutes with alive=false on both engines (an untracked orphan core held
# the port), every refusal walking all three reader candidates, and the blocked state blaming the
# profile. Each half of that sentence is pinned below.
check(
    "the local SOCKS port is attributed and reclaimed before either engine spawns a child",
    "object CorePortGuard" in files["portGuard"]
    # The holder is found through the kernel's own tables: LISTEN inode from /proc/net/tcp{,6},
    # then the socket:[inode] fd link back to a pid. No root, no shell.
    and "fun parseListenerInodes(" in files["portGuard"]
    and "fun findHolderPids(" in files["portGuard"]
    and '"/proc/net/' in files["portGuard"]
    and "socket:[" in files["portGuard"]
    # Ownership is checked twice before any signal: our UID AND our core binary path.
    and "fun isCoreBinary(" in files["portGuard"]
    and "probe.myUid()" in files["portGuard"]
    # Escalation is bounded, TERM first.
    and "SIGTERM" in files["portGuard"]
    and "SIGKILL" in files["portGuard"]
    # The failure vocabulary is the local-fault prefix every classifier already speaks.
    and '"core-port: 127.0.0.1:$port is already in use"' in files["portGuard"]
    # Both engines go through the same guard; neither spawns into a squatted port.
    and "CorePortGuard.reclaim(" in files["singBox"]
    and "CorePortGuard.reclaim(" in files["xray"]
    and "CorePortGuard.androidProbe()" in files["singBox"]
    and "CorePortGuard.androidProbe()" in files["xray"]
    and "CoreEngineInfo.SINGBOX_BINARY" in files["portGuardTest"],
)
check(
    "a bind conflict is a local fault with a named owner, never a config refusal",
    "fun isPortBindConflict(" in files["androidRuntime"]
    and "PORT_REMEDIATION" in files["androidRuntime"]
    and "isPortBindConflict(reason) -> PORT_REMEDIATION" in files["androidRuntime"]
    and "if (SingBoxAndroidRuntime.isPortBindConflict(reason)) return false" in files["singBox"]
    and "if (SingBoxAndroidRuntime.isPortBindConflict(reason)) throw error" in files["singBox"]
    and '"Local port in use"' in files["vpn"]
    # stop() proves its verdict; the evidence outlives the session.
    and "internal fun stop(child: Process): Boolean" in files["singBoxSession"]
    and "lastStopEvidence" in files["singBox"],
)
check(
    "local-client SOCKS handshake aborts are benign evidence, not scan failures",
    "internal object CoreLogNoise" in files["bug"]
    and "CoreLogNoise.isLocalClientHandshakeAbort(line)" in files["bug"]
    and "localClientHandshakeAborts=" in files["bug"]
    and "theReportedClientAbortLinesAreClassifiedBenign" in files["portGuardTest"]
    and "realFaultsAreNeverClassifiedAsClientAborts" in files["portGuardTest"]
    and "theReportedBindConflictLinesOfClassificationAndWalkExclusion" not in files["portGuardTest"]
    and "aStaleCoreHoldingThePortIsReapedAndTheStartProceeds" in files["portGuardTest"]
    and "aForeignHolderIsNamedButNeverSignalled" in files["portGuardTest"],
)
check(
    "MarbleNG's own SOCKS5 requests are RFC 1928 well-formed and delivered in one write",
    # The V158 audit disproved the "MarbleNG can't produce read-fqdn EOFs" claim: socksTarget
    # shipped the domain name without the RFC 1928 length prefix and every request left as
    # three separate writes. The wire shape and the single-write delivery are pinned.
    "internal fun socksTarget(" in files["socksClient"]
    and "return 3 to byteArrayOf(hostBytes.size.toByte()) + hostBytes" in files["socksClient"]
    and "internal fun buildSocks5Request(" in files["socksClient"]
    and "internal fun writeSocks5Request(" in files["socksClient"]
    and "output.write(byteArrayOf(5, 1, 0, target.first.toByte()))" not in files["socksClient"]
    and "aDomainTargetCarriesTheRfc1928LengthPrefix" in files["portGuardTest"]
    and "theDomainRequestLeavesAsOneWellFormedSegment" in files["portGuardTest"]
    and "theRequestIsSentInExactlyOneWrite" in files["portGuardTest"],
)


# MARBLE_SINGBOX_STARTUP_GATE_V162 — the reported session: `core-start-timeout` after 12119 ms,
# `alive=false`, kill switch held, and a retained core log whose entire content was the benign
# Android package-list WARN. Two defects, both reproducible without a device: the readiness
# condition waited for the Clash controller, which this core binds in its LAST start-up stage, so
# a delay that carried no traffic was paid as a failed connect; and `sing-box check` shared the
# child's start-up budget, so a slow validation could leave it a fraction of its window.
check(
    "the local inbound is the readiness gate of a live connect; the controller is a phase",
    "data class StartReadiness(" in files["singBoxSession"]
    # Three phases, named, so a report can say which half of the wait ran out.
    and "interface ReadinessProbes" in files["singBoxSession"]
    and "requireController: Boolean = awaitApi" in files["singBoxSession"]
    and "controllerTimeoutMs: Long = CONTROLLER_TIMEOUT_MS" in files["singBoxSession"]
    and "const val CONTROLLER_TIMEOUT_MS: Long = 2_500L" in files["singBoxSession"]
    and "the local inbound never answered in " in files["singBoxSession"]
    and "the controller never answered in " in files["singBoxSession"]
    # The live connect is the caller that may carry traffic without a controller.
    and "requireController = false" in files["singBox"]
    and "settleMs = LIVE_SETTLE_MS" in files["singBox"]
    and "const val LIVE_SETTLE_MS: Long = SingBoxCoreSelfTest.SETTLE_MS" in files["singBox"]
    and "lastStartReadiness" in files["singBox"]
    # …and the two halves are printed separately, because one sentence for both is what made
    # the reported log unreadable.
    and '"inbound" to (readiness?.inboundUp ?: coreStarted)' in files["vpn"]
    and '"controller" to (readiness?.controllerUp ?: true)' in files["vpn"]
    and "last start-up: inbound=" in files["bug"]
    and "aTunnelWhoseControllerNeverAnswersStillCarriesTraffic" in files["startupGateTest"]
    and "aControllerTheMeasurementActuallyDialsIsStillRequired" in files["startupGateTest"]
    and "anInboundThatNeverOpensIsTheOtherFailureAndSaysWhichOneItWas" in files["startupGateTest"]
    and "aCoreThatDiesAfterItsInboundOpensIsStillReportedAsACrash" in files["startupGateTest"],
)
check(
    "the validation spawn no longer spends the window the child starts in",
    "validateTimeoutMs: Long = VALIDATE_TIMEOUT_MS" in files["singBoxSession"]
    and "const val VALIDATE_TIMEOUT_MS: Long = 8_000L" in files["singBoxSession"]
    and "validator.waitFor(validateTimeoutMs, TimeUnit.MILLISECONDS)" in files["singBoxSession"]
    # Both halves of the fix, not one: the validator may no longer be cut short by the child's
    # clock, and the child's clock may no longer start before the validator has finished.
    and "val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(startupTimeoutMs)" in files["singBoxSession"]
    and "check(left() > 0) { \"core-start-timeout\" }" in files["singBoxSession"]
    and "aSlowValidationNoLongerShrinksTheWindowTheChildStartsIn" in files["startupGateTest"]
    and "aChildThatNeedsLongerThanTheLeftoversStillStarts" in files["startupGateTest"],
)
check(
    "an exhausted start-up window is explained as an exhausted start-up window",
    # The benign package-list WARN rides above every Android failure, so a device that merely
    # timed out was handed the pre-V157 "update MarbleNG / switch to Xray" paragraph.
    "fun isStartupTimeout(" in files["androidRuntime"]
    and "const val STARTUP_TIMEOUT_REMEDIATION: String =" in files["androidRuntime"]
    and "isStartupTimeout(reason) -> STARTUP_TIMEOUT_REMEDIATION" in files["androidRuntime"]
    and "SingBoxAndroidRuntime.STARTUP_TIMEOUT_REMEDIATION" in files["localFaultGate"]
    and "isStartupTimeout(reason) -> STARTUP_TIMEOUT_REMEDIATION" in files["androidRuntime"]
    and "aStartUpTimeoutIsNoLongerExplainedAsThePackageManagerWarning" in files["startupGateTest"]
    and "theTwoHalvesOfTheWaitAreStillNotVerdictsAboutAServer" in files["startupGateTest"]
    and "MARBLE_SINGBOX_STARTUP_GATE_V162" in files["startupGateDoc"],
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
build_go_cache = cache_dependency_paths(files["build"])
check(
    "Xray Go cache follows the pinned dependency checksum",
    ".bootstrap/xray/go.sum" in build_go_cache,
)
# The second core is compiled from pinned source in the same job, so its module cache has to be
# keyed on its own checksum too — otherwise a fork bump reuses a stale dependency tree and the
# build fails in a way that looks like a network problem.
check(
    "sing-box Go cache follows the pinned dependency checksum",
    ".bootstrap/singbox/go.sum" in build_go_cache,
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

# MARBLE_FREEDOM_SOCKOPT_STRATEGY_V163 — the pinned Xray core reads a freedom hop's resolve
# strategy from `streamSettings.sockopt.domainStrategy` only; the `settings.domainStrategy` /
# `targetStrategy` alias is migrated with a start-up WARNING today and removed tomorrow. The
# hardener must therefore never write the alias and must migrate an imported one.
check(
    "freedom hops carry their resolve strategy in sockopt only, never the deprecated alias",
    "internal fun writeFreedomResolveStrategy(outbound: JSONObject, strategy: String)" in files["hardener"]
    and 'settingsObject.put("targetStrategy"' not in files["hardener"]
    and 'Deprecated freedom.domainStrategy alias written next to sockopt.domainStrategy' in files["hardener"]
    and "MARBLE_FREEDOM_SOCKOPT_STRATEGY_V163" in files["hardener"]
    and "MARBLE_FREEDOM_SOCKOPT_STRATEGY_V163" in files["sinkholeTest"],
)

# MARBLE_RESOLVER_SINKHOLE_V163 — a domestic anti-sanction resolver (dns.shecan.ir …) and an
# endpoint measured with an expired certificate are *excluded* from every emitted resolver graph,
# not merely demoted; the sing-box remote fallback races when the evidence says a peer is failing.
check(
    "domestic and cert-broken resolvers are excluded from both engines' resolver graphs",
    "fun isDomesticResolver(endpoint: String): Boolean" in files["resolverPolicy"]
    and "fun excluded(" in files["resolverPolicy"]
    and "fun withoutExcluded(" in files["resolverPolicy"]
    and "const val CERT_BROKEN_TTL_MS" in files["resolverPolicy"]
    and "val measuredDnsExcludedEndpoints: String" in files["models"]
    and "measuredDnsExcludedEndpoints = dnsExcluded.joinToString" in files["intel"]
    and "ResolverEvidencePolicy.withoutExcluded(candidates, evidence, now)" in files["intel"]
    and "settings.measuredDnsExcludedEndpoints" in files["hardener"]
    and "ResolverEvidencePolicy.isDomesticResolver(url)" in files["hardener"]
    and "ResolverEvidencePolicy.isDomesticResolver(url)" in files["singBoxBuilder"]
    and 'settings.measuredDnsParallel) "parallel" else "sequential"' in files["singBoxBuilder"]
    and "MARBLE_RESOLVER_SINKHOLE_V163" in files["sinkholeDoc"]
    and "class ResolverSinkholeV163Test" in files["sinkholeTest"],
)

# MARBLE_SINGBOX_PINNED_PEER_V163 — a pcs/vcn (pinnedPeerCertSha256 / verifyPeerCertByName)
# profile is refused for sing-box extended BEFORE the fork's parser can accept the link and drop
# the pin: that is the "connected on sing-box, no Internet" report. The refusal names Xray.
check(
    "a certificate-pinning profile is refused for sing-box before the tunnel, naming Xray",
    "fun pinnedPeerRefusal(profile: ProxyProfile): String?" in files["singBoxBuilder"]
    and "internal fun linkCarriesPin(link: String): Boolean" in files["singBoxBuilder"]
    and "pinnedPeerRefusal(profile)?.let { return CandidateSet(emptyList(), it) }" in files["singBoxBuilder"]
    and "if (engine == CoreEngine.SINGBOX) return SingBoxConfigBuilder.pinnedPeerRefusal(profile)" in files["vpn"]
    and "MARBLE_SINGBOX_PINNED_PEER_V163" in files["pinnedPeerDoc"]
    and "MARBLE_SINGBOX_PINNED_PEER_V163" in files["sinkholeTest"],
)

# MARBLE_REMEMBERED_PING_KEEP_V163 / MARBLE_SETTINGS_HUB_TRIM_V163 — the benchmark table is no
# longer trimmed by a TRIM_MEMORY callback (which then got persisted over the remembered pings),
# and the Settings hub shows a bare title with no version stamp on the Tunnel core row.
check(
    "remembered pings survive memory pressure and the settings hub is trimmed",
    "MARBLE_REMEMBERED_PING_KEEP_V163" in files["repo"]
    and "benchmarks.filter { it.profileId == active }.take(1)" not in files["repo"]
    and 'MarbleCompactTopBar(title = "Settings")' in files["ui"]
    and "MARBLE_SETTINGS_HUB_TRIM_V163" in files["ui"]
    and files["ui"].count("SettingsVersionPreview(") == 2,
)

# MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — Kotlin block comments NEST, so a KDoc that merely
# *mentions* `/*` never terminates: its own `*/` closes the inner level and the comment swallows
# the rest of the file. kotlinc then answers with one "Unclosed comment" line and a wall of
# "Unresolved reference" errors pointing at files that are perfectly correct — which is exactly
# what this branch's first CI run produced, from `android/*` written as prose in two KDoc lines of
# SingBoxAndroidRuntime.kt. The gradle step is the most expensive place in the workflow to discover
# a tokenizing mistake, and it is also the least informative one, so the tokenizer runs here first.
#
# It is the same tokenizer a developer runs by hand, loaded from tools/kotlin-structure-check.py
# rather than reimplemented: two scanners would eventually become two opinions about what a
# comment is, which is how the mistake got past the local check in the first place.
_structure_spec = importlib.util.spec_from_file_location(
    "kotlin_structure_check", ROOT / "tools" / "kotlin-structure-check.py"
)
kotlin_structure = importlib.util.module_from_spec(_structure_spec)
_structure_spec.loader.exec_module(kotlin_structure)

kotlin_scan = [
    f"{path.relative_to(ROOT)}: {problem}"
    for path in sorted((ROOT / "app" / "src").rglob("*.kt"))
    for problem in kotlin_structure.check(path)
]
check(
    "every Kotlin source tokenizes cleanly — comments nest and terminate, literals close, "
    "braces balance"
    + (f" [first: {kotlin_scan[0]} • {len(kotlin_scan)} problem(s)]" if kotlin_scan else ""),
    not kotlin_scan,
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
