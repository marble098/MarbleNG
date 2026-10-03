# Route Atelier V207 — one grammar for the whole UI

`MARBLE_ROUTE_ATELIER_V207` is a design chapter. It rebuilds the interface around one idea: **a
product with plenty of effects and no grammar will spend those effects decorating its own
inconsistencies.** Before this chapter, four Home presentations held four private opinions about what
"connected" looks like, five silhouettes answered five different questions for one verb, a colour
token meant the brand in one file and a state in another, and an animation switch stopped some loops
while other code paths ignored it entirely.

A written review of the app's interface, argued from the code at `main@d703584`, named eighteen
defects and proposed a redesign ("Marble — Route Atelier"). This document is what was done about it,
what was deliberately not done, and — because the review is right that a design claim nobody can test
is a mood board — how little of it could be verified here.

The review's own conclusion is the one this pass accepts as its frame: the product does not lack
taste, it lacks a grammar. Effects were chosen surface by surface, so each new surface re-answered
questions the last one had already answered, badly and differently. Nothing below therefore starts by
repainting anything. It starts by writing the answers down where code can hold them to them.

## 1. The contract, as code

`ui/MarbleDesignContract.kt` is new, and it is deliberately pure Kotlin: every rule in it is
assertable off-device, which is the only way a design rule survives its next pleasant-looking
neighbour. Seven sections:

| Section | The question it answers | Where it is now obeyed |
|---|---|---|
| `MarbleTapTarget` | How big is anything actuable? | `PrismIconButton`, `PrismButton` (compact), `HomeBareAction`, the Atelier's text actions |
| `MarbleRouteState` + `connectVerb()` | What state is the connection in, and what is the verb? | Round, Slide, Power, Stream, Floating, the status card's copy, the deck's action |
| `MarbleStateHue` | Which colour means which fact? | every state surface, replacing per-style tone functions |
| `MarbleRoutePath` | How much of the route can be proven? | the Atelier signet |
| `MarbleLocationTrust` | What may a flag claim? | `ProtocolTile`, `IosStatusWideCard`, `IosServerItemRow`, the Servers row |
| `MarbleMotionPolicy` / `MarbleConnectMotion` | May this move, and by how much? | press travel, entrance cascade, ambient field, the round shutter's pop |
| `MarbleFeedbackPolicy` | How long must a result stay readable? | the root message effect + `HomeRuntimeNotice` |

`MarbleDesignContractV207Test` pins them: state precedence, the six-verb table, six distinct hue
roles, the trust ladder (a label emoji may never be a flag; a lone witness may be a flag but never a
confirmation), the 48 dp floor, press-travel ordering, the entrance cap, the ≤1.03 pop bound, the
dwell numbers, and — for the Persian UI — that every new literal has an owned translation rather
than a transliterated one.

## 2. What each finding got

**1 — four styles, two card families, no grammar.** The `when`-per-style tone functions
(`homeTone`, `styleConnectedTone`, `styleStateTone`) are deleted, not deprecated: they were the
mechanism of the drift. Every state surface now resolves one `MarbleRouteState` and reads hue, verb
and enabled-ness off it. A fifth presentation, **Route**, is added (§3) as the grammar made
visible; the four existing ones are untouched and still selectable.

**2 — the connect control had five shapes, and the round one held six signals.** The shapes stay
(feature preservation, §4). The round shutter was restrained, not removed, by four subtractions inside
it: the state-change pop comes down from 1.05 of a 168 dp disc to `MarbleConnectMotion.statePopPeak` =
1.02; the press travel is `MarbleControlKind.Primary.pressScale` instead of a number the control
invented; the tap ripple and the connected breathing halo are gated on `motion.acknowledges(Primary)`,
so under reduced motion the ripple never launches and the halo holds one fixed alpha; and one of the
two stacked `Brush.radialGradient` backgrounds on the face is gone, leaving the caller's `haloBrush` or
a single fallback. What was already right is left alone and named as such: the busy securing arc has run
on the shared motion clock since V186, and the state colour already transitions through
`MarbleMotionSpecs.Color` — this pass changed neither, because neither was broken. The review's argument
— *each signal is defensible, six at once for one tap is a display around a verb* — is exactly
correct, and it is answerable with subtraction inside the control rather than by deleting the control.

**3 — slide-to-connect: 78 % threshold, direction by state, `LayoutDirection.Ltr` forced, and the
command fired after the animation.** The ordering is the defect, and it is fixed: `ConnectButtonSlide`
dispatches `onToggle()` first and animates the knob afterwards; the Theme-1 slider already committed
immediately and was left alone. With reduced motion the knob `snapTo`s its end instead of travelling.
The track carries an `onClick(label)` accessibility action, so a screen-reader user gets a button, and
the caption now names the direction the verb needs ("slide right to connect" / "slide left to
disconnect") instead of the generic "slide to act". **The deliberate LTR pin stays**: this control's
thumb is a physical, screen-space object, and mirroring it would ask the user to move their finger the
other way for the same gesture; the reason is now written above the line that pins it.

**4 — touch targets were 46/36/42 with no floor.** `Modifier.marbleTapTarget()` is a layout node that
raises the *measured box* to 48 dp and centres the artwork inside it, so a 30 dp icon keeps its look
and gets a thumb-sized hit area. It is applied wherever a control's own size was the constraint
(`PrismIconButton`, `HomeBareAction`, the Atelier's text actions, `ControlHeight` raised to 48 dp).
One documented exception, §4.

**5 — press = shrink + translate + ripple + corner morph + spring, at every size.** Press travel is
per control *class* (`MarbleControlKind.pressScale`) instead of per call site, and `acknowledges(kind)`
is the single question a control asks before spending a frame on an acknowledgement. Icon buttons no
longer scale to their own private 0.93.

**6 — `PrismIconButton.descriptiveLabel` defaulted to `""`.** It is now a required parameter with no
default, and it localises through `isFaLexiconKey`: the product's own literals get the lexicon,
composed copy ("More actions for Frankfurt-03") passes through untouched instead of being
transliterated into nonsense. A nameless control is no longer expressible.

**7 — the app deleted its own feedback.** The root effect that cleared `repo.message` the instant the
app went idle is replaced by a dwell (`MarbleFeedbackPolicy.OutcomeDwellMs`, re-checked against the
value it is about to clear), and the message finally has a reader: `HomeRuntimeNotice`, an in-flow,
dismissible line inside the Home header column — regex-compacted to 180 characters, two lines,
`marbleSpringIn` on arrival, no overlay, no timer painting over content. "No floating messages at
all" was never the right rule; "no duplicate, interrupting message" is, and that is what is now true.

**8 — the Tests shortcut opened Settings and stopped.** `deckActions.onTests` now sets a focus key that
resolves through the same deep-link path Routing already used, and it names the card rather than the tab
(`SettingsPages.TESTS` → `workspace(TESTS, "Testing")`). `SettingsTabPage` resolves a focus request
against its first section, because the whole content of a workspace sits inside a single list item —
there is no per-card scroll to perform. So the rule is the honest one: land at the top when the thing
asked for *is* the first card (which is what the Tests shortcut asks for: the ping method), and do not
pretend to have moved otherwise. The SYSTEM tab's existing `"Notifications"` focus is untouched by this
— it is not a first card, so that link keeps the user's scroll position instead of jumping.

**9 — Settings taxonomy.** Partly addressed and honestly reported as such: the Home & display card,
Appearance and General still overlap in what they promise, and the review is right that a user cannot
predict which one holds the theme. What was done instead of a re-shuffle: the new ambient switch went
into the card whose siblings already answer "how this page behaves" (`homeShowDataUsage`,
`homeShowLiveQuality`), and the Tests entry got its real destination (§2.8). A re-categorisation of
the hub is a product decision about words, not about code, and doing it blind is how a redesign
breaks the muscle memory of the people who use the app daily.

**10 — settings body text was `labelSmall` and ellipsised.** `settingsBodyStyle` moves to `bodySmall`
(14/20), `settingsRowTitleStyle` to `bodyMedium`/Medium, `settingsTitleStyle` to `titleMedium`, so the
ramp keeps its steps while the *reading* material stops being caption-sized; the sub-page's
title/subtitle keep two lines rather than one clipped line. The review's phrase — density bought with
legibility — is the whole defect in four words.

**11 — the typeface page promised faces the build does not carry.** Each choice now states what it
resolves to ("Renders as system sans", "Bundled, real face"), and the note under the list says plainly
that no third-party font files ship and a missing face falls back to the nearest system face.
Persian still shapes with Vazirmatn whichever Latin face is picked, and says so in its own line.

**12 — tokens doubled as brand, state and category (`amethystBright == cyan`).** V205 fixed this for
the dock only. Here it is fixed for states: `MarbleStateHue` is a role enum, `stateHue()` maps six
states to six roles, and the test asserts the six roles are distinct, so two palette members holding
one value can no longer silently erase a state's meaning. Brand tokens keep their names for brand
surfaces. Protocol identity remains confined to its tile (§3), which is what "colour must not mean
three things" looks like in practice.

**13 — a server flag overstated its certainty.** `MarbleLocationTrust` splits the three sources that
used to be one fallback chain: a live session report, a measured code (with a provisional/verified
tier of its own), and an emoji inside the node's own name. `buildHomeEvidence` resolves
`flagCode = intel.ifBlank { measured }` — never a label glyph — and paints a flag only when
`mayDrawFlag()`; the label tier now gets the world glyph and a dashed rim on `ProtocolTile`, whose
content description says the location is not verified. Same treatment on the Home status card, the
Home server rows and the Servers list.

**14 — entrance stagger on functional lists.** The ladder has one owner (`ExpressiveMath.staggerDelayMs`,
45 ms, capped at six steps) and `MarbleMotionPolicy.entranceDelayMs` is the switch that used to be
missing from it, so a list with reduced motion on pays zero delay instead of four hundred fifty
milliseconds of queue. The scroll guard already kept a thumb mid-drag from animating at all.

**15 — the animation setting was read once and never re-read.** `ProvideMarbleMotion` now holds a
`ContentObserver` on `Settings.Global.ANIMATOR_DURATION_SCALE` as state, so turning animations off in
system settings while the app is open settles every surface immediately; `MarbleMotionState.animates()`
/ `acknowledges()` are the two questions components ask, and the three that used to be decided locally
(entrance, ambient, press) are answered centrally.

**16 — a full-screen breathing glow that says nothing.** It is now a preference:
`AppSettings.homeAmbientBackdrop`, persisted through `AppStore`, provided once at the app root as
`LocalMarbleAmbientField`, and consumed by `PrismBackdrop`'s glows, the status pip's rings, the
heartbeat trace and the signet's waiting dot. Off keeps every colour, gradient and hairline exactly as
they are and stops the sampling. The AMOLED early-return and the coarse ~15 Hz loop from V193 stay.

**17 — marquee names in the Servers list.** The scroller is gone from the row rather than bounded: a
list is a comparison surface, and a row whose name moves is a row that cannot be compared. Two
settled lines with an ellipsis now carry it, and the full name remains readable and copyable in the
detail sheet, which a one-line scroller never offered.

**18 — an 18 000-line file with no single source of design truth.** `MarbleDesignContract.kt` is the
first place a design answer lives; `ui/MarbleHomeAtelier.kt` is a new presentation written *against*
it rather than inside the old file. That is the beginning of the split the review asks for, not the
end of it: `Aether2026.kt` is still 18 171 lines, and this pass did not attempt to move pages out of
it. Anything that claims otherwise would be a claim about a file it never touched.

## 3. The Route presentation

`HomeStyle.ROUTE_ATELIER` (id `route_atelier`), and it is the default for a fresh install — stored
choices are never rewritten by a default. Order is the order of a user's questions: header, national
event banner, the decision card, the server that decision acts on, measured quality, session totals,
shortcuts.

The decision card holds one sentence of state, the signet, and one button. The button's label and its
behaviour are produced from the same `MarbleRouteState`, so they cannot drift apart:

| State | Status line | Verb | What the tap does |
|---|---|---|---|
| `NO_ROUTE` | "Choose a route" | Add server | opens the Servers page — never a connect attempt |
| `READY` | "Ready to connect" | Connect | `reconnectLastOrAuto` |
| `SECURING` | "Securing route" | Cancel | `stopVpn` |
| `CONNECTED` | "Protected" | Disconnect | `stopVpn` |
| `CLOSING` | "Closing the route" | Wait | refused, and the control does not answer twice |
| `BLOCKED` | "Connection stopped" | Reset | `stopVpn`, with the failure line in the same card |

The signet is device → tunnel → exit. A node lights only on evidence: the device is real as soon as a
route exists, the tunnel lights when the handshake is up, the exit lights when an address has actually
been read — and a caption states how many of the three are proven, so a partial path is a partial
claim and not three quarters of a triumph. A waiting dot breathes only while a stage is genuinely
waiting, and only while the ambient field is on. TalkBack gets the three stages as words, because a
check mark, a hollow ring and a dash are exactly what a screen reader cannot see.

Surfaces follow the restraint rule: one card is one `HomeCloudCard` — opaque fill, one hairline, one
soft brand-cast shadow — and never a shadow plus a coloured frame plus a gradient plus a glow at the
same time. Protocol colour lives inside the 32 dp identity tile and nowhere else on the page.

## 4. Deliberately not done

- **The V205 items** (dock translucency, the scaled page transition, the banner-over-header overlap,
  the grey-fade midpoint) are not re-reported or re-fixed here; they are fixed, and this pass touched
  none of them.
- **Nothing was deleted to make the design simpler.** Four presentations, five connect silhouettes,
  the Modular studio with its reorder/toggle/resize, the protocol fields and the SSH/WireGuard editors
  all remain reachable. A feature is preserved when a user can get to it, not when a field still exists
  in a data class, so the check used here was the call site, not the model.
- **`PrismButton`'s standard height is 48 dp by token, and the floor modifier is applied only to the
  compact variant.** Raising every standard button's *node* would have reflowed the dense chip rows
  that use the same control; that is a trade, not a completion, and it is named where it is made.
- **The slide control's forced LTR** stays, for the reason in §2.3, with the caption stating the
  direction instead of the user having to infer it.
- **Settings categories were not renamed or merged** (§2.9).

## 5. Verification, and its honest limits

There is no JVM, no Gradle and no Android SDK in this environment: **nothing in this pass was
compiled, and no test was executed.** What was actually run is a tree-sitter Kotlin parse gate over
all 237 source files, comparing the multiset of syntax-error nodes against a baseline taken from
`HEAD` — it reports `NEW 0`, i.e. no file was structurally damaged. Every symbol the patch introduced
or called (`marbleRouteStateOf`, `marbleLocationTrustOf`, `serverLocationIsProvisional`,
`homeAmbientBackdrop`, `homeShowLiveQuality`, `liveJitterMs`, `clearMessage`, `styleRouteAtelier`,
the new `Tr` pairs, `MarbleConnectMotion.StateColorMs`, `MarbleMotionState.acknowledges`) was then
grep-verified against its declaration, because two invented APIs had already slipped through earlier
in this work and a parser cannot see an invented name.

The written tests (`MarbleDesignContractV207Test`, the updated `MarbleHomeStyleTest`) encode the
tables above; treat them as claims to be run, not as a green bar. Equally: no statement in this
document asserts anything about frame timings, on-device appearance, RTL layout of the new page, or
how any transition feels, because none of that was observed — only what the code says.

What *was* run beyond the parse gate is the repository's own preflight,
`scripts/system-integrity-check.py`, which is pure Python and therefore executable here: **319
invariants, 319 pass** (now 321, with the two import/scope invariants below). Four of its existing invariants pinned decisions this chapter deliberately
changed, so they were re-pointed rather than deleted, each in the shape of the rule it actually
guards:

| Preflight invariant | Old pin | Now |
|---|---|---|
| product Home default | `DEFAULT = IOS_FLOATING` | `DEFAULT = ROUTE_ATELIER`, plus a new check that the *former* default keeps its id, its implementation and its dispatch arm — a default may move, a presentation may not be orphaned by the move |
| Servers row name | `basicMarquee(` present in `Aether2026.kt` | `basicMarquee` absent from the file, and `ServersNodeCard` settles at two lines with an ellipsis |
| `ProtocolTile` content rule | `fallbackText = nameFlag` | `fallbackText = "🌐"`, `nameFlag` gone from the tile, and `locationTrust.mayDrawFlag()` gating the art |
| (unused import) | — | the now-dead `androidx.compose.foundation.basicMarquee` import was removed |

Eleven V207 invariants were added to the same file, so the grammar cannot quietly rot: the contract is
the only tone/verb/target/feedback owner; the three per-style tone functions stay deleted; the five
silhouettes share one `armed` gate; the slide control dispatches before it animates (checked as an
*order* inside its commit block, which is the actual defect); reduced motion removes travel and queues;
the ambient field is one setting published once and asked in four files; a location answer is never
upgraded from a node's name, all the way down to the resolver's persisted `provisional` flag; the route
presentation reuses the shared feature widgets instead of forking them; the 48 dp floor is in use and
`descriptiveLabel` has no default; and the chapter is pinned by a test class and written down here.

The CI job runs the Gradle compile and the unit tests as its final step, and that step is where this
chapter's code first met a compiler. **It is green** (`Unit tests and Kotlin compilation`, 166 s, on
`b17b324`), and it earned its keep: two defects survived every static review and only a compiler could
see them, both inside the rim the location-evidence work added to `ProtocolTile`.

- `import androidx.compose.foundation.layout.matchParentSize` — `matchParentSize` is a *member of
  `BoxScope`* (`Box.kt:262`), not a top-level function in that package, so the import is an unresolved
  reference; inside the `Box` the modifier needs no import at all.
- `size.minDimension` / `size.width` / `size.height` inside the new `Canvas { }` — `size` there is the
  tile's own `size: Dp` parameter, and a parameter of the enclosing function beats the draw scope's
  metric of the same name, which has no `minDimension`. It reads `this.size` now. `StatusDot` already
  carried a comment about this exact trap; the new code walked straight into it.

Neither is visible to a parser (both files tokenize cleanly, `NEW 0`) nor to a reading of the diff's
added lines (a wrong import is a line that looks fine alone, and the shadowing defect needs the enclosing
signature in view). Both are now pinned: one invariant forbids importing a `Row`/`Column`/`Box` scope
member anywhere in the tree — `main` imports none, so the ban cannot contradict existing code — and
another requires the ring to read `this.size`, in `scripts/system-integrity-check.py` (321 invariants).

How the two were found without ever seeing the compiler's text — the log archive of a job is not
reachable from this side, pushes to `.github/workflows/*` are refused for this token, and five attempts
to forward diagnostics from inside the build into a check-run annotation each died in the build script
itself. So the step was bisected instead, using only the duration of its own conclusion: main plus the
four hand-written files came back green in 156 s, the same tree plus `MarbleDesignSystem.kt` and
`MarbleProtocolIdentity.kt` came back red in 70 s, and `DesignSystem` then read clean line by line —
which left 76 lines to look at, and the two defects were in them.

## 6. Follow-ups this pass leaves open, named rather than hidden

1. Per-card focus inside a Settings workspace needs `SettingsSubPage`'s sections to become itemised
   list entries — today a page's whole body is one `item`, so a focus key can only be honoured when it
   names the first card (which is all the Tests shortcut needs, and the reason §2.8 is written the way
   it is rather than as a scroll-to-index that would have been a lie).
2. `Aether2026.kt` still holds pages, forms, navigation and settings in one file (§2.18). The contract
   gives the split a place to land; the split itself is not done.
3. Settings taxonomy (§2.9) needs a product decision about names before code can group anything.
4. The Route presentation has never been rendered. Its first real run will find something; the shared
   widgets it reuses (`HomeTopActionBar`, `HomeSessionStats`, `HomeShortcutDeck`, `ProtocolTile`) are
   all already shipped elsewhere, which is why the page is mostly composition rather than invention.
5. `homeAmbientBackdrop` defaults to on. If a device-side check shows the backdrop's cost is worth
   refusing by default, the default is one boolean in `AppSettings`.
