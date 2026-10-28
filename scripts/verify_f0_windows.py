#!/usr/bin/env python3
"""Offline F0 checks from WSL using existing Windows tools and an isolated copy."""

import argparse
import datetime
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET


TASKS = ("testDebugUnitTest", "lintDebug", "assembleDebug")


def windows_path(path):
    value = subprocess.check_output(["wslpath", "-w", str(path.resolve())], text=True).strip()
    if any(character in value for character in ('"', "%", "\r", "\n")):
        raise ValueError("Path cannot be represented safely in the Windows command file")
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, help="Existing SDK under /mnt/<drive>; auto-detected if unique")
    parser.add_argument("--jdk", type=Path, default=Path("/mnt/c/Program Files/Android/Android Studio/jbr"))
    parser.add_argument("--gradle-user-home", type=Path, help="Existing Windows Gradle cache")
    parser.add_argument("--readonly-cache-root", type=Path, help="Optional immutable cache seed containing modules-2")
    parser.add_argument("--check-environment", action="store_true", help="Validate installed tools/cache without copying or building")
    parser.add_argument("--tasks", nargs="+", choices=TASKS, default=list(TASKS))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if args.sdk is None:
        candidates = sorted(Path("/mnt/c/Users").glob("*/AppData/Local/Android/Sdk"))
        if len(candidates) != 1:
            parser.error("Specify --sdk: a unique existing Windows SDK was not found")
        args.sdk = candidates[0]
    sdk = args.sdk.resolve()
    if not str(sdk).startswith("/mnt/c/Users/"):
        parser.error("This Windows helper expects --sdk under /mnt/c/Users/<user>/")
    user_root = sdk.parents[3]
    gradle_home = (args.gradle_user_home or user_root / ".gradle").resolve()
    cmd = shutil.which("cmd.exe")
    required = [args.jdk / "bin/java.exe", sdk / "platforms/android-35/android.jar", sdk / "build-tools/36.0.0/aapt2.exe", gradle_home]
    if cmd is None or any(not path.exists() for path in required):
        parser.error("Existing cmd.exe, Windows JDK, SDK 35, Build Tools 36.0.0 and Gradle cache are required; nothing is installed")
    distributions = gradle_home / "wrapper/dists/gradle-8.11.1-bin"
    cached = any((path / "lib/gradle-launcher-8.11.1.jar").is_file() and (path.parent / "gradle-8.11.1-bin.zip.ok").is_file() for path in distributions.glob("*/gradle-8.11.1"))
    if not cached:
        parser.error("Gradle 8.11.1 wrapper distribution must already be cached; automatic download is not allowed by this helper")
    if args.readonly_cache_root and not (args.readonly_cache_root / "modules-2").is_dir():
        parser.error("--readonly-cache-root must contain modules-2")
    if args.check_environment:
        print(json.dumps({"jdk": str(args.jdk), "sdk": str(sdk), "gradleUserHome": str(gradle_home), "cachedGradle": "8.11.1", "offline": True}, indent=2))
        return 0

    scratch_root = user_root / "AppData/Local/Temp/app_gastos_f0"
    scratch_root.mkdir(parents=True, exist_ok=True)
    scratch = Path(tempfile.mkdtemp(prefix="verify-", dir=scratch_root))
    project = scratch / "project"
    project.mkdir()
    inputs = {}
    for name in ("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat", "app/build.gradle.kts", "app/proguard-rules.pro"):
        destination = project / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(root / name, destination)
    for name in ("gradle", "app/src", "app/schemas"):
        shutil.copytree(root / name, project / name)
    for path in sorted(project.rglob("*")):
        if path.is_file():
            relative = path.relative_to(project).as_posix()
            inputs[relative] = hashlib.sha256(path.read_bytes()).hexdigest()
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    output = root / ".artifacts/f0" / stamp
    output.mkdir(parents=True)
    state = {
        "startedUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "sourceRoot": str(root), "project": str(project), "jdk": str(args.jdk),
        "sdk": str(sdk), "gradleUserHome": str(gradle_home),
        "readonlyCacheRoot": str(args.readonly_cache_root) if args.readonly_cache_root else None,
        "tasks": args.tasks, "offline": True, "sourceInputHashes": inputs,
    }
    lines = ["@echo off", "setlocal", f'set "JAVA_HOME={windows_path(args.jdk)}"', f'set "ANDROID_HOME={windows_path(sdk)}"', f'set "ANDROID_SDK_ROOT={windows_path(sdk)}"', f'set "GRADLE_USER_HOME={windows_path(gradle_home)}"']
    if args.readonly_cache_root:
        lines.append(f'set "GRADLE_RO_DEP_CACHE={windows_path(args.readonly_cache_root)}"')
    lines += [f'cd /d "{windows_path(project)}"', '"%JAVA_HOME%\\bin\\java.exe" -version', 'call gradlew.bat --offline --no-daemon --console=plain --continue ' + " ".join(args.tasks), "exit /b %ERRORLEVEL%"]
    runner = scratch / "run-gradle.cmd"
    runner.write_text("\r\n".join(lines) + "\r\n", encoding="utf-8", newline="")
    summary = output / "summary.json"
    summary.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n")
    print("Evidence:", output, flush=True)
    print("Isolated project:", project, flush=True)
    log = output / "gradle.log"
    with log.open("w") as stream:
        result = subprocess.run([cmd, "/d", "/c", windows_path(runner)], stdout=stream, stderr=subprocess.STDOUT)
    state["exitCode"] = result.returncode
    state["finishedUtc"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    state["sourceInputsUnchanged"] = all((root / name).is_file() and hashlib.sha256((root / name).read_bytes()).hexdigest() == digest for name, digest in inputs.items())
    state["unitTests"] = []
    reports = output / "reports"
    for filename in (project / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
        report = ET.parse(filename).getroot()
        state["unitTests"].append({key: report.attrib.get(key) for key in ("name", "tests", "failures", "errors", "skipped", "timestamp")})
        reports.mkdir(exist_ok=True)
        shutil.copy2(filename, reports / filename.name)
    for filename in (project / "app/build/reports").glob("lint-results-debug.*"):
        reports.mkdir(exist_ok=True)
        shutil.copy2(filename, reports / filename.name)
    apk = project / "app/build/outputs/apk/debug/app-debug.apk"
    state["apk"] = {"path": str(apk), "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "bytes": apk.stat().st_size} if apk.is_file() else None
    summary.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n")
    print("Gradle exit:", result.returncode)
    print("Source inputs unchanged:", state["sourceInputsUnchanged"])
    print("\n".join(log.read_text(errors="replace").splitlines()[-45:]))
    return result.returncode if state["sourceInputsUnchanged"] else 2


if __name__ == "__main__":
    sys.exit(main())
