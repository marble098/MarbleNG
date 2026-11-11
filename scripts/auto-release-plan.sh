#!/usr/bin/env bash
# MARBLE_AUTO_RELEASE_V278 — choose the next release identity from the size of the change.
#
# Called by .github/workflows/auto-release.yml after every push to main (a merged pull
# request is a push to main). It only *chooses*; the build, the collision check, the tag and
# the draft release stay in build.yml, which this workflow dispatches with the two values.
#
#   base     the highest vX.Y.Z among the repository's tags and releases (drafts included)
#   weight   lines added + removed between base and HEAD in what ships inside the APK
#            (docs, Markdown, tests, CI and fixtures do not count)
#   level    weight >= MARBLE_RELEASE_MAJOR_LINES (20000) -> major
#            weight >= MARBLE_RELEASE_MINOR_LINES (3000)  -> minor
#            otherwise                                    -> patch
#   version  the project's decimal ladder: minor and patch run 0..9 and carry
#            (11.6.9 -> 11.7.0, 11.9.9 -> 12.0.0), as every release so far has
#   code     max(current Unix time, highest recorded versionCode + 1), so an installed
#            APK always sees an upgrade
#
# Output: key=value lines on stdout (and in $GITHUB_OUTPUT when set):
#   skip, reason, base_tag, weight, level, version_name, version_code
set -euo pipefail

: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
head_ref="${MARBLE_RELEASE_HEAD:-HEAD}"
minor_lines="${MARBLE_RELEASE_MINOR_LINES:-3000}"
major_lines="${MARBLE_RELEASE_MAJOR_LINES:-20000}"
now_epoch="${MARBLE_RELEASE_NOW:-$(date -u +%s)}"
code_ceiling=2100000000
semver='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'

emit() {
  printf '%s=%s\n' "$1" "$2"
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then printf '%s=%s\n' "$1" "$2" >> "$GITHUB_OUTPUT"; fi
}
fail() { echo "::error::$*" >&2; exit 1; }

for value in "$minor_lines" "$major_lines" "$now_epoch"; do
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || fail "Thresholds and clock must be positive integers, got '$value'."
done
(( minor_lines < major_lines )) || fail "The minor threshold must be below the major threshold."

head_sha="$(git rev-parse --verify "${head_ref}^{commit}")"

# Releases carry the recorded versionCode in their notes; drafts are included on purpose —
# a draft's tag and code are taken, exactly as scripts/release-version.sh treats them.
releases="$(gh api --paginate "repos/$GITHUB_REPOSITORY/releases?per_page=100" | jq -s 'add // []')"
jq -e 'type == "array"' <<< "$releases" >/dev/null || fail "Invalid GitHub response while listing releases."

all_tags="$( { git tag --list 'v*'; jq -r '.[].tag_name // empty' <<< "$releases"; } \
  | grep -E "$semver" | sort -Vu || true)"
base_tag="$(tail -n 1 <<< "$all_tags")"

if [[ -z "$base_tag" ]]; then
  fail "No vX.Y.Z tag or release exists yet; run 'Build signed Android APKs' once by hand to set the first version."
fi

base_sha="$(git rev-parse --verify --quiet "refs/tags/${base_tag}^{commit}" || true)"
if [[ -z "$base_sha" ]]; then
  fail "Release $base_tag has no tag in this checkout (fetch-depth: 0 is required)."
fi

if [[ "$base_sha" == "$head_sha" ]]; then
  emit skip true
  emit reason "HEAD is already released as $base_tag"
  emit base_tag "$base_tag"
  exit 0
fi

weight="$(git diff --numstat "$base_sha" "$head_sha" -- . \
  ':(exclude)docs' ':(exclude)*.md' ':(exclude).github' \
  ':(exclude)app/src/test' ':(exclude)app/src/androidTest' \
  ':(exclude)scripts/test-*' ':(exclude)scripts/testdata' ':(exclude)scripts/fixtures' \
  | awk '$1 != "-" { total += $1 + $2 } END { print total + 0 }')"

if (( weight >= major_lines )); then level=major
elif (( weight >= minor_lines )); then level=minor
else level=patch
fi

[[ "$base_tag" =~ $semver ]]
major="${BASH_REMATCH[1]}"; minor="${BASH_REMATCH[2]}"; patch="${BASH_REMATCH[3]}"

bump() {
  case "$1" in
    major) major=$((major + 1)); minor=0; patch=0 ;;
    minor) minor=$((minor + 1)); patch=0 ;;
    patch) patch=$((patch + 1)) ;;
  esac
  if (( patch > 9 )); then minor=$((minor + 1)); patch=0; fi
  if (( minor > 9 )); then major=$((major + 1)); minor=0; patch=0; fi
}

bump "$level"
# base_tag is the maximum, so the next version is free; the loop only guards the carry.
while grep -qxF "v$major.$minor.$patch" <<< "$all_tags"; do bump patch; done
version_name="$major.$minor.$patch"

previous_code="$(jq -r '.[].body // empty' <<< "$releases" \
  | grep -oE 'versionCode`?:?[[:space:]]*`?[0-9]{1,10}' | grep -oE '[0-9]+$' \
  | awk -v ceiling="$code_ceiling" '$1 + 0 <= ceiling && $1 + 0 > max { max = $1 + 0 } END { print max + 0 }' || true)"
previous_code="${previous_code:-0}"
version_code="$now_epoch"
if (( version_code <= previous_code )); then version_code=$((previous_code + 1)); fi
(( version_code <= code_ceiling )) || fail "versionCode $version_code exceeds $code_ceiling."

emit skip false
emit reason "$weight changed lines since $base_tag -> $level"
emit base_tag "$base_tag"
emit weight "$weight"
emit level "$level"
emit version_name "$version_name"
emit version_code "$version_code"
