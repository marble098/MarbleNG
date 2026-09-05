# Connection and Home root cause audit (V136)

This revision closes three independent failure paths that had been masking one another:

1. Hysteria2 share links containing Salamander obfuscation were parsed as a generic Hysteria2
   node, so the obfuscation password never reached Xray.
2. A failed live probe left the previous rolling RTT in memory, and the Home connection-ping
   ladder could fall back to an older live or benchmark value even when the current probe had no
   verified response.
3. Live ping was composed as a sibling meter/slab. Even when the slab had a fixed slot, it was a
   separate visual instrument and still made the connection control's layout contract indirect.

## Hysteria2: one schema from URI to Xray

`ProxyParser.parseHy2()` now emits the Xray-core v26.7.28 document, not a sing-box or mihomo
variant. The relevant shape is:

```json
{
  "protocol": "hysteria",
  "settings": {
    "version": 2,
    "address": "edge.example",
    "port": 443
  },
  "streamSettings": {
    "method": "hysteria",
    "security": "tls",
    "tlsSettings": {
      "serverName": "edge.example",
      "fingerprint": "chrome",
      "alpn": ["h3"]
    },
    "hysteriaSettings": {
      "version": 2,
      "auth": "password"
    },
    "udpmasks": [
      {
        "type": "salamander",
        "settings": { "password": "the-obfs-password" }
      }
    ]
  }
}
```

The parser keeps an explicit `alpn` query list and defaults it to `h3`. `sni`, fingerprint and
`insecure` are carried into TLS. `obfs=salamander` requires `obfs-password`; unsupported obfs
values are rejected instead of being silently translated into another client's schema. The
hardener copies the transport object and adds DNS, address-family, routing and sockopt policy
without dropping `udpmasks`.

`ProxyParserHy2Test` covers the plain link, a TLS + Salamander link, rejection of incomplete or
unsupported obfs, and preservation of the mask after final hardening.

## Runtime authority: Xray validates what Marble will start

`XrayManager.start()` now follows this order:

1. prepare only local/bundled routing assets on the connection-critical path;
2. harden the selected profile into the exact runtime document;
3. atomically commit `runtime.json` (a failed rename is an error, never a truncating fallback);
4. run the shipped binary with `xray run -test -c runtime.json`;
5. only after a successful core-level parse, spawn the live process and wait for its SOCKS
   listener.

Validation results are cached by the hardened document and the native binary identity, so
reconnects do not repeatedly spawn a dry-run for an identical config. The cache is cleared when
stale runtime artifacts are discarded. A rejected transport, DNS, routing or version-specific
field therefore fails before TUN/HEV traffic is allowed to depend on it.

## Honest latency and failure invalidation

There are two measurement planes, and neither is allowed to invent a value:

- the Home connection ping accepts only verified HTTPS first-byte, valid real-delay, or successful
  full-GET responses from the current probe. A SOCKS CONNECT handshake is diagnostic evidence, not
  a displayed Internet RTT. An older `livePingMs` or stored benchmark is useful for sizing
  deadlines only; it is never a result for the current probe;
- the live route monitor measures certificate-verified HTTPS through the running SOCKS/Xray path.
  When the literal and domain ladders all miss, `invalidateLiveQuality()` clears the displayed RTT,
  jitter, tail, score, sample counts and rolling outcome window. The next successful response starts
  a fresh window. A failed Home probe sets `ConnectionPingState.FAILED` and the UI renders `×`; an
  unmeasured route remains `—`, and an in-flight probe is `•••`.

This preserves genuine sub-20 ms measurements when they are actually returned by the remote
probe, while removing the old synthetic floor and the stale/fallback paths that made an old number
look current.

## Home geometry contract

`HomeLivePingEffect` owns only the bounded one-shot retry cadence. It has no Compose layout. The
selected control owns the visual evidence through `HomeInlinePingBadge`, which has a fixed width
and height and is placed inside every silhouette:

- round: badge inside the circular shutter;
- slide: badge inside the fixed-height track;
- classic: badge inside the fixed-height power row;
- stream and floating: badge inside their fixed-height bars/pills;
- Signature's draggable FAB uses the same fixed badge.

`HomeLivePingSlab`, `HomeLivePingMeter` and the meter-bearing `HomePowerStage` were removed. The
Signature stage is now only a bounds-preserving control host, and the dock reserves only the
physical control—not a hidden live-ping row. Ping state changes therefore cannot insert a sibling,
grow a hero, or push the status/evidence blocks down the page.

The source-wide integrity audit remains green after this change. The Android unit/build command is
still the final authority for compiler and device-level validation; the runtime `xray run -test`
path is exercised on a device with the pinned native asset.
