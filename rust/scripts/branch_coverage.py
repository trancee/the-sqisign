#!/usr/bin/env python3
"""Fail unless both outcomes of every branch are covered.

`cargo llvm-cov` has no branch threshold, so the coverage job exports JSON
with `--branch` (nightly) and this script gates it (Constitution T3, ADR 0004
item 4). The `llvm-cov` summary scores each generic function by its best
single instantiation, so a branch whose outcomes the tests take under
different parameter sets counts as missed there. This script reads the full
export instead and counts an outcome as covered when any instantiation takes
it. It fails on an export without branch data, so a run without `--branch`
cannot pass.

Usage (from `rust/`): `python3 scripts/branch_coverage.py EXPORT.json`.
"""

import json
import sys


def main(path):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)["data"][0]
    taken = {}
    for entry in data["files"]:
        for line, col, _, _, true, false, *_ in entry.get("branches", []):
            key = (entry["filename"], line, col)
            t, f = taken.get(key, (0, 0))
            taken[key] = (t + true, f + false)
    if not taken:
        print("no branch data: run `cargo llvm-cov` with `--branch` and without `--summary-only`")
        return 1
    missed = 0
    for (name, line, col), counts in sorted(taken.items()):
        for outcome, count in zip(("true", "false"), counts):
            if count == 0:
                missed += 1
                print(f"{name}:{line}:{col}: never {outcome}")
    total = 2 * len(taken)
    print(f"branch outcomes: {total - missed}/{total}")
    return 0 if missed == 0 else 1


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    sys.exit(main(sys.argv[1]))
