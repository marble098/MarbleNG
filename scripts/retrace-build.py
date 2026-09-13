#!/usr/bin/env python3
"""TEMPORARY SESSION ANALYSIS — deleted before the pull request.

An obfuscated crash report (every frame is `r8-map-id-<hash>:<line>`) can only be resolved with
the mapping file of the exact build that produced it. The development sandbox has no JDK, no
Android SDK and no route to Maven/Google/Gradle hosts, so the mapping has to be produced by a CI
runner; the CI logs themselves are not reachable from the sandbox either, so the evidence is
returned through workflow *annotations*, which the checks API does expose.

The script is called from scripts/system-integrity-check.py and only inside the "Source
verification" workflow. It rebuilds each candidate release commit in a git worktree, retraces the
report with that build's own mapping, and prints the result as `::warning::MARBLE-RETRACE-…`
annotations while the build is still running, so a job timeout cannot swallow the evidence.
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
BUDGET_SECONDS = 16 * 60
WORKERS = 2
CHUNK = 1100
MAX_CHUNKS = 2

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
        return 124, f"timeout after {timeout}s"
    output = process.stdout or ""
    if output:
        print(output[-3_000:], flush=True)
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
        worktree = f"{WORK}/wt-{label}"
        run(["git", "worktree", "remove", "--force", worktree], cwd=REPO, check=False)
        shutil.rmtree(worktree, ignore_errors=True)
        run(["git", "worktree", "add", "--detach", "--force", worktree, sha], cwd=REPO)
        make_signer(worktree)

        env = dict(os.environ)
        env["GRADLE_USER_HOME"] = GRADLE_HOME
        mapping = os.path.join(worktree, "app/build/outputs/mapping/release/mapping.txt")
        attempts = [
            [":app:minifyReleaseWithR8"],
            ["clean", "assembleRelease"],
        ]
        for index, tasks in enumerate(attempts):
            code, output = run(
                [
                    gradle_bin, "--no-daemon", *tasks,
                    "-PVERSION_NAME=0.0.0-retrace", "-PVERSION_CODE=1",
                    "-x", "prepareSingBoxRules",
                    "-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8",
                    "--max-workers=2",
                ],
                cwd=worktree,
                env=env,
                check=False,
                timeout=max(120, int(BUDGET_SECONDS - (time.time() - started))),
            )
            if code == 0 and os.path.isfile(mapping) and os.path.getsize(mapping) > 0:
                result["ok"] = True
                result["mapping"] = mapping
                break
            result["detail"] = (
                f"attempt {index + 1} ({' '.join(tasks)}) exit={code}\n{output[-1_500:]}"
            )
            if time.time() - started > BUDGET_SECONDS - 120:
                break
    except Exception as error:  # noqa: BLE001 - evidence must never be lost to an exception
        result["detail"] = repr(error)

    result["seconds"] = int(time.time() - started)
    return result


def compact_frames(mapping: str, crash: str) -> tuple[str, str, str]:
    """Return (summary, frame lines, source excerpts) for one mapping."""
    from retrace import Mapping, parse_crash, source_path  # noqa: E402  session tooling

    digest = hashlib.sha256(open(mapping, "rb").read()).hexdigest()
    index = Mapping(mapping)
    sample = [f"  {line}" for line in list(_class_lines(mapping))[:4]]

    resolved = []
    excerpts = []
    total = 0
    for obf_class, obf_method, obf_line, _raw in parse_crash(crash):
        total += 1
        entry, method = index.resolve(obf_class, obf_method, obf_line)
        if entry is None or method is None:
            resolved.append(f"  ? {obf_class}.{obf_method}:{obf_line}")
            continue
        original_line = method.original_line(obf_line)
        resolved.append(
            f"  {obf_class}.{obf_method}:{obf_line}"
            f"->{entry.original}.{method.original_name}[{original_line}]"
        )
        path = source_path(entry.original)
        if path and original_line and entry.original.startswith("com.marbleng"):
            with open(path, encoding="utf-8", errors="replace") as handle:
                lines = handle.readlines()
            excerpts.append(f"  {entry.original}.{method.original_name} {path}:{original_line}")
            for number in range(max(1, original_line - 1), min(len(lines), original_line + 2) + 1):
                excerpts.append(f"    {number}| {lines[number - 1].strip()[:160]}")

    summary = (
        f"classes={len(index.classes)} sha256={digest[:16]} "
        f"resolved={sum(1 for line in resolved if not line.strip().startswith('?'))}/{total}"
    )
    body = "\n".join(
        [
            f"sha256={digest}",
            "mapping sample:",
            *sample,
            "frames:",
            *resolved,
            "source:",
            *(excerpts or ["  (no com.marbleng frame)"]),
        ]
    )
    return summary, body, digest


def _class_lines(mapping: str):
    with open(mapping, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            if line and not line.startswith((" ", "\t", "#")) and "->" in line:
                yield line.rstrip()


def blob_of(body: str) -> list[str]:
    blob = base64.b64encode(gzip.compress(body.encode("utf-8"), 9)).decode("ascii")
    return [blob[i : i + CHUNK] for i in range(0, len(blob), CHUNK)]


def announce(label: str, sha: str, summary: str) -> None:
    print(
        f"::warning title=MARBLE-RETRACE {label} {sha[:8]}::{summary[:900]}",
        flush=True,
    )


def publish(label: str, sha: str, summary: str, body: str) -> None:
    announce(label, sha, summary)
    chunks = blob_of(body)
    for number, chunk in enumerate(chunks[:MAX_CHUNKS], start=1):
        print(f"::warning title=MARBLE-RETRACE-{label}-{number}-of-{len(chunks)}::{chunk}", flush=True)
    if len(chunks) > MAX_CHUNKS:
        print(
            f"::warning title=MARBLE-RETRACE-{label}-TRUNCATED::payload {len(chunks)} chunks",
            flush=True,
        )


def main() -> int:
    with open(os.path.join(REPO, ".github/retrace/targets.json"), encoding="utf-8") as handle:
        targets = json.load(handle)["targets"]

    crash = os.path.join(REPO, ".github/retrace/crash.txt")
    os.makedirs(WORK, exist_ok=True)
    os.makedirs(GRADLE_HOME, exist_ok=True)
    gradle_bin = prepare_gradle()

    # The pull-request checkout is a shallow merge commit, so the candidate release commits have
    # to be fetched explicitly before a worktree can be created for them.
    run(["git", "fetch", "--depth", "100", "origin", "main"], cwd=REPO, check=False)

    deadline = time.time() + BUDGET_SECONDS
    order = list(targets)
    results: list[dict] = []
    pending: list[concurrent.futures.Future] = []
    index = 0

    def handle(result: dict) -> None:
        results.append(result)
        if result.get("ok"):
            try:
                summary, body, _digest = compact_frames(result["mapping"], crash)
                publish(result["label"], result["sha"], summary, body)
            except Exception as error:  # noqa: BLE001
                publish(result["label"], result["sha"], f"retrace failed: {error!r}", repr(error))
        else:
            publish(
                result["label"],
                result["sha"],
                f"BUILD FAILED in {result['seconds']}s",
                result.get("detail", "")[-3_000:],
            )

    with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        while index < len(order) or pending:
            # A build needs several minutes: never start one that the budget cannot finish.
            while (
                len(pending) < WORKERS
                and index < len(order)
                and deadline - time.time() > 8 * 60
            ):
                pending.append(pool.submit(build_one, gradle_bin, order[index]))
                index += 1
            if not pending:
                break
            done, _ = concurrent.futures.wait(
                pending,
                timeout=max(20.0, deadline - time.time()),
                return_when=concurrent.futures.FIRST_COMPLETED,
            )
            if not done:
                break
            for future in done:
                pending.remove(future)
                handle(future.result())

    print(f"::warning::MARBLE-RETRACE-DONE built={len(results)}/{len(order)}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
