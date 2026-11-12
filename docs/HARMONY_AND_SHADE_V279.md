# V279 — harmony, a living lens, and a notification that adapts

`MARBLE_HARMONY_V279` · `MARBLE_DOCK_BACKDROP_V279` · `MARBLE_SHADE_ACTIONS_V279`

This pass unifies the everyday icon language, makes the active destination read at a glance, and
finishes the floating dock as a lens rather than a painted pill. It also gives the connected
control a quiet heartbeat and redesigns the expanded tunnel notification without changing the
existing route-deck contract.

## A single icon voice

Home's metric tiles, link affordance and expressive cards now use `MarbleRowIcon` and
`MarbleRowIconShape`, the same rounded wash, scale and glyph treatment used by Settings rows. The
large expressive cards retain their ambient Material-shape watermark; the actual icon is upright and
consistent rather than another independently styled badge.

## Active means filled

The four bottom destinations now morph between the existing outlined glyph and a purpose-drawn
filled HOME, SERVER, GLOBE or TUNERS silhouette. `NavigationItemView` animates fill with selection,
and the wide rail uses the same fill contract. The floating-lens magnification, label visibility,
fixed tab geometry and accessibility descriptions remain unchanged.

## A lens over the live page

`MarbleDockBackdrop` records the page's draw output into a graphics layer, replays it through a
28 px clamped blur into a padded offscreen layer, and clips that material to the moving lens. This
is real backdrop sampling rather than a stronger blur of the lens's own opaque fill. The capture is
turned off while the dock is wide or the page is scrolling, and the render effect is gated to API 31;
older platforms keep the existing vector-glass treatment. The capsule is more transparent, its
sheens are quieter, and the lens retains its rim and caustics. The old 12 px lens-local blur and its
V262 contract remain intact.

## Connected, without changing size

`ConnectPulse` supplies a bounded 2.4-second cosine breath, a soft connected halo and one expanding
ring. The centre trigger, Home pill and dock power button draw these only while connected and only
when looping motion is allowed. They remain inside fixed drawing bounds, so phase changes never
re-measure the dock or move a tab. Reduced motion keeps a static indication.

## A shade with two deliberate modes

The existing notification already has state hierarchy, telemetry controls, six colour themes, the
Persian/RTL twin, a route list and a paged eight-row Studio. V279 completes the expanded, short form:
when route controls are not occupying it, two large RemoteViews-safe actions offer Disconnect (or
Reconnect while blocked) and Open MarbleNG. With the route deck present, those actions stay available
through the established system action row, and the deck keeps its hard row budget instead of being
pushed below the shade's height limit. The compact notification remains a single state row.

The action intents are created by `SmartNotifier` and retain their existing distinct actions, request
codes and data-URI discipline. The selected notification theme paints the action washes, and theme,
telemetry, timer or shade-control changes request a refresh of the already-running tunnel item. The
theme id participates in `ConnectionNotificationState.signature`, so the cooldown gate cannot swallow
a visible palette change. `NotificationPickerLayoutTest` checks both quick-action visibility and the
existing LTR/RTL picker footer behavior.

## Verification

- `ConnectPulseV279Test` pins connected-only activation and bounded paint math.
- `NotificationPickerLayoutTest` exercises expanded actions, their press targets, compact hiding,
  deck coexistence and the existing row-budget layout behavior.
- `NotificationCopyV231Test` verifies that a theme change is a distinct visible notification
  signature.
- `scripts/system-integrity-check.py` remains the source-wide architecture gate.

The local environment does not contain a JDK or Android SDK; Gradle/Robolectric, lint and release
compilation therefore remain CI checks on the pull request. Merge only after the required CI checks
are green.
