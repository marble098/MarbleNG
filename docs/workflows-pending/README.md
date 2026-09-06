# Workflow files that need a manual replace

The CI token used for this branch cannot write `.github/workflows/*` (GitHub refuses a
`workflows`-less App token), so the two workflow changes for V151 are staged here instead.

Copy each file over the one in `.github/workflows/` with the same name:

| File here | Replace | What changed |
| --- | --- | --- |
| `verify.yml` | `.github/workflows/verify.yml` | the core-lock step now requires `.singbox.tag`/`.singbox.repo`, and a new **Core versions agree everywhere** step fails the PR if any pin is missing, if gradle or `prepare-native.sh` stops reading `core-lock.json`, if the sing-box channel stops being `beta`, or if a hard-coded version string appears outside the lock |
| `update-cores.yml` | `.github/workflows/update-cores.yml` | a new **Validate the resolved lock** step runs before the push to main (this job writes `main` directly, so a half-resolved lock must die here), and the commit message names all three cores |

Until they are in place the PR gate still runs, it just does not yet assert the sing-box pin.
