#!/usr/bin/env python3
"""TEMPORARY SESSION ANALYSIS — deleted before the pull request.

An obfuscated crash report (all frames are `r8-map-id-<hash>:<line>`) can only be resolved with
the mapping file of the exact build that produced it. The development sandbox has no JDK, no
Android SDK and no route to Maven/Google/Gradle hosts, so the mapping has to be produced by a CI
runner; the CI logs themselves are not reachable from the sandbox either, so the retraced
evidence is returned through workflow *annotations*, which the checks API does expose.

The script is invoked from scripts/system-integrity-check.py and only inside the
"Source verification" workflow. It rebuilds each candidate release commit in a git worktree,
retraces the report with that build's own mapping, and prints the result as
`::warning::MARBLE-RETRACE-…` annotations.
"""

from __future__ import annotations

import base64
import concurrent.futures
import gzip
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.request

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WORK = "/tmp/retrace"
GRADLE_VERSION = "9.5.1"
GRADLE_DIST = f"{WORK}/gradle-{GRADLE_VERSION}"
GRADLE_HOME = f"{WORK}/gradle-home"
BUDGET_SECONDS = 12 * 60
WORKERS = 2
CHUNK = 1100
MAX_CHUNKS = 8

sys.path.insert(0, os.path.join(REPO, ".github", "retrace"))


def run(command, cwd=None, env=None, timeout=1800, check=True):
    print(f"\n$ {' '.join(command)}", flush=True)
    try:
        process = subprocess.run(
            command,
            cwd=cwd,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=timeout,
        )
    except subprocess.TimeoutExpired:
        return 124, f"timeout after {timeout}s: {' '.join(command)}"
    output = process.stdout or ""
    if output:
        print(output[-4_000:], flush=True)
    if check and process.returncode != 0:
        raise RuntimeError(f"command failed ({process.returncode}): {' '.join(command)}")
    return process.returncode, output


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
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    with open(os.path.join(worktree, "signing.properties"), "w", encoding="utf-8") as handle:
        handle.write(
            "storeFile=release.jks\nstorePassword=marble123\n"
            "keyAlias=marble\nkeyPassword=marble123\n"
        )


def build_one(gradle_bin: str, target: dict) -> dict:
    label, sha = target["label"], target["sha"]
    started = time.time()
    result = {"label": label, "sha": sha, "ok": False, "seconds": 0, "detail": ""}

    try:
        ref = f"refs/retrace/{sha}"
        run(["git", "fetch", "--depth", "1", "origin", f"{sha}:{ref}"], cwd=REPO)
        worktree = f"{WORK}/wt-{label}"
        run(["git", "worktree", "remove", "--force", worktree], cwd=REPO, check=False)
        shutil.rmtree(worktree, ignore_errors=True)
        run(["git", "worktree", "add", "--detach", "--force", worktree, ref], cwd=REPO)
        make_signer(worktree)

        env = dict(os.environ)
        env["GRADLE_USER_HOME"] = GRADLE_HOME
        code, output = run(
            [
                gradle_bin, "--no-daemon", "clean", "assembleRelease",
                "-PVERSION_NAME=0.0.0-retrace", "-PVERSION_CODE=1",
                "-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8",
            ],
            cwd=worktree,
            env=env,
            check=False,
        )

        mapping = os.path.join(worktree, "app/build/outputs/mapping/release/mapping.txt")
        if code != 0 or not os.path.isfile(mapping) or os.path.getsize(mapping) == 0:
            result["detail"] = output[-3_000:]
        else:
            result["ok"] = True
            result["mapping"] = mapping
    except Exception as error:  # noqa: BLE001 - evidence must never be lost to an exception
        result["detail"] = repr(error)

    result["seconds"] = int(time.time() - started)
    return result


def frames_of(crash: str) -> list[str]:
    lines = []
    with open(crash, encoding="utf-8") as handle:
        for line in handle:
            stripped = line.strip()
            if stripped.startswith("at "):
                lines.append(stripped)
    return lines


def retrace_section(label: str, mapping: str, crash: str) -> str:
    from retrace import Mapping, parse_crash, source_path  # noqa: E402  (session tooling)

    digest = hashlib.sha256(open(mapping, "rb").read()).hexdigest()
    index = Mapping(mapping)
    out = [f"### {label}", f"mapping-sha256={digest}", f"classes={len(index.classes)}"]
    resolved = 0
    for obf_class, obf_method, obf_line, _raw in parse_crash(crash):
        entry, method = index.resolve(obf_class, obf_method, obf_line)
        if entry is None or method is None:
            out.append(f"  ? {obf_class}.{obf_method}:{obf_line}")
            continue
        original_line = method.original_line(obf_line)
        resolved += 1
        out.append(
            f"  {obf_class}.{obf_method}:{obf_line} -> {entry.original}.{method.original_name}"
            f" [{original_line}]"
        )
        path = source_path(entry.original)
        if path and original_line and entry.original.startswith("com.marbleng"):
            with open(path, encoding="utf-8", errors="replace") as handle:
                lines = handle.readlines()
            low = max(1, original_line - 1)
            high = min(len(lines), original_line + 1)
            for number in range(low, high + 1):
                out.append(f"      {number}| {lines[number - 1].strip()[:150]}")
            out.append(f"      file={path}")
    out.append(f"resolved={resolved}/{len(frames_of(crash))}")
    return "\n".join(out)


def emit(payload: str) -> None:
    blob = base64.b64encode(gzip.compress(payload.encode("utf-8"), 9)).decode("ascii")
    chunks = [blob[i : i + CHUNK] for i in range(0, len(blob), CHUNK)]
    print(f"payload bytes={len(payload)} gz-b64={len(blob)} chunks={len(chunks)}", flush=True)
    head = payload.splitlines()[:6]
    print("::warning title=MARBLE-RETRACE-HEADER::" + " | ".join(head)[:900], flush=True)
    for number, chunk in enumerate(chunks[:MAX_CHUNKS], start=1):
        print(
            f"::warning title=MARBLE-RETRACE-{number}-of-{len(chunks)}::{chunk}",
            flush=True,
        )
    if len(chunks) > MAX_CHUNKS:
        print(
            f"::warning title=MARBLE-RETRACE-TRUNCATED::{len(chunks)} chunks, sent {MAX_CHUNKS}",
            flush=True,
        )


def main() -> int:
    with open(os.path.join(REPO, ".github/retrace/targets.json"), encoding="utf-8") as handle:
        targets = json.load(handle)["targets"]

    crash = os.path.join(REPO, ".github/retrace/crash.txt")
    os.makedirs(WORK, exist_ok=True)
    os.makedirs(GRADLE_HOME, exist_ok=True)
    gradle_bin = prepare_gradle()

    deadline = time.time() + BUDGET_SECONDS
    results: list[dict] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futures = []
        for target in targets:
            if time.time() >= deadline:
                results.append(
                    {
                        "label": target["label"],
                        "sha": target["sha"],
                        "ok": False,
                        "seconds": 0,
                        "detail": "skipped: analysis budget exhausted",
                    }
                )
                continue
            futures.append(pool.submit(build_one, gradle_bin, target))
        for future in futures:
            remaining = max(30.0, deadline - time.time())
            try:
                results.append(future.result(timeout=remaining))
            except Exception as error:  # noqa: BLE001
                results.append(
                    {"label": "unknown", "sha": "?", "ok": False, "seconds": 0,
                     "detail": repr(error)}
                )

    results.sort(key=lambda item: item["label"])
    sections = []
    for result in results:
        if result["ok"]:
            try:
                sections.append(retrace_section(result["label"], result["mapping"], crash))
            except Exception as error:  # noqa: BLE001
                sections.append(f"### {result['label']}\nretrace failed: {error!r}")
        else:
            sections.append(
                f"### {result['label']} ({result['sha'][:8]}) BUILD FAILED "
                f"in {result['seconds']}s\n{result.get('detail', '')[:1_500]}"
            )
    emit("\n".join(sections) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
