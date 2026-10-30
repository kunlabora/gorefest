---
name: release
description: Cut a Boardfest release - bump the version in build.gradle.kts (patch, minor or major), commit "Version X.Y.Z", add an annotated vX.Y.Z tag with a one-line summary, and push main plus the tag (which deploys). Use when the user asks to release, bump the version, tag, or "version, tag and push".
---

# Release

Pushing `main` deploys (`.github/workflows/ci.yml`: test → build → deploy on every push to main). A release is just a version bump on top of finished, committed work.

## Before

1. `git status`: the tree is clean apart from `.claude/worktrees/`, and you are on `main`. Commit or ask about anything else first; don't fold it into the release commit.
2. `./gradlew test` passes. If it fails, stop and report.
3. Pick the bump from what the user said. If they didn't say, use **patch** for fixes only, **minor** when a story or feature landed, **major** only when they ask. Tell them which you picked.

## Steps

1. Read the current `version = "X.Y.Z"` in `build.gradle.kts` (the only place the version lives; the footer shows it as "Boardfest vX.Y.Z") and change it to the new one. Nothing else goes in this commit.
2. Commit with exactly this message (plus the attribution line the session asks for):
   ```
   Version X.Y.Z
   ```
3. Write the tag summary from the commits since the previous tag (`git log --oneline vPREV..HEAD`). It's one line, `Boardfest X.Y.Z: <what changed>`, in the brand voice: plain words, comma-separated, user-facing first, bug ids in passing. Earlier tags:
   - `Boardfest 2.0.0: the redesign`
   - `Boardfest 2.1.0: GDPR (privacy notice, account page, erasure, export, retention), logs without secrets, QA round 2`
   - `Boardfest 2.1.1: stylesheet reaches browsers after a deploy, account and privacy screen fixes, BUG-018/025/026 (...)`
4. Make an **annotated** tag on the version commit:
   ```bash
   git tag -a vX.Y.Z -m "Boardfest X.Y.Z: ..."
   ```
5. Push the branch, then the tag:
   ```bash
   git push origin main
   ```
   ```bash
   git push origin vX.Y.Z
   ```

## After

Tell the user the new version, the commit hash, the tag line, and the pushed range (`old..new`). Say that the push to main starts the deploy, and that the footer shows the new version once it's live.

The push asks for confirmation (the `guard.py` hook); that is expected, the user confirms it.

Never move or re-push an existing tag. If a tag is wrong after it was pushed, ask the user before doing anything about it.
