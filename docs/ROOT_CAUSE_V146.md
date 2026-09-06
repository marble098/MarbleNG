# V146 — Home ping scope, ping honesty, slide park, Persian completion, Iran liveness

Five requirements, each fixed at its cause. Every marker below is greppable in the source
(`MARBLE_*_V146`).

---

## 1. `MARBLE_HOME_PING_ROUTE_GROUP_V146` — the top-of-Home ping pings the route's subscription

**Was:** `AppRepository.pingHomeGroup()` delegated to
`testSource(librarySourceFilter.ifBlank { "all" })` — it pinged whatever the Home *group chip*
happened to filter by, which is independent of the route the page is actually showing. A user
looking at a Shatel node while the chip sat on "All groups" would sweep every subscription.

**Now:** the pulse icon pings the subscription that the route **shown on the page** belongs to.
"Shown route" has exactly one definition — the repository's new `homeRoute()` (active route, else
the selected one, else the remembered one), which is now also what the deck uses
(`rememberDeckEvidence`), so the ping action, the connect button and the page can never disagree
about which route is on screen. The sweep still runs the single ping engine over the user's
Settings budget (`testSource` → `pingProfiles`), and an unknown source id still fails closed.

The per-route ping of that same server stays one tap away on the status banner
(`onTestPing` → `measureHomePing`), and the group-chip ping remains on the Servers page.

---

## 2. `MARBLE_TUNNEL_PING_DNS_SAFE_V146` + `MARBLE_SMART_BUDGET_FULL_V146` — ping honesty

**Was — tunnel DNS leak:** `RouteProbe.httpPingOnce` used `HttpURLConnection` with a SOCKS proxy.
`HttpURLConnection` resolves the destination hostname through Android's own resolver *before*
opening the proxy connection, so the "real delay" ping through the tunnel leaked the 204 origin's
name (gstatic/cloudflare/google) to the underlay resolver. On a link that poisons or drops those
names the tunnel itself was healthy and the measurement still reported unreachable — convicted by
the exact DNS lookup the tunnel exists to bypass.

**Now:** the tunnel path (`httpPingOnceThroughTunnel`) uses `SocksHttpClient.request`, which sends
ATYP=domain to the SOCKS5 inbound so Xray resolves the name *inside* the tunnel, and completes
certificate-verified TLS against the real hostname. The only name the system resolver ever sees is
`127.0.0.1`. The direct path (`httpPingOnceDirect`) keeps `HttpURLConnection` — that is the
underlay measurement, where the system resolver is the right resolver.

**Was — hidden budget carve-out:** `smartPing` Phase 2 (the real HTTPS measurement) used
`realTimeoutMs = (budgetMs * 0.65)`, so a route that answers in 7 s under a 10 s budget was
reported as timed-out at 6.5 s.

**Now:** Phase 2 owns the full per-sample budget. The fast TCP/DNS gate is what keeps dead nodes
cheap, not a budget carve-out of the measurement the user actually asked for.

---

## 3. `MARBLE_SLIDE_PARK_V146` — the dragged knob stays on the side it was dragged to

**Was:** both slide-to-connect controls always sprang back to zero after a drag, so a connected
control still read "slide to connect" and the knob did not reflect the live state.

**Now:** the knob parks on the side of its committed state — end while a route is live (or being
opened), start otherwise. A completed drag flies to the committed side and holds; a short drag
springs back to the state's own side. Commit direction follows the route state (connected drags
back to disconnect, disconnected drags forward to connect). `IosSlideToConnect` also gained the
`dragging` guard that `ConnectButtonSlide` already had, so a connection-state change landing
mid-drag can never animate the thumb under the user's finger.

---

## 4. `MARBLE_PERSIAN_COMPLETION_V146` — the last English strings are Persianized

**Was:** `trx()`/`faTranslate()` return the original English on a miss, so strings that were never
added to `FaLexicon` survived silently in English.

**Now:** ~40 user-facing strings that still fell back to English have Persian entries in
`FaLexicon` — the Identity Guard section, the Routing entry card, the five ping-method rows, the
routing rule workspace, the import/add-route menus and pickers, the privacy-audit copy, and the
runtime status/toast strings (clipboard, config link, version details, Xray rejection, DNS
resolver timeout, …). Identifiers, animation labels and compound patterns were left untouched.

---

## 5. `MARBLE_IRAN_LIVENESS_V146` — the Iran-tuned TCP liveness profile is actually applied

**Was:** `SocketLivenessPolicy.forTransport` carried an `iranMode` flag and a researched
high-RTT/high-loss profile (longer keep-alive + user-timeout for Iran's filtered international
transit, `MARBLE_IRAN_LIVENESS_V80`), but every caller passed the default `false`. The profile was
dead code: a connected tunnel on MCI/Irancell used the short non-filtered-network timeouts and
could drop a live connection that merely paused on a throttled link.

**Now:** `XrayConfigHardener.harden` passes `iranMode = iranActive` — the *same* verdict that arms
the poison-injector block rules, computed once so the two can never disagree — so an Iran Mode
connection emits the longer keep-alive and user-timeout it was designed for. Verified by
`XrayConfigHardenerTest.iran mode applies longer tcp liveness to the tunnel outbound` and
`NetworkPolicyTest.iranSocketPolicyIsMorePatientThanBaseline`.

Additional research (XTLS Serverless-for-Iran, Xray discussion #5969, the freedom `noises` schema)
confirmed the existing fragment/noise chains already use the current upstream shapes — plain
`tlshello` TCP fragmentation is reassembled by Iran's DPI, so the steel recipes use delayed first
writes and byte-level shredding instead — and that no additional knob is safe to turn on without
server-side support.
