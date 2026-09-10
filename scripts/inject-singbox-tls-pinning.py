#!/usr/bin/env python3
"""Anchor-guarded sing-box source injector for Xray-compatible TLS certificate pinning.

MARBLE_SINGBOX_PINNED_PEER_V164

Why this file exists
--------------------
A VLESS/TCP/TLS node whose share link carries `pcs=<sha256>` (Xray pinnedPeerCertSha256) and
`vcn=<host>` (Xray verifyPeerCertByName) behind a fronted `sni=spotify.com` "connected" on the
sing-box extended core and moved no traffic: the fork's parser silently dropped both keys and
the core verified the certificate against the fronted SNI with the system CA store, so every
handshake failed — the exact "connected, no Internet" signature.

MarbleNG refused such profiles for the sing-box engine until now, because sing-box cannot
express a certificate hash pin. This injector removes that limitation at the source of the
pinned core:

 1. option/tls.go gains two outbound TLS options:
      pinned_peer_cert_sha256   — hex SHA-256 of a certificate's DER (Xray `pcs`)
      verify_peer_cert_by_name  — DNS names the peer must verify against (Xray `vcn`)
 2. common/tls/std_client.go and common/tls/utls_client.go translate them into a
    tls.Config whose InsecureSkipVerify + VerifyPeerCertificate reproduce Xray's own
    verifyPeerCert / verifyChain semantics: a pin on the leaf accepts the peer; a pin on a
    CA in the chain replaces the trust roots and continues into the name check (or the
    server_name check); a mismatch is a hard handshake failure.
 3. parser/link/vless.go and parser/link/trojan.go read `pcs`/`vcn` from share links, so the
    fork's own `{type: parser}` outbound honours the pin instead of silently dropping it.
 4. common/tls/pin_verify.go — the verifier itself — is copied verbatim from
    native/singboxpatch/tls/pin_verify.go, which is stdlib-only so this repository's CI can
    compile and unit-test it against the pristine source without building the whole fork.

The script is idempotent (a second run is a no-op) and anchor-guarded (a moved anchor fails
the build loudly instead of silently shipping an unpatched core). The verifier's contract is
pinned by `go test` in native/singboxpatch/tls (self-signed CA + leaf, generated in-memory),
which reproduces the pinning server's certificate condition on any host.
"""

from pathlib import Path
import shutil
import sys

MARKER = "MARBLE_SINGBOX_PINNED_PEER_V164"

# Xray semantics this mirrors, recorded in every injected file so a future reader can diff
# MarbleNG's core against the fork it was built from.
XRAY_SEMANTICS = "XTLS/Xray-core transport/internet/tls verifyPeerCert / verifyChain"


class Patch:
    """One anchored replacement: `old` must appear exactly once, `new` replaces it."""

    def __init__(self, path, old, new, why):
        self.path = path
        self.old = old
        self.new = new
        self.why = why


PIN_BLOCK = (
    "\n"
    "\t// " + MARKER + " — Xray-compatible certificate pinning (" + XRAY_SEMANTICS + ").\n"
    "\t// The fork's own `pinned_peer_cert_sha256` (`pcs`) and `verify_peer_cert_by_name`\n"
    "\t// (`vcn`) replace chain verification exactly like Xray: the peer is accepted only\n"
    "\t// when a pinned hash matches — the leaf outright, or a CA that then anchors the\n"
    "\t// name check — and the optional names verify. See VerifyPeerCertPin.\n"
    "\tvar pinnedPeerCertSha256 [][]byte\n"
    "\tfor _, hashHex := range options.PinnedPeerCertSha256 {\n"
    "\t\thashBytes, err := hex.DecodeString(strings.ReplaceAll(hashHex, \":\", \"\"))\n"
    "\t\tif err != nil || len(hashBytes) != 32 {\n"
    "\t\t\treturn nil, E.New(\"invalid pinned_peer_cert_sha256: expected 32-byte sha256 hex\")\n"
    "\t\t}\n"
    "\t\tpinnedPeerCertSha256 = append(pinnedPeerCertSha256, hashBytes)\n"
    "\t}\n"
    "\tif len(pinnedPeerCertSha256) > 0 || len(options.VerifyPeerCertByName) > 0 {\n"
    "\t\tif len(options.Certificate) > 0 || options.CertificatePath != \"\" {\n"
    "\t\t\treturn nil, E.New(\"pinned_peer_cert_sha256/verify_peer_cert_by_name is conflict with certificate or certificate_path\")\n"
    "\t\t}\n"
    "\t\ttlsConfig.InsecureSkipVerify = true\n"
    "\t\tpinServerName := serverName\n"
    "\t\tpinRootCAs := tlsConfig.RootCAs\n"
    "\t\ttimeFunc := tlsConfig.Time\n"
    "\t\tif timeFunc == nil {\n"
    "\t\t\ttimeFunc = time.Now\n"
    "\t\t}\n"
    "\t\tpublicKeyPins := options.CertificatePublicKeySHA256\n"
    "\t\ttlsConfig.VerifyPeerCertificate = func(rawCerts [][]byte, verifiedChains [][]*x509.Certificate) error {\n"
    "\t\t\tif len(publicKeyPins) > 0 {\n"
    "\t\t\t\tif err := VerifyPublicKeySHA256(publicKeyPins, rawCerts); err != nil {\n"
    "\t\t\t\t\treturn err\n"
    "\t\t\t\t}\n"
    "\t\t\t}\n"
    "\t\t\treturn VerifyPeerCertPin(pinnedPeerCertSha256, options.VerifyPeerCertByName, pinServerName, pinRootCAs, timeFunc, rawCerts)\n"
    "\t\t}\n"
    "\t}\n"
)

SPKI_BLOCK = (
    "\tif len(options.CertificatePublicKeySHA256) > 0 {\n"
    "\t\tif len(options.Certificate) > 0 || options.CertificatePath != \"\" {\n"
    "\t\t\treturn nil, E.New(\"certificate_public_key_sha256 is conflict with certificate or certificate_path\")\n"
    "\t\t}\n"
    "\t\ttlsConfig.InsecureSkipVerify = true\n"
    "\t\ttlsConfig.VerifyPeerCertificate = func(rawCerts [][]byte, verifiedChains [][]*x509.Certificate) error {\n"
    "\t\t\treturn VerifyPublicKeySHA256(options.CertificatePublicKeySHA256, rawCerts)\n"
    "\t\t}\n"
    "\t}\n"
)

LINK_CASES = (
    "\t\tcase \"pcs\", \"pinnedPeerCertSha256\", \"pinned_peer_cert_sha256\":\n"
    "\t\t\tTLSOptions.PinnedPeerCertSha256 = strings.Split(value, \",\")\n"
    "\t\tcase \"vcn\", \"verifyPeerCertByName\", \"verify_peer_cert_by_name\":\n"
    "\t\t\tTLSOptions.VerifyPeerCertByName = strings.Split(value, \",\")\n"
)

PATCHES = [
    # ── 1. The two outbound TLS options ────────────────────────────────────────────────
    Patch(
        "option/tls.go",
        '\tCertificatePublicKeySHA256 badoption.Listable[[]byte]          `json:"certificate_public_key_sha256,omitempty"`\n',
        '\tCertificatePublicKeySHA256 badoption.Listable[[]byte]          `json:"certificate_public_key_sha256,omitempty"`\n'
        '\t// ' + MARKER + ' — Xray-compatible certificate pinning (share-link `pcs` / `vcn`).\n'
        '\t// The hashes are hex SHA-256 of DER certificates; the names follow Xray\'s\n'
        '\t// verifyPeerCertByName contract. Both replace chain verification.\n'
        '\tPinnedPeerCertSha256        badoption.Listable[string]          `json:"pinned_peer_cert_sha256,omitempty"`\n'
        '\tVerifyPeerCertByName        badoption.Listable[string]          `json:"verify_peer_cert_by_name,omitempty"`\n',
        "outbound TLS options must learn the pin fields",
    ),
    # ── 2. std client: hex import + the pinning block ─────────────────────────────────
    Patch(
        "common/tls/std_client.go",
        '\t"encoding/base64"\n'
        '\t"net"\n',
        '\t"encoding/base64"\n'
        '\t"encoding/hex"\n'
        '\t"net"\n',
        "std TLS client decodes hex pins",
    ),
    Patch(
        "common/tls/std_client.go",
        SPKI_BLOCK,
        SPKI_BLOCK + PIN_BLOCK,
        "std TLS client applies certificate pins",
    ),
    # ── 3. uTLS client: hex import + the same pinning block ────────────────────────────
    Patch(
        "common/tls/utls_client.go",
        '\t"crypto/x509"\n'
        '\t"math/rand"\n',
        '\t"crypto/x509"\n'
        '\t"encoding/hex"\n'
        '\t"math/rand"\n',
        "uTLS client decodes hex pins",
    ),
    Patch(
        "common/tls/utls_client.go",
        SPKI_BLOCK,
        SPKI_BLOCK + PIN_BLOCK,
        "uTLS client applies certificate pins",
    ),
    # ── 4. The fork's own link parsers read pcs/vcn instead of dropping them ───────────
    Patch(
        "parser/link/vless.go",
        '\t\tcase "alpn":\n'
        '\t\t\tTLSOptions.ALPN = strings.Split(value, ",")\n'
        '\t\tcase "fp":\n',
        '\t\tcase "alpn":\n'
        '\t\t\tTLSOptions.ALPN = strings.Split(value, ",")\n'
        + LINK_CASES +
        '\t\tcase "fp":\n',
        "vless share links carry the pin into the parser outbound",
    ),
    Patch(
        "parser/link/trojan.go",
        '\t\tcase "alpn":\n'
        '\t\t\tTLSOptions.ALPN = strings.Split(value, ",")\n'
        '\t\tcase "fp":\n',
        '\t\tcase "alpn":\n'
        '\t\t\tTLSOptions.ALPN = strings.Split(value, ",")\n'
        + LINK_CASES +
        '\t\tcase "fp":\n',
        "trojan share links carry the pin into the parser outbound",
    ),
]


def apply_patch(root: Path, patch: Patch) -> str:
    target = root / patch.path
    if not target.is_file():
        raise SystemExit("sing-box source file missing: " + patch.path)

    text = target.read_text(encoding="utf-8")
    if patch.new in text:
        return "already patched"
    if text.count(patch.old) != 1:
        raise SystemExit(
            "sing-box anchor moved or is ambiguous ("
            + str(text.count(patch.old))
            + " matches) in "
            + patch.path
            + " — "
            + patch.why
            + ". Re-read the pinned core before rebuilding; never ship an unpatched binary."
        )

    target.write_text(text.replace(patch.old, patch.new, 1), encoding="utf-8")
    return "patched"


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: inject-singbox-tls-pinning.py SINGBOX_SOURCE")

    root = Path(sys.argv[1]).resolve()
    if not (root / "go.mod").is_file():
        raise SystemExit("not a sing-box source tree: " + str(root))

    module = (root / "go.mod").read_text(encoding="utf-8").splitlines()[0]
    if module.strip() != "module github.com/sagernet/sing-box":
        raise SystemExit("unexpected sing-box module line: " + module)

    results = []
    for patch in PATCHES:
        results.append((patch.path, apply_patch(root, patch)))

    # The verifier is copied verbatim from the repository's standalone module so the code CI
    # unit-tests is byte-for-byte the code the core ships.
    source = Path(__file__).resolve().parent.parent / "native" / "singboxpatch" / "tls" / "pin_verify.go"
    if not source.is_file():
        raise SystemExit("pin verifier source missing: " + str(source))
    if MARKER not in source.read_text(encoding="utf-8"):
        raise SystemExit("pin verifier source lacks the " + MARKER + " marker")

    target = root / "common" / "tls" / "pin_verify.go"
    target.parent.mkdir(parents=True, exist_ok=True)
    previous = target.read_text(encoding="utf-8") if target.is_file() else ""
    content = source.read_text(encoding="utf-8")
    if previous == content:
        results.append(("common/tls/pin_verify.go", "already patched"))
    else:
        shutil.copyfile(source, target)
        results.append(("common/tls/pin_verify.go", "verifier written"))

    # A patched tree must carry the marker in the options file and the verifier, otherwise the
    # build verification in prepare-native.sh has nothing to grep for.
    for relative in ("option/tls.go", "common/tls/pin_verify.go"):
        if MARKER not in (root / relative).read_text(encoding="utf-8"):
            raise SystemExit("pin marker missing after injection: " + relative)

    print("sing-box TLS certificate pinning (" + MARKER + "):")
    for path, state in results:
        print("  [" + state + "] " + path)


if __name__ == "__main__":
    main()
