# Workflow files that need a manual replace

The CI token used for this branch cannot write `.github/workflows/*` (GitHub refuses a
`workflows`-less App token), so the workflow changes for **MARBLE_SINGBOX_GO127_FORCE_CLOSE_V161**
are staged here instead. Each file below is complete and derived from the current live workflow, so
replacing is a copy, not a merge.

Copy each file over the one in `.github/workflows/` with the same name:

| File here | Replace | What changed |
| --- | --- | --- |
| `verify.yml` | `.github/workflows/verify.yml` | a new **sing-box pinned-source smoke** (three steps) clones the pinned sing-box commit from `core-lock.json`, resolves the Go toolchain from the pinned *Xray's* `go.mod` — the exact resolution `build.yml` performs — injects all three backports (V157 crash fix, V161 Go 1.27 force-close, V164 certificate pinning), checks every marker site, proves injector idempotence, runs the injected regression tests plus the `-tags badlinkname` link pin on `./transport/v2rayxhttp`, and links the whole core on the host with the release `SINGBOX_TAGS` read from `prepare-native.sh`; job timeout 25 → 40 minutes. This is the gate that would have caught build run #247 (the post-merge link failure of PR #135) at PR time: the core-lock bot's `GOTOOLCHAIN`-moving Xray bump lands on main with no build of its own, so a PR gate that never links the pinned sing-box under the pinned toolchain cannot see what the release build will do |
| `update-cores.yml` | `.github/workflows/update-cores.yml` | the pre-push "Prove the Android crash backport still applies" dry run now also dry-runs `scripts/inject-singbox-go127-fix.py` and `scripts/inject-singbox-tls-pinning.py` (V164), so a sing-box tag that moves the force-close anchors or the pinning anchors — or an Xray bump that moves the toolchain that makes the force-close block fatal — kills the one-minute updater before it commits a lock nobody can build. It also requires `.singbox.commit`/`.singbox.patch`/single-digest in the resolved lock (from the earlier V157 batch, kept) |

Notes for whoever installs them:

* `build.yml` (still the file to replace `.github/workflows/build.yml` with, but see the
  caveat) is staged from the earlier V157 batch and is **stale against the live file**: it
  describes a sing-box clone into `.bootstrap/singbox` that the current
  `scripts/prepare-native.sh` (which clones into `.cores/singbox-src` itself) never creates, and
  it predates the live V181/V182 release-publishing changes. It needs re-deriving from the live
  file before it is installed — or simply deleting from this batch, since the V161 fix needs no
  `build.yml` change (`prepare-native.sh` does everything). It is kept only because
  `system-integrity-check.py` still asserts the two-module `setup-go` cache key against the
  staged copy.
* After copying, `bash -n` the run blocks of the installed files before pushing — neither
  GitHub nor the integrity audit syntax-checks `run:` blocks. (The copies here have been
  YAML-parsed and `bash -n`-checked.)
* The V161 release-path fix itself (injector + `prepare-native.sh` link pin) does **not** wait
  for this install: it is live the moment this PR merges, because it lives in `scripts/`.

Until they are installed the PR gate still runs; it just does not yet link the pinned sing-box
under the pinned toolchain, and the core updater only dry-runs the V157 crash backport. The
`run #247` failure class is closed on main the moment this PR merges (the release build now
pins the link before the first ABI); the staged files close it at PR time and in the updater.

## MARBLE_SINGBOX_PINNED_PEER_V164 additions to this batch

The `verify.yml` copy in this directory now also covers the certificate-pinning backport:
the smoke applies `scripts/inject-singbox-tls-pinning.py` as the third injector, greps its
marker sites (`option/tls.go`, `common/tls/pin_verify.go`, and the `pcs`/`vcn` cases in
both link parsers), proves its idempotence, unit-tests the stdlib-only verifier from
`native/singboxpatch` on the pinned toolchain (`go test ./...`), and the whole-core link
step compiles the patched std/uTLS clients and parsers under the release tags — the same
gates `scripts/prepare-native.sh` applies before the NDK build.
