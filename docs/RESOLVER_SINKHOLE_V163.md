# MARBLE_RESOLVER_SINKHOLE_V163 — resolvers that must be *out*, not merely last

Companion markers in this release: `MARBLE_FREEDOM_SOCKOPT_STRATEGY_V163`,
`MARBLE_SINGBOX_PINNED_PEER_V163` (see `SINGBOX_PINNED_PEER_V163.md`),
`MARBLE_REMEMBERED_PING_KEEP_V163`, `MARBLE_SETTINGS_HUB_TRIM_V163`.

## What the runtime log showed

```
[Warning] The "freedom.domainStrategy" setting is deprecated and will be removed.
          For compatibility, its value has been automatically migrated to "sockopt.domainStrategy".
[Error]   app/dns: failed to retrieve response for … > Post "https://dns.shecan.ir/dns-query":
          tls: failed to verify certificate: x509: certificate has expired or is not yet valid:
          current time … is after 2026-07-10T04:40:15Z
[Error]   app/dns: … Post "https://dns.shecan.ir/dns-query": context deadline exceeded
[Error]   app/dns: … Post "https://9.9.9.9/dns-query": context deadline exceeded
[Error]   app/dns: … Post "https://1.1.1.1/dns-query": context deadline exceeded
ROUTE  probe-warmup-miss-held / probe-result-stale-discarded
EGRESS startup-observation-route-suspect reason=no-egress-evidence
```

The last three app-side lines are *consequences*, not faults: the probes that produce them run
HTTPS through the tunnel, and every one of those needs a DNS answer first. With the resolver
graph in the state above, no answer arrived in time, the warm-up hold and the stale-discard
paths did exactly what they are documented to do, and the start-up observation correctly said
"no egress evidence". Fixing the resolver graph is what makes those lines disappear.

## Root cause 1 — a domestic resolver in the tunnel's resolver graph

`dns.shecan.ir` is an Iranian "anti-sanction" resolver. It was sitting in
`AppSettings.dnsPrimaryDoH`, and both config writers copied the user's primary into the
**remote** pool — the resolvers that are asked *through the proxy* for every browsing lookup.
That is wrong on three independent counts:

1. **Leak.** Every hostname the user visits is sent, from the exit IP, to an Iranian operator.
   `LeakGuard` even listed "shecan" among *encrypted providers*, so the sentinel called this
   state safe.
2. **Poison.** Shecan's whole purpose is to answer sanctioned domains with its own proxy
   addresses. Through the tunnel those answers are useless at best.
3. **Broken.** Its certificate expired on 2026-07-10. Every query paid a full TLS handshake and
   then failed. `ResolverEvidencePolicy` already treated one `certExpired` event as decisive —
   but "decisive" only meant *demote to last rank*. With `enableParallelQuery` armed by that very
   evidence, the core still raced the broken endpoint on every lookup, and under sequential
   failover the primary slice of every cold lookup was burned first.

## Root cause 2 — the deprecated freedom alias

`XrayConfigHardener` wrote the resolve plan of every freedom/direct hop as
`settings.domainStrategy` **and** `settings.targetStrategy` **and** `sockopt.domainStrategy`.
The pinned core (`v26.9.9`, `infra/conf/xray.go`) migrates the first two into the third with a
warning on every start and documents their removal; `proxy/freedom/freedom.go` reads only
`streamSettings.SocketSettings.DomainStrategy` for both the TCP dial and the UDP `PacketWriter`.
The comment claiming UDP needed the outbound-level field was out of date.

## The fix

### `ResolverEvidencePolicy`

- `isDomesticResolver(endpoint)` — true for any host in `IranNetworkRegistry.DOMESTIC_RESOLVERS`,
  any `.ir` host, and the known anti-sanction hostnames outside `.ir`.
- `excluded(candidates, evidence, now)` — domestic endpoints, plus endpoints whose last
  `certExpired` failure is within `CERT_BROKEN_TTL_MS` (6 h) and not followed by a proven answer.
- `withoutExcluded(...)` — drops them without ever emptying the list; a domestic entry is
  dropped even when it is the only one.

### Both engines

- `AppSettings.measuredDnsExcludedEndpoints` (transient, like `measuredDnsDemotedEndpoints`) is
  filled by `MarbleIntelligence.effectiveSettings` per physical network.
- **Xray** (`XrayConfigHardener`): the user pair and the stock pool are filtered by
  `resolverAllowed` before ranking, so a healthy stock resolver leads when the user's primary is
  domestic or broken. Endpoint-bootstrap `https+local://` literals were never affected (they take
  IP literals only).
- **sing-box** (`SingBoxConfigBuilder.dnsConfig` / `bootstrapDoH`, `MarbleIntelligence
  .singBoxResolverPool`): the same filter on the remote pool and the bootstrap peers; the stock
  trio is appended so the `take(3)` still yields three peers. The `dns-remote` fallback server
  switches from `sequential` to `parallel` when `measuredDnsParallel` is set — the extended
  fork's `fallback` transport supports both strategies and `parallel` returns the first good
  answer, which is what `enableParallelQuery` does on Xray.
- **Kotlin-side lookups** (`CensorshipAwareDnsResolver.initSession`): the user's own DoH pair is
  not raced for tunnel-endpoint resolution when it is domestic.

### Xray freedom hops

`XrayConfigHardener.writeFreedomResolveStrategy` is the single writer: it removes any
`settings.domainStrategy` / `settings.targetStrategy` / outbound `targetStrategy` alias (imported
or not) and writes `sockopt.domainStrategy` only (nothing for `AsIs`). The verifier rejects a
config that carries both forms.

## Anti-leak guarantees kept

- Ordinary browsing DNS still never falls through to plaintext or the system resolver on either
  engine (`disableFallbackIfMatch` / `xgc-dns` on Xray, `final: dns-remote` with no `local` peer
  on sing-box).
- `::/0` reject when IPv6 is off, `IRAN_POISON_BLOCK_IPS`, and the endpoint bootstrap rules are
  untouched.
- The change is strictly subtractive on the resolver side: an endpoint is removed only when it
  cannot answer (expired certificate) or must not be asked (domestic).

## Tests

`ResolverSinkholeV163Test` pins every rule above; `XrayConfigHardenerTest` was updated to assert
the alias is *absent* rather than present; `scripts/system-integrity-check.py` gained four V163
invariants.
