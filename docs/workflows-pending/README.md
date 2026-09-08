# Workflow files that need a manual replace

The CI token used for this branch cannot write `.github/workflows/*` (GitHub refuses a
`workflows`-less App token), so the workflow changes for **MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157**
are staged here instead. Each file below is complete and derived from the current live workflow, so
replacing is a copy, not a merge.

Copy each file over the one in `.github/workflows/` with the same name:

| File here | Replace | What changed |
| --- | --- | --- |
| `build.yml` | `.github/workflows/build.yml` | the signed build now clones the pinned sing-box source into `.bootstrap/singbox` and checks out `.singbox.commit`, because the Android core is compiled rather than downloaded; `setup-go` caches both pinned modules (`cache-dependency-path` lists the Xray and sing-box `go.sum`); the fork's Go requirement is printed before the toolchain is resolved; job timeout 60 → 90 minutes for a job that compiles a second core |
| `verify.yml` | `.github/workflows/verify.yml` | the lock step also requires `.singbox.commit` (40 hex) and `.singbox.patch`, and requires exactly one digest whose key is the `linux-amd64` host artifact; the source-build invariants assert `prepare-native.sh` injects the backport, runs `go test`, builds via `build_singbox` and no longer names any `sing-box-*-android-*` release asset; a new **sing-box Android CLI crash-fix native smoke** step clones the pinned commit, injects, greps the marker at all five sites, re-injects to prove idempotence and runs `go test ./protocol/direct ./route`; job timeout 25 → 40 minutes |
| `update-cores.yml` | `.github/workflows/update-cores.yml` | the resolved lock is validated for `.singbox.commit`/`.singbox.patch`/single-digest, and a new pre-push step runs the injector as a dry run so anchor drift kills the updater *before* it commits a lock nobody can build (this job writes `main` directly) |

Until they are installed the PR gate still runs; it just does not yet assert the sing-box source
pin, and the native build still downloads the prebuilt Android artifacts. That is the state
`scripts/system-integrity-check.py` reports against: its workflow invariants read the staged copy
when one exists, so a change that is written but not yet installed is still checked.

The previous batch staged here (V151: `.singbox.tag`/`.singbox.repo` in the lock step, **Core
versions agree everywhere**, and **Validate the resolved lock**) has been installed — both steps are
present in the live workflows today, and the files above keep them.
