# V150 — Home mirrors the Servers page: same pings, same sort, same selected server

## خلاصهٔ فارسی

در صفحهٔ سرورها، بعد از پینگِ لینک اشتراک، پینگ‌ها نشان داده و بر همان اساس مرتب می‌شوند و کاربر
یک سرور انتخاب می‌کند. همین تنظیم باید در بخش سرورهای صفحهٔ اصلی هم دقیقاً یکسان اعمال شود؛ ولی
نمی‌شد: صفحهٔ اصلی یک وضعیت کاملاً جدا برای خودش داشت.

- قبل از این رفع: لیست سرورهای صفحهٔ اصلی **نه** بر اساس `nodeSortMode` مرتب می‌شد، **نه**
  پینگِ یکایک سرورها را نشان می‌داد، و برای «انتخاب‌شده» فقط به `server.id == activeProfileId`
  نگاه می‌کرد — یعنی فقط سروری که همین لحظه ترافیک حمل می‌کرد به‌عنوان انتخاب‌شده شناخته می‌شد،
  نه سروری که کاربر در «سرورها» برگزیده بود.
- بعد از این رفع: لیست سرورهای صفحهٔ اصلی دقیقاً همان لیست صفحهٔ سرورهاست — همان
  `ServersFilter` (پروتکل / فقطِ قابل‌دسترس / سقف پینگ، محدود به گروه)، همان حالتِ مرتب‌سازی
  (`nodeSortMode` + `reverse`)، همان پینگِ اندازه‌گیری‌شدهٔ هر سرور و همان پیش‌بینیِ انتخاب/فعال
  (`repo.isSelectedProfile` / `repo.isActiveProfile`).

---


## 1. The defect — Home had its own list, its own order, its own "selected" rule

The Servers tab (`CyberLibrary`) builds its list through one pure engine:

```kotlin
val benchmarks = repo.benchmarks.associateBy { it.profileId }
val filter = ServersFilter(
    protocol = settings.serversProtocolFilter,
    sourceId = repo.librarySourceFilter,
    onlyReachable = settings.serversOnlyReachable,
    maxPingMs = settings.serversMaxPingMs,
    groupByCountry = settings.serversGroupByCountry
)
val visibleProfiles = ServersQuery.sort(
    profiles = ServersQuery.visible(allProfiles, filter, benchmarks),
    mode = settings.nodeSortMode,
    reverse = settings.nodeSortReverse,
    benchmarks = benchmarks
)
```

Each row then shows the benchmark's latency (`ServersPingCapsule`), and the selection predicate is
`repo.isSelectedProfile(profile)` while the live row is `repo.isActiveProfile(profile)`.

The Home server box (`IosServerListBox`, MarbleHomeStyles.kt) did **none** of that:

```kotlin
val visibleServers = ServersQuery.visible(
    profiles = repo.libraryProfiles,
    filter = ServersFilter(sourceId = if (activeSubId.isBlank()) "all" else activeSubId)
)
// ...
val isSelected = (server.id == repo.activeProfileId)
```

so the Home list:

1. never applied the user's sort mode (a ping-sorted Servers list came back in published order on
   Home, or vice versa);
2. never showed the per-server ping at all — no measuring spinner, no `✕` for a failed probe, no
   `—` for an unmeasured server;
3. only marked the **currently connected** row as selected, so a server the user had deliberately
   picked on the Servers tab (while disconnected, the normal browse path) read as unselected on
   Home — the exact "Home is completely separate for itself" report.

The two surfaces were not two views of one truth; they were two parallel states of the same data.

## 2. The root cause is a shared-engine omission, not a new rule

`ServersQuery.sort`/`visible` are the single deterministic engine, and `AppRepository` is the single
owner of the benchmark evidence and the selected/active predicates. The Home box simply never called
them. Because the Home call sites (`HomeThemeSlider`, `HomeThemeFloating`, `HomeThemeEmbossed`,
`HomeThemeModular`) all delegate to `IosServerListBox`, one fix inside that composable makes every
Home presentation consistent with the Servers page — no duplicated rules, no second source of truth.

## 3. The fix

- **Same filter + sort.** `IosServerListBox` now reads `repo.settings` and `repo.benchmarks`, builds
  the exact `ServersFilter` (protocol / reachable / max-ping, scoped to the group chip that already
  shares `repo.librarySourceFilter` with the Servers tab), and runs it through
  `ServersQuery.sort(..., mode = settings.nodeSortMode, reverse = settings.nodeSortReverse, ...)`.
- **Same per-server ping.** Each row receives `result = benchmarks[server.id]` and a `testing =`
  flag (`repo.probeStateOf(server.id) == ProbeState.TESTING`), and renders
  `HomeServerLatencySlab` using the identical "measured" gate as the Servers page
  (`it.success > 0 && it.latencyMs >= 20`) and the identical green/amber/red tone ramp. A running
  probe shows a small spinner, a failed probe shows `✕`, an unmeasured one shows `—`.
- **Same selection + active predicates.** `isSelected = repo.isSelectedProfile(server)` and
  `isConnected = repo.isActiveProfile(server)`, replacing the old `server.id ==
  repo.activeProfileId`. A selected-but-disconnected server now reads as selected on Home exactly as
  it does on the Servers page.
- **Tap behaviour unchanged.** A tap still calls `repo.selectProfile(server)`, then re-routes only
  when a tunnel is already up (`if (evidence.connected) actions.onConnectProfile(server)`).

## 4. Why this also closes the "wrong pings" and "instability" reports

The instability and misleading-latency reports were already root-caused client-side in earlier
releases and are pinned by this source tree (`scripts/system-integrity-check.py` reports 152/152
pass on this commit):

- **V135** — the IPv6 dead end, an unbounded reconnect loop and the memory cost of both. IPv6
  literals are purged from bootstrap resolvers when the underlay cannot carry v6, the `::/0` capture
  is gated on a real v6 route, and the Kotlin-side caches release one memory-pressure step earlier.
- **V146** — the tunnel ping used the device resolver (a DNS leak) and a hidden budget carve-out.
  The tunnel path now sends `ATYP=domain` to the SOCKS inbound so Xray resolves *inside* the
  tunnel, and Phase 2 owns the full per-sample budget.
- **V149** — the "connected, no Internet" signature was the TLS pinning field mapping
  (`vcn`/`pcs` → `verifyPeerCertByName`/`pinnedPeerCertSha256`), and the four ping methods reported
  healthy servers as failed for four independent reasons (JSSE CA-store validation of a proxy cert,
  ICMP `-q` suppressing the parsed `time=` lines, any non-2xx status being `UNREACHABLE`, and a
  HEAD that waited for a body). All four are fixed and unit-tested.

The remaining, still-visible defect was that the Servers page consumed all that evidence while the
Home page did not. Once the Home list is wired to the same engine and the same benchmark map, the
number the user reads on Servers and the number on Home cannot diverge, and the
connection-stability fixes already in place reach the Home list (a failed/injected probe shows the
same `✕`/`⚠️` here as there).

## 5. Why this adds no memory back

The Home now reads the **existing** `repo.benchmarks` map — the same evidence the Servers page
already holds — rather than re-measuring or caching its own copy. The only new allocation is a
per-recomposition `associateBy { it.profileId }` transient, the same one `CyberLibrary` already does.
`onMemoryPressure` still trims `benchmarks` (to the active profile) at level ≥ 40 and releases
`privacy`/`bugReport`/`serverIntel` earlier, so the memory-pressure contract is unchanged.

## 6. Verification

- `scripts/system-integrity-check.py` → 152/152 pass.
- `tools/kotlin-structure-check.py` → `MarbleHomeStyles.kt` [OK].
- `tools/compose-scope-check.py` → 0 composable-context violations.
- The sort/filter/only-reachable/max-ping/ping-order behaviour is pinned by
  `app/src/test/java/com/marbleng/app/core/ServersQueryTest.kt` (the exact engine the Home now
  uses), which already covers `pingSortPutsNeverMeasuredServersLast`,
  `onlyReachableHidesUnmeasuredAndFailedServers`, `maxPingCeilingHidesUnmeasuredServersToo` and the
  per-mode stability sorts.

Marker: `MARBLE_HOME_MIRRORS_SERVERS_V150` in `app/src/main/java/com/marbleng/app/ui/MarbleHomeStyles.kt`.
