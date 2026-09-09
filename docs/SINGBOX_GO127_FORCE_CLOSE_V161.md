# SING-BOX GO 1.27 FORCE-CLOSE — V161

**Why the build that merged PR #135 (run #247) died at "Building sing-box for arm64-v8a" seven
minutes in, why nothing that guards a pull request could have said so, and why the fix is a
build-time backport of a split the fork already made for its other HTTP transport.**

Engine pin: unchanged — `shtorm-7/sing-box-extended` `v1.14.0-extended-2.7.1`
(`217f77643f19e915ea38ba2a9ac7e28e22a0cec5`) from `core-lock.json`, and the Xray/HEV pins are
untouched too. Nothing about this chapter changes a core's behavior on a device: the patched
force-close does exactly what it did before, through the same fork-authored code path, just
reached the way Go 1.27 wants it reached. The marker every layer of the proof greps for is
`MARBLE_SINGBOX_GO127_FORCE_CLOSE_V161`.

---

## خلاصه (Persian summary)

- **بیلدِ شمارهٔ ۲۴۷ (بلافاصله بعد از ادغامِ PR #135) شکست خورد، ولی تقصیرِ PR نبود.** یک
  ربات ساعت ۰۸:۱۰ صبح همان روز، بدون اجرای هیچ بیلدی، نسخهٔ Xray را از v26.7.28 به
  v26.9.9 برد. ورژنِ Go از روی `go.mod` همان Xray تعیین می‌شود و از `go 1.26` به
  `go 1.27` رفت. هستهٔ sing-box که تغییر نکرده بود، ناگهان با کامپایلرِ تازه بیلد شد و
  در مرحلهٔ لینک مرد: `undefined symbol: golang.org/x/net/http2.(*Transport).connPool`.
- **ریشهٔ خرابی:** از x/net نسخهٔ ۰.۵۷.۰ به بعد، وقتی با Go 1.27 کامپایل شود، پکیجِ
  `http2` دیگر یک پیاده‌سازی مستقل نیست؛ یک پوششِ نازک دورِ http2 داخلیِ خودِ Go است و
  سمبلِ خصوصیِ `(*Transport).connPool` — که کدِ `v2rayxhttp` هستهٔ sing-box با
  `//go:linkname` برای بستنِ اتصال‌های استخرش می‌کشید — دیگر وجود ندارد.
- **چرا خودِ سازندهٔ fork متوجه نشده بود؟** fork همان تبدیل را برای پکیجِ `v2rayhttp`
  انجام داده بود (سه فایلِ جدا برای قبل و بعد از Go 1.27 و حالتِ بدونِ `badlinkname`)،
  اما `v2rayxhttp` را جا انداخته بود؛ و CI خودِ fork روی Go 1.26.7 پین است، پس همیشه
  شاخهٔ قدیمی را می‌بیند و هرگز به لینکِ شکسته نمی‌رسد.
- **راه‌حل:** همان انشعابِ سه‌تاییِ خودِ fork، این بار برای `v2rayxhttp`، به صورتِ
  وصله‌ی زمانِ بیلد (`scripts/inject-singbox-go127-fix.py`) روی سورسِ پین‌شده — مثل
  وصلهٔ کرشِ اندروید که از V157 سر جایش است. رفتارِ هسته روی دستگاه هیچ تغییری نمی‌کند.
- **دیگر هرگز نباید این کلاس از خرابی به main برسد:** هنگامِ بیلد، تستِ لینکِ
  `go test -tags badlinkname ./transport/v2rayxhttp` قبل از کامپایلِ اولین ABI اجرا
  می‌شود؛ در دروازهٔ PR (بعد از نصبِ فایل‌های workflow برگزار شده) همان تست با
  تولچینِ پین‌شدهٔ Xray و یک لینکِ کاملِ هسته اجرا می‌شود؛ و رباتِ به‌روزرسانیِ هسته‌ها
  هر دو وصله را قبل از نوشتنِ قفل روی main خشک‌اجرا می‌کند.

---

## 1. The failure, read backwards from the log

Run #247 (`Build signed Android APKs`, main @ `37f4be7`, the merge commit of PR #135) stopped
at `prepare-native.sh failed at "Building sing-box for arm64-v8a" (exit 1)` with:

```
ld.lld: error: undefined symbol: golang.org/x/net/http2.(*Transport).connPool
>>> referenced by go.go
>>>               go.o:(github.com/sagernet/sing-box/transport/v2rayxhttp.(*DefaultDialerClient).Close)
clang: error: linker command failed with exit code 1
```

PR #135 touched no build script and no core pin — its diff is the app, its tests and its
chapter. What changed between the last green main build (run for PR #134's merge, 3 hours
earlier) and this one was a commit PR #135 never saw:

* `589afca` — `chore(cores): update Xray/HEV/sing-box`, pushed to main at 08:10 UTC by the
  scheduled `Update beta cores` job **with `GITHUB_TOKEN`**, which by design triggers no
  workflow. The lock bump itself was routine and correct: Xray `v26.7.28` → `v26.9.9`.
* `build.yml` does not choose a Go version of its own; it resolves it from the pinned Xray
  (`go-version-file: .bootstrap/xray/go.mod`, `GOTOOLCHAIN: auto` in the native step). Xray
  `v26.7.28` says `go 1.26`. Xray `v26.9.9` says `go 1.27`. The runner had Go 1.27.1.
* `prepare-native.sh` builds sing-box under that toolchain with the fork's release tag list,
  which includes `badlinkname`. On Go 1.27, `golang.org/x/net v0.57.0`'s `http2` package
  compiles as a **wrapper** (`//go:build go1.27 && !http2legacy` in `transport_wrap.go`)
  around `net/http`'s internal http2; the pre-wrapper implementation — including the
  unexported method `(*Transport).connPool` — is behind `//go:build !(go1.27 && !http2legacy)`
  and is simply not in the binary. The `//go:linkname transportConnPool
  golang.org/x/net/http2.(*Transport).connPool` pull in
  `transport/v2rayxhttp/dialer.go:62` therefore names a symbol that no longer exists, and
  the link dies.

The pinned sing-box commit did not change. Its `go.mod` (`go 1.26.4`, `x/net v0.57.0`) did
not change. **Only the toolchain did** — and that is the whole story of this failure: a
pinned third-party core is compiled with a toolchain pinned by *another* project's `go.mod`.

## 2. Why the fork never hit it, and what it did about the other half

The extended fork already knows about the wrapper: `transport/v2rayhttp` was split into

| file | build tag | force-close strategy |
| --- | --- | --- |
| `force_close_legacy.go` | `!go1.27` | the old `connPool` linkname (still valid ≤ 1.26) |
| `force_close_go127.go` | `go1.27 && badlinkname` | unwrap wrapper → `*http.Transport` → internal `*http2.Transport` → pool → `Close()` |
| `force_close_go127_stub.go` | `go1.27 && !badlinkname` | public `CloseIdleConnections()` |

`transport/v2rayxhttp` — the *extended* XHTTP transport, upstream's code with the fork's
force-close bolted on — never got the same treatment. And the fork's own CI cannot notice:
its workflows pin `go-version: 1.26.7`, so the legacy file always wins there. MarbleNG, which
inherits its toolchain from the Xray pin, is the first caller to compile `v2rayxhttp` under
Go 1.27 with `badlinkname`.

## 3. The fix — the fork's own split, ported to v2rayxhttp

`scripts/inject-singbox-go127-fix.py` (run by `prepare-native.sh` right after the V157 crash
backport, and dry-run by the core updater) patches the pinned source before anything
compiles:

* **`transport/v2rayxhttp/dialer.go`** loses the `clientConnPool` mirror and the stale
  `transportConnPool` linkname; `DefaultDialerClient.Close()` now delegates the http2 case
  to `closeHTTP2Connections(transport)` — the same call `v2rayhttp.ResetTransport` makes.
* **`force_close_legacy.go`** (`!go1.27`) carries the deleted code verbatim, so a Go ≤ 1.26
  build is byte-for-byte the old behavior.
* **`force_close_go127.go`** (`go1.27 && badlinkname`) mirrors the fork's own v2rayhttp file:
  `(*http2.Transport).init` unwraps the wrapper to the `*http.Transport` it configured,
  `net/http/internal/http2_test.transportFromH1Transport` (push-linknamed by the stdlib's own
  `net/http/http2.go:557`) hands back the internal `*http2.Transport`, and mirrored layouts
  of `net/http/internal/http2.Transport` / `.clientConnPool` reach the pooled `*ClientConn`s.
  Every pulled symbol and every mirrored field offset was verified against the go1.27.1 and
  x/net v0.57.0 sources, not inferred.
* **`force_close_go127_stub.go`** (`go1.27 && !badlinkname`) degrades to the public
  `CloseIdleConnections()` — the fork's own no-license-to-linkname fallback, and the shape
  the release build would take if the tag list ever drops `badlinkname`.

The injector is idempotent (second run is a no-op), anchor-guarded (a moved anchor kills the
build with the fork's own "never ship an unpatched binary" error), and refuses to run against
a tree that is not the extended fork (`v2rayxhttp` does not exist in upstream sing-box).

## 4. The proof, at three layers

1. **In the release build, before any ABI compiles** — `prepare-native.sh` now runs

   ```
   go test -tags badlinkname -ldflags "-checklinkname=0" ./transport/v2rayxhttp
   ```

   alongside the V157 crash regression tests. Compiling *and linking* the package under the
   release toolchain and the release `badlinkname` tag is the exact operation that failed at
   run #247's first ABI — it now costs seconds, up front.

2. **At pull-request time** (staged in `docs/workflows-pending/verify.yml`): a new
   sing-box pinned-source smoke clones the pinned commit, verifies it is the pinned commit,
   resolves the Go toolchain from the **pinned Xray's** `go.mod` — the same resolution
   `build.yml` performs, which is the thing the old PR gate never replicated — injects both
   backports, checks every marker site, proves idempotence, runs both test invocations, and
   then links the whole core on the host with the release tag set read from
   `prepare-native.sh`. Run #247's failure, replayed against this gate, dies in the smoke
   with the linker's own message at PR time instead of on main after the merge.

3. **Before the lock ever lands on main** (staged in
   `docs/workflows-pending/update-cores.yml`): the core updater dry-runs *both* injectors
   against the tag it is about to pin, so an upstream tag that moves either backport's
   anchors — or an Xray bump that moves the toolchain — fails the one-minute updater job
   instead of the next 40-minute release build.

`scripts/system-integrity-check.py` pins all of it (new V161 invariants): the injector is
wired into all three workflows, the link pin runs with `badlinkname` in both the build and
the PR gate, and the release tag set keeps `badlinkname` and `tfogo_checklinkname0`.

## 5. What is deliberately NOT changed

* **`core-lock.json`** — no pin moves. The Xray bump to `v26.9.9` was legitimate; only the
  sing-box source needed to know how to build under it.
* **The core's runtime behavior** — all three force-close variants end in the same
  `ClientConn.Close()` the old linkname reached. No protocol, no config, no default changes.
* **`SINGBOX_TAGS`** — the list (including `badlinkname`) is the fork's own release list;
  the chapter only documents that the tag is now load-bearing for the force-close too.
* **Live workflows** — this branch's token cannot write `.github/workflows/*`; the two
  workflow changes are staged in `docs/workflows-pending/` per that directory's README,
  derived from the current live files, and the release build works without them (the fix
  itself lives in `prepare-native.sh` and the injector).
