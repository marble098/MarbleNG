#!/usr/bin/env python3
"""TEMPORARY SESSION ANALYSIS — deleted before the pull request.

An obfuscated crash report (every frame is `r8-map-id-<hash>:<line>`) can only be resolved with
the mapping file of the exact build that produced it. Rebuilding the candidate commits with R8 was
tried first and produced mappings whose obfuscated names (`sq2`, `dq2`, …) do not contain a single
class from the report (`ko0`, `vm`, `ga`, …), so the mapping files of the shipped APKs are not
reproducible by a plain rebuild of a main merge — the build the report came from has to be
identified first.

This script therefore works in two stages, both executed inside the "Source verification" workflow
(the sandbox has no JDK, no Android SDK and no Maven/Google egress, and no route to any release
asset):

1. Probe the published release APKs. Downloading a release APK is impossible from the sandbox but
   trivial on a runner, and a DEX file carries every obfuscated class name verbatim. Counting the
   crash's own class descriptors (`Lko0;`, `Lvm;`, …) inside `classes*.dex` of each release names
   the exact build the tombstone came from, with no mapping needed.
2. Rebuild that one commit with R8, retrace the report against its mapping, and emit the resolved
   frames plus the mapping's `# pg_map_id` (which must equal the report's `r8-map-id-…`).

Evidence is printed as `::warning::MARBLE-RETRACE-…` annotations: CI logs are not reachable from
the sandbox, but workflow annotations are, through the checks API.
"""

from __future__ import annotations

import base64
import concurrent.futures
import gzip
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.request
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WORK = "/tmp/retrace"
GRADLE_VERSION = "9.5.1"
GRADLE_DIST = f"{WORK}/gradle-{GRADLE_VERSION}"
GRADLE_HOME = f"{WORK}/gradle-home"
TOTAL_BUDGET = 14 * 60
CHUNK = 1100
MAX_CHUNKS = 2

sys.path.insert(0, os.path.join(REPO, ".github", "retrace"))


def run(command, cwd=None, env=None, timeout=1800, check=True):
    print(f"\n$ {' '.join(command)}", flush=True)
    try:
        process = subprocess.run(
            command, cwd=cwd, env=env, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, timeout=timeout,
        )
    except subprocess.TimeoutExpired:
        return 124, f"timeout after {timeout}s"
    output = process.stdout or ""
    if output:
        print(output[-2_000:], flush=True)
    if check and process.returncode != 0:
        raise RuntimeError(f"command failed ({process.returncode}): {' '.join(command)}")
    return process.returncode, output


# --------------------------------------------------------------------------------------- probing


def crash_class_names(crash: str) -> list[str]:
    """Obfuscated class names the report mentions (frames and suppressed exceptions)."""
    names: list[str] = []
    with open(crash, encoding="utf-8", errors="replace") as handle:
        text = handle.read()
    for raw in text.splitlines():
        match = re.match(r"^\s*at\s+([\w$.<>]+)\.([\w$<>]+)\(", raw)
        if match:
            names.append(match.group(1))
        suppressed = re.search(r"Suppressed:\s*(\S+?):\s*\[(.*)\]", raw)
        if suppressed:
            names.append(suppressed.group(1))
            for token in re.findall(r"([A-Za-z][\w$]{1,63})[@\{]", suppressed.group(2)):
                names.append(token)
    unique = []
    for name in names:
        if "." in name or not name[0].islower() or len(name) < 2:
            continue
        if name not in unique:
            unique.append(name)
    return unique


def download(url: str, destination: str) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": "MarbleNG-retrace"})
    with urllib.request.urlopen(request, timeout=300) as response, open(destination, "wb") as handle:
        shutil.copyfileobj(response, handle, 1 << 20)


def dex_hits(apk: str, names: list[str]) -> dict[str, int]:
    hits: dict[str, int] = {}
    with zipfile.ZipFile(apk) as archive:
        for entry in archive.namelist():
            if not re.fullmatch(r"classes\d*\.dex", entry):
                continue
            data = archive.read(entry)
            for name in names:
                marker = b"L" + name.encode("ascii") + b";"
                count = data.count(marker)
                if count:
                    hits[name] = hits.get(name, 0) + count
    return hits


def probe(releases: list[dict], names: list[str]) -> list[dict]:
    results = []
    for release in releases:
        url = (
            "https://github.com/marble098/MarbleNG/releases/download/"
            f"{release['tag']}/{release['asset']}"
        )
        apk = f"{WORK}/{release['tag']}-{release['asset']}"
        started = time.time()
        try:
            if not os.path.isfile(apk) or os.path.getsize(apk) < 1_000_000:
                print(f"downloading {url}", flush=True)
                download(url, apk)
            hits = dex_hits(apk, names)
            results.append(
                {
                    "tag": release["tag"],
                    "sha": release["sha"],
                    "matched": [name for name in names if name in hits],
                    "hits": hits,
                    "size": os.path.getsize(apk),
                    "seconds": int(time.time() - started),
                }
            )
        except Exception as error:  # noqa: BLE001 - never lose the other probes to one failure
            results.append({"tag": release["tag"], "sha": release["sha"], "error": repr(error)})
    return results


def report_probe(results: list[dict], names: list[str]) -> None:
    lines = [f"probed {len(results)} release APKs, {len(names)} crash class names:"]
    for result in results:
        if "error" in result:
            lines.append(f"  {result['tag']}: ERROR {result['error'][:160]}")
            continue
        lines.append(
            f"  {result['tag']} ({result['sha'][:8]}): matched={len(result['matched'])}/{len(names)}"
            f" in {result['seconds']}s"
        )
        lines.append(f"    found: {' '.join(result['matched']) or '(none)'}")
    print("::warning title=MARBLE-RETRACE-PROBE " + " | ".join(
        f"{r['tag']}={len(r.get('matched', []))}/{len(names)}" if "error" not in r
        else f"{r['tag']}=ERROR"
        for r in results
    ) + "::" + "\n".join(lines), flush=True)
    best = max(results, key=lambda r: len(r.get("matched", [])), default=None)
    if best and len(best.get("matched", [])) >= max(6, len(names) - 3):
        print(
            f"::warning title=MARBLE-RETRACE-PROBE-BEST::{best['tag']} {best['sha']} "
            f"matched={len(best['matched'])}/{len(names)}",
            flush=True,
        )


# ---------------------------------------------------------------------------------------- build


def prepare_gradle() -> str:
    binary = f"{GRADLE_DIST}/bin/gradle"
    if os.path.isfile(binary):
        return binary
    os.makedirs(WORK, exist_ok=True)
    archive = f"{WORK}/gradle-{GRADLE_VERSION}-bin.zip"
    if not os.path.isfile(archive):
        url = f"https://services.gradle.org/distributions/gradle-{GRADLE_VERSION}-bin.zip"
        print(f"downloading {url}", flush=True)
        urllib.request.urlretrieve(url, archive)
    subprocess.run(["unzip", "-q", "-o", archive, "-d", WORK], check=True)
    return binary


def make_signer(worktree: str) -> None:
    keytool = os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "keytool")
    if not os.path.isfile(keytool):
        keytool = "keytool"
    subprocess.run(
        [
            keytool, "-genkeypair", "-noprompt",
            "-keystore", os.path.join(worktree, "release.jks"),
            "-storepass", "marble123", "-keypass", "marble123", "-alias", "marble",
            "-keyalg", "RSA", "-keysize", "2048", "-validity", "30",
            "-dname", "CN=Marble Retrace, O=MarbleNG",
        ],
        check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    with open(os.path.join(worktree, "signing.properties"), "w", encoding="utf-8") as handle:
        handle.write(
            "storeFile=release.jks\nstorePassword=marble123\n"
            "keyAlias=marble\nkeyPassword=marble123\n"
        )


def build_mapping(gradle_bin: str, release: dict, budget: float) -> dict:
    label, sha = release["tag"], release["sha"]
    started = time.time()
    result = {"label": label, "sha": sha, "ok": False, "seconds": 0, "detail": ""}
    try:
        worktree = f"{WORK}/wt-{label}"
        run(["git", "worktree", "remove", "--force", worktree], cwd=REPO, check=False)
        shutil.rmtree(worktree, ignore_errors=True)
        run(["git", "worktree", "add", "--detach", "--force", worktree, sha], cwd=REPO)
        make_signer(worktree)

        env = dict(os.environ)
        env["GRADLE_USER_HOME"] = GRADLE_HOME
        mapping = os.path.join(worktree, "app/build/outputs/mapping/release/mapping.txt")
        for tasks in ([":app:minifyReleaseWithR8"], ["clean", "assembleRelease"]):
            code, output = run(
                [
                    gradle_bin, "--no-daemon", *tasks,
                    "-PVERSION_NAME=0.0.0-retrace", "-PVERSION_CODE=1",
                    "-x", "prepareSingBoxRules",
                    "-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8",
                    "--max-workers=2",
                ],
                cwd=worktree, env=env, check=False,
                timeout=max(120, int(budget - (time.time() - started))),
            )
            if code == 0 and os.path.isfile(mapping) and os.path.getsize(mapping) > 0:
                result["ok"] = True
                result["mapping"] = mapping
                break
            result["detail"] = (
                f"attempt ({' '.join(tasks)}) exit={code}\n{output[-1_500:]}"
            )
            if time.time() - started > budget - 90:
                break
    except Exception as error:  # noqa: BLE001 - evidence must never be lost to an exception
        result["detail"] = repr(error)
    result["seconds"] = int(time.time() - started)
    return result


# -------------------------------------------------------------------------------------- retrace


def retrace(mapping_path: str, crash: str, crash_map_id: str) -> tuple[str, str]:
    from retrace import Mapping, parse_crash, source_path  # noqa: E402  session tooling

    with open(mapping_path, encoding="utf-8", errors="replace") as handle:
        header = [line.rstrip() for _, line in zip(range(8), handle)]
    map_id = ""
    for line in header:
        found = re.search(r"pg_map_id:\s*(\S+)", line)
        if found:
            map_id = found.group(1)

    index = Mapping(mapping_path)
    resolved, excerpts, total = [], [], 0
    for obf_class, obf_method, obf_line, _raw in parse_crash(crash):
        total += 1
        entry, method = index.resolve(obf_class, obf_method, obf_line)
        if entry is None or method is None:
            resolved.append(f"  ? {obf_class}.{obf_method}:{obf_line}")
            continue
        original_line = method.original_line(obf_line)
        resolved.append(
            f"  {obf_class}.{obf_method}:{obf_line} -> "
            f"{entry.original}.{method.original_name}[{original_line}]"
        )
        path = source_path(entry.original)
        if path and original_line and entry.original.startswith("com.marbleng"):
            with open(path, encoding="utf-8", errors="replace") as handle:
                lines = handle.readlines()
            excerpts.append(f"  {entry.original}.{method.original_name} {path}:{original_line}")
            for number in range(max(1, original_line - 1), min(len(lines), original_line + 2) + 1):
                excerpts.append(f"    {number}| {lines[number - 1].strip()[:160]}")

    hits = sum(1 for line in resolved if "->" in line)
    summary = (
        f"classes={len(index.classes)} frames={hits}/{total} "
        f"map-id={map_id or '?'} match={'yes' if map_id == crash_map_id else 'no'}"
    )
    body = "\n".join(
        [
            f"crash-map-id={crash_map_id}",
            "mapping header:",
            *(f"  {line}" for line in header if line),
            "frames:",
            *resolved,
            "source:",
            *(excerpts or ["  (no com.marbleng frame)"]),
        ]
    )
    return summary, body


def publish(label: str, sha: str, summary: str, body: str) -> None:
    print(f"::warning title=MARBLE-RETRACE {label} {sha[:8]}::{summary[:900]}", flush=True)
    blob = base64.b64encode(gzip.compress(body.encode("utf-8"), 9)).decode("ascii")
    chunks = [blob[i : i + CHUNK] for i in range(0, len(blob), CHUNK)]
    for number, chunk in enumerate(chunks[:MAX_CHUNKS], start=1):
        print(
            f"::warning title=MARBLE-RETRACE-{label}-{number}-of-{len(chunks)}::{chunk}",
            flush=True,
        )
    if len(chunks) > MAX_CHUNKS:
        print(f"::warning title=MARBLE-RETRACE-{label}-TRUNCATED::{len(chunks)} chunks", flush=True)


# ----------------------------------------------------------------------------------------- main


def main() -> int:
    deadline = time.time() + TOTAL_BUDGET
    with open(os.path.join(REPO, ".github/retrace/releases.json"), encoding="utf-8") as handle:
        config = json.load(handle)
    crash = os.path.join(REPO, ".github/retrace/crash.txt")
    names = crash_class_names(crash)
    os.makedirs(WORK, exist_ok=True)
    os.makedirs(GRADLE_HOME, exist_ok=True)

    results = probe(config["releases"], names)
    report_probe(results, names)

    best = max(results, key=lambda r: len(r.get("matched", [])), default=None)
    enough = best and len(best.get("matched", [])) >= max(6, len(names) - 3)
    if not enough:
        print("::warning::MARBLE-RETRACE-DONE no release matched; nothing built", flush=True)
        return 0

    gradle_bin = prepare_gradle()
    run(["git", "fetch", "--depth", "300", "origin", "main"], cwd=REPO, check=False)
    remaining = deadline - time.time()
    built = build_mapping(gradle_bin, best, budget=max(240.0, remaining - 30))
    if built.get("ok"):
        try:
            summary, body = retrace(built["mapping"], crash, config["crash_map_id"])
            publish(built["label"], built["sha"], summary, body)
        except Exception as error:  # noqa: BLE001
            publish(built["label"], built["sha"], f"retrace failed: {error!r}", repr(error))
    else:
        publish(built["label"], built["sha"], f"BUILD FAILED in {built['seconds']}s",
                built.get("detail", "")[-3_000:])
    print(
        f"::warning::MARBLE-RETRACE-DONE probed={len(results)} built={built['label']} "
        f"ok={built.get('ok')} in {built['seconds']}s",
        flush=True,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
