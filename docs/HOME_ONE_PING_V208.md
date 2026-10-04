# MARBLE_HOME_ONE_PING_V208 — one Home ping, with the V209 status-strip follow-up

*Core:* `app/src/main/java/com/marbleng/app/ui/MarbleHomeStyles.kt` (`HomeGroupPingButton`,
`HomePingButtonSize`), `app/src/main/java/com/marbleng/app/core/HomePingScope.kt`,
`app/src/main/java/com/marbleng/app/core/CausalAttribution.kt` (`NationalEventBannerPolicy`)
*Wiring:* `HomeTopActionBar` → `HomeActions.onPingGroup` → `AppRepository.pingHomeGroup` →
route-source `testSource` → `pingProfiles` → `publishHomeRoutePing`.


## The report

> 1. On the home screen, clicking the ping button in the top box makes the box disappear and
>    reappear for a moment. This must not happen.
> 2. There should be no ping at the top of the page. One ping button only, and its action is to
>    ping the current group.

Both reports are about the same header, and they turned out to share a cause: the header had too
many things to say and too many ways to say them.

## 1. The flicker (V208 history; status strip removed in V209)

In V208 the box was `HomeRuntimeNotice`, a one-line result under the header. Its presence used to be
`repo.message.isNotBlank()`. V209 removes both the composable and its call site; the description below
records why that intermediate fix was made, not the current Home UI.

A tap on the ping button starts `pingHomeGroup() → testSource() → pingProfiles()`, and that sweep
rewrites `repo.message` on every progress tick ("3/20 endpoints • NL-07") and once more when it
finishes. Between the tap and the task's first write there is a frame where the old outcome has
been cleared and the new label has not landed, so the bar left composition — and its entrance is a
spring (`marbleSpringIn`, built on `composed { }`, which replays the whole rise from zero alpha
every time the composable re-enters). The page therefore showed a box, nothing, and the same box
arriving again, in under a second.

The same flapping had a second, quieter source: a bar whose height followed its text made the whole
page breathe as the progress line grew and shrank.

The V208 fix used two rules, one per failure:

1. **Presence is one boolean that cannot flap.** The bar stayed present while there was something to
   say *or* while work was running, so a sweep could not remove it mid-flight.
2. **The slot is a fixed height.** The former `MarbleNoticeHeight = 38.dp` slot used one compacted line.

Both rules are now historical: V209 removes the slot because Home should not show transient status
text at all.

A third flapper was closed in the same pass: the national-event banner re-ran attribution on every
evidence update, so an inconclusive ping could toggle it. `NationalEventBannerPolicy`
(`core/CausalAttribution.kt`) now keeps the previous verdict unless the new evidence clears a 0.6
confidence threshold — hysteresis on a banner, so a measurement cannot blink it.

## V209 follow-up — no transient Home status

The transient result strip under the Home header is intentionally gone. `HomeRuntimeNotice` and its
fixed-height slot were deleted; the root message effect only expires settled repository messages so
old results do not linger in state. Progress remains on the active ping control, connection truth on
the status card, and Android notifications remain separate.

The Home ping also follows the source of the route currently shown on Home, never a stale filter left
on the Servers page. `HomePingScope.sourceId()` falls back to the Manual bucket when the route has no
source id.

## 2. One ping, and it pings the group

The header had grown a small zoo of ping surfaces: a heartbeat trace, a live-ping panel that was
tappable, a deck ping chip that was tappable, a retry row that started a single-route test, a dock
pill that did the same — several of which printed a latency number next to the connect control.

The page's question is not "how fast is this one server", it is "how good is my group right now".
So:

- **`HomeGroupPingButton`** is the only ping control on the page. It calls
  `HomeActions.onPingGroup → repo.pingHomeGroup()`, which measures the subscription the route on
  screen belongs to (or the manual library when there is none). It is a 38 dp circle —
  `HomePingButtonSize` is a named constant precisely because it never changes: idle and cancelling
  are the same circle, so pressing it cannot re-layout the header.
- **No latency text in the header.** The button is a verb, not a meter: the glyph is `PULSE` idle
  and `STOP` while sweeping, the hue cyan or danger, and no number. A number next to the connect
  control invites the reader to treat a 130 ms mobile link as a fault.
- **Every other ping surface is a display.** `kineticClickable` was removed from the Studio live-ping
  panel and the deck ping chip; the Atelier failure row and the dock pill now route to `onPingGroup`
  instead of starting a single-route test of their own.

The route's latency cell stays honest without a second verb: `publishHomeRoutePing` takes the
sweep's own sample for the route on screen and writes it into `selectedPingMs` /
`selectedPingState` / `selectedPingFailure`. It never starts a measurement, and it returns
immediately while a tunnel is up — when connected, `livePingMs` and the route monitor own that
number and a stored probe would be stale the moment it landed.

## Checks

- `scripts/system-integrity-check.py`: one Home group-ping control, no transient status-strip composable,
  no `kineticClickable` in `HomeLivePingSlab` or `HomeLivePingMeter`, and `publishHomeRoutePing` wired
  into the sweep.
- The chapter's predecessor (`MARBLE_HOME_HEARTBEAT_PING_V206`) is the invariant this replaces: a
  heartbeat that *looked* like a measurement was the wrong answer to "the header shows a number",
  and the number is now simply not there.
