# Xray Genius v1.3.0 → MarbleNG feature map

The original 7,605-line Bash file is preserved verbatim in `legacy/`.

## Ported into native Android

- Auto connect, last-profile reconnect and smart benchmark/selection.
- Full test for an individual config with success, latency, jitter and throughput.
- Subscription URL storage, refresh-all, manual URI/raw/base64 import, file import and whole Xray JSON import.
- Config browser grouped by source/subscription.
- VLESS, VMess, Trojan, Shadowsocks, Hysteria2 and basic SOCKS/HTTP URI parsing, plus Xray JSON passthrough/hardening.
- Telegram public-channel radar with real-tunnel qualification and optional auto-created passed group.
- Cloudflare Worker deployment helper; API token/access key are stored through Android Keystore-backed AES/GCM storage.
- Privacy center, proxy egress audit, DNS audit, Google AI reachability, status, logs, capability map, doctor and connection history.
- Appearance themes and benchmark/Telegram policy settings.
- Latest-core check in-app and automatic upstream core tracking/builds in GitHub Actions.

## Android transport upgrade

The Bash client only exposes localhost SOCKS. MarbleNG adds Android `VpnService`, creates IPv4 and IPv6 default routes, and passes the TUN file descriptor into `hev-socks5-tunnel` through JNI. HEV forwards the TUN to Xray's localhost SOCKS5h inbound.

The app itself is excluded from its own VPN to prevent an Xray/HEV routing loop. The runtime Xray config is pruned to the selected proxy chain plus blackhole and does not retain a `freedom` fallback.

If HEV/Xray dies unexpectedly, MarbleNG keeps the VPN interface established instead of intentionally tearing it down, so traffic is held while the user repairs/disconnects. Android can still remove a VPN when the process is killed by the OS; for the strongest OS-level kill switch, enable **Always-on VPN** and **Block connections without VPN** in Android settings.

## Core/update model

Android does not safely support replacing an APK's packaged native executable in-place like Termux does. The app therefore converts the Bash “update Xray” action into a core update check, while `.github/workflows/update-cores.yml` updates `core-lock.json` and causes a new APK to be rebuilt and signed with the same signing identity.
