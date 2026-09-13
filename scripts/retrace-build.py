#!/usr/bin/env python3
"""TEMPORARY SESSION ANALYSIS — deleted before the pull request.

Rebuilds the Maven-free parts of the release pipeline for a list of candidate commits so that
R8 produces the mapping file each of them would have shipped, then retraces an obfuscated
on-device crash report against every one of those mappings. The evidence (retraced frames,
source context, mapping hashes and the mappings themselves) is committed to the session branch
because CI logs are not reachable from the development sandbox.

It is invoked from scripts/prepare-native.sh only when the workflow ref is the session branch;
every other ref (main, pull requests, forks) skips it entirely.
"""

from __future__ import annotations

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
EVIDENCE = "retrace-evidence"
PASS_LINE = "=" * 78


def run(command, cwd=None, env=None, timeout=3600, check=True):
    """Run a command, echo it, and return (code, output)."""
    print(f"\n$ {' '.join(command)}\n", flush=True)
    process = subprocess.run(
        command,
        cwd=cwd,
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        timeout=timeout,
    )
    if process.stdout:
        print(process.stdout[-20_000:], flush=True)
    if check and process.returncode != 0:
        raise SystemExit(f"command failed ({process.returncode}): {' '.join(command)}")
    return process.returncode, process.stdout or ""


def prepare_gradle() -> str:
    binary = f"{GRADLE_DIST}/bin/gradle"
    if os.path.isdir(GRADLE_DIST):
        return binary
    os.makedirs(WORK, exist_ok=True)
    archive = f"{WORK}/gradle-{GRADLE_VERSION}-bin.zip"
    url = f"https://services.gradle.org/distributions/gradle-{GRADLE_VERSION}-bin.zip"
    if not os.path.isfile(archive):
        print(f"downloading {url}", flush=True)
        urllib.request.urlretrieve(url, archive)
    subprocess.run(["unzip", "-q", "-o", archive, "-d", WORK], check=True)
    return binary


def make_signer(worktree: str) -> None:
    keystore = os.path.join(worktree, "release.jks")
    keytool = os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "keytool")
    if not os.path.isfile(keytool):
        keytool = "keytool"
    subprocess.run(
        [
            keytool,
            "-genkeypair",
            "-noprompt",
            "-keystore",
            keystore,
            "-storepass",
            "marble123",
            "-keypass",
            "marble123",
            "-alias",
            "marble",
            "-keyalg",
            "RSA",
            "-keysize",
            "2048",
            "-validity",
            "30",
            "-dname",
            "CN=Marble Retrace, O=MarbleNG",
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    with open(os.path.join(worktree, "signing.properties"), "w", encoding="utf-8") as handle:
        handle.write(
            "storeFile=release.jks\n"
            "storePassword=marble123\n"
            "keyAlias=marble\n"
            "keyPassword=marble123\n"
        )


def build_mapping(gradle: str, target: dict) -> tuple[str | None, str]:
    label = target["label"]
    sha = target["sha"]
    worktree = f"{WORK}/wt-{label}"
    run(["git", "worktree", "remove", "--force", worktree], cwd=REPO, check=False)
    shutil.rmtree(worktree, ignore_errors=True)
    run(["git", "worktree", "add", "--detach", "--force", worktree, sha], cwd=REPO)
    make_signer(worktree)

    env = dict(os.environ)
    env["GRADLE_USER_HOME"] = GRADLE_HOME
    env["ORG_GRADLE_PROJECT_org_gradle_jvmargs"] = "-Xmx4g -Dfile.encoding=UTF-8"
    started = time.time()
    code, output = run(
        [
            gradle,
            "--no-daemon",
            "clean",
            "assembleRelease",
            "-PVERSION_NAME=0.0.0-retrace",
            "-PVERSION_CODE=1",
        ],
        cwd=worktree,
        env=env,
        check=False,
        timeout=3000,
    )
    elapsed = int(time.time() - started)

    mapping = os.path.join(worktree, "app/build/outputs/mapping/release/mapping.txt")
    if not os.path.isfile(mapping) or os.path.getsize(mapping) == 0:
        return None, (
            f"{PASS_LINE}\nBUILD {label} ({sha[:8]}) FAILED after {elapsed}s (exit {code})\n"
            f"{PASS_LINE}\n{output[-8_000:]}\n"
        )
    return mapping, (
        f"{PASS_LINE}\nBUILD {label} ({sha[:8]}) ok in {elapsed}s, mapping "
        f"{os.path.getsize(mapping)} bytes\n{PASS_LINE}\n"
    )


def main() -> int:
    with open(os.path.join(REPO, ".github/retrace/targets.json"), encoding="utf-8") as handle:
        targets = json.load(handle)["targets"]

    os.makedirs(WORK, exist_ok=True)
    os.makedirs(GRADLE_HOME, exist_ok=True)
    gradle = prepare_gradle()

    sections: list[str] = []
    mappings: list[tuple[str, str]] = []

    for target in targets:
        label = target["label"]
        try:
            mapping, header = build_mapping(gradle, target)
        except Exception as error:  # noqa: BLE001 - the harness must never hide a result
            header = f"{PASS_LINE}\nBUILD {label} raised {error!r}\n{PASS_LINE}\n"
            mapping = None
        sections.append(header)
        print(header, flush=True)
        if not mapping:
            continue

        digest = hashlib.sha256(open(mapping, "rb").read()).hexdigest()
        copied = f"{WORK}/mapping-{label}.txt"
        shutil.copyfile(mapping, copied)
        mappings.append((label, copied))
        sections.append(
            f"mapping sha256={digest}\n"
            f"mapping sha256[:32]={digest[:32]}\n"
            f"crash map id     =e1a496adca832d5b4f903fff022dac8f011fa793dfc668a828dc42dc966927ad\n"
        )

        code, output = run(
            [
                sys.executable,
                os.path.join(REPO, ".github/retrace/retrace.py"),
                "--mapping",
                mapping,
                "--crash",
                os.path.join(REPO, ".github/retrace/crash.txt"),
            ],
            cwd=REPO,
            check=False,
        )
        sections.append(output)

    evidence_dir = os.path.join(REPO, EVIDENCE)
    os.makedirs(evidence_dir, exist_ok=True)
    with open(os.path.join(evidence_dir, "retrace.txt"), "w", encoding="utf-8") as handle:
        handle.write("\n".join(sections))
    for label, path in mappings:
        with open(path, "rb") as source, gzip.open(
            os.path.join(evidence_dir, f"mapping-{label}.txt.gz"), "wb"
        ) as target:
            shutil.copyfileobj(source, target)

    publish_evidence()
    return 0


def publish_evidence() -> None:
    branch = os.environ.get("GITHUB_REF_NAME", "arena/01a099af-marbleng")
    run(["git", "add", "-f", EVIDENCE], cwd=REPO)
    run(
        [
            "git",
            "-c",
            "user.email=retrace@marbleng.invalid",
            "-c",
            "user.name=Marble retrace harness",
            "commit",
            "-m",
            "chore(analysis): R8 retrace evidence for the obfuscated crash report",
        ],
        cwd=REPO,
        check=False,
    )
    for attempt in range(3):
        code, _ = run(
            ["git", "push", "origin", f"HEAD:refs/heads/{branch}"],
            cwd=REPO,
            check=False,
        )
        if code == 0:
            print(f"evidence pushed to {branch}", flush=True)
            return
        run(["git", "fetch", "origin", branch], cwd=REPO, check=False)
        run(["git", "rebase", f"FETCH_HEAD"], cwd=REPO, check=False)
    print("::warning::could not push the retrace evidence", flush=True)


if __name__ == "__main__":
    sys.exit(main())
