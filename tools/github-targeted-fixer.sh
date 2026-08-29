#!/usr/bin/env bash
# ============================================================================
# github-targeted-fixer.sh — targeted repo surgery over the GitHub REST API
# ============================================================================
#
# Finds and fixes specific problems in a GitHub repository WITHOUT `git clone`:
# only the files that actually need to change are fetched (tree API + contents
# API), transformed locally, verified, and pushed back as a single commit on a
# new branch with an optional pull request.
#
# Built-in fixpacks (curated from the MarbleNG bug audit):
#   ui-clean          removes verbose / self-referential UI clutter sentences
#                     from app/src/main/java/com/marbleng/app/ui/Aether2026.kt
#   locale-us         forces Locale.US decimal formatting ("%.1f".format(x)
#                     renders Persian/German decimal separators otherwise)
#   rankhelper-done   makes native/rankhelper/main.go report the number of
#                     jobs it actually executed instead of len(batch.Jobs)
#
# Usage:
#   # read-only, GitHub API only (no local checkout)
#   ./github-targeted-fixer.sh tree [path-prefix]        # list repo files
#   ./github-targeted-fixer.sh fetch <path>              # print one file
#   ./github-targeted-fixer.sh find  "<code snippet>"    # GitHub code search
#   ./github-targeted-fixer.sh fixpacks                  # list fixpacks
#   ./github-targeted-fixer.sh audit                     # static bug scan
#   ./github-targeted-fixer.sh fixpack ui-clean          # preview diff only
#
#   # apply locally (an existing working tree, still no remote write)
#   MARBLE_WORKDIR=/path/to/repo ./github-targeted-fixer.sh fixpack ui-clean --apply
#
#   # apply remotely: upload commit to a new branch + open a pull request
#   ./github-targeted-fixer.sh fixpack ui-clean --apply --remote \
#       --branch fix/ui-clean --title "refine(ui): remove clutter copy"
#
# Env:
#   MARBLE_REPO     owner/repo            (default: marble098/MarbleNG)
#   MARBLE_REF      base branch/ref       (default: main)
#   MARBLE_WORKDIR  existing local tree   (local mode when set)
#   GITHUB_TOKEN    API token             (falls back to `gh auth token`)
#
# Requires: bash 4+, curl (fallback), gh (preferred, for auth), jq, python3.
# Every transformation rule declares the exact number of matches it expects;
# if the upstream file drifted, the rule is skipped and nothing is written.
# ============================================================================

set -euo pipefail

REPO="${MARBLE_REPO:-marble098/MarbleNG}"
REF="${MARBLE_REF:-main}"
PR_BASE="$REF"
WORKDIR="${MARBLE_WORKDIR:-}"
TOKEN="${GITHUB_TOKEN:-}"
BRANCH=""
PR_TITLE=""
PR_BODY=""
APPLY=0
REMOTE=0

usage() {
  sed -n '2,32p' "$0" | sed 's/^# \{0,1\}//'
}

die() { printf 'error: %s\n' "$*" >&2; exit 1; }
info() { printf '\033[36m[fixer]\033[0m %s\n' "$*" >&2; }
ok() { printf '\033[32m[fixer]\033[0m %s\n' "$*" >&2; }

require() { command -v "$1" >/dev/null 2>&1 || die "missing required tool: $1"; }

# ----------------------------------------------------------------------------
# GitHub API access (gh preferred, plain curl + token as fallback)
# ----------------------------------------------------------------------------

resolve_token() {
  [[ -n "$TOKEN" ]] && return 0
  if command -v gh >/dev/null 2>&1; then
    TOKEN="$(gh auth token 2>/dev/null || true)"
  fi
}

api_json() { # api_json <METHOD> <api-path> [json-input-file]
  local method="$1" path="$2" input="${3:-}"
  if command -v gh >/dev/null 2>&1; then
    if [[ -n "$input" ]]; then
      gh api -X "$method" "$path" --input "$input"
    else
      gh api -X "$method" "$path"
    fi
  else
    resolve_token
    local args=(-sS -X "$method"
      -H "Accept: application/vnd.github+json"
      -H "X-GitHub-Api-Version: 2022-11-28")
    [[ -n "$TOKEN" ]] && args+=(-H "Authorization: Bearer $TOKEN") || args+=(-n)
    [[ -n "$input" ]] && args+=(--data-binary "@$input" -H "Content-Type: application/json")
    curl "${args[@]}" "https://api.github.com/$path"
  fi
}

urlencode_path() { # path with '/' kept
  python3 -c 'import sys,urllib.parse;print("/".join(urllib.parse.quote(s,safe="") for s in sys.argv[1].split("/")))' "$1"
}

fetch_file() { # fetch_file <path> [ref] -> file content on stdout
  local path="$1" ref="${2:-$REF}" raw
  raw="$(api_json GET "repos/$REPO/contents/$(urlencode_path "$path")?ref=$ref" || true)"
  if [[ -z "$raw" || "$raw" == "null" ]]; then
    die "could not fetch '$path' (branch '$ref')"
  fi
  local content download
  content="$(printf '%s' "$raw" | jq -r '.content // empty' 2>/dev/null || true)"
  if [[ -n "$content" && "$content" != "null" ]]; then
    printf '%s' "$content" | python3 -c 'import base64,sys;sys.stdout.buffer.write(base64.b64decode(sys.stdin.read()))'
    return 0
  fi
  # Large files come back without .content; fall back to the download_url.
  download="$(printf '%s' "$raw" | jq -r '.download_url // empty' 2>/dev/null || true)"
  [[ -n "$download" && "$download" != "null" ]] || die "no content and no download_url for '$path'"
  curl -fsSL "$download" || die "download_url fetch failed for '$path'"
}

# ----------------------------------------------------------------------------
# Embedded python: fixpack transforms + static audit
# ----------------------------------------------------------------------------

run_python() { python3 - "$@" <<'PYEMBED'
import difflib
import os
import re
import sys

# (regex, replacement, expected_count_or_None, description)
RULES = {
    "ui-clean": [
        (r'[ \t]*Text\("Raw evidence and retained historical logs stay in COPY/SAVE TXT only\. They are intentionally not rendered here, keeping Settings compact and responsive\.",\n\s*color=Aether\.InkFaint,style=MaterialTheme\.typography\.bodySmall\)\n',
         '', 1, 'bug-finder raw-evidence meta note'),
        (r'[ \t]*\?: Text\("Run the scan while the problem is happening\. It is passive and does not open diagnostic HTTPS/DNS connections\.",\n\s*color=Aether\.InkMuted,style=MaterialTheme\.typography\.bodySmall\)',
         '', 1, 'bug-finder empty-state meta note'),
        (r'[ \t]*Text\("Passive crash, process, thread, Xray, HEV, VPN and engine evidence\. The scanner itself does not generate Internet traffic\.",\n\s*color=Aether\.InkFaint, style=MaterialTheme\.typography\.bodySmall\)\n',
         '', 1, 'observatory subtitle'),
        (r'"Off by default\. When enabled, normal app/VPN threads only enqueue bounded events; storage work stays on a diagnostics thread\."',
         '"Off by default"', 1, 'debug-mode off subtitle'),
        (r'[ \t]*Text\("Debug Mode records a rolling live session plus Bug Finder snapshots\. Raw proxy configs and credentials are redacted\.",\n\s*color=Aether\.InkMuted, style=MaterialTheme\.typography\.bodySmall\)\n',
         '', 1, 'debug-mode meta note'),
        (r'[ \t]*Text\(\n\s*"Choose how nodes are measured everywhere: Test all, the performance test, a single node " \+\n\s*"test and the automatic route picker\.",\n\s*color = Aether\.InkFaint,\n\s*style = MaterialTheme\.typography\.bodySmall\n\s*\)\n',
         '', 1, 'probe-settings intro paragraph'),
        (r'"Select a node in Library\. Its public IP, city, network/datacenter, ASN and ISP will appear here\."',
         '"No node selected"', 1, 'server-intel empty state'),
        (r'[ \t]*Text\(\n\s*"Location and datacenter/network labels are IP-database estimates, not GPS-level physical location\.",\n\s*color=Aether\.InkFaint,\n\s*style=MaterialTheme\.typography\.labelSmall\n\s*\)\n',
         '', 1, 'ip-database disclaimer'),
        (r'[ \t]*Text\(\n\s*"Android requires a foreground-service status while the VPN/proxy is running\. The controls below manage optional alerts and how much live telemetry is shown\.",\n\s*color = Aether\.InkFaint,\n\s*style = MaterialTheme\.typography\.bodySmall\n\s*\)\n',
         '', 1, 'notification-settings intro paragraph'),
        (r'"Recommended baseline: tlshello • 100-200 • 10-20 ms\. More aggressive values can lower stability\."',
         '"Baseline: tlshello • 100-200 • 10-20 ms"', 1, 'fragment baseline note'),
        (r'"\$missingGeoAssets is required\. Signed builds contain a bundled fallback; Prepare also refreshes configured sources\."',
         '"$missingGeoAssets is required; tap Prepare to install it."', 1, 'geo-assets note'),
        (r'"Default source: Chocolate4U/Iran-v2ray-rules release branch\. Marble refreshes remote data after 24 hours and keeps the last known-good file if refresh fails\."',
         '"Default source: Chocolate4U/Iran-v2ray-rules • refreshes after 24h"', 1, 'routing-source note'),
        (r'"Privacy note: Iran-direct is intentional bypass, not a leak\. Iranian destinations see your ISP egress IP; other destinations remain on the selected proxy\. Identity Guard pins the proxied exit and strips arbitrary public direct rules\."',
         '"Iran-direct is deliberate: Iranian destinations see your ISP IP; the rest stays on the proxy."', 1, 'privacy note'),
        (r'"PattNG/Xray TLS control • `unsafe` uses native Go TLS; leave Cipher Suites empty for automatic defaults\."',
         '"`unsafe` uses native Go TLS; empty Cipher Suites = automatic."', 1, 'manual TLS note'),
        (r'"This config will be added to the currently selected source and kept across refreshes\."',
         '"Will be added to the selected source and kept across refreshes."', 1, 'manual save hint'),
        (r'"SSH carries TCP through the protected loopback adapter; UDP is blocked fail-closed\."',
         '"TCP via protected loopback; UDP blocked."', 1, 'ssh note'),
        (r'"Direct probes never open the proxy, so a node can look fast here and still fail " \+',
         '"TCP/ICMP bypass the proxy; only Real tunnel proves the route carries traffic."', 1, 'probe warning (part 1)'),
        (r'\n\s*"to carry traffic\. Auto-connect still verifies the route it picks\."',
         '', 1, 'probe warning (part 2)'),
        (r'"This removes \$failedCount node\$\{if \(failedCount == 1\) "" else "s"\} from \$\{target\.name\} whose most recent stored \$kind test failed\. Other sources and other test types are untouched\."',
         '"This removes $failedCount failed node${if (failedCount == 1) "" else "s"} from ${target.name}."', 1, 'prune-failed dialog'),
        (r'"This removes the subscription and \$\{repo\.subscriptionNodeCount\(target\.id\)\} nodes that belong to it\. Other sources are untouched\."',
         '"This removes the subscription and its ${repo.subscriptionNodeCount(target.id)} nodes."', 1, 'delete-source dialog'),
        (r'"\$\{sentinel\.splitBypassCount\} apps intentionally bypass the VPN; device protection is partial\."',
         '"${sentinel.splitBypassCount} apps bypass the VPN; coverage is partial."', 1, 'sentinel split-bypass note'),
        (r'"Dead endpoints are dropped with a quick TCP check, then the survivors get a real Xray " \+',
         '"Quick TCP gate, then a real Xray tunnel test. Default."', 1, 'probe explainer hybrid'),
        (r'\n\s*"tunnel test\. Best balance of speed and truth, and the default\."',
         '', 1, 'probe explainer hybrid tail'),
        (r'"Every node starts a real Xray process and fetches a real HTTPS URL through it\. This is " \+',
         '"One real Xray process per node with a real HTTPS fetch. Slowest and most accurate."', 1, 'probe explainer tunnel'),
        (r'\n\s*"the only method that proves a node actually works, and the slowest\."',
         '', 1, 'probe explainer tunnel tail'),
        (r'"Measures the TCP handshake to the server address \(tcping\)\. Very fast and light, but it " \+',
         '"Measures the TCP handshake (tcping). Fast, but cannot tell a working proxy from a filtered route."', 1, 'probe explainer tcp'),
        (r'\n\s*"cannot tell a working proxy from an expired account or a filtered route\."',
         '', 1, 'probe explainer tcp tail'),
        (r'"Classic ping through the system\. Fast, but many servers and mobile carriers drop ICMP, " \+',
         '"Classic system ping. Many carriers drop ICMP, so healthy nodes can look unreachable."', 1, 'probe explainer icmp'),
        (r'\n\s*"so healthy nodes can appear unreachable\."',
         '', 1, 'probe explainer icmp tail'),
    ],
    "locale-us": [
        (r'("%\.1f[^"]*")\.format\(', r'String.format(Locale.US, \1, ', None, '%.1f decimal formatting'),
        (r'("%\.0f[^"]*")\.format\(', r'String.format(Locale.US, \1, ', None, '%.0f decimal formatting'),
    ],
    "rankhelper-done": [
        (r'var wg sync\.WaitGroup\n\s*\n\s*for _, job := range batch\.Jobs \{\n\s*job := job\n\s*if job\.ID == "" \|\| job\.Config == "" \{\n\s*continue\n\s*\}\n\s*wg\.Add\(1\)',
         r'var wg sync.WaitGroup\n    executed := 0\n\n    for _, job := range batch.Jobs {\n        job := job\n        if job.ID == "" || job.Config == "" {\n            continue\n        }\n        executed++\n        wg.Add(1)', 1, 'count executed jobs'),
        (r'(Event:\s*"done",\n\s*OK:\s*true,\n\s*Jobs:\s*)len\(batch\.Jobs\),',
         r'\1executed,', 1, 'done event reports executed count'),
    ],
}

LOCALE_FILES = [
    "app/src/main/java/com/marbleng/app/AppRepository.kt",
    "app/src/main/java/com/marbleng/app/core/BenchmarkEngine.kt",
    "app/src/main/java/com/marbleng/app/core/ConnectionTuner.kt",
    "app/src/main/java/com/marbleng/app/core/SmartNotifier.kt",
    "app/src/main/java/com/marbleng/app/core/ContinuousRouteOptimizer.kt",
]

def ensure_locale_import(text: str) -> str:
    if re.search(r'^import java\.util\.Locale\s*$', text, re.M):
        return text
    lines = text.split('\n')
    imports = []
    for i, line in enumerate(lines):
        m = re.match(r'^import ([\w.]+)', line)
        if m:
            imports.append((i, m.group(1)))
    target = 'java.util.Locale'
    pos = None
    for i, pkg in imports:
        if pkg > target:
            pos = i
            break
    if pos is None and imports:
        pos = imports[-1][0] + 1
    if pos is not None:
        lines[pos:pos] = ['import java.util.Locale']
    else:
        pkg_line = next((i for i, l in enumerate(lines) if l.startswith('package ')), 0)
        lines[pkg_line + 1:pkg_line + 1] = ['', 'import java.util.Locale']
    return '\n'.join(lines)

def render_diff(path: str, old: str, new: str) -> None:
    diff = difflib.unified_diff(
        old.splitlines(keepends=True), new.splitlines(keepends=True),
        fromfile='a/' + path, tofile='b/' + path)
    sys.stdout.writelines(diff)

def transform(fixpack: str, root: str, files, apply: bool) -> int:
    rules = RULES.get(fixpack)
    if rules is None:
        print(f'unknown fixpack: {fixpack}', file=sys.stderr)
        return 2
    failures = 0
    changed = []
    for rel in files:
        path = os.path.join(root, rel)
        with open(path, 'r', encoding='utf-8') as fh:
            original = fh.read()
        text = original
        for regex, sub, expect, desc in rules:
            found = len(re.findall(regex, text))
            # explicit expect: exact count required (upstream-drift guard).
            # None: apply every occurrence, zero occurrences allowed.
            ok_count = found == expect if expect is not None else True
            if not ok_count:
                print(f'[skip] {fixpack}/{rel}: "{desc}" anchor count {found} != expected {expect} (upstream drift?)', file=sys.stderr)
                failures += 1
                continue
            if found == 0:
                continue
            text = re.sub(regex, sub, text)
            print(f'[ok]   {fixpack}/{rel}: {desc} ({found} applied)')
        if fixpack == 'locale-us':
            text = ensure_locale_import(text)
        if fixpack == 'ui-clean':
            # Deleted blocks can leave indentation-only blank lines behind.
            text = re.sub(r'[ \t]+\n', '\n', text)
        if text != original:
            changed.append(rel)
            render_diff(rel, original, text)
            if apply:
                with open(path, 'w', encoding='utf-8') as fh:
                    fh.write(text)
    if not changed:
        print('no changes produced', file=sys.stderr)
    return 0 if failures == 0 else 2

def audit(root: str, files) -> int:
    findings = 0
    print('=== MarbleNG targeted static audit ===')
    for rel in files:
        path = os.path.join(root, rel)
        with open(path, 'r', encoding='utf-8') as fh:
            text = fh.read()
        if rel in LOCALE_FILES:
            sites = re.findall(r'(?<!Locale\.US, )"%\.\d+f[^"]*"\.format\(', text)
            if sites:
                findings += 1
                print(f'[BUG] {rel}: {len(sites)} locale-dependent decimal format(s) without Locale.US '
                      f'(Persian/German locales render wrong decimal separators)')
        if rel == 'native/rankhelper/main.go':
            if re.search(r'Event:\s*"done",\n\s*OK:\s*true,\n\s*Jobs:\s*len\(batch\.Jobs\),', text):
                findings += 1
                print('[BUG] native/rankhelper/main.go: done event reports len(batch.Jobs) even though '
                      'empty-ID/Config jobs are skipped (nonzero count with zero results possible)')
        if rel.endswith('Aether2026.kt'):
            clutter = 0
            for regex, _sub, _expect, desc in RULES['ui-clean']:
                n = len(re.findall(regex, text))
                if n:
                    clutter += n
                    print(f'[INFO] {rel}: clutter rule "{desc}" still present ({n})')
            if clutter:
                print(f'[INFO] {rel}: {clutter} clutter sentence(s) present — fixpack ui-clean removes them')
            else:
                print(f'[OK] {rel}: no known UI clutter sentences')
    if findings == 0:
        print('no known bugs found in the scanned files')
    print(f'=== audit done: {findings} bug finding(s) ===')
    return 0 if findings == 0 else 1

def main() -> int:
    mode = sys.argv[1] if len(sys.argv) > 1 else ''
    if mode == '--fixpack':
        fixpack = sys.argv[2]
        args = sys.argv[3:]
        root = args[args.index('--root') + 1]
        files = [a for a in args if not a.startswith('--') and a != root and not a.startswith('-')]
        apply = '--apply' in args
        return transform(fixpack, root, files, apply)
    if mode == '--audit':
        root = sys.argv[sys.argv.index('--root') + 1]
        files = [a for a in sys.argv[sys.argv.index('--root') + 2:] if not a.startswith('--')]
        return audit(root, files)
    print('usage: python3 - {--fixpack NAME | --audit} --root DIR [--apply] FILE...', file=sys.stderr)
    return 2

if __name__ == '__main__':
    sys.exit(main())
PYEMBED
}

# ----------------------------------------------------------------------------
# Commands
# ----------------------------------------------------------------------------

cmd_tree() {
  require jq
  local prefix="${1:-}"
  api_json GET "repos/$REPO/git/trees/$REF?recursive=1" \
    | jq -r --arg p "$prefix" '.tree[]? | select(.type == "blob") | .path | select(startswith($p))' \
    | sort
}

cmd_fetch() {
  [[ $# -ge 1 ]] || die "usage: fetch <path>"
  fetch_file "$1" "$REF"
}

cmd_find() {
  require jq
  [[ $# -ge 1 ]] || die "usage: find <query>"
  local q raw paths
  q="$(python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1]))' "$*")"
  raw="$(api_json GET "search/code?q=${q}+repo:${REPO}&per_page=30" || true)"
  paths="$(printf '%s' "$raw" | jq -r '.items[]?.path // empty' 2>/dev/null || true)"
  if [[ -z "$paths" ]]; then
    info "code search returned no results for '$*' (the repository may not be indexed for code search)."
    info "fallback: list candidate files with 'tree' and inspect them with 'fetch'."
    return 1
  fi
  printf '%s\n' "$paths"
}

fixpack_files() {
  case "$1" in
    ui-clean)
      printf '%s\n' "app/src/main/java/com/marbleng/app/ui/Aether2026.kt" ;;
    locale-us)
      printf '%s\n' \
        "app/src/main/java/com/marbleng/app/AppRepository.kt" \
        "app/src/main/java/com/marbleng/app/core/BenchmarkEngine.kt" \
        "app/src/main/java/com/marbleng/app/core/ConnectionTuner.kt" \
        "app/src/main/java/com/marbleng/app/core/SmartNotifier.kt" \
        "app/src/main/java/com/marbleng/app/core/ContinuousRouteOptimizer.kt" ;;
    rankhelper-done)
      printf '%s\n' "native/rankhelper/main.go" ;;
    *)
      die "unknown fixpack '$1' (try: ui-clean locale-us rankhelper-done)" ;;
  esac
}

ensure_branch() {
  local base_sha branch_sha
  base_sha="$(api_json GET "repos/$REPO/git/ref/heads/$PR_BASE" | jq -r '.object.sha // empty')"
  [[ -n "$base_sha" ]] || die "cannot resolve base branch '$PR_BASE'"
  branch_sha="$(api_json GET "repos/$REPO/git/ref/heads/$BRANCH" 2>/dev/null | jq -r '.object.sha // empty' || true)"
  if [[ -z "$branch_sha" ]]; then
    jq -n --arg r "refs/heads/$BRANCH" --arg s "$base_sha" '{ref:$r,sha:$s}' > "$TMP/create-branch.json"
    api_json POST "repos/$REPO/git/refs" "$TMP/create-branch.json" >/dev/null
    ok "created branch '$BRANCH' from '$PR_BASE' ($base_sha)"
  else
    ok "reusing existing branch '$BRANCH' ($branch_sha)"
  fi
}

upload_file() { # upload_file <path> <file>
  local path="$1" file="$2" b64 sha existing
  b64="$(python3 -c 'import base64,sys;sys.stdout.write(base64.b64encode(open(sys.argv[1],"rb").read()).decode())' "$file")"
  existing="$(api_json GET "repos/$REPO/contents/$(urlencode_path "$path")?ref=$BRANCH" 2>/dev/null || true)"
  sha="$(printf '%s' "$existing" | jq -r '.sha // empty' 2>/dev/null || true)"
  jq -n --arg m "fix: targeted $CMD update of $path" --arg c "$b64" --arg b "$BRANCH" --arg s "$sha" \
    '{message:$m, content:$c, branch:$b} + (if $s == "" then {} else {sha:$s} end)' > "$TMP/upload.json"
  api_json PUT "repos/$REPO/contents/$(urlencode_path "$path")" "$TMP/upload.json" >/dev/null
}

create_pr() {
  local owner="${REPO%%/*}"
  jq -n --arg t "$PR_TITLE" --arg b "${PR_BODY:-$PR_TITLE}" --arg h "$owner:$BRANCH" --arg base "$PR_BASE" \
    '{title:$t, body:$b, head:$h, base:$base}' > "$TMP/pr.json"
  api_json POST "repos/$REPO/pulls" "$TMP/pr.json" | jq -r '.html_url // .message'
}

cmd_fixpack() {
  require python3
  require jq
  local name="$1"
  local files=()
  while IFS= read -r f; do files+=("$f"); done < <(fixpack_files "$name")

  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  for f in "${files[@]}"; do
    mkdir -p "$TMP/$(dirname "$f")"
    if [[ -n "$WORKDIR" ]]; then
      [[ -f "$WORKDIR/$f" ]] || die "local file missing: $WORKDIR/$f (MARBLE_WORKDIR set?)"
      cp "$WORKDIR/$f" "$TMP/$f"
      info "local file: $f"
    else
      fetch_file "$f" "$REF" > "$TMP/$f"
      info "fetched:    $f @ $REF"
    fi
  done

  local py_args=(--fixpack "$name" --root "$TMP")
  [[ $APPLY -eq 1 ]] && py_args+=(--apply)
  py_args+=("${files[@]}")
  run_python "${py_args[@]}" || die "transform anchors drifted — nothing was written (update the fixpack rules)"

  if [[ $APPLY -eq 0 ]]; then
    ok "dry-run only. Add --apply (local workdir) or --apply --remote --branch B --title T (PR)."
    return 0
  fi

  if [[ -n "$WORKDIR" ]]; then
    for f in "${files[@]}"; do
      if cmp -s "$TMP/$f" "$WORKDIR/$f"; then
        info "unchanged:  $f"
      else
        cp "$TMP/$f" "$WORKDIR/$f"
        ok "applied:    $f -> $WORKDIR"
      fi
    done
    return 0
  fi

  [[ $REMOTE -eq 1 ]] || die "no MARBLE_WORKDIR and no --remote: remote writes need --remote --branch B --title T"
  [[ -n "$BRANCH" && -n "$PR_TITLE" ]] || die "--remote needs --branch <branch> and --title <pr title>"
  ensure_branch
  for f in "${files[@]}"; do
    local_sha="$(python3 -c 'import hashlib,sys;print(hashlib.sha1(open(sys.argv[1],"rb").read()).hexdigest())' "$TMP/$f")"
    remote_raw="$(api_json GET "repos/$REPO/contents/$(urlencode_path "$f")?ref=$BRANCH" 2>/dev/null || true)"
    remote_sha="$(printf '%s' "$remote_raw" | jq -r '.sha // empty' 2>/dev/null || true)"
    # git blob id = sha1("blob <size>\0<content>")
    remote_blob="$(printf '%s' "$remote_raw" | jq -r '.content // empty' 2>/dev/null | python3 -c 'import base64,sys,hashlib;d=base64.b64decode(sys.stdin.read());print(hashlib.sha1(b"blob "+str(len(d)).encode()+b"\0"+d).hexdigest())' 2>/dev/null || true)"
    if [[ "$remote_blob" != "$local_sha" ]]; then
      upload_file "$f" "$TMP/$f"
      ok "uploaded:   $f -> branch '$BRANCH'"
    else
      info "unchanged:  $f (already fixed on '$BRANCH')"
    fi
  done
  create_pr
}

cmd_audit() {
  require python3
  local files=(
    "app/src/main/java/com/marbleng/app/ui/Aether2026.kt"
    "app/src/main/java/com/marbleng/app/AppRepository.kt"
    "app/src/main/java/com/marbleng/app/core/BenchmarkEngine.kt"
    "app/src/main/java/com/marbleng/app/core/ConnectionTuner.kt"
    "app/src/main/java/com/marbleng/app/core/SmartNotifier.kt"
    "app/src/main/java/com/marbleng/app/core/ContinuousRouteOptimizer.kt"
    "native/rankhelper/main.go"
  )
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  for f in "${files[@]}"; do
    mkdir -p "$TMP/$(dirname "$f")"
    if [[ -n "$WORKDIR" ]]; then
      [[ -f "$WORKDIR/$f" ]] || die "local file missing: $WORKDIR/$f"
      cp "$WORKDIR/$f" "$TMP/$f"
    else
      fetch_file "$f" "$REF" > "$TMP/$f" || info "skip missing: $f"
    fi
  done
  run_python --audit --root "$TMP" "${files[@]}" || true
}

# ----------------------------------------------------------------------------
# Argument parsing
# ----------------------------------------------------------------------------

POS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --apply) APPLY=1 ;;
    --remote) REMOTE=1 ;;
    --branch) BRANCH="${2:?missing branch value}"; shift ;;
    --title) PR_TITLE="${2:?missing title value}"; shift ;;
    --body) PR_BODY="${2:?missing body value}"; shift ;;
    --ref) REF="${2:?missing ref value}"; PR_BASE="$REF"; shift ;;
    --repo) REPO="${2:?missing repo value}"; shift ;;
    --help|-h) usage; exit 0 ;;
    --*) die "unknown flag: $1" ;;
    *) POS+=("$1") ;;
  esac
  shift
done

CMD="${POS[0]:-}"
[[ -n "$CMD" ]] || { usage; die "missing command"; }
set -- "${POS[@]:1}"

case "$CMD" in
  tree) cmd_tree "$@" ;;
  fetch) cmd_fetch "$@" ;;
  find) cmd_find "$@" ;;
  fixpacks) printf 'ui-clean\nlocale-us\nrankhelper-done\n' ;;
  fixpack) cmd_fixpack "${1:?usage: fixpack <name>}" ;;
  ui-clean) cmd_fixpack "ui-clean" ;;
  audit) cmd_audit ;;
  *) usage; die "unknown command: $CMD" ;;
esac
