# Four-seat design review

This is a role-based review of source, not a claim that four external people or a
physical device evaluated the app. Decision: preserve the crisp dual-pass optic,
reduce competing server chrome, and fix shared components rather than add another
visual language. Native screenshot/device validation remains a follow-up.

## 1 — Visual design: glass needs a night material
Critique: white acrylic and fixed grey/blue content do not constitute a dark theme.
Implemented corrections:
1. Passive tab foreground follows onSurfaceVariant.
2. Active fallback foreground follows primary.
3. Capsule follows the theme's container tone.
4. AMOLED capsule stays pure black.
5. Capsule upper rim becomes accent-tinted in dark mode.
6. Lower capsule boundary uses outlineVariant instead of white glare.
7. Lens core uses accent light at night.
8. AMOLED core has lower opacity than regular dark glass.
9. Night caustic is subdued rather than a white wash.
10. Reflection hotspot is dimmed at night.
11. Upper lens rim adapts to darkness.
12. Side and lower rims use the same tinted material.
13. Inner meniscus dims at night.
14. Specular arc dims and tints at night.
15. Badge background uses semantic error.
16. Badge text uses onError.
17. Connection-dot and badge outlines use the capsule tone, not a white halo.

## 2 — Information architecture: servers before subscriptions
Critique: All sources shows a horizontal card for the aggregate and cards for the
same subscriptions already represented by list sections and the management sheet.
18. All-sources view suppresses the redundant horizontal cards.
19. Source management remains visible and one tap away; choosing a source in the
sheet restores the switching rail, so this is not a removed navigation feature.
20. Remove repeated location totals from the source header.
21. Remove scanning animation from that header; source management retains scan actions.
22. Active filters use primary, not a warning colour for a normal user preference.

## 3 — Interaction design: selection is not connection
Critique: a floating tick after the title plus a dot over the flag plus coloured
text make the same claim three times, and steal room from the server name.
23. Remove the connected tick after the title (batch-selection checkbox remains).
24. Remove the extra dot overlapping the country flag.
25. Put the explicit localized Active state in the metadata line instead.
26. Use semantic successContainer for the active row.
27. Pair its title with onSuccessContainer for readable active text.
28. Remove the maximum row-height cap so large fonts can grow vertically.
29. Expose selected/batch-checked state through accessibility semantics.
30. Expose the route state through stateDescription.

## 4 — Accessible design system: fix the shared primitives
Critique: clipping, tiny actions and hard-coded white spread to every screen when
implemented in reusable components.
31. Long settings values have a bounded width so they cannot consume the title.
32. Non-actionable settings rows no longer promise navigation with a chevron.
33. Rows with their own trailing control do not add a second chevron.
34. Compact buttons retain a 48dp minimum height.
35. Shared icon buttons use a 48dp target.
36. Custom-colour filled buttons choose black/white text by luminance.
37. Switch thumbs choose contrasting black/white for custom track colours.
38. Search clear action has a localized accessible name.
39. Search focus has a visible primary indicator instead of no focus boundary.
40. Icon-only tabs keep an accessible title even when labels are hidden.
41. Bottom tabs expose a selectable group.
42. All true-black aliases resolve dark mode independently of system mode (previously
some aliases requested a black pass over a light palette, which was skipped).

## Verification
- Existing Robolectric row tests cover Persian 150% text, fixed ping width, and action targets.
- Existing dock tests cover dual-pass clipping, drag, badges, RTL and settings.
- Added regression for every true-black alias with system dark disabled.
- Source verification and Android unit/compile checks run in the PR's GitHub Actions;
  this sandbox has no Java/Android SDK, so a local native build is not claimed.
