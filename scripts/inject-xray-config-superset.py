#!/usr/bin/env python3
# Anchor-guarded Xray source injector for MarbleNG's core config superset.
#
# MARBLE_CORE_CONFIG_SUPERSET_V165
#
# What it does, and nothing else:
#   1. stages `native/xraypatch/marble_outbound_policy.go` into `infra/conf/`, the package that owns
#      the outbound policy;
#   2. gives `requiresTransportSecurity` one extra line — the application-consent exit — so a
#      `vless`/`trojan` outbound without TLS is refused only when nobody has taken responsibility
#      for it, instead of always;
#   3. re-reads the patched file and refuses to report success unless the hook is present exactly once.
#
# It is idempotent (a second run is a no-op) and it fails loudly on any anchor change: an upstream
# rename of `requiresTransportSecurity` must stop the build with a sentence about an anchor, never
# produce a core that silently dropped the patch — that failure mode is how a whole subscription
# becomes "Unsupported VLESS" with no explanation.
#
# The patch does NOT touch certificate verification, REALITY, the private-network matcher, or any
# other security rule. `xray` outside MarbleNG keeps upstream's default-deny behaviour.

from pathlib import Path
import shutil
import sys

GUARD = "if marbleAllowsUnencryptedOutbound() {"
SIGNATURE = "func requiresTransportSecurity(address *Address) bool {\n"
POLICY_FILE = "marble_outbound_policy.go"
MARKER = "MARBLE_CORE_CONFIG_SUPERSET_V165"


def inject(text: str) -> str:
    if GUARD in text:
        if text.count(GUARD) != 1:
            raise SystemExit("plaintext-outbound consent hook count != 1")
        if text.count(SIGNATURE) != 1:
            raise SystemExit("requiresTransportSecurity signature anchor changed")
        return text

    start = text.find(SIGNATURE)
    if start < 0:
        raise SystemExit("Xray plaintext-outbound anchor changed: " + SIGNATURE.strip())
    if text.count(SIGNATURE) != 1:
        raise SystemExit("requiresTransportSecurity signature anchor is ambiguous")

    insert_at = start + len(SIGNATURE)
    guard = (
        "\t// MARBLE_CORE_CONFIG_SUPERSET_V165 — MarbleNG's own core delegates this decision to\n"
        "\t// the application (Settings → Engine → Dial unencrypted nodes); see\n"
        "\t// infra/conf/" + POLICY_FILE + ". Upstream default-deny is intact for every other caller.\n"
        "\tif marbleAllowsUnencryptedOutbound() {\n"
        "\t\treturn false\n"
        "\t}\n"
    )
    result = text[:insert_at] + guard + text[insert_at:]

    if result.count(GUARD) != 1:
        raise SystemExit("plaintext-outbound consent hook missing after injection")
    return result


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: inject-xray-config-superset.py XRAY_SOURCE")

    xray = Path(sys.argv[1]).resolve()
    root = Path(__file__).resolve().parents[1]
    source = root / "native" / "xraypatch" / POLICY_FILE
    conf = xray / "infra" / "conf"
    destination = conf / POLICY_FILE
    xraygo = conf / "xray.go"

    if not source.is_file():
        raise SystemExit(f"missing outbound policy source: {source}")
    if not xraygo.is_file():
        raise SystemExit(f"missing Xray outbound config: {xraygo}")

    shutil.copyfile(source, destination)
    original = xraygo.read_text(encoding="utf-8")
    patched = inject(original)
    if patched != original:
        xraygo.write_text(patched, encoding="utf-8")

    if MARKER not in destination.read_text(encoding="utf-8"):
        raise SystemExit("outbound policy marker missing")
    if GUARD not in xraygo.read_text(encoding="utf-8"):
        raise SystemExit("plaintext-outbound consent hook missing from infra/conf/xray.go")
    print("[OK] Marble config-superset plaintext policy injected")


if __name__ == "__main__":
    main()
