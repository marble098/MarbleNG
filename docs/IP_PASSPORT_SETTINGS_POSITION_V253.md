# IP Passport, Settings Position & Calm Defaults — V253

`MARBLE_IP_PASSPORT_V253` · `MARBLE_SETTINGS_POSITION_V253` · `MARBLE_CALM_DEFAULT_TEXT_V253` ·
`MARBLE_SERVERS_ATLAS_V253`

This chapter records one pass with four jobs, all of them answers to reader complaints rather
than new machinery.

## ۱. The IP tab becomes a passport page

The V251 page was an instrument: a morphing exit card, orbiting beads, family gauges, a per-interface
ledger with scope badges, a verdict essay and a tools list. Every value on it was true and almost
none of it was what a person opens the tab to learn.

`IpIdentityScreen` is redrawn as a passport, in the order the question is asked:

1. **The flag, large.** The exit country's flag is drawn at passport size by the app's own vector
   flag art (`drawCountryFlag`) in a soft-shadowed panel, with the place under it and the family and
   country-code as badges. An unknown country falls back to a tonal globe — never a wrong flag.
2. **The address the internet sees**, headline size, one tap to copy, with the two facts a support
   call repeats as pills: *through the tunnel* / *direct*, and *public*.
3. **The facts a leak-check site shows** — the browserleaks.com/ip set minus what a phone cannot
   answer: public IPv4, public IPv6, country, region, city, provider, ASN, time zone, network and
   resolvers. Every row appears only when the sweep actually measured it; a blank line is noise.
   Region and time zone were already in the `ipwho.is` answer and were being thrown away; the
   enrichment now keeps them (`IpEgress.region`, `IpEgress.timezone`).
4. **This device**, trimmed: the working addresses of the network underneath and the address the
   tunnel lends, capped at four rows. Loopback, link-local, multicast and documentation addresses
   are facts for `ip addr`, not for this page.
5. **One privacy verdict** — *your real IP is hidden / visible*, computed from the two observations
   (tunnel exit vs. direct), plus the two doors that act on it: the leak audit and IP privacy.

The page still sweeps on arrival in every state (`repo.refreshIpStack()`), and the arithmetic is
still `IpAddressBook`. What changed is the drawing: one hero, three quiet cards, nothing that moves
unless it has something new to say.

## ۲. Settings remembers where you stand

The complaint: filter the hub to a section, open one title, come back — and the app has dropped you
at the top of "All". The hub owned its search, its category chip and its scroll inside its own
composable, and any navigation (a detail page, a sibling topic, a tab detour) threw all three away.

`SettingsHubState` now lives in the root shell (`rememberSettingsHubState()`), above the animated
depth switch: the section chip, the search text and the `LazyListState` survive every navigation and
return the reader to the exact tile they left. The taxonomy is re-ordered to the journey a VPN is
set up in — *Connection & core* first (it owns the featured tile), then routing, measurement, DNS,
appearance, data, and maintenance last — and the twelve-expert network check moved out of the
crowded "Ping & smart auto" hub into "System & diagnostics" where the fix-it tools live. The
confusing "Appearance & system" chip is now "App & system".

Filler copy is gone: the hub's "Tap a row to change it" hint, and the essay-length footers on the
motion, Home layout, notification and network-check pages. The tiles speak for themselves.

## ۳. Servers: the details that were wrong

- **RTL flag clusters** fan out from the reading edge now. The overlapping stack used a fixed
  left-to-right offset and spilled past its card in a Persian interface.
- **"Reset Filters" now means the same thing everywhere.** The empty-state reset forgot the sort
  direction that the chip-strip reset cleared.
- **Pull-to-refresh answers on a manual-only library.** There were no subscriptions to refetch, so
  the gesture did nothing; it now re-measures the flag sweep instead.
- **The quick-actions sheet isolates the host** in bidi text (`\u2068host\u2069`), the same rule the
  row already followed — an IPv4 literal inside a Persian sentence reads backwards otherwise.

## ۴. The quiet default

`TextScale.DEFAULT_PERCENT` is now the smallest rung (80 %). A fresh install — and any install that
never touched the slider — opens on the quietest, most compact interface the app can draw, and
grows the text only if the reader asks. Stored sizes are clamped exactly as before, so nothing
migrates.

## What was deliberately not done

- No new settings pages, no new preferences, no new chapters of machinery — this pass removes.
- The V251 arithmetic (`IpAddressBook`, `IpEchoProbe`) is untouched; the tests keep proving it.
- Historical chapters keep their historical claims; this file is the correction, not an edit of
  the past.

`docs/EXPRESSIVE_HOME_AND_SETTINGS_V252.md` is the chapter before this one.
