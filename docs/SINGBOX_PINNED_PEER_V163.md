# MARBLE_SINGBOX_PINNED_PEER_V163 — "connects on sing-box, no Internet"

## The report

A VLESS / TCP / TLS node (`flow=xtls-rprx-vision`, `sni=spotify.com`, `fp=chrome`,
`alpn=h2,http/1.1`, `pcs=<4 × sha256>`, `vcn=<real host>`, `allowInsecure=0`) connects on the
sing-box extended engine — the core starts, the SOCKS inbound listens, the TUN comes up — and no
page ever loads. On the Xray engine the same node works.

## Why

`pcs` is Xray's `tlsSettings.pinnedPeerCertSha256`: a hex SHA-256 over the **whole leaf
certificate** (`transport/internet/tls/pin.go`). `vcn` is `verifyPeerCertByName`. When either is
present Xray sets `InsecureSkipVerify` and does its own verification in `verifyPeerCert`: hash
match → accept; otherwise chain-verify against the names in `vcn`. That is what lets a fronted
`sni=spotify.com` coexist with a self-signed certificate for the real host.

sing-box extended's `parser` outbound reads the same share link (`parser/link/vless.go`) and
knows neither key. It ignores them and builds a standard TLS client for `server_name =
spotify.com` with the system roots. The handshake fails on every connection, after the core has
already reported itself healthy. The fork's only pinning option is
`certificate_public_key_sha256` — a **SPKI** hash of the leaf's public key, which cannot be
derived from a certificate hash — and `insecure: true` would silently discard the user's pin.

Marble's own two translators (`SingBoxTransportTranslator.tls`) already refused the JSON form
with `config-unsupported: tlsSettings.pinnedPeerCertSha256`. But
`AppSettings.singBoxPreferParser` defaults to true, so the parser candidate ran **first**, the
core accepted it, and the refusal never fired.

## The fix

- `SingBoxConfigBuilder.pinnedPeerRefusal(profile)` looks at both truths — the share link
  (`linkCarriesPin`, every `pcs`/`vcn` alias `TlsPinningPolicy` knows) and the stored JSON
  (`TlsPinningPolicy.configIsPinned`) — and returns `PINNED_PEER_REFUSAL`, which starts with
  `config-unsupported:` (so existing tests and the Engine page treat it like any other
  refusal), names `pinnedPeerCertSha256`, and tells the user to run the node on **Xray**.
- `candidateSet` consults it before any reader runs, so the parser can no longer become a
  candidate for a pinned node.
- `MarbleVpnService.profileCompatibilityIssue` has a `SINGBOX` branch calling the same function,
  so the connect path fails **before the TUN** with `profile-preflight-rejected` and the readable
  reason, instead of establishing a kill-switch hold on a tunnel that can never carry traffic.

The pin is a security promise the user made; it is never downgraded to `insecure` behind their
back. Engine selection remains the user's contract (`MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157`) —
the app does not switch engines silently; it says which one to pick.

## Tests

`ResolverSinkholeV163Test` — `a pcs or vcn share link is refused for sing-box before the parser
can accept it`, `a pinned stored json is refused the same way`. Existing
`SingBoxCoreV151Test.certificatePinningIsReportedNotPretended` and
`CoreInteropRegressionTest.unsupportedTransportAndSecurityCannotDisappearDuringDescribe` keep
passing with the new (earlier) refusal.
