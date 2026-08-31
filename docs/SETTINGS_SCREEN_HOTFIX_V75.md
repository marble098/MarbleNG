# Settings Screen Total Hotfix — V75 (2026-08-31)

> **Superseded by [SETTINGS_SCREEN_HOTFIX_V76.md](SETTINGS_SCREEN_HOTFIX_V76.md).** V75
> removed duplicate insets and added the viewport tripwire but still kept the tab-strip
> `fillMaxHeight()` fades and a weighted `Box → key → LazyColumn` content host, which are the
> two remaining zero-viewport paths fixed in V76.

> Symptom reported on device: the Settings workspace **tab strip renders and responds to
> clicks, but the content area below it stays empty for every tab** (General, Freedom,
> Testing, Network, Engine, System).

The settings workspace in `app/…/ui/Aether2026.kt` (`SpatialSettings`) was rebuilt
defensively so that every historical class of "tabs visible, content invisible" failure is
either eliminated or detected at runtime. The full analysis is below.

## Root-cause candidates (all addressed)

1. **Duplicate system-bar insets (most likely real-device cause).**
   The workspace applied `.systemBarsPadding()` **plus** `.windowInsetsPadding(WindowInsets.navigationBars)`
   on top of the `Scaffold` inner padding (which already contains the status bar, the
   floating dock and the navigation bar). On edge-to-edge phones with large display/font
   scale the duplicated padding could consume the entire remaining viewport, leaving the
   weighted workspace host at zero height — the tab strip (an unweighted child) kept
   rendering, exactly matching the reported symptom.
   → Exactly **one** insets pass now remains: `imePadding()`.

2. **Fragile vertical measurement chain.**
   The content `LazyColumn` was reached through `BoxWithConstraints (weight) → Column
   (weight) → host → LazyColumn`. `BoxWithConstraints` is a `SubcomposeLayout`; placing it
   in the weighted path was implicated by previous fixes in zero-height hosts on affected
   builds. `BoxWithConstraints` now sits **only at the root** (to pick the wide-screen
   rail) and the workspace host is a **plain weighted `Box`** whose child page fills it
   with `fillMaxSize()`.

3. **Cross-tab lazy-state reuse.**
   Switching tabs reused one `LazyColumn` instance with different item counts; a scroll
   offset from a long tab could be applied to a one-card tab and lazy state leaked between
   workspaces. The visible page is now composed under `key(selectedTabIndex)` — every tab
   owns a fresh list measured for its own content and always opens scrolled to the top.

4. **Stale saved tab index.**
   `selectedTabIndex` comes from `rememberSaveable`; an index saved by an older build can
   point past `tabs.lastIndex` after enum changes. The index is now coerced at every use
   site and self-heals once on composition.

5. **Silent Expert-mode mutation.**
   The Home → Routing flow force-enabled Expert mode (`updateSettings(copy(expertMode=true))`)
   as a side effect. The focused Routing card now renders in **both** modes and no user
   setting is ever mutated by navigation.

6. **Zero-height viewport with no fallback.**
   Even after 1–5, a hypothetical collapsed viewport would silently blank every option.
   A **viewport tripwire** measures the host on every layout pass; if it measures zero
   height twice in a row, the page swaps to a **non-lazy scrollable `Column`** (which
   renders every section eagerly) and emits a
   `SETTINGS / viewport-degraded-fallback` diagnostics event via
   `AppRepository.diagnosticsEvent(...)`, so the failure is observable instead of silent.

7. **Out-of-range stored numeric values.**
   `NumberSetting` (Local SOCKS port, refresh cadence, ping samples, MTU floor …) renders
   and steps the stored value directly. Stale/corrupted persisted values are now clamped to
   the legal range for both display and ± stepping.

## Content & option guarantees (UI/UX)

- Tab content is produced by a single `settingsSections(tab, repo, expertMode, focusSection)`
  builder (a list of `SettingsSectionSpec`), shared verbatim by the lazy page **and** the
  zero-viewport fallback — the two render paths can never drift apart.
- Every tab renders at least one real card in **both** expert and standard modes:
  General (3 cards), Freedom (1), Testing (1 + gate/Intelligence), Network
  (Split tunneling + gate/full set), Engine (gate or Fragmentation & Mux),
  System (2). Non-expert tabs now show an **"Expert workspace" card with an inline switch**
  (`ExpertGateRow`) instead of a dead "Expert mode off" hint.
- The Routing card from the Home quick action is placed first in the Network workspace when
  focused, in both expert and standard modes.
- Tab chips are real tabs for accessibility: `Role.Tab`, `selected` semantics,
  `contentDescription` and `stateDescription`; the wide rail and compact strip now carry a
  per-tab accent tone (Cyan / Amethyst / Emerald / Amber), and the top bar subtitle shows
  the active workspace name.

## Regression guards

`scripts/system-integrity-check.py` (90/90 passing) gained three checks:

- Settings workspace applies exactly one inset pass (no `systemBarsPadding()` /
  `windowInsetsPadding(WindowInsets.navigationBars)` in the UI file).
- Viewport tripwire and shared section source exist (`viewport-degraded-fallback`,
  `SettingsSectionSpec`, `ExpertGateRow(`).
- Routing focus never mutates Expert mode (`copy(expertMode=true)` absent).

All prior Settings invariants (`key(selectedTabIndex)`, `SettingsTabPane(`, `SettingsWorkspacePage(`,
`MARBLE_SETTINGS_CONTENT_VIEWPORT_HARDENING_V72`, compact `LazyRow` strip, adaptive
`NavigationRail`, DNS Compose boundary) remain enforced and passing.
