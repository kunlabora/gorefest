#!/usr/bin/env python3
"""Stop hook: Kotlin changed since the last `./gradlew check` (or test) run? Say so once.

Compares the newest changed .kt or contract file with the last test results. It nudges once per stop
(`stop_hook_active` lets the second stop through), so it never traps the session.
"""
import json
import os
import subprocess
import sys

ROOT = os.environ.get("CLAUDE_PROJECT_DIR", os.getcwd())


def main():
    event = json.load(sys.stdin)
    if event.get("stop_hook_active"):
        return
    try:
        status = subprocess.run(["git", "status", "--porcelain"], cwd=ROOT, capture_output=True, text=True, timeout=10).stdout
    except (OSError, subprocess.SubprocessError):
        return
    changed = [line[3:].split(" -> ")[-1] for line in status.splitlines()]
    watched = [p for p in changed if p.endswith((".kt", ".kts")) or p.startswith("docs/contract/")]
    times = [os.path.getmtime(os.path.join(ROOT, p)) for p in watched if os.path.isfile(os.path.join(ROOT, p))]
    if not times:
        return
    results = os.path.join(ROOT, "build/test-results/test")
    runs = [e.stat().st_mtime for e in os.scandir(results) if e.is_file()] if os.path.isdir(results) else []
    last_run = max(runs, default=0)
    if max(times) > last_run:
        print(json.dumps({
            "decision": "block",
            "reason": "Kotlin or Contract files changed since the last test run. Run `./gradlew check` "
                      "(tests, contract coverage, architecture rules, detekt) before calling this done, "
                      "or say plainly that it wasn't run.",
        }))


if __name__ == "__main__":
    main()
