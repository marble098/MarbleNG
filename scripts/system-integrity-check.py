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
    "singBoxTransport": read("app/src/main/java/com/marbleng/app/core/SingBoxTransportTranslator.kt"),
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
    # MARBLE_CORE_CONFIG_SUPERSET_V165 — the "can the selected core load this?" boundary, the lossless
    # repair pass, the alias-aware share-link reader, the refusal-storm guard, and the ONE policy patch
    # this app applies to its own core. They are pinned together deliberately: the app may relax only
    # what the core patch actually relaxes, and both must keep agreeing with the pinned source.
    "configSuperset": read("app/src/main/java/com/marbleng/app/core/CoreConfigSuperset.kt"),
    "configRepairs": read("app/src/main/java/com/marbleng/app/core/XrayConfigRepairs.kt"),
    "linkParams": read("app/src/main/java/com/marbleng/app/core/ShareLinkParams.kt"),
    "linkNormalizer": read("app/src/main/java/com/marbleng/app/core/ShareLinkNormalizer.kt"),
    "xhttpExtra": read("app/src/main/java/com/marbleng/app/core/XhttpExtra.kt"),
    "linkWireParity": read("app/src/main/java/com/marbleng/app/core/LinkWireParity.kt"),
    "vlessIpv6Test": read("app/src/test/java/com/marbleng/app/core/VlessXhttpRealityIpv6Test.kt"),
    "vlessIpv6Doc": read("docs/VLESS_XHTTP_REALITY_IPV6.md"),
    # MARBLE_REALITY_MLKEM_HANDSHAKE_V194 — the REALITY handshake-shape fault (missing hybrid
    # key share) and its diagnostic half: the policy that names residual rejections, the test
    # that pins the verbatim keys plus the emitted flag, and the Bug Finder that reports them.
    "realityPolicy": read("app/src/main/java/com/marbleng/app/core/RealityHandshakePolicy.kt"),
    "realityMlkemTest": read("app/src/test/java/com/marbleng/app/core/RealityMlkemHandshakeTest.kt"),
    "realityMlkemDoc": read("docs/REALITY_MLKEM_HANDSHAKE_V194.md"),
    "bugFinder": read("app/src/main/java/com/marbleng/app/core/BugFinder.kt"),
    "blockGuard": read("app/src/main/java/com/marbleng/app/core/ConfigBlockGuard.kt"),
    "preflight": read("app/src/main/java/com/marbleng/app/core/ProfilePreflightValidator.kt"),
    "auditor": read("app/src/main/java/com/marbleng/app/core/ProfileSecurityAuditor.kt"),
    "supersetInjector": read("scripts/inject-xray-config-superset.py"),
    "supersetPolicy": read("native/xraypatch/marble_outbound_policy.go"),
    "supersetPolicyTest": read("native/xraypatch/marble_outbound_policy_test.go"),
    "persianLexicon": read("app/src/main/java/com/marbleng/app/ui/MarblePersianLexicon.kt"),
    "supersetDoc": read("docs/CORE_CONFIG_SUPERSET_V165.md"),
    "interopDoc": read("docs/core-interoperability.md"),
    "supersetTest": read("app/src/test/java/com/marbleng/app/core/CoreConfigSupersetV165Test.kt"),
    "repairsTest": read("app/src/test/java/com/marbleng/app/core/XrayConfigRepairsV165Test.kt"),
    "xrayStartFailure": read("app/src/main/java/com/marbleng/app/core/XrayStartFailure.kt"),
    "reservedTagTest": read("app/src/test/java/com/marbleng/app/core/ReservedTagCollisionV183Test.kt"),
    "reservedTagDoc": read("docs/RESERVED_TAG_COLLISION_V183.md"),
    "linkParamsTest": read("app/src/test/java/com/marbleng/app/core/ShareLinkParamsV165Test.kt"),
    "blockGuardTest": read("app/src/test/java/com/marbleng/app/core/ConfigBlockGuardV165Test.kt"),
    "tlsPinning": read("app/src/main/java/com/marbleng/app/core/TlsPinningPolicy.kt"),
    "reachability": read("app/src/main/java/com/marbleng/app/core/MultiVectorReachability.kt"),
    "probe": read("app/src/main/java/com/marbleng/app/core/RouteProbe.kt"),
    "pinningTest": read("app/src/test/java/com/marbleng/app/core/TlsPinningPolicyTest.kt"),
    "pingTruthTest": read("app/src/test/java/com/marbleng/app/core/PingMethodTruthV149Test.kt"),
    "ssh": read("app/src/main/java/com/marbleng/app/core/SshTransportManager.kt"),
    "socks": read("app/src/main/java/com/marbleng/app/core/SocksHttpClient.kt"),
    "resolverPolicy": read("app/src/main/java/com/marbleng/app/core/ResolverEvidencePolicy.kt"),
    # MARBLE_IP_FAMILY_SCAN_V196 — the measured address family of a server, the ladder that
    # keeps a forced family from becoming an outage, and the name-vs-resolver classifier that
    # stopped one failing domain from looking like a resolver storm.
    "familyScanner": read("app/src/main/java/com/marbleng/app/core/IpFamilyScanner.kt"),
    # MARBLE_IP_FAMILY_TRUTH_V197 / MARBLE_SERVER_LOCATION_V197 / MARBLE_IME_HYGIENE_V197 /
    # MARBLE_ROUTE_PROBE_MULTI_TARGET_V197 — the four chapters that came out of one logcat:
    # a scan that could call a dual-stack server IPv4-only, a location test that measured a
    # country and then drew no flag, a keyboard that was asked to hide a hundred times, and a
    # probe that only discovered a filtered target after spending a whole cycle on it.
    "endpointResolver": read(
        "app/src/main/java/com/marbleng/app/core/EncryptedEndpointResolver.kt"
    ),
    "locationResolver": read(
        "app/src/main/java/com/marbleng/app/core/ServerLocationResolver.kt"
    ),
    "locationTest": read(
        "app/src/test/java/com/marbleng/app/core/ServerLocationResolverV192Test.kt"
    ),
    "locationV197Test": read(
        "app/src/test/java/com/marbleng/app/core/ServerLocationResolverV197Test.kt"
    ),
    "ime": read("app/src/main/java/com/marbleng/app/ui/MarbleIme.kt"),
    "imeTest": read("app/src/test/java/com/marbleng/app/ui/MarbleImeHiderTest.kt"),
    "v197Doc": read("docs/IPV6_TRUTH_LOCATION_AND_IME_V197.md"),
    "familyLadder": read("app/src/main/java/com/marbleng/app/core/Ipv6FallbackLadder.kt"),
    "domainFault": read("app/src/main/java/com/marbleng/app/core/DnsDomainFaultPolicy.kt"),
    "stormGuard": read("app/src/main/java/com/marbleng/app/core/DnsStormGuard.kt"),
    "familyScannerTest": read("app/src/test/java/com/marbleng/app/core/IpFamilyScannerTest.kt"),
    "familyLadderTest": read("app/src/test/java/com/marbleng/app/core/Ipv6FallbackLadderTest.kt"),
    "domainFaultTest": read("app/src/test/java/com/marbleng/app/core/DnsDomainFaultPolicyTest.kt"),
    "stormGuardTest": read("app/src/test/java/com/marbleng/app/core/DnsStormGuardV196Test.kt"),
    "familyScanDoc": read("docs/IP_FAMILY_SCAN_V196.md"),
    "ipv6Test": read("app/src/test/java/com/marbleng/app/core/Ipv6LeakHardeningTest.kt"),
    "readme": read("README.md"),
    "addressFamily": read("app/src/main/java/com/marbleng/app/core/AddressFamilyPolicy.kt"),
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
    "atelier": read("app/src/main/java/com/marbleng/app/ui/MarbleHomeAtelier.kt"),
    "studio": read("app/src/main/java/com/marbleng/app/ui/MarbleHomeStudio.kt"),
    "adaptation": read("app/src/main/java/com/marbleng/app/core/TransportAdaptation.kt"),
    # MARBLE_SERVER_TILE_LAYOUT_V208 — the compact server grid, its policy and its test.
    "serverTiles": read("app/src/main/java/com/marbleng/app/ui/MarbleServerTiles.kt"),
    "serverTilesTest": read("app/src/test/java/com/marbleng/app/ui/ServerTileLayoutV208Test.kt"),
    # MARBLE_FRAGMENT_PROFILES_V208 — the rewritten Fragment & Mux stack: the wire policy, the
    # profile ladder and the tests that pin both.
    "singBoxWirePolicy": read("app/src/main/java/com/marbleng/app/core/SingBoxTransportPolicy.kt"),
    "fragmentProfileTest": read(
        "app/src/test/java/com/marbleng/app/core/FragmentProfileLadderV208Test.kt"
    ),
    "singBoxWireTest": read(
        "app/src/test/java/com/marbleng/app/core/SingBoxTransportPolicyV208Test.kt"
    ),
    "copyTest": read("app/src/test/java/com/marbleng/app/ui/MarbleCopyV208Test.kt"),
    "fragmentProfileDoc": read("docs/FRAGMENT_PROFILES_V208.md"),
    "serverTileDoc": read("docs/SERVER_TILE_LAYOUT_V208.md"),
    "homeOnePingDoc": read("docs/HOME_ONE_PING_V208.md"),
    "settingsCopyDoc": read("docs/SETTINGS_ONE_LINE_COPY_V208.md"),
    "protocolIdentity": read("app/src/main/java/com/marbleng/app/ui/MarbleProtocolIdentity.kt"),
    # MARBLE_ROUTE_ATELIER_V207 — the design grammar and the presentation built against it. They are
    # required files now: a build that drops either one turns Home's default presentation into a page
    # that the dispatcher cannot render, so `read()` failing here is the correct loud answer.
    "designContract": read("app/src/main/java/com/marbleng/app/ui/MarbleDesignContract.kt"),
    "homeAtelier": read("app/src/main/java/com/marbleng/app/ui/MarbleHomeAtelier.kt"),
    "routeAtelierTest": read("app/src/test/java/com/marbleng/app/ui/MarbleDesignContractV207Test.kt"),
    "homeStyleTest": read("app/src/test/java/com/marbleng/app/ui/MarbleHomeStyleTest.kt"),
    "routeAtelierDoc": read("docs/ROUTE_ATELIER_V207.md"),
    "strings": read("app/src/main/java/com/marbleng/app/ui/MarbleStrings.kt"),
    # MARBLE_PING_SPEED_DIAL_V199 — the Persian face of the dial, checked like every other file.
    "persianLexicon": read("app/src/main/java/com/marbleng/app/ui/MarblePersianLexicon.kt"),
    # MARBLE_DOCK_SLOT_V167 — the fourth tab's model test and the chapter that explains it.
    "dockSlotTest": read("app/src/test/java/com/marbleng/app/model/DockSlotV167Test.kt"),
    "dockSlotDoc": read("docs/DOCK_SLOT_AND_COMPACT_BANNER_V167.md"),
    # MARBLE_SOCKET_FLIGHT_V168 — the single authority for physical-socket tuning on both
    # engines, the pure HEV YAML policy, their joint regression test and the chapter.
    "socketPolicy": read("app/src/main/java/com/marbleng/app/core/CoreSocketPolicy.kt"),
    "hevPolicy": read("app/src/main/java/com/marbleng/app/core/HevTunnelPolicy.kt"),
    "socketFlightTest": read("app/src/test/java/com/marbleng/app/core/SocketFlightV168Test.kt"),
    "socketFlightDoc": read("docs/SOCKET_FLIGHT_V168.md"),
    "marbleApp": read("app/src/main/java/com/marbleng/app/ui/MarbleApp.kt"),
    "homeStudio": read("app/src/main/java/com/marbleng/app/ui/MarbleHomeStudio.kt"),
    "connectPlacement": read("app/src/main/java/com/marbleng/app/ui/MarbleConnectPlacement.kt"),
    # MARBLE_EXPRESSIVE_MOTION_V186 — the motion chapter: its library, the shared motion clock it
    # extends, the pure-math test that pins the wave/stagger/depth contracts, and the chapter.
    "expressive": read("app/src/main/java/com/marbleng/app/ui/MarbleExpressive.kt"),
    "motion": read("app/src/main/java/com/marbleng/app/ui/MarbleMotion.kt"),
    "expressiveTest": read("app/src/test/java/com/marbleng/app/ui/MarbleExpressiveMathV186Test.kt"),
    "expressiveDoc": read("docs/EXPRESSIVE_MOTION_V186.md"),
    # MARBLE_SERVERS_HIERARCHY_V189 — the Servers page's two levels: the pure size table and plan
    # arithmetic, the test that pins both, and the chapter that explains the nesting.
    "serversHierarchy": read("app/src/main/java/com/marbleng/app/ui/MarbleServersHierarchy.kt"),
    "serversHierarchyTest": read(
        "app/src/test/java/com/marbleng/app/ui/ServersHierarchyV189Test.kt"
    ),
    "serversHierarchyDoc": read("docs/SERVERS_HIERARCHY_V189.md"),
    "tile": read("app/src/main/java/com/marbleng/app/quicktile/MarbleQuickTileService.kt"),
    "manifest": read("app/src/main/AndroidManifest.xml"),
    "security": read("app/src/main/res/xml/network_security_config.xml"),
    "native": read("scripts/prepare-native.sh"),
    "build": workflow("build.yml"),
    "gradle": read("app/build.gradle.kts"),
    "verify": workflow("verify.yml"),
    "updateCores": workflow("update-cores.yml"),
    # MARBLE_HIGH_JITTER_SHIELD_V206 — the second jitter instrument: the robust estimator, the
    # baseline-relative verdict and the cost-bounded measurement plan, plus the test that pins
    # every claim made about them and the chapter that explains the four defects of the first cut.
    "highJitterShield": read(
        "app/src/main/java/com/marbleng/app/core/HighJitterShield.kt"
    ),
    "highJitterShieldTest": read(
        "app/src/test/java/com/marbleng/app/core/HighJitterShieldTest.kt"
    ),
    "highJitterShieldDoc": read("docs/HIGH_JITTER_SHIELD_V206.md"),
    # MARBLE_FRAGMENT_MUX_PAGE_V206 — the dedicated Fragment & Mux page and its chapter.
    "fragmentMuxDoc": read("docs/FRAGMENT_MUX_PAGE_V206.md"),
    # MARBLE_NOTIFICATION_CHANNELS_ONCE_V206 — the notifier is a production source too: it was
    # rebuilt on every settings write, i.e. on every keystroke of every settings field.
    "notifier": read("app/src/main/java/com/marbleng/app/core/SmartNotifier.kt"),
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
    and all(step in files["permissions"] for step in ("VPN", "NOTIFICATIONS"))
    and "BATTERY" not in files["permissions"]
)
check(
    "font choices persist and reach the theme",
    "enum class AppFont" in files["models"]
    and "fontFamily" in files["store"]
    and "AppFont.entries" in files["ui"]
    and "fontId" in files["theme"],
)
check(
    "bottom dock has a stable single-surface contract",
    "MARBLE_STABLE_NAVIGATION_V202" in files["ui"]
    and "val dockSurface = chrome.surface" in files["ui"]
    and "glassFraction" not in files["ui"]
    and "pillWashAlpha" not in files["ui"],
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

# MARBLE_HOME_THEME_TWO_DEFAULT_V160 / MARBLE_ROUTE_ATELIER_V207 — every presentation is modelled and
# reachable, and the product default is named exactly once so the model, the store and the parser
# cannot disagree about it. V160 set that name to Theme 2 (Floating); V207 moved it to the route
# presentation, and the check moved with it — what it pins is the SHAPE of the rule (one name, read by
# the store and the parser, with a dispatcher behind it), not which theme happens to hold it today.
check(
    "the route presentation is modelled and is the product default",
    'ROUTE_ATELIER("route_atelier")' in files["models"]
    and "homeStyle: String = HomeStyle.DEFAULT.id" in files["models"]
    and "val DEFAULT: HomeStyle get() = ROUTE_ATELIER" in files["models"]
    and "HomeStyle.DEFAULT" in files["store"]
    and "HomeThemeAtelier(" in files["homeStyles"]
    and "HomeStyleSurface(" in files["ui"]
    and "HomeStyleSurface(" in files["homeStyles"],
)
# A default may be moved; a presentation may not be orphaned by the move. Theme 2 keeps its id, its
# implementation and its reachability, because an install that chose it must still find it.
check(
    "the former default presentation is still modelled and reachable",
    'IOS_FLOATING("ios_floating")' in files["models"]
    and "HomeThemeFloating(" in files["homeStyles"]
    and "HomeStyle.IOS_FLOATING -> HomeThemeFloating(" in files["homeStyles"],
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
    "Persian UI has a complete fallback rather than an English leak",
    "return faFallback(text)" in files["persianLexicon"]
    and "private fun faTransliterate" in files["persianLexicon"]
    and 'languageEnglish = "انگلیسی"' in files["strings"]
    and "persianFallbackNeverLeaksAnUntranslatedLatinUiLabel" in read(
        "app/src/test/java/com/marbleng/app/ui/MarbleDesignSystemTest.kt"
    ),
)
check(
    "AMOLED navigation surface is transparent",
    "Color.Transparent.toArgb()" in files["theme"]
    and "isNavigationBarContrastEnforced=false" in files["theme"]
    and "@android:color/transparent" in read("app/src/main/res/values/styles.xml"),
)
check(
    "AMOLED page background leaves uncovered OLED pixels off",
    "void = Color.Black" in files["theme"]
    and "val DarkBgTop = Color.Black" in files["design"]
    and "val DarkBgBottom = Color.Black" in files["design"]
    and "return@Canvas" in files["design"],
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
    "full TUN refuses a disabled DNS hijack rather than forwarding plaintext port 53",
    "if (mode != MODE_PROXY && !app.repo.settings.dnsHijackEnabled)" in files["vpn"]
    and 'failBeforeTunnel("Full VPN requires DNS hijacking' in files["vpn"]
    and "val enableDnsHijack = mode == ConnectionMode.FULL_TUN && !settings.dnsHijackEnabled"
        in files["repo"]
    and "Required to start Full TUN without plaintext DNS" in files["ui"],
)
check(
    "endpoint bootstrap is encrypted local DoH and fails closed without a family-compatible peer",
    "https+local://${dnsHostLiteral(ip)}/dns-query" in files["hardener"]
    and "configuredBootstrapIps.filter { resolverFamilyAllowed(it) }" in files["hardener"]
    and "require(bootstrapIps.isNotEmpty())" in files["hardener"]
    and "dnsPlan.preference != IpFamilyPreference.IPV6_ONLY" in files["hardener"],
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
# MARBLE_SMOOTH_CLOCK_V193 / MARBLE_ROUTE_ATELIER_V207 — V193 bounded an endless name scroller to
# three passes, which fixed the frame cost and kept the usability defect: a row whose name moves is a
# row a user cannot compare, and several of them starting two seconds apart turned the Servers page
# into signage. V207 took the marquee off the list row entirely (two settled lines, ellipsis; the full
# name stays readable and copyable in the detail sheet). The invariant is the one the product actually
# promises now: no text scrolls inside a Servers row, and no unused scroller import is left behind.
check(
    "library long names settle instead of scrolling",
    "basicMarquee" not in files["ui"]
    and "maxLines = 2" in _fun_body(files["ui"], "private fun ServersNodeCard(")
    and "overflow = TextOverflow.Ellipsis" in _fun_body(files["ui"], "private fun ServersNodeCard("),
)
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
    # MARBLE_PING_SPEED_DIAL_V199 — the gap flows from the caller's speed dial now: the batch
    # call passes the dial-driven spacing whose DEFAULT is still the shipped
    # PingBudget.SAMPLE_SPACING_MS, so a caller without settings keeps the exact V160 pace.
    and "sampleSpacingMs: Long = PingBudget.SAMPLE_SPACING_MS" in files["probe"]
    and "spacingMs = sampleSpacingMs" in files["probe"]
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
# MARBLE_PING_SPEED_DIAL_V199 — every ping measurement got a speed dial.
#
#  - the shipped default (dial off) runs every sweep about 1.5× the V160 baseline — the
#    "about 50 % faster" promise for all three methods — delivered as sweep width and sample
#    pacing, never as a shorter timeout or fewer samples;
#  - the dial is one object (PingSpeed), one pair of settings, one store round-trip, one
#    Settings control, and the sweep-width policy is one pure function with its own test.
# ───────────────────────────────────────────────────────────────────────────────────────────────

ping_speed_dial_test = read(
    "app/src/test/java/com/marbleng/app/model/PingSpeedTest.kt"
)
sweep_width_test = read("app/src/test/java/com/marbleng/app/core/SweepWidthTest.kt")
check(
    "the speed dial is one policy object with one ruler for default and manual speed",
    "object PingSpeed" in files["models"]
    and "const val DEFAULT_FACTOR = 1.5" in files["models"]
    and "const val MIN_PERCENT = 50" in files["models"]
    and "const val MAX_PERCENT = 200" in files["models"]
    and "fun factor(custom: Boolean, dialPercent: Int): Double" in files["models"]
    and "val pingSpeedCustom: Boolean = false" in files["models"]
    and "val pingSpeedPercent: Int = PingSpeed.DEFAULT_PERCENT" in files["models"]
    and "fun AppSettings.pingSpeedFactor(): Double" in files["models"]
    # The default is the dial's answer, not a stored percent: dial off always reads 1.5.
    and "PingSpeed.factor(pingSpeedCustom, pingSpeedPercent)" in files["models"],
)
check(
    "the dial scales sweep width and pacing, never the accuracy budget",
    # Width: the direct sweep pool scales through the accessor the sweep constructs its
    # settings with, clamped through the same PingBudget concurrency as a raw value.
    "(pingConcurrency * pingSpeedFactor()).roundToInt()" in files["models"]
    and "fun AppSettings.pingWorkers(): Int" in files["models"]
    # Pacing: the quiet gap scales with a burst-proof floor and a stall-proof ceiling.
    and "fun AppSettings.pingSampleSpacingMs(): Long" in files["models"]
    and "coerceIn(20L, 300L)" in files["models"]
    # The accuracy contract is untouched by the dial: samples and timeout read the raw fields.
    and "fun AppSettings.pingTimeoutMs(): Int = PingBudget.timeoutSec(pingTimeoutSec) * 1_000" in files["models"]
    and "fun AppSettings.pingSampleCount(): Int = PingBudget.samples(pingSamples)" in files["models"]
    # The batch deadline describes the pacing the prober will really use.
    and "perServerBudgetMs(timeoutSec: Int, samples: Int, spacingMs: Long)" in files["models"],
)
check(
    "the sweep-width policy is one pure function, tested from every side",
    "fun sweepWidth(" in files["bench"]
    and "xray.singBox?.measurementCoreCeiling ?: SingBoxManager.MAX_TEMPORARY_CORES" in files["bench"]
    # The native-child ceiling stays a memory decision: the dial never buys a child past it.
    and "tcpWorkers.coerceIn(1, singBoxCeiling.coerceIn(1, 64))" in files["bench"]
    # The Xray Real-delay pool scales with the dial from its old 2..4 envelope.
    and "val factor = if (speedFactor.isFinite()) speedFactor else 1.0" in files["bench"]
    and "val scaled = (base * factor).roundToInt()" in files["bench"]
    and "scaled.coerceIn(2, xrayChildCeiling.coerceIn(2, 8))" in files["bench"]
    and "class SweepWidthTest" in sweep_width_test,
)
check(
    "the device sizes the Xray Real-delay pool exactly like the sing-box pool",
    "val measurementCoreCeiling: Int =" in files["xray"]
    and "MeasurementCoreBudget.BASE + MeasurementCoreBudget.read(context)" in files["xray"],
)
check(
    "the dial reaches every multi-sample measurement and the anti-probing stagger",
    "pauseBetweenSamples(settings.pingSampleSpacingMs())" in files["probe"]
    and "sampleSpacingMs = settings.pingSampleSpacingMs()" in files["probe"]
    and "sampleSpacingMs = probeSettings.pingSampleSpacingMs()" in files["repo"]
    and "ProbeTargetPool.staggerProbe(staggerScale)" in files["reachability"],
)
check(
    "the dial persists, renders, and translates",
    "pingSpeedCustom = prefs.getBoolean(\"pingSpeedCustom\", false)" in files["store"]
    and "PingSpeed.percent(prefs.getInt(\"pingSpeedPercent\", PingSpeed.DEFAULT_PERCENT))" in files["store"]
    and ".putBoolean(\"pingSpeedCustom\", s.pingSpeedCustom)" in files["store"]
    and ".putInt(\"pingSpeedPercent\", PingSpeed.percent(s.pingSpeedPercent))" in files["store"]
    and "AnimatedVisibility(s.pingSpeedCustom)" in files["ui"]
    and "onValueChangeFinished" in files["ui"]
    and "\"Ping speed\" to" in files["persianLexicon"]
    and "\"Custom speed\" to" in files["persianLexicon"],
)
check(
    "the speed dial is pinned by unit tests in both directions",
    "dialOffIsAlwaysTheShippedFasterDefault" in ping_speed_dial_test
    and "theDialNeverTouchesTheAccuracyBudget" in ping_speed_dial_test
    and "theQuietGapNeverBecomesABurstOrAStall" in ping_speed_dial_test
    and "nativeChildMeasurementsStayUnderTheDeviceCeiling" in sweep_width_test
    and "theXrayRealDelayPoolScalesWithTheDial" in sweep_width_test
    and "noInputEscapesTheLegalRanges" in sweep_width_test,
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
# MARBLE_PROTOCOL_IDENTITY — the Home server row dropped its own slab for the shared
# ServerPingStat (MarbleProtocolIdentity.kt): the same tone-only measurement rule now lives in
# one component both lists read, so the invariant is pinned there — the readout must never grow
# a tinted fill back, and the Home row must actually compose it.
check(
    "the Home latency readout is tone-only",
    ".background(tone.copy(alpha" not in _fun_body(files["protocolIdentity"], "fun ServerPingStat(")
    and "ServerPingStat(" in files["homeStyles"],
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

# MARBLE_APK_INSTALL_CONTRACT_V195 — "App not installed" the instant Install is tapped is the
# package installer rejecting the native payload, not a slow failure.  Two properties decide it:
# every staged library must be 16 KB ELF aligned (Android 15+ 16 KB-page devices refuse to map,
# and the installer aborts on, 4 KB aligned PT_LOAD segments), and the installer must extract the
# compressed native entries (extractNativeLibs).  The Go cores are not NDK-built, so their
# alignment only exists if CGO_LDFLAGS pins it at every go build; the extraction contract only
# holds if useLegacyPackaging stays true and the merged manifest is normalized, because AGP 9
# forbids android:extractNativeLibs in the source manifest and defaults it to "false" for
# minSdk >= 23.  The cores are also executed from nativeLibraryDir, so a "false" flip is fatal
# both at install time and at runtime.
check(
    "native payload is 16 KB page aligned end to end",
    files["native"].count("max-page-size=16384") >= 5  # 4 go builds + the assertion copy
    and "assert_elf_page_alignment" in files["native"]
    and "APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true" in read("app/src/main/jni/Application.mk")
    and "max-page-size=16384" in read("app/src/main/jni/Android.mk"),
)
check(
    "installer extraction contract is pinned and normalized",
    "useLegacyPackaging = true" in files["gradle"]
    and "marbleNormalizeExtractNativeLibs" in files["gradle"]
    and "android:extractNativeLibs=" in files["gradle"]
    and "MARBLE_APK_INSTALL_CONTRACT_V195" in files["gradle"],
)
check(
    "core binaries ship without AGP stripping",
    '"**/libxray.so"' in files["gradle"]
    and '"**/libmarbleng.so"' in files["gradle"]
    and '"**/libsingbox.so"' in files["gradle"],
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
    "TlsPinningPolicy.sanitizeConfigDocument(src)" in files["hardener"]
    and "fun hardenForDelayTest(" in files["hardener"]
    and "String = harden(source, socksPort, settings, link, underlayHasIpv6)" in files["hardener"]
    and "JSONObject(harden(source, 19091, settings, underlayHasIpv6 = underlayHasIpv6))"
        in files["hardener"],
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

# MARBLE_SINGBOX_PINNED_PEER_V163 / MARBLE_SINGBOX_PINNED_COMPAT_V164 — a pcs/vcn
# (pinnedPeerCertSha256 / verifyPeerCertByName) profile was originally refused for sing-box
# extended before the fork's parser could accept the link and drop the pin (V163). Now (V164)
# pinned configs are allowed through the translated path with `tls.insecure: true`, and the
# parser candidate is excluded for pinned configs since it silently ignores pcs/vcn.
check(
    "a certificate-pinning profile is translated with insecure for sing-box, not silently dropped",
    "fun pinnedPeerRefusal(profile: ProxyProfile): String?" in files["singBoxBuilder"]
    and "internal fun linkCarriesPin(link: String): Boolean" in files["singBoxBuilder"]
    and "MARBLE_SINGBOX_PINNED_COMPAT_V164" in files["singBoxBuilder"]
    and "if (isPinned || xhttpLink || realityLink) null" in files["singBoxBuilder"]
    and "tls-pinning" in files["singBoxTransport"]
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
# ══════════════════════════════════════════════════════════════════════════════
# MARBLE_CORE_CONFIG_SUPERSET_V165 — one authority decides whether a config is
# usable, and it agrees with the pinned core instead of with a copy of it.
#
# The reported outage was three independent vetoes over one fact: a VLESS node
# without TLS. The VPN preflight refused it (a hand-written copy of Xray's rule
# plus a shorter private-address list), the rank gate hid it as
# "censorship-unsafe", and the core itself was refusing it for the reason the
# first two were imitating. All three are now the same code, and the core's rule
# is delegated to the application by one patched function — which is only
# acceptable because the app asks for the same consent the patch waives.
# ══════════════════════════════════════════════════════════════════════════════
check(
    "plaintext consent is one name, shared by the manager, the patch and the docs",
    'const val PLAINTEXT_POLICY_ENV = "MARBLE_ALLOW_UNENCRYPTED_PUBLIC_OUTBOUND"' in files["configSuperset"]
    and "environment()[CoreConfigSuperset.PLAINTEXT_POLICY_ENV] = \"1\"" in files["xray"]
    and 'marblePlaintextOutboundEnv = "MARBLE_ALLOW_UNENCRYPTED_PUBLIC_OUTBOUND"' in files["supersetPolicy"]
    and "MARBLE_ALLOW_UNENCRYPTED_PUBLIC_OUTBOUND" in files["supersetDoc"],
)

check(
    "the core patch is one early return, in the function that owns the rule",
    "func requiresTransportSecurity(address *Address) bool {" in files["supersetInjector"]
    and "marbleAllowsUnencryptedOutbound() {" in files["supersetInjector"]
    and "func marbleAllowsUnencryptedOutbound() bool" in files["supersetPolicy"]
    and "PrintRemovedFeatureError" not in files["supersetPolicy"]
    and "GetPrivateIPMatcher" not in files["supersetPolicy"],
)

check(
    "the pinned-source CI smoke proves the patch and runs its Go tests",
    'python3 "$ROOT/scripts/inject-xray-config-superset.py" "$XRAY_SRC"' in files["native"]
    and "marble_outbound_policy_test.go" in files["native"]
    and "MarbleNG plaintext-outbound consent hook missing from infra/conf/xray.go" in files["native"]
    and files["verify"].count("inject-xray-config-superset.py") >= 1
    and "go test -run 'TestMarble' ./infra/conf" in files["verify"]
    and "TestMarblePlaintextOutboundConsentOverridesPublicAddress" in files["supersetPolicyTest"]
    and "TestMarblePlaintextOutboundConsentParsing" in files["supersetPolicyTest"],
)

check(
    "no app-level VLESS veto survives: preflight, rank and connect ask one authority",
    "private fun isPrivateEndpointHost(" not in files["vpn"]
    and "fun verdict(profile: ProxyProfile, engine: CoreEngine, settings: AppSettings): Verdict"
    in files["configSuperset"]
    and "CoreConfigSuperset.verdict(profile, engine, settings)" in files["vpn"]
    and "CoreConfigSuperset.verdict(profile, engine, settings)" in files["preflight"]
    and "CoreConfigSuperset.verdict(profile, engine, settings)" in files["auditor"]
    and 'if (security == "none"' not in files["vpn"]
    and "verdict.runnable" in files["preflight"]
    and "verdict.note.isNotEmpty()" in files["auditor"],
)

check(
    "the refusal the users and translators know is still the refusal, and only consent reaches it",
    'const val PLAINTEXT_REFUSAL = "Unsupported VLESS • pick a server with TLS/REALITY"'
    in files["configSuperset"]
    and "CoreConfigSuperset.PLAINTEXT_REFUSAL" in files["vpn"]
    and '"Unsupported VLESS • pick a server with TLS/REALITY"' in files["persianLexicon"]
    and "Unsupported VLESS • pick a server with TLS/REALITY" in files["ui"],
)

check(
    "a cleartext node is labelled, and consent is a stored setting with a UI control",
    "val allowUnencryptedPublicOutbound: Boolean = true" in files["models"]
    and 'putBoolean("allowUnencryptedPublicOutbound", s.allowUnencryptedPublicOutbound)' in files["store"]
    and 'getBoolean("allowUnencryptedPublicOutbound", true)' in files["store"]
    and "checked = s.allowUnencryptedPublicOutbound" in files["ui"]
    and "PLAINTEXT_PUBLIC_NOTE" in files["configSuperset"]
    and "Dial unencrypted nodes" in files["persianLexicon"],
)

check(
    "the config repair pass is lossless and runs before every consumer of a config",
    'internal const val REMOVED_CHAIN_FIELD = "proxySettings"' in files["configRepairs"]
    and "fun apply(source: String): Report" in files["configRepairs"]
    and "return Report(source, emptyList())" in files["configRepairs"]
    and files["hardener"].count("XrayConfigRepairs.apply(") >= 2
    and "val root = JSONObject(harden(source, 19091, settings, underlayHasIpv6 = underlayHasIpv6))"
        in files["hardener"]
    and "String = harden(source, socksPort, settings, link, underlayHasIpv6)" in files["hardener"]
    and "sockopt.put(\"dialerProxy\", segments[index - 1].primaryTag)" in files["hardener"]
    and '.put(\n                "proxySettings",\n' not in files["hardener"]
    and "transportLayer\", true)" not in files["hardener"]
    and "NativeSingBoxConfig.isNative(root)) return Report(source, emptyList())" in files["configRepairs"],
)

check(
    "Marble never manufactures transport security for a node that did not ask for it",
    'put("security", "tls")' not in files["configRepairs"]
    and 'put("security", "reality")' not in files["configRepairs"]
    and "security-inferred-from-" in files["configRepairs"]
    and "repairs.isEmpty()) return Report(source, emptyList())" in files["configRepairs"],
)

check(
    "share links are read by one alias-aware, Uri-free reader",
    "private fun params(uri: Uri): ShareLinkParams" in files["parser"]
    and files["parser"].count("getQueryParameter(") == 0
    and "fun ofRawLink(raw: String): ShareLinkParams" in files["linkParams"]
    and "fun first(vararg keys: String, default: String = \"\")" in files["linkParams"]
    and "XHTTP_LINK_FIELDS" in files["parser"]
    and '"serviceName", "service"' in files["parser"]
    and "mlkem768x25519plus" in files["parser"],
)

check(
    "rendered IPv6 XHTTP REALITY links keep their wire fields on both engines and both ping paths",
    "ShareLinkNormalizer.normalize(match.value)" in files["parser"]
    and "ShareLinkNormalizer.normalize(raw)" in files["singBoxBuilder"]
    and "ShareLinkNormalizer.normalize(raw)" in files["linkParams"]
    and "XhttpExtra.parse(it)" in files["parser"]
    and "XhttpExtra.parse(raw)" in files["singBoxTransport"]
    and "if (isPinned || xhttpLink || realityLink) null" in files["singBoxBuilder"]
    and "xhttpLink -> listOfNotNull(fromLink, stored)" in files["singBoxBuilder"]
    and "LinkWireParity.matches(profile, derived)" in files["repo"]
    and "AddressFamilyPolicy.excludedIpv6Endpoint(profile.host, settings)" in files["vpn"]
    and 'failureReason = "ipv6-disabled"' in files["probe"]
    and "!AddressFamilyPolicy.isLiteralIp(host)" in files["probe"]
    and "class VlessXhttpRealityIpv6Test" in files["vlessIpv6Test"]
    and "base64ExtraAndTopLevelTuningArePreservedNotSilentlyOverwritten" in files["vlessIpv6Test"]
    and "IPv6-only endpoint" in files["vlessIpv6Doc"],
)

# MARBLE_REALITY_MLKEM_HANDSHAKE_V194 — a VLESS/REALITY node that connects in v2rayNG failed
# 55/55 here with `reality verification failed`, deterministically, on every destination. The
# keys were innocent (they travel verbatim; no base64 decode of `pbk` exists on that path): the
# fault is the handshake shape. Xray >= v26.9.8 REALITY servers require the X25519MLKEM768 key
# share, which the pinned sing-box core strips unless its reality block sets
# `support_x25519mlkem768` — and neither its link parser nor MarbleNG's translator used to set
# it. The translator now emits the flag (safe for older servers, which scan X25519 first and
# tolerate the extra share), REALITY links skip the core parser that cannot emit it, and the
# residual rejections are classified with the actual remedy instead of being retried blindly.
check(
    "translated REALITY carries the hybrid key share current servers require, and REALITY links skip the core parser that cannot emit it",
    '.put("support_x25519mlkem768", true)' in files["singBoxTransport"]
    and "internal fun linkCarriesReality(link: String): Boolean" in files["singBoxBuilder"]
    and "if (isPinned || xhttpLink || realityLink) null" in files["singBoxBuilder"]
    and "object RealityHandshakePolicy" in files["realityPolicy"]
    and 'const val SINGBOX_MARKER = "reality verification failed"' in files["realityPolicy"]
    and "RealityHandshakePolicy.check(RealityHandshakePolicy.scan(coreEvidence), active)" in files["bugFinder"]
    and "class RealityMlkemHandshakeTest" in files["realityMlkemTest"]
    and "urlSafeKeyAlphabetSurvivesImportByteIdentical" in files["realityMlkemTest"]
    and "singboxTranslationEmitsMlkemFlagWithVerbatimKeys" in files["realityMlkemTest"]
    and "realityLinksSkipTheCoreParserOnBothTransports" in files["realityMlkemTest"]
    and "X25519MLKEM768" in files["realityMlkemDoc"],
)

check(
    "a repeated config refusal costs one event, and never a skipped reconnect",
    "configBlockGuard.observe(profile.id, issue)" in files["vpn"]
    and "private fun failBeforeTunnel(reason: String, loud: Boolean = true)" in files["vpn"]
    and "const val MAX_QUIET_MS = 600_000L" in files["blockGuard"]
    and "Pure with respect to the caller's control flow" in files["blockGuard"]
    and "class ConfigBlockGuard(\n    private val now: () -> Long" in files["blockGuard"],
)

check(
    "the second core is never judged by the first core's shape rules",
    "engine == CoreEngine.SINGBOX && profile.raw.isNotBlank()" in files["preflight"]
    and "singbox-parser-link" in files["preflight"]
    and '"core-gap"' in files["preflight"]
    and "CORE_GAP_TRANSPORT_REMOVED" in files["configSuperset"]
    and "CORE_GAP_KCP_CAMOUFLAGE" in files["configSuperset"]
    and "CORE_GAP_KCP_CAMOUFLAGE" in files["persianLexicon"],
)

check(
    "V165 behaviour is covered by named tests and a chapter that explains the boundary",
    "class CoreConfigSupersetV165Test" in files["supersetTest"]
    and "class XrayConfigRepairsV165Test" in files["repairsTest"]
    and "class ShareLinkParamsV165Test" in files["linkParamsTest"]
    and "class ConfigBlockGuardV165Test" in files["blockGuardTest"]
    and "MARBLE_CORE_CONFIG_SUPERSET_V165" in files["supersetDoc"]
    and "MARBLE_CORE_CONFIG_SUPERSET_V165" in files["interopDoc"]
    and "requiresTransportSecurity" in files["supersetDoc"]
    and "CoreConfigSuperset" in files["interopDoc"],
)

# MARBLE_HOME_STATUS_REFRAME_V187 — the fourth dock slot is user-owned, and one shared status
# card serves all four Home presentations with a compact route row plus optional live throughput.
# MARBLE_HOME_CLOUD_DEPTH_V191 — the flat-plane era of that card ended: the container is shared
# exactly as before, but it now carries the product depth contract (one cool brand shadow, a
# gradient rim, a whisper wash) instead of V187's total shadow ban. The invariant pins the lift.
_banner_body = _fun_body(files["homeStyles"], "internal fun IosStatusWideCard(")
_theme1_body = _fun_body(files["homeStyles"], "internal fun HomeThemeSlider(")
check(
    "the Home status card is shared, lifted on one cool shadow and telemetry stays in its route hierarchy",
    files["homeStyles"].count("IosStatusWideCard(") == 5
    and "MARBLE_HOME_STATUS_REFRAME_V187" in files["homeStyles"]
    and "HomeCloudCard(modifier = modifier.fillMaxWidth(), shape = shape)" in _banner_body
    # MARBLE_HOME_CLOUD_DEPTH_V191 — 0.dp is gone for good; the pin is the lift itself.
    and "val CardElevation = 3.dp" in files["design"]
    and "val SelectedElevation = 7.dp" in files["design"]
    and "MARBLE_HOME_CLOUD_DEPTH_V191" in files["design"]
    and "homeStatusText(evidence)" in _banner_body
    # MARBLE_SESSION_USAGE_V192 — the telemetry row now answers to EITHER display choice: the
    # live rates (speed widget) and/or the session's data total, and the last session's usage
    # keeps the status row honest while disconnected.
    and "visible = evidence.connected && (evidence.showSpeedWidget || evidence.showDataUsage)" in _banner_body
    and _banner_body.count("HomeConnectionMetric(") == 3
    and "glyph = HomeGlyph.DOWNLOAD" in _banner_body
    and "glyph = HomeGlyph.UPLOAD" in _banner_body
    and "glyph = HomeGlyph.DATA" in _banner_body
    and "evidence.lastSessionBytes" in _banner_body
    and "clickable { actions.onIpDetails() }" in _banner_body
    and "HomeIpRow(" not in _banner_body
    and ".weight(1f)" in _theme1_body
    and "IosSlideToConnect(evidence, actions" in _theme1_body,
)
check(
    "the fourth dock slot is a preference, not a fixture",
    "enum class DockSlotKind" in files["models"]
    and "fun <T> dockSlots(" in files["models"]
    and "fun <T> dockSlotIndex(" in files["models"]
    and "SpatialTab.CUSTOM" in files["ui"]
    and "dockSlots(SpatialTab.entries, SpatialTab.CUSTOM, repo.settings.dockSlotEnabled)" in files["ui"]
    and "when (tabs.getOrNull(pageIndex) ?: SpatialTab.DECK)" in files["ui"]
    and "if (item == SpatialTab.CUSTOM && !slot.enabled) return@forEach" in files["ui"],
)
check(
    "the fourth dock slot is personalized from Settings and persisted",
    all(
        field in files["models"]
        for field in (
            "dockSlotEnabled",
            "dockSlotKind",
            "dockSlotSourceId",
            "dockSlotLabel",
            "dockSlotIcon",
        )
    )
    and "parseDockSlotKind" in files["store"]
    and "dockSlotLabel" in files["store"]
    and "const val DOCK_SLOT" in files["ui"]
    and "SettingsDockSlotPage(" in files["ui"]
    and "SettingsDockSlotPreview(" in files["ui"]
    and "DockSlotIconChoice(" in files["ui"]
    and "dockSlotCaptionText(" in files["ui"],
)
check(
    "V167 behaviour is covered by named tests and a chapter",
    "class DockSlotV167Test" in files["dockSlotTest"]
    and "MARBLE_DOCK_SLOT_V167" in files["dockSlotDoc"]
    and "MARBLE_HOME_COMPACT_BANNER_V167" in files["dockSlotDoc"]
    and "dockSlotIndex" in files["dockSlotDoc"]
    and "Fourth tab" in files["dockSlotDoc"],
)

# MARBLE_SOCKET_FLIGHT_V168 — one authority owns the tuning of every socket that crosses the
# physical network. Xray and sing-box used to tune different sockets and forget others, and HEV
# paid an extra handshake round trip per app connection. These invariants keep the two writers,
# the TUN config and the pinned-core-verified option names in lockstep.
check(
    "V168 physical-socket policy exists and is shared by both engines",
    "object CoreSocketPolicy" in files["socketPolicy"]
    and "MARBLE_SOCKET_FLIGHT_V168" in files["socketPolicy"]
    and "fun writeXrayPhysicalTcpSockopt(" in files["socketPolicy"]
    and "fun writeSingBoxPhysicalDial(" in files["socketPolicy"]
    and "CoreSocketPolicy.writeXrayPhysicalTcpSockopt" in files["hardener"]
    and "CoreSocketPolicy.applyDefaultUtlsFingerprint" in files["hardener"]
    and "CoreSocketPolicy.writeSingBoxPhysicalDial" in files["singBoxBuilder"]
    and "class SocketFlightV168Test" in files["socketFlightTest"],
)
check(
    "V168 Xray offers MPTCP and BBR on the socket that actually dials",
    '"tcpMptcp", true' in files["socketPolicy"]
    and '"tcpCongestion", "bbr"' in files["socketPolicy"]
    # The generated terminal fragment dialer is built with the physical-socket profile.
    and "CoreSocketPolicy.writeXrayPhysicalTcpSockopt(" in files["hardener"]
    # tcpKeepAliveCount is not a real Xray sockopt field; it must never be emitted by either writer.
    and "tcpKeepAliveCount" not in files["hardener"]
    and "tcpKeepAliveCount" not in files["socketPolicy"],
)
check(
    "V168 sing-box terminal hops carry the full dial flight, chained hops carry only detour",
    '"tcp_multi_path", true' in files["socketPolicy"]
    and '"udp_fragment", true' in files["socketPolicy"]
    and '"tcp_keep_alive"' in files["socketPolicy"]
    and '"tcp_keep_alive_interval"' in files["socketPolicy"]
    and "fun directOutbound(" in files["singBoxBuilder"]
    # The Android-CLI-banned graphical dial fields must never be reintroduced by this policy.
    and "network_strategy" not in files["socketPolicy"]
    and "network_type" not in files["socketPolicy"]
    and "bind_interface" not in files["socketPolicy"],
)
check(
    "V168 HEV YAML is built purely, with pipelining, a fragment-safe connect budget and a wider UDP pool",
    "object HevTunnelPolicy" in files["hevPolicy"]
    and "pipeline: true" in files["hevPolicy"]
    and "tcp-fastopen" in files["hevPolicy"]
    and "connect-timeout" in files["hevPolicy"]
    and "udp-copy-buffer-nums" in files["hevPolicy"]
    and "HevTunnelPolicy.buildConfig(" in files["vpn"]
    # The inline YAML builder inside the service must be gone: one writer, one test target.
    and 'add("socks5:")' not in files["vpn"],
)
# Google retired the legacy 'tools' SDK package (2026-09) while setup-android@v4 defaults its
# packages input to 'tools platform-tools'; every run then died at 'Set up Android SDK' before
# any repo code was compiled. The App token cannot write .github/workflows/, so — per the
# docs/workflows-pending convention — the fix ships in the staged complete copies that
# workflow(name) prefers. Every v4 call site in a staged file must name surviving packages.
def _setup_android_pins_packages(wf: str) -> bool:
    return (
        wf.count("uses: android-actions/setup-android@v4") >= 1
        and wf.count("uses: android-actions/setup-android@v4")
        == wf.count('packages: "platform-tools"')
    )

check(
    "V168 CI: staged workflows never rely on the retired default 'tools' SDK package",
    _setup_android_pins_packages(workflow("verify.yml"))
    and _setup_android_pins_packages(workflow("build.yml"))
    and all(
        "uses: android-actions/setup-android@v4" in f.read_text(encoding="utf-8")
        for f in [ROOT / ".github/workflows/verify.yml", ROOT / ".github/workflows/build.yml"]
    ),
)
check(
    "V168 behaviour is pinned by named tests and explained by a chapter",
    "class SocketFlightV168Test" in files["socketFlightTest"]
    and "MARBLE_SOCKET_FLIGHT_V168" in files["socketFlightTest"]
    and "MARBLE_SOCKET_FLIGHT_V168" in files["socketFlightDoc"]
    and "pipeline" in files["socketFlightDoc"]
    and "tcp_multi_path" in files["socketFlightDoc"]
    and "udp_fragment" in files["socketFlightDoc"],
)

# MARBLE_RESERVED_TAG_COLLISION_V183 — `existing tag found: block` (exit code 23). The hardener
# appends its own block/direct/dns-out/fragment outbounds, so an imported document that already
# names one of them must be renamed before the graph is read, and a load refusal must be reported
# as a document fault rather than as "Kill switch active".
check(
    "V183 imported outbounds can never collide with the tags the hardener emits",
    "renameReservedImportedTags(old)" in files["hardener"]
    and 'internal val RESERVED_OUTBOUND_TAGS: Set<String> = setOf(' in files["hardener"]
    and '"block", "direct", "dns-out", "fragment-direct", "tls-fragment"' in files["hardener"]
    and 'internal fun importedAliasFor(tag: String): String = "import-$tag"' in files["hardener"]
    and "if (tag in byTag) {" in files["hardener"]
    and 'private val infra = setOf("freedom", "direct", "blackhole", "block", "dns", "loopback")' in files["hardener"]
    and "canonicalizeProtocolAlias(outbound, repairs)" in files["configRepairs"]
    and '"direct" to "freedom"' in files["configRepairs"]
    and '"block" to "blackhole"' in files["configRepairs"],
)
check(
    "V183 an Xray load refusal is classified and named in every reader",
    "object XrayStartFailure" in files["xrayStartFailure"]
    and "DUPLICATE_OUTBOUND_TAG" in files["xrayStartFailure"]
    and 'if (!line.contains("ok=false")) return ""' in files["xrayStartFailure"]
    and 'XrayStartFailure.classify(xray.lastStartError, allRuntime)' in files["bug"]
    and '"Xray core start-up"' in files["bug"]
    and '"PROCESS EXIT HISTORY"' in files["bug"]
    and '"ANDROID PROCESS EXIT HISTORY"' not in files["bug"]
    and 'XrayStartFailure.faultClass(coreStartError, "Core/configuration error")' in files["vpn"]
    and "internal fun summarizeStartFailure(lines: List<String>): String" in files["xray"]
    and "class ReservedTagCollisionV183Test" in files["reservedTagTest"]
    and "existing tag found: block" in files["reservedTagTest"]
    and "MARBLE_RESERVED_TAG_COLLISION_V183" in files["reservedTagDoc"],
)

# MARBLE_EXPRESSIVE_MOTION_V186 — the motion chapter is a library, an adoption and a contract.
# The library holds the Material 3 Expressive tokens (emphasized curves, duration ladder,
# stagger step), the physical specs (release/pop/settle springs, entrance and roll pieces) and
# the pure wave math; the product UI must actually run on it — wavy indicators, arrival
# cascades, page depth, rolling readouts and the expressive transitions; the pure math is
# pinned by a named test class; and the chapter document explains the boundary. A design
# chapter that stops being used is drift, and drift is what this checker catches.
check(
    "V186 expressive motion library holds its tokens, specs and pure math",
    "object MarbleExpressiveMotion" in files["expressive"]
    and "object MarbleExpressiveSpecs" in files["expressive"]
    and "object ExpressiveMath" in files["expressive"]
    and "val EmphasizedDecelerate" in files["expressive"]
    and "val EmphasizedAccelerate" in files["expressive"]
    and "const val StaggerStepMs" in files["expressive"]
    and "fun staggerDelayMs(" in files["expressive"]
    and "fun arcSweep(" in files["expressive"]
    and "STAGGER_MAX_INDEX" in files["expressive"]
    and "SpringReleaseFloat" in files["expressive"]
    and "WaveSpringFloat" in files["expressive"],
)
check(
    "V186 expressive components, modifiers and transitions exist",
    "fun MarbleExpressiveCircularIndicator(" in files["expressive"]
    and "fun MarbleExpressiveLinearIndicator(" in files["expressive"]
    and "fun MarbleExpressiveValueText(" in files["expressive"]
    and "fun MarbleExpressiveGlyphSwap(" in files["expressive"]
    and "fun Modifier.marbleStaggerIn(" in files["expressive"]
    and "fun Modifier.marbleSpringIn(" in files["expressive"]
    and "fun Modifier.marblePopWhen(" in files["expressive"]
    and "fun Modifier.marblePageDepth(" in files["expressive"]
    and "fun Modifier.expressiveClickable(" in files["expressive"]
    and "fun rememberMarbleEntranceWindow(" in files["expressive"]
    and "fun rememberExpressiveMorphShape(" in files["expressive"]
    and "expressiveContainerTransform(" in files["expressive"]
    and "expressiveSharedAxisX(" in files["expressive"]
    and "fun expressiveFadeThrough(" in files["expressive"],
)
check(
    "expressive motion is active without unstable full-page transforms",
    "MarbleExpressiveCircularIndicator(" in files["ui"]
    and "MarbleExpressiveLinearIndicator(" in files["ui"]
    and "MarbleExpressiveValueText(" in files["ui"]
    and "marbleStaggerIn(" in files["ui"]
    and "expressiveFadeThrough(" in files["ui"]
    and "MARBLE_STABLE_NAVIGATION_V202" in files["ui"]
    and "rememberMarbleEntranceWindow(" in files["ui"]
    and "marbleStaggerIn(" in files["homeStyles"]
    and "ExpressiveMath.arcSweep(" in files["homeStyles"]
    and "MarbleExpressiveGlyphSwap(" in files["homeStyles"]
    and "rememberMarbleEntranceWindow(" in files["homeStyles"]
    and "MarbleExpressiveValueText(" in files["homeStudio"]
    and "marblePopWhen(" in files["homeStudio"]
    and "ExpressiveMath." in files["homeStudio"]
    and "marbleStaggerIn(" in files["permissions"]
    and "rememberMarbleEntranceWindow(" in files["permissions"]
    and "releaseSpec" in files["motion"]
    and "MarbleExpressiveShapes" in files["theme"]
    and "MARBLE_EXPRESSIVE_MOTION_V186" in files["design"],
)

check(
    "V186 the elastic surface keeps the fixed-slot and determinism contracts",
    # The V135 reveal stays opacity-only: the meter column keeps its reserved 150 dp slot.
    ".width(150.dp)" in files["homeStudio"]
    and "graphicsLayer { alpha = presence }" in files["homeStudio"]
    # The slide controls keep their committed thresholds and park behavior (V146).
    and "val threshold = .78f" in files["homeStyles"]
    and "maxDragPx * 0.65f" in files["homeStyles"]
    and "MARBLE_SLIDE_PARK_V146" in files["homeStyles"]
    # The floating shutter keeps its pinned geometry.
    and "fun ConnectButtonFloating(" in files["homeStudio"]
    and ".size(76.dp)" in files["homeStudio"],
)
check(
    "V186 expressive math is pinned by a named test and explained by a chapter",
    "class MarbleExpressiveMathV186Test" in files["expressiveTest"]
    and "staggerDelayMs" in files["expressiveTest"]
    and "arcSweep" in files["expressiveTest"]
    and "MARBLE_EXPRESSIVE_MOTION_V186" in files["expressiveDoc"]
    and "MARBLE_EXPRESSIVE_MOTION_V186" in files["expressive"],
)

# MARBLE_SERVERS_HIERARCHY_V189 — the Servers page is two levels now, and the difference between
# them is the design: a subscription card (largest radius, boldest outline, largest type) and the
# servers nested inside it (smaller in every shared dimension, inset, borderless, hairline
# separated). A pure table owns the sizes and the plan arithmetic, the product reads that table,
# and a named test pins both — a nested row that grows back to the size of its card has to fail
# here before it can ship.
check(
    "V189 the servers hierarchy owns one size table and the plan arithmetic",
    "object ServersHierarchy" in files["serversHierarchy"]
    and "enum class SubscriptionUsageTier" in files["serversHierarchy"]
    and "const val CALM_CEILING_PERCENT = 70" in files["serversHierarchy"]
    and "const val WATCH_CEILING_PERCENT = 90" in files["serversHierarchy"]
    and "const val GROUP_CORNER_DP = 16f" in files["serversHierarchy"]
    and "const val LIST_CORNER_DP = 12f" in files["serversHierarchy"]
    and "const val ROW_TILE_DP = 30f" in files["serversHierarchy"]
    and "const val STANDALONE_TILE_DP = 40f" in files["serversHierarchy"]
    and "const val ROW_NAME_SP = 13f" in files["serversHierarchy"]
    and "fun usagePercent(" in files["serversHierarchy"]
    and "fun usageFraction(" in files["serversHierarchy"]
    and "fun usageTier(" in files["serversHierarchy"]
    and "fun compactBytes(" in files["serversHierarchy"]
    and "fun groupBadge(" in files["serversHierarchy"]
    and "fun activeFilterCount(" in files["serversHierarchy"]
    # The table is arithmetic only: no Compose, so JVM unit tests can read it.
    and "androidx.compose" not in files["serversHierarchy"]
    and "MARBLE_SERVERS_HIERARCHY_V189" in files["serversHierarchy"],
)
check(
    "V189 the Servers page reads the hierarchy table instead of hard-coding its sizes",
    "ServersHierarchy.GROUP_CORNER_DP.dp" in files["ui"]
    and "ServersHierarchy.GROUP_BORDER_DP.dp" in files["ui"]
    and "ServersHierarchy.GROUP_NAME_SP.sp" in files["ui"]
    and "ServersHierarchy.GROUP_CONTROL_DP.dp" in files["ui"]
    and "ServersHierarchy.ROW_TILE_DP.dp" in files["ui"]
    and "ServersHierarchy.ROW_NAME_SP.sp" in files["ui"]
    and "ServersHierarchy.LIST_INSET_DP.dp" in files["ui"]
    and "ServersHierarchy.percentLabel(" in files["ui"]
    and "ServersHierarchy.groupBadge(groups.size)" in files["ui"]
    and "ServersHierarchy.activeFilterCount(" in files["ui"]
    and "ServersHierarchy.ROW_PING_SP.sp" in files["protocolIdentity"]
    and "compact = true" in _fun_body(files["ui"], "fun ServersNodeCard("),
)
check(
    "V189 a nested server row is smaller, inset and borderless",
    # The card's outline is the boldest one and still runs through every row.
    "width = ServersGroupFrameWidth" in _fun_body(files["ui"], "fun ServersNodeCard(")
    # The row sits inside the card on the container step, with no outline of its own.
    and ".padding(horizontal = ServersHierarchy.LIST_INSET_DP.dp)"
    in _fun_body(files["ui"], "fun ServersNodeCard(")
    and ".background(Aether.Glass)" in _fun_body(files["ui"], "fun ServersNodeCard(")
    and "HorizontalDivider(" in _fun_body(files["ui"], "fun ServersNodeCard(")
    # The old 40 dp standalone tile is gone from the nested row, and so is its 38 dp rail.
    and "size = 40.dp" not in _fun_body(files["ui"], "fun ServersNodeCard(")
    and ".size(38.dp)" not in _fun_body(files["ui"], "fun ServersFilterRail(")
    # A probe that got no answer fades the row instead of reddening the number.
    and "quietFailure = true" in _fun_body(files["ui"], "fun ServersNodeCard(")
    and "ServersHierarchy.FAILED_ROW_ALPHA" in _fun_body(files["ui"], "fun ServersNodeCard(")
    and "attempted -> if (quietFailure) Aether.InkMuted else Aether.Danger"
    in files["protocolIdentity"],
)
check(
    "V189 a subscription card shows its plan as a bar and its counts as badges",
    # The usage bar is real, and its colour is the plan's own tier.
    "MarbleExpressiveLinearIndicator(" in _fun_body(files["ui"], "fun ServersGroupHeader(")
    and "usageTierTone(" in files["ui"]
    and "subscriptionUsageFraction(" in _fun_body(files["ui"], "fun ServersGroupHeader(")
    # The count rides beside the subscription's own name, not on a line of its own.
    and "ServersGroupCountPill(count = total" in _fun_body(files["ui"], "fun ServersGroupHeader(")
    # The three trailing controls are equal siblings on one gap.
    and "Arrangement.spacedBy(ServersHierarchy.GROUP_CONTROL_GAP_DP.dp)"
    in _fun_body(files["ui"], "fun ServersGroupHeader(")
    # The counts the header used to print moved into the filter controls for good.
    and "$groupCount groups" not in files["ui"]
    and "badge = groupBadge" in _fun_body(files["ui"], "fun ServersFilterRail(")
    and "badge = serverBadge" in _fun_body(files["ui"], "fun ServersFilterRail("),
)
check(
    "V189 the servers hierarchy is pinned by a named test and explained by a chapter",
    "class ServersHierarchyV189Test" in files["serversHierarchyTest"]
    and "usageTier" in files["serversHierarchyTest"]
    and "ROW_TILE_DP" in files["serversHierarchyTest"]
    and "groupBadge" in files["serversHierarchyTest"]
    and "MARBLE_SERVERS_HIERARCHY_V189" in files["serversHierarchyTest"]
    and "MARBLE_SERVERS_HIERARCHY_V189" in files["serversHierarchyDoc"]
    and "MARBLE_SERVERS_HIERARCHY_V189" in files["ui"],
)

# MARBLE_VISUAL_REPAIR_V190 — the real-device review of Home and Servers.
check(
    "V190 the stacked servers frame is clipped to its own slice",
    "clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height)"
    in _fun_body(files["ui"], "fun Modifier.serversStackedFrame(")
    and ".background(Aether.VoidElevated)" in _fun_body(files["ui"], "fun ServersNodeCard(")
    and "displayServerName(" in _fun_body(files["ui"], "fun ServersNodeCard(")
    and "fun displayServerName(" in files["design"],
)
check(
    "V190 the ping meter keeps its centred three-bar geometry",
    "val total = barW * 3f + gap * 2f" in files["protocolIdentity"]
    and "heights.sum() + gap" not in files["protocolIdentity"],
)
check(
    "V193 the flag is the circle and the wire scheme speaks as text",
    # MARBLE_PROTOCOL_TEXT_IDENTITY_V193 / MARBLE_ROUTE_ATELIER_V207 — the tile's one content rule:
    # the flag art fills the disc when the location has evidence behind it, and everything else is the
    # world glyph. V193 kept the hand-drawn wire-scheme glyphs out of the circle; V207 closed the last
    # upgrade path that was left, the flag emoji a subscription typed into the node's own name. A
    # label is a seller's claim, not a measurement, so `nameFlag` is gone from the tile and the
    # fallback is a globe — while the dashed rim says "unverified" without costing a sentence.
    "CountryFlagCircle(" in _fun_body(files["protocolIdentity"], "fun ProtocolTile(")
    and 'fallbackText = "🌐"' in _fun_body(files["protocolIdentity"], "fun ProtocolTile(")
    and "nameFlag" not in _fun_body(files["protocolIdentity"], "fun ProtocolTile(")
    and "locationTrust.mayDrawFlag()" in _fun_body(files["protocolIdentity"], "fun ProtocolTile(")
    and "ProtocolGlyph" not in files["protocolIdentity"]
    # The protocol identifies as TEXT in its own hue: a text pill with no glyph well.
    and "Box(" not in _fun_body(files["protocolIdentity"], "fun ProtocolBadge(")
    and "protocolTone(family)" in _fun_body(files["protocolIdentity"], "fun ProtocolBadge(")
    and "background(tone.copy(alpha = .14f))" in files["protocolIdentity"],
)
check(
    "V190 one colour source per theme",
    "val systemDynamicColor = false" in files["theme"]
    and "dynamic = true" in files["theme"]
    and "dynamicPhonePalette(generated, !light)" in files["theme"]
    and "Aether.IsDynamic" in files["design"]
    and "window.statusBarColor=palette.void.toArgb()" in files["theme"],
)
check(
    "V190 the page backdrop runs edge to edge",
    "MARBLE_EDGE_TO_EDGE_BACKDROP_V190" in files["ui"]
    and "containerColor = Color.Transparent" in files["ui"],
)

# ---------------------------------------------------------------------------
# MARBLE_IP_FAMILY_SCAN_V196 / MARBLE_IPV6_FALLBACK_LADDER_V196 / MARBLE_DNS_DOMAIN_FAULT_V196
#
# The reported outage was not a broken network: Force IPv6 refused every IPv4-only server in the
# library before the tunnel existed, and the kill switch held the old TUN each time. These
# invariants pin the shape of the fix — one decision point, one measured fact behind it, and no
# reader left holding the raw preference.
# ---------------------------------------------------------------------------
check(
    "V196 a forced address family degrades along a ladder instead of refusing",
    # The ladder owns all four rungs and the two honest refusals...
    "enum class FamilyRung" in files["familyLadder"]
    and "IPV6_STRICT" in files["familyLadder"]
    and "IPV6_FIRST" in files["familyLadder"]
    and "IPV4_FIRST" in files["familyLadder"]
    and "REFUSED" in files["familyLadder"]
    and "fun resolve(" in files["familyLadder"]
    and "fun apply(" in files["familyLadder"]
    # ...and the strict opt-in is the only thing that can still turn one into a refusal.
    and "strict: Boolean = false" in files["familyLadder"]
    and "val strictAddressFamily: Boolean = false" in files["models"]
    and 'prefs.getBoolean("strictAddressFamily", false)' in files["store"]
    and 'putBoolean("strictAddressFamily", s.strictAddressFamily)' in files["store"],
)
check(
    "V196 the family rung is decided once, where every reader already looks",
    # MarbleIntelligence.effectiveSettings is the single funnel of the connect path, the delay
    # test, the ranking pool and every prober. Deciding anywhere else is how one reader ends up
    # honouring a policy six others do not.
    "val familyResolution = familyResolution(profile, base, n)" in files["intel"]
    and "Ipv6FallbackLadder.apply(" in files["intel"]
    and "fun familyResolution(" in files["intel"]
    and "var ipFamilyEvidence:" in files["intel"]
    and "Ipv6FallbackLadder.apply(settings, resolution)" in files["repo"]
    and "fun familyResolutionFor(" in files["repo"]
    # The preflight that produced `Kill switch active` now asks the ladder first.
    and "repo.familyResolutionFor(profile)" in files["vpn"]
    and "resolution?.refusal?.let" in files["vpn"]
    # ...and the shared predicate no longer excludes anything unless strict was asked for.
    and "if (!settings.strictAddressFamily) return false" in files["addressFamily"],
)
check(
    "V196 a server's address family is measured, not guessed",
    "fun scan(" in files["familyScanner"]
    and "enum class IpFamilyVerdict" in files["familyScanner"]
    # Records and reachability stay separate facts: an AAAA nobody dialled is not IPv6 support.
    and "IPV6_UNPROVEN" in files["familyScanner"]
    and "IPV4_ONLY" in files["familyScanner"]
    and "fun classify(" in files["familyScanner"]
    # Encrypted DoH only — a scan must never leak a node name to the local resolver.
    and "AddressFamilyPolicy.resolveFamilyWithBudget" in files["familyScanner"]
    # A verdict belongs to one physical network and expires.
    and "fun usableOn(" in files["familyScanner"]
    and "const val TTL_MS" in files["familyScanner"]
    # Durable, bounded, and reachable from the repository the UI reads.
    and "fun loadIpFamilyScans(" in files["store"]
    and "fun saveIpFamilyScan(" in files["store"]
    and "fun scanIpFamily(" in files["repo"]
    and "fun scanIpFamilyForProfiles(" in files["repo"],
)
check(
    "V196 the Servers page can scan one server or a whole group and reports the verdict",
    "ServersGroupAction.SCAN_FAMILY" in files["ui"]
    and '"Scan IPv4 / IPv6"' in files["ui"]
    and "repo.scanIpFamily(profile)" in files["ui"]
    and "repo.scanIpFamilyForProfiles(group.profiles, group.title)" in files["ui"]
    # The measurement is shown where it was asked for, not only in a transient message.
    and "IpFamilyScanDialog(" in files["ui"]
    and "IpFamilyGroupDialog(" in files["ui"]
    and "repo.ipFamilyScan(profile)?.let" in files["ui"],
)
check(
    "V196 one failing name is not a failing resolver pool",
    "object DnsDomainFaultPolicy" in files["domainFault"]
    and "DISTINCT_ENDPOINTS_FOR_FAULT" in files["domainFault"]
    and "fun isDomainFaultLine(" in files["domainFault"]
    # Domain-faulted lines are filtered out BEFORE endpoint attribution and the storm detector.
    and "DnsDomainFaultPolicy.observe(" in files["intel"]
    and "DnsDomainFaultPolicy.isDomainFaultLine(it, faults, nowMs)" in files["intel"]
    and "ResolverEvidencePolicy.observe(attributable.asSequence(), before, nowMs)" in files["intel"]
    # The storm guard can now be stood down by evidence of recovery, not only by the clock.
    and "fun recordProvenAnswer(" in files["stormGuard"]
    and "stormGuard.recordProvenAnswer(nowMs)" in files["intel"]
    # And the report names the site instead of blaming the pool.
    and "DNS name faults" in files["bugFinder"]
    and "dnsDomainFaultSummary()" in files["bugFinder"],
)
check(
    "V196 the family ladder, the scan and the name classifier are pinned by tests and a chapter",
    "class Ipv6FallbackLadderTest" in files["familyLadderTest"]
    and "forceIpv6OnAnIpv4OnlyNodeDialsIpv4InsteadOfRefusing" in files["familyLadderTest"]
    and "strictEnforcementStillRefusesAndNamesTheAlternative" in files["familyLadderTest"]
    and "class IpFamilyScannerTest" in files["familyScannerTest"]
    and "anAdvertisedIpv6ThatNeverAnswersIsUnprovenNotAbsent" in files["familyScannerTest"]
    and "class DnsDomainFaultPolicyTest" in files["domainFaultTest"]
    and "twoIndependentEndpointsFailingTheSameNameIsADomainFault" in files["domainFaultTest"]
    and "oneEndpointFailingManyNamesStaysEndpointEvidence" in files["domainFaultTest"]
    and "class DnsStormGuardV196Test" in files["stormGuardTest"]
    # The strict contract keeps its own regression file, opting in explicitly.
    and "strictAddressFamily = true" in files["ipv6Test"]
    and "forceIpv6WithoutStrictDegradesInsteadOfRefusing" in files["ipv6Test"]
    and "MARBLE_IP_FAMILY_SCAN_V196" in files["familyScanDoc"]
    and "IP_FAMILY_SCAN_V196.md" in files["readme"],
)

# =================================================================================================
# V197 — the four fixes
# =================================================================================================

check(
    "V197 a family is only called absent when independent resolvers say so",
    # The resolver used to keep the FIRST provider that answered, and the winner was chosen before
    # its answer was parsed: one empty answer section deleted a whole family and a dual-stack
    # server was reported IPv4-only. Both families are now asked in parallel, with witnesses.
    "fun resolveAll(" in files["endpointResolver"]
    and "fun queryFamily(" in files["endpointResolver"]
    and "MIN_WITNESS_PROVIDERS" in files["endpointResolver"]
    and "MAX_WITNESS_PROVIDERS" in files["endpointResolver"]
    and "fun resolveFamilyWithBudget(" in files["addressFamily"]
    # ...and the scan refuses to believe absence from a single silent answer.
    and "fun resolveFamilies(" in files["familyScanner"]
    and "RESOLVE_CONFIRM_PASSES" in files["familyScanner"]
    and "MIN_CONFIRM_BUDGET_MS" in files["familyScanner"],
)
check(
    "V197 a lost first IPv6 packet does not cost a server its IPv6",
    # One connect per address decided reachability, and the first IPv6 packet on a link is exactly
    # the one neighbour discovery and PMTU discovery eat. Bounded retries, still a finite sweep.
    "CONNECT_ATTEMPTS_PER_ADDRESS" in files["familyScanner"]
    and "fun probeAddress(" in files["familyScanner"]
    and "CONNECT_ATTEMPTS_PER_ADDRESS" in files["familyScannerTest"]
    and "aSingleLostIpv6PacketDoesNotBrandADualStackNodeIpv4Only" in files["familyScannerTest"]
    and "aFamilyThatOnlyAppearsOnTheConfirmingResolutionIsBelieved" in files["familyScannerTest"],
)
check(
    "V197 IPv6 is the preferred family inside the noise floor",
    # The user's goal is the fastest path and for Marble that path is IPv6, so a handshake
    # difference too small to be real must not be allowed to argue the tunnel out of it.
    "IPV6_PREFERENCE_TOLERANCE_MS" in files["familyScanner"]
    and "val ipv6Preferred" in files["familyScanner"]
    and "ipv6WinsATieAndEverythingInsideTheNoiseFloor" in files["familyScannerTest"],
)
check(
    "V197 every added server is located, including the ones a label already names",
    # The old sweep skipped any node whose own label named a country, so the flag was the
    # subscription's claim and not the address's fact. The label now orders the queue only.
    "fun ensureServerInsights(" in files["repo"]
    and "ensureServerInsights()" in files["repo"]
    and "LOCATION_SWEEP_CONCURRENCY" in files["repo"]
    and "INSIGHT_FAMILY_BATCH" in files["repo"]
    # A lone answer is remembered as lone, so the next sweep can promote it.
    and "verdict.provisional" in files["repo"]
    and "loadServerLocationProvisional" in files["store"],
)
check(
    "V197 a single uncontradicted geo answer is shown and marked provisional",
    # Two of three providers being unreachable used to mean "no flag", which is the same thing as
    # not having tested. Silence is not disagreement — but a thin answer stays thin until proved.
    "enum class LocationConfidence" in files["locationResolver"]
    and "data class ServerLocationVerdict" in files["locationResolver"]
    and "MIN_SILENT_WITNESSES_FOR_LONE_ACCEPT" in files["locationResolver"]
    and "fun resolveCountryDetailed(" in files["locationResolver"]
    and "val provisional" in files["locationResolver"]
    # The quorum rule that was already right is untouched, and still pinned by its own test.
    and "voteRequiresAnIndependentQuorum" in files["locationTest"]
    and "oneAnswerWhileTheRestOfThePoolIsUnreachableIsAccepted" in files["locationV197Test"]
    and "aLoneAnswerIsMarkedProvisionalSoItIsTestedAgain" in files["locationV197Test"],
)
check(
    "V197 the keyboard is released by an event, never by a recomposition",
    "MARBLE_IME_HYGIENE_V197" in files["ime"]
    # A surface that only looks like a text field must never be handed an input session.
    and "fun Modifier.marbleImeInert()" in files["ime"]
    and "marbleImeInert()" in files["ui"]
    # Every real field declares the event that ends its session.
    and "marbleImeOptions(" in files["ui"]
    and "marbleImeActions(" in files["ui"]
    # ...and a host that leaves releases it exactly once.
    and "fun MarbleImeReleaseOnDispose(" in files["ime"]
    and "MarbleImeReleaseOnDispose(" in files["ui"]
    # No composable is allowed to ask for a hide on its own.
    and "LocalSoftwareKeyboardController" not in files["ui"]
    and "LocalSoftwareKeyboardController" not in files["ime"],
)
check(
    "V197 a repeated hide request is contained instead of logged a hundred times",
    "MARBLE_IME_HIDE_COOLDOWN_MS" in files["ime"]
    and "fun hide(): Boolean" in files["ime"]
    and "val suppressedHides" in files["ime"]
    and "class MarbleImeHiderTest" in files["imeTest"]
    and "aLoopInsideTheCooldownCostsOneCallInsteadOfAHundred" in files["imeTest"],
)
check(
    "V197 the route probe is multi-target from the first sample, and a pivot is a race",
    # A cycle that follows a failed one used to re-measure the same silent target and only then
    # ask somebody else, one at a time. Now it races two targets, and a pivot races its candidates.
    "fun raceFirstSample(" in files["vpn"]
    and "fun routeProbePartnerFor(" in files["vpn"]
    and "lastRouteCycleVerified" in files["vpn"]
    and "PIVOT_RACE_TARGETS" in files["vpn"]
    # A freshly cleared window is not worth scoring yet: latency publishes, the score waits.
    and "PIVOT_QUALITY_MIN_SAMPLES" in files["vpn"]
    and "holdQuality" in files["vpn"]
    and "holdQuality: Boolean = false" in files["repo"]
    and "score settling after target change" in files["repo"],
)
check(
    "V197 the four chapters are pinned by tests and written down",
    "MARBLE_IP_FAMILY_TRUTH_V197" in files["familyScanner"]
    and "MARBLE_SERVER_LOCATION_V197" in files["locationResolver"]
    and "MARBLE_IME_HYGIENE_V197" in files["ime"]
    and "MARBLE_ROUTE_PROBE_MULTI_TARGET_V197" in files["vpn"]
    and "IPV6_TRUTH_LOCATION_AND_IME_V197.md" in files["readme"],
)

# ---------------------------------------------------------------------------
# MARBLE_HIGH_JITTER_SHIELD_V206 / V207
# ---------------------------------------------------------------------------
# Very high jitter was handled by a mean that one packet destroys, a verdict that is a single
# bit, thresholds that ignore what is normal for the link in front of us, and a response whose
# cost grew with the severity. The shield replaces all four.
check(
    "V206 the high-jitter shield measures dispersion robustly and relative to the link",
    "object HighJitterShield" in files["highJitterShield"]
    # Robust dispersion: the IQR, not the mean (one packet moves it) and not the MAD (which is
    # exactly zero on a link that alternates between two paths — defect E of the first cut).
    and "IQR_TO_SIGMA" in files["highJitterShield"]
    and "fun robustJitterMs()" in files["highJitterShield"]
    and "SPIKE_IQR_FACTOR" in files["highJitterShield"]
    # Relative to the link's own baseline, with an absolute floor for a link never seen calm.
    and "BASELINE_SEED_MAX_MS" in files["highJitterShield"]
    and "ABSOLUTE_CALM_MS" in files["highJitterShield"]
    and "BASELINE_HEADROOM" in files["highJitterShield"],
)
check(
    "V207 the shield's statistics are lazy, clock-free and allocation-free",
    # Defect A: sorted inside add() meant O(n log n) per sample even when nobody read it.
    "private fun recomputeIfNeeded()" in files["highJitterShield"]
    and "var evaluations: Int = 0" in files["highJitterShield"]
    # Defect B: the wall clock jumps; the caller owns the timestamp. Checked against the object
    # body only — the chapter at the top of the file *names* the method it stopped using.
    and "System.currentTimeMillis()" not in files["highJitterShield"].split(
        "object HighJitterShield", 1
    )[1]
    and "nowMs: Long = sample.nowMs" in files["highJitterShield"]
    # No allocation after construction: two arrays, allocated once.
    and "private val ring: DoubleArray" in files["highJitterShield"]
    and "private val scratch: DoubleArray" in files["highJitterShield"]
    and "fun add(rttMs: Double)" in files["highJitterShield"],
)
check(
    "V207 the mitigation is continuous, bounded and remembers nothing stale",
    # Defect C: four fixed levels stepped the cadence by a third in one tick.
    "ALPHA_ATTACK" in files["highJitterShield"]
    and "ALPHA_RELEASE" in files["highJitterShield"]
    and "MIN_HOLD_MS" in files["highJitterShield"]
    # Defect D: an idle link stayed armed until the next reconnect.
    and "IDLE_AFTER_MS" in files["highJitterShield"]
    and "ALPHA_IDLE" in files["highJitterShield"]
    # The cost of mitigation is capped: worse links may not be measured without limit.
    and "MAX_PROBES_PER_MINUTE" in files["highJitterShield"]
    and "fun budgetedTicks(" in files["highJitterShield"]
    # One ladder for the burst, in the shield — not a copy of it in the test.
    and "MIN_BURST" in files["highJitterShield"]
    and "fun burstFor(level: Double): Int" in files["highJitterShield"],
)
check(
    "V206 the shield is wired into the route monitor, not left on a shelf",
    "HighJitterShield.RobustWindow()" in files["vpn"]
    and "fun observeHighJitter(" in files["vpn"]
    and "fun highJitterProbeCadenceTicks(" in files["vpn"]
    and "fun resetHighJitterShield()" in files["vpn"]
    and "HighJitterShield.DEGRADED_LEVEL" in files["vpn"]
    and "jitter-shield-verdict" in files["vpn"]
    and "jitter-shield-rerank-requested" in files["vpn"]
    # The plan is not decoration: the probe burst the shield asks for is the one that runs.
    and "highJitterPlan?.probeBurst" in files["vpn"],
)
check(
    "V206 the shield's claims are pinned by tests and written down",
    "class HighJitterShieldTest" in files["highJitterShieldTest"]
    and "one stalled handshake does not move the robust jitter but destroys the mean"
    in files["highJitterShieldTest"]
    and "a bimodal window reads as jittery, not as perfectly smooth"
    in files["highJitterShieldTest"]
    and "the level attacks fast and releases slowly" in files["highJitterShieldTest"]
    and "an idle link fades instead of staying armed" in files["highJitterShieldTest"]
    and "the probe budget is never exceeded at any level" in files["highJitterShieldTest"]
    and "statistics are recomputed once per change, not once per read or per sample"
    in files["highJitterShieldTest"]
    and "MARBLE_HIGH_JITTER_SHIELD_V206" in files["highJitterShieldDoc"]
    and "HIGH_JITTER_SHIELD_V206.md" in files["readme"],
)

# ---------------------------------------------------------------------------
# MARBLE_FRAGMENT_MUX_PAGE_V206
# ---------------------------------------------------------------------------
# Fragment & Mux used to be two cards with the same title, one of which silently overrode the
# other; every keystroke in them rewrote all ~250 preferences and re-created the notification
# channels on the main thread.
check(
    "V206 Fragment & Mux is one dedicated page with one door",
    'const val TRANSPORT = "transport"' in files["ui"]
    and "@Composable\nprivate fun SettingsTransportPage(" in files["ui"]
    and "FragmentMuxEntryCard(repo)" in files["ui"]
    and "fun fragmentMuxCardSubtitle(" in files["ui"]
    # One subject, one copy of each: the two identically-titled cards are gone.
    and files["ui"].count("FragmentMuxSettings(repo)") == 1
    and files["ui"].count("TransportAdaptationSettings(repo)") == 1,
)
check(
    "V206 the wire state is stated before the controls that produce it",
    "@Composable\nprivate fun FragmentMuxWireCard(" in files["ui"]
    and "fun transportLearnerOwnsWire(" in files["ui"]
    and "transportAdaptationEnabled" in files["ui"],
)
check(
    "V206 settings writes are coalesced instead of running once per keystroke",
    "fun updateSettings(v: AppSettings, coalesceWrite: Boolean = false)" in files["repo"]
    and "private fun scheduleSettingsWrite()" in files["repo"]
    and "fun flushSettings()" in files["repo"]
    and "SETTINGS_WRITE_DEBOUNCE_MS" in files["repo"]
    # The one place a coalesced write must not be lost: the activity stopping.
    and "app.repo.flushSettings()" in files["main"]
    # And the fields that produce a stream of values ask for it.
    and "coalesceWrite = true" in files["ui"],
)
check(
    "V206 the notification channels are created once, not once per settings write",
    # The channel table is a compile-time constant; rebuilding it per keystroke was four binder
    # calls into system_server per character, on the main thread.
    "MARBLE_NOTIFICATION_CHANNELS_ONCE_V206" in files["notifier"]
    and "channelsEnsured" in files["notifier"]
    and "if (channelsEnsured.get()) return" in files["notifier"]
    and files["notifier"].count("manager.createNotificationChannels(") == 1,
)
check(
    "V206 a Fragment value that cannot go on the wire says so",
    "fun fragmentFieldError(" in files["ui"]
    and "FRAGMENT_NUMERIC_FIELD" in files["ui"],
)
check(
    "V206 the learner's memory is derived once per change, not once per recomposition",
    "private const val TRANSPORT_MEMORY_ROWS" in files["ui"]
    and "private data class TransportMemoryRow(" in files["ui"]
    and "val memoryRows = remember(memory)" in files["ui"]
    and "String.format(\n                                    java.util.Locale.US"
    not in files["ui"],
)
check(
    "V206 the Fragment & Mux chapter is written down",
    "MARBLE_FRAGMENT_MUX_PAGE_V206" in files["fragmentMuxDoc"]
    and "FRAGMENT_MUX_PAGE_V206.md" in files["readme"],
)

# ---------------------------------------------------------------------------
# MARBLE_HOME_ONE_PING_V208
# ---------------------------------------------------------------------------
# The Home header owns one group-measurement action and no transient status strip. The Home ping
# follows the route's source, while progress and connection truth stay with their owning controls.
check(
    "V208 the Home header has no duplicate ping or transient status strip",
    "HomeGroupPingButton(" in files["homeStyles"]
    and "val HomePingButtonSize" in files["homeStyles"]
    and "onTestPing" not in files["homeStyles"].split("fun HomeTopActionBar(")[1]
    and "HomeRuntimeNotice" not in files["homeStyles"]
    and "MarbleNoticeHeight" not in files["homeStyles"]
    and "MARBLE_HOME_NO_TRANSIENT_STATUS_V209" in files["ui"]
    # One verb: the group ping. The per-profile test has no tap surface left in the product.
    and "onPingGroup()" in files["atelier"]
    # The two live-ping surfaces on the Studio presentations are gauges now: no tap, no second
    # way to start a measurement from the header area.
    and "kineticClickable" not in files["studio"].split("fun HomeLivePingSlab(")[1].split("\nprivate fun ")[0]
    and "kineticClickable"
    not in files["studio"].split("fun HomeLivePingMeter(")[1].split("\ninternal fun ")[0],
)

check(
    "V208 the group ping writes its result into the route's own evidence",
    "private fun publishHomeRoutePing(" in files["repo"]
    and "publishHomeRoutePing(expanded)" in files["repo"]
    and "pingHomeGroup()" in files["repo"],
)

# ---------------------------------------------------------------------------
# MARBLE_SETTINGS_ONE_LINE_COPY_V208
# ---------------------------------------------------------------------------
# Every settings description is one sentence. The rule is enforced where the text is drawn, so it
# also holds for copy that has not been written yet.
check(
    "V208 settings descriptions are clamped to one sentence",
    "object MarbleCopy" in files["designContract"]
    and "fun oneSentence(" in files["designContract"]
    and files["ui"].count("MarbleCopy.oneSentence(trx(subtitle))") >= 6
    and "MARBLE_SETTINGS_ONE_LINE_COPY_V208" in files["ui"],
)

# ---------------------------------------------------------------------------
# MARBLE_FRAGMENT_PROFILES_V208
# ---------------------------------------------------------------------------
check(
    "V208 Fragment and Mux are selectable ladders, not two checkboxes",
    "enum class FragmentProfile(" in files["adaptation"]
    and "enum class MuxProfile(" in files["adaptation"]
    and "fun withFragmentProfile(" in files["adaptation"]
    and "fun withMuxProfile(" in files["adaptation"]
    and "fun applyUserChoice(" in files["adaptation"]
    and "fun muxIsUnsafeFor(" in files["adaptation"]
    # Blank means "the policies decide", `custom` means "my numbers" — a recipe that cannot tell
    # those two apart is the bug this chapter exists to fix.
    and "object FragmentChoice" in files["models"]
    and "object MuxChoice" in files["models"]
    and "FragmentProfileChooser(" in files["ui"]
    and "MuxProfileChooser(" in files["ui"],
)

check(
    "V208 the user's recipe is applied after the policies, never overwritten by them",
    # The order IS the fix: `DpiEvasionPolicy.heal` rewrites the fragment numbers on its way to the
    # config builder, so a choice applied before it is an input and not a decision.
    "TransportAdaptation.applyUserChoice(" in files["repo"]
    and files["repo"].index("TransportAdaptation.applyUserChoice(")
    > files["repo"].index("DpiEvasionPolicy.heal"),
)

check(
    "V208 the Fragment & Mux wire mapping matches the pinned sing-box schema",
    "object SingBoxTransportPolicy" in files["singBoxWirePolicy"]
    and 'const val RecordFragmentKey: String = "record_fragment"' in files["singBoxWirePolicy"]
    and 'const val FragmentKey: String = "fragment"' in files["singBoxWirePolicy"]
    and 'const val FallbackDelayKey: String = "fragment_fallback_delay"'
    in files["singBoxWirePolicy"]
    and 'const val MultiplexField: String = "multiplex"' in files["singBoxWirePolicy"]
    # A misspelt key is not a warning: the core refuses to decode the outbound.
    and "tls.put(RecordFragmentKey, true)" in files["singBoxWirePolicy"]
    and "tls.put(FragmentKey, true)" in files["singBoxWirePolicy"]
    and "fun applyFragment(" in files["singBoxWirePolicy"]
    and "fun multiplex(" in files["singBoxWirePolicy"]
    # `multiplex` is a fatal decode error on an outbound that does not declare it.
    and 'val MARBLE_SMUX_PROTOCOLS: Set<String> = setOf("vless", "vmess", "trojan", "shadowsocks")'
    in files["singBoxBuilder"]
    and "protocol in MARBLE_SMUX_PROTOCOLS" in files["singBoxBuilder"],
)

# ---------------------------------------------------------------------------
# MARBLE_SERVER_TILE_LAYOUT_V208
# ---------------------------------------------------------------------------
check(
    "V208 servers can be read as compact boxes on the page and on Home",
    "object ServerTilePolicy" in files["serverTiles"]
    and "fun rememberServerTileColumns(" in files["serverTiles"]
    and "fun ServerTile(" in files["serverTiles"]
    and "fun ServerTileRow(" in files["serverTiles"]
    and "ServerTilePolicy.chunkRows(" in files["ui"]
    and "ServerTilePolicy.chunkRows(" in files["homeStyles"]
    and "ServerLayout.GRID" in files["ui"],
)

check(
    "V208 the layout is a user choice in Settings",
    "enum class ServerLayout" in files["models"]
    and "val AppSettings.serversLayoutEnum" in files["models"]
    and "fun parseServerLayout(" in files["models"]
    and "serversLayout = layout.id" in files["ui"]
    and "fun ServerLayoutChoice(" in files["ui"]
    and "SERVER_TILE_LAYOUT_V208.md" in files["readme"],
)

check(
    "V208 the four chapters of this pass are written down",
    all(
        marker in files["readme"]
        for marker in (
            "FRAGMENT_PROFILES_V208.md",
            "SERVER_TILE_LAYOUT_V208.md",
            "HOME_ONE_PING_V208.md",
            "SETTINGS_ONE_LINE_COPY_V208.md",
        )
    )
    and "MARBLE_FRAGMENT_PROFILES_V208" in files["fragmentProfileDoc"]
    and "MARBLE_SERVER_TILE_LAYOUT_V208" in files["serverTileDoc"]
    and "MARBLE_HOME_ONE_PING_V208" in files["homeOnePingDoc"]
    and "MARBLE_SETTINGS_ONE_LINE_COPY_V208" in files["settingsCopyDoc"],
)

# ---------------------------------------------------------------------------
# MARBLE_ROUTE_ATELIER_V207
#
# The review's root finding was that the product had effects and no grammar: five silhouettes for one
# command, hue meaning brand in one file and state in another, a touch target defined by whichever card
# drew the control, and an animation switch some code paths honoured while others ignored it. None of
# that is a type error, which is exactly why it survived eleven redesigns — and why the fix is pinned
# here as well as in a unit test. These invariants do not check that the new code is pretty. They check
# that the rules have ONE owner, that no surface re-answers a question centrally, and that the chapter
# is written down. A future "nice tweak" that re-invents a per-style tone function fails right here.
# ---------------------------------------------------------------------------
check(
    "V207 the design grammar has one home, and it is not a style sheet",
    "object MarbleTapTarget" in files["designContract"]
    and "val Floor: Dp = 48.dp" in files["designContract"]
    and "enum class MarbleRouteState" in files["designContract"]
    and "fun marbleRouteStateOf(" in files["designContract"]
    and "fun MarbleRouteState.connectVerb()" in files["designContract"]
    and "enum class MarbleLocationTrust" in files["designContract"]
    and "fun MarbleLocationTrust.mayDrawFlag()" in files["designContract"]
    and "object MarbleFeedbackPolicy" in files["designContract"]
    and "internal val LocalMarbleAmbientField" in files["designContract"]
    # The contract must stay testable off-device: it may read Compose types, but it must not need a
    # context, a coroutine or a frame clock to answer a question.
    and "import android.content.Context" not in files["designContract"],
)
check(
    "V207 a state surface may not invent its own tone or its own verb",
    # The three per-style tone functions were the drift in miniature. If one comes back, so does the
    # day four Home presentations mean four different things by "connected".
    "internal fun homeTone(" not in files["homeStyles"]
    # The names may appear in the comment that explains the deletion; the declarations may not come
    # back, because a per-style tone is precisely the drift this chapter exists to prevent.
    and "internal fun styleConnectedTone(" not in files["homeStyles"]
    and "internal fun styleStateTone(" not in files["homeStyles"]
    and "internal fun connectButtonTone(evidence: HomeEvidence): Color = "
        "marbleRouteTone(evidence.routeState)" in files["homeStyles"]
    and "return when (evidence.routeState.connectVerb())" in files["homeStyles"]
    and "return when (evidence.routeState) {" in files["homeStyles"],
)
check(
    "V207 the five silhouettes share one enabled-ness rule",
    # A control that answers twice while a tunnel is closing is a control that fights the engine. The
    # gate is the state table's answer, and the old local reading of one flag is gone for good.
    "val armed = evidence.routeState.isActionable()" in files["homeStyles"]
    and "val armed = !evidence.disconnecting" not in files["homeStyles"],
)
_slide_commit = _fun_body(files["homeStyles"], "private fun ConnectButtonSlide(")
_slide_commit = _slide_commit[_slide_commit.index("if (completed) {"):]
check(
    "V207 a slide-to-confirm dispatches the command before it animates",
    # The defect was an ordering: `animateTo(…) then onToggle()` put a network action behind a 120 ms
    # decorative tween, so a dropped frame could leave the haptic having promised a connection nobody
    # had been told about. Follow-through is allowed; it just may not hold the verb hostage.
    "onToggle()" in _slide_commit
    and "knob.animateTo" in _slide_commit
    and _slide_commit.index("onToggle()") < _slide_commit.index("knob.animateTo"),
)
check(
    "V207 reduced motion removes travel and queues, not only durations",
    # A scale spring that snaps to 0.94 in zero milliseconds is still a control that jumps under the
    # finger, and an entrance that still waits 45 ms per row is a bug the user cannot switch off.
    # Both questions go to one owner, and that owner is fed the LIVE system setting.
    "fun animates(): Boolean = MarbleMotionPolicy.animates(motionEnabled)" in files["motion"]
    and "fun entranceDelayFor(index: Int): Long = MarbleMotionPolicy.entranceDelayMs" in files["motion"]
    and "fun pressScaleFor(kind: MarbleControlKind): Float = MarbleMotionPolicy.pressScale" in files["motion"]
    and "fun acknowledges(kind: MarbleControlKind): Boolean" in files["motion"]
    and "delay(motion.entranceDelayFor(index))" in files["expressive"]
    # The observer that reads the animator scale must be released with the composition that owns it.
    and "registerContentObserver" in files["motion"]
    and "unregisterContentObserver" in files["motion"],
)
check(
    "V209 Home removes transient status while internal messages stay bounded",
    # No status strip or snackbar is rendered. The repository message is only retained briefly after
    # work settles so its old result cannot leak into a later task; system notifications are separate.
    "MARBLE_HOME_NO_TRANSIENT_STATUS_V209" in files["ui"]
    and "MarbleFeedbackPolicy.dwellMillis(actionRequired = false)" in files["ui"]
    and "HomeRuntimeNotice" not in files["homeStyles"]
    and "MarbleNoticeHeight" not in files["homeStyles"]
    and "Snackbar(" not in files["ui"],
)
check(
    "V207 the ambient field is one preference, published once and asked everywhere",
    # Backdrop glow, status rings, the heartbeat trace and the signet's waiting dot are the same cost
    # and the same question, so they are the same setting — provided once above every page rather than
    # threaded through ten signatures where it can drift.
    "val homeAmbientBackdrop: Boolean = true" in files["models"]
    and 'prefs.getBoolean("homeAmbientBackdrop", true)' in files["store"]
    and '.putBoolean("homeAmbientBackdrop", s.homeAmbientBackdrop)' in files["store"]
    and "LocalMarbleAmbientField provides repo.settings.homeAmbientBackdrop" in files["ui"]
    and 'title = "Ambient page motion"' in files["ui"]
    and "LocalMarbleAmbientField.current" in files["design"]
    # MARBLE_HOME_ONE_PING_V208 removed the second consumer (the header's heartbeat trace, deleted
    # with the rest of the header ping), so the count this can demand here is one — the field is
    # still provided once above the pages and asked in the design system, the Studio chrome and
    # the Atelier cards rather than threaded through signatures.
    and files["homeStyles"].count("LocalMarbleAmbientField.current") >= 1
    and "LocalMarbleAmbientField.current" in files["homeAtelier"],
)
check(
    "V207 the location answer is never upgraded from a node's own name",
    # Three sources can name a country and they are not three versions of one fact. The chain of
    # precedence stays (a page should use the best code it has), but the label tier is no longer a flag
    # and a lone witness is no longer a confirmed one — and the strength travels WITH the answer.
    "val flagCode = if (intelCode.isNotBlank()) intelCode else measuredCode" in files["homeStyles"]
    and "hasLabelGlyph = labelCode.isNotBlank()" in files["homeStyles"]
    and "locationTrust = locationTrust," in files["homeStyles"]
    and "mayPaintLocationFlag" in files["homeStyles"]
    and "locationTrust = evidence.locationTrust" in files["homeAtelier"]
    and "locationTrust = marbleLocationTrustOf(" in files["ui"]
    # The provisional tier is not recomputed from nothing: the resolver's own single-witness verdict is
    # what the tile reads, persisted and restored with the location it qualifies.
    and "fun serverLocationIsProvisional(profile: ProxyProfile?): Boolean" in files["repo"]
    and "serverLocationProvisional[key] = verdict.provisional" in files["repo"]
    and 'put("provisional", provisionalFlags[k] == true)' in files["store"],
)
check(
    "V207 the route presentation is a page, not a fork of the model",
    # The cheapest way for a redesign to lose a feature is to reimplement the widgets it displays. The
    # route presentation therefore consumes the shared evidence and the shared feature surfaces; if it
    # ever grows its own session stats or its own server picker, a feature will live in two places and
    # one of them will be wrong.
    "internal fun HomeThemeAtelier(" in files["homeAtelier"]
    and "HomeStyle.ROUTE_ATELIER -> HomeThemeAtelier(" in files["homeStyles"]
    and "HomeStyle.ROUTE_ATELIER -> HomeFlavor.ROUTE_ATELIER" in files["homeStyles"]
    and "private fun RoutePathSignet(" in files["homeAtelier"]
    and "HomeTopActionBar(" in files["homeAtelier"]
    and "HomeSessionStats(" in files["homeAtelier"]
    and "HomeShortcutDeck(" in files["homeAtelier"]
    and "NationalEventBanner(" in files["homeAtelier"]
    and "ProtocolTile(" in files["homeAtelier"]
    # The signet states its own uncertainty, in words as well as in pixels.
    and "provenCount" in files["homeAtelier"]
    and "contentDescription = speech" in files["homeAtelier"]
    and "path.provenCount" in files["homeAtelier"],
)
check(
    "V207 every control that can be hit can be hit at 48 dp, without being repainted",
    # The review's phrase was "visual refinement was bought by shrinking the touch area". The floor is
    # a layout node, so a designer's artwork keeps its size and the slot absorbs the hit area — which is
    # also why an icon control no longer has to grow to 48 dp to be legal.
    "fun Modifier.marbleTapTarget(" in files["designContract"]
    and ".marbleTapTarget(" in files["design"]
    and ".marbleTapTarget(" in files["homeStyles"]
    and ".marbleTapTarget(" in files["homeAtelier"]
    and "val ControlHeight = 48.dp" in files["design"]
    # A nameless icon button is the other half of the same defect: an unlabeled control is not a small
    # control, it is an inaccessible one. The parameter has no default any more.
    and "descriptiveLabel: String," in files["design"]
    and "descriptiveLabel: String = \"\"" not in files["design"],
)
check(
    "V207 the chapter is pinned by tests and written down",
    "class MarbleDesignContractV207Test" in files["routeAtelierTest"]
    and "assertEquals(5, HomeStyle.entries.size)" in files["homeStyleTest"]
    and "assertEquals(HomeStyle.ROUTE_ATELIER, HomeStyle.DEFAULT)" in files["homeStyleTest"]
    and "MARBLE_ROUTE_ATELIER_V207" in files["routeAtelierDoc"]
    and "MARBLE_ROUTE_ATELIER_V207" in files["homeAtelier"]
    and "MARBLE_ROUTE_ATELIER_V207" in files["designContract"]
    # The Persian UI may not let the word-level fallback invent a reading for a whole sentence.
    and "\"Slide right to connect\"" in files["persianLexicon"]
    and "\"location not verified\"" in files["persianLexicon"]
    and "\"Ambient page motion\"" in files["persianLexicon"],
)

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
