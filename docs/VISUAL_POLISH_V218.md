# MARBLE_VISUAL_POLISH_V218 — the corners close, the type ramp speaks, the disclosures move

One pass over the four things a real device kept complaining about: a subscription card whose
corners broke apart the moment it closed, page type that was simply too big, weights that were
not bound to sizes (and one weight no bundled face even ships), and settings disclosures that
opened on Compose's stock spring while the rest of the product moved on its own motion language.

## 1 — A sub closes through every corner, not across them

**The bug.** On the Servers page, collapsing a subscription ("بستن ساب") snapped its silhouette in
a single frame. The header clipped itself with `ServersCardShape` — 20 dp on all four corners —
while its outline kept drawing `serversStackedFrame` at the hierarchy's 16 dp, and the stack of
server rows below it was still fading out. For the whole collapse the clip said one corner, the
border said another, and the eye read the difference as the card breaking at its corners.

**The fix.** One animated fraction — `attach` — now owns the whole transition:

- the header's clip is built per frame: top corners pinned at `GROUP_CORNER_DP` (16 dp), bottom
  corners growing from 0 (stack attached) to 16 dp (card standing alone). The closed card now
  rounds at the same radius its outline has always drawn, so clip and border describe one corner
  at every frame;
- `serversStackedFrame` takes the fraction as `openBottomFraction`: the outline hides its bottom
  edge by over-extending its rounded rect two radii past the slice, and that over-extension eases
  to zero as the card closes, so the bottom border bends back into view instead of popping in;
- the header's own bottom padding (12 dp alone, 10 dp while carrying the stack) interpolates on
  the same fraction;
- the ripple bound (`kineticClickable`) rides the morphing shape, so a tap mid-transition clips
  to the corner the card is actually showing.

The arithmetic is pure and lives in `MarbleServersHierarchy.kt` — `openFraction`, `attachCornerDp`,
`frameBottomExtension` — pinned by `VisualPolishV218Test` next to the V189 size table, so the
clip and the frame can never drift onto two different corners again. The static
`ServersGroupHeadShape` is gone; there is nothing left to disagree with.

## 2 — The type ramp came down a step

Page type was oversized everywhere it mattered: a 28 sp page headline, 24 sp status titles, 20 sp
section and sheet headings, 18 sp list titles. Every step of `aetherTypography` dropped one notch
and tightened with it:

| role | before | after |
|---|---|---|
| displayLarge / Medium / Small | 40 / 34 / 30 sp | 34 / 30 / 26 sp |
| headlineLarge / Medium / Small | 28 / 24 / 20 sp | 24 / 21 / 18 sp |
| titleLarge / Medium / Small | 18 / 16 / 14 sp | 17 / 15.5 / 14 sp |
| bodyLarge | 16 sp | 15.5 sp |
| labelLarge | 14 sp | 13.5 sp |
| labelSmall (Latin) | 11.5 sp | 11 sp |

Persian keeps its own floors where the script needs them: `labelSmall` stays 12 sp and body text
stays Medium, because Vazirmatn's Regular is too light at small sizes.

## 3 — Every size tier owns one weight, and every weight is a real face

The ramp now reads as a ladder: Display is **Bold**, headlineLarge is **Bold**, the rest of the
headlines are **SemiBold**, titles step **SemiBold → Medium**, body keeps the Persian-Medium /
Latin-Normal split, labels are **Medium** under a **SemiBold** labelLarge. Larger text sits
heavier and tighter; smaller text steps down instead of shouting at the same weight as the
heading above it.

The five `FontWeight.Black` overrides (page headline, update dialog title and glyph, the Home
wordmark, the live-ping readout) are gone. Vazirmatn ships Regular, Medium, SemiBold and Bold —
a Black request made the platform synthesise a smeared faux-bold, which is exactly the "heavy,
dirty text" reading on device. Those sites now take their weight from the ramp or ask for Bold,
a face that exists.

## 4 — The disclosures move on the product's motion, not the framework's

Seven settings disclosures (core sniffing, both mux sections, smart notifications, the routing
expert editor, the speed-test section) opened on `AnimatedVisibility`'s stock spring — a
different curve from everything else the product animates. They now enter and exit on
`expressiveExpandVertically()` / `expressiveCollapseVertically()`, the same emphasized-decelerate
pair the Settings hub cards already used, and the update dialog's scroll hint cross-fades on the
entrance specs instead of expand-jumping. One motion language, start to finish.

`AnimatedVisibility(s.pingSpeedCustom)` keeps its stock form on purpose: the speed dial's
persistence invariant (V199) pins that exact call, and its default expand already reads fine.

## Verification

- `scripts/system-integrity-check.py` — 352 invariants, all green.
- `VisualPolishV218Test` — the corner-morph arithmetic: clamping, both endpoints, linearity, and
  the agreement of clip and frame at every end of the morph.
- `ServersHierarchyV189Test` — the V189 size table still holds (16 dp card corner, 12 dp list
  corner, 13 sp row name under a 16 sp group name).
