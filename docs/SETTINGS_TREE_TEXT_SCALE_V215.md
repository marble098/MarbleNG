# MARBLE_SETTINGS_TREE_V215 — the regrouped Settings, text size, and a steadier Home

This chapter answers five requests about the Home page and Settings. Each one is a decision
recorded here so a later change can see what it is reversing.

## 1. Home floating controls and the ping readings

**The black centre.** The floating disc's face was the action token itself, and the glyph ink was
scored against that token. On a light token (the connect cyan, the measure hue in Daylight) the
palette's navy ink won, so the glyph read as a dark hole in the middle of a bright disc.

Decision: the face is now a *deep shade of the token* (`marbleDeepFaceArgb`, MarbleContrast.kt),
pulled toward navy until a white glyph clears 4.5:1 on it. The bright token stays the ring and the
shadow, so each control still says what it is. The palette tokens and the navy ink pair in
`MarbleFloatActions` are unchanged; `MarbleFloatingActionsV210Test` still pins them.

**The colour that changed constantly.** `homePingTone` coloured a provisional reading by the
latency band it fell in. A run is several samples, each in a different band, so both ping icons
flipped colour on every sample. Decision: the band applies only once a run is MEASURED. While a run
is open, the reading wears the measuring hue (`homePingToneKind`, pinned by a JVM test).

**Speed of the first value.** The first sample was already published as it landed, but samples under
20 ms were held back until a later one arrived. The provisional floor is now 1 ms
(`PROVISIONAL_PING_FLOOR_MS`). Nothing else changed: sample counts, timeouts, medians, the 20 ms
verdict floor and the pacing dial are the same. A provisional value is still marked with `~`.

## 2. Text size for the whole app

A new setting, `textScalePercent`, with four steps: 85, 100, 115 and 130 percent (`TextScale`).
`ProvideMarbleTextScale` multiplies the system font scale on the `LocalDensity` under the whole app,
so every `sp` grows together and every `dp` geometry is untouched. The control is four chips in
Settings › Appearance › Text & language. Stored values outside the ladder are clamped on read.

## 3. Settings organised into six sections

The six main sections stay. Each sub-section now opens the card it names, instead of routing
through catch-all workspaces:

| Section | Sub-sections |
|---|---|
| Home & display | Home style · Home widgets · Home layout · Ping controls |
| Connection | Engine & tunnel · Tunnel & proxy (Connection card) · Network & routing · Automatic connect |
| Measurement & servers | Ping & ranking · Server library (Server cards) |
| Data & sources | Backup · Sources (Subscriptions card) |
| Appearance | Theme · Navigation (Fourth tab, Navigation bar) · Text & language (text size, typeface, language) |
| System | Alerts · About |

A card that opened from a hub row is its own page (`SettingsCardPage`). The page shows the same
composable the workspace shows, so no setting is duplicated. The GENERAL workspace is no longer
reachable from the hub, but the cards it held are addressed through it.

## 4. Deprecated options removed

Removed from `AppSettings`, from `AppStore` (read and write), and from the pins:

- `expertMode` and its read-out row. It hid nothing since V118, so the switch was a dead control.
- `rememberLast`, `homeShowSummaryMetrics`, `homeShowIranMode`, `homeShowQuickActions`,
  `homeShowServerSelector`, `homeShowRouteDetails`, `homeShowRouteRibbon`, `iranModeNotify`.
  Each had zero reads outside `Models.kt` and `AppStore.kt`, and no Settings row wrote them.

Kept on purpose, because they still change behaviour: `configCompatibilityMode` (read by
`XrayConfigHardener`), `homeShowLiveQuality` (read by the Atelier Home), and the core option escape
hatches. Old preference keys left in a device's file are simply never read again.

## 5. A Home header that does not grow

The national-filtering notice was a full-width banner in the flow under the header, so the top of
the Atelier page grew whenever it was on. The notice is now a line of the decision card, in the
same row that already carries a route's problem and its retry. The header keeps its height in every
state. The connection state is still the decision card's sentence, not a header readout, which keeps
the V156 rule that the header never repeats the session state.

## Verification

Local gates: `scripts/system-integrity-check.py` (350 checks, pinned in the V215 block),
`tools/compose-scope-check.py`, `tools/kotlin-structure-check.py`, and the Prism and routing
invariant blocks of `verify.yml`. JVM tests: `TextScaleV215Test`, `MarbleFloatingFaceV215Test`,
`HomePingToneKindV215Test`. Compilation and the Gradle suite run in the PR's CI.
