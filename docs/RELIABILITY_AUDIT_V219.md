# Connection and routing reliability audit

## Fixed defects

- Core-port reclamation previously used wall-clock deadlines and restarted full TERM/KILL
  waits for every holder. A nominal four-second startup budget could therefore take many
  times longer (or stall after a clock adjustment). All process waits now share one monotonic
  budget. Only this app's exact core binaries remain eligible for signalling; foreign holders
  are never killed. Invalid timing arguments fail before polling.
- Continuous optimizer measurements swallowed `InterruptedException` through `runCatching`,
  then waited for the next candidate. The awaiter now propagates cancellation and cancels
  unfinished work and shuts down its executor in `finally`, including exceptional exits.
- A cancelled bulk sweep could copy a synchronized results list while remaining workers
  appended to it. Result snapshots now hold the list's monitor before ranking.
- Settings' routing simulator cached its verdict using only five settings fields. Changes to
  custom-rule enablement, domestic precision, geo tags and other routing inputs could leave
  an old verdict visible. Its cache now tracks the complete immutable settings value and the
  geo-load status as well as the query.

## Verification and boundaries

Regression tests exercise failed-candidate isolation, cancellation of running and queued work,
executor shutdown, a shared reclaim deadline with multiple unkillable holders, and invalid
polling intervals. Existing tests continue to cover stale-core reaping and foreign-process
protection. Source integrity, Compose scope and Kotlin structure checks run before submission;
GitHub source verification is the gate for JVM tests and debug/release Kotlin compilation.

These changes remove unnecessary waits and resource contention; they do not claim a measured
throughput or ping improvement. No timeout reduction was applied to actual network probes,
and no certificate, DNS fail-closed or route-identity protection was relaxed. This is a targeted
source audit, not an exhaustive guarantee: device-only UI, VPN permission flows, physical
network handovers and carrier-specific performance still require Android device testing.
