# MARBLE_SERVER_TILE_LAYOUT_V208 — small server boxes, and the choice between them and rows

*Core:* `app/src/main/java/com/marbleng/app/ui/MarbleServerTiles.kt` (`ServerTilePolicy`,
`ServerTile`, `ServerTileRow`), `app/src/main/java/com/marbleng/app/model/Models.kt` (`ServerLayout`)
*Tests:* `app/src/test/java/com/marbleng/app/ui/ServerTileLayoutV208Test.kt`
*Wiring:* the Servers page `LazyColumn` and `IosServerListBox` both branch on
`AppSettings.serversLayoutEnum`; Settings › General › `ServerLayoutChoice` writes it.


## The report

> Add a compact-box display for servers, on the servers page and on the home screen. The boxes
> should look interesting, professional, compact and well-spaced. In settings the user must be
> able to choose between row display and box display.

## What the row was doing

A server has exactly three facts a user ever scans: **where it is**, **what it is called**, and
**how fast it answered**. The product printed those three facts on a full-width row — flag, name,
protocol badge, latency, quality bars — with an empty middle on every line, and a height that let
five or six servers fill the screen.

On a 200-node subscription that is a wall of text. The information density of the useful part of
each row is low, the page has to be scrolled to compare anything, and the empty middle is the
reason: the row's width was fixed by the screen, not by the content.

## The second silhouette

`ui/MarbleServerTiles.kt` adds a compact presentation of the *same* server, not a second feature.

**`ServerTile`** folds the row's anatomy into a card of roughly a third of its height:

- the protocol tile leads (26 dp, the same `ProtocolTile` the row uses, so the hue still means the
  same protocol);
- `ProtocolBadge` sits beside it, then the name in two lines max;
- the measured latency closes the tile with `ServerPingStat(compact = true, quietFailure = true)` —
  a failure on a tile says nothing, because a grid of forty failures shouting "no route" is a wall
  of noise where the row could afford one line each;
- the state dot marks live, and the selected rim and animated fill are the row's own colours, so a
  user who knows the list knows the grid.

The tile is a **presentation**: it takes the same tap semantics (select, and connect only when a
tunnel is already up), the same selected/active predicate, the same `BenchmarkResult`. Switching
the layout cannot change what a server does.

**`ServerTilePolicy`** decides how many fit on a line, from the width it is actually given:

```kotlin
const val MinTileWidthDp: Int = 148   // below this the name ellipsises to nothing
const val TileGapDp: Int = 8          // the rhythm the rows already use
const val MaxColumns: Int = 4         // a tile the width of a thumb is unreadable

fun columnsFor(availableWidthDp: Int): Int {
    if (availableWidthDp <= 0) return 1
    val byWidth = (availableWidthDp + TileGapDp) / (MinTileWidthDp + TileGapDp)
    return byWidth.coerceIn(1, MaxColumns)
}
```

360 dp → two columns. 460 → three. 616 → four, and never more. `rememberServerTileColumns`
subtracts the page's 32 dp of side padding first, so the last column is not measured into existence
and then clipped.

## Both surfaces, one preference

`ServerLayout { ROW, GRID }` is one setting (`AppSettings.serversLayout`, persisted by `AppStore`,
parsed leniently by `parseServerLayout` so an older `list`/`tiles` value still resolves). Two lists
that disagree about how a server looks are two products, so the same preference drives:

- **the Servers page** — the group's servers are chunked into lines of `tileColumns` and each line
  is one `LazyColumn` item. A `LazyColumn` cannot hand a grid its own cells, and doing it this way
  keeps the list's keys, its recycling and its `animateItem` glides: a reorder still moves one line
  instead of rebuilding the group. Folded groups stay folded (`tileLayout && !collapsed`), and the
  tile path is excluded from the stacked-row branch so a server is never drawn twice.
- **the Home server box** — `IosServerListBox` grew a third branch (search-empty / tiles / rows) and
  `MarbleHomeAtelier` composes it at `maxListHeight = 340.dp`, so the home page shows a grid of the
  group instead of a six-row window into it.

## The choice

Settings › General › **Server cards**, a `ServerLayoutChoice` with two options — *Rows* and
*Compact boxes* — each with its own miniature preview and one sentence of copy, per the product's
copy rule:

> *Rows* — one server per line, with its full address and actions.
> *Compact boxes* — small cards, as many per line as your screen fits.

## Checks

- `ui/ServerTileLayoutV208Test.kt` — the column rule at both ends (a phone gets two because one
  tile needs 148 dp; a desktop still gets four), monotonicity so dragging a window never *loses* a
  column, the chunking that must not drop or duplicate a server, and a nonsense column count that
  must not hang.
- `scripts/system-integrity-check.py` — the tile exists, both surfaces chunk through
  `ServerTilePolicy`, the layout is a settings choice, and the model exposes it.
