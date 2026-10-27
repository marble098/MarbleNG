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
    """The live workflow, and only the live workflow.

    MARBLE_TOOLCHAIN_AUTOPILOT_V211 — this function used to prefer a copy under
    `docs/workflows-pending/`, on the belief that the branch token could not write
    `.github/workflows/*`. It can: the V211 work pushed workflow changes to this branch and watched
    them run. The staged copies were meanwhile **stale against the live files** while every
    invariant read them in preference to the real thing, which is the one failure mode the
    convention could not survive. The directory is gone and the checks below assert that.
    """
    return read(f".github/workflows/{name}")

files = {
    "main": read("app/src/main/java/com/marbleng/app/MainActivity.kt"),
    "permissions": read("app/src/main/java/com/marbleng/app/ui/MarblePermissionOnboarding.kt"),
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
    "resolverCatalog": read("app/src/main/java/com/marbleng/app/core/DnsResolverCatalog.kt"),
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
    "textScale": read("app/src/main/java/com/marbleng/app/ui/MarbleTextScale.kt"),
    "settingsTreeDoc": read("docs/SETTINGS_TREE_TEXT_SCALE_V215.md"),
    "settingsTreeTest": read("app/src/test/java/com/marbleng/app/model/TextScaleV215Test.kt"),
    "marbleApp": read("app/src/main/java/com/marbleng/app/ui/MarbleApp.kt"),
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
    # MARBLE_CORE_OPTIONS_V211 — the cores' own options, written once and never rewritten. The
    # learner that used to live in `TransportAdaptation.kt` is gone with the fragment feature it
    # served; this is its replacement, and it is the only writer of the per-core fields.
    "coreOptions": read("app/src/main/java/com/marbleng/app/core/CoreOptions.kt"),
    # MARBLE_SERVER_TILE_LAYOUT_V208 — the compact server grid, its policy and its test.
    # MARBLE_NATIVE_ANDROID_V213 — the offline flag library: every country the product can name,
    # drawn as vector art, and the circle that frames one.
    "countryFlagArt": read("app/src/main/java/com/marbleng/app/ui/CountryFlagArt.kt"),
    # MARBLE_SERVER_TILE_TRUTH_V210 — what a box may claim, and the name slot's one-line rule.
    # MARBLE_FLOATING_ACTIONS_V210 — the per-theme colours of a floating action, and the chrome
    # that scores the ink on top of them.
    # MARBLE_MERIDIAN_V221 — the flat chapter: the surface switch, the accent, the power
    # projection, the Censorship Lab, the warm cache and the pages that expose them.
    "censorshipLab": read("app/src/main/java/com/marbleng/app/core/CensorshipLab.kt"),
    "corePower": read("app/src/main/java/com/marbleng/app/core/CorePower.kt"),
    "fragmentPolicy": read("app/src/main/java/com/marbleng/app/core/FragmentPolicy.kt"),
    "connectWarmup": read("app/src/main/java/com/marbleng/app/core/ConnectWarmup.kt"),
    "flapGuard": read("app/src/main/java/com/marbleng/app/core/ProfileFlapGuard.kt"),
    "stressMonitor": read("app/src/main/java/com/marbleng/app/core/TcpStressMonitor.kt"),
    "meridianDoc": read("docs/MERIDIAN_V221.md"),
    "meridianTest": read("app/src/test/java/com/marbleng/app/core/MeridianV221Test.kt"),
    "censorshipTest": read(
        "app/src/test/java/com/marbleng/app/core/CensorshipLabV221Test.kt"
    ),
    "corePowerTest": read("app/src/test/java/com/marbleng/app/core/CorePowerV221Test.kt"),
    # MARBLE_SETTINGS_SECTIONS_V210 — the Settings tree: its policy and its test.
    # MARBLE_FRAGMENT_PROFILES_V208 — the rewritten Fragment & Mux stack: the wire policy, the
    # profile ladder and the tests that pin both.
    "singBoxWirePolicy": read("app/src/main/java/com/marbleng/app/core/SingBoxTransportPolicy.kt"),
    "coreOptionsTest": read(
        "app/src/test/java/com/marbleng/app/core/CoreOptionsV211Test.kt"
    ),
    "singBoxWireTest": read(
        "app/src/test/java/com/marbleng/app/core/SingBoxTransportPolicyV208Test.kt"
    ),
    "coreOptionsDoc": read("docs/CORE_OPTIONS_V211.md"),
    "serverTileDoc": read("docs/SERVER_TILE_LAYOUT_V208.md"),
    # MARBLE_SERVER_TILE_TRUTH_V210 / MARBLE_HOME_ROUTE_PING_V210 / MARBLE_FLOATING_ACTIONS_V210
    # / MARBLE_SETTINGS_SECTIONS_V210 — the V210 pass: what a server box may claim, which server
    # the Home ping buttons measure, the per-theme colours of a floating action, and the Settings
    # tree.
    "serverTileTruthDoc": read("docs/SERVER_TILE_TRUTH_V210.md"),
    "homeRoutePingDoc": read("docs/HOME_ROUTE_PING_V210.md"),
    "floatingActionsDoc": read("docs/FLOATING_ACTIONS_V210.md"),
    "settingsSectionsDoc": read("docs/SETTINGS_SECTIONS_V210.md"),
    "homeOnePingDoc": read("docs/HOME_ONE_PING_V208.md"),
    "settingsCopyDoc": read("docs/SETTINGS_ONE_LINE_COPY_V208.md"),
    # MARBLE_ROUTE_ATELIER_V207 — the design grammar and the presentation built against it. They are
    # required files now: a build that drops either one turns Home's default presentation into a page
    # that the dispatcher cannot render, so `read()` failing here is the correct loud answer.
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
    # MARBLE_EXPRESSIVE_MOTION_V186 — the motion chapter: its library, the shared motion clock it
    # extends, the pure-math test that pins the wave/stagger/depth contracts, and the chapter.
    "expressiveDoc": read("docs/EXPRESSIVE_MOTION_V186.md"),
    # MARBLE_SERVERS_HIERARCHY_V189 — the Servers page's two levels: the pure size table and plan
    # arithmetic, the test that pins both, and the chapter that explains the nesting.
    "serversHierarchyDoc": read("docs/SERVERS_HIERARCHY_V189.md"),
    "tile": read("app/src/main/java/com/marbleng/app/quicktile/MarbleQuickTileService.kt"),
    "manifest": read("app/src/main/AndroidManifest.xml"),
    "security": read("app/src/main/res/xml/network_security_config.xml"),
    "native": read("scripts/prepare-native.sh"),
    # MARBLE_TUN2SOCKS_SHUTDOWN_V223 — the second datapath's teardown: the Go wrapper (which is
    # copied into the pinned tree), the JNI shim that owns the stop, the Kotlin bridge, the policy
    # the service asks, the test that pins it and the chapter that explains it.
    "t2sWrapper": read("native/tun2socks/main.go"),
    "t2sJni": read("app/src/main/jni/tun2socks_jni.c"),
    "t2sBridge": read("app/src/main/java/com/marbleng/app/nativebridge/Tun2SocksTunnel.kt"),
    "shutdownPolicy": read("app/src/main/java/com/marbleng/app/core/TunnelShutdownPolicy.kt"),
    "shutdownTest": read(
        "app/src/test/java/com/marbleng/app/core/Tun2SocksShutdownV223Test.kt"
    ),
    "shutdownDoc": read("docs/TUN2SOCKS_SHUTDOWN_V223.md"),
    # MARBLE_FDSAN_DOUBLE_CLOSE_V224 — the borrowed-descriptor rule: the HEV JNI bridge (the
    # other side of the boundary the Go wrapper already had asserted), the test that pins the
    # Kotlin-visible half and the chapter that explains the Stack.Wait() close V223 missed.
    "hevJni": read("app/src/main/jni/marbleng_jni.c"),
    "fdsanTest": read(
        "app/src/test/java/com/marbleng/app/core/FdsanDoubleCloseV224Test.kt"
    ),
    "fdsanDoc": read("docs/FDSAN_DOUBLE_CLOSE_V224.md"),
    "build": workflow("build.yml"),
    "gradle": read("app/build.gradle.kts"),
    # MARBLE_TOOLCHAIN_AUTOPILOT_V211 — one version file, read by the settings script, the module
    # build, the wrapper and the workflows; one workflow that resolves and writes it.
    "settingsGradle": read("settings.gradle.kts"),
    "resolveToolchain": read("scripts/resolve-toolchain.py"),
    "rootGradle": read("build.gradle.kts"),
    "gradlewScript": read("gradlew"),
    "toolchainPins": read("gradle/toolchain.properties"),
    "updateToolchain": workflow("update-toolchain.yml"),
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
    # MARBLE_NOTIFICATION_CHANNELS_ONCE_V206 — the notifier is a production source too: it was
    # rebuilt on every settings write, i.e. on every keystroke of every settings field.
    "notifier": read("app/src/main/java/com/marbleng/app/core/SmartNotifier.kt"),
    # MARBLE_NOTIFICATION_COUNCIL_V231 — the notification app's copy engine, the test that pins
    # it and the chapter that explains the five-expert review behind it.
    "notificationCopy": read(
        "app/src/main/java/com/marbleng/app/core/NotificationCopy.kt"
    ),
    "notificationTest": read(
        "app/src/test/java/com/marbleng/app/core/NotificationCopyV231Test.kt"
    ),
    "notificationDoc": read("docs/NOTIFICATION_COUNCIL_V231.md"),
    # MARBLE_PERSIAN_DOCK_AND_SHADE_COUNCIL_V232 — real Persian fallback, dock positioning,
    # notification shortcuts and the role-based five-person review.
    "persianLexicon": read("app/src/main/java/com/marbleng/app/ui/MarblePersianLexicon.kt"),
    "dockModel": read("app/src/main/java/com/marbleng/app/model/Models.kt"),
    "dockStore": read("app/src/main/java/com/marbleng/app/data/AppStore.kt"),
    "dockSettings": read("app/src/main/java/com/marbleng/app/ui/SettingsAppearancePages.kt"),
    "dockTest": read("app/src/test/java/com/marbleng/app/model/DockPresentationV232Test.kt"),
    "persianTest": read("app/src/test/java/com/marbleng/app/ui/PersianLocalizationV232Test.kt"),
    "dockCouncilDoc": read("docs/PERSIAN_DOCK_AND_SHADE_COUNCIL_V232.md"),
    "receiver": read("app/src/main/java/com/marbleng/app/MarblePackageReplacedReceiver.kt"),
    # MARBLE_PERSIAN_CONNECT_DOCK_COUNCIL_V233 — the complete-coverage gate, three independent
    # connect buttons and the measured dock indicator.
    "v233PermissionDialog": read(
        "app/src/main/java/com/marbleng/app/ui/MarblePermissionOnboarding.kt"
    ),
    "v233QuickTileCopy": read(
        "app/src/main/java/com/marbleng/app/core/QuickTileCopy.kt"
    ),
    "v233ConnectDeckModel": read(
        "app/src/main/java/com/marbleng/app/model/ConnectDeck.kt"
    ),
    "v233ConnectDeckUi": read(
        "app/src/main/java/com/marbleng/app/ui/MarbleConnectDeck.kt"
    ),
    "v233DockSpec": read(
        "app/src/main/java/com/marbleng/app/model/DockIndicatorSpec.kt"
    ),
    "v233PersianTest": read(
        "app/src/test/java/com/marbleng/app/ui/PersianCoverageV233Test.kt"
    ),
    "v233QuickTileTest": read(
        "app/src/test/java/com/marbleng/app/core/QuickTileCopyV233Test.kt"
    ),
    "v233DeckTest": read(
        "app/src/test/java/com/marbleng/app/model/ConnectDeckActionV233Test.kt"
    ),
    "v233DockTest": read(
        "app/src/test/java/com/marbleng/app/model/DockIndicatorSpecV233Test.kt"
    ),
    "v233CouncilDoc": read("docs/PERSIAN_CONNECT_DOCK_COUNCIL_V233.md"),
    # MARBLE_LATEST_VERSION_RULE_V235 / MARBLE_WEIGHT_LADDER_V235 — the one update rule (newest
    # published wins, beta or release) in its pure-Kotlin, shell and fixture forms, the tests that
    # pin it, the weight ladder's test, and the chapter that records both.
    "latestRule": read("app/src/main/java/com/marbleng/app/core/LatestReleaseRule.kt"),
    "latestRuleTest": read(
        "app/src/test/java/com/marbleng/app/core/LatestReleaseRuleV235Test.kt"
    ),
    "weightLadderTest": read(
        "app/src/test/java/com/marbleng/app/ui/MarbleWeightLadderV235Test.kt"
    ),
    "coreRuleTest": read("scripts/test-core-lock-rule.sh"),
    "v235Doc": read("docs/LATEST_VERSION_RULE_AND_WEIGHT_LADDER_V235.md"),
    # MARBLE_NOTIFICATION_SWITCH_V234 / MARBLE_SHADE_ATELIER_V234 / MARBLE_RTL_CONNECT_CONTROL_V234 —
    # the shade's own route switch, the drawn ongoing item, and the mirrored Home connect control.
    "routeCycle": read("app/src/main/java/com/marbleng/app/core/NotificationRouteCycle.kt"),
    "routeCycleTest": read(
        "app/src/test/java/com/marbleng/app/core/NotificationRouteCycleV234Test.kt"
    ),
    "shadeDesign": read("app/src/main/java/com/marbleng/app/core/NotificationShadeDesign.kt"),
    "shadeView": read("app/src/main/java/com/marbleng/app/core/TunnelNotificationView.kt"),
    "shadeTest": read(
        "app/src/test/java/com/marbleng/app/core/NotificationShadeDesignV234Test.kt"
    ),
    "slideSpec": read("app/src/main/java/com/marbleng/app/model/SlideConnectSpec.kt"),
    "slideTest": read(
        "app/src/test/java/com/marbleng/app/model/SlideConnectSpecV234Test.kt"
    ),
    "shadeLayout": read("app/src/main/res/layout/notification_tunnel.xml"),
    "shadeLayoutRtl": read("app/src/main/res/layout/notification_tunnel_rtl.xml"),
    "switchIcon": read("app/src/main/res/drawable/ic_notification_switch.xml"),
    "sourceIcon": read("app/src/main/res/drawable/ic_notification_source.xml"),
    "v234Doc": read("docs/NOTIFICATION_SWITCH_AND_SHADE_V234.md"),
    # MARBLE_MULTI_SOURCE_ROUTING_V212 — the geo source set, the curated domestic knowledge, the
    # routing engine that reads both, and the three tests that pin them.
    "geoRegistry": read("app/src/main/java/com/marbleng/app/core/GeoAssetRegistry.kt"),
    "geoIndex": read("app/src/main/java/com/marbleng/app/core/GeoAssetIndex.kt"),
    "precisionPack": read("app/src/main/java/com/marbleng/app/core/IranPrecisionPack.kt"),
    "routingEngine": read("app/src/main/java/com/marbleng/app/core/RoutingEngine.kt"),
    "geoRegistryTest": read(
        "app/src/test/java/com/marbleng/app/core/GeoAssetRegistryV212Test.kt"
    ),
    "precisionPackTest": read(
        "app/src/test/java/com/marbleng/app/core/IranPrecisionPackV212Test.kt"
    ),
    "multiSourceTest": read(
        "app/src/test/java/com/marbleng/app/core/MultiSourceRoutingV212Test.kt"
    ),
    "multiSourceDoc": read("docs/MULTI_SOURCE_ROUTING_V212.md"),
    # MARBLE_HOME_PING_CONTROLS_V212 / MARBLE_SERVER_TILE_PARITY_V212 / MARBLE_SURFACE_DEPTH_V212
    # — the three surface chapters of the same pass, and the test that pins their wiring.
    "homePingActionTest": read(
        "app/src/test/java/com/marbleng/app/model/HomePingControlsV212Test.kt"
    ),
    "homePingControlsDoc": read("docs/HOME_PING_CONTROLS_V212.md"),
    "serverTileParityDoc": read("docs/SERVER_TILE_PARITY_V212.md"),
    # MARBLE_NATIVE_ANDROID_V213 — the native chapter: the platform's measurement table and its
    # component set, the critique that named the seven surfaces, the chapter that answers them and
    # the test that pins both halves. Named `nativeUi`, not `native`: that key is the Go/C++ side.
    "nativeUiDoc": read("docs/NATIVE_ANDROID_V213.md"),
    "nativeCritiqueDoc": read("docs/APPEARANCE_CRITIQUE_V213.md"),
    "surfaceDepthDoc": read("docs/SURFACE_DEPTH_V212.md"),
    # MARBLE_IOS_DESIGN_SYSTEM — clean modular iOS UI architecture
    "iosTheme": read("app/src/main/java/com/marbleng/app/ui/IosTheme.kt"),
    "iosComponents": read("app/src/main/java/com/marbleng/app/ui/IosComponents.kt"),
    "uiFormatters": read("app/src/main/java/com/marbleng/app/ui/UiFormatters.kt"),
    "homeScreen": read("app/src/main/java/com/marbleng/app/ui/HomeScreen.kt"),
    "serversScreen": read("app/src/main/java/com/marbleng/app/ui/ServersScreen.kt"),
    "serverForms": read("app/src/main/java/com/marbleng/app/ui/ServerFormsAndDetailPages.kt"),
    "customSlot": read("app/src/main/java/com/marbleng/app/ui/CustomSlotScreen.kt"),
    "settingsScreen": read("app/src/main/java/com/marbleng/app/ui/SettingsScreen.kt"),
    "settingsConnection": read("app/src/main/java/com/marbleng/app/ui/SettingsConnectionPages.kt"),
    "settingsRouting": read("app/src/main/java/com/marbleng/app/ui/SettingsRoutingPages.kt"),
    "settingsTesting": read("app/src/main/java/com/marbleng/app/ui/SettingsTestingPages.kt"),
    "settingsAppearance": read("app/src/main/java/com/marbleng/app/ui/SettingsAppearancePages.kt"),
    "settingsSystem": read("app/src/main/java/com/marbleng/app/ui/SettingsSystemPages.kt"),
    "marbleModals": read("app/src/main/java/com/marbleng/app/ui/MarbleModals.kt"),
    "glyphArt": read("app/src/main/java/com/marbleng/app/ui/MarbleGlyphArt.kt"),
    "iosTest": read("app/src/test/java/com/marbleng/app/ui/IosDesignSystemTest.kt"),
    "colorsXml": read("app/src/main/res/values/colors.xml"),
    "stylesXml": read("app/src/main/res/values/styles.xml"),
    # MARBLE_NETWORK_COUNCIL_V236 — the twelve-expert scanner: the pure judgement, the one file
    # that touches Android, the persisted verdict, the page, the test and the chapter.
    "council": read("app/src/main/java/com/marbleng/app/core/NetworkCouncil.kt"),
    "councilScanner": read("app/src/main/java/com/marbleng/app/core/NetworkScanner.kt"),
    "councilReport": read("app/src/main/java/com/marbleng/app/core/CouncilReport.kt"),
    "councilPage": read("app/src/main/java/com/marbleng/app/ui/SettingsNetworkCouncilPages.kt"),
    "councilTest": read("app/src/test/java/com/marbleng/app/core/NetworkCouncilV236Test.kt"),
    "councilDoc": read("docs/NETWORK_COUNCIL_SCANNER_V236.md"),
}

workflow_sources = "\n".join(
    path.read_text(encoding="utf-8")
    for path in sorted((ROOT / ".github" / "workflows").glob("*.yml"))
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

# MARBLE_HOME_STYLE_TRIM_V121 — three presentations of one connection surface.
# Parametric and Bioluminescent were removed from the product: they are not modelled, not
# implemented, not translated and not reachable anywhere.
home_styles = ("IOS_SLIDER", "IOS_FLOATING", "IOS_EMBOSSED", "IOS_MODULAR")
retired_home_styles = ("BIOLUMINESCENT", "PARAMETRIC")
check(
    "connection ping is one-shot and never a background timer",
    "fun measureConnectionPing()" in files["repo"]
    and "connectionPingInFlight" in files["repo"]
    and "enum class ConnectionPingState" in files["models"]
    and "ConnectionPingState.FAILED" in files["repo"],
)

# MARBLE_HOME_THEME_TWO_DEFAULT_V160 / MARBLE_ROUTE_ATELIER_V207 — every presentation is modelled and
# reachable, and the product default is named exactly once so the model, the store and the parser
# cannot disagree about it. V160 set that name to Theme 2 (Floating); V207 moved it to the route
# presentation, and the check moved with it — what it pins is the SHAPE of the rule (one name, read by
# the store and the parser, with a dispatcher behind it), not which theme happens to hold it today.
# A default may be moved; a presentation may not be orphaned by the move. Theme 2 keeps its id, its
# implementation and its reachability, because an install that chose it must still find it.
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
# Home carries no legacy Signature swppers or server-card settings; the presentation is chosen
# in Settings and server selection stays shared between Home and the Servers page.
check(
    "selected-server endpoint ping is one-shot with failure kinds",
    "fun measureSelectedPing()" in files["repo"]
    and "selectedPingInFlight" in files["repo"]
    and "fun measureHomePing()" in files["repo"]
    and "classifyPingFailure(" in files["repo"],
)
check(
    "dragged Signature floating-button position state is gone",
    "rememberProFabPosition" not in files["repo"]
    and "setProFabPosition" not in files["store"]
    and "proFabPosition" not in files["repo"],
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

# MARBLE_SEAMLESS_LOOPS_V112 — loop restarts must be invisible.

# MARBLE_HOME_PING_AUTOFIT_V112 — the ping can never overflow its readout.

# MARBLE_SYSTEM_FONT_V112 — the device typeface is a first-class choice; Persian stays Vazir.

# MARBLE_NIGHT_OUTLINES_V112 — dark-theme frame personality is a user choice.

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
    "release packaging requires a stable signer",
    "signing.properties" in files["gradle"]
    and "unsigned APKs are not installable" in files["gradle"]
    and "apksigner" in files["build"],
)
# MARBLE_SETTINGS_TREE_V215 — the flags no screen read are removed from the model and the store.
check(
    "V215 retired Home flags and the expert switch are gone from model and store",
    all(
        name not in files["models"] and name not in files["store"]
        for name in (
            "homeShowRouteRibbon",
            "homeShowServerSelector",
            "homeShowSummaryMetrics",
            "homeShowQuickActions",
            "homeShowIranMode",
            "homeShowRouteDetails",
            "rememberLast",
            "iranModeNotify",
            "expertMode",
        )
    ),
)
check("no legacy chainEnabled resurrects the fragment dialers", "chainEnabled" not in files["dpiPolicy"])
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
    "Bug Finder reports passive and external leak scores separately",
    "Passive leak containment" in files["bug"]
    and "External anti-leak audit" in files["bug"]
    and "ipLeakScore" in files["bug"],
)

# UI / Home.
# MARBLE_ROUTING_SEPARATE_V143 — Routing is its own dedicated Settings page and the Home->Routing
# deep link jumps straight to it without mutating Expert mode.
# MARBLE_SMOOTH_CLOCK_V193 / MARBLE_ROUTE_ATELIER_V207 — V193 bounded an endless name scroller to
# three passes, which fixed the frame cost and kept the usability defect: a row whose name moves is a
# row a user cannot compare, and several of them starting two seconds apart turned the Servers page
# into signage. V207 took the marquee off the list row entirely (two settled lines, ellipsis; the full
# name stays readable and copyable in the detail sheet). The invariant is the one the product actually
# promises now: no text scrolls inside a Servers row, and no unused scroller import is left behind.
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
    "no production code still selects a retired ping method",
    not any(
        "ProbeMethod." + retired in files[key]
        for retired in ("HYBRID", "TUNNEL", "TCP_CONNECT", "TCP_RECOMMENDED", "HTTP_GET", "HTTP_HEAD", "ICMP", "DNS")
        for key in ("repo", "bench", "probe", "settingsTesting", "settingsScreen", "vpn", "store", "models")
    ),
)
# MARBLE_URLTEST_SINGBOX_ONLY_V156 — the URL test is sing-box extended's own delay controller and
# nothing else. The Xray look-alike (a Kotlin HTTPS HEAD through Xray's SOCKS inbound) measured
# something different behind the same label, so the same server reported two incomparable numbers
# depending on the selected core. The method now refuses on any engine that does not own it, and
# Settings stops offering it there.
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


# The status word and its dot sat between the wordmark and the actions. `repo.state == "CONNECTED"`
# elsewhere in the file builds the evidence model the banner below reads; that stays. What must not
# come back is the header rendering a status word of its own.
# Comments are stripped first: the reason the status word was removed is recorded in the comment
# right where it used to be, and that prose must not read as the readout coming back.

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
    '149.112.112.112' in files["resolverCatalog"]
    and '1.0.0.1' in files["resolverCatalog"]
    and "fun isXrayBootstrapSafe(endpoint: String)" in files["resolverCatalog"]
    and "DnsResolverCatalog.isXrayBootstrapSafe(url)" in files["hardener"]
    and "DnsResolverCatalog.candidates" in files["intel"],
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
# MARBLE_PROTOCOL_IDENTITY — the Home server row dropped its own slab for the shared
# ServerPingStat (MarbleProtocolIdentity.kt): the same tone-only measurement rule now lives in
# one component both lists read, so the invariant is pinned there — the readout must never grow
# a tinted fill back, and the Home row must actually compose it.
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
# MARBLE_TOOLCHAIN_AUTOPILOT_V211 — the staging convention is retired: the branch token writes
# `.github/workflows/*` directly, so a copy of a workflow anywhere else is a version that drifts.
# What the staged copies used to carry is asserted here, against the live files.
check(
    "the workflow staging convention is retired and its fixes live in the workflows themselves",
    not (ROOT / "docs" / "workflows-pending").exists()
    and "scripts/inject-singbox-go127-fix.py" in files["verify"]
    and "go-version-file" in files["verify"]
    and "scripts/inject-singbox-android-fix.py" in files["updateCores"]
    and "scripts/inject-singbox-go127-fix.py" in files["updateCores"],
)
check(
    "V211 the build reads one version file and updates itself to the newest pre-release",
    # One file names every version: the plugin half in pluginManagement, the library half in the
    # module, the distribution in the wrapper.
    "gradle/toolchain.properties" in files["settingsGradle"]
    and 'id("com.android.application") version pin("agp"' in files["settingsGradle"]
    and 'pin("kotlin"' in files["settingsGradle"]
    and "toolchainVersion(" in files["gradle"]
    and 'toolchainVersion("composeBom"' in files["gradle"]
    and 'toolchainVersion("kotlin"' not in files["settingsGradle"]
    and "gradle/toolchain.properties" in files["gradlewScript"]
    # Nothing else names one: a second copy is a version that drifts.
    and "9.9.0-milestone-2" not in files["settingsGradle"]
    and "9.2.1" not in files["rootGradle"]
    and "2.3.10" not in files["rootGradle"]
    and 'V=9.5.1' not in files["gradlewScript"]
    # The autopilot: resolve, prove the app compiles, then push. It is the only writer.
    and "python3 scripts/resolve-toolchain.py --write" in files["updateToolchain"]
    and ":app:compileDebugKotlin" in files["updateToolchain"]
    and "if: steps.moved.outputs.moved == 'yes' && success()" in files["updateToolchain"]
    and "git push origin HEAD:main" in files["updateToolchain"]
    and "MARBLE_TOOLCHAIN_AUTOPILOT" in files["resolveToolchain"]
    # And every workflow takes Gradle from that file rather than from a literal.
    and 'gradle-version: ${{ steps.toolchain.outputs.gradle }}' in files["verify"]
    and 'gradle-version: ${{ steps.toolchain.outputs.gradle }}' in files["build"],
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
# MARBLE_TUN2SOCKS_CORE_V222 — the second datapath is a product surface exactly like the second
# proxy engine: a pin, a packaged library, a persisted choice, a start path, a stop path, and a
# page that offers its own options. Any one of those missing makes the choice cosmetic.
# MARBLE_TUN2SOCKS_SHUTDOWN_V223 — the disconnect abort. Three rules, and every file that has to
# hold one of them is asserted here, because the Kotlin compiler cannot see the Go library and the
# Go library cannot see the service.
def _without_go_comments(text: str) -> str:
    """The wrapper with `//` comments stripped, so an invariant can forbid *code*, not prose."""
    return "\n".join(line.split("//", 1)[0] for line in text.splitlines())


_t2s_go = _without_go_comments(files["t2sWrapper"])
# The teardown function on its own: "close the transport last" is a statement about this order,
# not about the file (start() closes a half-built transport earlier, on the CreateStack failure).
_t2s_teardown = _t2s_go[_t2s_go.index("func teardown("):]
_t2s_teardown = _t2s_teardown[: _t2s_teardown.index("\n}\n") + 3]

check(
    "V224 the second datapath stops before it lets go, and only ever closes its own dup",
    # The descriptor belongs to the VpnService and is only lent to the wrapper. This check used
    # to forbid `unix.Close(` in the wrapper outright (V223) — and the crash came back anyway,
    # because the close was never in the wrapper: gVisor's Stack.Wait() removes each NIC with
    # closeLinkEndpoint=true, which runs the pinned tun2socks FD.Close() = unix.Close(fd) on the
    # borrowed number, and the service's fdsan-tagged adoptFd close then aborted the process
    # ("fdsan: double-close of file descriptor 148 detected"). Forbidding close *text* in the
    # wrapper cannot see a close inside Wait(); what makes any present or future stack close
    # harmless is that the device is opened on a dup the wrapper owns. So that is what is
    # asserted now: the dup exists, the device is opened on it, the borrowed number is never
    # opened on and never closed, and the only raw close left is of the dup (the fdbased.Open
    # failure path, where neither tun2socks nor gVisor closes anything).
    "MARBLE_FDSAN_DOUBLE_CLOSE_V224" in files["t2sWrapper"]
    and "unix.F_DUPFD_CLOEXEC" in _t2s_go
    and "fdbased.Open(strconv.Itoa(ownedFd)" in _t2s_go
    and "fdbased.Open(strconv.Itoa(fd)" not in _t2s_go
    and "unix.Close(fd)" not in _t2s_go
    and "unix.Close(ownedFd)" in _t2s_go
    # ... and the datapath is stopped the only way the endpoint allows: the stopfd, then the wait.
    and "func stopLinkEndpoint(" in _t2s_go
    and "dev.Attach(nil)" in _t2s_go
    and "st.Close()" in _t2s_go
    and "st.Wait()" in _t2s_go
    and "transport.Close()" in _t2s_go
    # The fan-in is closed after the stack: closing it first would leave the stack's forwarders
    # blocked on an unbuffered channel nobody drains.
    and _t2s_teardown.index("st.Wait()") < _t2s_teardown.index("transport.Close()")
    # The device close belongs between the wait and the fan-in: Wait() normally already ran it
    # (through the NIC removal), the pinned FD.Close() is closed-flag guarded, so this call is
    # the fallback that keeps the dup from leaking if a future stack stops closing endpoints —
    # and it may only run once every dispatcher has left the number, which is after Wait().
    and _t2s_teardown.index("st.Wait()") < _t2s_teardown.index("dev.Close()") < _t2s_teardown.index("transport.Close()")
    # A reconnect may not open a second stack over one descriptor.
    and "stateStopping" in _t2s_go
    and "codeStopping       = -7" in _t2s_go
    # The join the service needs before it closes anything.
    and "func marble_t2s_stop(" in _t2s_go
    and "marble_t2s_stop" in files["t2sJni"]
    and "nativeStop" in files["t2sJni"]
    and "fun stopAndWait(" in files["t2sBridge"]
    and "nativeStop(waitMs: Int): Int" in files["t2sBridge"]
    and "const val CODE_STOPPING: Int = -7" in files["t2sBridge"]
    and "const val STOP_TIMEOUT_MS: Int = 1_500" in files["t2sBridge"]
    # The error string a JNI thread may be reading is never freed underneath it.
    and "func currentError()" in _t2s_go
    and "errorSlots" in _t2s_go,
)
check(
    "V223 a cleanup stops its own datapath, closes the descriptor once, and reads a forced exit",
    # The decision lives in one testable place ...
    "object TunnelShutdownPolicy" in files["shutdownPolicy"]
    and "fun stopPlan(core: TunnelCore, datapathActive: Boolean): DatapathStop" in files["shutdownPolicy"]
    and "fun mayCloseDescriptor(datapathOwnsFd: Boolean): Boolean" in files["shutdownPolicy"]
    and "const val REASON_SIGNALED: Int = 2" in files["shutdownPolicy"]
    # ... and the service asks it instead of poking both cores.
    and "TunnelShutdownPolicy.stopPlan(core, live)" in files["vpn"]
    and "DatapathStop.QUIT_AND_JOIN" in files["vpn"]
    and "Tun2SocksTunnel.stopAndWait()" in files["vpn"]
    and "runCatching { HevTunnel.quit() }\n        runCatching { Tun2SocksTunnel.quit() }" not in files["vpn"]
    # One owner for the duplicated descriptor: adopted by the worker, released by it on every
    # path out, closed exactly once under a lock, deferred while the datapath still holds it.
    and "adoptDatapathFd(dupFd)" in files["vpn"]
    and "releaseDatapathFd(tunnelCore)" in files["vpn"]
    and "synchronized(hevFdLock)" in files["vpn"]
    and "TunnelShutdownPolicy.mayCloseDescriptor(datapathOwnsFd.get())" in files["vpn"]
    and 'diag.event(\n            "TUN", "fd-close",' in files["vpn"]
    # Stopping a HEV that never ran is what arms its sticky stop bit (and its abort()).
    and "if (hevActive) stopDatapath()" not in files["vpn"]
    # The forced exit is decoded in both reports, and the second datapath is on the timeline.
    and "TunnelShutdownPolicy.forcedExitReading(exit.reason, exit.status)" in files["bug"]
    and "TunnelShutdownPolicy.forcedExitReading(exit.reason, exit.status)" in files["diag"]
    and '"TUN2SOCKS |", "TUNNEL |"' in files["bug"]
    and "problems(tun2socksLog)" in files["bug"]
    # The rules are pinned by a device-free test and written down in a chapter.
    and "class Tun2SocksShutdownV223Test" in files["shutdownTest"]
    and "TUN2SOCKS_SHUTDOWN_V223.md" in files["readme"]
    and "MARBLE_TUN2SOCKS_SHUTDOWN_V223" in files["shutdownDoc"],
)
# MARBLE_FDSAN_DOUBLE_CLOSE_V224 — one TUN device, two numbers, one owner and one close per
# number. The Go half of this rule is asserted in the V224 datapath check above (and again, on
# the staged release source, by scripts/prepare-native.sh); what belongs here is everything
# around it: the HEV bridge applying the same borrowed-descriptor rule before an auto-bumped
# HEV can reopen the class on the default datapath, the policy marker the Kotlin side reads,
# and the test + chapter that keep the reasoning attached to the code.
check(
    "V224 both datapaths borrow the TUN descriptor and close only their own dup",
    "MARBLE_FDSAN_DOUBLE_CLOSE_V224" in files["hevJni"]
    and "F_DUPFD_CLOEXEC" in files["hevJni"]
    and "hev_socks5_tunnel_main_from_str((const unsigned char*)s,n,owned_fd)" in files["hevJni"]
    and "close(owned_fd)" in files["hevJni"]
    and "MARBLE_FDSAN_DOUBLE_CLOSE_V224" in files["t2sJni"]
    and "FD_OWNERSHIP_MARKER" in files["shutdownPolicy"]
    and "MARBLE_FDSAN_DOUBLE_CLOSE_V224" in files["shutdownPolicy"]
    and "fdsan double-close" in files["shutdownPolicy"]
    # The release build re-asserts the dup on the source it actually compiles, so a wrapper
    # regression fails the native build instead of a user's disconnect.
    and "MARBLE_FDSAN_DOUBLE_CLOSE_V224" in files["native"]
    and "fdbased.Open(strconv.Itoa(ownedFd)" in files["native"]
    and "class FdsanDoubleCloseV224Test" in files["fdsanTest"]
    and "FDSAN_DOUBLE_CLOSE_V224.md" in files["readme"]
    and "MARBLE_FDSAN_DOUBLE_CLOSE_V224" in files["fdsanDoc"],
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
# MARBLE_MODULAR_CUSTOMIZER_V151 — Home style 4 offers Home style 2's floating control, and its
# Customize affordance can be hidden with the way back kept in Settings.
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
# MARBLE_TOOLCHAIN_AUTOPILOT_V211 — the second core is compiled from pinned source by
# `scripts/prepare-native.sh`, which clones the fork itself into the bootstrap tree and verifies
# the commit before compiling; the release workflow no longer clones it in a step of its own, so
# the sing-box half of this invariant belongs to the script that does the work.
check(
    "sing-box is built from the pinned source commit, and the cache follows the pinned checksum",
    "singbox-src" in files["native"]
    and ".singbox.commit" in files["native"]
    and "sing-box source is not the pinned commit" in files["native"]
    and ".bootstrap/xray/go.sum" in build_go_cache,
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

# MARBLE_RELEASE_INSTALL_GATE_V217 — the install contract was pinned at the source level
# (V195) but never proven on the packaged artifact: apksigner validates that a signature is
# well-formed, not that it is the same identity as the release phones already carry, and
# nothing compared the packaged versionCode/versionName/extractNativeLibs/ABI payload with
# what the release claims to ship. The gate closes all four on every dist APK, plus the
# published-release baseline (strictly newer versionCode, same signing certificate unless
# release-signing.lock pins an intentional rotation), and the Gradle version properties
# fail loudly instead of silently falling back to a downgrade.
check(
    "release APKs prove the install contract before publishing",
    "Verify the APK install contract" in files["build"]
    and "MARBLE_RELEASE_INSTALL_GATE_V217" in files["build"]
    and '"$AAPT2" dump badging' in files["build"]
    and "extractNativeLibs" in files["build"]
    and "useLegacyPackaging=true must keep every lib/* entry DEFLATED" in files["build"]
    and "Signing identity changed" in files["build"]
    and "release-signing.lock" in files["build"]
    and "RELEASE_CERT_SHA256" in files["build"]
    and "not strictly newer" in files["build"],
)
check(
    "release version properties fail loudly instead of a silent downgrade",
    "MARBLE_RELEASE_INSTALL_GATE_V217" in files["gradle"]
    and "Release signing is configured but VERSION_CODE is missing" in files["gradle"]
    and "Release signing is configured but VERSION_NAME is missing" in files["gradle"],
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
    and "ResolverEvidencePolicy.withoutExcluded(candidates, evidence, nowMs)" in files["intel"]
    and "settings.measuredDnsExcludedEndpoints" in files["hardener"]
    and "ResolverEvidencePolicy.isDomesticResolver(url)" in files["hardener"]
    and "ResolverEvidencePolicy.isDomesticResolver(url)" in files["singBoxBuilder"]
    and 'settings.measuredSingBoxDnsParallel) "parallel" else "sequential"' in files["singBoxBuilder"]
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
# The hub header, as V210 draws it: the title, the tree's own state and the expand/collapse
# action. Scoping the invariant to the header (rather than to the whole 19k-line UI file) is what
# lets it keep saying something exact: the hub's title is one bare word with no version stamp
# under it, whatever else the page grows.

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
# any repo code was compiled. Every v4 call site in a live workflow must name surviving packages.
def _setup_android_pins_packages(wf: str) -> bool:
    return (
        wf.count("uses: android-actions/setup-android@v4") >= 1
        and wf.count("uses: android-actions/setup-android@v4")
        == wf.count('packages: "platform-tools"')
    )

check(
    "V168 CI: workflows never rely on the retired default 'tools' SDK package",
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
    and '"block", "direct", "dns-out"' in files["hardener"]
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


# MARBLE_SERVERS_HIERARCHY_V189 — the Servers page is two levels now, and the difference between
# them is the design: a subscription card (largest radius, boldest outline, largest type) and the
# servers nested inside it (smaller in every shared dimension, inset, borderless, hairline
# separated). A pure table owns the sizes and the plan arithmetic, the product reads that table,
# and a named test pins both — a nested row that grows back to the size of its card has to fail
# here before it can ship.

# MARBLE_VISUAL_REPAIR_V190 — the real-device review of Home and Servers.

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
# MARBLE_FAMILY_SCAN_UI_RETIRED_V220 — the scan UI is retired by product decision: no page may
# offer a scan, report a verdict or draw a family chip. The measurement backend (IpFamilyScanner,
# the ladder, the store, the repository entry points) still serves the tunnel and keeps its own
# pins; only the surface is gone.
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
# MARBLE_CORE_OPTIONS_V211
# ---------------------------------------------------------------------------
# The fragment feature is deleted, not disabled: no page, no card, no learner, no generated
# dialer, and no settings key. A user who wants a shape on the wire writes it in the core's own
# options — which is exactly what the second half of this block pins.

check(
    "V211 no writer constructs a fragment outbound any more",
    "fragmentOutbound" not in files["hardener"]
    and "tlsFragmentOutbound" not in files["hardener"]
    and 'fragmentPackets' not in files["hardener"]
    and 'fragmentLength' not in files["hardener"]
    and '"fragment-direct"' not in files["hardener"]
    and '"tls-fragment"' not in files["hardener"]
    and "fragmentEnabled" not in files["repo"]
    and "settleTransportSession" not in files["repo"]
    # The imported-hop guard survives, because a hand-imported chain is still the user's config.
    and '"block", "direct", "dns-out"' in files["hardener"],
)


check(
    "V211 the sing-box wire policy keeps the core's own field names",
    "object SingBoxTransportPolicy" in files["singBoxWirePolicy"]
    and 'const val MultiplexField: String = "multiplex"' in files["singBoxWirePolicy"]
    and "fun multiplex(" in files["singBoxWirePolicy"]
    and "applyFragment" not in files["singBoxWirePolicy"]
    and "record_fragment" not in files["singBoxWirePolicy"]
    and 'val MARBLE_SMUX_PROTOCOLS: Set<String> = setOf("vless", "vmess", "trojan", "shadowsocks")'
    in files["singBoxBuilder"]
    and "protocol in MARBLE_SMUX_PROTOCOLS" in files["singBoxBuilder"],
)

check(
    "V211 the extra-JSON escape hatch is a total refusal or a clean merge",
    "protectedKeys: Set<String> = PROTECTED_KEYS" in files["coreOptions"]
    and "PROTECTED_KEYS: Set<String> = setOf(" in files["coreOptions"]
    # sing-box's routing block is called `route`, so the builder passes its own set: the Xray
    # names would have guarded the wrong key and left the computed rule graph replaceable.
    and "SINGBOX_PROTECTED_KEYS: Set<String> = setOf(" in files["coreOptions"]
    and "CoreOptions.SINGBOX_PROTECTED_KEYS" in files["singBoxBuilder"]
    and "applyXrayRoutingStrategy" not in files["hardener"]
    and "extra-json-refused" in files["hardener"]
    and "extra-json-refused" in files["singBoxBuilder"]
    and "extra-json-merged" in files["hardener"]
    and "extra-json-merged" in files["singBoxBuilder"],
)

check(
    "V211 the chapter is written down",
    "MARBLE_CORE_OPTIONS_V211" in files["coreOptionsDoc"]
    and "CORE_OPTIONS_V211.md" in files["readme"],
)

# ---------------------------------------------------------------------------
# MARBLE_HOME_ONE_PING_V208 / MARBLE_HOME_ROUTE_PING_V210 / MARBLE_HOME_PING_CONTROLS_V212
# ---------------------------------------------------------------------------
# V208 collapsed the Home page to one ping verb and made the gauge and the deck pill displays;
# V210 kept that shape and changed the header's scope to the route on screen. V212 reopens the
# first decision, on the report that one of the two ping buttons on Home simply does nothing when
# it is pressed. The new contract is the inverse: every ping surface on the connection page is a
# control, and the verb it runs is a setting rather than a fact about its shape. What survives
# from V208/V210 is the part that was actually load-bearing — no transient status strip under the
# header, one door for the bulk sweep, and no widget inventing its own meaning for "ping".

check(
    "V208 the group ping writes its result into the route's own evidence",
    "private fun publishHomeRoutePing(" in files["repo"]
    and "publishHomeRoutePing(expanded)" in files["repo"]
    and "pingHomeGroup()" in files["repo"],
)


# ---------------------------------------------------------------------------
# MARBLE_SERVER_TILE_TRUTH_V210
# ---------------------------------------------------------------------------
# Two reports about the compact server boxes: a server that does not answer has to be told apart
# from one that does, and a long name must not grow the box it lives in. The first is a verdict
# over the measurement the page already holds; the second is a layout rule — one line, and a line
# that stretches to the row instead of the row stretching to it.

# ---------------------------------------------------------------------------
# MARBLE_FLOATING_ACTIONS_V210
# ---------------------------------------------------------------------------
# The chrome around a floating button was theme-aware; its face was not. An action is a token
# now, owned by the palette like the body, the hairline and the shadow — four verbs per theme,
# with the glyph ink scored against them rather than assumed white.

# ---------------------------------------------------------------------------
# MARBLE_SETTINGS_SECTIONS_V210
# ---------------------------------------------------------------------------
# Settings is a tree: main sections holding their own named sub-sections, closable from their
# headers. The state is one set of collapsed keys, owned above the page switch, and a section
# nobody has touched is open — so a section added later is open too.

# ---------------------------------------------------------------------------
# MARBLE_SETTINGS_TREE_V215 / MARBLE_TEXT_SCALE_V215 / MARBLE_HOME_PING_COLOUR_V215 /
# MARBLE_FLOATING_FACE_V215 / MARBLE_ATELIER_NOTICE_IN_CARD_V215 / MARBLE_PING_FIRST_VALUE_V215
# ---------------------------------------------------------------------------
check(
    "V215 the chapter is written down and pinned by its tests",
    "MARBLE_SETTINGS_TREE_V215" in files["settingsTreeDoc"]
    and "class TextScaleV215Test" in files["settingsTreeTest"]
    and "SETTINGS_TREE_TEXT_SCALE_V215.md" in files["readme"],
)

# The four V210 chapters are written down and pinned by tests, like every chapter before them.

# ---------------------------------------------------------------------------
# MARBLE_SETTINGS_ONE_LINE_COPY_V208
# ---------------------------------------------------------------------------
# Every settings description is one sentence. The rule is enforced where the text is drawn, so it
# also holds for copy that has not been written yet.

# ---------------------------------------------------------------------------
# MARBLE_SERVER_TILE_LAYOUT_V208
# ---------------------------------------------------------------------------


check(
    "V208 the four chapters of this pass are written down",
    all(
        marker in files["readme"]
        for marker in (
            "CORE_OPTIONS_V211.md",
            "SERVER_TILE_LAYOUT_V208.md",
            "HOME_ONE_PING_V208.md",
            "SETTINGS_ONE_LINE_COPY_V208.md",
        )
    )
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

# ---------------------------------------------------------------------------
# MARBLE_SERVER_TILE_PARITY_V212
# ---------------------------------------------------------------------------
# The compact box is an alternative silhouette for the same list, not a reduced server. V208 gave
# it its geometry and V210 its truth; this pins the two things the row had that the box did not —
# the measured address family and the server's own menu — because both are single lines a refactor
# can drop without changing any type.
# MARBLE_FAMILY_SCAN_UI_RETIRED_V220 — parity is now about the menu, not the family chip: the
# retirement removed the chip from both silhouettes at once, so neither answers what the other
# withholds. The shared menu slot is still the thing a refactor must not drop.

# ---------------------------------------------------------------------------
# MARBLE_MULTI_SOURCE_ROUTING_V212
# ---------------------------------------------------------------------------
# Routing reads a SET of geo databases, and a rule names a file — so the two invariants that keep
# this from breaking a connection are the canonical names on the primary (a fresh install routes
# from the bundled copy) and fail-closed emission (a token for a file that is not on disk is a
# config the core refuses to load).
check(
    "V212 routing reads a set of geo databases and fails closed",
    "fun tokensFor(settings: AppSettings, kind: Kind, tag: String): List<String>" in files["geoRegistry"]
    and 'const val PRIMARY_GEOIP_FILE: String = "geoip.dat"' in files["geoRegistry"]
    and 'const val PRIMARY_GEOSITE_FILE: String = "geosite.dat"' in files["geoRegistry"]
    and "const val MAX_SOURCES: Int = 6" in files["geoRegistry"]
    and "fun geoTokens(" in files["routingEngine"]
    and "if (file !in ready) return@filter false" in files["routingEngine"]
    and "settings.measuredGeoTagsByFile[file]" in files["routingEngine"]
    and "fun readyGeoAssetFiles(" in files["xray"]
    and "measuredGeoReadyFiles" in files["xray"]
    # The set is additive: enabling a source adds it, choosing a primary re-orders it, and neither
    # drops what was there — which is the defect the chapter exists to remove.
    and "fun withAdded(ids: List<String>, id: String): List<String>" in files["geoRegistry"]
    and "fun toggleGeoAssetSource(" in files["repo"]
    # The index answers which database a tag came from, so the simulator can say so.
    and "fun sourcesFor(" in files["geoIndex"]
    and "class GeoAssetRegistryV212Test" in files["geoRegistryTest"]
    and "class MultiSourceRoutingV212Test" in files["multiSourceTest"]
    and "MARBLE_MULTI_SOURCE_ROUTING_V212" in files["multiSourceDoc"]
    and "MULTI_SOURCE_ROUTING_V212.md" in files["readme"],
)

# The curated domestic layer is the product's own list, and it is a DIRECT rule set: it belongs
# only in the mode whose job is keeping domestic traffic here, and it is emitted last so the
# first-match-wins core takes a rule the user wrote over the product's opinion.
check(
    "V212 the domestic precision layer never outranks a rule the user wrote",
    "object IranPrecisionPack" in files["precisionPack"]
    and "fun matches(rawHost: String, precision: GeoPrecision): Boolean" in files["precisionPack"]
    # No CIDR ranges: a wrong Iranian block sends someone else's traffic out of the tunnel, and a
    # range this code cannot verify is a range it must not assert.
    and "0.0.0.0" not in files["precisionPack"]
    and "fun precisionActive(settings: AppSettings): Boolean" in files["routingEngine"]
    and "settings.routingMode == RoutingMode.GEO_DIRECT" in files["routingEngine"]
    and files["routingEngine"].index("applyPrecisionRules(rulesOut, settings, seen)")
    > files["routingEngine"].index("for (rule in emittableUserRules(settings))")
    # sing-box has no .dat, so it writes the same knowledge in its own idiom.
    and "domain_suffix" in files["singBoxBuilder"]
    and "applyPrecisionRules(rules, settings)" in files["singBoxBuilder"]
    and "class IranPrecisionPackV212Test" in files["precisionPackTest"],
)

# ---------------------------------------------------------------------------
# MARBLE_SURFACE_DEPTH_V212
# ---------------------------------------------------------------------------
# The V191 depth contract left HomeCloudCard and became a modifier any surface can wear, and the
# two full-screen dismissals answer the platform's predictive-back progress instead of snapping.

# ---------------------------------------------------------------------------
# Persistence and language for the whole pass
# ---------------------------------------------------------------------------
check(
    "V212 the preferences schema carries the new decisions",
    "const val PREFS_SCHEMA_VERSION = 3" in files["models"]
    and "if (from < 3)" in files["store"]
    and "geoAssetSourceIds" in files["store"]
    and "homePingGaugeAction" in files["store"]
    and "class HomePingControlsV212Test" in files["homePingActionTest"]
    and "MARBLE_HOME_PING_CONTROLS_V212" in files["homePingControlsDoc"]
    and "HOME_PING_CONTROLS_V212.md" in files["readme"],
)

check(
    "V212 the new controls speak Persian",
    '"Ping controls"' in files["persianLexicon"]
    and '"Measure every server"' in files["persianLexicon"]
    and '"Read several geo databases"' in files["persianLexicon"]
    and '"Domestic separation"' in files["persianLexicon"]
    and '"Latency gauge"' in files["persianLexicon"],
)

# ---------------------------------------------------------------------------
# MARBLE_NATIVE_ANDROID_V213
# ---------------------------------------------------------------------------
# Seven surfaces reported as "not Android": the boxes, the tabs, the servers, the flags, the
# settings, the options and the notifications. The chapter's answer is one measurement table, one
# component set that paints *only* platform roles, and a call-site sweep — so the invariant is
# that the table exists, that its numbers are the platform's, that the components read the scheme
# rather than a brand token, and that the surfaces which were rebuilt are still wearing them.



check(
    "V213 the notifications fill the fields the platform defines",
    "const val ACCENT = 0xFF0066CC.toInt()" in files["notifier"]
    and ".setColor(ACCENT)" in files["notifier"]
    and ".setSubText(statusSubText(text))" in files["notifier"]
    # V231 localizes the kind subtext: `kindSubText(kind)` is still the dictionary key, the
    # expression that reaches the builder is its translation.
    and ".setSubText(subText)" in files["notifier"]
    and "val subText = NotificationCopy.translate(kindLabel, language)" in files["notifier"]
    and ".setGroup(GROUP_ID)" in files["notifier"]
    and ".setLocalOnly(true)" in files["notifier"]
    and "setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)" in files["notifier"]
    and "R.drawable.ic_notification_stop" in files["notifier"]
    # A status channel that buzzes per live-stats update is the defect this removes.
    and "enableVibration(false)" in files["notifier"]
    and "setSound(null, null)" in files["notifier"]
    and "internal fun statusSubText(text: String): String" in files["notifier"]
    and "internal fun kindSubText(kind: SmartNotificationKind): String" in files["notifier"]
    # The platform's own back gesture and the keyboard's own resize, declared rather than worked
    # around.
    and 'android:enableOnBackInvokedCallback="true"' in files["manifest"]
    and 'android:windowSoftInputMode="adjustResize"' in files["manifest"],
)



# ---------------------------------------------------------------------------
# MARBLE_MERIDIAN_V221 — the flat chapter
# ---------------------------------------------------------------------------
# One switch has to reach every material. The failure this guards is not "flat looks wrong"; it is
# a build in which the new page toggles a local that only the cards V221 rebuilt happen to read,
# leaving half the product lifted while the other half is flat.



check(
    "V221 the power profiles are a bounded projection over the effective settings",
    "object CorePower" in files["corePower"]
    and "fun tune(" in files["corePower"]
    and "if (profile == PowerProfile.BALANCED) return base" in files["corePower"]
    and "fun muxSafeFor(" in files["corePower"]
    and "CorePower.tune(" in files["repo"]
    and "CorePower.muxSafeFor(profile)" in files["repo"]
    and "class CorePowerV221Test" in files["corePowerTest"]
    # No profile may touch identity: the projection reads them, never writes them.
    and "routingMode = " not in files["corePower"]
    and "dnsHijackEnabled = " not in files["corePower"]
    and "strictAddressFamily = " not in files["corePower"],
)


check(
    "V221 handshake fragmentation emits only the fork's client-side keys and never spoof",
    "const val RECORD_KEY: String = \"record_fragment\"" in files["fragmentPolicy"]
    and "const val FRAGMENT_KEY: String = \"fragment\"" in files["fragmentPolicy"]
    and "const val FALLBACK_KEY: String = \"fragment_fallback_delay\"" in files["fragmentPolicy"]
    and "FragmentPolicy.applyTo(tls, settings, quic = quicTransport, notes = notes)"
    in files["singBoxBuilder"]
    and "spoof" not in files["fragmentPolicy"].replace("`spoof`", "").replace("spoof_method", "")
    # REALITY is excluded because its ClientHello is the camouflage.
    and 'if (tls.optJSONObject("reality") == null)' in files["singBoxBuilder"],
)

check(
    "V221 the warm cache is bounded, expires and never caches a failure",
    "object ConnectWarmup" in files["connectWarmup"]
    and "const val TTL_MS: Long = 90_000L" in files["connectWarmup"]
    and "const val MAX_HOSTS: Int = 64" in files["connectWarmup"]
    and "if (resolved.isNotEmpty()) {" in files["connectWarmup"]
    and "if (key.isBlank() || addresses.isEmpty()) return" in files["connectWarmup"]
    and "ConnectWarmup.cached(host)" in files["probe"]
    and "ConnectWarmup.remember(host, candidates)" in files["probe"]
    and "warmRoutes()" in files["repo"]
    and "class MeridianV221Test" in files["meridianTest"],
)

check(
    "V221 Happy Eyeballs obeys its own setting instead of a hard-coded stagger",
    'val happyEyeballsDelayMs = settings.happyEyeballsTryDelayMs' in files["probe"]
    and "settings.adaptiveHappyEyeballsEnabled" in files["probe"]
    and "happyEyeballsDelayMs" in files["probe"],
)

check(
    "V221 the new options are persisted through the same normalisers that read them",
    'prefs.getString("accentTheme"' in files["store"]
    and 'prefs.getString("powerProfile"' in files["store"]
    and 'prefs.getString("fragmentStrategy"' in files["store"]
    and 'putString("accentTheme", parseFlatAccent(s.accentTheme).id)' in files["store"]
    and 'putString("powerProfile", parsePowerProfile(s.powerProfile).id)' in files["store"]
    and 'putString("fragmentStrategy", parseFragmentStrategy(s.fragmentStrategy).id)' in files["store"]
    and 'putBoolean("flatInterface", s.flatInterface)' in files["store"]
    and 'putBoolean("warmupOnLaunch", s.warmupOnLaunch)' in files["store"]
    and 'putBoolean("censorshipAutoApply", s.censorshipAutoApply)' in files["store"],
)


check(
    "V221 the chapter is written down and the new strings speak Persian",
    "MARBLE_MERIDIAN_V221" in files["meridianDoc"]
    and "MERIDIAN_V221.md" in files["readme"]
    and '"Censorship Lab" to "' in files["persianLexicon"]
    and '"Flat surfaces" to "' in files["persianLexicon"]
    and '"Accent" to "' in files["persianLexicon"]
    and '"Power" to "' in files["persianLexicon"],
)


production = "\n".join(
    value for key, value in files.items()
    if key not in {"build", "verify", "native"}
)

check(
    "V221 Automatic fragmentation is a network decision, not a document key",
    "fun effectiveStrategyId(storedStrategyId: String, censored: Boolean): String"
    in files["fragmentPolicy"]
    and "FragmentStrategy.AUTO -> Spec()" in files["fragmentPolicy"]
    and "FragmentPolicy.effectiveStrategyId(tuned.fragmentStrategy, censored)" in files["repo"],
)

check("no GlobalScope in production", "GlobalScope" not in production)


# MARBLE_IOS_DESIGN_SYSTEM invariants
check(
    "legacy Aether/Meridian UI files are removed",
    not (ROOT / "app/src/main/java/com/marbleng/app/ui/Aether2026.kt").exists()
    and not (ROOT / "app/src/main/java/com/marbleng/app/ui/AetherTheme.kt").exists()
    and not (ROOT / "app/src/main/java/com/marbleng/app/ui/MarbleDesignSystem.kt").exists()
    and not (ROOT / "app/src/main/java/com/marbleng/app/ui/MarbleMeridian.kt").exists(),
)

check(
    "iOS design tokens enforce #F8F9FA canvas, #FFFFFF 20dp cards, 12dp squircles and 1dp dividers",
    "const val BACKGROUND_HEX: Long = 0xFFF8F9FA" in files["iosTheme"]
    and "const val CARD_HEX: Long = 0xFFFFFFFF" in files["iosTheme"]
    and "const val CARD_CORNER_DP: Int = 20" in files["iosTheme"]
    and "const val SQUIRCLE_CORNER_DP: Int = 12" in files["iosTheme"]
    and "const val DIVIDER_THICKNESS_DP: Int = 1" in files["iosTheme"]
    and "#F8F9FA" in files["colorsXml"]
    and "@color/ios_background" in files["stylesXml"],
)

check(
    "iOS list item uses right-aligned squircle glyph, bold title, gray subtitle and left chevron",
    "fun IosListItem(" in files["iosComponents"]
    and "MarbleGlyph.CHEVRON_LEFT" in files["iosComponents"]
    and "LayoutDirection.Rtl" in files["iosComponents"]
    and "fun IosDivider(" in files["iosComponents"],
)

check(
    "MarbleApp wraps language, text scale and MarbleIosTheme and binds all modular tabs",
    "ProvideMarbleLanguage(repo.settings.appLanguage)" in files["marbleApp"]
    and "ProvideMarbleTextScale(repo.settings.textScalePercent)" in files["marbleApp"]
    and "MarbleIosTheme(" in files["marbleApp"]
    and "HomeScreen(" in files["marbleApp"]
    and "ServersScreen(" in files["marbleApp"]
    and "CustomSlotScreen(" in files["marbleApp"]
    and "SettingsScreen(" in files["marbleApp"]
    and "ServerDetailPage(" in files["marbleApp"]
    and "ServerAddSheet(" in files["marbleApp"],
)

check(
    "SettingsScreen dispatches all 20 modular iOS sub-pages",
    all(
        page in files["settingsScreen"]
        for page in (
            "SettingsConnectionModePage",
            "SettingsCoreEnginePage",
            "SettingsTunnelDatapathPage",
            "SettingsPowerProfilePage",
            "SettingsRoutingPage",
            "SettingsDnsAndFamilyPage",
            "SettingsSplitTunnelPage",
            "SettingsCensorshipLabPage",
            "SettingsPingTestingPage",
            "SettingsAutoSelectorPage",
            "SettingsIdentityLocationPage",
            "SettingsAppearancePage",
            "SettingsLanguageAndFontPage",
            "SettingsTabBarPage",
            "SettingsHomeLayoutPage",
            "SettingsSubscriptionsPage",
            "SettingsBackupRestorePage",
            "SettingsNotificationsPage",
            "SettingsDiagnosticsPage",
            "SettingsAboutPage",
        )
    ),
)

# ---------------------------------------------------------------------------
# MARBLE_NOTIFICATION_COUNCIL_V231 — the notification app, audited by five experts
# ---------------------------------------------------------------------------
# The critique's verdict was that the notification *system* existed but the notification *app* did
# not: one English voice for a bilingual product, three channels for five kinds of event, no
# grouping, no state, no count, an unbounded cooldown table, and a settings page that described a
# notification it never showed. The invariants below pin the rebuilt pieces, each one traceable to a
# finding in `docs/NOTIFICATION_COUNCIL_V231.md`.

check(
    "V231 the channel taxonomy is one table of four channels",
    "enum class NotificationChannelKey(val id: String)" in files["notificationCopy"]
    and 'CONNECTION("marbleng-vpn")' in files["notificationCopy"]
    and 'ALERTS("marbleng-smart")' in files["notificationCopy"]
    and 'PRIVACY("marbleng-privacy")' in files["notificationCopy"]
    and 'UPDATES("marbleng-updates")' in files["notificationCopy"]
    and "val menuOrder: List<NotificationChannelKey> = listOf(CONNECTION, ALERTS, PRIVACY, UPDATES)"
    in files["notificationCopy"]
    # Android stores the ids on the installed device: a renamed channel is a second channel.
    and 'const val CHANNEL_CONNECTION = "marbleng-vpn"' in files["notifier"]
    and 'const val CHANNEL_PRIVACY = "marbleng-privacy"' in files["notifier"],
)

check(
    "V231 one word per connection state, in both languages",
    "enum class ConnectionStatus" in files["notifier"]
    and "fun statusTitle(status: ConnectionStatus, language: NotificationLanguage): String"
    in files["notificationCopy"]
    and "fun statusSubText(status: ConnectionStatus, language: NotificationLanguage): String"
    in files["notificationCopy"]
    and "data class ConnectionNotificationState(" in files["notifier"]
    and "fun signature(language: NotificationLanguage): String" in files["notifier"],
)

check(
    "V231 the ongoing item is built from state, not from a joined string",
    "fun connectionNotification(" in files["notifier"]
    and "state: ConnectionNotificationState" in files["notifier"]
    # MARBLE_SHADE_ATELIER_V234 — the chronometer is a view in the drawn layout now.
    and "views.setChronometer(" in files["shadeView"]
    and "builder.setUsesChronometer(false)" in files["notifier"]
    and ".setProgress(0, 0, true)" in files["notifier"]
    and "R.drawable.ic_notification_reconnect" in files["notifier"]
    and "ACTION_RECONNECT_LAST" in files["notifier"]
    and "ACTION_RECONNECT_LAST" in files["main"]
    # A notification may not write its own metrics: the service hands over the state.
    and "private fun connectionState(" in files["vpn"]
    and "private fun publishStatus(" in files["vpn"]
    and "private fun promoteStatus(" in files["vpn"]
    and "publishStatus(ConnectionStatus.PROTECTED)" in files["vpn"]
    and "notifier.updateConnection(NOTIFY, connectionState(" in files["vpn"]
    and "SmartNotifier.formatRate(" not in files["vpn"],
)

check(
    "V231 an unchanged status never reaches SystemUI",
    "class ConnectionSignatureGate" in files["notificationCopy"]
    and "connectionGate.allow(state.signature(language), now)" in files["notifier"]
    and "if (!connectionGate.allow(" in files["notifier"],
)

check(
    "V231 alerts are grouped, counted, bounded and id-stable",
    "fun postSummary(language: NotificationLanguage)" in files["notifier"]
    and ".setGroupSummary(true)" in files["notifier"]
    and "class NotificationCooldown" in files["notificationCopy"]
    and "private val cooldowns = NotificationCooldown()" in files["notifier"]
    and "class NotificationIdAllocator" in files["notificationCopy"]
    and "private val ids = NotificationIdAllocator(" in files["notifier"]
    # The unbounded table and the hash-only id are what the council removed.
    and "ConcurrentHashMap<String, Long>" not in files["notifier"]
    and "eventKey.hashCode()" not in files["notifier"],
)

check(
    "V231 privacy has its own channel and can be told to stay quiet",
    "NotificationManager.IMPORTANCE_HIGH" in files["notifier"]
    and "NotificationChannelKey.PRIVACY" in files["notifier"]
    and "if (s.notifyPrivacyHeadsUp) CHANNEL_PRIVACY else CHANNEL_SMART" in files["notifier"]
    and "val notifyPrivacyHeadsUp: Boolean = true" in files["models"]
    and 'prefs.getBoolean("notifyPrivacyHeadsUp", true)' in files["store"]
    and '.putBoolean("notifyPrivacyHeadsUp", s.notifyPrivacyHeadsUp)' in files["store"],
)

check(
    "V231 the session clock is a switch and a platform chronometer",
    "val notificationSessionTimer: Boolean = true" in files["models"]
    and 'prefs.getBoolean("notificationSessionTimer", true)' in files["store"]
    and '.putBoolean("notificationSessionTimer", s.notificationSessionTimer)' in files["store"]
    and "showTimer = current.notificationSessionTimer" in files["vpn"]
    and "Session Clock" in files["settingsSystem"]
    # MARBLE_SHADE_ATELIER_V234 — the same switch now drives the chronometer *inside* the drawn
    # view, and the header's own clock is switched off so the item never shows two of them.
    and "builder.setUsesChronometer(false)" in files["notifier"]
    and "fun clockVisible(status: ConnectionStatus, showTimer: Boolean): Boolean"
    in files["shadeDesign"],
)

check(
    "V231 a Persian app posts Persian notifications, bidi-safe",
    "fun languageFor(appLanguageId: String?, deviceLanguage: String?): NotificationLanguage"
    in files["notificationCopy"]
    and "fun isolate(text: String): String" in files["notificationCopy"]
    and "const val RLM: Char = " in files["notificationCopy"]
    and "fun translate(text: String, language: NotificationLanguage): String"
    in files["notificationCopy"]
    and "private val FaCopy: Map<String, String>" in files["notificationCopy"]
    # The channel table is rewritten when the language changes, so Android's own screen agrees.
    and "fun ensureChannels(language: NotificationLanguage = resolveLanguage())" in files["notifier"]
    and "channelsLanguage == language" in files["notifier"]
    and "NotificationCopy.channelTitle(NotificationChannelKey.CONNECTION, language)"
    in files["notifier"]
    and "SmartNotifier(context) { settings.appLanguage }" in files["repo"]
    and "SmartNotifier(this) {" in files["vpn"],
)

check(
    "V231 a notification tap opens the page that can act on it",
    "const val EXTRA_PAGE = " in files["notifier"]
    and "private fun openAppIntent(route: NotificationRoute, language: NotificationLanguage)"
    in files["notifier"]
    and ".setData(Uri.parse(" in files["notifier"]
    # Distinct request codes per route: FLAG_UPDATE_CURRENT on one code would rewrite the extras
    # of every notification already in the shade.
    and "REQUEST_OPEN_BASE + route.ordinal" in files["notifier"]
    and "handleNotificationIntent(" in files["main"]
    and "app.repo.requestNotificationPage(page)" in files["main"]
    and "repo.consumeNotificationPage()" in files["marbleApp"]
    and "SettingsDestination.NOTIFICATIONS_BG" in files["marbleApp"],
)

check(
    "V231 the settings page shows what the shade will show",
    "fun SettingsNotificationsPage(" in files["settingsSystem"]
    and "private fun NotificationPreviewCard(repo: AppRepository)" in files["settingsSystem"]
    and "NotificationCopy.telemetryCompact(" in files["settingsSystem"]
    and "NotificationCopy.statusTitle(status, NotificationLanguage.EN)" in files["settingsSystem"]
    and "NotificationChannelKey.menuOrder.forEachIndexed" in files["settingsSystem"]
    and "Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS" in files["settingsSystem"]
    and "Settings.EXTRA_CHANNEL_ID" in files["settingsSystem"]
    and "s.notifyNetworkChanges" in files["settingsSystem"]
    and "s.notifyCoreUpdates" in files["settingsSystem"]
    and "s.notifyPrivacyHeadsUp" in files["settingsSystem"]
    and "s.notificationSessionTimer" in files["settingsSystem"]
    and "Turn on Smart Event Alerts to send a test" in files["settingsSystem"],
)

check(
    "V231 the switches that had no producer now have one",
    # `notifyCoreUpdates` was a switch no code ever raised an alert for.
    "notifier.alert(\n                    SmartNotificationKind.CORE" in files["repo"]
    and '"App update available"' in files["repo"]
    # The post-update notice was a silent-channel alert with no action.
    and "SmartNotifier(context) { language }.restoreNotice()" in files["receiver"]
    and "fun restoreNotice(language: NotificationLanguage = resolveLanguage())" in files["notifier"]
    and "NotificationCopy.actionRestore(language)" in files["notifier"],
)

check(
    "V231 the copy engine is tested on the host JVM",
    "class NotificationCopyV231Test" in files["notificationTest"]
    and "fun anUnchangedStatusNeverReachesSystemUi()" in files["notificationTest"]
    and "fun everyEventKeyKeepsAStableCollisionFreeId()" in files["notificationTest"]
    and "fun thePersianDictionaryTranslatesKnownCopyAndNeverTransliterates()"
    in files["notificationTest"]
    and "fun ellipsisNeverSplitsAGraphemeAndNeverLeavesADanglingWord()" in files["notificationTest"],
)

check(
    "V231 the council chapter is written down and linked",
    "MARBLE_NOTIFICATION_COUNCIL_V231" in files["notificationCopy"]
    and "NOTIFICATION_COUNCIL_V231.md" in files["readme"]
    and "## ۱. ترکیب شورا" in files["notificationDoc"]
    and "## ۴. جدول اجرا" in files["notificationDoc"],
)

# MARBLE_PERSIAN_DOCK_AND_SHADE_COUNCIL_V232 — the council's voted corrections.
check(
    "V232 Persian unknowns stay readable instead of being transliterated",
    "return text" in files["persianLexicon"]
    and "faTransliterate" not in files["persianLexicon"]
    and "faFallback" not in files["persianLexicon"]
    and "unknownEnglishCopyIsNeverFalselyTransliterated" in files["persianTest"],
)

check(
    "V232 the bottom dock is selectable, persisted and defaults to attached",
    "enum class DockPresentation" in files["dockModel"]
    and "fun encodeDockPreference(size: DockSize, presentation: DockPresentation)" in files["dockModel"]
    and "encodeDockPreference(parseDockSize(" in files["dockStore"]
    and '"dockSize",' in files["dockStore"]
    and "parseDockPresentation(s.dockSize)" in files["marbleApp"]
    and "DockPresentation.FLOATING" in files["marbleApp"]
    and "Bottom Bar Position" in files["dockSettings"]
    and "sizeAndPresentationShareTheExistingPreferenceWithoutLoss" in files["dockTest"],
)

check(
    "V232 the connection shade owns route management",
    # MARBLE_NOTIFICATION_SWITCH_V234 — V232 shipped two actions that *opened* the pickers. The
    # council's request on 2026-10-26 (V234) was to do the switch inside the shade itself, so the
    # invariant keeps its subject — the shade can change the server and the subscription — and
    # asserts the stronger mechanism: two service verbs that move the live tunnel, plus the page
    # routes the shade can still deep-link to for the long tail (alerts, taps).
    "fun actionNextServer(language: NotificationLanguage)" in files["notificationCopy"]
    and "fun actionNextSubscription(language: NotificationLanguage)" in files["notificationCopy"]
    and "fun actionChangeServer(language: NotificationLanguage)" in files["notificationCopy"]
    and "fun actionChangeSubscription(language: NotificationLanguage)" in files["notificationCopy"]
    and "cycleIntent(NotificationCycleKind.SERVER)" in files["notifier"]
    and "cycleIntent(NotificationCycleKind.SUBSCRIPTION)" in files["notifier"]
    and "NotificationRoute.SERVERS -> IosRootTab.SERVERS" in files["marbleApp"]
    and 'SERVERS("servers")' in files["notificationCopy"],
)

check(
    "V232 the five-role review, Persian copy and regression tests are documented",
    "class PersianLocalizationV232Test" in files["persianTest"]
    and "class DockPresentationV232Test" in files["dockTest"]
    and "| نرگس |" in files["dockCouncilDoc"]
    and "رأی: **۵ از ۵**" in files["dockCouncilDoc"]
    and "PERSIAN_DOCK_AND_SHADE_COUNCIL_V232.md" in files["readme"],
)

# MARBLE_PERSIAN_CONNECT_DOCK_COUNCIL_V233 — Persian is complete, the three buttons are
# independent, and the dock line has a measured home.

def v233_lexicon_keys():
    """Every whole-string key of the base dictionary AND the warm-voice layer."""
    return set(
        re.findall(
            r'^\s*"((?:[^"\\]|\\.)*)"\s+to\s+',
            files["persianLexicon"],
            re.M,
        )
    )


v233_known = v233_lexicon_keys()
v233_known_lower = {key.lower() for key in v233_known}
v233_ui_sources = sorted(
    (ROOT / "app/src/main/java/com/marbleng/app/ui").glob("*.kt")
)
v233_trx_literal = re.compile(r'\btrx\(\s*"((?:[^"\\]|\\.)*)"\s*\)')
v233_message_literal = re.compile(r'\b(?:message|setRuntimeMessage)\s*(?:=\s*|\(\s*)"((?:[^"\\]|\\.)*)"')

v233_untranslated = set()
for v233_path in v233_ui_sources:
    if v233_path.name in ("MarblePersianLexicon.kt", "MarbleStrings.kt"):
        continue
    v233_source = v233_path.read_text(encoding="utf-8")
    for v233_pattern in (v233_trx_literal, v233_message_literal):
        for v233_literal in v233_pattern.findall(v233_source):
            if "$" in v233_literal or "\\" in v233_literal or not v233_literal.strip():
                continue
            if v233_literal in v233_known or v233_literal.lower() in v233_known_lower:
                continue
            v233_untranslated.add(v233_literal)

for v233_source_key in ("repo", "main"):
    for v233_literal in v233_message_literal.findall(files[v233_source_key]):
        if "$" in v233_literal or "\\" in v233_literal or not v233_literal.strip():
            continue
        if v233_literal in v233_known or v233_literal.lower() in v233_known_lower:
            continue
        v233_untranslated.add(v233_literal)

# The permission dialog builds its strings inside trx(if (...) ... else ...), so the single-literal
# scanner above cannot see them. They are named explicitly here.
v233_permission_copy = [
    "Allow VPN connection",
    "Allow notifications",
    "MarbleNG needs Android's VPN permission to establish a protected tunnel on this device.",
    "Allow status notifications so Android keeps your active VPN session visible and reliable.",
    "Your traffic stays private on-device and only leaves through the server you choose.",
    "You can customize or mute optional alerts anytime in Settings.",
]

check(
    "V233 every UI literal and every repository message has a real Persian sentence",
    len(v233_known) >= 1900
    and not v233_untranslated
    and all(copy in v233_known for copy in v233_permission_copy),
)

check(
    "V233 the friendly voice and the live-message engine are real layers",
    "private val FaFriendlyVoice" in files["persianLexicon"]
    and "private val FaEffectiveLexicon: Map<String, String> = FaLexicon + FaFriendlyVoice"
    in files["persianLexicon"]
    and "private fun faTranslateDynamic" in files["persianLexicon"]
    and "private val FaRuntimePatterns" in files["persianLexicon"]
    and 'private const val FA_SEGMENT = " • "' in files["persianLexicon"]
    and "fun faIsolate" in files["persianLexicon"]
    and '"Connect" to "بزن وصل شو"' in files["persianLexicon"]
    and "connect = \"بزن وصل شو\"" in files["strings"]
    and "readyToConnect = \"آمادهای؟ بزن بریم\"" in files["strings"],
)

check(
    "V233 language is provided to the whole window, not just one page",
    "ProvideMarbleLanguage(app.repo.settings.appLanguage)" in files["main"]
    and "ProvideMarbleTextScale(app.repo.settings.textScalePercent)" in files["main"]
    and "ConnectionPermissionDialog(" in files["main"]
    and "fun ProvideMarbleLanguage(" in files["strings"]
    and "LocalMarbleTextScaleApplied" in files["textScale"]
    and "trx(error)" in files["v233PermissionDialog"],
)

check(
    "V233 the Quick Settings tile speaks the selected language",
    "object QuickTileCopy" in files["v233QuickTileCopy"]
    and "NotificationCopy.languageFor(appLanguageId, deviceLanguage)" in files["v233QuickTileCopy"]
    and "QuickTileCopy.label(repo.state, language)" in files["tile"]
    and "QuickTileCopy.subtitle(repo.state, repo.stateDetail, lastName, language)" in files["tile"]
    and "QuickTileCopy.contentDescription(" in files["tile"]
    and '"MarbleNG • Connecting"' not in files["tile"]
    and '"Connect once in app"' not in files["tile"]
    and "class QuickTileCopyV233Test" in files["v233QuickTileTest"],
)

check(
    "V233 three connect buttons have three behaviours and three lights",
    "enum class ConnectDeckAction" in files["v233ConnectDeckModel"]
    and "enum class ConnectDeckLight" in files["v233ConnectDeckModel"]
    and "behavior = \"reconnect_last_or_auto\"" in files["v233ConnectDeckModel"]
    and "behavior = \"auto_selector_winner\"" in files["v233ConnectDeckModel"]
    and "behavior = \"local_proxy_only\"" in files["v233ConnectDeckModel"]
    and "fun isIndependent(): Boolean" in files["v233ConnectDeckModel"]
    and "fun ConnectDeckSection(" in files["v233ConnectDeckUi"]
    and "ConnectDeckLight.IGNITION" in files["v233ConnectDeckUi"]
    and "ConnectDeckLight.ORBIT" in files["v233ConnectDeckUi"]
    and "ConnectDeckLight.RIPPLE" in files["v233ConnectDeckUi"]
    and "rememberInfiniteTransition(" in files["v233ConnectDeckUi"]
    and 'item(key = "home-connect-deck")' in files["homeScreen"]
    and "ConnectDeckSection(" in files["homeScreen"]
    and "litDeckId" in files["homeScreen"]
    and "repo.runAutoServerSelection(reason = \"connect-deck\")" in files["homeScreen"]
    and "repo.setConnectionMode(ConnectionMode.LOCAL_PROXY)" in files["homeScreen"]
    and "class ConnectDeckActionV233Test" in files["v233DeckTest"],
)

check(
    "V233 the active dock line is measured, centred and no longer offset",
    "object DockIndicatorSpec" in files["v233DockSpec"]
    and "const val SLOT_DP: Int = 6" in files["v233DockSpec"]
    and "const val WIDTH_DP: Int = 18" in files["v233DockSpec"]
    and "fun allFit(): Boolean" in files["v233DockSpec"]
    and "fun DockActiveIndicator(visible: Boolean, color: Color)" in files["marbleApp"]
    and "DockIndicatorSpec.SLOT_DP.dp" in files["marbleApp"]
    and "DockIndicatorSpec.barHeightDp(size).dp" in files["marbleApp"]
    and "DockActiveIndicator(visible = isSelected, color = activeColor)" in files["marbleApp"]
    and "offset(y = 10.dp)" not in files["marbleApp"]
    and "Spacer(Modifier.height(3.dp))" not in files["marbleApp"]
    and "class DockIndicatorSpecV233Test" in files["v233DockTest"],
)

check(
    "V233 the five-role council, its copy, and its regression tests are recorded",
    "| نرگس |" in files["v233CouncilDoc"]
    and "رأی: **۵ از ۵**" in files["v233CouncilDoc"]
    and "## ۵. جدول اجرا" in files["v233CouncilDoc"]
    and "PERSIAN_CONNECT_DOCK_COUNCIL_V233.md" in files["readme"]
    and "class PersianCoverageV233Test" in files["v233PersianTest"]
    and "fun theLabelsThatUsedToStayEnglishNowHaveRealPersianSentences()" in files["v233PersianTest"],
)

# ---------------------------------------------------------------------------
# MARBLE_NOTIFICATION_SWITCH_V234 — the shade changes the server and the subscription itself
# ---------------------------------------------------------------------------

check(
    "V234 the route cycle is a pure decision with three rules",
    "MARBLE_NOTIFICATION_SWITCH_V234" in files["routeCycle"]
    and "data class CycleRoute(" in files["routeCycle"]
    and "enum class NotificationCycleKind(val id: String)" in files["routeCycle"]
    and "fun next(" in files["routeCycle"]
    and "fun nextServer(" in files["routeCycle"]
    and "fun nextSubscription(" in files["routeCycle"]
    and "fun routeLine(route: String, source: String): String" in files["routeCycle"]
    # The three rules: never dead, library order, an honest null.
    and "if (order.size <= 1) return nextServer(routes, currentId, currentSourceId)"
    in files["routeCycle"]
    and "if (list.size == 1) return null" in files["routeCycle"]
    and "class NotificationRouteCycleV234Test" in files["routeCycleTest"]
    and "fun aSingleNodeSubscriptionFallsBackToTheWholeLibrary()" in files["routeCycleTest"]
    and "fun aLibraryWithOneSubscriptionStillMoves()" in files["routeCycleTest"],
)

check(
    "V234 the shade's two verbs reach the running service, not a page",
    "const val ACTION_CYCLE_SERVER = " in files["vpn"]
    and "const val ACTION_CYCLE_SUBSCRIPTION = " in files["vpn"]
    and "ACTION_CYCLE_SERVER,\n                ACTION_CYCLE_SUBSCRIPTION -> {" in files["vpn"]
    and "private fun cycleRoute(kind: NotificationCycleKind)" in files["vpn"]
    and "repo.notificationCycleRoute(kind)" in files["vpn"]
    and "startConnection(target.id, target.subscriptionId, activeMode)" in files["vpn"]
    and "CYCLE_MIN_INTERVAL_MS" in files["vpn"]
    and "fun notificationCycleRoute(kind: NotificationCycleKind): ProxyProfile?" in files["repo"]
    and "NotificationRouteCycle.next(" in files["repo"]
    and "cycleIntent(NotificationCycleKind.SERVER)" in files["notifier"]
    and "cycleIntent(NotificationCycleKind.SUBSCRIPTION)" in files["notifier"]
    # Two verbs sharing a request code are one PendingIntent: the ids and the data URI keep them apart.
    and "REQUEST_CYCLE_BASE + kind.ordinal" in files["notifier"]
    and "marbleng://cycle/" in files["notifier"]
    and "ACTION_CYCLE_SERVER" in files["notifier"]
    and "ACTION_CYCLE_SUBSCRIPTION" in files["notifier"]
    # The old behaviour — a verb that only opened a page — must not come back on its own.
    and "openAppIntent(NotificationRoute.SERVERS, language)" not in files["notifier"]
    and "openAppIntent(NotificationRoute.SUBSCRIPTIONS, language)" not in files["notifier"]
    and "DETAIL_NO_OTHER_SERVER: String" in files["notificationCopy"]
    and "DETAIL_NO_OTHER_SUBSCRIPTION: String" in files["notificationCopy"],
)

check(
    "V234 the live subscription is part of the state and of its signature",
    "val subscriptionName: String = " in files["notifier"]
    and "append(subscriptionName).append('|')" in files["notifier"]
    and "subscriptionName = activeRouteSourceName()" in files["vpn"]
    and "private fun activeRouteSourceName(): String" in files["vpn"]
    and "NotificationRouteCycle.routeLine(route, source)" in files["notifier"]
    # A permission that cannot move anything still answers in the same item.
    and "publishStatus(lastPublishedStatus, detail = detail)" in files["vpn"]
    and "NotificationCopy.cycleEmptyEnglish(kind)" in files["vpn"],
)

# ---------------------------------------------------------------------------
# MARBLE_RTL_CONNECT_CONTROL_V234 — the Home connect control mirrors with the language
# ---------------------------------------------------------------------------

check(
    "V234 the Home connect control mirrors when the app is RTL",
    "MARBLE_RTL_CONNECT_CONTROL_V234" in files["slideSpec"]
    and "fun dragFraction(current: Float, dragAmountPx: Float, travelPx: Float, rtl: Boolean): Float"
    in files["slideSpec"]
    and "fun thumbOffsetPx(travelPx: Float, fraction: Float, rtl: Boolean): Int"
    in files["slideSpec"]
    and "fun released(fraction: Float): Boolean" in files["slideSpec"]
    and "val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl" in files["homeScreen"]
    and "SlideConnectSpec.thumbOffsetPx(travelPx, dragFraction, rtl)" in files["homeScreen"]
    and "SlideConnectSpec.dragFraction(" in files["homeScreen"]
    and "SlideConnectSpec.released(dragFraction)" in files["homeScreen"]
    and "if (rtl) MarbleGlyph.CHEVRON_LEFT else MarbleGlyph.CHEVRON" in files["homeScreen"]
    # The three signs that made the control unusable in Persian must stay out of the composable.
    and "(dragFraction + dragAmount / travelPx)" not in files["homeScreen"]
    and "(dragFraction * travelPx)" not in files["homeScreen"]
    and "dragFraction >= 0.76f" not in files["homeScreen"]
    and "class SlideConnectSpecV234Test" in files["slideTest"]
    and "fun theSameGestureCommitsInBothDirections()" in files["slideTest"],
)

# ---------------------------------------------------------------------------
# MARBLE_SHADE_ATELIER_V234 — the ongoing item is drawn, not assembled
# ---------------------------------------------------------------------------

v234_ltr_ids = set(re.findall(r'@\+id/(notif_[a-z_]+)', files["shadeLayout"]))
v234_rtl_ids = set(re.findall(r'@\+id/(notif_[a-z_]+)', files["shadeLayoutRtl"]))
v234_required_ids = {
    "notif_root",
    "notif_badge",
    "notif_title",
    "notif_route",
    "notif_clock",
    "notif_progress",
    "notif_telemetry_row",
    "notif_ping",
    "notif_down",
    "notif_up",
}
v234_badges = sorted(set(re.findall(r'R\.drawable\.(notif_badge_[a-z]+)', files["shadeView"])))

check(
    "V234 the ongoing item is drawn by a RemoteViews layout, not a paragraph",
    "MARBLE_SHADE_ATELIER_V234" in files["shadeDesign"]
    and "MARBLE_SHADE_ATELIER_V234" in files["shadeView"]
    and "fun badge(status: ConnectionStatus): ShadeBadge" in files["shadeDesign"]
    and "fun clockVisible(status: ConnectionStatus, showTimer: Boolean): Boolean"
    in files["shadeDesign"]
    and "fun progressVisible(status: ConnectionStatus): Boolean" in files["shadeDesign"]
    and "fun telemetryVisible(status: ConnectionStatus, showTelemetry: Boolean): Boolean"
    in files["shadeDesign"]
    and "fun chronometerBase(sessionStartedAtMs: Long, nowWallMs: Long, nowElapsedMs: Long): Long"
    in files["shadeDesign"]
    and "RemoteViews(context.packageName, layout)" in files["shadeView"]
    and "R.layout.notification_tunnel_rtl" in files["shadeView"]
    and "views.setChronometer(" in files["shadeView"]
    and "views.setTextViewText(" in files["shadeView"]
    and "views.setTextColor(" in files["shadeView"]
    and "NotificationCompat.DecoratedCustomViewStyle()" in files["notifier"]
    and "setCustomContentView(shade.compact)" in files["notifier"]
    and "setCustomBigContentView(shade.expanded)" in files["notifier"]
    # One style only: `setStyle` is singular, and a second call steals the decorator.
    and files["notifier"].index("NotificationCompat.DecoratedCustomViewStyle()")
    < files["notifier"].index("setCustomBigContentView(shade.expanded)")
    and "NotificationCompat.BigTextStyle()" not in files["notifier"].split(
        "fun connectionNotification(\n        state: ConnectionNotificationState", 1
    )[-1].split("fun connectionNotification(title: String", 1)[0]
    # The two-line rule the drawn view uses instead of a paragraph.
    and "val secondLine = if (detail.isNotEmpty()) detail else routeLine" in files["notifier"]
    and "class NotificationShadeDesignV234Test" in files["shadeTest"]
    and "fun theClockAndTheRailNeverShareTheShade()" in files["shadeTest"]
    and "fun theChronometerBaseIsMonotonicNotWallClock()" in files["shadeTest"],
)

check(
    "V234 the drawn layout is RemoteViews-safe and has a Persian twin",
    v234_ltr_ids == v234_rtl_ids
    and v234_required_ids <= v234_ltr_ids
    and 'android:layoutDirection="rtl"' in files["shadeLayoutRtl"]
    and 'android:layoutDirection="rtl"' not in files["shadeLayout"]
    # Only the views SystemUI can inflate: no ConstraintLayout, no custom widget, no Compose.
    and "<androidx." not in files["shadeLayout"]
    and "<androidx." not in files["shadeLayoutRtl"]
    and "ConstraintLayout" not in files["shadeLayout"]
    and "<Chronometer" in files["shadeLayout"]
    and "<ProgressBar" in files["shadeLayout"]
    and len(v234_badges) == 5
    and all(
        (ROOT / f"app/src/main/res/drawable/{name}.xml").is_file() for name in v234_badges
    )
    # The two new verbs have their own marks, on the same 24-unit grid.
    and 'android:viewportWidth="24"' in files["switchIcon"]
    and 'android:viewportWidth="24"' in files["sourceIcon"]
    and "ic_notification_switch" in files["notifier"]
    and "ic_notification_source" in files["notifier"],
)

check(
    "V234 the switch, the mirror and the drawing are recorded and linked",
    "MARBLE_NOTIFICATION_SWITCH_V234" in files["v234Doc"]
    and "| سارا |" in files["v234Doc"]
    and "## ۵. جدول اجرا" in files["v234Doc"]
    and "## ۶. آنچه عمداً انجام نشد" in files["v234Doc"]
    and "NOTIFICATION_SWITCH_AND_SHADE_V234.md" in files["readme"]
    and "MARBLE_NOTIFICATION_SWITCH_V234" in files["notificationCopy"],
)

# MARBLE_LATEST_VERSION_RULE_V235 — one update rule everywhere something updates: the newest
# *published* (non-draft) version wins by published_at, beta or release, with the version as the
# same-instant tie-break and (in the app) the downgrade guard. These checks exist so no surface
# can grow a second definition of "latest" again: no channel filter in the core updater, no
# stable-only endpoint in the app, no compatibility-line cap as the toolchain's target.
_v235_lock = json.loads(files["coreLock"])
_v235_cores = ("xray", "hev", "singbox", "tun2socks")
_v235_fixtures = (
    "XTLS__Xray-core",
    "heiher__hev-socks5-tunnel",
    "shtorm-7__sing-box-extended",
    "xjasonlyu__tun2socks",
)
check(
    "V235 the core updater picks by publication time, never by label",
    "MARBLE_LATEST_VERSION_RULE_V235" in files["coreUpdater"]
    and "select(.draft == false)" in files["coreUpdater"]
    and "(.published_at // .created_at" in files["coreUpdater"]
    and "sort_by([.at, (.tag | vkey)])" in files["coreUpdater"]
    # The retired channel filters must not come back as eligibility rules.
    and "prerelease == true" not in files["coreUpdater"]
    and "prerelease == false" not in files["coreUpdater"]
    and "xray_channel" not in files["coreUpdater"]
    and "--resolve-only" in files["coreUpdater"]
    and "MARBLE_RELEASES_FIXTURE_DIR" in files["coreUpdater"]
    and "::warning::" in files["coreUpdater"]
    and "resolve_singbox_commit" in files["coreUpdater"],
)

check(
    "V235 every branch of the one rule is proven offline in PR verification",
    "MARBLE_LATEST_VERSION_RULE_V235" in files["coreRuleTest"]
    and "expect xray" in files["coreRuleTest"]
    and "v26.10.21" in files["coreRuleTest"]
    and "2.20.0-beta1" in files["coreRuleTest"]
    and all(
        (ROOT / f"scripts/testdata/core-lock-rule/{name}.json").is_file()
        for name in _v235_fixtures
    )
    and "./scripts/test-core-lock-rule.sh" in files["verify"]
    and "scripts/inject-singbox-android-fix.py" in files["updateCores"],
)

check(
    "V235 the lock stamps the rule and carries full provenance for all four cores",
    _v235_lock.get("rule") == "latest-published"
    and all("channel" not in _v235_lock[c] for c in _v235_cores)
    and all(len(_v235_lock[c].get("commit", "")) == 40 for c in _v235_cores)
    and all("T" in _v235_lock[c].get("publishedAt", "") for c in _v235_cores)
    and all(isinstance(_v235_lock[c].get("prerelease"), bool) for c in _v235_cores)
    and '[[ "$(jq -r \'.rule\' core-lock.json)" == "latest-published" ]]' in files["verify"]
    and '[[ "$(jq -r \'.rule\' core-lock.json)" == "latest-published" ]]' in files["updateCores"],
)

check(
    "V235 the in-app update check runs the same one rule",
    "object LatestReleaseRule" in files["latestRule"]
    and "fun newest(candidates: List<Candidate>): Candidate?" in files["latestRule"]
    and "fun isUpdate(candidateTag: String, currentVersion: String): Boolean"
    in files["latestRule"]
    and "fun compareVersions(left: String, right: String): Int" in files["latestRule"]
    and "LatestReleaseRule.newest(candidates)" in files["repo"]
    and "LatestReleaseRule.isUpdate(tag, BuildConfig.VERSION_NAME)" in files["repo"]
    and "releases?per_page=30" in files["repo"]
    # The stable-only endpoint and the x.y.z-only parser were the old rule's two halves.
    and "releases/latest" not in files["repo"]
    and "parseStableSemver" not in files["repo"]
    and "class LatestReleaseRuleV235Test" in files["latestRuleTest"]
    and "fun aStablePublishedAfterABetaIsTheUpdate()" in files["latestRuleTest"]
    and "fun aBetaPublishedAfterAStableIsTheUpdate()" in files["latestRuleTest"]
    and "fun aDraftNeverWinsEvenWhenItIsNewest()" in files["latestRuleTest"],
)

check(
    "V235 the toolchain targets newest published with the line as compile fallback",
    "MARBLE_LATEST_VERSION_RULE_V235" in files["resolveToolchain"]
    and '"--in-line-only"' in files["resolveToolchain"]
    and "def resolve(offline: bool = False, in_line_only: bool = False)"
    in files["resolveToolchain"]
    and "--in-line-only --write" in files["updateToolchain"]
    and "steps.compile_newest.outcome == 'failure'" in files["updateToolchain"]
    and "python3 scripts/resolve-toolchain.py --write" in files["updateToolchain"]
    and ":app:compileDebugKotlin" in files["updateToolchain"]
    and "git push origin HEAD:main" in files["updateToolchain"],
)

# MARBLE_WEIGHT_LADDER_V235 — one weight per role, each rung a real bundled face. The spec is
# plain primitives in the theme file (JVM-pinnable), the family registers exactly those faces,
# and the ladder's ordering is asserted by the unit test these anchors keep alive.
check(
    "V235 the type ladder names one registered weight per role",
    "object MarbleWeightSpec" in files["iosTheme"]
    and "const val BODY: Int = 400" in files["iosTheme"]
    and "const val LABEL: Int = 500" in files["iosTheme"]
    and "const val TITLE: Int = 600" in files["iosTheme"]
    and "const val HEADLINE: Int = 700" in files["iosTheme"]
    and "const val DISPLAY: Int = 800" in files["iosTheme"]
    and "Font(R.font.vazirmatn_extrabold, FontWeight(MarbleWeightSpec.DISPLAY))"
    in files["iosTheme"]
    and "fontWeight = FontWeight(MarbleWeightSpec.DISPLAY)" in files["iosTheme"]
    and all(
        (ROOT / f"app/src/main/res/font/{name}.ttf").is_file()
        for name in (
            "vazirmatn_regular",
            "vazirmatn_medium",
            "vazirmatn_semibold",
            "vazirmatn_bold",
            "vazirmatn_extrabold",
        )
    )
    and "class MarbleWeightLadderV235Test" in files["weightLadderTest"]
    and "fun theLadderClimbsOneRungPerRole()" in files["weightLadderTest"]
    and "fun everyRungHasARealBundledFace()" in files["weightLadderTest"],
)

check(
    "V235 the rule and the ladder are recorded and linked",
    "MARBLE_LATEST_VERSION_RULE_V235" in files["v235Doc"]
    and "MARBLE_WEIGHT_LADDER_V235" in files["v235Doc"]
    and "## ۴. جدول اجرا" in files["v235Doc"]
    and "## ۵. آنچه عمداً انجام نشد" in files["v235Doc"]
    and "LATEST_VERSION_RULE_AND_WEIGHT_LADDER_V235.md" in files["readme"]
    and "MARBLE_LATEST_VERSION_RULE_V235" in files["latestRule"]
    and "MARBLE_WEIGHT_LADDER_V235" in files["iosTheme"],
)

# ---------------------------------------------------------------------------
# MARBLE_NETWORK_COUNCIL_V236 — twelve specialists, one scan, one applied plan
# ---------------------------------------------------------------------------
# The request was one button that studies the network the phone is actually on and then writes the
# best settings it can justify. These invariants pin the five promises that make that safe: the
# judgement is pure, the seats own named knobs only, no forbidden surface is reachable, the plan is
# bounded, and the page/report/test/chapter all exist and are linked.

_v236_fields = set(
    re.findall(r"^        ([A-Z0-9_]+)\((?:[^)]*)\),?$", files["council"], re.M)
)
_v236_seats = set(
    re.findall(
        r'^        ([A-Z_]+)\("(?:[^"]*)", "(?:[^"]*)"\)[,;]?$',
        files["council"].split("enum class CouncilSeat", 1)[1].split("\n    }", 1)[0],
        re.M,
    )
)

check(
    "V236 the council is a pure judgement over twelve seated specialists",
    "MARBLE_NETWORK_COUNCIL_V236" in files["council"]
    and "object NetworkCouncil" in files["council"]
    and "enum class CouncilSeat" in files["council"]
    and len(_v236_seats) == 12
    and "fun deliberate(" in files["council"]
    and "fun measure(evidence: NetworkEvidence): Metrics" in files["council"]
    and "fun resolve(proposals: List<Proposal>): List<Proposal>" in files["council"]
    and "fun normalize(settings: AppSettings): AppSettings" in files["council"]
    # Pure means pure: no Android type and no socket in the judgement, which is exactly what makes
    # `NetworkCouncilV236Test` possible on a host JVM.
    and "import android." not in files["council"]
    and "java.net" not in files["council"]
    and "System.currentTimeMillis" not in files["council"],
)

check(
    "V236 every seat owns a named knob and the forbidden surfaces have no knob at all",
    len(_v236_fields) >= 30
    and "enum class TuningField(val label: String, val kind: FieldKind)" in files["council"]
    and "TuningField.FRAGMENT" in files["council"]
    and "TuningField.MUX" in files["council"]
    and "TuningField.ADDRESS_FAMILY" in files["council"]
    # Routing, split tunnelling, identity, certificate verification and the DNS fail-closed
    # posture are not reachable by a plan: there is no field to write them with.
    and not any(
        banned in _v236_fields
        for banned in (
            "ROUTING",
            "SPLIT_TUNNEL",
            "IDENTITY",
            "CERTIFICATE",
            "ROUTE_BLOCK",
            "SOCKS_PORT",
            "LOCAL_PROXY_PORT",
            "ALLOW_UNENCRYPTED",
        )
    )
    and "theCouncilNeverTouchesRoutingIdentityOrSplitTunnelling" in files["councilTest"],
)

check(
    "V236 the constitution is enforced in code, not prose",
    # measured evidence outranks the operator table
    "inconclusive" in files["council"]
    and "IranNetworkRegistry.byOrganisation" in files["council"]
    # a clean path never buys the heavy treatment
    and "FragmentStrategy.AUTO" in files["council"]
    # mux never lands on a route that cannot carry it
    and "muxSafe" in files["council"]
    and "muxSafe = CorePower.muxSafeFor(route)" in files["repo"]
    # IPv6 is preferred, never switched off, to hide a dead AAAA
    and 'TuningField.IPV6,\n            "true"' in files["council"]
    and "PREFER_IPV4" in files["council"]
    and len(re.findall(r"fun seat[A-Z][A-Za-z]*\(", files["council"])) == 12,
)

check(
    "V236 the scan measures the link, the operator, the path and the device",
    "MARBLE_NETWORK_COUNCIL_V236" in files["councilScanner"]
    and "object NetworkScanner" in files["councilScanner"]
    and "fun captureEnvironment(context: Context)" in files["councilScanner"]
    and "TelephonyManager" in files["councilScanner"]
    and "ConnectivityManager" in files["councilScanner"]
    and "currentThermalStatus" in files["councilScanner"]
    and "DnsWireCodec.buildQuery" in files["councilScanner"]
    and "MultiVectorReachability.probe(" in files["councilScanner"]
    and "fun probeQuic()" in files["councilScanner"]
    and "fun probeMtu()" in files["councilScanner"]
    and "fun probePorts()" in files["councilScanner"]
    and "fun probeLatency()" in files["councilScanner"]
    and "fun dialFamilies()" in files["councilScanner"]
    # No bulk download: the throughput figure is the platform's own estimate.
    and "HttpURLConnection" not in files["councilScanner"]
    and "throughputDownKbps = environment.linkDownKbps" in files["councilScanner"]
    # Honest MTU: an unmeasured rung is reported as unmeasured.
    and "if (largest == 0) return 0 to 0.0" in files["councilScanner"]
    and "ACCESS_WIFI_STATE" in files["manifest"],
)

check(
    "V236 the plan lands through updateSettings and the verdict survives a restart",
    "fun startNetworkCouncilScan()" in files["repo"]
    and "fun applyNetworkCouncilPlan(" in files["repo"]
    and "NetworkCouncil.deliberate(" in files["repo"]
    and "updateSettings(target.settings)" in files["repo"]
    and "if (snapshot.networkCouncilAutoApply && plan.changes.isNotEmpty())" in files["repo"]
    and "fun loadCouncilReport(): CouncilReport?" in files["store"]
    and "fun saveCouncilReport(report: CouncilReport?)" in files["store"]
    and "networkCouncilAutoApply = prefs.getBoolean(\"networkCouncilAutoApply\", true)"
    in files["store"]
    and "networkCouncilAutoApply" in files["models"]
    and "MARBLE_NETWORK_COUNCIL_V236" in files["councilReport"]
    # The report is not settings: a restore must not resurrect an old network.
    and "networkCouncilReport" not in files["store"].split("fun createBackup", 1)[1].split(
        "fun loadProfiles", 1
    )[0],
)

check(
    "V236 the page is reachable from Settings and speaks Persian",
    "SettingsDestination.NETWORK_COUNCIL" in files["settingsScreen"]
    and "SettingsNetworkCouncilPage(" in files["settingsScreen"]
    and "fun SettingsNetworkCouncilPage(" in files["councilPage"]
    and "NetworkCouncil.CouncilSeat.entries.forEachIndexed" in files["councilPage"]
    and "plan.changes.forEachIndexed" in files["councilPage"]
    and "repo.startNetworkCouncilScan()" in files["councilPage"]
    and "repo.applyNetworkCouncilPlan()" in files["councilPage"]
    and "Network Council Scanner" in files["persianLexicon"]
    and "اسکنر شورای شبکه" in files["persianLexicon"]
    and "دوازده کرسی شورا" in files["persianLexicon"],
)

check(
    "V236 the chapter, the tests and the README record the pass",
    "MARBLE_NETWORK_COUNCIL_V236" in files["councilDoc"]
    and "## ۱. ترکیب شورا" in files["councilDoc"]
    and "## ۳. قانون اساسی شورا" in files["councilDoc"]
    and "## ۵. جدول اجرا" in files["councilDoc"]
    and "## ۶. آنچه عمداً انجام نشد" in files["councilDoc"]
    and "NETWORK_COUNCIL_SCANNER_V236.md" in files["readme"]
    and "class NetworkCouncilV236Test" in files["councilTest"]
    and "fun anInjectedResetBuysFullFragmentationAndPadding()" in files["councilTest"]
    and "fun theOperatorTableMayAdviseButNeverOverrulesACompletedHandshake()" in files["councilTest"]
    and "fun everyAppliedValueIsBoundedToWhatTheCoresAccept()" in files["councilTest"]
    and "fun theSavedVerdictRoundTripsThroughItsJsonForm()" in files["councilTest"]
    and "fun theCouncilNeverTouchesRoutingIdentityOrSplitTunnelling()" in files["councilTest"],
)

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
