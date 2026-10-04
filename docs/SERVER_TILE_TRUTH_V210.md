# MARBLE_SERVER_TILE_TRUTH_V210 — a box that answers, a box that does not, and a name that fits

*Core:* `app/src/main/java/com/marbleng/app/ui/MarbleServerTiles.kt` (`ServerTileTruth`,
`ServerTileNamePolicy`, `ServerTile`, `ServerTileRow`)
*Tests:* `app/src/test/java/com/marbleng/app/ui/ServerTileTruthV210Test.kt`
*Wiring:* the Servers page `LazyColumn` and `IosServerListBox`, both through the same `ServerTile`.


## The report

> 1. In the box view of servers, the ones that do not answer have to be told apart from the ones
>    that do — by the whole box going paler.
> 2. A long server name must not break the layout of the boxes. Long names should move, in the
>    place the name has inside the box.

Two reports about the same silhouette, and they share one cause: the compact box
(`MARBLE_SERVER_TILE_LAYOUT_V208`) drew every server as if all servers were the same kind of
thing.

## 1. Every box looked equally alive

The tile had three states — active, selected, neither — and all three were about *what the user
had done*, not about *what the server had done*. A node that had just failed its measurement was
painted in the same fill, with the same ink, as the fastest node in the subscription. The one
thing a grid of forty servers exists to show — which of these is dead — was the one thing it
hid, and the only clue was a `✕` glyph 12 sp tall in the corner.

**The fix is not a badge, it is a verdict.** `ServerTileTruth` turns the measurement the page
already holds into one of three answers:

| verdict | when | what the box does |
| --- | --- | --- |
| `ANSWERED` | a measurement exists and clears the 20 ms honest floor | full weight |
| `SILENT` | a measurement exists and does not | the whole box fades to `SilentAlpha` (.42) |
| `UNMEASURED` | nobody has asked | full weight — deliberately |

The third row is the one that took the thinking. **Absence of a measurement is not evidence.** A
library the user imported thirty seconds ago has no results at all; dimming forty boxes because
the sweep has not run yet would punish the user for not having run it, and would make the page
look broken on first launch. So an unmeasured box looks exactly like a working one.

The fade is applied to the *whole* painted box — fill, hairline, flag, name, address, latency —
once, after the fill and the rim are composited, and it animates on `MarbleMotionSpecs.DockFloat`
so a sweep settles into its new truth instead of blinking the grid. A fade on a label alone would
have been read as "this text is disabled"; a fade on the box is read as "this server is not
here", which is the fact.

`SILENT` is also withheld while a box is *being* measured (`testing = true` → `UNMEASURED`). A
sweep rewrites the result list as it goes: without that rule a box that failed the last sweep
would sit faded while its own verdict was pending, and then fade back out a second later.

## 2. A long name grew its box

The name slot was `maxLines = 2`. Server names are the one field in a tile whose length the
product does not control — subscriptions ship labels like
`🇩🇪 DE-07 · Frankfurt Premium Plus [Premium]` — and a second line made *that one tile* taller than
the others in its row. The row is a `Row`, so every other box stayed the same size and the grid
stopped being a grid: one tall box, two short ones, and a seam you can see from across the room.

Two changes, because the two halves of the defect are different:

**The slot is one line, always.** `ServerTileNamePolicy.MaxLines = 1`, `softWrap = false`. A name
that does not fit travels through the slot instead of expanding it — `Modifier.basicMarquee`,
which measures the text against the slot itself and only animates when it overflows, so a short
name is an ordinary static label and there is no "is this one long enough?" branch to get wrong.
It is attached only when `MarbleMotion.current.motionEnabled`: with the system's *remove
animations* switch on, an endlessly sliding label is the one kind of motion a user with a
vestibular trigger cannot switch off, and the honest fall-back is the ellipsis the row always
used.

**Every box takes its row's height.** `ServerTileRow` measures at `IntrinsicSize.Max` and each
tile fills it (`fillMaxHeight`), so whatever a name, an address or a latency reading does to one
box, no box can be taller or shorter than the box beside it.

## Checks

- `ui/ServerTileTruthV210Test.kt` — the three verdicts, the latency floor from both sides, the
  rule that an unmeasured box is not a silent one, the rule that a box mid-measurement never
  fades, the fade staying inside a readable range, and the name-slot numbers.
- `scripts/system-integrity-check.py` — the policy objects exist and are used at the drawing
  site: `alpha(tileAlpha)`, `maxLines = ServerTileNamePolicy.MaxLines`, `basicMarquee(`,
  `height(IntrinsicSize.Max)` and `Modifier.weight(1f).fillMaxHeight()`.
- The chapter's predecessor (`MARBLE_SERVER_TILE_LAYOUT_V208`) is what made the box; this chapter
  is about what the box is allowed to claim.
