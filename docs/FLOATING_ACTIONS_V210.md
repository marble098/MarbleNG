# MARBLE_FLOATING_ACTIONS_V210 — a floating button's face belongs to the theme too

*Core:* `app/src/main/java/com/marbleng/app/ui/MarbleFloatingChrome.kt` (`MarbleFloatActions`,
`MarbleFloatActionTones`, `MarbleFloatActionSet`, `MarbleFloatChrome.inkOn`),
`app/src/main/java/com/marbleng/app/ui/AetherTheme.kt` (`AetherPalette.floatActions`,
`floatInkLight`, `floatInkDark`)
*Tests:* `app/src/test/java/com/marbleng/app/ui/MarbleFloatingActionsV210Test.kt`
*Wiring:* `FloatingConnectFab`, `FloatingSplitAction` (the split control) and `HomeRoutePingButton`.


## The report

> The floating buttons must show different colours in different themes, and be completely
> coordinated and flexible.

Read as a styling request; it is a completeness complaint.

## The chrome was themed, the face was not

`MARBLE_FLOATING_CHROME_V201` gave every theme its own floating **body**, **hairline** and
**shadow** — and left the part the user actually looks at to the call site. The connect disc read
`Aether.Cyan`, the pause read `Aether.Danger`, and in the dynamic (Material You) palette
`Aether.Danger` is a hard-coded brand red: **the one control a user touches most was the one
control that ignored the wallpaper.**

Three things were impossible to state, let alone guarantee:

1. **Are the two halves of the split control distinguishable?** Nothing compared `stop` to
   `measure`. Material You's `primary` and `tertiary` are routinely two pastels a user cannot
   tell apart, and two discs under one thumb that resolve to the same pastel are two discs the
   user cannot tell apart.
2. **Is the glyph readable?** `marbleOnColor` existed, but a call site had to remember to use it
   — and the split control's pause bars did (`marbleOnColor(Aether.Danger)`) while its neighbour
   might not.
3. **Do two themes actually differ?** Not one of these questions could be answered by a test,
   because not one of the answers lived anywhere a test could reach.

## An action is a token

Each theme now owns four verbs:

| token | meaning | Daylight | Pure black | Wallpaper |
| --- | --- | --- | --- | --- |
| `connect` | armed — pressing it secures a tunnel | `#0066CC` electric | `#3399FF` bright | `scheme.primary` |
| `securing` | negotiating / measuring | `#001144` deep navy | `#0066CC` electric | `scheme.secondary` |
| `stop` | end a live tunnel | `#E23D5B` | `#FF718B` | `scheme.error` |
| `measure` | ping the server on screen | `#009A74` | `#55D7B4` | `scheme.tertiary` |

Three decisions worth stating:

- **`stop` and `measure` stay functional colours, never brand or decoration.** A VPN must not
  dress "disconnect" in the brand hue, and in the dynamic palette a "stop" borrowed from the
  wallpaper's ornament is a stop that stops looking like one.
- **`securing` is a whole step of the ramp away from `connect`, in both directions.** Light drops
  navy-ward, dark drops *below* the bright blue. The V205 defect was a busy state that held the
  armed state's value; two palette steps cannot collide by accident.
- **The ink pair is per theme and ordered by luminance.** `MarbleFloatActions.dynamic` sorts
  `onPrimary` / `onPrimaryContainer`, because Material You guarantees that pair is readable on a
  primary fill — not that `onPrimary` is the light one, which in a dark scheme it is not.

The whole set is built as **packed ARGB** (`MarbleFloatActionSet`) and converted to Compose
colours only at the palette's edge, so all three questions above are unit tests on a plain JVM
rather than promises in a comment.

## Coordination

`MarbleFloatChrome` carries the set, and `inkOn(tone)` is the single way any floating surface
gets a glyph colour: it scores the theme's two ink candidates against the tone it is handed and
takes the winner, pushing it the rest of the way if even the winner is short of the 3:1
graphical floor. The FAB, the pause disc, the measure disc and the header's route-ping pulse all
read the same object, so "coordinated" is a consequence of there being one source rather than a
goal to be chased.

## Checks

- `ui/MarbleFloatingActionsV210Test.kt` — the four verbs are four distinct colours in every
  theme; no role is shared between Daylight, Pure black and the wallpaper palette; every glyph
  clears 3:1 on its own disc in all four palettes; the dark theme's light stop hue keeps the dark
  ink while its bright connect hue keeps the white one (the V201 regression, re-stated); the
  dynamic palette orders its ink pair by luminance; and the wallpaper's own roles arrive
  untouched.
- `scripts/system-integrity-check.py` — the token objects exist, all three palettes build them,
  `Aether.FloatActions` exposes them, and the split control and the header pulse read
  `chrome.actions.*` with `chrome.inkOn(...)` for the glyph.
