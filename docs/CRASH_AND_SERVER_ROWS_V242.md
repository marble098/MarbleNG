# MARBLE_CONNECT_PILL_CRASH_V242 / SERVER_ROW_ORDER_V242

> Date: 2026-10-29 · Branch: `arena/04f816d8-marbleng`
>
> **Report:** repeated `FATAL EXCEPTION: main — java.lang.IllegalArgumentException: Padding must be non-negative`,
> including immediately after `CONNECTED`, plus disordered server rows.
>
> **Result:** the connect pill can no longer pass a negative animated inset to Compose. Server rows
> keep a stable two-line layout, a reserved ping column, a 48dp action target, profile-keyed swipe
> state, RTL-safe addresses and scrollable action sheets.

---

## 1. The crash: a spring is not a padding contract

The only animated padding in the shell was the connect pill's side inset:

```kotlin
val inset by animateDpAsState(
    targetValue = if (connected) 0.dp else 20.dp,
    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
)
Modifier.padding(horizontal = inset)
```

`MotionScheme.expressive()` spatial springs intentionally overshoot. On `20dp -> 0dp`, the value
passes below zero for a few frames. `Modifier.padding` rejects that frame with
`Padding must be non-negative`. This matches the log timing: the tunnel reaches `CONNECTED`, the
pill morphs, and the next composition crashes. Process restart/reconnect repeats the same frame.

The fix has two layers:

1. The inset uses `defaultEffectsSpec()` — the non-overshooting Material motion family. Height and
   corner keep the expressive spatial spring.
2. `ConnectPillMotion.insetDp(raw)` clamps every frame to `0..20dp` and fails closed on non-finite
   values before the value reaches `Modifier.padding`.

`ConnectPillMotionV242Test` samples the real Material expressive spring and proves the old raw
curve goes negative while the layout boundary never does. `ConnectPillCrashV242Test` drives the real
Compose pill through connect, disconnect and rapid interrupted reversals frame by frame.

## 2. Server rows: one stable grammar

The old row put title, address, ping, check and overflow into one unreserved horizontal line. A long
localized ping label or a long name could squeeze the other columns; a new result changed the row's
width distribution.

The new row is a two-line card row:

| Zone | Rule |
|---|---|
| Leading | 44dp country/server avatar; active/selected check is an overlay, not a new column |
| Title line | weighted ellipsized name + 48dp overflow action |
| Detail line | protocol + isolated host/address, then a reserved 88dp health capsule |
| State | tonal selected/active surface; no red failure field |
| Swipe | state is keyed by `profile.id`; destructive reveal follows layout direction |

The host is wrapped in FSI/PDI (`U+2068`/`U+2069`) so IPv4/IPv6 literals keep network order inside
Persian RTL copy. Quick actions and the sort/filter sheet opt into `MarbleBottomSheet(scrollable =
true)` so large text and small screens do not hide the last actions.

`ServerRowLayoutV242Test` inflates the real row under Robolectric/native graphics and checks RTL,
150% text, the 48dp action target, the reserved ping width, and that the ping column does not move
when a result arrives.

## 3. What the other log lines mean

- `libxray.so` SELinux `avc: denied` lines are Android sandbox denials for native probe paths. They
  are warnings, not the fatal exception; this change does not weaken the sandbox or add permissions.
- `ip-family-scan ... UNREACHABLE` / `IPV4_ONLY` are measurement verdicts. They are not UI crashes.
- The repeated process restarts were a consequence of the same padding crash during reconnect.

## 4. Verification

- `ConnectPillMotionV242Test`: raw spatial spring overshoot, safe clamp, effects spring, non-finite frames.
- `ConnectPillCrashV242Test`: real Compose connect/disconnect/interruption frames.
- `ServerRowLayoutV242Test`: real Compose RTL/large-text row geometry and stable ping column.
- Existing JVM suite, Kotlin debug/release compilation, Compose scope and source integrity audits.
- Release workflow now uploads `mapping.txt` as a CI artifact so future obfuscated logcat can be retraced.

## 5. Deliberately not changed

No VPN, DNS, routing, core, permission or subscription behavior was rewritten. The fix is scoped to
the reproduced UI crash and the server-row presentation/order defects around it.
