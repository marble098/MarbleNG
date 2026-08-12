#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; LOCK="$ROOT/core-lock.json"; CORE="$ROOT/.cores"; JNILIBS="$ROOT/app/src/main/jniLibs"; HEVDST="$ROOT/app/src/main/jni/hev"

for x in git jq go curl unzip; do command -v "$x" >/dev/null || { echo "Missing $x" >&2; exit 1; }; done
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"; [[ -n "$NDK" && -x "$NDK/ndk-build" ]] || { echo 'ANDROID_NDK_HOME must point to NDK 28.2.13676358+' >&2; exit 1; }

XRAY_TAG="$(jq -r .xray.tag "$LOCK")"; HEV_TAG="$(jq -r .hev.tag "$LOCK")"
rm -rf "$CORE" "$JNILIBS" "$HEVDST"; mkdir -p "$CORE" "$JNILIBS" "$ROOT/app/src/main/assets/xray"

echo "[1/4] Xray source $XRAY_TAG"; git clone --quiet --depth 1 --branch "$XRAY_TAG" https://github.com/XTLS/Xray-core.git "$CORE/xray"

# --- بخش اضافه شده: فریب دادن کامپایلر برای پذیرش اجباری نسخه 1.22 ---
echo "Downgrading go.mod requirement to 1.22..."
(cd "$CORE/xray" && go mod edit -go=1.22 && go mod edit -toolchain=none || true)
# ----------------------------------------------------------------------

echo "[2/4] Xray Android binaries"
build_xray(){ local abi="$1" arch="$2" arm="${3:-}"; mkdir -p "$JNILIBS/$abi"; (cd "$CORE/xray"; env CGO_ENABLED=0 GOOS=android GOARCH="$arch" ${arm:+GOARM=$arm} go build -buildmode=pie -trimpath -buildvcs=false -ldflags='-s -w' -o "$JNILIBS/$abi/libxray.so" ./main); chmod +x "$JNILIBS/$abi/libxray.so"; }
build_xray arm64-v8a arm64; build_xray armeabi-v7a arm 7; build_xray x86_64 amd64; build_xray x86 386

echo "[3/4] HEV Android library $HEV_TAG"; git clone --quiet --depth 1 --branch "$HEV_TAG" --recursive https://github.com/heiher/hev-socks5-tunnel.git "$HEVDST"
"$NDK/ndk-build" -C "$ROOT/app/src/main" NDK_PROJECT_PATH="$ROOT/app/src/main" APP_BUILD_SCRIPT="$ROOT/app/src/main/jni/Android.mk" APP_APPLICATION_MK="$ROOT/app/src/main/jni/Application.mk" NDK_LIBS_OUT="$JNILIBS" -j"$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)"

echo '[4/4] Xray geo assets + lock asset'; rel="$(curl -fsSL --retry 3 "https://api.github.com/repos/XTLS/Xray-core/releases/tags/$XRAY_TAG")";url="$(jq -r '.assets[]|select(.name=="Xray-linux-64.zip")|.browser_download_url' <<<"$rel" | head -1)"; [[ -n "$url" && "$url" != null ]] || { echo 'Xray release data asset missing' >&2; exit 1;};curl -fL --retry 3 -o "$CORE/xray.zip" "$url"; unzip -p "$CORE/xray.zip" geoip.dat > "$ROOT/app/src/main/assets/xray/geoip.dat";unzip -p "$CORE/xray.zip" geosite.dat > "$ROOT/app/src/main/assets/xray/geosite.dat";cp "$LOCK" "$ROOT/app/src/main/assets/core-lock.json"
for abi in arm64-v8a armeabi-v7a x86_64 x86; do test -s "$JNILIBS/$abi/libxray.so";test -s "$JNILIBS/$abi/libhev-socks5-tunnel.so";test -s "$JNILIBS/$abi/libmarbleng.so";done
echo 'Native cores ready.'
