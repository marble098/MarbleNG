#!/usr/bin/env python3
# Anchor-guarded Xray source injector for MarbleNG Realtime Engine.
#
# The function span includes its closing brace before looking for `return nil`,
# so the tail anchor cannot be lost by slicing at the boundary.
# MARBLE_REALTIME_ENGINE_V70

from pathlib import Path
import shutil
import sys

HOOK = "marbleTrackSocket(fd, network, address)"
SIGNATURE = "func applyOutboundSocketOptions("
BOUNDARY = "\n}\n\n// applyInboundSocketOptions"
RETURN_TAIL = "\n\treturn nil\n}"

def inject(text: str) -> str:
    if HOOK in text:
        if text.count(HOOK) != 1:
            raise SystemExit("telemetry hook count != 1")
        return text

    start = text.find(SIGNATURE)
    if start < 0:
        raise SystemExit("Xray outbound sockopt function signature changed")

    boundary = text.find(BOUNDARY, start)
    if boundary < 0:
        raise SystemExit("Xray outbound/inbound function boundary changed")

    # Include the first "\n}" of BOUNDARY in the function slice.
    function_end = boundary + 2
    function = text[start:function_end]

    tail = function.rfind(RETURN_TAIL)
    if tail < 0:
        raise SystemExit("Xray outbound final return anchor changed")

    hook = (
        "\n\tif isTCPSocket(network) {\n"
        "\t\tmarbleTrackSocket(fd, network, address)\n"
        "\t}\n"
    )
    function = (
        function[:tail]
        + hook
        + RETURN_TAIL
        + function[tail + len(RETURN_TAIL):]
    )
    result = text[:start] + function + text[function_end:]

    if result.count(HOOK) != 1:
        raise SystemExit("telemetry hook count != 1 after injection")
    return result

def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: inject-xray-realtime.py XRAY_SOURCE")

    xray = Path(sys.argv[1]).resolve()
    root = Path(__file__).resolve().parents[1]
    source = root / "native/xraypatch/marble_telemetry_linux.go"
    destination = xray / "transport/internet/marble_telemetry_linux.go"
    sockopt = xray / "transport/internet/sockopt_linux.go"

    if not source.is_file():
        raise SystemExit(f"missing telemetry source: {source}")
    if not sockopt.is_file():
        raise SystemExit(f"missing Xray sockopt: {sockopt}")

    shutil.copyfile(source, destination)
    original = sockopt.read_text(encoding="utf-8")
    patched = inject(original)
    if patched != original:
        sockopt.write_text(patched, encoding="utf-8")

    if "MARBLE_REALTIME_ENGINE_V70" not in destination.read_text(encoding="utf-8"):
        raise SystemExit("realtime telemetry marker missing")
    print("[OK] Marble realtime TCP_INFO hook injected")

if __name__ == "__main__":
    main()
