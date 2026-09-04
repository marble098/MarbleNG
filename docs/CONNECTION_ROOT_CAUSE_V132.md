# MarbleNG — connection root cause analysis (V132)

**Symptom reported:** *“The server is connected in another client and answers ping there,
but it does not work in this client.”*

A node that works elsewhere proves four things: the share link is valid, the remote is alive,
the account is not expired, and the physical network can reach the remote address. Everything
MarbleNG does **after** that point is therefore suspect, and everything MarbleNG does that
another client does **not** do is the prime suspect.

This document traces the connect path in this repository and lists the defects found in it,
with the exact code location, the failure text the user saw, and the fix.

The path is:

```
MainActivity.connect(profile)
  └─ AppRepository.startVpn(profile)
       └─ MarbleVpnService.startConnection(id, sourceId, mode)
            ├─ promoteForeground(...)                → "Android rejected foreground-service startup"
            ├─ profileCompatibilityIssue(profile)    → "Unsupported VLESS …"
            ├─ establishTun(profile, session, …)     → "VPN establish failed"
            └─ startXrayAndForward(profile, …)
                 └─ XrayManager.start(profile, port, settings)
                      ├─ portAvailable(port)         → "Local port … is already in use"
                      ├─ prepareRoutingAssetsForConnect(settings)
                      │                              → "Routing assets are being updated; retry…"
                      ├─ XrayConfigHardener.harden(sourceConfig, port, settings)
                      │                              → "No proxy outbound" / verify() assertions
                      └─ waitSocksPort(port, 7_000)  → "SOCKS listener did not open" / "Xray exited with code …"
```

---

## 1. CRITICAL — a background routing-data refresh could veto every connection

**File:** `app/src/main/java/com/marbleng/app/core/XrayManager.kt`, `prepareRoutingAssetsForConnect()`

```kotlin
val needGeoIp = requiresGeoIp(settings)
val needGeoSite = requiresGeoSite(settings)
if (!needGeoIp && !needGeoSite) return routingAssetStatus()

val acquired = assetLock.tryLock(250L, TimeUnit.MILLISECONDS)
check(acquired) { "Routing assets are being updated; retry after preparation finishes" }
```

The product defaults are `routingMode = GEO_DIRECT`, `routeGeoIpTags = "ir,private"`,
`routeGeoSiteTags = "ir"` and `routeBlockAds = true` with `routeAdsTag = "category-ads-all"`.
`requiresGeoIp()` and `requiresGeoSite()` therefore both return **true for every connection**,
so every single connect attempted to take the global `assetLock`.

`prepareRoutingAssets()` — the updater behind Settings → refresh, the 24-hour asset refresh and
any first-run preparation — holds that same lock while it performs two HTTPS downloads
(15 s connect timeout, 60 s read timeout, plus a jsDelivr mirror retry). On a slow or filtered
network that is **minutes**.

Result: any connect pressed during that window died instantly with

```
IllegalStateException: Routing assets are being updated; retry after preparation finishes
```

…even though both `geoip.dat` and `geosite.dat` were already complete and valid on disk. The
server was never dialled. From the user's side this is indistinguishable from "this server does
not work in MarbleNG" — and it is invisible in another client, which has no geo-asset updater
that can block its connect path.

**Fix.** The lock is only ever needed to *write* an asset that is missing. When everything the
selected policy needs is already on disk there is nothing to protect against, so the connect
path now reads `routingAssetStatus()` and returns immediately without touching the lock.

---

## 2. CRITICAL — the node hostname was resolved by two literals and nothing else

**File:** `core/XrayConfigHardener.kt`, `harden()`

When the selected outbound's endpoint is a **domain** (the common case for subscriptions),
`bootstrapDomains` is non-empty and the hardener writes dedicated bootstrap resolvers:

```kotlin
dnsServers.put(JSONObject()
    .put("address", "https+local://${dnsHostLiteral(ip)}/dns-query")
    .put("domains", JSONArray(bootstrapDomains.map { "full:$it" }))
    .put("skipFallback", true)
    …)
…
dnsConfig.put("disableFallbackIfMatch", bootstrapDomains.isNotEmpty())
```

Xray's documented DNS flow is:

1. Build **list 1**: servers whose `domains` matches the query.
2. If `disableFallback` → skip list 2.
3. If `disableFallbackIfMatch` **and list 1 is not empty** → skip list 2.
4. Build **list 2**: the remaining servers whose `skipFallback` is not `true`.
5. Query list 1 then list 2.

So for the node hostname, the **only** resolvers that exist are the `https+local://` literals,
and until this change there were exactly two of them: `settings.dnsPrimaryIp` (`1.1.1.1`) and
`settings.dnsSecondaryIp` (`8.8.8.8`) — the two most-filtered public resolvers on censored
networks. `https+local` dials them **directly, bypassing the tunnel**, which is required (the
proxy-routed DoH chain needs the endpoint IP before it can carry anything) but also means the
bootstrap is exposed to exactly the filtering the user is trying to escape.

When both literals were unreachable, Xray could not resolve the node hostname, never dialled,
and the session failed — while the same server connected and pinged in a client that lets the
OS resolver or the remote answer the name.

**Fix.** The user's own resolvers keep first place; a set of independent stock literals
(`1.1.1.1`, `8.8.8.8`, `9.9.9.9`, `1.0.0.1`, `8.8.4.4`, `149.112.112.112`) follows them, plus
IPv6 literals when the underlay can actually carry IPv6. Later candidates also get a slightly
larger timeout so one filtered address cannot burn the whole budget.
Regression test: `XrayConfigHardenerTest."domain endpoint gets an independent bootstrap resolver ladder"`.

---

## 3. HIGH — `setUnderlyingNetworks()` could pin the tunnel to a dead network

**File:** `vpn/MarbleVpnService.kt`, `establishTun()`

```kotlin
val upstream = app.repo.intelligence.underlyingNetworks()
if (upstream.isNotEmpty()) runCatching { builder.setUnderlyingNetworks(upstream.toTypedArray()) }
```

`underlyingNetworks()` returns a `Network` object captured from a `ConnectivityManager`
callback. That handle stays valid long after the network behind it has lost
`NET_CAPABILITY_INTERNET`, stalled behind a captive portal, or been handed over to another
transport. `setUnderlyingNetworks()` then commits everything the TUN forwards to that one dead
underlay.

Worse, the asymmetry is invisible from the app side: MarbleNG excludes **itself** from its own
VPN (`addDisallowedApplication(packageName)`), so Xray's egress socket is **not** bound by this
call and still reaches the server over the system default network. The tunnel therefore reports
"connected", the route monitor sees the node, and yet no application traffic ever comes back —
while the identical configuration works in a client that never pins an underlying network.

**Fix.** `usableUpstreamNetworks()` re-checks `NET_CAPABILITY_INTERNET` **and**
`NET_CAPABILITY_NOT_VPN` at establish time and drops the pin when the candidate can no longer
prove it carries traffic. An empty result leaves `setUnderlyingNetworks()` unset, so Android
applies its own transport selection and failover again.

---

## 4. HIGH — the TUN DNS filter could refuse to establish the tunnel at all

**File:** `vpn/MarbleVpnService.kt`, `establishTun()`

```kotlin
listOf(settings.dnsPrimaryIp, settings.dnsSecondaryIp)
    .filter(String::isNotBlank)
    .forEach { candidate ->
        if (candidate.contains(':') || underlay.hasIpv4) dnsServers += candidate
    }
…
if (dnsCount == 0) { return false }        // → handleFailure("VPN establish failed")
```

`NetworkSnapshot.hasIpv4` **defaults to `false`** and only becomes true once link properties
have been published by the intelligence callback. A connect fired before that callback, on a
network whose link properties never arrive, or with both DNS fields blanked in Settings, emptied
the resolver list — and the code then refused to build the TUN at all.

But these addresses are only a **fallback**: Xray hijacks port 53 itself (the `dns-out`
outbound and the port-53 routing rule), so the TUN's DNS servers only answer when the encrypted
path is not in use. Refusing to establish the interface because of them turned a cosmetic
mis-configuration into a hard, silent connection failure with the message
`VPN establish failed`, which names nothing the user can act on.

**Fix.** The list can no longer come out empty: literal fallbacks are appended when the family
filter removes everything, and the "no DNS server at all" branch is now a genuine
cannot-happen diagnostic instead of the default outcome.

---

## 5. MEDIUM — Identity Guard failed closed on IPv4-only underlays

**File:** `vpn/MarbleVpnService.kt`, `establishTun()`

```kotlin
if (settings.identityGuardEnabled && !ipv6Ok) {
    diag.event("TUN", "identity-ipv6-fail-closed", …)
    return false
}
```

`identityGuardEnabled` defaults to **true**, so any device or ROM where
`builder.addAddress("fc00::1", 128).addRoute("::", 0)` could not be applied — an IPv4-only
underlay, a vendor restriction, a conflicting VPN — could never connect at all.

Identity Guard is a guarantee about **exit stability**: the session must stay on one public
exit IP. It is not a guarantee about capturing IPv6. The case that actually leaks is *IPv6
exists on the underlay but the TUN could not capture it*.

**Fix.** Fail closed only when `underlay.hasIpv6` is true and the TUN still could not capture
IPv6. When there is no IPv6 on the underlay there is nothing to leak, so the session continues
and the condition is recorded as `identity-ipv6-unavailable` for diagnostics.

---

## Why this presented as "the core and the client connection"

All five defects sit in the two stages the report named — **the core** (Xray config/DNS
generation and process start) and **the client's connection stage** (TUN establishment). None
of them is a parsing or protocol bug, which is exactly why the same share link worked in another
client: the profile was never the problem.

## Diagnostics added

Every fixed branch now emits a `diag.event` (`TUN`/`XRAY` categories) that Bug Finder surfaces:

| Event | Meaning |
| --- | --- |
| `TUN/dns-fallback-literals` | the configured resolver list was empty; literals were substituted |
| `TUN/underlying-networks` | how many candidate networks survived the liveness check |
| `TUN/underlying-networks-failed` | `setUnderlyingNetworks()` itself threw |
| `TUN/identity-ipv6-unavailable` | no IPv6 to capture; the session continued |
| `TUN/identity-ipv6-fail-closed` | IPv6 present but not capturable; refused to leak |

## Verification

* `python3 scripts/system-integrity-check.py` → 128/128 PASS
* `python3 tools/compose-scope-check.py` → 0 composable-context violations
* `python3 tools/kotlin-structure-check.py` → OK on every source file
* New unit tests: `XrayConfigHardenerTest."domain endpoint gets an independent bootstrap resolver ladder"`
