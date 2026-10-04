# MARBLE_HOME_ROUTE_PING_V210 — the ping buttons ask about the server on screen

*Core:* `app/src/main/java/com/marbleng/app/ui/MarbleHomeStyles.kt` (`HomeRoutePingButton`,
`HomeFloatingSplitControl`, `homeRouteMeasuring`), `HomeActions.onPingRoute`
*Wiring:* `AppRepository.measureHomePing()` → `measureConnectionPing()` while a tunnel is up,
`measureSelectedPing()` while it is down.
*Predecessor:* `docs/HOME_ONE_PING_V208.md`.


## The report

> 1. The floating buttons of the home page must, after connecting, be **both** a stop button and
>    a ping of the server that is connected.
> 2. The ping button in the top box of the home page (the connection-status box) must ping that
>    same server.

Both reports are about one decision V208 made and this chapter reverses — carefully, because the
decision was right about the diagnosis and wrong about the prescription.

## What V208 got right, and what it got wrong

V208 found a real defect: the Home page had grown a zoo of ping surfaces, several of which
printed a latency number, so a user could not tell which one measured what. Its fix was "one ping
button, and it pings the **group**": the page's question was declared to be *how good is my
subscription right now*, the floating control lost its ping half, and every other ping surface
became a display.

The count was right. The scope was not.

A pulse button sitting on the status box of a page that is showing **one named server**, pressed
by someone whose tunnel just went slow, is not being asked about a subscription. It is being
asked about *that server*. Telling that user "we measured your 200-node subscription" is a true
sentence answering a question nobody asked — and the second half of the report is the direct
consequence: with the split control's ping half gone, the one measurement a **connected** user
can reach for required a trip to another page.

## The change

**One verb, one destination, two doors.** `HomeActions.onPingRoute` is the route measurement:

- while a tunnel is up it measures the **live route through the tunnel** — the only honest answer
  to "is my connection alive?";
- while it is down it measures the **selected route's endpoint**.

`HomeTopActionBar` and `HomeFloatingSplitControl` both call it. Two buttons that answer the same
question are one verb, or they are one bug — which is exactly what the old page had, and why the
predicate "is the route being measured?" is one function (`homeRouteMeasuring`) rather than two
copies of the same `when`.

The group sweep is not removed — it is re-homed. `pingHomeGroup()` / `onPingGroup` still exist,
the Servers page still sweeps a subscription, and the fourth tab's PULSE page keeps the one
remaining door to it, relabelled **Ping group** so it says what it measures. Two other call sites
moved to the route verb because their own copy was already promising the route: the Atelier's
failure row ("Measure the route again") and the header itself. What changed, in the end, is which
door the **Home header** is: it is the route's door, because the route is what the header is
sitting on top of.

**The header pulse is renamed and re-scoped.** `HomeGroupPingButton` → `HomeRoutePingButton`, and
its accessibility label names the server it will measure, so the button can never promise a scope
it does not deliver. The V208 rules that were about honesty and stability are kept exactly as
they were: no latency number on the button, a constant 38 dp circle in every state, and no
transient status strip under the header.

**The split control has two halves again.** While the tunnel is down it is one shutter-style FAB;
the moment it is up it is the pause disc on top of a measure disc, both anchored so the lower
action never moves (V202's footprint rule is untouched).

## The one state that is not a promise

A group sweep is cancellable, and the header is where Home cancels one (V156): while a sweep
runs, the pulse is a `STOP` square and `cancelProbes()` is behind it.

A single-route measurement is **not** cancellable — there is nothing on the other side to
interrupt. So the two "busy" states are drawn differently on purpose:

| running | glyph | tap |
| --- | --- | --- |
| group sweep | `STOP` square, danger hue | cancels the sweep |
| route measurement | spinner, measure hue | none — the control is disabled |

A disabled `STOP` would be a button offering an action it cannot perform. The spinner is the
honest version of "wait": the measurement is single-flight, so a second tap would be a no-op
anyway.

## Checks

- `scripts/system-integrity-check.py` — `HomeRoutePingButton(` exists, `onPingRoute()` is the
  verb the header calls, `onPingGroup()` no longer appears in `MarbleHomeStyles.kt` (one verb per
  surface), the group sweep keeps exactly one door (`onPingGroup()` appears once, on the fourth
  tab's PULSE page, behind a label that names the group), `homeRouteMeasuring` is one predicate
  used by both controls, and `onPingRoute` is wired to `repo.measureHomePing()`.
- The V208 checks that are still true are kept: no `HomeRuntimeNotice`, no `MarbleNoticeHeight`,
  `HomePingButtonSize` unchanged, and the two Studio live-ping surfaces are still gauges with no
  `kineticClickable`.
- The chapter this reopens (`MARBLE_HOME_ONE_PING_V208`) is the invariant it replaces; both are
  cited in the check names so the history is visible where it is enforced.
