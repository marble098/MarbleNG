# MarbleNG Prism Product UI v54

v54 replaces the flat v53 presentation with a branded, premium visual system while keeping
the existing Xray runtime untouched.

Highlights:
- soft cyan/violet/emerald Prism backdrop
- selective gradient borders and depth
- floating official Material NavigationBar
- compact headers without double system insets
- animated device → secure core → server illustration
- Home node title comes from the profile, not transient runtime status
- flag avatars and cleaned Library node titles
- compact filter/sort sheet and action hierarchy
- Settings category pills without rectangular edge masks
- visual theme preview cards
- bounded switch rows and tighter information density
- shared spring motion and animator-scale support
- source-aware deletion preserved exactly

The patch stays on the repository's current stable Compose BOM instead of moving production
to Material3 alpha APIs. Source verification gates merge to main.
