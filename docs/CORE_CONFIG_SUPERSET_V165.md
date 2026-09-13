# CORE CONFIG SUPERSET — V165

**Why a subscription of 42 working servers became 42 unusable ones, why the answer was never
"pick a different server", and what it takes for both cores to accept everything their own source
says they accept — without Marble ever inventing a config the user did not import.**

Engine pins: unchanged. `XTLS/Xray-core` `v26.9.9` and `shtorm-7/sing-box-extended`
`v1.14.0-extended-2.7.1` (`core-lock.json`). This chapter does modify Xray — one patched function,
`requiresTransportSecurity` in `infra/conf/xray.go`, applied by
`scripts/inject-xray-config-superset.py` at build time and proven in CI against the pinned tag. The marker every layer greps for is
`MARBLE_CORE_CONFIG_SUPERSET_V165`.

---

## خلاصه (Persian summary)

- **۴۲ سرور وارد شد و هیچ‌کدام قابل استفاده نبود.** پیام نهایی «Unsupported VLESS • pick a server
  with TLS/REALITY» بود؛ یعنی تقصیر سرور. سرورها سالم بودند — سه لایهٔ متفاوت در خودِ ماربل، هر
  کدام با یک کپی دستی از یک قاعده، آن‌ها را رد می‌کرد.
- **کپی اول در preflight سرویس VPN بود** (قاعدهٔ هستهٔ Xray را از نو نوشته بود و لیست IP دامنهٔ
  خصوصیِ کوتاه‌تری داشت)، **کپی دوم در Smart Rank** («VLESS بدون TLS منسوخ است» → خارج از رتبه‌بندی)،
  **کپی سوم خودِ هسته** بود. هستهٔ ماربل حالا این تصمیم را به برنامه واگذار می‌کند؛ پس دو کپی اول
  باید حذف می‌شدند، و شدند.
- **هستهٔ دوم هیچ‌وقت این را رد نکرده بود.** sing-box extended یک VLESS بدون TLS را می‌پذیرد و
  اجرا می‌کند؛ «Engine selection is never switched automatically» یعنی ما هرگز هسته را مخفیانه عوض
  نمی‌کنیم — اما معنی‌اش این هم نیست که یک هسته اجازه دارد کاربر را از کانفیگی که هستهٔ دیگر
  می‌پذیرد محروم کند، آن هم با یک قانون که خودِ هسته‌اش دیگر اجرا نمی‌کند.
- **هیچ کانفیگی بازنویسی نمی‌شود تا امن‌تر به نظر برسد.** TLS اضافه نمی‌شود، transport عوض نمی‌شود،
  هیچ کلیدی حذف نمی‌شود. تنها things that change: پارامترهای لینک که قبلاً خوانده نمی‌شدند حالا
  خوانده می‌شوند، و شکل‌هایی که هسته *اصلاً* نمی‌شناسد (h2/h3/quic، استتار mKCP) با ذکر هستهٔ دیگر
  رد می‌شوند.
- **سرعت:** همان لینک حالا با `multiMode` گرِیس، تنظیمات کامل xhttp/xmux، و `mtu/tti/capacity` برای
  mkcp باز می‌شود؛ و ۳۱ ردِ یکسان در ۱۰ ثانیه به یک رویداد تبدیل شد (`ConfigBlockGuard`)، پس لاگ
  واقعاً چیزی که خراب است را نشان می‌دهد.

---

## The failure, in the order it happened

The diagnostic bundle that opened this work said, in one line: engine `xray`, 42 profiles, and a
`profile-preflight-rejected` for every connect attempt — repeated 31 times in ten seconds for two
profiles. Reconstructing it:

1. `MarbleVpnService.profileCompatibilityIssue` held a hand-written copy of Xray's plaintext rule:
   *VLESS + `security=none` + `encryption=none` + not a private host → refuse*. It ran **before**
   settings were even resolved, so nothing about the user's own preferences could change the answer.
2. Its private-host test was a shorter list than the core's own matcher. `100.64.0.0/10` (Tailscale),
   `0.0.0.0/8`, `192.0.2.0/24`, `::ffff:10.0.0.1`, a dotless `router` — every one of those is private
   to Xray and public to Marble. The disagreement ran in both directions: nodes refused that the core
   would have dialled, and a node the core would have rejected reaching process start and dying there
   instead of being explained in the UI.
3. `ProfileSecurityAuditor.rankEligibility` kept a third copy — "VLESS without TLS/REALITY is
   deprecated" — which removed the same nodes from the rank pool. That is why the app could not even
   *measure* them: the log line `rank-deprecated-hidden` with 42 names is what "no fast node found"
   was made of.
4. The core's own rule, meanwhile, had already been delegated: `scripts/inject-xray-realtime.py`'s
   sibling patch had been planned for exactly this, and the pinned `infra/conf` is the one place the
   answer actually has to come from.

And the second core never refused any of it. sing-box extended dials `security=none` VLESS today.
So the product was, in effect, one pinned-core copy of a rule + two app-level imitations of it.

## What is now true

### 1 · One authority

`core/CoreConfigSuperset.kt` owns the whole question and answers it twice — once for the wire
(`Wire`: `ENCRYPTED`, `POST_QUANTUM`, `PLAINTEXT_PRIVATE`, `PLAINTEXT_PUBLIC`, `UNKNOWN`) and once for
policy (`Verdict(runnable, wire, reason, detail, note)`). The VPN preflight, `ProfilePreflightValidator`
(and therefore the rank pool) and `ProfileSecurityAuditor.rankEligibility` all call it; none of them
keeps a rule of its own any more. `CorePrivateEndpoint` in the same file is a transcription of the
core's matcher — CIDRs, the reserved ranges, the IPv4-mapped case, the `Domain_Domain` suffix rules
and the dotless-name regexp — with the *same* semantics as `common/geodata`, so "private" cannot mean
two things in one app.

Plaintext to a public address is **dialled and labelled** (`note`, surfaced on the Servers row and in
Bug Finder, never rewritten into TLS). It is refused only when the user turns consent off
(Settings → Engine → Dial unencrypted nodes), and then it is refused on **both** engines: a user's "no
plaintext" cannot be engine-dependent. `XrayManager.createProcessBuilder` sets
`MARBLE_ALLOW_UNENCRYPTED_PUBLIC_OUTBOUND=1` for the tunnel, the `run -test` verifier and every
throwaway Rank/Turbo child, so the delegation covers every process the app starts and the standalone
binary keeps upstream's default-deny.

### 2 · Real gaps, named with the engine that has them

Two shapes are not a policy disagreement but a fact about the pinned core, and they are still refused
— with a reason that says where to go:

* `h2`/`h3`/`http`/`quic` transports: `TransportProtocol.Build` answers them with
  `PrintRemovedFeatureError`, so the config cannot load at all;
* mKCP header camouflage: `KCPConfig.Build` parses `header`/`seed` and then never reads them, which
  is worse than a refusal — the node would connect without its camouflage and hang.

`CoreConfigSuperset.coreGapIssue` says so in one sentence each, and those sentences are **literals**
(`CORE_GAP_TRANSPORT_REMOVED`, `CORE_GAP_KCP_CAMOUFLAGE`) so the Persian lexicon can map them: the
lookup is exact-match, and a template is untranslatable by construction.

### 3 · The link is the config, read all of it

`core/ShareLinkParams.kt` replaced `Uri.getQueryParameter` in the importer. It lowercases keys (a
panel that writes `SNI=`/`PublicKey=`/`FP=` was importing as "no security"), understands the alias
sets the ecosystem actually uses (`type|network|net`, `security|sec`, `sni|serverName|peer`,
`fp|fingerprint`, `serviceName|service`, `pbk|publicKey`, …), finds parameters written **after** the
fragment (`vless://…#Name?type=xhttp&security=tls`), percent-decodes UTF-8 and — deliberately — leaves
`+` alone, because `+` inside `pbk` and `mldsa65Verify` is key material, not a space.

Two inference rules were added where a link states a fact and omits its label:

* a link carrying `pbk` is REALITY. Naming `security=reality` for it is not a guess about the server;
  a public key has exactly one meaning, and reading it as plaintext is what produced the refusal;
* `security=tls` is inferred from `tls=1|true|tls` spellings, and `sni` defaults to the host, which is
  what every other client does and what removes Xray's `reality` "missing serverName" load error.

Nothing here can turn a plaintext node into a TLS one: `security=none` written explicitly stays none.
`type|network|net`, `mode|extra|host|path` and the whole `xhttp` tuning table
(`xPaddingBytes/Key/Header/Placement/Method/ObfsMode`, `uplinkHTTPMethod`, `sessionID*`, `seq*`,
`uplinkData*`, `uplinkChunkSize`, `noGRPCHeader`, `noSSEHeader`, `scMaxEachPostBytes`,
`scMinPostsIntervalMs`, `scMaxBufferedPosts`, `scStreamUpServerSecs`, `serverMaxHeaderBytes`, `xmux`),
gRPC's `multiMode`/`idle_timeout`/`health_check_timeout`/`permit_without_stream`/
`initial_windows_size`/`user_agent`, WebSocket's `heartbeatPeriod`/`headers` and mKCP's
`mtu`/`tti`/`uplinkCapacity`/`downlinkCapacity` are now carried into the emitted config instead of
being dropped. The copy is typed against the core's own structs, not guessed: every `Int32Range`
field keeps the link's string form, because that is how a range (`xpaddingbytes="100-200"`) is
written and what the type accepts, while the two fields the core types as plain integers
(`scMaxBufferedPosts`, `serverMaxHeaderBytes`) are written as numbers — a quoted integer there is
`json: cannot unmarshal string into Go value of type int64`, i.e. a fatal load error; `headers` is
flattened to strings because the core types it `map[string]string` and a nested value would fail the
same way. That is the speed half of this chapter: those fields are not cosmetics, they are the
operator's own throughput and padding choices.

VLESS `encryption` is normalised only where it cannot mean anything: `auto`, `none/none`, `zero`,
`plain`, `default` and empty all become `none`, because the core's vocabulary for that field is
`none` or a `mlkem768x25519plus.*` string and anything else is a fatal load error
(`VLESS users: unsupported "encryption"`). A value that *claims* a payload cipher (`aes-128-gcm`) is
not rewritten behind the user's back; it is reported as a gap. The post-quantum form is recognised
structurally (`CoreConfigSuperset.isPostQuantumEncryption`, four dot-parts, `native|xorpub|random`,
`1rtt|0rtt`, key segments that decode to 32 or 1184 bytes — matching `infra/conf/vless.go` exactly),
and it is passed through **un-lowercased**, since its tail is key material.

### 4 · The config the core can load

`core/XrayConfigRepairs.kt` runs at the head of `harden`, `hardenForDelayTest` and every hop of
`composeChain`, so the tunnel, the delay test and the native rank all measure the same bytes. It is a
*repair* pass, not a translation: `proxySettings{tag}` becomes `streamSettings.sockopt.dialerProxy`
(the field `proxySettings` was removed in favour of, and Marble's own chain composer had been writing
the dead name), a missing VLESS `encryption` becomes `none`, a single-member `vnext[0]` is mirrored
into the simplified form the core reads first — `vnext` kept, so a re-export returns what was
imported — a one-member trojan `servers[]` gains its `password`, a document that carries
`tlsSettings`/`realitySettings` while never naming a `security` gets that word written, and quoted
numbers (`"port": "8443"`) are unquoted within a bounded walk. When nothing applies, the caller gets
the original string back byte for byte; a native sing-box document is never touched.

### 5 · A refusal is a state

`core/ConfigBlockGuard.kt` folds repeats of the same (profile, reason) refusal: the first is
reported, a repeat within the window is counted and silenced, each folded repeat doubles the window
(30 s → 60 s → … capped at 10 min), and a *different* reason for the same profile is news at once.
It never decides whether a connection is attempted — reconnect scheduling, failover and the tile keep
their own schedules, which is what makes it safe to put in the connect path at all. What it fixes is
the diagnostic plane: 31 identical rows were not merely noise, they were the reason the DNS demotions
and the `stressed=true` TCP sample in the same file could not be found.

## Contract, boundary, and what was deliberately not done

* **No silent engine switch.** `docs/core-interoperability.md`: engine selection is a user contract.
  Where the selected core genuinely cannot run a node, the refusal says which core can.
* **No security invention.** Marble does not add TLS, does not change a transport, does not touch
  `flow`, does not strip a pin. Repairs are limited to shapes whose meaning is unambiguous
  (`proxySettings`→`dialerProxy` moves a reference; it does not create one).
* **REALITY without a `serverName` in *pasted JSON* is still quarantined** — that path has no link to
  infer from, and the core's loader error there is fatal. Links are different: `pbk` names the security.
* **`packet_encoding` is not emitted.** The pinned core has no such field; writing it into a config is
  how a "compatibility" rename becomes a load error.

## Verification

```bash
python3 scripts/system-integrity-check.py            # V165 invariants + the whole suite
python3 tools/kotlin-structure-check.py $(git diff --name-only | grep '\.kt$')
./gradlew :app:testDebugUnitTest --tests 'com.marbleng.app.core.CoreConfigSupersetV165Test' \
  --tests 'com.marbleng.app.core.XrayConfigRepairsV165Test' \
  --tests 'com.marbleng.app.core.ShareLinkParamsV165Test' \
  --tests 'com.marbleng.app.core.ConfigBlockGuardV165Test'
# The patch itself, against the pinned tag (this is what CI's Realtime Xray smoke job does):
XRAY_TAG=$(jq -r '.xray.tag' core-lock.json)
git clone --depth 1 --branch "$XRAY_TAG" https://github.com/XTLS/Xray-core.git /tmp/xray-pin
python3 scripts/inject-xray-config-superset.py /tmp/xray-pin
cp native/xraypatch/marble_outbound_policy_test.go /tmp/xray-pin/infra/conf/
(cd /tmp/xray-pin && env GOTOOLCHAIN=auto go test -run 'TestMarble' ./infra/conf)
```

On a device, the whole chapter is one action: import the 42-node cleartext subscription, and the
Servers row shows 42 usable nodes, each labelled unencrypted; Smart Rank measures them; the log shows
one `profile-preflight-rejected` per changed fact instead of one per attempt.
