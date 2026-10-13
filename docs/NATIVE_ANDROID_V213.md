# MARBLE_NATIVE_ANDROID_V213 — the native chapter

*Core:* `app/src/main/java/com/marbleng/app/ui/MarbleNativeAndroid.kt` (`MarbleNativeSpec`,
`MarbleNativeCard`, `MarbleNativeListItem`, `MarbleNativeSwitch`, `MarbleNativeChoiceRow`,
`MarbleNativeSegmentedRow`, `MarbleNativeSectionHeader`, `MarbleNativeDivider`, `MarbleNativeChevron`)
*Tests:* `app/src/test/java/com/marbleng/app/ui/MarbleNativeAndroidV213Test.kt`
*Critique:* `docs/APPEARANCE_CRITIQUE_V213.md` — the seven-surface report this chapter answers.

## The report

> *"Criticise the app's appearance harshly, then change it: the boxes, the tabs, the servers, the
> flags, the settings, the options, the notifications. Bring all of it to the newest native Android."*

The critique (in `APPEARANCE_CRITIQUE_V213.md`) found the same defect seven times: **the product had
invented its own answer where Android already has one**. A colour that is not a platform role, a
size below a platform floor, a control with no platform anatomy, a notification field the platform
expects to be set and the app left empty.

This chapter does not add a design system — it *subtracts* one. Marble's own grammar is kept exactly
where it was the product's: the state colours (emerald live, cyan chosen, amber degraded, red
blocked), the measured data, the one-sentence copy contract, the kinetic press, the marbled motion
clock. What changes is the **material**: every surface, row, control, flag and notification now
reads its colour and its geometry from the platform's own roles.

## 1. The numbers moved into one table

`MarbleNativeSpec` owns the platform's measurements as plain integers, so a JVM test can pin them:

| Quantity | Value | Where it comes from |
|---|---|---|
| One-line list row | 56 dp | the Material list item |
| Two-line list row | 72 dp | ditto, with a supporting line |
| Dense row | 48 dp | the accessibility touch floor, as a *painted* height |
| Leading container | 40 dp (22 dp glyph) | the Android settings list |
| Corner ramp | 24 / 20 / 16 dp (card / tile / row) | the platform's corner steps |
| Divider | 1 dp, inset 16 dp (64 dp after a leading container) | the platform's list divider |
| Lift | 4 dp resting, 8 dp chosen, 2 dp contact | the platform's elevation steps |
| Indicator | 56 × 32 dp | the navigation bar's active indicator |
| Choice indicator | 20 dp ring, 10 dp dot | the platform's radio |

`AetherShapes` (the *stock Material* surfaces: sheets, menus, dialogs, text fields) moves to the
newest, rounder ramp — **14 / 18 / 24 / 30 / 36 dp** — so a bottom sheet and a Prism card are the
same roundness language for the first time.

## 2. Boxes: the card is a container, not a border

`marbleNativeCardFill()` reads the live scheme (`surfaceContainerLowest` light / `surfaceContainerLow`
dark), `marbleNativeOutline()` reads the neutral `outlineVariant` family, and
`marbleNativeShadow(tone)` builds the platform's **ambient + spot + contact** triple. Applied to:

- `PrismPanel` — the app's most-used box. Its rim is now *directional* (the top edge catches the
  surface's light and the stroke settles into the neutral outline by the bottom) instead of one
  accent/violet gradient wrapped around all four edges, and it wears both shadows.
- `prismElevated` — the same pair, so containers that cannot be a panel agree with the panels.
- `SettingsHubCard` — the group card, at the platform's 24 dp corner.
- `MarbleNativeCard` — the new component, used by the surfaces rebuilt below.
- `Modifier.marbleSurfaceDepth` (V212's contract) — resting lift 3 → **4 dp**, raised 7 → **9 dp**,
  plus the contact shadow, so the server grid's depth matches the panels'.
- The subscription frame and every server slice: `Aether.Glass` → the platform's container step,
  and the *accent* hairline (`GlassBorder`) → the neutral outline. A page of subscriptions is no
  longer a dozen brand-blue rectangles.

The sweep then ran over **every remaining box in the product**, because a chapter that leaves six
cards on the old recipe has not changed the material, it has changed one screen of it:

| Surface | Before | Now |
|---|---|---|
| The menu panel (`MarbleMenuPanel`) | 22 dp corner, 22 dp lift, card fill, accent hairline | the platform's 24 dp corner, container-high fill, neutral outline, ambient + spot + **contact** |
| The fourth tab's cards (`DockSlotCard`) | hand-mixed navy shadow, `tone @ .40f` rim | the native shadow triple, the native container, a rim that lights at the top |
| The probe strip (`ServersProbeStrip`) | 4 dp lift, `tone @ .22f` rim, 13 dp inset | the native pair + contact, neutral rim, 16 dp card inset |
| The subscription header card | the product's elevated token | the platform's container step (it lifts off the OLED floor now) |
| A slot's node row (`DockNodeRow`) | 15 dp corner, `GlassStrong`, accent hairline | the native row corner, the native container, the native tonal wash when chosen, a 48 dp painted floor |
| `InformationRow` / `InformationLinkRow` | `homeCloudInset*`, 13 dp corner, tone rail | the native row: container, outline, 16 dp corner, 48 / 72 dp anatomy |
| `NumberSetting` | `homeCloudInset*`, 13 dp corner, tone rail | the native row; the stepper's own buttons stay the product's |
| `SettingsHubSubSectionCard` | `homeCloudInset*`, 3 dp tone rail | the platform's *inset* step, the neutral outline, and a round tone-filled level marker where the rail was |
| The subscription card's corner (`ServersCardShape`) | 16 dp | 20 dp — the platform's tile corner, shared by the card, its slices and the frame around them |

## 3. Tabs: the active indicator is a stadium

The dock's selected tab is `marbleNativePill()` — a full round whose radius follows the box —
instead of a fixed 22 dp corner inside a 30 dp bar. The bar's *body and hairline* come from
`rememberMarbleFloatChrome()`, which now resolves the platform's container step and outline family;
the shadow stays the palette's own (V201's rule) and the four action tones are untouched. The
floating FAB, the menu sheet and every Home floating widget inherit the same body, because they were
always reading this one token set.

## 4. Servers: tiles and rows from one material

- `ServerTile` — the platform's tile corner (20 dp, was 13 dp), the platform's container fill, the
  neutral outline as its edge, emerald/cyan still spoken by the rim, and the platform's two-line
  row height. Every V210/V212 guarantee is untouched: the silent box still recedes, the name still
  travels inside one line, the depth contract is still the one modifier.
- The subscription list — container fill for the slice, container step for the nested block, the
  neutral outline for the frame and the divider, a 48 dp painted floor on the row block.

## 5. Flags: a chip, not a sticker

`CountryFlagCircle` gains an optional `ring`. When a ring is given the flag is inset by the same
1 dp, so the rim reads as a **frame** instead of a stroke laid over the flag's own edge — and the
unknown-location tile (the world glyph) sits in the *same* frame, so "measured country" and "nobody
knows" are one object in two states. The two standalone chips (the Home route chip and the dock's
flag tile) now pass `marbleNativeOutline()`. Inside `ProtocolTile` the flag's inset grows from 2 to
3 dp so the tile's own tone reads as a ring around the country instead of compositing with the rim
into one thick stroke.

## 6. Settings and options: the platform's list item, the platform's switch

- `SettingSwitch` — the option row *is* `MarbleNativeListItem` now: 56 dp (72 dp with a supporting
  line), a 16 dp corner, the platform's container fill and outline, `bodyLarge` title and
  `bodyMedium` supporting line, with `MarbleNativeSwitch` as the trailing control. Same params, same
  one-tap target, same 300 ms debounce, same spoken sentence.
- `SettingsHubSwitch`, `SettingsHubRow` — the same anatomy; the hub door keeps its *preview* and
  gains a drawn disclosure chevron instead of a typed glyph.
- `ServersMenuSwitch`, `ServersInsecureSwitch`, `HomeQuickSettingRow` — `MarbleNativeSwitch`, and
  the insecure toggle keeps its amber **word** instead of an amber control.
- `marbleSwitchColors()` — now the four platform roles (`primary` / `onPrimary` /
  `surfaceContainerHighest` / `outline`), so every remaining `Switch(` in the product is the switch
  Android Settings draws, in the user's own colours, in all four themes.
- `MarbleNativeChoiceRow` (a real radio row), `MarbleNativeSegmentedRow` (a real segmented control),
  `MarbleNativeValueRow`, `MarbleNativeSectionHeader` (sentence case, normal tracking, the primary
  tone — a heading that does not shout).
- `SettingsHubGroupCard` — the group card is the native card: the container step, the platform's
  24 dp corner, the neutral directional rim, the shadow pair with the contact shadow, and a 40 dp
  round leading container for the section's icon. Its heading stops shouting: no `uppercase()`, no
  1.4 sp tracking, `labelLarge` in the section's tone.
- `SettingsToneRail` — **deleted, not deprecated.** The 3 dp coloured bar on a settings row's
  leading edge was the product's own invention, no Android surface carries one, and it made every
  row 3 dp wider than the platform's anatomy. Its whole call-site set is on `MarbleNativeListItem`
  now; a helper with no call site is removed so the next settings row cannot find it.
- The chips and segments (`CyberChoiceChip`, `CyberSegment`) — a chip is a **stadium** on Android
  (was a 12 dp rounded box), its unselected edge is the platform's outline (was the glass hairline)
  and its selected wash is the platform's container strength; a two-line option tile takes the
  native row corner and the platform's container as its *resting* fill instead of a translucent
  `GlassStrong @ .30f` wash whose apparent colour depended on the page behind it.

## 7. Notifications: every platform field, filled

Built in `SmartNotifier`:

- `setColor(ACCENT)` — the brand's electric blue, the accent the system tints the small icon and the
  app name with, on both the private and the public version of every notification;
- `setSubText(...)` — the platform's own second line, carrying the **state**
  (`statusSubText("Connected • Frankfurt • 42 ms")` → `"Connected"`) or the alert's **kind**
  (`kindSubText`: Privacy / Recovery / Network / Subscription / Update / Test);
- `setGroup`, `setLocalOnly(true)`, `setBadgeIconType(BADGE_ICON_SMALL)`;
- per-kind colours on the alert path — `error` red for a privacy warning, green for recovery, blue
  for network, purple for updates;
- a real action icon (`ic_notification_stop`) instead of the app's status-bar mark standing in for
  the verb "Disconnect";
- the channel table configured the way Android's own per-channel screen reads it back: the ongoing
  tunnel channel is silent (`setSound(null, null)`, `enableVibration(false)`, no badge, private
  lock-screen visibility) — it *was* buzzing and dinging on every live-stats update — and the alert
  channel shows a badge.

Deliberately **not** done: Android 16's promoted-ongoing "live update" flag. It requires a
user-visible contract (a promoted notification is a promise that the app will keep updating it on a
schedule the system audits) and it could not be verified in this environment; the honest version of
"the newest notification" is the platform hierarchy above, not an unverified flag.

## 8. The platform's own back gesture

`AndroidManifest` sets `android:enableOnBackInvokedCallback="true"` — without it the platform never
offers the app the new back callback, so V212's predictive-back *progress* had nothing to animate
and the gesture snapped at commit. The activity also declares `windowSoftInputMode="adjustResize"`,
because every surface is edge-to-edge and reserves the keyboard with `imePadding()`: panning the
window instead slides the app's own header off the screen while the user types.

## What this chapter deliberately does not do

- **No new dependency.** The libraries were already the newest there are (Compose BOM
  `2026.09.00`); "native" here means the platform's roles and geometry, not a library swap.
- **No second design system.** `MarbleNativeAndroid.kt` owns no palette: every colour it paints is
  `MaterialTheme.colorScheme`, which `AetherTheme` rebuilds for the brand palettes, for AMOLED and
  for a wallpaper palette in one place.
- **No structural change.** No page was re-laid out, no data path touched, no state moved. Every
  V210/V211/V212 invariant — the collapsed-keys policy, the server-box truth rules, the one ping
  verb per surface, the depth contract — is still there, and the source checks still pin them.

## Checks

- `MarbleNativeAndroidV213Test` pins the spec (row heights, touch floor, corners, divider insets,
  shadow triple, the four platform colour roles the helpers read) and the wiring in the surfaces
  this chapter rebuilt.
- `scripts/system-integrity-check.py` gains the V213 block: the chapter's marker, the spec table,
  the platform-role helpers, the notification fields, the manifest flag and the doc's presence in
  the README. Two older checks (V189's nested row, V190's slice) now accept the *platform's*
  container step as well as the product's glass token, because both were pinning "the row is one
  step away from the card's own surface" and that property is unchanged — only the role that
  answers it moved.
- The V185/V207/V208/V210/V212 invariants are untouched, deliberately: the state grammar, the copy
  contract, the server-box truth rules and the predictive-back count are all still enforced.
