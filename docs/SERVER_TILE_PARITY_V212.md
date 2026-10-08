# MARBLE_SERVER_TILE_PARITY_V212 — the box is the same server as the row

*Core:* `app/src/main/java/com/marbleng/app/ui/MarbleServerTiles.kt` (`ServerTile`)
*Tests:* `app/src/test/java/com/marbleng/app/ui/ServerTileParityV212Test.kt`
*Wiring:* the Servers page grid (`Aether2026.kt`) and the Home server box
(`MarbleHomeStyles.kt` › `IosServerListBox`), both through the same `ServerTile`.


## The report

> The box view of servers is missing a lot of things. For example it does not show IPv4 and IPv6,
> and its three-line icon cannot be tapped.

`MARBLE_SERVER_TILE_LAYOUT_V208` introduced the compact box as an *alternative silhouette* for the
same list, and `MARBLE_SERVER_TILE_TRUTH_V210` gave it the two things a grid most needs (a box that
does not answer recedes; a long name cannot break the grid). What it never got was the rest of the
row: the measured address family, and the server's own menu.

So the preference in Settings did not choose between two ways of *reading* the library. It chose
between a layout that could answer "can this node do IPv6?" and one that could not, between a layout
where a server can be renamed, moved, QR-shared, inspected and deleted and one where it can only be
selected. The only way to get those back was to switch the layout back — which is exactly what the
report describes.

## What parity means here, and what it does not

Two facts about the row were missing from the box, and they are missing for different reasons:

**1. The measured address family.** The row renders `repo.ipFamilyScan(profile)?.chip` in
`familyChipTone(...)`: `v4`, `v6` or `v4+v6`, and only when a scan actually answered. The box now
renders the same chip in the same colour, because both silhouettes read the same
`AppRepository.ipFamilyScan` — there is one scan, one verdict and one hue, and a box that shows
nothing is not a smaller row, it is a row with an answer withheld.

The rule the row established is kept exactly: **no scan, no chip.** Inventing `v4` from a hostname is
the guess the scanner exists to replace, and the whole IPv6 policy is built on that answer being
real.

**2. The menu.** `ServersNodeMenu` is now `internal` and composed into the grid through a
`trailing` slot on `ServerTile`. The slot is deliberately a composable and not a parameter list: the
tile is a presentation, so it does not learn about renaming, moving, QR codes or deleting — it only
reserves the place where *the page's own* menu goes. Two silhouettes with two menus is how they
drifted apart; one menu composed by two layouts cannot.

`familyChipTone` became `internal` for the same reason. A colour that lives inside one file is a
colour two silhouettes can disagree about.

## The one asymmetry, on purpose

The Home server box's grid shows the family chip but not the menu — because the Home box's *rows*
have no menu either. It is a route picker, not a route manager: it selects, and everything else
lives on the Servers page. Giving its boxes a menu the rows do not have would have created the
mirror image of the reported defect.

## Checks

- the tile has both slots and draws the chip — `ServerTileParityV212Test`
- both grids pass the family chip; the Servers grid composes the menu — `ServerTileParityV212Test`
- the menu and the family colour are shared, not duplicated — `ServerTileParityV212Test`
- geometry is unchanged, so `ServerTileLayoutV208Test` and `ServerTileTruthV210Test` still pin it
