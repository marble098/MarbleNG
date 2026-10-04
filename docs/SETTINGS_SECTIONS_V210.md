# MARBLE_SETTINGS_SECTIONS_V210 — Settings is a tree, not a shelf

*Core:* `app/src/main/java/com/marbleng/app/ui/MarbleSettingsSections.kt` (`SettingsHubPolicy`,
`SettingsHubGroups`, `SettingsHubGroupSpec`, `SettingsHubSectionSpec`),
`app/src/main/java/com/marbleng/app/ui/Aether2026.kt` (`SettingsHub`, `settingsHubGroups`,
`SettingsHubGroupCard`, `SettingsHubSubSectionCard`),
`app/src/main/java/com/marbleng/app/ui/MarbleExpressive.kt` (`expressiveExpandVertically`)
*Tests:* `app/src/test/java/com/marbleng/app/ui/MarbleSettingsSectionsV210Test.kt`


## The report

> Organise the settings page into a few main sections, with sub-sections under them — sub-sections
> that really are branches of those main sections. The change has to be clearly visible.

## What the page was

Six cards of equal weight, each a flat column of rows, in one scrolling list. Not one thing on
the page said how the six related to each other, and "Home & display", "Appearance", "Data &
sources" and "Engine" are not siblings — they are different *kinds* of thing, drawn as one list
of equals.

Two failures follow from that shape, and both are navigation failures rather than styling ones:

1. **There is no path.** A user looking for the typeface has to know it lives under Appearance.
   The page offers no way to learn that except reading all six cards, and no way to *skip* a
   section once they know they do not want it.
2. **There is no depth.** Everything inside a section was drawn at one level, so a live switch, a
   door to another page and a destructive action all read as the same kind of object.

## The tree

**A main section** is a tone-lit card with an icon, a title, a count of what it holds and a
chevron. Tapping the header closes it; the state is remembered for the visit. Six of them:

| main section | sub-sections |
| --- | --- |
| Home & display | Home style · Home widgets |
| Connection | Engine · Routing · Automatic connect |
| Measurement & servers | Ping & ranking · Server library |
| Data & sources | Backup · Sources |
| Appearance | Theme · Navigation · Language & text |
| System | Alerts · About |

Every row the flat hub had is still on the page, filed under the parent it belongs to. Two rows
moved up a level because the tree made the duplication obvious: **Server cards** (how a server is
drawn — rows or compact boxes) now has a door from the hub, and **Background access** is in
System › Alerts beside the Notifications door it belongs with, instead of being a card nobody
could find.

**A sub-section** is an inset card with its own name and rail inside that parent. The inset *is*
the hierarchy: a sub-section sits on the parent's inset fill, carries a short rail in the parent's
tone, and names itself one step of the type ramp below the parent's label — so the two levels
read as two levels without a divider or an indent guide. Its rows inherit the parent's tone
(`LocalSettingsSectionTone`), so a sub-section can never introduce a colour of its own.

**The header owns the whole tree's state**: "All sections open" or "3 / 6 sections open", and an
*Expand all* / *Collapse all* action. That is the difference between a hierarchy you can admire
and one you can use — a user who lives in Appearance closes the other five in one tap.

## Two decisions worth defending

**The state is the set of *closed* keys, not the set of open ones.** A section nobody has touched
is open, so a section added in a later release opens by default instead of inheriting a stored
decision nobody made about it. `SettingsHubPolicy.collapseAll(keys)` names what it closes for the
same reason: a "collapse all" that silently leaves a new section open is a bug that only shows up
a release later.

**Expansion is a height animation, not a swap.** `expressiveExpandVertically` / 
`expressiveCollapseVertically` (the delayed fade of the fade-through pattern, over
`MarbleMotionSpecs.Layout`) rather than `expressiveFadeThrough`: a collapsible section is a
spatial relationship — the parent stays exactly where it is and the children appear beneath it —
and a fade-through is for two equal-level screens. The chevron turns a quarter turn and never
changes size, so nothing in the list moves horizontally when a section opens.

The collapsed state is hoisted into `SpatialSettings`, above the page switch, next to the scroll
states it already keeps for the same reason: collapsing Appearance, visiting Theme and coming
back must not silently re-open everything.

## Checks

- `ui/MarbleSettingsSectionsV210Test.kt` — a section nobody touched is open (including one this
  build has never heard of), a tap closes only that section and a second tap opens it again,
  *expand all* means every section is open whatever was closed, *collapse all* names every
  section it closes, the count reads the same list the page draws, and the keys are the six the
  page draws.
- `scripts/system-integrity-check.py` — the policy object and the key list exist; the hub
  composes `settingsHubGroups`, `SettingsHubGroupCard` and `SettingsHubSubSectionCard`; the hub
  itself composes **no** flat `SettingsHubCard` any more (that is what makes the hierarchy
  structural rather than decorative); and the collapsed state is hoisted and toggled through the
  policy.
