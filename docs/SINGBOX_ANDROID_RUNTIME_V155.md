# SINGBOX ANDROID RUNTIME — V155

**Why `URL test (sing-box extended)` returned `reachable=0` forever, and the principled fix.**

Pinned core: `shtorm-7/sing-box-extended v1.14.0-extended-2.7.1` (`core-lock.json`).

---

## 1. The evidence

```
2026-09-07T16:22:27.832588Z | SINGBOX | start-result | ok=false | reason=sing-box rejected the config:
WARN `independent_cache` DNS option is deprecated ...
FATAL initialize network manager: create network monitor: netlink socket in Android is banned by Google
```

The URL test is not broken. It never ran. `RouteProbe.urlTestHook` asks the core for
`GET /proxies/{tag}/delay`, and when the core is not alive the app spawns a throwaway instance —
which died at exactly the same place. `reachable=0` was the *only* honest answer the probe could
give; the fault was one layer down.

The two lines above were also widely mis-read as "an Android limitation that needs root/ADB or the
official client". They are not. Both are **config bugs**, and both are decidable from the JSON
before a process is ever spawned.

## 2. Reading the core, not the error message

### 2.1 The netlink FATAL is conditional

`route/network.go` of the pinned core:

```go
usePlatformDefaultInterfaceMonitor := nm.platformInterface != nil &&
    nm.platformInterface.UsePlatformDefaultInterfaceMonitor()
enforceInterfaceMonitor := options.AutoDetectInterface          // ← the only source
if !usePlatformDefaultInterfaceMonitor {
    networkMonitor, err := tun.NewNetworkUpdateMonitor(logger)
    if !((err != nil && !enforceInterfaceMonitor) || errors.Is(err, os.ErrInvalid)) {
        if err != nil {
            return nil, E.Cause(err, "create network monitor")   // ← the FATAL
        }
        ...
```

and `sing-tun/monitor_linux.go`:

```go
if runtime.GOOS == "android" {
    netlinkSocket, err := unix.Socket(unix.AF_NETLINK, unix.SOCK_DGRAM, unix.NETLINK_ROUTE)
    if err != nil { return nil, ErrNetlinkBanned }
    ...
```

Read them together and the semantics are unambiguous:

* the banned netlink socket produces an **error**, not a panic;
* that error is **tolerated** — the core simply runs without an interface monitor — *unless*
  `enforceInterfaceMonitor` is true;
* `enforceInterfaceMonitor` has exactly one source: `route.auto_detect_interface`.

So a sing-box CLI child process runs perfectly well on a stock, unrooted Android device. The
"use root or the ADB user, or switch to the graphical client" advice in the error string is
addressed to configs that genuinely need interface monitoring (a `tun` inbound with `auto_route`,
`default_network_strategy`, DHCP DNS). MarbleNG needs none of them: **Android's own `VpnService`
owns the TUN**, and the core only ever serves a local `mixed` inbound that `hev-socks5-tunnel`
dials.

### 2.2 How the official Android client avoids it

SFA (`sing-box-for-android`) never spawns the binary. It links **libbox** and injects a
`PlatformInterface` whose `UsePlatformDefaultInterfaceMonitor()` returns true, so
`CreateDefaultInterfaceMonitor` is backed by `ConnectivityManager` instead of netlink. That is the
other valid answer to the same problem — and it is unavailable to a product that ships the
upstream release binary and runs it with `ProcessBuilder`. For that process model the correct fix
is the one above: never request the monitor.

### 2.3 An impending deprecation is `os.Exit(1)`, not a warning

`experimental/deprecated/stderr.go`:

```go
if !feature.Impending() {
    f.logger.Warn(feature.MessageWithLink())
    return
}
if feature.EnvName != "" {
    if enable, err := strconv.ParseBool(os.Getenv("ENABLE_DEPRECATED_" + feature.EnvName)); err == nil && enable {
        f.logger.Warn(feature.MessageWithLink())
        return
    }
    f.logger.Error(feature.MessageWithLink())
    f.logger.Fatal("to continuing using this feature, set environment variable ENABLE_DEPRECATED_" + feature.EnvName + "=true")
}
```

`Impending()` is true once `ScheduledVersion.Minor - Version.Minor <= 1`, and `log.Fatal` is
`os.Exit(1)` (`log/observable.go`). On a **1.14** core that already applies to:

| Note | Scheduled | Effect on 1.14 |
|---|---|---|
| `missing route.default_domain_resolver` (or `domain_resolver` in dial fields) | 1.14.0 | **fatal** |
| legacy domain-strategy options | 1.14.0 | **fatal** |
| outbound DNS rule item | 1.14.0 | **fatal** |
| `independent_cache`, `store_rdrc`, rule-set `download_detour`, implicit default HTTP client | 1.16.0 | warning |

`common/dialer/dialer.go` reports the first one whenever a dialer must resolve a domain and more
than one DNS transport exists. MarbleNG's resolver pool is plural by design, so **every** MarbleNG
config met that condition. Even with the netlink bug fixed, the next start would have exited on
this instead.

## 3. What changed

### 3.1 `SingBoxAndroidRuntime` (new)

The single place that states the contract for running the CLI from an app UID:

* `ANDROID_FORBIDDEN_ROUTE_KEYS` — `auto_detect_interface`, `default_network_strategy`,
  `default_network_type`, `default_fallback_network_type`, `default_fallback_delay`,
  `default_interface`, `default_mark`, `override_android_vpn`, `find_process`, `find_neighbor`,
  `dhcp_lease_files`.
* `ANDROID_FORBIDDEN_DIAL_KEYS` — `bind_interface`, `routing_mark`, `network_strategy`,
  `network_type`, `fallback_network_type`, `protect_path`.
* `ANDROID_FORBIDDEN_INBOUND_TYPES` — `tun`, `redirect`, `tproxy`.
* `ANDROID_FORBIDDEN_DNS_TYPES` — `dhcp`.
* `DEPRECATION_ENV` — all eleven `ENABLE_DEPRECATED_*` flags from the pinned core's
  `constants.go`, exported on every child process. This is the backstop: a deprecation MarbleNG
  has not seen yet degrades to a log line instead of killing the engine.
* `TMPDIR` / `HOME` — Go's `os.TempDir()` answers `/tmp`, which does not exist on Android, so
  anything the core spools (rule-set downloads, cache writes) failed with `no such file or
  directory`. Both now point inside the app sandbox.

### 3.2 `SingBoxConfigBuilder`

| Before | After | Why |
|---|---|---|
| *(no `route.default_domain_resolver`)* | `"default_domain_resolver": "dns-local"` | fatal on 1.14; the system resolver is the only correct dial-time answer — it can never depend on the tunnel it is helping to build |
| `cache_file.store_rdrc: true` | `cache_file.store_dns: true` | `store_rdrc` deprecated in 1.14, removal 1.16 |
| `rule_set[].download_detour: "direct"` | `rule_set[].http_client: { detour: "direct" }` + `http_clients` + `route.default_http_client` | `download_detour` deprecated in 1.14; the core rejects both spellings together. The promise is unchanged: rule sets are fetched *outside* the tunnel, because a fresh install has none |

Still, deliberately, absent: `auto_detect_interface`.

### 3.3 `SingBoxConfigDoctor` — reactive repair became a preflight

`repair()` only ran *after* `sing-box check` had already refused a config, and then guessed the fix
from a Go error string. For these two faults that is the wrong shape, because both are decidable
from the JSON. `hardenForAndroid()` is the new **preflight**: unconditional, idempotent, and run on
the connect config *and* the throwaway URL-test config, so the measurement path and the connect
path can no longer drift.

It strips every forbidden route/dial/inbound/DNS option, migrates `store_rdrc` → `store_dns` and
`download_detour` → `http_client`, and guarantees a resolvable `route.default_domain_resolver`
(appending a `local` transport when a foreign config has no non-tunnel resolver at all — pointing
at a proxied resolver would be a bootstrap loop, a subtler failure than the one being fixed).

`repair()` keeps everything it had, plus one migration it alone may make: when the core's rejection
names `http_client`, the rule-set plumbing is downgraded back to `download_detour`. Losing a modern
spelling beats losing the engine — but only when the core actually complained about it, so a fresh
config still round-trips through `repair()` untouched.

### 3.4 `SingBoxManager`

* every process is created by `spawn()`, so no call site can forget the runtime contract;
* `checkConfig()` now checks a **diagnostic copy** with `log.output` removed. That one key was
  hiding the answer: with it set the core writes its warnings, DNS errors and fatal exits to the
  runtime log file while the pipe MarbleNG reads gets only the last cobra line. The copy differs
  from the real config in the log sink alone, so the verdict is still the verdict;
* `explain()` appends a remediation to a netlink ban or an `ENABLE_DEPRECATED_*` exit instead of
  surfacing raw Go text;
* preflight-hardening notes are surfaced through `lastSelfHealNotes` exactly like self-heal notes,
  so a config that had to be repaired is visible in the log rather than silent.

### 3.5 `BugFinder`

A new `SINGBOX ANDROID RUNTIME CONTRACT` section answers the first two triage questions directly:
does the runtime config still request an interface monitor, and was a netlink ban seen in the
retained core logs.

## 4. What this does *not* claim

* It does not make a `tun` inbound work inside the app sandbox. It cannot; that genuinely needs
  root/ADB, and the config doctor removes the inbound rather than letting the core die on it.
* It does not silence a *real* schema error. `ENABLE_DEPRECATED_*` only affects sing-box's own
  deprecation gate; an option the core cannot parse is still a rejection, and it is still reported
  verbatim.
* It does not invent a latency number. If the core still cannot start, the URL test still reports
  `reachable=0` — with the core's own reason plus a remediation attached, instead of a bare zero.

## 5. Pinned by

`app/src/test/java/com/marbleng/app/core/SingBoxAndroidRuntimeV155Test.kt`

* the config asks Android for no netlink interface monitor, and the only inbound is the local
  `mixed` endpoint;
* hardening strips every interface-monitor request from a hostile/foreign config, including a
  `tun` inbound and a `dhcp` DNS transport;
* `route.default_domain_resolver` is written, resolvable, and repaired when missing or dangling;
* `store_rdrc`/`independent_cache` are never written and are migrated when present;
* rule sets download through a named direct HTTP client, with a gated downgrade path;
* all eleven `ENABLE_DEPRECATED_*` flags are exported;
* the netlink ban and the deprecation exit are both classified as engine-level faults, so failover
  stops walking nodes that would all fail identically;
* hardening is a **no-op** on a config the builder wrote — the writer and the doctor must agree.
