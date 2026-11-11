#!/usr/bin/env bash
# Offline fixtures for scripts/auto-release-plan.sh: a throwaway repository and a fake gh.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

cat > "$tmp/gh" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
[[ "${MOCK_FAIL:-0}" != 1 ]] || exit 1
case "$*" in
  *releases*) printf '%s\n' "${MOCK_RELEASES:-[]}" ;;
  *) exit 2 ;;
esac
MOCK
chmod +x "$tmp/gh"
export PATH="$tmp:$PATH" GITHUB_REPOSITORY=owner/repo MARBLE_RELEASE_NOW=1800000000
unset GITHUB_OUTPUT

repo="$tmp/repo"
git init -q "$repo"
cd "$repo"
git config user.email fixture@example.invalid
git config user.name fixture
git config commit.gpgsign false
git config tag.gpgsign false
mkdir -p app/src/main app/src/test docs
echo base > app/src/main/A.kt
git add -A && git commit -qm base

plan() { bash "$root/scripts/auto-release-plan.sh"; }
field() { sed -n "s/^$1=//p" <<< "$2"; }
expect() { # name, plan output, field, wanted
  local got; got="$(field "$3" "$2")"
  [[ "$got" == "$4" ]] || { echo "$1: $3 is '$got', wanted '$4'"; echo "$2"; exit 1; }
}
lines() { seq 1 "$1" | sed "s/^/$2 /" > "$3"; }
retag() { git tag -d $(git tag) >/dev/null; git tag "$1"; }

# No release at all is a failure, not an invented 0.0.1.
if plan >/dev/null 2>&1; then echo 'Planned a release with no base version'; exit 1; fi

git tag v11.6.8
out="$(plan)"; expect 'released head' "$out" skip true

# Small change -> patch, on the decimal ladder; code is the clock.
lines 40 a app/src/main/A.kt; git add -A; git commit -qm small
out="$(plan)"
expect small "$out" skip false
expect small "$out" level patch
expect small "$out" weight 41
expect small "$out" version_name 11.6.9
expect small "$out" version_code 1800000000

# 11.6.9 + patch carries to 11.7.0, exactly as the history of this repository does.
retag v11.6.9
lines 10 b app/src/main/B.kt; git add -A; git commit -qm more
out="$(plan)"; expect carry "$out" version_name 11.7.0

# Docs, tests and CI do not ship: weight 0 is still a patch release of the merge.
retag v11.7.0
lines 9000 d docs/big.md; lines 9000 t app/src/test/T.kt; git add -A; git commit -qm docs
out="$(plan)"
expect docs "$out" weight 0
expect docs "$out" version_name 11.7.1

# Medium -> minor, large -> major.
lines 3000 m app/src/main/M.kt; git add -A; git commit -qm medium
out="$(plan)"; expect medium "$out" level minor; expect medium "$out" version_name 11.8.0
lines 20000 x app/src/main/X.kt; git add -A; git commit -qm large
out="$(plan)"; expect large "$out" level major; expect large "$out" version_name 12.0.0

# 11.9.x + minor carries into the next major.
retag v11.9.4
git commit -q --allow-empty -m again
lines 3500 n app/src/main/N.kt; git add -A; git commit -qm medium2
out="$(MARBLE_RELEASE_MINOR_LINES=3000 plan)"; expect 'minor carry' "$out" version_name 12.0.0

# A draft release newer than every local tag is the base (its tag must exist to diff against).
git tag v11.9.5 HEAD~1
out="$(MOCK_RELEASES='[{"tag_name":"v11.9.5","draft":true,"body":"- Android `versionCode`: `1793542931`"}]' plan)"
expect 'draft base' "$out" base_tag v11.9.5
expect 'draft base' "$out" level minor

# versionCode never goes backwards, even with a clock behind the last release; a code above
# Android's ceiling in some old notes is ignored rather than followed.
out="$(MOCK_RELEASES='[{"tag_name":"v11.9.5","body":"- Android `versionCode`: `1900000000`"},{"tag_name":"v1.0.0","body":"versionCode: 99999999999"}]' plan)"
expect 'monotonic code' "$out" version_code 1900000001
out="$(MOCK_RELEASES='[{"tag_name":"v11.9.5","body":"x"}] [{"tag_name":"v1.0.0","body":null}]' plan)"
expect 'paginated' "$out" version_code 1800000000

# A release whose tag is missing locally cannot be diffed: fail, do not guess.
if MOCK_RELEASES='[{"tag_name":"v99.0.0"}]' plan >/dev/null 2>&1; then echo 'Planned against a tag that is not in the checkout'; exit 1; fi
if MOCK_FAIL=1 plan >/dev/null 2>&1; then echo 'API failure accepted'; exit 1; fi
if MOCK_RELEASES='{}' plan >/dev/null 2>&1; then echo 'Invalid release list accepted'; exit 1; fi
if MARBLE_RELEASE_NOW=2100000001 plan >/dev/null 2>&1; then echo 'versionCode above the ceiling accepted'; exit 1; fi

echo '[OK] Automatic release plan fixtures'
