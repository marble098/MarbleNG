# CORE OPTIONS — V211

**Why the Fragment section changed nothing a user could feel, why the answer was not a better
Fragment section but the deletion of it, and what takes its place: every option the two pinned
cores accept, written once, owned by the user — with a JSON escape hatch for the ones a screen
cannot hold.**

Marker: `MARBLE_CORE_OPTIONS_V211`. Engine pins: `core-lock.json` (Xray `v26.9.30`, HEV `2.18.0`,
sing-box-extended `v1.14.1-extended-2.7.2`). No core patch is added by this chapter; it *removes*
config the app used to invent.

---

## خلاصه (Persian summary)

- **بخش فرگمنت عملاً هیچ کاری نمی‌کرد، و دلیلش ساختاری بود.** هر مقداری که در آن صفحه تایپ می‌شد،
  یک لایهٔ خودکار دیگر (نردبان DPI، حالت ایران، یادگیرندهٔ ترافیک) روی همان مقدار می‌نوشت. عددی که
  روی صفحه بود، عددی نبود که به هسته می‌رفت — پس کنترل، قابل‌شکست نبود و کاربر نمی‌فهمید چه چیزی
  فرستاده شده است.
- **راه‌حل «بهتر کردن» آن صفحه نبود؛ حذف کامل آن بود.** صفحه، کارت، نردبان فرگمنت، یادگیرنده،
  کلیدهای تنظیمات و تمام کارهایی که شکل فرگمنت روی سیم می‌ساختند حذف شدند. تنها چیزی که ماند،
  احترام به کانفیگِ *واردشدهٔ* کاربر است: اگر خودتان یک زنجیرهٔ fragment در JSON وارد کرده باشید،
  همان می‌ماند.
- **جای آن، تنظیمات دلخواه هسته‌ها آمد — با کامل‌ترین سطحی که دو هسته قبول می‌کنند.** لاگ، sniff،
  inbound، کاربر و رمز inbound، mux کامل (Xray و sing-box جدا)، سوکت، مسیریابی، policy، DNS و
  timeoutها؛ همه با واژگان خودِ هسته، همه از تنظیمات کاربر. مسیریابی
  (`routing.domainStrategy`/`domainMatcher`) روی صفحهٔ Routing می‌ماند تا هر کلید دقیقاً یک نویسنده داشته باشد.
- **قاعدهٔ آخرین نویسنده: کاربر.** توابع `CoreOptions` خالص‌اند و *بعد* از لایه‌های liveness و
  سازگاری اجرا می‌شوند. هر مقداری که کاربر تنظیم نکرده باشد، حذف می‌شود تا خودِ هسته تصمیم بگیرد؛
  هر مقداری که تنظیم کرده باشد، عیناً نوشته می‌شود و هیچ لایهٔ خودکاری آن را بازنویسی نمی‌کند.
- **درِ خروج فرار نمی‌کند: رد می‌کند.** هر JSON دلخواهی را می‌توان به سند نهایی دوخت (addition)، اما
  اگر کلیدهای مالکیت‌دار Marble (`inbounds`، `outbounds`، `routing`/`route`، `dns`، `log`) در آن باشد یا
  سند سالم نباشد، کل درخواست رد می‌شود و سند دست‌نخورده می‌ماند؛ هیچ ادغام نیمه‌کاره‌ای وجود ندارد.

---

## The failure, stated once

The report that opened this chapter was one sentence long: the fragment section is useless when you
are connected. That is not an opinion about fragmentation — fragmentation is a real technique — it
is a statement about **who wrote the value**. Three separate layers could rewrite the numbers on
their way to the config builder:

1. `DpiEvasionPolicy` selected a recipe from the censoring technique it had classified;
2. Iran Mode overrode the recipe again while it was active;
3. `TransportAdaptation` (the learner) pruned and adapted what survived both.

The result was a settings screen that could not fail visibly. A control whose number never reaches
the wire is not a control: it cannot be right, it cannot be wrong, and the user cannot tell the
two apart. The same architecture, applied to a feature the user had a **valid** mental model of,
turned a working technique into a placebo.

So this chapter does the two things in the order the report implies. It deletes the fragment
feature **completely** — not hidden, not disabled — and it attacks the cause by making the cores'
own options the user's, with no layer between the switch and the JSON.

## What was deleted

| Deleted | Where it lived |
|---|---|
| The Fragment & Mux page, its card, its wire-state banner, its ladders and choosers | `Aether2026.kt` |
| `FragmentProfile`, `MuxProfile`, `TransportPair`, `TransportMemoryRecord`, `TransportAdaptation`, `HierarchicalThompson`, `PageHinkley` | `core/TransportAdaptation.kt` (whole file) |
| The generated `fragment-direct` / `tls-fragment` dialers and every overlay pass that wrote a recipe onto a hop | `core/XrayConfigHardener.kt` |
| `record_fragment`, `fragment`, `fragment_fallback_delay` on the wire, and the fragment half of the TLS translator | `core/SingBoxTransportPolicy.kt`, `SingBoxTransportTranslator.kt` |
| `fragmentEnabled`, `fragmentPackets`, `fragmentLength`, `fragmentInterval`, `fragmentMaxSplit`, self-inserted `tcpMaxSeg`, the recipe vocabulary (`TLSHELLO`, `HAMRAH_STEEL`, …), the adaptive-fragment switch, `transportMemory` | `model/Models.kt`, `data/AppStore.kt` |
| The recipe ladders in the DPI policy, the Iran-Mode fragment writer, the tuner's fragment budget | `core/DpiEvasionPolicy.kt`, `core/IranShield.kt`, `core/ConnectionTuner.kt` |
| The tests and chapters that pinned all of it | `app/src/test`, `docs/FRAGMENT_MUX_PAGE_V206.md`, `docs/FRAGMENT_PROFILES_V208.md` |

**What deliberately stays.** A *hand-imported* document is the user's own config, and Marble does
not rewrite it. `XrayConfigHardener` still recognises a `freedom`/`direct` hop that carries a
`fragment` object and keeps it in the chain (its `sockopt` is filled where the author left
omissions, exactly as for any other hop, and `LinkDeadlinePolicy` still budgets its first-write
pacing). `ProxyParser` still reads such a document and `SingBoxConfigBuilder` still *reports* that
sing-box has no equivalent. The difference is the direction of the arrow: the app no longer
**writes** a fragment shape, it only preserves one it was given.

## What replaced it: `core/CoreOptions.kt`

One file, pure functions, no policy calls. Settings in, one JSON object out. Every value is written
in the core's own vocabulary, and a value outside it is normalised at the *reader* in
`data/AppStore.kt`, never silently rewritten on the way to the wire.

### Xray

| Setting | Written as | Notes |
|---|---|---|
| `xrayLogLevel`, `xrayDnsLog` | `log.loglevel`, `log.dnsLog` | `none,error,warning,info,debug` |
| `xraySniffingEnabled`, `xraySniffingRouteOnly`, `xraySniffMetadataOnly` | `sniffing.enabled/routeOnly/metadataOnly` | `metadataOnly` is omitted when off |
| `xraySniffDestOverride` | `sniffing.destOverride` | the user's order ∩ `{http,tls,quic,fakedns}`; `fakedns` is appended when the fake pool is armed, because Xray refuses a session that came out of the pool without it |
| `xrayAllowLan`, `xrayHttpInboundPort` | inbound listen address / HTTP inbound port | |
| `xraySocksUdpEnabled` | SOCKS inbound `settings.udp` | written as a boolean, never as an omission |
| `muxEnabled`, `muxConcurrency`, `muxXudpConcurrency`, `muxUdp443` (`skip/allow/reject`) | `outbounds[].mux` | |
| `xraySockoptDomainStrategy`, `xrayTcpNoDelay`, `xrayTcpKeepAliveIntervalSec`, `xrayTcpUserTimeoutMs`, `xrayTcpCongestion`, `xrayTcpMptcp`, `xrayTcpWindowClamp` | `sockopt.*` | a neutral value (0 s, 0 ms, blank, `AsIs`) is **absent**, so the core's own default and the liveness profile still apply |
| — (the Routing page owns `routing.domainStrategy`/`domainMatcher`) | `routing.domainStrategy/domainMatcher` | written by the hardener from `routeDomainStrategy`/`routeDomainMatcher`, next to the rules they describe; this page names the control instead of adding a second writer for one key |
| `xrayPolicyHandshakeSec`, `…ConnIdleSec`, `…UplinkOnlySec`, `…DownlinkOnlySec`, `…BufferSizeKb` | `policy.levels."0".*` | the whole block is absent when every value is neutral |
| `xrayExtraJson` | merged into the finished document | see the refusal contract below |

### sing-box

| Setting | Written as |
|---|---|
| `singBoxLogLevel`, `singBoxLogTimestamp` | `log.level`, `log.timestamp` |
| `singBoxSniffEnabled`, `singBoxSniffOverrideDestination`, `singBoxResolveDestination` | `inbounds[].sniff*`, `route.…` / sniff action |
| `singBoxAllowLan`, `singBoxHttpInboundPort` | inbound listen / HTTP inbound port |
| `singBoxInboundUsername`, `singBoxInboundPassword` | the mixed inbound's `users` (omitted together when blank) |
| `singBoxMuxEnabled` + `singBoxMuxProtocol` (`h2mux/smux/yamux`), `singBoxMuxMaxConnections` (1–128), `singBoxMuxMinStreams` (0–128), `singBoxMuxMaxStreams` (1–1024), `singBoxMuxPadding` | `multiplex` on a protocol that accepts it (`vless/vmess/trojan/shadowsocks`); otherwise the request is **reported**, never written |
| `singBoxUnifiedDelay`, `singBoxCacheFile`, `singBoxPreferParser`, `singBoxConnectTimeoutSec` | `experimental.*`, `route.default_dial_timeout` |
| `singBoxExtraJson` | merged into the finished document |

### The escape hatch

`CoreOptions.merge(target, extraJson, protectedKeys)` has three outcomes and no fourth:

* **blank** → no-op;
* **a JSON object** → objects merge key by key, recursively; every other value (array, string,
  number, boolean, null) **replaces** what was there. An array is one whole decision — a half-merged
  rule list would be neither the user's nor the app's;
* **a refusal** → the document does not parse, or is not an object, or names a key Marble owns —
  the `inbounds`/`outbounds`/`dns`/`log` blocks plus Xray's `routing` and sing-box's `route`
  (`CoreOptions.PROTECTED_KEYS` / `CoreOptions.SINGBOX_PROTECTED_KEYS`: sing-box spells the routing
  block differently, so the builder passes its own set) — and then **nothing** is applied, the
  document is left byte-identical, and the reason is one sentence in the UI and one `COREOPTIONS`
  event in the log.

Everything else is fair game: `stats`, `api`, `metrics`, `burstObservatory`, `observatory`,
`reverse`, `policy`, `fakedns`, any nested `sockopt`, a whole extra sing-box `experimental` block.

## The rule that makes the screens honest

`CoreOptions` is called **after** the automatic passes, and that order is the contract:

```
build config → liveness / address-family / compatibility passes → CoreOptions (the user)
             → extra JSON → sanitize (TLS pinning) → write
```

A field the user left at its neutral value must stay *absent* so that the automatic layers can fill
it. A field the user set must survive them. That is one rule, and it is what the unit tests in
`app/src/test/java/com/marbleng/app/core/CoreOptionsV211Test.kt` pin: the neutral document has no
invented keys, the user's numbers come out verbatim, and a refused patch leaves nothing behind.

## Verification

* `CoreOptionsV211Test` — sniffing order and the armed fake pool, socks UDP as a boolean, the
  sockopt "only what the user changed" contract, the absence of a second routing writer, policy and
  log, the merge semantics
  (object merge, array replace, blank no-op, parse refusal, protected-key refusal).
* `scripts/system-integrity-check.py` — the V211 block asserts the fragment feature is *gone*
  (no page, no card, no learner file, no settings key, no generated dialer) and that the options
  surface, both extra-JSON editors and the refusal path are present.
* `SingBoxTransportPolicyV208Test`, `XrayConfigHardenerTest`, `SocketFlightV168Test`,
  `SingBoxCoreV151Test`, `ReservedTagCollisionV183Test`, `ResolverSinkholeV163Test` — rewritten
  around the surviving behaviour: the multiplex field names, the reserved tags, and the fact that an
  imported fragment hop is preserved and budgeted rather than generated.
