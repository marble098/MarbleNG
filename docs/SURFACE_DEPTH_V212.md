# MARBLE_SURFACE_DEPTH_V212 — one depth contract for every surface, and predictive back

*Core:* `app/src/main/java/com/marbleng/app/ui/MarbleSurfaceDepth.kt` (`MarbleDepthPolicy`,
`Modifier.marbleSurfaceDepth`, `Modifier.marblePredictiveBack`)
*Tests:* covered by the source-wide invariants below (the mechanism is a modifier and a gesture
handler; both are drawn, not computed)
*Wiring:* `ServerTile`, the connection-detail surface and the Settings sub-page surface.


## The report

> The app looks dry and unattractive. Replace the current UI libraries with the newest pure-visual
> Android UI libraries and strengthen the look a lot, with the same structure.

Two things had to be separated before anything was written, because one of them is not a code change.

**The libraries are already the newest there are.** The app is on Compose BOM `2026.09.00`, Kotlin
`2.5.0-Beta1`, AGP `9.5.0-alpha08` and Gradle `9.9.0-milestone-2`, pinned in
`gradle/toolchain.properties`. There is no older UI toolkit in the dependency graph to replace, and
adding a third-party "visual" library on top of Compose would add a dependency whose version this
sandbox cannot resolve and whose look the product would then have to fight. So the honest reading of
the request is: *use the newest mechanisms*, which is a design job, not a dependency job.

**"Dry" has a specific cause in this codebase.** `MARBLE_HOME_CLOUD_DEPTH_V191` gave the Home cards a
depth contract — one cool brand-tinted shadow, a gradient rim that catches light at the top, a whisper
wash inside the top edge. But it was written *inside* `HomeCloudCard`, as a body rather than as a
contract. Every surface that is not a Home card — the server boxes above all — was still
`background(fill)` plus a 1 dp hairline, i.e. the pre-V191 plane. One page could show a card that
sits on a light and, four pixels below it, a card that does not, and the eye reads that inconsistency
as cheapness rather than as two different components.

## 1. The contract became a modifier

`Modifier.marbleSurfaceDepth(shape, fill, rim, lifted)` is the V191 treatment extracted, with the
numbers in one place (`MarbleDepthPolicy`) instead of re-derived per call site. It adds no new
effect: the same three channels, under the same rules — never a grey shadow, never a second
translucent plane (V187 removed those because nested translucency on a busy page reads as fog, and
that reasoning has not changed).

The caller keeps `fill` and `rim`, so a selected card still selects in its own colour, a live one
still rims in emerald, and a silent server still recedes. The modifier answers only the question a
call site should not have to answer: *does this box sit on a light?* Two lift steps is all the
product has — resting and raised — because a third would need a third meaning.

The server grid is the first surface to wear it.

## 2. Predictive back

`PredictiveBackHandler` replaces `BackHandler` on the two full-screen surfaces that dismiss (the
connection-detail page and a Settings sub-page). Android's back gesture is not an edge any more: the
system reports a progress while the finger travels, and an app that ignores it snaps at the moment
the gesture commits while everything around it was already moving — a new discontinuity in an app
whose rest of the UI animates on the shared frame clock.

`Modifier.marblePredictiveBack(progress)` is the platform's own answer: the surface being left
**recedes**. It does not slide sideways past its replacement, because these two are not a stack — the
connection page is still there, unchanged, behind the surface being dismissed, and a shared-axis
slide would claim otherwise.

## 3. What this chapter deliberately does not do

No blur, no backdrop layers, no new dependency, no version bump, and no restructuring — the request
was explicit about keeping the same structure, and the structure is what makes the surfaces
comparable in the first place.

## Checks

- the source-wide invariants for the design system still pin the Home card's own depth contract
- the mechanism is a `Modifier` and a gesture handler, so it is verified by the build and by the
  source invariants rather than by arithmetic; the numbers it owns (`MarbleDepthPolicy`) are the
  contract's, not a call site's
