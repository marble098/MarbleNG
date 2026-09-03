# Servers, clean Home and five connection button models — V117

`MARBLE_SERVERS_MENU_V117`, `MARBLE_SERVERS_STYLE_CARDS_V117`,
`MARBLE_CONNECT_BUTTONS_V117`, `MARBLE_DYNAMIC_COLOR_V117`,
`MARBLE_PING_FLOOR_V117`, `MARBLE_CLEAN_HOME_V117`

This round is presentation + one measurement discipline. It adds no permission, no network call
and no background worker.

## 1. The iOS surface is gone

- `MarbleIOSDesign.kt` was deleted along with every `MarbleIOS*`, `MARBLE_IOS_*` and `iOS`
  comment across the app sources.
- The Servers action drawer was replaced by `MarbleMenuPanel` / `MarbleMenuPanelItem`, a small
  panel written in the app's own Prism material language and anchored top-right under the page
  header. It is no longer a page-centred sheet.
- The header hamburger button uses the same primary positive `CyberButton` treatment as the
  primary action at the top of the Servers page, with a new vector `HomeIcon.MENU`.

## 2. Servers boxes

- The word "Delete" is removed from the visible server box and the standalone trash button is
  removed from the row. Delete now lives as one quiet, confirmed item inside the per-server
  overflow sheet.
- The Signature row is slightly taller and more airy; the shared source-module frost carries a
  stronger accent/amethyst colour wash so the boxes read as designed, not grey slabs.
- The source module design (one container per subscription, grouped rows, fold animation, meta
  chips, per-style edges) is kept and polished in Marble's own language.

## 3. Clean first-entry Home

- Fresh installs now start with Signature studio extras off by default: no floating button, no
  status banner, no corner action cluster, no server rail and no bottom style switcher. The Home
  is a clean connection surface (button + status + IP + uptime + one-shot ping), with those
  layers still available from Settings → Appearance → Signature studio.

## 4. Five connection button models

`HomePowerControl` remains the shared contract and now delegates to `MarbleConnectionButton`,
which implements five distinct models:

| Flavor | Model |
| --- | --- |
| Signature / Pro | `FLOAT` — floating orb with a rotating aurora |
| Bioluminescent | `PULSE` — breathing membrane with expanding ripple |
| Cosmic Orbit | `ORBIT` — instrument dial with orbiting arcs and ticks |
| Cosmic Immersion | `CORE` — clean centred circular instrument |
| Parametric | `SHIELD` — shield-shaped action with a drafting frame |

Each model runs the same three-stage motion: idle breathing (or float), an indeterminate
connecting sweep, a settled connected glow, plus the fail-closed blocked reset state.

## 5. Stronger dynamic colour

`homeTone` now follows the live measurement instead of one fixed green: fast = cyan/emerald,
fair = amber, slow = coral; connecting/blocked keep their state signals. The Shared Home surface
and floating button both read the same evidence.

## 6. Ping readout no longer flashes an unrepresentative number

`measureConnectionPing()` no longer publishes the stored/live seed (often an unconvincing 15 ms)
as a finished `MEASURED` result, and no longer publishes the first verified probe immediately.
The Home readout stays in `MEASURING` until the median of the verified probe race is available,
then refines with the race winner. The seed remains only as the last-resort ladder tail.

## Verification

- `python3 scripts/system-integrity-check.py` → 126/126.
- `python3 tools/kotlin-structure-check.py` on every changed `.kt` → OK.
- `python3 tools/compose-scope-check.py` on all changed UI files → 0 violations.
- `python3 tools/compose-scope-check.py` covers the Canvas animations and the new menu panel.
