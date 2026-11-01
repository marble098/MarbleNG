#!/usr/bin/env bash
# MARBLE_ROUTING_ASSET_PIN_V257 — the release-build failure of 2026-11-01, reproduced offline.
#
# scripts/bundle-routing-assets.sh exists because the old step fetched `geoip.dat` and its
# `.sha256sum` separately **by branch path** from a source that force-pushes a whole new commit on
# every publish. Two requests two seconds apart can therefore land on two different snapshots; the
# digest does not match; the step exits 1 with no message. This file proves the new fetcher cannot
# do that, and that it recovers when a snapshot is torn anyway:
#
#   1. a clean pair is installed, and the digests are the published ones
#   2. **the production race**: the first resolved commit carries one file from snapshot A and a
#      digest from snapshot B, the next commit is whole → the run retries once, installs the second
#      snapshot, and never leaves the torn files in the destination
#   3. nothing verifies → exit 1, destination untouched
#
# No network: a local stub serves the tree and answers the branch API from a queue of commits.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

FETCHER="$ROOT/scripts/bundle-routing-assets.sh"
REPO="Chocolate4U/Iran-v2ray-rules"
REF="release"

WORK="$(mktemp -d)"
SERVER_PID=""
cleanup() {
    if [ -n "$SERVER_PID" ]; then kill "$SERVER_PID" 2>/dev/null || true; fi
    rm -rf "$WORK"
}
trap cleanup EXIT

pass() { printf '[OK] %s\n' "$*"; }
die() { printf '[FAIL] %s\n' "$*" >&2; exit 1; }

# --- the stub -------------------------------------------------------------------------------------

cat > "$WORK/stub.py" <<'PY'
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

root = Path(sys.argv[1])
port_file = Path(sys.argv[2])
REPO = "Chocolate4U/Iran-v2ray-rules"
REF = "release"
counters = {"api": 0}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def _send(self, code, body, ctype="application/octet-stream"):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path == f"/api/repos/{REPO}/branches/{REF}":
            queue = json.loads((root / "queue.json").read_text())
            index = min(counters["api"], len(queue) - 1)
            counters["api"] += 1
            body = json.dumps({"name": REF, "commit": {"sha": queue[index]}}).encode()
            return self._send(200, body, "application/json")
        if path.startswith("/raw/"):
            candidate = root / path[len("/raw/"):].lstrip("/")
            if candidate.is_file():
                return self._send(200, candidate.read_bytes())
        return self._send(404, b"not found", "text/plain")


server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
port_file.write_text(str(server.server_address[1]))
server.serve_forever()
PY

# A fresh server per test: the branch-API counter lives in the stub process, and a test that
# depends on "the first resolve returns the torn commit" has to start from zero.
start_server() {
    if [ -n "$SERVER_PID" ]; then
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
        SERVER_PID=""
    fi
    rm -f "$WORK/port"
    # The stub serves `$WORK/tree/raw` at `/raw/...`, which is where `publish` writes.
    python3 "$WORK/stub.py" "$WORK/tree/raw" "$WORK/port" &
    SERVER_PID=$!
    for _ in $(seq 1 100); do
        [ -s "$WORK/port" ] && break
        sleep 0.1
    done
    [ -s "$WORK/port" ] || die "the stub server did not come up"
    PORT="$(cat "$WORK/port")"
}

# Every test starts from the same served tree and a fresh queue of commits.
reset_tree() {
    rm -rf "$WORK/tree"
    mkdir -p "$WORK/tree"
}

# publish <sha> <geoip-body> <geosite-body> [torn]
#   `torn` names the file whose digest is deliberately taken from the *other* snapshot.
publish() {
    local sha="$1" geoip="$2" geosite="$3" torn="${4:-}"
    local dir="$WORK/tree/raw/$REPO/$sha"
    mkdir -p "$dir"
    printf '%s' "$geoip" > "$dir/geoip.dat"
    printf '%s' "$geosite" > "$dir/geosite.dat"
    local geoip_sum geosite_sum
    geoip_sum="$(sha256sum "$dir/geoip.dat" | awk '{print $1}')"
    geosite_sum="$(sha256sum "$dir/geosite.dat" | awk '{print $1}')"
    # A torn snapshot is exactly what a publish landing between two requests produces: the file
    # belongs to one commit and the digest file to the next one.
    if [ "$torn" = "geoip" ]; then geoip_sum="$(printf 'stale-%s' "$geoip_sum" | sha256sum | awk '{print $1}')"; fi
    if [ "$torn" = "geosite" ]; then geosite_sum="$(printf 'stale-%s' "$geosite_sum" | sha256sum | awk '{print $1}')"; fi
    printf '%s  %s\n' "$geoip_sum" "geoip.dat" > "$dir/geoip.dat.sha256sum"
    printf '%s  %s\n' "$geosite_sum" "geosite.dat" > "$dir/geosite.dat.sha256sum"
}

body_for() {
    local label="$1"
    python3 - "$label" <<'PY'
import sys
label = sys.argv[1]
print(("# MarbleNG routing fixture " + label + " ") * 64)
PY
}

run_fetcher() {
    local dest="$1"; shift
    MARBLE_ROUTING_REPO="$REPO" \
    MARBLE_ROUTING_REF="$REF" \
    MARBLE_ROUTING_API_BASE="http://127.0.0.1:$PORT/api" \
    MARBLE_ROUTING_RAW_BASE="http://127.0.0.1:$PORT/raw" \
    MARBLE_ROUTING_CDN_BASE="http://127.0.0.1:$PORT/raw" \
    MARBLE_ROUTING_BACKOFF_SECONDS=0 \
    MARBLE_ROUTING_DEST="$dest" \
    "$@" bash "$FETCHER"
}

[[ -f "$FETCHER" ]] || die "missing $FETCHER"
bash -n "$FETCHER"

SHA_A="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
SHA_B="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
GEOIP_A="$(body_for geoip-snapshot-a)"
GEOSITE_A="$(body_for geosite-snapshot-a)"
GEOIP_B="$(body_for geoip-snapshot-b)"
GEOSITE_B="$(body_for geosite-snapshot-b)"

# --- 1. a clean pair is installed, and it is the published pair ------------------------------------
reset_tree
start_server
publish "$SHA_A" "$GEOIP_A" "$GEOSITE_A"
printf '["%s"]\n' "$SHA_A" > "$WORK/tree/raw/queue.json"
DEST="$WORK/out-1"
mkdir -p "$DEST"
log="$(run_fetcher "$DEST" env MARBLE_ROUTING_ATTEMPTS=1 2>&1)" || die "clean snapshot failed:
$log"
[ -s "$DEST/geoip.dat" ] || die "geoip.dat was not installed"
[ -s "$DEST/geosite.dat" ] || die "geosite.dat was not installed"
expected="$(awk 'NF {print $1; exit}' "$WORK/tree/raw/$REPO/$SHA_A/geoip.dat.sha256sum")"
actual="$(sha256sum "$DEST/geoip.dat" | awk '{print $1}')"
[ "$expected" = "$actual" ] || die "installed geoip.dat does not match the published digest"
grep -q "Pinned snapshot: $REPO@$SHA_A" <<<"$log" || die "the pinned snapshot is not reported:
$log"
grep -q "\[OK\] geoip.dat" <<<"$log" || die "the installed digest is not reported:
$log"
pass "clean snapshot installs the published pair ($SHA_A)"

# --- 2. the production race: a torn snapshot, then a whole one -------------------------------------
reset_tree
start_server
publish "$SHA_A" "$GEOIP_A" "$GEOSITE_A" geoip
publish "$SHA_B" "$GEOIP_B" "$GEOSITE_B"
printf '["%s","%s"]\n' "$SHA_A" "$SHA_B" > "$WORK/tree/raw/queue.json"
DEST="$WORK/out-2"
mkdir -p "$DEST"
if ! log="$(run_fetcher "$DEST" env MARBLE_ROUTING_ATTEMPTS=2 2>&1)"; then
    die "a torn first snapshot was not recovered:
$log"
fi
grep -q "did not verify" <<<"$log" || die "the torn snapshot was not reported as torn:
$log"
grep -q "Pinned snapshot: $REPO@$SHA_B" <<<"$log" || die "the retry did not land on the second commit:
$log"
installed="$(sha256sum "$DEST/geoip.dat" | awk '{print $1}')"
wanted="$(sha256sum "$WORK/tree/raw/$REPO/$SHA_B/geoip.dat" | awk '{print $1}')"
[ "$installed" = "$wanted" ] || die "the destination kept the torn file instead of the whole one"
[ -z "$(ls -A "$DEST" | grep 'part$' || true)" ] || die "a partial download was left behind"
pass "a torn snapshot is retried and replaced by the whole one ($SHA_A -> $SHA_B)"

# --- 3. nothing verifies: exit 1, destination untouched --------------------------------------------
reset_tree
start_server
publish "$SHA_A" "$GEOIP_A" "$GEOSITE_A" geoip
publish "$SHA_B" "$GEOIP_B" "$GEOSITE_B" geosite
printf '["%s","%s"]\n' "$SHA_A" "$SHA_B" > "$WORK/tree/raw/queue.json"
DEST="$WORK/out-3"
mkdir -p "$DEST"
if log="$(run_fetcher "$DEST" env MARBLE_ROUTING_ATTEMPTS=2 2>&1)"; then
    die "a run in which nothing verified reported success:
$log"
fi
grep -q "expected" <<<"$log" || die "the mismatch was not named in the output:
$log"
grep -q "no verified routing snapshot" <<<"$log" || die "the give-up message is missing:
$log"
[ -z "$(ls -A "$DEST")" ] || die "a failed run wrote into the destination: $(ls -A "$DEST")"
pass "an unverifiable snapshot installs nothing and says why"

echo
echo "[OK] MARBLE_ROUTING_ASSET_PIN_V257 fixtures passed"
