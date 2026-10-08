# MARBLE_HOME_PING_CONTROLS_V212 — every ping button measures something, and the user says what

*Core:* `app/src/main/java/com/marbleng/app/ui/MarbleHomeStyles.kt` (`runHomePingAction`,
`homePingActionLabel`, `homePingActionDetail`, `HomeEvidence.pingGaugeAction/pingChipAction/pingHeaderAction`),
`app/src/main/java/com/marbleng/app/model/Models.kt` (`HomePingAction`, `parseHomePingAction`)
*Tests:* `app/src/test/java/com/marbleng/app/model/HomePingControlsV212Test.kt`,
`app/src/test/java/com/marbleng/app/ui/ServerTileParityV212Test.kt`
*Wiring:* `HomeLivePingMeter` and `HomeShortcutDeck` (Studio), the `HomeSessionStats` latency cell and
`HomeTopActionBar` (Home styles), Settings › Home › **Ping controls**.


## The report

> The home page has two ping buttons; one of them does nothing when it is pressed and the other one
> works. Both should work, and there should be settings to choose what each of them does.

That sentence is a layout complaint with a design decision buried in it, so it is worth being
explicit about what the earlier chapters actually decided, because this one reverses part of them.

`MARBLE_HOME_ONE_PING_V208` collapsed the page to **one** ping verb. The gauge and the deck pill
stopped accepting taps on purpose: they had each started a *different* measurement, so the page had
two controls that both said "ping" and disagreed about what that meant. `MARBLE_HOME_ROUTE_PING_V210`
kept that shape and changed the surviving verb's scope to the route on screen.

Both were answers to a real defect. Neither was an answer to *this* one. A user who presses a control
and gets nothing has not been protected from ambiguity — they have been given a broken button, and
the reason it is broken is invisible.

## The two halves

**1. Every ping surface is a control.** The gauge, the deck pill, the latency cell and the header
pulse all take a tap. Each one obeys the product's own enabled-ness rule for a ping control
(`homePingTappable(evidence)`), so a live sweep still cancels instead of stacking, a route
measurement is still single-flight, and none of them can start a second measurement while one is
running.

**2. The verb is a setting, not a property of the shape.** Three preferences, one per surface:

| surface | setting | ships as |
| --- | --- | --- |
| latency gauge and latency cell | `homePingGaugeAction` | `route` |
| shortcut pill | `homePingChipAction` | `group` |
| header pulse | `homePingHeaderAction` | `route` |

Four verbs, and all four are real destinations:

| verb | what it does |
| --- | --- |
| `route` | measure the server on screen (through the tunnel while one is up) |
| `group` | sweep the subscription that server belongs to |
| `library` | sweep the whole library — the bulk verb that used to live only on Servers |
| `tests` | open the Tests workspace instead of measuring |

`onPingLibrary` is new to `HomeActions` and maps to `AppRepository.testAll()`: the same bulk
measurement the Servers page's *Ping all* runs, so pointing a Home control at it does not create a
second sweep implementation.

## Why the vocabulary is shared and not per-widget

The mapping from a stored id to an action is one function (`runHomePingAction`), the label a control
speaks is one function (`homePingActionLabel`), and both read the same enum. The V208 defect was not
that two widgets accepted taps — it was that each widget hard-coded its own idea of "ping", so the
two could not be compared, and nobody could tell from the page which one was wrong. A per-widget
constant list would have restored the taps and kept that failure mode.

Because the label is derived from the same enum the control resolves, a control cannot say one thing
and do another: the accessibility name of the header pulse is the verb it runs, and the Settings
chooser draws its options from `HomePingAction.entries`, so a verb added later appears in the chooser
without a second list to update.

## Persistence

Three new `AppSettings` fields, stored as ids and parsed leniently: `parseHomePingAction` maps
older or unknown spellings (`"node"`, `"all"`, `"settings"`, …) onto a real action, and anything
else onto `HomePingAction.DEFAULT`. A preference that outlives a release is how a button goes dead,
so an unrecognised value resolves to a working control rather than to no-op.

## Checks

- every surface accepts a tap and resolves its verb — `ServerTileParityV212Test`
- one mapping, four non-empty destinations — `ServerTileParityV212Test`
- Settings writes all three fields and draws the controls' own vocabulary — `ServerTileParityV212Test`
- ids are distinct, parsing never yields a dead button, and the three surfaces ship on two
  different verbs — `HomePingControlsV212Test`
- the source-wide invariant `V212 every Home ping surface is a control with a configurable verb`
  replaces the V210 one that pinned the gauge as a display
