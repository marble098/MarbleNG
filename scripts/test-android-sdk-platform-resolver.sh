#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RESOLVER="$ROOT/scripts/resolve-android-platform.py"
FIXTURE="$ROOT/scripts/fixtures/android-sdk-platforms-37-minor.txt"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

selected="$(python3 "$RESOLVER" 37 < "$FIXTURE" 2> "$TMP/resolve.log")"
[[ "$selected" == "platforms;android-37.0" ]] || {
    cat "$TMP/resolve.log" >&2
    echo "[FAIL] expected android-37.0 first, got '$selected'" >&2
    exit 1
}
grep -q "stable candidates: platforms;android-37.0, platforms;android-37.1" "$TMP/resolve.log"
echo "[PASS] android-37.0 resolves without legacy android-37; beta/ext packages are ignored"

cat > "$TMP/minors.txt" <<'EOF'
Available Packages:
  platforms;android-37.1 | 1 | Android SDK Platform 37.1
  platforms;android-37.3 | 1 | Android SDK Platform 37.3
  platforms;android-37.3-beta2 | 1 | Android SDK Platform 37.3 Beta 2
  platforms;android-37-ext16 | 1 | Android SDK Platform 37 Extension 16
EOF
selected="$(python3 "$RESOLVER" 37 < "$TMP/minors.txt" 2> /dev/null)"
[[ "$selected" == "platforms;android-37.3" ]] || {
    echo "[FAIL] expected highest stable minor 37.3, got '$selected'" >&2
    exit 1
}
echo "[PASS] highest numeric stable minor wins when .0 is absent"

cat > "$TMP/unavailable.txt" <<'EOF'
Available Packages:
  platforms;android-37.2-beta3 | 1 | Android SDK Platform 37.2 Beta 3
  platforms;android-37-ext15 | 1 | Android SDK Platform 37 Extension 15
  platforms;android-38.0 | 1 | Android SDK Platform 38.0
EOF
if python3 "$RESOLVER" 37 < "$TMP/unavailable.txt" > /dev/null 2> "$TMP/unavailable.log"; then
    echo "[FAIL] preview/ext-only catalog should not resolve compileSdk 37" >&2
    exit 1
fi
grep -q "No stable Android SDK platform package found for compileSdk 37" "$TMP/unavailable.log"
grep -q "platforms;android-37.2-beta3" "$TMP/unavailable.log"
echo "[PASS] missing stable platform fails with a useful catalog listing"
