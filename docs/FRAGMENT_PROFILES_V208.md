# MARBLE_FRAGMENT_PROFILES_V208 — Fragment & Mux, rewritten

*Core:* `app/src/main/java/com/marbleng/app/core/TransportAdaptation.kt` (`FragmentProfile`,
`MuxProfile`, `applyUserChoice`), `app/src/main/java/com/marbleng/app/core/SingBoxTransportPolicy.kt`
*Tests:* `app/src/test/java/com/marbleng/app/core/FragmentProfileLadderV208Test.kt`,
`app/src/test/java/com/marbleng/app/core/SingBoxTransportPolicyV208Test.kt`
*Wiring:* `AppRepository.effectiveSettingsFor` → `TransportAdaptation.applyUserChoice` (last);
`SingBoxTransportTranslator` → `SingBoxTransportPolicy.applyFragment`;
`SingBoxConfigBuilder` → `SingBoxTransportPolicy.multiplex`.


## The report

> The current fragment and mux system does not work at all. Criticise it harshly and rewrite it
> completely: a fragment system with several ready-made selectable profiles in settings, plus
> custom settings.

The report is accurate. It was not a feature with rough edges; on one of the two cores it wrote
nothing at all, and on the other it wrote a value that nothing downstream was obliged to keep.

## The critique

**1. The user's choice was an input, not a decision.**

`AppRepository.effectiveSettingsFor` is the single funnel every config passes through. Inside it,
the automatic policies ran *after* the user's settings were read: `DpiEvasionPolicy.heal` called
`applyRecipe`, which set `fragmentEnabled = true` and overwrote every fragment number with the
recipe the DPI ladder had chosen; `IranShield.apply` did the same on Iran Mode links and
additionally forced `muxEnabled = false` for Vision and REALITY nodes. The learner ran only when
`transportAdaptationEnabled` was on and the mode was `AUTO` — off by default.

So on a filtered link, the two situations where fragmentation matters, the numbers in Settings were
replaced on the way to the config builder. A user could set "1-1 / 1 / 4 / 517", connect, and get
whatever the policy had decided — with the page still showing their own values, because the page
reads `AppSettings` and the core reads the effective copy. **The setting could not fail visibly.**

**2. On sing-box the recipe was reduced to one boolean.**

The pinned core is `shtorm-7/sing-box-extended v1.14.1-extended-2.7.2` (`core-lock.json`). Its
fragmentation lives in the TLS options (`option/tls.go`), and it has exactly three relevant fields:
`record_fragment`, `fragment` and `fragment_fallback_delay`. There is no `packets`, no `length`, no
`interval`, no `maxSplit`, and **no `fragment` outbound type at all** — that was verified against
the upstream tree before a single line was written here.

The translator did this with all of it:

```kotlin
if (settings.fragmentEnabled) result.put("fragment", true)
```

Every number the user chose was discarded, and the one boolean that survived was the *expensive*
one: the core documents `tls.fragment` as packet-level handshake fragmentation with "poor
performance", tells you to "try `record_fragment` first", and — because Android gives the process
no `CAP_NET_RAW` — makes every handshake wait `fragment_fallback_delay` (500 ms by default) instead
of measuring the real wait. Turning that on for every profile is a half-second handshake tax
presented to the user as "fragmentation".

**3. Multiplexing on sing-box wrote nothing.**

Xray's Mux.Cool and sing-box's smux are different wire protocols, so the honest note was "not
compatible". But the core does have a `multiplex` object (`option/multiplex.go`: `enabled`,
`protocol` ∈ {h2mux, smux, yamux}, `max_connections`, `min_streams`, `max_streams`, `padding`),
supported on the vless, vmess, trojan and shadowsocks outbounds — and the fields map cleanly. The
Mux card was therefore decorative on that engine: a switch that changed no byte.

**4. There was nothing to choose.**

Two checkboxes and four raw numeric fields. The four fields are `packets`, `length`, `interval`,
`maxSplit` — a vocabulary that requires reading the Xray source to use. Eight working recipes were
encoded nowhere in the product, so the page asked the user to invent a fragmentation strategy.

## The rewrite

### A ladder of ready recipes, and a Custom rung

`FragmentProfile` is now an ordered ladder, each rung a complete recipe:

| id | label | packets | length | interval | maxSplit | strength |
| --- | --- | --- | --- | --- | --- | --- |
| `off` | Off | — | — | — | — | 0 |
| `tlshello` | ClientHello split | tlshello | 100-200 | 10-20 | — | 1 |
| `record_split` | Fine record split | tlshello | 40-80 | 5-10 | — | 2 |
| `gfw_knocker` | Packet split (GFW-knocker) | 1-3 | 1-3 | 5-10 | — | 3 |
| `skip_chain` | Official skip-fragment chain | 1-1 | 130 | 560 | 4 | 4 |
| `full_fragment` | Full fragment (517) | 1-1 | 1 | 4 | 517 | 5 |
| `steel_cascade` | Steel cascade (split → shred) | 1-3 | 1-3 | 5-10 | inner 1-1 | 5 |
| `extreme` | Extreme micro-fragment | 1-1 | 1 | 2 | 517 | 6 |

`MuxProfile` is the same shape for multiplexing: `off`, `light` (4), `balanced` (8), `throughput`
(16), `udp_heavy` (8 + UDP on 443), `stealth` (2).

Each rung carries **one sentence** of copy — `FragmentProfile.summary` — because the product's copy
rule is one option, one sentence, and a recipe the user cannot picture from its row is a recipe they
will never pick.

### Three states, not two

`AppSettings.fragmentProfileId` distinguishes:

- **blank** — *no opinion*. The automatic policies decide. A fresh install must not silently mean
  "fragmentation off" for every Iran Mode user.
- **`custom`** — the four numeric fields go on the wire exactly as typed.
- **a recipe id** — that recipe's fields go on the wire.

Collapsing those into one boolean was the original bug: `custom` was being snapped to the nearest
named recipe, so a hand-typed 1300-byte length became the 100-200 ClientHello split.
`TransportAdaptation.withCustomFragment` copies the numbers verbatim.

### The choice is applied last

```kotlin
// AppRepository.effectiveSettingsFor
TransportAdaptation.applyUserChoice(tuned, settings, profile)
```

`applyUserChoice` runs after Iran Mode, the DPI ladder and the intelligence engine have had their
say. One recipe materialises every field the two cores, the benchmark engine, the connection tuner
and the wire read-out already read, so there is one code path and no second place to apply a
different recipe.

One veto survives, and it is stated where the choice is honoured rather than silently undone later:
multiplexing on XTLS Vision or REALITY is slower *and* a louder fingerprint, so
`muxIsUnsafeFor` refuses it for a recipe and for hand-typed values alike.

### The sing-box mapping

`core/SingBoxTransportPolicy.kt` is the honest translation, pure and JSON-only:

- `record_fragment = true` whenever a recipe is on — the cheap half, which is what a
  plaintext-matching filter actually chokes on.
- `fragment = true` + `fragment_fallback_delay` only from strength 3 up, where the recipe is
  explicitly asking for the wire to look wrong at the packet layer.
- the fallback delay comes from the recipe's own interval, clamped into the core's 20–500 ms band,
  because that is the only number in a recipe that means "how long to hold the split".
- off writes **no keys at all**, not `false` — a config the user never touched stays free of fields
  that would only invite a decoder to notice them.
- `multiplex` (the field name the core declares — `mux` would be a fatal decode error) with
  `protocol` = `h2mux` below 12 streams and `yamux` above, `max_streams` = the concurrency,
  `max_connections` = one per four streams, `padding` on, and `xudpConcurrency` honestly dropped
  rather than misfiled into a field that does not exist.

## Checks

- `core/FragmentProfileLadderV208Test.kt` — ladder identity and order, one-sentence copy, the three
  states, the choice surviving a policy-shaped base, custom values surviving verbatim, the veto.
- `core/SingBoxTransportPolicyV208Test.kt` — mild recipes must *not* ask for packet fragmentation,
  off writes nothing, the delay band, the smux object, the connection math.
- `scripts/system-integrity-check.py` — the page, the choosers, the ordering
  (`applyUserChoice` after `DpiEvasionPolicy.heal`) and the field names.
