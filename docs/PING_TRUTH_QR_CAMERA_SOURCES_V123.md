# MARBLE_ICMP_REPLY_ANCHOR_V123 — ping truth, two QR intakes, no Manual source

Six user-reported problems and the CI failure that came with them, fixed in one pass.

## 1. ICMP loss was never the real loss (`core/RouteProbe.kt`)

`parseIcmpOutput` matched *any* `time <n> ms` in the batch output. The statistics line ends with
exactly that:

```
4 packets transmitted, 2 received, 50% packet loss, time 3004ms
```

So every batch was credited with one extra 3-second "reply" that no host ever sent:

| batch | reported before | reported now |
|---|---|---|
| 4 sent, 4 answered | 4 samples (5th was the 3 s artifact) | 4 samples |
| 4 sent, 2 answered | 75 % success | 50 % success |
| summary table only (`-q` builds) | 33 % success | 100 % success |
| 4 sent, 0 answered | 25 % success, reachable latency | 0 % success, unreachable |

A reply is now only a reply when its line carries a sequence counter — `icmp_seq=` on
iputils/toybox, `seq=` on busybox — and the success rate is the binary's own
`transmitted/received` tally when it prints one. `time<1 ms` (a LAN echo that rounds below a
millisecond) still counts; `time=99999 ms` still does not.

`RouteProbeTest` gained three regressions: the busybox `seq=` spelling, the statistics table never
counting as a reply, and the sub-millisecond reply.

## 2. QR import: two intakes, not one (`ui/MarbleQrScanner.kt`, `core/QrImageDecoder.kt`)

The `+` menu's QR row is now a submenu with both ways a code reaches a phone:

* **Scan with camera** — a live viewfinder (CameraX) that decodes on-device with the ZXing core
  Marble already ships. No ML model is downloaded, no frame is stored, nothing is uploaded, and the
  provider is unbound the moment the sheet closes. `CAMERA` is requested when the scanner opens and
  is declared with `android:required="false"` hardware, so a camera-less device still installs and
  still imports from pictures. A refusal falls back to the gallery instead of dead-ending.
* **From gallery** — the existing permission-free image picker.

Both land in the same intake. `importQrPayload` decides what the payload *is*: a share link becomes
a node, a provider URL becomes a subscription source that Marble keeps refreshed — previously a
scanned subscription URL was parsed as if it were a dead node.

## 3. The Manual source is gone (`model/Models.kt`, `data/AppStore.kt`, `AppRepository.kt`, UI)

`manualSourceEnabled` and the virtual `manual` bucket are removed: the Settings switch, the Manual
group, the Manual filter chip, the "enable Manual first" refusals and the "Duplicate to Manual"
gate. User-authored configs (paste, QR, file, hand-built node, chain, duplicate) now live in a real
local source, created on first import if the library has nothing else.

`migrateLegacyManualSource()` re-homes rows stored under the old id — identity, measurements and
learned acceleration intact — and re-points a remembered route and a persisted source filter that
pointed at `manual`, so an upgrade restart lands on the same server instead of on nothing.

## 4. "Nothing matches" card removed

A search or filter that matched nothing painted a card with two buttons in the middle of the list.
The list now simply has no rows; the search field and filter rail above it are the way back.

## 5. The ping readout reads as a measurement

`HomeIcon.PING` is a real glyph — emitter dot, two signal arcs, a solid speed bolt — instead of two
hairline arcs that turned to a smudge at 14 dp. The Servers capsule carries it beside the number,
one notch taller (30 dp) with one notch larger type, and a hairline in the measurement's own tone.

## 6. The slide-to-connect band has one home

`ConnectButtonStyle.SLIDE` is a bar, not a centred object, so it no longer sits in the hero slot.
`HomeConnectBandDock` pins it to a glass shelf above the tab dock on all three Home styles
(Signature, Cosmic Orbit, Cosmic Immersion), sized from the real viewport width so it can never
overflow, with each style reserving `MarbleSlideBandReserve` at the end of its scroll content so the
last card is never buried underneath it. The round and classic silhouettes are untouched.
