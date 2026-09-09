#!/usr/bin/env python3
"""Anchor-guarded sing-box source injector for the Go 1.27 http2 force-close break.

MARBLE_SINGBOX_GO127_FORCE_CLOSE_V161

Why this file exists
--------------------
Build run #247 ("Build signed Android APKs", main @ 37f4be7, the merge of PR #135) failed
after 7m41s at "Building sing-box for arm64-v8a" with

    ld.lld: error: undefined symbol: golang.org/x/net/http2.(*Transport).connPool
    >>> referenced by go.go
    >>>               go.o:(github.com/sagernet/sing-box/transport/v2rayxhttp.(*DefaultDialerClient).Close)

The pinned sing-box commit did not change, and neither did a single one of its go.sum
entries. What changed was the toolchain:

  * core-lock bot commit 589afca (2026-09-09 08:10 UTC, pushed with GITHUB_TOKEN so it
    triggered no build of its own) bumped Xray v26.7.28 -> v26.9.9.
  * build.yml resolves the Go toolchain from the pinned Xray's go.mod
    (`go-version-file: .bootstrap/xray/go.mod`), and that directive moved from
    `go 1.26` to `go 1.27`, so the first build after the bump compiled with Go 1.27.1.
  * From golang.org/x/net v0.57.0 on, `x/net/http2` compiled under go1.27 (without the
    `http2legacy` tag) is no longer an implementation at all: `transport.go` and
    `client_conn_pool.go` are gated `//go:build !(go1.27 && !http2legacy)` and a thin
    wrapper (`transport_wrap.go`, `//go:build go1.27 && !http2legacy`) delegates to
    net/http's internal http2. The unexported `(*Transport).connPool` method — the symbol
    `transport/v2rayxhttp/dialer.go` pulls with `//go:linkname` to force-close pooled
    connections — stops existing, and the pull dies at link time.

The extended fork already knows this: `transport/v2rayhttp` was split into
`force_close_legacy.go` (`!go1.27`), `force_close_go127.go` (`go1.27 && badlinkname`) and
`force_close_go127_stub.go` (`go1.27 && !badlinkname`) — but `transport/v2rayxhttp` never
got the same treatment, and the fork's own CI cannot notice because it pins Go 1.26.7,
which always takes the legacy file. MarbleNG builds with the Xray-mandated toolchain and
the `badlinkname` tag, so it is the first caller to reach the stale declaration.

The fix
-------
Exactly the fork's own three-way split, ported to `transport/v2rayxhttp`:

  * `dialer.go` loses the `clientConnPool` mirror and the `transportConnPool` linkname;
    `DefaultDialerClient.Close()` now calls `closeHTTP2Connections(transport)` for the
    http2 case, exactly like `v2rayhttp`'s `ResetTransport` does.
  * `force_close_legacy.go` (`!go1.27`) carries the old code verbatim — on Go <= 1.26 the
    legacy symbol exists and links.
  * `force_close_go127.go` (`go1.27 && badlinkname`) mirrors the fork's own v2rayhttp
    file: `(*Transport).init` unwraps the wrapper to the `*http.Transport` it configured,
    net/http push-linknames the internal `*http2.Transport` out as
    `net/http/internal/http2_test.transportFromH1Transport`, and mirrored layouts of
    `net/http/internal/http2.Transport` / `.clientConnPool` reach the pooled
    `*ClientConn`s to close them. Every pulled symbol and every mirrored field offset was
    verified against the go1.27.1 and x/net v0.57.0 sources:

        net/http/internal/http2.Transport{ t1 TransportConfig (interface, 2 words);
                                          connPool noDialClientConnPool (one-word
                                          struct{*clientConnPool}) }
        net/http/internal/http2.clientConnPool{ t *Transport; mu sync.Mutex;
                                                conns map[string][]*ClientConn; ... }
        (*ClientConn).Close            transport.go:1045
        transportFromH1Transport       pushed by net/http/http2.go:557

  * `force_close_go127_stub.go` (`go1.27 && !badlinkname`) degrades to the public
    `CloseIdleConnections()`, the fork's own no-license-to-linkname fallback.

The script is idempotent (a second run is a no-op) and anchor-guarded: a moved anchor
fails the build loudly instead of silently shipping an unpatched core. It also writes a
compile-and-link regression test per toolchain era, which `scripts/prepare-native.sh`
runs as `go test -tags badlinkname ./transport/v2rayxhttp` — under the release toolchain
that links the Go 1.27 variant and its linknames in seconds, before any ABI is compiled.
"""

from pathlib import Path
import sys

MARKER = "MARBLE_SINGBOX_GO127_FORCE_CLOSE_V161"

# The build whose failure this backports, for every future reader of the injected tree.
INCIDENT_RUN = "https://github.com/marble098/MarbleNG/actions/runs/34335407394"
FORK_REFERENCE = (
    "transport/v2rayhttp/force_close_{legacy,go127,go127_stub}.go "
    "(shtorm-7/sing-box-extended v1.14.0-extended-2.7.1)"
)


class Patch:
    """One anchored replacement: `old` must appear exactly once, `new` replaces it."""

    def __init__(self, path, old, new, why):
        self.path = path
        self.old = old
        self.new = new
        self.why = why


PATCHES = [
    # ── The stale declaration: mirror type + linkname leave dialer.go ─────────────────
    Patch(
        "transport/v2rayxhttp/dialer.go",
        "type clientConnPool struct {\n"
        "\tt     *http2.Transport\n"
        "\tmu    sync.Mutex\n"
        "\tconns map[string][]*http2.ClientConn // key is host:port\n"
        "}\n"
        "\n"
        "type efaceWords struct {\n"
        "\ttyp  unsafe.Pointer\n"
        "\tdata unsafe.Pointer\n"
        "}\n"
        "\n"
        "//go:linkname transportConnPool golang.org/x/net/http2.(*Transport).connPool\n"
        "func transportConnPool(t *http2.Transport) http2.ClientConnPool\n",
        "// " + MARKER + " — the http2 pool force-close moved out of this file into\n"
        "// build-tag-split variants (force_close_legacy.go / force_close_go127.go /\n"
        "// force_close_go127_stub.go). The linkname this file used to pull existed only in\n"
        "// the pre-1.27 golang.org/x/net/http2; the pinned Xray's go 1.27 toolchain made it\n"
        "// an undefined symbol at link time (build run #247). efaceWords stays here: both\n"
        "// the legacy and the Go 1.27 variants cast through it.\n"
        "type efaceWords struct {\n"
        "\ttyp  unsafe.Pointer\n"
        "\tdata unsafe.Pointer\n"
        "}\n",
        "legacy http2 clientConnPool mirror + connPool linkname (undefined on go1.27)",
    ),
    # ── Close(): the http2 case delegates to the variant files ────────────────────────
    Patch(
        "transport/v2rayxhttp/dialer.go",
        "\tcase *http2.Transport:\n"
        "\t\tconnPool := transportConnPool(transport)\n"
        "\t\tp := (*clientConnPool)((*efaceWords)(unsafe.Pointer(&connPool)).data)\n"
        "\t\tp.mu.Lock()\n"
        "\t\tdefer p.mu.Unlock()\n"
        "\t\tfor _, vv := range p.conns {\n"
        "\t\t\tfor _, cc := range vv {\n"
        "\t\t\t\tcc.Close()\n"
        "\t\t\t}\n"
        "\t\t}\n",
        "\tcase *http2.Transport:\n"
        "\t\t// " + MARKER + ": toolchain-era dependent, see the force_close_*.go files.\n"
        "\t\tcloseHTTP2Connections(transport)\n",
        "DefaultDialerClient.Close http2 branch (linked the undefined symbol)",
    ),
]

LEGACY_FILE = '''//go:build !go1.27

package xhttp

import (
\t"sync"
\t"unsafe"

\t"golang.org/x/net/http2"
)

// ''' + MARKER + ''' — the pre-1.27 force-close. On Go <= 1.26 golang.org/x/net/http2 is
// its own implementation and (*Transport).connPool is reachable by linkname, so this is
// the fork's original code, moved out of dialer.go unchanged so the two toolchain eras
// stop sharing a declaration. See also the fork's own
// ''' + FORK_REFERENCE + '''.

type clientConnPool struct {
\tt     *http2.Transport
\tmu    sync.Mutex
\tconns map[string][]*http2.ClientConn // key is host:port
}

func closeHTTP2Connections(transport *http2.Transport) {
\tconnPool := transportConnPool(transport)
\tp := (*clientConnPool)((*efaceWords)(unsafe.Pointer(&connPool)).data)
\tp.mu.Lock()
\tdefer p.mu.Unlock()
\tfor _, vv := range p.conns {
\t\tfor _, cc := range vv {
\t\t\tcc.Close()
\t\t}
\t}
}

//go:linkname transportConnPool golang.org/x/net/http2.(*Transport).connPool
func transportConnPool(t *http2.Transport) http2.ClientConnPool
'''

GO127_FILE = '''//go:build go1.27 && badlinkname

package xhttp

import (
\t"net/http"
\t"sync"
\t"unsafe"

\t"golang.org/x/net/http2"
)

// ''' + MARKER + ''' — the Go 1.27 force-close, mirroring the fork's own
// ''' + FORK_REFERENCE + ''',
// which fixed that package for the x/net http2 wrapper but missed this one.
//
// From golang.org/x/net v0.57.0 on, http2 compiled under go1.27 (without http2legacy) is
// a wrapper over net/http's internal http2, so the legacy (*Transport).connPool symbol no
// longer exists and the linkname that used to live in dialer.go dies at link time with
//
//\tld.lld: error: undefined symbol: golang.org/x/net/http2.(*Transport).connPool
//
// (build run #247: the pinned Xray v26.9.9 moved the release toolchain to Go 1.27). The
// path below is the wrapper's own: init() unwraps to the *http.Transport it configured,
// net/http pushes the internal *http2.Transport out through transportFromH1Transport,
// and the mirrored layouts of net/http/internal/http2.Transport and .clientConnPool
// (verified against go1.27.1) reach the pooled *ClientConns to close them.

// cmd/compile creates the method symbols reachable from *http.Transport's field types with
// their package recorded when the type is first used by a declaration; a linkname pull
// processed afterwards reuses that symbol as a package-indexed reference, which the linker's
// -checklinkname does not inspect. This declaration must precede the linkname declarations.
var _ *http.Transport

// net/http/internal/http2.Transport
type internalTransport struct {
\tt1       [2]uintptr // TransportConfig (interface)
\tconnPool *clientConnPool
}

// net/http/internal/http2.clientConnPool
type clientConnPool struct {
\tt     *internalTransport
\tmu    sync.Mutex
\tconns map[string][]unsafe.Pointer // key is host:port, value is []*ClientConn
}

func closeHTTP2Connections(transport *http2.Transport) {
\th2Transport := transportFromH1Transport(transportInit(transport))
\tt := (*internalTransport)((*efaceWords)(unsafe.Pointer(&h2Transport)).data)
\tif t == nil {
\t\treturn
\t}
\tp := t.connPool
\tp.mu.Lock()
\tdefer p.mu.Unlock()
\tfor _, vv := range p.conns {
\t\tfor _, cc := range vv {
\t\t\tclientConnClose(cc)
\t\t}
\t}
}

//go:linkname transportInit golang.org/x/net/http2.(*Transport).init
func transportInit(t *http2.Transport) *http.Transport

//go:linkname transportFromH1Transport net/http/internal/http2_test.transportFromH1Transport
func transportFromH1Transport(t *http.Transport) any

//go:linkname clientConnClose net/http/internal/http2.(*ClientConn).Close
func clientConnClose(cc unsafe.Pointer) error
'''

GO127_STUB_FILE = '''//go:build go1.27 && !badlinkname

package xhttp

import "golang.org/x/net/http2"

// ''' + MARKER + ''' — without badlinkname there is no license to reach into net/http's
// internals, so closing the pooled connections degrades to the public
// CloseIdleConnections. Same shape as the fork's own
// ''' + FORK_REFERENCE + '''.

func closeHTTP2Connections(transport *http2.Transport) {
\ttransport.CloseIdleConnections()
}
'''

# One compile-and-link pin per toolchain era. `go test -tags badlinkname` compiles AND
# links the package under the running toolchain, so an undefined linkname fails here in
# seconds — which is exactly how run #247 should have died, before 4 minutes of Xray and
# HEV builds and the first ABI's link attempt.
LEGACY_TEST = '''//go:build !go1.27

package xhttp

// ''' + MARKER + ''' — pins the pre-1.27 force-close variant: the legacy linkname must
// resolve under the running (<= 1.26) toolchain, and Close() must reach it.

import (
\t"testing"

\t"golang.org/x/net/http2"
)

func TestMarbleLegacyForceCloseLinks(t *testing.T) {
\tvar forceClose func(*http2.Transport) = closeHTTP2Connections
\tif forceClose == nil {
\t\tt.Fatal("unreachable")
\t}
}
'''

GO127_TEST = '''//go:build go1.27

package xhttp

// ''' + MARKER + ''' — pins the Go 1.27 force-close variant. Which of the three files
// implements closeHTTP2Connections is decided by the badlinkname tag; this test only has
// to compile and link the selected one, so a stale linkname (the run #247 failure, an
// undefined golang.org/x/net/http2.(*Transport).connPool) fails `go test -tags
// badlinkname ./transport/v2rayxhttp` before any ABI is compiled.

import (
\t"testing"

\t"golang.org/x/net/http2"
)

func TestMarbleGo127ForceCloseLinks(t *testing.T) {
\tvar forceClose func(*http2.Transport) = closeHTTP2Connections
\tif forceClose == nil {
\t\tt.Fatal("unreachable")
\t}
}
'''

FILES = {
    "transport/v2rayxhttp/force_close_legacy.go": LEGACY_FILE,
    "transport/v2rayxhttp/force_close_go127.go": GO127_FILE,
    "transport/v2rayxhttp/force_close_go127_stub.go": GO127_STUB_FILE,
    "transport/v2rayxhttp/marble_force_close_test.go": LEGACY_TEST,
    "transport/v2rayxhttp/marble_force_close_go127_test.go": GO127_TEST,
}


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
        raise SystemExit("usage: inject-singbox-go127-fix.py SINGBOX_SOURCE")

    root = Path(sys.argv[1]).resolve()
    if not (root / "go.mod").is_file():
        raise SystemExit("not a sing-box source tree: " + str(root))

    module = (root / "go.mod").read_text(encoding="utf-8").splitlines()[0]
    if module.strip() != "module github.com/sagernet/sing-box":
        raise SystemExit("unexpected sing-box module line: " + module)

    dialer = root / "transport" / "v2rayxhttp" / "dialer.go"
    if not dialer.is_file():
        raise SystemExit(
            "sing-box source is not the extended fork: transport/v2rayxhttp/dialer.go "
            "does not exist (upstream sing-box has no v2rayxhttp package)"
        )

    results = []
    for patch in PATCHES:
        results.append((patch.path, apply_patch(root, patch)))

    for relative, content in FILES.items():
        target = root / relative
        target.write_text(content, encoding="utf-8")
        if MARKER not in content:
            raise SystemExit("generated file lost its marker: " + relative)
        results.append((relative, "variant written"))

    # A patched tree must carry the marker in the file that used to fail linking,
    # otherwise the build verification in prepare-native.sh has nothing to grep for.
    if MARKER not in dialer.read_text(encoding="utf-8"):
        raise SystemExit("Go 1.27 force-close marker missing after injection")

    print("sing-box Go 1.27 v2rayxhttp force-close (" + MARKER + "):")
    print("  incident : " + INCIDENT_RUN)
    for path, state in results:
        print("  [" + state + "] " + path)


if __name__ == "__main__":
    main()
