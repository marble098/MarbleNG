# THE FOURTH TAB AND THE COMPACT HOME BANNER — V167

**The bottom bar was promised four slots and shipped three. The fourth one is now real, it belongs
to the user, it is personalized from its own Settings page — and the Home status banner that used to
eat the top of every page is roughly half its old height in all four themes.**

Engine pin: unchanged — the pinned Xray-core, hev-socks5-tunnel and sing-box extended from
`core-lock.json`. Nothing in this chapter touches a core binary.

Markers: `MARBLE_DOCK_SLOT_V167` for the fourth tab and its Settings page,
`MARBLE_HOME_COMPACT_BANNER_V167` for the Home status banner.

---

## خلاصه (Persian summary)

نوار پایین برنامه از ابتدا قرار بود چهار تب داشته باشد و همیشه با سه تب منتشر می‌شد. تب چهارم
اکنون واقعی است و مالِ خودِ کاربر است:

- سه شکل دارد: **نبض زنده** (مسیر فعال + نرخ‌ها + ابزارها)، **یک اشتراک** (همه‌ی سرورهای یک منبع،
  با پینگ و رتبه‌بندی و به‌روزرسانی همان منبع)، و **یک کانفیگ** (یک سرور مشخص، یک ضربه).
- یک صفحه‌ی اختصاصی در **تنظیمات ← تب چهارم** دارد: روشن/خاموش، نوع، منبع یا کانفیگ، نامی که خودتان
  می‌نویسید (حداکثر ۱۴ نویسه)، و آیکون (نبض، جرقه، لایه‌ها، قطب‌نما).
- با خاموش‌کردن آن، نوار دقیقاً به همان سه تب قبلی برمی‌گردد؛ نه صفحه‌ای گم می‌شود و نه چیزی جابه‌جا.

و بنر وضعیت صفحه‌ی خانه از حدود ۱۳۹dp به حدود ۷۳dp رسید — در **هر چهار تم** — بدون اینکه حتی یک
اطلاعات حذف شود: وضعیت و مدت اتصال در یک خط، و ردیف دوم با پرچم، نام سرور، IP و پروتکل و منبع، و
همان پینگ با عرض رزروشده.

---

## 1. Why the fourth slot was missing, and why it is a slot instead of a page

The bar has been three tabs (`DECK`, `LIBRARY`, `SETTINGS`) since `MARBLE_FLOATING_DOCK_V117`. Four
attempts to add a fourth one were shelved for the same reason: every candidate was a *product*
decision imposed on every user. A "Rank" tab duplicated the Servers page's own rank action; a "Route
score" tab duplicated Home's own quality readout. Shipping either would have added a permanent tab
that most installs never open.

The fourth slot is therefore not a fourth page the product invents. It is a slot the user fills, and
the feature is the *choosing*:

| Kind | What the tab opens | Why it is a real shape |
| --- | --- | --- |
| `PULSE` (default) | The live route: state, uptime, down/up, ping, jitter, quality — plus the six tools that act on the whole library | The only kind that is meaningful on a fresh install with an empty library, so the slot is never blank |
| `SOURCE` | Every server of one subscription, or of the local **Manual** bucket, or of the whole library | The page a subscription owner actually wants a thumb away: their own list, with *that source's* ping/rank/refresh verbs |
| `CONFIG` | One saved config, with the product's own connect control, its own ping, and its details | One tap to one node, without walking the library every time |

`DockSlotKind` (`model/Models.kt`) owns the three ids and their parser. The parser also reads the
retired draft vocabulary (`SUB`, `SUBSCRIPTION`, `GROUP` → `SOURCE`; `NODE`, `PROFILE`, `SERVER` →
`CONFIG`), because a value already written to a device has to keep opening the slot it named.

## 2. A preference, not a fixture

`MARBLE_DOCK_CUSTOM_V145` made the bar's size, icons and labels a preference. The fourth slot joins
them, and it is the first one that can *remove* a tab, which is what makes it dangerous: the pager,
the persistence record and the bar all have to agree about how many tabs exist, in the same frame.

One list decides, and it is a pure function:

```kotlin
val tabs = dockSlots(SpatialTab.entries, SpatialTab.CUSTOM, repo.settings.dockSlotEnabled)
val initialIndex = dockSlotIndex(tabs, rememberedSpatialTab(repo.lastAppTab))
```

* the pager is created with `{ tabs.size }` pages and its `initialPage` is the repaired index;
* the page behind the bar is looked up — `when (tabs.getOrNull(pageIndex) ?: SpatialTab.DECK)` — and
  never indexed by ordinal, so a bar that lost its fourth slot can never hand the pager a page it
  does not have;
* a persisted `CUSTOM` from a session where the slot was on is repaired into the bar that is
  actually on screen (`dockSlotIndex`), so turning the slot off does not ask for page 4 of a
  three-page pager;
* every tab change still goes through `goToTab(pageOf(tab))`, and the dock keeps iterating
  `SpatialTab.entries` while skipping the hidden slot — the three tabs the product shipped with keep
  their exact positions.

The slot itself is seven normalized preferences, written and read by `AppStore` like every other dock
key: `dockSlotEnabled`, `dockSlotKind`, `dockSlotSourceId`, `dockSlotProfileId`,
`dockSlotProfileSourceId`, `dockSlotLabel`, `dockSlotIcon`. Both the kind and the glyph are
re-normalized on read **and** on write, so a hand-edited or older value can never leave the bar
holding a glyph or a kind that does not exist.

## 3. The caption and the glyph are the user's

The bar draws four tabs in one row, so a caption is bounded at the model level —
`DOCK_SLOT_CAPTION_MAX = 14` — and blank is not an error: it means "name it after what it opens".
Precedence is *user's words → the target's own name → the kind's constant*:

```kotlin
fun dockSlotCaption(configured: String): String = configured.trim().take(DOCK_SLOT_CAPTION_MAX)
fun dockSlotDefaultCaption(kind: DockSlotKind, targetName: String): String
```

Only the product's own two constants (`Pulse`, `Custom`) are translated; a subscription is allowed
to be called *Manual*, and the product does not get to rename it (`dockSlotCaptionText`).

The glyph is one of four Canvas silhouettes — a pulse trace, a spark, a stack of layers and a
bearing — drawn by the same `MarbleTabIcon` Canvas that draws the other three tabs, so the fourth
slot keeps the bar's optical weight instead of arriving as a foreign icon. `DockSlotIcon` owns the
four ids and reads the retired draft names (`BOLT`/`STAR`/`ACTIVITY`, `STACK`/`LIBRARY`/`BOOK`,
`COMPASS`/`ROUTE`/`GLOBE`).

## 4. The Settings page

`SettingsPages.DOCK_SLOT` is a real sub-page (`SettingsDockSlotPage`), reachable three ways: the hub
row **Fourth tab**, the General workspace card next to **Navigation bar**, and the *Customize* action
in the fourth tab's own header — because a slot that names a page should carry the door to change it.
The page holds, in the order the questions come:

1. a live miniature of the bar at the size the user chose, with the fourth slot drawn exactly as the
   bar draws it — the only honest answer to *"what did I just change?"* that does not leave the page;
2. the master switch;
3. the kind (`CyberSegment` × 3);
4. the subject — the source picker (`All servers`, `Manual`, one row per subscription with its own
   server count) or the config picker (search across the library, bounded to 60 rows, one tap to pin);
5. the caption field, with the resolved fallback named under it;
6. the four glyphs, drawn at their real size.

Every control writes through `repo.updateSettings` immediately. There is no Save button: the bar is on
screen behind the page, and a preview that lies about the result is worse than no preview at all.

The tab's own page reads the same resolution: `dockSlotTarget(repo, kind)` resolves the ids against
the library once, and the caption, the header, the list and the empty states all read that one value,
so a tab can never be labelled with a subscription it no longer shows.

## 5. The fourth tab's three surfaces

* **Pulse** — status word, uptime, live down/up, the remembered ping of the running route, jitter and
  the engine's own quality score when it has one (`repo.liveRouteScore >= 0`; an unmeasured route
  says so instead of printing a zero). Under it: rank all, ping all, refresh all, Bug Finder,
  privacy audit and IP details — every one of them a real engine entry point, and the running sweep
  reported by the Servers page's own progress strip so the control that started a measurement stays
  the control that can cancel it.
* **Source** — the source's own header (name, `Used`/`Expires` when the source is a subscription) and
  its scoped verbs: `testSource` and `smartRankSource` always, and `refreshLibrarySource` wherever
  there is something remote to fetch — the local **Manual** bucket is the one scope that cannot be
  refreshed, so it is the one that does not pretend to be. Then the servers in the user's own sort
  order. The user's *hide* filters are deliberately not applied: a page that promises "one
  subscription" has to show the whole subscription.
* **Config** — the pinned node, its wire badge and address, its remembered latency, and three
  honest verbs: `Use this config` (a real `selectProfile`, the same call the Servers list makes),
  `Connect` through the app's own connect path, and `Ping`, which measures that exact node with the
  same engine as every other ping. A node deleted from the library leaves an empty target the page
  explains, with the way back into Settings.

## 6. The Home banner, slimmer in all four themes

`IosStatusWideCard` is one shared card — the four Home presentations call the same function — so the
banner's weight was paid by every theme four times over. Its old anatomy was three stacked slots with
three fixed floors (32 / 40 / 32 dp), a divider between each pair, 16 dp of vertical padding and a
40 dp flag tile: ~139 dp of the top of every Home page, repeating five facts.

`MARBLE_HOME_COMPACT_BANNER_V167` rebuilds it as two rows and a hairline — 139 dp down to ~73 dp:

* **Row 1** — the status dot (13 dp now, not 18), the state word and the session uptime on one
  20 dp line;
* **Row 2** — the flag tile (28 dp), the node's name, and on a second monospace line the exit IP with
  its country code, the wire scheme and the source; the ping readout keeps its own reserved width at
  the trailing edge so a measurement landing in it can never re-flow the row;
* the whole identity row opens the full IP report (the strip it used to be is gone, and the glyph is
  now an affordance rather than the only target), with the copy action still one tap away.

Nothing that was fixed-height became conditional: no strip appears or disappears, so coming up still
never pushes the connect control down the page. Only the vocabulary is smaller.

## 7. Tests

`app/src/test/java/com/marbleng/app/model/DockSlotV167Test.kt` pins the pure contract:

* every kind and every glyph round-trips through its stored id, and the retired draft vocabulary
  still names the slot it named;
* `DockSlotKind.DEFAULT` is `PULSE`, so a fresh install's fourth slot is never blank;
* the bar is three tabs when the slot is off, and the three shipped tabs keep their order;
* `dockSlotIndex` repairs a remembered `CUSTOM` into a bar that no longer carries it, and never
  returns a negative index;
* the caption is the user's own words, trimmed and bounded; the default caption names the target —
  and a pulse is never named after a source it does not open.

`scripts/system-integrity-check.py` gained four structural checks: the four-theme banner (one card,
five call sites, no third strip, the compact status line), the slot being a preference (pure list
helpers, the screen-repaired `when`, the skipped hidden slot), the personalization being persisted
model → store → page, and this chapter plus the named test.
