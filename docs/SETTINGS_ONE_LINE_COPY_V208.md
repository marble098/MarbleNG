# MARBLE_SETTINGS_ONE_LINE_COPY_V208 — one option, one sentence

*Core:* `app/src/main/java/com/marbleng/app/ui/MarbleDesignContract.kt` (`object MarbleCopy`)
*Tests:* `app/src/test/java/com/marbleng/app/ui/MarbleCopyV208Test.kt`
*Wiring:* `SettingSwitch`, `SettingsHubCard`, `SettingsHubRow`, `SettingsHubSwitch`,
`SettingsSectionCard` and `SettingsSubPage` all draw `MarbleCopy.oneSentence(trx(subtitle))`.


## The report

> Shorten all the settings descriptions: every option's description must be at most one sentence.

## What was actually on the page

Settings had turned into a document. Rows explained the feature behind the switch, repeated the
title in other words, and then added a warning about a case the user had not hit yet:

> *"Remember resolved addresses and DNS answers between runs. Turn off to write nothing to disk."*
>
> *"Servers you added yourself live here. They are never replaced by a subscription refresh."*
>
> *"Timeout and sample count apply to every method. Servers at once is the direct-method sweep
> concurrency; Real delay runs one throwaway core per server, so its pool is capped by what this
> device can carry — the speed dial widens it inside that bound."*

The last one is 259 characters on a row that reserves two lines. Twenty-nine such rows meant the
page was mostly prose, the switch a reader came for sat below the fold, and nothing on the page
could be scanned: at that length no row is a label, every row is a paragraph, and the eye has no
anchor to jump between.

## The rule

One option, one sentence. Not "shorter prose" — a hard ceiling, because a rule that is a matter
of taste is a rule the next contributor's paragraph quietly violates.

Two things enforce it:

**The copy.** Twenty-nine multi-sentence strings were rewritten to one sentence each, keeping the
one fact that changes what the user does and dropping the rest:

| before | after |
| --- | --- |
| "Servers you added yourself live here. They are never replaced by a subscription refresh." | "Servers you added yourself live here and are never replaced by a subscription refresh." |
| "Timeout and sample count apply to every method. Servers at once is the direct-method sweep concurrency; Real delay runs one throwaway core per server, …" | "Timeout and sample count apply to every method; the width below is the sweep's concurrency." |
| "Home style 4 keeps a Customize row at the top of the page. Turn this off for a clean page; this switch is how it comes back." | "Show the Customize row at the top of Home style 4." |
| "A challenger must beat the current route by this much before it moves. Raise it to stop the route wandering between two servers that measure the same." | "How much better a challenger must measure before the selector leaves the current route." |

**The mechanism.** `MarbleCopy` (`ui/MarbleDesignContract.kt`) owns the rule, and the six settings
primitives that draw a subtitle — `SettingsSubPage`, `SettingsHubCard`, `SettingsHubRow`,
`SettingsHubSwitch`, `SettingsSectionCard`, `SettingSwitch` — all render
`MarbleCopy.oneSentence(trx(subtitle))`. A row that is handed a paragraph prints its first
sentence, so the rule survives the next contribution instead of depending on remembering it.

`oneSentence` is not a dumb truncation:

- it flattens source-wrapped literals first (copy here is concatenated across lines, and without
  that the row would print the source's own line breaks);
- it finds the first *real* sentence end — a period inside a token (`1.14.1-extended-2.7.2`,
  `3.5 GB`, `e.g.`, `vless://`) never ends a sentence;
- it knows the Persian terminators `؛` and `؟`, because the product ships in Persian and a rule
  that only knows Latin ones would let a Persian paragraph straight through;
- it clamps the survivor at 140 characters with a visible `…`, as a safety net for one runaway
  sentence rather than as the thing doing the summarising.

## The two checks

- `ui/MarbleCopyV208Test.kt` pins the mechanism *and* scans `Aether2026.kt` for any remaining
  multi-sentence literal, so a paragraph cannot be reintroduced quietly.
- `scripts/system-integrity-check.py` asserts the six clamped call sites and the marker.

The Persian lexicon was updated for the strings that had entries, so a Persian reader gets the
shortened sentence translated rather than reconstructed word by word from the old one.
