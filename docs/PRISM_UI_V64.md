# MarbleNG Prism UI v64 — anchored Home status + painted connected-node frame

Two real-device polish defects, both fixed without moving a single measured box.

## 1. Home: the Connect control no longer travels

`HomeOrbitalHero` printed the runtime title and sentence directly above the Connect ring. Every state
has a different length ("Xray is negotiating the route" is one line, "Choose a route or use the last
successful server" wraps to two), so pressing Connect shortened or lengthened that block and dragged the
whole Connect surface up and down inside the same card.

`HomeStatusAnchor` now owns the block:

- the title reserves one line of `titleLarge`, the sentence reserves `HOME_STATUS_DETAIL_LINES` lines of
  `bodySmall`;
- the reservation is computed from the two styles through `LocalDensity`, so it stays correct under font
  scaling instead of being a hardcoded `Dp`;
- `maxLines` caps the same line count, so copy can never exceed its own reservation;
- the swap itself animates inside the reservation (`AnimatedContent` + the shared Prism springs).

Result: the ring, the selector and everything below the status block keep one position in
`READY / CONNECTING / CONNECTED / BLOCKED`.

## 2. Library: the connected row is now unmistakable — and still a row

v61 removed the emerald `PrismPanel` frame from the connected row because swapping border/radius/shadow on
one list item made it look like a different component and resized it. That left the live route almost
invisible. v64 keeps the constraint and restores the emphasis the design system always promised
("connected node receives a semantic halo/outline") as pure paint:

- `PrismRouteFrame` is a sibling overlay (`Modifier.matchParentSize()`), so the halo is drawn after the
  card finished drawing itself and never participates in measurement: ring, inner bloom, left energy rail
  with a travelling pulse;
- `PrismPanel(tint = …)` floods the surface with the state color *under* the content, so text contrast is
  unchanged;
- the endpoint strip takes the route color, the avatar gains a verified check badge outside its clip, and
  the tiny dot chip becomes a real `CONNECTED` pill whose line reserves the pill height, so the row still
  never changes height when a route starts or stops;
- the row whose handshake is still running borrows the same frame in violet (the palette's transitional
  state) and shows `SECURING`, which gives tap feedback for the seconds before `CONNECTED` lands.

Ambient motion is read inside `PrismRouteFrame` only, and it is composed only while a row is emphasized, so
an idle or disconnected list performs no extra per-frame work and honors the system animator scale.

Markers: `MARBLE_HOME_STATUS_ANCHOR_UI_V64`, `MARBLE_ACTIVE_NODE_HALO_UI_V64`,
`MARBLE_ACTIVE_NODE_HALO_DS_V64`, `MARBLE_ANCHORED_STATUS_TEXT_DS_V64`.
