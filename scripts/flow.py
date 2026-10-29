#!/usr/bin/env python3
"""Flow metrics and Monte Carlo forecasts from the milestone tables in docs/.

Reads every docs/milestone-*.md table with Arrived, Started and Done columns (see docs/backlog.md, "Flow metrics").

    scripts/flow.py                          cycle time, lead time, throughput, work in progress
    scripts/flow.py --when 8                 when will 8 more stories be done? (default: everything not done)
    scripts/flow.py --how-many-by 2026-12-01 how many stories will be done by then?
    scripts/flow.py --since 2026-10-01       sample throughput from this day on (default: the last 28 days)

Days are calendar days. Cycle time counts a story started and done on the same day as 1 day.
"""
import argparse
import glob
import os
import random
import re
from collections import Counter
from datetime import date, timedelta

DOCS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "docs")
OPEN = ("📋", "⏭", "🚧")


def parse_date(text):
    return date.fromisoformat(text) if text else None


def stories():
    found = []
    for path in sorted(glob.glob(os.path.join(DOCS, "milestone-*.md"))):
        header = None
        for line in open(path, encoding="utf-8"):
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if line.startswith("| #"):
                header = cells
            elif header and "Done" in header and re.fullmatch(r"[A-Z]+\d+", cells[0]):
                row = dict(zip(header, cells))
                found.append({
                    "id": row["#"],
                    "milestone": os.path.basename(path)[:-3],
                    "status": row["Status"],
                    "arrived": parse_date(row.get("Arrived")),
                    "started": parse_date(row.get("Started")),
                    "done": parse_date(row.get("Done")),
                })
            elif not line.startswith("|"):
                header = None
    return found


def days(first, last):
    return (last - first).days + 1


def percentile(values, p):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(p / 100 * len(ordered)))]


def spread(label, values):
    if not values:
        print(f"{label}: no data")
        return
    print(f"{label} (n={len(values)}): p50 {percentile(values, 50)}d · p85 {percentile(values, 85)}d · "
          f"p95 {percentile(values, 95)}d · max {max(values)}d")


def daily_throughput(delivered, since, today):
    per_day = Counter(s["done"] for s in delivered)
    return [per_day.get(since + timedelta(d), 0) for d in range(days(since, today))]


def report(all_stories, today):
    delivered = [s for s in all_stories if s["status"] == "✅" and s["done"]]
    spread("Cycle time (started → done)", [days(s["started"], s["done"]) for s in delivered if s["started"]])
    spread("Lead time (arrived → done)", [days(s["arrived"], s["done"]) for s in delivered if s["arrived"]])

    print("\nThroughput per week (✅ only):")
    weeks = Counter(s["done"] - timedelta(s["done"].weekday()) for s in delivered)
    if weeks:
        monday = min(weeks)
        while monday <= today:
            print(f"  week of {monday}: {'#' * weeks[monday]} {weeks[monday]}")
            monday += timedelta(7)

    doing = [s for s in all_stories if s["status"] == "🚧"]
    print(f"\nWork in progress: {len(doing)}")
    for s in doing:
        age = f"{days(s['started'], today)}d old" if s["started"] else "no Started date"
        print(f"  {s['id']} ({s['milestone']}): {age}")
    print(f"Not done yet (📋 ⏭ 🚧): {sum(s['status'] in OPEN for s in all_stories)}")


def simulate(samples, runs, step):
    """Run `step(draw)` `runs` times, where draw() returns one sampled day's throughput."""
    return [step(lambda: random.choice(samples)) for _ in range(runs)]


def forecast_when(samples, count, today, runs):
    def run(draw):
        done, day = 0, 0
        while done < count:
            day += 1
            done += draw()
            if day > 3650:
                return None
        return day

    results = simulate(samples, runs, run)
    if None in results:
        print("Throughput sample is too thin to forecast; widen it with --since.")
        return
    print(f"\nWhen will {count} more stories be done? ({runs} runs)")
    for p in (50, 85, 95):
        print(f"  {p}% sure by {today + timedelta(percentile(results, p))}")


def forecast_how_many(samples, until, today, runs):
    horizon = (until - today).days
    results = simulate(samples, runs, lambda draw: sum(draw() for _ in range(horizon)))
    print(f"\nHow many stories done from tomorrow to {until}? ({runs} runs)")
    for p in (50, 85, 95):
        print(f"  {p}% sure of at least {percentile(results, 100 - p)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--today", type=date.fromisoformat, default=date.today())
    parser.add_argument("--since", type=date.fromisoformat, help="first day of the throughput sample")
    parser.add_argument("--when", type=int, nargs="?", const=-1, help="forecast the date N more stories are done")
    parser.add_argument("--how-many-by", type=date.fromisoformat, help="forecast how many are done by this date")
    parser.add_argument("--runs", type=int, default=10_000)
    parser.add_argument("--seed", type=int, help="for repeatable forecasts")
    args = parser.parse_args()
    random.seed(args.seed)

    all_stories = stories()
    report(all_stories, args.today)
    if args.when is None and args.how_many_by is None:
        return

    since = args.since or args.today - timedelta(27)
    delivered = [s for s in all_stories if s["status"] == "✅" and s["done"]]
    samples = daily_throughput(delivered, since, args.today)
    print(f"\nThroughput sample: {sum(samples)} stories over {len(samples)} days from {since}")
    if not any(samples):
        print("Nothing was done in that window; widen it with --since.")
        return
    if args.when is not None:
        count = args.when if args.when > 0 else sum(s["status"] in OPEN for s in all_stories)
        if count == 0:
            print("\nNothing left to forecast: pass --when N.")
        else:
            forecast_when(samples, count, args.today, args.runs)
    if args.how_many_by:
        forecast_how_many(samples, args.how_many_by, args.today, args.runs)


if __name__ == "__main__":
    main()
