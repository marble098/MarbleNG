# MARBLE_HOME_ONE_PING_V208 — the top box stopped blinking, and there is one ping again

*Core:* `app/src/main/java/com/marbleng/app/ui/MarbleHomeStyles.kt` (`HomeGroupPingButton`,
`HomePingButtonSize`, `MarbleNoticeHeight`, `HomeRuntimeNotice`),
`app/src/main/java/com/marbleng/app/core/CausalAttribution.kt` (`NationalEventBannerPolicy`)
*Wiring:* `HomeTopActionBar` → `HomeActions.onPingGroup` → `AppRepository.pingHomeGroup` →
`pingProfiles` → `publishHomeRoutePing`.


## The report

> 1. On the home screen, clicking the ping button in the top box makes the box disappear and
>    reappear for a moment. This must not happen.
> 2. There should be no ping at the top of the page. One ping button only, and its action is to
>    ping the current group.

Both reports are about the same header, and they turned out to share a cause: the header had too
many things to say and too many ways to say them.

## 1. The flicker

The box is `HomeRuntimeNotice`, the one line under the header that reports what just happened.
Its presence used to be `repo.message.isNotBlank()`.

A tap on the ping button starts `pingHomeGroup() → testSource() → pingProfiles()`, and that sweep
rewrites `repo.message` on every progress tick ("3/20 endpoints • NL-07") and once more when it
finishes. Between the tap and the task's first write there is a frame where the old outcome has
been cleared and the new label has not landed, so the bar left composition — and its entrance is a
spring (`marbleSpringIn`, built on `composed { }`, which replays the whole rise from zero alpha
every time the composable re-enters). The page therefore showed a box, nothing, and the same box
arriving again, in under a second.

The same flapping had a second, quieter source: a bar whose height followed its text made the whole
page breathe as the progress line grew and shrank.

Two rules, one per failure:

1. **Presence is one boolean that cannot flap.** The bar is present while there is something to say
   *or* while work is running — `if (!MarbleFeedbackPolicy.isOutcome(raw) && !working) return`. A
   tap that starts a sweep can no longer remove the bar mid-flight; it appears once and retires once.
2. **The slot is a fixed height.** `MarbleNoticeHeight = 38.dp`, one line, and the text is compacted
   through `MarbleCopy.oneSentence` so it cannot ask for a second.

A third flapper was closed in the same pass: the national-event banner re-ran attribution on every
evidence update, so an inconclusive ping could toggle it. `NationalEventBannerPolicy`
(`core/CausalAttribution.kt`) now keeps the previous verdict unless the new evidence clears a 0.6
confidence threshold — hysteresis on a banner, so a measurement cannot blink it.

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

- `scripts/system-integrity-check.py`: no `onTestPing` in `HomeTopActionBar`, no `kineticClickable`
  in `HomeLivePingSlab` or `HomeLivePingMeter`, the fixed notice height and its presence rule, and
  `publishHomeRoutePing` wired into the sweep.
- The chapter's predecessor (`MARBLE_HOME_HEARTBEAT_PING_V206`) is the invariant this replaces: a
  heartbeat that *looked* like a measurement was the wrong answer to "the header shows a number",
  and the number is now simply not there.
