# V262 — Smart Jitter Reducer: verified probe target, per-tick burst, real-time home readout

MARBLE_SMART_JITTER_REALTIME_V262

The 2026-11-03 session logs showed the jitter reducer probing `loss` on **every** tick while the
route probe in the same session reported `successPercent=100 lossPercent=0`, plus the pivot note
"previous literal HTTPS target produced zero verified RTTs". The home hero's jitter tile also
only moved when the VPN service's slower route probe published, never from the reducer itself.

## Root cause and fixes

| Finding (log evidence) | Fix |
| --- | --- |
| `JITTER \| probe \| detail=loss` on every tick; route probe `successPercent=100` at the same instant | `SmartJitterDefaults.tlsSniFor` maps a literal anycast probe target to its real certificate hostname (`1.1.1.1`/`1.0.0.1` → `cloudflare-dns.com`, `8.8.8.8` → `dns.google`, `9.9.9.9` → `dns.quad9.net`) and `SmartJitterController.channel()` passes it as the probe's `tlsHost`. The SOCKS dial still uses the configured target; only the TLS SNI/verification name changes — the same mapping the route probe's `probeTlsHost` already applies. A literal IP sent as the TLS hostname is what produced zero verified RTTs. |
| One RTT per tick → the RFC 3550 §A.8 EWMA got a single delta per probe interval, so the jitter figure lagged the route | Each tick now fires a burst of `BURST_SAMPLES = 3` warm keep-alive requests `BURST_GAP_MS = 50` ms apart on the same connection (the route probe's own burst is the same shape: 3 samples, 45 ms), feeding several consecutive in-path deltas per interval. Burst is capped at `MAX_BURST_SAMPLES = 8` and costs one radio wake per tick. |
| Home hero jitter not real-time | `SmartJitterController.onState` fires on every published `State`; `AppRepository` attaches a listener that mirrors `jitterMs`/`validSamples` into `liveJitterMs`/`liveJitterSamples` (the values `HomeScreen`'s hero and the other live surfaces already read) whenever the reducer is running with valid samples and the session is `CONNECTED`. With the module OFF the bridge stays silent and behaviour is unchanged. |

## Scientific basis

- **Jitter estimator** — RFC 3550 §A.8 (`J = J + (|D| − J) / 16` over consecutive RTT deltas),
  already the module's rule in `JitterWindow`; the burst simply supplies more consecutive `D`
  values per probe interval instead of one, which is what shrinks the estimator's variance and
  latency-to-settle without changing its math.
- **Cold-sample exclusion** — unchanged: the first sample of a fresh connection still counts as
  an attempt but never enters the statistics (report v11.2.3, P0-2).
- **TLS verification** — a certificate is issued for names, not (reliably) for literals; the
  probe must dial the measured target and verify the name the target actually answers on.

## Acceptance criteria for the next log pass

1. No `JITTER | probe | detail=loss` streak while the same session's `ROUTE | latency-sample`
   reports `successPercent=100` — the probe target now verifies.
2. Each `JITTER | probe` event carries `burst=k/3`, and the home hero's jitter updates on
   reducer ticks, not only on route-probe publications.
3. No verdict below 8 valid samples; cold samples still excluded (V238 rules untouched).
