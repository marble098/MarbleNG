# Resilient Connect Plan

`ResilientConnectPlan` adds three evidence-driven connection improvements without changing a
profile, bypassing its security settings, or fabricating fallback servers:

1. **Staggered IPv4/IPv6 race.** When both families are reachable, IPv4 is attempted immediately
   and IPv6 follows after a short link-sensitive delay (0–250 ms). Unavailable families are not
   scheduled. The caller can select the first successful connection and cancel its sibling.
2. **Congestion-aware probe fan-out.** Healthy links can use up to four probes concurrently;
   high-RTT or lossy links reduce that to two or one, avoiding self-inflicted radio congestion.
   A caller may supply a stricter concurrency cap (the policy clamps it to 1–6).
3. **Hysteretic failover and bounded retries.** Failover requires two consecutive failures and
   returning to a primary route requires three successes. Retry waits use bounded exponential
   backoff, with a short retry under severe packet loss.

The policy is pure Kotlin and produces an immutable plan from existing link evidence. Integration
points that own probe cancellation and route switching can execute it while retaining their current
identity, leak-guard and endpoint validation. Unit tests cover healthy/poor links, family
availability, hysteresis and retry bounds.
