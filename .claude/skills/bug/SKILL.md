---
name: bug
description: Fix a Boardfest bug the IMP way - each bug is a gap in the Contract, so add or sharpen the obligation first, then a test that fails on the bug, then the fix, and commit it as fix:. Use when the user reports something broken, wrong or crashing ("X doesn't work", "people can still Y", a stack trace, a Discord report).
---

# Bug

A bug means the Contract (`docs/contract/`) didn't promise the right thing, or a test didn't hold it to its promise (IMP Rule 28). Closing the gap is the fix; changing the code is the last step.

## 1. Is it a bug, and where?

- Reproduce it: a failing test, the dev app (`preview_start` with `gorefest`), or the user's steps. If you can't reproduce it, say so and ask for what's missing; don't fix by guessing.
- **`fix:` or `refine:`**: `fix:` only for a defect in the code running in production now (`git describe --tags` is the release; check the bug exists there, not only in unreleased commits on `main`). A defect in unreleased work is `refine:`, and it goes in the story it belongs to.
- Find the area (`docs/contract/README.md` → Areas) and read its obligations.

## 2. The obligation

One of three cases:
- **No obligation covers it**: add one, with the next free number in the area, as one plain sentence about what people can see or rely on ("A cancelled game day takes no new plays"). If it's listed under Known gaps in the README, move it out.
- **An obligation covers it, but its tests pass**: the tests are too weak. Note which (`grep -rn '"XX-n"' src/test`) and sharpen them in step 3.
- **The obligation itself is wrong**: the promise changes. Strike the old one through (`- ~~**XX-n**~~ …, dropped <date>: why`) and add the new one; tell the user, since this is a product decision.

## 3. Red

Write the test that fails because of the bug, tagged `@Obligation("XX-n")`: a decider given/when/then for a wrong decision, a projection test for a wrong read model, a web test (Jsoup + CSS selectors) for a page. Run just that test and see it fail for the bug's reason, not a typo. A test that passes now proves nothing: rethink it.

## 4. Green

The smallest change that makes it pass. Then:
- `./gradlew check` (tests, Contract coverage, architecture, detekt).
- Touched a decider or the matcher: `./gradlew mutationTest` and `scripts/kill_matrix.py --survivors` for the class you changed.
- Run the `adversarial-review` agent on the working tree. Give it nothing but "the working tree". Address what it finds or say why not.

## 5. Could it happen again?

If the bug is a kind (not a one-off), add a check that makes the kind impossible: a rule in `ArchitectureTest`, a guard in `.claude/hooks/guard.py`, or a component that carries the right thing. If an agent wrote the bug and the same mistake has happened before, this step isn't optional.

## 6. Commit

One commit: `fix: <what people see now, in plain words>` (or `refine:` per step 1), with the obligation id in the body. Commit on `main` without asking; don't push (pushing deploys: offer the `release` skill, a patch bump).

Report: the cause in a sentence, the obligation (new, sharpened or replaced), the test, the fix, and whether a guardrail was added.
