# MARBLE_FRAGMENT_MUX_PAGE_V206 — Fragment & Mux becomes one section

*Settings:* `Settings › Engine › Fragment & Mux` → **its own page** (`SettingsPages.TRANSPORT`)
*UI:* `SettingsTransportPage`, `FragmentMuxWireCard`, `FragmentMuxEntryCard` in `Aether2026.kt`
*Core touched:* `AppRepository.updateSettings` (coalesced writes), `SmartNotifier.ensureChannels`

---

## 1. What the section was

Engine & tunnel carried **two cards with the same title**:

| Card | Content | Problem |
|---|---|---|
| `Fragment & Mux` (Amber, SPARK) | the fragment/Mux values | the values the user typed |
| `Fragment & Mux` (Amethyst, SHIELD) | the learner | the thing that **overrides** those values |

Two controls with one name is not a hierarchy, it is a guess-the-card game — and they
contradicted each other by construction: with the learner in Automatic mode the numbers in the
first card were never the numbers on the wire, and neither card said so.

## 2. What it is now

One page, opened from one card, in the order the questions are actually asked:

1. **"What is on the wire now?"** — `FragmentMuxWireCard` prints the effective
   (fragment, Mux) pair, *who chose it*, and the learner's reason. A settings page that opens
   with inputs instead of with the state those inputs produced makes the user infer the state,
   which is precisely how the two old cards read as contradictory.
2. **"Who decides?"** — the learner: memory, mode, drift, forget.
3. **"What are the values?"** — the manual knobs, headed "Fallback when the learner has
   nothing yet" when the learner owns the wire, so the fields say what they are *for*.

The Engine card is now a single door whose subtitle answers the same question
(`Learning • 12 remembered` / `Your values • learner off`) without being opened.

## 3. Performance: the critique, and what was fixed

### 3.1 Every keystroke rewrote every setting

Each of the three Fragment fields called `updateSettings` **per character**. That method ends
with `store.saveSettings(settings)` — a ~250-key `SharedPreferences` editor, built and
`apply()`d on the calling thread — and then `notifier.ensureChannels()`, which is four binder
calls into `system_server` that make the system process write its own XML.

Typing `100-200` therefore cost **nine full rewrites of every preference in the product** plus
nine channel re-creations, on the main thread, inside 900 ms of typing. Dragging the Mux
concurrency slider did the same at frame rate.

**Fixed at both ends:**

* `SmartNotifier.ensureChannels()` now runs **once per process**. The channel table is a
  compile-time constant; re-creating an identical channel is a no-op the framework still has to
  parse, persist and re-sort. The flag is set only on success, so a failed attempt is retried.
* `AppRepository.updateSettings(v, coalesceWrite = true)` applies the change to memory
  *immediately and synchronously* (the UI never lags a keystroke, nothing can be lost by
  navigating away) and schedules the disk write on a single-thread writer with a trailing 400 ms
  debounce. A burst of typing or a whole slider drag costs **one** rewrite, **off the main
  thread**. `flushSettings()` forces it — called from `MainActivity.onStop` and before any
  immediate write, so the only thing that can be lost is the last 400 ms after a process kill.
* Every control in the section that produces a *stream* of values (three text fields, two
  steppers) passes `coalesceWrite = true`. Switches stay immediate: one tap, one write.

### 3.2 The memory list was rebuilt inside composition

`TransportAdaptationSettings` called

```kotlin
memory.values.sortedByDescending { it.updatedAtMs }.take(8).forEach { record -> ... }
```

and, inside each row, `String.format(Locale.US, "%.0f", …)` — **during composition**. The card
recomposes on every frame of the switch animation, on every keystroke anywhere on the page, and
on every memory write; each time it re-sorted up to `MAX_CELLS` (96) entries, re-parsed eight
pair ids into profile enums, and ran eight `Formatter` passes (each allocating a `Formatter`, a
`StringBuilder` and a `DecimalFormatSymbols`) on the UI thread.

**Fixed:** the rows are derived once per change of the memory behind `remember(memory)` into
eight `TransportMemoryRow` values that hold only `String`s and `Int`s. The row's own composition
now does no sorting, no formatting and no enum lookup, so it can recompose as often as the page
likes.

### 3.3 The pair shown on the wire was recomputed in composition

`TransportAdaptation.pairFromSettings` walks both profile enums. It is now behind
`remember(settings, decision)`.

## 4. Usability: the critique, and what was fixed

| Was | Now |
|---|---|
| Two cards, one title | One page, one door |
| The learner silently replaced the typed values | The wire card says who chose the pair and why |
| Fields accepted `1--3`, `abc`, `100-200-300` and stored them; the core then ignored them, so "fragmentation does not work" | `fragmentFieldError` says which field cannot go on the wire |
| No indication that the values are only a fallback | The values card's subtitle says so when the learner owns the wire |
| Eight memory rows with no cap in the UI | `TRANSPORT_MEMORY_ROWS`, derived once |
