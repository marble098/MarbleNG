# V111 — Immersive Home Styles, Real Vazirmatn, Full Persian UI, Smart Tunnel Ping

## What changed

### 1. Four fully immersive Home connection styles (`MarbleHomeStyles.kt`)
Every style (BIOLUMINESCENT, COSMIC_ORBIT, COSMIC_IMMERSION, PARAMETRIC) now:

- owns the **entire viewport**: a full-bleed Canvas backdrop (abyssal organism / command deck /
  nebula HUD / architect's blueprint) driven by the shared Marble frame clock — no per-card
  infinite transitions, no bitmaps;
- renders the same evidence snapshot (`HomeEvidence`) through a **style-specific skin**
  (`HomeFlavor`): node name, source/subscription name, IP + country flag, the three IP actions
  (copy / refresh / details), live session uptime, and the one-shot connection ping — each element
  is drawn differently per style (organic leaf chips, deck readout rows, nebula stat rings,
  numbered blueprint spec rows);
- keeps working controls: copy puts the IP on the clipboard, refresh forces a server-intel
  reload, details opens the IP dialog, and the ping element re-measures on every tap;
- scales its layout from the real available height so tall screens are filled instead of leaving
  dead space.

### 2. Real tunnel ping (`AppRepository.measureConnectionPing`, MARBLE_SMART_TUNNEL_PING_V111)
The one-shot ping now races three independent HTTP-204 endpoints
(gstatic / cloudflare / google) **through the active proxy** and reports the minimum, with an 8s
ceiling — a much more honest measure of intelligent tunnel latency, and it re-runs on every tap.

### 3. Real Vazirmatn font (MARBLE_VAZIR_REAL_FONT_V111)
`res/font/vazirmatn_{regular,medium,semibold,bold}.ttf` are the official Vazirmatn releases
(SIL Open Font License 1.1, © rastikerdar — https://github.com/rastikerdar/vazirmatn).
`aetherTypography` is now Persian-aware: when the app language is Persian every text style uses
`VazirFamily` and letter-spacing is neutralised (Latin tracking breaks Arabic-script shaping).

### 4. Full Persian coverage (MARBLE_BILINGUAL_V111, `MarblePersianLexicon.kt`)
A render-time translation layer `trx()` is wired into every shared text-rendering component
(PrismButton, PrismSelectionTile, PrismBadge, MarbleMetricCard, PrismThemeChoice,
PrismSearchField, SpatialHeader, SectionLabel, SettingsSectionCard, SettingSwitch,
NumberSetting, chips, segments, fields, metric tiles, tab strips, permission onboarding …) plus
~65 direct literals. Lookup order: exact → trimmed → lowercase → regex patterns → English
passthrough. Brand/protocol terms (MarbleNG, DoH, TUN, REALITY, IPv6…) intentionally stay Latin.
RTL layout continues to come from `ProvideMarbleLanguage` (`LayoutDirection.Rtl`).

## Invariants
`scripts/system-integrity-check.py`: 115/115 pass. All CI Prism invariants preserved byte-exact.
