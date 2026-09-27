# REALITY vs current Xray servers: the X25519MLKEM768 key share

## Reproduction and root causes

A VLESS/REALITY node (XHTTP transport, IPv6 literal, rendered-text `&amp;` separators)
connected in v2rayNG and failed **55/55 attempts with `reality verification failed`** on the
sing-box engine — deterministically, on every destination, with zero successes. The report
blamed the public-key parsing (a standard-base64 decode of the URL-safe `pbk`), but the keys
are innocent: `pbk`, `sid`, `spx` and `sni` travel **verbatim** from the share link into both
cores' configs, and no base64 decode of `pbk` exists anywhere on that path.

The fault is the handshake shape. Since Xray-core v26.9.8 (`github.com/xtls/reality@8cdf7bf9`)
the REALITY *server* rejects any ClientHello without `X25519MLKEM768 before optional X25519`
and silently falls back to the decoy site, so the client fails session verification. The
pinned sing-box extended core *strips* that hybrid share from every uTLS handshake unless its
reality block sets `support_x25519mlkem768` (its own `common/tls/reality_client.go`; the
option is `OutboundRealityOptions` in its `option/tls.go`) — and neither its link parser
(`parser/link/*` at the pinned commit) nor MarbleNG's translator used to set it. The string
`reality verification failed` is sing-box-exclusive (Xray never prints it), which is why the
same node worked under every Xray-based client. Upstream tracks this as
SagerNet/sing-box#4520 (open); the pinned fork release carries the option but no default.

## Changes

- The sing-box translator emits `"support_x25519mlkem768": true` on **every** translated
  REALITY outbound. The flag is unconditionally safe: older servers scan for plain X25519
  first and merely tolerate the extra share, while new servers require it.
- REALITY share links skip the fork's `parser` outbound, which builds its REALITY options
  without the flag and therefore cannot complete a handshake against a current server. Link
  detection mirrors the importer's own inference (`security=reality`, or a `pbk` carried for
  no other reason), so the candidate set and the importer never disagree. Plain TLS links
  still reach the core parser as before.
- Only fingerprints whose uTLS preset carries the share (chrome in the pinned metacubex/utls
  v1.8.7) can satisfy a new server. Residual rejections — a non-chrome fingerprint, a pasted
  native sing-box document without the flag, or genuinely mismatched keys — are classified by
  `RealityHandshakePolicy` into one Bug Finder check with the actual remedy, instead of being
  retried blindly and misreported as a dead server. MarbleNG never rewrites a pasted native
  document to add the flag; the check tells its owner the exact line to add.

## Verification

`RealityMlkemHandshakeTest` uses **synthetic** fixtures only (documentation-range IPs, a dummy
UUID, an invented 43-char URL-safe key cut from the reported alphabet, example domains). It
pins the byte-identical keys from link to Xray JSON to sing-box JSON, the emitted flag, the
parser exclusion on both transports, the untouched TLS path, the REALITY-link detector, and
the policy's markers, burst escalation and fingerprint-aware remedy. The system integrity
audit pins the translator line, the candidate exclusion, the policy wiring and the test names.
No production account, server, public key or live endpoint is contacted by these tests.
