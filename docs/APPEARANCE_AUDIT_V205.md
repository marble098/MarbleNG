# Appearance audit V205 — the light-theme defect sweep

A field report named three symptoms: the bottom tab bar's colour changes worked on
AMOLED but broke in the System theme, a pop-up box sat over the first page's header,
and the Home floating buttons looked wrong. The audit that followed found one root
defect behind the first symptom and a family of related ones across the pages.

## 1. The grey-fade defect (dock, Servers rows, Settings chips)

`animateColorAsState` interpolates **every** channel of a `Color`, and
`Color.Transparent` is black at alpha zero. Any tween whose off state was
`Color.Transparent` therefore dragged the fill's RGB toward black while the alpha
fell; the midpoint composited as a dull grey wash.

The defect is theme-asymmetric by physics: over the AMOLED bar (#090D14) the grey
midpoint is invisible, so the animation "works fine"; over the light System bar
(#EDF3FB) every tab change flashed a dirty grey box, so colour and animation read
as broken. Measured at the midpoint of the dock pill tween:

| bar        | grey-fade composite | clean alpha-fade |
|------------|---------------------|------------------|
| light ice  | (165, 174, 186) ✗   | (211, 228, 246)  |
| AMOLED     | (9, 18, 28) ≈ clean | (14, 28, 46)     |

**Fix:** the off state is now the *same colour at alpha zero* — the hue is held
and only alpha animates. Applied to the dock pill and indicator rim, the Servers
row state wash and the Settings choice chips. Rows of the Home server card and the
dock-slot kind tiles instead flattened their translucent selection wash onto the
resting fill (`compositeOver`), so their tweens run between two solid colours.

## 2. The dock's light presence and the dead Violet

Two more reasons the bar's colour appeared broken outside AMOLED:

- One fixed fill strength (.22) was tuned on the black bar, where a bright accent
  needs very little of itself. Over the ice bar the same wash disappeared into the
  surface — the selected tab looked unselected. Fill and rim strengths are now
  theme-aware (.36 / .60 light, .24 / .42 dark).
- The "Violet" dock accent resolved to `Aether.AmethystBright`, which in **both**
  brand palettes holds the exact value of `Aether.Cyan` — choosing Violet over
  Ocean changed literally nothing. The dock now carries a true violet
  (#7A5CD6 light / #B08CFF AMOLED).

`MarbleFloatingChromeTest` pins all three: the light pill must separate from the
bar by ≥ 1.12:1 for every accent, the AMOLED pill keeps its quieter tuning, and
the fade-off endpoint keeps its RGB.

## 3. The pop-up box over the Home header

The national-filtering banner was a TopCenter **overlay of the whole page**, which
painted a box straight over the wordmark and the header actions — the floating box
users saw on the first page. It now lives *in the page flow*: every Home theme
composes it directly under its header row. The same pass ended the translucent-film
skin (a 14 % danger wash with no surface, rim or shadow): the banner is a proper
card — opaque float surface, danger wash and hairline, palette-hued shadow, and
text pushed to 4.5:1 against the surface it sits on.

## 4. The Home floating buttons, redesigned

Critique of the V202 control: it was a doughnut, not a button — a 76 dp pale
chrome bezel around a 60 dp colour disc, wrapped in a third halo ring; and its
busy branch was dead code (`if (busy) AmethystBright else Cyan`, two tokens that
hold the same value in both palettes). The control is now one filled disc in the
semantic state colour (`connectButtonTone`): armed cyan, securing amethyst,
closing amber, like every other connect control; the glyph ink is measured with
`marbleOnColor`; the shadow is cast in the face's own hue. The connected split
pair follows the same grammar (solid danger/emerald discs), and the 88 dp reserved
footprint is untouched, so anchors never jump.

The same sweep made every floating Home widget opaque — the shortcut deck, the
selected-route card, the stream bar, the floating shutter and the floor dock all
paid their fills with `VoidElevated.copy(alpha = .74 … .97)` over the aurora, the
exact translucent-stack defect MARBLE_HOME_CLOUD_V141 banned for the cards.

## 5. Theme-blind details, corrected everywhere

- Header bare actions (+ / pulse / info): the dark theme's bright sky is only
  2.8:1 on the light page — under the 3:1 graphics floor. Their ink is now
  measured against the page (`marbleReadableOn`, 3.0).
- The slide-to-connect sheen was hard-coded white — invisible on the light
  theme's white track, so its invitation only existed in dark mode. It now paints
  with the state tone on light pages.
- Black ambient shadows under the slide thumb, the orbital core, the protocol
  tile and the menu sheet are replaced with hue-cast pairs (a black shadow on an
  ice surface reads as dirt — the V201 rule, now applied without exception).
