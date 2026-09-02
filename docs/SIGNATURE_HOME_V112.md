# Signature Home — V112

MARBLE_SIGNATURE_HOME_V112 (plus MARBLE_SEAMLESS_LOOPS_V112, MARBLE_HOME_PING_LADDER_V112,
MARBLE_HOME_PING_AUTOFIT_V112, MARBLE_NIGHT_OUTLINES_V112) delivers five user-facing upgrades on
the Home connection surface:

1. The new **Signature** connection style — the new product default — a static, professional,
   fully customizable presentation.
2. A real ping for all five styles (the old probe reported "no response" while the tunnel was
   healthy).
3. Seamless, invisible-loop animations in every style.
4. A **System font** option next to Vazir, and Vazir always forced for Persian.
5. User-adjustable dark-theme frame outlines (subtle / bold / colored / hidden).

---

## 1. The Signature studio (`HomeStyle.PRO`)

New in `Models.kt`:

- `HomeStyle.PRO("pro")` — **default** (`homeStyle` in `AppSettings` now resolves to `PRO`).
- `ProServerCardStyle` — `GLASS` / `ACCENT` / `PLAIN` server-card backgrounds.
- `ProAccent` — `ELECTRIC` (#3399FF) / `EMERALD` (#2ED3A7) / `AMETHYST` (#9D8CFF) / `AMBER`
  (#F2B45F) / `CYAN` (#9BE8FF).
- `ProBannerScope` — `HOME` / `ALL`.
- `ProShortcut` — `LIBRARY` / `RANK` / `PRIVACY` / `ROUTING` / `TESTS`.
- `DarkOutlineStyle` — `SUBTLE` / `BOLD` / `COLORED` / `HIDDEN`.

Every layer is an independent switch persisted in `AppSettings` and wired through
`AppStore`/`AppRepository`:

| Setting | Meaning |
| --- | --- |
| `proFloatingButtonEnabled` | The draggable floating connect button (v2rayNG-style shutter). |
| `proStatusBannerEnabled` | Slim status banner (state + selected server + live ping). |
| `proBannerScope` | Banner on Home only, or riding on top of every page. |
| `proCornerActionsEnabled` | Top-right corner cluster: **+** add server, **zap** ping grab, user-selected shortcut, **⋮** overflow menu. |
| `proServerRailEnabled` | Home rail with the servers chosen in the Library source selector. |
| `proStyleSwitcherEnabled` | Bottom-of-page style chips — change connection style without leaving Home. |
| `proServerCardStyle` | Glass / accent-colored / plain server-card backgrounds. |
| `proAccent` | Accent driving all Signature surfaces and animations. |
| `proShortcut` | Which quick action the corner shortcut button runs. |
| `darkOutlineStyle` | Dark-theme frame personality (see §5). |
| `proFabPosition` | Persisted normalized FAB position (survives restarts). |

New UI in `ui/MarbleSignatureHome.kt`:

- `HomeStyleSignature` — the Home page content: identity rows with a breathing accent spine,
  IP capsule, stat grid, signature Connect ring (three concentric aurora arcs + quarter ticks).
- `SignatureStatusBanner` — strip-style banner (connects state, server, live ping, uptime).
- `SignatureCornerCluster` — corner buttons and the **⋮** dropdown (copy IP / refresh IP /
  IP details / library).
- `SignatureServerRail` — horizontal rail of the selected servers; tap to connect.
- `SignatureStyleSwitcher` — v2rayNG-like floating style chips.
- `SignatureFloatingConnectOverlay` — draggable connect button, app-wide; drag position
  persisted as normalized fractions; ring animations (breathing halo / sweeping arc while
  connecting / rotating dash constellation when connected).
- `rememberDeckEvidence` — the single shared truth for Home, banner and FAB.
- `SignatureCornerButton`, `signatureAccentColor`, `signatureStatusTone`.

The four classic styles keep their identity: they simply receive an extra `HomeProContext`
(default `null`) they ignore, and a `pro` parameter on `HomeStyleSurface` that switches the
whole surface to the Signature layout when set.

## 2. Real ping (`MARBLE_HOME_PING_LADDER_V112`)

`AppRepository.measureConnectionPing()` was a single-mode probe: one failed handshake produced
"no response" even while the tunnel was perfectly healthy. It is now a racing ladder:

- Four literal-IP/TLS-hostname probes (`1.1.1.1`→cloudflare-dns.com, `8.8.8.8`→dns.google,
  `9.9.9.9`→dns.quad9.net, `1.0.0.1`→cloudflare-dns.com) measuring **first-byte HTTPS latency**.
- Three domain probes (`www.gstatic.com`, `cp.cloudflare.com`, `connectivitycheck.gstatic.com`)
  measuring best-of-2 **tunnel RTT**.
- One full-request opinion (`GET /generate_204`) where any 2xx/3xx proves the tunnel end-to-end.
- All probes run in parallel (bounded pool, 9s hard cap); the **minimum healthy sample wins**.
- Results are invalidated if the session changed while probing; a concurrent-tap guard
  (`connectionPingInFlight`) deduplicates rapid tapping.
- `CyberDeck` adds a one-shot rescue: ~1.8 s after a connect that lands in IDLE it measures once,
  and ~2.2 s later retries exactly once if the reading came back FAILED. No repeating timers.

Diagnostics: every measurement logs a `home-connection-ping` event with the winner mode and all
sample modes/latencies.

## 3. Seamless loops (`MARBLE_SEAMLESS_LOOPS_V112`)

Every wrap-around effect (ripples, travelling pulses, drifting motes, scan lines) is multiplied
by `loopFade(t) = sin(pi * t)` — the element is fully transparent exactly when it teleports back,
so the restart frame is pixel-identical to the pre-start frame. Full-circle rotations
(`motion.loop(ms) * 360f`) are inherently seamless. Applied across Organic, Orbit, Nebula,
Blueprint and Signature.

## 4. Fonts (`AppFont`)

- `AppFont.SYSTEM("system")` joins `VAZIR` — `FontFamily.Default` end-to-end (home styles,
  banners, FAB, dock).
- Persian **always** renders in Vazir regardless of the app font choice — enforced centrally in
  `AetherTheme`/`Tr` typography resolution.

## 5. Night outlines (`MARBLE_NIGHT_OUTLINES_V112`)

`darkOutlineStyle` reshapes every dark-theme frame hairline:

- `SUBTLE` — the existing quiet hairline (default).
- `BOLD` — stronger, more present borders.
- `COLORED` — brand-tinted borders.
- `HIDDEN` — borders removed entirely.

Resolved once per theme composition (`applyNightOutline`); light theme is untouched.

## 6. Ping auto-fit (`MARBLE_HOME_PING_AUTOFIT_V112`)

`HomeStatValueText` renders every stat readout with length-tiered sizing (full size ≤ 7 glyphs,
86% ≤ 10, 70% beyond), `maxLines = 1`, `softWrap = false` and `TextOverflow.Ellipsis`. Long
values — English "no response", Persian "بدون پاسخ", "measuring…", "1234 ms" — can no longer
overflow their box in any of the five styles.

## Files touched

| File | Change |
| --- | --- |
| `model/Models.kt` | New enums, `AppSettings` fields (PRO default, studio, outlines). |
| `data/AppStore.kt` | Persistence for every new setting + `proFabPosition`. |
| `AppRepository.kt` | Ping ladder, FAB position persistence. |
| `ui/AetherTheme.kt` | System font, Persian-forced Vazir, night outlines. |
| `ui/MarbleHomeStyles.kt` | PRO flavor branches, `loopFade`, `HomeStatValueText`, new glyphs. |
| `ui/MarbleSignatureHome.kt` | New file — the whole Signature studio. |
| `ui/Aether2026.kt` | Shared deck evidence + actions, banner/FAB overlays, studio Settings. |
| `ui/MarbleApp.kt` | Outline style wiring. |
| `ui/MarbleStrings.kt`, `ui/MarblePersianLexicon.kt` | EN/FA strings. |
| `ui/MarbleHomeStyleTest.kt` | Unit coverage: enums, fallbacks, locale resolution, accents. |
| `scripts/system-integrity-check.py` | 126 structural checks (11 new for V112). |
