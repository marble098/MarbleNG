#!/usr/bin/env bash
# Validate the exact manually chosen release identity and fail closed on collisions.
set -euo pipefail

: "${VERSION_NAME:?VERSION_NAME is required}"
: "${VERSION_CODE:?VERSION_CODE is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"

if [[ ! "$VERSION_NAME" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "::error::Version must be a numeric semantic version like 11.5.1, without v or leading zeroes." >&2
  exit 1
fi
if [[ ! "$VERSION_CODE" =~ ^[1-9][0-9]{0,9}$ ]] || (( VERSION_CODE > 2100000000 )); then
  echo "::error::versionCode must be an integer from 1 to 2100000000, without leading zeroes." >&2
  exit 1
fi

tag="v$VERSION_NAME"
# matching-refs and the paginated releases endpoint return successful empty lists
# for missing refs, unlike treating every API error as a harmless 404.
refs="$(gh api "repos/$GITHUB_REPOSITORY/git/matching-refs/tags/$tag")"
releases="$(gh api --paginate "repos/$GITHUB_REPOSITORY/releases?per_page=100" | jq -s .)"
if ! jq -e 'type == "array"' <<< "$refs" >/dev/null ||
   ! jq -e 'type == "array" and length > 0 and all(.[]; type == "array")' <<< "$releases" >/dev/null; then
  echo "::error::Invalid GitHub response while checking release collisions." >&2
  exit 1
fi
if jq -e --arg ref "refs/tags/$tag" 'any(.[]; .ref == $ref)' <<< "$refs" >/dev/null ||
   jq -e --arg tag "$tag" 'any(.[][]; .tag_name == $tag)' <<< "$releases" >/dev/null; then
  echo "::error::Tag or release $tag already exists. Choose a different version; nothing was changed." >&2
  exit 1
fi
printf '[OK] Selected version %s, versionCode %s, tag %s is available.\n' "$VERSION_NAME" "$VERSION_CODE" "$tag"
