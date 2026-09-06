# MARBLE_PING_METHODS_V148 — Ping methods and accuracy

## A short Persian summary (خلاصه)

صفحهٔ «پینگ» در تنظیمات حالا هفت روش واقعی دارد که کاربر می‌تواند انتخاب کند:

- **Smart** (پیش‌فرض) — دروازهٔ سریع آدرس + تست واقعی HTTPS از تونل وقتی متصل است. اگر سرور
  فقط به TCP جواب بدهد (مثلاً پورت HTTP یا Shadowsocks)، دیگر به‌عنوان «شکست‌خورده» علامت
  نمی‌خورد.
- **Real test** — یک هستهٔ واقعی Xray برای هر سرور؛ مسیر، حساب و پروتکل را اثبات می‌کند.
- **TCP Connect** — سریع‌ترین بررسی زنده‌بودن: دست‌دادن خام TCP با آدرس سرور.
- **TCP (recommended)** — دروازهٔ سریعِ TCP+TLS؛ کندتر از TCP خام، ولی خیلی سریع‌تر از تست واقعی.
- **HTTP GET** — درخواست کامل HTTPS از مسیر انتخاب‌شده.
- **HTTP HEAD** — همان درخواست با کمترین حجم پاسخ.
- **ICMP Ping** — پینگ کلاسیک از خود گوشی (از پروکسی عبور نمی‌کند).

همهٔ این روش‌ها از همان بودجهٔ اندازه‌گیری (مهلت، تعداد نمونه، همزمانی) استفاده می‌کنند و
پینگ صفحهٔ خانه هم دقیقاً همان روشی را اجرا می‌کند که در تنظیمات انتخاب شده است — حتی وقتی تونل
وصل باشد.

## 1. What changed

V147 had reduced the product to only `HYBRID` (Smart) and `TUNNEL` (Real test), keeping
TCP/ICMP/HTTP/DNS as internal primitives. The main problem users reported was that Smart could
mark a healthy server as failed when the strict verified TCP+TLS gate could not complete (for
example a server that speaks a non-TLS protocol or a fronted endpoint), while Real test was
correct but slow.

V148 keeps the accuracy work from V147 (honest success percentages, warm-up discard, no hidden
sample ceiling, user-owned `PingBudget`) and adds back the address-level methods users actually
choose, with three deliberate exceptions in behaviour:

- **DNS stays removed.** It measured only the local resolver and never the server or proxy path.
- **TCP Connect is raw** (`Socket.connect`) and only proves the port answers. It is the fastest
  liveness check.
- **TCP (recommended)** is the verified Layer-0 gate (TCP + TLS ServerHello/Alert) that Smart
  uses internally. It is fast and detects stateful filters that accept the handshake and then
  kill the stream.

### Method table

| Method | Enum | What it measures | Fast | Proves config |
|---|---|---|---|---|
| Smart (default) | `HYBRID` | Endpoint gate + real HTTPS through the live tunnel | yes | no |
| Real test | `TUNNEL` | Real Xray core + HTTPS through SOCKS | no | yes |
| TCP Connect | `TCP_CONNECT` | Raw TCP handshake to `host:port` | yes | no |
| TCP (recommended) | `TCP_RECOMMENDED` | Verified TCP+TLS gate to `host:port` | yes | no |
| HTTP GET | `HTTP_GET` | Full HTTPS GET through the selected route | medium | no |
| HTTP HEAD | `HTTP_HEAD` | Lightweight HTTPS HEAD through the selected route | medium | no |
| ICMP Ping | `ICMP` | Classic `/system/bin/ping` to the server address | yes | no |

## 2. Router/engine behaviour

- `RouteProbe.measureUnified` dispatches all seven methods. `TUNNEL` reuses the live SOCKS port
  when connected and falls back to the verified TCP gate when it is not.
- `BenchmarkEngine.directProbe` is now true for every method except `TUNNEL`, so sweeps of
  Smart/TCP/HTTP/ICMP run directly in the worker pool and never spawn a throwaway Xray child.
  `directResult` maps each method to its `RouteProbe` entry point.
- `AppRepository.measureConnectionPing` no longer hard-codes the old nine-racer tunnel ladder.
  Connected Home ping now runs the method selected in Settings with the live SOCKS port supplied,
  so the Home readout follows the settings exactly as the user asked.
- `AppRepository.pingProfiles` deduplicates endpoint-level methods (all non-`TUNNEL` methods) and
  keeps Real test per-server.

## 3. Home overlay

The top-of-page `HomePingInlinePanel` and its `showPingInline` state were removed. Ping results
are shown by the in-place `HomeLivePingMeter` and the `HomeShortcutDeck` ping button, so tapping
ping never adds an extra box at the top of Home.

## 4. Guard to prevent regression

`scripts/system-integrity-check.py` now asserts:

- `ProbeMethod` contains the seven V148 methods and never `ProbeMethod.DNS`.
- The settings UI renders every product method and never `ProbeMethod.DNS ->`.
- `HomePingInlinePanel` / `showPingInline` do not exist in the UI.
- `app/src/test/.../ProbeMethodV148Test.kt` pins the same method set in unit tests.
