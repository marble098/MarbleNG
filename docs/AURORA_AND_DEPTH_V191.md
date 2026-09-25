# MarbleNG Aurora & Depth v191

`MARBLE_AURORA_BACKDROP_V191` and its sibling passes answer one complaint, heard about the whole
product: **the interface reads as tired and lifeless**. Thirteen screenshots of the live app all
show the same root condition, and this chapter repairs it at the design-system layer — the one
place a fix reaches every screen at once. No behavior, engine, routing or storage path changes.

## The diagnosis (from the code that renders those screens)

1. **The page under every tab was a two-stop gradient whose stops differ by ~3 % lightness**
   (`#F8FBFE → #EDF4FB` light, `#000000 → #060D18` dark). On a real panel that is one flat
   empty field. Every card, pill, divider and badge floated on it with nothing behind them —
   the single biggest source of the "dead page" feel.
2. **Every shadow had been removed from Home** (`MARBLE_HOME_FLAT_SURFACES_V187`,
   `CardElevation = 0.dp`). Opaque white boxes with a 1 dp border on a near-white page put every
   surface on exactly the same depth plane.
3. **The PrismPanel ambient glow was tuned to a maximum of 3.6 % alpha** — below what an eye
   registers. The element shipped, was never once seen, and the product read as flat.
4. **The dock's selected pill carried a 16 % fill / 20 % rim** — on the light theme the active
   tab was nearly indistinguishable from its neighbours; a navigation bar must answer "where am
   I?" from across the room.
5. **While scrolling in Light, the dock entered glass at ~61 % white body** — on a white page
   the bar effectively vanished mid-gesture.
6. **The Settings hub was a wall of identical grey boxes** — same fill, same 18 % border, no
   depth and no colour, so the page read as one undifferentiated column.
7. **The connection detail page painted its own flat `Void` floor** over the window, so the
   container transform landed on a dead rectangle instead of the page the user just left.

## The fixes

### Aurora backdrop — `PrismBackdrop` (every tab, both themes)

The base vertical wash survives; laid over it are three large soft radial glows — electric blue
off the top-start corner, bright ice off the top-end corner, and a deep navy pool at the floor.
Alphas are whisper-level in Light (~4–7 %) and ambient in Dark (~9–14 %): atmosphere and depth,
never decoration. Under the phone-colours theme every hue derives from the live Aether ramp, so
a wallpaper palette keeps its own aurora (theme coherence, same lesson as V190).

The two upper glows breathe — very slowly (11 s / 15 s), out of phase — on Marble's one shared
frame clock, read inside the draw lambda so the page never recomposes, and frozen at the calm
midpoint when the user disables animations. Nothing scrolls or shimmers; the page simply has air
and light in it.

### Home cards rejoin the physical world — `HomeCloudCard`

The flat-plane experiment ends. The fix for "crowded" was never *zero* shadow — it is ONE quiet
shadow. Each card now carries the Prism depth contract:

- **one cool brand-tinted shadow** — navy-cast in Light (a grey shadow on a blue page reads as
  dirt), an electric glow lift in Dark (shadows on AMOLED need colour to exist at all);
  resting 3 dp, selected 7 dp, animated on the spring so selection *raises* the chosen route;
- **a gradient hairline** that catches the aurora at the top and fades to the plain rim at the
  bottom — light hitting an edge, not a second border;
- **a whisper brand wash** inside the top edge (3.5 % light / 5.5 % dark), fading over the
  first ~130 dp — enough to break the perfect flatness of the fill, never enough to tint
  content.

All four Home presentations render through this one container, so the whole first screen moves
together. The slide-to-connect track keeps its control identity but gains the same soft shadow,
so it no longer reads as a hole cut in the page between two lifted cards.

### The glow that finally glows — `PrismPanel`

Resting ambient light rises from an invisible 1.4 % to a calm, visible 4.5 %; a selected panel
floods to a clear 10 %. The shadow's ambient/spot lifts with it (`.16/.22 → .22/.32`), and the
rim gradient starts one step stronger. Every Settings sub-page card, the Live quality bento, the
detail pages and the fourth slot's panels inherit this in one place.

### The dock that says where you are

- selected pill: **24 % fill + 34 % rim + a vertical accent wash** (lit object, not a tint);
  geometry untouched — the bar still never moves;
- light-theme glass: body `.78 → .94`, hairline `.08 → .16`, and the glass floor
  `0.78 → 0.90` — the page shows *through* the bar instead of the bar vanishing *into* the page.

### Colour-coded Settings hub — `SettingsHubCard`

Each group card carries its section's tone in the surface itself: a soft tone-lit wash from the
top edge, one cool shadow, a tone gradient rim, and the section label lifted to the full tone in
bold. The page reads as colour-coded groups on separate depth planes, not a grey column.

### The Servers page gets its hierarchy back

- **Group (level-1) headers** carry a whisper accent wash from the top edge, faded out long
  before the rows begin — the subscription reads as a lit container, and the wash can never
  seam into its rows.
- **The header verbs (`+`, sort)** keep their card surface but gain a soft cool shadow, a
  legible rim, and a lift on selection — controls that read as controls.
- **The live probe strip** joins the same depth language while it is on screen.

### Fourth tab & detail page

- `DockSlotCard` (every surface of the customizable slot) adopts the shadow + tone-wash +
  gradient-rim language, so the page reads as the same physical system as Home and Settings.
- The connection detail overlay no longer paints its own flat floor; the window-level aurora
  shows through, keeping the container-transform arrival on the living page.

### Buttons: the filled verb lifts

`PrismButton` keeps the V117 flat rule for quiet and tinted controls — dense Settings groups
stay calm — but the one *filled* verb of a surface (Connect, Disconnect) now carries a soft
accent-tinted lift. A shadowless saturated slab read as a dead sticker next to the lifted cards
it sits on.

## Compatibility & cost

- minSdk 26 unchanged; no new APIs — only `shadow`, `Brush` and the existing shared frame clock.
- The aurora lives in ONE draw node under the window: no recompositions, no allocation outside
  three gradient brushes per frame, and it freezes completely when animations are disabled.
- Every wash length is density-aware (dp → px), so the lit bands keep their proportion on every
  panel.
- No geometry anywhere moves: the dock rule, the anchored status copy and every fixed slot from
  V186 are untouched. Only colour, light and depth were added.
