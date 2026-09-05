# Routing, rebuilt: the professional rule workspace and the smart address-family decision (V136)

User report that triggered this rebuild, in their words: **routing "is disastrous"** — there was
no way to build rules at all, the defaults hardwired one country's assumptions for everyone, and
opening a site produced the OS "check your connection" page while the internet itself was up.
Additionally, **IPv6 (and "prefer IPv6") must default ON — but the app, not a blind toggle,
decides when v4 vs v6 is actually used.**

Design references studied before writing a line: **v2rayNG** (`2dust/v2rayNG`) rule model,
template presets and `RoutingSettingActivity` UX; **Incy** (INCY-DEV) routing profiles
(domain/IP lists split across proxy/direct/block with separate remote and domestic DNS,
deterministic first-match evaluation).

---

## 1. What was actually broken (root causes, not symptoms)

### 1.1 The rule store could never be emptied, and could not be edited

* `RoutingEngine.parseRules("")` **resurrected `DEFAULT_RULES`**. An empty policy and the
  built-in policy were the same thing, so the app could never represent "no rules" — and a UI
  that wanted to delete rules had nothing to delete into. The stored JSON is now the single
  source of truth: `""` / `"[]"` parse to an empty list, and the defaults are only *seeded once*
  at migration time (`RoutingDefaults.PREFS_SCHEMA_VERSION = 2`).
* There was **no rule editor at all** — only five expert text fields and three raw JSON rule
  boxes. The old `applyUserRules` threw on the first malformed token it disliked
  (`"port \"443 udp\""` killed the whole config start); worse, rules that *were* accepted were
  appended with no dedup, so editing could duplicate matchers.

### 1.2 Rules were never validated before emission

The old chain had no notion of a rule being *invalid*. A user rule whose port was `"443 udp"`
either failed the whole start with an opaque error or was silently dropped depending on the
code path; neither failure was visible anywhere. V136 validates every rule
(`RoutingEngine.validateRule`) with **ERROR / WARNING severities** surfaced per-card in the UI
(red border + message), and `emittableUserRules` excludes ERROR rules from the emitted config
so a bad edit can never take the tunnel down again. The hardener's `verify()` walk now checks
the *emitted* user rules, so what is on disk is what Xray tested.

### 1.3 The "check your connection" class of failure

Traced end-to-end: the VPN service starts the Xray core **before** it establishes the TUN, and
`handleFailure` tears the attempt down on any start failure, so a rejected config can no longer
blackhole the tun. The historical blackhole was the V135 triangle (unreachable IPv6 resolvers +
`::/0` capture + starved DNS budgets — see `ROOT_CAUSE_V135.md`); V136 completes that fix on the
routing side:

* the family plan (`AddressFamilyPolicy.plan`) is computed from the **underlay's real v6
  capability**, not from a wish;
* `verify()` asserts every user rule Xray will load compiles (`xray -test`), that geo needs are
  satisfied before connect, and that the fallback stays on the selected proxy;
* the new **route simulator** answers the remaining question in the UI: type a host and Marble
  walks every rule in order, names the winner (`PROXY / DIRECT / BLOCKED` + reason + per-step
  ✓/–/?/· walkthrough) — offline, without ever resolving the query host (only textual IP
  literals are matched; `geoip` layers report "unverifiable" honestly).

### 1.4 One country was hardcoded into everyone's defaults

`parseRules("")` seeded geoip/geosite **Iran** rules for every user on earth, and
`IdentityGuard`/`applyIranRoutingPreset` treated that as an invariant. V136 keeps Iran as a
first-class *option* (the app's origin and largest user base), but the neutral defaults are:

* **Mode:** `GEO_DIRECT` stays the default mode, but the geo tags now come from the user's
  selected geo-asset source (chocolate4u Iran, Loyalsoldier global, jsDelivr mirrors, or custom
  URLs) and the baseline seed is `RoutingPresets.Preset.RECOMMENDED` = ads-block + the
  user's configured `routeGeoIpTags` / `routeGeoSiteTags` direct lists (default still `ir`
  because that remains the dominant audience — never hardcoded assumptions *inside the engine*,
  only defaults the user can change in one tap);
* **Presets** (v2rayNG's template idea, region-aware): `RECOMMENDED`, `PRIVACY`
  (ads + trackers + BitTorrent block), `GLOBAL_PROXY` (LAN direct, rest proxied), and opt-in
  `IRAN_DIRECT` / `RUSSIA_DIRECT` / `CHINA_DIRECT`. Applying a preset replaces the rule list
  after confirmation and never touches mode, geo source or expert lists — always reversible.
  This mirrors v2rayNG's `WHITE/BLACK/GLOBAL/WHITE_IRAN/WHITE_RUSSIA` split while removing its
  quirk of force-appending UDP/443 block to every template.

## 2. What ships in the UI (the rebuilt Routing workspace)

Modelled on v2rayNG's `RoutingSettingActivity` (reorderable list, per-rule enable switch, typed
per-field editor, outbound tag suggestions, domainStrategy dropdown) and Incy's
proxy/direct/block-per-list model, built only from Compose primitives (no new dependencies):

1. **Routing mode** — four cards (`Proxy all / Private direct / Geo direct / Custom`). The mode
    decides *implicit* behaviour exactly like v2rayNG's modes: the rule list never duplicates it.
2. **Presets** — chip row; the chip of the *active* preset is highlighted by comparing the rule
    id list; applying goes through a Replace/Cancel dialog.
3. **Geo databases** — source dropdown (built-ins + custom HTTPS URLs), per-database status
    cards (ready/stale/missing, size, age) and Update/Verify buttons. `XrayManager` gained
    `refreshGeoAssetIndex()` so every asset refresh also rebuilds the suggestion index
    (also refreshed in the background on app start).
4. **Independent switches** — Block ads and Bypass private LAN stay user-owned in every mode
    (GEO_DIRECT pins private bypass on, shown as "Always on in this mode").
5. **Rules** — the ordered list, top wins. Per card: outbound colour spine, monospace matcher
    summary, live validation errors, enable switch, Edit/Duplicate/Move-to-top/Move-to-bottom/
    Delete menu, and **long-press drag reorder** with live displacement (haptics, per-item
    measured heights, `routingDragTarget` slot math — the reorderable-library behaviour
    reimplemented on `detectDragGesturesAfterLongPress`).
6. **Editor sheet** — typed fields, not a JSON box: matcher kind chips (Domains / GeoSite tag /
    GeoIP tag / IP-CIDR / Port), per-kind help text, **live autocomplete from geoip.dat /
    geosite.dat** (`GeoAssetIndex.suggest`, exact → prefix → stem → substring, ranked by entry
    count; tapping a DOMAIN-kind suggestion re-prefixes `geosite:`), network/ports/protocol
    refinements, PROXY/DIRECT/BLOCK outbound chips and an emitted-JSON preview.
7. **Route simulator** — "Why is a site failing?" with verdict badge, reason and full
    per-rule walkthrough.
8. **Expert section** (collapsed by default) — domainStrategy with explanations, ad-block tag,
    geo tag lists and the five text lists, which now emit **below** the user's own rules.

## 3. Geo data, analysed and indexed

`geosite.dat` / `geoip.dat` are protobuf streams (Xray `router.GeoSiteList` / `GeoIPList`):
each entry is a country/category **code + list of domains** (plain/regexp/domain/full types) or
**code + CIDR blocks**. Files are ~100 KB–20 MB, so the index never materialises strings:

`GeoAssetIndex` (new) is a hand-rolled streaming scanner that walks the protobuf wire format
directly — no protobuf dependency — keeping only per-tag FNV-1a **56-bit hash arrays** (binary
searched for membership; `matchesGeosite` answers "is this host inside geosite tag X" without
loading a single domain string; regexp entries answer `false` honestly). Cap: 1.4M hashes /
48 MiB. Corrupt files degrade to "no index" (never throw); missing files fall back to a small
built-in catalog so suggestions work on first launch. `suggest()` powers the editor
autocomplete and `known()` powers validation (a geo tag that is neither in the loaded index nor
`private` is an ERROR, not a silent no-op).

## 4. The smart address-family decision (IPv6 defaults ON, decided by the app)

`ipv6Enabled = true`, `preferIpv6 = true` are now the shipped defaults (with a schema-v2
migration that flips the pair exactly once for existing installs), **and** the blind behaviour
is gone:

* `AddressFamilyPolicy.plan(settings, underlayHasIpv6, …)` is the single decision point:
  * v6 **off** in settings → `IPV4_ONLY` + `::/0` blackhole (unchanged, explicit);
  * v6 on but **underlay has no global v6** → `DUAL`, v6 *not* prioritised, race still armed
    (happy-eyeballs finds a family in parallel instead of serial timeouts);
  * v6 on + underlay capable + measured v6 healthy → `IPV6_FIRST` with RFC 8305 racing
    (`preferIpv6` off chooses the conservative v4-first `DUAL` ordering);
  * **a measured-broken IPv6 path demotes the plan to `DUAL` (v4-first) automatically** — a
    preference is an ordering, never a demand that ignores evidence; the only thing a
    measurement cannot override is the strict v6 configuration (`UseIPv6` / a node pinning
    itself to v6), which fails as v6-only on purpose;
  * fragment/chained/UDP paths get the deterministic `ForceIPv6v4` order (no race possible).
* The same plan gates DNS resolver families (v6 literals never ship to a v4-only network) and
  the freedom/bootstrap lists, per V135.
* The Home IPv6 tile label now says what the setting actually means ("prefer IPv6 when the
  network supports it").

## 5. Tests

* `RoutingEngineTest` — rewritten for V136: default-rules shape, empty-policy honesty, mode
  implicit ordering in a hardened config, strict-Custom mode, invalid-rule skipping, port and
  domain validation shapes, CIDR parsing without DNS, simulator verdicts/fallback/unverifiable
  layers, preset materialisation, ordered emission, and the **smart-family default**
  (v6 on + v4-only underlay ⇒ DUAL without v6 priority; v6-capable underlay ⇒ IPV6_FIRST race).
* `GeoAssetIndexTest` (new) — hand-encoded protobuf fixtures: tag/count scanning, full/root
  domain membership, suggestion ranking, corrupt-file degradation, built-in catalog fallback.
* Existing suites (`NetworkPolicyTest`, `DnsDeadlineConfigTest`, `XrayConfigHardenerTest`) keep
  passing: all their scenarios use explicit settings, and the semantics they pin (v6 off ⇒
  `IPV4_ONLY`; v4-only underlay purges v6 resolvers; v6-capable underlay keeps them) are
  preserved exactly.

## 6. Files

* New: `core/GeoAssetIndex.kt`, `core/RoutingPresets.kt`, `app/src/test/.../GeoAssetIndexTest.kt`.
* Rewritten: `core/RoutingEngine.kt` (parse/validate/emulate/simulate), Routing UI inside
  `ui/Aether2026.kt` (`MARBLE_ROUTING_UI_V136`).
* Touched: `core/AddressFamilyPolicy.kt` (underlay-gated v6 priority), `model/Models.kt` (RoutingRule +network/protocol/port, defaults, migration version),
  `data/AppStore.kt` (schema-v2 migration), `core/XrayConfigHardener.kt` (verify walk +
  family-plan call-through), `core/XrayManager.kt` (index refresh + engine delegation),
  `core/BugFinder.kt` (engine-driven geo checks), `AppRepository.kt` (preset/simulator/rule
  APIs), `MarbleApplication.kt` (background index refresh), Persian lexicon (routing strings).
