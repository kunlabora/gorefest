---
name: regen-check
description: Check that the Contract is complete enough to rebuild the code from (IMP Rule 17) - a fresh agent rewrites one decider or read model from its obligations alone in a throwaway worktree, and the existing tests judge it. Gaps it reveals become obligations. Use occasionally (after a milestone, or when an area feels under-specified), or when the user asks for a regeneration check.
---

# Regeneration check

If the Contract and its tests really say what the code must do, an agent that has never seen the code can rebuild it and the tests pass. Where they don't, the Contract has a gap: the code does something nobody promised, or promises something no test holds.

## 1. Pick a target

One small, pure unit in `gorefest.core`: a decider (`…Decider.kt`) or a read model, plus its area in `docs/contract/`. Good targets have many obligations and few dependencies. Tell the user which and why.

## 2. Rebuild blind

Start one subagent with `isolation: "worktree"`. In its worktree, first delete the target file's body (keep the package, the public signatures and the types it uses, so the rest compiles). Brief it:
- the target's signatures, `core/Event.kt`, `core/Rejection.kt` and the area's Contract file, and the conventions in CLAUDE.md;
- it may not read the deleted file's history (`git log -p`, `git show`) or the tests;
- implement the target, then run the area's tests once (`./gradlew test --tests '…'`) and report which fail, without fixing them by reading the tests;
- commit on its branch; never merge or push.

## 3. Read the result

- **A test fails**: the test holds a rule the Contract doesn't state. Add the obligation (next free number) and tag the test. Or the rule is accidental: ask the user whether to keep it.
- **All tests pass, but the rebuild behaves differently** (diff the two, or run `./gradlew mutationTest` on it): behaviour nobody tests. Add the obligation and the test, or list it under Known gaps.
- **All pass, same behaviour**: the area is well specified. Say so.

## 4. Clean up

Remove the worktree and branch. Commit only the Contract and test changes on `main`: `refine: Regeneration check on <target>: <what it found>`. The rebuilt code is never kept.
