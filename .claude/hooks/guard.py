#!/usr/bin/env python3
"""PreToolUse guard (IMP: guardrails on the agent itself). Warn and ask, never block.

Asks the person before a change that is hard to undo or breaks a promise of the codebase:
- pushing (pushing main deploys),
- editing a Flyway migration that already exists (it has run in production),
- removing or renaming a stored event type (`@SerialName` is forever),
- dropping a Contract obligation id (ids are forever; strike it through instead),
- growing the detekt baseline (new code meets the rules).
Everything else passes silently.
"""
import json
import os
import re
import sys

ROOT = os.environ.get("CLAUDE_PROJECT_DIR", os.getcwd())


def ask(reason):
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "ask",
        "permissionDecisionReason": reason,
    }}))
    sys.exit(0)


def rel(path):
    return os.path.relpath(os.path.abspath(os.path.join(ROOT, path)), ROOT)


def current(path):
    try:
        with open(os.path.join(ROOT, path), encoding="utf-8") as f:
            return f.read()
    except OSError:
        return None


def after(tool, inp, before):
    """The file's text once the edit lands, or None if it can't be worked out."""
    if tool == "Write":
        return inp.get("content", "")
    if tool == "Edit" and before is not None:
        old, new = inp.get("old_string", ""), inp.get("new_string", "")
        return before.replace(old, new) if inp.get("replace_all") else before.replace(old, new, 1)
    return None


def check_bash(command):
    if re.search(r"\bgit\b[^;&|]*\bpush\b", command):
        ask("This pushes, and pushing main deploys to production. Only the release skill pushes, when you asked for a release.")
    if "detektBaseline" in command:
        ask("This rewrites the detekt baseline, which would hide new findings. New code should meet the rules instead.")
    if re.search(r"db/migration/V\d+", command) and re.search(r"\bsed\s+-i|>\s*\S*db/migration|\bmv\b|\brm\b", command):
        ask("This changes or removes an existing Flyway migration, which has already run in production. Add a new V<n> migration instead.")


def check_file(tool, inp):
    path = rel(inp.get("file_path", ""))
    before = current(path)
    text = after(tool, inp, before)

    if re.fullmatch(r"src/main/resources/db/migration/V\d+__.*\.sql", path) and before is not None:
        ask(f"{path} already exists and has run in production; changing it breaks Flyway's checksum. Add a new V<n> migration instead.")

    if path == "config/detekt/baseline.xml" and before is not None and text is not None:
        if text.count("<ID>") > before.count("<ID>"):
            ask("This adds findings to the detekt baseline. New code should meet the rules instead.")

    if path.startswith("src/main/kotlin/") and path.endswith(".kt") and before is not None and text is not None:
        names = lambda s: set(re.findall(r'@SerialName\("([^"]+)"\)', s))
        gone = names(before) - names(text)
        if gone:
            ask(f"This removes or renames the stored event type(s) {', '.join(sorted(gone))}. "
                "Stored events decode by that name on every replay: add a new event instead (CLAUDE.md, Event evolution).")

    if re.fullmatch(r"docs/contract/[^/]+\.md", path) and before is not None and text is not None:
        ids = lambda s: set(re.findall(r"\*\*([A-Z]+-\d+)\*\*", s))
        gone = ids(before) - ids(text)
        if gone:
            ask(f"This drops Contract obligation(s) {', '.join(sorted(gone))}. Ids are forever: strike a dropped one through "
                "(`- ~~**XX-n**~~ …, dropped <date>: why`) instead of deleting or renumbering.")


def main():
    event = json.load(sys.stdin)
    tool, inp = event.get("tool_name", ""), event.get("tool_input", {}) or {}
    if tool == "Bash":
        check_bash(inp.get("command", ""))
    elif tool in ("Edit", "Write"):
        check_file(tool, inp)


if __name__ == "__main__":
    main()
