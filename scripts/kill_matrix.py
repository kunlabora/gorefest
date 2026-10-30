#!/usr/bin/env python3
"""IMP's kill matrix (Rule 26): which core tests catch a mutant no other test catches.

Reads build/reports/pitest/mutations.xml from `./gradlew mutationTest` (run with --fullMutationMatrix)
and prints, per test class, the tests that kill nothing on their own: candidates to merge or delete.
A test that proves a Contract obligation stays even when it kills nothing unique; say so in its name.
Usage: scripts/kill_matrix.py [mutations.xml] [--survivors]
"""
import collections
import re
import sys
import xml.etree.ElementTree as ET

args = [a for a in sys.argv[1:] if not a.startswith("--")]
path = args[0] if args else "build/reports/pitest/mutations.xml"
mutations = ET.parse(path).getroot()

method = re.compile(r"\[class:([^\]]+)\]/\[method:([^\]]+)\(\)\]")
kills = collections.defaultdict(int)
unique = collections.defaultdict(int)
survivors = []

for m in mutations:
    killers = {f"{c.rsplit('.', 1)[-1]} :: {n}" for c, n in method.findall(m.findtext("killingTests") or "")}
    for t in killers:
        kills[t] += 1
    if len(killers) == 1:
        unique[next(iter(killers))] += 1
    if m.get("status") in ("SURVIVED", "NO_COVERAGE"):
        survivors.append((m.findtext("sourceFile"), int(m.findtext("lineNumber")), m.findtext("description"), m.get("status")))

total = len(mutations)
killed = sum(1 for m in mutations if m.get("detected") == "true")
redundant = sorted(t for t in kills if unique[t] == 0)
print(f"{killed}/{total} mutants killed by {len(kills)} tests; {len(redundant)} tests kill nothing on their own.\n")
for cls, tests in sorted(collections.Counter(t.split(" :: ")[0] for t in redundant).items()):
    print(f"{cls} ({tests})")
    for t in redundant:
        if t.startswith(cls + " :: "):
            print(f"  - {t.split(' :: ', 1)[1]}  (kills {kills[t]}, none alone)")

if "--survivors" in sys.argv:
    print(f"\n{len(survivors)} mutants no test notices:")
    for f, line, desc, status in sorted(survivors):
        print(f"  {f}:{line}  {desc}{'  (no coverage)' if status == 'NO_COVERAGE' else ''}")
