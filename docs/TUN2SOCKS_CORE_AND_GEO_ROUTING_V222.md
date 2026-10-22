# TUN2SOCKS CORE & COMPLETE GEO ROUTING — V222

**Two answers in one chapter. The tunnel now has a second engine the user can choose, pinned to an
upstream release and bumped by CI like every other core; and the sing-box engine stops dropping geo
tags it cannot find in the APK — every tag the routing page accepts is materialised from the same
pinned `.dat` files Xray already routes with.**

Marker: `MARBLE_TUN2SOCKS_CORE_V222` / `MARBLE_SINGBOX_GEO_ROUTING_V222`. Pins: `core-lock.json`
(Xray, HEV, sing-box-extended, **tun2socks v2.7.0**) and `singbox-rules-lock.json`.

---

## خلاصه (Persian summary)

- **یک هستهٔ دوم برای اتصال اضافه شد: `tun2socks`.** `hev-socks5-tunnel` همان چیزی است که ماربل از
  روز اول داشته و پیش‌فرض می‌ماند؛ حالا می‌توانید در تنظیمات → موتور تونل، `tun2socks` را انتخاب
  کنید. این انتخاب از انتخاب هستهٔ پروکسی (Xray / sing-box) جدا است و با آن ترکیب می‌شود: هر هستهٔ
  پروکسی می‌تواند پشت هر دو تونل اجرا شود.
- **چرا کتابخانه و نه فایل اجرایی:** تونل باید همان file descriptor را بخواند که `VpnService`
  ساخته است. `ProcessBuilder` نمی‌تواند descriptor را به فرزند بدهد؛ پس نسخهٔ پین‌شدهٔ
  `xjasonlyu/tun2socks` به‌صورت کتابخانهٔ c-shared (`libtun2socks.so`) در همان پردازش برنامه
  بارگذاری می‌شود.
- **هیچ‌وقت برنامه را نمی‌کشد.** بستهٔ `engine` خودِ tun2socks در خطا `log.Fatalf` می‌زند؛ برای
  کتابخانه‌ای داخل پردازش VPN یعنی `SIGABRT` جلوی چشم کاربر. پوشش Marble همان بسته‌های عمومی را
  ترکیب می‌کند و به‌جای کشتن پردازش، کد خطا و دلیل برمی‌گرداند.
- **انتخاب کامل، نه یک کلید پنهان.** MTU، تایم‌اوت UDP، بافرهای TCP، بافر میانه و سطح لاگ همه در
  تنظیمات هستند، در `AppSettings` ذخیره می‌شوند و در `Tun2SocksPolicy` یک‌جا کلمپ می‌شوند.
- **مسیریابی geo روی sing-box حالا ۱۰۰٪ کار می‌کند.** قبلاً فقط چهار تگ (`geoip:ir`, `geosite:ir`,
  `geoip:private`, `category-ads-all`) نگاشت می‌شد و بقیه — `cn`، `ru`، `category-bank-ir`، هر تگی
  که از `.dat` خودتان می‌آید — با یک یادداشت دور انداخته می‌شد. حالا هر تگ از همان فایل‌های پین‌شدهٔ
  `.dat` به یک rule set از نوع `source` تبدیل می‌شود و واقعاً اعمال می‌شود.

---

## 1. The second datapath

### The choice is a separate axis

`CoreEngine` answers *who speaks the proxy protocol*. `TunnelCore` answers *who moves the device's
packets to that proxy's loopback SOCKS port*:

```kotlin
enum class TunnelCore(val id: String) { HEV("hev"), TUN2SOCKS("tun2socks") }
```

`AppSettings.tunnelCoreId` persists the choice (default `hev`, so an upgraded install behaves
exactly as before). Everything that reads it goes through `AppSettings.tunnelCore()` and the aliases
in `parseTunnelCore`, so a hand-edited or legacy value (`hev-socks5-tunnel`, `t2s`) lands on a real
core instead of silently disabling the tunnel.

`AppRepository.setTunnelCore(core)` mirrors `setCoreEngine` exactly: a no-op for the current core, a
refusal with the loader's own sentence when the APK cannot load it on this device, `stopVpn()` when a
session is live, then persist, log `TUNNEL datapath-switch`, and tell the user to reconnect.

### Why a `dlopen` shim rather than generated JNI

`libtun2socks.so` is a Go c-shared library; `libmarbleng.so` is C. They are loaded side by side, and
`jni/tun2socks_jni.c` resolves the Go symbols with `dlopen`/`dlsym` under a `pthread` mutex:

| condition | result |
| --- | --- |
| library absent, or ABI not packaged | `available() == false`, `-1000` |
| library present, symbols missing | `-1001` |
| descriptor or proxy rejected by the core | `-2` / `-5` with the core's own text |

The consequence that matters: a device whose ABI has no `libtun2socks.so` does not crash with
`UnsatisfiedLinkError` at class init; it says *"This APK does not carry libtun2socks.so for this
device's ABI … Marble stays on hev-socks5-tunnel"*, and `MarbleVpnService` falls back to HEV with a
`TUNNEL datapath-fallback` event.

### The wrapper, and the one bug it exists to prevent

`native/tun2socks/main.go` is copied into the pinned tree as `cmd/libmarble/main.go` and built with
`-buildmode=c-shared`. It composes the same public packages the upstream binary uses —
`fdbased.Open`, `socks5.New`, `tunnel.New(...).ProcessAsync()`, `core.CreateStack` — because
`engine.Insert/Start` calls `log.Fatalf` on failure. Exported ABI:

```
int  marble_t2s_run(proxy, mtu, fd, udp_timeout, tcp_send_buf, tcp_recv_buf,
                    moderate, log_level, log_path)
void marble_t2s_quit()
void marble_t2s_stats(long long *tx_packets, long long *tx_bytes,
                      long long *rx_packets, long long *rx_bytes)
const char *marble_t2s_error()
const char *marble_t2s_version()
```

Return codes: `0` ok, `-1` already running, `-2` proxy, `-3` device, `-4` stack, `-5` descriptor,
`-6` internal. `quit()` closes a channel and returns immediately (an ANR there would be a second
bug); `run()` owns teardown, so a revoked descriptor cannot keep a goroutine alive into the next
session. The descriptor is validated with `unix.FcntlInt(fd, F_GETFD)` *before* the stack is built,
which is what turns "fd -1" into `-5` instead of a crash inside gVisor.

### Options, stored once and clamped once

`Tun2SocksPolicy` is the only writer of the native contract:

| setting | range | default |
| --- | --- | --- |
| MTU cap (`0` = session MTU, never raises it) | 576–9000 | 0 |
| UDP session timeout | 5–600 s | 60 s |
| TCP send / receive buffer | 0 (kernel) – 65536 KB | 0 |
| moderate receive buffer | on/off | off |
| log level | debug / info / warn / error / silent | warn |

`buildConfig(...)` returns `null` — and the connect fails with a sentence — when the port is not a
port or the descriptor is already revoked, rather than starting a datapath that would refuse every
packet. `AppStore` normalises the same values on read, so a hand-edited prefs file cannot install a
zero-byte MTU.

### Packaging and the bump

`scripts/prepare-native.sh` builds the library for all four ABIs (16 KB page flags, machine asserted
afterwards, version marker grepped), and `core-lock.json` pins the tag, the commit **and** the
SHA-256 of the pinned host archive. `scripts/update-core-lock.sh` resolves a new release, and
`update-cores.yml` proves the newly resolved code still compiles Marble's wrapper (`go vet` plus a
c-shared build) before a bump can land. `verify.yml` repeats that build and drives the ABI through
`ctypes`, asserting the version marker and the error codes above — including `-5` for an invalid
descriptor and `-2` for a non-SOCKS proxy.

---

## 2. Complete geo routing on sing-box

### The failure

The sing-box builder mapped four tags and dropped everything else:

```kotlin
internal fun geoTag(ip: Boolean, raw: String): String? = when {
    ip && tag == "private" -> RULE_SET_GEOIP_PRIVATE
    ip && tag == "ir"      -> RULE_SET_GEOIP_IR
    !ip && tag == "ir"     -> RULE_SET_GEOSITE_IR
    !ip && tag == "ads"    -> RULE_SET_ADS
    else                   -> null      // a note, and the rule is silently not applied
}
```

The Routing page could hold the rule, a preset could ship it, Xray applied it — and the sing-box
engine quietly ignored it. That is not a missing feature; it is the same profile routing differently
depending on which core the user picked, with the difference hidden in the diagnostics notes.

### The fix

The app already owns the data: `GeoAssetIndex` parses the pinned Chocolate4U/v2fly `.dat` files for
the Xray engine. `SingBoxGeoRuleSets` turns any requested tag into a sing-box **source** rule set:

```json
{ "version": 3, "rules": [ { "domain_suffix": ["…"] }, { "ip_cidr": ["…"] } ] }
```

- **Every tag the UI can produce is a request**: the implicit Iran/ads rules, every enabled routing
  rule, and every `geosite:` / `geoip:` token inside the block/proxy/direct domain and IP lists.
- **Multi-source means multi-source**: each readable `.dat` that contains the tag contributes, so the
  multi-source setting finally means the same thing on both engines.
- **Bundled wins**: `geoip-ir`, `geosite-ir` and the ads bundle are precompiled and installed from
  the APK, so a tag they already cover never costs a generated file.
- **Offline**: nothing is downloaded at connect time; if the assets are absent the rule is reported,
  never invented.
- **Bounded and cached**: at most 32 sets, 32 MB each, 400 000 items per tag; the file name carries a
  digest of the source stamps, so a reconnect reuses it and an asset update invalidates it.
- **One pass per file**: tags are extracted in a batch. `GeoAssetIndex.extractPayloads` walks a
  `.dat` once, reads each entry's tag code, and fully decodes only the entries somebody asked for —
  twenty tags are no longer twenty passes over a multi-megabyte file. A cached plan does not read
  the files at all.
- **DNS knows what route knows**: a set referenced only by a DNS rule is registered in
  `route.rule_set` too, because sing-box refuses to start when a DNS router names a tag that does not
  exist yet. That ordering bug is what the `usedRuleSets` registry exists to prevent.

The builder then emits one definition per referenced tag with the format of the file it was given
(`source` for generated JSON, `binary` for bundled `.srs`) and `geoTag` resolves the tag through the
prepared map. A tag that is genuinely absent from both the bundle and the local assets becomes a note
that says what to do (download the geo assets in Routing) instead of a silent drop.

### What the tests assert

`SingBoxGeoRoutingV222Test` writes real protobuf `.dat` files (the same wire format Chocolate4U and
vfly publish), runs the production index, the production materialiser and the production config
builder over them, and then asserts on the finished document:

- every requested tag gets a `source` rule set, and the second start reuses it byte for byte;
- a bundled set always wins over a generated one;
- a reverse-match list is reported instead of inverted silently;
- a tag nobody has is reported and never invented;
- domain/IP tokens (`geoip:cn` inside `routeBlockIps`) route instead of becoming a literal CIDR;
- no rule — route or DNS — references a rule set the document does not define.

---

## Verification

- `scripts/system-integrity-check.py` — V222: the second datapath is pinned, packaged, selectable
  and stopped on switch (11 conditions across the lock, gradle, JNI, policy, service and UI).
- `verify.yml` — lock assertions, the c-shared ABI smoke, `:app:testDebugUnitTest`,
  `:app:compileDebugKotlin`, `:app:compileReleaseKotlin`.
- `update-cores.yml` — a resolved tun2socks tag must still compile the mobile wrapper before the lock
  is written.
- `TunnelCoreV222Test` — id round-trip, availability/refusal, clamped native config, MTU cap
  semantics, normalisation of stored settings, and the default product still starting on HEV.

## How to bump

```bash
scripts/update-core-lock.sh            # resolves latest tun2socks (beta channel), writes tag+commit+sha256
scripts/prepare-native.sh              # rebuilds libtun2socks.so for all ABIs
```

`update-cores.yml` runs both in CI; a new upstream release opens the bump, proves the wrapper still
compiles, and the ABI smoke in `verify.yml` proves the built library still answers.
