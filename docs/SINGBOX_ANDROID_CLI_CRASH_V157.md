# SINGBOX ANDROID CLI CRASH — V157

**Why every profile died with `core-start: exited 2` and a SIGSEGV, why the product answered
"0 of 17 servers reachable", and the fix at both ends of that sentence.**

Pinned core: `shtorm-7/sing-box-extended v1.14.0-extended-2.7.1` at commit
`217f77643f19e915ea38ba2a9ac7e28e22a0cec5`, built by MarbleNG as
`1.14.0-extended-2.7.1-marble.crash-fix.1` (`core-lock.json`).

---

## 1. The evidence

Four reports from one device, one evening. They are the same fault wearing four vocabularies.

```
07:02:17  URL test    → reachable = 0 of 17 endpoints
07:02:23  Real delay  → reachable = 0 of 17 endpoints

10:30:31  Turkey 8    → core-start: exited 2: … pc=0x5c0a469b6c
10:30:56  Turkey 15   → core-start: exited 2: … pc=0x628ff65b6c
          state=BLOCKED • Core/configuration error • scan-finish | failures=1

10:32:24  ERROR/CRASH → WARN network: initialize package manager: read packages list:
                        open /data/system/packages.xml: permission denied
                        panic: runtime error: invalid memory address or nil pointer dereference
                        [signal SIGSEGV: segmentation violation code=0x1 addr=0x30 pc=0x5b24819b6c]

          Bug Finder (DISCONNECTED) → scan-finish | failures=0
```

Read together, they say one thing: **the core process could not start, so nothing was ever
measured.** No server was contacted at 07:02, no profile was misconfigured at 10:30, and the scan
at the end found nothing only because every check it runs about the core is gated on a connection
that a crashing core never establishes.

Three details in the log matter, and one of them is a trap:

* `exited 2` is what a Go **panic** exits with. `exited 1` is `log.Fatal` — a refusal. The product
  was matching the `core-start:` *prefix* and treating both as "the core rejected this config",
  which is why it kept offering the next reader and the next server.
* `addr=0x30` is a small constant offset: a method call on a nil receiver, not a corrupted heap.
* `pc` differs on every run (`0x5c0a…`, `0x628f…`, `0x5b24…`) because of ASLR, and the profile
  differs with it. Nothing may key on either — the two "different" crashes at 10:30 are the same
  instruction.
* The `packages.xml` WARN is **not** the cause. It is the fingerprint of the process model that
  produced the cause, and treating it as the fault is the misreading this document exists to close
  (§2.3).

## 2. Reading the core, not the error message

### 2.1 An app-UID CLI child has no interface monitor

MarbleNG runs sing-box the way the official Android client does not: as a plain `ProcessBuilder`
child of the app UID, with hev-socks5-tunnel owning the TUN. V155 established the config half of
that contract — no `tun` inbound, no `auto_detect_interface`, no `bind_interface`, nothing that
*asks* for a netlink interface monitor, because sing-tun refuses to open netlink sockets in an
Android app process ("banned by Google").

What V155 could not fix is the other half: the core still **dereferences** a monitor it does not
have. `route/network.go` leaves `interfaceMonitor` nil on that path, and four call sites assume it
is non-nil. A config can never satisfy them, because the nil is a property of the *build target*
(`GOOS=android`) and the *uid*, not of the JSON.

### 2.2 The panic site

`protocol/direct/outbound.go`, reached from the outbound start walk:

```go
func (h *Outbound) Start(stage adapter.StartStage) error {
	switch stage {
	case adapter.StartStatePostStart, adapter.StartStateStarted:
		if len(h.myAddresses.Load()) == 0 {
			h.fetchMyAddresses()
		}
	}
	return nil
}

func (h *Outbound) fetchMyAddresses() {
	myInterfaceNames := h.network.InterfaceMonitor().MyInterfaces()   // ← nil, addr=0x30
```

`SingBoxConfigBuilder` writes a `direct` outbound into **every** config it produces — bypass
routing, the DNS bridge, the rule-set HTTP client and `route.final` all need one. So the fault was
not "some profiles crash": it was 100% of profiles, on every ABI, in every build that shipped the
upstream Android artifact.

Note the ordering, because it decides how the failure can be observed: `box.Start()` brings up
inbounds at `StartStateStart` and only then walks outbounds through `StartStatePostStart`. The local
SOCKS port is therefore *listening* microseconds before the panic. Any start-up check that returns
as soon as the port answers can report success for a process that is already dying (§3.6).

### 2.3 The `packages.xml` WARN is evidence, not the fault

```go
// route/network.go
if C.IsAndroid && r.platformInterface == nil {
	packageManager, err := tun.NewPackageManager(tun.PackageManagerOptions{…})
	…
	err = packageManager.Start()          // → open /data/system/packages.xml: permission denied
```

`/data/system/packages.xml` is `0660 system:system`; an app UID can only ever get EACCES. sing-tun
logs it as a WARN and continues with no package manager, which is correct behaviour for MarbleNG —
process/package rules are not something the config writer ever emits.

So the line appears on **healthy** Android start-ups too. That has a consequence the classification
has to respect: a reason string containing the WARN says nothing about *why* the core stopped, and
counting it as fatal would turn genuine per-node schema refusals (whose log tail carries the same
WARN above the real `FATAL` line) into "this device is broken". `isPackageManagerFault` is
therefore evidence — a Bug Finder WARN, a remediation paragraph, the fingerprint that identifies a
pre-fix build — and is deliberately **not** part of `isUnusableCore`.

### 2.4 Why four previous layers could not catch it

| Layer | What it does | Why it did not fire |
| --- | --- | --- |
| V151 engine switch | explicit `Settings → Tunnel core` | a crash is not a user action; the switch is a contract, not a fallback |
| V152 `SingBoxConfigDoctor` | repairs known schema migrations, classifies engine-level faults | its markers are refusals (`parse config`, `deprecated in sing-box`); a panic matched none, and no repair of the JSON can add a nil guard to the binary |
| V155 `SingBoxAndroidRuntime` | removes every config option that *requests* a monitor | the crash is in the core's own unconditional dereference, not in a request |
| V156 link authority / candidate walk | offers every representation of a profile until one is accepted | all three readers produce the same `direct` outbound, so all three crashed — the walk multiplied the crash count by three and then reported it as three refusals |

CI did not catch it either, and could not have: the host acceptance tests ran the **linux-amd64**
release artifact on a Linux runner, where netlink works and the monitor is non-nil. The crashing
code path only exists for `GOOS=android` children of an app UID. That is why the fix has to include
a test that recreates the nil monitor on a Linux runner (§3.1).

## 3. What changed

### 3.1 `scripts/inject-singbox-android-fix.py` (new)

Backports upstream `288411b0b9044c11a00a8ab478000e3ec1133101` (SagerNet/sing-box, `testing`,
2026-09-06) into the pinned fork, and adds one guard upstream does not have:

| Site | Fault it closes |
| --- | --- |
| `protocol/direct/outbound.go` | **the reported crash**: `fetchMyAddresses` on a nil monitor |
| `route/network.go` | `DefaultNetworkInterface()` — the accessor behind the `network_type`, `network_is_expensive` and `network_is_constrained` rule items |
| `dns/transport/dhcp/dhcp.go` | DHCP transport registering a callback on a nil monitor (upstream) |
| `route/rule/rule_default_interface_address.go` | `default_interface_address` rule item (upstream) |
| `route/rule/rule_network_interface_address.go` | `network_interface_address` rule item (upstream) |

It writes two Go regression tests into the source tree —
`protocol/direct/marble_android_monitor_test.go` and `route/marble_android_monitor_test.go` — which
recreate the Android condition on any runner by stubbing a `NetworkManager` whose
`InterfaceMonitor()` returns nil. They are what makes the guard provable on Linux CI.

Every patch is **anchor-guarded**: it matches the exact surrounding source and exits 1 on drift, so
a fork bump that moves the code fails the build loudly instead of silently shipping an unpatched
core. Re-running it is a no-op (idempotent), which the CI smoke step asserts.

### 3.2 `scripts/prepare-native.sh` — the Android core is compiled, not downloaded

Section 5/5 no longer fetches `sing-box-*-android-*.tar.gz`. It clones the pinned repository, checks
out `.singbox.commit`, verifies `HEAD`, injects the backport, runs the Go tests on the host, and only
then builds all four ABIs:

```
clone → HEAD == .singbox.commit → inject → go test ./protocol/direct ./route
      → build_singbox ×4 ABI (GOOS=android, -buildmode=pie -trimpath -buildvcs=false,
         -ldflags "-X …/constant.Version=1.14.0-extended-2.7.1-marble.crash-fix.1 -s -w
                   -buildid= -checklinkname=0")
      → llvm-readelf Machine assert per ABI + version-marker assert → jniLibs
```

The two asserts are the point: a wrong-architecture binary or an unpatched tree cannot reach
`jniLibs`. `-s -w` is safe here because Go tracebacks come from `gopclntab`, which stripping keeps.

### 3.3 `core-lock.json` and `scripts/update-core-lock.sh`

The lock gained `.singbox.commit` (the exact source revision) and `.singbox.patch`
(`crash-fix.1` → the `-marble.crash-fix.1` version suffix), and its `sha256` map dropped every
Android digest: the only artifact still downloaded is `…-linux-amd64.tar.gz`, which the host
acceptance tests run. `update-core-lock.sh` resolves a tag to a commit (lightweight *and*
annotated), keeps `.patch`, and prints a NOTE on a tag bump — because a bump now means "the
injector's anchors must still match", which is exactly what the updater's dry-run gate checks.

### 3.4 CI

* `build.yml` clones the sing-box source into `.bootstrap/singbox`, caches both pinned Go modules
  (`cache-dependency-path` now lists two `go.sum` files), prints the fork's Go requirement, and has
  a 90-minute budget for a job that compiles a second core.
* `verify.yml` validates the new lock fields, asserts the source-build invariants in
  `prepare-native.sh`, and runs a **sing-box Android CLI crash-fix native smoke**: clone → verify
  `HEAD` → inject → grep the marker at all five sites → re-inject (idempotence) →
  `go test ./protocol/direct ./route`.
* `update-cores.yml` runs the injector as a dry run **before** it commits a new lock, so anchor
  drift kills the updater instead of publishing a lock nobody can build.

All three are staged in `docs/workflows-pending/` rather than committed to `.github/workflows/`:
the token this branch pushes with has no `workflows` permission, and GitHub rejects the push
outright rather than partially. The staged files are complete and derived from the live ones, the
README there is the replace table, and `scripts/system-integrity-check.py` reads the staged copy
when one exists — so these three are asserted now and take effect the moment a maintainer copies
them into place.

### 3.5 `SingBoxAndroidRuntime` — the classification

```
isCoreCrash(reason)          panic: | fatal error: | SIGSEGV | segmentation violation |
                             invalid memory address or nil pointer dereference |
                             unexpected fault address | goroutine 1 [running] |
                             goroutine stack exceeds | SIGABRT | runtime error: | "exited 2:"
isPackageManagerFault(reason) /data/system/packages.xml | initialize|read|create|start package manager
isUnusableCore(reason)       isCoreCrash || isNetlinkBan          ← the package-manager WARN is not here
explain(reason)              appends CRASH_REMEDIATION / PACKAGE_MANAGER_REMEDIATION /
                             NETLINK_REMEDIATION, idempotently; anything unrecognised passes through
```

Each remediation names the cause, says what this build does about it, and gives the one action a
user on an older APK has (`update MarbleNG`, or choose Xray-core in Settings). A raw Go traceback is
not an answer a person can act on.

### 3.6 `SingBoxCoreSelfTest` — ask the binary, not a server

A canary config, the smallest document that still walks the path the product depends on:

```json
{"log":{"level":"error","timestamp":true},
 "inbounds":[{"type":"mixed","tag":"socks-in","listen":"127.0.0.1","listen_port":PORT}],
 "outbounds":[{"type":"direct","tag":"direct"}],
 "route":{"final":"direct"}}
```

Same inbound type, same `direct` outbound, same `route.final` the production writer emits — and no
DNS transport, no controller, no cache file, no rule set. It cannot reach a network, so whatever it
reports is a property of the binary and the device. It is deliberately *not* a production config:
every extra construct would add a way for the canary to fail that has nothing to do with the
question, and a false "the core is broken" is worse than no canary at all.

Two implementation details carry the weight:

* **`settleMs`.** Because inbounds start before outbounds (§2.2), a start-up loop that returns the
  moment the port answers can PASS microseconds before the panic. `SingBoxProcessSession.open`
  therefore takes a grace window; the canary passes 700 ms and the live/measurement paths keep the
  default `0`, since they observe the child continuously and must not pay a grace period per server.
* **The verdict is cached against the binary's fingerprint** (`path|size|mtime`), so the cost is one
  process per installed core — one per APK, in practice. Only a *definitive* verdict is cached: a
  cancelled or inconclusive canary is re-asked, or one unlucky probe would disable the self-test
  until the next update.

`SingBoxManager.selfTest()` runs it; `requireUsableCore()` is called at the top of both `start()` and
`withTemporary()`, so a known-dead core costs **one** process instead of three readers × seventeen
nodes × retries. It short-circuits only on `Verdict.coreFault` — an inconclusive verdict is reported
and never blocks the engine, because that would be the canary inventing an outage of its own.

### 3.7 `ProbeLocalFaultGate` — one fault, not seventeen dead servers

The second latch a sweep obeys, next to V156's `ProbeCancelGate`:

* **Trips** on a fault that cannot change while the sweep runs: an unusable core, `core-install:`,
  `core-assets:`, `core-crash:`, `core-selftest:`, `core-exit:`, `core-port:`, `core-unavailable:`,
  `no-live-tunnel`.
* **Does not trip** on a per-node refusal (`core-config:`, `config-unsupported:` — the next profile
  is a different document), on capacity (`core-busy:`), on slowness (`core-start-timeout:`,
  `core-check-timeout:`), or on anything network-shaped (`urltest-*`), which is the sweep's actual
  subject matter. Anything that measured something (`success > 0`) never trips it.

`AppRepository` observes it in `markProbeResult` **on the worker, before posting to the main
thread** — posting first would let sibling workers pick up their next candidates while the message
was still queued, which is precisely how one broken core became seventeen dead servers. A trip arms
the cancel latch (one uniform stop for every sweep implementation), publishes `probeLocalFault`,
interrupts the worker, and keeps every measurement already made. The gate is reset at both ends of a
batch, so a fault found this morning cannot kill the sweep the user starts after fixing it.

The batch summaries now go through `batchSummary()`: the counts stay — they are real — but a stopped
batch says `• stopped: the tunnel core crashed while starting` instead of implying that seventeen
servers were tested and found dead.

### 3.8 The three predicates that had to agree

* `SingBoxManager.isConfigRefusal` excludes a crash: the document was accepted, the process then
  died, so the next reader would produce the same SIGSEGV. Exit 1 refusals still walk
  (`core-start: exited 1: FATAL parse config` stays a refusal, pinned by V156's test).
* `SingBoxConfigDoctor.isEngineLevelFault` now also returns true for `isUnusableCore`, delegating to
  the runtime object instead of copying panic strings into a second list that would drift.
* `CoreFailurePolicy.isLocal` gained `core-crash:` and `core-selftest:`, still by **prefix only**: a
  URL-test reason can carry a server's own response body, and classifying a remote string as a local
  fault would silently drop real observations.

### 3.9 `BugFinder` — the check that ignores app state

Every other core check in the scan is gated on a live route or a particular state, so a scan taken
while DISCONNECTED walked past all of them and printed `failures=0` with a SIGSEGV sitting in the
retained log it printed two sections later. The new `SingBox core start-up` check runs unconditionally
over the retained core logs plus the recorded self-test verdict: a crash is a FAIL with the panic line
as its headline (not the WARN above it), the package-manager probe alone is a WARN, and a clean device
says so. The scan reads evidence; it never spawns a canary of its own. `scan()` also takes
`lastProbeFault`, so a report can say why the last sweep stopped.

### 3.10 `MarbleVpnService` — name the owner, keep the contract

`handleFailure` gained a `faultClass` parameter. A core that cannot run on this device now blocks with
`Core cannot run on this device • …` instead of `Core/configuration error • …`, which is a claim about
the *profile*. The engine contract is untouched: **there is no automatic engine hand-off.** Switching
engines remains the user's explicit act (V151), and the invariant checker now asserts that
`activeEngine = CoreEngine.` appears nowhere in the service, so nobody can add one quietly.

### 3.11 `tools/kotlin-structure-check.py` — Kotlin block comments nest

This branch's first CI run did not fail on the crash fix. It failed on a KDoc.

Two prose lines in `SingBoxAndroidRuntime.kt` described the fault as happening on an
`` `android/*` `` build. Kotlin block comments **nest**, so each `/*` opened a second level, the
KDoc's own `*/` closed that level instead of the KDoc, and the comment swallowed the rest of the
file. `kotlinc` answered with:

```
e: …/SingBoxAndroidRuntime.kt:161:6  Syntax error: Missing '}.
e: …/SingBoxAndroidRuntime.kt:285:1 Syntax error: Unclosed comment.
e: …/BugFinder.kt:185:35            Unresolved reference 'isCoreCrash'.
e: …/ProbeLocalFaultGate.kt:189:35  Unresolved reference 'CRASH_REMEDIATION'.
e: …/SingBoxProcessSession.kt:134:39 Unresolved reference … receiver type mismatch
… thirty more, in seven files that were perfectly correct
```

Every "Unresolved reference" was the tail of one file that no longer existed as far as the compiler
was concerned — `isCoreCrash`, `explain`, `prepare`, the three remediation strings — and the
downstream ones (`child.inputStream.use`, `sink.read`, `compareTo`) were the second-order shadow of
`builder()` returning an error type. The two syntax lines were the whole diagnosis, and they were
buried under thirty misleading ones.

`tools/kotlin-structure-check.py` exists precisely to catch this class of mistake without a JDK, and
it reported `[OK]`: it skipped a block comment with `text.find("*/")`, which is the *Java* rule. It
now counts nesting levels, and treats a `/*` inside a comment as a hard error rather than a
curiosity, because in this codebase it is never intentional and its only effect is to delete code.

`scripts/system-integrity-check.py` — step 8 of `verify.yml`, which runs *before* the JDK is even
installed — loads that same tokenizer and runs it over every `app/src/**/*.kt`, so a source tree
that cannot tokenize is rejected in seconds, in the step whose output names the file and the line,
instead of two minutes later in the gradle step whose output names thirty innocent files. The check
is the tool, imported rather than reimplemented: two tokenizers would eventually become two opinions
about what a comment is, which is how this got through in the first place.

## 4. What this does not claim

* It does not make an app-UID child able to open netlink. It cannot; Android forbids it. The core now
  survives not having a monitor, which is a different statement.
* It does not silence the `packages.xml` WARN. That line is still printed, still harmless, and still
  reported as evidence — because a build that prints it and a build that crashes on it are the same
  build, and the WARN is how you tell an old APK from a new one.
* It does not invent a latency, a reachable count or a failure count. When the core cannot start, the
  sweep stops after one node and says the device could not measure; Bug Finder reports one FAIL.
* It does not switch engines, blacklist profiles, or mark servers dead for a local fault.
* It does not claim the canary proves connectivity. It proves start-up. A passing canary and a dead
  network are perfectly compatible, and the measurements still have to say which one you have.

## 5. Pinned by

`app/src/test/java/com/marbleng/app/core/SingBoxCoreCrashV157Test.kt`

* the three reported crashes classify identically whatever their `pc` was, and the bare `exited 2:`
  is enough on its own, because the retained log tail is line-limited;
* one crash arms the sweep gate once, and eight concurrent workers arm it exactly once;
* a per-node refusal **carrying the package-manager WARN** stays a per-node refusal;
* the reasons that must not stop a sweep (per-node, capacity, slowness, every `urltest-*`) do not;
* a crash travels with its remediation exactly once, and an unrecognised reason passes through
  verbatim;
* the headline is the `panic:` line, not the WARN above it;
* the canary mirrors the production constructs, stays offline, and rejects an invalid port;
* a verdict only short-circuits the product when it is a core fault;
* probing a missing or too-small binary is a fault report, not an exception and not a process;
* a verdict is cached against its binary and dies with it.

`app/src/test/java/com/marbleng/app/core/SingBoxNativeIntegrationTest.kt`

* the canary starts the pinned core and serves its local inbound, and its verdict round-trips through
  the cache — a canary the core refuses would report "broken device" on healthy hardware, which is a
  worse bug than the one it was written for;
* an unusable binary yields a `core-install:` verdict instead of a thrown exception.

Native and CI: the two Go tests the injector writes (`go test ./protocol/direct ./route`, run on the
host before any ABI is built and again in `verify.yml`'s crash-fix smoke step), the readelf and
version-marker asserts in `prepare-native.sh`, and the `MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157`
invariants in `scripts/system-integrity-check.py`.

Also in `scripts/system-integrity-check.py`: *"every Kotlin source tokenizes cleanly — comments nest
and terminate, literals close, braces balance"*, which runs `tools/kotlin-structure-check.py` over
all of `app/src` before the gradle step (§3.11). It is pinned by construction rather than by a test
file, and it was verified by reintroducing the `android/*` KDoc and watching the check fail with the
offending file and line.
