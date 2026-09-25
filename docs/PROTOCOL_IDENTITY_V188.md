# MarbleNG Protocol Identity & List Refinement v188

`MARBLE_PROTOCOL_IDENTITY` + `MARBLE_SETTINGS_DEDUP` + `MARBLE_DOCK_NO_TAP_FADE` is a design
chapter. It gives every wire scheme its own minimal visual identity, rebuilds the two server
lists around it (Servers page and Home), removes the duplicated doors in Settings, and stops the
bottom dock from fading on a tab tap. The visual reference is the current generation of modern
VPN clients on the Play Store (the ZedSecure lineage): circular icon containers, quiet tinted
surfaces, hairline borders that light up with state, and a latency readout that owns its own
column instead of floating in the middle of a row.

## 1. One identity per protocol — `MarbleProtocolIdentity.kt`

Before this chapter a server's type was a plain text chip (`VLESS/REALITY`) in a flat colour,
and the two lists disagreed about the colour: the Servers page painted Trojan emerald and
Shadowsocks amber, the Home page painted them the other way around.

One new file, `app/src/main/java/com/marbleng/app/ui/MarbleProtocolIdentity.kt`, now owns the
whole identity system:

- **`ProtocolFamily`** — the ten wire families Marble can name: VLESS, VMESS, TROJAN,
  SHADOWSOCKS, HYSTERIA2, WIREGUARD, SSH, SOCKS, HTTP and the `PROXY` fallback.
  `protocolFamilyOf(scheme)` normalises whatever the parser stored.
- **`protocolTone(family)`** — the single tone table both lists read. One scheme, one colour,
  on every screen, for good.
- **`ProtocolGlyph`** — a small hand-drawn Canvas silhouette per family, all in one 24×24
  stroke space so the weight ratio survives from an 11 dp badge to a 40 dp tile: a V with a
  signal ripple, an envelope, a shield with a keyhole, a sock, twin chevrons, a key, a terminal
  prompt, a tunnel ring, a request/reply swap, and a sealed hex for the unknown.
- **`ProtocolBadge`** — the tiny type chip: glyph beside a 9 sp label on a tint that is barely
  there (10 % fill, 24 % hairline). Smaller and quieter than the old text-only badge.
- **`ProtocolTile`** — the circular container that opens every server row: glyph on a quiet
  tint, a 1 dp rim that takes the state colour (emerald = carrying traffic, amethyst =
  handshake, cyan = stored selection) and casts a soft shadow, and an optional country-flag
  chip riding the rim's edge.
- **`ServerStateChip`** — the "Connected / Securing / Selected" word at its quietest size:
  9.5 sp on a tint pill.
- **`ServerPingStat`** + **`PingQualityBars`** — the latency readout both lists now share:
  the number and its unit on top, a three-bar quality meter under it, in a fixed-width
  right-aligned stat column. Running probes show the wavy spinner; a failed probe is a red ✕;
  an untried server is a quiet dash.

## 2. The two server lists, rebuilt around one anatomy

The Servers-page row and the Home row had drifted apart — different tiles, different badges,
different ping treatments. They now share one anatomy, in both languages and in RTL:

```
( ( ◍ ) )  ▮ Server name            [Connected]
  tile    ▸ VLESS/REALITY  host:port        34 ms
                                                  ▂▄▆
```

- a circular **protocol tile** (type + flag + state on the rim),
- the **name** beside a tiny state word,
- the **protocol badge** beside the endpoint,
- the **latency stat column** at the trailing edge — no longer a lone number floating in the
  middle of the row,
- the row's menu / check mark last.

What left: the 3 dp state bar (the rim carries the state now), the separate flag text (the
tile carries it), the monogram tiles on Home (`VL`, `SS`, …), the two divergent tone tables,
and the second latency treatment. Everything else — stacked group boxes, swipe-to-edit,
marquee names, the same selected/connected predicates — is untouched.

## 3. Settings: one door per thing

The hub used to answer "which core?" from four doors — *Engine & tunnel*, *Tunnel core*,
*Xray core settings* and *sing-box extended* — three of them re-rendering the same switch
cards a second time. And *General & servers* on the Connection card opened the exact page
*General* on the System card.

After the dedup:

- **Connection** — *Network & routing*, *Tests & ranking*.
- **Appearance** — *Theme*, *Home style*, *Fourth tab*, *Typeface*, *Language* (unchanged).
- **System** — *Notifications*, *Engine & tunnel* (now badged with the running core's name),
  *General*, *Information*.

The *Engine & tunnel* workspace is the only engine page: it gained the *Engine* switch card
(it opens with the running core's name on its own title line), the *Pinned versions* card and
the *Delay test* card from the retired *Tunnel core* page, so no control was lost — the
duplicates were. The separate `core` / `xray-core` / `singbox-core` pages and their
`SettingsPages` keys are gone; a restored page key from an older build falls through to
Information instead of crashing the navigator. The *Fourth tab* card that duplicated the
slot's own page (the same switch, the same Customize button) is gone from the General
workspace; the slot's page remains the one home of that setting.

## 4. The dock no longer fades on a tap

`MARBLE_DOCK_SCROLL_ONLY_V123` had the right idea — the glass state belongs to real scrolling,
never to a tap — but the turn marker that enforced it was cleared in
`LaunchedEffect(currentTab)`, and the pager flips `currentPage` at the **middle** of a
programmatic turn. The marker died exactly when the animation was still running, so the back
half of every tab tap re-triggered the glass fade.

The marker now survives the page flip: `goToTab` sets it, and only a settled pager clears it
(the `isScrollInProgress` watch). Tapping the tab that is already on screen is a no-op. A
real finger scroll and a real content scroll still fade the bar, as designed.

## Compatibility

No engine, routing, storage or behaviour path changes. minSdk stays 26. The system integrity
audit pins the new invariants: the shared latency readout stays tone-only (no tinted fill
grows back), the engine page still names the running core on its own title line, and the
expert-gate read-out survives with the gating it no longer drives.
