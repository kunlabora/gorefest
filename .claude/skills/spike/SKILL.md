---
name: spike
description: Try two or three ways of building something in parallel, each in its own throwaway git worktree, then compare them and pick (IMP Rule 15 - code is cheap, so explore by building). Use when the shape of a change is genuinely unclear (a new algorithm, a data model, a library, a screen flow), or when the user asks to spike, try options, or prototype. Not for choices a request doc already decided.
---

# Spike

Building two options is now cheaper than arguing about one. A spike answers a question with running code, then is thrown away. What survives is the decision and what was learned.

## 1. The question

Write it down in one sentence with what decides it ("Which taste model predicts held-out PLAYED comparisons better?", "Can the poll page stay under 1 s with 40 games?"). Pick a measure: a test, a number from `Evaluation`, a screenshot, a line count. No measure, no spike: ask the user what would convince them.

Choose two or three options that really differ. Tell the user the question, the options and the measure in a few lines, then go on unless they object.

## 2. Build in parallel

One subagent per option, started in a single message with `isolation: "worktree"`, so each gets its own checkout and nothing touches `main` or anyone else's work. Brief each the same way:
- the question and the measure, its option, and that the code is throwaway (no docs, no Contract, no polish; tests only where they measure);
- a time box: stop at the first answer good enough to compare;
- report: the measure's result, what was hard, what surprised, and the branch name. Commit on its branch; never merge, never push.

## 3. Compare and decide

Put the results side by side (a short table: option, measure, cost, risk). Recommend one with the reason; the user decides when it's a product choice. Record the decision where it belongs: the request doc (`**Decided (date):** … (spike: options A, B)`) or a note in CLAUDE.md for an architecture choice.

## 4. Throw it away

Delete the spike branches and worktrees (`git worktree remove`, `git branch -D`) once the user has seen the result. Then build the chosen option properly, through the `story` skill or the change at hand: Contract first, tests, the usual guardrails. Copying a function from the spike is fine; merging the spike branch is not.
