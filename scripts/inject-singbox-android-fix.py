#!/usr/bin/env python3
"""Anchor-guarded sing-box source injector for the Android CLI nil-monitor crash.

MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157

Why this file exists
--------------------
MarbleNG runs the pinned sing-box extended core the way a terminal does: `ProcessBuilder`
spawns the release binary as a child of the app process. That child has an app UID and no
`PlatformInterface`, which is the one combination the core's Android code does not survive:

  1. `sing-tun` refuses to open a netlink socket on `GOOS=android`
     (`tun.ErrNetlinkBanned`, "netlink socket in Android is banned by Google"), so
     `route.NewNetworkManager` tolerates the error and leaves **both** `networkMonitor`
     and `interfaceMonitor` nil — unless `route.auto_detect_interface` is set, in which
     case the same error is fatal. MarbleNG never sets it, so the monitors stay nil.
  2. `route/network.go` then initialises the Android package manager unconditionally
     (`if C.IsAndroid && r.platformInterface == nil`), which reads
     `/data/system/packages.xml`. That file is `0660 system:system`, so an app UID gets
     `permission denied`; the core logs `WARN network: initialize package manager: read
     packages list: …` and leaves `packageManager` nil too.
  3. `protocol/direct/outbound.go` calls `h.network.InterfaceMonitor().MyInterfaces()` in
     `fetchMyAddresses()`, reached from `Outbound.Start()` at `StartStatePostStart` for
     **every** `direct` outbound. With a nil monitor that is a method call on a nil
     interface:

         panic: runtime error: invalid memory address or nil pointer dereference
         [signal SIGSEGV: segmentation violation code=0x1 addr=0x30 pc=0x…]

     The process dies with exit status 2, which MarbleNG surfaced as
     `core-start: exited 2: …`. Because every MarbleNG config carries a `direct` outbound
     (bypass routing, rule-set downloads, the DNS bootstrap detour), *every* sing-box
     start crashed: the session went BLOCKED, failover replayed the same crash once per
     node, and URL test / Real delay reported `reachable=0` for the whole subscription.

There is no configuration that avoids it. `action: "direct"` is parsed by 1.14 but the
route router never selects it, so bypass traffic can only be expressed through a `direct`
outbound, and a `direct` outbound always reaches the crashing line.

The fix is upstream's own
-----------------------
SagerNet/sing-box issue #4498 (2026-09-05) reports exactly this trace from an unprivileged
Android CLI. Upstream fixed it the next day in commit
`288411b0b9044c11a00a8ab478000e3ec1133101` — "Fix crash when interface monitor is
unavailable" — by nil-guarding every site that assumes a monitor exists. That commit is
newer than the release MarbleNG pins (`v1.14.0-extended-2.7.1`), and the extended fork has
not merged it, so this script backports it onto the pinned source before compiling.

Four of the five edits below are that commit verbatim. The fifth (`DefaultNetworkInterface`)
is the same guard one level up: three rule items (`network_type`, `network_is_expensive`,
`network_is_constrained`) call it with no nil check of their own, so guarding the accessor
retires the whole class instead of one caller at a time.

The script is idempotent (a second run is a no-op), anchor-guarded (a moved anchor fails the
build loudly instead of silently shipping an unpatched core), and it writes the Go regression
tests that `scripts/prepare-native.sh` and `.github/workflows/verify.yml` run before any ABI
is compiled. Those tests reproduce the Android condition on any host: a `NetworkManager`
stub whose `InterfaceMonitor()` returns nil is exactly what an app-UID child gets on a
device, so the crash is pinned even though CI runs on Linux.
"""

from pathlib import Path
import sys

MARKER = "MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157"

# Upstream commit this backports, recorded in every injected file so a future reader can
# diff MarbleNG's core against the fork it was built from.
UPSTREAM_COMMIT = "288411b0b9044c11a00a8ab478000e3ec1133101"
UPSTREAM_ISSUE = "https://github.com/SagerNet/sing-box/issues/4498"


class Patch:
    """One anchored replacement: `old` must appear exactly once, `new` replaces it."""

    def __init__(self, path, old, new, why):
        self.path = path
        self.old = old
        self.new = new
        self.why = why


PATCHES = [
    # ── The crash itself: `direct` outbound PostStart → nil interface monitor ──────────
    Patch(
        "protocol/direct/outbound.go",
        "func (h *Outbound) fetchMyAddresses() {\n"
        "\tmyInterfaceNames := h.network.InterfaceMonitor().MyInterfaces()\n",
        "func (h *Outbound) fetchMyAddresses() {\n"
        "\t// " + MARKER + ": an app-UID CLI child on Android has no interface monitor\n"
        "\t// (sing-tun bans netlink there), so `direct` must not assume one exists.\n"
        "\t// Backport of upstream " + UPSTREAM_COMMIT + ".\n"
        "\tinterfaceMonitor := h.network.InterfaceMonitor()\n"
        "\tif interfaceMonitor == nil {\n"
        "\t\treturn\n"
        "\t}\n"
        "\tmyInterfaceNames := interfaceMonitor.MyInterfaces()\n",
        "direct outbound SIGSEGV at StartStatePostStart (core-start: exited 2)",
    ),
    # ── Same guard one level up: three rule items call this accessor unguarded ─────────
    Patch(
        "route/network.go",
        "func (r *NetworkManager) DefaultNetworkInterface() *adapter.NetworkInterface {\n"
        "\tiif := r.interfaceMonitor.DefaultInterface()\n",
        "func (r *NetworkManager) DefaultNetworkInterface() *adapter.NetworkInterface {\n"
        "\t// " + MARKER + ": nil without netlink, which is every Android CLI child.\n"
        "\tif r.interfaceMonitor == nil {\n"
        "\t\treturn nil\n"
        "\t}\n"
        "\tiif := r.interfaceMonitor.DefaultInterface()\n",
        "network_type / network_is_expensive / network_is_constrained rule items",
    ),
    # ── Upstream 288411b0: dhcp transport registers a callback on a nil monitor ────────
    Patch(
        "dns/transport/dhcp/dhcp.go",
        "\tif t.interfaceName == \"\" {\n"
        "\t\tt.interfaceCallback = t.networkManager.InterfaceMonitor().RegisterCallback(t.interfaceUpdated)\n"
        "\t}\n",
        "\tif t.interfaceName == \"\" {\n"
        "\t\t// " + MARKER + ": backport of upstream " + UPSTREAM_COMMIT + ".\n"
        "\t\tinterfaceMonitor := t.networkManager.InterfaceMonitor()\n"
        "\t\tif interfaceMonitor == nil {\n"
        "\t\t\treturn E.New(\"missing monitor for auto DHCP, set route.auto_detect_interface\")\n"
        "\t\t}\n"
        "\t\tt.interfaceCallback = interfaceMonitor.RegisterCallback(t.interfaceUpdated)\n"
        "\t}\n",
        "dhcp DNS transport callback registration",
    ),
    # ── Upstream 288411b0: default_interface_address rule item ────────────────────────
    Patch(
        "route/rule/rule_default_interface_address.go",
        "func (r *DefaultInterfaceAddressItem) Match(metadata *adapter.InboundContext) bool {\n"
        "\tdefaultInterface := r.interfaceMonitor.DefaultInterface()\n",
        "func (r *DefaultInterfaceAddressItem) Match(metadata *adapter.InboundContext) bool {\n"
        "\t// " + MARKER + ": backport of upstream " + UPSTREAM_COMMIT + ".\n"
        "\tif r.interfaceMonitor == nil {\n"
        "\t\treturn false\n"
        "\t}\n"
        "\tdefaultInterface := r.interfaceMonitor.DefaultInterface()\n",
        "default_interface_address rule item",
    ),
    # ── Upstream 288411b0: network_interface_address rule item ────────────────────────
    Patch(
        "route/rule/rule_network_interface_address.go",
        "\tinterfaces := r.networkManager.NetworkInterfaces()\n"
        "\tmyInterfaces := r.networkManager.InterfaceMonitor().MyInterfaces()\n",
        "\tinterfaces := r.networkManager.NetworkInterfaces()\n"
        "\t// " + MARKER + ": backport of upstream " + UPSTREAM_COMMIT + ".\n"
        "\tvar myInterfaces []string\n"
        "\tinterfaceMonitor := r.networkManager.InterfaceMonitor()\n"
        "\tif interfaceMonitor != nil {\n"
        "\t\tmyInterfaces = interfaceMonitor.MyInterfaces()\n"
        "\t}\n",
        "network_interface_address rule item",
    ),
]


DIRECT_TEST = '''package direct

// {marker} — regression pin for the crash that killed every sing-box start on Android.
//
// An app-UID CLI child gets no interface monitor: sing-tun refuses netlink on GOOS=android,
// route.NewNetworkManager tolerates that refusal, and Outbound.Start(StartStatePostStart)
// then called InterfaceMonitor().MyInterfaces() on a nil interface — SIGSEGV, exit status 2,
// reported by MarbleNG as `core-start: exited 2`.
//
// Upstream reproduced it in {issue} and fixed it in {commit}. This test recreates the
// device condition on any host, so the guard is pinned by `go test` and not by a device.

import (
\t"context"
\t"testing"

\t"github.com/sagernet/sing-box/adapter"
\t"github.com/sagernet/sing-tun"
)

// marbleNoInterfaceMonitor is the Android CLI condition: every other NetworkManager method
// is left nil on purpose, so a future caller that reaches for one fails here instead of on
// a phone.
type marbleNoInterfaceMonitor struct {{
\tadapter.NetworkManager
}}

func (marbleNoInterfaceMonitor) InterfaceMonitor() tun.DefaultInterfaceMonitor {{ return nil }}

func TestMarbleDirectOutboundStartsWithoutAnInterfaceMonitor(t *testing.T) {{
\th := &Outbound{{network: marbleNoInterfaceMonitor{{}}}}
\tfor _, stage := range []adapter.StartStage{{adapter.StartStatePostStart, adapter.StartStateStarted}} {{
\t\tif err := h.Start(stage); err != nil {{
\t\t\tt.Fatalf("Start(stage=%d) = %v, want nil", stage, err)
\t\t}}
\t}}
\t// The interface-update path reaches the same accessor and is what a network change
\t// would trigger mid-session.
\th.InterfaceUpdated(context.Background())
\tif addresses := h.myAddresses.Load(); len(addresses) != 0 {{
\t\tt.Fatalf("myAddresses = %v, want none without an interface monitor", addresses)
\t}}
}}
'''


ROUTE_TEST = '''package route

// {marker} — the accessor three rule items (network_type, network_is_expensive,
// network_is_constrained) call without a nil check of their own. Guarding it here retires
// the whole class instead of one caller at a time. Upstream {commit}.

import "testing"

func TestMarbleDefaultNetworkInterfaceWithoutAnInterfaceMonitor(t *testing.T) {{
\tmanager := &NetworkManager{{}}
\tif networkInterface := manager.DefaultNetworkInterface(); networkInterface != nil {{
\t\tt.Fatalf("DefaultNetworkInterface() = %v, want nil without an interface monitor", networkInterface)
\t}}
}}
'''

TESTS = {
    "protocol/direct/marble_android_monitor_test.go": DIRECT_TEST,
    "route/marble_android_monitor_test.go": ROUTE_TEST,
}


def render(template: str) -> str:
    return template.format(marker=MARKER, commit=UPSTREAM_COMMIT, issue=UPSTREAM_ISSUE)


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
        raise SystemExit("usage: inject-singbox-android-fix.py SINGBOX_SOURCE")

    root = Path(sys.argv[1]).resolve()
    if not (root / "go.mod").is_file():
        raise SystemExit("not a sing-box source tree: " + str(root))

    module = (root / "go.mod").read_text(encoding="utf-8").splitlines()[0]
    if module.strip() != "module github.com/sagernet/sing-box":
        raise SystemExit("unexpected sing-box module line: " + module)

    results = []
    for patch in PATCHES:
        results.append((patch.path, apply_patch(root, patch)))

    for relative, template in TESTS.items():
        target = root / relative
        target.write_text(render(template), encoding="utf-8")
        results.append((relative, "regression test written"))

    # A patched tree must carry the marker in the file that used to crash, otherwise the
    # build verification in prepare-native.sh has nothing to grep for.
    if MARKER not in (root / "protocol/direct/outbound.go").read_text(encoding="utf-8"):
        raise SystemExit("crash fix marker missing after injection")

    print("sing-box Android CLI crash backport (" + UPSTREAM_COMMIT[:12] + "):")
    for path, state in results:
        print("  [" + state + "] " + path)


if __name__ == "__main__":
    main()
