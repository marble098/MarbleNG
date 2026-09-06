# MARBLE_PING_TRUTH_V147 — Ping methods: critique, removal and accuracy fixes

## A short Persian summary (خلاصه)

صفحهٔ «پینگ» در تنظیمات، شش روش داشت که چهارتای آن‌ها هیچ‌وقت دربارهٔ خود پروکسی نبودند:

- **ICMP** و **HTTP** و **DNS** اصلاً از تونل نمی‌گذشتند (به‌ترتیب `ping` معمولی، مسیر مستقیم به
  Google، و فقط رزولوشن DNS سیستم). نتیجه این بود که همیشه عددِ مسیرِ اینترنتِ خودِ گوشی را می‌دادند،
  نه وضعیت سرور؛ در خروجی پینگِ هر سرور هم همان عدد تکرار می‌شد.
- **TCP** فقط همان گیتِ لایه‌ی ۰ بود که در «Smart» به‌صورت داخلی استفاده می‌شد. اگر به‌جای Smart
  انتخاب می‌شد، یک handshake به آدرسِ سرور به‌عنوان «وضعیت پروکسی» جایگزین می‌شد؛ درحالی‌که account،
  protocol و route را اثبات نمی‌کرد.

علاوه‌بر حذف این چهار گزینه از مدل و صفحهٔ تنظیمات، چند مشکلِ دقت در همان دو روش باقی‌مانده هم ریشه‌ای
رفع شد:

1. **درصد موفقیت دیگر جعل نمی‌شود.** قبل از این، اگر گیت از هر ۳ نمونه فقط ۲ نمونه جواب می‌داد،
   Smart نتیجه را به‌صورتِ `successPercent = 100` ثبت می‌کرد. حالا همان `up to 66` می‌ماند.
2. **نمونهٔ گرم (warm-up) در همه‌جا حذف می‌شود.** صفحه می‌گفت «اولین نمونه دور ریخته می‌شود»، ولی
   مسیرهای HTTP/Tunnel آن را نگه می‌داشتند و مدین را به‌سمتِ مقدارِ سردِ آرپ/کاناترکت می‌کشیدند.
3. **سقف مخفی «سه نمونه» حذف شد.** Smart و Real test با تعداد نمونهٔ تنظیم‌شده کار می‌کنند؛
   انتخاب کاربر دیگر صرفاً یک پیشنهاد نیست.
4. **وقتی تونل متصل است، روشِ آدرس‌محور دیگر نمی‌تواند خواندنیِ تونل زنده را عوض کند.** سؤالِ
   «پینگِ Home» وقتی متصل هستی همیشه «این مسیر زنده خوب است؟» است.

---

## 1. What the settings page actually offered (and why it was wrong)

| Old method | What it measured | Why it was ineffective for this app |
|---|---|---|
| `HYBRID` (Smart) | Fast TCP+DNS gate, then (when a tunnel existed) HTTPS RTT | The only fast candidate, but it overwrote gate success with 100 % |
| `TUNNEL` (Real test) | Real Xray core + HTTPS through SOCKS | Correct, but it silently capped samples at 3 |
| `TCP` | Endpoint TCP/TLS handshake | Endpoint-only; never proved protocol/account |
| `ICMP` | `/system/bin/ping`, bypasses the proxy | Carriers filter it; measures the phone path, not the server |
| `HTTP` | Direct HTTPS to a fixed 204 origin | Same number for every server, so ranking was meaningless |
| `DNS` | Cloud/system resolver round trip | Resolver only; for literal IPs it returned instantly without network I/O |

The page even admitted three of these were not proxy tests in its own descriptions, while still allowing
them to be selected and then using the selected method to rank servers. That is the core product bug:
a measurement surface presented a **network-path meter** as a **proxy-server meter**.

## 2. Root-cause defects found and fixed

### 2.1 `smartPing` promoted a partial gate to a perfect verdict

`RouteProbe.smartPing` ran the verified `reachabilityExtended` gate and then, on any successful
TCP fallback, returned `tcpResult.copy(successPercent = 100)`. A node that answered 2/3 samples (or a
node with injection evidence that still had one good sample) therefore entered ranking as 100 % healthy.
Real loss/jitter evidence was thrown away at the moment it mattered most.

**Fix:** preserve `tcpResult.successPercent`; set `failureReason = "partial-tcp-gate"` when it is below
100. The magic `60`/`40` fallback confidence values were also removed — the measured success rate is now
the only confidence carried next to the measured latency.

### 2.2 The warm-up sample was advertised but only honoured on one path

The UI said *“The published latency is the median; the warm-up sample is discarded.”* In code:

- `RouteProbe.measure` (TCP/ICMP) discarded it.
- `reachabilityExtended` (Smart's gate) did **not**.
- `httpPingBatch` (Smart's tunnel phase / the real-tunnel path) did **not**.
- `tunnelHttpsMeasure` did **not**.

So the number depended on how recently the same server had been probed (cold ARP/NDP, cold
carrier-NAT conntrack, cold TLS session). That is history-dependent noise, not a path property.

**Fix:** all four paths now call the same `summarize`/discard logic and drop the first measured value
when at least three samples are present. Success and loss percentages still count every attempt; only
the latency distribution drops the first value.

### 2.3 A hidden “3 samples” ceiling

`smartPing` passed `samples = gateSamples.coerceAtMost(3)` to `httpPingBatch`, and
`measureUnified`/`tunnelHttpsMeasure` additionally clamped to 3. A user who chose 8 samples still got a
median of at most 3 HTTPS round trips. This is exactly the class of hidden override that `PingBudget`
was created to eliminate.

**Fix:** `httpPingBatch` and `tunnelHttpsMeasure` now use `PingBudget.samples(samples)`. The gate still
keeps dead nodes cheap; the HTTPS phase is no longer allowed to shrink the honest tail.

### 2.4 Connected Home ping could be replaced by an address method

When connected, `measureConnectionPing` had a branch for TCP/ICMP/HTTP/DNS that measured the selected
profile's endpoint (or the underlay) while the live tunnel was right there. The Home readout then
claimed to be the ping of the route while actually reporting something else.

**Fix:** the connected Home ping is always the verified in-tunnel ladder. “Selected method” still
applies to disconnected server pings and to ranking, where there is no active tunnel to consult.

### 2.5 The budget page claimed concurrency was global

The “Servers at once” control said *“Every ping in the app … obeys exactly these values.”* In reality
`BenchmarkEngine` applies `PingBudget.concurrency` only to direct (Smart) sweeps; the real-tunnel path
uses a native-safe `2..4` core pool because each candidate spawns a real Xray child. The page is
honest now: the row is labelled **Smart servers at once**, and it states the native-safe ceiling for
Real test.

## 3. What remains

- **Smart** (`ProbeMethod.HYBRID`) — verified Layer-0 gate (TCP + TLS ServerHello/Alert, Happy-Eyeballs
  family racing, 50–400 ms anti-probing stagger), configured sample count, warm-up discarded, real
  measured success rate. Fast and comparative. When the app is connected the Home readout is upgraded
  to the live in-tunnel HTTPS ladder.
- **Real test** (`ProbeMethod.TUNNEL`) — one real Xray core per config, HTTPS through SOCKS, warm-up
  discard, configured sample count. Slow; the only method that can call a config a failed tunnel.

TCP/ICMP/HTTP/DNS remain as **internal primitives** in `RouteProbe` because Smart needs them. They are
no longer product methods, so an address verdict can never be presented as a proxy verdict.

## 4. Guard to prevent regression

`scripts/system-integrity-check.py` now asserts:

- `ProbeMethod` is exactly `{ HYBRID, TUNNEL }`.
- The settings UI never contains removed `ProbeMethod.TCP ->` / `ICMP ->` / `HTTP ->` / `DNS ->` cases.
- `app/src/test/.../ProbeMethodV147Test.kt` pins the same method set in unit tests.
