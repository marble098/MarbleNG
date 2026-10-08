# MARBLE_MULTI_SOURCE_ROUTING_V212 — several geo databases at once, and a dial for how finely

*Core:* `app/src/main/java/com/marbleng/app/core/GeoAssetRegistry.kt`,
`app/src/main/java/com/marbleng/app/core/IranPrecisionPack.kt`,
`app/src/main/java/com/marbleng/app/core/GeoAssetIndex.kt`, `core/RoutingEngine.kt`,
`core/XrayManager.kt`, `core/SingBoxConfigBuilder.kt`
*Tests:* `app/src/test/java/com/marbleng/app/core/GeoAssetRegistryV212Test.kt`,
`app/src/test/java/com/marbleng/app/core/IranPrecisionPackV212Test.kt`,
`app/src/test/java/com/marbleng/app/core/MultiSourceRoutingV212Test.kt`
*Wiring:* `AppRepository.applyGeoAssetSource/toggleGeoAssetSource/geoSourceStates/setGeoPrecision`,
Settings › Connection › Routing (**Geo databases**, **Domestic separation**), preferences schema v3.


## The report

> Routing sends Iranian traffic direct, but it is still weak. Sometimes Iranian sites and apps still
> go through the proxy. Please redesign the routing professionally so the app can use several geoip
> and geosite sources at the same time and separate the traffic much more finely.

Two separate weaknesses, and they need two separate answers.

## 1. One source, chosen by replacement

`AppSettings.geoAssetSourceId` was one id, and `applyGeoAssetSource` *replaced* it. A user who wanted
Loyalsoldier's wider world coverage gave up Chocolate4U's Iranian list, and vice versa — so the
coverage a domestic route needs and the coverage an international one needs could never both be on.
That is the structural half of "still weak", and no amount of rule editing fixes it.

It is a **set** now: `geoAssetSourceIds`, ordered, capped at six (`GeoAssetRegistry.MAX_SOURCES`),
with a switch (`geoMultiSourceEnabled`) and a floor of one — routing with no geo database is a worse
product than routing with the one the APK ships, so the primary cannot be toggled off, only
re-ordered.

### Why no merging

The obvious design — download N `.dat` files and union them into one — is not what the core needs.
Xray's geo loader addresses a database *by file name*: `geoip:ir` is sugar for `ext:geoip.dat:ir`, and
`ext:<file>:<tag>` is accepted directly. So several databases coexist in the asset directory and a
rule names the one it wants. A protobuf writer would have had to reproduce a format the product does
not own, at a memory cost proportional to the union, to produce something the core can already read.

The file naming follows from that: the **primary** source keeps the canonical `geoip.dat`/
`geosite.dat`, and every other source gets `geoip-<id>.dat`/`geosite-<id>.dat` beside it. Keeping the
canonical names on the primary is what lets a fresh install route from the bundled copy with no
download at all, and it is why a primary token is still written as `geoip:ir` — byte-identical to
every rule an earlier release wrote.

### Fail-closed emission

A geo rule names a file, and a missing file is a config the core refuses to load. So the connect path
measures what is actually on disk (`XrayManager.readyGeoAssetFiles`), writes it into a transient
settings field (`measuredGeoReadyFiles` — a fact about this session's filesystem, never a
preference), and `RoutingEngine.geoTokens` emits a token **only** for a source whose file answered.
An enabled-but-not-downloaded source contributes no rule rather than a hopeful one: the tunnel still
connects, with the sources that did answer.

`GeoAssetIndex` became multi-file for the same reason — it scans every `.dat` in the directory, keeps
per-file tag maps, and answers `sourcesFor(kind, tag)`, which is what turns "a geo tag matched" into
"**this database** matched" in the rule editor and the simulator.

## 2. A list is only as good as the list

Every geo database is somebody's published snapshot, and `ir` inside any of them is a list somebody
maintains. The moment a domestic service appears on a domain no list has caught up with — a bank's
payment host, a messenger's CDN, an app's API on a `.com` — that traffic falls through to the proxy
default. Adding a second and third source widens the net, but all three share the same blind spot,
because they are all lists of the same shape maintained by the same kind of process.

So the product keeps its own list: `IranPrecisionPack`, 104 curated domestic domains in 13
categories, the `.ir` TLD, and 46 brand stems. Three levels:

| level | emits |
| --- | --- |
| `STANDARD` (geo tags) | nothing — only the tags the user configured |
| `ENHANCED` (curated, **the default**) | the TLD + the curated domains |
| `MAXIMUM` (strict) | that, plus the brand keywords |

**Deliberately no CIDR ranges.** An Iranian IP block that is wrong sends someone else's traffic out
of the tunnel — a leak, not a mistake — and a range this code cannot verify is a range it must not
assert. The IP side of "domestic stays direct" is `geoip:ir` asked of *every* enabled database,
which is verified by each database's own publisher.

### Where the layer sits, and why that is the contract

Precision rules are `direct` rules, so they are emitted **last** in `applyUserRules` — after the
mode's implicit rules, after ad blocking, and after the user's own rules. The core takes the first
match, so a domain the user blocked or proxied by hand has already been emitted (and marked) by the
time this runs: the product's opinion can neither override a rule the user wrote nor duplicate it.

`precisionActive(settings)` is the one answer both engines and the UI read, and it is false in
`CUSTOM` (strictly the user's rules), in `PROXY_ALL` (nothing is direct by design), with the domestic
switch off, and at `STANDARD`.

sing-box has no `.dat`, so `SingBoxConfigBuilder` writes the same knowledge in its own idiom —
`domain_suffix` for the TLD and the domains, `domain_keyword` for the stems — in the same position
relative to the user's rules. A rule only one engine can express is a rule that only works on one of
them.

## Sources, verified

The built-in catalog is four sources plus the custom form, each checked against the publisher's
release API on the day this landed: Chocolate4U `Iran-v2ray-rules` (primary, bundled),
Loyalsoldier `v2ray-rules-dat`, v2fly `domain-list-community`, and v2fly `geoip` (geoip only — asking
it for a geosite tag has no answer, and `SourceSpec.provides` is what says so). Two candidates were
dropped rather than shipped broken: `Chocolate4U/GeoLite2-Country` does not exist, and
`runetfreedom/geoip` has no releases.

## Checks

- the set: order, floor, cap, dedup, the legacy single-id fallback — `GeoAssetRegistryV212Test`
- token shape, canonical file names, `geoip:private` names no file, one-family sources —
  `GeoAssetRegistryV212Test`
- fail-closed emission, precision gating, dedup against user rules, chunking —
  `MultiSourceRoutingV212Test`
- the curated list's own integrity, the three levels, provenance — `IranPrecisionPackV212Test`
- the routing page draws the set and the dial — `ServerTileParityV212Test`
- preferences schema **v3** migrates `geoAssetSourceId` → `geoAssetSourceIds` and seeds the dial
