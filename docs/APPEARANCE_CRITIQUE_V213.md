# Appearance critique V213 — the seven surfaces that were not Android

> *"Criticise the app's appearance harshly, then rebuild it: the boxes, the tabs, the servers, the
> flags, the settings, the options and the notifications — bring all of it to the newest native
> Android."*

The request is one sentence and it names seven surfaces, which is the right shape for a critique:
a "the app looks bad" report is unfalsifiable, but seven named surfaces can each be checked against
the platform they are drawn on. What follows is that check, surface by surface, and the verdict is
the same seven times — **the product had invented its own answer where Android already has one.**

Nothing here is a matter of taste. Every item below is a *measurable* disagreement with the
platform: a colour that is not a platform role, a size below a platform floor, a control with no
platform anatomy, a notification field the platform expects to be set and the app left empty.

---

## 1. The boxes — films, not objects

What they were: `background(fill)` plus a 1 dp rim in an accent hue, and — where there was a
shadow — a shadow cast in the brand's own blue *on a page that is already blue*. Measured
consequences:

| Defect | Measurement |
|---|---|
| The shadow is the surface's hue | `spotColor = accent @ 32 %` over an ice-blue page: the lift and the page are the same family, so the edge does the work the shadow was supposed to do |
| The lift is below the threshold of sight | `PrismSurface.RestingElevation = 2 dp` on a layered page — a 2 dp ambient shadow at 16-22 % alpha composites to a ΔL the eye reads as noise, not as depth |
| The rim is a drawn rectangle | one accent/violet gradient wrapped around all four edges, identical at the top and the bottom, so the surface has no light source and no relationship to the page |
| Two adjacent cards disagree | a Home card carried the V191 depth contract, the server box four pixels below it carried a hairline — that reads as *cheapness*, not as two components |

The platform's answer, which the product was ignoring: a card is an **opaque tonal container**
(`surfaceContainerLowest` in a light theme, a lifted step above the black floor in a dark one), its
edge is a **neutral directional hairline** from the `outlineVariant` family, and its lift is a
**shadow pair** — ambient and spot in the surface's own hue — plus a tight **contact** shadow that
puts the box *on* the page instead of above it.

## 2. The tabs — a pill of pills

The selected tab's indicator was `RoundedCornerShape(22.dp)`. On a bar whose own corner is 30 dp,
and whose height is 64 dp, a 22 dp corner is a *rounded rectangle inside a stadium*: two different
roundness languages stacked 4 dp apart, which is the single most common way an app announces that
its navigation is custom. Android's active indicator is a full round (**radius follows the box**),
and the platform's bar is a `surfaceContainer` step with an `outlineVariant` hairline — not a
hand-mixed `#EDF3FB` wash with a navy stroke at 14 %.

The defect underneath the shape: the bar's body and its hairline were **invented colours**, so
whatever else in the product moved to a wallpaper palette, the dock stayed the brand's. A bar that
is not a step of the surface ladder is a bar that can never match the cards it floats over.

## 3. The servers — a grid that is a different material from the page

The compact box had just been put on the depth contract (V212), which fixed *whether* it sits on a
light but not *what it is made of*: fill from the inset-glass token, rim from the accent's hairline,
corner at 13 dp — a level Android does not have. The list beside it had the same problem in the
other direction: the subscription frame was drawn with the **accent** hairline (`GlassBorder`), so a
page of six subscriptions showed a dozen brand-blue rims competing with the state colours the rows
use to say "connected" and "selected". And the nested list's fill was the product's glass token
rather than a container step, which is why the servers list and the settings list looked like two
apps.

## 4. The flags — stickers, not chips

`CountryFlagCircle` painted a 3:2 flag edge-to-edge into a clipped circle with **no rim of its
own**. On a light card the pale bands of a real flag (Japan, Poland, Greece, the Nordic crosses)
dissolve into the surface: the mark has no edge, so it reads as a smudge rather than as an object.
Every surface Android draws a country on — a contact chip, a settings row, a place in Maps — gives
it a hairline. And the unknown-location tile (the world glyph) sat in the same circle with *no*
frame at all, so "measured country" and "nobody knows" were two different kinds of picture instead
of one object in two states.

## 5. The settings — six cards of equal weight, rows under the touch floor

| Defect | Measurement |
|---|---|
| The row is below Android's touch floor | `SettingSwitch` painted ~36 dp (7 dp padding inside a 13 dp box) — under the 48 dp minimum in the platform's own accessibility guidance; the *hit box* was padded elsewhere, which is exactly the trick that makes a product feel imprecise under the thumb |
| The row has no platform anatomy | no leading container, no divider, no value slot, no chevron: a settings row on Android is (leading icon container · title · supporting line · trailing control) |
| The switch is custom-painted | `checkedThumbColor = Color.White`, `uncheckedTrackColor = GlassStrong`, `InkFaint` thumbs — four hand-picked colours per call site, so the control ignored a wallpaper palette and its *off* state was not the platform's |
| The heading shouts | group titles were uppercased **and** letterspaced **and** tone-coloured: three signals for one word |
| The cards are rims, not containers | `Brush.verticalGradient(tone @38 % → tone @14 %)` — an accent rectangle drawn around every group |

## 6. The options — chips pretending to be radios, switches pretending to be status lights

A single-choice control is a **radio row** or a **segmented button** on Android. The product drew a
bordered box per option and flooded the chosen one with a 14 % accent wash — a button that had been
told to behave like a radio — so a page of four choices read as four competing controls instead of
one question with four answers. And the "allow insecure certificate" switch was painted amber when
on: a switch is not a status light, and a warning belongs on the *label*, where it names the
consequence.

## 7. The notifications — every platform field left empty

| Field the platform expects | What the app sent | What a user sees |
|---|---|---|
| `setColor` | not set | the small icon and app name in the system's default grey — the most visible "this app did not bother" signal in the shade |
| `setSubText` | not set | one long sentence crammed into the body ("Connected • Frankfurt • 42 ms") |
| `setGroup` / `setLocalOnly` / `setBadgeIconType` | not set | a tunnel's live status offered to the user's other devices; badge behaviour left to defaults |
| per-kind colour | one grey for all six kinds | a privacy warning, a recovery event and a subscription refresh were indistinguishable before reading |
| the action icon | the app's own status-bar mark | the logo standing in for the verb "stop" |
| channel fields (`setSound(null)`, `enableVibration(false)`, `setShowBadge`, `setLockscreenVisibility`) | not set | the **ongoing tunnel channel buzzed and dinged** on every live-stats update, because a silent status channel was never declared |

---

## The verdict, in one line

The product had a design system and used it as a substitute for the platform: every one of the seven
defects above is a place where Marble invented a colour, a size, a shape or a field that Android
already defines. The V213 chapters keep the parts that were the product's own — the state grammar,
the measured data, the copy contract, the motion — and hand the *material* back to the system.
