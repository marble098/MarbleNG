# MARBLE_V149 — TLS peer pinning and honest ping methods

## خلاصهٔ فارسی

دو دستهٔ باگ در این نسخه رفع شد.

**۱) نگاشت فیلدهای TLS (علت اصلی «وصل هست ولی اینترنت نیست»).**
دو گزینه‌ای که کاربر ست می‌کند دقیقاً معادل دو فیلد بومی Xray هستند:

- «Verify peer certificate by name» → `tlsSettings.verifyPeerCertByName`
- «Certificate fingerprint (SHA-256)» → `tlsSettings.pinnedPeerCertSha256`

پارسر MarbleNG هیچ‌کدام را داخل `streamSettings.tlsSettings` نمی‌نوشت: فقط کلید بلندِ
`pinnedPeerCertSha256` را می‌خواند و کلیدهای کوتاه واقعی لینک‌ها (`pcs` و `vcn`) را نادیده
می‌گرفت. نتیجه این بود که Xray به‌جای pinning می‌رفت سراغ اعتبارسنجی استاندارد از روی CA سیستم؛
و چون سرورهای pinning گواهی self-signed دارند، **هر** هندشیک شکست می‌خورد. هسته زنده بود و
SOCKS گوش می‌داد، پس اپ «متصل» نشان می‌داد، ولی هیچ جریانی کامل نمی‌شد.

نکتهٔ فرمت: برخلاف تصور رایج، Xray برای این فیلد **hex** می‌خواهد نه base64 — کد هسته
`hex.DecodeString` را اجرا می‌کند و هر مقداری که ۳۲ بایت نشود کل کانفیگ را رد می‌کند. حالا هر سه
فرمت (hex، hex با دونقطه، و base64) پذیرفته و همیشه به hex کوچک تبدیل می‌شود.

**۲) روش‌های پینگ.** هر چهار روش برای سرورهای کاملاً سالم «شکست‌خورده» گزارش می‌دادند. علت‌ها
جداگانه بودند و همه رفع شدند (جدول پایین).

---

## 1. Root cause — the TLS field mapping bug

### 1.1 What the user set

```
vless://<uuid>@vps1.maje.eu.org:8443
  ?vcn=vps1.maje.eu.org
  &pcs=c882…c843,a237…ac07,0fc0…85f0,ee5f…e2c6
  &security=tls&sni=spotify.com&flow=xtls-rprx-vision&fp=chrome&alpn=h2,http/1.1
```

`vcn` and `pcs` are the canonical short keys for Xray's two peer-verification fields. The profile
fronts `spotify.com` as the SNI while the real certificate belongs to `vps1.maje.eu.org` — which
is precisely why both options exist.

### 1.2 What MarbleNG generated

`ProxyParser.stream()` read exactly one pinning key, the long form `pinnedPeerCertSha256`, and
never `pcs` or `vcn` at all. `ManualConfigBuilder` had no fields for either. The emitted block was:

```json
{ "serverName": "spotify.com", "fingerprint": "chrome", "alpn": ["h2","http/1.1"] }
```

### 1.3 Why that is a guaranteed, silent, total failure

With no pinning fields present, `RandCarrier.verifyPeerCert` (Xray `transport/internet/tls/config.go`)
falls through to standard chain validation against the system root CAs, with `ServerName` =
`spotify.com`. A pinning server is self-signed by construction, and the name does not match the
fronted SNI, so validation fails on **every handshake, without exception**.

Nothing about that failure is visible from outside: the Xray process starts, the SOCKS inbound
binds, and Marble's own liveness checks all pass. The app truthfully reports *the core* as
connected. Only the streams die. That is the exact "connected, no Internet" signature.

### 1.4 Secondary interaction with Marble's own IDENTITY layer

`MarbleVpnService` logs `IDENTITY / initial-pin-deferred` on a fresh session: Identity Guard pins
the first observed exit IP rather than verifying one that does not exist yet. This layer is
*independent* of Xray's `pinnedPeerCertSha256` — it pins an egress **IP**, not a certificate — but
the two produced a confusing combined symptom: Identity Guard waits for a first exit observation
that can never arrive, because every TLS handshake underneath it is already being rejected. Fixing
the field mapping removes the deadlock at its source; Identity Guard's deferral logic is correct
and is left unchanged.

### 1.5 The fix

`TlsPinningPolicy` is the single authority for both fields and is applied at **four** layers, so
no path can bypass it:

| Layer | Entry point | Why it is needed |
|---|---|---|
| Share links | `ProxyParser.stream()` / `parseHysteria2()` | Reads `vcn`/`pcs` plus every alias |
| Manual editor | `ManualConfigBuilder.tlsSettings()` | Two new draft fields with validation |
| Pasted JSON | `ProxyParser.profileFromConfig()` | Never passes through the link parser |
| Last gate | `XrayConfigHardener.harden()` / `hardenForDelayTest()` | Repairs already-stored profiles |

Format handling, pinned against Xray-core `v26.7.28`:

- `pinnedPeerCertSha256` is emitted as **comma-separated lowercase hex**. Hex, OpenSSL colon-hex
  and base64 input are all accepted and converted. An element that does not decode to exactly
  32 bytes is dropped, because Xray rejects the *entire* configuration on one malformed pin.
- `verifyPeerCertByName` is emitted as a comma-separated, trimmed, lowercased DNS name list.
- `allowInsecure` is **never emitted**. It is a removed feature in this core:
  `TLSConfig.Build()` returns `PrintRemovedFeatureError` when it is true, which fails config load.
  A legacy `allowInsecure` request is translated into `verifyPeerCertByName` carrying the endpoint
  name; a real pin always outranks it.

---

## 2. Ping methods — why healthy servers read as FAILED

| Method | Root cause | Fix |
|---|---|---|
| **TCP (recommended)** | The gate ran a JSSE `SSLSocket.startHandshake()` with `endpointIdentificationAlgorithm = "HTTPS"`, i.e. it asked the *device CA store* to validate the proxy's certificate against the endpoint host. Self-signed certificates and fronted SNIs — the normal case for a proxy node, and the whole reason the pinning options exist — fail that check every time. | The gate now writes its own well-formed TLS 1.3 ClientHello (with SNI and `key_share`) on a raw socket and reads the first 5 bytes. A Handshake (`0x16`) **or** an Alert (`0x15`) record is a verified answer; non-TLS bytes also count as proof of life. No trust store, no certificate parsing, no hostname verification. When TLS is silent but not reset, it falls back to a raw TCP connect instead of convicting the node. |
| **ICMP Ping** | `ping -n -q` was invoked, but `-q` **suppresses the per-packet `time=` lines** that the parser's `time=([\d.]+) ms` regex depends on. `rttValues` was therefore empty on every run and the probe returned `no-responses` — even when the same output said `0% packet loss`. This failed 100 % of ICMP pings on every device. | `-q` removed; the summary `min/avg/max` line is parsed as a fallback for stripped-down builds; statistics outrank the exit code; and ICMP silence now confirms against a raw TCP connect, because most VPS providers and mobile carriers drop ICMP outright. |
| **HTTP GET** | Any status outside `200..399` was `UNREACHABLE`, so a 403 from a CDN edge, a 404 from a moved `/generate_204`, or a 429 rate-limit failed a route that had provably completed a full round trip. Without a tunnel it measured a public 204 origin — identical for every server, and blocked wholesale on censored links. | Any complete status line is a success; the code travels in `failureReason` for diagnostics. Without a tunnel the request now targets the **selected endpoint**, falling back to the verified gate. |
| **HTTP HEAD** | All of the above, plus an unconditional `inputStream.read()` that made a HEAD request block waiting for a body a HEAD response is defined never to have, and that throws on 4xx/5xx where only `errorStream` is readable. | `responseCode` is read under its own guard; the body drain is skipped for HEAD and uses `errorStream` for error statuses. |
| **All methods** | Successes were divided by the *configured* sample count, but the early-abandon rule can stop the loop after two attempts — so 2/2 answers under an 8-sample budget published **25 %** success, which the UI and the ranker read as a failing server. | The denominator is the attempts actually made, matching what `summarize()` already did for the other methods. |

---

## 3. Regression guards

- `TlsPinningPolicyTest` — hex/colon-hex/base64 normalization, malformed-pin rejection, both
  fields reaching `tlsSettings`, `allowInsecure` never emitted, recursive document repair.
- `PingMethodTruthV149Test` — TLS record classification (Handshake, Alert, non-TLS, truncated) and
  full structural validation of the generated ClientHello, including that an IP literal endpoint
  omits SNI (RFC 6066) and that every declared length is self-consistent.
- `scripts/system-integrity-check.py` — asserts the pinning policy exists, that both Xray field
  names are written, that no `allowInsecure` is emitted anywhere in production sources, and that
  ICMP never re-acquires `-q`.
