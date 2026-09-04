# QR camera import, Manual-source removal, band placement and the Smart ping rescue — V123

Seven product changes in one pass, each one traced to the behaviour it replaces.

---

## 1. QR import has two doors (`MARBLE_QR_CAMERA_V123`)

**Before.** `+ → Import from QR code` opened the system image picker. A code held up on another
phone could only be imported by photographing it in another app first.

**Now.** The menu names both doors:

- **Scan QR code** — a live viewfinder (`MarbleQrCameraScanner`), runtime `CAMERA` permission asked
  for at the moment of the tap.
- **QR code from gallery** — the existing image picker, still permission-free.

The gallery door is also reachable *from inside* the viewfinder, so switching costs one tap.

Both doors decode on device:

- `QrImageDecoder` (existing) reads a bitmap through ZXing.
- `QrFrameDecoder` (new) reads the luminance plane of a `YUV_420_888` camera frame — row padding,
  sensor rotation and light-on-dark inversion included.

No camera library, no ML model, no frame written to disk. `android.hardware.camera` is declared
`required="false"`, so a device without one still installs and still imports from the gallery.

`QrFrameDecoder` is pure Kotlin over a byte array, so the whole decode path is unit-tested by
rasterizing the product's own `QrCode` encoder output back into a frame layout.

## 2. The Manual source is gone (`MARBLE_MANUAL_SOURCE_REMOVAL_V123`)

**Before.** A Settings switch decided whether hand-built nodes, pasted configs and chains had a home
at all. With it off, every one of those actions failed with "enable Manual in Settings first", and
the Servers page carried a second, parallel bucket with its own group, chip and filter entry.

**Now.** The switch, the bucket, the group, the filter chip and the `manualSourceEnabled` preference
are removed. One rule replaces them:

> A server the user authored is saved into the source they are looking at; when that is a view
> ("All groups") or nothing exists yet, it is saved into a permanent local source created on first
> need.

The local source is an ordinary local `Subscription` — listed, renamable and deletable like any
other group — not a parallel concept. Marble Freedom stays the only source that cannot receive
imports, because its rows are generated locally.

Existing installs migrate on start: profiles stored under the retired `manual` id are re-homed into
the local source and marked user-owned, so nothing becomes invisible.

## 3. No empty-state panel (`MARBLE_SERVERS_NO_EMPTY_STATE_V123`)

The "Nothing matches" / "No connections yet" card is removed from the Servers list. The header
already carries the live server count, the search field shows what is being filtered and `+` is one
tap away, so the page stays quiet instead of putting a card where the user expects servers.

## 4. A ping glyph that reads as ping (`MARBLE_PING_GAUGE_ICON_V123`)

The old glyph was a dot under two hairline arcs; at the 13–17 dp the Servers page renders it, it
read as a smudge. It is now a Wi-Fi fan opening upward from a solid pivot with a bold speedometer
needle pointing into the fan's fast zone, drawn at the same stroke weight as the icons beside it,
and the page-wide ping control renders it at 21 dp in the accent tone.

## 5. Adding a subscription by paste works (`MARBLE_SUBSCRIPTION_PASTE_V123`)

Three separate reasons a pasted link failed:

1. **A name was mandatory.** The button stayed disabled until both fields were filled. The link is
   now sufficient; the group is named after the provider's host (`SubscriptionLink.nameFor`).
2. **The URL was taken verbatim.** A clipboard hands over the link with a trailing newline, inside
   angle brackets, or after `link:`. `SubscriptionLink.normalize` reduces all of those to the bare
   URL before anything else looks at it.
3. **The result was invisible.** `addSubscription` returned `Unit`, so the sheet closed on a
   refusal and the reason lived only in a transient message. It now returns the created source's id
   (or `null`), and the sheet closes only on success.

The generic paste box also recognises a subscription link and routes it to the subscription flow,
instead of handing it to the config-link parser and reporting "0 profiles imported".

## 6. Band-shaped connect controls get a real slot (`MARBLE_CONNECT_BAND_PLACEMENT_V123`)

The slide-to-connect band and the classic switch are wide and short. Dropped into the round
shutter's slot they floated in the middle of a hero ring, and in the Orbit console a 248 dp band sat
inside a column a fraction of the screen wide.

Every style now keeps the round shutter exactly where it has always been and gives a band its own
lower, full-width slot through `HomeConnectionBand`, which sizes itself to the column:

| Style | Round shutter | Band |
| --- | --- | --- |
| Signature | centre of the hero field | foot of the hero field, on the ring baseline |
| Cosmic Orbit | inside the orbit ring | under the orbit, inside the same panel |
| Cosmic Immersion | under the sky | below the identity block |

All three stay above the fold, so the primary action never requires scrolling.

## 7. Smart ping stops failing healthy servers (`MARBLE_SMART_PING_RESCUE_V123`)

**The bug.** `ProbeMethod.HYBRID` was not a method of its own — `BenchmarkEngine.testCandidate`
fell through to the same path as `TUNNEL`. Every server paid for a child Xray process plus an HTTPS
budget clamped to 1.2–2.5 s, and on a phone that budget is routinely lost to process start-up and
CPU contention. The measurement came back empty, `success` was 0, and the card showed a red cross —
for nodes that answer a handshake in 40 ms and work in every other client.

`RouteProbe.smartPing` had the same class of bug on its own path: with no tunnel it ran a *direct*
HTTPS request to a well-known 204 endpoint as its "real measurement". That request never touches the
server under test and is blocked on every filtered network, so its failure poisoned the verdict.

**Now.**

1. `RouteProbe.smartGate` — one TCP handshake (Happy-Eyeballs racing plus the existing transient
   retry), and one resolver round trip as a second opinion when the handshake is empty and the host
   is a real name. No child process.
2. Only a gate-passer pays for the verified tunnel measurement, and it gets a start-up budget of at
   least 6 s so a cold child process on a loaded device is not counted as a dead node.
3. A gate-passer whose verification could not complete is reported **reachable but unverified** —
   `GATE_ONLY_SUCCESS` (60%) with the real handshake latency — and is never recorded as tunnel
   intelligence, so it can neither show a false red cross nor teach the ranker that an unproven node
   is a working route.

Ranking is untouched: Rank and PattRank still force `ProbeMethod.TUNNEL`.

---

## Verification

- `scripts/system-integrity-check.py` — 128/128 invariants.
- `tools/kotlin-structure-check.py` — balanced sources, no unterminated literals.
- `tools/compose-scope-check.py` — 0 composable-context violations (it caught, and this branch
  fixes, `Aether.*` reads inside the scanner's `Canvas` draw scope).
- The four inline invariant checks of `.github/workflows/verify.yml`, run locally.
- New JVM unit tests: `SubscriptionLinkTest`, `QrFrameDecoderTest`, `SmartPingGateTest`.
- Kotlin compilation and the unit-test task run in CI (`:app:testDebugUnitTest`,
  `:app:compileDebugKotlin`, `:app:compileReleaseKotlin`).
