# VLESS / XHTTP / REALITY on an IPv6-only endpoint

## Reproduction and root causes

A rendered (HTML + Markdown) share link can contain `&amp;security=reality`,
`&amp;extra=%7B…%7D` and `sni=[example.org](http://example.org)` instead of URI query separators
and a DNS-only SNI. Previously the importer bound keys named `amp;security` and `amp;extra`,
produced a plaintext VLESS/XHTTP outbound with no REALITY key, and passed the Markdown markup
verbatim to the TLS handshake. Neither result could authenticate against the original server.
There is a second, independent problem on the sing-box engine: its pinned fork's VLESS link
parser decodes XHTTP `extra` **only as base64url JSON**, and maps only a subset of its fields.
Percent-encoded JSON is ignored; the outbound either fails `sing-box check` (missing
`x_padding_bytes`) or starts without the operator's camouflage. Starting a core is not evidence
that the node delivered any traffic. Old imported profiles were not repaired on app startup:
reconciliation compared only host:port, so the missing REALITY and XHTTP fields went unnoticed.

The endpoint in this report is an **IPv6 literal**. Without IPv6 on the physical network, no
IPv4 fallback can reach it; disabling IPv6 in app settings also intentionally excludes it. A
failed raw ICMP/TCP probe is not the same as a real through-tunnel delay, and no app can conjure
IPv6 transit from an IPv4-only underlay.

## Changes

- Normalise only **query separators** that look like escaped HTML (`&amp;key=`), never arbitrary
  `&amp;` in key material, a percent-encoded value, or the display-name fragment. Unwrap a
  Markdown-wrapped SNI only if the URL host *exactly equals* the DNS label, with no credentials,
  query or redirect path. Ambiguous/mismatched labels remain untouched; they are not evidence of
  a different server name. The original key, port, short ID, path and URL-encoded extra are never
  replaced with guessed values. Normalisation runs on import and when reading a previously saved
  link on the sing-box engine.
- Decode both JSON and base64url JSON XHTTP `extra`; reject invalid `extra` instead of silently
  discarding required padding. The sing-box translator preserves explicit top-level options over
  defaults from `extra`. On XHTTP links use Marble's current link reader before stored JSON,
  without trying the fork's lossy link parser. Non-XHTTP protocols retain the user's normal
  reader preference and fallback order.
- Compare the stored Xray cache with the link's **wire fields**, not just host:port. This repairs
  previously imported nodes whose security, REALITY key/SNI, or XHTTP path/extra was corrupted,
  without rewriting unrelated routing or logging metadata when the cache already agrees.
- Strip IPv6 URL brackets at the importer boundary, keep the literal (not DNS) in profile
  metadata, and identify IPv4-only mode as `ipv6-disabled` before a tunnel or ping is attempted.
  A message tells the user to enable IPv6 and use an IPv6-capable network. Hex letters in an
  IPv6 literal no longer count as a hostname/DNS success in Smart ping.

## Verification

`VlessXhttpRealityIpv6Test` uses a **synthetic** documentation-range IPv6 address, dummy UUID,
placeholder key and example.org. It checks the rendered-link normalisation, generated Xray
outbound, REALITY/XHTTP settings, both sing-box reader preferences and translated outbound,
base64 extra, precedence, cache migration, IPv4-only error and ping classification. The existing
`SingBoxNativeIntegrationTest` covers XHTTP+REALITY URL test and real HTTPS delay over loopback
against pinned core binaries when `MARBLE_NATIVE_TESTS_REQUIRED=1` is set. Source verification
compiles the app, runs the JVM tests and the architecture guard. No production account, server,
public key or live endpoint is contacted by these tests. A successful CI check is not a claim
that a private IPv6 server was dialled from an Android device on the user's current carrier.
