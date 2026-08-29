# MarbleNG Design System v53

## Architecture

The audited MarbleNG repository is Xray-based. This redesign changes the design system and
Jetpack Compose product UI only. It intentionally preserves Xray, HEV, routing, DNS, Identity
Guard, benchmark policy, Quick Tile, privacy/leak protection and recovery behavior.

## Material 3 implementation

Stable Material 3 components are used directly:
- Material You Dynamic Color for System theme on Android 12+
- TopAppBar
- NavigationBar / NavigationRail
- ElevatedCard and CardDefaults elevation
- FilterChip
- FilledTonalButton
- SingleChoiceSegmentedButtonRow / SegmentedButton
- Switch
- ModalBottomSheet
- SwipeToDismissBox

This follows Material 3 Expressive principles without requiring an alpha-only theme API.

## Semantic state palette

State color has meaning:
- Emerald: connected / verified healthy
- Slate: disconnected / neutral / unknown
- Violet: connecting / transitional
- Amber: degraded / warning
- Red: blocked / failed / destructive

System Dynamic Color may recolor generic Material surfaces and primary controls, but Marble's
security/health state colors remain deterministic so wallpaper color cannot make a critical state
look healthy.

## Metric bands

Ping:
- < 100 ms: good / green
- 100..250 ms: warning / amber
- > 250 ms: poor / red
- unknown: neutral

Jitter:
- < 20 ms: good
- 20..50 ms: warning
- > 50 ms: poor
- no verified sample: neutral

Quality:
- >= 80: good
- 60..79: warning
- < 60: poor
- unknown: neutral

The boundary cases are unit-tested in MarbleDesignSystemTest.

## Spacing

Primary surfaces use an 8dp grid:
- micro optical step: 4dp
- S: 8dp
- M: 16dp
- L: 24dp
- XL: 32dp

## Home

- compact TopAppBar instead of a repeated oversized page title
- animated radial connection/quality ring
- anchored Home status block: the runtime title/sentence reserve their own height, so a shorter
  sentence never pulls the Connect control upward and a longer one never pushes it down
- state color concentrated in the ring/control
- touchable selected-server surface with avatar and chevron
- explicit Route details row
- asymmetric Ping/Jitter/Quality bento composition
- recent verified-ping sparkline kept in-memory in the UI
- real quick switches for Full TUN, IPv6, Adaptive MTU and kill-switch auto-recovery
- fail-closed kill-switch Armed state remains visible and is never represented as a fake disable

## Library

- compact TopAppBar
- sources/filter/sort move to ModalBottomSheet
- Refresh/Ping/Rank remain distinct filled-tonal actions
- server avatar uses a real hostname country-code TLD only when available; otherwise protocol initial
- compact semantic ping badge
- connected node receives a semantic halo/outline painted **on top of** the row: emerald ring, inner
  bloom, left energy rail with a travelling pulse, a state flood under the content and a verified badge
  on the avatar. Nothing in that emphasis is measured, so a connected row keeps the exact box of a
  disconnected one and the list never reflows. While the handshake for that row is still running the
  same frame shows in violet, the transitional state of the palette.
- swipe right: edit
- swipe left: deletion confirmation
- overflow menu remains available for advanced operations

## Settings

Compact:
- horizontally scrollable tabs
- edge fades communicate hidden tabs
- horizontal swipe remains enabled

Wide:
- NavigationRail + workspace content

Theme selection uses SingleChoiceSegmentedButtonRow. The old hand-built MarbleToggle is removed;
official Material 3 Switch is required by the updated CI invariant.

## Dark mode and validation

Light, Dark and System/Dynamic paths are compiled by the existing Source verification workflow.
That workflow already runs unit tests plus Debug and Release Kotlin compilation before promotion.
