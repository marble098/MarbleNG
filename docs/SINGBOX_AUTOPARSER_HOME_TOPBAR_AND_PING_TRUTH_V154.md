# MARBLE V154 — Sing-Box Autoparser, Ping Truth, Cancellable Sweeps, New Home Top Bar

Every ping button in the product can lie in the same three ways, and every one of them shipped
in V153: a healthy server reported `FAILED` because the measurement died on transport, not on
the server. This note is the surgery, end to end.

## 1. The automatic Xray → sing-box parser is now truly automatic

The premise hasn't changed since V151: sing-box extended ships its own `parser` outbound that
reads share links, and Marble builds `{type: parser, link: …}` configs whenever a node carries
a link. The problem is that a single reader has a single blind spot, and a blind spot is a dead
node. The V154 rule: **one refusal can never kill a node**.

- `SingBoxConfigBuilder.candidateBuilds()` now returns *every* sing-box config a node can be
  expressed as, in preference order: the core's own parser outbound **and** Marble's own
  translation of the stored Xray JSON (the order flips with `singBoxPreferParser`). A read that
  cannot serve the profile simply isn't in the list.
- `SingBoxManager.start()` and `SingBoxManager.urlTestProfile()` walk the candidates: each
  config is written, handed to `SingBoxConfigDoctor` (the V152 self-heal) and verified with
  `sing-box check`; the **first candidate the core itself accepts** carries the session or the
  measurement. `lastStartStrategy` records which reader won, and the refusal of the losing
  reader lands in `lastSelfHealNotes` so the log explains itself.
- Reader coverage, per hop:

  | Xray shape | Before | Now |
  |---|---|---|
  | vless/vmess `settings.vnext[]` only | ✅ | ✅ |
  | vless/vmess **direct form** (`settings.{address,port,id}` — every PattNG export, every Marble import) | ❌ *"no vnext server"* | ✅ |
  | dropped `users[]` field-by-field | ❌ whole node | ✅ per-field resolution |
  | vmess `packetEncoding` | ❌ UDP silently lost | ✅ `packet_encoding` (packetaddr/xudp) |
  | shadowsocks SIP002 `uot` | ❌ | ✅ `udp_over_tcp{enabled}` |
  | **wireguard** outbound | ❌ untranslatable | ✅ peers/endpoint/keys/reserved/mtu/workers |
  | hysteria v1 vs v2 | by protocol name (wrong for stored hy2 nodes, which are `protocol: hysteria` + `version: 2`) | **explicit `version` wins**, name is the tiebreak |
  | hysteria rates (`up_mbps`, `"100 Mbps"` strings) | raw `toString` | digit-extraction; **v1 forced defaults 10/50** (the core requires both) |
  | hysteria obfs (`finalmask.udp[0]`, `settings.obfs`, `hysteriaSettings.obfs`) | ❌ read nowhere | ✅ v1 raw string / v2 `obfs{type: salamander}` |
  | **tuic / anytls** (link-only nodes, blank `configJson`) | hand to the core parser, pray | translated from the raw link by Marble (`linkOutbound`), so a stale core reader never kills them |
  | WS Host header | `headers.Host` only | `headers.Host` → `headers.host` → flat `host` (Marble's own emitter) |
  | gRPC multiMode | `multi_mode` only | `multi_mode` **or** `multiMode` → `permit_without_stream` |
  | raw `headerType` HTTP disguise | silent `network: "tcp"` write | honest note + plain TCP instead of a rejected field |
  | TLS floors/ceilings, ECH, REALITY key under Marble's `password` spelling, `cipherSuites` pin | ❌ | ✅ min/max version, `ech.config`, `public_key`, pin-note |
  | mux on QUIC / wireguard | core-rejecting field | gated to TCP-family protocols only |
  | URL-test concurrency = TCP workers (up to 20+ throwaway cores) | spawn storm → whole subscription red | capped at 4 native children |

- `SingBoxConfigDoctor`'s engine-level fault markers learned the parser's refusal vocabulary
  (`invalid link`, `unsupported scheme`, `unknown protocol`), so a config-class rejection still
  classifies correctly and the VPN service falls back to the Xray engine instead of marching the
  rest of the subscription through the same refusal.
- 20 tests pin the new contracts in `app/src/test/java/com/marbleng/app/core/
  SingBoxAutoparserV154Test.kt`; the V151 schema contract is untouched and still green.

## 2. "Real delay" — rewritten around one promise

> A server that is alive never reports FAILED.

Two mechanical ways it lied before, both gone:

1. **No live tunnel meant no measurement.** Disconnected, every gate-exempt profile — Hysteria2,
   WireGuard, and *every pasted Xray JSON* (scheme `json`; i.e. precisely a subscription of Xray
   configs) — returned `no-live-tunnel` → FAILED, even though one tap connected it. Now
   `RouteProbe.realDelayHook`, installed by the repository, spawns a throwaway core for the
   candidate and times real HTTPS round trips to the delay URL through that tunnel — the same
   measurement the live session reports, owned by the probe. The hook retries a dead spawn once:
   a spawn storm is not a dead node.
2. **The gate's clock was one second.** The PattNG liveness gate (`TCP_GATE_TIMEOUT_MS`) is a
   desktop budget; on a congested mobile link an alive endpoint regularly needs 2–3 s for a bare
   handshake, and the gate red-lined nodes that were simply far away. The gate budget now follows
   the caller's timeout (`gateBudgetMs`, clamped to 1.5–4 s). It remains a cheap pre-flight for
   endpoints that are *actually* dead: the throwaway core would dial the same destination.

The same dead-spawn mercy was extended to the sweep path: `BenchmarkEngine.measure` retries a
core that never bound its port once before `xray-start` may stand (a genuinely unbuildable
config exits instantly in both attempts, so the retry costs milliseconds there).

## 3. Cancellation everywhere a sweep can start (MARBLE_PING_CANCEL_V154)

A "Ping all" across a big subscription can run for minutes; starting it by mistake had no undo.
Now:

- `AppRepository.cancelProbes()` arms a `probeCancelRequested` flag → `probeCancelling` in UI
  state. It is safe to call at any time (a no-op with no sweep live).
- `BenchmarkEngine.run(shouldStop)` polls the flag at every candidate boundary: queued
  candidates are abandoned, in-flight workers unwind through the normal futures, and **finished
  measurements are kept** — cancel stops what never started, it never erases what was measured.
- Every surface that can start a sweep can end it, from the same spot:
  - **Home top bar** — the pulse button swaps to a filled STOP square while the group sweep is
    live, tapping it cancels.
  - **Servers filter rail** — the "Ping every server" button becomes a red stop square during
    the sweep.
  - **Servers group headers** — a group's ping button toggles to STOP while *that* group is in
    the batch.
  - **Servers progress strip** — carries an always-visible stop control next to the counter and
    says "Cancelling…" while unwinding.

## 4. The Home top bar, redesigned (MARBLE_HOME_TOPBAR_V154)

The three bare circles floating above the banner are now one floating glass capsule:

- **Left:** a 36 dp prism tile cut from the signature ramp with a "marble orb" inside it whose
  rim light takes the session's semantic colour, the MarbleNG wordmark, and a live status line —
  a 7 dp status dot that breathes while the session is live next to Connected / Standby.
- **Right:** three 38 dp tonal round actions — Add (menu), Test (ping ↔ STOP swap with scale-fade
 ), IP details — colour only where the meaning is, 1 dp borders so they stay legible on the glass
  at every theme brightness.

The chrome stays outside the status banner, the wordmark stays first on every Home theme, and
the + menu still anchors under the + (V145 rules unchanged).

## Verification

- `python3 tools/kotlin-structure-check.py` — clean on every touched file.
- New unit tests: `SingBoxAutoparserV154Test` (20 tests).
- Gradle unit tests + `./gradlew :app:assembleDebug` + PR-triggered CI build — see the V154 pull
  request for the green checks.
