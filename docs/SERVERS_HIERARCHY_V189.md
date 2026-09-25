# MarbleNG Servers Hierarchy v189

`MARBLE_SERVERS_HIERARCHY_V189` is a design chapter. It rebuilds the Servers page around one
idea: **a subscription is a container, and its servers live inside it.** Before this chapter the
page was a stack of visually equal rows — a subscription header and its servers shared one card
outline, one type scale, one tile size and one border weight, so the only thing that said "these
ten rows belong to that header" was their order. Now the page has two levels, and the difference
between them is measurable: level 2 is smaller than level 1 in every dimension it shares with it.

The reference is how modern file and account managers nest content: a parent card with the
boldest outline and the largest type, and a recessed list inside it whose rows are separated by
hairlines rather than by borders of their own.

## 1. One table, `MarbleServersHierarchy.kt`

Everything that decides how big a level is, and everything a subscription's data plan says, moved
into one new file: `app/src/main/java/com/marbleng/app/ui/MarbleServersHierarchy.kt`. It imports
no Compose. It is a size table and some arithmetic, so the drawing code reads one source of truth
and ordinary JVM unit tests (`ServersHierarchyV189Test`) pin it.

**The size table**

| | level 1 — subscription | level 2 — a server inside it |
|---|---|---|
| corner radius | **16 dp** | **12 dp** |
| outline | **1.5 dp**, the boldest on the page | **none** — a hairline divider between rows |
| position | the card's own edge | **8 dp inset** from that edge, both sides |
| surface | `VoidElevated` (the card) | `Glass` (one container step inside it) |
| name | **16 sp** bold | **13 sp** bold |
| tile | 32 dp chevron | **30 dp** protocol tile (a standalone server keeps 40 dp) |
| controls | three equal 32 dp icons on a 3 dp gap | a 28 dp menu |
| row padding | 12 dp | 11 dp / 7 dp |

**The plan arithmetic** — `usagePercent`, `usageFraction`, `usageTier`, `compactBytes`,
`usageText`. The percent is rounded once, and the tier is derived from that same rounded number,
so the printed percent and the colour of the bar can never disagree about which side of 70 % or
90 % a plan is on. `SubscriptionUsageTier` is `CALM` (≤ 70 %), `WATCH` (≤ 90 %), `CRITICAL`
(above) and `UNKNOWN` (the provider reported no quota at all — no bar, no percent, just "∞").

## 2. The filter rail: counts inside the controls

The rail used to be four 38 dp controls with 7 dp between them, and the page header above it
carried a line of counts (`2 groups • 48 servers`) that the reader had to hold in their head and
match to a control by hand.

- the header's count line is **gone**;
- the group capsule now badges **how many groups** the page holds ("All groups · 2 groups"), and
  the protocol capsule badges **how many servers** the current scope shows ("All protocols · 48");
- the advanced-filter button badges **how many of its own switches are on**, so the menu says
  what it is doing before it is opened;
- everything dropped a step: 38 → **32 dp** controls, 7 → **6 dp** gaps, the capsules' leading
  glyphs removed (the space they took now holds the count), 11 sp labels, 9 sp badges.

The badges take the capsule's own tone at badge weight — never a second colour — so a count reads
as part of the control and not as a notification. Both capsules are `weight(1f, fill = false)`
with an ellipsising label, so on a narrow phone the label shortens and the badge survives.

## 3. The subscription card — level 1

```
┌─────────────────────────────────────────────────── 16 dp radius, 1.5 dp outline
│ ▸  My Subscription  [48 servers]        ⟳  ⏻  ⋯    16 sp bold name, three equal controls
│ ⓘ 12.4 GB / 50.0 GB                        25%
│ ▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░   real bar, colour = the plan's tier
│ Expires: 2026/12/01   🌐 Website   Auto-update on   10.5 sp, secondary
│   ┌───────────────────────────────────────────┐    ← the nested block starts here, inset
```

- the header row is chevron + **bold name** + a count pill, with **three equal controls**
  (refresh / status / menu) on one even 3 dp gap at the trailing edge;
- the usage line is text **and a real bar** — `MarbleExpressiveLinearIndicator`, so the fill
  settles on the expressive progress spring — coloured by tier: emerald to 70 %, amber to 90 %,
  danger past it. An unmetered plan shows an empty track, because there is nothing to fill;
- expiry, the provider's **Website** link and the auto-update state moved into one **secondary
  line** under the bar at 10.5 sp. They are read once and then ignored, so they no longer compete
  with the plan itself. The website is a small globe-and-word link, not a button.

## 4. The servers inside it — level 2

The rows are no longer cards. Each one is a slice of a single recessed block:

- **inset** 8 dp from the card's edge on both sides, on `Glass` — one container step away from
  the card's own `VoidElevated`, darker than the card in Light (recessed) and lighter than it in
  Dark (an inset well). Either way the servers sit on a *different surface* from the card that
  holds them;
- **no border of its own** — rows are separated by a 1 dp hairline, inset further than the panel
  so it reads as a separator inside the block. The only row that grows an outline is the one
  carrying traffic, and it grows it in the state colour beside its own state word
  (`Connected` / `Securing` / `Selected`);
- **smaller everything**: a 30 dp protocol tile (a standalone server on Home keeps 40 dp), a
  13 sp name, a 10.5 sp endpoint, a 12.5 sp latency with a smaller quality meter, 7 dp of
  vertical padding, a 28 dp menu;
- the block **ends 8 dp before the card does**, so the nested list never touches the outline that
  contains it; its first row rounds the block's top and its last row its bottom (12 dp);
- the subscription's own outline still runs down through every row at the card's edge, so the
  whole group remains one continuous box — one frame, drawn slice by slice, at 1.5 dp.

**Failure is quiet.** A server a probe ran against and could not reach fades to 58 % and shows a
cross in a muted tone instead of a red number. Red on this page is reserved for facts a user must
act on now — a measured latency over 250 ms still reads red, because that one is a measurement;
"this one did not answer" is a property of that server, not an emergency, and the row says so by
stepping back rather than by shouting. `ServerPingStat` grew two switches for this, `compact` and
`quietFailure`, both off by default so the Home list is untouched.

## 5. What did not change

Selection is still not connection (a tap selects; it re-connects only while a tunnel is up),
swipe-right still opens rename, names still marquee, the protocol identity system from v188 still
owns the tile, the glyph, the badge and the tone table, the entrance cascade still greets the page
once, and one probe sweep still has exactly one cancel. The Persian lexicon gained the count
forms the new badges need (`1 group`, `N groups`, `1 filter`, `N filters`), so both languages say
the same thing in the same place.

## 6. The contract

`ServersHierarchyV189Test` pins the two things that make the hierarchy real: that level 2 is
smaller than level 1 in every shared dimension (tile, name, ping, corner radius), and that the
plan's percent, fill and colour are three readings of one rounded number. A future edit that
grows a nested row back to the size of its card, or that paints a 71 % plan green, fails a test
instead of shipping.
