#!/usr/bin/env python3
"""Bundle small, pinned SRS files at BUILD time, never during VPN startup.

The lock pins both the upstream commit and SHA-256. A mirror is acceptable only if the bytes
match the lock. No trust-on-first-use checksum and no download detour through an unstarted VPN.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def valid(data, entry):
    return (len(data) == entry["size"] and data[:3] == b"SRS"
            and hashlib.sha256(data).hexdigest() == entry["sha256"])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dest", type=Path, default=ROOT / "app/src/main/assets/singbox")
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    lock = json.loads((ROOT / "singbox-rules-lock.json").read_text())
    assert re.fullmatch(r"[0-9a-f]{40}", lock["commit"]), "un-pinned rule set commit"
    assert lock["repo"] == "MetaCubeX/meta-rules-dat", "unexpected rule set source"
    args.dest.mkdir(parents=True, exist_ok=True)
    for entry in lock["rules"]:
        assert re.fullmatch(r"[a-z0-9-]+\.srs", entry["file"]), "unsafe rule set name"
        target = args.dest / entry["file"]
        if target.is_file() and valid(target.read_bytes(), entry):
            continue
        if args.verify_only:
            raise ValueError(f"Missing/corrupt bundled rule set: {target}")
        sources = [
            f'https://raw.githubusercontent.com/{lock["repo"]}/{lock["commit"]}/{entry["path"]}',
            f'https://cdn.jsdelivr.net/gh/{lock["repo"]}@{lock["commit"]}/{entry["path"]}',
        ]
        errors = []
        for url in sources:
            try:
                request = urllib.request.Request(url, headers={"User-Agent": "MarbleNG-build"})
                with urllib.request.urlopen(request, timeout=30) as response:
                    data = response.read(2 * 1024 * 1024 + 1)
                if not valid(data, entry):
                    raise ValueError("SHA-256, SRS header or length mismatch")
                staging = target.with_suffix(".tmp")
                staging.write_bytes(data)
                staging.replace(target)
                break
            except Exception as error:
                errors.append(str(error))
        else:
            raise RuntimeError(f'Cannot bundle {entry["tag"]}: {errors}')
    (args.dest / "manifest.json").write_text(json.dumps(lock, indent=2) + "\n")
    print(f'[OK] {len(lock["rules"])} pinned, verified offline sing-box rule sets in {args.dest}')


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"[FAIL] {error}", file=sys.stderr)
        sys.exit(1)
