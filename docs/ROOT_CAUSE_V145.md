# V145 — Home menu, group ping, subscription reach, measurement budget, dock, geo gate

Nine user-reported defects, fixed at their cause. This document is the critique that produced
each change and the contract each change now holds to. Every marker below is greppable in the
source (`MARBLE_*_V145`).

---

## 1. `MARBLE_HOME_ADD_MENU_V145` — the `+` opens a menu, not a page

**Was:** `HomeActions.onAddRoute` set `addRouteOpen`, and `Aether2026App` rendered
`AddRouteMenuDialog`, an `AlertDialog` with a title, a subtitle, three full-width option cards
and a Cancel button. Tapping a 36 dp icon dimmed the entire product surface to ask a
three-option question, and the answer required a second trip across the screen.

**Now:** `HomeTopActionBar` wraps the `+` in a `Box` and hangs a `DropdownMenu`
(`HomeAddRouteMenu`) directly under it, styled with the same panel chrome as the group menu on
the same page. `AddRouteMenuDialog` / `AddRouteMenuOption` and the `addRouteOpen` state are
deleted — there is no second code path left that can raise a modal for this action.

## 2. `MARBLE_SERVERS_GROUP_PING_V145` — every subscription card owns a ping icon

**Was:** measuring a group lived in the header's three-dot menu (`ServersGroupAction.PING`),
while its sibling verb — refresh — was a one-tap icon right next to that menu.

**Now:** the header renders a ping control beside the refresh control, with a live spinner while
that group's own batch is running (`repo.probeActive && group.profiles.any { it.id in
repo.probeBatch }`), and disabled when the group is empty. The menu entry and the enum member
are gone: one verb, one control.

## 3. `MARBLE_SUBSCRIPTION_REACH_V145` — links load with the VPN on *and* off

**Was:** `AppRepository.httpSubscription` tied two independent reachability problems together.

```kotlin
val connected = state != "DISCONNECTED"
if (connected) check(xray.isAlive) { … }         // refresh fails closed during CONNECTING
DpiAwareFetcher.fetch(allowDirect = !connected,  // connected ⇒ never try the underlay
                      throughSocks = if (connected) …  else null)  // disconnected ⇒ no tunnel
```

MarbleNG excludes its own package from the TUN, so a "direct" request while connected travels
over the *physical underlay* — which is exactly how a domestic panel or an IP-restricted
provider is reachable. Refusing to try it is why some subscriptions "only work with the VPN
off"; having no SOCKS path while disconnected is why others "only work with the VPN on".

**Now:**

* both transports are always attempted; only their **order** depends on state
  (`DpiAwareFetcher.fetch(preferSocks = …)`), tunnel-first when a healthy tunnel exists,
  underlay-first otherwise, each wave repeated with the secondary UA;
* the fail-closed `check(xray.isAlive)` is replaced by `liveSocksPortOrZero()`, which simply
  reports whether there is a tunnel worth borrowing;
* when nothing is connected and every direct path failed, `fetchThroughTemporaryTunnel` starts a
  short-lived Xray core on up to three known-good servers purely to fetch the link, then tears it
  down. A censored subscription URL no longer requires the user to connect by hand first.

## 4. `MARBLE_HOME_GROUP_PING_V145` — the Home pulse icon measures the group

The Home page is scoped to one subscription (the group chip above the server list), so the
question its ping button answers is "how is this subscription doing?". `HomeActions.onPingGroup`
→ `repo.pingHomeGroup()` → the same one ping engine over every server of the selected group
("All groups" pings everything). The icon shows a spinner while the sweep runs. The per-route
ping did not disappear: it is the readout on the status banner (`onTestPing`).

## 5. `MARBLE_HOME_WORDMARK_V145` — the MarbleNG signature, in all four themes

`MarbleWordmark` renders one glyph run through a prism text brush (ice → cyan → amethyst →
emerald) at the start of `HomeTopActionBar`, which every one of the four Home presentations
already renders. One implementation, one position, no per-theme drift, and — being a single
`Text` with a brush rather than four coloured nodes — it survives RTL, ellipsis and font changes.

## 6. `MARBLE_MODULAR_LAYOUT_V145` — the customizer layout, repaired

Bugs reachable with the customizer's own controls, all fixed:

1. **A module could vanish permanently.** The page rendered `modularCardOrder` literally, so an
   order string missing an entry never drew that module again — including `CONNECT`, i.e. a Home
   screen with no connect button. `ModularLayout.order()` now guarantees a permutation of the
   known modules (unknown tokens dropped, duplicates collapsed, missing ones appended), applied
   on read in `AppStore`, in the page and in the editor.
2. **Duplicated entries drew the same card twice.** Same fix.
3. **Content past the screen edge was unreachable** — a fixed `Column` clips. The page scrolls.
4. **`weight(1f)` fought the user's own card-height slider.** The servers module is bounded by
   the chosen height, exactly as the customizer promises.
5. **`modularShowShortcuts` and `modularShowSocks` were dead settings** — persisted, restored,
   never read. Both are real modules now (`HomeShortcutDeck`, `ModularSocksCard`), joined by new
   `modularShowStatus` / `modularShowServers` switches, all editable in the dialog.
6. **The floating connect button had no ping companion.** `ModularConnectModule` now shows a ping
   action beside the connect control the moment the tunnel is up, for every silhouette.
7. The editor gained **Reset layout**, and the card-size chips track the actual configured
   height instead of staying lit next to a hand-dragged value.

## 7. `MARBLE_PING_CONTROL_V145` + `MARBLE_PING_ACCURACY_V145` — the measurement, harshly

### The critique

The ping engine was honest in its architecture (one method, one entry point) and dishonest in
its numbers, because four separate layers silently narrowed the budget underneath the user:

| Layer | What it did |
|---|---|
| `AppRepository.testSource` | rewrote a sweep to `benchSamples = 1`, `benchTimeoutSec = 2`, `tcpWorkers = max(20, 24)..32` |
| `RouteProbe.smartPing` | clamped the caller's budget to `1_200..6_000 ms` and always measured **one** sample |
| `BenchmarkEngine.directResult` | additionally clamped TCP to `tcpPrecheckTimeoutMs` (1 s) |
| `BenchmarkEngine.measure` | capped a real tunnel sample at 2.5 s |
| Home ping | `coerceIn(500, 8_000)` |

Consequences the user actually sees: a subscription of healthy servers turning red on a slow
link (2 s deadline, single handshake, 24 parallel), a "timeout" setting that changes nothing, and
two runs of the same sweep disagreeing.

Three more accuracy defects, independent of any setting:

* **`tcpOnce` timed the whole Happy-Eyeballs loop**, not the successful attempt: a dual-stack
  node with a blackholed AAAA reported *IPv6 timeout + real IPv4 handshake* as its latency —
  a 40 ms server measured as ~1040 ms, then ranked on that number.
* **Every sample re-resolved DNS**, so the first sample of a domain node measured the resolver
  and the rest measured the OS cache.
* **Samples were fired back to back**, measuring the remote SYN backlog / connection reuse rather
  than the path, and the cold first sample was kept in the median.

### The fix

* `PingBudget` (model) is the single budget: **timeout per server 1–30 s** (choices 2/3/5/10/15),
  **samples per server 1–10**, **parallel servers 1–64** (choices 1/2/4/8/16/32), plus
  `perServerBudgetMs()` from which the batch deadline is derived. Every clamp listed above now
  routes through it, so a configured value is the value that runs.
* Settings › Tests › Ping exposes all three with one-tap chips, states what each one costs, and
  prints the worst-case wall clock per server.
* `RouteProbe`: per-attempt stopwatch, one resolution per multi-sample run, `PingBudget
  .SAMPLE_SPACING_MS` between samples, warm-up sample discarded at ≥3 samples, and one shared
  `summarize()` producing median / jitter / p95 / loss for every method.
* **Cost control that does not cost accuracy.** Honest timeouts multiply on dead nodes
  (`samples × timeout`), which would make a sweep over a subscription of silent servers crawl.
  A target that produces nothing on two consecutive attempts is abandoned
  (`CONSECUTIVE_FAILURES_BEFORE_ABANDON`) — the verdict cannot change, only the waiting — while a
  single success disarms the rule and the full sample budget is spent. Loss is reported against
  the attempts actually made. Shipped defaults: 5 s, 3 samples, 16 in parallel.
* `smartPing` honours the caller's budget and measures its TCP gate with the configured sample
  count instead of a single SYN.

## 8. `MARBLE_DOCK_CUSTOM_V145` — the bottom bar is a preference

`dockShowLabels`, `dockShowIcons` and `dockSize` (small / medium / large) live in `AppSettings`
and are edited in Settings › General › Navigation bar. `DockMetrics` publishes the resulting
geometry through `LocalDockMetrics`, and `dockClearance()` — the room every page reserves under
its content — is derived from the same value, so the bar and its reservation can never disagree.
`dockShowsIcons()` refuses the one illegal combination (nothing drawn at all) in the model rather
than by disabling a switch.

## 9. `MARBLE_GEO_READY_GATE_V145` — geo routing off until the databases are complete

**Was:** `XrayManager.start` failed the connection when the selected policy needed
`geoip.dat`/`geosite.dat` and no local or bundled copy existed:

```kotlin
error("geoip.dat is required by the selected routing policy but no local/bundled copy is available")
```

The default policy (GEO_DIRECT + ad blocking) needs both, so a fresh install on a censored link —
where the very first asset download is precisely the thing that fails — could not connect at all,
and the message named a file the user cannot produce. Worse, "ready" only meant *larger than
1 KiB*: a truncated download or an HTML block page satisfied it, was handed to Xray, and killed
the core (connected, nothing loads). A background asset refresh could also veto a connect
outright (`check(acquired)` on the asset lock).

**Now:**

* `RoutingEngine.withGeoAssetGate(settings, geoIpReady, geoSiteReady)` — a pure function —
  removes exactly the rules that need an absent database: `GEO_DIRECT` degrades to
  `BYPASS_PRIVATE` (private CIDRs are literals, no database involved), geo tag lists and
  `geoip:`/`geosite:` tokens are dropped, ad blocking is suspended, and user rules that match on
  a missing database are disabled *in the copy handed to the config writer*. The user's stored
  settings are never rewritten, so the full policy resumes by itself the moment the file lands.
* `looksLikeGeoDatabase()` validates the protobuf header and the declared length, and
  `downloadAsset` requires the transfer to match `Content-Length`. A partial or non-protobuf
  payload is discarded, the previous good copy survives, and `routingAssetStatus()` reports
  READY only for a complete, structurally valid database.
* The asset lock is no longer able to fail a connect; losing the race simply means "not ready
  yet", which the gate already handles.
* `AppRepository.ensureGeoAssetsInBackground()` fetches the databases silently at launch (only
  when the policy needs one that is missing, only while disconnected, never touching `busy`), and
  the Routing page states plainly that geo routing is paused until the download finishes.

---

## Tests

* `model/PingBudgetTest` — clamps, offered choices, 10 s per server, worst-case budget growth.
* `model/ModularLayoutAndDockPolicyTest` — order repair (including "no CONNECT"), idempotence,
  dock size parsing, and the illegal icons-off + labels-off combination.
* `core/GeoAssetGateTest` — degradation per missing database, rule gating without mutating the
  user's settings, and the downgrade reason.
* `core/SubscriptionTransportTest` — both transports are always attempted, in either order.
