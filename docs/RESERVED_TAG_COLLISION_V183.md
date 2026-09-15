# RESERVED OUTBOUND TAG COLLISION — V183

**Why two serverless profiles (`Serverless-v50-fragA` / `-fragB`) died at load with
`Xray exited with code 23 … app/proxyman/outbound: existing tag found: block`, why the app answered
with "Kill switch active • Core/configuration error" and a Bug Finder that found nothing, and why
the fix is in the document MarbleNG writes — not in the profile, not in the core.**

Core pin: unchanged — `XTLS/Xray-core` from `core-lock.json`. The marker every layer of the proof
greps for is `MARBLE_RESERVED_TAG_COLLISION_V183`.

---

## خلاصه (Persian summary)

- **باگ اصلی:** Xray با کد ۲۳ بالا نمی‌آمد چون سندی که ماربل برایش می‌نوشت **دو** خروجی با تگ
  `block` داشت: یکی از خودِ پروفایل (کانفیگ‌های serverless طبق نمونهٔ رسمی XTLS خودشان
  `block`/`direct`/`dns-out` دارند) و یکی که `XrayConfigHardener` همیشه آخرِ لیست اضافه می‌کند.
  مدیرِ خروجی‌های Xray تگِ تکراری را در لحظهٔ لود رد می‌کند، پس هیچ بسته‌ای ارسال نشد و هر دو
  پروفایل در ۹ ثانیه به‌طور یکسان شکست خوردند.
- **اصلاح ۱ (ریشه):** قبل از خواندنِ گرافِ خروجی‌ها، هر تگِ واردشده که با تگ‌های خودِ ماربل
  (`block`, `direct`, `dns-out`, `fragment-direct`, `tls-fragment`) تداخل دارد به `import-<tag>`
  تغییر نام می‌دهد و همهٔ ارجاع‌ها (`dialerProxy`, `proxySettings.tag`) دنبالش می‌روند. تگ‌های
  تکراریِ خودِ سند هم فقط یک بار emit می‌شوند.
- **اصلاح ۲:** Xray پروتکل‌های `direct` و `block` را به‌عنوان نام مستعارِ `freedom` و `blackhole`
  می‌پذیرد، اما خوانندگانِ ماربل فقط نامِ اصلی را می‌شناختند؛ یعنی `"protocol": "direct"` می‌توانست
  به‌عنوان پروکسی و `"protocol": "block"` به‌عنوان dialer اشتباه گرفته شود. حالا `XrayConfigRepairs`
  آن‌ها را canonical می‌کند و همهٔ مجموعه‌های infra هر دو املا را می‌شناسند.
- **اصلاح ۳ (گزارش):** `XrayStartFailure` خطای لودِ هسته را طبقه‌بندی می‌کند (تگ تکراری / سند
  ردشده / پورت اشغال). Bug Finder یک چکِ `FAIL` با عنوان «Xray core start-up» و راه‌حل می‌دهد،
  حالتِ BLOCKED به‌جای «Core/configuration error» می‌گوید «Configuration rejected by Xray»، و
  خطِ خطا حالا با `Failed to start:` شروع می‌شود نه با سه خطِ `[Info] app/dns` که علت را می‌بُرید.
- **هشدار packages.xml:** این خط را هر هستهٔ اندرویدیِ sing-box در هر استارت چاپ می‌کند و کرشی که
  زمانی همراهش بود در باینریِ ماربل اصلاح شده؛ پس به‌تنهایی `INFO` است نه `WARN`. کرشِ واقعی
  همچنان شاخهٔ `FAIL` جداگانهٔ خودش را دارد.
- **هشدار Historical process exits:** خودِ چک حالا خلاصهٔ هر رکوردِ crash/ANR را حمل می‌کند و
  بخشِ `PROCESS EXIT HISTORY` دقیقاً با همین نام و همیشه وجود دارد.
- **DNS demote / TCP stressed:** این‌ها شواهدِ شبکه‌اند نه باگ نرم‌افزاری، و به همان شکلِ قبل
  گزارش می‌شوند؛ در این تغییر عمداً دست نخوردند.

---

## 1. The evidence

```
XRAY | start-result | ok=false | elapsedMs=223 | phase=failed | alive=false | inbound=false
  reason=Xray exited with code 23: … [Info] app/dns: DNS: created DOH client … |
  Failed to start: main: failed to create server > app/proxyman/outbound: existing tag found: block
VPN  | blocked | mode=tun | killSwitchHold=true | xrayAlive=false | tunOpen=true
APP  | state | from=CONNECTING | to=BLOCKED | detail=Kill switch active • Xray exited with code 23 …
```

Two profiles, nine seconds apart, byte-identical failure. The core never dialled anything.

## 2. The cause

`XrayConfigHardener.harden()` copies the imported outbounds it keeps, appends its own
`fragment-direct` / `tls-fragment` / `direct` / `dns-out` and — unconditionally — a
`{"tag":"block","protocol":"blackhole"}`. The pinned core's outbound manager
(`app/proxyman/outbound/outbound.go`, `AddHandler`) refuses the second definition of any tag:

```go
if _, found := m.taggedHandler[tag]; found {
    return errors.New("existing tag found: " + tag)
}
```

The upstream XTLS `serverless_for_Iran.jsonc` ships `block-out`, `tcp-direct-out`, `dns-out`,
`tls-fragment`; hand-edited derivatives (the ones the user's `-fragA/-fragB` were) shorten them to
the bare names. `dns-out` and `tls-fragment` collided too — they simply were not the first
duplicate the core met.

A second, quieter defect sat underneath: the core accepts `"protocol": "direct"` and
`"protocol": "block"` as aliases of `freedom`/`blackhole` (`infra/conf/xray.go`), but every Marble
reader (`infra`, `isSelectableProxy`, `ProxyParser.infraProtocols`,
`CoreConfigSuperset.INFRA_OUTBOUND_PROTOCOLS`, `SingBoxConfigBuilder`, `AppRepository`) knew only
the canonical spelling.

## 3. The fix

| Layer | Change |
|---|---|
| `XrayConfigHardener` | `renameReservedImportedTags()` runs before the graph is read: colliding imported tags → `import-<tag>` (unique against the document), `dialerProxy`/`proxySettings` references rewritten. Duplicate tags already in the document are emitted once. `infra` recognises both spellings. |
| `XrayConfigRepairs` | `canonicalizeProtocolAlias()`: `direct→freedom`, `block→blackhole`, reported as `protocol-<alias>-to-<canonical>`. |
| `ProxyParser`, `CoreConfigSuperset`, `SingBoxConfigBuilder`, `AppRepository` | infra sets include the aliases. |
| `XrayStartFailure` (new) | Classifies a load refusal (`DUPLICATE_OUTBOUND_TAG`, `CONFIG_REJECTED`, `PORT_IN_USE`) with the cause line, a headline and a remediation; reads the runtime timeline when the in-memory reason is gone, and only when the *latest* start failed. |
| `XrayManager.summarizeStartFailure` | The failure hint leads with the fatal line instead of the DNS `[Info]` prologue. |
| `MarbleVpnService` | BLOCKED `faultClass` = "Configuration rejected by Xray" / "Local port in use"; the diagnostic event carries `faultKind` and the cause line. |
| `BugFinder` | New `Xray core start-up` FAIL check in every app state; packages.xml probe alone is INFO; `Historical process exits` WARN carries per-record summaries; section renamed to `PROCESS EXIT HISTORY` and always present. |
| `SingBoxAndroidRuntime.PACKAGE_MANAGER_REMEDIATION` | No longer tells the user to update or switch engines for a line every healthy start prints. |

## 4. The proof

`ReservedTagCollisionV183Test` pins: unique emitted tags for the XTLS shape; the imported fragment
chain survives the rename with its `dialerProxy`; reference rewriting including alias uniqueness;
in-document duplicates emitted once; alias canonicalisation; a `block`-protocol outbound never
selected as the proxy; classification of the exact exit-23 line (tag named, `[Info]` excluded,
remediation names `import-block`); the timeline fallback and its recovery rule; the failure hint
ordering.
