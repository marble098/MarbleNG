# MarbleNG

<!-- MARBLENG_TELEGRAM_COMMUNITY_V46:START -->
<div align="center">

## 💎 Welcome to the MarbleNG Community

**🐞 Found a bug? &nbsp; 💡 Have a suggestion? &nbsp; 🚀 Want to help MarbleNG improve?**

The official Telegram channel is the **fastest and best place** to report problems, share ideas, follow important updates, and help shape the future of MarbleNG.

<a href="https://t.me/MarbleNG">
  <img src="https://img.shields.io/badge/Telegram-Join_%40MarbleNG-26A5E4?style=for-the-badge&logo=telegram&logoColor=white" alt="Join @MarbleNG on Telegram">
</a>

### ✨ [Join @MarbleNG on Telegram](https://t.me/MarbleNG) ✨

<sub>📣 Bug reports • Feature requests • Suggestions • News • Release updates</sub>

</div>

<!-- MARBLENG_TELEGRAM_COMMUNITY_V46:END -->

---

**MarbleNG is a modern Android network client built around Xray-core, Android `VpnService`, and `hev-socks5-tunnel`.**

It focuses on real proxy verification, fast one-tap connection, fail-closed routing, adaptive network intelligence, and a clean Compose interface.

## Highlights

- Always remembers the **last successfully connected node**.
- One-tap Home reconnect after app restart or process death.
- Full-device Android TUN mode or local SOCKS proxy mode.
- Fast TCP reachability tests and real Xray tunnel verification.
- Smart ranking for the whole Servers list or one selected group.
- Navy/ice/electric-blue brand identity shared by the Solid White, Dark, and System themes.
- Font-independent Canvas vector icons for critical actions.
- Smart GitHub Release update checks.
- Signed multi-ABI APKs built by GitHub Actions.
- Remembers the last ping of every server across restarts, updates and process death.
- A **ping speed dial**: the default sweeps about 50 % faster across all three methods, and a slider lets you choose slower or faster — width and pacing only, never accuracy.
- A **fourth tab you fill yourself** — a subscription, one config, or the live pulse of the route.
- **Material 3 Expressive motion** — stretching loaders, cascading arrivals, rolling readouts and springy releases on every control.
- **A living, layered surface** — a slowly breathing brand aurora behind every page, and one cool-shadow depth system above it, so nothing floats on a dead flat field.
- **A Servers page that shows its hierarchy** — subscription cards with a real usage bar, and their servers nested inside them, smaller in every dimension.
- **A Servers page that starts with servers** — one search line and one filter sheet instead of four chip rows, a pinned "active route" card, collapsible groups with a single overflow, 68dp rows that say `VLESS · TLS · XHTTP` and a four-level ping, delete by swipe with undo, and an extended FAB that folds away while the list moves.
- **A twelve-expert network council in Settings** — one scan reads the link, the operator, ping, TCP, UDP, DNS, ports, MTU and both address families, then applies the settings it can defend on both cores — with a dissent line for every seat that lost the argument.

## The expressive shell: one control, one decision

`docs/SERVERS_DECK_V243.md` is the newest chapter.
`docs/CRASH_AND_SERVER_ROWS_V242.md` is the chapter before it.
`docs/EXPRESSIVE_SHELL_V241.md` records the expressive shell before that.

**V243 — the Servers page starts with servers.** The report counted four filter rows (search,
sort, groups, protocols) before the first server, one address printed four times, a clipped red
`Delete` label glued to the ping, a FAB covering the last card, and three tiny green words where the
group's actions belonged. The page is now: a title line with two actions, a single search line whose
filter action opens a sheet, the chip row only when a filter really narrows something, a pinned
active-route card while a tunnel is up, collapsible group headers with one 48dp overflow, and 68dp
rows — protocol chip, name, `TLS · XHTTP`, a reserved 88dp ping column with four semantic bars, and
one overflow target. Deletion is a gesture with a five-second undo window, and the list reserves the
FAB's height so nothing is ever covered. `ServersDeck` holds the arithmetic (row ceiling, the
150/300ms ping lines, the transport line, the selection roster, the undo window) and
`ServersDeckV243Test` pins it on the JVM.

**V242 crash and server-row pass.** The connect pill no longer lets an expressive spring overshoot
into negative padding: the inset uses a non-overshooting effects spring plus a finite layout clamp,
with JVM and real-Compose frame regressions. Server rows now keep a stable two-line grammar —
avatar, ellipsized name, 48dp actions, protocol/address and a reserved ping capsule — with
profile-keyed swipe state, RTL-safe addresses and scrollable action sheets.

**Home stopped asking a question it should answer.** The audit counted five controls for one
action — a power orb, a slide-to-connect track and three deck cards — so the screen now has one
**pill** that squishes when pressed and grows into its connected shape, one **menu chip** that
names the mode it is in (Smart, Full TUN, Local proxy) and one **status chip** that opens the
live core log. The slide track and the deck are gone from the screen; their catalogues and
tests stay as the record of how the mirrored gesture and the three behaviours were proven.

**The servers page breathes.** Rows carry a calm health capsule (mint / amber / muted grey)
instead of a coloured field, sorting is a chip row (`Lowest ping`, `Best score`, `Name`), the
trash icon is gone — a swipe with a haptic confirmation is the affordance — and the FAB leaves
the screen while the list is moving instead of sitting on the last row.

**Surfaces, not outlines.** `MarbleCard` has no border any more and `MarbleDivider` is air: the
whole Settings page lost its grey ruling and its box-inside-a-box rhythm. Corners sit at 24–32 dp,
display titles collapse into the toolbar as the page scrolls, and the floating dock is a tonal
object with one soft shadow that slides away while you read.

**Live telemetry instead of four dead strings.** A one-minute traffic window is drawn as a
sparkline against its own peak (so a slow link and a fast link both fill the height) and a tap
cycles down+up, down and up. The core log is one tap from Home, reading only the tail of one file
every 1.5 s. And the tuning knobs behind Smart Jitter and the Censorship Lab are now three named
presets — **Gaming**, **Streaming**, **Extreme Bypass** — that never touch routing, DNS, the
split tunnel or the selected server.

## The surface, the icons, and the whole window

`docs/AURORA_SURFACE_V219.md` is the newest chapter.

**The app is the window.** The activity now enters true edge-to-edge, both bars are transparent in
every theme tier, and Compose owns every inset in one pass each: the page stack pads the status bar,
the floating dock pads the gesture bar, the editors pad the keyboard. A page backdrop paints under
the clock and under the home indicator, and one 168 dp shelf gives the dock a floor, so the bottom of
the phone belongs to the app instead of to a system strip.

**One glyph grid replaced the Settings doodles.** Every point of every icon is placed on a 24-unit
grid with one stroke rule — 1.75 units, round-capped, rendering between 1.3 dp and 2.1 dp across the
product's icon sizes — plus filled accents for a duotone read. Glyphs mount on `MarbleGlyphTile`: the
card fill, the tone's own gradient, a specular wash and a lit rim. The six Settings groups, every
section card, every row preview, the back control, the disclosures and all three navigation tabs
speak that vocabulary; the fourth tab keeps its own glyph choice.

**Cards catch light and lines fade.** Every card wears one specular plane at its top edge on top of
its wash, its rim is white-lit at the top on a light page, the corner ramp moved onto the platform's
24/20/16 steps, dividers fade out toward their trailing edge, and the 40 dp tone circles behind row
icons became squircle containers. The mark itself is drawn now — a lit tile, one orbit and one folded
"M" with a translucent facet — in the Home header, the custom page and all three launcher icons.

**The drag-to-connect bar was rebuilt.** A tinted well with a lit rim, a gradient energy bed that
grows under the thumb instead of snapping to it, three chevrons that fade as you approach, and a lit
sphere for the knob. The gesture and its commit ordering are unchanged: only the material is new.

**The small surfaces were rebuilt with it.** One shared row skin (`marbleLitSurface`) replaces three
hand-written modifiers on every switch tile, stepper, information row and flag chip; buttons are
gradient-lit objects rather than flat accent rectangles; the dock is milled, with a lit active pill;
bottom sheets open on a lit grabber instead of a grey capsule; and the status-bar, Quick Settings and
themed launcher icons are all the same prism, one silhouette each.

## Corners that close, type that fits

`docs/VISUAL_POLISH_V218.md` is the previous chapter.

**A subscription card now closes through every corner instead of snapping across them.** One
animated fraction drives the header's clip, its outline's bottom edge, its padding and its ripple
bound together, so a collapsing sub morphs from the open 16 dp top-only silhouette to the closed
16 dp card — the same radius the outline has always drawn, at every frame of the morph. The old
code jumped from a 20 dp clip onto a 16 dp border in a single frame, and the corners visibly
fought each other for the whole collapse.

**The type ramp came down a step and grew a weight ladder.** Page headlines dropped 28 → 24 sp,
status titles 24 → 21 sp, section headings 20 → 18 sp, and every size tier now owns one dedicated
weight — Display Bold, headlines SemiBold, titles stepping down to Medium, labels Medium — all of
them faces Vazirmatn actually ships. The five `FontWeight.Black` overrides that made the platform
synthesise a smeared faux-bold are gone.

**The settings disclosures move on the product's own motion.** The stock `AnimatedVisibility`
springs in the engine, notification, routing and speed-test sections were replaced with the same
emphasized expand/collapse pair the Settings hub already used, so every disclosure in the app
opens on one curve.

## Servers you can read at a glance

`docs/SERVERS_HIERARCHY_V189.md` is the newest chapter.

**A subscription is a container, and its servers live inside it.** The page used to be a stack of
visually equal rows: a subscription header and its servers shared one outline, one type scale and
one tile size, so nothing but their order said which servers belonged to which plan. The page has
two levels now, and the gap between them is measurable — 16 dp corners and a 1.5 dp outline for
the card, 12 dp corners, no outline and an 8 dp inset for the list inside it; a 16 sp
subscription name over 13 sp server names; a 30 dp protocol tile where a standalone server still
gets 40 dp. Rows are separated by hairlines, never by borders, and the only row that grows an
outline is the one carrying traffic.

**A plan is a bar, not a sentence.** Each subscription shows its used/total figures and, under
them, a real progress bar whose colour is the plan's own state — green to 70 %, amber to 90 %, red
past it — with the percent beside it. Expiry, the provider's website and the auto-update state
stepped down into one quiet secondary line, because they are read once and then ignored.

**Counts live in the controls.** The header's `2 groups • 48 servers` line is gone: the group
capsule badges how many groups the page holds, the protocol capsule badges how many servers the
current scope shows, and the advanced-filter button badges how many of its own switches are on.
The whole rail dropped a step (38 → 32 dp) to pay for it.

**Failure is quiet.** A server a probe could not reach fades and shows a cross in a muted tone;
red stays for a measured latency that is genuinely bad. One table
(`MarbleServersHierarchy.kt`) owns the sizes and the plan arithmetic, and
`ServersHierarchyV189Test` pins both, so a nested row can never quietly grow back to the size of
the card that holds it.

## A face for every server type, and a calmer Settings

`docs/PROTOCOL_IDENTITY_V188.md` is the chapter before it.

**Every wire scheme now has its own minimal identity.** VLESS ripples, VMESS sends an envelope,
Trojan hides behind a shield, Shadowsocks wears the sock, Hysteria2 pushes twin chevrons,
WireGuard carries a key, SSH keeps a prompt, SOCKS a tunnel ring and HTTP a request/reply swap —
each on a circular tile with its own tone, the country flag riding the rim, and a tiny badge
under the name. Both server lists (Servers page and Home) share that one anatomy: type tile,
name, endpoint, and the latency in its own right-aligned stat column with a three-bar quality
meter — no longer a lone number floating in the middle of a row.

**Settings answers each question once.** The four doors that used to ask "which core?" (Engine
& tunnel, Tunnel core, Xray core settings, sing-box extended) are one door now, badged with the
running core's name; the duplicate *General & servers* row and the duplicated *Fourth tab* card
are gone, with nothing lost — the engine page owns the switch, the pinned versions and the
delay test. And the bottom dock no longer fades when you tap a tab: glass is reserved for real
scrolling.

## Motion that feels like the newest Android

`docs/SMOOTH_MOTION_DEDUP_AND_FLAG_IDENTITY_V193.md` is the newest chapter: the ambient clock now
caps itself at 60 fps (and feeds the full-screen backdrop a 15 Hz coarse twin), every per-frame
read moved into the draw phase, and servers marquee three passes instead of forever — so
animations and transitions stay smooth while the room behind them breathes.

`docs/EXPRESSIVE_MOTION_V186.md` is the motion-language chapter.

**Every movement in MarbleNG now speaks Material 3 Expressive**, the motion language shipping on
current Android — built entirely from primitives every supported device has, so minSdk stays 26
and no behavior changes. One new library (`MarbleExpressive.kt`) holds the emphasized curves,
the duration ladder, the release springs and the pure, unit-tested wave math; the whole product
was rewired through it:

- **Loaders stretch.** Every spinner and progress bar — the busy top bar, the probe strip, the
  securing rings of all five connect silhouettes, refresh and testing spinners down to 11 dp —
  is now a wavy indicator whose arcs grow long and snap short on the shared frame clock, the
  signature rhythm of the newest Android.
- **Screens arrive in cascades.** All four Home themes, the Servers library, the Settings hub,
  the custom dock page and the permission dialog stagger their content in one 45 ms step apart,
  through a self-disarming entrance window: a screen arrives once, and scrolling or filtering
  never replays the arrival.
- **Numbers roll.** The home status word, the ping badge, the live meter headline and the metric
  cards exchange values on the emphasized pair — the new value decelerates up into the fixed
  slot while the old one accelerates away. Nothing around a readout ever moves.
- **Releases spring.** Every press lets go with one visible overshoot; a short drag on either
  slide-to-connect control squishes the knob against the track wall before it settles; buttons
  morph their corner radius while pressed; selections and session flips pop once as
  acknowledgement.
- **Navigation has depth.** Pages parallax and shrink inside the pager, Home → connection
  detail runs a container transform, and Settings sub-pages ride the shared X axis.
- **A protected tunnel reads as alive.** The status pip emits a soft double ring every 2.6
  seconds — strictly inside its own 13 dp slot.

The guarantees survive the elastic surface: the V135 opacity-only ping reveal, the compact
banner geometry, the slide thresholds and park behavior, deterministic metric bands, and full
respect for the system "remove animations" setting — with motion off, every spinner, ring and
cascade rests at its exact target value.

## A page with air and light in it

`docs/AURORA_AND_DEPTH_V191.md` is the newest chapter.

**The interface stopped being a flat field.** Every page now sits on a calm brand *aurora* — the
quiet cloud gradient with three large soft radial glows over it (electric blue off one top
corner, bright ice off the other, a deep navy pool at the floor), breathing so slowly it reads as
atmosphere rather than animation, frozen completely when animations are off. Above it, every
raised surface rejoined the physical world with one rule: **one cool brand-tinted shadow, never a
grey one** — navy-cast in Light, an electric glow lift on AMOLED. Home cards lift 3 dp (the
selected route 7, animated on the spring), Settings hub groups carry their section's tone as a
lit wash down the card, subscription headers on the Servers page glow faintly at their top edge,
and the dock's selected pill finally reads as selected at a glance — with the light-theme glass
bug (the bar vanishing mid-scroll) fixed in the same breath. The PrismPanel glow, tuned in some
earlier life to a maximum of 3.6 % alpha, is now actually visible.

## The Material refresh

`docs/MATERIAL_REFRESH_V185.md` is the previous chapter.

**MarbleNG now wears the newest Google Material language.** The type ramp follows the Material 3
scale with unambiguous steps between display, headline, title and body; cards, containers and
borders move to Material You's calm surface-container finish (cool near-white light surfaces,
navy-lifted steps over AMOLED black); every shape rounds one step up and touch targets meet the
Material baseline; and the whole Canvas icon family strokes at the Material Symbols weight,
larger in every slot — including a taller, rounder floating dock with bigger glyphs. minSdk
stays 26 and no behavior changes: this is the design system only.

## A personal fourth tab, and a calmer Home

`docs/DOCK_SLOT_AND_COMPACT_BANNER_V167.md` is the previous chapter.

**The bottom bar has four slots now, and the fourth one belongs to the user.** Choose the live
pulse, one subscription, or one config; then tune its target, caption, glyph, independent accent, and
live-connection badge from Settings → **Fourth tab**. The preview updates with those choices, and
turning the slot off restores the exact three-tab bar without leaving a dangling page.

**The Home status card now has a clear hierarchy:** a compact connection-state line, one route row,
and an optional two-cell live-telemetry grid for download and upload. The grid unfolds only
when the route is connected and the existing speed-display setting is enabled; the floating speed
card is gone, so it no longer competes with the server identity. All Home cards use a flat fill and
hairline instead of box shadows. In Theme 1, the server list owns the flexible, scrollable center lane
and the horizontal slide-to-connect control stays anchored at the page floor.

## Speed and memory of a measurement

`docs/PING_SPEED_AND_REMEMBERED_PING_V160.md` is the previous chapter.

**Real delay is 30–60 % faster with the same number, the same sample budget and the same
accuracy.** It used to open a fresh SOCKS connection and run a fresh TLS handshake *per sample*,
so a three-sample ping paid the whole cold start of the route three times over. Every sample now
runs on the session the first one opened — one handshake, then one real request and one real
response per sample — which is what the Rank sweep has done since V156 and what v2rayNG calls
"attempt two reuses the same verified session when the origin permits keep-alive". The quiet gap
between samples moved into the batch so the samples are never a burst an adaptive filter can
learn, the cold sample is still discarded, and the Home ping button now publishes the same number
Rank does.

**URL test is faster too, and the pool that carries it is sized by the device.** The startup loop
that waits for the core napped a flat 60 ms between readiness probes — up to a minute of pure
idleness over a hundred-node sweep — and now ramps 4 → 8 → 16 → 25 ms. The measurement pool is no
longer a constant four: `MeasurementCoreBudget` grants six or eight slots only on devices that
report the cores and the heap to carry them, and a small device keeps the four it has always run.

**The measurement is no longer the one thing the product forgets.** Every result is written down
when it lands and read back on the next launch, bounded at 400 rows, pruned after 30 days and
dropped when its node is deleted — so leaving the app and coming back no longer shows a Servers
list with no latency anywhere.

**Fresh installs open on Theme 2 (Floating) with Google Sans**, and Settings names the tunnel core
next to its own title. All three are first-launch or display-only: an install that already chose a
presentation or a typeface keeps its choice, because the store asks whether a value was ever
written instead of writing the default back the first time it is read.

`docs/SINGBOX_STARTUP_GATE_V162.md` is the newest chapter: a connect on the sing-box engine ended
in `core-start-timeout` after 12 seconds with the kill switch held, and the only line in the core's
log was the benign Android package-list warning every `GOOS=android` core prints. The process had
not died — MarbleNG killed it, because "the core is up" was defined as *the local inbound **and**
the Clash controller*, and the controller is an internal service this core binds in its **last**
start-up stage, after every outbound's post-start walk. So the app waited for the entire core
start-up before it would carry a byte of traffic, and it could not tell the two halves apart in a
report: a core that never opened its inbound (a core this device cannot run) and a core whose
tunnel was up and whose controller never answered (a working route with a missing measurement
surface) printed the same sentence — which also handed the user V157's "update MarbleNG" paragraph
for a fault that has nothing to do with that crash. The wait is phased now: the inbound is the
gate, the controller is a bounded 2.5 s phase whose absence is *reported, not fatal*, `sing-box
check` no longer spends the window the child starts in, and `start-result` prints `inbound=` and
`controller=` separately so the next device's report can be read without re-deriving the stage
table.

`docs/PING_FALSE_FAILED_V159.md` is the previous chapter: URL test and Real delay both worked, yet
occasionally published a perfectly healthy server as `FAILED`. Two budget defects, not routing
defects: the URL test shared **one deadline across its fallback targets**, so the coldest request
of a fresh core (the first, which pays DNS + TCP + TLS + fetch on a ~1 s route) consumed the whole
timeout and left the secondary/CDN fallbacks — warm by then, and built for exactly that moment —
with a budget of zero; and Real delay's two Home paths (live tunnel, and the throwaway tunnel the
disconnected ping builds) fetched **one** origin while the Rank sweep already walked every
`DelayTest` candidate, so one moment of SNI-throttling failed every sample of a perfect route.
`ProbeTargetWalk` now owns one shared walk — every candidate target gets the full budget, the
first answer wins, at most three targets, interruptible between them — and a URL-test throwaway
core that never came up is retried once, because a spawn storm is a fact about the device, not a
verdict about the node.

`docs/SINGBOX_PORT_SOVEREIGNTY_V158.md` is the previous chapter: one device spent eight minutes
refused with `bind: address already in use` on `127.0.0.1:10808` while **both engines reported
`alive=false`** — the socket was held by a core process nothing tracked (an orphan from an earlier
app process Android had killed), nothing on the connect path ever asked the OS who held the port,
and the refusal wore the `core-start:` prefix, so every reader candidate was spawned to die on the
same port before the user was told the profile was "misconfigured". MarbleNG now attributes the
holder through `/proc/net/tcp{,6}` + `/proc/<pid>/fd` and reaps same-UID core binaries
(TERM → KILL) before either engine spawns, names and spares foreign holders, classifies a bind
conflict as the `core-port:` local fault it is (one spawn, no reader walk, `Local port in use` in
the blocked state), reports the exit verdict of every core `stop()`, fixes the wire defect that
let MarbleNG's own domain-target SOCKS5 requests arrive without RFC 1928's length prefix (each
request is now one assembled, single-`write` segment), and counts the
`read fqdn: unexpected EOF` ERROR lines of local clients aborting their own SOCKS handshake as
benign evidence instead of Bug Finder failures.

`docs/SINGBOX_ANDROID_CLI_CRASH_V157.md` is the previous chapter: three shipped builds carried an
Android sing-box artifact that panicked in its own `direct` outbound for **every** profile, because
an app-UID child gets no netlink interface monitor on Android. The product answered in four
remote-sounding vocabularies — `reachable = 0 of 17` for both the URL test and Real delay, a BLOCKED
`Core/configuration error`, and a Bug Finder scan that reported `failures=0` because every core check
it runs is gated on a connection a crashing core never establishes. MarbleNG now compiles the core
from the pinned commit with the upstream nil-guards backported
(`scripts/inject-singbox-android-fix.py`, proven by Go tests in CI before a single ABI is built), asks
the binary whether it can start before it asks any server (`SingBoxCoreSelfTest`), stops a sweep at the
first fault that cannot change mid-sweep (`ProbeLocalFaultGate`), and reports a crashed core as a
crashed core in every app state.

`docs/ENGINE_SELF_HEAL_AND_PING_AIR_V152.md` is the previous sing-box chapter: the shipped 8.0.6 build
could not connect at all on the sing-box engine because the emitted config still carried the
`dns` outbound sing-box removed in 1.13.0 — every profile was refused, the session went BLOCKED,
and failover replayed the same refusal seventeen times. The outbound is gone, the config is
checked by a **self-healing doctor** (`SingBoxConfigDoctor`) that repairs the known core
migrations in place, and an engine-level fault now stops the walk at once instead of replaying it
per candidate: failover to another *node* is pointless when the engine itself refused, and choosing
the other engine stays the user's explicit act in Settings → Tunnel core.

`docs/CONNECTION_ROOT_CAUSE_V132.md` documents the full trace of the connect path and the five
defects found in it, including the two that made a healthy server fail in MarbleNG while it
connected and pinged in another client:

- a background routing-data refresh could veto every connection for minutes at a time;
- the node hostname was resolved by two `https+local` bootstrap literals and nothing else, so two
  filtered resolver IPs made an otherwise working server undialable.

`docs/CORE_MALFUNCTION_ROOT_CAUSE_V133.md` covers the next layer down: a session in which the core
started cleanly and never crashed, while encrypted DNS and route health failed continuously. Every
deadline and threshold in the stack was a constant sized for a fast link, so on a ~1.1 s route each
DoH query expired before the resolver could answer, and the failures then propagated into the
acceleration engine — a 30-minute backoff that could neither decay nor be released, jitter control
that oscillated, a per-socket MSS treated as a path MTU, and an IPv6 verdict that never reached the
emitted config.

`docs/RESOLVER_EVIDENCE_AND_MEASUREMENT_PLANE_V134.md` closes the resolver feedback loop: failures
the core logs are attributed to the endpoint it named, demote it in the emitted resolver order for
the current physical network, and arm parallel query only on that evidence.

`docs/ROOT_CAUSE_V135.md` covers the IPv6/memory/socket triangle: a Wi-Fi network with no global
IPv6 still received an unreachable IPv6 resolver graph and a captured `::/0` route, paid 8 s per
dead resolver on the fragment chain, and re-dialled six times in two minutes with zero pacing
until the OS revoked the VPN permission. The historical fixes purged resolver families the
underlay could not dial, gated IPv6 route capture, sized fragment-chain DNS budgets from the
measured link, and paced automatic recovery. **The route-capture policy was superseded:** Full
TUN now always captures both IP families (then rejects a disabled family inside the core), so an
uncaptured `::/0` cannot bypass the VPN. The current family/DNS security contract and device
verification steps are in [IPv6 and leak hardening](docs/IPV6_LEAK_HARDENING.md).

`docs/IP_FAMILY_SCAN_V196.md` made Marble IPv6-first in practice rather than in intent. Force IPv6 used to be a gate: on a Wi-Fi with no IPv6 route and a
library of mostly IPv4-only servers, every connect attempt was refused before the tunnel started
and the kill switch held the old TUN, so the screen read `BLOCKED • Kill switch active` on server
after server. A transport preference that produces an outage is a defect, so it now walks a
ladder — **IPv6 strict → IPv6 first → IPv4 dial with IPv6 kept for destinations → an honest
refusal that names the alternative** — decided once, inside the one function the tunnel, the delay
test, the ranking pool and every prober share. The ladder measures the fact it needs itself:
encrypted DoH for A/AAAA, then a real bounded TCP connect per family, cached per endpoint *and*
per physical network (the manual scan buttons retired in V220; the measurement stayed). The same
chapter covers `DnsDomainFaultPolicy`: two independent healthy resolvers timing out on one name is
a fact about that name, not a resolver storm, and it no longer demotes providers or arms parallel
racing.

`docs/IPV6_TRUTH_LOCATION_AND_IME_V197.md` came from one logcat. A
dual-stack server was being reported IPv4-only because a DoH race picked its winner before reading
the answer, so one empty answer section deleted a whole family; the resolver now asks A and AAAA in
parallel and keeps every independent witness, and the scan refuses to believe absence from a single
silent answer or a single lost IPv6 packet. Adding a server or a subscription now measures **both**
of the things a row claims — where the server is and which families it answers on — in the
background: the location pool grew from three keyless providers to five, and a lone uncontradicted
answer is shown instead of discarded, marked provisional so the next sweep promotes it to a quorum.
The same chapter fixes the hundred `InsetsController.hide(ime())` calls in a log with no keyboard
ever open (a surface that only looks like a text field was being handed an input session, and no
field declared the event that ends one), and turns the probe's reactive target pivot into a race
that hedges from the first sample of a cycle and lets a freshly cleared window earn its score back.

Four chapters landed in that pass, each from one report:

`docs/SETTINGS_ONE_LINE_COPY_V208.md` — every settings description is now **one sentence**. Twenty-nine
rows carried paragraphs that explained the feature behind the switch and pushed the switch itself
below the fold; the copy was rewritten and the rule is enforced where the text is drawn
(`MarbleCopy.oneSentence`), so a new row inherits it.

`docs/HOME_ONE_PING_V208.md` — the home header's box no longer blinks when a ping starts (its
presence was `message.isNotBlank()`, and a sweep rewrites the message several times a second, so the
bar left composition and replayed its spring entrance), and there is **one** ping button again: it
pings the current group, shows no latency number, and every other ping surface on the page is a
display.

`docs/SERVER_TILE_LAYOUT_V208.md` — servers can be read as **compact boxes** on the Servers page and
in the Home server box, two-up on a phone and four-up on a tablet, or as the rows they have always
been; Settings › General › Server cards chooses.

### The V210 pass

Four chapters landed in the pass after them, all four from the same review of the connection page
and the settings page:

`docs/SERVER_TILE_TRUTH_V210.md` — a compact server box now answers the only question a grid is
for: a server that was measured and did not answer **fades** (the whole box, not just its label),
while a server nobody has measured keeps its full weight, because no measurement is not a verdict.
The second half is a layout rule: the name slot is one line, always, an overflowing name travels
through it, and every box takes its row's height — so a 60-character subscription label can no
longer make one tile taller than the three beside it.

`docs/HOME_ROUTE_PING_V210.md` — the Home ping buttons ask about the **server on screen**. V208
declared the page's question to be "how good is my subscription?"; this chapter keeps the *count*
of ping controls it established and reverses the *scope*, because a pulse sitting on the status box
of a page that is showing one named server is being asked about that server. The header's pulse
runs the route verb, and the floating control gets its ping half back next to the pause, both of
them the same measurement: through the tunnel while it is up, at the endpoint while it is down.

`docs/FLOATING_ACTIONS_V210.md` — the chrome around a floating button was theme-aware; its face
was not, and in the wallpaper palette the stop button was a hard-coded brand red. Every theme now
owns four action tokens — connect, securing, stop, measure — the glyph on each is scored from the
same theme's ink pair, and "no two of the four are the same colour, in any theme" is a unit test.

`docs/SETTINGS_SECTIONS_V210.md` — Settings is a **tree**. Six main sections, each closable from
its own header, each holding named sub-sections as insets inside it: Appearance owns Theme,
Navigation and Language & text; Connection owns Engine, Routing and Automatic connect. Every row
the flat page had is still there, plus an *Expand all* / *Collapse all* control in the header, and
a section nobody has touched is open — so one added in a later release opens too.

### The V211 pass

`docs/CORE_OPTIONS_V211.md` — the Fragment section is **deleted**, not hidden. It changed nothing a
user could feel because three automatic layers (the DPI ladder, Iran Mode, the traffic learner)
rewrote its numbers on the way to the config builder: the value on screen was never the value on the
wire, so the control could not fail visibly. The page, the card, the ladders, the learner file, the
generated `fragment-direct`/`tls-fragment` dialers, the sing-box `record_fragment`/`fragment` keys
and every settings field behind them are gone. In their place: **the cores' own options**, written
once, in the core's own vocabulary — log, sniffing and dest-override order, inbounds and inbound
users, SOCKS UDP, Xray Mux.Cool (concurrency, XUDP concurrency, UDP/443) and sing-box multiplex
(protocol, max connections, min/max streams, padding), the full `sockopt` surface, routing strategy
and matcher, the five `policy` timeouts, sing-box's parser/unified-delay/cache/connect timeout — and
a JSON escape hatch that deep-merges anything the screen does not model, with a **total refusal**
(and a sentence) when the patch names a subsystem Marble owns. The last writer is the user: a value
left neutral stays absent so the automatic layers can fill it, and a value that is set survives
them. An *imported* document keeps whatever fragment chain it arrived with — the app no longer
writes one, but it still refuses to rewrite the user's own.

`docs/HIGH_JITTER_SHIELD_V206.md` is the chapter before them. Very high jitter was being answered with
a mean that one stalled packet destroys, a verdict that is a single bit, thresholds that ignore
what is normal for the link in front of us, and a response whose cost grew with the severity. The
shield replaces all four: dispersion is the interquartile range (robust to one packet, and — unlike
the median absolute deviation — not zero on a link that alternates between two paths), the verdict
is relative to a baseline the link earns while it is calm, the mitigation is one continuous level
that attacks fast, releases slowly and holds after an escalation, and what it spends on extra
probes is capped by a hard per-minute budget. The chapter also records the five defects found in
the first cut of the shield itself and what each one became.

### The V212 pass

Four chapters, all four from one review of the connection page, the servers grid and the routing
page:

`docs/HOME_PING_CONTROLS_V212.md` — the connection page's ping buttons were **two controls and one
verb**, and the surface that was not the header did nothing when pressed. Every ping surface is a
control now, and each one's verb is a setting: Settings › Home › **Ping controls** points the latency
gauge, the shortcut pill and the header pulse at *measure this server*, *measure this subscription*,
*measure every server* or *open ping settings*. One mapping turns a stored verb into an action and
one function names it, so a control cannot say one thing and do another — which is what the V208
collapse was protecting against, and what a dead button did not deliver.

`docs/SERVER_TILE_PARITY_V212.md` — the compact server box was missing two things the row has: the
**measured address family** (`v4`/`v6`/`v4+v6`, and only when a scan answered) and the server's own
**menu**. The box takes both, through one shared menu and one shared colour, so the layout preference
chooses a silhouette and not a set of answers.

`docs/MULTI_SOURCE_ROUTING_V212.md` — routing reads a **set** of geo databases instead of one
chosen by replacement, and it can separate domestic traffic more finely than any geo tag allows.
The primary source keeps the canonical `geoip.dat`/`geosite.dat` so a fresh install still routes
from the bundled copy; every other source is addressed by its own file, which is what the core's
`ext:` loader is for; a token is emitted only for a file that is on disk; and
`IranPrecisionPack` — 104 curated domestic domains, the `.ir` TLD and 46 brand stems, deliberately
**no CIDR ranges** — is emitted last, so a rule the user wrote always outranks the product's
opinion. Preferences move to schema **v3**.

`docs/SURFACE_DEPTH_V212.md` — "the app looks dry" had a specific cause: the V191 depth contract
(one cool shadow, a lit rim, a whisper wash) lived *inside* the Home card, so every other surface
was still on the pre-V191 plane. It is now `Modifier.marbleSurfaceDepth`, the server grid wears it,
and the two full-screen dismissals answer Android's **predictive back** progress instead of snapping
when the gesture commits. The libraries were already the newest there are — Compose BOM
`2026.09.00` — so this chapter is about mechanisms, not dependencies.

### The V213 pass — the native chapter

One report, seven surfaces, and one verdict seven times over: **the product had invented its own
answer where Android already has one.** `docs/APPEARANCE_CRITIQUE_V213.md` is the critique — the
boxes were translucent films with accent rims instead of opaque tonal containers, the tab
indicator was a 22 dp rounded rectangle inside a 30 dp bar instead of a stadium, the server grid
was made of a different material than the page holding it, the flags were edge-to-edge stickers
with no frame of their own, the settings rows painted ~36 dp (under Android's 48 dp touch floor)
with a hand-coloured switch and an uppercased letterspaced heading, the choices were chips
pretending to be radios, and every notification field the platform expects — colour, subtext,
group, badge behaviour, a silent ongoing channel — was left empty.

`docs/NATIVE_ANDROID_V213.md` is the rebuild. One table owns the platform's measurements
(`MarbleNativeSpec`: 56/72/48 dp rows, a 40 dp leading container, 24/20/16 dp corners, a 2 dp
contact shadow, the 56×32 dp active indicator), and one module owns the platform's components —
`MarbleNativeCard`, `MarbleNativeListItem`, `MarbleNativeSwitch`, `MarbleNativeChoiceRow`,
`MarbleNativeSegmentedRow`, `MarbleNativeSectionHeader`, `MarbleNativeDivider`,
`MarbleNativeChevron` — which read **only** `MaterialTheme.colorScheme` roles and therefore follow
the brand palettes, AMOLED and a wallpaper palette alike. Every box in the product (panels, hub
cards, the subscription frame, the server tiles) now wears the same container fill, the same
neutral directional hairline and the platform's ambient + spot + contact shadow triple; the dock's
body and edge become the platform's container step and outline family, with a stadium indicator;
`CountryFlagCircle` can be framed, so a flag is a chip and an unmeasured location is the same chip
in another state; the option rows *are* the platform's list item, every switch in the product is
the platform's switch, chips are stadiums and option tiles rest on the platform's container,
sections speak in sentence case, the Home page's private eight-value surface table — and the
settings tone rail, which was never an Android control — are **deleted** rather than recoloured,
and the notifications fill the fields Android defines (accent colour, subtext carrying the state or the kind, group, local-only,
small-icon badge, a real action icon, and an ongoing channel that is finally silent). The manifest
hands the back gesture to the platform (`enableOnBackInvokedCallback`) and resize, not pan, to the
keyboard.

No new dependency, no second design system, no structural change: every V210/V211/V212 invariant —
the collapsed-keys policy, the server-box truth rules, one ping verb per surface, the depth
contract — is still enforced by its own check.

### The V231 pass — the notification app, reviewed by five experts

`docs/NOTIFICATION_COUNCIL_V231.md` is the chapter (Persian, as the council's record). V232 records the five-role Persian, bottom-dock and notification review in `docs/PERSIAN_DOCK_AND_SHADE_COUNCIL_V232.md`. V213 filled
the fields Android defines; **V231 made the notification an app instead of a helper function.** A
five-expert panel — UX research, product design, Android's notification platform, product/IA, and
accessibility/i18n — audited every notification surface with code as evidence, and the verdict was
structural: eleven call sites hand-joined `"STATE • route • metric"` into a string, so the shade
never knew which part was the state; the title always read `MarbleNG`; the privacy alarm sat on the
same default-importance channel as a subscription refresh (and `PRIORITY` is ignored above API 25, so
"important" was never important); `.setGroup` had no summary; the cooldown table and the id set were
unbounded maps keyed by session; ids were `hash % 1200` and could overwrite each other; every
notification shared one `PendingIntent` request code; and the settings page described a notification
it never showed while two of its switches (`notifyNetworkChanges`, `notifyCoreUpdates`) had no row —
and one of them had no producer at all.

The rebuild: `ConnectionNotificationState` with five states in both languages, so the ongoing item
now says **what is true** (title), **where** (text), **how it measures** (one bidi-isolated segment)
and **for how long** (Android's own `setUsesChronometer`, which costs zero notifications); a
signature gate so an unchanged status never reaches SystemUI and a changed one is posted at most once
a second; four channels with localized names (`Tunnel status` silent and badge-free, `Route alerts`,
`Privacy alarms` at `IMPORTANCE_HIGH`, `Updates`) re-created when the app language changes; a silent
group summary that counts what is waiting; a collision-free bounded id allocator and a TTL'd bounded
cooldown table; per-kind status-bar silhouettes; a `Manage` action that deep-links to the channel's
own Android screen and a `Reconnect` verb that goes through the same permission gate as the Quick
Tile; notification taps that land on the page that can act on the alert (privacy → Diagnostics,
subscription → Subscriptions) with a distinct request code per route; `notifyCoreUpdates` wired to
the update check; the post-update notice moved off the silent channel and given a Restore action; and
a settings page that renders the real notification from the real formatters, lists all four channels
with their live importance (`ON`/`LOUD`/`QUIET`/`MUTED`) and deep-links into each one. The copy half
lives in `core/NotificationCopy.kt` — no `Context`, so `NotificationCopyV231Test` can pin the bidi
isolation, the grapheme-safe ellipsis, the Persian dictionary (unknown copy stays English rather than
being transliterated) and the bounded collections on the host JVM.

### The V233 council — Persian everywhere, three real connect buttons, and a dock line that fits

`docs/PERSIAN_CONNECT_DOCK_COUNCIL_V233.md` is the chapter (Persian, like the council's record). The
same five-role panel reconvened for three complaints: **Persian was incomplete**, **the connection
buttons were one button wearing different clothes**, and **the active tab's little blue line sat in
the wrong place**. The findings were structural. `ProvideMarbleLanguage` lived inside `MarbleApp`
while `ConnectionPermissionDialog` was its sibling, so the permission dialog always fell back to the
static English `CompositionLocal`; 418 `trx("…")` literals and 72 repository messages had no lexicon
key at all, so V232's correct "never transliterate an unknown string" rule silently printed English;
and the Quick Settings tile lives outside Compose, so it had never seen a translation. The rebuild
moves language and text scale to the activity root (with an idempotent scale provider so a second
installation cannot square the factor), adds 564 friendly whole-sentence translations plus a
whole-string `FaFriendlyVoice` layer and a `" • "` segment translator that isolates live server
names with FSI/PDI, and puts every system-tile string behind the pure-Kotlin `QuickTileCopy`.

Three buttons now own three different repository entry points — `reconnectLastOrAuto`, the
auto-selector winner, and a local-SOCKS-only `auto()` — and three different lights (a breathing
ignition core, a sweeping orbit, and two travelling ripples), each drawn with plain Compose Canvas
and pinned by `ConnectDeckActionV233Test`. The dock line is no longer an unmeasured child of the
icon box pushed down by `offset(y = 10.dp)`: `DockIndicatorSpec` owns an 18×3dp line inside a 6dp
slot that **every** tab reserves, the compact bar grew 52→56dp, and `DockIndicatorSpecV233Test`
proves the content fits all three sizes, all four icon/label combinations and all seven text-scale
steps. A new integrity block gates lexicon completeness, so a future literal cannot quietly ship in
English again.

### The V234 pass — the shade switches the route, the connect control mirrors, the item is drawn

`docs/NOTIFICATION_SWITCH_AND_SHADE_V234.md` is the chapter (Persian, like the council's record). It
answers three sentences: *change the subscription and the server inside the notification itself*,
*when the app is Persian and right-to-left the connect button on Home must change too*, and *the
notification needs a new, modern look*.

**The shade now moves the tunnel.** V231's two route buttons only opened Servers and Subscriptions, so
changing a node meant unlocking the phone and crossing a screen. Both actions are now service verbs —
`ACTION_CYCLE_SERVER` / `ACTION_CYCLE_SUBSCRIPTION` reached with distinct `PendingIntent`s (a shared
request code would collapse the two verbs into one) — and the running foreground service rebuilds the
route and re-publishes the same item with the new name. The decision half is pure Kotlin:
`core/NotificationRouteCycle.kt` owns three rules that `NotificationRouteCycleV234Test` pins — a
*subscription with one server still moves* (it walks the library), a *library with one subscription
still moves* (the server verb is used), and a verb that cannot move anything answers with a line in
the same item instead of silence. A live subscription with eleven nodes stays inside itself and wraps;
the walk follows the library order, so two taps return to where it started. `subscriptionName` joined
`ConnectionNotificationState` (and its signature), because a user cycling sources has to see which
source is live: the second line reads `route · subscription`. A 1.5 s floor swallows the double tap
that would otherwise tear down the route it just built, and a stale item whose service has stopped
cannot resurrect a session.

**The Home connect control mirrors with the language.** `IosSlideConnectBar` was written in physical
pixels: the thumb moved with `IntOffset(+offsetPx, 0)`, the drag added `dragAmount` verbatim, and the
guide chevrons pointed right. In an RTL layout the thumb *rests* on the right edge, so every one of
those signs was wrong — the thumb left the track and the bar could not be used in Persian at all.
The three signs moved into `model/SlideConnectSpec.kt` (`dragFraction`, `thumbOffsetPx`,
`released`), `SlideConnectSpecV234Test` proves a full leftward drag on a Persian phone and a full
rightward drag on an English one commit identically, and both chevrons — the guides and the thumb's
own — turn around.

**The item is drawn instead of assembled.** The ongoing notification left `BigTextStyle` for a real
`RemoteViews` layout: a badge that is a *different drawing per state*, the state as the title,
`route · subscription` beneath it, the platform chronometer on the reading edge, and a rail of
measurement chips that exists only when the numbers are true. `NotificationShadeDesign` (pure,
host-JVM tested) decides every *when* — a clock and an indeterminate rail never share a row, rates
are never printed while no traffic moves, and the wall-clock session start becomes a monotonic
`Chronometer` base. `TunnelNotificationView` paints it with only RemoteViews-safe views and colours
taken from the phone's `uiMode` (the app theme is a fixed light Material theme while the shade follows
the phone), and `notification_tunnel_rtl.xml` is the same box with `layoutDirection="rtl"` for the
Persian app on an English phone — a state no resource qualifier can express. The textual fields stay:
they are what a screen reader reads and what any surface that cannot inflate a `RemoteViews` layout
receives. `setStyle` is called exactly once, because a second call replaces the decorator and the
custom view loses its header and its actions.

### The V235 pass — one update rule (newest published wins), and a type ladder with real weights

`docs/LATEST_VERSION_RULE_AND_WEIGHT_LADDER_V235.md` is the chapter (Persian). It answers two
sentences: *make the cores' update rules complete — the only rule is: upgrade to the latest version
that exists, beta or release*, and *use the fonts' different weights in the different parts of the
app*.

**One rule, everywhere something updates.** Until V235 each surface kept its own idea of "latest":
the core updater filtered by *channel* (`prerelease` for Xray, `beta` for the rest), the in-app check
asked GitHub's `/releases/latest` — by definition the newest *stable* — and the toolchain autopilot
capped itself at a compatibility line. The channel idea failed in production exactly as predicted:
Xray sat pinned to beta `v26.9.30` (2026-09-30) while stable `v26.10.21` (2026-10-21) was newer in
every sense, because a `prerelease` filter cannot see a build wearing the wrong label — and the
stable-only endpoint was the same blind spot mirrored. The rule is now stated once and used by all
three updaters (`MARBLE_LATEST_VERSION_RULE_V235`): among **non-draft** releases, the one with the
latest `published_at` wins — a stable published after a beta is the update, a beta after a stable is
the update — with the version as a same-instant tie-break, and (in the app) the version still
guarding direction so a fresh hotfix of an old line can never offer a downgrade. `core-lock.json`
dropped `channel` for `"rule": "latest-published"` plus per-core provenance (resolved commit — now
for all four cores — publication instant, label); `scripts/test-core-lock-rule.sh` pins every branch
of the picker offline in PR verification; the pure-Kotlin half, `core/LatestReleaseRule.kt`, is what
`AppRepository.checkForAppUpdate` now runs, and the update dialog names a pre-release as one. The
toolchain targets newest-published too, with its old line kept as the compile-failure fallback: the
scheduled job resolves newest, must prove `:app:compileDebugKotlin`, and only falls back when that
proof fails. The first thing the new rule did, on its first run, was move Xray `v26.9.30 → v26.10.21`.

**A weight ladder instead of two voices.** The app effectively spoke in Bold (page titles, dialog
titles, every settings row, verdicts) and Normal (prose). `MarbleWeightSpec` in `ui/IosTheme.kt` now
names one weight per role — DISPLAY 800 for large-header page titles, HEADLINE 700, TITLE 600 for row
and card titles, LABEL 500, BODY 400, NUMERIC 700 — the typography slots carry the rungs, and the
components were retuned to them (rows step down to 600, trailing values up to 500, resting chips to
500 so selection is a two-rung step, menu selection to 600, warnings up a rung; the redundant
`.copy(Bold)`s are gone). A fifth real face, `vazirmatn_extrabold.ttf` (same Vazirmatn 33.003
release as the other four), backs the DISPLAY rung — no synthesised weights. Light/Thin were
deliberately not shipped: the app has no large quiet text to give them, and thin strokes at caption
sizes are where Persian legibility dies first. `MarbleWeightLadderV235Test` pins the ladder.

### The V236 pass — a twelve-expert network council that scans and then tunes

`docs/NETWORK_COUNCIL_SCANNER_V236.md` is the chapter (Persian, like every council record). Settings
gains one row — **Network Council Scanner** — and one button. Press it, and twelve specialists read
the network the phone is actually on: the link and its ceiling (Wi-Fi or mobile data, band, signal,
link estimate, interface MTU, metered and validated state), the operator behind it (name and
MCC/MNC, resolved against the offline registry), the latency ladder, jitter, loss, DNS honesty, a
port matrix, UDP/QUIC reachability, an MTU ladder, IPv4 and IPv6 separately, TLS interference and the
device's own thermal and memory limits. Then each seat writes the knobs it owns — power and workload
profile, mux and its concurrency, buffers, fast open, MTU, keep-alive, connect timeouts, UDP
mapping timeout, fallback width, resolvers and their DoH endpoints, the DNS query strategy, the
address family and the Happy Eyeballs delay, fragmentation and mux padding, and the measurement
budgets — and the plan lands through the same `updateSettings` a hand-edited switch uses.

**The judgement is pure Kotlin.** `core/NetworkCouncil.kt` owns the twelve seats, the confidence
each one speaks with and the arithmetic behind the score and the grade; `NetworkScanner.kt` is the
only file that touches Android; `CouncilReport.kt` persists the verdict (never the stale evidence)
outside the backup envelope. Every seat owns a named set of `TuningField`s, so the constitution is
code rather than prose: routing, split tunnelling, identity, certificate verification and the DNS
fail-closed posture have no field at all. A disagreement is resolved by measured confidence — a
completed handshake beats the operator table, an inconclusive one lets it speak — and the loser is
quoted in the UI as a dissent line instead of being deleted. A clean path never buys the heavy
fragmentation, IPv6 is never switched off to hide a dead AAAA, mux is never enabled on a transport
that cannot carry it, and the scan downloads nothing: throughput is the link's own estimate.
`NetworkCouncilV236Test` pins all of it, twenty-four tests deep.

### The V215 pass — Settings, text size, and a steadier Home

`docs/SETTINGS_TREE_TEXT_SCALE_V215.md` is the chapter. Settings is regrouped so every sub-section
opens the card it names; a text-size setting scales the whole app; the Home ping stops changing colour
on every sample and shows its first value sooner; the floating discs wear a deep face with a white
glyph (no more black centre); the national-filtering notice moved into the decision card so the
Home header keeps its height; and the settings nobody read (`expertMode` and eight Home and Iran
flags) are removed.

### The V216 pass — settings harmony and the ping engines

`docs/SETTINGS_HUB_HARMONY_V216.md` is the Settings critique and rebuild: the type ramp is
one-directional again (section names are `titleMedium` ink, sub-sections the platform's tracked
label, rows `bodyLarge`), every navigable row is borderless so exactly one outline wraps each
card instead of three concentric frames around one sentence, content speaks in ink while colour
is reserved for the section's icon and wash and for the option the user picked, and the last
translucent glass choosers — ping method, theme, Home style, engine — move to the platform's
opaque container step. `docs/PING_METHODS_CRITIQUE_V216.md` is the ping critique and rewrite:
the TCP dialer is a hedged head-start ladder that no silent address family can starve, its retry
window on a dead node costs 1.5× rather than 2× the timeout, the Real-delay batch rescues the
samples its own callback already published instead of printing FAILED beside a live number, and
the core-measured methods deduplicate the sweep by configuration — byte-identical documents
share one throwaway core while different accounts on one endpoint never share a verdict.

### The V219 pass — the aurora surface

`docs/AURORA_SURFACE_V219.md` is the critique and rebuild of the surface layer: true edge-to-edge
with Compose owning every inset, one 24-unit glyph grid and one lit tile for every icon in Settings
and the navigation dock, cards that catch light at their top edge, dividers that fade, a drawn prism
mark across the header, the notifications and all three launcher icons, shared lit materials for
rows, buttons, dock and sheets, and a drag-to-connect control rebuilt as a tinted well with an energy
bed and a lit sphere — with the V146 parking and V207 commit ordering untouched.

### The V222 pass — a second tunnel engine, and geo routing that stops dropping tags

`docs/TUN2SOCKS_CORE_AND_GEO_ROUTING_V222.md` is the chapter. The tunnel is now a **choice**: beside
the proxy core (Xray / sing-box), Settings → *Tunnel engine* offers `hev-socks5-tunnel` — the
datapath Marble has always shipped, still the default — and `tun2socks`, the pinned
`xjasonlyu/tun2socks` release built into `libtun2socks.so` and loaded **in this process**, because
only this process holds the descriptor `VpnService` created. It never terminates the app: the
upstream engine package calls `log.Fatalf` on failure, so Marble composes the same public packages
and returns a code and a sentence instead — `-5` for a revoked descriptor, `-2` for a proxy the
tunnel cannot use, and a fallback to HEV with the loader's own reason on an APK that does not carry
the library for this ABI. It has its own full option set (MTU cap, UDP timeout, TCP buffers,
moderate receive window, log level), its own log file in the diagnostics bundle, and its own pin in
`core-lock.json` — with `update-cores.yml` proving the newly resolved tag still compiles the mobile
wrapper before a bump can land. In the same pass, **sing-box geo routing became complete**: the
builder used to map four geo tags and silently drop every other one with a note, so the same profile
routed correctly on Xray and wrongly on sing-box. Now every tag the Routing page accepts —
`geoip:cn`, `geosite:category-bank-ir`, a tag from a custom `.dat` — is materialised from the same
pinned files Xray already trusts into a real `source` rule set, extracted in one pass per file, and
registered for both route and DNS rules. A tag that is genuinely absent is still reported, never
invented.

### The V223 pass — the disconnect abort, and a descriptor with one owner

`docs/TUN2SOCKS_SHUTDOWN_V223.md` is the chapter. The report was a process exit with
`reason=SIGNALED status=6` about 400 ms after `shutdown-begin`, with `TUN2SOCKS | run-exit code=0`
two milliseconds in and **no** Java exception, tombstone or `fatal-crash-pending.txt` — which is what
a native `abort()` looks like from inside the app, and why `status=6` (SIGABRT) had to be read as a
signal number rather than as a mystery code. It only ever happened on the tun2socks datapath, and
that narrowed it down: on the HEV path the service is the only closer of the duplicated TUN
descriptor, while on the tun2socks path the Go core closed the same number too — two closes, 300–400
ms apart, in a process that is opening and closing descriptors throughout that window. The Go
wrapper now never closes a descriptor it does not own, stops the datapath the way gVisor documents
(`Attach(nil)` fires the stopfd and waits for every dispatch loop) before releasing the stack, runs a
real `idle → running → stopping → idle` state machine so a reconnect cannot open a second stack over
one descriptor, and gains `marble_t2s_stop(wait_ms)` — the join the service needs before it closes
anything. In the service, exactly one owner holds the descriptor, the close happens once under a
lock, and a cleanup stops **its own** datapath instead of both: calling HEV's quit from a tun2socks
session armed a sticky `SYNC_STOP` whose `assert()` on a closed event socketpair is itself an
`abort()`. The report reads a forced exit properly now (`SIGABRT(6) • native abort()…`, plus the
`TUN2SOCKS |`/`TUNNEL |` lifecycle lines that were missing from the timeline), and
`Tun2SocksShutdownV223Test` pins all three rules without a device.

### The V224 pass — the fdsan double-close, and a borrowed descriptor nobody else may close

`docs/FDSAN_DOUBLE_CLOSE_V224.md` is the chapter. A tun2socks disconnect on a V223 build still died —
`fdsan: double-close of file descriptor 148 detected`, one line after the service's own single,
locked `fd-close` — because V223 had removed the wrapper's close but not the close *inside the
stack*: gVisor's `Stack.Wait()` removes every NIC with `closeLinkEndpoint=true`, which runs the
pinned tun2socks `FD.Close()`, and that is `unix.Close(fd)` wrapped around gVisor's no-op. The raw
syscall close never reaches bionic's fdsan table, so the service's fdsan-tagged
`ParcelFileDescriptor.adoptFd(fd).close()` met a kernel-closed number, and bionic turns exactly
that pair (tagged close + `EBADF`) into the "double-close detected" abort. The rule is now about
ownership, not about the absence of a `close` call: the service's descriptor is only ever **lent**
across JNI, both native datapaths duplicate it at the boundary (`F_DUPFD_CLOEXEC` — in the Go
wrapper before `fdbased.Open`, and in the HEV JNI bridge before `hev_socks5_tunnel_main_from_str`)
and run and close only their own dup. One TUN device, two numbers, one owner and one close per
number — so `Stack.Wait()`, a future endpoint teardown, or an auto-bumped HEV can close whatever
they own without ever touching the number the service closes. The service side is unchanged on
purpose (an unclosed `hevFd` would leak on every native early-refusal path), and the ownership
split is pinned where each language is read: `FdsanDoubleCloseV224Test`, the V224 integrity checks
(the device may only be opened on `ownedFd`, never on the borrowed `fd`), and a stage-time guard
in `prepare-native.sh` that fails the release build if the staged wrapper ever loses the dup.

### The V221 pass — Meridian: flat surfaces, an accent the user owns, and a Censorship Lab

`docs/MERIDIAN_V221.md` is the chapter. The interface gains a second material — **flat**, now the
default: one tonal fill, one plain hairline and no shadow, gradient or specular wash — driven by a
single switch (`flatInterface`) that every card, row, tile, sheet, dock and dialog reads through one
composition local. Six accent palettes (Ocean is the V220 brand, byte-identical) re-tint the tab, the
connected ring, the slider and every selected row from one setting. **Power profiles** project the
device's own cores and memory onto the cores' real knobs (multiplex, TCP fast open, socket buffers,
measurement pools) with a hard, tested bound and a Balanced profile that is the identity function.
The **Censorship Lab** is a new tool page: five parallel probes on the live path, a pure diagnosis
that names the technique (DNS interference, name filtering, injected reset, address block, port
filter, shaping) and a one-tap prescription that only moves the knobs it owns — TLS record
fragmentation and handshake fragmentation on the extended core, fake-IP DNS, address family — and
never identity, routing or certificate policy. Under it: a bounded 90-second warm DNS cache that the
first ping and the first connect reuse, Happy Eyeballs obeying its own setting instead of a hard-coded
200 ms stagger, and locale-correct byte/speed formatting everywhere.

## Main navigation

MarbleNG uses three primary tabs: **Home**, **Servers**, and **Settings**.

### Home

- Connect / Disconnect / Cancel / Reset control.
- Exact last-route one-tap reconnect.
- **Only the selected route** — the one server or subscription group you chose, never a list.
- Full TUN / local proxy state.
- Live Ping, Jitter, and Quality.
- Optional node / Xray / mode summary metrics.
- Iran Mode state when enabled.
- Quick access to Rank, Servers, Privacy, and Routing.
- Physical-network label and live upload/download activity.
- Tunnel and kill-switch state.
- Three Home presentations — Signature (the default professional studio), Cosmic orbit and Cosmic immersion — switchable from Settings.
- Five connection controls, one product decision: the round shutter, slide-to-connect, classic power bar, a **stream bar** with a light band travelling right to left, and a **floating pill** docked above the bottom of the page. The last two are pinned to the floor of the page so the primary action is always in reach.
- A **live ping instrument** in a permanent, reserved slot beside the connection control: arc gauge, current value, sparkline of the last probes. It only ever measures the one server you are attached to, it is revealed — with an opacity fade, never a layout change — only once the connect control itself shows CONNECTED, and its chrome complements the connect button (same elevated glass, the control's own state tone in its hairline), so nothing on the page ever shifts when it appears.
- A **shortcut deck** above the status banner: add server, paste, QR code and a permanent ping readout.
- Signature studio extras, every layer optional from Settings: a draggable floating connect button, a status banner (Home-only or on all pages), a configurable corner shortcut.
- Real connection ping: parallel HTTPS first-byte + tunnel RTT probes with a healthy-minimum ladder, so a busy edge no longer reads as "no response".
- Seamless loop animations in every style — no visible start or end boundary.

The floating bottom tab bar is completely still: no breathing, no resize, no selection pulse. Only its colour, shadow depth and selection wash animate, and all of them use overshoot-free tweens so a tap can never flash the bar.

The Home title and main connection surface use a fixed layout so changing runtime status text does not move the Connect control. The status title and sentence above the Connect ring are anchored to a reserved block, so a shorter or longer sentence swaps in place instead of pulling the control up and down. Ping and uptime readouts auto-shrink and ellipsize so they never overflow their box in any style.

### Servers

The **Servers** tab manages subscriptions, source buckets, and nodes. It is a card system: one page
header, one search field with a filter rail under it, one collapsible header per group and one
independent card per server. Nothing is flush against anything else — every surface keeps its own
radius and hairline, and every colour comes from the active theme palette.

#### Header, search and filters

- Bold **Servers** headline with a live `2 groups • 18 servers` count.
- Two round controls on the right: `+` opens Add node, and the sort control opens a fully rounded
  dropdown (Default, Name, Name (Z-A), Ping, Country, Protocol) that ticks the active choice.
- One pill-shaped search field: name, protocol, host, transport, security or country.
- A filter rail under it: the active group capsule, an **All protocols** capsule that lists every
  protocol with its live count, an advanced-filter menu (Group by country, Only reachable,
  Max ping Off/100/200/500 ms, Reset) and a control that measures every server of the scope at once.

#### Groups

- Add **HTTPS** remote subscription URLs.
- Create local source buckets.
- Paste configs from the clipboard.
- Import config files.
- Refresh one selected source or all remote sources.
- Rename, edit, or delete a source.
- Filter by All, Manual, or one subscription.
- Source-managed and user-owned local profiles are kept distinct.

A group header is one rounded box. Folded it shows a chevron, the name, the server count and the
auto-update state; open it grows its own facts — the plan usage box (`384.7 GB / ∞`), the expiry
line, a **Website** capsule, a refresh control and a three-dot menu (manage, refresh, copy URL, copy
all servers, ping, rank, show only, delete). Redundant source-dashboard chrome and duplicate counters
stay removed.

#### Server cards

Each server is its own 16dp card: the country on the left, the identity column in the middle (bold
name, a protocol badge such as `VLESS/REALITY` or `VMESS/TLS/H2`, then the endpoint in faint ink)
and a latency capsule plus a three-dot menu on the right.

- Connect directly to a node; the connected card keeps its geometry and only changes frame and one word.
- Per-server menu: **Edit**, **Copy link**, **Export QR code**, **Ping**, **Move to group**,
  **Details**, **Copy Xray JSON**, **Edit Xray JSON**, **Duplicate to Manual** and **Delete**.
- The QR export is rendered on device by Marble's own encoder — no network, no image dependency.
- Sort by Default, Name, Name (Z-A), Ping, Country or Protocol; never-measured servers sort last
  rather than pretending `0 ms` is fastest.
- Run real full tests, or measure a whole group at once.
- Rename, move between groups, or delete nodes.
- Copy original config/share text or generated Xray JSON; edit Xray JSON for supported profiles.
- Duplicate a profile into the always-on Manual storage.
- Swipe right for the rename dialog.
- See per-card queued/testing progress.

The latency capsule never resizes: an unmeasured server reads `0 ms` in the theme's danger tone and
tells TalkBack the truth — that nothing has been measured yet.

#### Add node

`+` opens a full-screen sheet with rounded top corners: **Node**, **Chain** or **Subscription**.

- Name, a comprehensive Protocol dropdown (VLESS, VMess, Trojan, Shadowsocks, Hysteria2, WireGuard,
  SSH, SOCKS5, HTTP, HTTPS, Xray JSON — plus the protocols this core cannot dial, listed disabled
  with the reason), a wide Server field beside Port, then UUID / password, Flow, Transport and
  Security dropdowns.
- **Save** stays disabled until the config is complete, and the sentence under the form says exactly
  which field is missing — the same check the builder itself uses.
- Paste and file import land in the group the page is currently showing.

### Settings

Common settings are visible first; advanced network controls are behind **Expert controls**.

Main areas include:

- Appearance (connection style, Signature studio customization, accent color, night outline style — subtle / bold / colored / hidden, light/dark theme, Vazir or System font, language)
- Connection
- Testing & ping
- Split tunneling
- Notifications
- Subscriptions
- Regional protection / Iran Mode
- Marble Intelligence
- DNS
- Routing
- Core options (Xray and sing-box)
- Chain proxy
- Bug Finder

## Last-route persistence

A successful connection is treated as durable user intent.

1. When a profile reaches `CONNECTED`, MarbleNG saves that profile ID.
2. Closing or killing the app does not erase it.
3. On the next launch, Home resolves that profile from the current Servers list.
4. Pressing **Connect** reconnects that exact profile first.
5. If the profile was deleted, MarbleNG clears the stale reference and falls back to automatic selection.

## Supported inputs

### URI / subscription inputs

- VLESS
- VMess
- Trojan
- Shadowsocks
- Hysteria2 / HY2
- SOCKS / SOCKS5
- HTTP / HTTPS
- SSH
- Base64 subscription payloads
- Plain link lists
- Xray JSON

### Manual editor

- VLESS
- VMess
- Trojan
- Shadowsocks
- Hysteria2
- HTTP
- HTTPS
- SOCKS5
- SSH
- WireGuard-style manual configuration
- Raw/custom Xray JSON

### Xray transports and security

Depending on the profile, MarbleNG preserves modern Xray stream settings such as:

- TCP / raw
- WebSocket
- gRPC
- HTTP/2
- HTTPUpgrade
- XHTTP / SplitHTTP
- mKCP
- TLS / REALITY-related stream settings
- SNI
- ALPN
- fingerprints
- advanced custom Xray JSON

## Connection architecture

Full TUN mode:

```text
Android apps
    ↓
Android VpnService / TUN
    ↓
hev-socks5-tunnel
    ↓
local Xray SOCKS path
    ↓
Xray outbound
    ↓
selected remote node
    ↓
Internet
```

Local proxy mode exposes the configured loopback proxy without forcing the whole Android device through TUN.

## Testing and ranking

### Quick TCP ping

- Fast host/port reachability.
- Endpoint de-duplication for large aggregator subscriptions.
- Per-node progress updates.

### Real Xray verification

- Uses a real Xray path.
- Tests proxy usability rather than merely checking whether a TCP port accepts connections.
- Keeps tunnel evidence separate from lightweight TCP evidence.

### Smart rank

- Can rank the whole Servers list or the currently selected group.
- Uses healthy tunnel evidence, score, and latency.
- Tests the enabled scope without an artificial eight-node cap.

## Live telemetry

While connected, MarbleNG can expose:

- route RTT
- jitter
- route quality score
- download rate
- upload rate
- physical-network information
- active route state

Unknown metrics remain unknown rather than being presented as fabricated zero-quality evidence.

## Marble Intelligence

Adaptive features include:

- network-scoped route history
- persistent health evidence
- connection race
- smart fallback
- network-change recovery
- adaptive MTU
- adaptive DNS ordering
- IPv4 / IPv6 adaptation
- adaptive throughput testing
- UDP / QUIC health evidence
- thermal-aware testing
- adaptive tunnel buffers
- workload profiles
- continuous route optimization
- Marble Turbo connection tuning

### Marble Turbo

When enabled, MarbleNG can compare measured transport strategies on the selected route and remember the better method without arbitrarily changing the user's intended exit profile.

## Privacy and fail-closed behavior

- Full-device TUN fail-closed behavior.
- Kill-switch state.
- DNS interception.
- Encrypted DNS / DoH configuration.
- Identity Guard.
- Split-tunnel visibility.
- Privacy audit through the active proxy.
- Exit IP and DNS observation.

For stronger OS-level protection, Android's **Always-on VPN** and **Block connections without VPN** can also be enabled.

## DNS

Expert DNS controls include:

- TCP/UDP port 53 interception
- primary and secondary TUN DNS
- primary and secondary DoH
- adaptive DoH ordering
- adaptive dual-stack behavior
- IPv6 enable/disable
- IPv6 preference
- `UseIP`, `UseIPv4`, `UseIPv6`, and `UseSystem`
- common resolver presets

## Routing

Routing modes:

- Proxy all
- Private direct
- Geo direct
- Custom

Controls include:

- GeoIP / GeoSite tags
- private-network bypass
- direct domains
- proxy-only domains
- blocked domains
- direct IPs
- blocked IPs/CIDRs
- domain strategy
- ad blocking
- `geoip.dat`
- `geosite.dat`
- Xray routing-policy verification

## Iran Mode / regional protection

Policies:

- Auto
- Always on
- Off

Capabilities include:

- underlay / ISP classification
- confidence-based detection
- filtering-technique observations
- domestic direct-routing policy
- routing presets
- adaptive countermeasures
- optional deep probing

## Split tunneling

- All apps through VPN
- Only selected apps
- Bypass selected apps
- Installed-app picker for package-level routing

## Notifications

MarbleNG uses Android's foreground-service notification while its connection service is active.

Optional alerts include:

- connection events
- recovery events
- privacy warnings
- network changes
- subscription events
- core updates
- live telemetry
- configurable cooldown
- Android notification channel management

In-app Snackbar notices use a flat surface with **no heavy black drop shadow**.

## Subscription management

- startup refresh for stale remote sources
- configurable refresh cadence
- manual selected-source refresh
- refresh-all
- source metadata when supplied by providers
- safe source deletion
- user-owned local profile preservation

## SSH

The manual SSH path carries TCP through the protected local adapter. Unsupported UDP behavior remains fail-closed.

## WireGuard-style manual input

The manual editor exposes fields for:

- private key
- local address/CIDR
- peer public key
- pre-shared key
- allowed IPs
- reserved values
- keepalive
- MTU
- userspace mode

## Core options (Xray and sing-box)

Settings › Connection › Engine carries one page per running core. Expert controls include:

- Xray: log level and DNS log, sniffing with the dest-override order, LAN and HTTP inbound port,
  SOCKS UDP, Mux.Cool (concurrency, XUDP concurrency, UDP/443), the whole `sockopt` surface
  (domain strategy, TCP no-delay, keep-alive, user timeout, congestion control, MPTCP, window
  clamp), routing strategy and domain matcher, the five `policy` timeouts and the buffer size, and
  an extra-JSON editor
- sing-box: log level and timestamps, sniffing and destination override, LAN and HTTP inbound port,
  inbound username/password, multiplex (protocol, max connections, min/max streams, padding),
  connectivity options (parser, unified delay, cache file, connect timeout), and an extra-JSON
  editor

Everything the screen does not model can be written into the extra-JSON editor: objects merge key by
key, arrays and scalars replace, and a patch that names a subsystem Marble manages (`inbounds`,
`outbounds`, `routing`, `dns`, `log`) is refused as a whole, with the reason shown, leaving the
generated document untouched.

## Chain proxy

An optional two-hop route can use a selected second profile as the next hop.

## Bug Finder and diagnostics

Built-in diagnostics cover areas such as:

- Xray runtime
- TUN / HEV state
- app connection state
- active profile
- routing assets
- connection history
- runtime logs

Debug Mode can export technical reports for development and bug investigation.

## In-app updates

By default, MarbleNG checks the latest stable GitHub Release when the app returns to the foreground.

When a newer semantic version exists:

- a native update dialog appears;
- version and release notes are displayed;
- the GitHub Release can be opened;
- automatic update checks can be disabled in Settings.

## Versioning

MarbleNG uses semantic release names with a monotonic Android `versionCode`.

Typical patch progression:

```text
1.0.2 → 1.0.3 → ... → 1.0.9 → 1.1.0
```

Larger changes can request minor or major jumps. Published release tags are intended to be immutable.

## Android ABIs

Signed releases target:

- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`
- `x86`

The release pipeline can also publish a universal APK.

## Native cores

Pinned in `core-lock.json`:

- Xray-core
- hev-socks5-tunnel

Pinning makes native-core changes explicit and reproducible.

## GitHub Actions build

The signed build workflow:

1. checks out full history and tags;
2. provisions JDK and Android SDK/NDK;
3. reads pinned native-core versions;
4. builds native dependencies;
5. restores the persistent Android signing identity from GitHub Actions secrets;
6. validates the keystore and private key;
7. calculates the semantic app version;
8. builds signed APKs;
9. verifies APK signatures;
10. uploads Actions artifacts;
11. publishes a GitHub Release.

No signing private key is committed to the repository.

### Play Protect-safe installation

Release packaging now refuses to produce an installable `assembleRelease`/`bundleRelease` artifact
without `signing.properties`. This prevents an unsigned local APK from being mistaken for the
trusted MarbleNG release. Install the APK from the signed GitHub Actions artifact, or upload that signed APK to Google Play
internal testing while preserving the existing release certificate;
do not rotate the certificate between builds. The workflow verifies the resulting APK certificate
with `apksigner` before publishing it. Play Protect reputation still belongs to Google Play and
cannot be bypassed by app code.

### Installable native payload (16 KB pages + installer extraction)

An APK can be perfectly signed and still be rejected the instant **Install** is
tapped ("App not installed"). Two properties decide that outcome, and both are
now pinned and fail-loud:

- every shipped `lib/**/*.so` is ELF `PT_LOAD` aligned to 16 KB (the Go cores
  are linked with `-Wl,-z,max-page-size=16384` and every staged library is
  verified before Gradle runs) — Android 15+ 16 KB-page devices reject
  misaligned libraries at install time;
- the installer extracts the compressed native entries
  (`useLegacyPackaging = true`, with the merged manifest normalized so
  `android:extractNativeLibs` can never flip to `false`) — the Xray and
  sing-box cores are executables launched from `nativeLibraryDir`.

Details and the full root-cause write-up: `docs/APK_INSTALL_CONTRACT_V195.md`.

## Local build

The GitHub Actions workflow is the reference build environment.

Typical local flow:

```bash
./scripts/prepare-native.sh
./gradlew assembleRelease
```

Check the current workflow and Gradle files for the exact SDK/NDK/toolchain versions.

## Repository docs

Additional implementation notes live under `docs/`, including regional protection, Marble Intelligence, UI, routing, and runtime documentation.

## Security notes

- Never commit Android signing keys.
- Never commit subscription credentials.
- Treat imported proxy URLs as secrets.
- Debug output should avoid authentication material.
- Keep Android's system VPN protection enabled when leak prevention is critical.


## System integrity preflight

`scripts/system-integrity-check.py` is a fast architecture-level regression guard that runs in
source verification and again before the signed release build. It checks that Android lifecycle,
persistence, exact Library identity, Xray/HEV ownership, encrypted DNS, temporary test processes,
Marble Intelligence, diagnostics and release invariants still agree with one another.

This complements — rather than replaces — Kotlin compilation, Xray config validation, JNI symbol
verification, APK signature verification and the in-app Bug Finder runtime evidence.

## Project status

MarbleNG is under active development. The `main` branch and latest signed GitHub Release are the authoritative sources for the current feature set.
