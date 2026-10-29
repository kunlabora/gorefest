# Backlog

The stories live in one file per milestone:

| Milestone                                  | Stories       | What                                                       | Released |
|--------------------------------------------|---------------|------------------------------------------------------------|----------|
| [1: MVP](milestone-1.md)                   | S0–S17        | From the walking skeleton to date polls                    | 1.0      |
| [2: user testing and GDPR](milestone-2.md) | U1–U20, G1–G9 | What the 2026-10-06 user test asked for, and the GDPR work | 2.1, 2.2 |

New stories go in the latest milestone until a new one is opened (`milestone-3.md`, same shape). The screen-by-screen
visual redesign has its own list ([redesign-backlog.md](redesign-backlog.md)), and QA bugs theirs
([qa/README.md](qa/README.md)).

## Status and done

Status: 📋 todo · ⏭ next up · 🚧 doing · ✅ done · 🚫 won't do. Move a story by changing its status in its milestone's
table; tick acceptance criteria in its section or request doc as they land.
A story is done when every criterion is ticked and covered by a test (decider given/when/then where there's a decision,
a web test where there's a page).

## Flow metrics

Every milestone table carries three dates (`YYYY-MM-DD`), so cycle time, lead time and throughput can be measured and
forecast:

- **Arrived**: the day the row is added (📋).
- **Started**: the day it goes to 🚧. Empty for a story that was never started.
- **Done**: the day it goes to ✅ or 🚫. Won't-do stories count for neither cycle time nor throughput.

Fill them in when the status changes; don't rewrite them afterwards. The dates for milestones 1 and 2 were taken from
the git history afterwards.

From these:

- **Cycle time** = Done − Started + 1 day (started and done on the same day is 1 day).
- **Lead time** = Done − Arrived + 1 day.
- **Throughput** = stories done (✅) per day or week.
- **Work in progress** = stories in 🚧, and how long each has been there.

`scripts/flow.py` reports them and runs Monte Carlo forecasts by resampling daily throughput (calendar days, idle ones
included):

```bash
scripts/flow.py
```

```bash
scripts/flow.py --when 8 --since 2026-10-01
```

```bash
scripts/flow.py --how-many-by 2026-12-01
```

`--when` without a number forecasts everything not done yet. The sample defaults to the last 28 days; pick `--since` so
it covers a stretch that looks like the work ahead. Forecasts count stories, so they work best when stories are of
similar size: split a big one rather than letting it skew the sample.
