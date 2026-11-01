#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cat > "$tmp/gh" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
[[ "${MOCK_FAIL:-0}" != 1 ]] || exit 1
case "$*" in
  *matching-refs*) printf '%s\n' "${MOCK_REFS:-[]}" ;;
  *releases*) printf '%s\n' "${MOCK_RELEASES:-[]}" ;;
  *) exit 2 ;;
esac
MOCK
chmod +x "$tmp/gh"
export PATH="$tmp:$PATH" GITHUB_REPOSITORY=owner/repo
export VERSION_NAME=11.5.1 VERSION_CODE=2000000000
run() { bash "$root/scripts/release-version.sh" >/dev/null 2>&1; }
run || { echo 'Valid version was rejected'; exit 1; }
for version in '' v11.5.1 1.2 01.2.3 '1.2.3-beta' '1.2.3;echo'; do
  if (export VERSION_NAME="$version"; run); then echo "Invalid version accepted: $version"; exit 1; fi
done
for code in '' 0 01 -1 2100000001 999999999999999999999 abc; do
  if (export VERSION_CODE="$code"; run); then echo "Invalid code accepted: $code"; exit 1; fi
done
(export MOCK_REFS='[{"ref":"refs/tags/v11.5.10"}]'; run) || { echo 'Prefix is not an exact collision'; exit 1; }
for refs in '[{"ref":"refs/tags/v11.5.1"}]' '{}'; do
  if (export MOCK_REFS="$refs"; run); then echo "Ref collision or invalid response accepted: $refs"; exit 1; fi
done
for releases in '[{"tag_name":"v11.5.1","draft":true}]' '[{"tag_name":"v11.5.1","draft":false}]' '[{"tag_name":"v1.0.0"}] [{"tag_name":"v11.5.1"}]' '{}'; do
  if (export MOCK_RELEASES="$releases"; run); then echo "Release collision or invalid response accepted: $releases"; exit 1; fi
done
if (export MOCK_FAIL=1; run); then echo 'API failure accepted'; exit 1; fi
echo '[OK] Manual version validation and collision fixtures'
