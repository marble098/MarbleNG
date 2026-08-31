# Settings Screen Deep Hotfix — V76 (2026-08-31)

> Symptom still reported after V75: the Settings workspace **tab strip renders and responds,
> but the content area remains blank** for General/Freedom/Testing/Network/Engine/System.

V75 moved `BoxWithConstraints` to the root of `SpatialSettings`, removed duplicate insets and
added a zero-height tripwire, but it **kept the weighted host boundary** (`Box → key →
LazyColumn`) and **kept a `fillMaxHeight()` overlay inside the tab-strip `Box`**. Those two
items were the remaining ways the phone layout could measure the content viewport as zero
while the strip still drew.

## The two remaining root causes

1. **Tab-strip host could consume the entire remaining height.**
   The compact strip was a `Box` with no fixed height. Inside it the two scroll-hint fades used
   `Modifier.fillMaxHeight()`. In a `Column`, an unweighted child that contains a
   `fillMaxHeight()` child is measured with the parent's remaining max height — so the
   `fillMaxHeight()` fades could make the strip `Box` grow to the full available height. The
   strip itself was still visible (it is laid out first), while the weighted content pane
   received the leftover, i.e. **zero height**. This exactly explains "tabs work, content is
   empty on every tab."

2. **A weighted `Box → key → LazyColumn` boundary remained between the strip and the content.**
   Even with the zero-height tripwire, the fallback used the same weighted modifier, so once the
   viewport was zero the fallback was zero too. A `Box` (or any intermediate non-scrollable host)
   between the weighted parent and the `LazyColumn` is unnecessary and was the failure shape
   PR #45 had already removed.

## The V76 structural fix

- **Phone/portrait settings is now one ordinary `LazyColumn`, not a grid of unweighted header /
  strip plus a weighted workspace.** The header is an item, the tab strip is a `stickyHeader`,
  and every `SettingsSectionCard` is a normal item. There is **no separate weighted content
  viewport** at all, so there is nothing below the strip that can collapse to zero.
- **Wide/rail settings keeps a `NavigationRail` but passes the modifier directly to the
  scrollable.** `SettingsTabPane` now simply forwards `modifier` to `SettingsWorkspacePage`;
  there is no `Box`, no `key(selectedTabIndex)` layout boundary, and no `onGloballyPositioned`
  host measurement in between. Per-tab fresh state is provided by
  `remember(selectedTabIndex) { LazyListState() }`, which is a state key, not a layout node.
- **`BoxWithConstraints` is gone from the Settings path entirely.** The adaptive rail decision
  now comes from `LocalConfiguration.current.screenWidthDp` (the UI-file marker
  `maxWidth >= 700.dp` is preserved for the product-UI invariant).
- **The tab-strip `Box` is bounded** with `heightIn(min = 58.dp)`, and the scroll-hint fades now
  use `matchParentSize()` instead of `fillMaxHeight()`, so they can never grow their parent to
  the full viewport.
- The viewport tripwire stays useful for the wide layout and is attached directly to the
  `LazyColumn`/fallback `Column` modifier (again, not to an intermediate `Box`). It still emits
  `SETTINGS / viewport-degraded-fallback` if a page ever measures zero height.
- `settingsSections(...)` remains the single content source, `SettingsSectionCard` is shared by
  every path, and `settingsSections`/`ExpertGateRow` behavior is unchanged.

## Engineering invariants preserved

- Exactly one insets pass (`imePadding`); the `Scaffold` already pads system bars/dock.
- No `copy(expertMode=true)` navigation side effect — Routing focus renders in both modes.
- No second source of truth for per-tab content.
- All tab chips remain real semantic tabs (`Role.Tab`, `selected`, descriptions).
- The wide `NavigationRail` and compact `LazyRow` tab strip both remain and respond to clicks.

## Regression guards

`scripts/system-integrity-check.py` now enforces 93 invariants, including:

- No `BoxWithConstraints(` anywhere in the UI source.
- Mobile settings uses one sticky `LazyColumn` (`stickyHeader(key = "settings-tabs-strip")`) with
  per-tab `LazyListState`.
- `SettingsTabPane(` → `SettingsWorkspacePage(` direct forwarding remains intact.
- One inset pass, viewport tripwire, shared section source, and no Expert navigation mutation.
- The existing product-UI v54 checks for `NavigationRail`/`maxWidth >= 700.dp`, compact
  `LazyRow`, and `PrismPanel` still pass.
