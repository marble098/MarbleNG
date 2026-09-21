# SOCKET FLIGHT — V168

**Why a connected, fragmented tunnel on a filtered link could still stall for minutes, why the
second engine ran its physical sockets almost naked, and what the pinned cores themselves prove
is safe to enable by default — with no new setting the user has to find and no config value
overwritten.**

Engine pins: unchanged. `XTLS/Xray-core` `v26.9.9`, `shtorm-7/sing-box-extended`
`v1.14.0-extended-2.7.1` (commit `217f776…`, `core-lock.json`), and `heiher/hev-socks5-tunnel`
`2.17.1` (socks5 core submodule `162dd99…`). No core Go/C code was changed for this chapter;
every option below was verified against the pinned source before it was written. The marker
every layer greps for is `MARBLE_SOCKET_FLIGHT_V168`, implemented in
`core/CoreSocketPolicy.kt` and `core/HevTunnelPolicy.kt`, pinned by
`app/src/test/.../core/SocketFlightV168Test.kt`.

---

## خلاصه (Persian summary)

- **ایراد اصلی: وقتی فرگمنت روشن بود، سوکت واقعیِ اتصال اصلاً تنظیم نمی‌شد.** در Xray وقتی
  فرگمنت فعال است، سوکتی که به سرور وصل می‌شود متعلق به outbound آزادِ انتهایی (`fragment-direct`)
  است نه hop پروکسی؛ و هستهٔ Xray دقیقاً `sockopt` همان hop آزاد را روی سوکت اعمال می‌کند. تمام
  تنظیمات keep-alive، TCP_USER_TIMEOUT و Fast Open فقط روی hop پروکسی نوشته می‌شدند — یعنی غالب
  کانفیگ‌های ضدفیلتر در ایران با سوکت «لخت» کار می‌کردند و روی یک لینک مکث‌کرده، اتصال بی‌صدا می‌مرد.
- **MPTCP حالا روی هر دو هسته به‌صورت پیش‌فرض پیشنهاد می‌شود** (`tcpMptcp` / `tcp_multi_path`).
  در سورسِ پین‌شده اثبات شد که دیالر Go قابلیت MPTCP را در زمان اجرا probe می‌کند و روی هر خطایی
  (کرنل قدیمی، مسیر پشتیبانی‌نشده) به‌صورت شفاف به TCP معمولی برمی‌گردد؛ پس روشن‌کردنش هیچ اتصالی
  را خراب نمی‌کند و روی جابه‌جایی آنتن/وای‌فای تلفن، اتصال یکپارچه می‌ماند و توان عملیاتی بالا می‌رود.
- **BBR روی سوکت‌های فیزیکی Xray پیشنهاد می‌شود** (`tcpCongestion: "bbr"`). کرنلی که ماژول BBR
  نداشته باشد با خطای setsockopt جواب می‌دهد؛ در `system_dialer.go` نسخهٔ پین‌شده اثبات شد که این
  خطا فقط در سطح info لاگ می‌شود و دیال شکست نمی‌خورد (همان cubic باقی می‌ماند).
- **هستهٔ sing-box دیگر سوکت خام ندارد:** hop مستقیم (`direct`) که DNS رمزنگاری‌شدهٔ bootstrap و
  ترافیک مستقیم را می‌برد هم اکنون MPTCP، `udp_fragment` و keep-alive ایران‌محور دارد؛ پروتکل‌های
  QUIC (Hysteria2/TUIC/WireGuard) `udp_fragment: true` می‌گیرند — درمان مستندِ حفره‌های PMTU که
  باعث «وصل می‌شود ولی صفحه‌ها نیمه‌باز می‌مانند» می‌شد.
- **هر اتصال جدیدِ اپلیکیشن یک رفت‌وبرگشت SOCKS کمتر دارد:** HEV حالا `socks5.pipeline: true` می‌فرستد
  (دست‌دهی SOCKS5 به‌جای دو رفت‌وبرگشت، در یک رفت پشت‌سرهم بسته‌بندی می‌شود)، استخر UDP از ۱۰ به
  ۳۲ بافر ۱۵۰۰ بایتی بزرگ شد تا انفجار بسته‌های QUIC جذب شود، و مهلت connect از ۱۰ ثانیهٔ
  پیش‌فرض HEV به ۱۵ ثانیه رسید تا دست‌دهیِ روی لینکِ فرگمنتیِ پرتأخیر کشته نشود (و همچنان زیر
  سقف ۱۶ ثانیه‌ای خود هسته‌هاست).
- **یک شکاف تشخیص DPI هم بسته شد:** JSON دستیِ TLS/REALITY که `fingerprint` نداشت با ClientHello
  خام Go بیرون می‌آمد؛ حالا فقط در صورت خالی‌بودن، `chrome` گذاشته می‌شود. لینک‌های اشتراکی و
  کانفیگ‌ساز دستی از قبل chrome می‌گذاشتند و هر مقدار صریح (حتی `unsafe`) دست‌نخورده می‌ماند.
- **پینگ و سرعت:** Fast Open روی انتهای HEV↔هسته در هر دو موتور یکدست شد؛ BDP bufferها (سیاست
  قبلی V141) دست‌نخورده ماندند؛ و هیچ تنظیمات جدیدی به UI اضافه نشد — همه‌چیز از همان کلیدهای
  موجود (`tcpFastOpenEnabled`, `tcpMaxSeg`, حالت ایران) پیروی می‌کند تا رفتار «یک تنظیم، دو
  هسته» واقعاً برقرار باشد.

---

## Defect 1 — fragmentation tuned the wrong socket

`XrayConfigHardener` writes liveness (`tcpKeepAliveIdle/Interval`, `tcpUserTimeout`), TFO and MSS
onto hops whose settings contain endpoint domains. A generated freedom fragment dialer has no
endpoint domain — the **proxy** hop carries the server address — so it never entered that pass.
But once `streamSettings.sockopt.dialerProxy = "fragment-direct"` is attached, Xray opens the real
TCP socket to the server from the freedom hop and applies *that hop's* sockopt to it
(`transport/internet/system_dialer.go` is invoked with the freedom dialer's `SocketConfig`).
Result: with fragmentation on — the dominant anti-censorship shape in Iran — the physical socket
had no keep-alive probes and no `TCP_USER_TIMEOUT`. A link that merely paused (common on MCI /
Irancell transit) left half-open sessions that neither side reaped, and the app reported a
connected tunnel that delivered nothing.

The fix builds the generated `fragment-direct` (and, under two-layer fragmentation, only the
terminal `fragment-direct`, never the intermediate `tls-fragment`) through
`CoreSocketPolicy.writeXrayPhysicalTcpSockopt`, and applies the same fill to a hand-imported
terminal fragment freedom hop. Imported values are always preserved: only omissions are filled.

## Defect 2 — Multipath TCP was never offered

Both pinned cores wire MPTCP, but Marble never emitted the key:

- Xray `v26.9.9` `system_dialer.go`: `if sockopt.TcpMptcp { dialer.SetMultipathTCP(true) }`;
- sing-box extended `common/dialer/default.go`: `if options.TCPMultiPath { dialer4.SetMultipathTCP(true) }`;
- Go's multipath dialer (verified in the Go 1.27 source the pinned Xray builds with) probes
  kernel/path support and **retries the same dial over plain TCP** on `EINVAL` (kernel < 5.6),
  `EPROTONOSUPPORT` or `ENOPROTOOPT`. An unsupported environment therefore behaves exactly as
  before; a supported phone gains subflow survival across radio handovers.

It is offered on every physical TCP hop on both engines, and explicitly stripped (alongside the
other TCP keys) from UDP transports (mkcp/quic/hysteria) where there is no TCP socket.

## Defect 3 — BBR was never requested on lossy transit

BBR copes with the random-loss filtering transit where cubic backs off forever. It is a
per-socket `TCP_CONGESTION` request: a kernel without the module returns an error, which in
`system_dialer.go` is handled as `errors.LogInfoInner(ctx, err, "failed to apply socket
options")` — inside `c.Control`, whose return value never fails the dial. The same code path
proves every other sockopt (and the identical UDP path) fails soft. `tcpCongestion: "bbr"` is
therefore filled only when the imported config does not name one.

## Defect 4 — the sing-box engine's direct and QUIC sockets were untuned

Before V168, sing-box's only dial fields were `tcp_fast_open` and `connect_timeout`, and even
those were absent from the `direct` outbound — the hop that carries the encrypted bootstrap DoH
and every direct-route connection. The unified `writeSingBoxPhysicalDial` now writes, on exactly
one hop per chain (the hop without a `detour`, mirroring Xray's "only the physical socket" rule):

| key | TCP profiles | QUIC/UDP profiles (Hysteria2, TUIC, WireGuard, QUIC transport) |
| --- | --- | --- |
| `tcp_multi_path` | true | true (harmless; no TCP dial is made) |
| `udp_fragment` | true | true — kernel IP fragmentation against PMTU black holes |
| `tcp_fast_open` / `connect_timeout` | setting-driven | setting-driven |
| `tcp_keep_alive` / `tcp_keep_alive_interval` | Iran-aware profile from `SocketLivenessPolicy` | omitted |

`udp_fragment` semantics were checked in the pinned fork's `option/outbound.go`
(`UDPFragment *bool`) and `common/dialer/default.go`: an explicit `false` *installs*
`DisableUDPFragment()`, while `true` leaves the kernel default fragmentation path intact, which
is precisely the QUIC PMTU-black-hole remedy documented for Hysteria2 on mobile.

The Android-CLI forbidden dial keys (`network_strategy`, `network_type`,
`fallback_network_type`, `bind_interface`, `routing_mark`, `protect_path`, package/uid rules —
see `SingBoxAndroidRuntime`) are never emitted; the V155/V157 sanitizer continues to strip them
from imported native documents.

## Defect 5 — every app connection paid a full extra SOCKS round trip

The pinned HEV core's own parser (`src/hev-config.c`, tag `2.17.1`) accepts
`socks5.pipeline: true`; the socks5 core submodule (`hev_socks5_client_handshake_pipeline`) then
sends method selection **and** the CONNECT request back-to-back instead of waiting for the
method-selection reply first. This saves one loopback round trip per new app TCP connection. It
is safe because the SOCKS server is always one of Marble's own pinned cores (Xray socks inbound,
sing-box mixed inbound), both of which buffer the handshake.

The same audit pinned the rest of the HEV document against `hev-config.c` — unknown keys are
silently ignored by that parser, so every emitted key must be one it reads:

| key | value | why |
| --- | --- | --- |
| `socks5.pipeline` | `true` | one fewer RTT per connection |
| `socks5.tcp-fastopen` | follows the existing TFO setting | HEV implements `TCP_FASTOPEN_CONNECT` (`src/misc/hev-utils.c`); the setsockopt result is discarded and the kernel transparently falls back to a normal handshake |
| `misc.connect-timeout` | `15000` | the 10 000 ms default could expire while the core dials through a fragmented chain; kept under the cores' own 16 s dial ceiling |
| `misc.udp-copy-buffer-nums` | `32` | 48 KiB burst pool (default 10 × 1500 B) absorbs QUIC bursts; below the 64 KiB TCP floor so the 86 016 B task stack is unchanged |
| `misc.task-stack-size` | `86016` | unchanged; the C parser still auto-raises it when BDP-enlarged TCP buffers require more |

The YAML moved out of `MarbleVpnService.runTun()` into the pure, unit-tested `HevTunnelPolicy`,
and the emitted key set is pinned in `SocketFlightV168Test` against the accepted-key table taken
verbatim from `hev-config.c`.

## Defect 6 — pasted TLS JSON could leak a bare Go ClientHello

A TLS/REALITY outbound whose `fingerprint` string was blank made Xray present Go `crypto/tls`'s
default ClientHello — a trivially classifiable DPI signature. Share-link import
(`ProxyParser`) and the manual builder already default to `chrome`; `applyDefaultUtlsFingerprint`
closes the remaining gap for pasted JSON only, skips UDP transports (QUIC/KCP/Hysteria have no
uTLS layer), and never overwrites an explicit value including `unsafe`.

## What was deliberately NOT changed

- **No new settings surface.** All behaviour follows existing keys (TFO toggle, MSS override,
  Iran mode). MPTCP/BBR/pipeline/udp-fragment are fail-soft defaults whose failure mode is the
  status quo.
- **No buffer-policy churn.** The BDP-derived HEV TCP/UDP buffer sizing (`LatencyBufferPolicy`,
  V141) remains the authority; only the UDP *burst* pool count changed.
- **No keep-alive on UDP/QUIC profiles** — the concept does not apply; sing-box would reject the
  combination implicitly and Xray's QUIC stack manages its own liveness.
- **No `tcpKeepAliveCount`** — that key does not exist in pinned Xray's sockopt schema; Go uses
  an unlimited count internally.
- **Turbo / ConnectionTuner parity** was checked and already held for the sing-box path:
  `MarbleIntelligence.effectiveSettingsFor` applies the measured `AccelerationPlan` (fragment
  shapes, TFO, MSS, mux, family/DNS plan) to the shared `AppSettings` before either config
  writer runs, so both engines receive identical Turbo decisions.

## CI unblock — the retired `tools` SDK package (2026-09-15)

Source verification went red on every branch (last green `main`: 2026-09-13) in the **Set up
Android SDK** step, before any repo code was compiled. `android-actions/setup-android@v4`
defaults its `packages` input to `tools platform-tools`; Google removed the long-obsolete `tools`
package from the SDK repository, so `sdkmanager tools` now answers `Failed to find package
'tools'` and exits 1. Bumping the action does not help (v4.0.1 keeps the same default). Every
call site — `build.yml`, `verify.yml`, `marble-cloud-gate.yml` and their staged copies under
`docs/workflows-pending/` — now names `packages: "platform-tools"` explicitly; the following
steps install `platforms;android-37` and `build-tools;36.0.0`. The fix is staged in the complete
workflow copies under `docs/workflows-pending/` (the push token cannot write
`.github/workflows/`, the established convention in that directory's README) and pinned by a
named integrity invariant over the staged copies; the live files, including
`marble-cloud-gate.yml` (which has no staged copy), receive the same one-block override when the
staged workflows are installed. Until that install the only red CI step on every branch is this
SDK provisioning step, which fails before any repo code compiles.

## Verification map (pinned source, not documentation hearsay)

- Xray `v26.9.9` `transport/internet/system_dialer.go` — non-fatal sockopt application on both
  TCP and UDP; MPTCP wiring; 16 s dialer timeout.
- Go 1.27 `net/mptcpsock_linux.go` / `tcpsock_posix.go` — MPTCP capability probe and guaranteed
  plain-TCP fallback.
- sing-box extended `217f776…` `option/parser.go` (`ParserOutboundOptions` embeds full
  `DialerOptions`), `option/outbound.go` (dial field schema), `common/dialer/default.go`
  (`TCPMultiPath`, `UDPFragment`, TFO slow-open), `option/direct.go` (direct embeds
  `DialerOptions`, unknown fields rejected — every emitted key must exist).
- HEV `2.17.1` `src/hev-config.c` / `src/hev-utils.c` and socks5-core submodule `162dd99…`
  `src/hev-socks5-client.c` — accepted keys, defaults, pipeline handshake,
  `TCP_FASTOPEN_CONNECT` fail-soft implementation.
- Field guidance on QUIC PMTU: Hysteria2 deployment guidance (`udp_fragment.length` 1200 on
  difficult routes) and the sing-box issue tracking QUIC receive-window throughput ceilings.
