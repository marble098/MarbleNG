# MARBLE V156 — The link is the authority, the URL test has one owner, and a sweep can be stopped

Five things were wrong in the shipped build, and each one was a place where the product said one
thing and did another:

1. The sing-box engine ran a **translation of a translation** instead of the config the user
   subscribed to.
2. **Real delay** reported `FAILED` for every server you pinged before connecting, and took
   minutes to sweep a subscription when it did work.
3. **URL test** answered on both engines with two different measurements under one name.
4. A bulk ping that you started by mistake had **no undo**.
5. The Home header printed the session state **next to the logo**, a third place saying what the
   status banner underneath already says in full.

---

## 1. The share link is the authority (`MARBLE_SINGBOX_LINK_AUTHORITY_V156`)

Every node in MarbleNG carries two copies of the same truth:

| Field | What it is |
|---|---|
| `raw` | the share link the user subscribed to — `vless://…#Node A` |
| `configJson` | the Xray JSON Marble derived from that link **at import time** |

The Xray engine ran the JSON. The sing-box writer *only ever looked at the JSON*, and translated
it into sing-box outbounds. So the extended core was running a translation of a translation, and
every parameter Marble's translator does not model was silently dropped from the node the user
actually paid for. A stale import, or a link syntax Marble learned to read better in a later
version, was frozen into the node forever.

### 1.1 Three readers, and one refusal can never kill a node

`SingBoxConfigBuilder.candidateBuilds()` now returns **every** sing-box config a node can be
expressed as, best first:

| # | Strategy | Reader |
|---|---|---|
| 1 | `link-parser` | `{type: parser, link: …}` — the extended fork's own URI parser. It owns every parameter a link can express, including the ones this codebase has never heard of. |
| 2 | `link-translated` | Marble re-reads the link with `ProxyParser` and translates what it read. A core reader that is behind on link syntax cannot kill the node either. |
| 3 | `translated` | The stored Xray JSON, hop by hop. Still the only reader for a pasted JSON document and for a chain the user built by hand. |

`AppSettings.singBoxPreferParser` — a switch that already existed in Settings, was persisted, and
**was never read by the writer** — now chooses which end of that list leads. A pasted native
sing-box document short-circuits all three: that document *is* the config.

`SingBoxManager.openFirst()` walks the list and hands each entry to the core. The first one
`sing-box` itself accepts carries the session or the measurement; `lastStartStrategy` records which
reader won and the losing readers' refusals travel with it as notes, so the Engine page can say
why the config that is running is not the one that was tried first.

Only a **config-class** refusal moves on to the next candidate (`core-config:`, `core-start:
exited`, the doctor's engine-level fault markers). A port that will not bind or a core that is out
of memory would fail identically for every spelling, so it fails immediately with its own message
instead of multiplying the wait.

Both the connect path and the measurement path build and open through the *same* two functions,
so the two can no longer drift apart.

### 1.2 The same fix for Xray: reconcile the stored config with its link

Xray runs `configJson`, so the fix for Xray is to make that field true. `AppRepository` now
re-reads every single-line share link with the *current* parser at startup and repairs the stored
copy where the two disagree on anything that decides how the node dials — protocol, endpoint,
transport, security — or where the stored copy is missing entirely.

It is deliberately narrow: only single-line share links are touched (a pasted JSON document or a
hand-edited subscription blob is never rewritten); identity always comes from the stored profile;
a link-only scheme the parser stores with a blank `configJson` (tuic, anytls) never blanks a
working config — those are handed to the core's own parser; and it is idempotent, so after one
repair nothing is written again.

### 1.3 Routing works on both cores

Routing was already written by one scaffolder for every strategy, and it stays that way — pinned
by `routingIsIdenticalForEveryReaderOfTheSameNode`. One real routing bug was fixed on the way:

`SingBoxConfigBuilder.geoTag()` **threw** for a geo tag MarbleNG does not bundle (`geoip:us`, say).
Because routing is written for every strategy, that exception killed the whole config: one rule in
a shared routing profile made *every* node unconnectable on the sing-box engine while the same
profile worked fine on Xray. It now returns `null`, the rule is skipped, and the drop is named in
`Build.notes`. The direction of the mistake is deliberate — an unmatched destination falls through
to `final`, which is the proxy, so dropping a rule can only ever send traffic through the tunnel
rather than leak it.

---

## 2. Real delay: it measures, and it is fast (`MARBLE_REAL_DELAY_TRUTH_V156`, `MARBLE_REAL_DELAY_SPEED_V156`)

### 2.1 Why it looked broken

`RouteProbe.realDelay(profile, tunnelPort = 0, …)` answered `no-live-tunnel` → FAILED. That is the
Home ping button in its disconnected state, and every gate-exempt protocol — Hysteria2, WireGuard,
and *every pasted Xray JSON*, whose scheme is `json`. A missing tunnel is not a verdict about the
server.

`RouteProbe.realDelayHook`, installed by the repository, now builds one for the occasion: the same
throwaway core the sweep path uses, on the selected engine, timed with the identical HTTPS
round-trip measurement. With no core available at all it still says `no-live-tunnel`, honestly.

A core that never comes up is retried **once** in both the probe and the sweep: a spawn storm is a
fact about the device, not a dead node. A genuinely unbuildable config exits instantly on both
attempts, so the retry costs milliseconds where it is pointless.

### 2.2 Why it was slow

Every measured server on the sing-box engine cost **two process spawns and two waits**:

- `sing-box check` as a separate process, then `sing-box run` — which validates the same schema
  and exits non-zero with the same message;
- a wait for the Clash controller to answer, on a measurement that goes through the SOCKS inbound
  and never touches the controller.

Both are load-bearing for a *live* session and pure overhead for a throwaway one, so they are now
parameters of the one opener (`validate`, `awaitApi`) with the strict defaults, and the
measurement path passes `validate = false` and `awaitApi = false` (the URL test keeps the
controller, because it reads it).

The native-child ceiling went from 2 to 4 (`SingBoxManager.MAX_TEMPORARY_CORES`), which is also
the benchmark pool's width for every method that builds a core. Together these turn a 100-node
sweep from minutes into tens of seconds.

---

## 3. URL test belongs to sing-box extended alone (`MARBLE_URLTEST_SINGBOX_ONLY_V156`)

The URL test is the core's own measurement: `GET /proxies/{tag}/delay` against sing-box's Clash
controller, timed with unified-delay accounting through the outbound the core itself dialed.

On the Xray engine the same button used to run a look-alike — an HTTPS HEAD pushed through Xray's
SOCKS inbound by a Kotlin socket. No controller, no unified delay, a second independently-timed
HTTP stack. The same server therefore reported two different numbers depending on which core
happened to be selected.

Now:

- `ProbeMethod.availableOn(engine)` / `forEngine(engine)` / `unavailableReason(engine)` are the one
  rule;
- Settings dims the row and refuses the tap on Xray, with the reason printed under it;
- `updateSettings()` — the single point every engine switch and every restored preference passes
  through — demotes a stored `URL_TEST` to `REAL_DELAY` when the engine cannot run it, and says so;
- `RouteProbe.urlTest()` refuses with `urltest-requires-singbox` rather than measuring something
  else behind the same name;
- `SocksUrlTest`, the retired Xray substitute, is gone. Its one protective rule (HTTPS only, and
  never a URL carrying credentials) survives as `UrlTestTarget.validate()`, used by the session
  that actually asks the core.

---

## 4. Every bulk measurement can be cancelled (`MARBLE_PING_CANCEL_V156`)

"Ping all", a group ping, the Home group ping, Smart and Rank all run as one probe batch, so they
got one cancel — `AppRepository.cancelProbes()` — reachable from every surface that can start a
sweep:

- **Servers progress strip** — an always-visible red STOP square next to the counter, and the
  title reads "Cancelling…" while it unwinds;
- **Servers "Ping every server"** — the same button becomes the stop while a sweep is live;
- **Servers group headers** — a group's ping icon becomes a STOP while *that* group is the batch;
- **Home top bar** — the pulse action becomes a filled STOP square while the group sweep runs.

Cancellation is precise about what it means, because the obvious implementation gets it wrong:

- **Cancel is not abort.** A candidate that has not started is abandoned; a measurement that
  already finished is kept and stays on screen. `ProbeCancelGate.retainedAfterCancel` pins it.
- **Cancel is immediate.** The batch wait used to block in `Future.get(remainingBudget)`, so it
  could not notice anything until the slowest worker finished. The same waits are now sliced
  (`CANCEL_POLL_SLICE_MS`), the flag is re-read every slice, and the worker thread is interrupted
  so a socket, a core spawn or the measurement semaphore unwinds now.
- **Cancel is idempotent and reusable.** `ProbeCancelGate.arm()` reports whether *this* call armed
  the latch, and `reset()` clears it when the batch ends — forgetting that would make every sweep
  after the first exit immediately.
- The pooled task thread clears a stale interrupt flag at both ends, so a cancel can never leak
  into the next unrelated task.

---

## 5. The Home header no longer repeats the session state (`MARBLE_HOME_TOPBAR_NO_STATUS_V156`)

The status dot and its `CONNECTED / CONNECTING / DISCONNECTING / BLOCKED / READY` word sat between
the wordmark and the three actions, on **all four** Home presentations — they all share
`HomeTopActionBar`, so removing it there removes it everywhere.

It was a third place announcing a session state the status banner underneath already owns in full,
with the reason, the uptime and the route. Next to the wordmark it read as part of the brand rather
than as information. The header is now the product signature and its three actions.

---

## Pinned by

`app/src/test/java/com/marbleng/app/core/SingBoxLinkAuthorityV156Test.kt`

- a stored node is handed to the core's own parser first;
- every reader is offered, so one refusal cannot kill the node;
- turning the parser preference off flips the order, not the coverage;
- Marble reads the link itself when the stored JSON is missing;
- a pasted JSON document still translates; a pasted native document short-circuits;
- routing is byte-identical for every reader of the same node;
- an unbundled geo rule is reported instead of killing the engine;
- a node no reader can serve is refused with a reason; SSH stays on Xray;
- only a document refusal moves on to the next reader.

`app/src/test/java/com/marbleng/app/core/ProbeTruthV156Test.kt`

- URL test is offered only on the engine that owns it, and the reason and the gate never disagree;
- switching away from sing-box demotes the stored choice to a real measurement;
- a cancel arms once, is a no-op on a second tap, and is reusable by the next sweep;
- cancelling keeps every measurement that already landed;
- Real delay with no tunnel says so only when no hook can build one;
- the delay-target contract is one rule for the one URL test.

`app/src/test/java/com/marbleng/app/core/CoreInteropRegressionTest.kt`

- the URL test accepts only a plain HTTPS target and refuses one carrying credentials;
- the URL test refuses an engine that does not own it, and runs on the one that does.

`scripts/system-integrity-check.py` gained eight wiring invariants for all of the above
(188 checks, all passing).
