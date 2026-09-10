# MARBLE_SINGBOX_PINNED_PEER_V164 — pcs/vcn runs on sing-box extended, verified

## The state V163 left

`MARBLE_SINGBOX_PINNED_PEER_V163` correctly diagnosed the "connects on sing-box, no Internet"
report — a VLESS/TCP/TLS node with `pcs=`/`vcn=` behind a fronted `sni=spotify.com` — and fixed
it by *refusing* the profile on sing-box extended, naming Xray as the engine to use. The fork's
parser silently dropped both keys and verified the fronted SNI against system roots, and no
sing-box option could express a whole-leaf-certificate hash (`certificate_public_key_sha256` is
an SPKI hash, and `insecure: true` would discard the pin).

V163 was always a containment fix. V164 removes the limitation: the pinned sing-box core now
**implements Xray's pinning contract natively**, so a `pcs`/`vcn` node runs on either engine
with its pin verified — never silently downgraded.

## The contract the core now speaks

The semantics mirror Xray's `transport/internet/tls` `verifyPeerCert`/`verifyChain` exactly
(`native/singboxpatch/tls/pin_verify.go`, copied verbatim into the fork as
`common/tls/pin_verify.go`):

- pins are SHA-256 hex over the **whole DER certificate**;
- a pin matching the **leaf** accepts the peer outright;
- a pin matching a **CA in the chain** replaces the roots and the chain is then verified against
  `verify_peer_cert_by_name` entries (or `server_name`, which must be present);
- a name list verifies the leaf against each name in order, first success wins;
- no pin match → the handshake fails with `peer cert is unrecognized (against
  pinned_peer_cert_sha256)` — the same failure mode as Xray.

The verifier is stdlib-only (`crypto/sha256`, `crypto/x509`) with no new dependencies, and its
module (`native/singboxpatch/go.mod`) lets CI unit-test it standalone from `native/` with an
in-memory CA + leaf fixture.

## What the injector patches

`scripts/inject-singbox-tls-pinning.py` (MARKER `MARBLE_SINGBOX_PINNED_PEER_V164`, anchored and
idempotent like the two existing injectors):

1. `option/tls.go` — two new outbound fields:
   `pinned_peer_cert_sha256` and `verify_peer_cert_by_name`.
2. `common/tls/std_client.go` and `common/tls/utls_client.go` — when pins or verify names are
   present: `InsecureSkipVerify = true`, and `VerifyPeerCertificate` runs the existing SPKI
   check (if set) and then `VerifyPeerCertPin` with the parsed pins, names, SNI, roots and time
   source. Pins must be 32-byte hex; conflicting `certificate`/`certificate_path` is an error.
3. `parser/link/vless.go` and `parser/link/trojan.go` — the fork's own link parser now reads
   `pcs` (and `pinnedPeerCertSha256`/`pinned_peer_cert_sha256` aliases) and `vcn` (and its
   aliases), so the parser outbound honours the pin instead of dropping it.

`scripts/prepare-native.sh` runs the injector and fails the build if the marker is missing from
`option/tls.go`; the release patch level is `crash-fix.2-pinning.1` in `core-lock.json`.

## What changed in Marble

- `SingBoxTransportTranslator.tls` translates instead of refusing: `pcs` →
  `pinned_peer_cert_sha256` (normalized lowercase hex list), `vcn` → `verify_peer_cert_by_name`,
  Xray's SPKI hash → `certificate_public_key_sha256` (hex digest re-spelled as base64 — the
  same 32 bytes). A malformed pin is a `config-unsupported:` refusal, never a silent drop.
- `SingBoxConfigBuilder.pinnedPeerRefusal` narrowed to the one pin no sing-box option can
  express: Xray's **whole-chain** hash (`pinnedPeerCertificateChainSha256`) → `CHAIN_PIN_REFUSAL`
  (`config-unsupported:`, names Xray). `pcs`/`vcn`/SPKI profiles are supported on both engines.
- The parser-first candidate is a full citizen again for pinned links: the patched fork reads
  `pcs`/`vcn` itself, so there is no candidate that can accept the link and drop the pin.

The promise is unchanged: a pin is a security promise the user made, and it is translated
losslessly or refused — never downgraded to `insecure`.

## Tests

- `native/singboxpatch/tls/pin_verify_test.go` — the verifier contract on any host: leaf accept,
  leaf mismatch, CA-anchored name check, wrong-SNI rejection, `vcn` verification, empty chain.
- `SingBoxPinnedPeerV164Test` — the Kotlin translation contract for the exact reported link
  shape (via the `linkJson` unit-test seam): pin + verify name + fronted SNI land in the
  patched options; lowercase hex normalization; SPKI hex→base64 round-trip; malformed pin and
  chain-pin refusals; `allowInsecure` coexisting with (never erasing) the pin; parser-first
  support.
- `ResolverSinkholeV163Test` — updated to the V164 outcome: `pcs`/`vcn` links run, the
  whole-chain pin is refused with the same `config-unsupported:` contract.
- `CoreInteropRegressionTest` — the chain pin stays in the describe-refusal table, and a new
  case pins that a `pcs`+`vcn` profile survives translation with `insecure` absent.
