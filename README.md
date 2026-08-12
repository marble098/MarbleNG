# MarbleNG / Xray Genius Android

Native Android port of the Xray Genius Termux v1.3.0 client. The Android version keeps the original subscription/config workflow, smart testing, history, Telegram radar, Cloudflare Worker helper, privacy diagnostics and settings, but replaces the Termux-only local SOCKS usage with an Android `VpnService` + HEV SOCKS5 TUN bridge.

## Core pipeline

- Xray-core is built from the exact pre-release tag in `core-lock.json` for `arm64-v8a`, `armeabi-v7a`, `x86_64` and `x86`.
- `hev-socks5-tunnel` is built from its exact tag using its official Android NDK makefiles.
- `scripts/update-core-lock.sh` discovers newer upstream pre-release/release tags.
- GitHub Actions creates signed universal and per-ABI APKs.

## Local build

Install Android SDK 37, NDK 28.2.13676358, JDK 17, Go and Git, then:

```bash
./scripts/prepare-native.sh
./gradlew assembleRelease
```

GitHub Actions is the recommended build path because it pins and provisions the complete toolchain automatically.

## Signing

No signing key is stored in Git. The Termux injector creates signing material once, stores it as GitHub Actions secrets, and never replaces existing signing secrets on later injections. That keeps the Android signing identity stable across future releases.

## Privacy model

The VPN process is excluded from its own VPN so Xray can reach upstream endpoints without a routing loop. Device traffic enters the TUN, HEV forwards it to the localhost Xray SOCKS5h inbound, and the runtime hardener removes direct/freedom fallback from the selected proxy path. If the forwarding core dies unexpectedly, the VPN interface is deliberately kept established while forwarding is stopped, blocking traffic until repair or explicit disconnect.

For Android's strongest system-level kill switch, users can additionally enable **Always-on VPN → Block connections without VPN** for MarbleNG in Android settings.
